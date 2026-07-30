package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.skilles.chronoclones.network.RecordingHighlightPayload;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Records what a player did inside a container, as the clicks they made.
 */
public final class ContainerWatch {

    private ContainerWatch() {}

    /**
     * An open container and the clicks made in it so far.
     *
     * @param snapshot every occupied player slot as the menu opened, narrowed on close
     * @param touched  the player slots the clicks name, collected live because a swap's
     *                 hotbar button needs the open menu to resolve
     */
    private record Watch(MenuTarget target, BlockPos pos, List<SessionSteps.Event> clicks, int menuSize,
                         List<ChronoAction.UseContainer.CarrierSlot> snapshot, Set<Integer> touched) {}

    /**
     * Something right-clicked this tick, held only until we learn whether it opened a menu.
     *
     * @param pos where it is in the world, for the highlight and for the capture position
     */
    private record Pending(MenuTarget target, BlockPos pos, int actionIndex) {}

    /**
     * What the menu looked like as a click arrived, which is gone by the time it returns.
     */
    private record Before(Optional<Holder<Item>> slotItem, boolean held) {

        static final Before NOTHING = new Before(Optional.empty(), false);
    }

    private static final Map<UUID, Watch> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Before> MID_CLICK = new ConcurrentHashMap<>();

    /** The action index lets {@link #onContainerOpened} retract the click if a menu opens. */
    public static void noteInteraction(ServerPlayer player, BlockPos pos, int actionIndex,
                                       RecordingSession session) {
        // An anchor's own slots are machinery; replay refuses to reach into one anyway.
        if (player.level().getBlockState(pos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return;
        }
        PENDING.put(player.getUUID(), new Pending(
                new MenuTarget.Block(session.toLocal(pos), Optional.of(
                        player.level().getBlockState(pos).typeHolder())),
                pos, actionIndex));
    }

    /** The same, for an entity: a villager's trades, a horse's saddlebags, a chest boat. */
    public static void noteInteraction(ServerPlayer player, Entity target, int actionIndex,
                                       RecordingSession session) {
        PENDING.put(player.getUUID(), new Pending(
                new MenuTarget.Entity(session.toLocal(target.position()),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType())),
                target.blockPosition(), actionIndex));
    }

    /**
     * A container opened: start collecting clicks, and retract the click that opened it.
     */
    public static void onContainerOpened(ServerPlayer player, RecordingSession session) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        // A block menu has to be one replay can open again; an entity's is opened through the
        // entity, which the target rule finds for itself.
        if (pending.target() instanceof MenuTarget.Block
                && player.level().getBlockState(pending.pos())
                        .getMenuProvider(player.level(), pending.pos()) == null) {
            return;
        }

        session.dropActionAt(pending.actionIndex());
        Watch watch = new Watch(pending.target(), pending.pos(), new ArrayList<>(),
                player.containerMenu.slots.size(), snapshot(player), new LinkedHashSet<>());
        OPEN.put(player.getUUID(), watch);
        // Nothing to highlight yet, but this signals that the container is being watched.
        publish(player, watch);
    }

    /**
     * Tells the client which slots the session has picked up, for the highlight drawn over the menu.
     */
    private static void publish(ServerPlayer player, Watch watch) {
        List<Integer> carried = new ArrayList<>();
        for (ChronoAction.UseContainer.CarrierSlot slot : carried(watch.snapshot(), watch.touched())) {
            carried.add(slot.menuSlot());
        }
        send(player, new RecordingHighlightPayload(
                player.containerMenu.containerId, List.copyOf(watch.touched()), carried));
    }

    /**
     * Every occupied player slot as the menu opened.
     */
    private static List<ChronoAction.UseContainer.CarrierSlot> snapshot(ServerPlayer player) {
        List<ChronoAction.UseContainer.CarrierSlot> layout = new ArrayList<>();
        AbstractContainerMenu menu = player.containerMenu;

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container != player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            layout.add(new ChronoAction.UseContainer.CarrierSlot(index, stack));
        }
        return layout;
    }

    /**
     * The menu as a click arrives, from the mixin's first half.
     *
     * <p>A click is only nameable from both sides of it: what the square held, and whether the cursor
     * came away with anything.
     */
    public static void beforeClick(ServerPlayer player, int slot) {
        if (!watching(player)) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        Optional<Holder<Item>> item = Optional.empty();
        if (slot >= 0 && slot < menu.slots.size()) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) {
                item = Optional.of(BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
            }
        }
        MID_CLICK.put(player.getUUID(), new Before(item, !menu.getCarried().isEmpty()));
    }

    /**
     * The same click, returned, from the mixin's second half.
     */
    public static void onClick(ServerPlayer player, int slot, int button, ContainerInput input) {
        Before before = MID_CLICK.remove(player.getUUID());
        if (!watching(player)) {
            return;
        }
        Watch watch = OPEN.get(player.getUUID());
        if (watch == null) {
            return;
        }
        if (before == null) {
            // The watch began between the two halves of one click, so this one is unnameable.
            before = Before.NOTHING;
        }
        watch.clicks().add(new SessionSteps.Event.Clicked(
                new SessionSteps.Observation(slot, button, input, before.slotItem(),
                        before.held(), !player.containerMenu.getCarried().isEmpty())));

        // -1 is outside the window and container slots are not the carrier's business; the
        // snapshot filters both.
        watch.touched().add(slot);
        if (input == ContainerInput.SWAP) {
            // The one click whose target is not the slot it names: the button is a hotbar index.
            int swapped = menuSlotOf(player, button);
            if (swapped >= 0) {
                watch.touched().add(swapped);
            }
        }
        publish(player, watch);
    }

    private static boolean watching(ServerPlayer player) {
        return RecordingSessions.get(player) != null && OPEN.containsKey(player.getUUID());
    }

    /**
     * A control in the menu: an enchantment tier, a loom pattern. From the mixin, since there is no
     * event and no click to read it from.
     */
    public static void onButton(ServerPlayer player, int id) {
        record(player, new SessionStep.Button(id));
    }

    /**
     * A merchant's offer chosen, kept as what it offers rather than where it sat in the list.
     */
    public static void onTrade(ServerPlayer player, int index) {
        if (!(player.containerMenu instanceof MerchantMenu merchant)) {
            return;
        }
        MerchantOffers offers = merchant.getOffers();
        if (index < 0 || index >= offers.size()) {
            return;
        }
        MerchantOffer offer = offers.get(index);
        record(player, new SessionStep.Trade(offer.getCostA(), offer.getCostB(), offer.getResult()));
    }

    /** A name typed into an anvil. */
    public static void onRename(ServerPlayer player, String text) {
        record(player, new SessionStep.Rename(text));
    }

    private static void record(ServerPlayer player, SessionStep step) {
        if (!watching(player)) {
            return;
        }
        Watch watch = OPEN.get(player.getUUID());
        if (watch != null) {
            watch.clicks().add(new SessionSteps.Event.Did(step));
        }
    }

    /** Where a player-inventory index sits in the open menu, or -1 if this menu does not show it. */
    private static int menuSlotOf(ServerPlayer player, int inventorySlot) {
        AbstractContainerMenu menu = player.containerMenu;
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                return index;
            }
        }
        return -1;
    }

    /**
     * The player's own items that this session actually depends on.
     */
    static List<ChronoAction.UseContainer.CarrierSlot> carried(
            List<ChronoAction.UseContainer.CarrierSlot> snapshot, Set<Integer> touched) {
        List<ChronoAction.UseContainer.CarrierSlot> carried = new ArrayList<>();
        for (ChronoAction.UseContainer.CarrierSlot slot : snapshot) {
            if (touched.contains(slot.menuSlot())) {
                carried.add(slot);
            }
        }
        return carried;
    }

    /** The session, or null if nothing was clicked. */
    public static ChronoAction.@Nullable UseContainer onContainerClosed(ServerPlayer player,
                                                                     RecordingSession session) {
        Watch watch = OPEN.get(player.getUUID());
        forget(player);
        if (watch == null || watch.clicks().isEmpty()) {
            return null;
        }
        return new ChronoAction.UseContainer(watch.target(), watch.menuSize(),
                carried(watch.snapshot(), watch.touched()),
                SessionSteps.interpret(watch.clicks()));
    }

    /** The world position of the container currently open for this player, if any. */
    public static @Nullable BlockPos openPosition(ServerPlayer player) {
        Watch watch = OPEN.get(player.getUUID());
        return watch == null ? null : watch.pos();
    }

    /**
     * Stops watching this player, and takes the highlight down with it.
     */
    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        MID_CLICK.remove(player.getUUID());
        send(player, new RecordingHighlightPayload(NO_CONTAINER, List.of(), List.of()));
    }

    /**
     * Forgetting a watch also happens on the way out of a dimension, and on respawn.
     *
     * <p>Asked whether the channel exists rather than assumed: sending down a connection that has
     * not negotiated it is a hard error, not a dropped packet.
     */
    private static void send(ServerPlayer player, RecordingHighlightPayload payload) {
        if (player.connection != null && !player.hasDisconnected()
                && player.connection.hasChannel(RecordingHighlightPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** A container id no menu has, which the client reads as "draw nothing". */
    private static final int NO_CONTAINER = -1;

    public static void clear() {
        OPEN.clear();
        PENDING.clear();
        MID_CLICK.clear();
    }
}

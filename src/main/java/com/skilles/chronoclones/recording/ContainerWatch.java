package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.network.RecordingHighlightPayload;

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
import com.skilles.chronoclones.platform.PlatformNetwork;
import org.jspecify.annotations.Nullable;

/** Records what a player did inside a container, as the clicks they made. */
public final class ContainerWatch {

    private ContainerWatch() {}

    private record Watch(MenuTarget target, BlockPos pos, List<SessionSteps.Event> clicks, int menuSize,
                         List<ChronoAction.UseContainer.CarrierSlot> snapshot, Set<Integer> touched) {}

    private record Pending(MenuTarget target, BlockPos pos, long tick) {}

    private record Before(Optional<Holder<Item>> slotItem, boolean held) {

        static final Before NOTHING = new Before(Optional.empty(), false);
    }

    private static final Map<UUID, Watch> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Before> MID_CLICK = new ConcurrentHashMap<>();

    public static void noteInteraction(ServerPlayer player, BlockPos pos,
                                       RecordingSession session) {
        PENDING.put(player.getUUID(), new Pending(
                new MenuTarget.Block(session.toLocal(pos), Optional.of(
                        //? if >=26 {
                        player.level().getBlockState(pos).typeHolder())),
                        //?} else {
                        /*player.level().getBlockState(pos).getBlockHolder())),
                        *///?}
                pos, now(player)));
    }

    public static void noteInteraction(ServerPlayer player, Entity target,
                                       RecordingSession session) {
        PENDING.put(player.getUUID(), new Pending(
                new MenuTarget.Entity(session.toLocal(target.position()),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType())),
                target.blockPosition(), now(player)));
    }

    public static void onContainerOpened(ServerPlayer player, RecordingSession session) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        if (pending.tick() != now(player)) {
            return;
        }
        if (pending.target() instanceof MenuTarget.Block
                && player.level().getBlockState(pending.pos())
                        .getMenuProvider(player.level(), pending.pos()) == null) {
            return;
        }

        InteractionWatch.claim(player);
        Watch watch = new Watch(pending.target(), pending.pos(), new ArrayList<>(),
                player.containerMenu.slots.size(), snapshot(player), new LinkedHashSet<>());
        OPEN.put(player.getUUID(), watch);
        publish(player, watch);
    }

    private static void publish(ServerPlayer player, Watch watch) {
        List<Integer> carried = new ArrayList<>();
        for (ChronoAction.UseContainer.CarrierSlot slot : carried(watch.snapshot(), watch.touched())) {
            carried.add(slot.menuSlot());
        }
        send(player, new RecordingHighlightPayload(
                player.containerMenu.containerId, List.copyOf(watch.touched()), carried));
    }

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

    /** A click is only nameable from both sides: what the slot held, and what the cursor took. */
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
            before = Before.NOTHING;
        }
        watch.clicks().add(new SessionSteps.Event.Clicked(
                new SessionSteps.Observation(slot, button, input, before.slotItem(),
                        before.held(), !player.containerMenu.getCarried().isEmpty())));

        watch.touched().add(slot);
        // The one click whose target is not the slot it names: the button is a hotbar index.
        if (input == ContainerInput.SWAP) {
            int swapped = menuSlotOf(player, button);
            if (swapped >= 0) {
                watch.touched().add(swapped);
            }
        }
        publish(player, watch);
        stopIfOverfull(player, watch);
    }

    /** The action cap counts a whole session as one action, so this is what bounds it. */
    private static void stopIfOverfull(ServerPlayer player, Watch watch) {
        if (watch.clicks().size() < ChronoclonesConfig.maxContainerSteps()) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session != null) {
            RecordingCapture.stop(player, session, RecordingSession.StopReason.STEP_CAP);
        }
    }

    private static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    private static boolean watching(ServerPlayer player) {
        return RecordingSessions.get(player) != null && OPEN.containsKey(player.getUUID());
    }

    public static void onButton(ServerPlayer player, int id) {
        record(player, new SessionStep.Button(id));
    }

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
            stopIfOverfull(player, watch);
        }
    }

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

    public static @Nullable BlockPos openPosition(ServerPlayer player) {
        Watch watch = OPEN.get(player.getUUID());
        return watch == null ? null : watch.pos();
    }

    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        MID_CLICK.remove(player.getUUID());
        send(player, new RecordingHighlightPayload(NO_CONTAINER, List.of(), List.of()));
    }

    private static void send(ServerPlayer player, RecordingHighlightPayload payload) {
        if (player.connection == null || player.hasDisconnected()) {
            return;
        }
        //? if <26 {
        /*// 21.1's hasChannel reads a channel attribute that mock and fake players lack.
        if (!player.connection.isAcceptingMessages()) {
            return;
        }
        *///?}
        // Only if the client declared it understands this payload; a vanilla client would kick.
        boolean listening =
                //? if neoforge {
                player.connection.hasChannel(RecordingHighlightPayload.TYPE);
                //?} else {
                //? if forge {
                /*com.skilles.chronoclones.platform.forge.ForgeNetwork.canSend(player);
                *///?}
                //? if fabric {
                //? if >=1.20.5 {
                /*net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                        player, RecordingHighlightPayload.TYPE);
                *///?} else {
                /*net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                        player, RecordingHighlightPayload.TYPE.id());
                *///?}
                //?}
                //?}
        if (listening) {
            PlatformNetwork.sendToPlayer(player, payload);
        }
    }

    private static final int NO_CONTAINER = -1;

    public static void clear() {
        OPEN.clear();
        PENDING.clear();
        MID_CLICK.clear();
    }
}

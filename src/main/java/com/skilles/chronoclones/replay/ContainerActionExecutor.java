package com.skilles.chronoclones.replay;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

public final class ContainerActionExecutor {

    private ContainerActionExecutor() {}

    public static ActionResult execute(ActionContext ctx, ChronoAction.UseContainer action) {
        ServerLevel level = ctx.level();
        ActionSettings settings = ctx.settings();

        BlockPos localBlock = action.target().localBlock();
        Vec3 worldPoint = action.target().toWorld(ctx.placement().origin(), ctx.placement().facing());
        BlockPos worldPos = BlockPos.containing(worldPoint);

        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(worldPos)) {
            return ActionResult.fail(FailureReason.UNLOADED, localBlock);
        }

        FakePlayer owner = ctx.acquire(worldPoint,
                ctx.placement().facing().toYRot(), 0.0f, ItemStack.EMPTY);
        try {
            Session session = openMenu(ctx, action, worldPoint, owner);
            if (session == null) {
                return ActionResult.fail(FailureReason.NO_MENU, localBlock);
            }
            AbstractContainerMenu menu = session.menu();
            // Slot indices mean nothing outside the menu that produced them.
            if (menu.slots.size() != action.menuSize()) {
                return ActionResult.fail(FailureReason.WRONG_BLOCK, localBlock);
            }

            ContainerCarrier.load(ctx.inventory(), owner, menu, settings);
            try {
                for (int index = 0; index < action.steps().size(); index++) {
                    ActionSettings.StepSettings rule = settings.step(index);
                    if (!rule.enabled()) {
                        continue;
                    }
                    FailureReason refusal = runStep(menu, owner, action.steps().get(index), rule);
                    if (refusal != FailureReason.NONE) {
                        return ActionResult.fail(refusal, localBlock);
                    }
                }
            } finally {
                // In a finally: a mod's slot throwing mid-session must not leave a routine's items inside
                // a fake player nobody can open.
                menu.removed(owner);
                ContainerCarrier.drain(level, ctx.anchorPos(), ctx.inventory(), owner, menu);
                session.close();
            }
            return ActionResult.OK;
        } finally {
            ctx.release(owner);
        }
    }

    private record Session(AbstractContainerMenu menu, Runnable release) {

        static Session of(AbstractContainerMenu menu) {
            return new Session(menu, () -> { });
        }

        void close() {
            release.run();
        }
    }

    /** Built directly: a fake player's openMenu is a no-op, so nothing would be open to click. */
    private static @Nullable Session openMenu(ActionContext ctx, ChronoAction.UseContainer action,
                                              Vec3 worldPoint, FakePlayer owner) {
        ServerLevel level = ctx.level();
        if (action.target() instanceof MenuTarget.Entity target) {
            Entity entity = findEntity(ctx, target, worldPoint);
            return entity == null ? null : openEntityMenu(entity, owner);
        }

        BlockPos worldPos = BlockPos.containing(worldPoint);
        MenuProvider provider = level.getBlockState(worldPos).getMenuProvider(level, worldPos);
        if (provider == null) {
            return null;
        }
        AbstractContainerMenu menu = provider.createMenu(1, owner.getInventory(), owner);
        return menu == null ? null : Session.of(menu);
    }

    private static @Nullable Entity findEntity(ActionContext ctx, MenuTarget.Entity target,
                                               Vec3 worldPoint) {
        TargetRule rule = ctx.target();
        AABB box = Targeting.boxAround(worldPoint, rule);
        List<Entity> candidates = ctx.level().getEntitiesOfClass(Entity.class, box, entity ->
                entity.isAlive()
                        && !(entity instanceof ChronoCloneEntity)
                        && !(entity instanceof Player)
                        && !entity.getUUID().equals(ctx.operator().id())
                        && rule.accepts(entity.getType()));

        return Targeting.choose(candidates, worldPoint, target.expectedType().value(),
                ctx.recordedSubject());
    }

    private static @Nullable Session openEntityMenu(Entity entity, FakePlayer owner) {
        if (entity instanceof Merchant merchant) {
            if (merchant.getTradingPlayer() != null) {
                return null;
            }
            merchant.setTradingPlayer(owner);
            MerchantMenu menu = new MerchantMenu(1, owner.getInventory(), merchant);
            menu.setOffers(merchant.getOffers());
            return new Session(menu, () -> merchant.setTradingPlayer(null));
        }
        if (entity instanceof AbstractHorse horse) {
            return Session.of(new HorseInventoryMenu(1, owner.getInventory(), horse.getInventory(),
                    horse, horse.getInventoryColumns()));
        }
        if (entity instanceof MenuProvider provider) {
            AbstractContainerMenu menu = provider.createMenu(1, owner.getInventory(), owner);
            return menu == null ? null : Session.of(menu);
        }
        return null;
    }

    private static FailureReason runStep(AbstractContainerMenu menu, FakePlayer owner,
                                         SessionStep step, ActionSettings.StepSettings rule) {
        int levels = experienceCost(menu, step);
        if (levels > 0 && owner.experienceLevel < levels && !drinkUpTo(owner, levels)) {
            return FailureReason.NO_EXPERIENCE;
        }

        return switch (step) {
            case SessionStep.Move move -> {
                if (!reachable(menu, move.from()) || (!move.quick() && !reachable(menu, move.to()))) {
                    yield FailureReason.NO_SLOT;
                }
                yield runMove(menu, owner, move, rule)
                        ? FailureReason.NONE
                        : FailureReason.WRONG_BLOCK;
            }
            case SessionStep.RawClick raw -> {
                if (raw.slot() >= menu.slots.size()) {
                    yield FailureReason.WRONG_BLOCK;
                }
                if (raw.slot() >= 0 && !reachable(menu, raw.slot())) {
                    yield FailureReason.NO_SLOT;
                }
                menu.clicked(raw.slot(), raw.button(), raw.input(), owner);
                yield FailureReason.NONE;
            }
            case SessionStep.Button button -> {
                menu.clickMenuButton(owner, button.id());
                yield FailureReason.NONE;
            }
            case SessionStep.Trade trade -> runTrade(menu, trade);
            case SessionStep.Rename rename -> {
                if (menu instanceof AnvilMenu anvil) {
                    anvil.setItemName(rename.text());
                    yield FailureReason.NONE;
                }
                yield FailureReason.WRONG_BLOCK;
            }
        };
    }

    /** An anchor holds every clone's storage in one menu and only the open page is live. */
    private static boolean reachable(AbstractContainerMenu menu, int index) {
        return index >= 0 && index < menu.slots.size() && menu.getSlot(index).isActive();
    }

    /** Asked in advance, because a menu that cannot afford the work simply declines silently. */
    private static int experienceCost(AbstractContainerMenu menu, SessionStep step) {
        if (menu instanceof EnchantmentMenu table && step instanceof SessionStep.Button button) {
            int id = button.id();
            return id >= 0 && id < table.costs.length ? table.costs[id] : 0;
        }
        if (menu instanceof AnvilMenu anvil && step instanceof SessionStep.Move move
                && move.from() == AnvilMenu.RESULT_SLOT) {
            return anvil.getCost();
        }
        return 0;
    }

    /** Consumed rather than thrown: a bottle that has to land would take several ticks. */
    private static boolean drinkUpTo(FakePlayer owner, int levels) {
        while (owner.experienceLevel < levels) {
            int slot = findInInventory(owner, Items.EXPERIENCE_BOTTLE);
            if (slot < 0) {
                return false;
            }
            owner.getInventory().removeItem(slot, 1);
            RandomSource random = owner.level().getRandom();
            owner.giveExperiencePoints(3 + random.nextInt(5) + random.nextInt(5));
        }
        return true;
    }

    private static int findInInventory(FakePlayer owner, Item item) {
        Inventory inventory = owner.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** By what the offer is, never by index: a villager reorders its trades as it levels. */
    private static FailureReason runTrade(AbstractContainerMenu menu, SessionStep.Trade trade) {
        if (!(menu instanceof MerchantMenu merchant)) {
            return FailureReason.WRONG_BLOCK;
        }
        MerchantOffers offers = merchant.getOffers();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            if (!matches(offer, trade)) {
                continue;
            }
            if (offer.isOutOfStock()) {
                return FailureReason.OUT_OF_STOCK;
            }
            merchant.setSelectionHint(index);
            merchant.tryMoveItems(index);
            return FailureReason.NONE;
        }
        return FailureReason.NO_OFFER;
    }

    /** Components on the result, so Mending is not bought as Unbreaking. Counts are ignored
     * because a price moves with demand and the player wanted the trade, not the price. */
    private static boolean matches(MerchantOffer offer, SessionStep.Trade trade) {
        return offer.getCostA().getItem() == trade.costA().getItem()
                && offer.getCostB().getItem() == trade.costB().getItem()
                && ItemStack.isSameItemSameComponents(offer.getResult(), trade.result())
                && offer.getResult().getCount() == trade.result().getCount();
    }

    /**
     * Moves an item as the player did: take, put, and put back whatever was not wanted.
     *
     * <p>Through {@code clicked} rather than by writing slots, so a mod's own slot rules and
     * refusals apply exactly as they do to a player.
     */
    private static boolean runMove(AbstractContainerMenu menu, FakePlayer owner, SessionStep.Move move,
                                   ActionSettings.StepSettings rule) {
        if (move.from() < 0 || move.from() >= menu.slots.size()) {
            return false;
        }
        if (!move.quick() && (move.to() < 0 || move.to() >= menu.slots.size())) {
            return false;
        }

        int from = sourceFor(menu, move, rule.slot());
        if (from < 0) {
            return true;
        }
        if (!rule.allows(menu.getSlot(from).getItem().getItem())) {
            return true;
        }

        if (move.quick()) {
            menu.clicked(from, 0, ContainerInput.QUICK_MOVE, owner);
            return true;
        }

        SessionStep.Amount amount = rule.amountOr(move.observed());
        menu.clicked(from, amount == SessionStep.Amount.HALF ? 1 : 0, ContainerInput.PICKUP, owner);
        menu.clicked(move.to(), amount == SessionStep.Amount.ONE ? 1 : 0,
                ContainerInput.PICKUP, owner);

        if (!menu.getCarried().isEmpty()) {
            menu.clicked(from, 0, ContainerInput.PICKUP, owner);
        }
        return true;
    }

    /**
     * The slot this move takes from, which is the recorded one unless told to look further.
     *
     * <p>A looser rule only looks on the same side of the menu: slot indices say nothing about what
     * they belong to, so searching all of it for a chest's coal would find the clone's own.
     */
    private static int sourceFor(AbstractContainerMenu menu, SessionStep.Move move, SlotRule rule) {
        Item item = move.item().value();
        if (rule.mode() != SlotRule.Mode.ANY && menu.getSlot(move.from()).getItem().is(item)) {
            return move.from();
        }
        if (rule.mode() == SlotRule.Mode.EXACT) {
            return -1;
        }
        Container side = menu.getSlot(move.from()).container;
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).container == side && menu.getSlot(slot).isActive()
                    && menu.getSlot(slot).getItem().is(item)) {
                return slot;
            }
        }
        return -1;
    }
}

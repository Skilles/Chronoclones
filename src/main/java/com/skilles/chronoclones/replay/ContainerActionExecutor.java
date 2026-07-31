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
import com.skilles.chronoclones.registry.ModTags;

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

/**
 * Replaying a container session by opening the block's real menu and clicking it.
 */
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
        // Anchors may not reach into other anchors, for the same reason they may not right-click
        // them: a routine that loots its neighbours is a routine that loots its owner's other farms.
        if (action.target() instanceof MenuTarget.Block
                && level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return ActionResult.fail(FailureReason.BLACKLISTED, localBlock);
        }

        FakePlayer owner = ctx.acquire(worldPoint,
                ctx.placement().facing().toYRot(), 0.0f, ItemStack.EMPTY);
        try {
            Session session = openMenu(ctx, action, worldPoint, owner);
            if (session == null) {
                return ActionResult.fail(FailureReason.NO_MENU, localBlock);
            }
            AbstractContainerMenu menu = session.menu();
            // Slot indices mean nothing outside the menu that produced them, so a differently
            // shaped menu would be clicked at random.
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
                // Returns whatever is on the cursor to the player, then everything the player is
                // holding to the anchor, in a finally, because a mod's slot throwing mid-session
                // must not leave a routine's items inside a fake player nobody can open.
                menu.removed(owner);
                ContainerCarrier.drain(level, ctx.anchorPos(), ctx.inventory(), owner, menu);
                session.close();
            }
            return ActionResult.OK;
        } finally {
            ctx.release(owner);
        }
    }

    /**
     * An open menu, and whatever has to be let go of once it closes.
     */
    private record Session(AbstractContainerMenu menu, Runnable release) {

        static Session of(AbstractContainerMenu menu) {
            return new Session(menu, () -> { });
        }

        void close() {
            release.run();
        }
    }

    /**
     * Opens the menu the session was recorded against.
     *
     * <p>The menu is built directly rather than through the interaction that opened it, because a
     * fake player's {@code openMenu} is a no-op: nothing would ever be open to click.
     */
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

    /**
     * The recorded kind of entity nearest the recorded point, else the nearest thing the rule admits.
     */
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

    /**
     * The menus an entity can carry: a merchant's offers, a mount's saddlebags, or anything that is
     * its own {@link MenuProvider}, which covers chest vehicles and a mod's own entities.
     */
    private static @Nullable Session openEntityMenu(Entity entity, FakePlayer owner) {
        if (entity instanceof Merchant merchant) {
            // A merchant will not trade with two customers at once, and it awards its experience to
            // whoever it thinks it is trading with.
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

    /**
     * One step of a session.
     *
     * @return {@link FailureReason#NONE} if it ran, and otherwise why the session stops here. A step
     *         that names a square this menu does not have means the menu is not the one recorded, so
     *         nothing further should be clicked in it.
     */
    private static FailureReason runStep(AbstractContainerMenu menu, FakePlayer owner,
                                         SessionStep step, ActionSettings.StepSettings rule) {
        int levels = experienceCost(menu, step);
        if (levels > 0 && owner.experienceLevel < levels && !drinkUpTo(owner, levels)) {
            return FailureReason.NO_EXPERIENCE;
        }

        return switch (step) {
            case SessionStep.Move move -> runMove(menu, owner, move, rule)
                    ? FailureReason.NONE
                    // A square this menu does not have means it is not the menu that was recorded.
                    : FailureReason.WRONG_BLOCK;
            case SessionStep.RawClick raw -> {
                if (raw.slot() >= menu.slots.size()) {
                    yield FailureReason.WRONG_BLOCK;
                }
                // The square the player clicked, whatever is in it now.
                menu.clicked(raw.slot(), raw.button(), raw.input(), owner);
                yield FailureReason.NONE;
            }
            // A refused button is not a broken session: a menu that declines one simply does not
            // do that thing, and the steps after it may still have work to do.
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

    /**
     * What this step will charge in levels, before it is attempted.
     *
     * <p>Asked in advance because the menus that charge simply decline when they cannot: an
     * enchantment the clone cannot afford does nothing at all, and nothing says why.
     */
    private static int experienceCost(AbstractContainerMenu menu, SessionStep step) {
        if (menu instanceof EnchantmentMenu table && step instanceof SessionStep.Button button) {
            int id = button.id();
            return id >= 0 && id < table.costs.length ? table.costs[id] : 0;
        }
        // The anvil charges when the work is taken out, not when it is set up.
        if (menu instanceof AnvilMenu anvil && step instanceof SessionStep.Move move
                && move.from() == AnvilMenu.RESULT_SLOT) {
            return anvil.getCost();
        }
        return 0;
    }

    /**
     * Drinks bottles o' enchanting out of the clone's own stock until it can afford the work.
     *
     * <p>Consumed rather than thrown: a bottle that has to land somewhere would make this a thing
     * that happens over several ticks, in the middle of an open menu.
     */
    private static boolean drinkUpTo(FakePlayer owner, int levels) {
        while (owner.experienceLevel < levels) {
            int slot = findInInventory(owner, Items.EXPERIENCE_BOTTLE);
            if (slot < 0) {
                return false;
            }
            owner.getInventory().removeItem(slot, 1);
            // Vanilla's own spread for a thrown bottle: 3 to 11.
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

    /**
     * Selects the offer the recording named, by what it offers.
     *
     * <p>Never by index: a villager's trades reorder as it levels, so the fifth trade of that day is
     * not the same promise as the fifth trade today. Counts are not matched either, because a price
     * moves with demand and reputation and the player wanted the trade, not the price.
     */
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
            // Sold out is worth saying: the payment would go in, no result would come out, and the
            // session would look like it had simply decided not to work today.
            if (offer.isOutOfStock()) {
                return FailureReason.OUT_OF_STOCK;
            }
            merchant.setSelectionHint(index);
            merchant.tryMoveItems(index);
            return FailureReason.NONE;
        }
        // The merchant no longer offers it, so there is nothing to buy and nothing to guess at.
        return FailureReason.NO_OFFER;
    }

    private static boolean matches(MerchantOffer offer, SessionStep.Trade trade) {
        return offer.getCostA().getItem() == trade.costA().getItem()
                && offer.getCostB().getItem() == trade.costB().getItem()
                // Components on the result, so Mending is not bought as Unbreaking.
                && ItemStack.isSameItemSameComponents(offer.getResult(), trade.result())
                && offer.getResult().getCount() == trade.result().getCount();
    }

    /**
     * Moves an item as the player moved it: take, put, and put back whatever was not wanted.
     *
     * <p>Through {@code clicked} rather than by writing the slots, so a mod's own slot rules, its
     * crafting result and its refusals all apply exactly as they do to a player.
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
        // Nothing to take: the same outcome as clicking an empty square, without the clicks. A
        // chest that has not refilled yet is the ordinary case, not a broken routine.
        if (from < 0) {
            return true;
        }
        if (!rule.allows(menu.getSlot(from).getItem().getItem())) {
            return true;
        }

        if (move.quick()) {
            // A shift-click's destination and amount were always the menu's business.
            menu.clicked(from, 0, ContainerInput.QUICK_MOVE, owner);
            return true;
        }

        SessionStep.Amount amount = rule.amountOr(move.observed());
        menu.clicked(from, amount == SessionStep.Amount.HALF ? 1 : 0, ContainerInput.PICKUP, owner);
        menu.clicked(move.to(), amount == SessionStep.Amount.ONE ? 1 : 0,
                ContainerInput.PICKUP, owner);

        // What the destination would not take, or the rest of a stack the move only wanted one of.
        if (!menu.getCarried().isEmpty()) {
            menu.clicked(from, 0, ContainerInput.PICKUP, owner);
        }
        return true;
    }

    /**
     * The square this move takes from, which is the recorded one unless told to look further.
     *
     * <p>A looser rule only ever looks on the same side of the menu: slot indices say nothing about
     * what they belong to, so searching the whole menu for a chest's coal would happily find the
     * clone's own.
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
            if (menu.getSlot(slot).container == side && menu.getSlot(slot).getItem().is(item)) {
                return slot;
            }
        }
        return -1;
    }
}

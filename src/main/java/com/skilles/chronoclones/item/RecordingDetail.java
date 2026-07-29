package com.skilles.chronoclones.item;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The step-by-step reading of a recording, shown while shift is held.
 */
public final class RecordingDetail {

    private RecordingDetail() {}

    /** Beyond this the tooltip is taller than the screen and stops being readable. */
    private static final int MAX_LINES = 24;
    /** Container sessions expand too, but a long one would drown everything else. */
    private static final int MAX_CLICKS = 6;

    public static List<Component> describe(List<TimedAction> actions) {
        List<Component> lines = new ArrayList<>();
        int shown = 0;

        for (TimedAction timed : actions) {
            if (shown >= MAX_LINES) {
                lines.add(Component.translatable("tooltip.chronoclones.detail.more", actions.size() - shown)
                        .withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            lines.add(line(timed));
            shown++;

            if (timed.action() instanceof ChronoAction.UseContainer session) {
                shown += appendSession(lines, session);
            }
        }
        return lines;
    }

    private static Component line(TimedAction timed) {
        String at = String.format("%.1fs", timed.tick() / 20.0f);
        return Component.literal(" ")
                .append(Component.literal(at).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" "))
                .append(describe(timed.action()));
    }

    /** What one action does, for a row that has not been given a name of its own. */
    public static Component summary(ChronoAction action) {
        return describe(action);
    }

    private static Component describe(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> Component.translatable("tooltip.chronoclones.detail.break",
                    name(a.expectedBlock().value().getName()), at(a.localPos()))
                    .withStyle(ChatFormatting.GOLD);
            case ChronoAction.PlaceBlock a -> Component.translatable("tooltip.chronoclones.detail.place",
                    itemName(a.item().value()), at(a.localPos()))
                    .withStyle(ChatFormatting.GREEN);
            case ChronoAction.AttackEntity a -> Component.translatable("tooltip.chronoclones.detail.attack",
                    name(a.expectedType().value().getDescription()), at(a.localPos()))
                    .withStyle(ChatFormatting.RED);
            case ChronoAction.UseOnBlock a -> Component.translatable("tooltip.chronoclones.detail.use_on",
                    itemName(a.item().value()), at(a.localPos()))
                    .withStyle(ChatFormatting.AQUA);
            case ChronoAction.UseItem a -> Component.translatable("tooltip.chronoclones.detail.use",
                    itemName(a.item().value()))
                    .withStyle(ChatFormatting.AQUA);
            case ChronoAction.InteractEntity a -> Component.translatable("tooltip.chronoclones.detail.interact",
                    name(a.expectedType().value().getDescription()), at(a.localPos()))
                    .withStyle(ChatFormatting.AQUA);
            case ChronoAction.UseContainer a -> Component.translatable("tooltip.chronoclones.detail.container",
                    at(a.localPos()), a.clicks().size())
                    .withStyle(ChatFormatting.YELLOW);
        };
    }

    /** Nested lines for one container session: what it brings, then what it does. */
    private static int appendSession(List<Component> lines, ChronoAction.UseContainer session) {
        int added = 0;

        for (ChronoAction.UseContainer.CarrierSlot carried : session.carrier()) {
            lines.add(indent(Component.translatable("tooltip.chronoclones.detail.container.needs",
                            // getHoverName, so a routine wanting a pickaxe named "Tunneler"
                            // says so.
                            carried.stack().getCount(), name(carried.stack().getHoverName()))
                    .withStyle(ChatFormatting.DARK_AQUA)));
            added++;
        }

        int clicks = Math.min(session.clicks().size(), MAX_CLICKS);
        for (int i = 0; i < clicks; i++) {
            lines.add(indent(clickLine(session.clicks().get(i)).withStyle(ChatFormatting.GRAY)));
            added++;
        }
        if (session.clicks().size() > clicks) {
            lines.add(indent(Component.translatable("tooltip.chronoclones.detail.more",
                    session.clicks().size() - clicks).withStyle(ChatFormatting.DARK_GRAY)));
            added++;
        }
        return added;
    }

    /**
     * A click in words.
     */
    private static net.minecraft.network.chat.MutableComponent clickLine(ChronoAction.UseContainer.Click click) {
        String key = switch (click.input()) {
            case PICKUP -> click.button() == 0
                    ? "tooltip.chronoclones.detail.click.pickup_all"
                    : "tooltip.chronoclones.detail.click.pickup_half";
            case QUICK_MOVE -> "tooltip.chronoclones.detail.click.quick_move";
            case SWAP -> "tooltip.chronoclones.detail.click.swap";
            case CLONE -> "tooltip.chronoclones.detail.click.clone";
            case THROW -> "tooltip.chronoclones.detail.click.throw";
            case QUICK_CRAFT -> "tooltip.chronoclones.detail.click.drag";
            case PICKUP_ALL -> "tooltip.chronoclones.detail.click.collect";
        };
        return Component.translatable(key, click.slot());
    }

    private static Component indent(Component line) {
        return Component.literal("   ").append(line);
    }

    /**
     * An item's display name without building a stack.
     */
    private static Component itemName(Item item) {
        return item == Items.AIR
                ? Component.translatable("tooltip.chronoclones.detail.empty_hand")
                : name(Component.translatable(item.getDescriptionId()));
    }

    private static Component name(Component component) {
        return component.copy().withStyle(ChatFormatting.WHITE);
    }

    private static Component at(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component at(Vec3 pos) {
        return at(BlockPos.containing(pos));
    }
}

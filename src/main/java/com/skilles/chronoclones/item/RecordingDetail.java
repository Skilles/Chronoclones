package com.skilles.chronoclones.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class RecordingDetail {

    private RecordingDetail() {}

    private static final int MAX_LINES = 24;
    private static final int MAX_STEPS = 6;

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

    public static Component summary(ChronoAction action) {
        return describe(action);
    }

    public static Component title(TimedAction timed) {
        return timed.settings().hasName()
                ? Component.literal(timed.settings().name())
                : derivedTitle(timed.action(), timed.settings());
    }

    private static Component derivedTitle(ChronoAction action, ActionSettings settings) {
        boolean recorded = settings.recordedSubject();
        boolean typed = !settings.target().filter().isEmpty();

        return switch (action) {
            case ChronoAction.BreakBlock a -> recorded
                    ? name("break", a.expectedBlock().value().getName())
                    : any("break");
            case ChronoAction.PlaceBlock a -> recorded
                    ? name("place", itemName(a.item().value()))
                    : any("place");
            case ChronoAction.AttackEntity a -> typed
                    ? name("attack", a.expectedType().value().getDescription())
                    : any("attack");
            case ChronoAction.InteractEntity a -> typed
                    ? name("interact", a.expectedType().value().getDescription())
                    : any("interact");
            case ChronoAction.UseOnBlock a -> useOnTitle(a, recorded);
            case ChronoAction.UseItem a -> a.item().value() == Items.AIR
                    ? empty("use")
                    : name("use", itemName(a.item().value()));
            case ChronoAction.UseContainer a -> containerTitle(a);
        };
    }

    private static Component useOnTitle(ChronoAction.UseOnBlock action, boolean recorded) {
        boolean holding = action.item().value() != Items.AIR;
        Optional<Holder<Block>> block = recorded ? action.expectedBlock() : Optional.empty();

        if (holding && block.isPresent()) {
            return Component.translatable("gui.chronoclones.editor.name.use_on.on",
                    itemName(action.item().value()).copy(), block.get().value().getName());
        }
        if (holding) {
            return name("use_on", itemName(action.item().value()));
        }
        return block.map(held -> name("use_on", held.value().getName()))
                .orElseGet(() -> empty("use_on"));
    }

    private static Component containerTitle(ChronoAction.UseContainer session) {
        if (session.target() instanceof MenuTarget.Entity entity) {
            return name("container", entity.expectedType().value().getDescription());
        }
        if (session.target() instanceof MenuTarget.Block block && block.expectedBlock().isPresent()) {
            return name("container", block.expectedBlock().get().value().getName());
        }
        return any("container");
    }

    public static Component subtitle(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> at("at", a.localPos());
            case ChronoAction.PlaceBlock a -> at("at", a.localPos());
            case ChronoAction.AttackEntity a -> at("at", BlockPos.containing(a.localPos()));
            case ChronoAction.UseOnBlock a -> at("at", a.localPos());
            case ChronoAction.InteractEntity a -> at("at", BlockPos.containing(a.localPos()));
            case ChronoAction.UseItem a -> Component.translatable(
                    "gui.chronoclones.editor.detail." + a.hand().name().toLowerCase(java.util.Locale.ROOT));
            case ChronoAction.UseContainer a -> containerSubtitle(a);
        };
    }

    private static Component containerSubtitle(ChronoAction.UseContainer session) {
        int steps = session.steps().size();
        String plural = steps == 1 ? "" : "s";
        if (session.target() instanceof MenuTarget.Entity) {
            return Component.translatable("gui.chronoclones.editor.detail.step" + plural, steps);
        }
        return Component.translatable("gui.chronoclones.editor.detail.at_step" + plural,
                at(session.target().localBlock()), steps);
    }

    private static Component name(String verb, Component subject) {
        return Component.translatable("gui.chronoclones.editor.name." + verb, subject.copy());
    }

    private static Component any(String verb) {
        return Component.translatable("gui.chronoclones.editor.name." + verb + ".any");
    }

    private static Component empty(String verb) {
        return Component.translatable("gui.chronoclones.editor.name." + verb + ".empty");
    }

    private static Component at(String key, BlockPos pos) {
        return Component.translatable("gui.chronoclones.editor.detail." + key, at(pos));
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
            case ChronoAction.UseContainer a -> containerLine(a).withStyle(ChatFormatting.YELLOW);
        };
    }

    private static MutableComponent containerLine(ChronoAction.UseContainer session) {
        if (session.target() instanceof MenuTarget.Entity entity) {
            return Component.translatable("tooltip.chronoclones.detail.container.entity",
                    name(entity.expectedType().value().getDescription()), session.steps().size());
        }
        return Component.translatable("tooltip.chronoclones.detail.container",
                at(session.target().localBlock()), session.steps().size());
    }

    private static int appendSession(List<Component> lines, ChronoAction.UseContainer session) {
        int added = 0;

        for (ChronoAction.UseContainer.CarrierSlot carried : session.carrier()) {
            lines.add(indent(Component.translatable("tooltip.chronoclones.detail.container.needs",
                            carried.stack().getCount(), name(carried.stack().getHoverName()))
                    .withStyle(ChatFormatting.DARK_AQUA)));
            added++;
        }

        int shown = Math.min(session.steps().size(), MAX_STEPS);
        for (int i = 0; i < shown; i++) {
            lines.add(indent(stepLine(session.steps().get(i)).withStyle(ChatFormatting.GRAY)));
            added++;
        }
        if (session.steps().size() > shown) {
            lines.add(indent(Component.translatable("tooltip.chronoclones.detail.more",
                    session.steps().size() - shown).withStyle(ChatFormatting.DARK_GRAY)));
            added++;
        }
        return added;
    }

    public static MutableComponent stepLine(SessionStep step) {
        return switch (step) {
            case SessionStep.Move move -> moveLine(move);
            case SessionStep.RawClick click -> clickLine(click);
            case SessionStep.Button button -> Component.translatable(
                    "tooltip.chronoclones.detail.step.button", button.id());
            case SessionStep.Trade trade -> Component.translatable(
                    "tooltip.chronoclones.detail.step.trade",
                    name(trade.result().getHoverName()), name(trade.costA().getHoverName()));
            case SessionStep.Rename rename -> Component.translatable(
                    "tooltip.chronoclones.detail.step.rename", rename.text());
        };
    }

    private static MutableComponent moveLine(SessionStep.Move move) {
        Component item = itemName(move.item().value());
        if (move.quick()) {
            return Component.translatable("tooltip.chronoclones.detail.step.send", item, move.from());
        }
        String key = switch (move.observed()) {
            case ALL -> "tooltip.chronoclones.detail.step.move";
            case HALF -> "tooltip.chronoclones.detail.step.move_half";
            case ONE -> "tooltip.chronoclones.detail.step.move_one";
        };
        return Component.translatable(key, item, move.from(), move.to());
    }

    private static MutableComponent clickLine(SessionStep.RawClick click) {
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

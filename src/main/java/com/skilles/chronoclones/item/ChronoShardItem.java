package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * The Chrono Shard: a medium that carries a recording between players.
 *
 * <p>Blank until inscribed. Inscribing copies a recording onto it; imprinting from it does not
 * consume it, so one shard can seed many anchors.
 *
 * <p><b>An inscribed shard is fully inspectable before it is imprinted</b>. That is not
 * decoration: on a shared server, handing someone an opaque routine that turns out to mine a shaft
 * under their base is an actual attack, so the tooltip reports the author, the length, exactly what
 * the routine does broken down by action type, and how far it reaches.
 *
 * <p>The shard carries the author; it never carries an owner. Ownership is decided by whoever
 * imprints an anchor with it — see {@code ChronoAnchorBlockEntity} for why that split is
 * security-critical.
 */
public class ChronoShardItem extends Item {

    public ChronoShardItem(Properties properties) {
        super(properties);
    }

    public static boolean isInscribed(ItemStack stack) {
        return stack.has(ModDataComponents.RECORDING.get());
    }

    public static @Nullable Recording recordingOf(ItemStack stack) {
        return stack.get(ModDataComponents.RECORDING.get());
    }

    /** Returns a new inscribed shard carrying {@code recording}. */
    public static ItemStack inscribe(ItemStack blank, Recording recording) {
        ItemStack inscribed = blank.copyWithCount(1);
        inscribed.set(ModDataComponents.RECORDING.get(), recording);
        return inscribed;
    }

    /** Blank shards stack; inscribed ones must not, since each carries distinct data. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return isInscribed(stack) ? 1 : super.getMaxStackSize(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isInscribed(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag flag) {
        Recording recording = recordingOf(stack);
        if (recording == null) {
            adder.accept(Component.translatable("tooltip.chronoclones.shard.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        RecordingTooltips.describe(recording).forEach(adder);
    }
}

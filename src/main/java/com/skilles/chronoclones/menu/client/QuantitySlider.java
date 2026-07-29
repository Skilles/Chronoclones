package com.skilles.chronoclones.menu.client;

import java.util.function.IntConsumer;

import com.skilles.chronoclones.recording.ActionSettings.QuantityRule;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * A ceiling on what a container session may carry, in items.
 *
 * <p>Zero is the bottom of the scale and means no ceiling at all, because "as much as the clone is
 * holding" is the default and a slider needs somewhere to put it.
 */
final class QuantitySlider extends AbstractSliderButton {

    /** A double chest's worth: past this a cap is the same as no cap. */
    private static final int MAX = 64;

    private final IntConsumer onChange;

    QuantitySlider(int x, int y, int width, int height, QuantityRule rule, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(),
                Math.clamp(capOf(rule) / (double) MAX, 0.0, 1.0));
        this.onChange = onChange;
        updateMessage();
    }

    private static int capOf(QuantityRule rule) {
        return rule.mode() == QuantityRule.Mode.ANY ? 0 : rule.count();
    }

    private int cap() {
        return (int) Math.round(value * MAX);
    }

    @Override
    protected void updateMessage() {
        setMessage(cap() <= 0
                ? Component.translatable("gui.chronoclones.editor.quantity.any")
                : Component.translatable("gui.chronoclones.editor.quantity", cap()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(cap());
    }

    @Override
    public void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}

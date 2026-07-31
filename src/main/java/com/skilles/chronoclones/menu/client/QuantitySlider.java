package com.skilles.chronoclones.menu.client;

import java.util.function.IntConsumer;

import com.skilles.chronoclones.recording.ActionSettings.QuantityRule;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * A ceiling on what a container session may carry in, in items.
 *
 * <p>Snapped to a ladder rather than free-running. The old slider ran from nothing to sixty-four on
 * the assumption that a stack was as good as no limit -- but a session lends from every square the
 * clone owns, not from one, so a cap of sixty-four and no cap at all are genuinely different
 * settings and there was no way to ask for anything in between them and unlimited.
 */
final class QuantitySlider extends FlatSlider {

    /**
     * The choices, from no ceiling upwards.
     *
     * <p>Zero is the bottom of the scale and means no ceiling at all, because that is the default
     * and a slider needs somewhere to put it.
     */
    private static final int[] STOPS = {0, 1, 8, 16, 32, 64, 128, 256};

    private final IntConsumer onChange;

    QuantitySlider(Font font, int x, int y, int width, int height, QuantityRule rule,
                   IntConsumer onChange) {
        super(font, x, y, width, height, positionOf(capOf(rule)));
        this.onChange = onChange;
        updateMessage();
    }

    private static int capOf(QuantityRule rule) {
        return rule.mode() == QuantityRule.Mode.ANY ? 0 : rule.count();
    }

    /** Where on the bar a cap sits: the nearest stop at or below it, so a saved value keeps. */
    private static double positionOf(int cap) {
        int index = 0;
        for (int stop = 0; stop < STOPS.length; stop++) {
            if (STOPS[stop] <= cap) {
                index = stop;
            }
        }
        return index / (double) (STOPS.length - 1);
    }

    private int cap() {
        int index = (int) Math.round(value * (STOPS.length - 1));
        return STOPS[Math.clamp(index, 0, STOPS.length - 1)];
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
}

package com.skilles.chronoclones.menu.client;

import java.util.function.DoubleConsumer;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class RadiusSlider extends FlatSlider {

    private final DoubleConsumer onChange;
    private final int maxRadius;

    RadiusSlider(Font font, int x, int y, int width, int height, TargetRule rule,
                 DoubleConsumer onChange) {
        super(font, x, y, width, height,
                Math.clamp(rule.radius() / (double) ChronoclonesConfig.maxRadius(), 0.0, 1.0));
        this.onChange = onChange;
        this.maxRadius = ChronoclonesConfig.maxRadius();
        updateMessage();
    }

    private double radius() {
        return Math.round(value * maxRadius * 2.0) / 2.0;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("gui.chronoclones.editor.radius", radius()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(radius());
    }
}

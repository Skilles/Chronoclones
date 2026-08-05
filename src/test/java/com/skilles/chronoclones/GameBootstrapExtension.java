package com.skilles.chronoclones;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Boots the vanilla registries before any test class runs. On NeoForge the FML test launcher
 * has already done this and both calls are guarded no-ops; on Fabric, fabric-loader-junit only
 * starts the loader, so the game bootstrap is on us.
 *
 * <p>Reflective on purpose: this extension may load outside the game classloader (Fabric's
 * tests run inside Knot while JUnit machinery stays outside), so the bootstrap must be invoked
 * on whichever loader owns the test class.
 */
public class GameBootstrapExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        //? if forge {
        /*// Forge's bootstrap hooks post bus events that need the FML transformer, which a
        // plain-JVM test run does not have. The registries are built and frozen before the
        // hook explodes, so swallow it and finish vanilla's own tail piece: the state id map.
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (RuntimeException | Error halted) {
            if (net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size() == 0) {
                for (net.minecraft.world.level.block.Block block :
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                    block.getStateDefinition().getPossibleStates()
                            .forEach(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY::add);
                }
            }
        }
        *///?} else {
        ClassLoader gameLoader = context.getRequiredTestClass().getClassLoader();
        Class.forName("net.minecraft.SharedConstants", true, gameLoader)
                .getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap", true, gameLoader)
                .getMethod("bootStrap").invoke(null);
        //?}
    }
}

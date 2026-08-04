package com.skilles.chronoclones;

import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.registry.ModBlocks;
import com.skilles.chronoclones.registry.ModCreativeTabs;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.registry.ModEntities;
import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.registry.ModMenus;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** Loader-neutral mod core; each loader's entrypoint lives under {@code platform}. */
public final class Chronoclones {

    public static final String MODID = "chronoclones";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Runs every registry class's static registrations, in dependency order. */
    public static void init() {
        ModBlocks.init();
        ModItems.init();
        ModDataComponents.init();
        ModCreativeTabs.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModMenus.init();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private Chronoclones() {}
}

package com.skilles.chronoclones;

import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.registry.ModBlocks;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.registry.ModEntities;
import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.registry.ModMenus;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Chronoclones.MODID)
public class Chronoclones {
    public static final String MODID = "chronoclones";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Chronoclones(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ChronoclonesConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}

package com.skilles.chronoclones.registry;

import java.util.Set;
import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES =
            Registrar.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Chronoclones.MODID);

    public static final Supplier<BlockEntityType<ChronoAnchorBlockEntity>> CHRONO_ANCHOR =
            //? if >=26 {
            BLOCK_ENTITIES.register("chrono_anchor", () -> new BlockEntityType<>(
                    ChronoAnchorBlockEntity::new,
                    Set.of(ModBlocks.CHRONO_ANCHOR.get())));
            //?} else {
            /*BLOCK_ENTITIES.register("chrono_anchor", () -> BlockEntityType.Builder.of(
                    ChronoAnchorBlockEntity::new,
                    ModBlocks.CHRONO_ANCHOR.get()).build(null));
            *///?}

    public static void init() {}

    private ModBlockEntities() {}
}

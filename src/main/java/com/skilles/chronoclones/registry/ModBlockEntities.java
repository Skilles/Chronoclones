package com.skilles.chronoclones.registry;

import java.util.Set;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Chronoclones.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChronoAnchorBlockEntity>> CHRONO_ANCHOR =
            BLOCK_ENTITIES.register("chrono_anchor", () -> new BlockEntityType<>(
                    ChronoAnchorBlockEntity::new,
                    Set.of(ModBlocks.CHRONO_ANCHOR.get())));

    private ModBlockEntities() {}
}

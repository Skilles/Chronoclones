package com.skilles.chronoclones.registry;

import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlock;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {

    public static final Registrar<Block> BLOCKS = Registrar.create(BuiltInRegistries.BLOCK, Chronoclones.MODID);

    public static final Supplier<ChronoAnchorBlock> CHRONO_ANCHOR = BLOCKS.register(
            "chrono_anchor",
            () -> new ChronoAnchorBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, Chronoclones.id("chrono_anchor")))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.0f, 9.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ChronoAnchorBlock.ACTIVE) ? 7 : 0)));

    public static void init() {}

    private ModBlocks() {}
}

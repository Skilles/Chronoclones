package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Chronoclones.MODID);

    public static final DeferredBlock<ChronoAnchorBlock> CHRONO_ANCHOR = BLOCKS.registerBlock(
            "chrono_anchor",
            ChronoAnchorBlock::new,
            (BlockBehaviour.Properties props) -> props
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.0f, 9.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ChronoAnchorBlock.ACTIVE) ? 7 : 0));

    private ModBlocks() {}
}

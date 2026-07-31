package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.entity.ChronoCloneEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Chronoclones.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ChronoCloneEntity>> CHRONO_GHOST =
            ENTITY_TYPES.register("chrono_clone", () -> EntityType.Builder
                    .<ChronoCloneEntity>of(ChronoCloneEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .noSave()
                    .fireImmune()
                    .updateInterval(1)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Chronoclones.id("chrono_clone"))));

    private ModEntities() {}
}

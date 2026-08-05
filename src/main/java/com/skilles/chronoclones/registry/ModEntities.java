package com.skilles.chronoclones.registry;

import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {

    public static final Registrar<EntityType<?>> ENTITY_TYPES =
            Registrar.create(BuiltInRegistries.ENTITY_TYPE, Chronoclones.MODID);

    public static final Supplier<EntityType<ChronoCloneEntity>> CHRONO_GHOST =
            ENTITY_TYPES.register("chrono_clone", () -> EntityType.Builder
                    .<ChronoCloneEntity>of(ChronoCloneEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .noSave()
                    .fireImmune()
                    .updateInterval(1)
                    .clientTrackingRange(8)
                    //? if >=26 {
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Chronoclones.id("chrono_clone"))));
                    //?} else {
                    /*.build(Chronoclones.id("chrono_clone").toString()));
                    *///?}

    public static void init() {}

    private ModEntities() {}
}

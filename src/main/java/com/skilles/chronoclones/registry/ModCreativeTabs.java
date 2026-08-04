package com.skilles.chronoclones.registry;

import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

public final class ModCreativeTabs {

    public static final Registrar<CreativeModeTab> TABS =
            Registrar.create(BuiltInRegistries.CREATIVE_MODE_TAB, Chronoclones.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chronoclones"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.CHRONO_ANCHOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CHRONO_ANCHOR.get());
                        output.accept(ModItems.CHRONO_RECORDER.get());
                        output.accept(ModItems.CHRONO_SHARD.get());
                        output.accept(ModItems.CHRONO_SPLITTER.get());
                        output.accept(ModItems.CHRONO_ACCELERATOR.get());
                        output.accept(ModItems.CHRONO_GOGGLES.get());
                        output.accept(ModItems.CREATIVE_CHARGE_CELL.get());
                    })
                    .build());

    public static void init() {}

    private ModCreativeTabs() {}
}

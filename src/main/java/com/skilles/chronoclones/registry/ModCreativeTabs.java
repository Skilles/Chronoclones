package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Chronoclones.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chronoclones"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.CHRONO_ANCHOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CHRONO_ANCHOR.get());
                        output.accept(ModItems.CHRONO_RECORDER.get());
                        output.accept(ModItems.CHRONO_SPLITTER.get());
                        output.accept(ModItems.CHRONO_ACCELERATOR.get());
                        output.accept(ModItems.CHRONO_FOCUS.get());
                        output.accept(ModItems.CHRONO_LENS.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}

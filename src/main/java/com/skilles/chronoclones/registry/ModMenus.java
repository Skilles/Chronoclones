package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, Chronoclones.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ChronoAnchorMenu>> CHRONO_ANCHOR =
            MENUS.register("chrono_anchor", () -> IMenuTypeExtension.create(ChronoAnchorMenu::new));

    private ModMenus() {}
}

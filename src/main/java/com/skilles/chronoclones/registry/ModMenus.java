package com.skilles.chronoclones.registry;

import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.platform.AnchorMenus;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

    public static final Registrar<MenuType<?>> MENUS =
            Registrar.create(BuiltInRegistries.MENU, Chronoclones.MODID);

    public static final Supplier<MenuType<ChronoAnchorMenu>> CHRONO_ANCHOR =
            MENUS.register("chrono_anchor", AnchorMenus::anchorMenuType);

    public static void init() {}

    private ModMenus() {}
}

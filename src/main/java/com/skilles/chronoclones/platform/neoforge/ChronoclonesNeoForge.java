//? if neoforge {
package com.skilles.chronoclones.platform.neoforge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.platform.Registrar;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Chronoclones.MODID)
public class ChronoclonesNeoForge {

    public ChronoclonesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Chronoclones.init();
        Registrar.registerAllTo(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ChronoclonesConfig.SPEC);
    }
}
//?}

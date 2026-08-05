//? if forge {
/*package com.skilles.chronoclones.platform.forge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@net.minecraftforge.fml.common.Mod(Chronoclones.MODID)
public class ChronoclonesForge {

    public ChronoclonesForge() {
        // The same JSON-backed config the Fabric build reads; Forge's TOML spec stays unused.
        ChronoclonesConfig.loadOrCreate(
                FMLPaths.CONFIGDIR.get().resolve(Chronoclones.MODID + ".json"));

        Chronoclones.init();
        Registrar.registerAllTo(FMLJavaModLoadingContext.get().getModEventBus());
        ForgeNetwork.register();
    }
}
*///?}

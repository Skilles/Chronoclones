//? if neoforge {
package com.skilles.chronoclones.platform.neoforge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.ChronoclonesClientInit;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Chronoclones.MODID, dist = Dist.CLIENT)
public class ChronoclonesClientNeoForge {

    public ChronoclonesClientNeoForge(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        ChronoclonesClientInit.init();
    }
}
//?}

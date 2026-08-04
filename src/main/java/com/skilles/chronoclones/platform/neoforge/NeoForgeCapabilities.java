package com.skilles.chronoclones.platform.neoforge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

@EventBusSubscriber(modid = Chronoclones.MODID)
public final class NeoForgeCapabilities {

    private NeoForgeCapabilities() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.CHRONO_ANCHOR.get(),
                (anchor, side) -> new WorldlyContainerWrapper(anchor.getExternalInventory(), side));
    }
}

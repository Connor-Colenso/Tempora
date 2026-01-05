package com.colen.tempora;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.Tempora.renderingErrorBlock;
import static com.colen.tempora.config.Config.synchronizeConfiguration;

import com.colen.tempora.events.OnWorldLoad;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.blocks.RenderingErrorItemBlock;
import com.colen.tempora.commands.TemporaUndoRanged;
import com.colen.tempora.items.TemporaWand;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.networking.PacketShowRegionInWorld;
import com.colen.tempora.networking.PacketTimeZone;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Tempora.config = new Configuration(event.getSuggestedConfigurationFile());
        GameRegistry.registerItem(new TemporaWand(), "admin_wand");
        GameRegistry.registerBlock(renderingErrorBlock, RenderingErrorItemBlock.class, "renderingErrorBlock");
    }

    public void init(FMLInitializationEvent event) {

        TemporaUndoRanged.MAX_RANGE = Tempora.config.getInt(
            "Command Config",
            "Undo ranged max distance",
            64,
            5,
            Integer.MAX_VALUE,
            "Tempora undo max range, in blocks. Recommended to keep low, as this will get exponentially more expensive, the wider the range.");

        NETWORK.registerMessage(PacketTimeZone.Handler.class, PacketTimeZone.class, 0, Side.SERVER);
        NETWORK.registerMessage(
            PacketShowRegionInWorld.RegionMsg.Handler.class,
            PacketShowRegionInWorld.RegionMsg.class,
            1,
            Side.CLIENT);

        TemporaEvents.registerAll();

        // Each logger handles their own config settings.
        for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            logger.handleCustomLoggerConfig(Tempora.config);
            logger.genericConfig(Tempora.config);
        }

        // After all config handling is done.
        if (Tempora.config.hasChanged()) {
            Tempora.config.save();
        }

        MinecraftForge.EVENT_BUS.register(new OnWorldLoad());

        synchronizeConfiguration(Tempora.config);
    }

    public void postInit(FMLPostInitializationEvent event) {

    }
}

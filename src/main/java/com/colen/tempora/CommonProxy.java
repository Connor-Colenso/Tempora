package com.colen.tempora;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.Tempora.renderingErrorBlock;
import static com.colen.tempora.config.Config.synchronizeConfiguration;

import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.networking.handlers.PacketSendUUIDHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.blocks.RenderingErrorItemBlock;
import com.colen.tempora.chat.TemporaChatRegistry;
import com.colen.tempora.commands.TemporaUndoRangedCommand;
import com.colen.tempora.events.OnWorldLoad;
import com.colen.tempora.items.TemporaWand;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericRenderEventPacketHandler;
import com.colen.tempora.loggers.generic.RenderEventPacket;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;

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

        TemporaChatRegistry.register();
    }

    public void init(FMLInitializationEvent event) {

        TemporaUndoRangedCommand.MAX_RANGE = Tempora.config.getInt(
            "Command Config",
            "Undo ranged max distance",
            64,
            5,
            Integer.MAX_VALUE,
            "Tempora undo max range, in blocks. Recommended to keep low, as this will get exponentially more expensive, the wider the range.");

        NETWORK.registerMessage(GenericRenderEventPacketHandler.class, RenderEventPacket.class, 1, Side.CLIENT);

        NETWORK.registerMessage(
            PacketShowRegionInWorld.RegionMsg.Handler.class,
            PacketShowRegionInWorld.RegionMsg.class,
            2,
            Side.CLIENT);

        NETWORK.registerMessage(
            PacketSendUUIDHandler.class,
            PacketRemoveRegionFromClient.class,
            3,
            Side.CLIENT
        );

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

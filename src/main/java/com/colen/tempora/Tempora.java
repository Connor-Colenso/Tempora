package com.colen.tempora;

import com.colen.tempora.commands.ExplodeCommand;
import net.minecraft.block.Block;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.colen.tempora.blocks.RenderingErrorBlock;
import com.colen.tempora.commands.TemporaTpCommand;
import com.colen.tempora.commands.CreateRegionCommand;
import com.colen.tempora.commands.HomeChunkCommand;
import com.colen.tempora.commands.ListRegionsCommand;
import com.colen.tempora.commands.QueryEventsCommand;
import com.colen.tempora.commands.QuerySQLCommand;
import com.colen.tempora.commands.RemoveRegionCommand;
import com.colen.tempora.commands.TemporaUndoCommand;
import com.colen.tempora.commands.TemporaUndoRangedCommand;
import com.colen.tempora.loggers.block_change.RegionRegistry;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@SuppressWarnings("unused")
@Mod(modid = Tempora.MODID, version = Tags.VERSION, name = Tempora.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class Tempora {

    public static final String MODID = "tempora";
    public static final String MODNAME = "Tempora";

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.colen.tempora.ClientProxy", serverSide = "com.colen.tempora.CommonProxy")
    public static CommonProxy proxy;

    public static Configuration config;

    public static Block renderingErrorBlock = new RenderingErrorBlock();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverInit(FMLServerStartingEvent event) {
        if (TemporaUtils.shouldTemporaRun()) {
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                logger.registerEvent();
            }
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        registerNewCommands(event);

        if (TemporaUtils.shouldTemporaRun() && TemporaUtils.isServerSide()) {
            GenericPositionalLogger.onServerStart();
        }

        RegionRegistry.loadNow();
    }

    private void registerNewCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new QueryEventsCommand());
        event.registerServerCommand(new QuerySQLCommand());
        event.registerServerCommand(new HomeChunkCommand());

        event.registerServerCommand(new CreateRegionCommand());
        event.registerServerCommand(new ListRegionsCommand());
        event.registerServerCommand(new RemoveRegionCommand());

        event.registerServerCommand(new TemporaUndoCommand());
        event.registerServerCommand(new TemporaUndoRangedCommand());
        event.registerServerCommand(new TemporaTpCommand());
        event.registerServerCommand(new ExplodeCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (TemporaUtils.isServerSide()) {
            GenericPositionalLogger.onServerClose();
            RegionRegistry.saveIfDirty();
        }
    }
}

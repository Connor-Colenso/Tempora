package com.myname.mymodid;

import static com.myname.mymodid.Config.synchronizeConfiguration;
import static com.myname.mymodid.Tags.MODID;

import com.myname.mymodid.PositionalEvents.Loggers.BlockPlaceLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.myname.mymodid.Commands.HeatMap.HeatMapCommand;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacket;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacketHandler;
import com.myname.mymodid.PositionalEvents.Commands.QueryEventsCommand;
import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacket;
import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacketHandler;
import com.myname.mymodid.Commands.TrackPlayer.PlayerTrackerRenderer;
import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerCommand;
import com.myname.mymodid.Items.TemporaScannerItem;
import com.myname.mymodid.PositionalEvents.Loggers.BlockBreak.BlockBreakLogger;
import com.myname.mymodid.PositionalEvents.Loggers.CommandLogger;
import com.myname.mymodid.PositionalEvents.Loggers.EntityDeathLogger;
import com.myname.mymodid.PositionalEvents.Loggers.EntityPositionLogger;
import com.myname.mymodid.PositionalEvents.Loggers.EntitySpawnLogger;
import com.myname.mymodid.PositionalEvents.Loggers.ExplosionLogger;
import com.myname.mymodid.PositionalEvents.Loggers.GenericPositionalLogger;
import com.myname.mymodid.PositionalEvents.Loggers.ItemUseLogger;
import com.myname.mymodid.PositionalEvents.Loggers.PlayerMovementLogger;
import com.myname.mymodid.Rendering.RenderInWorldDispatcher;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

@SuppressWarnings("unused")
@Mod(modid = MODID, version = Tags.VERSION, name = Tags.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class Tempora {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.myname.mymodid.ClientProxy", serverSide = "com.myname.mymodid.CommonProxy")
    public static CommonProxy proxy;

    private static Configuration config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        GameRegistry.registerItem(new TemporaScannerItem(), "tempora_scanner");
        Tempora.LOG.info("I am " + Tags.MODNAME + " at version " + Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(PlayerPositionPacketHandler.class, PlayerPositionPacket.class, 0, Side.CLIENT);
        NETWORK.registerMessage(HeatMapPacketHandler.class, HeatMapPacket.class, 1, Side.CLIENT);

        // This must happen before we start registering events.
        synchronizeConfiguration(config);

        if (TemporaUtils.shouldTemporaRun()) {
            new BlockBreakLogger();
            new BlockPlaceLogger();
            new ExplosionLogger();
            new ItemUseLogger();
            new PlayerMovementLogger();
            new CommandLogger();
            new EntityPositionLogger();
            new EntityDeathLogger();
            new EntitySpawnLogger();
        }

        // Each logger handles their own config settings.
        for (GenericPositionalLogger logger : GenericPositionalLogger.loggerList) {
            logger.handleConfig(config);
        }

        // After all config handling is done.
        if (config.hasChanged()) {
            config.save();
        }

        if (TemporaUtils.isClientSide()) {
            MinecraftForge.EVENT_BUS.register(new RenderInWorldDispatcher());
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {}

    @Mod.EventHandler
    public void serverInit(FMLServerStartingEvent event) {
        // Only register this on the server side. We do it here because in SP, preInit etc only runs for the client.

        if (TemporaUtils.shouldTemporaRun()) {
            for (GenericPositionalLogger logger : GenericPositionalLogger.loggerList) {
                logger.registerEvent();
            }
        }

    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        registerNewCommands(event);

        if (TemporaUtils.shouldTemporaRun()) {
            GenericPositionalLogger.onServerStart();
        }
    }

    private void registerNewCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new QueryEventsCommand());
        event.registerServerCommand(new TrackPlayerCommand());
        event.registerServerCommand(new HeatMapCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (TemporaUtils.isServerSide()) {
            PlayerTrackerRenderer.clearBuffer();
            GenericPositionalLogger.onServerClose();
        }
    }
}

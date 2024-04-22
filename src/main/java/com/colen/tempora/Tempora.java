package com.colen.tempora;

import static com.colen.tempora.Config.Config.synchronizeConfiguration;

import com.colen.tempora.Items.TemporaWand;
import com.colen.tempora.PositionalEvents.Loggers.PlayerInteractWithInventory.PlayerInteractWithInventoryLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.colen.tempora.Commands.HeatMap.HeatMapCommand;
import com.colen.tempora.Commands.HeatMap.Network.HeatMapPacket;
import com.colen.tempora.Commands.HeatMap.Network.HeatMapPacketHandler;
import com.colen.tempora.Commands.TrackPlayer.Network.PlayerPositionPacket;
import com.colen.tempora.Commands.TrackPlayer.Network.PlayerPositionPacketHandler;
import com.colen.tempora.Commands.TrackPlayer.PlayerTrackerRenderer;
import com.colen.tempora.Commands.TrackPlayer.TrackPlayerCommand;
import com.colen.tempora.Items.TemporaScannerItem;
import com.colen.tempora.PositionalEvents.Commands.QueryEventsCommand;
import com.colen.tempora.PositionalEvents.Loggers.BlockBreak.BlockBreakLogger;
import com.colen.tempora.PositionalEvents.Loggers.BlockPlace.BlockPlaceLogger;
import com.colen.tempora.PositionalEvents.Loggers.Command.CommandLogger;
import com.colen.tempora.PositionalEvents.Loggers.EntityDeath.EntityDeathLogger;
import com.colen.tempora.PositionalEvents.Loggers.EntityPosition.EntityPositionLogger;
import com.colen.tempora.PositionalEvents.Loggers.EntitySpawn.EntitySpawnLogger;
import com.colen.tempora.PositionalEvents.Loggers.Explosion.ExplosionLogger;
import com.colen.tempora.PositionalEvents.Loggers.Generic.GenericPositionalLogger;
import com.colen.tempora.PositionalEvents.Loggers.GenericPacket;
import com.colen.tempora.PositionalEvents.Loggers.ItemUse.ItemUseLogger;
import com.colen.tempora.PositionalEvents.Loggers.PlayerMovement.PlayerMovementLogger;
import com.colen.tempora.Rendering.RenderInWorldDispatcher;

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
@Mod(modid = Tempora.MODID, version = Tags.VERSION, name = Tempora.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class Tempora {

    public static final String MODID = "tempora";
    public static final String MODNAME = "Tempora";

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.colen.tempora.ClientProxy", serverSide = "com.colen.tempora.CommonProxy")
    public static CommonProxy proxy;

    private static Configuration config;
    public static PlayerInteractWithInventoryLogger playerInteractionLogger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        GameRegistry.registerItem(new TemporaScannerItem(), "tempora_scanner");
        GameRegistry.registerItem(new TemporaWand(), "admin_wand");
        Tempora.LOG.info("I am " + Tempora.MODNAME + " at version " + Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(PlayerPositionPacketHandler.class, PlayerPositionPacket.class, 0, Side.CLIENT);
        NETWORK.registerMessage(HeatMapPacketHandler.class, HeatMapPacket.class, 1, Side.CLIENT);

        NETWORK.registerMessage(GenericPacket.ClientMessageHandler.class, GenericPacket.class, 11, Side.CLIENT);

        // This must happen before we start registering events.
        synchronizeConfiguration(config);

        if (TemporaUtils.shouldTemporaRun()) {
            new BlockBreakLogger();
            new BlockPlaceLogger();
            new ExplosionLogger();
            new ItemUseLogger();
            new PlayerMovementLogger();
            playerInteractionLogger = new PlayerInteractWithInventoryLogger();
            new CommandLogger();
            new EntityPositionLogger();
            new EntityDeathLogger();
            new EntitySpawnLogger();
        }

        // Each logger handles their own config settings.
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
            logger.handleConfig(config);
            logger.handleOldDataConfig(config);
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
            for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
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

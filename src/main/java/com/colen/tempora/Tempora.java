package com.colen.tempora;

import static com.colen.tempora.config.Config.synchronizeConfiguration;

import com.colen.tempora.logging.loggers.block_change_logger.BlockChangeLogger;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.colen.tempora.items.TemporaWand;
import com.colen.tempora.logging.commands.QueryEventsCommand;
import com.colen.tempora.logging.loggers.GenericPacket;
import com.colen.tempora.logging.loggers.player_block_break.PlayerBlockBreakLogger;
import com.colen.tempora.logging.loggers.block_place.BlockPlaceLogger;
import com.colen.tempora.logging.loggers.command.CommandLogger;
import com.colen.tempora.logging.loggers.entity_death.EntityDeathLogger;
import com.colen.tempora.logging.loggers.entity_position.EntityPositionLogger;
import com.colen.tempora.logging.loggers.entity_spawn.EntitySpawnLogger;
import com.colen.tempora.logging.loggers.explosion.ExplosionLogger;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.item_use.ItemUseLogger;
import com.colen.tempora.logging.loggers.player_interact_with_inventory.PlayerInteractWithInventoryLogger;
import com.colen.tempora.logging.loggers.player_movement.PlayerMovementLogger;

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

    public static PlayerInteractWithInventoryLogger playerInteractWithInventoryLogger;
    public static BlockChangeLogger blockChangeLogger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        GameRegistry.registerItem(new TemporaWand(), "admin_wand");
        Tempora.LOG.info("I am " + Tempora.MODNAME + " at version " + Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(GenericPacket.ClientMessageHandler.class, GenericPacket.class, 11, Side.CLIENT);

        // This must happen before we start registering events.
        synchronizeConfiguration(config);

        if (TemporaUtils.shouldTemporaRun()) {
            new PlayerBlockBreakLogger();
            new BlockPlaceLogger();
            new ExplosionLogger();
            new ItemUseLogger();
            new PlayerMovementLogger();
            playerInteractWithInventoryLogger = new PlayerInteractWithInventoryLogger();
            blockChangeLogger = new BlockChangeLogger();
            new CommandLogger();
            new EntityPositionLogger();
            new EntityDeathLogger();
            new EntitySpawnLogger();
        }

        // Each logger handles their own config settings.
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
            logger.handleCustomLoggerConfig(config);
            logger.genericConfig(config);
        }

        // After all config handling is done.
        if (config.hasChanged()) {
            config.save();
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
                logger.removeOldDatabaseData();
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
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (TemporaUtils.isServerSide()) {
            GenericPositionalLogger.onServerClose();
        }
    }
}

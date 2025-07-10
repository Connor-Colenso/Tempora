package com.colen.tempora;

import static com.colen.tempora.config.Config.synchronizeConfiguration;

import com.colen.tempora.commands.HomeChunkCommand;
import com.colen.tempora.commands.ListRegionsCommand;
import com.colen.tempora.commands.QuerySQLCommand;
import com.colen.tempora.loggers.block_change.BlockChangePacketHandler;
import com.colen.tempora.loggers.command.CommandPacketHandler;
import com.colen.tempora.loggers.entity_death.EntityDeathPacketHandler;
import com.colen.tempora.loggers.entity_position.EntityPositionPacketHandler;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnPacketHandler;
import com.colen.tempora.loggers.explosion.ExplosionPacketHandler;
import com.colen.tempora.networking.PacketShowEventInWorld;
import com.colen.tempora.networking.PacketShowRegionInWorld;
import com.colen.tempora.rendering.RenderEventsInWorld;
import com.colen.tempora.rendering.RenderRegionsInWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.colen.tempora.events.PlayerLogin;
import com.colen.tempora.items.TemporaWand;
import com.colen.tempora.commands.CreateRegion;
import com.colen.tempora.commands.QueryEventsCommand;
import com.colen.tempora.commands.RemoveRegion;
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.command.CommandLogger;
import com.colen.tempora.loggers.entity_death.EntityDeathLogger;
import com.colen.tempora.loggers.entity_position.EntityPositionLogger;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnLogger;
import com.colen.tempora.loggers.explosion.ExplosionLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.inventory.InventoryLogger;
import com.colen.tempora.loggers.item_use.ItemUseLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakLogger;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceLogger;
import com.colen.tempora.loggers.player_movement.PlayerMovementLogger;
import com.colen.tempora.networking.PacketTimeZone;

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

    public static InventoryLogger inventoryLogger;
    public static BlockChangeLogger blockChangeLogger;
    public static PlayerBlockBreakLogger playerBlockBreakLogger;
    public static PlayerBlockPlaceLogger playerBlockPlaceLogger;
    public static ExplosionLogger explosionLogger;
    public static ItemUseLogger itemUseLogger;
    public static PlayerMovementLogger playerMovementLogger;
    public static CommandLogger commandLogger;
    public static EntityPositionLogger entityPositionLogger;
    public static EntityDeathLogger entityDeathLogger;
    public static EntitySpawnLogger entitySpawnLogger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        GameRegistry.registerItem(new TemporaWand(), "admin_wand");
        Tempora.LOG.info("I am " + Tempora.MODNAME + " at version " + Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(PacketTimeZone.Handler.class, PacketTimeZone.class, 0, Side.SERVER);
        NETWORK.registerMessage(PacketShowEventInWorld.PosMessage.Handler.class, PacketShowEventInWorld.PosMessage.class, 1, Side.CLIENT);
        NETWORK.registerMessage(PacketShowRegionInWorld.RegionMsg.Handler.class, PacketShowRegionInWorld.RegionMsg.class, 2, Side.CLIENT);
        BlockChangePacketHandler.initPackets();
        CommandPacketHandler.initPackets();
        EntityDeathPacketHandler.initPackets();
        EntityPositionPacketHandler.initPackets();
        EntitySpawnPacketHandler.initPackets();
        ExplosionPacketHandler.initPackets();

        // This must happen before we start registering events.
        synchronizeConfiguration(config);

        if (TemporaUtils.isClientSide()) {
            MinecraftForge.EVENT_BUS.register(new PlayerLogin());

            if (TemporaUtils.shouldTemporaRun()) {
                MinecraftForge.EVENT_BUS.register(new RenderEventsInWorld());
                MinecraftForge.EVENT_BUS.register(new RenderRegionsInWorld());
            }
        }

        if (TemporaUtils.shouldTemporaRun()) {
            playerBlockBreakLogger = new PlayerBlockBreakLogger();
            playerBlockPlaceLogger = new PlayerBlockPlaceLogger();
            explosionLogger = new ExplosionLogger();
            itemUseLogger = new ItemUseLogger();
            playerMovementLogger = new PlayerMovementLogger();
            inventoryLogger = new InventoryLogger();
            blockChangeLogger = new BlockChangeLogger();
            commandLogger = new CommandLogger();
            entityPositionLogger = new EntityPositionLogger();
            entityDeathLogger = new EntityDeathLogger();
            entitySpawnLogger = new EntitySpawnLogger();
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
        event.registerServerCommand(new QuerySQLCommand());
        event.registerServerCommand(new HomeChunkCommand());

        event.registerServerCommand(new CreateRegion());
        event.registerServerCommand(new ListRegionsCommand());
        event.registerServerCommand(new RemoveRegion());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (TemporaUtils.isServerSide()) {
            GenericPositionalLogger.onServerClose();
        }
    }
}

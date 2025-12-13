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
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.block_change.BlockChangePacketHandler;
import com.colen.tempora.loggers.command.CommandLogger;
import com.colen.tempora.loggers.command.CommandPacketHandler;
import com.colen.tempora.loggers.entity_death.EntityDeathLogger;
import com.colen.tempora.loggers.entity_death.EntityDeathPacketHandler;
import com.colen.tempora.loggers.entity_position.EntityPositionLogger;
import com.colen.tempora.loggers.entity_position.EntityPositionPacketHandler;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnLogger;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnPacketHandler;
import com.colen.tempora.loggers.explosion.ExplosionLogger;
import com.colen.tempora.loggers.explosion.ExplosionPacketHandler;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.inventory.InventoryLogger;
import com.colen.tempora.loggers.inventory.InventoryPacketHandler;
import com.colen.tempora.loggers.item_use.ItemUseLogger;
import com.colen.tempora.loggers.item_use.ItemUsePacketHandler;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakPacketHandler;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceLogger;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlacePacketHandler;
import com.colen.tempora.loggers.player_movement.PlayerMovementLogger;
import com.colen.tempora.loggers.player_movement.PlayerMovementPacketHandler;
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

        BlockChangePacketHandler.initPackets();
        CommandPacketHandler.initPackets();
        EntityDeathPacketHandler.initPackets();
        EntityPositionPacketHandler.initPackets();
        EntitySpawnPacketHandler.initPackets();
        ExplosionPacketHandler.initPackets();
        InventoryPacketHandler.initPackets();
        ItemUsePacketHandler.initPackets();
        PlayerBlockBreakPacketHandler.initPackets();
        PlayerBlockPlacePacketHandler.initPackets();
        PlayerMovementPacketHandler.initPackets();

        // We always instantiate the classes, as otherwise the logger may be null when trying to get events from the
        // server.
        Tempora.playerBlockBreakLogger = new PlayerBlockBreakLogger();
        Tempora.playerBlockPlaceLogger = new PlayerBlockPlaceLogger();
        Tempora.explosionLogger = new ExplosionLogger();
        Tempora.itemUseLogger = new ItemUseLogger();
        Tempora.playerMovementLogger = new PlayerMovementLogger();
        Tempora.inventoryLogger = new InventoryLogger();
        Tempora.blockChangeLogger = new BlockChangeLogger();
        Tempora.commandLogger = new CommandLogger();
        Tempora.entityPositionLogger = new EntityPositionLogger();
        Tempora.entityDeathLogger = new EntityDeathLogger();
        Tempora.entitySpawnLogger = new EntitySpawnLogger();

        // Each logger handles their own config settings.
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
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

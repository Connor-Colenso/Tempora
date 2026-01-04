package com.colen.tempora;

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

import static com.colen.tempora.Tempora.blockChangeLogger;
import static com.colen.tempora.Tempora.inventoryLogger;

public class TemporaEvents {

    // Logger names.
    public static final String BLOCK_CHANGE = "BlockChangeLogger";
    public static final String COMMAND = "CommandLogger";
    public static final String ENTITY_DEATH = "EntityDeathLogger";
    public static final String ENTITY_POSITION = "EntityPositionLogger";
    public static final String ENTITY_SPAWN = "EntitySpawnLogger";
    public static final String EXPLOSION = "ExplosionLogger";
    public static final String INVENTORY = "InventoryLogger";
    public static final String ITEM_USE = "ItemUseLogger";
    public static final String PLAYER_BLOCK_BREAK = "PlayerBlockBreakLogger";
    public static final String PLAYER_BLOCK_PLACE = "PlayerBlockPlaceLogger";
    public static final String PLAYER_MOVEMENT = "PlayerMovementLogger";

    public static void registerAll() {

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

        // We reference these from mixins, as they do not rely on events for logging.
        inventoryLogger = new InventoryLogger();
        blockChangeLogger = new BlockChangeLogger();

        TemporaLoggerManager.register(BLOCK_CHANGE, blockChangeLogger);
        TemporaLoggerManager.register(COMMAND, new CommandLogger());
        TemporaLoggerManager.register(ENTITY_DEATH, new EntityDeathLogger());
        TemporaLoggerManager.register(ENTITY_POSITION, new EntityPositionLogger());
        TemporaLoggerManager.register(ENTITY_SPAWN, new EntitySpawnLogger());
        TemporaLoggerManager.register(EXPLOSION, new ExplosionLogger());
        TemporaLoggerManager.register(INVENTORY, inventoryLogger);
        TemporaLoggerManager.register(ITEM_USE, new ItemUseLogger());
        TemporaLoggerManager.register(PLAYER_BLOCK_BREAK, new PlayerBlockBreakLogger());
        TemporaLoggerManager.register(PLAYER_BLOCK_PLACE, new PlayerBlockPlaceLogger());
        TemporaLoggerManager.register(PLAYER_MOVEMENT, new PlayerMovementLogger());
    }
}

package com.colen.tempora;

import com.colen.tempora.loggers.block_change.BlockChangeEventInfo;
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.command.CommandEventInfo;
import com.colen.tempora.loggers.command.CommandLogger;
import com.colen.tempora.loggers.entity_death.EntityDeathEventInfo;
import com.colen.tempora.loggers.entity_death.EntityDeathLogger;
import com.colen.tempora.loggers.entity_position.EntityPositionEventInfo;
import com.colen.tempora.loggers.entity_position.EntityPositionLogger;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnEventInfo;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnLogger;
import com.colen.tempora.loggers.explosion.ExplosionEventInfo;
import com.colen.tempora.loggers.explosion.ExplosionLogger;
import com.colen.tempora.loggers.inventory.InventoryEventInfo;
import com.colen.tempora.loggers.inventory.InventoryLogger;
import com.colen.tempora.loggers.item_use.ItemUseEventInfo;
import com.colen.tempora.loggers.item_use.ItemUseLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakLogger;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceEventInfo;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceLogger;
import com.colen.tempora.loggers.player_movement.PlayerMovementEventInfo;
import com.colen.tempora.loggers.player_movement.PlayerMovementLogger;

public class TemporaEvents {

    // We keep these references they need external access from mixins to log.
    public static InventoryLogger inventoryLogger = new InventoryLogger();
    public static BlockChangeLogger blockChangeLogger = new BlockChangeLogger();

    // Logger names. DO NOT RENAME. This WILL break all past data.
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

        TemporaLoggerManager.register(blockChangeLogger, BlockChangeEventInfo::new);
        TemporaLoggerManager.register(new CommandLogger(), CommandEventInfo::new);
        TemporaLoggerManager.register(new EntityDeathLogger(), EntityDeathEventInfo::new);
        TemporaLoggerManager.register(new EntityPositionLogger(), EntityPositionEventInfo::new);
        TemporaLoggerManager.register(new EntitySpawnLogger(), EntitySpawnEventInfo::new);
        TemporaLoggerManager.register(new ExplosionLogger(), ExplosionEventInfo::new);
        TemporaLoggerManager.register(inventoryLogger, InventoryEventInfo::new);
        TemporaLoggerManager.register(new ItemUseLogger(), ItemUseEventInfo::new);
        TemporaLoggerManager.register(new PlayerBlockBreakLogger(), PlayerBlockBreakEventInfo::new);
        TemporaLoggerManager.register(new PlayerBlockPlaceLogger(), PlayerBlockPlaceEventInfo::new);
        TemporaLoggerManager.register(new PlayerMovementLogger(), PlayerMovementEventInfo::new);
    }
}

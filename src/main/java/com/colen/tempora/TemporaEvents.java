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
import com.colen.tempora.loggers.item_pickup_drop.ItemPickupDropEventInfo;
import com.colen.tempora.loggers.item_pickup_drop.ItemPickupDropLogger;
import com.colen.tempora.loggers.item_use.ItemUseEventInfo;
import com.colen.tempora.loggers.item_use.ItemUseLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakLogger;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceEventInfo;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceLogger;
import com.colen.tempora.loggers.player_position.PlayerPositionEventInfo;
import com.colen.tempora.loggers.player_position.PlayerPositionLogger;
import com.colen.tempora.loggers.tps.TPSEventInfo;
import com.colen.tempora.loggers.tps.TPSLogger;
import com.colen.tempora.loggers.weather.WeatherEventInfo;
import com.colen.tempora.loggers.weather.WeatherLogger;

public class TemporaEvents {

    // We keep these references as mixins and the API (com.colen.tempora.api.TemporaApi) log through them.
    public static BlockChangeLogger blockChangeLogger = new BlockChangeLogger();
    public static CommandLogger commandLogger = new CommandLogger();
    public static EntityDeathLogger entityDeathLogger = new EntityDeathLogger();
    public static EntityPositionLogger entityPositionLogger = new EntityPositionLogger();
    public static EntitySpawnLogger entitySpawnLogger = new EntitySpawnLogger();
    public static ExplosionLogger explosionLogger = new ExplosionLogger();
    public static InventoryLogger inventoryLogger = new InventoryLogger();
    public static ItemPickupDropLogger itemPickupDropLogger = new ItemPickupDropLogger();
    public static ItemUseLogger itemUseLogger = new ItemUseLogger();
    public static PlayerBlockBreakLogger playerBlockBreakLogger = new PlayerBlockBreakLogger();
    public static PlayerBlockPlaceLogger playerBlockPlaceLogger = new PlayerBlockPlaceLogger();
    public static PlayerPositionLogger playerPositionLogger = new PlayerPositionLogger();

    // Global loggers, for events with no position.
    public static TPSLogger tpsLogger = new TPSLogger();
    public static WeatherLogger weatherLogger = new WeatherLogger();

    // Logger names. DO NOT RENAME IT. This WILL break all past data.
    public static final String BLOCK_CHANGE = "BlockChangeLogger";
    public static final String COMMAND = "CommandLogger";
    public static final String ENTITY_DEATH = "EntityDeathLogger";
    public static final String ENTITY_POSITION = "EntityPositionLogger";
    public static final String ENTITY_SPAWN = "EntitySpawnLogger";
    public static final String EXPLOSION = "ExplosionLogger";
    public static final String INVENTORY = "InventoryLogger";
    public static final String ITEM_PICKUP_DROP = "ItemPickupDropLogger";
    public static final String ITEM_USE = "ItemUseLogger";
    public static final String PLAYER_BLOCK_BREAK = "PlayerBlockBreakLogger";
    public static final String PLAYER_BLOCK_PLACE = "PlayerBlockPlaceLogger";
    public static final String PLAYER_POSITION = "PlayerPositionLogger";
    public static final String TPS = "TPSLogger";
    public static final String WEATHER = "WeatherLogger";

    public static void registerAll() {

        TemporaLoggerManager.register(blockChangeLogger, BlockChangeEventInfo::new);
        TemporaLoggerManager.register(commandLogger, CommandEventInfo::new);
        TemporaLoggerManager.register(entityDeathLogger, EntityDeathEventInfo::new);
        TemporaLoggerManager.register(entityPositionLogger, EntityPositionEventInfo::new);
        TemporaLoggerManager.register(entitySpawnLogger, EntitySpawnEventInfo::new);
        TemporaLoggerManager.register(explosionLogger, ExplosionEventInfo::new);
        TemporaLoggerManager.register(inventoryLogger, InventoryEventInfo::new);
        TemporaLoggerManager.register(itemPickupDropLogger, ItemPickupDropEventInfo::new);
        TemporaLoggerManager.register(itemUseLogger, ItemUseEventInfo::new);
        TemporaLoggerManager.register(playerBlockBreakLogger, PlayerBlockBreakEventInfo::new);
        TemporaLoggerManager.register(playerBlockPlaceLogger, PlayerBlockPlaceEventInfo::new);
        TemporaLoggerManager.register(playerPositionLogger, PlayerPositionEventInfo::new);

        TemporaLoggerManager.register(tpsLogger, TPSEventInfo::new);
        TemporaLoggerManager.register(weatherLogger, WeatherEventInfo::new);
    }
}

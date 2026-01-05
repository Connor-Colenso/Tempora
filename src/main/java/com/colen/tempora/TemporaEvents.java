package com.colen.tempora;

import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.block_change.BlockChangeQueueElement;
import com.colen.tempora.loggers.command.CommandLogger;
import com.colen.tempora.loggers.command.CommandQueueElement;
import com.colen.tempora.loggers.entity_death.EntityDeathLogger;
import com.colen.tempora.loggers.entity_death.EntityDeathQueueElement;
import com.colen.tempora.loggers.entity_position.EntityPositionLogger;
import com.colen.tempora.loggers.entity_position.EntityPositionQueueElement;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnLogger;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnQueueElement;
import com.colen.tempora.loggers.explosion.ExplosionLogger;
import com.colen.tempora.loggers.explosion.ExplosionQueueElement;
import com.colen.tempora.loggers.inventory.InventoryLogger;
import com.colen.tempora.loggers.inventory.InventoryQueueElement;
import com.colen.tempora.loggers.item_use.ItemUseLogger;
import com.colen.tempora.loggers.item_use.ItemUseQueueElement;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakLogger;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakQueueElement;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceLogger;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceQueueElement;
import com.colen.tempora.loggers.player_movement.PlayerMovementLogger;
import com.colen.tempora.loggers.player_movement.PlayerMovementQueueElement;

public class TemporaEvents {

    // We keep these references they need external access from mixins to log.
    public static InventoryLogger inventoryLogger = new InventoryLogger();
    public static BlockChangeLogger blockChangeLogger = new BlockChangeLogger();

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

        TemporaLoggerManager.register(BLOCK_CHANGE, blockChangeLogger, BlockChangeQueueElement::new);
        TemporaLoggerManager.register(COMMAND, new CommandLogger(), CommandQueueElement::new);
        TemporaLoggerManager.register(ENTITY_DEATH, new EntityDeathLogger(), EntityDeathQueueElement::new);
        TemporaLoggerManager.register(ENTITY_POSITION, new EntityPositionLogger(), EntityPositionQueueElement::new);
        TemporaLoggerManager.register(ENTITY_SPAWN, new EntitySpawnLogger(), EntitySpawnQueueElement::new);
        TemporaLoggerManager.register(EXPLOSION, new ExplosionLogger(), ExplosionQueueElement::new);
        TemporaLoggerManager.register(INVENTORY, inventoryLogger, InventoryQueueElement::new);
        TemporaLoggerManager.register(ITEM_USE, new ItemUseLogger(), ItemUseQueueElement::new);
        TemporaLoggerManager
            .register(PLAYER_BLOCK_BREAK, new PlayerBlockBreakLogger(), PlayerBlockBreakQueueElement::new);
        TemporaLoggerManager
            .register(PLAYER_BLOCK_PLACE, new PlayerBlockPlaceLogger(), PlayerBlockPlaceQueueElement::new);
        TemporaLoggerManager.register(PLAYER_MOVEMENT, new PlayerMovementLogger(), PlayerMovementQueueElement::new);
    }
}

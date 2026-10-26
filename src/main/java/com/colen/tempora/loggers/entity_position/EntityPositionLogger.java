package com.colen.tempora.loggers.entity_position;

import static com.colen.tempora.utils.GenericUtils.entityRegistryName;
import static com.colen.tempora.utils.GenericUtils.isClientSide;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityPositionLogger extends GenericPositionalLogger<EntityPositionEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ENTITY_POSITION;
    }

    @Override
    public @NotNull String getShortName() {
        return "mobpos";
    }

    @Override
    public @NotNull EntityPositionEventInfo newEventInfo() {
        return new EntityPositionEventInfo();
    }

    private static int entityMovementLoggingInterval;

    // --------------------------------------
    // Mobs
    // --------------------------------------
    // A mob is logged every few seconds for as long as it lives, so its UUID and name are stored once, in this table
    // beside the events, under a number of its own. Events refer to it by mobID.

    static final String MOB_TABLE = "Mobs";

    // How far behind a mob's lastSeen may be. Bringing it up to date for every event would be a write each time.
    private static final long LAST_SEEN_INTERVAL_MILLISECONDS = TimeUnit.HOURS.toMillis(1);
    // Recently logged mobs, so most events need no lookup. Mobs never stop coming, so only this many are kept.
    private static final int CACHED_MOBS = 20_000;

    // UUID -> the mob as stored. Worker thread only, apart from being emptied as the database opens.
    private final Map<String, StoredMob> cachedMobs = new LinkedHashMap<>(1024, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, StoredMob> eldest) {
            return size() > CACHED_MOBS;
        }
    };

    // A mob's row in the Mobs table: its number, and the lastSeen stored with it.
    private static final class StoredMob {

        final long id;
        final long lastSeen;

        StoredMob(long id, long lastSeen) {
            this.id = id;
            this.lastSeen = lastSeen;
        }
    }

    @Override
    protected void createExtraTables(Connection conn) throws SQLException {
        // A new database (another world, in singleplayer) has none of them.
        cachedMobs.clear();
        try (Statement st = conn.createStatement()) {
            // AUTOINCREMENT, so a pruned mob's number is never given to another mob. Without it SQLite reuses the
            // highest number when that mob is deleted, and its old events, if not all pruned yet, would read back as
            // the new mob.
            st.execute(
                "CREATE TABLE IF NOT EXISTS " + MOB_TABLE
                    + " (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, name TEXT NOT NULL,"
                    + " lastSeen INTEGER NOT NULL)");
            // For pruning, which deletes mobs by when they were last seen.
            st.execute("CREATE INDEX IF NOT EXISTS idx_" + MOB_TABLE + "_lastSeen ON " + MOB_TABLE + " (lastSeen)");
        }
    }

    /**
     * Gives each event its mob's number, storing mobs seen for the first time, and bringing lastSeen up to date for
     * mobs whose lastSeen is more than LAST_SEEN_INTERVAL_MILLISECONDS behind.
     */
    @Override
    protected @Nullable Runnable writeExtras(List<? extends GenericEventInfo> events, Connection conn)
        throws SQLException {
        // Only cached once the batch commits: if it fails, the rows written here are rolled back with it.
        Map<String, StoredMob> written = new HashMap<>();

        try (
            PreparedStatement find = conn.prepareStatement("SELECT id, lastSeen FROM " + MOB_TABLE + " WHERE uuid = ?");
            PreparedStatement insert = conn
                .prepareStatement("INSERT INTO " + MOB_TABLE + " (uuid, name, lastSeen) VALUES (?, ?, ?) RETURNING id");
            PreparedStatement touch = conn.prepareStatement("UPDATE " + MOB_TABLE + " SET lastSeen = ? WHERE id = ?")) {

            for (GenericEventInfo event : events) {
                if (!(event instanceof EntityPositionEventInfo mob)) continue;

                StoredMob stored = written.get(mob.entityUUID);
                if (stored == null) stored = cachedMobs.get(mob.entityUUID);
                if (stored == null) {
                    stored = find(find, mob.entityUUID);
                    if (stored != null) written.put(mob.entityUUID, stored);
                }

                if (stored != null && mob.timestamp - stored.lastSeen > LAST_SEEN_INTERVAL_MILLISECONDS) {
                    touch.setLong(1, mob.timestamp);
                    touch.setLong(2, stored.id);
                    // None if pruning deleted the mob since it was cached, so it is stored again below.
                    stored = touch.executeUpdate() == 1 ? new StoredMob(stored.id, mob.timestamp) : null;
                    if (stored != null) written.put(mob.entityUUID, stored);
                }

                if (stored == null) {
                    insert.setString(1, mob.entityUUID);
                    insert.setString(2, mob.entityName);
                    insert.setLong(3, mob.timestamp);
                    try (ResultSet rs = insert.executeQuery()) {
                        rs.next();
                        stored = new StoredMob(rs.getLong(1), mob.timestamp);
                    }
                    written.put(mob.entityUUID, stored);
                }

                mob.mobID = stored.id;
            }
        }

        return written.isEmpty() ? null : () -> cachedMobs.putAll(written);
    }

    private static @Nullable StoredMob find(PreparedStatement find, String uuid) throws SQLException {
        find.setString(1, uuid);
        try (ResultSet rs = find.executeQuery()) {
            return rs.next() ? new StoredMob(rs.getLong(1), rs.getLong(2)) : null;
        }
    }

    @Override
    public void loadExtras(List<? extends GenericEventInfo> events, Connection conn) throws SQLException {
        Map<Long, List<EntityPositionEventInfo>> byMob = new HashMap<>();
        for (GenericEventInfo event : events) {
            if (event instanceof EntityPositionEventInfo mob) {
                byMob.computeIfAbsent(mob.mobID, id -> new ArrayList<>())
                    .add(mob);
            }
        }
        if (byMob.isEmpty()) return;

        String placeholders = String.join(", ", Collections.nCopies(byMob.size(), "?"));
        try (PreparedStatement ps = conn
            .prepareStatement("SELECT id, uuid, name FROM " + MOB_TABLE + " WHERE id IN (" + placeholders + ")")) {
            int i = 1;
            for (long id : byMob.keySet()) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    for (EntityPositionEventInfo mob : byMob.get(rs.getLong(1))) {
                        mob.entityUUID = rs.getString(2);
                        mob.entityName = rs.getString(3);
                    }
                }
            }
        }
    }

    /**
     * Deletes mobs last seen before the oldest events kept, allowing for lastSeen being up to
     * LAST_SEEN_INTERVAL_MILLISECONDS behind, so no event kept refers to them. Each is stored again, under a new
     * number, if it turns up later.
     */
    @Override
    protected void pruneExtras(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + MOB_TABLE + " WHERE lastSeen < ?")) {
            ps.setLong(
                1,
                System.currentTimeMillis() - databaseManager.getOldestDataMilliseconds()
                    - LAST_SEEN_INTERVAL_MILLISECONDS);
            ps.executeUpdate();
        }
    }

    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(settingLine("tempora.logger.setting.interval", ticks(entityMovementLoggingInterval)));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        entityMovementLoggingInterval = config.getInt(
            "entity_position_logging_interval",
            getLoggerName(),
            500,
            1,
            Integer.MAX_VALUE,
            "How frequently are non-player entity locations recorded to the database, measured in ticks (20 ticks per second)?");
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onEntityUpdate(LivingUpdateEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.entityLiving.ticksExisted % entityMovementLoggingInterval != 0) return; // As an example, track every
                                                                                          // 20 seconds.

        logEntityPosition(event.entityLiving);
    }

    /**
     * Logs where an entity is right now. Players are not logged here (see PlayerPositionLogger), nor are dropped items
     * and XP orbs.
     */
    public void logEntityPosition(Entity entity) {
        if (!isEnabledByConfig) return;
        if (entity instanceof EntityPlayerMP) return;
        if (entity instanceof EntityItem) return;
        if (entity instanceof EntityXPOrb) return;

        EntityPositionEventInfo eventInfo = new EntityPositionEventInfo();
        eventInfo.rotationYaw = entity.rotationYaw;
        eventInfo.rotationPitch = entity.rotationPitch;

        eventInfo.entityName = entityRegistryName(entity);
        eventInfo.entityUUID = entity.getUniqueID()
            .toString();

        logEvent(eventInfo, entity.dimension, entity.posX, entity.posY, entity.posZ);
    }

}

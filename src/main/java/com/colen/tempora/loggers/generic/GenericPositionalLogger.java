package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import com.colen.tempora.loggers.generic.column.Column;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.enums.LoggerEventType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    public static final long SECONDS_RENDERING_DURATION = 10;

    protected PositionalLoggerDatabase databaseManager = new PositionalLoggerDatabase(this);

    private static volatile boolean running = true;

    private final LinkedBlockingQueue<EventToLog> concurrentEventQueue = new LinkedBlockingQueue<>();
    protected List<EventToLog> transparentEventsToRenderInWorld = new ArrayList<>();
    protected List<EventToLog> nonTransparentEventsToRenderInWorld = new ArrayList<>();
    private String loggerName;

    private boolean isEnabled;

    public void addEventToRender(EventToLog event) {
        event.eventRenderCreationTime = System.currentTimeMillis();

        if (event.needsTransparencyToRender()) {
            transparentEventsToRenderInWorld.add(event);
        } else {
            nonTransparentEventsToRenderInWorld.add(event);
        }
    }

    @SideOnly(Side.CLIENT)
    public List<EventToLog> getSortedLatestEventsByDistance(Collection<EventToLog> input, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        Map<String, EventToLog> latestPerBlock = new HashMap<>();

        for (EventToLog element : input) {
            if (element.dimensionID != playerDim) continue;

            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;
            EventToLog existing = latestPerBlock.get(key);

            if (existing == null || element.timestamp > existing.timestamp) {
                latestPerBlock.put(key, element);
            }
        }

        List<EventToLog> sorted = new ArrayList<>(latestPerBlock.values());
        sortByDistanceDescending(sorted, e);
        return sorted;
    }

    @SideOnly(Side.CLIENT)
    public void sortByDistanceDescending(List<EventToLog> list, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        list.sort((a, b) -> Double.compare(squaredDistance(b, px, py, pz), squaredDistance(a, px, py, pz)));
    }

    @SideOnly(Side.CLIENT)
    private static double squaredDistance(GenericQueueElement e, double x, double y, double z) {
        double dx = e.x - x;
        double dy = e.y - y;
        double dz = e.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    protected LogWriteSafety defaultLogWriteSafetyMode() {
        return LogWriteSafety.NORMAL;
    }

    public PositionalLoggerDatabase getDatabaseManager() {
        return databaseManager;
    }

    public abstract @NotNull LoggerEventType getLoggerEventType();

    private Object readColumn(ResultSet rs, Class<?> type, String column) throws SQLException {
        if (type == int.class || type == Integer.class) {
            return rs.getInt(column);
        }
        if (type == long.class || type == Long.class) {
            return rs.getLong(column);
        }
        if (type == double.class || type == Double.class) {
            return rs.getDouble(column);
        }
        if (type == float.class || type == Float.class) {
            return rs.getFloat(column);
        }
        if (type == boolean.class || type == Boolean.class) {
            return rs.getInt(column) != 0;
        }
        if (type == String.class) {
            return rs.getString(column);
        }

        throw new IllegalStateException("Unsupported field type: " + type);
    }

    public abstract @NotNull EventToLog getQueueElementInstance();

    public @NotNull List<EventToLog> generateQueryResults(ResultSet resultSet) throws SQLException {

        List<EventToLog> eventList = new ArrayList<>();

        try {
            List<Field> fields = getAllAnnotatedFieldsAlphabetically();

            while (resultSet.next()) {
                EventToLog element = getQueueElementInstance();

                // Populate base fields once
                element.populateDefaultFieldsFromResultSet(resultSet);

                for (Field field : fields) {
                    Column col = field.getAnnotation(Column.class);
                    if (col == null) continue;

                    String columnName = col.name()
                        .isEmpty() ? field.getName() : col.name();

                    Object value = readColumn(resultSet, field.getType(), columnName);

                    field.set(element, value);
                }

                eventList.add(element);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create event instance", e);
        }

        return eventList;
    }

    public abstract LoggerEnum getLoggerType();

    public abstract void renderEventsInWorld(RenderWorldLastEvent e);

    // todo move
    public List<Field> getAllAnnotatedFieldsAlphabetically() {
        List<Field> fields = new ArrayList<>();
        Deque<Class<?>> hierarchy = new ArrayDeque<>();

        Class<?> clazz = inferEventToLogClass();

        while (clazz != null && clazz != Object.class) {
            hierarchy.push(clazz);
            clazz = clazz.getSuperclass();
        }

        while (!hierarchy.isEmpty()) {
            Class<?> current = hierarchy.pop();

            List<Field> declared = new ArrayList<>();
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    declared.add(field);
                }
            }

            // Bring some stability to ordering. todo move sorting to be per class, not overall.
            declared.sort(Comparator.comparing(Field::getName));

            fields.addAll(declared);
        }

        return fields;
    }

    // Annoying!
    @SuppressWarnings("unchecked")
    protected Class<EventToLog> inferEventToLogClass() {
        Type type = getClass().getGenericSuperclass();

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalStateException("Logger must directly extend GenericPositionalLogger<EventToLog>.");
        }

        Type arg = ((ParameterizedType) type).getActualTypeArguments()[0];

        if (arg instanceof Class<?>) {
            return (Class<EventToLog>) arg;
        }

        throw new IllegalStateException("Cannot determine event class: " + arg);
    }

    // Logger name is also the SQL table name.
    public String getLoggerName() {
        return getLoggerType().name();
    }

    public void handleCustomLoggerConfig(Configuration config) {}

    public final void registerEvent() {
        if (!getLoggerEventType().shouldRegister()) return;

        switch (getLoggerEventType()) {
            case ForgeEvent -> MinecraftForge.EVENT_BUS.register(this);
            case MinecraftEvent -> FMLCommonHandler.instance()
                .bus()
                .register(this);
            default -> throw new IllegalStateException("Unknown LoggerEventType: " + getLoggerEventType());
        }
    }

    public final void queueEvent(EventToLog event) {
        if (!isEnabled) return;
        concurrentEventQueue.offer(event); // Non-blocking, thread-safe
    }

    private void startQueueWorker(String sqlTableName) {

        running = true;

        Thread t = new Thread(() -> queueLoop(sqlTableName), "Tempora-" + sqlTableName);
        t.setDaemon(false);
        t.setUncaughtExceptionHandler((thr, ex) -> {
            LOG.error("Tempora queue-worker '{}' crashed – halting JVM!", thr.getName(), ex);
            FMLCommonHandler.instance()
                .exitJava(-1, false);
        });
        t.start();
    }

    private static final int LARGE_QUEUE_THRESHOLD = 5_000;

    private void queueLoop(String sqlTableName) {
        List<EventToLog> buffer = new ArrayList<>();

        try {
            while (running || !concurrentEventQueue.isEmpty()) {
                // This blocks until at least one event is available.
                buffer.add(concurrentEventQueue.take());

                // Drain any additional available events into the buffer
                concurrentEventQueue.drainTo(buffer);

                if (concurrentEventQueue.size() > LARGE_QUEUE_THRESHOLD) {
                    LOG.warn("{} has {} events pending, possible slowdown.", sqlTableName, concurrentEventQueue.size());
                }

                // Insert the batch into DB and commit
                databaseManager.insertBatch(buffer);
                databaseManager.getDBConn()
                    .commit();
                buffer.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            LOG.error(
                "Queue worker interrupted for {} from external source. This is not meant to happen!",
                sqlTableName);
        } catch (Exception e) {
            throw new RuntimeException("DB failure in " + sqlTableName, e);
        }

        if (running) {
            throw new IllegalStateException("Queue worker terminated unexpectedly.");
        }
    }

    public final void genericConfig(@NotNull Configuration config) {
        isEnabled = config.getBoolean("isEnabled", getLoggerName(), loggerEnabledByDefault(), "Enables this logger.");
        databaseManager.genericConfig(config);
    }

    // Run on server start up/world load.
    private void initialiseLogger() {
        try {
            // Clear events and initialise connection.
            if (!concurrentEventQueue.isEmpty()) {
                LOG.warn(
                    "Tempora log for {} was not empty upon initialisation. This may suggest state leakage has occurred. Please report this.",
                    loggerName);
            }

            clearEvents();
            databaseManager = new PositionalLoggerDatabase(this);
            databaseManager.initialiseDatabase();
            startQueueWorker(getLoggerName());

        } catch (SQLException e) {
            throw new RuntimeException("[Tempora] Failed to initialise database for logger " + loggerName, e);
        }
    }

    public final LinkedBlockingQueue<EventToLog> getConcurrentEventQueue() {
        return concurrentEventQueue;
    }

    private void clearEvents() {
        concurrentEventQueue.clear();
    }

    // --------------------------------------
    // Static methods
    // --------------------------------------

    public static void onServerStart() {
        LOG.info("Opening Tempora databases.");

        for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            logger.initialiseLogger();
        }
    }

    public static void onServerClose() {
        try {
            running = false; // Signal worker thread to stop

            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                logger.databaseManager.shutdownDatabase();
            }

        } catch (Exception e) {
            LOG.error("Error closing resources.", e);
        } finally {
            // Just to ensure that we are not carrying data over to a new world opening.
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                logger.clearEvents();
            }
        }
    }

    private boolean loggerEnabledByDefault() {
        return true;
    }

    public void clearOldEventsToRender() {
        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        transparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
        nonTransparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
    }

    public void setName(String loggerName) {
        this.loggerName = loggerName;
    }
}

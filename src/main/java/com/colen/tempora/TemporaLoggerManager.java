package com.colen.tempora;

import static com.colen.tempora.Tempora.NETWORK;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.generic.GenericRenderEventPacketHandler;
import com.colen.tempora.loggers.generic.RenderEventPacket;

import cpw.mods.fml.relauncher.Side;

public final class TemporaLoggerManager {

    private TemporaLoggerManager() {}

    /* ---------------- LOGGER REGISTRY ---------------- */

    private static final Map<String, GenericPositionalLogger<?>> LOGGERS = new HashMap<>();

    /* ---------------- QUEUE ELEMENT REGISTRY ---------------- */

    private static final Map<Byte, Supplier<? extends GenericQueueElement>> FACTORIES = new HashMap<>();
    private static final Map<Class<?>, Byte> CLASS_TO_ID = new HashMap<>();
    private static byte nextQueueElementId = 0;

    /* ---------------- PACKET REGISTRATION ---------------- */

    private static boolean packetsRegistered = false;

    @SuppressWarnings("unchecked")
    public static <T extends GenericQueueElement> void register(String loggerName, GenericPositionalLogger<T> logger,
        Supplier<T> factory) {
        if (LOGGERS.containsKey(loggerName)) {
            throw new IllegalStateException("Logger already registered: " + loggerName);
        }

        // Little messy, but works...
        Class<T> queueElementClass = (Class<T>) factory.get()
            .getClass();

        if (CLASS_TO_ID.containsKey(queueElementClass)) {
            throw new IllegalStateException("QueueElement already registered: " + queueElementClass.getName());
        }

        // Assign ID
        byte id = nextQueueElementId++;

        // Register logger
        logger.setName(loggerName);
        LOGGERS.put(loggerName, logger);

        // Register queue element networking metadata
        CLASS_TO_ID.put(queueElementClass, id);
        FACTORIES.put(id, factory);

        // Sanity check: logger ↔ queue element consistency
        T probe = factory.get();
        if (!loggerName.equals(probe.getLoggerName())) {
            throw new IllegalStateException("Logger name mismatch: " + loggerName + " vs " + probe.getLoggerName());
        }

        // Register packet exactly once
        if (!packetsRegistered) {
            NETWORK.registerMessage(GenericRenderEventPacketHandler.class, RenderEventPacket.class, 1000, Side.CLIENT);
            packetsRegistered = true;
        }
    }

    /* ---------------- NETWORK HELPERS ---------------- */

    public static byte getQueueElementId(GenericQueueElement element) {
        Byte id = CLASS_TO_ID.get(element.getClass());
        if (id == null) {
            throw new IllegalStateException(
                "Unregistered QueueElement: " + element.getClass()
                    .getName());
        }
        return id;
    }

    public static GenericQueueElement createQueueElement(byte id) {
        Supplier<? extends GenericQueueElement> f = FACTORIES.get(id);
        if (f == null) {
            throw new IllegalStateException("Unknown QueueElement id: " + id);
        }
        return f.get();
    }

    /* ---------------- LOGGER ACCESS ---------------- */

    public static GenericPositionalLogger<?> getLogger(String loggerName) {
        return LOGGERS.get(loggerName);
    }

    public static Collection<GenericPositionalLogger<?>> getLoggerList() {
        return Collections.unmodifiableCollection(LOGGERS.values());
    }

    @SuppressWarnings("unchecked")
    public static <T extends GenericQueueElement> GenericPositionalLogger<T> getTypedLogger(String loggerName) {
        return (GenericPositionalLogger<T>) LOGGERS.get(loggerName);
    }

    @NotNull
    public static List<String> getAllLoggerNames() {
        return LOGGERS.values()
            .stream()
            .map(GenericPositionalLogger::getLoggerName)
            .collect(Collectors.toList());
    }
}

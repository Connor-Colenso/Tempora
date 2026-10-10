package com.colen.tempora.loggers.generic;

/**
 * An event that belongs to the whole server rather than a place, e.g. the weather changing or the server's TPS, logged
 * by a {@link GenericGlobalLogger}. It has no position, and is never drawn in the world. An event that is still about
 * one dimension, such as the weather there, adds its own dimension column.
 */
public abstract class GlobalEventInfo extends GenericEventInfo {
}

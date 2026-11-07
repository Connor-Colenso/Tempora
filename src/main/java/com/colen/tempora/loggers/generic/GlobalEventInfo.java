package com.colen.tempora.loggers.generic;

/**
 * Server wide event with no place, like weather changing or TPS, logged by a {@link GenericGlobalLogger}. Never drawn
 * in the world. Events still about one dimension (weather there) add their own dimension column
 */
public abstract class GlobalEventInfo extends GenericEventInfo {
}

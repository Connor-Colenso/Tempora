package com.colen.tempora.rendering;

import static com.colen.tempora.Tempora.LOG;

import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderEventsInWorld {

    // Loggers whose rendering threw. They are skipped from then on, as Forge rethrows anything this handler throws,
    // which would crash the game every frame, and keep the loggers after the broken one from drawing.
    private final Set<String> brokenLoggers = new HashSet<>();

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        for (GenericPositionalLogger<?> positionalLogger : TemporaLoggerManager.getPositionalLoggers()) {
            if (brokenLoggers.contains(positionalLogger.getLoggerName())) continue;

            try {
                positionalLogger.renderEventsInWorld(e);
                positionalLogger.clearOldEventsToRender();
            } catch (RuntimeException | LinkageError ex) {
                brokenLoggers.add(positionalLogger.getLoggerName());
                LOG.error(
                    "Drawing {} events failed, so they will not be drawn again until the game restarts.",
                    positionalLogger.getLoggerName(),
                    ex);
            }
        }
    }
}

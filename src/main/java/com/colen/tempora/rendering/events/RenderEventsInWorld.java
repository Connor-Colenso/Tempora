package com.colen.tempora.rendering.events;

import static com.colen.tempora.Tempora.LOG;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.colen.tempora.loggers.generic.PositionalEventInfo;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Draws the events in ClientEventStore, each logger's with its EventRenderer
@SideOnly(Side.CLIENT)
public final class RenderEventsInWorld {

    // Loggers whose rendering threw, skipped from then on. Forge rethrows anything this handler throws, which would
    // crash the game every frame and stop loggers after the broken one drawing
    private final Set<String> brokenLoggers = new HashSet<>();

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ClientEventStore.expire();
        if (ClientEventStore.isEmpty()) return;

        EventRenderContext context = EventRenderContext.create(e);
        if (context == null) return;

        Map<String, List<PositionalEventInfo>> visible = new LinkedHashMap<>();
        for (String loggerName : ClientEventStore.loggerNames()) {
            if (brokenLoggers.contains(loggerName)) continue;

            List<PositionalEventInfo> events = ClientEventStore.visible(loggerName, context);
            if (!events.isEmpty()) visible.put(loggerName, events);
        }
        if (visible.isEmpty()) return;

        GL11.glPushMatrix();
        try {
            // Renderers draw in world coords
            GL11.glTranslated(-context.cameraX, -context.cameraY, -context.cameraZ);

            // Text is drawn through walls so it goes after every logger's blocks and entities, which would cover it
            // otherwise
            forEachWorkingRenderer(visible, (renderer, events) -> renderer.renderWorld(events, context));
            forEachWorkingRenderer(visible, (renderer, events) -> renderer.renderText(events, context));
        } finally {
            GL11.glPopMatrix();
        }
    }

    // Leaving a server or changing dimension. Kept events are the old world's so nothing's lost, and the renderers'
    // ghosts would otherwise keep that whole world in memory
    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        if (!e.world.isRemote) return;

        ClientEventStore.clear();
        EventRenderers.clearCaches();
    }

    private void forEachWorkingRenderer(Map<String, List<PositionalEventInfo>> visible, RenderPass pass) {
        for (Map.Entry<String, List<PositionalEventInfo>> entry : visible.entrySet()) {
            String loggerName = entry.getKey();
            if (brokenLoggers.contains(loggerName)) continue;

            try {
                pass.render(EventRenderers.get(loggerName), entry.getValue());
            } catch (RuntimeException | LinkageError ex) {
                brokenLoggers.add(loggerName);
                LOG.error(
                    "Drawing {} events failed, so they will not be drawn again until the game restarts.",
                    loggerName,
                    ex);
            }
        }
    }

    @FunctionalInterface
    private interface RenderPass {

        void render(EventRenderer<PositionalEventInfo> renderer, List<PositionalEventInfo> events);
    }
}

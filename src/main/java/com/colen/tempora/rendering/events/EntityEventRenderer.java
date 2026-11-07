package com.colen.tempora.rendering.events;

import static com.colen.tempora.Tempora.LOG;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;

import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws each event as its entity, outlined, with a label above. Entities this client can't make (mod it doesn't have,
 * renderer that throws) show as an outlined box instead
 */
@SideOnly(Side.CLIENT)
public abstract class EntityEventRenderer<E extends PositionalEventInfo> implements EventRenderer<E> {

    // About player sized, for entities that can't be drawn
    private static final double FALLBACK_HALF_WIDTH = 0.3;
    private static final double FALLBACK_HEIGHT = 1.8;

    protected final Color colour;

    // Made once per event not every frame, null for events whose entity can't be drawn. Weak so each goes after
    // ClientEventStore drops its event, but only once the map's next used, so it's also cleared on world unload since
    // each entity holds its world
    private final Map<E, Entity> ghosts = new WeakHashMap<>();

    protected EntityEventRenderer(Color colour) {
        this.colour = colour;
    }

    /** EntityList name of the entity, e.g. "Zombie" */
    protected abstract @Nullable String entityName(E event);

    protected abstract float yaw(E event);

    protected abstract float pitch(E event);

    /** Lines shown above the entity */
    protected abstract List<String> label(E event);

    /** Tweaks a newly made entity before it's first drawn, e.g. to pose it */
    protected void prepareGhost(Entity entity, E event) {}

    @Override
    public void clearCaches() {
        ghosts.clear();
    }

    @Override
    public void renderWorld(List<E> events, EventRenderContext context) {
        for (E event : events) {
            Entity ghost = ghostFor(event, context);
            if (ghost != null) {
                try {
                    RenderUtils.renderEntityInWorld(
                        ghost,
                        event.x,
                        event.y,
                        event.z,
                        yaw(event),
                        pitch(event),
                        context.partialTicks);
                    RenderUtils.renderEntityAABBInWorld(ghost, colour);
                    continue;
                } catch (RuntimeException | LinkageError e) {
                    LOG.warn(
                        "Could not draw {} for a {} event, showing a box instead.",
                        entityName(event),
                        event.getLoggerName(),
                        e);
                    ghosts.put(event, null);
                }
            }

            RenderUtils.renderRegion(
                event.x - FALLBACK_HALF_WIDTH,
                event.y,
                event.z - FALLBACK_HALF_WIDTH,
                event.x + FALLBACK_HALF_WIDTH,
                event.y + FALLBACK_HEIGHT,
                event.z + FALLBACK_HALF_WIDTH,
                colour);
        }
    }

    @Override
    public void renderText(List<E> events, EventRenderContext context) {
        for (E event : events) {
            Entity ghost = ghosts.get(event);
            double height = ghost == null ? FALLBACK_HEIGHT : ghost.height;
            EventRenderHelpers.renderLabelAbove(label(event), event.x, event.y + height + 0.3, event.z);
        }
    }

    private @Nullable Entity ghostFor(E event, EventRenderContext context) {
        if (ghosts.containsKey(event)) return ghosts.get(event);

        Entity ghost = null;
        try {
            // Null for names this client doesn't know
            ghost = EntityList.createEntityByName(entityName(event), context.world);
            if (ghost != null) prepareGhost(ghost, event);
        } catch (RuntimeException | LinkageError e) {
            LOG.warn(
                "Could not make {} for a {} event, showing a box instead.",
                entityName(event),
                event.getLoggerName(),
                e);
            ghost = null;
        }

        ghosts.put(event, ghost);
        return ghost;
    }
}

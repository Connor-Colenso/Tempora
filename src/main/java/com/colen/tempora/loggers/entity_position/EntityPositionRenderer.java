package com.colen.tempora.loggers.entity_position;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.rendering.events.EntityEventRenderer;
import com.colen.tempora.rendering.events.EventRenderContext;
import com.colen.tempora.rendering.events.EventRenderHelpers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shows each entity where it was, with a trail joining one entity's positions in the order it was at them. */
@SideOnly(Side.CLIENT)
public class EntityPositionRenderer extends EntityEventRenderer<EntityPositionEventInfo> {

    public EntityPositionRenderer() {
        super(Color.GREEN);
    }

    @Override
    public void renderWorld(List<EntityPositionEventInfo> events, EventRenderContext context) {
        for (List<EntityPositionEventInfo> path : EventRenderHelpers.groupBy(events, event -> event.entityUUID)) {
            EventRenderHelpers.renderPath(path, colour);
        }
        super.renderWorld(events, context);
    }

    @Override
    protected String entityName(EntityPositionEventInfo event) {
        return event.entityName;
    }

    @Override
    protected float yaw(EntityPositionEventInfo event) {
        return event.rotationYaw;
    }

    @Override
    protected float pitch(EntityPositionEventInfo event) {
        return event.rotationPitch;
    }

    @Override
    protected List<String> label(EntityPositionEventInfo event) {
        return Arrays.asList(
            EnumChatFormatting.GREEN + EventRenderHelpers.entityName(event.entityName),
            EnumChatFormatting.GRAY + EventRenderHelpers.timeAgo(event.timestamp));
    }
}

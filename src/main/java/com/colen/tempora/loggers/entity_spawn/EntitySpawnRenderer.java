package com.colen.tempora.loggers.entity_spawn;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.rendering.events.EntityEventRenderer;
import com.colen.tempora.rendering.events.EventRenderHelpers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Each entity where it spawned
@SideOnly(Side.CLIENT)
public class EntitySpawnRenderer extends EntityEventRenderer<EntitySpawnEventInfo> {

    public EntitySpawnRenderer() {
        super(new Color(64, 128, 255));
    }

    @Override
    protected String entityName(EntitySpawnEventInfo event) {
        return event.entityName;
    }

    @Override
    protected float yaw(EntitySpawnEventInfo event) {
        return event.rotationYaw;
    }

    @Override
    protected float pitch(EntitySpawnEventInfo event) {
        return event.rotationPitch;
    }

    @Override
    protected List<String> label(EntitySpawnEventInfo event) {
        return Arrays.asList(
            EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted(
                "tempora.render.event.spawned",
                EventRenderHelpers.entityName(event.entityName)),
            EnumChatFormatting.GRAY + EventRenderHelpers.timeAgo(event.timestamp));
    }
}

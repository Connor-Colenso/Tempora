package com.colen.tempora.loggers.entity_death;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.rendering.events.EntityEventRenderer;
import com.colen.tempora.rendering.events.EventRenderHelpers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Each mob lying where it died, tinted red, with what killed it
@SideOnly(Side.CLIENT)
public class EntityDeathRenderer extends EntityEventRenderer<EntityDeathEventInfo> {

    public EntityDeathRenderer() {
        super(Color.RED);
    }

    @Override
    protected String entityName(EntityDeathEventInfo event) {
        return event.entityName;
    }

    @Override
    protected float yaw(EntityDeathEventInfo event) {
        return event.rotationYaw;
    }

    @Override
    protected float pitch(EntityDeathEventInfo event) {
        return event.rotationPitch;
    }

    // Vanilla tints a dying mob red and tips it onto its side, all the way over after 20 ticks
    @Override
    protected void prepareGhost(Entity entity, EntityDeathEventInfo event) {
        if (entity instanceof EntityLivingBase living) living.deathTime = 20;
    }

    @Override
    protected List<String> label(EntityDeathEventInfo event) {
        return Arrays.asList(
            EnumChatFormatting.RED + StatCollector.translateToLocalFormatted(
                "tempora.render.event.died",
                EventRenderHelpers.entityName(event.entityName)),
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "tempora.render.event.killed_by",
                EventRenderHelpers.killerName(event.killerPlayerUUID, event.killerMob, event.damageType)),
            EnumChatFormatting.GRAY + EventRenderHelpers.timeAgo(event.timestamp));
    }
}

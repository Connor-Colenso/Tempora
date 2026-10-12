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

/** Shows each mob lying where it died, tinted red, with what killed it. */
@SideOnly(Side.CLIENT)
public class EntityDeathRenderer extends EntityEventRenderer<EntityDeathEventInfo> {

    public EntityDeathRenderer() {
        super(Color.RED);
    }

    @Override
    protected String entityName(EntityDeathEventInfo event) {
        return event.nameOfDeadEntity;
    }

    @Override
    protected float yaw(EntityDeathEventInfo event) {
        return event.rotationYaw;
    }

    @Override
    protected float pitch(EntityDeathEventInfo event) {
        return event.rotationPitch;
    }

    // Vanilla draws a dying mob tinted red and tipping onto its side, all the way over after 20 ticks of dying.
    @Override
    protected void prepareGhost(Entity entity, EntityDeathEventInfo event) {
        if (entity instanceof EntityLivingBase living) living.deathTime = 20;
    }

    @Override
    protected List<String> label(EntityDeathEventInfo event) {
        return Arrays.asList(
            EnumChatFormatting.RED + StatCollector.translateToLocalFormatted(
                "tempora.render.event.died",
                EventRenderHelpers.entityName(event.nameOfDeadEntity)),
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "tempora.render.event.killed_by",
                EventRenderHelpers.playerName(event.killedBy)),
            EnumChatFormatting.GRAY + EventRenderHelpers.timeAgo(event.timestamp));
    }
}

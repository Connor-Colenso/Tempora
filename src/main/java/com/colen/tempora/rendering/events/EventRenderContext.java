package com.colen.tempora.rendering.events;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.loggers.generic.PositionalEventInfo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** What every event renderer needs about the frame being drawn. */
@SideOnly(Side.CLIENT)
public final class EventRenderContext {

    public final float partialTicks;

    // Where the camera is this frame, in world coordinates.
    public final double cameraX;
    public final double cameraY;
    public final double cameraZ;

    public final World world;
    public final int dimension;

    // A smoothly increasing tick count, for animations.
    public final float animationTicks;

    private EventRenderContext(float partialTicks, EntityLivingBase camera, World world) {
        this.partialTicks = partialTicks;
        this.cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        this.cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        this.cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        this.world = world;
        this.dimension = world.provider.dimensionId;
        this.animationTicks = camera.ticksExisted + partialTicks;
    }

    /** Null while there is no world to draw in, e.g. on the main menu. */
    static @Nullable EventRenderContext create(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return null;

        return new EventRenderContext(event.partialTicks, mc.renderViewEntity, mc.theWorld);
    }

    public double distanceSqToCamera(double x, double y, double z) {
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceSqToCamera(PositionalEventInfo event) {
        return distanceSqToCamera(event.x, event.y, event.z);
    }

    /** The yaw that makes something standing at x, z face the camera. */
    public float yawTowardsCamera(double x, double z) {
        return yawTowards(x, z, cameraX, cameraZ);
    }

    /** The yaw that makes something standing at fromX, fromZ face toX, toZ. */
    public static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }
}

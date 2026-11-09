package com.colen.tempora.utils;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

// Moves an entity to another dimension and drops it at the given spot, building nothing. Vanilla's teleporter looks for
// a Nether portal near the arrival point and builds one if there isn't one (or the End's obsidian platform), which a
// teleport from our chat output must never do
public final class NoPortalTeleporter extends Teleporter {

    private final double x, y, z;

    public NoPortalTeleporter(WorldServer world, double x, double y, double z) {
        super(world);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Ignores the coords it's given, those are where the entity was in its old dimension. Anything else would load
    // chunks there and have the dimension change event see it there
    @Override
    public void placeInPortal(Entity entity, double oldX, double oldY, double oldZ, float yaw) {
        entity.setLocationAndAngles(x, y, z, yaw, entity.rotationPitch);
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float yaw) {
        return false;
    }

    @Override
    public boolean makePortal(Entity entity) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {}
}

package com.colen.tempora.utils;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

// Moves an entity to another dimension and drops it where it already is, building nothing. Vanilla's teleporter looks
// for a Nether portal near the arrival point and builds one if there isn't one (or the End's obsidian platform), which
// a teleport from our chat output must never do
public final class NoPortalTeleporter extends Teleporter {

    public NoPortalTeleporter(WorldServer world) {
        super(world);
    }

    @Override
    public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
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

package com.colen.tempora.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.config.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class GenericUtils {

    public static String getDimensionName(int dimID) {
        WorldProvider provider = DimensionManager.createProviderFor(dimID);
        provider.setDimension(dimID);
        return provider.getDimensionName();
    }

    /**
     * The name an entity is registered under in EntityList, e.g. "CaveSpider", which chat translates into its name and
     * the client turns back into the entity to draw it. Falls back to the class name for entities no mod registered.
     */
    public static String entityRegistryName(Entity entity) {
        String name = EntityList.getEntityString(entity);
        return name != null ? name
            : entity.getClass()
                .getSimpleName();
    }

    public static boolean isClientSide() {
        return FMLCommonHandler.instance()
            .getEffectiveSide() == Side.CLIENT;
    }

    public static boolean isServerSide() {
        return !isClientSide();
    }

    // True when launched from the development workspace (runClient, runServer), false in a real modpack.
    public static boolean isDevEnvironment() {
        return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }

    public static boolean shouldTemporaRun() {
        return isServerSide() || Config.shouldTemporaRun;
    }

}

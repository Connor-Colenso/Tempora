package com.colen.tempora.utils;

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

    public static boolean isClientSide() {
        return FMLCommonHandler.instance()
            .getEffectiveSide() == Side.CLIENT;
    }

    public static boolean isServerSide() {
        return !isClientSide();
    }

    public static boolean shouldTemporaRun() {
        return isServerSide() || Config.shouldTemporaRun;
    }

}

package com.colen.tempora.utils;

import java.util.Scanner;

import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.config.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class GenericUtils {

    public static boolean askTerminalYesNo(String question) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print(question + " [yes/no]: ");

            String input = scanner.nextLine()
                .trim()
                .toLowerCase();

            if (input.equals("yes") || input.equals("y")) {
                return true;
            }
            if (input.equals("no") || input.equals("n")) {
                return false;
            }

            System.out.println("Please type 'yes' or 'no'.");
        }
    }

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

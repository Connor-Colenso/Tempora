package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.config.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class GenericUtils {

    private static final int STACK_TRACE_DEPTH = 6;

    public static String getCallingClassChain() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        List<String> classNames = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        final int SKIP = 3;

        for (int i = SKIP; i < stack.length && classNames.size() < STACK_TRACE_DEPTH; i++) {
            StackTraceElement e = stack[i];

            String name = e.getClassName() + "#" + e.getMethodName();
            if (seen.add(name)) {
                classNames.add(name);
            }
        }

        return String.join(" -> ", classNames);
    }

    public static long parseSizeStringToBytes(String sizeStr) {
        sizeStr = sizeStr.trim()
            .toLowerCase();
        long multiplier = 1;
        String numberPart = sizeStr;

        if (sizeStr.endsWith("tb")) {
            multiplier = 1000L * 1000L * 1000L * 1000L; // 1 TB = 10^12 bytes decimal
            numberPart = sizeStr.substring(0, sizeStr.length() - 2)
                .trim();
        } else if (sizeStr.endsWith("gb")) {
            multiplier = 1000L * 1000L * 1000L; // 1 GB = 10^9 bytes decimal
            numberPart = sizeStr.substring(0, sizeStr.length() - 2)
                .trim();
        } else if (sizeStr.endsWith("mb")) {
            multiplier = 1024L * 1024L; // 1 MB = 1024^2 bytes binary
            numberPart = sizeStr.substring(0, sizeStr.length() - 2)
                .trim();
        } else if (sizeStr.endsWith("kb")) {
            multiplier = 1024L; // 1 KB = 1024 bytes binary
            numberPart = sizeStr.substring(0, sizeStr.length() - 2)
                .trim();
        } else if (sizeStr.endsWith("b")) {
            numberPart = sizeStr.substring(0, sizeStr.length() - 1)
                .trim();
        }
        // else no suffix: assume bytes

        return Long.parseLong(numberPart) * multiplier;
    }

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

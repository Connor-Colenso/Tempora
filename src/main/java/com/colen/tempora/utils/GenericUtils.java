package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenericUtils {

    private static final int STACK_TRACE_DEPTH = 3;

    public static String getCallingClassChain() {
        StackTraceElement[] stack = Thread.currentThread()
            .getStackTrace();
        List<String> classNames = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Start from index 3 to skip getStackTrace(), this method, and likely your wrapper
        for (int i = STACK_TRACE_DEPTH; i < stack.length && classNames.size() < STACK_TRACE_DEPTH; i++) {
            String fullClassName = stack[i].getClassName();
            int lastDot = fullClassName.lastIndexOf('.');
            String simpleName = lastDot == -1 ? fullClassName : fullClassName.substring(lastDot + 1);

            if (seen.add(simpleName)) {
                classNames.add(simpleName);
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

}

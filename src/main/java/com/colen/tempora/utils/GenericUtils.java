package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenericUtils {

    private static final int STACK_TRACE_DEPTH = 3;

    public static String getCallingClassChain() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
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

}

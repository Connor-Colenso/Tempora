package com.colen.tempora.utils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.ArrayList;
import java.util.List;

public class StackTraceUtils {

    private static final int STACK_TRACE_DEPTH = 999;

    private static final BiMap<String, Long> stackTraceMap = HashBiMap.create();
    private static long nextId = 0;

    public static String getCallingClassChain() {
        StackTraceElement[] stack = Thread.currentThread()
            .getStackTrace();

        List<String> classNames = new ArrayList<>();

        final int SKIP = 4;

        for (int i = SKIP; i < stack.length && classNames.size() < STACK_TRACE_DEPTH; i++) {
            StackTraceElement e = stack[i];

            String entry = e.getClassName() + "#" + e.getMethodName() + ":" + e.getLineNumber();

            classNames.add(entry);
        }

        return String.join(" -> ", classNames);
    }

    // Returns a stable ID for a given stack trace string.
    // Assigns a new one if it doesn't exist yet.
    public static long getID(String callingClassChain) {
        Long existing = stackTraceMap.get(callingClassChain);
        if (existing != null) {
            return existing;
        }

        long id = nextId++;
        stackTraceMap.put(callingClassChain, id);
        return id;
    }

    public static String getStackTrace(long stackTraceID) {
        String stackTrace = stackTraceMap.inverse().get(stackTraceID);
        if (stackTrace == null) {
            throw new IllegalStateException("Stack trace id " + stackTraceID + " not found, this should not happen.");
        }
        return stackTrace;
    }

//    public static void clear() {
//        stackTraceMap.clear();
//        nextId = 0;
//    }
}

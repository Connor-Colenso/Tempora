package com.colen.tempora.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class StackTraceUtils {

    // Enough to see which mod made a change, and small enough for the whole trace to travel in one chat line.
    private static final int STACK_TRACE_DEPTH = 64;

    // Trace text -> its ID, so each distinct trace is hashed once. Thread safe, as blocks change on other mods'
    // threads too. Emptied if it grows this large, which costs only re-hashing.
    // Keys are whole traces, a few KB each, so this stays small.
    private static final int MAX_CACHED_IDS = 1_000;
    private static final ConcurrentHashMap<String, Long> IDS = new ConcurrentHashMap<>();

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

    /**
     * A trace's ID: the first 8 bytes of its SHA-256. The same trace always gets the same ID, across restarts and on
     * any server, so an ID stored with an event always means the same trace.
     */
    public static long getID(String callingClassChain) {
        Long cached = IDS.get(callingClassChain);
        if (cached != null) return cached;

        long id = hash(callingClassChain);
        if (IDS.size() >= MAX_CACHED_IDS) IDS.clear();
        IDS.put(callingClassChain, id);
        return id;
    }

    /**
     * A trace as chat lines, most recent call first, e.g. "0. Chunk#func_150807_a:640", each with its line number in
     * the trace. Used for the hover on the server and for printing the whole trace on the client.
     */
    public static List<IChatComponent> toChatLines(String stackTrace) {
        String[] lines = stackTrace.split("->");
        List<IChatComponent> components = new ArrayList<>();

        int index = 0;

        // Iterate in reverse (most recent first)
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();

            // Extract class, method, line
            // Format: fully.qualified.Class#method:line
            String className = trimmed;
            String methodName = "";
            String lineNumber = "?";

            int hashIndex = trimmed.lastIndexOf('#');
            int colonIndex = trimmed.lastIndexOf(':');

            if (hashIndex != -1) {
                className = trimmed.substring(0, hashIndex);
                methodName = trimmed.substring(hashIndex + 1, colonIndex != -1 ? colonIndex : trimmed.length());
            }

            if (colonIndex != -1 && colonIndex + 1 < trimmed.length()) {
                String rawLine = trimmed.substring(colonIndex + 1);
                lineNumber = rawLine.equals("-1") ? "?" : rawLine;
            }

            // Strip package → keep simple class name
            int lastDot = className.lastIndexOf('.');
            if (lastDot != -1 && lastDot + 1 < className.length()) {
                className = className.substring(lastDot + 1);
            }

            // Build simple display with 1 space
            String finalText = className + "#" + methodName + ":" + lineNumber;

            // Index component
            IChatComponent lineNumberComp = new ChatComponentTranslation("%s. ", new ChatComponentNumber(index));
            lineNumberComp.getChatStyle()
                .setColor(EnumChatFormatting.YELLOW);

            // Text component
            IChatComponent lineText = new ChatComponentText(finalText);
            lineText.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);

            IChatComponent fullLine = new ChatComponentTranslation("%s%s", lineNumberComp, lineText);
            components.add(fullLine);

            index++;
        }

        return components;
    }

    private static long hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            long id = 0;
            for (int i = 0; i < 8; i++) id = (id << 8) | (digest[i] & 0xFF);
            return id;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}

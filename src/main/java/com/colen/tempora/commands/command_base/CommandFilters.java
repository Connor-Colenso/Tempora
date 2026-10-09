package com.colen.tempora.commands.command_base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

/**
 * Optional "key:value" arguments, e.g. "r:10 t:1d l:break,place". They can come in any order and any of them can be
 * left out.
 */
public final class CommandFilters {

    private final Map<String, String> values;

    private CommandFilters(Map<String, String> values) {
        this.values = values;
    }

    // Parses args from index "from" onwards. Null, after telling the sender why, if one isn't a known key:value.
    public static @Nullable CommandFilters parse(ICommandSender sender, String[] args, int from, String... keys) {
        Set<String> allowed = new HashSet<>(Arrays.asList(keys));
        Map<String, String> values = new HashMap<>();

        for (int i = from; i < args.length; i++) {
            String arg = args[i];
            int colon = arg.indexOf(':');
            String key = colon > 0 ? arg.substring(0, colon)
                .toLowerCase(Locale.ROOT) : null;

            if (key == null || !allowed.contains(key) || colon == arg.length() - 1) {
                List<String> expected = new ArrayList<>();
                for (String k : keys) expected.add(k + ":");

                IChatComponent error = new ChatComponentTranslation(
                    "tempora.command.bad_filter",
                    arg,
                    String.join(" ", expected));
                error.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(error);
                return null;
            }

            values.put(key, arg.substring(colon + 1));
        }

        return new CommandFilters(values);
    }

    public String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    public @Nullable String get(String key) {
        return values.get(key);
    }

    /**
     * Tab completion for the last argument. Before a colon this suggests keys not used yet ("r:", "t:", ...), after
     * it the suggestions for that key. Values can be comma separated lists, where only the last item is completed.
     */
    public static List<String> complete(String[] args, Map<String, ? extends Collection<String>> suggestions) {
        String typed = args[args.length - 1];
        List<String> completions = new ArrayList<>();

        int colon = typed.indexOf(':');
        if (colon < 0) {
            Set<String> used = new HashSet<>();
            for (int i = 0; i < args.length - 1; i++) {
                int c = args[i].indexOf(':');
                if (c > 0) used.add(
                    args[i].substring(0, c)
                        .toLowerCase(Locale.ROOT));
            }

            for (String key : suggestions.keySet()) {
                if (!used.contains(key) && key.startsWith(typed.toLowerCase(Locale.ROOT))) {
                    completions.add(key + ":");
                }
            }
            return completions;
        }

        Collection<String> options = suggestions.get(
            typed.substring(0, colon)
                .toLowerCase(Locale.ROOT));
        if (options == null) return completions;

        // Keep everything up to the last comma, complete the item after it.
        String value = typed.substring(colon + 1);
        int lastComma = value.lastIndexOf(',');
        String done = typed.substring(0, colon + 1 + lastComma + 1);
        String partial = value.substring(lastComma + 1)
            .toLowerCase(Locale.ROOT);

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT)
                .startsWith(partial)) {
                completions.add(done + option);
            }
        }
        return completions;
    }
}

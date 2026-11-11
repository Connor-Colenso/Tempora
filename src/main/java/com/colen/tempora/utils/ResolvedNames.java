package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.NameRoles;
import com.colen.tempora.loggers.generic.column.names.ShownNames;
import com.colen.tempora.loggers.generic.column.names.lookups.AchievementNames;
import com.colen.tempora.loggers.generic.column.names.lookups.ItemNames;
import com.colen.tempora.loggers.generic.column.names.lookups.MobNames;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

// What a player's client found the text of an f: filter names in their language. For each NameRole the things it
// matched, each a list of values in part order (NamePart.ANY for a part any value fits). Only values, the text itself
// stays on the server in TextFilter. Sent to the server in PacketNameFilter
public final class ResolvedNames {

    // Things one filter can match, more than this and it's too vague to be useful and makes a huge query
    public static final int MAX_MATCHES = 1000;
    // Any meta of that ID, for item(id, meta). Items with no subtypes (tools store damage there) and blocks with no
    // item
    public static final int ANY_META = -1;
    // Under the 32 KiB a client can send in one packet
    static final int MAX_PACKET_BYTES = 30_000;
    // Roles one answer can name, way more than any pack has
    private static final int MAX_ROLES = 256;

    private static final byte ANY_VALUE = 0;
    private static final byte INT_VALUE = 1;
    private static final byte TEXT_VALUE = 2;

    private static final ResolvedNames NOTHING = new ResolvedNames(Collections.emptyMap(), 0);

    private final Map<NameRole, Set<List<Object>>> matches;
    // How many things it matched if that was over MAX_MATCHES (then it holds nothing), otherwise 0
    private final int tooMany;

    private ResolvedNames(Map<NameRole, Set<List<Object>>> matches, int tooMany) {
        this.matches = matches;
        this.tooMany = tooMany;
    }

    // One thing of every role made so far, 0 or "" in each part. For TextFilter.probe
    static ResolvedNames probe() {
        Map<NameRole, Set<List<Object>>> matches = new LinkedHashMap<>();
        for (NameRole role : NameRoles.known()) {
            List<Object> values = new ArrayList<>();
            for (NamePart part : role.parts()) values.add(part.type == ColumnType.INTEGER ? (Object) 0 : "");
            matches.put(role, Collections.singleton(Collections.unmodifiableList(values)));
        }
        return new ResolvedNames(matches, 0);
    }

    public static ResolvedNames nothing() {
        return NOTHING;
    }

    /** Things of this role it matched, values in part order */
    public Set<List<Object>> matches(NameRole role) {
        Set<List<Object>> found = matches.get(role);
        return found == null ? Collections.emptySet() : found;
    }

    public Set<List<Object>> matches(Class<? extends NameRole> role) {
        return matches(NameRoles.of(role));
    }

    public int tooMany() {
        return tooMany;
    }

    private int count() {
        int count = 0;
        for (Set<List<Object>> things : matches.values()) count += things.size();
        return count;
    }

    // Long names can push it under MAX_MATCHES past what one packet holds. Then it counts as matching too much, same
    // as going over the cap
    public ResolvedNames fitForPacket() {
        ByteBuf buf = Unpooled.buffer();
        encode(buf);
        if (buf.readableBytes() <= MAX_PACKET_BYTES) return this;
        return tooMany(count());
    }

    // Roles by class name, so it doesn't matter in what order either side made them
    public void encode(ByteBuf buf) {
        buf.writeInt(tooMany);
        buf.writeInt(matches.size());
        for (Map.Entry<NameRole, Set<List<Object>>> role : matches.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, NameRoles.name(role.getKey()));
            buf.writeInt(
                role.getKey()
                    .parts()
                    .size());
            buf.writeInt(
                role.getValue()
                    .size());
            for (List<Object> thing : role.getValue()) {
                for (Object value : thing) writeValue(buf, value);
            }
        }
    }

    private static void writeValue(ByteBuf buf, Object value) {
        if (value == NamePart.ANY) {
            buf.writeByte(ANY_VALUE);
        } else if (value instanceof Integer number) {
            buf.writeByte(INT_VALUE);
            buf.writeInt(number);
        } else {
            buf.writeByte(TEXT_VALUE);
            ByteBufUtils.writeUTF8String(buf, (String) value);
        }
    }

    // From a client so sizes are checked, too many and it's the same as matching too much. Shown as one past the cap.
    // Roles this server doesn't have and things that don't fit their role are dropped, they can't be in any column
    public static ResolvedNames decode(ByteBuf buf) {
        int tooMany = buf.readInt();
        int roles = buf.readInt();
        if (roles < 0 || roles > MAX_ROLES) return tooMany(MAX_MATCHES + 1);

        // Counted against what's left so adding them up can't overflow
        int left = MAX_MATCHES;
        Map<NameRole, Set<List<Object>>> matches = new LinkedHashMap<>();
        for (int r = 0; r < roles; r++) {
            NameRole role = NameRoles.byName(ByteBufUtils.readUTF8String(buf));
            int parts = buf.readInt();
            if (parts < 1 || parts > NameRoles.MAX_PARTS) return tooMany(MAX_MATCHES + 1);
            int count = buf.readInt();
            if (count < 0 || count > left) return tooMany(MAX_MATCHES + 1);
            left -= count;

            for (int i = 0; i < count; i++) {
                Object[] values = new Object[parts];
                for (int p = 0; p < parts; p++) {
                    byte type = buf.readByte();
                    if (type == ANY_VALUE) values[p] = NamePart.ANY;
                    else if (type == INT_VALUE) values[p] = buf.readInt();
                    else if (type == TEXT_VALUE) values[p] = ByteBufUtils.readUTF8String(buf);
                    else return tooMany(MAX_MATCHES + 1);
                }
                if (role != null && NameRoles.fits(role, values)) {
                    matches.computeIfAbsent(role, k -> new LinkedHashSet<>())
                        .add(Collections.unmodifiableList(Arrays.asList(values)));
                }
            }
        }
        return new ResolvedNames(matches, Math.max(tooMany, 0));
    }

    private static ResolvedNames tooMany(int count) {
        return new ResolvedNames(Collections.emptyMap(), count);
    }

    // Collects matches. Past MAX_MATCHES it holds nothing and just says how many there were
    public static final class Builder {

        private final Map<NameRole, Set<List<Object>>> matches = new LinkedHashMap<>();

        /** One thing, values in part order, see {@link ShownNames#add} */
        public Builder add(NameRole role, Object... values) {
            if (!NameRoles.fits(role, values)) {
                throw new IllegalArgumentException(
                    Arrays.toString(values) + " don't fit the parts of "
                        + role.getClass()
                            .getSimpleName()
                        + ", "
                        + role.parts()
                        + ".");
            }
            matches.computeIfAbsent(role, k -> new LinkedHashSet<>())
                .add(Collections.unmodifiableList(Arrays.asList(values.clone())));
            return this;
        }

        public Builder add(Class<? extends NameRole> role, Object... values) {
            return add(NameRoles.of(role), values);
        }

        public Builder item(int id, int meta) {
            return add(ItemNames.class, id, meta == ANY_META ? NamePart.ANY : (Object) meta);
        }

        public Builder entity(String registryName) {
            return add(MobNames.class, registryName);
        }

        public Builder achievement(String statID) {
            return add(AchievementNames.class, statID);
        }

        public ResolvedNames build() {
            Map<NameRole, Set<List<Object>>> kept = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<NameRole, Set<List<Object>>> role : matches.entrySet()) {
                Set<List<Object>> things = withoutCovered(role.getValue());
                kept.put(role.getKey(), things);
                count += things.size();
            }
            return count > MAX_MATCHES ? tooMany(count) : new ResolvedNames(kept, 0);
        }

        // A thing is pointless next to one that's the same apart from ANY where it has a value, e.g. an exact meta
        // next to any meta of the same ID
        private static Set<List<Object>> withoutCovered(Set<List<Object>> things) {
            Set<Integer> masks = new LinkedHashSet<>();
            for (List<Object> thing : things) masks.add(anyMask(thing));
            if (masks.size() == 1) return things;

            Set<List<Object>> kept = new LinkedHashSet<>();
            for (List<Object> thing : things) {
                if (!isCovered(things, masks, thing)) kept.add(thing);
            }
            return kept;
        }

        private static boolean isCovered(Set<List<Object>> things, Set<Integer> masks, List<Object> thing) {
            int mask = anyMask(thing);
            for (int wider : masks) {
                if (wider == mask || (wider & mask) != mask) continue;
                List<Object> general = new ArrayList<>(thing);
                for (int i = 0; i < general.size(); i++) if ((wider & (1 << i)) != 0) general.set(i, NamePart.ANY);
                if (things.contains(general)) return true;
            }
            return false;
        }

        // Bit i set when part i is ANY
        private static int anyMask(List<Object> thing) {
            int mask = 0;
            for (int i = 0; i < thing.size(); i++) if (thing.get(i) == NamePart.ANY) mask |= 1 << i;
            return mask;
        }
    }
}

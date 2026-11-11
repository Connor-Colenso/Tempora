package com.colen.tempora.loggers.generic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.NameRoles;

/**
 * The columns the f: filter of /tempora lookup matches in a logger's table, from its {@link NameRole} columns. Worked
 * out and checked once, when the logger registers, so a column that can't work fails then
 */
public final class NameColumns {

    /** The columns one thing is stored in, one per part of its role, in part order */
    public static final class Group {

        public final NameRole role;
        /** As set with Column.group, empty if there's only one of this role */
        public final String name;
        public final List<String> columns;

        private Group(NameRole role, String name, List<String> columns) {
            this.role = role;
            this.name = name;
            this.columns = columns;
        }
    }

    private final List<Group> groups;

    private NameColumns(List<Group> groups) {
        this.groups = groups;
    }

    /** In the order their first column comes in the table */
    public List<Group> groups() {
        return groups;
    }

    /** All of them, in the order they go on the end of the position index */
    public List<String> indexColumns() {
        List<String> columns = new ArrayList<>();
        for (Group group : groups) columns.addAll(group.columns);
        return columns;
    }

    /** Throws if a role can't be made, a column doesn't fit its part, or a group is missing parts */
    public static NameColumns from(String loggerName, List<ColumnDef> columns) {
        // Columns of each group by part index, filled in as they come
        Map<GroupKey, String[]> found = new LinkedHashMap<>();

        for (ColumnDef col : columns) {
            String where = loggerName + "." + col.name;
            if (col.nameRole == NameRole.class) {
                // Most likely a nameRole someone forgot, which would quietly leave the column out of f:
                if (!col.part.isEmpty() || !col.group.isEmpty()) {
                    throw new IllegalStateException(where + " has a part or group but no nameRole.");
                }
                continue;
            }

            NameRole role;
            try {
                role = NameRoles.of(col.nameRole);
            } catch (RuntimeException e) {
                throw new IllegalStateException(where + " has a name role that can't be used.", e);
            }
            List<NamePart> parts = role.parts();
            String roleName = col.nameRole.getSimpleName();

            int index = partIndex(parts, col.part);
            if (index < 0) {
                throw new IllegalStateException(
                    col.part.isEmpty()
                        ? where + " has to say which part of " + roleName + " it is, one of " + parts + "."
                        : where + " is part " + col.part + " but " + roleName + " only has " + parts + ".");
            }

            NamePart part = parts.get(index);
            if (col.columnType != part.type) {
                throw new IllegalStateException(
                    where + " is part "
                        + part.name
                        + " of "
                        + roleName
                        + ", so it must be "
                        + (part.type == ColumnType.INTEGER ? "an int." : "a plain String."));
            }

            String[] slots = found.computeIfAbsent(new GroupKey(role, col.group), k -> new String[parts.size()]);
            if (slots[index] != null) {
                throw new IllegalStateException(
                    loggerName + " has two columns for part "
                        + part.name
                        + " of "
                        + roleName
                        + groupText(col.group)
                        + ", "
                        + slots[index]
                        + " and "
                        + col.name
                        + ". Tell them apart with group.");
            }
            slots[index] = col.name;
        }

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<GroupKey, String[]> entry : found.entrySet()) {
            GroupKey key = entry.getKey();
            String[] slots = entry.getValue();
            List<NamePart> parts = key.role.parts();
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) if (slots[i] == null) missing.add(parts.get(i).name);
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                    loggerName + " has no column for part(s) "
                        + missing
                        + " of "
                        + key.role.getClass()
                            .getSimpleName()
                        + groupText(key.group)
                        + ".");
            }
            groups.add(new Group(key.role, key.group, Collections.unmodifiableList(Arrays.asList(slots))));
        }
        return new NameColumns(Collections.unmodifiableList(groups));
    }

    // An empty part is fine when the role has only one
    private static int partIndex(List<NamePart> parts, String part) {
        if (part.isEmpty()) return parts.size() == 1 ? 0 : -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).name.equals(part)) return i;
        }
        return -1;
    }

    private static String groupText(String group) {
        return group.isEmpty() ? "" : " in group '" + group + "'";
    }

    private static final class GroupKey {

        final NameRole role;
        final String group;

        GroupKey(NameRole role, String group) {
            this.role = role;
            this.group = group;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GroupKey other)) return false;
            return role == other.role && group.equals(other.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(role), group);
        }
    }
}

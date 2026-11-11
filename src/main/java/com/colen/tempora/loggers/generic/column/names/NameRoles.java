package com.colen.tempora.loggers.generic.column.names;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * The one instance of each {@link NameRole} class, made the first time a column (or anything else) asks for it. The
 * client lists the names of every role in here, so it has the roles of every registered logger
 */
public final class NameRoles {

    /** More parts than this and it's not something a player names */
    public static final int MAX_PARTS = 16;

    private static final Map<Class<? extends NameRole>, NameRole> roles = new LinkedHashMap<>();
    // Class name to role, how the client says which role it answers for
    private static final Map<String, NameRole> byName = new LinkedHashMap<>();

    private NameRoles() {}

    /** Throws if the class can't be made or its parts don't make sense */
    public static synchronized NameRole of(Class<? extends NameRole> type) {
        NameRole role = roles.get(type);
        if (role != null) return role;

        if (type == NameRole.class) throw new IllegalArgumentException("NameRole itself means no role.");
        role = make(type);
        check(type, role.parts());

        roles.put(type, role);
        byName.put(type.getName(), role);
        return role;
    }

    // An enum with one constant is that constant, anything else through its no argument constructor
    private static NameRole make(Class<? extends NameRole> type) {
        if (type.isEnum()) {
            NameRole[] constants = type.getEnumConstants();
            if (constants.length != 1) {
                throw new IllegalStateException(type.getName() + " is an enum, so it needs exactly one constant.");
            }
            return constants[0];
        }
        try {
            Constructor<? extends NameRole> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                type.getName() + " needs a constructor with no arguments. A nested class has to be static.",
                e);
        } catch (InstantiationException e) {
            throw new IllegalStateException(type.getName() + " is abstract, so it can't be made.", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(type.getName() + " threw while being made.", e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(type.getName() + " can't be made.", e);
        }
    }

    /** Every role made so far, in the order they were first asked for */
    public static synchronized List<NameRole> known() {
        return new ArrayList<>(roles.values());
    }

    /** By {@link #name}, null if no column here uses it */
    public static synchronized @Nullable NameRole byName(String name) {
        return byName.get(name);
    }

    /** What the packet calls a role */
    public static String name(NameRole role) {
        // A constant with a body is a class of its own, its enum is what columns name
        if (role instanceof Enum<?>constant) return constant.getDeclaringClass()
            .getName();
        return role.getClass()
            .getName();
    }

    /** Whether these values are one thing of this role, see {@link ShownNames#add} */
    public static boolean fits(NameRole role, Object[] values) {
        List<NamePart> parts = role.parts();
        if (values == null || values.length != parts.size()) return false;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || !parts.get(i)
                .fits(values[i])) return false;
        }
        return true;
    }

    private static void check(Class<? extends NameRole> type, List<NamePart> parts) {
        if (parts == null || parts.isEmpty()) throw new IllegalStateException(type.getName() + " has no parts.");
        if (parts.size() > MAX_PARTS) {
            throw new IllegalStateException(type.getName() + " has more than " + MAX_PARTS + " parts.");
        }
        Set<String> names = new HashSet<>();
        for (NamePart part : parts) {
            if (part == null || part.name == null || part.name.isEmpty()) {
                throw new IllegalStateException(type.getName() + " has a part with no name.");
            }
            if (!names.add(part.name)) {
                throw new IllegalStateException(type.getName() + " has two parts called " + part.name + ".");
            }
        }
    }
}

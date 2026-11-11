package com.colen.tempora.loggers.generic.column.names.lookups;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.EntityList;
import net.minecraft.util.StatCollector;

import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.ShownNames;

/**
 * Mobs by EntityList name (e.g. "Zombie"), on a String field. Anything else stored in it, like a player UUID, just
 * never matches
 */
public final class MobNames implements NameRole {

    public static final String NAME = "name";

    private static final List<NamePart> PARTS = Collections.singletonList(NamePart.text(NAME));

    @Override
    public List<NamePart> parts() {
        return PARTS;
    }

    // As EventChat.entityName shows them
    @Override
    public void shownNames(ShownNames add) {
        for (Object object : EntityList.stringToClassMapping.keySet()) {
            String registryName = (String) object;
            String key = "entity." + registryName + ".name";
            add.add(StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : registryName, registryName);
        }
    }
}

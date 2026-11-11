package com.colen.tempora.loggers.generic.column.names;

import java.util.List;

import com.colen.tempora.loggers.generic.column.Column;

/**
 * A kind of thing the f: filter of /tempora lookup finds by the name players see for it, like items, mobs or
 * achievements. Columns holding one are marked with {@link Column#nameRole}, f: then matches events naming something
 * whose shown name has the text in it. Columns with a role get added to the position index so f: stays quick.
 * <p>
 * A thing can be stored in more than one column, each a {@link NamePart}. Items are an ID and a meta, a mob is just its
 * EntityList name. Other mods can add their own by implementing this, e.g. a machine stored as block, meta, tier and
 * mode:
 *
 * <pre>
 *
 * public final class MachineNames implements NameRole {
 *
 *     public static final String BLOCK = "block", META = "meta", TIER = "tier", MODE = "mode";
 *
 *     public List&lt;NamePart&gt; parts() {
 *         return Arrays
 *             .asList(NamePart.integer(BLOCK), NamePart.integer(META), NamePart.integer(TIER), NamePart.text(MODE));
 *     }
 *
 *     public void shownNames(ShownNames add) {
 *         for (Machine m : Machines.all()) add.add(m.getLocalizedName(), m.block, m.meta, m.tier, m.mode);
 *     }
 * }
 *
 * &#64;Column(nameRole = MachineNames.class, part = MachineNames.BLOCK)
 * public int machineBlock;
 * // and one column for each other part
 * </pre>
 *
 * Tempora makes one of each with its no argument constructor (or uses the constant of a one constant enum), the first
 * time a column or a logger's textFilterCondition uses it. Every part needs a column, and when an event names two
 * things of the same role (block change names one before and one after) their columns get told apart with
 * {@link Column#group}. Text stored as players read it, like a command, has no role, override
 * GenericLogger.textFilterCondition with TextFilter.textCondition for that
 */
public interface NameRole {

    /** The columns one of these is stored in. Same order every time, values in {@link #shownNames} follow it */
    List<NamePart> parts();

    /**
     * Gives {@code add} every thing of this kind with the name players see for it, one value per part, in
     * {@link #parts} order. Use {@link NamePart#ANY} for a part any value of fits, e.g. an item without subtypes. Only
     * called on the client, in its current language and again after it changes. Names should be the ones lookup
     * results show, and anything that can't be named should be skipped
     */
    void shownNames(ShownNames add);
}

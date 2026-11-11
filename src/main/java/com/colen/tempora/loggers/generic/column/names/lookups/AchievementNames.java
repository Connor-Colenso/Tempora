package com.colen.tempora.loggers.generic.column.names.lookups;

import java.util.Collections;
import java.util.List;

import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.ShownNames;

/** Achievements by stat ID (e.g. "achievement.mineWood"), on a String field */
public final class AchievementNames implements NameRole {

    public static final String ID = "id";

    private static final List<NamePart> PARTS = Collections.singletonList(NamePart.text(ID));

    @Override
    public List<NamePart> parts() {
        return PARTS;
    }

    // Shown as their name in [ ] (AchievementEventInfo)
    @Override
    public void shownNames(ShownNames add) {
        for (Achievement achievement : AchievementList.achievementList) {
            try {
                String shown = EnumChatFormatting.getTextWithoutFormattingCodes(
                    achievement.func_150951_e()
                        .getUnformattedText());
                if (shown != null && !shown.isEmpty()) add.add(shown, achievement.statId);
            } catch (Exception ignored) {
                // Can't be named, so can't be found by name either
            }
        }
    }
}

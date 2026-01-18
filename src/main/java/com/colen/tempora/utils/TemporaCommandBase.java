package com.colen.tempora.utils;

import net.minecraft.command.CommandBase;
import net.minecraft.util.IChatComponent;

import java.util.List;

public abstract class TemporaCommandBase extends CommandBase {

    public abstract IChatComponent getCommandDescription();

    public abstract IChatComponent getCommandExample();

    public abstract List<IChatComponent> getArgsDescriptions();
}

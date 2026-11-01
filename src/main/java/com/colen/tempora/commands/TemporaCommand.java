package com.colen.tempora.commands;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.TemporaSubcommandGroup;
import com.colen.tempora.commands.region.RegionCheckCommand;
import com.colen.tempora.commands.region.RegionCreateCommand;
import com.colen.tempora.commands.region.RegionListCommand;
import com.colen.tempora.commands.region.RegionRemoveCommand;
import com.colen.tempora.commands.region.RegionSetCommand;
import com.colen.tempora.utils.GenericUtils;

/**
 * The single /tempora command (alias /tem). Everything else is a subcommand, e.g. /tempora lookup or
 * /tempora region set. Run on its own, it lists them.
 */
public class TemporaCommand extends CommandBase {

    public static final String NAME = "tempora";
    private static final List<String> ALIASES = Collections.singletonList("tem");

    // Kept so chat output can build clickable commands for them.
    public static final LookupCommand LOOKUP = new LookupCommand();
    public static final GlobalCommand GLOBAL = new GlobalCommand();
    public static final RegionSetCommand REGION_SET = new RegionSetCommand();

    private static final TemporaSubcommandGroup ROOT = new TemporaSubcommandGroup("");

    static {
        ROOT.add(LOOKUP);
        ROOT.add(GLOBAL);
        ROOT.add(new PlayerCommand());
        ROOT.add(new HomeCommand());
        ROOT.add(new StatusCommand());
        ROOT.add(new SqlCommand());
        ROOT.add(new WandCommand());
        ROOT.add(
            new TemporaSubcommandGroup(
                "region",
                new RegionCreateCommand(),
                new RegionListCommand(),
                new RegionRemoveCommand(),
                REGION_SET,
                new RegionCheckCommand()));
        ROOT.add(new HelpCommand(ROOT));

        // Only for testing explosion logging, so it never exists on a real server.
        if (GenericUtils.isDevEnvironment()) {
            ROOT.add(new ExplodeCommand());
        }
    }

    @Override
    public String getCommandName() {
        return NAME;
    }

    @Override
    public List<String> getCommandAliases() {
        return ALIASES;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + NAME + " help";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (isDisabled(sender)) return;
        ROOT.execute(sender, args);
    }

    /** Whether Tempora is off in config, telling the sender how to turn it on if so. */
    public static boolean isDisabled(ICommandSender sender) {
        if (GenericUtils.shouldTemporaRun()) return false;

        IChatComponent disabled = new ChatComponentTranslation("tempora.command.disabled");
        disabled.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(disabled);
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (!GenericUtils.shouldTemporaRun()) return Collections.emptyList();
        return ROOT.tabComplete(sender, args);
    }
}

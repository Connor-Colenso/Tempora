package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.CommandUtils.ONLY_IN_GAME;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.RenderEventPacket;
import com.colen.tempora.loggers.generic.UndoResponse;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class TemporaUndoRangedCommand extends CommandBase {

    private static final Map<String, List<? extends GenericEventInfo>> PENDING_UNDOS = new ConcurrentHashMap<>();
    private static final Map<String, String> PENDING_UNDOS_LOGGER_NAMES = new ConcurrentHashMap<>();

    public static int MAX_RANGE;

    @Override
    public String getCommandName() {
        return "tempora_undo_ranged";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_undo_ranged <radius> <time> <logger_name>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(new ChatComponentTranslation(ONLY_IN_GAME));
            return;
        }

        if (args.length < 1) throw new WrongUsageException(getCommandUsage(sender));

        // ==== Confirmation path ====
        if (args[0].equalsIgnoreCase("confirm")) {
            handleConfirmation(player, args);
            return;
        }

        // ==== Preview path ====
        if (args.length != 3) throw new WrongUsageException(getCommandUsage(sender));

        int radius = parseInt(sender, args[0]);
        long seconds = TimeUtils.convertToSeconds(args[1]);
        String loggerName = args[2];

        if (radius < 0) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.range.negative"));
            return;
        }

        if (radius > MAX_RANGE) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.range.too_large", MAX_RANGE));
            radius = MAX_RANGE;
        }

        GenericPositionalLogger<?> logger = TemporaLoggerManager.getLogger(loggerName);

        if (!logger.isUndoEnabled()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.not_enabled", loggerName));
            return;
        }

        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - seconds * 1000L);
        String table = logger.getLoggerName();

        // Attempt at optimised SQL query
        String sql = "SELECT t.* FROM " + table
            + " t "
            + "JOIN (SELECT x,y,z,MIN(timestamp) ts FROM "
            + table
            + " WHERE x BETWEEN ?-? AND ?+? AND y BETWEEN ?-? AND ?+? "
            + " AND z BETWEEN ?-? AND ?+? AND dimensionID=? AND timestamp>=? "
            + " GROUP BY x,y,z) oldest "
            + "ON t.x=oldest.x AND t.y=oldest.y AND t.z=oldest.z AND t.timestamp=oldest.ts "
            + "ORDER BY t.timestamp ASC";

        List<? extends GenericEventInfo> results;

        int playerX = (int) player.posX;
        int playerY = (int) player.posY;
        int playerZ = (int) player.posZ;

        try (Connection conn = logger.getDatabaseManager()
            .getReadOnlyConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, playerX);
            ps.setInt(2, radius);
            ps.setInt(3, playerX);
            ps.setInt(4, radius);

            ps.setInt(5, playerY);
            ps.setInt(6, radius);
            ps.setInt(7, playerY);
            ps.setInt(8, radius);

            ps.setInt(9, playerZ);
            ps.setInt(10, radius);
            ps.setInt(11, playerZ);
            ps.setInt(12, radius);

            ps.setInt(13, player.dimension);
            ps.setTimestamp(14, cutoff);

            try (ResultSet rs = ps.executeQuery()) {
                results = logger.generateQueryResults(rs);
            }
        } catch (Exception e) {
            LOG.error("Undo preview DB error", e);
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.generic_fail", e.getMessage()));
            return;
        }

        if (results.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.nothing_to_undo", loggerName));
            return;
        }

        // Send preview markers
        for (GenericEventInfo event : results) {
            NETWORK.sendTo(new RenderEventPacket(event), player);
        }

        // Renders the checker box region on the client.
        // RegionToRender region = new RegionToRender(
        // player.dimension,
        // playerX - radius,
        // playerY - radius,
        // playerZ - radius,
        // playerX + radius + 1,
        // playerY + radius + 1,
        // playerZ + radius + 1,
        // System.currentTimeMillis(),
        // UUID.randomUUID().toString());
        //
        // NETWORK.sendTo(new PacketShowRegionInWorld.RegionMsg(region), player);

        // Store preview results
        String uuid = UUID.randomUUID()
            .toString();
        PENDING_UNDOS.put(uuid, results);
        PENDING_UNDOS_LOGGER_NAMES.put(uuid, loggerName);

        IChatComponent hoverText = new ChatComponentTranslation("tempora.undo.preview.highlight");
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.DARK_RED);

        IChatComponent click = new ChatComponentTranslation("tempora.undo.preview.confirm").setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                .setChatClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tempora_undo_ranged confirm " + uuid)));

        sender.addChatMessage(new ChatComponentTranslation("tempora.undo.preview", click));
    }

    private void handleConfirmation(EntityPlayerMP sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException(getCommandUsage(sender));

        String uuid = args[1];

        List<? extends GenericEventInfo> stored = PENDING_UNDOS.get(uuid);
        String loggerName = PENDING_UNDOS_LOGGER_NAMES.get(uuid);

        if (stored == null || loggerName == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.event_not_found", uuid));
            return;
        }

        GenericPositionalLogger<?> logger = TemporaLoggerManager.getLogger(loggerName);

        if (!logger.isUndoEnabled()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.not_enabled", loggerName));
            return;
        }

        long startMs = System.currentTimeMillis();
        List<UndoResponse> undoResponses = logger.undoEvents(stored, sender);
        long durationMs = System.currentTimeMillis() - startMs;

        int successCounter = 0;
        for (UndoResponse undoResponse : undoResponses) {
            if (!undoResponse.success) {
                sender.addChatMessage(undoResponse.message);
            } else {
                successCounter++;
            }
        }

        IChatComponent successRanged = new ChatComponentTranslation(
            "tempora.undo.success.ranged",
            new ChatComponentNumber(successCounter),
            new ChatComponentNumber(undoResponses.size()),
            new ChatComponentNumber(successCounter * 100.0 / undoResponses.size()),
            new ChatComponentNumber(durationMs),
            new ChatComponentTranslation("time.unit.milliseconds"));

        successRanged.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);

        sender.addChatMessage(successRanged);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
            return CommandUtils.completeLoggerNames(args);
        }
        return null;
    }

    public static void onServerClose() {
        PENDING_UNDOS.clear();
    }
}

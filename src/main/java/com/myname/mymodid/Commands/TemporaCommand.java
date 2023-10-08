package com.myname.mymodid.Commands;

import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.drawable.AdaptableUITexture;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.MultiChildWidget;
import com.gtnewhorizons.modularui.common.widget.TabContainer;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;
import com.myname.mymodid.TemporaUtils;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import java.sql.*;
import java.util.concurrent.TimeUnit;

import static com.gtnewhorizons.modularui.common.widget.textfield.BaseTextFieldWidget.WHOLE_NUMS;

public class TemporaCommand extends CommandBase implements ITileWithModularUI {

    private int serverValue = 0;
    private final String textFieldValue = "";
    private static final AdaptableUITexture DISPLAY = AdaptableUITexture
        .of("modularui:gui/background/display", 143, 75, 2);

    @Override
    public ModularWindow createWindow(UIBuildContext buildContext) {
        ModularWindow.Builder builder = ModularWindow.builder(new Size(176, 272));

        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND).bindPlayerInventory(buildContext.getPlayer());

        return builder
            .widget(
                new TabContainer().addPage(createPage())).build();
    }

    private Widget createPage() {

        int xCoord = 30;
        int yCoord = 25;

        int textboxWidth = 30;
        int textboxHeight = 14;
        int spacing = 4;


        return new MultiChildWidget()
            // Scale factor.
            .addChild(new TextFieldWidget().setGetter(this::getScaleFactor).setSetter(this::setScaleFactor)
                .setPattern(WHOLE_NUMS)
                .setTextColor(Color.WHITE.dark(1)).setTextAlignment(Alignment.Center).setScrollBar()
                .setBackground(DISPLAY.withOffset(-2, -2, 4, 4))
                .setSize(textboxWidth, textboxHeight).setPos(xCoord, 25));

    }

    double scaleFactor;
    private String getScaleFactor() {
        return Double.toString(scaleFactor);
    }
    private void setScaleFactor(String string) {
        try {
            scaleFactor = Double.parseDouble(string);
        } catch (Exception ignored) { }
    }

    @Override
    public String getCommandName() {
        return "tempora";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {



/*        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        int radius = parseInt(sender, args[0]);
        long seconds = parseTime(args[1]);

        queryDatabase(sender, radius, seconds);*/
    }

    private long parseTime(String time) {
        char timeSpecifier = time.charAt(time.length() - 1);
        int value = Integer.parseInt(time.substring(0, time.length() - 1));

        return switch (timeSpecifier) {
            case 's' -> value;
            case 'm' -> TimeUnit.MINUTES.toSeconds(value);
            case 'h' -> TimeUnit.HOURS.toSeconds(value);
            case 'd' -> TimeUnit.DAYS.toSeconds(value);
            default -> throw new IllegalArgumentException("Invalid time format.");
            // Needs better handling.
        };
    }

    private void queryDatabase(ICommandSender sender, int radius, long seconds) {
        try (Connection conn = DriverManager.getConnection(TemporaUtils.databaseDirectory() + "blockBreakEvents.db")) {

            final String sql = "SELECT playerName, blockType, x, y, z, timestamp FROM BlockBreakEvents "
                + "WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ? AND "
                + "timestamp >= datetime(CURRENT_TIMESTAMP, ? || ' seconds')";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, sender.getPlayerCoordinates().posX);
            pstmt.setInt(2, radius);
            pstmt.setInt(3, sender.getPlayerCoordinates().posY);
            pstmt.setInt(4, radius);
            pstmt.setInt(5, sender.getPlayerCoordinates().posZ);
            pstmt.setInt(6, radius);
            pstmt.setLong(7, -seconds); // Negate seconds for the "ago" behavior.

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String result = String.format(
                    "%s broke %s at [%d, %d, %d] on %s",
                    rs.getString("playerName"),
                    rs.getString("blockType"),
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    rs.getString("timestamp"));
                sender.addChatMessage(new ChatComponentText(result));

            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }

}

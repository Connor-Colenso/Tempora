//package com.myname.mymodid.Commands.TrackPlayer;
//
//import com.myname.mymodid.Network.TempName;
//import com.myname.mymodid.Tempora;
//import com.myname.mymodid.TemporaUtils;
//import cpw.mods.fml.common.FMLCommonHandler;
//import cpw.mods.fml.common.eventhandler.SubscribeEvent;
//import cpw.mods.fml.common.gameevent.TickEvent;
//import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
//import net.minecraft.entity.player.EntityPlayerMP;
//import net.minecraft.server.MinecraftServer;
//import net.minecraft.util.ChatComponentText;
//import org.jetbrains.annotations.NotNull;
//
//import java.sql.*;
//import java.util.HashMap;
//
//import static com.myname.mymodid.TemporaUtils.isClientSide;
//
//public class TrackPlayerUpdater {
//
//    static final HashMap<String, String> hashMap = new HashMap<>();
//
//    public TrackPlayerUpdater() {
//        FMLCommonHandler.instance()
//            .bus()
//            .register(this);
//    }
//
//
//    @SubscribeEvent
//    @SuppressWarnings("unused")
//    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
//        // Events are only logged server side every 5 seconds at the start of a tick.
//        if (isClientSide()) return;
//        if (event.phase != TickEvent.Phase.START) return;
//
//        // Trigger this update every 5 seconds.
//        if (FMLCommonHandler.instance()
//            .getMinecraftServerInstance()
//            .getTickCounter() % 100 != 0) return;
//
//        try (Connection conn = DriverManager.getConnection(TemporaUtils.databaseDirectory() + "playerMovementEvents.db")) {
//
//            final MinecraftServer server = MinecraftServer.getServer();
//            final int renderDistance = server.getConfigurationManager().getViewDistance() * 16; // 16 blocks per chunk
//
//            final String sql = "SELECT playerName, x, y, z, timestamp FROM PlayerMovementEvents "
//                + "WHERE playerName = ? AND ABS(x - ?) <= " + renderDistance + " AND ABS(y - ?) <= 255 AND ABS(z - ?) <= " + renderDistance;
//            PreparedStatement pstmt = conn.prepareStatement(sql);
//            pstmt.setString(1, playerName);
//            pstmt.setDouble(2, sender.getPlayerCoordinates().posX);
//            pstmt.setDouble(3, sender.getPlayerCoordinates().posY);
//            pstmt.setDouble(4, sender.getPlayerCoordinates().posZ);
//
//            ResultSet rs = pstmt.executeQuery();
//            sender.addChatMessage(new ChatComponentText("Now tracking player " + playerName + ". Run command again to stop tracking."));
//
//            // We use this firstPacket info to make sure we know we can clear the client side buffer.
//            boolean firstPacket = true;
//            while (rs.next()) {
//                EntityPlayerMP player = (EntityPlayerMP) sender.getEntityWorld().getPlayerEntityByName(sender.getCommandSenderName());
//
//                double x = rs.getDouble("x");
//                double y = rs.getDouble("y");
//                double z = rs.getDouble("z");
//                long timestamp = rs.getLong("timestamp");
//
//                TempName packet = new TempName(x, y, z, timestamp, firstPacket);
//                Tempora.NETWORK.sendTo(packet, player);
//                firstPacket = false;
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//    }
//}

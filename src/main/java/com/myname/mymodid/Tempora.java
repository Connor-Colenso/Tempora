package com.myname.mymodid;

import com.myname.mymodid.Commands.HeatMap.HeatMapCommand;
import com.myname.mymodid.Commands.QueryEventsCommand;
import com.myname.mymodid.Commands.TemporaCommand;
import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerCommand;
import com.myname.mymodid.Loggers.*;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacket;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacketHandler;
import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacket;
import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacketHandler;
import com.myname.mymodid.Commands.TrackPlayer.PlayerTrackerRenderer;
import com.myname.mymodid.Rendering.RenderInWorldDispatcher;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.myname.mymodid.Tags.MODID;

@SuppressWarnings("unused")
@Mod(modid = MODID, version = Tags.VERSION, name = Tags.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class Tempora {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.myname.mymodid.ClientProxy", serverSide = "com.myname.mymodid.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(PlayerPositionPacketHandler.class, PlayerPositionPacket.class, 0, Side.CLIENT);
        NETWORK.registerMessage(HeatMapPacketHandler.class, HeatMapPacket.class, 1, Side.CLIENT);

        new BlockBreakLogger();
        new ExplosionLogger();
        new ItemUseLogger();
        new PlayerMovementLogger();
        new CommandLogger();


        MinecraftForge.EVENT_BUS.register(new RenderInWorldDispatcher());
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
        registerNewCommands(event);
        GenericLogger.onServerStart();
    }

    private void registerNewCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new QueryEventsCommand());
        event.registerServerCommand(new TrackPlayerCommand());
        event.registerServerCommand(new HeatMapCommand());
        event.registerServerCommand(new TemporaCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        PlayerTrackerRenderer.clearBuffer();
        GenericLogger.onServerClose();
    }
}

package com.myname.mymodid;

import static com.myname.mymodid.Tags.MODID;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.myname.mymodid.Commands.HeatMap.HeatMapCommand;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacket;
import com.myname.mymodid.Commands.HeatMap.Network.HeatMapPacketHandler;
import com.myname.mymodid.Commands.LastInDimension;
import com.myname.mymodid.Commands.QueryEventsCommand;
import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacket;
import com.myname.mymodid.Commands.TrackPlayer.Network.PlayerPositionPacketHandler;
import com.myname.mymodid.Commands.TrackPlayer.PlayerTrackerRenderer;
import com.myname.mymodid.Commands.TrackPlayer.TrackPlayerCommand;
import com.myname.mymodid.Items.TemporaScannerItem;
import com.myname.mymodid.Loggers.BlockBreakLogger;
import com.myname.mymodid.Loggers.CommandLogger;
import com.myname.mymodid.Loggers.EntityLogger;
import com.myname.mymodid.Loggers.ExplosionLogger;
import com.myname.mymodid.Loggers.GenericLogger;
import com.myname.mymodid.Loggers.ItemUseLogger;
import com.myname.mymodid.Loggers.PlayerMovementLogger;
import com.myname.mymodid.Rendering.RenderInWorldDispatcher;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

@SuppressWarnings("unused")
@Mod(modid = MODID, version = Tags.VERSION, name = Tags.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class Tempora {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.myname.mymodid.ClientProxy", serverSide = "com.myname.mymodid.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        GameRegistry.registerItem(new TemporaScannerItem(), "tempora_scanner");
        Tempora.LOG.info("I am " + Tags.MODNAME + " at version " + Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(PlayerPositionPacketHandler.class, PlayerPositionPacket.class, 0, Side.CLIENT);
        NETWORK.registerMessage(HeatMapPacketHandler.class, HeatMapPacket.class, 1, Side.CLIENT);

        if (TemporaUtils.isServerSide()) {
            new BlockBreakLogger();
            new ExplosionLogger();
            new ItemUseLogger();
            new PlayerMovementLogger();
            new CommandLogger();
            new EntityLogger();
        }

        if (TemporaUtils.isClientSide()) {
            MinecraftForge.EVENT_BUS.register(new RenderInWorldDispatcher());
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {}

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        registerNewCommands(event);

        if (TemporaUtils.isServerSide()) {
            GenericLogger.onServerStart();
        }
    }

    private void registerNewCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new QueryEventsCommand());
        event.registerServerCommand(new TrackPlayerCommand());
        event.registerServerCommand(new HeatMapCommand());
        event.registerServerCommand(new LastInDimension());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (TemporaUtils.isServerSide()) {
            PlayerTrackerRenderer.clearBuffer();
        }

        if (TemporaUtils.isServerSide())
        {
            GenericLogger.onServerClose();
        }
    }
}

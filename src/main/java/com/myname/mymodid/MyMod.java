package com.myname.mymodid;

import com.myname.mymodid.Commands.TrackPlayerCommand;
import com.myname.mymodid.Network.TempName;
import com.myname.mymodid.Network.TempNameHandler;
import com.myname.mymodid.Rendering.RenderEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.myname.mymodid.Commands.QueryEventsCommand;
import com.myname.mymodid.Loggers.*;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.*;

import static com.myname.mymodid.Tags.MODID;

@SuppressWarnings("unused")
@Mod(modid = MODID, version = Tags.VERSION, name = Tags.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class MyMod {

    // Define your SimpleNetworkWrapper instance
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

        int packetId = 0; // Start with a unique ID and increment for each new packet type
        NETWORK.registerMessage(TempNameHandler.class, TempName.class, packetId++, Side.CLIENT);

        new BlockBreakLogger();
        new ExplosionLogger();
        new ItemUseLogger();
        new PlayerMovementLogger();
        new CommandLogger();

        MinecraftForge.EVENT_BUS.register(new RenderEvent());
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
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        GenericLogger.onServerClose();
    }
}

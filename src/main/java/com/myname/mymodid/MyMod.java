package com.myname.mymodid;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.myname.mymodid.Commands.QueryEventsCommand;
import com.myname.mymodid.Loggers.BlockBreakLogger;
import com.myname.mymodid.Loggers.ExplosionLogger;
import com.myname.mymodid.Loggers.ItemUseLogger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.*;

@SuppressWarnings("unused")
@Mod(modid = Tags.MODID, version = Tags.VERSION, name = Tags.MODNAME, acceptedMinecraftVersions = "[1.7.10]")
public class MyMod {

    public static final Logger LOG = LogManager.getLogger(Tags.MODID);

    @SidedProxy(clientSide = "com.myname.mymodid.ClientProxy", serverSide = "com.myname.mymodid.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    BlockBreakLogger blockBreakLogger;
    ExplosionLogger explosionLogger;
    ItemUseLogger itemUseLogger;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        blockBreakLogger = new BlockBreakLogger();
        explosionLogger = new ExplosionLogger();
        itemUseLogger = new ItemUseLogger();

        // Register the block break logger to capture events
        MinecraftForge.EVENT_BUS.register(blockBreakLogger);
        MinecraftForge.EVENT_BUS.register(explosionLogger);
        MinecraftForge.EVENT_BUS.register(itemUseLogger);
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
        event.registerServerCommand(new QueryEventsCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (blockBreakLogger != null) {
            blockBreakLogger.closeDatabase();
        }

        if (explosionLogger != null) {
            explosionLogger.closeDatabase();
        }

        if (itemUseLogger != null) {
            itemUseLogger.closeDatabase();
        }
    }
}

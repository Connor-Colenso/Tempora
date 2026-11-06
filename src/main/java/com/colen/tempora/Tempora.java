package com.colen.tempora;

import static com.colen.tempora.utils.GenericUtils.shouldTemporaRun;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.colen.tempora.blocks.RenderingErrorBlock;
import com.colen.tempora.blocks.stress.StressTestBlock;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.events.OnPlayerLogin;
import com.colen.tempora.events.ServerTickHandler;
import com.colen.tempora.items.TemporaWand;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.rendering.ClientRegionStore;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@SuppressWarnings("unused")
// GTNHLib's chat components and config are used on both sides, so a pack without a recent enough one gets FML's
// missing mod screen rather than a crash later on.
@Mod(
    modid = Tempora.MODID,
    version = Tags.VERSION,
    name = Tempora.MODNAME,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gtnhlib@[0.11.52,);after:appliedenergistics2;after:NotEnoughItems")
public class Tempora {

    public static final String MODID = "tempora";
    public static final String MODNAME = "Tempora";

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.colen.tempora.ClientProxy", serverSide = "com.colen.tempora.CommonProxy")
    public static CommonProxy proxy;

    public static Block renderingErrorBlock = new RenderingErrorBlock();
    public static Block stressTestBlock = new StressTestBlock();
    public static Item temporaWand = new TemporaWand();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTickHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(new OnPlayerLogin());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Even while Tempora is off, so /tempora can say how to turn it on.
        registerNewCommands(event);

        if (!shouldTemporaRun()) {
            LOG.info("Tempora is disabled in config/tempora/tempora.cfg, so nothing will be logged.");
            return;
        }

        // Before any logger starts, as loggers check regions for every event.
        TemporaRegionRegistry.load();

        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            logger.registerEvent();
        }

        GenericLogger.onServerStart();
    }

    private void registerNewCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new TemporaCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        // Before the loggers close their databases, as it reads them.
        TemporaQueries.onServerClose();

        GenericLogger.onServerClose();

        TemporaRegionRegistry.unload();

        ClientRegionStore.onServerClose();
    }
}

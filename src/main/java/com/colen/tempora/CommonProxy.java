package com.colen.tempora;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.Tempora.renderingErrorBlock;
import static com.colen.tempora.Tempora.stressTestBlock;

import net.minecraftforge.common.MinecraftForge;

import com.colen.tempora.blocks.RenderingErrorItemBlock;
import com.colen.tempora.blocks.stress.StressTestTileEntity;
import com.colen.tempora.chat.TemporaChatRegistry;
import com.colen.tempora.config.CommandsConfig;
import com.colen.tempora.config.Config;
import com.colen.tempora.events.OnWorldLoad;
import com.colen.tempora.loggers.generic.GenericRenderEventPacketHandler;
import com.colen.tempora.loggers.generic.RenderEventPacket;
import com.colen.tempora.networking.handlers.PacketRemoveClientRegionByUUID;
import com.colen.tempora.networking.handlers.TeleportRequestHandler;
import com.colen.tempora.networking.packets.PacketRemoveRegionFromClient;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.networking.packets.PacketTeleportRequest;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.WorldGenTicks;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // First so Config.enabled is known from preInit on
        Config.load(event.getModConfigurationDirectory());
        ConfigurationManager.registerConfig(CommandsConfig.class);
        GameRegistry.registerItem(Tempora.temporaWand, "admin_wand");
        GameRegistry.registerBlock(renderingErrorBlock, RenderingErrorItemBlock.class, "rendering_error_block");

        if (GenericUtils.isDevEnvironment()) {
            GameRegistry.registerBlock(stressTestBlock, "stressTestBlock");
            GameRegistry.registerTileEntity(StressTestTileEntity.class, "tempora:stressTestTileEntity");
        }

        TemporaChatRegistry.register();
    }

    public void init(FMLInitializationEvent event) {

        NETWORK.registerMessage(GenericRenderEventPacketHandler.class, RenderEventPacket.class, 1, Side.CLIENT);

        NETWORK.registerMessage(
            PacketShowRegionInWorld.RegionMsg.Handler.class,
            PacketShowRegionInWorld.RegionMsg.class,
            2,
            Side.CLIENT);

        NETWORK
            .registerMessage(PacketRemoveClientRegionByUUID.class, PacketRemoveRegionFromClient.class, 3, Side.CLIENT);

        NETWORK.registerMessage(TeleportRequestHandler.class, PacketTeleportRequest.class, 4, Side.SERVER);

        TemporaEvents.registerAll();

        // Each logger loads its own config. Loggers other mods register later read theirs when they register
        TemporaLoggerManager.loadConfig();

        MinecraftForge.EVENT_BUS.register(new OnWorldLoad());
        MinecraftForge.EVENT_BUS.register(Tempora.temporaWand);
        MinecraftForge.EVENT_BUS.register(new WorldGenTicks.ChunkSaves());
    }

    public void postInit(FMLPostInitializationEvent event) {

    }
}

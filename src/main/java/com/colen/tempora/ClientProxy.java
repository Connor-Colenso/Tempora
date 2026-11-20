package com.colen.tempora;

import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import com.colen.tempora.config.ClientConfig;
import com.colen.tempora.config.DebugConfig;
import com.colen.tempora.loggers.achievement.AchievementRenderer;
import com.colen.tempora.loggers.block_change.BlockChangeRenderer;
import com.colen.tempora.loggers.command.CommandRenderer;
import com.colen.tempora.loggers.entity_death.EntityDeathRenderer;
import com.colen.tempora.loggers.entity_position.EntityPositionRenderer;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnRenderer;
import com.colen.tempora.loggers.explosion.ExplosionRenderer;
import com.colen.tempora.loggers.inventory.InventoryRenderer;
import com.colen.tempora.loggers.item_pickup_drop.ItemPickupDropRenderer;
import com.colen.tempora.loggers.item_use.ItemUseRenderer;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakRenderer;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceRenderer;
import com.colen.tempora.loggers.player_death.PlayerDeathRenderer;
import com.colen.tempora.loggers.player_position.PlayerPositionRenderer;
import com.colen.tempora.rendering.RenderIDsInWorld;
import com.colen.tempora.rendering.events.EventRenderers;
import com.colen.tempora.rendering.events.RenderEventsInWorld;
import com.colen.tempora.rendering.regions.HudRenderRegionsInWorld;
import com.colen.tempora.rendering.regions.RenderRegionsInWorld;
import com.colen.tempora.utils.ClientNameIndex;
import com.colen.tempora.utils.ServerVersionCheck;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("unused")
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        ConfigurationManager.registerConfig(ClientConfig.class);
        ConfigurationManager.registerConfig(DebugConfig.class);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        registerEventRenderers();

        // Answers f: filter requests from the server, tick events are on FML's bus
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientNameIndex());
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerVersionCheck());
        MinecraftForge.EVENT_BUS.register(new RenderEventsInWorld());
        MinecraftForge.EVENT_BUS.register(new RenderRegionsInWorld());
        MinecraftForge.EVENT_BUS.register(new HudRenderRegionsInWorld());

        // Debug only
        MinecraftForge.EVENT_BUS.register(new RenderIDsInWorld());

        MinecraftForge.EVENT_BUS.register(this);
    }

    // The error block isn't registered so the block atlas doesn't load its icon for it
    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) Tempora.renderingErrorBlock.registerBlockIcons(event.map);
    }

    // How each of our positional loggers draws its events. Runs after the loggers are registered in CommonProxy.init
    private static void registerEventRenderers() {
        EventRenderers.register(TemporaEvents.achievementLogger, new AchievementRenderer());
        EventRenderers.register(TemporaEvents.blockChangeLogger, new BlockChangeRenderer());
        EventRenderers.register(TemporaEvents.commandLogger, new CommandRenderer());
        EventRenderers.register(TemporaEvents.entityDeathLogger, new EntityDeathRenderer());
        EventRenderers.register(TemporaEvents.entityPositionLogger, new EntityPositionRenderer());
        EventRenderers.register(TemporaEvents.entitySpawnLogger, new EntitySpawnRenderer());
        EventRenderers.register(TemporaEvents.explosionLogger, new ExplosionRenderer());
        EventRenderers.register(TemporaEvents.inventoryLogger, new InventoryRenderer());
        EventRenderers.register(TemporaEvents.itemPickupDropLogger, new ItemPickupDropRenderer());
        EventRenderers.register(TemporaEvents.itemUseLogger, new ItemUseRenderer());
        EventRenderers.register(TemporaEvents.playerBlockBreakLogger, new PlayerBlockBreakRenderer());
        EventRenderers.register(TemporaEvents.playerBlockPlaceLogger, new PlayerBlockPlaceRenderer());
        EventRenderers.register(TemporaEvents.playerDeathLogger, new PlayerDeathRenderer());
        EventRenderers.register(TemporaEvents.playerPositionLogger, new PlayerPositionRenderer());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}

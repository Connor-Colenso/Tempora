package com.colen.tempora;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.colen.tempora.config.ClientConfig;
import com.colen.tempora.config.DebugConfig;
import com.colen.tempora.loggers.block_change.BlockChangeRenderer;
import com.colen.tempora.loggers.command.CommandRenderer;
import com.colen.tempora.loggers.entity_death.EntityDeathRenderer;
import com.colen.tempora.loggers.entity_position.EntityPositionRenderer;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnRenderer;
import com.colen.tempora.loggers.explosion.ExplosionRenderer;
import com.colen.tempora.loggers.inventory.InventoryRenderer;
import com.colen.tempora.loggers.item_use.ItemUseRenderer;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakRenderer;
import com.colen.tempora.loggers.player_block_place.PlayerBlockPlaceRenderer;
import com.colen.tempora.loggers.player_position.PlayerPositionRenderer;
import com.colen.tempora.rendering.RenderIDsInWorld;
import com.colen.tempora.rendering.events.EventRenderers;
import com.colen.tempora.rendering.events.RenderEventsInWorld;
import com.colen.tempora.rendering.regions.HudRenderRegionsInWorld;
import com.colen.tempora.rendering.regions.RenderRegionsInWorld;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import codechicken.nei.api.API;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

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

        MinecraftForge.EVENT_BUS.register(new RenderEventsInWorld());
        MinecraftForge.EVENT_BUS.register(new RenderRegionsInWorld());
        MinecraftForge.EVENT_BUS.register(new HudRenderRegionsInWorld());

        // Debug only.
        MinecraftForge.EVENT_BUS.register(new RenderIDsInWorld());
    }

    // How each of Tempora's positional loggers draws its events. Registered after the loggers, in CommonProxy.init.
    private static void registerEventRenderers() {
        EventRenderers.register(TemporaEvents.blockChangeLogger, new BlockChangeRenderer());
        EventRenderers.register(TemporaEvents.commandLogger, new CommandRenderer());
        EventRenderers.register(TemporaEvents.entityDeathLogger, new EntityDeathRenderer());
        EventRenderers.register(TemporaEvents.entityPositionLogger, new EntityPositionRenderer());
        EventRenderers.register(TemporaEvents.entitySpawnLogger, new EntitySpawnRenderer());
        EventRenderers.register(TemporaEvents.explosionLogger, new ExplosionRenderer());
        EventRenderers.register(TemporaEvents.inventoryLogger, new InventoryRenderer());
        EventRenderers.register(TemporaEvents.itemUseLogger, new ItemUseRenderer());
        EventRenderers.register(TemporaEvents.playerBlockBreakLogger, new PlayerBlockBreakRenderer());
        EventRenderers.register(TemporaEvents.playerBlockPlaceLogger, new PlayerBlockPlaceRenderer());
        EventRenderers.register(TemporaEvents.playerPositionLogger, new PlayerPositionRenderer());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        // NEI is optional: without it, there is no item list to hide the block from.
        if (Loader.isModLoaded("NotEnoughItems")) {
            API.hideItem(new ItemStack(Tempora.renderingErrorBlock));
        }
    }
}

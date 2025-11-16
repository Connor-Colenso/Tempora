package com.colen.tempora;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.colen.tempora.events.PlayerLogin;
import com.colen.tempora.rendering.RenderEventsInWorld;
import com.colen.tempora.rendering.RenderRegionsInWorld;

import codechicken.nei.api.API;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@SuppressWarnings("unused")
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        MinecraftForge.EVENT_BUS.register(new PlayerLogin());
        MinecraftForge.EVENT_BUS.register(new RenderEventsInWorld());
        MinecraftForge.EVENT_BUS.register(new RenderRegionsInWorld());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        API.hideItem(new ItemStack(Tempora.renderingErrorBlock));
    }
}

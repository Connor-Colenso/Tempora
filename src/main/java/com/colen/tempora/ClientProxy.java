package com.colen.tempora;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.colen.tempora.events.PlayerLogin;
import com.colen.tempora.rendering.RenderEventsInWorld;
import com.colen.tempora.rendering.RenderIDsInWorld;
import com.colen.tempora.rendering.RenderRegionsInWorld;
import com.colen.tempora.utils.RenderingUtils;

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
        MinecraftForge.EVENT_BUS.register(new RenderIDsInWorld());
        MinecraftForge.EVENT_BUS.register(new RenderRegionsInWorld());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        API.hideItem(new ItemStack(Tempora.renderingErrorBlock));

        RenderingUtils.CLIENT_EVENT_RENDER_DISTANCE = Tempora.config.getInt(
            "Client event render distance",
            "Client Rendering",
            10,
            4,
            256,
            "This is the maximum range at which events will render for you, be warned, setting this to a high number may cause stability issues when previewing undo operations due to the large number of events inside.");
    }
}

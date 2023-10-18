package com.myname.mymodid.Items;

import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.UIInfos;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.IItemWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.CycleButtonWidget;
import com.gtnewhorizons.modularui.common.widget.MultiChildWidget;
import com.gtnewhorizons.modularui.common.widget.TabContainer;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.gtnewhorizons.modularui.common.widget.VanillaButtonWidget;
import com.myname.mymodid.TemporaUtils;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class TemporaScannerItem extends Item implements IItemWithModularUI {

    public TemporaScannerItem() {
        super();

        // Max stack size. Common values are 1 for tools/weapons, 16 for special items, and 64 for most other items.
        this.setMaxStackSize(1);

        // Set the creative tab for this item.
        this.setCreativeTab(CreativeTabs.tabTools); // Adjust this to whatever tab you want the item to appear in.

        // Set the unlocalized and registry name for this item.
        this.setUnlocalizedName("tempora_scanner"); // This is used for localization.
    }

    @Override
    public ModularWindow createWindow(UIBuildContext buildContext, ItemStack stack) {
        ModularWindow.Builder builder = ModularWindow.builder(new Size(176, 272));

        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND)
            .bindPlayerInventory(buildContext.getPlayer());

        return builder.widget(new TabContainer().addPage(createPage())).build();
    }

    // Event variables and getters/setters
    private boolean requestEntityMovement = false;
    private boolean requestEntityDeath = false;
    private boolean requestBlockBreaks = false;
    private boolean requestBlockPlace = false;
    private boolean requestCommands = false;
    private boolean requestPlayerMovement = false;
    private boolean requestPlayerDeath = false;
    private boolean requestExplosions = false;
    private boolean requestItemUse = false;

    private void setEventState(String eventName, int value) {
        switch (eventName) {
            case "EntityMovement" -> requestEntityMovement = value != 0;
            case "EntityDeath" -> requestEntityDeath = value != 0;
            case "BlockBreaks" -> requestBlockBreaks = value != 0;
            case "BlockPlace" -> requestBlockPlace = value != 0;
            case "Commands" -> requestCommands = value != 0;
            case "PlayerMovement" -> requestPlayerMovement = value != 0;
            case "PlayerDeath" -> requestPlayerDeath = value != 0;
            case "Explosions" -> requestExplosions = value != 0;
            case "ItemUse" -> requestItemUse = value != 0;
        }
    }

    private int getEventState(String eventName) {
        return switch (eventName) {
            case "Entity Movement" -> requestEntityMovement ? 1 : 0;
            case "Entity Death" -> requestEntityDeath ? 1 : 0;
            case "Block Breaks" -> requestBlockBreaks ? 1 : 0;
            case "Block Place" -> requestBlockPlace ? 1 : 0;
            case "Commands" -> requestCommands ? 1 : 0;
            case "Player Movement" -> requestPlayerMovement ? 1 : 0;
            case "Player Death" -> requestPlayerDeath ? 1 : 0;
            case "Explosions" -> requestExplosions ? 1 : 0;
            case "Item Use" -> requestItemUse ? 1 : 0;
            default -> 0;
        };
    }

    static final String[] eventNames = {
        "Entity Movement",
        "Entity Death",
        "Block Breaks",
        "Block Place",
        "Commands",
        "Player Movement",
        "Player Death",
        "Explosions",
        "Item Use"
    };


    private Widget createPage() {

        MultiChildWidget mcw = new MultiChildWidget();

        // Entity Movement
        // Entity Death
        // Block breaks
        // Block place
        // Commands
        // Player Movement
        // Player Death
        // Explosions
        // Item use

        for (int i = 0; i < eventNames.length; i++) {
            final int eventIndex = i;
            mcw.addChild(new CycleButtonWidget().setLength(2)
                .setSetter(value -> setEventState(eventNames[eventIndex], value))
                .setGetter(() -> getEventState(eventNames[eventIndex]))
                .setTexture(UITexture.fullImage("tempora", "gui/button"))
                .addTooltip(
                    0,
                    "Not selected")
                .addTooltip(
                    1,
                    "Selected")
                .setTooltipHasSpaceAfterFirstLine(false).setPos(new Pos2d(8, 8 + 20 * i)));

            mcw.addChild(new TextWidget(eventNames[eventIndex]).setPos(new Pos2d(30, 15 + 20 * i)));
        }

        // Do other CycleButtonWidgets here


        mcw.addChild(
        new VanillaButtonWidget()
            .setDisplayString("Submit")
            .setOnClick((clickData, widget) -> {
                if (!widget.isClient()) {
                    // Send packet here.
                    widget.getContext().getPlayer().addChatMessage(
                        new ChatComponentText("Internal Name: " + widget.getInternalName()));


                }
            }).setPos(176-32-2, 272-18).setSize(32, 16).setInternalName("debug"));


        return mcw;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemStackIn, World world, EntityPlayer player) {
        if (TemporaUtils.isClientSide()) {
            UIInfos.PLAYER_HELD_ITEM_UI
                .open(player, world, Vec3.createVectorHelper(10, 10, 10));
        }
        return super.onItemRightClick(itemStackIn, world, player);
    }
}

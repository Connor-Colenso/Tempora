package com.myname.mymodid.Items;

import static com.gtnewhorizons.modularui.common.widget.textfield.BaseTextFieldWidget.WHOLE_NUMS;

import com.gtnewhorizons.modularui.api.UIInfos;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.math.CrossAxisAlignment;
import com.gtnewhorizons.modularui.api.math.MainAxisAlignment;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.CycleButtonWidget;
import com.gtnewhorizons.modularui.common.widget.Row;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.myname.mymodid.TemporaUtils;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.drawable.AdaptableUITexture;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.IItemWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.MultiChildWidget;
import com.gtnewhorizons.modularui.common.widget.TabContainer;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;
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

    private static final AdaptableUITexture DISPLAY = AdaptableUITexture
        .of("modularui:gui/background/display", 143, 75, 2);

    @Override
    public ModularWindow createWindow(UIBuildContext buildContext, ItemStack stack) {
        ModularWindow.Builder builder = ModularWindow.builder(new Size(176, 272));

        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND)
            .bindPlayerInventory(buildContext.getPlayer());

        return builder.widget(new TabContainer().addPage(createPage()))
            .build();
    }
    private int serverValue = 0;

    private Widget createPage() {

        return new MultiChildWidget()
            .addChild(                new CycleButtonWidget().setLength(2).setGetter(() -> serverValue)
                .setSetter(val -> this.serverValue = val)
                .setTexture(UITexture.fullImage("tempora", "gui/button"))
                .addTooltip(
                    0,
                    "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum.")
                .addTooltip(
                    1,
                    "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.")
                .setTooltipHasSpaceAfterFirstLine(false).setPos(new Pos2d(68, 0)));

    }

    double scaleFactor;

    private String getScaleFactor() {
        return Double.toString(scaleFactor);
    }

    private void setScaleFactor(String string) {
        try {
            scaleFactor = Double.parseDouble(string);
        } catch (Exception ignored) {}
    }

    double xAngle;

    private String getxAngle() {
        return Double.toString(xAngle);
    }

    private void setxAngle(String string) {
        try {
            xAngle = Double.parseDouble(string);
        } catch (Exception ignored) {}
    }

    double yAngle;

    private String getyAngle() {
        return Double.toString(yAngle);
    }

    private void setyAngle(String string) {
        try {
            yAngle = Double.parseDouble(string);
        } catch (Exception ignored) {}
    }

    double zAngle;

    private String getzAngle() {
        return Double.toString(zAngle);
    }

    private void setzAngle(String string) {
        try {
            zAngle = Double.parseDouble(string);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onItemUse(ItemStack p_77648_1_, EntityPlayer player, World world, int p_77648_4_, int p_77648_5_, int p_77648_6_, int p_77648_7_, float p_77648_8_, float p_77648_9_, float p_77648_10_) {
        if (TemporaUtils.isClientSide()) {
            UIInfos.PLAYER_HELD_ITEM_UI
                .open(player, world, Vec3.createVectorHelper(10, 10, 10));
        }
        return true;

    }
}

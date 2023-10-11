package com.myname.mymodid.Items;

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
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.NotNull;

import static com.gtnewhorizons.modularui.common.widget.textfield.BaseTextFieldWidget.WHOLE_NUMS;

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

    private int serverValue = 0;
    private final String textFieldValue = "";
    private static final AdaptableUITexture DISPLAY = AdaptableUITexture
        .of("modularui:gui/background/display", 143, 75, 2);

    @Override
    public ModularWindow createWindow(UIBuildContext buildContext, ItemStack stack) {
        ModularWindow.Builder builder = ModularWindow.builder(new Size(176, 272));

        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND).bindPlayerInventory(buildContext.getPlayer());

        return builder
            .widget(
                new TabContainer().addPage(createPage())).build();
    }

    private Widget createPage() {

        int xCoord = 30;
        int yCoord = 25;

        int textboxWidth = 30;
        int textboxHeight = 14;
        int spacing = 4;


        return new MultiChildWidget()
            // Scale factor.
            .addChild(new TextFieldWidget().setGetter(this::getScaleFactor).setSetter(this::setScaleFactor)
                .setPattern(WHOLE_NUMS)
                .setTextColor(Color.WHITE.dark(1)).setTextAlignment(Alignment.Center).setScrollBar()
                .setBackground(DISPLAY.withOffset(-2, -2, 4, 4))
                .setSize(textboxWidth, textboxHeight).setPos(xCoord, 25))

            // Rotations.
            .addChild(new TextFieldWidget().setGetter(this::getxAngle).setSetter(this::setxAngle)
                .setPattern(WHOLE_NUMS)
                .setTextColor(Color.WHITE.dark(1)).setTextAlignment(Alignment.Center).setScrollBar()
                .setBackground(DISPLAY.withOffset(-2, -2, 4, 4))
                .setSize(textboxWidth, textboxHeight).setPos(xCoord, 25+(textboxHeight+spacing)*2))
            .addChild(new TextFieldWidget().setGetter(this::getyAngle).setSetter(this::setyAngle)
                .setPattern(WHOLE_NUMS)
                .setTextColor(Color.WHITE.dark(1)).setTextAlignment(Alignment.Center).setScrollBar()
                .setBackground(DISPLAY.withOffset(-2, -2, 4, 4))
                .setSize(textboxWidth, textboxHeight).setPos(xCoord, 25+(textboxHeight+spacing)*3))
            .addChild(new TextFieldWidget().setGetter(this::getzAngle).setSetter(this::setzAngle)
                .setPattern(WHOLE_NUMS)
                .setTextColor(Color.WHITE.dark(1)).setTextAlignment(Alignment.Center).setScrollBar()
                .setBackground(DISPLAY.withOffset(-2, -2, 4, 4))
                .setSize(textboxWidth, textboxHeight).setPos(xCoord, 25+(textboxHeight+spacing)*4));
    }

    double scaleFactor;
    private String getScaleFactor() {
        return Double.toString(scaleFactor);
    }
    private void setScaleFactor(String string) {
        try {
            scaleFactor = Double.parseDouble(string);
        } catch (Exception ignored) { }
    }
    double xAngle;
    private String getxAngle() {
        return Double.toString(xAngle);
    }
    private void setxAngle(String string) {
        try {
            xAngle = Double.parseDouble(string);
        } catch (Exception ignored) { }
    }

    double yAngle;
    private String getyAngle() {
        return Double.toString(yAngle);
    }
    private void setyAngle(String string) {
        try {
            yAngle = Double.parseDouble(string);
        } catch (Exception ignored) { }
    }

    double zAngle;
    private String getzAngle() {
        return Double.toString(zAngle);
    }
    private void setzAngle(String string) {
        try {
            zAngle = Double.parseDouble(string);
        } catch (Exception ignored) { }
    }


}

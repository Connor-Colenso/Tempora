package com.colen.tempora.chat;

import net.minecraft.block.Block;
import net.minecraft.event.HoverEvent;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentBuffer;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;

// Block or item name from ID and meta, worked out on the client so it's in the player's language. Loads of modded items
// like GT machines name themselves through getItemStackDisplayName instead of a translation key, so a key alone can't
// always be localised. With an item hover (EventChat.item) the stack comes from that, so names that depend on NBT
// (Thaumcraft wands, renamed items) come out right. Colours in the name are taken out, the line decides the colour
public class ChatComponentBlockName extends AbstractChatComponentBuffer<ChatComponentBlockName> {

    private int id;
    private int meta;

    // Chat draws every line every frame, and reading the hover's NBT each time adds up
    private String cachedText;
    private String cachedLanguage;

    public ChatComponentBlockName(int id, int meta) {
        this.id = id;
        this.meta = meta;
    }

    public ChatComponentBlockName() {}

    // Client side
    @Override
    public String getUnformattedTextForChat() {
        String language = StatCollector.translateToLocal("language.code");
        if (cachedText == null || !language.equals(cachedLanguage)) {
            cachedText = name();
            cachedLanguage = language;
        }
        return cachedText;
    }

    private String name() {
        // 0 is always vanilla air
        if (id == 0) return StatCollector.translateToLocal("tempora.block.air");

        Item item = Item.getItemById(id);
        Block block = null;
        if (item == null) {
            block = Block.getBlockById(id);
            if (block != null) item = Item.getItemFromBlock(block);
        }

        // Unknown IDs come back as air
        if (item == null && (block == null || block == Blocks.air)) return "[unknown ID " + id + "]";

        try {
            if (item == null) return EventChat.withoutFormatting(nameWithoutItem(block));
            ItemStack stack = hoverStack();
            if (stack == null) stack = new ItemStack(item, 1, meta);
            return EventChat.withoutFormatting(stack.getDisplayName());
        } catch (Exception e) {
            return "[" + id + ":" + meta + "]";
        }
    }

    // The stack in an item hover on this, null if there isn't one
    private @Nullable ItemStack hoverStack() {
        HoverEvent hover = getChatStyle().getChatHoverEvent();
        if (hover == null || hover.getAction() != HoverEvent.Action.SHOW_ITEM) return null;
        try {
            NBTBase nbt = JsonToNBT.func_150315_a(
                hover.getValue()
                    .getUnformattedText());
            return nbt instanceof NBTTagCompound tag ? ItemStack.loadItemStackFromNBT(tag) : null;
        } catch (NBTException e) {
            return null;
        }
    }

    // Doors, beds, wires, crops etc have no item of their own. Its translation if it has one, otherwise the mod that
    // owns it (a Forge Multipart block is any part), otherwise its registry name
    public static String nameWithoutItem(Block block) {
        String translated = block.getLocalizedName();
        if (!translated.equals(block.getUnlocalizedName() + ".name")) return translated;

        GameRegistry.UniqueIdentifier owner = GameRegistry.findUniqueIdentifierFor(block);
        ModContainer mod = owner == null ? null
            : Loader.instance()
                .getIndexedModList()
                .get(owner.modId);
        return mod != null ? mod.getName() : Block.blockRegistry.getNameForObject(block);
    }

    @Override
    public void encode(PacketBuffer buf) {
        buf.writeInt(id);
        buf.writeInt(meta);
    }

    @Override
    public void decode(PacketBuffer buf) {
        id = buf.readInt();
        meta = buf.readInt();
    }

    @Override
    public String getID() {
        return "tempora:ChatComponentBlockName";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentBlockName(id, meta);
    }
}

package com.colen.tempora.Mixins;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public final class TemporaLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

//    @Override
//    public String getMixinConfig() {
//        return "mixins.postea.early.json";
//    }
//
//    @Override
//    public List<String> getMixins(Set<String> loadedCoreMods) {
//        final List<String> mixins = new ArrayList<>();
//        mixins.add("MixinItemStack");
//        mixins.add("MixinChunk");
//        mixins.add("MixinAnvilChunkLoader");
//        return mixins;
//    }
}

package com.colen.tempora.loggers.block_change;

public class SetBlockEventInfo {

    public int beforeBlockID;
    public int beforeMeta;
    public int beforePickBlockID;
    public int beforePickBlockMeta;
    public String beforeEncodedNBT;

    public int afterBlockID;
    public int afterMeta;
    public int afterPickBlockID;
    public int afterPickBlockMeta;
    public String afterEncodedNBT;

    // This is for event tracking internally, and not to be transmitted to the client.
    public long worldTick;
    public boolean isWorldGen;

    public boolean isNoOp() {
        return beforeBlockID == afterBlockID && beforeMeta == afterMeta;
    }
}

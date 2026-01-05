package com.colen.tempora.enums;

public enum LoggerEventType {

    ForgeEvent,
    MinecraftEvent,
    None;

    public boolean shouldRegister() {
        return this != None;
    }
}

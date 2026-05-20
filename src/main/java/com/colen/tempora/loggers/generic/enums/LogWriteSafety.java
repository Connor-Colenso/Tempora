package com.colen.tempora.loggers.generic.enums;

public enum LogWriteSafety {
    NORMAL, // WAL+FULL sync (SQLite default)
    HIGH_RISK // WAL, synchronous=OFF, autocheckpoint large
}

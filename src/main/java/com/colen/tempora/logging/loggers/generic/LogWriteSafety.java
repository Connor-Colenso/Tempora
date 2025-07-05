package com.colen.tempora.logging.loggers.generic;

public enum LogWriteSafety {
    NORMAL, // WAL+FULL sync (SQLite default)
    HIGH_RISK // WAL, synchronous=OFF, autocheckpoint large
}

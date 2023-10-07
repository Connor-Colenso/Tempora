package com.myname.mymodid.Loggers;

import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class GenericLogger {

    public static final List<Connection> databaseList = new ArrayList<>();
    private static final List<GenericLogger> loggerList = new ArrayList<>();
    protected Connection conn;

    public GenericLogger() {
        MinecraftForge.EVENT_BUS.register(this);
        loggerList.add(this);
    }

    public static void onServerStart() {
        for (@NotNull final GenericLogger logger : loggerList) {
            Connection conn = logger.initDatabase();
            databaseList.add(conn);
        }
    }

    public static void onServerClose() {
        for (@NotNull final Connection conn : databaseList) {
            try {
                conn.close();
            } catch (SQLException exception) {
                System.out.println("Critical exception, could not close Tempora databases properly.");
                exception.printStackTrace();
            }
        }
    }

    public abstract Connection initDatabase();
    protected abstract String databaseURL();

}

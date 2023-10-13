package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.NotNull;

public abstract class GenericLogger {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static final List<Connection> databaseList = new ArrayList<>();
    protected static final List<GenericLogger> loggerList = new ArrayList<>();
    protected Connection conn;

    public GenericLogger() {
        MinecraftForge.EVENT_BUS.register(this);
        loggerList.add(this);
    }

    public static void onServerStart() {
        for (@NotNull
        final GenericLogger logger : loggerList) {
            Connection conn = logger.initDatabase();
            databaseList.add(conn);
        }
    }

    public static void onServerClose() {
        for (@NotNull
        final Connection conn : databaseList) {
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

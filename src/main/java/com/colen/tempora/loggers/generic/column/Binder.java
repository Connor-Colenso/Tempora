package com.colen.tempora.loggers.generic.column;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface Binder {
    void bind(PreparedStatement ps, int index, Object value) throws SQLException;
}

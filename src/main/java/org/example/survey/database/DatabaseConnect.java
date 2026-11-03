package org.example.survey.database;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseConnect {

    private static volatile DataSource dataSource;

    public DatabaseConnect(DataSource dataSource) {
        DatabaseConnect.dataSource = dataSource;
    }

    public static Connection getConnection() throws SQLException {
        DataSource ds = dataSource;
        if (ds == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return ds.getConnection();
    }
}

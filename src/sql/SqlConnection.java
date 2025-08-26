package sql;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import util.EnvLoader;


public class SqlConnection {

    private static final String DB_HOST = EnvLoader.get("DB_HOST");
    private static final String DB_PORT = EnvLoader.get("DB_PORT");
    private static final String DB_NAME = EnvLoader.get("DB_NAME");
    private static final String DB_USER = EnvLoader.get("DB_USER");
    private static final String DB_PASSWORD = EnvLoader.get("DB_PASSWORD");

    public Connection getConnection() throws SQLException {
        Connection connection;
        Properties connectionProps = new Properties();
        connectionProps.setProperty("user", DB_USER);
        connectionProps.setProperty("password", DB_PASSWORD);
        String url = String.format("jdbc:mysql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);
        try {
            connection = DriverManager.getConnection(url, connectionProps);
            if (connection != null) {
                System.out.println("Conexión exitosa a la base de datos");
            }
        } catch (SQLException e) {
            System.err.println("Error de conexión a la base de datos: " + e.getMessage());
            throw e;
        }
        return connection;
    }
}
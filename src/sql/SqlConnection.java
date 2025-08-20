package sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;


public class SqlConnection { 

    public static final String URL = "jdbc:mysql://localhost:3306/supercomputo";

    public static final String USER = "root";

    public static final String PSWD = "123456";


    public Connection getConnection() throws SQLException{

        Connection connection;
        Properties connectionProps = new Properties();
        connectionProps.setProperty("user",USER);
        connectionProps.setProperty("password",PSWD);
        try{
            connection = DriverManager.getConnection(URL, connectionProps);
            if (connection != null) {
                System.out.println("Conexión exitosa a la base de datos");
            }
        }catch (SQLException e) {
            System.err.println("Error de conexión a la base de datos: " + e.getMessage());
            throw e;
        }
        return connection;
    } 

} 
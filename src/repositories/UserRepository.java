package repositories;

import java.io.IOException;
import java.sql.*;
import sql.SqlConnection;

public class UserRepository{

    SqlConnection sqlConnection;

    public UserRepository() {
        this.sqlConnection= new SqlConnection();
    }

    private Connection getConnection() throws SQLException {
        return sqlConnection.getConnection();
    }

    public boolean checkCredentials(String user, String pass){
        String query = "SELECT username FROM users WHERE username = ? AND password = ?";
        try (Connection con = getConnection();
             PreparedStatement stmt = con.prepareStatement(query)) {

            stmt.setString(1, user);
            stmt.setString(2, pass);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("Error al verificar credenciales: " + e.getMessage());
            return false;
        }
    }
}

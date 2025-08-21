/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package repositories;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import sql.SqlConnection;

public class UserRepository {

    private SqlConnection dbc;
    private Connection con;

    public UserRepository() {
        try {
            this.dbc = new SqlConnection();
            this.con = this.dbc.getConnection();
        } catch (SQLException ex) {
            System.getLogger(UserRepository.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        
    }

    public Boolean CheckCredentials(String user, String pass) {
        try {
            String query = "SELECT * FROM users WHERE username='" + user + "' AND password='" + pass + "'";
            Statement myQuery = con.createStatement();
            ResultSet rs = myQuery.executeQuery(query);

            if (rs.next()) {
                String data = "";
                for (int i = 1; i <= 3; i++) {
                    data += rs.getString(i) + ":";
                }
                System.out.println("USER DATA: " + data);
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

}

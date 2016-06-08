/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 *
 * @author Janco1
 */
public class AktualnostRowMapper implements RowMapper<Aktualnost>{

    public Aktualnost mapRow(ResultSet rs, int i) throws SQLException {
        String portal=rs.getString("portal");
        String datum=rs.getString("datum");
        return new Aktualnost(portal, datum);
    }
    
}

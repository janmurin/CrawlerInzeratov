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
class InzeratIbaIDLinkyRowMapper implements RowMapper<Inzerat> {

     public Inzerat mapRow(ResultSet rs, int i) throws SQLException {
        // KVOLI SETRENIU PAMETOU IBA LINK A ID NACITAVAM
         Inzerat inz=new Inzerat();
        inz.setAktualny_link(rs.getString("aktualny_link"));
//        inz.setCena(rs.getString("cena"));
        inz.setId(Integer.parseInt(rs.getString("id")));
//        inz.setLokalita(rs.getString("lokalita"));
//        inz.setMeno(rs.getString("meno"));
//        inz.setNazov(rs.getString("nazov"));
//        inz.setTyp(rs.getString("typ"));
//        inz.setKategoria(rs.getString("kategoria"));
//        inz.setPortal(rs.getString("portal"));
//        inz.setTelefon(rs.getString("telefon"));
//        inz.setText(rs.getString("text"));
//        inz.setOdoslany(Boolean.parseBoolean(rs.getString("sent")));
//        inz.setTimeInserted(rs.getString("time_inserted"));
//        //inz.setArchivaciaTime(rs.getString("archivacia_time"));
//        inz.setPocetZobrazeni(Integer.parseInt(rs.getString("pocet_zobrazeni")));
//        inz.setZaujimavy(Boolean.parseBoolean(rs.getString("zaujimavy")));
//        inz.setSurne(Boolean.parseBoolean(rs.getString("surne")));
//        inz.setPrecitany(Boolean.parseBoolean(rs.getString("precitany")));
        return inz;
    }
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

import crawleri.BazosCrawler;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Janco1
 */
public class MySQLDatabase {

    private Connection connect = null;
    private Statement statement = null;
    private PreparedStatement preparedStatement = null;
    private ResultSet resultSet = null;
    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
    static String DB_URL = "";

    //  Database credentials
    static String USER = "";
    static String PASS = "";
    private Connection conn;
    private Statement stmt;
    private boolean volnaDB = true;

    public MySQLDatabase(String url, String user, String pass) {
        DB_URL = url;
        USER = user;
        PASS = pass;
    }

    public Inzerat getLastTimeInzeratInserted(String portalName) {

        List<Inzerat> inzeraty = new ArrayList<Inzerat>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = " SELECT * FROM Inzeraty WHERE portal='" + portalName + "' order by time_inserted desc LIMIT 0,10 ";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Inzerat inz = new Inzerat();
                inz.setAktualny_link(rs.getString("aktualny_link"));
                inz.setCena(rs.getString("cena"));
                inz.setId(Integer.parseInt(rs.getString("id")));
                inz.setLokalita(rs.getString("lokalita"));
                inz.setMeno(rs.getString("meno"));
                inz.setNazov(rs.getString("nazov"));
                inz.setTyp(rs.getString("typ"));
                inz.setKategoria(rs.getString("kategoria"));
                inz.setPortal(rs.getString("portal"));
                inz.setTelefon(rs.getString("telefon"));
                inz.setText(rs.getString("text"));
                inz.setOdoslany(Byte.parseByte(rs.getString("sent")));
                inz.setPrecitany(Byte.parseByte(rs.getString("precitany")));
                inz.setTimeInserted(rs.getString("time_inserted"));
                inz.setZaujimavy(Byte.parseByte(rs.getString("zaujimavy")));
                inz.setSurne(Byte.parseByte(rs.getString("surne")));
                inz.setPocetZobrazeni(Integer.parseInt(rs.getString("pocet_zobrazeni")));
                inzeraty.add(inz);
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        if (inzeraty.isEmpty()) {
            System.out.println("MYSQL: nemame inzeraty");
            Inzerat last = new Inzerat();
            last.setTimeInserted("2010-10-10 00:00:00");
            return last;
        } else {
            return inzeraty.get(0);
        }
    }

    public void deleteInzeratyWhereID(List<Integer> toDeleteIDs) {
        if (toDeleteIDs.isEmpty()) {
            System.out.println("toDeleteIDs is empty. No delete to remote DB.");
            volnaDB = true;
            return;
        }
        try {
            StringBuilder idsString = new StringBuilder();
            for (int i = 0; i < toDeleteIDs.size() - 1; i++) {
                idsString.append(toDeleteIDs.get(i) + ",");
            }
            idsString.append(toDeleteIDs.get(toDeleteIDs.size() - 1));
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = "DELETE FROM Inzeraty WHERE id IN(" + idsString + ") ;";
            stmt.executeUpdate(sql.toString());
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        volnaDB = true;
    }

    public void inzertInzeraty(List<Inzerat> inzeraty) {
        if (inzeraty.size() == 0) {
            volnaDB = true;
            return;
        }
//  POZOR!!!!! NIC NERIESI, PROSTE INSERTUJE VSETKO CO DOSTANE
        // OSETRIT DUPLICITU MUSI INA METODA
        StringBuilder sql = new StringBuilder("INSERT INTO Inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,zaujimavy,pocet_zobrazeni,surne,typ,kategoria,precitany)VALUES");
        //int id = getTopRegisteredInzeratID();
        for (int i = 0; i < inzeraty.size() - 1; i++) {
            //id++;
            Inzerat inzerat = inzeraty.get(i);
            sql.append(""
                    + "('" + inzerat.getId() + "',"
                    + "'" + inzerat.getPortal() + "',"
                    + "' " + inzerat.getNazov() + " ',"
                    + "' " + inzerat.getText() + " ',"
                    + "' " + inzerat.getMeno() + " ',"
                    + "'" + inzerat.getTelefon() + "',"
                    + "' " + inzerat.getLokalita() + " ',"
                    + "'" + inzerat.getAktualny_link() + "',"
                    + "'" + inzerat.getCena() + "',"
                    + "" + inzerat.isOdoslany() + ","
                    + "'" + inzerat.getTimeInserted() + "',false,'0'," + inzerat.isSurne() + ","
                    + "'" + inzerat.getTyp() + "',"
                    + "'" + inzerat.getKategoria() + "',false), \n");
        }
        // pridame maxHladanychInzeratov izerat
        Inzerat inzerat = inzeraty.get(inzeraty.size() - 1);
        sql.append(""
                + "('" + inzerat.getId() + "',"
                + "'" + inzerat.getPortal() + "',"
                + "' " + inzerat.getNazov() + " ',"
                + "' " + inzerat.getText() + " ',"
                + "' " + inzerat.getMeno() + " ',"
                + "'" + inzerat.getTelefon() + "',"
                + "' " + inzerat.getLokalita() + " ',"
                + "'" + inzerat.getAktualny_link() + "',"
                + "'" + inzerat.getCena() + "',"
                + "" + inzerat.isOdoslany() + ","
                + "'" + inzerat.getTimeInserted() + "',false,'0'," + inzerat.isSurne() + ","
                + "'" + inzerat.getTyp() + "',"
                + "'" + inzerat.getKategoria() + "',false); \n");
        //System.out.println("INSERT SQL:" +sql.toString());
        if (sql.length() == 0) {
            volnaDB = true;
            return;
        }
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            stmt.executeUpdate(sql.toString());
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            System.out.println("BAD SQL:" + sql.toString());
        } finally {
            close();
        }
    }

//    String getInsertScript(List<Inzerat> inzeraty) {
//        if (inzeraty.size() == 0) {
//            return "nullString";
//        }
////  POZOR!!!!! NIC NERIESI, PROSTE INSERTUJE VSETKO CO DOSTANE
//        // OSETRIT DUPLICITU MUSI INA METODA
//          StringBuilder sql = new StringBuilder();
//        //int id = getTopRegisteredInzeratID();
//        for (int i = 0; i < inzeraty.size() - 1; i++) {
//            //id++;
//            Inzerat inzerat = inzeraty.get(i);
//            sql.append("INSERT INTO Inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,zaujimavy,pocet_zobrazeni,surne,typ,kategoria,precitany)VALUES"
//                    + "('" + inzerat.getId() + "',"
//                    + "'" + inzerat.getPortal() + "',"
//                    + "'" + inzerat.getNazov() + "',"
//                    + "'" + inzerat.getText() + "',"
//                    + "'" + inzerat.getMeno() + "',"
//                    + "'" + inzerat.getTelefon() + "',"
//                    + "'" + inzerat.getLokalita() + "',"
//                    + "'" + inzerat.getAktualny_link() + "',"
//                    + "'" + inzerat.getCena() + "',"
//                    + "" + inzerat.isOdoslany() + ","
//                    + "'" + inzerat.getTimeInserted() + "',true,'0'," + inzerat.isSurne() + ","
//                    + "'" + inzerat.getTyp() + "',"
//                    + "'" + inzerat.getKategoria() + "',false); \n");
//        }
//        // pridame maxHladanychInzeratov izerat
//        Inzerat inzerat = inzeraty.get(inzeraty.size() - 1);
//        sql.append("INSERT INTO Inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,zaujimavy,pocet_zobrazeni,surne,typ,kategoria,precitany)VALUES"
//                + "('" + inzerat.getId() + "',"
//                + "'" + inzerat.getPortal() + "',"
//                + "'" + inzerat.getNazov() + "',"
//                + "'" + inzerat.getText() + "',"
//                + "'" + inzerat.getMeno() + "',"
//                + "'" + inzerat.getTelefon() + "',"
//                + "'" + inzerat.getLokalita() + "',"
//                + "'" + inzerat.getAktualny_link() + "',"
//                + "'" + inzerat.getCena() + "',"
//                + "" + inzerat.isOdoslany() + ","
//                + "'" + inzerat.getTimeInserted() + "',true,'0'," + inzerat.isSurne() + ","
//                + "'" + inzerat.getTyp() + "',"
//                + "'" + inzerat.getKategoria() + "',false); \n");
//        return sql.toString();
//    }
    public List<Integer> getInzeratyIDsPortal(String aktualnyPortal) {
        List<Integer> inzeraty = new ArrayList<Integer>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = "SELECT id FROM Inzeraty WHERE portal='" + aktualnyPortal + "' order by id asc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                inzeraty.add(Integer.parseInt(rs.getString("id")));
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        return inzeraty;
    }

    // you need to close all three to make sure
    private void close() {
        close((Closeable) resultSet);
        close((Closeable) statement);
        close((Closeable) connect);
        volnaDB = true;
    }

    private void close(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
            // don't throw now as it might leave following closables in undefined state
        }
    }

    public boolean mamDatabazu() {
        boolean mamDatabazu = volnaDB;
        volnaDB = false;
        return mamDatabazu;
    }

    public List<Inzerat> getInzeratyPortal(String aktualnyPortal) {
        List<Inzerat> inzeraty = new ArrayList<Inzerat>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = " SELECT * FROM Inzeraty WHERE portal='" + aktualnyPortal + "' order by id asc ";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Inzerat inz = new Inzerat();
                inz.setAktualny_link(rs.getString("aktualny_link"));
                inz.setCena(rs.getString("cena"));
                inz.setId(Integer.parseInt(rs.getString("id")));
                inz.setLokalita(rs.getString("lokalita"));
                inz.setMeno(rs.getString("meno"));
                inz.setNazov(rs.getString("nazov"));
                inz.setTyp(rs.getString("typ"));
                inz.setKategoria(rs.getString("kategoria"));
                inz.setPortal(rs.getString("portal"));
                inz.setTelefon(rs.getString("telefon"));
                inz.setText(rs.getString("text"));
                inz.setOdoslany(Byte.parseByte(rs.getString("sent")));
                inz.setPrecitany(Byte.parseByte(rs.getString("precitany")));
                inz.setTimeInserted(rs.getString("time_inserted"));
                inz.setZaujimavy(Byte.parseByte(rs.getString("zaujimavy")));
                inz.setSurne(Byte.parseByte(rs.getString("surne")));
                inz.setPocetZobrazeni(Integer.parseInt(rs.getString("pocet_zobrazeni")));
                inzeraty.add(inz);
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        if (inzeraty.isEmpty()) {
            System.out.println("MYSQL: nemame inzeraty");
            Inzerat last = new Inzerat();
            last.setTimeInserted("2010-10-10 00:00:00");
            return inzeraty;
        } else {
            return inzeraty;
        }
    }

    /**
     * POUZIVA SA PRE HROMADNY UPLOAD ZO VSETKYCH PORTALOV
     *
     * @return
     */
    Inzerat getLastTimeInzeratInserted() {

        List<Inzerat> inzeraty = new ArrayList<Inzerat>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = " SELECT * FROM Inzeraty order by time_inserted desc LIMIT 0,10 ";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Inzerat inz = new Inzerat();
                inz.setAktualny_link(rs.getString("aktualny_link"));
                inz.setCena(rs.getString("cena"));
                inz.setId(Integer.parseInt(rs.getString("id")));
                inz.setLokalita(rs.getString("lokalita"));
                inz.setMeno(rs.getString("meno"));
                inz.setNazov(rs.getString("nazov"));
                inz.setTyp(rs.getString("typ"));
                inz.setKategoria(rs.getString("kategoria"));
                inz.setPortal(rs.getString("portal"));
                inz.setTelefon(rs.getString("telefon"));
                inz.setText(rs.getString("text"));
                inz.setOdoslany(Byte.parseByte(rs.getString("sent")));
                inz.setPrecitany(Byte.parseByte(rs.getString("precitany")));
                inz.setTimeInserted(rs.getString("time_inserted"));
                inz.setZaujimavy(Byte.parseByte(rs.getString("zaujimavy")));
                inz.setSurne(Byte.parseByte(rs.getString("surne")));
                inz.setPocetZobrazeni(Integer.parseInt(rs.getString("pocet_zobrazeni")));
                inzeraty.add(inz);
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        if (inzeraty.isEmpty()) {
            System.out.println("MYSQL: nemame inzeraty");
            Inzerat last = new Inzerat();
            last.setTimeInserted("2010-10-10 00:00:00");
            return last;
        } else {
            return inzeraty.get(0);
        }
    }

    List<Integer> getInzeratyIDs() {
        List<Integer> inzeraty = new ArrayList<Integer>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = "SELECT id FROM Inzeraty  order by id asc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                inzeraty.add(Integer.parseInt(rs.getString("id")));
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        return inzeraty;
    }

    int deleteInzeratyWhereLinkNOTIn(List<String> platne) {
        int pocet = 0;
        try {
            StringBuilder idsString = new StringBuilder();
            for (int i = 0; i < platne.size() - 1; i++) {
                idsString.append("'"+platne.get(i) + "',");
            }
            idsString.append("'"+platne.get(platne.size() - 1)+"'");
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            if (idsString.length()==0){
                idsString.append("defaultLink");
            }
            sql = "SELECT count(*)as pocet FROM Inzeraty WHERE aktualny_link NOT IN(" + idsString + ") ;";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                pocet = Integer.parseInt(rs.getString("pocet"));
            }

            sql = "DELETE FROM Inzeraty WHERE aktualny_link NOT IN(" + idsString + ") ;";
            stmt.executeUpdate(sql.toString());
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        volnaDB = true;
        return pocet;
    }

    Map<Integer, String> getInzeratyIDLinks() {
        Map<Integer,String> inzeraty = new HashMap<Integer,String>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            stmt = conn.createStatement();
            String sql;
            sql = "SELECT id,aktualny_link FROM Inzeraty";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                //inzeraty.add();
                inzeraty.put(Integer.parseInt(rs.getString("id")), rs.getString("aktualny_link"));
            }
        } catch (Exception e) {
            Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        volnaDB = true;
        return inzeraty;
    }
}

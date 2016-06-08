/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

import crawleri.BazosCrawler;
import crawleri.Okres;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.hsqldb.jdbc.JDBCDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/**
 *
 * @author Janco1
 */
public class Database {

    private JdbcTemplate jdbcTemplate;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private String AKTUALNY_CAS;
    private boolean volnaDB = true;

    public Database() {

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:hsql://localhost:1234/inzeratydb");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        AKTUALNY_CAS = getTimestamp();
        volnaDB = true;
    }

    public boolean mamDatabazu() {
        boolean mamDatabazu = volnaDB;
        volnaDB = false;
        return mamDatabazu;
    }

    public List<Inzerat> getInzeratyList(String portalName) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM inzeraty WHERE portal='" + portalName + "'";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    public void inzertInzeraty(List<Inzerat> inzeraty) {
        AKTUALNY_CAS = getTimestamp();
        StringBuilder sql = new StringBuilder();
        int id = getTopRegisteredInzeratID();
        for (Inzerat inzerat : inzeraty) {
            id++;
            sql.append("INSERT INTO inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,pocet_zobrazeni,zaujimavy,surne,typ,kategoria,precitany)"
                    + "VALUES('" + id + "',"
                    + "'" + inzerat.getPortal() + "',"
                    + "'" + inzerat.getNazov() + "',"
                    + "'" + inzerat.getText() + "',"
                    + "'" + inzerat.getMeno() + "',"
                    + "'" + inzerat.getTelefon() + "',"
                    + "'" + inzerat.getLokalita() + "',"
                    + "'" + inzerat.getAktualny_link() + "',"
                    + "'" + inzerat.getCena() + "',"
                    + "'" + inzerat.isOdoslany() + "',"
                    + "'" + AKTUALNY_CAS + "','0',false," + inzerat.isSurne() + ","
                    + "'" + inzerat.getTyp() + "',"
                    + "'" + inzerat.getKategoria() + "',false); \n");
        }
        //System.out.println("INSERT SQL:" +sql.toString());
        if (sql.length() == 0) {
            volnaDB = true;
            return;
        }
        try {
            jdbcTemplate.execute(sql.toString());
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            //System.out.println("BAD SQL:" + sql.toString());
        }
        volnaDB = true;
    }

    private int getTopRegisteredInzeratID() {
        //get najvyssie id
        RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
        StringBuilder sql = new StringBuilder("select top 1 * from inzeraty order by id desc");
        List<Inzerat> foundBlocky = jdbcTemplate.query(sql.toString(), rowMapper);
        if (foundBlocky.size() == 0) {
            return 0;
        }
        int topID = foundBlocky.get(0).getId();
        return topID;
    }

    public void deleteInzeratyWithID(List<Integer> toDelete) {
        if (toDelete.size() == 0) {
            volnaDB = true;
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < toDelete.size() - 1; i++) {
            sb.append(toDelete.get(i) + ",");
        }
        sb.append(toDelete.get(toDelete.size() - 1) + ")");
        String prikaz = "DELETE FROM inzeraty WHERE id IN" + sb.toString();
        jdbcTemplate.execute(prikaz);
        volnaDB = true;
    }

    public void updateSurneInzeratyAktualnyCas() {
        System.out.println("DB: hladam surne inzeraty");
        String prikaz = "select id from inzeraty \n"
                + "where id in (select id from inzeraty where \n"
                + "month(time_inserted)=month(CURRENT_TIMESTAMP) \n"
                + "and day(time_inserted)=day(CURRENT_TIMESTAMP)\n"
                + "and year(time_inserted)=year(CURRENT_TIMESTAMP))\n"
                + "and (text like '%súrne%' \n"
                + "or text like '%surne%' \n"
                + "or text like '%Surne%' \n"
                + "or text like '%Súrne%' \n"
                + "or text like '%SURNE%' \n"
                + "or text like '%SÚRNE%' )";
        RowMapper<Integer> rowMapper = new RowMapper<Integer>() {
            public Integer mapRow(ResultSet rs, int i) throws SQLException {
                return Integer.parseInt(rs.getString("id"));
            }
        };
        List<Integer> inzeraty = jdbcTemplate.query(prikaz, rowMapper);
        StringBuilder sb = new StringBuilder();
        for (Integer i : inzeraty) {
            sb.append("UPDATE inzeraty SET surne=true WHERE id=" + i + ";\n");
        }
        try {
            if (sb.length() != 0) {
                jdbcTemplate.update(sb.toString());
            }
        } catch (Exception e) {
            System.out.println(sb.toString());
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        }
        System.out.println("DB: surne remote_inzeraty updatnute");
        volnaDB = true;
    }

    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(System.currentTimeMillis())).toString();
    }

    private void getvynimka() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    List<Inzerat> getInzeratyListGreaterThanLastTimeInserted(String time_inserted, String portalName) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM inzeraty WHERE portal='" + portalName + "' and time_inserted>timestamp('" + time_inserted + "')";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    // TUTO METODU MI NETREBA LEBO Z TABLE[INZERATY] UZ PRIAMO NEPOSIELAM DO MYSQL
//    List<Inzerat> getInzeratyWhereIDNotIN(List<Integer> idckaPortal, String portal) {
//        if (idckaPortal.size() == 0) {
//            volnaDB = true;
//            return null;
//        }
//        StringBuilder sb = new StringBuilder();
//        sb.append("(");
//        for (int i = 0; i < idckaPortal.size() - 1; i++) {
//            sb.append(idckaPortal.get(i) + ",");
//        }
//        sb.append(idckaPortal.get(idckaPortal.size() - 1) + ")");
//
//        try {
//            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
//            String sql = "SELECT * FROM inzeraty WHERE portal='" + portal + "' AND id NOT IN" + sb.toString();
//            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
//            volnaDB = true;
//            return inzeraty;
//        } catch (Exception exception) {
//            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
//        }
//        volnaDB = true;
//        return null;
//    }
    // TATO METODA SA POUZIVALA KED SA PRIAMO ODOSIELALI INZERATY Z TABULKY [INZERATY]
//    int getCountPortal(String aktualnyPortal) {
//        RowMapper<String> rowMapper = new RowMapper<String>() {
//
//            public String mapRow(ResultSet rs, int i) throws SQLException {
//                return rs.getString("pocet");
//            }
//        };
//        String sql = "SELECT count(*) as pocet FROM inzeraty WHERE portal='" + aktualnyPortal + "'";
//        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
//        volnaDB = true;
//        return Integer.parseInt(inzeraty.get(0));
//    }
//    List<String> getAktualneLinky(String portalName) {
//        try {
//            RowMapper<String> rowMapper = new RowMapper<String>() {
//
//                public String mapRow(ResultSet rs, int i) throws SQLException {
//                    return rs.getString("link");
//                }
//            };
//            String sql="";
//            if (portalName.equals("")){
//                sql = "SELECT * FROM aktualne_linkyBAZOS";
//            }
//
//            if (sql.length()==0){
//                System.out.println("getAktualneLinky: prazdy select, zle namatchovanie portalu");
//                getvynimka();  
//            }
//            List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
//            volnaDB = true;
//            return inzeraty;
//        } catch (Exception exception) {
//            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
//        }
//        return null;
//    }
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    public void inzertRemoteInzeraty(List<Inzerat> unikatne, String portal) {
        // TUTO METODU POUZIVAT IBA PRE TYCH CLIENTOV, KTORI NAJPRV UKLADAJU DO TABLE: [REMOTE_INZERATY]- lebo tam este neni time_inserted zaznaceny
        StringBuilder sql = new StringBuilder();
        AKTUALNY_CAS = getTimestamp();
        if (unikatne.size() == 0) {
            jdbcTemplate.execute("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n");
            volnaDB = true;
            return;
        }
        int id = getRemoteTopRegisteredInzeratID();
        sql.append("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n");
        sql.append("INSERT INTO remote_inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,zaujimavy,pocet_zobrazeni,surne,typ,kategoria,precitany)VALUES\n");
        //int id = getTopRegisteredInzeratID();
        for (int i = 0; i < unikatne.size() - 1; i++) {
            id++;
            Inzerat inzerat = unikatne.get(i);
            sql.append(""
                    + "('" + id + "',"
                    + "'" + inzerat.getPortal() + "',"
                    + "'" + inzerat.getNazov() + "',"
                    + "'" + inzerat.getText() + "',"
                    + "'" + inzerat.getMeno() + "',"
                    + "'" + inzerat.getTelefon() + "',"
                    + "'" + inzerat.getLokalita() + "',"
                    + "'" + inzerat.getAktualny_link() + "',"
                    + "'" + inzerat.getCena() + "',"
                    + "" + inzerat.isOdoslany() + ","
                    + "'" + AKTUALNY_CAS + "',false,'0'," + inzerat.isSurne() + ","
                    + "'" + inzerat.getTyp() + "',"
                    + "'" + inzerat.getKategoria() + "',false), \n");
        }
        // pridame maxHladanychInzeratov izerat
        Inzerat inzerat = unikatne.get(unikatne.size() - 1);
        id++;
        sql.append(""
                + "('" + id + "',"
                + "'" + inzerat.getPortal() + "',"
                + "'" + inzerat.getNazov() + "',"
                + "'" + inzerat.getText() + "',"
                + "'" + inzerat.getMeno() + "',"
                + "'" + inzerat.getTelefon() + "',"
                + "'" + inzerat.getLokalita() + "',"
                + "'" + inzerat.getAktualny_link() + "',"
                + "'" + inzerat.getCena() + "',"
                + "" + inzerat.isOdoslany() + ","
                + "'" + AKTUALNY_CAS + "',false,'0'," + inzerat.isSurne() + ","
                + "'" + inzerat.getTyp() + "',"
                + "'" + inzerat.getKategoria() + "',false); \n");
//        AKTUALNY_CAS = getTimestamp();
//        StringBuilder sql = new StringBuilder();
//        int id = getRemoteTopRegisteredInzeratID();
//        sql.append("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n");
//        for (Inzerat inzerat : unikatne) {
//            id++;
//            sql.append("INSERT INTO remote_inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,pocet_zobrazeni,zaujimavy,surne,typ,kategoria,precitany)"
//                    + "VALUES('" + id + "',"
//                    + "'" + inzerat.getPortal() + "',"
//                    + "'" + inzerat.getNazov() + "',"
//                    + "'" + inzerat.getText() + "',"
//                    + "'" + inzerat.getMeno() + "',"
//                    + "'" + inzerat.getTelefon() + "',"
//                    + "'" + inzerat.getLokalita() + "',"
//                    + "'" + inzerat.getAktualny_link() + "',"
//                    + "'" + inzerat.getCena() + "',"
//                    + "'" + inzerat.isOdoslany() + "',"
//                    + "'" + AKTUALNY_CAS + "','0',false," + inzerat.isSurne() + ","
//                    + "'" + inzerat.getTyp() + "',"
//                    + "'" + inzerat.getKategoria() + "',false); \n");
//        }
        //System.out.println("INSERT SQL:" +sql.toString());
        if (sql.length() == 0) {
            volnaDB = true;
            return;
        }

        try {
            jdbcTemplate.execute(sql.toString());
//            String prikaz="UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n";
//            jdbcTemplate.update(prikaz);
            volnaDB = true;
            //changes.firePropertyChange("aktualnostiUpdated", true, false);
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            //System.out.println("BAD SQL:" + sql.toString());
        }
        volnaDB = true;
    }

    void inzertBazosRemoteInzeraty(List<Inzerat> unikatne, String portal) {
        // TUTO METODU POUZIVAT IBA PRE TYCH CLIENTOV, KTORI NAJPRV UKLADAJU DO TABLE: [INZERATY]- lebo tam uz je time_inserted zaznaceny
        StringBuilder sql = new StringBuilder();
        AKTUALNY_CAS = getTimestamp();
        if (unikatne.size() == 0) {
            jdbcTemplate.execute("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n");
            volnaDB = true;
            return;
        }
        int id = getRemoteTopRegisteredInzeratID();
        sql.append("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n");
        sql.append("INSERT INTO remote_inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,zaujimavy,pocet_zobrazeni,surne,typ,kategoria,precitany)VALUES\n");
        //int id = getTopRegisteredInzeratID();
        for (int i = 0; i < unikatne.size() - 1; i++) {
            id++;
            Inzerat inzerat = unikatne.get(i);
            sql.append(""
                    + "('" + id + "',"
                    + "'" + inzerat.getPortal() + "',"
                    + "'" + inzerat.getNazov() + "',"
                    + "'" + inzerat.getText() + "',"
                    + "'" + inzerat.getMeno() + "',"
                    + "'" + inzerat.getTelefon() + "',"
                    + "'" + inzerat.getLokalita() + "',"
                    + "'" + inzerat.getAktualny_link() + "',"
                    + "'" + inzerat.getCena() + "',"
                    + "" + inzerat.isOdoslany() + ","
                    + "'" + inzerat.getTimeInserted() + "',false,'0'," + inzerat.isSurne() + ","
                    + "'" + inzerat.getTyp() + "',"
                    + "'" + inzerat.getKategoria() + "',false), \n");
        }
        // pridame maxHladanychInzeratov izerat
        Inzerat inzerat = unikatne.get(unikatne.size() - 1);
        id++;
        sql.append(""
                + "('" + id + "',"
                + "'" + inzerat.getPortal() + "',"
                + "'" + inzerat.getNazov() + "',"
                + "'" + inzerat.getText() + "',"
                + "'" + inzerat.getMeno() + "',"
                + "'" + inzerat.getTelefon() + "',"
                + "'" + inzerat.getLokalita() + "',"
                + "'" + inzerat.getAktualny_link() + "',"
                + "'" + inzerat.getCena() + "',"
                + "" + inzerat.isOdoslany() + ","
                + "'" + inzerat.getTimeInserted() + "',false,'0'," + inzerat.isSurne() + ","
                + "'" + inzerat.getTyp() + "',"
                + "'" + inzerat.getKategoria() + "',false); \n");
        //System.out.println("INSERT SQL:" +sql.toString());

        try {
            jdbcTemplate.execute(sql.toString());
//            String prikaz="UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n";
//            jdbcTemplate.update(prikaz);
            volnaDB = true;
            //changes.firePropertyChange("aktualnostiUpdated", true, false);
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            //System.out.println("BAD SQL:" + sql.toString());
        }
        volnaDB = true;
    }

    public List<Inzerat> getRemoteInzeratyList(String aktualnyPortal) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "' order by id asc";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    public void deleteRemoteDuplikatneInzeraty(List<Integer> toDelete) {
        if (toDelete.size() == 0) {
            volnaDB = true;
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < toDelete.size() - 1; i++) {
            sb.append(toDelete.get(i) + ",");
        }
        sb.append(toDelete.get(toDelete.size() - 1) + ")");
        String prikaz = "DELETE FROM remote_inzeraty WHERE id IN" + sb.toString();
        jdbcTemplate.execute(prikaz);
        volnaDB = true;
    }

    public void updateRemoteSurneInzeratyVsetko() {
        System.out.println("DB: hladam surne inzeraty");
        String prikaz = "select id from remote_inzeraty \n"
                + "where id in (select id from remote_inzeraty where \n"
                + "month(time_inserted)=month(CURRENT_TIMESTAMP) \n"
                + "and day(time_inserted)=day(CURRENT_TIMESTAMP)\n"
                + "and year(time_inserted)=year(CURRENT_TIMESTAMP))\n"
                + "and ((text like '%súrne%' \n"
                + "or text like '%surne%' \n"
                + "or text like '%Surne%' \n"
                + "or text like '%Súrne%' \n"
                + "or text like '%SURNE%' \n"
                + "or text like '%SÚRNE%' ) or (nazov like '%súrne%' \n"
                + "or nazov like '%surne%' \n"
                + "or nazov like '%Surne%' \n"
                + "or nazov like '%Súrne%' \n"
                + "or nazov like '%SURNE%' \n"
                + "or nazov like '%SÚRNE%' ) )";
        RowMapper<Integer> rowMapper = new RowMapper<Integer>() {
            public Integer mapRow(ResultSet rs, int i) throws SQLException {
                return Integer.parseInt(rs.getString("id"));
            }
        };
        List<Integer> inzeraty = jdbcTemplate.query(prikaz, rowMapper);
        StringBuilder sb = new StringBuilder();
        for (Integer i : inzeraty) {
            sb.append("UPDATE remote_inzeraty SET surne=true WHERE id=" + i + ";\n");
        }
        jdbcTemplate.update(sb.toString());
        System.out.println("DB: surne remote_inzeraty updatnute");
        volnaDB = true;
    }

    public void updateRemoteSurneInzeratyAktualnyCas() {
        System.out.println("DB: hladam surne inzeraty");
        String prikaz = "select id from remote_inzeraty \n"
                + "where id in (select id from remote_inzeraty where \n"
                + "month(time_inserted)=month(CURRENT_TIMESTAMP) \n"
                + "and day(time_inserted)=day(CURRENT_TIMESTAMP)\n"
                + "and year(time_inserted)=year(CURRENT_TIMESTAMP))\n"
                + "and ((text like '%súrne%' \n"
                + "or text like '%surne%' \n"
                + "or text like '%Surne%' \n"
                + "or text like '%Súrne%' \n"
                + "or text like '%SURNE%' \n"
                + "or text like '%SÚRNE%' ) or (nazov like '%súrne%' \n"
                + "or nazov like '%surne%' \n"
                + "or nazov like '%Surne%' \n"
                + "or nazov like '%Súrne%' \n"
                + "or nazov like '%SURNE%' \n"
                + "or nazov like '%SÚRNE%' ) )";
        RowMapper<Integer> rowMapper = new RowMapper<Integer>() {
            public Integer mapRow(ResultSet rs, int i) throws SQLException {
                return Integer.parseInt(rs.getString("id"));
            }
        };
        List<Integer> inzeraty = jdbcTemplate.query(prikaz, rowMapper);
        StringBuilder sb = new StringBuilder();
        for (Integer i : inzeraty) {
            sb.append("UPDATE remote_inzeraty SET surne=true WHERE id=" + i + ";\n");
        }
        if (sb.toString().length() > 0) {
            jdbcTemplate.update(sb.toString());
        }
        System.out.println("DB: surne remote_inzeraty updatnute");
        volnaDB = true;
    }

    public List<Inzerat> getRemoteInzeratyListGreaterThanLastTimeInserted(String timeInserted, String aktualnyPortal) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "' and time_inserted>timestamp('" + timeInserted + "')";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    public List<Inzerat> getRemoteInzeratyWhereIDNotIN(List<Integer> idckaPortal, String aktualnyPortal) {
        if (idckaPortal.size() == 0) {
            volnaDB = true;
            return new ArrayList<Inzerat>();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < idckaPortal.size() - 1; i++) {
            sb.append(idckaPortal.get(i) + ",");
        }
        sb.append(idckaPortal.get(idckaPortal.size() - 1) + ")");

        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "' AND id NOT IN" + sb.toString();
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return new ArrayList<Inzerat>();
    }

    public int getRemoteCountPortal(String aktualnyPortal) {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("pocet");
            }
        };
        String sql = "SELECT count(*) as pocet FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "'";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return Integer.parseInt(inzeraty.get(0));
    }

    private int getRemoteTopRegisteredInzeratID() {
        //get najvyssie id
        RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
        StringBuilder sql = new StringBuilder("select top 1 * from remote_inzeraty order by id desc");
        List<Inzerat> foundBlocky = jdbcTemplate.query(sql.toString(), rowMapper);
        if (foundBlocky.size() == 0) {
            return 0;
        }
        int topID = foundBlocky.get(0).getId();
        //System.out.println("remote_inzeraty top id: " + topID);
        return topID;
    }

    public List<String> getRemoteLinky(String aktualnyPortal) {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("aktualny_link");
            }
        };
        String sql = "SELECT aktualny_link FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "'";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    public List<String> getRemoteLinky() {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("aktualny_link");
            }
        };
        String sql = "SELECT aktualny_link FROM remote_inzeraty ";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    public List<Inzerat> getSukromneInzeratyFrom(String aktualnyPortal) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM inzeraty WHERE telefon not in "
                    + "(select telefon from(select telefon, count(*) as pocet from inzeraty group by telefon order by pocet desc)as T "
                    + "where pocet >" + 2 + ") and portal='" + aktualnyPortal + "'";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(MainForm.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    public List<Integer> deleteRemoteInzeratyNotIn(List<Inzerat> sukromne, String portal) {
        // hladame idcka tych inzeratov ktore nie su v zozname sukromnych a mazeme tieto inzeraty
        List<Integer> toDeleteSukromneIDs;
        if (sukromne.size() == 0) {
            // tak treba vsetko mazat lebo nic nie je sukromne
            RowMapper<Integer> rowMapper = new RowMapper<Integer>() {
                public Integer mapRow(ResultSet rs, int i) throws SQLException {
                    return Integer.parseInt(rs.getString("id"));
                }
            };
            String sql = "SELECT id FROM remote_inzeraty WHERE portal='" + portal + "'";
            toDeleteSukromneIDs = jdbcTemplate.query(sql, rowMapper);

            String prikaz = "DELETE FROM remote_inzeraty WHERE portal='" + portal + "';";
            jdbcTemplate.execute(prikaz);
            volnaDB = true;
            return toDeleteSukromneIDs;
        }
        // treba hladat tie ktore nie su sukromne
        StringBuilder sb = new StringBuilder();
        sb.append("('");
        for (int i = 0; i < sukromne.size() - 1; i++) {
            sb.append(sukromne.get(i).getAktualny_link() + "','");
        }
        sb.append(sukromne.get(sukromne.size() - 1).getAktualny_link() + "')");

        try {
            RowMapper<Integer> rowMapper = new RowMapper<Integer>() {
                public Integer mapRow(ResultSet rs, int i) throws SQLException {
                    return Integer.parseInt(rs.getString("id"));
                }
            };
            String sql = "SELECT * FROM remote_inzeraty WHERE portal='" + portal + "' AND aktualny_link NOT IN" + sb.toString();
            toDeleteSukromneIDs = jdbcTemplate.query(sql, rowMapper);

            String prikaz = "DELETE FROM remote_inzeraty WHERE portal='" + portal + "' AND aktualny_link NOT IN" + sb.toString();
            jdbcTemplate.execute(prikaz);
            volnaDB = true;
            return toDeleteSukromneIDs;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

//    void inzertRemoteInzeraty(List<Inzerat> sukromne, String aktualnyPortal) {
//        AKTUALNY_CAS = getTimestamp();
//        StringBuilder sql = new StringBuilder();
//        int id = getRemoteTopRegisteredInzeratID();
//        for (Inzerat inzerat : sukromne) {
//            id++;
//            sql.append("INSERT INTO remote_inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,pocet_zobrazeni,zaujimavy,surne,typ,kategoria,precitany)"
//                    + "VALUES('" + id + "',"
//                    + "'" + inzerat.getPortal() + "',"
//                    + "'" + inzerat.getNazov() + "',"
//                    + "'" + inzerat.getText() + "',"
//                    + "'" + inzerat.getMeno() + "',"
//                    + "'" + inzerat.getTelefon() + "',"
//                    + "'" + inzerat.getLokalita() + "',"
//                    + "'" + inzerat.getAktualny_link() + "',"
//                    + "'" + inzerat.getCena() + "',"
//                    + "'" + inzerat.isOdoslany() + "',"
//                    + "'" + AKTUALNY_CAS + "','0',false," + inzerat.isSurne() + ","
//                    + "'" + inzerat.getTyp() + "',"
//                    + "'" + inzerat.getKategoria() + "',false); \n");
//        }
//        //System.out.println("INSERT SQL:" +sql.toString());
//        if (sql.length() == 0) {
//            volnaDB = true;
//            return;
//        }
//        try {
//            jdbcTemplate.execute(sql.toString());
//        } catch (Exception exception) {
//            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
//            //System.out.println("BAD SQL:" + sql.toString());
//        }
//        volnaDB = true;
//    }
    public List<Aktualnost> getAktualnosti() {
        AktualnostRowMapper rowMapper = new AktualnostRowMapper();
        String sql = "SELECT * FROM aktualnosti";
        List<Aktualnost> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    // POUZIVA SA IBA NA TESTOVACIE UCELY RYCHLOSTI
    public List<String> getAktualneLinky(String portal) {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("aktualny_link");
            }
        };
        String sql = "SELECT aktualny_link FROM inzeraty WHERE portal='" + portal + "'";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

//    public String getInsertSkript(List<Inzerat> sukromne, String portal) {
//        // TUTO METODU POUZIVAT IBA PRE TYCH CLIENTOV, KTORI NAJPRV UKLADAJU DO TABLE: [INZERATY]- lebo tam uz je time_inserted zaznaceny
//        StringBuilder sql = new StringBuilder();
//        AKTUALNY_CAS = getTimestamp();
//        int id = getRemoteTopRegisteredInzeratID();
//        sql.append("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + portal + "';\n");
//
//        for (Inzerat inzerat : sukromne) {
//            id++;
//            sql.append("INSERT INTO remote_inzeraty (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,pocet_zobrazeni,zaujimavy,surne,typ,kategoria,precitany)"
//                    + "VALUES('" + id + "',"
//                    + "'" + inzerat.getPortal() + "',"
//                    + "'" + inzerat.getNazov() + "',"
//                    + "'" + inzerat.getText() + "',"
//                    + "'" + inzerat.getMeno() + "',"
//                    + "'" + inzerat.getTelefon() + "',"
//                    + "'" + inzerat.getLokalita() + "',"
//                    + "'" + inzerat.getAktualny_link() + "',"
//                    + "'" + inzerat.getCena() + "',"
//                    + "'" + inzerat.isOdoslany() + "',"
//                    + "'" + inzerat.getTimeInserted() + "','0',false," + inzerat.isSurne() + ","
//                    + "'" + inzerat.getTyp() + "',"
//                    + "'" + inzerat.getKategoria() + "',false); \n");
//        }
//        return sql.toString();
//    }
    public void insertRemoteNoveSukromne(List<Inzerat> sukromne, String aktualnyPortal) {
        // NESMIEM NIC MAZAT, MAZAT SA BUDE VSETKO HROMADNE, IBA HLUPO PRIDAM TIE INZERATY KTORE ESTE NIE SU
        // MAZANIE A UPDATE ROBIA INE METODY!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        if (sukromne.size() == 0) {
            // nic nemam co pridavat
            volnaDB = true;
            return;
        } else {
            try {
                RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
                String sql = "SELECT * FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "'";
                List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
                // zistim ktore este nemam v db
                List<Inzerat> nove = new ArrayList<Inzerat>();
                for (Inzerat inz : sukromne) {
                    boolean jeVDB = false;
                    for (Inzerat inz2 : inzeraty) {
                        if (inz.getAktualny_link().equals(inz2.getAktualny_link())) {
                            jeVDB = true;
                            break;
                        }
                    }
                    if (!jeVDB) {
                        nove.add(inz);
                    }
                }
                System.out.println("DB: naslo sa " + nove.size() + " novych sukromnych inzeratov");
                inzertBazosRemoteInzeraty(nove, aktualnyPortal);

                volnaDB = true;
                return;
            } catch (Exception exception) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            }
            volnaDB = true;
            return;
        }
    }

    // NAJDEBILNEJSI KUS KODU JE TOTO!!!!!!!!!!!!!!!!!!!!!!!!!!!
//    private void deleteRemoteInzeraty(String aktualnyPortal) {
//        String prikaz = "DELETE FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "'";
//        jdbcTemplate.execute(prikaz);
//        volnaDB = true;
//    }
    public List<String> getFiremneLinky() {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("link");
            }
        };
        String sql = "SELECT link FROM firemne_linky";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    public void deleteAndInsertFiremneLinky(List<String> toDelete) {
        if (toDelete.size() == 0) {
            volnaDB = true;
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < toDelete.size() - 1; i++) {
            sb.append(toDelete.get(i) + ",");
        }
        sb.append(toDelete.get(toDelete.size() - 1) + ")");
        String prikaz = "DELETE FROM firemne_linky ";
        jdbcTemplate.execute(prikaz);
        if (toDelete.size() == 0) {
            volnaDB = true;
            return;
        }
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < toDelete.size() - 1; i++) {
            sql.append("INSERT INTO firemne_linky(link)VALUES('" + toDelete.get(i) + "');");
        }
        sql.append("INSERT INTO firemne_linky(link)VALUES('" + toDelete.get(toDelete.size() - 1) + "');");
        try {
            jdbcTemplate.execute(sql.toString());
            volnaDB = true;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            //System.out.println("BAD SQL:" + sql.toString());
        }
        volnaDB = true;
    }

    public void insertFiremneLinky(List<String> firemneLinky) {
        if (firemneLinky.size() == 0) {
            volnaDB = true;
            return;
        }
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < firemneLinky.size() - 1; i++) {
            sql.append("INSERT INTO firemne_linky(link)VALUES('" + firemneLinky.get(i) + "');");
        }
        sql.append("INSERT INTO firemne_linky(link)VALUES('" + firemneLinky.get(firemneLinky.size() - 1) + "');");
        try {
            jdbcTemplate.execute(sql.toString());
            volnaDB = true;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            //System.out.println("BAD SQL:" + sql.toString());
        }
        volnaDB = true;
    }

    public List<Okres> getOkresy() {
        RowMapper<Okres> rowMapper = new RowMapper<Okres>() {

            public Okres mapRow(ResultSet rs, int i) throws SQLException {
                String obec = rs.getString("obec");
                String okres = rs.getString("okres");
                return new Okres(obec, okres);
            }
        };
        String sql = "SELECT * FROM okresy";
        List<Okres> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    public List<String> getOkresneMesta() {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("okres");
            }
        };
        String sql = "select okres from okresy group by okres";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    public void deleteFiremne(List<String> toDeleteFiremne) {
        // treba hladat tie ktore nie su sukromne
        StringBuilder sb = new StringBuilder();
        sb.append("('");
        for (int i = 0; i < toDeleteFiremne.size() - 1; i++) {
            sb.append(toDeleteFiremne.get(i) + "','");
        }
        sb.append(toDeleteFiremne.get(toDeleteFiremne.size() - 1) + "')");

        try {
            RowMapper<Integer> rowMapper = new RowMapper<Integer>() {
                public Integer mapRow(ResultSet rs, int i) throws SQLException {
                    return Integer.parseInt(rs.getString("id"));
                }
            };
            String prikaz = "DELETE FROM firemne_linky WHERE link IN" + sb.toString();
            jdbcTemplate.execute(prikaz);
            volnaDB = true;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
    }

    public List<Inzerat> getRemoteInzeratyList() {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM remote_inzeraty  order by id asc";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    List<Inzerat> getRemoteInzeratyListGreaterThanLastTimeInserted(String timeInserted) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM remote_inzeraty WHERE time_inserted>timestamp('" + timeInserted + "')";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    List<Inzerat> getRemoteInzeratyWhereIDNotIN(List<Integer> idckaPortal) {
        if (idckaPortal.size() == 0) {
            volnaDB = true;
            return new ArrayList<Inzerat>();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < idckaPortal.size() - 1; i++) {
            sb.append(idckaPortal.get(i) + ",");
        }
        sb.append(idckaPortal.get(idckaPortal.size() - 1) + ")");

        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM remote_inzeraty WHERE  id NOT IN" + sb.toString();
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return new ArrayList<Inzerat>();
    }

    int getRemoteCountPortal() {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("pocet");
            }
        };
        String sql = "SELECT count(*) as pocet FROM remote_inzeraty ";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return Integer.parseInt(inzeraty.get(0));
    }

    void updateAktualnost(String db_user) {
        AKTUALNY_CAS = getTimestamp();
        jdbcTemplate.execute("UPDATE aktualnosti SET datum='" + AKTUALNY_CAS + "' WHERE portal='" + db_user + "';\n");
        volnaDB = true;
        return;
    }

    public List<Inzerat> getInzeratyListLinky(String aktualnyPortal) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratIbaIDLinkyRowMapper();
            String sql = "SELECT * FROM inzeraty WHERE portal='" + aktualnyPortal + "'";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    public List<Inzerat> getRemoteInzeratyListLinky(String aktualnyPortal) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratIbaIDLinkyRowMapper();
            String sql = "SELECT * FROM remote_inzeraty WHERE portal='" + aktualnyPortal + "' order by id asc";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    void aplikujFiltraciuInzeratov() {
        pockajNaDatabazu();
        List<Inzerat> inzeraty = getRemoteInzeratyList();
        List<Inzerat> odfiltrovane = new ArrayList<Inzerat>();
        boolean[] vyuzite = new boolean[inzeraty.size()];
        String[] portaly = {"http://www.nehnutelnosti.sk", "http://reality.bazar.sk", "http://reality.inzercia.sk", "http://reality.bazos.sk"};
        // podla textu odstranime duplikaty
        List<Inzerat> rovnake = new ArrayList<Inzerat>();
        Inzerat inz;
        Inzerat inz2;
        String text;
        String text2;
        int rovnakeCount = 0;
        for (int i = 0; i < inzeraty.size(); i++) {
            if (vyuzite[i]) {
                continue;
            }
            inz = inzeraty.get(i);
            if (inz.getNazov().equals("TEST")){
                System.out.println("nazov je TEST");
                continue;
            }
            rovnake.add(inz);
            text = inz.getText().replaceAll("[\n\t ]", "");
            // hladame rovnake inzeraty ako tento
            for (int j = i + 1; j < inzeraty.size(); j++) {
                if (!vyuzite[j]) {
                    inz2 = inzeraty.get(j);
                    if (inz2.getTelefon().equals(inz.getTelefon())) {
                        // nasli sme inzerat od toho isteho cloveka, mozno ma rovnaky text
                        text2 = inz2.getText().replaceAll("[\n\t ]", "");
                        if (text.equals(text2) && !inz.getPortal().equals(inz2.getPortal())) {
//                            System.out.println("text1: "+text );
//                            System.out.println("text2: "+text);
//                            JOptionPane.showMessageDialog(null, "nasli sa zhodne texty");
                            // nasli sme rovnaky inzerat
                            rovnake.add(inz2);
                            vyuzite[j] = true;
                        }
                    }
                }
            }
            // teraz uz mame vsetky prejdene, ak je iba jeden rovnaky tak ho pridavame, ak je viacej tak podla priority portalu

            if (rovnake.size() == 1) {
                odfiltrovane.add(inz);
                // System.out.println(inz.getAktualny_link());
            } else {
                //System.out.println("========================================");
                rovnakeCount++;
                //System.out.println("rovnake cislo: " + rovnakeCount);
//                for (Inzerat rovnaky : rovnake) {
//                    System.out.println("link: " + rovnaky.getAktualny_link());
//                }
                for (String portal : portaly) {
                    boolean ukonci = false;
                    for (Inzerat rovnaky : rovnake) {
                        if (rovnaky.getPortal().equals(portal)) {
                            // nasli sme inzerat od nasho portalu oblubeneho
                            odfiltrovane.add(rovnaky);
                            //System.out.println("VYHRAL: " + rovnaky.getAktualny_link());
                            ukonci = true;
                            break;
                        }
                    }
                    if (ukonci) {
                        break;
                    }
                }
                // z viacej rovnakych sme si vybrali nas najlepsi
                // vyprazdnime sa
                //System.out.println("========================================");
            }
            rovnake = new ArrayList<Inzerat>();
        }

        pockajNaDatabazu();
        jdbcTemplate.execute("delete from odfiltrovane");
        insertOdfiltrovaneInzeraty(odfiltrovane);
        System.out.println("pocet odfiltrovanych inzeratov: " + odfiltrovane.size());
        // PREDPOKLADAM ZE VSETKY INZERATY SA INZERTNU A ZE NEMUSIM ROBIT KONTROLU
    }

    public void insertOdfiltrovaneInzeraty(List<Inzerat> unikatne) {
        StringBuilder sql = new StringBuilder();
        if (unikatne.size() == 0) {
            volnaDB = true;
            return;
        }
        sql.append("INSERT INTO odfiltrovane (id,portal,nazov,text,meno,telefon,lokalita,aktualny_link,cena,sent,time_inserted,zaujimavy,pocet_zobrazeni,surne,typ,kategoria,precitany)VALUES\n");
        //int id = getTopRegisteredInzeratID();
        for (int i = 0; i < unikatne.size() - 1; i++) {
            Inzerat inzerat = unikatne.get(i);
            sql.append(""
                    + "('" + inzerat.getId() + "',"
                    + "'" + inzerat.getPortal() + "',"
                    + "'" + inzerat.getNazov() + "',"
                    + "'" + inzerat.getText() + "',"
                    + "'" + inzerat.getMeno() + "',"
                    + "'" + inzerat.getTelefon() + "',"
                    + "'" + inzerat.getLokalita() + "',"
                    + "'" + inzerat.getAktualny_link() + "',"
                    + "'" + inzerat.getCena() + "',"
                    + "" + inzerat.isOdoslany() + ","
                    + "'" + inzerat.getTimeInserted() + "',false,'0'," + inzerat.isSurne() + ","
                    + "'" + inzerat.getTyp() + "',"
                    + "'" + inzerat.getKategoria() + "',false), \n");
        }
        // pridame maxHladanychInzeratov izerat
        Inzerat inzerat = unikatne.get(unikatne.size() - 1);
        sql.append(""
                + "('" + inzerat.getId() + "',"
                + "'" + inzerat.getPortal() + "',"
                + "'" + inzerat.getNazov() + "',"
                + "'" + inzerat.getText() + "',"
                + "'" + inzerat.getMeno() + "',"
                + "'" + inzerat.getTelefon() + "',"
                + "'" + inzerat.getLokalita() + "',"
                + "'" + inzerat.getAktualny_link() + "',"
                + "'" + inzerat.getCena() + "',"
                + "" + inzerat.isOdoslany() + ","
                + "'" + inzerat.getTimeInserted() + "',false,'0'," + inzerat.isSurne() + ","
                + "'" + inzerat.getTyp() + "',"
                + "'" + inzerat.getKategoria() + "',false); \n");
        try {
            jdbcTemplate.execute(sql.toString());
            volnaDB = true;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            //System.out.println("BAD SQL:" + sql.toString());
        }
        volnaDB = true;
    }

    private void pockajNaDatabazu() {
        while (!mamDatabazu()) {
            try {
                System.out.println("cakam na Databazu");
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    List<Inzerat> getOdfiltrovaneInzeratyListGreaterThanLastTimeInserted(String timeInserted) {
        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM odfiltrovane WHERE time_inserted>timestamp('" + timeInserted + "')";
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return null;
    }

    List<Inzerat> getOdfiltrovaneInzeratyWhereIDNotIN(List<Integer> idckaPortal) {
        if (idckaPortal.size() == 0) {
            volnaDB = true;
            return new ArrayList<Inzerat>();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < idckaPortal.size() - 1; i++) {
            sb.append(idckaPortal.get(i) + ",");
        }
        sb.append(idckaPortal.get(idckaPortal.size() - 1) + ")");

        try {
            RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
            String sql = "SELECT * FROM odfiltrovane WHERE  id NOT IN" + sb.toString();
            List<Inzerat> inzeraty = jdbcTemplate.query(sql, rowMapper);
            volnaDB = true;
            return inzeraty;
        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
        return new ArrayList<Inzerat>();
    }

    int getOdfiltrovaneCountPortal() {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("pocet");
            }
        };
        String sql = "SELECT count(*) as pocet FROM odfiltrovane ";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return Integer.parseInt(inzeraty.get(0));
    }

    List<String> getOdfiltrovaneLinky() {
        RowMapper<String> rowMapper = new RowMapper<String>() {

            public String mapRow(ResultSet rs, int i) throws SQLException {
                return rs.getString("aktualny_link");
            }
        };
        String sql = "SELECT aktualny_link FROM odfiltrovane ";
        List<String> inzeraty = jdbcTemplate.query(sql, rowMapper);
        volnaDB = true;
        return inzeraty;
    }

    List<Inzerat> getOdfiltrovaneInzeratyList() {
        RowMapper<Inzerat> rowMapper = new InzeratRowMapper();
        String sql = "select * from odfiltrovane";
        List<Inzerat> inzeraty = new ArrayList<Inzerat>();
        inzeraty.addAll(jdbcTemplate.query(sql, rowMapper));
        volnaDB = true;
        return inzeraty;
    }

    public void ulozVceraNajdenychStatistiku() {
        System.out.println("ulozVceraNajdenychStatistiku");
        try {
            RowMapper<Najdene> rowMapper = new RowMapper<Najdene>() {
                public Najdene mapRow(ResultSet rs, int i) throws SQLException {
                    Najdene n = new Najdene();
                    n.pocet = Integer.parseInt(rs.getString("pocet"));
                    n.den = Integer.parseInt(rs.getString("den"));
                    n.portal = rs.getString("portal");
                    n.rok = Integer.parseInt(rs.getString("rok"));
                    return n;
                }
            };
            // najprv skontrolujeme ci uz tam nie je
            String sql = "select * from najdene where den=dayofyear(DATEADD ( 'day', -1, current_timestamp )) and rok=year(DATEADD ( 'day', -1, current_timestamp ))";
            List<Najdene> inzeraty = jdbcTemplate.query(sql, rowMapper);
            if (inzeraty.size() == 0) {
                // este nemame za vcerajsi den statistiku
                sql = "select dayofyear(time_inserted) as den, year(DATEADD ( 'day', -1, current_timestamp ))as rok,portal, count(*) as pocet from odfiltrovane \n"
                        + "where dayofyear(time_inserted)=dayofyear(current_timestamp)-1 group by den,portal order by den";
                inzeraty = jdbcTemplate.query(sql, rowMapper);
                StringBuilder sb = new StringBuilder();
                // TODO: ak vcera som nic nenasiel tak hodi vynimku
                for (Najdene prve : inzeraty) {
                    sb.append("INSERT INTO najdene(den,rok,portal,pocet)VALUES(" + prve.den + "," + prve.rok + " ,'" + prve.portal + "'," + prve.pocet + " );\n");
                }
                jdbcTemplate.execute(sb.toString());
            }

        } catch (Exception exception) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
        }
        volnaDB = true;
    }
}

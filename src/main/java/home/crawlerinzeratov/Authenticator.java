/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

import crawleri.BazosCrawler;
import deleted.JsoupClient;
import static home.crawlerinzeratov.MySQLDatabase.DB_URL;
import java.awt.Image;
import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import org.apache.http.auth.UsernamePasswordCredentials;

import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class Authenticator {

    static final String LOGIN_URL = "http://www.1000blockov.sk/registrator";
    //static final String REG_URL = "https://narodnablockovaloteria.tipos.sk/sk/administracia/registracia-dokladu";

    private Connection connection;
    private Map<String, String> cookies;
    private String viewState;
    private String eventValidation;
    String connectionUrl;
    private boolean isLogged;

    public static java.sql.Connection connect = null;
    public static Statement statement = null;
    public static ResultSet resultSet = null;

    public Authenticator() {
        cookies = new HashMap<String, String>();
        // login je default
        connectionUrl = LOGIN_URL;
        System.out.println("Jsoup client process started.");

        connect();

    }

    public void connect() {
        connection = Jsoup.connect(getConnectionUrl());
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }

    public Document increaseAndReturnPocet(String macAddress, int uid, int pocet) {
        connectionUrl = LOGIN_URL + "/registracii.php?user=" + macAddress + "&appid=" + uid + "&pocet=" + pocet;
        //System.out.println("authenticator url: "+connectionUrl);
        connect();

        connection.method(Method.GET);
        try {
            Response response = connection.execute();
            Document document = response.parse();
            //System.out.println(document);
            return document;
        } catch (IOException e) {
            System.err.println("error on connecting");
            Logger.getLogger(JsoupClient.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }

    public Document getMacAddressCountForAppid(int appid) {
        connectionUrl = LOGIN_URL + "/appid.php?appid=" + appid;
        //System.out.println("authenticator url: "+connectionUrl);
        connect();

        connection.method(Method.GET);
        try {
            Response response = connection.execute();
            Document document = response.parse();
            //System.out.println(document);
            return document;
        } catch (IOException e) {
            System.err.println("error on connecting");
            Logger.getLogger(JsoupClient.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }

    public static void main(String[] args) {
        Authenticator a = new Authenticator();
        //Document d= increaseAndReturnMaxPocetet("novy user", 1, 7);
        Document d = a.getMacAddressCountForAppid(1);
        System.out.println(d);
    }

    public static String getCurrentDBUser() {
        String username = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            java.sql.Connection conn;
            Statement stmt;
            conn = DriverManager.getConnection("jdbc:mysql://mysql51.websupport.sk:3309/autentifikaciaDB?characterEncoding=UTF-8", "authUser", ">Swub8Og#");
            stmt = conn.createStatement();
            String sql;
            sql = " SELECT username FROM db_data";
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                username = rs.getString("username");
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
        return username;
    }

    static void setCurrentUser(String url, String user, String pass) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            java.sql.Connection conn;
            Statement stmt;
            conn = DriverManager.getConnection("jdbc:mysql://mysql51.websupport.sk:3309/autentifikaciaDB?characterEncoding=UTF-8", "authUser", ">Swub8Og#");
            stmt = conn.createStatement();
            String sql;
            sql = "UPDATE db_data SET connection_string='"+url+"', username='"+user+"', password='"+pass+"';";
            stmt.executeUpdate(sql.toString());
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            close();
        }
    }

    // you need to close all three to make sure
    public static void close() {
        close((Closeable) resultSet);
        close((Closeable) statement);
        close((Closeable) connect);
    }

    public static void close(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
            // don't throw now as it might leave following closables in undefined state
        }
    }

}

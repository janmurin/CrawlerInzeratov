package crawleri;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import static clienti.ClientBazar.BAZAR_LINK;
import clienti.Node;
import deleted.JsoupClient;
import home.crawlerinzeratov.Database;
import home.crawlerinzeratov.Inzerat;
import java.awt.Image;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class JsoupCrawler {

    static final String LOGIN_URL = "http://www.1000blockov.sk/registrator";
    //static final String REG_URL = "https://narodnablockovaloteria.tipos.sk/sk/administracia/registracia-dokladu";

    private Connection connection;
    private Map<String, String> cookies;
    private String viewState;
    private String eventValidation;
    String connectionUrl;
    private boolean isLogged;

    // TODO: sem pridu registracne udaje, pripadne v GUI ich mozte tahat z textovych policok
    public String EMAIL = "";
    public String PASSWORD = "";
    private List<String> okresneMesta;
    private List<Okres> okresy;

    public JsoupCrawler() {
    }

    public void connect() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_6_8) AppleWebKit/534.30 (KHTML, like Gecko) Chrome/12.0.742.122 Safari/534.30";
//        Jsoup.connect("http://example.com").userAgent(ua).get().html();
        connection = Jsoup.connect(getConnectionUrl()).userAgent(ua);
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }

    private void nacitajStromMien() {
        System.out.println("nacitavam strom mien");
        Node koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
        int pocetUnikatnych = 0;
        Database db = new Database();
        List<Integer> toDelete = new ArrayList<Integer>();
        List<Inzerat> noveInzeraty = db.getRemoteInzeratyList("http://reality.inzercia.sk");

        long startAnalyzis = System.currentTimeMillis();
        for (int i = 0; i < noveInzeraty.size(); i++) {
            Inzerat inzerat = noveInzeraty.get(i);
            if (!jeInzeratVStrome(koren, inzerat.getAktualny_link())) {
                pocetUnikatnych++;
            } else {
                toDelete.add(inzerat.getId());
            }
        }
        // k tymto inzeratom linkom treba este pridat najdene linky firemnych inzeratov
//        List<String> firemne = db.getFiremneLinky();
//        for (int i = 0; i < firemne.size(); i++) {
//            // nahadzeme do stromu navstivenych linkov aj firemne
//            String inzerat = firemne.get(i);
//            jeInzeratVStrome(koren, inzerat);
//        }
        // System.out.println("do stromu sme pridali aj  firemnych linkov: " + firemne.size());
        System.out.println("analyzis time: " + (System.currentTimeMillis() - startAnalyzis));
        System.out.println("pocet unikatnych inzeratov: " + pocetUnikatnych);
        System.out.println("to delete size: " + toDelete.size());
        /// pockajNaDatabazu();
        //database.deleteRemoteDuplikatneInzeraty(toDelete);
    }

    private boolean jeInzeratVStrome(Node aktualny, String nazov) {
        if (nazov.length() == 0) {
            return true;
        }
        // pozriet jeho deti, ked nema dieta vytvorit novu vetvu, ked ma tak return 
        for (Node dieta : aktualny.potomkovia) {
            if (dieta.hodnota.equalsIgnoreCase(nazov.charAt(0) + "")) {
                return jeInzeratVStrome(dieta, nazov.substring(1));
            }
        }

        // pridame novu vetvu
        while (nazov.length() > 0) {
            Node novy = new Node(nazov.charAt(0) + "");
            nazov = nazov.substring(1);
            aktualny.potomkovia.add(novy);
            aktualny = novy;
        }

        return false;
    }

    /**
     * metoda na zistenie okresneho mesta
     *
     * @param lokalita
     * @return
     */
    public String getOkresneMesto(String lokalita) {
        for (String okres : okresneMesta) {
            if (lokalita.contains(okres)) {
                return okres;
            }
        }
        for (Okres o : okresy) {
            if (lokalita.contains(o.obec)) {
                return o.okres;
            }
        }

        return "Ostatné";
    }

    public void execute() {
        Database db = new Database();
        db.updateRemoteSurneInzeratyVsetko();
    }

    public static void main(String[] args) {
        JsoupCrawler a = new JsoupCrawler();
        a.execute();
//        boolean[] jeObsadene=new boolean[600];
//        
//        int dif=30;
//        int idx=0;
//        int pocet=0;
//        System.out.println("INSERT INTO clienti (id,nazov_pc,kluc,ma_povolenie,cas_aktualizacie,kredit,minutych)VALUES");
//        while (dif>=3){
//            if (!jeObsadene[idx]){
//                pocet++;
//                System.out.println("('"+pocet+"','','',true,'"+idx+"',20,1),");
//                jeObsadene[idx]=true;
//            }
//            idx+=dif;
//            if(idx>=600){
//                dif/=2;
//                idx=0;
//            }
//        }
//        for (int i=0; i<jeObsadene.length; i++){
//            if (jeObsadene[i]){
//                System.out.print(i+" ");
//            }
//        }
        
    }

    public String getETAtime(long startTime, int pocetInzeratov, int vsetkych) {
        double rychlost = ((System.currentTimeMillis() - startTime) / 1000.0) / pocetInzeratov;
        double etaTime = (vsetkych - pocetInzeratov) * rychlost;
        int hodinE = (int) ((etaTime) / (3600));
        int minutE = (int) ((etaTime) / (60));
        int sekundE = (int) ((etaTime));
        sekundE %= 60;
        minutE %= 60;
        String hodinStringE = "" + hodinE;
        if (hodinE < 10) {
            hodinStringE = "0" + hodinE;
        }
        String minutStringE = "" + minutE;
        if (minutE < 10) {
            minutStringE = "0" + minutE;
        }
        String sekundStringE = "" + sekundE;
        if (sekundE < 10) {
            sekundStringE = "0" + sekundE;
        }
        //System.out.println("ETA:" + (hodinStringE + ":" + minutStringE + ":" + sekundStringE));
        return (hodinStringE + ":" + minutStringE + ":" + sekundStringE);
//            System.out.println("etaTime: "+etaTime);
//            System.out.println("rychlost: "+rychlost);
    }

    private boolean jeInzeratVStrome2(Node aktualny, String nazov) {
        boolean jeVStrome = true;
        while (nazov.length() > 0) {
            // skumame jeho potomkov
            boolean naslisme = false;
            for (Node dieta : aktualny.potomkovia) {
                if (dieta.hodnota.equalsIgnoreCase(nazov.charAt(0) + "")) {
                    aktualny = dieta;
                    naslisme = true;
                    nazov = nazov.substring(1);
                    break;
                    //return jeInzeratVStrome(dieta, nazov.substring(1));
                }
            }
            if (naslisme) {
                continue;
            }
            jeVStrome = false;
            // ani jeden potomok neobsahuje aktualny znak, vytvarame novu vetvu
            while (nazov.length() > 0) {
                Node novy = new Node(nazov.charAt(0) + "");
                nazov = nazov.substring(1);
                aktualny.potomkovia.add(novy);
                aktualny = novy;
            }
        }
        return jeVStrome;
    }

    public Document getPage(String string) {
        Document document = null;
        while (document == null) {
            try {
                connectionUrl = string;
                connect();

                connection.method(Method.GET);

                Response response = connection.execute();
                document = response.parse();
                //System.out.println(document);

            } catch (Exception e) {
//                System.out.println("getPage exception: " + e);
//                System.out.println("retrying...");
            }
        }
        //Document document = Jsoup.parse(html);
//        document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
//        document.select("br").append("\n");
//        document.select("p").prepend("\n\n");
        return document;
    }

    public String getElapsedTime(long startTime) {
        double elapsedTime = ((System.currentTimeMillis() - startTime) / 1000.0);
        int hodinE = (int) ((elapsedTime) / (3600));
        int minutE = (int) ((elapsedTime) / (60));
        int sekundE = (int) ((elapsedTime));
        sekundE %= 60;
        minutE %= 60;
        String hodinStringE = "" + hodinE;
        if (hodinE < 10) {
            hodinStringE = "0" + hodinE;
        }
        String minutStringE = "" + minutE;
        if (minutE < 10) {
            minutStringE = "0" + minutE;
        }
        String sekundStringE = "" + sekundE;
        if (sekundE < 10) {
            sekundStringE = "0" + sekundE;
        }
        //System.out.println("ETA:" + (hodinStringE + ":" + minutStringE + ":" + sekundStringE));
        return (hodinStringE + ":" + minutStringE + ":" + sekundStringE);
    }

}

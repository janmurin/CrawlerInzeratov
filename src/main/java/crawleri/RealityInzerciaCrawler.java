package crawleri;

import clienti.ClientNehnutelnosti;
import static clienti.ClientNehnutelnosti.NEHNUTELNOSTI_LINK;
import clienti.ClientRealityInzeraty24;
import clienti.ClientRealityInzeratyALL;
import clienti.ClientRealityInzeratyHladajLinky;
import clienti.Node;
import deleted.SilentHtmlUnitDriver;
import home.crawlerinzeratov.Database;
import home.crawlerinzeratov.Inzerat;
import crawleri.JsoupCrawler;
import home.crawlerinzeratov.Kategoria;
import home.crawlerinzeratov.MySQLDatabase;
import home.crawlerinzeratov.Typ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

public class RealityInzerciaCrawler implements Runnable, PropertyChangeListener {

    ExecutorService es = Executors.newCachedThreadPool();
    private Node koren;
    private final Database database;
    private final UrychlovacRemoteInsert urychlovacRemoteInsert;
    public static final String REALITY_INZERCIA_LINK = "http://reality.inzercia.sk";
    public static final int DELETE_STARE_MOD = 0;
    public static final int DOWNLOAD_24 = 1;
    public static final int DOWNLOAD_ALL = 2;
    public static final int UPDATE_ALL = 3;
    private long startTime;
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final String aktualnyPortal;
    private final int zvoleny_mod;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final MySQLDatabase mysql;
    JsoupCrawler jcrawler = new JsoupCrawler();
    private List<ClientRealityInzeratyALL> clientiAll;
    private List<ClientRealityInzeraty24> clienti24;
    private List<ClientRealityInzeratyHladajLinky> clientiHladajLinky;
    private int aktualnyKlient = 0;
    private List<Setting> settings;
    public int ukoncene = 0;

    public RealityInzerciaCrawler(int mode, Database db, MySQLDatabase msd) {
        database = db;
        mysql = msd;
        aktualnyPortal = REALITY_INZERCIA_LINK;
        zvoleny_mod = mode;
        urychlovacRemoteInsert = new UrychlovacRemoteInsert(database, null, aktualnyPortal);
    }

    public void run() {
        try {
            startTime = System.currentTimeMillis();
            clientiAll = new ArrayList<ClientRealityInzeratyALL>();
            clientiHladajLinky = new ArrayList<ClientRealityInzeratyHladajLinky>();
            clienti24 = new ArrayList<ClientRealityInzeraty24>();
            if (zvoleny_mod == DELETE_STARE_MOD) {
//            // PREJDEM VSETKY ODKAZY INZERATOV NA PORTALI A VYMAZEM Z DATABAZ TIE INZERATY KTORE NEBUDU V TOMTO ZOZNAME
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + " SPUSTENIE DELETE_STARE_MOD");
                // ulozime si kolko novych inzeratov vcera naslo
                pockajNaDatabazu();
                database.ulozVceraNajdenychStatistiku();

                hladajLinkyInzeratovDoStromu();
                //hladajNoveInzeraty_RealityInzerciaALL();
            }
            if (zvoleny_mod == DOWNLOAD_ALL) {
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + " SPUSTENIE DOWNLOAD_ALL");
                hladajNoveInzeraty_RealityInzerciaALL();
                // updatne sa databaza automaticky ked skoncia clienti
            }
            if (zvoleny_mod == DOWNLOAD_24) {
                //int vsetkychInzeratov = getPocetVsetkychInzeratov(aktualnyPortal);
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_24");
                changes.firePropertyChange("logHlaska", "", sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_24");
                hladajNoveInzeraty_24();
                //updateServerDB();
                // System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE DOWNLOAD_24 trvanie: " + getElapsedTime(startTime));
            }
            if (zvoleny_mod == UPDATE_ALL) {
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE UPDATE_ALL");
                updateServerDB();
                System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE UPDATE_ALL trvanie: " + getElapsedTime(startTime));
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
            changes.firePropertyChange("VYNIMKA", false, true);
            changes.firePropertyChange("logHlaska", "", sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  VYNIMKA: " + e.toString());
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if ("ClientRealityInzeraty_ukonceny".equals(evt.getPropertyName())) {
            // ukonceny client, spusti dalsieho cakajuceho alebo cakaj dokym nedojdu vsetci alebo vyhodnot
            System.out.println("pocetUkoncenych clientov: " + ukoncene);
            if (ukoncene == clientiAll.size()) {
                ulozNajdeneDoDatabazy();
            }
            if (aktualnyKlient < clientiAll.size()) {
                // este mame clienta ktory caka na spustenie
                spustiClienta(clientiAll.get(aktualnyKlient));
                aktualnyKlient++;
            }
        }
        if ("ClientRealityInzeraty24_ukonceny".equals(evt.getPropertyName())) {
            // ukonceny client, spusti dalsieho cakajuceho alebo cakaj dokym nedojdu vsetci alebo vyhodnot
            System.out.println("pocetUkoncenych clientov: " + ukoncene);
            if (ukoncene == clienti24.size()) {
                ulozNajdeneDoDatabazy24();
            }
        }
        if ("realityInzerciaNajdeneLinky".equals(evt.getPropertyName())) {
            System.out.println(" ukoncene: " + ukoncene);
            if (ukoncene >= clientiHladajLinky.size()) {
//                int count = 0;
//                for (ClientRealityInzeratyHladajLinky c : clientiHladajLinky) {
//                    if (c.skoncil) {
//                        count++;
//                    }
//                }
//                System.out.println("count: " + count);
////                if (count == clientiHladajLinky.size()) {
////                    vymazNeaktualneInzeraty();
////                }
                if (ukoncene == clientiHladajLinky.size()) {
                    vymazNeaktualneInzeraty();
                }
            }
//            if (aktualnyKlient < clientiHladajLinky.size()) {
//                // este mame clienta ktory caka na spustenie
//                spustiClienta(clientiHladajLinky.get(aktualnyKlient));
//                aktualnyKlient++;
//            }
        }
    }

    private void hladajNoveInzeraty_RealityInzerciaALL() {
        List<Inzerat> toInsert = new ArrayList<Inzerat>();;// nove inzeraty
        int pocetInzeratov = 0;
//        nacitajStromMien();
//        nacitajStromFiremnychLinkov();

        try {
            long startTime = System.currentTimeMillis();
            // prehladame podla kategoriach
            settings = getSettings();
            for (Setting s : settings) {
                Document doc = jcrawler.getPage(s.link);
                int pocetStranokMusimNavstivit = 0;
                int najdenychInzeratov = 0;
                try {
                    String najdenych = doc.select("html body div#main div#right div.pagination.ostatne div").get(0).text();
                    if (najdenych.contains("Nájdených:")) {
                        najdenych = najdenych.replace("Nájdených:", "").trim();
                        najdenychInzeratov = Integer.parseInt(najdenych.split(" ")[0].trim());
                    }
                    pocetInzeratov += najdenychInzeratov;
                    if (najdenychInzeratov > 15) {
                        // hladame stranky
                        String stranok = doc.select("html body div#main div#right div.pagination.ostatne div p.list strong.page_info").text();
                        stranok = stranok.replace("Strana", "");
                        stranok = stranok.replace(":", "").trim();
                        pocetStranokMusimNavstivit = Integer.parseInt(stranok.split("z")[1].trim());
                    } else {
                        //System.out.println("link: " + s.link+"  inzeratov: " + najdenychInzeratov);
                    }
                    if (najdenychInzeratov > 0) {
                        // teraz mame linky na ktorych sa nachadzaju inzeraty s poctom stran a stranok
                        s.inzeratov = najdenychInzeratov;
                        s.stran = pocetStranokMusimNavstivit;
                        if (zvoleny_mod == DOWNLOAD_24 || zvoleny_mod == DOWNLOAD_ALL) {
                            ClientRealityInzeratyALL novy = new ClientRealityInzeratyALL(database, s, this);
                            clientiAll.add(novy);
                        }
//                        if (zvoleny_mod == DELETE_STARE_MOD) {
//                            ClientRealityInzeratyHladajLinky novy = new ClientRealityInzeratyHladajLinky(s, this);
//                            clientiHladajLinky.add(novy);
//                        }
                        System.out.println("link: " + s.link + "  inzeratov: " + najdenychInzeratov + " pocet stranok: " + pocetStranokMusimNavstivit);
                    }
                } catch (Exception e) {
                    System.out.println("vynimka: " + e);
                    System.out.println("  pocet inzeratov: NEMA");
                    Logger.getLogger(ClientRealityInzeratyALL.class.getName()).log(Level.SEVERE, null, e);
                }

            }
            if (zvoleny_mod == DELETE_STARE_MOD) {
                System.out.println("hladanie linkov ukoncene, pocet clientov: " + clientiHladajLinky.size());
            } else {
                System.out.println("hladanie linkov ukoncene, pocet clientov: " + clientiAll.size());
            }
            System.out.println("spustam prvych 10 clientov");
//            // teraz spustime prvych 10 clientov
            for (int i = 0; i < 10; i++) {
                if (zvoleny_mod == DOWNLOAD_24 || zvoleny_mod == DOWNLOAD_ALL) {
                    spustiClienta(clientiAll.get(i));
                }
                if (zvoleny_mod == DELETE_STARE_MOD) {
                    spustiClienta(clientiHladajLinky.get(i));
                }
                aktualnyKlient++;
                Thread.sleep(5000);
            }
            System.out.println("while cyklus skoncil, nasli sme " + (pocetInzeratov) + " inzeratov");
//            System.out.println("kontrola linkov: ");
//            for (int i=0; i<clienti.size(); i++){
//                System.out.println("CLIENT "+i);
//                System.out.println("link: "+clienti.get(i).setting.link+" inzeratov: "+clienti.get(i).setting.inzeratov+" stran: "+clienti.get(i).setting.stran);
//            }
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void updateServerDB() {
        System.out.println("INIT update server DB");
        // PRED KAZDYM UPLOADOM SKONTROLOVAT DUPLICITU INZERATOV
        nacitajStromMien();
        System.out.println("Hladam surne inzeraty");
        pockajNaDatabazu();
        database.updateRemoteSurneInzeratyAktualnyCas();
        changes.firePropertyChange("aktualnostiUpdated", false, true);

        System.out.println("server DB sa updatne pri hromadnom update");
//        // TERAZ AUTOMATICKY UPDATNUT VZDIALENU DATABAZU O NOVE INZERATY
//        System.out.println("updating remote server db");
//        pockajNaMysqlDatabazu();
//        Inzerat lastTimeInserted = mysql.getLastTimeInzeratInserted(aktualnyPortal);
//        // zistili sme aky mame maxHladanychInzeratov time_inserted a podla toho vieme ktore inzeraty mame poslat
//        pockajNaDatabazu();
//        List<Inzerat> noveInzeraty = database.getRemoteInzeratyListGreaterThanLastTimeInserted(lastTimeInserted.getTimeInserted(), aktualnyPortal);
//        if (noveInzeraty != null && noveInzeraty.size() == 0) {
//            System.out.println("ziadne nove inzeraty nenajdene");
//            //changes.firePropertyChange("aktualnostiUpdated", true, true); tu to nie je potrebne lebo do databazy sa vlozia vzdy aj ked je prazdny zoznam
//            return;
//        } else {
//            System.out.println("nasli sa nove inzeraty");
//            List<Inzerat> toserver = new ArrayList<Inzerat>();
//            long startTime2 = System.currentTimeMillis();
//            System.out.println("posielam do vzdialenej databazy " + noveInzeraty.size() + " inzeratov");
//            for (int i = 0; i < noveInzeraty.size(); i++) {
//                if (toserver.size() < 10000) {
//                    toserver.add(noveInzeraty.get(i));
//                } else {
//                    pockajNaMysqlDatabazu();
//                    mysql.inzertInzeraty(toserver);
//                    System.out.print("insertnutych " + i + "/" + noveInzeraty.size() + " ");
//                    System.out.println(getETAtime(startTime2, i, noveInzeraty.size()));
//                    toserver = new ArrayList<Inzerat>();
//                }
//            }
//            pockajNaMysqlDatabazu();
//            mysql.inzertInzeraty(toserver);
//        }
//
//        // moze sa stat ze niektore inzeraty tam nedosli
//        // spytame sa ktore tam dosli a znova posielame tie co nedosli
//        System.out.println("kontrola ci su vsetky tam");
//        List<Inzerat> toserver = new ArrayList<Inzerat>();
//        boolean vsetkyDosli = false;
//        while (!vsetkyDosli) {
//            // vypytame si idcka vsetkych inzeratov z aktualneho portalu
//            // vypiseme z lokalnej databazy vsetky inzeraty z aktualneho portalu, ktore nemaju idcka na serveri
//            pockajNaMysqlDatabazu();
//            List<Integer> idckaPortal = mysql.getInzeratyIDs(aktualnyPortal);
//            pockajNaDatabazu();
//            List<Inzerat> chybneInzeraty = database.getRemoteInzeratyWhereIDNotIN(idckaPortal, aktualnyPortal);
//            if (chybneInzeraty.size() == 0) {
//                //System.out.println("vsetky inzeraty dosli");
//                break;
//            }
//            System.out.println("nedoslo " + chybneInzeraty.size() + ", posielam znova");
//            toserver = new ArrayList<Inzerat>();
//            long startTime2 = System.currentTimeMillis();
//            for (int i = 0; i < chybneInzeraty.size(); i++) {
//                if (toserver.size() < 10000) {
//                    toserver.add(chybneInzeraty.get(i));
//                } else {
//                    System.out.println("posielam do vzdialenej databazy " + toserver.size() + " inzeratov");
//                    pockajNaMysqlDatabazu();
//                    mysql.inzertInzeraty(toserver);
//                    System.out.print("insertnutych " + i + "/" + chybneInzeraty.size() + " ");
//                    System.out.println(getETAtime(startTime2, i, chybneInzeraty.size()));
//                    toserver = new ArrayList<Inzerat>();
//                }
//            }
//            pockajNaMysqlDatabazu();
//            mysql.inzertInzeraty(toserver);
//            pockajNaMysqlDatabazu();
//            idckaPortal = mysql.getInzeratyIDs(aktualnyPortal);
//            pockajNaDatabazu();
//            if (idckaPortal.size() == database.getRemoteCountPortal(aktualnyPortal)) {
//                vsetkyDosli = true;
//            }
//        }
//        System.out.println("vsetky inzeraty dosli");
    }

    private void nacitajStromMien() {
        System.out.println("nacitavam strom mien");
        koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
        int pocetUnikatnych = 0;
        List<Integer> toDelete = new ArrayList<Integer>();
        pockajNaDatabazu();
        //List<Inzerat> noveInzeraty = database.getRemoteInzeratyList(aktualnyPortal);
        List<Inzerat> noveInzeraty = database.getRemoteInzeratyListLinky(aktualnyPortal);

        long startAnalyzis = System.currentTimeMillis();
        for (int i = 0; i < noveInzeraty.size(); i++) {
            Inzerat inzerat = noveInzeraty.get(i);
            if (!jeInzeratVStrome(koren, inzerat.getAktualny_link())) {
                pocetUnikatnych++;
            } else {
                toDelete.add(inzerat.getId());
            }
        }
        // premazeme firemne linky
        Node koren2 = new Node("");
        pockajNaDatabazu();
        List<String> firemne = database.getFiremneLinky();
        List<String> unikatneFiremne = new ArrayList<String>();
        for (int i = 0; i < firemne.size(); i++) {
            // nahadzeme do stromu navstivenych linkov aj firemne
            String inzerat = firemne.get(i);
            if (!jeInzeratVStrome(koren2, inzerat)) {
                unikatneFiremne.add(inzerat);
            }
        }
        // vlozime unikatneFiremne do db
        pockajNaDatabazu();
        database.deleteAndInsertFiremneLinky(unikatneFiremne);
        // nahadzeme firemne do stromu inzeratov
        for (int i = 0; i < unikatneFiremne.size(); i++) {
            String inzerat = unikatneFiremne.get(i);
            jeInzeratVStrome(koren, inzerat);
        }
        System.out.println("do stromu sme pridali aj  firemnych linkov: " + unikatneFiremne.size());
        System.out.println("analyzis time: " + (System.currentTimeMillis() - startAnalyzis));
        System.out.println("pocet unikatnych inzeratov: " + pocetUnikatnych);
        System.out.println("to delete size: " + toDelete.size());
        pockajNaDatabazu();
        database.deleteRemoteDuplikatneInzeraty(toDelete);
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

    private int getAktualnyDen() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd");
        String den = sdf.format(new Date(System.currentTimeMillis()));
        return Integer.parseInt(den);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    private void hladajLinkyInzeratovDoStromu() {
        int pocetInzeratov = 0;

        try {
            long startTime = System.currentTimeMillis();
            // prehladame podla kategoriach
            settings = getLinkySettings();
            for (Setting s : settings) {
                Document doc = jcrawler.getPage(s.link);
                int pocetStranokMusimNavstivit = 0;
                int najdenychInzeratov = 0;
                try {
                    String najdenych = doc.select("html body div#main div#right div.pagination.ostatne div").get(0).text();
                    if (najdenych.contains("Nájdených:")) {
                        najdenych = najdenych.replace("Nájdených:", "").trim();
                        najdenychInzeratov = Integer.parseInt(najdenych.split(" ")[0].trim());
                    }

                    if (najdenychInzeratov > 15) {
                        pocetInzeratov += najdenychInzeratov;
                        // hladame stranky
                        String stranok = doc.select("html body div#main div#right div.pagination.ostatne div p.list strong.page_info").text();
                        stranok = stranok.replace("Strana", "");
                        stranok = stranok.replace(":", "").trim();
                        pocetStranokMusimNavstivit = Integer.parseInt(stranok.split("z")[1].trim());
                    } else {
                        //System.out.println("link: " + s.link+"  inzeratov: " + najdenychInzeratov);
                    }
                    if (najdenychInzeratov > 0) {
                        // teraz mame linky na ktorych sa nachadzaju inzeraty s poctom stran a stranok
                        s.inzeratov = najdenychInzeratov;
                        s.stran = pocetStranokMusimNavstivit;
                        if (pocetStranokMusimNavstivit > 400) {
                            ClientRealityInzeratyHladajLinky novy = new ClientRealityInzeratyHladajLinky(s, this, 1, 400);
                            clientiHladajLinky.add(novy);
                            novy = new ClientRealityInzeratyHladajLinky(s, this, 400, s.stran);
                            clientiHladajLinky.add(novy);
                        } else {
                            ClientRealityInzeratyHladajLinky novy = new ClientRealityInzeratyHladajLinky(s, this, 1, s.stran);
                            clientiHladajLinky.add(novy);
                        }

                        System.out.println("link: " + s.link + "  inzeratov: " + najdenychInzeratov + " pocet stranok: " + pocetStranokMusimNavstivit);
                    }
                } catch (Exception e) {
                    System.out.println("vynimka: " + e);
                    System.out.println("  pocet inzeratov: NEMA");
                    Logger.getLogger(ClientRealityInzeratyALL.class.getName()).log(Level.SEVERE, null, e);
                }

            }
            System.out.println("hladanie linkov ukoncene, pocet clientov: " + clientiHladajLinky.size());
            System.out.println("spustam prvych 5 clientov");
//            // teraz spustime prvych 10 clientov
            for (int i = 0; i < clientiHladajLinky.size(); i++) {
                spustiClienta(clientiHladajLinky.get(i));
                aktualnyKlient++;
                Thread.sleep(5000);
            }
            System.out.println("while cyklus skoncil, nasli sme " + (pocetInzeratov) + " inzeratov");
//            System.out.println("kontrola linkov: ");
//            for (int i=0; i<clienti.size(); i++){
//                System.out.println("CLIENT "+i);
//                System.out.println("link: "+clienti.get(i).setting.link+" inzeratov: "+clienti.get(i).setting.inzeratov+" stran: "+clienti.get(i).setting.stran);
//            }
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

//    public void propertyChange(PropertyChangeEvent evt) {
//        if ("najdeneInzeraty".equals(evt.getPropertyName())) {
//            pocetNajdenych++;
//            System.out.println("pocetUkoncenych clientov: " + pocetNajdenych);
//            if (pocetNajdenych == clienti.length) {
//                ulozNajdeneDoDatabazy();
//            }
//        }
//    }
//
//    private void ulozNajdeneDoDatabazy() {
//        System.out.println("vsetci clienti skoncili, mergujem inzeraty");
//        List<Inzerat> toInsert = new ArrayList<Inzerat>();
//        List<Inzerat> unikatne = new ArrayList<Inzerat>();
//        for (int i = 0; i < clienti.length; i++) {
//            toInsert.addAll(clienti[i].najdeneInzeraty);
//        }
//        koren = new Node("");
//        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
//        for (Inzerat inz : toInsert) {
//            if (!jeInzeratVStrome(koren, inz.getAktualny_link())) {
//                unikatne.add(inz);
//            }
//        }
//        pockajNaDatabazu();
//        database.inzertRemoteInzeraty(unikatne, aktualnyPortal);
//        System.out.println("do databazy vlozenych inzeratov: " + unikatne.size());
//        updateServerDB();
//        System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + " UKONCENIE DOWNLOAD_ALL trvanie: " + getElapsedTime(startTime));
//    }
    private void pockajNaDatabazu() {
        while (!database.mamDatabazu()) {
            try {
                System.out.println("cakam na Databazu");
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void pockajNaMysqlDatabazu() {
        while (!mysql.mamDatabazu()) {
            try {
                System.out.println("cakam na Mysql Databazu");
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
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

    private String getETAtime(long startTime, int pocetInzeratov, int vsetkych) {
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
        return (hodinStringE + ":" + minutStringE + ":" + sekundStringE);
    }

    private List<Setting> getLinkySettings() {
        List<Setting> settings = new ArrayList<Setting>();
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://byty.inzercia.sk/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://domy.inzercia.sk/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://objekty.inzercia.sk/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://pozemky.inzercia.sk/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://priestory.inzercia.sk/"));
        return settings;
    }

    private List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<Setting>();
        // byty
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://1-izbove-byty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._2_IZBOVY] + "", "http://2-izbove-byty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._3_IZBOVY] + "", "http://3-izbove-byty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._4_IZBOVY] + "", "http://4-izbove-byty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._5_IZBOVY] + "", "http://5-izbove-a-vacsie-byty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.GARZONKA] + "", "http://garzonky.inzercia.sk/predam/"));
        //domy
        //settings.add(new Setting(Typ.Predám + "", Kategoria.DOMY + "", "http://domy.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://bungalovy.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://drevostavby-zruby.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://chalupy-chaty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://rodinne-domy.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://unimobunky.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://vidiecke-domy.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://zahradne-chatky.inzercia.sk/predam/"));
        // pozemky
        //settings.add(new Setting(Typ.Predám + "", Kategoria.POZEMKY + "", "http://pozemky.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://hospodarsky-dvor.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://lesy.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://luky-pasienky.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://orna-poda.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://pozemky-priemyselnej-zony.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://rekreacne-pozemky.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://sady.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://stavebne-pozemky.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://vinice.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://zahradne-pozemky.inzercia.sk/predam/"));
        //objektyKategoria.values[
        //settings.add(new Setting(Typ.Predám + "", Kategoria.OSTATNE + "", "http://objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://administrativne-objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.GARAZE] + "", "http://garaze.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://hotely-penziony.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://komercne-objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://pamiatky.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://polnohospodarske-objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://polyfunkcne-objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://prevadzkove-objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://rekreacne-objekty.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://sklady.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://sportoviska.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://vyrobne-objekty.inzercia.sk/predam/"));
        // priestoryKategoria.values[
        //settings.add(new Setting(Typ.Predám + ""Kategoria.values[, Kategoria.OBCHODNE_PRIESTORY + "", "http://priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.KANCELARIE] + "", "http://kancelarske-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://obchodne-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://prevadzkove-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://rekreacne-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://restauracne-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://skladove-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://sportove-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://ubytovacie-priestory.inzercia.sk/predam/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://vyrobne-priestory.inzercia.sk/predam/"));

        // byty
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://1-izbove-byty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria._2_IZBOVY] + "", "http://2-izbove-byty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria._3_IZBOVY] + "", "http://3-izbove-byty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria._4_IZBOVY] + "", "http://4-izbove-byty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria._5_IZBOVY] + "", "http://5-izbove-a-vacsie-byty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.GARZONKA] + "", "http://garzonky.inzercia.sk/kupim/"));
        //domy
        //settings.add(new Setting(Typ.Kúpim + "", Kategoria.DOMY + "", "http://domy.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.DOMY] + "", "http://bungalovy.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://drevostavby-zruby.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://chalupy-chaty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.DOMY] + "", "http://rodinne-domy.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.DOMY] + "", "http://unimobunky.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.DOMY] + "", "http://vidiecke-domy.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://zahradne-chatky.inzercia.sk/kupim/"));
        // pozemky
        //settings.add(new Setting(Typ.Kúpim + "", Kategoria.POZEMKY + "", "http://pozemky.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://hospodarsky-dvor.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://lesy.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://luky-pasienky.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://orna-poda.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://pozemky-priemyselnej-zony.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://rekreacne-pozemky.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://sady.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://stavebne-pozemky.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://vinice.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://zahradne-pozemky.inzercia.sk/kupim/"));
        //objekty
        //settings.add(new Setting(Typ.Kúpim + "", Kategoria.OSTATNE + "", "http://objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://administrativne-objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.GARAZE] + "", "http://garaze.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://hotely-penziony.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://komercne-objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://pamiatky.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://polnohospodarske-objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://polyfunkcne-objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://prevadzkove-objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://rekreacne-objekty.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.SKLADY] + "", "http://sklady.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://sportoviska.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.SKLADY] + "", "http://vyrobne-objekty.inzercia.sk/kupim/"));
        // priestory
        // settings.add(new Setting(Typ.Kúpim + "", Kategoria.OBCHODNE_PRIESTORY + "", "http://priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.KANCELARIE] + "", "http://kancelarske-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://obchodne-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://prevadzkove-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://rekreacne-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://restauracne-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.SKLADY] + "", "http://skladove-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://sportove-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://ubytovacie-priestory.inzercia.sk/kupim/"));
        settings.add(new Setting(Typ.Kúpim + "", Kategoria.values[Kategoria.SKLADY] + "", "http://vyrobne-priestory.inzercia.sk/kupim/"));

        // byty
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._1_IZBOVY] + "", "http://1-izbove-byty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._2_IZBOVY] + "", "http://2-izbove-byty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._3_IZBOVY] + "", "http://3-izbove-byty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._4_IZBOVY] + "", "http://4-izbove-byty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria._5_IZBOVY] + "", "http://5-izbove-a-vacsie-byty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.GARZONKA] + "", "http://garzonky.inzercia.sk/prenajom/"));
        //domy
        //  settings.add(new Setting(Typ.Predám + "", Kategoria.DOMY + "", "http://domy.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://bungalovy.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://drevostavby-zruby.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://chalupy-chaty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://rodinne-domy.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://unimobunky.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.DOMY] + "", "http://vidiecke-domy.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.CHALUPY_CHATY] + "", "http://zahradne-chatky.inzercia.sk/prenajom/"));
        // pozemky
        // settings.add(new Setting(Typ.Predám + "", Kategoria.POZEMKY + "", "http://pozemky.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://hospodarsky-dvor.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://lesy.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://luky-pasienky.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://orna-poda.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://pozemky-priemyselnej-zony.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://rekreacne-pozemky.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://sady.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.POZEMKY] + "", "http://stavebne-pozemky.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://vinice.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.ZAHRADY] + "", "http://zahradne-pozemky.inzercia.sk/prenajom/"));
        //objekty
        //    settings.add(new Setting(Typ.Predám + "", Kategoria.OSTATNE + "", "http://objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://administrativne-objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.GARAZE] + "", "http://garaze.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://hotely-penziony.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://komercne-objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://pamiatky.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://polnohospodarske-objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://polyfunkcne-objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://prevadzkove-objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://rekreacne-objekty.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://sklady.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://sportoviska.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://vyrobne-objekty.inzercia.sk/prenajom/"));
        // priestory
        //     settings.add(new Setting(Typ.Predám + "", Kategoria.OBCHODNE_PRIESTORY + "", "http://priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.KANCELARIE] + "", "http://kancelarske-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://obchodne-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OBCHODNE_PRIESTORY] + "", "http://prevadzkove-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://rekreacne-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://restauracne-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://skladove-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.OSTATNE] + "", "http://sportove-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.HOTELY_RESTAURACIE] + "", "http://ubytovacie-priestory.inzercia.sk/prenajom/"));
        settings.add(new Setting(Typ.Predám + "", Kategoria.values[Kategoria.SKLADY] + "", "http://vyrobne-priestory.inzercia.sk/prenajom/"));

        return settings;
    }

    private void spustiClienta(ClientRealityInzeraty24 c) {
        System.out.println("spustam clienta: " + aktualnyKlient);
        c.addPropertyChangeListener(this);
        es.execute(c);
    }

    private void spustiClienta(ClientRealityInzeratyALL c) {
        System.out.println("spustam clienta: " + aktualnyKlient);
        c.addPropertyChangeListener(this);
        es.execute(c);
    }

    private void spustiClienta(ClientRealityInzeratyHladajLinky c) {
        System.out.println("spustam clienta: " + aktualnyKlient);
        c.addPropertyChangeListener(this);
        es.execute(c);
    }

    private void ulozNajdeneDoDatabazy() {
        System.out.println("vsetci clienti skoncili");
        // linky na firemne inzeraty su tiez ulozene v osobitnej tabulke
        // vymazeme duplicitne firemne linky TODO
        // netreba mazat, vsetko sa premaze ked dam vymazat stare

        // inzeraty su uz inzertnute, takze staci len updatnut
        //updateServerDB();
        System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + " UKONCENIE DOWNLOAD_ALL trvanie: " + getElapsedTime(startTime));
    }

    private void vymazNeaktualneInzeraty() {
        System.out.println("vsetci clienti skoncili, mergujem linky");
        List<String> toInsert = new ArrayList<String>();

        for (int i = 0; i < clientiHladajLinky.size(); i++) {
            toInsert.addAll(clientiHladajLinky.get(i).najdeneLinky);
        }
        koren = new Node("");
        int pocetUnikatnychLinkov = 0;
        int pocetDuplicitnych = 0;
        // pridame do stromu vsetky najdene linky
        for (String inz : toInsert) {
            if (!jeInzeratVStrome(koren, inz)) {
                // uz je link v strome
                pocetUnikatnychLinkov++;
            } else {
                pocetDuplicitnych++;
            }
        }
        // 2. prejdeme celu databazu a pytame sa ci je inzerat este aktualny, ak NIE, tak si pridame do zoznamu na zmazanie
        System.out.println("nacitavam inzeraty z lokal DB");
        pockajNaDatabazu();
        List<Inzerat> inzeraty = database.getRemoteInzeratyList(aktualnyPortal);
        System.out.println("hladam inzeraty na vymazanie");
        List<Integer> toDeleteIDs = new ArrayList<Integer>();
        for (Inzerat inz : inzeraty) {
            // v strome su platne linky z portalu
            if (!jeInzeratVStrome(koren, inz.getAktualny_link())) {
                toDeleteIDs.add(inz.getId());
            }
        }
        // teraz vymazeme firemne linky
        pockajNaDatabazu();
        List<String> firemne = database.getFiremneLinky();
        List<String> toDeleteFiremne = new ArrayList<String>();
        for (String s : firemne) {
            if (!jeInzeratVStrome(koren, s)) {
                toDeleteFiremne.add(s);
            }
        }
        System.out.println("pocet firemnych na vymazanie: " + toDeleteFiremne.size());
        pockajNaDatabazu();
        database.deleteFiremne(toDeleteFiremne);
        //BUG: PREDPOKLADAM ZE ZMAZANIE SA PODARI NA PRVY KRAT, 
        System.out.println("pocet unikatnych linkov: " + pocetUnikatnychLinkov + " duplicitnych: " + pocetDuplicitnych);
        System.out.println("to delete inzeraty size: " + toDeleteIDs.size());
        pockajNaDatabazu();
        database.deleteRemoteDuplikatneInzeraty(toDeleteIDs);
        System.out.println("lokal DB non-existent inzeraty ZMAZANE");
        System.out.println("mysql DB sa premaze pri update");
//        pockajNaMysqlDatabazu();
//        mysql.deleteInzeratyWhereID(toDeleteIDs);
//        System.out.println("remote DB non-existent inzeraty ZMAZANE");
        System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ": " + aktualnyPortal + "  UKONCENIE DELETE_STARE_MOD trvanie: " + getElapsedTime(startTime));
    }

    /**
     * v tejto metode sa prechdazju iba 5 hlavnych kategorii
     */
    private void hladajNoveInzeraty_24() {
        List<Inzerat> toInsert = new ArrayList<Inzerat>();;// nove inzeraty
        int pocetInzeratov = 0;
//        nacitajStromMien();
//        nacitajStromFiremnychLinkov();

        try {
            long startTime = System.currentTimeMillis();
            // prehladame podla kategoriach
            settings = getLinkySettings();
            for (Setting s : settings) {
                Document doc = jcrawler.getPage(s.link);
                int pocetStranokMusimNavstivit = 0;
                int najdenychInzeratov = 0;
                try {
                    String najdenych = doc.select("html body div#main div#right div.pagination.ostatne div").get(0).text();
                    if (najdenych.contains("Nájdených:")) {
                        najdenych = najdenych.replace("Nájdených:", "").trim();
                        najdenychInzeratov = Integer.parseInt(najdenych.split(" ")[0].trim());
                    }
                    pocetInzeratov += najdenychInzeratov;
                    if (najdenychInzeratov > 15) {
                        // hladame stranky
                        String stranok = doc.select("html body div#main div#right div.pagination.ostatne div p.list strong.page_info").text();
                        stranok = stranok.replace("Strana", "");
                        stranok = stranok.replace(":", "").trim();
                        pocetStranokMusimNavstivit = Integer.parseInt(stranok.split("z")[1].trim());
                    } else {
                        //System.out.println("link: " + s.link+"  inzeratov: " + najdenychInzeratov);
                    }
                    if (najdenychInzeratov > 0) {
                        // teraz mame linky na ktorych sa nachadzaju inzeraty s poctom stran a stranok
                        s.inzeratov = najdenychInzeratov;
                        s.stran = pocetStranokMusimNavstivit;
                        ClientRealityInzeraty24 novy = new ClientRealityInzeraty24(database, s, this);
                        clienti24.add(novy);
                        System.out.println("link: " + s.link + "  inzeratov: " + najdenychInzeratov + " pocet stranok: " + pocetStranokMusimNavstivit);
                    }
                } catch (Exception e) {
                    System.out.println("vynimka: " + e);
                    System.out.println("  pocet inzeratov: NEMA");
                    Logger.getLogger(ClientRealityInzeratyALL.class.getName()).log(Level.SEVERE, null, e);
                }

            }
            System.out.println("hladanie linkov ukoncene, pocet clientov: " + clientiAll.size());
            System.out.println("while cyklus skoncil, nasli sme " + (pocetInzeratov) + " inzeratov");
            System.out.println("spustam 5 clientov");
            for (int i = 0; i < 5; i++) {
                spustiClienta(clienti24.get(i));
                aktualnyKlient++;
                Thread.sleep(10000);
            }

        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void ulozNajdeneDoDatabazy24() {
        System.out.println("vsetci clienti skoncili");
        // linky na firemne inzeraty su tiez ulozene v osobitnej tabulke
        // vymazeme duplicitne firemne linky TODO
        // netreba mazat, vsetko sa premaze ked dam vymazat stare

        // inzeraty su uz inzertnute, takze staci len updatnut
        updateServerDB();
        String hlaska = sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE DOWNLOAD_24 trvanie: " + getElapsedTime(startTime);
        System.out.println(hlaska);
        changes.firePropertyChange("realityInzeraty24ended", "", hlaska);
    }

}

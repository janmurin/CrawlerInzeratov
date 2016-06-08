/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawleri;

import clienti.ClientBazosHladajInzeraty;
import clienti.ClientBazosHladajLinky;
import clienti.Node;
import deleted.SilentHtmlUnitDriver;
import home.crawlerinzeratov.Database;
import home.crawlerinzeratov.Inzerat;
import home.crawlerinzeratov.Kategoria;
import home.crawlerinzeratov.MySQLDatabase;
import home.crawlerinzeratov.Typ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ArealityCrawler implements Runnable, PropertyChangeListener {

    private int pocetUkoncenychClientov = 0;
    private ClientBazosHladajLinky[] clientiHladajLinky;
    private ClientBazosHladajInzeraty[] clientiHladajInzeraty;
    private Node koren;
    private Database database;
    ExecutorService es = Executors.newCachedThreadPool();
    private UrychlovacInsertInzeraty urychlovacInsert;
    public static final String AREALITY_LINK = "http://areality.sk";
    public static final int DELETE_STARE_MOD = 0;
    public static final int DOWNLOAD_24 = 1;
    public static final int DOWNLOAD_ALL = 2;
    public static final int UPDATE_ALL = 3;
    private long startTime;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private String aktualnyPortal;
    private int zvoleny_mod;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private MySQLDatabase mysql;
    private final List<Okres> okresy;
    private final List<String> okresneMesta;

    /**
     * konstruktor pre mazanie neaktualnych inzeratov
     */
    public ArealityCrawler(int mode, Database db, MySQLDatabase msd) {
        database = db;
        mysql = msd;
        aktualnyPortal = AREALITY_LINK;
        zvoleny_mod = mode;
        urychlovacInsert = new UrychlovacInsertInzeraty(database, null);
                pockajNaDatabazu();
        okresy = db.getOkresy();
        pockajNaDatabazu();
        okresneMesta = db.getOkresneMesta();
    }

    public void run() {

        try {
            startTime = System.currentTimeMillis();
            if (zvoleny_mod == DELETE_STARE_MOD) {
                //IBA SA VYMAZU TIE KTORE SU NEAKTUALNE V DATABAZE, NIC SA NEPRIDAVA DO DATABAZY
                int pocetVsetkychBazosInzeratov = getPocetVsetkychInzeratov(aktualnyPortal);
                System.out.println(sdf.format(new Date(startTime)) + ": " + aktualnyPortal + "  SPUSTENIE DELETE_STARE_MOD");
                
                int pocetClientov = pocetVsetkychBazosInzeratov / 10000 + 1; // kazdy klient prejde 10000 inzeratov max
                clientiHladajLinky = new ClientBazosHladajLinky[pocetClientov];
                pocetUkoncenychClientov = 0;
                System.out.println("inzeratov: " + pocetVsetkychBazosInzeratov);
                // postupne spustime jednotlivych clientov
                for (int i = 0; i < clientiHladajLinky.length; i++) {
                    clientiHladajLinky[i] = new ClientBazosHladajLinky(i * 10000 + 1, (i + 1) * 10000);
                    clientiHladajLinky[i].addPropertyChangeListener(this);
                    es.execute(clientiHladajLinky[i]);
                    System.out.println("spusteny ClientBazosHladajLinky " + i);
                    try {
                        System.out.println("cakam na spustenie dalsieho clienta");
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            if (zvoleny_mod == DOWNLOAD_24) {
                // toto nebudem paralelizovat
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_24");
                changes.firePropertyChange("logHlaska", "", sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_24");
                //maxHladanychInzeratov = getDnesnychInzeratov(aktualnyPortal);
                nacitajStromMien();
                hladajNoveInzeraty_Bazos24(aktualnyPortal, 0, getDnesnychInzeratov(aktualnyPortal));
                aktualizujDB();
                String hlaska = sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE DOWNLOAD_24 trvanie: " + getElapsedTime(startTime);
                System.out.println(hlaska);
                changes.firePropertyChange("bazos24ended", "", hlaska);
            }
            if (zvoleny_mod == DOWNLOAD_ALL) {
                System.out.println("DOWNLOAD ALL BAZOS: TODO");
//            /*
//             POUZIVAT IBA NA MASOVE STAHOVANIE INZERATOV, NAJPRV VSETKO SUROVO STIAHNUT A 
//             POTOM PREJST DATABAZU A ODSTRANIT DUPLIKATY, problem je prilis velky strom
//             */
//            //int pocetVsetkychBazosInzeratov = getPocetVsetkychInzeratov(aktualnyPortal);
//            int pocetVsetkychBazosInzeratov = 75000;
//            System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_ALL");
//
//            int davka = 10000;
//            int pocetClientov = pocetVsetkychBazosInzeratov / davka + 1; // kazdy klient prejde 10000 inzeratov max
//            clientiHladajInzeraty = new ClientBazosHladajInzeraty[1];
//            pocetUkoncenychClientov = 0;
//            System.out.println("inzeratov: " + pocetVsetkychBazosInzeratov);
//            // postupne spustime jednotlivych clientov
//            for (int i = 0; i < clientiHladajInzeraty.length; i++) {
//                clientiHladajInzeraty[i] = new ClientBazosHladajInzeraty(i * davka + 1, (i + 1) * davka, database);
//                clientiHladajInzeraty[i].addPropertyChangeListener(this);
//                es.execute(clientiHladajInzeraty[i]);
//                System.out.println("spusteny ClientBazosHladajInzeraty " + i);
//                try {
//                    System.out.println("cakam na spustenie dalsieho clienta");
//                    Thread.sleep(5000);
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }
            }
            if (zvoleny_mod == UPDATE_ALL) {
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE UPDATE_ALL");
                aktualizujDB();
                System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE UPDATE_ALL trvanie: " + getElapsedTime(startTime));
            }
        } catch (Exception e) {
             Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
             changes.firePropertyChange("VYNIMKA", false, true);
             changes.firePropertyChange("logHlaska", "", sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  VYNIMKA: "+e.toString());
        }
    }

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
        //System.out.println("ETA:" + (hodinStringE + ":" + minutStringE + ":" + sekundStringE));
        return (hodinStringE + ":" + minutStringE + ":" + sekundStringE);
//            System.out.println("etaTime: "+etaTime);
//            System.out.println("rychlost: "+rychlost);
    }

//    public static void main(String[] args) {
//        BazosCrawler bc = new BazosCrawler();
//        bc.execute();
//        System.out.println("som v maine za execute metodou");
//    }
    private void hladajNoveInzeraty_Bazos24(String currentLink, int min, int max) {
        // TUTO METODU POUZIVAT IBA NA DOWNLOAD 24- chyba je v riadiacej premennej while cyklu
        List<Inzerat> toInsert = new ArrayList<Inzerat>();;// nove inzeraty
        try {
            WebDriver driver = new SilentHtmlUnitDriver();
            long startTime = System.currentTimeMillis();
            long sucetLatencii = 0;
            long pocetLatencii = 0;
            long pomLatencia = 0;
            pomLatencia = System.currentTimeMillis();
            driver.get(currentLink);
            sucetLatencii += System.currentTimeMillis() - pomLatencia;
            pocetLatencii++;
            int cislo = min;
            int pocetInzeratov = 0;

            //boolean foundYesterdayInzerat = false;
            // bezi bud dokym neprekona maximum hladanych
            while (cislo < max) {
                long startTime15 = System.currentTimeMillis();
                List<WebElement> nadpisy = driver.findElements(By.cssSelector("span.nadpis"));
                Map<String, String> noveInzeraty = new HashMap<String, String>();
                if (nadpisy.size() == 0) {
                    System.out.print("size 0 ");
                }
                // zistujeme datum vlozenia inzeratu
//                String datum = driver.findElement(By.cssSelector("table.inzeraty span.velikost10")).getText();
//                datum = datum.substring(datum.indexOf("[") + 1, datum.indexOf("]"));
//                //  System.out.println("datum to parse: "+datum);
//                //System.out.println("den to parse: "+datum.split("\\.")[0]);
//                den = Integer.parseInt(datum.split("\\.")[0].trim());
////                    mesiac=Integer.parseInt(datum.split(".")[1]);
////                    rok=Integer.parseInt(datum.split(".")[2].trim());
//                if (den != aktualnyDen) {
//                    System.out.println("den: " + den + " aktualnyden: " + aktualnyDen + " UKONCENE PREHLADAVANIE INZERATOV");
//                    foundYesterdayInzerat = true;
//                    //break;
//                }

                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
                for (int j = 0; j < nadpisy.size(); j++) {
                    WebElement we = nadpisy.get(j);
                    // vyberieme si nadpis a link
                    String nazov = we.getText();
                    String link = we.findElement(By.cssSelector("a")).getAttribute("href");
                    if (!jeInzeratVStrome(koren, link)) {
                        noveInzeraty.put(nazov, link);
                    }
                }

                // pozrieme sa na nove inzeraty a pridame ich do zoznamu na insert do DB
                for (String nazov : noveInzeraty.keySet()) {
                    String link = noveInzeraty.get(nazov);
                    pomLatencia = System.currentTimeMillis();
                    driver.get(link);
                    sucetLatencii += System.currentTimeMillis() - pomLatencia;
                    pocetLatencii++;
                    // nacitame udaje o inzerate
                    WebElement text = null;
                    try {
                        text = driver.findElement(By.cssSelector("td[colspan=\"4\"]"));
                    } catch (Exception e) {
                        Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
                        System.out.println("ODPOVED ");
                        System.out.println(driver.getCurrentUrl());
                        // continue;
                    }
                    String textValue = text.getText();

                    List<WebElement> meno = driver.findElements(By.cssSelector("td[colspan=\"2\"]"));
                    String menoValue = meno.get(0).getText();
                    String menoInzerenta = menoValue.split("\n")[0];
                    String telefonInzerenta = null;
                    try {
                        telefonInzerenta = menoValue.split("\n")[1];
                    } catch (Exception e) {
                        telefonInzerenta = "0";
                    }
                    //System.out.println("MENO:\n" + menoInzerenta + " " +telefonInzerenta );

                    WebElement lokalita = driver.findElement(By.cssSelector("a[title=\"Približná lokalita\"]"));
                    String lokalitaValue = lokalita.getText();
                    //System.out.println("LOKALITA:\n" + lokalitaValue);

                    String cenaValue = meno.get(1).getText();
                    try {
                        cenaValue = cenaValue.substring(2, cenaValue.indexOf("(")).replaceAll(" ", "");
                    } catch (java.lang.StringIndexOutOfBoundsException e) {
                        cenaValue = "0";
                    }//div.barvalevat a#zvyraznenikat
                    String typ = null;
                    try {
                        typ = driver.findElement(By.cssSelector("div.barvalevat a#zvyraznenikat")).getText();

                    } catch (Exception e) {
                        typ = Typ.Predám + "";
                        System.err.println("CHYBA: nenaslo typ, nastavujem typ: Predam" + e.toString());
                    }
                    String kategoria = null;
                    try {
                        kategoria = driver.findElement(By.cssSelector("div.barvaleva a#zvyraznenikat")).getText();
                    } catch (Exception e) {
                        kategoria = Kategoria.values[Kategoria.OSTATNE];
                        System.err.println("CHYBA: nenaslo kategoria, nastavujem kategoria: Ostatne" + e.toString());
                    }
                    kategoria = getKategoria(kategoria.trim());
                    typ = getTyp(typ.trim());

                    //System.out.println("CENA:\n" + cenaValue);
                    Inzerat novy = new Inzerat();
                    novy.setAktualny_link(link.trim());
                    //System.out.println("link: " + link);
                    novy.setCena(cenaValue);
                    novy.setLokalita(getOkresneMesto(lokalitaValue.replaceAll("'", "").substring(6).trim()));
                    novy.setMeno(menoInzerenta.replaceAll("'", ""));
                    novy.setNazov(nazov.replaceAll("'", ""));
                    novy.setPortal(aktualnyPortal);
                    novy.setTelefon(telefonInzerenta);
                    novy.setText(textValue.replaceAll("'", ""));
                    novy.setKategoria(kategoria);
                    novy.setTyp(typ);
                    toInsert.add(novy);
                    pocetInzeratov++;
                    //driver.get(currentLink);
                }
                cislo += 15;

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (cislo - min) + "/" + (max - min) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, cislo - min, max - min) + " najdenych: " + pocetInzeratov + " priemerna latencia: " + (sucetLatencii / pocetLatencii));
                currentLink = "http://reality.bazos.sk/" + cislo + "/";
                pomLatencia = System.currentTimeMillis();
                driver.get(currentLink);
                sucetLatencii += System.currentTimeMillis() - pomLatencia;
                pocetLatencii++;
                if (toInsert.size() > 500) {
                    if (database.mamDatabazu()) {
                        System.out.println("mam databazu, insertujem inzeratov " + toInsert.size());
                        urychlovacInsert.inzeraty = toInsert;
                        es.execute(urychlovacInsert);
                        toInsert = new ArrayList<Inzerat>();
                    } else {
                        System.out.println("nemam databazu");
                    }
                }
            }
            pockajNaDatabazu();
            urychlovacInsert.inzeraty = toInsert;
            es.execute(urychlovacInsert);
            urychlovacInsert = null;
            System.out.println("while cyklus skoncil, presli sme " + (max - min) + " inzeratov" + " najdenych: " + pocetInzeratov + " priemerna latencia: " + (sucetLatencii / pocetLatencii));
            //Close the browser
            driver.get(currentLink);
            driver.quit();
            //updateServerDB();
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            pockajNaDatabazu();
            urychlovacInsert.inzeraty = toInsert;
            es.execute(urychlovacInsert);
            // ak bude vynimka tak skoncime
            // hladajNoveInzeraty_Bazos(currentLink, cislo);
        }
    }

//    private void deleteNonExistentInzeratyFromRemoteDB() {
//        // raz za den sa prejde cely portal a nahadzu sa do stromu vsetky inzeraty co tam su
//        // potom sa prechadza cela databaza a zaznamenava sa ake idcka inzeratov su este stale na portali
//        // potom zmazem vsetky inzeraty, ktorych idcka nie su v zozname
//
//        // 1. prejst cely portal a zapametat si ake inzeraty tam su
//        System.out.println("vytvaram novy strom a hadzem do neho linky aktualnych inzeratov z bazosu");
//        koren = new Node("");
//        hladajLinkyInzeratov();
//        System.out.println("mame nacitane linky na vsetky inzeraty z " + aktualnyPortal);
//// 2. prejdeme celu databazu a pytame sa ci je inzerat este aktualny, ak NIE, tak si pridame do zoznamu na zmazanie
//        System.out.println("nacitavam inzeraty z lokal DB");
//        pockajNaDatabazu();
//        List<Inzerat> inzeraty = database.getInzeratyList(aktualnyPortal);
//        System.out.println("hladam inzeraty na vymazanie");
//        List<Integer> toDeleteIDs = new ArrayList<Integer>();
//        for (Inzerat inz : inzeraty) {
//            if (!jeInzeratVStrome(koren, inz.getAktualny_link())) {
//                toDeleteIDs.add(inz.getId());
//            }
//        }
//        //BUG: PREDPOKLADAM ZE ZMAZANIE SA PODARI NA PRVY KRAT, 
//        System.out.println("to delete inzeraty size: " + toDeleteIDs.size());
//        pockajNaDatabazu();
//        database.deleteInzeratyWithID(toDeleteIDs);
//        System.out.println("lokal DB toDeleteIDs su ZMAZANE");
//        pockajNaMysqlDatabazu();
//        mysql.deleteInzeratyWhereID(toDeleteIDs);
//        System.out.println("remote DB toDeleteIDs su ZMAZANE");
//
//    }
    private void nacitajStromMien() {
        System.out.println("nacitavam strom mien");
        koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
        int pocetUnikatnych = 0;
        List<Integer> toDelete = new ArrayList<Integer>();
        pockajNaDatabazu();
        List<Inzerat> noveInzeraty = database.getInzeratyListLinky(aktualnyPortal);
        //List<Inzerat> noveInzeraty = database.getInzeratyList(aktualnyPortal);

        long startAnalyzis = System.currentTimeMillis();
        for (int i = 0; i < noveInzeraty.size(); i++) {
            Inzerat inzerat = noveInzeraty.get(i);
            if (!jeInzeratVStrome(koren, inzerat.getAktualny_link())) {
                pocetUnikatnych++;
            } else {
                toDelete.add(inzerat.getId());
            }
        }
        System.out.println("analyzis time: " + (System.currentTimeMillis() - startAnalyzis));
        System.out.println("pocet unikatnych inzeratov: " + pocetUnikatnych);
        System.out.println("to delete size: " + toDelete.size());
        pockajNaDatabazu();
        database.deleteInzeratyWithID(toDelete);
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

    private int getPocetVsetkychInzeratov(String BAZOS_LINK) {
        WebDriver driver = new SilentHtmlUnitDriver();

        driver.get(BAZOS_LINK);
        String pocet = "0";
        String source = driver.findElement(By.cssSelector("div.sirka")).getText();
        // System.out.println(source);
        String searchPhrase = "Inzeráty realit celkom:";
        String searchPhrase2 = "za 24 hodín:";
        for (int i = 0; i < source.length(); i++) {
            if (source.substring(i).startsWith(searchPhrase)) {
                String odsek = source.substring(i + searchPhrase.length());
                //System.out.println(odsek);
                pocet = odsek.substring(0, odsek.indexOf(","));
                String odsek2 = odsek.substring(odsek.indexOf(",") + searchPhrase2.length() + 2).trim();
                //dnesnychInzeratov = Integer.parseInt(odsek2);
                //System.out.println("dnesnych inzeratov: " + dnesnychInzeratov);
                // System.out.println(pocet);
                driver.quit();
                return Integer.parseInt(pocet.trim());
            }
        }

        //System.out.println(pocet);
        driver.quit();
        return 10000;
    }

    private int getDnesnychInzeratov(String aktualnyPortal) {
        WebDriver driver = new SilentHtmlUnitDriver();

        driver.get(aktualnyPortal);
        String pocet = "0";
        String source = driver.findElement(By.cssSelector("div.sirka")).getText();
        // System.out.println(source);
        String searchPhrase = "Inzeráty realit celkom:";
        String searchPhrase2 = "za 24 hodín:";
        for (int i = 0; i < source.length(); i++) {
            if (source.substring(i).startsWith(searchPhrase)) {
                String odsek = source.substring(i + searchPhrase.length());
                //System.out.println(odsek);
                pocet = odsek.substring(0, odsek.indexOf(","));
                String odsek2 = odsek.substring(odsek.indexOf(",") + searchPhrase2.length() + 2).trim();
                int dnesnychInzeratov = Integer.parseInt(odsek2);
                System.out.println("dnesnych inzeratov: " + dnesnychInzeratov);
                // System.out.println(pocet);
                driver.quit();
                return dnesnychInzeratov;
            }
        }

        //System.out.println(pocet);
        driver.quit();
        return 10000;
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

//    private void hladajLinkyInzeratov() {
//        try {
//            WebDriver driver = new HtmlUnitDriver();
//            driver.get(aktualnyPortal);
//
//            int cislo = 0;
//            int pocetInzeratov = 0;
//            int vsetkychInzeratov = getPocetVsetkychInzeratov(aktualnyPortal);
//            long startTime = System.currentTimeMillis();
//            String currentLink = aktualnyPortal;
//            int noveLinky = 0;
//
//            // bezi bud dokym neprejde vsetky inzeraty alebo kym nenajde inzerat z minuleho dna
//            while (cislo < vsetkychInzeratov) {
//                long startTime15 = System.currentTimeMillis();
//                List<WebElement> nadpisy = driver.findElements(By.cssSelector("span.nadpis"));
//                if (nadpisy.size() == 0) {
//                    System.out.print("size 0 ");
//                }
//
//                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
//                for (int j = 0; j < nadpisy.size(); j++) {
//                    WebElement we = nadpisy.get(j);
//                    // vyberieme si nadpis a link
//                    String nazov = we.getText();
//                    String link = we.findElement(By.cssSelector("a")).getAttribute("href");
//                    if (!jeInzeratVStrome(koren, link)) {
//                        // vlozili sme jedinecny link na inzerat do stromu
//                        noveLinky++;
//                    }
//                    pocetInzeratov++;
//                }
//
//                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
//                System.out.print(" " + (cislo) + "/" + (vsetkychInzeratov) + "   ");
//                System.out.println(getETAtime(startTime, pocetInzeratov, vsetkychInzeratov));
//                cislo += 15;
//                currentLink = "http://reality.bazos.sk/" + cislo + "/";
//                driver.get(currentLink);
//            }
//            System.out.println("while cyklus skoncil");
//            System.out.println("naslo sa na bazosi " + noveLinky + " novych linkov na inzeraty");
//            //Close the browser
//            driver.get(currentLink);
//            driver.quit();
//        } catch (Exception exception) {
//            System.out.println("VYNIMKA: " + exception);
//            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
//            System.out.println("DELETE_STARE_MOD NEUSPESNE");
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException ex) {
//                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
//            }
//        }
//    }
    private String getKategoria(String kategoria) {
        if (kategoria.equals("Garzónka")) {
            return Kategoria.values[Kategoria.GARZONKA];
        }
        if (kategoria.equals("1 izbový byt")) {
            return Kategoria.values[Kategoria._1_IZBOVY];
        }
        if (kategoria.equals("2 izbový byt")) {
            return Kategoria.values[Kategoria._2_IZBOVY];
        }
        if (kategoria.equals("3 izbový byt")) {
            return Kategoria.values[Kategoria._3_IZBOVY];
        }
        if (kategoria.equals("4 izbový byt")) {
            return Kategoria.values[Kategoria._4_IZBOVY];
        }
        if (kategoria.equals("5 izbový byt a väèší")) {
            return Kategoria.values[Kategoria._5_IZBOVY];
        }
        if (kategoria.equals("Nové projekty")) {
            return Kategoria.values[Kategoria.NOVE_PROJEKTY];
        }
        if (kategoria.equals("Domy")) {
            return Kategoria.values[Kategoria.DOMY];
        }
        if (kategoria.equals("Garáže")) {
            return Kategoria.values[Kategoria.GARAZE];
        }
        if (kategoria.equals("Hotely, reštaurácie")) {
            return Kategoria.values[Kategoria.HOTELY_RESTAURACIE];
        }
        if (kategoria.equals("Chalupy, Chaty")) {
            return Kategoria.values[Kategoria.CHALUPY_CHATY];
        }
        if (kategoria.equals("Kancelárie")) {
            return Kategoria.values[Kategoria.KANCELARIE];
        }
        if (kategoria.equals("Obchodné priestory")) {
            return Kategoria.values[Kategoria.OBCHODNE_PRIESTORY];
        }
        if (kategoria.equals("Pozemky")) {
            return Kategoria.values[Kategoria.POZEMKY];
        }
        if (kategoria.equals("Sklady")) {
            return Kategoria.values[Kategoria.SKLADY];
        }
        if (kategoria.equals("Záhrady")) {
            return Kategoria.values[Kategoria.ZAHRADY];
        }
        if (kategoria.equals("Ostatné")) {
            return Kategoria.values[Kategoria.OSTATNE];
        }
        if (kategoria.equals("Podnájom, spolubývajúci")) {
            return Kategoria.values[Kategoria.PODNAJOM_SPOLUBYVAJUCI];
        }
        if (kategoria.equals("Ubytovanie")) {
            return Kategoria.values[Kategoria.UBYTOVANIE];
        }
        System.err.println("nepodarilo sa odhalit kategoriu pre: " + kategoria);
        return Kategoria.values[Kategoria.OSTATNE];
    }

    private String getTyp(String typ) {
        if (typ.equals("Ponuka")) {
            return Typ.Predám + "";
        }
        if (typ.equals("Dopyt")) {
            return Typ.Kúpim + "";
        }
        System.err.println("nepodarilo sa odhalit typ pre: " + typ);
        return Typ.Predám + "";
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if ("bazosNajdeneLinky".equals(evt.getPropertyName())) {
            pocetUkoncenychClientov++;
            System.out.println("pocetUkoncenych clientov: " + pocetUkoncenychClientov);
            if (pocetUkoncenychClientov == clientiHladajLinky.length) {
                vymazNeaktualneInzeraty();
            }
        }
        if ("bazosNajdeneInzeraty".equals(evt.getPropertyName())) {
            pocetUkoncenychClientov++;
            System.out.println("pocetUkoncenych clientov: " + pocetUkoncenychClientov);
            if (pocetUkoncenychClientov == clientiHladajInzeraty.length) {
                ulozNajdeneInzeraty();
            }
        }
    }

    private void vymazNeaktualneInzeraty() {
        System.out.println("vsetci clienti skoncili, mergujem linky");
        List<String> toInsert = new ArrayList<String>();

        for (int i = 0; i < clientiHladajLinky.length; i++) {
            toInsert.addAll(clientiHladajLinky[i].najdeneLinky);
        }
        koren = new Node("");
        // pridame do stromu vsetky najdene linky
        for (String inz : toInsert) {
            if (!jeInzeratVStrome(koren, inz)) {
                // uz je link v strome
            }
        }
        // 2. prejdeme celu databazu a pytame sa ci je inzerat este aktualny, ak NIE, tak si pridame do zoznamu na zmazanie
        System.out.println("nacitavam inzeraty z lokal DB");
        pockajNaDatabazu();
        List<Inzerat> inzeraty = database.getInzeratyListLinky(aktualnyPortal);
        System.out.println("hladam inzeraty na vymazanie");
        List<Integer> toDeleteIDs = new ArrayList<Integer>();
        for (Inzerat inz : inzeraty) {
            // v strome su platne linky z portalu
            if (!jeInzeratVStrome(koren, inz.getAktualny_link())) {
                toDeleteIDs.add(inz.getId());
            }
        }
        //BUG: PREDPOKLADAM ZE ZMAZANIE SA PODARI NA PRVY KRAT, 
        System.out.println("to delete inzeraty size: " + toDeleteIDs.size());
        pockajNaDatabazu();
        database.deleteInzeratyWithID(toDeleteIDs);
        System.out.println("lokal DB non-existent inzeraty ZMAZANE");
        // teraz zmazat z remote_inzeratov POZOR: bazos inzeraty tu uz maju ine idcka ako v tabulke inzeraty
        // selectneme si sukromne inzeraty
        pockajNaDatabazu();
        List<Inzerat> sukromne = database.getSukromneInzeratyFrom(aktualnyPortal);
        // deletneme z remote_inzeraty tie ktore nie su v nasom obnovenom zozname sukromnych
        pockajNaDatabazu();
        List<Integer> toDeleteSukromneIDs = database.deleteRemoteInzeratyNotIn(sukromne, aktualnyPortal);

        System.out.println("deletnute neplatne inzeraty z remote_inzeraty, mysql DB sa maze pri update/synchronizacii");
//        pockajNaMysqlDatabazu();
//        mysql.deleteInzeratyWhereID(toDeleteSukromneIDs);
//        System.out.println("remote DB non-existent inzeraty ZMAZANE");
        System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ": " + aktualnyPortal + "  UKONCENIE DELETE_STARE_MOD trvanie: " + getElapsedTime(startTime));
    }

    private void ulozNajdeneInzeraty() {
        // TATO METODA SA VOLA IBA KED SKONCI HROMADNY DOWNLOAD ALL, UPDATE NA SERVER RIESI INA METODA
        System.out.println("vsetci clienti skoncili, odstranujem duplikaty");
        // deletneme potencionalne duplikatne inzeraty
        nacitajStromMien();

        // vytiahneme si vsetky sukromne inzeraty
        pockajNaDatabazu();
        List<Inzerat> sukromne = database.getSukromneInzeratyFrom(aktualnyPortal);
        System.out.println("inzertujem nove sukromne");
        pockajNaDatabazu();
        // zisti ktore z tychto sukromnych su nove a insertni ich do db
        database.insertRemoteNoveSukromne(sukromne, aktualnyPortal);

        System.out.println("vsetky sukromne inzeraty inzertnute");
        System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE DOWNLOAD_ALL trvanie: " + getElapsedTime(startTime));
    }

    private void aktualizujDB() {
        // TATO METODA SA VOLA IBA KED SA CHCE PRIAMO UPDATOVAT ALEBO KED SKONCI DOWNLOAD 24
        // EXPLICITNE JU TREBA ZAVOLAT AK SKONCI DOWNLOAD ALL
        System.out.println("INIT update server DB");
        // PRED KAZDYM UPLOADOM SKONTROLOVAT DUPLICITU INZERATOV
        nacitajStromMien();
        System.out.println("Hladam surne inzeraty");
        pockajNaDatabazu();
        database.updateSurneInzeratyAktualnyCas();

        // TERAZ AKTUALIZOVAT SUKROMNE IZNERATY
        // aktualizujeme sukromne inzeraty v remote_inzeraty
        pockajNaDatabazu();
        List<Inzerat> sukromne = database.getSukromneInzeratyFrom(aktualnyPortal);
        System.out.println("inzertujem nove sukromne");
        pockajNaDatabazu();
        // zisti ktore z tychto sukromnych su nove a insertni ich do db
        database.insertRemoteNoveSukromne(sukromne, aktualnyPortal);
        System.out.println("vsetky sukromne inzeraty aktualizovane");
        changes.firePropertyChange("aktualnostiUpdated", false, true);

        System.out.println("remote server DB sa neaktualizuje");
//        // TERAZ AUTOMATICKY UPDATNUT VZDIALENU DATABAZU O NOVE INZERATY
//        System.out.println("updating remote server db");
//        pockajNaMysqlDatabazu();
//        Inzerat lastTimeInserted = mysql.getLastTimeInzeratInserted(aktualnyPortal);
//        pockajNaDatabazu();
//        // v remote_inzeraty si najdeme tie ktore su bazos sukromne a boli pridane len nedavno
//        List<Inzerat> noveInzeraty = database.getRemoteInzeratyListGreaterThanLastTimeInserted(lastTimeInserted.getTimeInserted(), aktualnyPortal);
//        if (noveInzeraty != null && noveInzeraty.size() == 0) {
//            System.out.println("ziadne nove inzeraty nenajdene");
//            //return;
//        } else {
//            System.out.println("nasli sa nove inzeraty");
//            long startTime2 = System.currentTimeMillis();
//            List<Inzerat> toserver = new ArrayList<Inzerat>();
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
//        boolean vsetkyDosli = false;
//        List<Inzerat> toserver = new ArrayList<Inzerat>();
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
        if (lokalita.contains("Nové Mesto n.Váhom")){
            return "Nové Mesto nad Váhom";
        }
        return "Ostatné";
    }

}

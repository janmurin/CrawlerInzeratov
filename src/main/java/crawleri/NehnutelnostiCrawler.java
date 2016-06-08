package crawleri;

import clienti.ClientNehnutelnosti;
import static clienti.ClientNehnutelnosti.NEHNUTELNOSTI_LINK;
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

public class NehnutelnostiCrawler implements Runnable, PropertyChangeListener {

    ExecutorService es = Executors.newCachedThreadPool();
    private Node koren;
    private final Database database;
    private UrychlovacRemoteInsert urychlovacRemoteInsert;
    public static final String NEHNUTELNOSTI_LINK = "http://www.nehnutelnosti.sk";
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
    public boolean isRunning = false;
    JsoupCrawler jcrawler = new JsoupCrawler();
    private int pocetNajdenych;
    private ClientNehnutelnosti[] clienti;
    private final List<String> okresneMesta;
    private final List<Okres> okresy;

    /**
     * konstruktor pre mazanie neaktualnych inzeratov
     */
    public NehnutelnostiCrawler(int mode, Database db, MySQLDatabase msd) {
        database = db;
        mysql = msd;
        aktualnyPortal = NEHNUTELNOSTI_LINK;
        zvoleny_mod = mode;
        urychlovacRemoteInsert = new UrychlovacRemoteInsert(database, null, aktualnyPortal);
        pockajNaDatabazu();
        okresy = db.getOkresy();
        pockajNaDatabazu();
        okresneMesta = db.getOkresneMesta();
    }

//    public static void main(String[] args) {
//        NehnutelnostiCrawler nc = new NehnutelnostiCrawler(0, new Database(), new MySQLDatabase());
//        List<Inzerat> inzs = nc.database.getInzeratyList(NEHNUTELNOSTI_LINK);
//        nc.database.inzertRemoteInzeraty(inzs);
//    }
    public void run() {
        try {
            startTime = System.currentTimeMillis();
            if (zvoleny_mod == DELETE_STARE_MOD) {
                // PREJDEM VSETKY ODKAZY INZERATOV NA PORTALI A VYMAZEM Z DATABAZ TIE INZERATY KTORE NEBUDU V TOMTO ZOZNAME
                System.out.println(sdf.format(new Date(startTime)) + ": " + aktualnyPortal + "  SPUSTENIE DELETE_STARE_MOD");
                // ulozime si kolko novych inzeratov vcera naslo
                pockajNaDatabazu();
                database.ulozVceraNajdenychStatistiku();                

// 1. prejst cely portal a zapametat si ake inzeraty tam su
                System.out.println(" " + aktualnyPortal + "  vytvaram novy strom a hadzem do neho linky aktualnych inzeratov z nehnutelnosti.sk");
                koren = new Node("");
                int max = getPocetVsetkychInzeratov(NEHNUTELNOSTI_LINK);
                hladajLinkyInzeratovDoStromu(max);
                // 2. prejdeme celu databazu a pytame sa ci je inzerat este aktualny, ak NIE, tak si pridame do zoznamu na zmazanie
                System.out.println("nacitavam inzeraty z lokal DB");
                pockajNaDatabazu();
                List<Inzerat> inzeraty = database.getRemoteInzeratyList(aktualnyPortal);
                System.out.println("hladam inzeraty na vymazanie");
                List<Integer> toDeleteIDs = new ArrayList<Integer>();
                for (Inzerat inz : inzeraty) {
                    // v stromu su najdene inzeraty z portalu
                    if (!jeInzeratVStrome(koren, inz.getAktualny_link())) {
                        toDeleteIDs.add(inz.getId());
                    }
                }
                //BUG: PREDPOKLADAM ZE ZMAZANIE SA PODARI NA PRVY KRAT, 
                System.out.println("to delete inzeraty size: " + toDeleteIDs.size());
                pockajNaDatabazu();
                database.deleteRemoteDuplikatneInzeraty(toDeleteIDs);
                System.out.println("lokal DB non-existent inzeraty ZMAZANE");
                System.out.println("mysql DB sa premaze pri update");
//            pockajNaMysqlDatabazu();
//            mysql.deleteInzeratyWhereID(toDeleteIDs);
//            // ak manualne zmazem z lokalnej inzeraty tak z remote databazy ich tiez musim manualne zmazat
//            System.out.println("remote DB non-existent inzeraty ZMAZANE");
                System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ": " + aktualnyPortal + " UKONCENIE DELETE_STARE_MOD trvanie: " + getElapsedTime(startTime));
            }
            if (zvoleny_mod == DOWNLOAD_ALL) {
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + " SPUSTENIE DOWNLOAD_ALL");
                pocetNajdenych = 0;
                clienti = new ClientNehnutelnosti[10];
            //maxHladanychInzeratov = getDnesnychInzeratov(aktualnyPortal);
                //nacitajStromMien();
                int max = getPocetVsetkychInzeratov(NEHNUTELNOSTI_LINK);
                System.out.println("inzeratov: " + max);
                // postupne spustime jednotlivych clientov
                for (int i = 0; i < clienti.length; i++) {
                    clienti[i] = new ClientNehnutelnosti(i + 1, clienti.length, max, database);
                    clienti[i].addPropertyChangeListener(this);
                    es.execute(clienti[i]);
                    System.out.println("spusteny client " + i);
                    try {
                        System.out.println("cakam na spustenie dalsieho clienta");
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                // updatne sa databaza automaticky ked skoncia clienti
            }
            if (zvoleny_mod == DOWNLOAD_24) {
                int vsetkychInzeratov = getPocetVsetkychInzeratov(aktualnyPortal);
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_24");
                changes.firePropertyChange("logHlaska", "", sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE DOWNLOAD_24");
                nacitajStromMien();
                // hladame iba dnesne inzeraty a 100 okrem dneska max
                hladajNoveInzeraty_Nehnutelnosti(1, vsetkychInzeratov);
                updateServerDB();
                String hlaska = sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE DOWNLOAD_24 trvanie: " + getElapsedTime(startTime);
                System.out.println(hlaska);
                changes.firePropertyChange("nehnutelnosti24ended", "", hlaska);
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

    private void hladajNoveInzeraty_Nehnutelnosti(int min, int maxNajdenych) {
        List<Inzerat> toInsert = new ArrayList<Inzerat>();;// nove inzeraty
        try {
            long startTime = System.currentTimeMillis();
            int najdenychOkremDnes = 0;
            int pocetInzeratov = 0;
            // prejdeme inzeraty iba za dnesny den a 50 za minuly den
            String dnesnyDatum = new SimpleDateFormat("dd.MM.YY").format(new Date(System.currentTimeMillis()));
            int INZERATOV_OKREM_DNES = 100;
            // kolkostranok jedne proces prejde?
            // max/60 je stranok a rozdeli sa to medzi 10 procesov : teda max/60/10
            double pocetStranokMusimNavstivit = maxNajdenych / 60.0;

            for (int i = 0; i <= pocetStranokMusimNavstivit; i++) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://www.nehnutelnosti.sk/vyhladavanie/sukromna-osoba?p%5Blimit%5D=60&p[page]=" + (i + 1);
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);

                nadpisy = doc.select("html body div#content div#obsah div#mainContent.withLeftBox div#inzeraty.normal  div.advertisement-head h2");
                Elements datumy = doc.select("html body div#content div#obsah div#mainContent.withLeftBox div#inzeraty.normal div.inzerat-content p.grey span");
                for (int k = 0; k < datumy.size(); k += 2) {
                    Element el = datumy.get(k);
                    String datum = el.text().trim();
                    //System.out.println("datum: [" + datum + "]");
                    if (!datum.equals(dnesnyDatum)) {
                        najdenychOkremDnes++;
                    }
                }
                if (najdenychOkremDnes > INZERATOV_OKREM_DNES) {
                    // nasli sme dost dnesnych inzeratov
                    break;
                }
                //System.out.println("nadpisy size: " + nadpisy.size());
                noveInzeraty = new HashMap<String, String>();

                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
                for (Element el : nadpisy) {
                    String nazov = el.text().trim();
                    String link = el.getElementsByTag("a").attr("href");
                    if (!jeInzeratVStrome(koren, link)) {
                        noveInzeraty.put(nazov, link);
                    }
                    //System.out.println("nazov: " + nazov + " link: " + link);
                }
                //System.out.println("na tejto adrese najdenych novych: "+noveInzeraty.size());
                // pozrieme sa na nove inzeraty a pridame ich do zoznamu na insert do DB
                for (String nazov : noveInzeraty.keySet()) {
                    String link = noveInzeraty.get(nazov);
                    doc = jcrawler.getPage(link);
                    // nacitame udaje o inzerate
                    Element text = null;
                    try {
                        text = doc.select("div#detail-text p.popis").get(0);
                    } catch (Exception e) {
                        Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
                        System.out.println("ODPOVED ");
                        System.out.println(link);
                        // continue;
                    }
//                    System.out.println("========================================================");
//                    System.out.println("NAZOV: " + nazov);
//                    System.out.println("LINK: " + link);
                    if (text == null) {
                        continue;
                    }
                    String textValue = text.html().replaceAll("<br>", "\n").replaceAll("</br>", "").trim();
                    if (textValue.length() > 4000) {
                        System.out.println("textvalue > 4000 : " + link);
                        textValue = textValue.substring(0, 4000);
                    }
                    //System.out.println("TEXT: " + textValue);

                    Element meno = doc.select("div.brokerContacts span.bold").get(0);
                    String menoValue = meno.text();
                    String menoInzerenta = menoValue.trim();
                    String telefonInzerenta = null;
                    try {
                        telefonInzerenta = doc.select("div.brokerContacts span.hiddenPhone a").get(0).attr("data-phone");
                    } catch (Exception e) {
                        telefonInzerenta = "0";
                    }
                    //System.out.println("MENO:" + menoInzerenta + " " + telefonInzerenta);

                    String cenaValue = "";
                    String lokalitaValue = "";
                    String typ = "";
                    String kategoria = "";
                    String datum = "";
                    Elements parametre = doc.select("div#params p");
                    //System.out.println("parametre size: "+parametre.size());
                    for (Element we : parametre) {
                        String element = we.select("span.tlste").text();
                        //System.out.println("element name: "+element);
                        // lokalita
                        if (element.equals("Lokalita:")) {
                            lokalitaValue = we.select("span.left150").get(0).text().trim();
                            lokalitaValue = lokalitaValue.replaceAll("Okres", "").trim();
                        }
                        // cena
                        if (element.equals("Cena:")) {
                            try {
                                cenaValue = we.select("strong#data-price").text().trim();
                            } catch (Exception e) {
                                cenaValue = "dohodou";
                            }
                        }
                        // kategoria
                        if (element.equals("Druh:")) {
                            kategoria = we.select("strong").text().trim();
                        }
                        // typ
                        if (element.equals("Typ:")) {
                            typ = we.select("strong").text().trim();
                        }
                        // Dátum:
                        if (element.equals("Dátum:")) {
                            datum = we.select("strong").text().trim();
                        }
                    }
                    if (cenaValue.length() == 0) {
                        cenaValue = "dohodou";
                    }
                    kategoria = getKategoria(kategoria.trim(), link);
                    typ = getTyp(typ.trim(), link);
//                    System.out.println("LOKALITA:" + lokalitaValue);
//                    System.out.println("CENA: " + cenaValue);
//                    System.out.println("Typ: " + typ);
//                    System.out.println("Kategoria: " + kategoria);

                    Inzerat novy = new Inzerat();
                    novy.setAktualny_link(link);
                    novy.setCena(cenaValue);
                    novy.setLokalita(getOkresneMesto(lokalitaValue.replaceAll("'", "")));
                    novy.setMeno(menoInzerenta.replaceAll("'", ""));
                    novy.setNazov(nazov.replaceAll("'", ""));
                    novy.setPortal(NEHNUTELNOSTI_LINK);
                    novy.setTelefon(telefonInzerenta);
                    novy.setText(textValue.replaceAll("'", ""));
                    novy.setKategoria(kategoria);
                    novy.setTyp(typ);
                    toInsert.add(novy);
                    pocetInzeratov++;
                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i) + "/" + (int) (pocetStranokMusimNavstivit) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i, (int) pocetStranokMusimNavstivit) + " najdenych: " + pocetInzeratov);
                if (toInsert.size() > 250) {
                    if (database.mamDatabazu()) {
                        System.out.println("mam databazu, insertujem inzeratov " + toInsert.size());
                        urychlovacRemoteInsert.inzeraty = toInsert;
                        es.execute(urychlovacRemoteInsert);
                        toInsert = new ArrayList<Inzerat>();
                    } else {
                        System.out.println("nemam databazu");
                    }
                }
            }
            pockajNaDatabazu();
            urychlovacRemoteInsert.inzeraty = toInsert;
            es.execute(urychlovacRemoteInsert);
            urychlovacRemoteInsert = null;

            System.out.println("while cyklus skoncil, nasli sme " + (pocetInzeratov) + " inzeratov");
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            pockajNaDatabazu();
            urychlovacRemoteInsert.inzeraty = toInsert;
            es.execute(urychlovacRemoteInsert);
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
       // List<Inzerat> noveInzeraty = database.getRemoteInzeratyList(aktualnyPortal);
        List<Inzerat> noveInzeraty = database.getRemoteInzeratyListLinky(aktualnyPortal);

        long startAnalyzis = System.currentTimeMillis();
        for (int i = 0; i < noveInzeraty.size(); i++) {
            Inzerat inzerat = noveInzeraty.get(i);
            if (!jeInzeratVStrome(koren, inzerat.getAktualny_link())) {
                pocetUnikatnych++;
            } else {
                toDelete.add(new Integer(inzerat.getId()));
            }
        }
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

    private int getPocetVsetkychInzeratov(String BAZOS_LINK) {
        WebDriver driver = new SilentHtmlUnitDriver();

        driver.get("http://www.nehnutelnosti.sk/vyhladavanie/sukromna-osoba");
        String pocet = "0";
        String source = driver.findElement(By.cssSelector("div#zorad p")).getText();
        // System.out.println(source);
        String searchPhrase = "z celkom";
        for (int i = 0; i < source.length(); i++) {
            if (source.substring(i).startsWith(searchPhrase)) {
                String odsek = source.substring(i + searchPhrase.length());
                //System.out.println(odsek);
                pocet = odsek.substring(0, odsek.indexOf("i"));
                pocet = pocet.replaceAll(" ", "");
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

//    private int getDnesnychInzeratov(String aktualnyPortal) {
//        WebDriver doc = new HtmlUnitDriver();
//
//        doc.get(aktualnyPortal);
//        String pocet = "0";
//        String source = doc.findElement(By.cssSelector("div.sirka")).getText();
//        // System.out.println(source);
//        String searchPhrase = "Inzeráty realit celkom:";
//        String searchPhrase2 = "za 24 hodín:";
//        for (int i = 0; i < source.length(); i++) {
//            if (source.substring(i).startsWith(searchPhrase)) {
//                String odsek = source.substring(i + searchPhrase.length());
//                //System.out.println(odsek);
//                pocet = odsek.substring(0, odsek.indexOf(","));
//                String odsek2 = odsek.substring(odsek.indexOf(",") + searchPhrase2.length() + 2).trim();
//                int dnesnychInzeratov = Integer.parseInt(odsek2);
//                //System.out.println("dnesnych inzeratov: " + dnesnychInzeratov);
//                // System.out.println(pocet);
//                doc.quit();
//                return dnesnychInzeratov;
//            }
//        }
//
//        //System.out.println(pocet);
//        doc.quit();
//        return 10000;
//    }
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

    private void hladajLinkyInzeratovDoStromu(int max) {
        try {
            long startTime = System.currentTimeMillis();
            double pocetStranokMusimNavstivit = max / 60.0;

            for (int i = 0; i < pocetStranokMusimNavstivit; i++) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://www.nehnutelnosti.sk/vyhladavanie/sukromna-osoba?p%5Blimit%5D=60&p[page]=" + (i + 1);
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);

                nadpisy = doc.select("html body div#content div#obsah div#mainContent.withLeftBox div#inzeraty.normal  div.advertisement-head h2");
                //System.out.println("nadpisy size: " + nadpisy.size());
                noveInzeraty = new HashMap<String, String>();
                //Elements datumy=doc.select("html body div#content div#obsah div#mainContent.withLeftBox div#inzeraty.normal div.inzerat-content p.grey span");

                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
                for (Element el : nadpisy) {
                    String nazov = el.text().trim();
                    String link = el.getElementsByTag("a").attr("href");
                    // strom je prazdny
                    if (!jeInzeratVStrome(koren, link)) {
                        noveInzeraty.put(nazov, link);
                    }
                }
//                for (int k=0; k<datumy.size(); k+=2) {
//                    Element el=datumy.get(k);
//                    String datum = el.text().trim();
//                    System.out.println("datum: ["+datum+"]");
//                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i) + "/" + (int) (pocetStranokMusimNavstivit) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i, (int) pocetStranokMusimNavstivit));
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

    private String getKategoria(String kategoria, String link) {
        if (kategoria.equals("Garsónka")) {
            return Kategoria.values[Kategoria.GARZONKA];
        }
        if (kategoria.equals("1 izbový byt")) {
            return Kategoria.values[Kategoria._1_IZBOVY];
        }
        if (kategoria.equals("2 izbový byt")) {
            return Kategoria.values[Kategoria._2_IZBOVY];
        }
        if (kategoria.equals("3 izbový byt") || kategoria.equals("Byty")) {
            return Kategoria.values[Kategoria._3_IZBOVY];
        }
        if (kategoria.equals("4 izbový byt")) {
            return Kategoria.values[Kategoria._4_IZBOVY];
        }
        if (kategoria.equals("5 a viac izbový byt") || kategoria.equals("Mezonet") || kategoria.equals("Apartmán") || kategoria.equals("Iný byt")) {
            return Kategoria.values[Kategoria._5_IZBOVY];
        }
        if (kategoria.equals("Nové projekty")) {
            return Kategoria.values[Kategoria.NOVE_PROJEKTY];
        }
        if (kategoria.equals("Domy") || kategoria.equals("Rodinný dom") || kategoria.equals("Rodinná vila")
                || kategoria.equals("Vidiecky dom") || kategoria.equals("Bývalá po¾nohosp. usadlos")
                || kategoria.equals("Nájomný dom") || kategoria.equals("Apartmánový dom")) {
            return Kategoria.values[Kategoria.DOMY];
        }
        if (kategoria.equals("Garáž")) {
            return Kategoria.values[Kategoria.GARAZE];
        }
        if (kategoria.equals("Reštaurácia") || kategoria.equals("Hotel, penzión") || kategoria.equals("Kúpe¾ný objekt") || kategoria.equals("Reštauraèné priestory")) {
            return Kategoria.values[Kategoria.HOTELY_RESTAURACIE];
        }
        if (kategoria.equals("Chata a chalupa") || kategoria.equals("Zrubový dom") || kategoria.equals("Bungalov")
                || kategoria.equals("Rekreaèné domy") || kategoria.equals("Zruby a drevenice") || kategoria.equals("Záhradná chatka")
                || kategoria.equals("Iný objekt na rekreáciu")) {
            return Kategoria.values[Kategoria.CHALUPY_CHATY];
        }
        if (kategoria.equals("Priestory") || kategoria.equals("Kancelárie, admin. priestory")) {
            return Kategoria.values[Kategoria.KANCELARIE];
        }
        if (kategoria.equals("Administratívny objekt") || kategoria.equals("Polyfunkèný objekt") || kategoria.equals("Obchodné priestory") || kategoria.equals("Iné komerèné priestory")) {
            return Kategoria.values[Kategoria.OBCHODNE_PRIESTORY];
        }
        if (kategoria.equals("Spevnené plochy") || kategoria.equals("Pozemky") || kategoria.equals("Pozemok pre rod. domy") || kategoria.equals("Pozemok pre bytovú výstavbu")
                || kategoria.equals("Rekreaèný pozemok") || kategoria.equals("Pozemok pre obè. vybavenos") || kategoria.equals("Iný stavebný pozemok") || kategoria.equals("Iný po¾nohosp. pozemok")) {
            return Kategoria.values[Kategoria.POZEMKY];
        }
        if (kategoria.equals("Objekty") || kategoria.equals("Objekt pre obchod") || kategoria.equals("Objekt pre šport") || kategoria.equals("Výrobný objekt")
                || kategoria.equals("Iný komerèný objekt") || kategoria.equals("Skladový objekt") || kategoria.equals("Po¾nohosp. objekt") || kategoria.equals("Iný prevádzkový objekt")
                || kategoria.equals("Prevádzkový areál") || kategoria.equals("Opravárenský objekt") || kategoria.equals("Výrobné priestory") || kategoria.equals("Skladové priestory")
                || kategoria.equals("Èerpacia stanica PHM") || kategoria.equals("Opravárenské priestory") || kategoria.equals("Iné prevádzkové priestory")) {
            return Kategoria.values[Kategoria.SKLADY];
        }
        if (kategoria.equals("Záhrada") || kategoria.equals("Orná pôda") || kategoria.equals("Sad") || kategoria.equals("Lúka, pasienok") || kategoria.equals("Lesy")
                || kategoria.equals("Chmelnica, vinica") || kategoria.equals("Rybník, vodná plocha")) {
            return Kategoria.values[Kategoria.ZAHRADY];
        }
        if (kategoria.equals("Ostatné") || kategoria.equals("Iný objekt na bývanie") || kategoria.equals("Malá elektráreò") || kategoria.equals("Iný objekt")
                || kategoria.equals("Historický objekt") || kategoria.equals("Športové priestory") || kategoria.equals("Zmiešaná zóna") || kategoria.equals("Priemyselná zóna")
                || kategoria.equals("Komerèná zóna")) {
            return Kategoria.values[Kategoria.OSTATNE];
        }
        if (kategoria.equals("Podnájom, spolubývajúci")) {
            return Kategoria.values[Kategoria.PODNAJOM_SPOLUBYVAJUCI];
        }
        if (kategoria.equals("Ubytovanie")) {
            return Kategoria.values[Kategoria.UBYTOVANIE];
        }
        System.err.println("nepodarilo sa odhalit kategoriu pre: " + kategoria + " link: " + link);
        return Kategoria.values[Kategoria.OSTATNE];

    }

    private String getTyp(String typ, String link) {
        if (typ.equals("Predaj") || typ.equals("Prenájom") || typ.equals("Výmena") || typ.equals("Dražba")) {
            return Typ.Predám + "";
        }
        if (typ.equals("Dopyt") || typ.equals("Kúpa") || typ.equals("Podnájom")) {
            return Typ.Kúpim + "";
        }
        System.err.println("nepodarilo sa odhalit typ pre: " + typ + " link: " + link);
        return Typ.Predám + "";
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if ("najdeneInzeraty".equals(evt.getPropertyName())) {
            pocetNajdenych++;
            System.out.println("pocetUkoncenych clientov: " + pocetNajdenych);
            if (pocetNajdenych == clienti.length) {
                ulozNajdeneDoDatabazy();
            }
        }
    }

    private void ulozNajdeneDoDatabazy() {
        System.out.println("vsetci clienti skoncili, mergujem inzeraty");
        List<Inzerat> toInsert = new ArrayList<Inzerat>();
        List<Inzerat> unikatne = new ArrayList<Inzerat>();
        for (int i = 0; i < clienti.length; i++) {
            toInsert.addAll(clienti[i].najdeneInzeraty);
        }
        koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
        for (Inzerat inz : toInsert) {
            if (!jeInzeratVStrome(koren, inz.getAktualny_link())) {
                unikatne.add(inz);
            }
        }
        pockajNaDatabazu();
        database.inzertRemoteInzeraty(unikatne, aktualnyPortal);
        System.out.println("do databazy vlozenych inzeratov: " + unikatne.size());
        updateServerDB();
        System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + " UKONCENIE DOWNLOAD_ALL trvanie: " + getElapsedTime(startTime));
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
        return (hodinStringE + ":" + minutStringE + ":" + sekundStringE);
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
}

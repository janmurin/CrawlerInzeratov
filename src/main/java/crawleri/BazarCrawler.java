package crawleri;

import clienti.ClientBazar;
import static clienti.ClientBazar.BAZAR_LINK;
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
import javax.swing.JOptionPane;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

public class BazarCrawler implements Runnable, PropertyChangeListener {

    ExecutorService es = Executors.newCachedThreadPool();
    private Node koren;
    private final Database database;
    private UrychlovacRemoteInsert urychlovacRemoteInsert;
    public static final String BAZAR_LINK = "http://reality.bazar.sk";
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
    private ClientBazar[] clienti;
    private Iterable<String> okresneMesta;
    private Iterable<Okres> okresy;

    /**
     * konstruktor pre mazanie neaktualnych inzeratov
     */
    public BazarCrawler(int mode, Database db, MySQLDatabase msd) {
        database = db;
        mysql = msd;
        aktualnyPortal = BAZAR_LINK;
        zvoleny_mod = mode;
        urychlovacRemoteInsert = new UrychlovacRemoteInsert(database, null, aktualnyPortal);
        pockajNaDatabazu();
        okresy = db.getOkresy();
        pockajNaDatabazu();
        okresneMesta = db.getOkresneMesta();
    }

//    public static void main(String[] args) {
//        NehnutelnostiCrawler nc = new NehnutelnostiCrawler(0, new Database(), new MySQLDatabase());
//        List<Inzerat> inzs = nc.database.getInzeratyList(BAZAR_LINK);
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
                int max = getPocetVsetkychInzeratov(BAZAR_LINK);
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
                clienti = new ClientBazar[10];
                //maxHladanychInzeratov = getDnesnychInzeratov(aktualnyPortal);
                //nacitajStromMien();
                int max = getPocetVsetkychInzeratov(BAZAR_LINK);
                System.out.println("inzeratov: " + max);
                // postupne spustime jednotlivych clientov
                for (int i = 0; i < clienti.length; i++) {
                    clienti[i] = new ClientBazar(i + 1, clienti.length, max, database);
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
                hladajNoveInzeraty_Bazar(1, vsetkychInzeratov);
                aktualizujDB();
                String hlaska = sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE DOWNLOAD_24 trvanie: " + getElapsedTime(startTime);
                System.out.println(hlaska);
                changes.firePropertyChange("bazar24ended", "", hlaska);
            }
            if (zvoleny_mod == UPDATE_ALL) {
                System.out.println(sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  SPUSTENIE UPDATE_ALL");
                aktualizujDB();
                System.out.println(sdf.format(new Date(System.currentTimeMillis())) + ":" + aktualnyPortal + "  UKONCENIE UPDATE_ALL trvanie: " + getElapsedTime(startTime));
            }
        } catch (Exception e) {
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
            changes.firePropertyChange("VYNIMKA", false, true);
            changes.firePropertyChange("logHlaska", "", sdf.format(new Date(startTime)) + ":" + aktualnyPortal + "  VYNIMKA: " + e.toString());
        }
    }

    private void hladajNoveInzeraty_Bazar(int min, int maxNajdenych) {
        List<Inzerat> toInsert = new ArrayList<Inzerat>();;// nove inzeraty
        try {
            long startTime = System.currentTimeMillis();
            int najdenychOkremDnes = 0;
            // prejdeme inzeraty iba za dnesny den a 50 za minuly den
            String dnesnyDatum = new SimpleDateFormat("dd.MM.YY").format(new Date(System.currentTimeMillis()));
            int INZERATOV_OKREM_DNES = 100;
            int najdenych = 0;
            // kolkostranok jedne proces prejde?
            // max/60 je stranok a rozdeli sa to medzi 10 procesov : teda max/60/10
            double pocetStranokMusimNavstivit = maxNajdenych / 20.0 + 1;

            for (int i = 0; i < pocetStranokMusimNavstivit; i++) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://reality.bazar.sk/?p%5Bparam5%5D=6&p%5Bpage%5D=" + (i);
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);
                //                    html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35.top header h2 a
                nadpisy = doc.select("html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35.top header h2 a");
                if (nadpisy.size() == 0) {
                    nadpisy = doc.select("html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35 header h2 a");
                    if (nadpisy.size() == 0) {
                        System.out.println("nadpisy size 0, link: " + currentLink);
                        System.out.println(doc);
                        JOptionPane.showMessageDialog(null, "nadpisy size 0");
                    }
                }
                Elements datumy = doc.select("html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35.top div.item-teaser.text div p.stplce span");
                if (datumy.size() == 0) {
                    datumy = doc.select("html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35 div.item-teaser.text div p.stplce span");
                    if (datumy.size() == 0) {
                        System.out.println("DATUMY SIZE 0");
                        System.out.println(doc);
                        JOptionPane.showMessageDialog(null, "datumy size 0");
                    }
                }
                for (int k = 7; k < datumy.size(); k += 8) {
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
                    String link = el.getElementsByTag("a").attr("href").trim();
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

//                    System.out.println("========================================================");
//                    System.out.println("NAZOV: " + nazov);
//                    System.out.println("LINK: " + link);
                    Element text = doc.select("html body.detail div.container.position-relative main.span35 article section.about div.detail-info").get(0);
                    String textValue = text.html();//.replaceAll("<br>", "\n").replaceAll("</br>", "").trim();
                    textValue = textValue.substring(textValue.indexOf("</div>") + 6);
                    textValue = textValue.substring(0, textValue.indexOf("<div"));
                    textValue = textValue.replaceAll("<br>", "");
                    if (textValue.contains("<span>")) {
                        textValue = textValue.substring(0, textValue.indexOf("<span>")) + textValue.substring(textValue.indexOf("</span>") + 8);
                    }
                    if (textValue.length() > 4000) {
                        System.out.println("textvalue > 4000 : " + link);
                        textValue = textValue.substring(0, 4000);
                    }
                    //System.out.println("TEXT: " + textValue);

                    Element meno = doc.select("html body.detail div.container.position-relative main.span35 article section.about div.detail-info div.detail-more div.seller-info.span25 div.span21.value strong.seller-name").get(0);
                    String menoValue = meno.text();
                    String menoInzerenta = menoValue.trim();
                    String telefonInzerenta = null;
                    try {
                        String sel = "html body.detail div.container.position-relative main.span35 article section.about div.detail-info div.detail-more div.seller-info.span25 div.span21.value strong";
                        telefonInzerenta = doc.select(sel).get(doc.select(sel).size() - 1).text();
                    } catch (Exception e) {
                        telefonInzerenta = "0";
                    }
                    //System.out.println("MENO:" + menoInzerenta + " TELEFON: " + telefonInzerenta);

                    String cenaValue = doc.select("html body.detail div.container.position-relative main.span35 article section.about div.clearer span.price strong").text();
                    String lokalitaValue = ""; //doc.select("html body.detail div.container.position-relative main.span35 article section.about div.detail-info div.detail-more div.seller-info.span25 div.span21.value strong").text();
                    String typ = "";
                    String kategoria = "";
                    String parametre = doc.select("html body.detail div.container.position-relative main.span35 article section.about div.detail-info div.params").text();
                    //System.out.println("parametre: " + parametre);
                    for (int j = 0; j < parametre.length(); j++) {
                        String akt = parametre.substring(j);
                        if (akt.startsWith("Typ:")) {
                            typ = akt.substring(4, akt.indexOf(",")).trim();
                            continue;
                        }
                        if (akt.startsWith("Lokalita:")) {
                            lokalitaValue = akt.substring(9).trim();
                            if (lokalitaValue.contains(",")) {
                                lokalitaValue = lokalitaValue.substring(0, lokalitaValue.indexOf(",")).trim();
                            }
                            if (lokalitaValue.startsWith("Ko�ice")) {
                                lokalitaValue = "Ko�ice";
                            }
                            if (lokalitaValue.startsWith("Bratislava")) {
                                lokalitaValue = "Bratislava";
                            }
                            continue;
                        }
                        if (akt.startsWith("Druh:")) {
                            kategoria = akt.substring(6, akt.indexOf(",")).trim();
                        }
                        if (akt.startsWith("Po�et izieb:")) {
                            kategoria = akt.substring(13, akt.indexOf(",")).trim();
                        }
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
                    novy.setPortal(BAZAR_LINK);
                    novy.setTelefon(telefonInzerenta);
                    novy.setText(textValue.replaceAll("'", ""));
                    novy.setKategoria(kategoria);
                    novy.setTyp(typ);
                    toInsert.add(novy);
                    najdenych++;
                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i) + "/" + (int) (pocetStranokMusimNavstivit) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i, (int) pocetStranokMusimNavstivit) + " najdenych: " + najdenych);
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

            System.out.println("while cyklus skoncil, nasli sme " + (najdenych) + " inzeratov");
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

    private void aktualizujDB() {
        System.out.println("INIT aktualizacia local DB");
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

        driver.get("http://reality.bazar.sk/?p%5Bparam5%5D=6");
        String pocet = "0";
        String source = driver.findElement(By.cssSelector("html body.search div.container.position-relative main.span35.offset2 div.left.prev-next span.count")).getText();
        // System.out.println(source);
        String searchPhrase = "z celkom";
        for (int i = 0; i < source.length(); i++) {
            if (source.substring(i).startsWith(searchPhrase)) {
                String odsek = source.substring(i + searchPhrase.length());
                pocet = odsek.replaceAll(" ", "");
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
//        String searchPhrase = "Inzer�ty realit celkom:";
//        String searchPhrase2 = "za 24 hod�n:";
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
            double pocetStranokMusimNavstivit = max / 20.0 + 1;

            for (int i = 0; i < pocetStranokMusimNavstivit; i++) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://reality.bazar.sk/?p%5Bparam5%5D=6&p%5Bpage%5D=" + (i);
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);
                //html body.search div.span49.center section#main.offset2.span35 div#search-results.normal article.item.span35 header h2
                nadpisy = doc.select("html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35.top header h2 a");
                if (nadpisy.size() == 0) {
                    nadpisy = doc.select("html body.search div.container.position-relative main.span35.offset2 div#search-results.normal article.item.span35 header h2 a");
                    if (nadpisy.size() == 0) {
                        System.out.println("nadpisy size 0, link: " + currentLink);
                        System.out.println(doc);
                        JOptionPane.showMessageDialog(null, "nadpisy size 0");
                    }
                }
                noveInzeraty = new HashMap<String, String>();
                //Elements datumy=doc.select("html body div#content div#obsah div#mainContent.withLeftBox div#inzeraty.normal div.inzerat-content p.grey span");

                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
                for (Element el : nadpisy) {
                    String nazov = el.text().trim();
                    String link = el.getElementsByTag("a").attr("href").trim();
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
        if (kategoria.equals("Gars�nky")) {
            return Kategoria.values[Kategoria.GARZONKA];
        }
        if (kategoria.equals("1 izbov� byty")) {
            return Kategoria.values[Kategoria._1_IZBOVY];
        }
        if (kategoria.equals("2 izbov� byty")) {
            return Kategoria.values[Kategoria._2_IZBOVY];
        }
        if (kategoria.equals("3 izbov� byty") || kategoria.equals("Byty")) {
            return Kategoria.values[Kategoria._3_IZBOVY];
        }
        if (kategoria.equals("4 izbov� byty")) {
            return Kategoria.values[Kategoria._4_IZBOVY];
        }
        if (kategoria.equals("5 a viac izbov� byty") || kategoria.equals("Mezonet") || kategoria.equals("Apartm�n") || kategoria.equals("In� byty")) {
            return Kategoria.values[Kategoria._5_IZBOVY];
        }
        if (kategoria.equals("Nov� projekty")) {
            return Kategoria.values[Kategoria.NOVE_PROJEKTY];
        }
        if (kategoria.equals("Bungalovy") || kategoria.equals("Rodinn� domy") || kategoria.equals("B�val� po�nohospod�rske usadlosti")
                || kategoria.equals("Vily") || kategoria.equals("Ostatn� domy")
                || kategoria.equals("N�jomn� dom") || kategoria.equals("Apartm�nov� dom") || kategoria.equals("N�jomn� domy")) {
            return Kategoria.values[Kategoria.DOMY];
        }

        if (kategoria.equals("Jednotliv� gar�") || kategoria.equals("Hromadn� gar�")) {
            return Kategoria.values[Kategoria.GARAZE];
        }
        if (kategoria.equals("Re�taur�cia") || kategoria.equals("Hotel, penzi�n") || kategoria.equals("K�pe�n� objekt") || kategoria.equals("Re�taura�n� priestory")) {
            return Kategoria.values[Kategoria.HOTELY_RESTAURACIE];
        }

        if (kategoria.equals("Chalupy a rekrea�n� dom�eky") || kategoria.equals("Z�hradn� chatky") || kategoria.equals("Bungalov")
                || kategoria.equals("Rekrea�n� domy") || kategoria.equals("Zruby a drevenice") || kategoria.equals("Z�hradn� chatka")
                || kategoria.equals("In� objekt na rekre�ciu")) {
            return Kategoria.values[Kategoria.CHALUPY_CHATY];
        }
        if (kategoria.equals("Kancel�rske a administrat�vne priestory") || kategoria.equals("Kancel�rie, admin. priestory")) {
            return Kategoria.values[Kategoria.KANCELARIE];
        }
        if (kategoria.equals("Obchodn� priestory") || kategoria.equals("Re�taura�n� priestory") || kategoria.equals("�portov� priestory") || kategoria.equals("In� komer�n� priestory")
                || kategoria.equals("Oprav�rensk� priestory") || kategoria.equals("Priestory pre sklad") || kategoria.equals("Priestory pre v�robu")
                || kategoria.equals("Priestory pre chov zvierat") || kategoria.equals("In� prev�dzkov� priestory") || kategoria.equals("In� komer�n� objekty")) {
            return Kategoria.values[Kategoria.OBCHODNE_PRIESTORY];
        }
        if (kategoria.equals("Pozemky pre rodinn� domy") || kategoria.equals("Pozemky pre bytov� v�stavbu") || kategoria.equals("Rekrea�n� pozemky") || kategoria.equals("Komer�n� z�na")
                || kategoria.equals("Priemyseln� z�na") || kategoria.equals("Zmie�an� z�na") || kategoria.equals("Chme�nice a vinice") || kategoria.equals("Lesy")
                || kategoria.equals("Polia a orn� p�da") || kategoria.equals("Rybn�ky a vodn� plochy") || kategoria.equals("Sady")
                || kategoria.equals("Z�hrady") || kategoria.equals("In� po�nohospod�rske pozemky") || kategoria.equals("L�ky a pasienky") || kategoria.equals("In� stavebn� pozemky")) {
            return Kategoria.values[Kategoria.POZEMKY];
        }
        if (kategoria.equals("Objekty") || kategoria.equals("Objekt pre obchod") || kategoria.equals("Objekt pre �port") || kategoria.equals("V�robn� objekt")
                || kategoria.equals("In� komer�n� objekt") || kategoria.equals("Skladov� objekt") || kategoria.equals("Po�nohosp. objekt") || kategoria.equals("In� prev�dzkov� objekt")
                || kategoria.equals("Prev�dzkov� are�l") || kategoria.equals("Oprav�rensk� objekt") || kategoria.equals("V�robn� priestory") || kategoria.equals("Skladov� priestory")
                || kategoria.equals("In� prev�dzkov� objekty") || kategoria.equals("�erpacie stanice PHM") || kategoria.equals("In� prev�dzkov� priestory")) {
            return Kategoria.values[Kategoria.SKLADY];
        }
        if (kategoria.equals("Z�hrada") || kategoria.equals("Orn� p�da") || kategoria.equals("Sad") || kategoria.equals("L�ka, pasienok") || kategoria.equals("Lesy")
                || kategoria.equals("Chmelnica, vinica") || kategoria.equals("Rybn�k, vodn� plocha")) {
            return Kategoria.values[Kategoria.ZAHRADY];
        }
        if (kategoria.equals("Ostatn�") || kategoria.equals("In� objekt na b�vanie") || kategoria.equals("Mal� elektr�re�") || kategoria.equals("In� objekt")
                || kategoria.equals("Historick� objekty") || kategoria.equals("Objekty pre �port") || kategoria.equals("Zmie�an� z�na") || kategoria.equals("Priemyseln� z�na")
                || kategoria.equals("Komer�n� z�na")) {
            return Kategoria.values[Kategoria.OSTATNE];
        }
        if (kategoria.equals("Podn�jom, spolub�vaj�ci")) {
            return Kategoria.values[Kategoria.PODNAJOM_SPOLUBYVAJUCI];
        }
        if (kategoria.equals("Ubytovanie")) {
            return Kategoria.values[Kategoria.UBYTOVANIE];
        }
        System.err.println("nepodarilo sa odhalit kategoriu pre: " + kategoria + " link: " + link);
        return Kategoria.values[Kategoria.OSTATNE];
    }

    private String getTyp(String typ, String link) {
        if (typ.equals("Predaj") || typ.equals("Pren�jom") || typ.equals("V�mena") || typ.equals("Dra�ba") || typ.equals("Ponuka")) {
            return Typ.Pred�m + "";
        }
        if (typ.equals("Dopyt") || typ.equals("K�pa") || typ.equals("Podn�jom")) {
            return Typ.K�pim + "";
        }
        System.err.println("nepodarilo sa odhalit typ pre: " + typ + " link: " + link);
        return Typ.Pred�m + "";
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
        aktualizujDB();
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

        return "Ostatn�";
    }
}

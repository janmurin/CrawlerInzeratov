/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clienti;

import crawleri.BazosCrawler;
import home.crawlerinzeratov.Database;
import home.crawlerinzeratov.Inzerat;
import crawleri.JsoupCrawler;
import crawleri.Okres;
import crawleri.RealityInzerciaCrawler;
import crawleri.Setting;
import crawleri.UrychlovacRemoteInsert;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
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

/**
 *
 * @author Janco1
 */
public class ClientRealityInzeratyALL implements Runnable {

    private final JsoupCrawler jcrawler = new JsoupCrawler();
    public static final String REALITY_INZERCIA_LINK = "http://reality.inzercia.sk";
    private Node koren;
    private Node firemne;
    private final Database database;
    public final Setting setting;
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    UrychlovacRemoteInsert urychlovacRemoteInsert;
    ExecutorService es = Executors.newCachedThreadPool();
    private RealityInzerciaCrawler cir;
    private final List<String> okresneMesta;
    private final List<Okres> okresy;
    
    public ClientRealityInzeratyALL(Database db, Setting s, RealityInzerciaCrawler cr) {
        this.database = db;
        urychlovacRemoteInsert = new UrychlovacRemoteInsert(database, null, REALITY_INZERCIA_LINK);
        this.setting = s;
        cir=cr;
                pockajNaDatabazu();
        okresy = db.getOkresy();
        pockajNaDatabazu();
        okresneMesta = db.getOkresneMesta();
    }

    public void run() {
        System.out.println("spusteny client ClientRealityInzeraty link: " + setting.link);
//        try {
//            Thread.sleep((long) (Math.random() * 10000 + 3000));
//        } catch (InterruptedException ex) {
//            Logger.getLogger(ClientRealityInzeratyALL.class.getName()).log(Level.SEVERE, null, ex);
//        }

        //nacitajStromMien();
        if (setting.stran == 0) {
            setting.stran = 1;
        }
        int pocetInzeratov = 0;
        List<Inzerat> toInsert = new ArrayList<Inzerat>();
        long startTime = System.currentTimeMillis();
        List<String> firemneLinky = new ArrayList<String>();
        String currentLink = "";
        String currentLink2 = "";
        long spracovaneNadpisyTime = 0;
        long startTime15 = System.currentTimeMillis();
        Elements nadpisy = null;
        Map<String, String> noveInzeraty = null;
        Document doc = null;
        boolean jeFirma=false;
        Element felement;
        Elements infos;
        Element text;

        try {
            // mame link a mame pocet stran link: http://rekreacne-pozemky.inzercia.sk/predam/  inzeratov: 96 pocet stranok: 7
            for (int i = 1; i <= setting.stran; i++) {
                startTime15 = System.currentTimeMillis();

                currentLink = setting.link + "?strana=" + (i);
                //System.out.println(currentLink);
                doc = jcrawler.getPage(currentLink);

                nadpisy = doc.select("html body div#main div#right div.inzerat div.content h4 a");
                //System.out.println("nadpisy size: " + nadpisy.size());
                noveInzeraty = new HashMap<String, String>();

                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
                for (Element el : nadpisy) {
                    String nazov = el.text().trim();
                    String link = el.getElementsByTag("a").attr("href");
                    noveInzeraty.put(nazov, link);
//                    if (!jeInzeratVStrome(koren, link)) {
//                        noveInzeraty.put(nazov, link);
//                    }
                    //System.out.println("nazov: " + nazov + " link: " + link);
                }
                spracovaneNadpisyTime = System.currentTimeMillis();
                //System.out.println("na tejto adrese najdenych novych: "+noveInzeraty.size());
                for (String nazov : noveInzeraty.keySet()) {
                    String link = noveInzeraty.get(nazov);
                    doc = jcrawler.getPage(link);
                    currentLink2 = link;
                    // najprv skontrolujeme ci nema privela inzeratov
                    infos = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom div#top_tabs ul.tabs_left.tabs li a ");
                    jeFirma = false;
                    felement = null;
                    for (Element el : infos) {
                        if (el.text().contains("Inzeráty používate¾a")) {
                            felement = el;
                            String pocet = el.text().replace("Inzeráty používate¾a", "").replace("(", "").replace(")", "").trim();
                            if (Integer.parseInt(pocet) > 2) {
                                jeFirma = true;
                            }
                            break;
                        }
                    }
                    if (jeFirma) {
                        firemneLinky.add(link);
                        //System.out.println("link: " + link);
                        //System.out.println("nasli sme firemny inzerat " + felement.text());
                        continue;
                    }

                    // nacitame udaje o inzerate
                    String textValue = "";
                    try {
                        text = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom p.description_cont span#i-pps.description").get(0);
                        textValue = text.html().replaceAll("<br />", "\n").replaceAll("<br>", "").replaceAll("&nbsp;", " ").trim();
                        if (textValue.length() > 4000) {
                            System.out.println("textvalue > 4000 : " + link);
                            textValue = textValue.substring(0, 4000);
                        }
                    } catch (Exception e) {
                        Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
                        System.out.println("ODPOVED ");
                        System.out.println(link);
                        textValue = "chyba";
                        // continue;
                    }
//                    System.out.println("========================================================");
//                    System.out.println("NAZOV: " + nazov);
//                    System.out.println("LINK: " + link);

                    String menoInzerenta = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom div.contact div p.inzerent a#i-nzrnt").text().trim();
                    String telefonInzerenta = null;
                    try {
                        telefonInzerenta = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom div.contact div p.dolezitejsie span#i-mn span#printPhoneNumber").get(0).text().trim();
                    } catch (Exception e) {
                        telefonInzerenta = "nezverejnený";
                    }
//                    System.out.println("MENO:" + menoInzerenta + " TELEFON: " + telefonInzerenta);
                    if (menoInzerenta.isEmpty()) {
                        menoInzerenta = "neregistrovaný";
                    }

                    String cenaValue = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom span.showprice strong#i-cn.price").get(0).text();
                    String lokalitaValue = "";
                    try {
                        lokalitaValue = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom p.metainfo span#i-lkc.city a").get(0).text().split("okres")[1].trim();
                    } catch (Exception e) {

                        try {
                            lokalitaValue = doc.select("html body div#main div#right div.hproduct div.detail div.detos_right div.detos_right_bottom p.metainfo span#i-lkc.city a").get(0).text();
                        } catch (Exception ex) {
                            lokalitaValue = "nezistené";
                            System.out.println("lokalita nezistena: " + currentLink2);
                        }
                        //
                    }
                    String typ = "";
                    String kategoria = "";

                    kategoria = setting.kategoria;
                    typ = setting.typ;
//                    System.out.println("TEXT: " + textValue);
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
                    novy.setPortal(REALITY_INZERCIA_LINK);
                    novy.setTelefon(telefonInzerenta);
                    novy.setText(textValue.replaceAll("'", ""));
                    novy.setKategoria(kategoria);
                    novy.setTyp(typ);
                    toInsert.add(novy);
                    pocetInzeratov++;
                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i) + "/" + setting.stran + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i, setting.stran) + " najdenych: " + pocetInzeratov + " casNadpisov: " + (spracovaneNadpisyTime - startTime15) + " casSelectovania inzeratov: " + (System.currentTimeMillis() - spracovaneNadpisyTime));
                if (toInsert.size() > 500) {
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
            pockajNaDatabazu();
            database.insertFiremneLinky(firemneLinky);
            System.out.println("while cyklus skoncil, nasli sme " + pocetInzeratov + " inzeratov");
            cir.ukoncene++;
            changes.firePropertyChange("ClientRealityInzeraty_ukonceny", false, true);
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception + " link: " + currentLink + " link2: " + currentLink2);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            pockajNaDatabazu();
            urychlovacRemoteInsert.inzeraty = toInsert;
            es.execute(urychlovacRemoteInsert);
            pockajNaDatabazu();
            database.insertFiremneLinky(firemneLinky);
            changes.firePropertyChange("ClientRealityInzeraty_ukonceny", false, true);
        }
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

    private void nacitajStromMien() {
        System.out.println("nacitavam strom mien");
        koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
        int pocetUnikatnych = 0;
        List<Integer> toDelete = new ArrayList<Integer>();
        pockajNaDatabazu();
        List<Inzerat> noveInzeraty = database.getRemoteInzeratyList(REALITY_INZERCIA_LINK);

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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
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

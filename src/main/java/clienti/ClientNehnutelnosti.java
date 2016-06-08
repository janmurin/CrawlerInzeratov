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
import home.crawlerinzeratov.Kategoria;
import home.crawlerinzeratov.Typ;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author Janco1
 */
public class ClientNehnutelnosti implements Runnable {

    private int start;
    private int step;
    private int max;
    private JsoupCrawler jcrawler = new JsoupCrawler();
    public static final String NEHNUTELNOSTI_LINK = "http://www.nehnutelnosti.sk";
    private Node koren;
    private Database database;
    public List<Inzerat> najdeneInzeraty;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final List<Okres> okresy;
    private final List<String> okresneMesta;

    public ClientNehnutelnosti(int start, int step, int max, Database db) {
        this.start = start;
        this.step = step;
        this.max = max;
        this.database = db;
        pockajNaDatabazu();
        okresy = db.getOkresy();
        pockajNaDatabazu();
        okresneMesta = db.getOkresneMesta();
    }

    public void run() {
        nacitajStromMien();
        List<Inzerat> toInsert = new ArrayList<Inzerat>();;// nove inzeraty
        try {
            long startTime = System.currentTimeMillis();
            int najdenych = 0;

            // kolkostranok jedne proces prejde?
            // max/60 je stranok a rozdeli sa to medzi 10 procesov : teda max/60/10
            double pocetStranokMusimNavstivit = max / 60.0 / step;

            for (int i = 0; i < pocetStranokMusimNavstivit; i++) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://www.nehnutelnosti.sk/vyhladavanie/sukromna-osoba?p%5Blimit%5D=60&p[page]=" + (start + i * step);
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);
                nadpisy = doc.select("html body div#content div#obsah div#mainContent.withLeftBox div#inzeraty.normal  div.advertisement-head h2");
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
                    najdenych++;
                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i) + "/" + (int) (pocetStranokMusimNavstivit) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i, (int) pocetStranokMusimNavstivit) + " najdenych: " + najdenych);
            }
            System.out.println("while cyklus skoncil, nasli sme " + (toInsert.size()) + " inzeratov");
            najdeneInzeraty = toInsert;
            changes.firePropertyChange("najdeneInzeraty", false, true);
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            najdeneInzeraty = toInsert;
            changes.firePropertyChange("najdeneInzeraty", false, true);
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

    private void nacitajStromMien() {
        System.out.println("nacitavam strom mien");
        koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
        int pocetUnikatnych = 0;
        List<Integer> toDelete = new ArrayList<Integer>();
        pockajNaDatabazu();
        //List<Inzerat> noveInzeraty = database.getRemoteInzeratyList(NEHNUTELNOSTI_LINK);
        List<Inzerat> noveInzeraty = database.getRemoteInzeratyListLinky(NEHNUTELNOSTI_LINK);

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

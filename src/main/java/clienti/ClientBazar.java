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
public class ClientBazar implements Runnable {

    private int start;
    private int step;
    private int max;
    private JsoupCrawler jcrawler = new JsoupCrawler();
    public static final String BAZAR_LINK = "http://reality.bazar.sk";
    private Node koren;
    private Database database;
    public List<Inzerat> najdeneInzeraty;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private List<Okres> okresy;
    private Iterable<String> okresneMesta;

    public ClientBazar(int start, int step, int max, Database db) {
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
            double pocetStranokMusimNavstivit = max / 20.0 / step;

            for (int i = 0; i < pocetStranokMusimNavstivit; i++) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://reality.bazar.sk/?p%5Bparam5%5D=6&p%5Bpage%5D=" + (start + i * step);
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);
                nadpisy = doc.select("html body.search div.span49.center section#main.offset2.span35 div#search-results.normal article.item.span35.top header h2");
                if (nadpisy.size() == 0) {
                    //System.out.println("nadpisy size 0, link: " + currentLink);
//                    System.out.println(doc);
//                    JOptionPane.showMessageDialog(null, "nadpisy size 0");
                    nadpisy = doc.select("html body.search div.span49.center section#main.offset2.span35 div#search-results.normal article.item.span35 header h2");
                }
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
                            if (lokalitaValue.startsWith("Košice")) {
                                lokalitaValue = "Košice";
                            }
                            if (lokalitaValue.startsWith("Bratislava")) {
                                lokalitaValue = "Bratislava";
                            }
                            continue;
                        }
                        if (akt.startsWith("Druh:")) {
                            kategoria = akt.substring(6, akt.indexOf(",")).trim();
                        }
                        if (akt.startsWith("Poèet izieb:")) {
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
        //List<Inzerat> noveInzeraty = database.getRemoteInzeratyList(BAZAR_LINK);
        List<Inzerat> noveInzeraty = database.getRemoteInzeratyListLinky(BAZAR_LINK);

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
        if (kategoria.equals("Garsónky")) {
            return Kategoria.values[Kategoria.GARZONKA];
        }
        if (kategoria.equals("1 izbové byty")) {
            return Kategoria.values[Kategoria._1_IZBOVY];
        }
        if (kategoria.equals("2 izbové byty")) {
            return Kategoria.values[Kategoria._2_IZBOVY];
        }
        if (kategoria.equals("3 izbové byty") || kategoria.equals("Byty")) {
            return Kategoria.values[Kategoria._3_IZBOVY];
        }
        if (kategoria.equals("4 izbové byty")) {
            return Kategoria.values[Kategoria._4_IZBOVY];
        }
        if (kategoria.equals("5 a viac izbové byty") || kategoria.equals("Mezonet") || kategoria.equals("Apartmán") || kategoria.equals("Iné byty")) {
            return Kategoria.values[Kategoria._5_IZBOVY];
        }
        if (kategoria.equals("Nové projekty")) {
            return Kategoria.values[Kategoria.NOVE_PROJEKTY];
        }
        if (kategoria.equals("Bungalovy") || kategoria.equals("Rodinné domy") || kategoria.equals("Bývalé po¾nohospodárske usadlosti")
                || kategoria.equals("Vily") || kategoria.equals("Ostatné domy")
                || kategoria.equals("Nájomný dom") || kategoria.equals("Apartmánový dom") || kategoria.equals("Nájomné domy")) {
            return Kategoria.values[Kategoria.DOMY];
        }

        if (kategoria.equals("Jednotlivá garáž") || kategoria.equals("Hromadná garáž")) {
            return Kategoria.values[Kategoria.GARAZE];
        }
        if (kategoria.equals("Reštaurácia") || kategoria.equals("Hotel, penzión") || kategoria.equals("Kúpe¾ný objekt") || kategoria.equals("Reštauraèné priestory")) {
            return Kategoria.values[Kategoria.HOTELY_RESTAURACIE];
        }

        if (kategoria.equals("Chalupy a rekreaèné domèeky") || kategoria.equals("Záhradné chatky") || kategoria.equals("Bungalov")
                || kategoria.equals("Rekreaèné domy") || kategoria.equals("Zruby a drevenice") || kategoria.equals("Záhradná chatka")
                || kategoria.equals("Iný objekt na rekreáciu")) {
            return Kategoria.values[Kategoria.CHALUPY_CHATY];
        }
        if (kategoria.equals("Kancelárske a administratívne priestory") || kategoria.equals("Kancelárie, admin. priestory")) {
            return Kategoria.values[Kategoria.KANCELARIE];
        }
        if (kategoria.equals("Obchodné priestory") || kategoria.equals("Reštauraèné priestory") || kategoria.equals("Športové priestory") || kategoria.equals("Iné komerèné priestory")
                || kategoria.equals("Opravárenské priestory") || kategoria.equals("Priestory pre sklad") || kategoria.equals("Priestory pre výrobu")
                || kategoria.equals("Priestory pre chov zvierat") || kategoria.equals("Iné prevádzkové priestory") || kategoria.equals("Iné komerèné objekty")) {
            return Kategoria.values[Kategoria.OBCHODNE_PRIESTORY];
        }
        if (kategoria.equals("Pozemky pre rodinné domy") || kategoria.equals("Pozemky pre bytovú výstavbu") || kategoria.equals("Rekreaèné pozemky") || kategoria.equals("Komerèná zóna")
                || kategoria.equals("Priemyselná zóna") || kategoria.equals("Zmiešaná zóna") || kategoria.equals("Chme¾nice a vinice") || kategoria.equals("Lesy")
                || kategoria.equals("Polia a orná pôda") || kategoria.equals("Rybníky a vodné plochy") || kategoria.equals("Sady")
                || kategoria.equals("Záhrady") || kategoria.equals("Iné po¾nohospodárske pozemky") || kategoria.equals("Lúky a pasienky") || kategoria.equals("Iné stavebné pozemky")) {
            return Kategoria.values[Kategoria.POZEMKY];
        }
        if (kategoria.equals("Objekty") || kategoria.equals("Objekt pre obchod") || kategoria.equals("Objekt pre šport") || kategoria.equals("Výrobný objekt")
                || kategoria.equals("Iný komerèný objekt") || kategoria.equals("Skladový objekt") || kategoria.equals("Po¾nohosp. objekt") || kategoria.equals("Iný prevádzkový objekt")
                || kategoria.equals("Prevádzkový areál") || kategoria.equals("Opravárenský objekt") || kategoria.equals("Výrobné priestory") || kategoria.equals("Skladové priestory")
                || kategoria.equals("Iné prevádzkové objekty") || kategoria.equals("Èerpacie stanice PHM") || kategoria.equals("Iné prevádzkové priestory")) {
            return Kategoria.values[Kategoria.SKLADY];
        }
        if (kategoria.equals("Záhrada") || kategoria.equals("Orná pôda") || kategoria.equals("Sad") || kategoria.equals("Lúka, pasienok") || kategoria.equals("Lesy")
                || kategoria.equals("Chmelnica, vinica") || kategoria.equals("Rybník, vodná plocha")) {
            return Kategoria.values[Kategoria.ZAHRADY];
        }
        if (kategoria.equals("Ostatné") || kategoria.equals("Iný objekt na bývanie") || kategoria.equals("Malá elektráreò") || kategoria.equals("Iný objekt")
                || kategoria.equals("Historické objekty") || kategoria.equals("Objekty pre šport") || kategoria.equals("Zmiešaná zóna") || kategoria.equals("Priemyselná zóna")
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
        if (typ.equals("Predaj") || typ.equals("Prenájom") || typ.equals("Výmena") || typ.equals("Dražba") || typ.equals("Ponuka")) {
            return Typ.Predám + "";
        }
        if (typ.equals("Dopyt") || typ.equals("Kúpa") || typ.equals("Podnájom") || typ.equals("H¾adám")) {
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

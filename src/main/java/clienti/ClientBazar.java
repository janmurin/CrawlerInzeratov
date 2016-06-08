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
        if (typ.equals("Dopyt") || typ.equals("K�pa") || typ.equals("Podn�jom") || typ.equals("H�ad�m")) {
            return Typ.K�pim + "";
        }
        System.err.println("nepodarilo sa odhalit typ pre: " + typ + " link: " + link);
        return Typ.Pred�m + "";
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

        return "Ostatn�";
    }
}

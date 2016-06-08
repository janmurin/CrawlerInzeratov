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
import crawleri.RealityInzerciaCrawler;
import crawleri.Setting;
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
public class ClientRealityInzeratyHladajLinky implements Runnable {

    private JsoupCrawler jcrawler = new JsoupCrawler();
    public static final String REALITY_INZERCIA_LINK = "http://reality.inzercia.sk";
    public List<String> najdeneLinky;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    public final Setting setting;
    public boolean skoncil=false;
    public RealityInzerciaCrawler ric;
    private final int start;
    private final int koniec;

    public ClientRealityInzeratyHladajLinky(Setting s, RealityInzerciaCrawler ric, int start, int koniec) {
        najdeneLinky = new ArrayList<String>();
        this.setting = s;
        this.ric=ric;
        this.start=start;
        this.koniec=koniec;
    }

    public void run() {
        try {
            System.out.println("ClientRealityInzeratyHladajLinky executed");
            long startTime = System.currentTimeMillis();
            String currentLink = "";
            if (setting.stran == 0) {
                setting.stran = 1;
            }
            long startTime15 = 0;
            Elements nadpisy = null;
            Document doc = null;

            for (int i = start; i <= koniec; i++) {
                startTime15 = System.currentTimeMillis();

                currentLink = setting.link + "?strana=" + (i);
                //System.out.println(currentLink);
                doc = jcrawler.getPage(currentLink);
                nadpisy = doc.select("html body div#main div#right div.inzerat div.content h4 a");

                for (Element el : nadpisy) {
                    String link = el.getElementsByTag("a").attr("href");
                    najdeneLinky.add(link);
                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i-start) + "/" + (koniec-start) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i-start, koniec-start) + " najdenych: " + najdeneLinky.size());
            }
            System.out.println("while cyklus skoncil, nasli sme " + (najdeneLinky.size()) + " linkov");
            if (najdeneLinky.size() == 0) {
                System.out.println("NULA LINKOV: link " + currentLink + " stran: " + setting.stran + " inzeratov: " + setting.inzeratov);
            }
            skoncil=true;
            ric.ukoncene++;
            changes.firePropertyChange("realityInzerciaNajdeneLinky", false, true);
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            changes.firePropertyChange("realityInzerciaNajdeneLinky", false, true);
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
        return (hodinStringE + ":" + minutStringE + ":" + sekundStringE);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }
}

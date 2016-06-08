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
public class ClientBazosHladajLinky implements Runnable {

    private int start;
    private int step;
    private int max;
    private JsoupCrawler jcrawler = new JsoupCrawler();
    public static final String BAZOS_LINK = "http://reality.bazos.sk";
    public List<String> najdeneLinky;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);

    public ClientBazosHladajLinky(int start, int max) {
        this.start = start;
        this.max = max;
        najdeneLinky = new ArrayList<String>();
    }

    public void run() {
        try {
            long startTime = System.currentTimeMillis();

            // tu je lepsia ina metoda for cyklu ako na nehnutelnostiach
            for (int i = start; i <= max; i += 15) {
                long startTime15 = System.currentTimeMillis();
                Elements nadpisy = null;
                Map<String, String> noveInzeraty = null;
                Document doc = null;

                String currentLink = "http://reality.bazos.sk/" + (i) + "/";
                //System.out.println("getting: "+currentLink);
                doc = jcrawler.getPage(currentLink);

                nadpisy = doc.select("span.nadpis");
                //System.out.println("nadpisy size: " + nadpisy.size());
                noveInzeraty = new HashMap<String, String>();

                // pozrieme sa na inzeraty a ulozime si na nahliadnutie tie ktore este nemame v DB
                for (Element el : nadpisy) {
                    String link = "http://reality.bazos.sk" + el.getElementsByTag("a").attr("href");
                    //System.out.println("kontrola: ["+link.trim()+"]");
                    najdeneLinky.add(link);
                }

                System.out.printf(" %04d", (System.currentTimeMillis() - startTime15));
                System.out.print(" " + (i - start) + "/" + (max - start) + "   ");
                System.out.println("ETA: " + getETAtime(startTime, i - start, max - start) + " najdenych: " + najdeneLinky.size());
            }
            System.out.println("while cyklus skoncil, nasli sme " + (najdeneLinky.size()) + " linkov");
            changes.firePropertyChange("bazosNajdeneLinky", false, true);
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            changes.firePropertyChange("bazosNajdeneLinky", false, true);
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

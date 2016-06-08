/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package deleted;

import crawleri.BazosCrawler;
import home.crawlerinzeratov.Database;
import home.crawlerinzeratov.Inzerat;
import crawleri.UrychlovacInsertInzeraty;
import static crawleri.BazosCrawler.BAZOS_LINK;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

public class JsoupClient {

    private Connection connection;
    //String connectionUrl;
    private List<Inzerat> inzeratyAktualnyPortal;
    private Database database;
    ExecutorService es = Executors.newCachedThreadPool();
    private UrychlovacInsertInzeraty urychlovac;
    public static final String BAZOS_LINK = "http://reality.bazos.sk/";
    private int pocetInzeratov;
    private long startTime;
    private int cislo;

    public JsoupClient() {
        // login je default
        //connectionUrl = BAZOS_LINK;
        
        System.out.println("Jsoup client process started.");
        //connect();
        //nacitajDatabazu("http://reality.bazos.sk"); // TODO
    }

    public static void main(String[] args) {
        JsoupClient bc = new JsoupClient();
        bc.execute();
    }

    private void nacitajDatabazu(String portalName) {
        //SpustacDatabazy.execute();
        database = new Database();
        //inzeratyAktualnyPortal = database.getInzeratyList(portalName);
        urychlovac = new UrychlovacInsertInzeraty(database, null);
    }

    public void hladajNoveInzeraty(String portalName) {
        if (portalName.equalsIgnoreCase(BAZOS_LINK)) {
            hladajNoveInzeraty_Bazos(BAZOS_LINK, 0);
        }
    }

    public void execute() {
        String portalName = BAZOS_LINK;
        startTime = System.currentTimeMillis();
        nacitajDatabazu(portalName);
        hladajNoveInzeraty(portalName);

    }

    public void get(String link) {
        connection = Jsoup.connect(link);
        connection.method(Method.GET);
    }

    public void printETATime() {
        double rychlost = ((System.currentTimeMillis() - startTime) / 1000) / pocetInzeratov;
        double etaTime = (77590 - pocetInzeratov - cislo) * rychlost;
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
        System.out.println("ETA:" + (hodinStringE + ":" + minutStringE + ":" + sekundStringE));
    }

//    public void connect() {
//        connection = Jsoup.connect(getConnectionUrl());
//    }
    private void hladajNoveInzeraty_Bazos(String currentLink, int cisloStart) {
        try {
            //WebDriver driver = new FirefoxDriver();

            //String currentLink = "http://reality.bazos.sk/";
            //driver.get(currentLink);
            get(currentLink);
            Response response = connection.execute();
            
            Document document = response.parse();
            
            boolean beziCrawler = true;
            cislo = cisloStart;
            pocetInzeratov = 0;
            startTime = System.currentTimeMillis();
            List<Inzerat> toInsert;// nove inzeraty
            List<Inzerat> toUpdate;// stare inzeraty, iba updatnut linky
            while (beziCrawler) {
                long startTime15 = System.currentTimeMillis();
                //List<WebElement> nadpisy = driver.findElements(By.cssSelector("span.nadpis"));
                Elements nadpisy = parseElement(document, "span.nadpis");
                Elements linky = parseElement(document, "span.nadpis a");
                beziCrawler = false;
//                if (nadpisy.size() < 15) {
//                    beziCrawler = false;
//                }
                toInsert = new ArrayList<Inzerat>();
                for (int j = 0; j < 1; j++) {
                    //nadpisy = driver.findElements(By.cssSelector("span.nadpis"));
                    //WebElement we = nadpisy.get(j);
                    String nazov =nadpisy.get(j).text();
                    String link = "http://reality.bazos.sk"+linky.get(j).attr("href");
                    System.out.println((j) + "." + nazov + " LINK: " + link);
                    
                    get(link);
                    //Response odpoved = connection.execute();
                    //Document doc=odpoved.parse();
                    Document doc = Jsoup.parse(new URL(link).openStream(), "UTF-8", link);
                   // System.out.println(doc);
//                    // nacitame udaje o inzerate
                    Elements textElement = parseElement(doc, "td[colspan]");
                    System.out.println("elements text size: "+textElement.size());
                    String textValue = textElement.get(0).html();
                    System.out.println("TEXT:\n" + textValue);

                    Elements meno = parseElement(doc, "td[colspan]");
                    System.out.println("elements text size: "+meno.size());
//                    List<WebElement> meno = driver.findElements(By.cssSelector("td[colspan=\"2\"]"));
                    String menoValue = meno.get(1).text();
                    System.out.println("meno\n"+menoValue);
//                    String menoInzerenta = menoValue.split("\n")[0];
//                    String telefonInzerenta = menoValue.split("\n")[1];
//                    System.out.println("MENO:\n" + menoInzerenta + " " +telefonInzerenta );
//
//                    WebElement lokalita = driver.findElement(By.cssSelector("a[title=\"Približná lokalita\"]"));
//                    String lokalitaValue = lokalita.getText();
//                    //System.out.println("LOKALITA:\n" + lokalitaValue);
//
//                    String cenaValue = meno.get(1).getText();
//                    try {
//                        cenaValue = cenaValue.substring(2, cenaValue.indexOf("(")).replaceAll(" ", "");
//                    } catch (java.lang.StringIndexOutOfBoundsException e) {
//                        cenaValue = "0";
//                    }
//                    //System.out.println("CENA:\n" + cenaValue);
//                    Inzerat novy = new Inzerat();
//                    novy.setAktualny_link(link);
//                    novy.setCena(Integer.parseInt(cenaValue));
//                    novy.setLokalita(lokalitaValue.replaceAll("'", ""));
//                    novy.setMeno(menoInzerenta.replaceAll("'", ""));
//                    novy.setNazov(nazov.replaceAll("'", ""));
//                    novy.setPortal(BAZOS_LINK);
//                    novy.setTelefon(telefonInzerenta);
//                    novy.setText(textValue.replaceAll("'", ""));
//                    toInsert.add(novy);
                    pocetInzeratov++;
//
//                    driver.get(currentLink);
                }
                //urychlovac.inzeraty = toInsert;
                //es.execute(urychlovac);
                System.out.print(((System.currentTimeMillis() - startTime15) / 1000) + " " + (cislo) + "/77590  ");
                printETATime();
                cislo += 15;
                currentLink = "http://reality.bazos.sk/" + cislo + "/";
                //driver.get(currentLink);
            }
        // Google's search is rendered dynamically with JavaScript.
            // Wait for the page to load, timeout after 10 seconds
//        (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
//            public Boolean apply(WebDriver d) {
//                return d.getTitle().toLowerCase().startsWith("cheese!");
//            }
//        });

            //Close the browser
            //driver.quit();
        } catch (Exception exception) {
            System.out.println("VYNIMKA: " + exception);
            Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
            hladajNoveInzeraty_Bazos(currentLink, cislo);
        }
    }

//    public Image getCaptcha() throws IOException {
//        Response response = connection.execute();
//        cookies.putAll(response.cookies());
//        Document doc = response.parse();
//
//        // Microsoft premenne idu zo servera na stranku, treba ich precitat a poslat, oni sa menia kazdym requestom, bez toho to bolo nechodive
//        // toto zabralo najviac casu, kym som na to prisiel
//        viewState = parseElement(doc, "input#__VIEWSTATE").val();
//        eventValidation = parseElement(doc, "input#__EVENTVALIDATION").val();
//
//        // read mp3 captcha
//        Elements mp3captcha = parseElement(doc, "a#hlAudioDownload");
//        if ((mp3captcha != null) && (mp3captcha.size() > 0)) {
//            String src = mp3captcha.first().attr("href");
//            URL url = null;
//            try {
//                url = new URL(src);
//            } catch (MalformedURLException e) {
//            }
//            if (url == null) {
//                src = getConnectionUrl() + src;
//                try {
//                    url = new URL(src);
//                } catch (MalformedURLException e) {
//                }
//            }
//            if (url != null) {
//                org.apache.commons.io.FileUtils.copyURLToFile(url, new File("mp3captcha.mp3"));
//                mp3Captcha = new File("mp3captcha.mp3");
//            }
//        }
//        // read img captcha
//        Elements captcha = parseElement(doc, "img#imgCaptcha");
//        if ((captcha != null) && (captcha.size() > 0)) {
//            String src = captcha.first().attr("src");
//            URL url = null;
//            try {
//                url = new URL(src);
//            } catch (MalformedURLException e) {
//            }
//            if (url == null) {
//                src = getConnectionUrl() + src;
//                try {
//                    url = new URL(src);
//                } catch (MalformedURLException e) {
//                }
//            }
//            if (url != null) {
//                Image image = ImageIO.read(url);
//                return image;
//            }
//        }
//
//        System.out.println("returning image is null");
//        return null;
//    }
//    public String getConnectionUrl() {
//        return connectionUrl;
//    }
    private static Elements parseElement(Document doc, String selector) {
        return doc.select(selector);
    }

    public void disconnect() {
        // v podstate nepotrebne
        connection = null;
    }

    public void run() {

    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

import crawleri.BazosCrawler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Janco1
 */
public class PomocneVypocty {

    Database database = new Database();
    private ArrayList<Email> emaily;

    private boolean pridajEmail(String addr) {
        for (Email e : emaily) {
            if (e.adresa.equals(addr)) {
                e.pocet++;
                return true;
            }
        }
        emaily.add(new Email(addr, 1));
        return false;
    }

    private class Email implements Comparable<Email> {

        String adresa;
        int pocet;

        public Email(String adresa, int pocet) {
            this.adresa = adresa;
            this.pocet = pocet;
        }

        public int compareTo(Email o) {
            return Integer.compare(pocet, o.pocet);
        }
    }

    private List<String> riadky = new ArrayList<String>();

    public void execute() {
        List<Inzerat> inzeratyList = database.getInzeratyList("http://reality.bazos.sk");
        System.out.println("inzeraty list size: " + inzeratyList.size());

        emaily = new ArrayList<Email>();

        for (Inzerat inz : inzeratyList) {
            if (inz.getText().contains("@")) {
                vlozDoEmailov(inz.getText());
            }
        }

        Collections.sort(emaily);
        Collections.reverse(emaily);
//        System.out.println(emaily);
        int suma = 0;
        for (int i = 0; i < emaily.size(); i++) {
            suma += emaily.get(i).pocet;
            System.out.println(i + ". " + emaily.get(i).adresa + ": " + emaily.get(i).pocet);
        }
        System.out.println("vsetkych emailov: " + suma);
    }

    public static void main(String[] args) {
        PomocneVypocty pomocneVypocty = new PomocneVypocty();
        pomocneVypocty.execute();

    }

    private void vlozDoEmailov(String text) {
        List<String> adresy = new ArrayList<String>();
//        int medzeraIdx=0;
//        text=text.replaceAll("\n", " ");
//        for(int i=0; i<text.length(); i++){
//            if(text.charAt(i)==' '){
//                medzeraIdx=i;
//            }
//            if(text.charAt(i)=='@'){
//                String adresa=text.substring(medzeraIdx, text.substring(i).indexOf(".sk")+i+3).replaceAll(" ", "");
////                try {
////                    riadky.add(text.substring(i - 20, i + 20));
////                } catch (Exception e) {
////                    Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, e);
////                }
//                adresy.add(adresa);
//            }
//        }
        String RE_MAIL = "([\\w\\-]([\\.\\w])+[\\w]+@([\\w\\-]+\\.)+[A-Za-z]{2,4})";
        Pattern p = Pattern.compile(RE_MAIL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            //      System.out.println(m.group(1));
            //adresy.add(m.group(1));
            String addr = m.group(1);
            pridajEmail(addr);
        }
        //System.out.println("naslo tieto adresy: " + adresy);
    }
}

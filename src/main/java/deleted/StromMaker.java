/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package deleted;

import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Janco1
 */
public class StromMaker {

    private Node koren;

    private class Node implements Comparable<Node> {

        String hodnota;
        Set<Node> potomkovia = new TreeSet<Node>();

        public Node(String hodnota) {
            this.hodnota = hodnota;
        }

        public int compareTo(Node o) {
            return (int) hodnota.charAt(0) - (int) o.hodnota.charAt(0);
        }
    }

    private void nacitajStromMien() {
        koren = new Node("");
        // prechadzame vsetky inzeraty a hladame ci sa nachadza v strome, ak nie, tak ho pridame
//        for (Inzerat inzerat : inzeratyAktualnyPortal) {
//            jeInzeratVStrome(koren, inzerat.getNazov());
//        }
        System.out.println(jeInzeratVStrome(koren, "prvy Element"));
        System.out.println(jeInzeratVStrome(koren, "prvy Element"));
        System.out.println(jeInzeratVStrome(koren, "prvy Elementka"));
        System.out.println(jeInzeratVStrome(koren, "2prvy Elementka"));
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

    public static void main(String[] args) {
        StromMaker sm = new StromMaker();
        sm.nacitajStromMien();
    }
}

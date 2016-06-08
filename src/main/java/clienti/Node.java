/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clienti;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Janco1
 */
public class Node {

        public String hodnota;
        public List<Node> potomkovia = new ArrayList<Node>();

        public Node(String hodnota) {
            this.hodnota = hodnota;
        }
    }

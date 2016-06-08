/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

import crawleri.Okres;
import java.util.List;

/**
 *
 * @author Janco1
 */
public class pomOkresy {
    
    public static void main(String[] args) {
        Database db=new Database();
        List<Okres> okresy = db.getOkresy();
        for (Okres o:okresy){
            System.out.println("insert into okresy(obec,okres)values('"+o.obec+"','"+o.okres+"');");
        }
    }
}

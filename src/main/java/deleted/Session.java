/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package deleted;

import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

/**
 *
 * @author Janco1
 */
public class Session {
    String link;
    String nazov;
    Document doc;
    boolean downloaded=false;

    Session(String string, String currentLink) {
        nazov=string;
        link=currentLink;
        
    }
}

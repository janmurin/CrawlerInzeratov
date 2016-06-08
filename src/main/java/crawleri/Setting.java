/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawleri;

/**
 *
 * @author Janco1
 */
public class Setting {

    public String typ;
    public String kategoria;
    public String link;
    public int inzeratov;
    public int stran;

    public Setting(String typ, String kategoria, String link) {
        this.typ = typ;
        this.kategoria = kategoria;
        this.link = link;
    }
}

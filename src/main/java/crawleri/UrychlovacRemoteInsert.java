/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawleri;

import home.crawlerinzeratov.Database;
import home.crawlerinzeratov.Inzerat;
import java.util.List;

/**
 *
 * @author Janco1
 */
public class UrychlovacRemoteInsert implements Runnable {

    private Database database;
    public List<Inzerat> inzeraty;
    String portal;

    public UrychlovacRemoteInsert(Database database, List<Inzerat> inzeraty, String portal) {
        this.database = database;
        this.inzeraty = inzeraty;
        this.portal=portal;
    }

    public void run() {
        database.inzertRemoteInzeraty(inzeraty,portal);
        System.out.println(inzeraty.size() + " inzeratov insertnutych, DB uvolnena");
    }

}

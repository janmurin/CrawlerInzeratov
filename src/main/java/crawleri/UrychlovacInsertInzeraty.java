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
public class UrychlovacInsertInzeraty implements Runnable {

    private Database database;
    public List<Inzerat> inzeraty;
    

    public UrychlovacInsertInzeraty(Database database, List<Inzerat> inzeraty) {
        this.database = database;
        this.inzeraty = inzeraty;
    }

    public void run() {
        database.inzertInzeraty(inzeraty);
        System.out.println(inzeraty.size()+" inzeratov insertnutych, DB uvolnena");
    }

    
}

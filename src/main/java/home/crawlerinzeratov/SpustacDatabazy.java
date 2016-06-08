package home.crawlerinzeratov;

import org.hsqldb.Server;

public class SpustacDatabazy {
    
    public static void  execute(){
        Server server =new Server();
        server.setDatabaseName(0,"inzeratydb");
        server.setDatabasePath(0 ,"db/inzeratydb");
        server.setPort(1234);
        //server.setAddress("1.1.1.1");
        server.start();     
    }
    public static void main(String[] args) {
        //System.setProperty("sun.arch.data.model", "32");
        Server server =new Server();
        server.setDatabaseName(0,"inzeratydb");
        server.setDatabasePath(0 ,"db/inzeratydb");
        server.setPort(1234);
        //server.setAddress("1.1.1.1");
        server.start();
    }
}

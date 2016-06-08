/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package deleted;

import deleted.Session;
import crawleri.BazosCrawler;
import crawleri.JsoupCrawler;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Janco1
 */
public class Downloader implements Runnable {

    boolean koniec = false;
    Queue<Session> sessions = new LinkedList<Session>();
    Queue<Session> downloadedSessions = new LinkedList<Session>();
    private int counter=0;
    JsoupCrawler jcrawler=new JsoupCrawler();

    public void run() {
        while (!koniec) {
            while (!sessions.isEmpty()) {
                sessions.peek().doc=jcrawler.getPage(sessions.peek().link);
                //sessions.poll();
                downloadedSessions.offer(sessions.poll());
                counter++;
                //System.out.println("getlink: "+counter);
            }
            try {
                //System.out.println("Downloader: cakam na session");
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BazosCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    void offerSession(Session session) {
        sessions.offer(session);
        //System.out.println("Downloader: session prijata");
    }

    Session getSession() {
        if (downloadedSessions.isEmpty()){
            //System.out.println("Downloader: vraciam session");
        }
        return downloadedSessions.poll();
    }

}

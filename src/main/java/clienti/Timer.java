/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clienti;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Janco1
 */
public class Timer implements Runnable {

    long startTime;
    boolean stop;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    //StopWatch stopWatch=new StopWatch();

    public Timer(long startTime) {
        this.startTime = startTime;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    
    public void run() {

        System.out.println("timer started at: " + (new SimpleDateFormat("HH:mm:ss").format(new Date(startTime))));
        long lastTime = startTime;

        while (true) {
            if (stop) {
                System.out.println("timer STOPPED at: " + (new SimpleDateFormat("HH:mm:ss").format(new Date(System.currentTimeMillis()))));
                int medzicas = (int) (-startTime + lastTime);
                changes.firePropertyChange("medzicas", 0, medzicas);
                break;
            }
            changes.firePropertyChange("secondAdded", false, true);
            lastTime = System.currentTimeMillis();
            //System.out.println(lastTime);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Timer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}

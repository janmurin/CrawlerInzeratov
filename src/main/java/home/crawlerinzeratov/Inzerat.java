/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package home.crawlerinzeratov;

/**
 *
 * @author Janco1
 */
public class Inzerat implements Comparable<Inzerat> {

    private int id;
    private String portal;
    private String nazov;
    private String text;
    private String meno;
    private String telefon;
    private String lokalita;
    private String aktualny_link;
    private String typ;
    private String kategoria;
    private String cena;
    private boolean odoslany;
    private String timeInserted;
     private String archivaciaTime;
    private int pocetZobrazeni;
    private boolean zaujimavy;
    private boolean surne;
    private boolean precitany;

    public String getArchivaciaTime() {
        return archivaciaTime;
    }

    public void setArchivaciaTime(String archivaciaTime) {
        this.archivaciaTime = archivaciaTime;
    }
    
    public boolean isPrecitany() {
        return precitany;
    }

    public void setPrecitany(boolean precitany) {
        this.precitany = precitany;
    }
    

    public String getTyp() {
        return typ;
    }

    public void setTyp(String typ) {
        this.typ = typ;
    }

    public String getKategoria() {
        return kategoria;
    }

    public void setKategoria(String kategoria) {
        this.kategoria = kategoria;
    }

    public boolean isSurne() {
        return surne;
    }

    public void setSurne(boolean surne) {
        this.surne = surne;
    }

    public void setSurne(byte surne) {
        if (surne == 0) {
            this.surne = false;
        } else {
            this.surne = true;
        }
    }

    public int getPocetZobrazeni() {
        return pocetZobrazeni;
    }

    public void setPocetZobrazeni(int pocetZobrazeni) {
        this.pocetZobrazeni = pocetZobrazeni;
    }

    public boolean isZaujimavy() {
        return zaujimavy;
    }

    public void setZaujimavy(boolean zaujimavy) {
        this.zaujimavy = zaujimavy;
    }

    public void setZaujimavy(byte zaujimavy) {
        if (zaujimavy == 0) {
            this.zaujimavy = false;
        } else {
            this.zaujimavy = true;
        }
    }
    public void setPrecitany(byte zaujimavy) {
        if (zaujimavy == 0) {
            this.precitany = false;
        } else {
            this.precitany = true;
        }
    }

    public String getTimeInserted() {
        return timeInserted;
    }

    public void setTimeInserted(String last_modified) {
        this.timeInserted = last_modified;
    }

    public boolean isOdoslany() {
        return odoslany;
    }

    public void setOdoslany(boolean odoslany) {
        this.odoslany = odoslany;
    }

    public void setOdoslany(byte odoslany) {
        if (odoslany == 0) {
            this.odoslany = false;
        } else {
            this.odoslany = true;
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPortal() {
        return portal;
    }

    public void setPortal(String portal) {
        this.portal = portal;
    }

    public String getNazov() {
        return nazov;
    }

    public void setNazov(String nazov) {
        this.nazov = nazov;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMeno() {
        return meno;
    }

    public void setMeno(String meno) {
        this.meno = meno;
    }

    public String getTelefon() {
        return telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
    }

    public String getLokalita() {
        return lokalita;
    }

    public void setLokalita(String lokalita) {
        this.lokalita = lokalita;
    }

    public String getAktualny_link() {
        return aktualny_link;
    }

    public void setAktualny_link(String aktualny_link) {
        this.aktualny_link = aktualny_link;
    }

    public String getCena() {
        return cena;
    }

    public void setCena(String cena) {
        this.cena = cena;
    }

    @Override
    public String toString() {
        return id + " " + aktualny_link;
    }

    public int compareTo(Inzerat o) {
        return this.timeInserted.compareTo(o.getTimeInserted());
    }


}

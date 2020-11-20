package cs10.apps.web.statsforspotify.view;

import cs10.apps.web.statsforspotify.model.Artist;

import java.awt.*;

public class LocalTop10Frame extends DetailsFrame {
    private final Artist[] artists;

    public LocalTop10Frame(Artist[] artists){
        super("Local Top 10 Artists", "Rank and Popularity", 400, 300);
        this.artists = artists;
    }

    @Override
    protected void fillDetails() {
        Color[] colors = new Color[]{Color.RED, Color.ORANGE, Color.GREEN, Color.CYAN, Color.MAGENTA};
        for (int i=0; i<artists.length; i++){
            String tag = artists[i].getName();
            histograma.agregarColumna(tag, artists[i].getScore(), colors[i % colors.length]);
        }
    }
}

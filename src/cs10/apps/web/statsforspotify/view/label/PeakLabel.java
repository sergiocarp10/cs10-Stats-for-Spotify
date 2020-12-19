package cs10.apps.web.statsforspotify.view.label;

import java.awt.*;

public class PeakLabel extends CircleLabel {

    public PeakLabel(){
        super("Peak", true);
        setSharp(true);
        setVisible(false);
        setAverage(1);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}

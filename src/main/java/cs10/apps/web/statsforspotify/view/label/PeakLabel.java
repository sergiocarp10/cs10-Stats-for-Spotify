package cs10.apps.web.statsforspotify.view.label;

public class PeakLabel extends CircleLabel {
    private float originalMinutes;

    public PeakLabel(){
        super("--", true);
        setVisible(false);
    }

    public void changeToPeak(){
        setMinutes(false);
        setTitle("Track Peak");
        setInverted(true);
    }

    public void changeToLastFM(float minutes, int playCount, int average){
        setOriginalValue(0);
        setMinutes(true);
        originalMinutes = minutes;
        //setValue(0);
        setTitle("Minutes");
        setAverage(average);
        setInverted(false);
    }

    public void updateMinutes(int time){
        float valueF = originalMinutes + time / 60f;
        int value = (int) valueF;
        if (getValue() >= 0 && value != getValue()){
            setValue(value);
            repaint();
        }
    }
}

package cs10.apps.web.statsforspotify.view;

import com.wrapper.spotify.model_objects.specification.Track;
import cs10.apps.web.statsforspotify.app.AppOptions;
import cs10.apps.web.statsforspotify.app.DevelopException;
import cs10.apps.web.statsforspotify.io.ArtistDirectory;
import cs10.apps.web.statsforspotify.io.Library;
import cs10.apps.web.statsforspotify.io.SongFile;
import cs10.apps.web.statsforspotify.model.BlockedItem;
import cs10.apps.web.statsforspotify.model.Collab;
import cs10.apps.web.statsforspotify.utils.IOUtils;
import cs10.apps.web.statsforspotify.view.label.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Set;

public class CustomPlayer extends JPanel {
    private final DecimalFormat decimalFormat = new DecimalFormat("#00");
    private final CustomThumbnail thumbnail;
    private final CircleLabel popularityLabel;
    private final ScoreLabel scoreLabel;
    private final PeakLabel peakLabel;
    private final JProgressBar progressBar;
    private final AppOptions appOptions;
    private String currentSongId;
    private Library library;
    private JLabel sessionLabel;
    private Set<BlockedItem> blacklist;
    private int average, sessionScore, pendingScore, timeCount;

    public CustomPlayer(int thumbSize, AppOptions appOptions) {
        this.appOptions = appOptions;
        this.thumbnail = new CustomThumbnail(thumbSize);
        this.popularityLabel = new PopularityLabel();
        this.scoreLabel = new ScoreLabel();
        this.peakLabel = new PeakLabel();
        this.progressBar = new JProgressBar(0, 100);

        this.customizeProgressBar();
        this.add(thumbnail);
        this.add(popularityLabel);
        this.add(scoreLabel);
        this.add(progressBar);
        this.add(peakLabel);
    }

    public void enableLibrary(){
        new Thread(() -> library = Library.getInstance(null)).start();
    }

    private void customizeProgressBar(){
        progressBar.setPreferredSize(new Dimension(360,30));
        progressBar.setBorder(new EmptyBorder(0,10,0,25));
        progressBar.setStringPainted(true);
        progressBar.setForeground(Color.GREEN);
        progressBar.setUI(new BasicProgressBarUI() {
            protected Color getSelectionBackground() { return Color.black; }
            protected Color getSelectionForeground() { return Color.black; }
        });
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public void setString(String s){
        this.progressBar.setString(s);
    }

    public void setAverage(int average){
        this.average = average;
        this.popularityLabel.setAverage(average);
        this.peakLabel.setAverage(average / 3);
    }

    public void clear(){
        this.currentSongId = "";
        this.progressBar.setValue(0);
        this.progressBar.setString("");
        this.scoreLabel.setValue(0);
        this.popularityLabel.setValue(0);
        this.peakLabel.setValue(0);
        this.thumbnail.setUnknown();
    }

    public Library getLibrary() {
        return library;
    }

    /**
     * Updates thumbnail and circle labels
     * @param track current track from playback
     * @return if the current song is becoming unpopular
     */
    public boolean setTrack(Track track){
        if (track == null) throw new DevelopException(this);
        peakLabel.changeToPeak();

        if (track.getAlbum().getImages().length > 0){
            this.thumbnail.setCover(track.getAlbum().getImages()[0].getUrl());
        } else this.thumbnail.setUnknown();

        this.progressBar.setMaximum(track.getDurationMs() / 1000 + 1);
        this.progressBar.setValue(0);

        ArtistDirectory a = library.getArtistByName(track.getArtists()[0].getName());
        SongFile songFile;
        int previousPop = 0;

        if (a == null) songFile = null;
        else {
            songFile = a.getSongById(track.getId());
            previousPop = (int) a.getAveragePopularity();
        }

        //if (songFile != null) previousPop = songFile.getMediumAppearance().getPopularity();

        if (track.getArtists().length > 1){
            this.scoreLabel.setCollab(true);
            this.scoreLabel.setAverage(average / 2);
            this.scoreLabel.setValue((int) Collab.calcScore(track.getArtists(), track.getPopularity()));
        } else {
            this.scoreLabel.setCollab(false);
            this.scoreLabel.setAverage(average / 3);
            this.scoreLabel.setValue(library.getArtistRank(track.getArtists()[0].getName()));
        }

        this.peakLabel.setValue(-1);

        if (songFile != null){
            previousPop = songFile.getMediumAppearance().getPopularity();
            System.out.println(track.getName() + " normal popularity is " + previousPop);
            pendingScore = (track.getPopularity() - previousPop + 1);
            peakLabel.changeToPeak();
            //peakLabel.setAverage(average / 3);
            int average = (int) a.getAveragePeak();
            peakLabel.setOriginalValue(average);
            int peak = songFile.getPeak().getChartPosition();
            //int comp = songFile.getLastAppearance().getChartPosition() * 2 / 3 + 1;
            //int code = songFile.getPeak().getRankingCode();
            //if (peak > track.getPopularity() / 2) peakLabel.setOriginalValue(0);
            //else if (Math.abs(code - lastRankingCode) < 64) peakLabel.setOriginalValue(peak);
            //else peakLabel.setOriginalValue(comp);
            peakLabel.setValue(peak);
            peakLabel.repaint();
        } else {
            peakLabel.setValue(0);
            peakLabel.setReplaceable(true);
            pendingScore = (track.getPopularity() / 16);
        }

        this.popularityLabel.setOriginalValue(previousPop);
        this.popularityLabel.setValue(track.getPopularity());
        timeCount = 0;
        //System.out.println("Session Score: " + sessionScore);

        if (previousPop > 0 && track.getPopularity() < previousPop){
            changeProgressColor(Color.orange);
            return true;
        } else {
            if (previousPop == track.getPopularity()) changeProgressColor(Color.cyan);
            else changeProgressColor(Color.green);
            return false;
        }
    }

    public void setProgress(int value){
        this.progressBar.setValue(value);
    }

    public void setTime(int timeInSeconds){
        this.setProgress(timeInSeconds);
        int seconds = timeInSeconds % 60;
        int minutes = timeInSeconds / 60;
        progressBar.setString(minutes + ":" + decimalFormat.format(seconds));
        if (timeInSeconds > 0 && peakLabel.isMinutes()) peakLabel.updateMinutes(timeInSeconds);

        // Beta 1.05.3
        if (++timeCount == 100){
            sessionScore += pendingScore;
            sessionLabel.setText("Session Score: " + sessionScore);
            timeCount = 0;
            checkScore();
        }
    }

    private void checkScore(){
        if (pendingScore < 0 && sessionScore < 0 && sessionScore % 8 == 0) {
            BlockedItem bi = new BlockedItem(currentSongId);
            bi.setTimesUntilUnlock(4);
            sessionScore = 0;
            blacklist.add(bi);
            IOUtils.saveBlacklist(blacklist);
        }
    }

    public void setCurrentSongId(String currentSongId) {
        this.currentSongId = currentSongId;
    }

    public String getCurrentSongId() {
        return currentSongId == null ? "" : currentSongId;
    }

    public void changeProgressColor(Color color){
        this.progressBar.setForeground(color);
    }

    public AppOptions getAppOptions() {
        return appOptions;
    }

    public PeakLabel getPeakLabel() {
        return peakLabel;
    }

    public int getAverage() {
        return average;
    }

    public void setSessionLabel(JLabel sessionLabel) {
        this.sessionLabel = sessionLabel;
    }

    public int getSessionScore() {
        return sessionScore;
    }

    public void setBlacklist(Set<BlockedItem> blacklist) {
        this.blacklist = blacklist;
    }
}

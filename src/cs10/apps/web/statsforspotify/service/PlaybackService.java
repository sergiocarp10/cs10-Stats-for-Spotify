package cs10.apps.web.statsforspotify.service;

import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Track;
import cs10.apps.desktop.statsforspotify.model.Ranking;
import cs10.apps.desktop.statsforspotify.model.Song;
import cs10.apps.web.statsforspotify.utils.ApiUtils;
import cs10.apps.web.statsforspotify.view.OptionPanes;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class PlaybackService {
    private final ApiUtils apiUtils;
    private final JTable jTable;
    private final JFrame jFrame;
    private Ranking ranking;
    private boolean running, allowWaiting;
    private Thread thread;

    public PlaybackService(ApiUtils apiUtils, JTable jTable, JFrame jFrame) {
        this.apiUtils = apiUtils;
        this.jTable = jTable;
        this.jFrame = jFrame;
    }

    public void restart(){
        thread.interrupt();
        running = false;
        this.run();
    }

    public void run() {
        running = true;
        thread = new Thread(this::getCurrentData);
        thread.start();
    }

    public void setRanking(Ranking ranking) {
        this.ranking = ranking;
    }

    private void getCurrentData() {
        CurrentlyPlaying currentlyPlaying = apiUtils.getCurrentSong();
        if (!currentlyPlaying.getIs_playing()) {
            jFrame.setTitle("No song is playing now");
            running = false;
            return;
        }

        try {
            Track track = (Track) currentlyPlaying.getItem();
            jFrame.setTitle("Now Playing: " + track.getName() + " by " + track.getArtists()[0].getName());
            selectCurrentRow(track.getId());
            Thread.sleep(track.getDurationMs() - currentlyPlaying.getProgress_ms());
            allowWaiting = true;
            getCurrentData();
        } catch (InterruptedException e){
            System.err.println("Playback Service interrupted");
        } catch (Exception e) {
            e.printStackTrace();
            if (allowWaiting){
                allowWaiting = false;
                retryIn15();
            } else {
                jFrame.setTitle("(C) 2020 - Calderón Sergio - Personal Chart History");
                OptionPanes.showPlaybackStopped();
                running = false;
            }
        }
    }

    private void retryIn15(){
        try {
            int secondsToSleep = new Random().nextInt(15) + 15;
            jFrame.setTitle("Retrying in " + secondsToSleep + " seconds...");
            Thread.sleep(secondsToSleep * 1000);
            getCurrentData();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    private void selectCurrentRow(String id){
        Song song = ranking.getSong(id);
        if (song != null){
            System.out.println("Current Song Rank: " + song.getRank());
            int i = song.getRank()-1;
            jTable.getSelectionModel().setSelectionInterval(i,i);
            scrollToCenter(jTable, i, 4);
            //Rectangle cellRect = jTable.getCellRect(i, 0, true);
            //jTable.scrollRectToVisible(cellRect);
            jFrame.repaint();
        } else System.err.println("Current Song is not in ranking");
    }

    public boolean isRunning() {
        return running;
    }

    private void scrollToCenter(JTable table, int rowIndex, int vColIndex) {
        if (!(table.getParent() instanceof JViewport)) {
            return;
        }
        JViewport viewport = (JViewport) table.getParent();
        Rectangle rect = table.getCellRect(rowIndex, vColIndex, true);
        Rectangle viewRect = viewport.getViewRect();
        rect.setLocation(rect.x - viewRect.x, rect.y - viewRect.y);

        int centerX = (viewRect.width - rect.width) / 2;
        int centerY = (viewRect.height - rect.height) / 2;
        if (rect.x < centerX) {
            centerX = -centerX;
        }
        if (rect.y < centerY) {
            centerY = -centerY;
        }
        rect.translate(centerX, centerY);
        viewport.scrollRectToVisible(rect);
    }
}

package cs10.apps.web.statsforspotify.service;

import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.Track;
import cs10.apps.desktop.statsforspotify.model.Ranking;
import cs10.apps.desktop.statsforspotify.model.Song;
import cs10.apps.web.statsforspotify.utils.ApiUtils;
import cs10.apps.web.statsforspotify.view.OptionPanes;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlaybackService {
    private final ApiUtils apiUtils;
    private final JTable jTable;
    private final JFrame jFrame;
    private Ranking ranking;
    private boolean running;
    private final DecimalFormat decimalFormat = new DecimalFormat("#00");

    // Version 3
    private Thread thread;
    private JProgressBar progressBar;
    private int time;

    // Version 4
    private Song lastSelectedSong;
    private int magicNumber;

    public PlaybackService(ApiUtils apiUtils, JTable jTable, JFrame jFrame, JProgressBar progressBar) {
        this.apiUtils = apiUtils;
        this.jTable = jTable;
        this.jFrame = jFrame;
        this.progressBar = progressBar;
    }

    public void run() {
        if (isRunning()) return;
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
            selectCurrentRow(track.getId(), track.getName().charAt(0)-'A'+1);

            time = currentlyPlaying.getProgress_ms() / 1000;
            int maximum = track.getDurationMs() / 1000;
            progressBar.setMaximum(maximum);
            running = true;

            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                progressBar.setValue(time);
                int seconds = time % 60;
                int minutes = time / 60;
                progressBar.setString(minutes + ":" + decimalFormat.format(seconds));
                if (time >= maximum || !running){
                    scheduledExecutorService.shutdown();
                    getCurrentData();
                } else time++;
            }, 0, 1, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            retryIn30();
        }
    }

    private void retryIn30(){
        try {
            jFrame.setTitle("Retrying in " + 30 + " seconds...");
            Thread.sleep(30000);
            getCurrentData();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    private void selectCurrentRow(String id, int firstCharNumber){
        Song song = ranking.getSong(id);
        if (song != null){
            lastSelectedSong = song;
            magicNumber = 0;

            System.out.println("Current Song Rank: " + song.getRank());
            int i = song.getRank()-1;
            jTable.getSelectionModel().setSelectionInterval(i,i);
            scrollToCenter(jTable, i, 4);
            jFrame.repaint();
        } else {
            System.err.println("Current Song is not in ranking");
            if (lastSelectedSong != null){
                magicNumber += firstCharNumber;
                System.out.println("Current magic number: " + magicNumber);
                if (magicNumber > 30){
                    int rankSelected = (int) (lastSelectedSong.getRank() +
                            magicNumber * 0.01 * lastSelectedSong.getPopularity());
                    if (rankSelected <= ranking.size()){
                        System.out.println("I've selected the track #" + rankSelected);
                        lastSelectedSong = ranking.get(rankSelected-1);
                        apiUtils.addToQueue(lastSelectedSong);
                        magicNumber = 0;
                    }
                }
            }
        }
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

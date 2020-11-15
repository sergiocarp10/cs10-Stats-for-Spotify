package cs10.apps.web.statsforspotify.view;

import cs10.apps.desktop.statsforspotify.model.Library;
import cs10.apps.desktop.statsforspotify.model.Ranking;
import cs10.apps.desktop.statsforspotify.model.Song;
import cs10.apps.desktop.statsforspotify.model.Status;
import cs10.apps.desktop.statsforspotify.utils.OldIOUtils;
import cs10.apps.desktop.statsforspotify.view.RankingModel;
import cs10.apps.web.statsforspotify.model.BigRanking;
import cs10.apps.web.statsforspotify.model.TopTerms;
import cs10.apps.web.statsforspotify.service.PlaybackService;
import cs10.apps.web.statsforspotify.utils.ApiUtils;
import cs10.apps.web.statsforspotify.utils.IOUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

public class StatsFrame extends JFrame {
    private final ApiUtils apiUtils;
    private RankingModel model;
    private Ranking ranking;
    private JTable table;
    private String username;
    private PlaybackService playbackService;

    // version 3
    private JProgressBar progressBar;
    private BigRanking bigRanking;
    private Random random;

    public StatsFrame(ApiUtils apiUtils) throws HeadlessException {
        this.apiUtils = apiUtils;
        this.random = new Random();
    }

    public void init() throws Exception {
        username = apiUtils.getUser().getDisplayName();
        setTitle(username + " - Your Spotify Stats");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);

        // Menu Bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem jmiOpen = new JMenuItem("Open...");
        JMenuItem jmiSave = new JMenuItem("Save");
        JMenuItem jmiSaveAs = new JMenuItem("Save As...");
        jmiOpen.addActionListener(e -> System.out.println("Open pressed"));
        jmiSave.addActionListener(e -> System.out.println("Save pressed"));
        jmiSaveAs.addActionListener(e -> System.out.println("Save As pressed"));
        fileMenu.add(jmiOpen);
        fileMenu.add(jmiSave);
        fileMenu.add(jmiSaveAs);
        JMenu helpMenu = new JMenu("Help");
        helpMenu.addActionListener(e -> System.out.println("Help pressed"));
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);

        // Progress Bar
        JPanel buttonsPanel = new JPanel();
        progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        progressBar.setPreferredSize(new Dimension(650,30));
        progressBar.setBorder(new EmptyBorder(8,16,0,50));
        progressBar.setStringPainted(true);
        progressBar.setForeground(Color.GREEN);
        progressBar.setUI(new BasicProgressBarUI() {
            protected Color getSelectionBackground() { return Color.black; }
            protected Color getSelectionForeground() { return Color.black; }
        });

        buttonsPanel.add(progressBar);

        // Ranking Buttons
        JButton buttonShortTerm = new JButton(TopTerms.SHORT.getDescription());
        JButton buttonMediumTerm = new JButton(TopTerms.MEDIUM.getDescription());
        JButton buttonLongTerm = new JButton(TopTerms.LONG.getDescription());
        JButton buttonNowPlaying = new JButton("Show what I'm listening to");
        buttonsPanel.setBorder(new EmptyBorder(8, 16, 0, 16));
        //buttonsPanel.add(buttonShortTerm);
        //buttonsPanel.add(buttonMediumTerm);
        //buttonsPanel.add(buttonLongTerm);
        buttonsPanel.add(buttonNowPlaying);

        // Table
        String[] columnNames = new String[]{
                "Status", "Album", "Rank", "Song Name", "Artists", "Popularity", "Release Date"
        };

        model = new RankingModel(columnNames, 0);
        table = new JTable(model);
        table.setRowHeight(50);
        customizeTexts();

        // Add Components
        getContentPane().add(BorderLayout.NORTH, menuBar);
        getContentPane().add(BorderLayout.CENTER, buttonsPanel);
        getContentPane().add(BorderLayout.SOUTH, new JScrollPane(table));

        // Show all
        playbackService = new PlaybackService(apiUtils, table, this, progressBar);
        setVisible(true);
        //show(TopTerms.SHORT);

        // Version 3
        setResizable(false);
        init3();

        // Set Listeners (buttons will be deleted on Version 3)
        buttonShortTerm.addActionListener(e -> show(TopTerms.SHORT));
        buttonMediumTerm.addActionListener(e -> show(TopTerms.MEDIUM));
        buttonLongTerm.addActionListener(e -> show(TopTerms.LONG));
        buttonNowPlaying.addActionListener(e -> {
            //playbackService.setRanking(ranking);
            playbackService.run();
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                String artistsNames = (String) model.getValueAt(table.getSelectedRow(), 4);
                String[] artists = artistsNames.split(", ");
                String mainName = artists[random.nextInt(artists.length)];
                openArtistWindow(mainName, IOUtils.getScores(mainName));
                //String mainName = artistsNames.split(", ")[0];
                //Artist artist = library.findByName(mainName);
                //if (artist != null) openArtistWindow(artist);

            }
        });
    }

    private void init3(){
        bigRanking = new BigRanking();
        bigRanking.addRankingToCompare(IOUtils.loadPreviousRanking());

        progressBar.setString("Connecting to Spotify...");
        bigRanking.add(apiUtils.getPaging(TopTerms.SHORT));
        progressBar.setValue(40);
        bigRanking.add(apiUtils.getPaging(TopTerms.MEDIUM));
        progressBar.setValue(80);
        bigRanking.add(apiUtils.getPaging(TopTerms.LONG));
        progressBar.setValue(100);

        int i = 0;

        // show on table
        progressBar.setString("Loading ranking...");
        for (Song s : bigRanking){
            progressBar.setValue((++i) * 100 / bigRanking.size());
            model.addRow(toRow(s));
        }

        // update service
        progressBar.setString("");
        playbackService.setRanking(bigRanking);
        playbackService.run();

        // save file
        long prevR = IOUtils.readLastRankingCode()[1];
        if (prevR == 0 || prevR != bigRanking.getCode()){
            IOUtils.saveLastRankingCode(prevR, bigRanking.getCode());
            IOUtils.makeLibraryFiles(bigRanking);
            IOUtils.saveRanking(bigRanking, true);
        } else {
            System.out.println("Nothing to save");
        }

    }

    private void customizeTexts(){
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer();
        cellRenderer.setHorizontalAlignment(JLabel.CENTER);

        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(250);
        table.getColumnModel().getColumn(4).setPreferredWidth(250);

        for (int i=2; i<model.getColumnCount(); i++){
            table.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }
    }

    private void show(TopTerms term) {
        if (ranking != null && ranking.getTitle().equals(term.getDescription())){
            System.out.println("Skipping a new research");
            return;
        }

        setTitle("Please wait...");
        this.ranking = apiUtils.getRanking(term);
        this.ranking.setTitle(term.getDescription());

        // auto save
        IOUtils.saveRanking(ranking, false);

        // load previous
        this.loadChanges(term);

        while (model.getRowCount() > 0){
            model.removeRow(0);
        }

        for (Song s : ranking){
            model.addRow(toRow(s));
        }

        setTitle(username + " - " + term.getDescription());
    }

    private Object[] toRow(Song song){
        return new Object[]{OldIOUtils.getImageIcon(song.getStatus()),
                downloadImage(song.getImageUrl()), song.getRank(),
                song.getName(), song.getArtists(), song.getPopularity(), song.getReleaseDate()};
    }

    private ImageIcon downloadImage(String url){
        try {
            BufferedImage bi = ImageIO.read(new URL(url));
            Image image = bi.getScaledInstance(50, 50, Image.SCALE_SMOOTH);
            return new ImageIcon(image);
        } catch (MalformedURLException e){
            System.err.println("Invalid format: " + url);
            e.printStackTrace();
        } catch (IOException e){
            System.err.println("Error while trying to download from web");
            e.printStackTrace();
        }

        return null;
    }

    private void loadChanges(TopTerms term){
        Ranking previousR = IOUtils.getLastSavedRanking(term);
        if (previousR == null) return;

        for (Song s : ranking){
            Song prevS = previousR.getSong(s.getId());
            if (prevS == null) s.setStatus(Status.NEW);
            else {
                s.setPreviousRank(prevS.getRank());
                s.validateWeb();
            }
        }
    }

    private void openArtistWindow(String artist, float[] scores){
        ArtistFrame artistFrame = new ArtistFrame(artist, scores);
        artistFrame.init();
        artistFrame.setVisible(true);
    }
}

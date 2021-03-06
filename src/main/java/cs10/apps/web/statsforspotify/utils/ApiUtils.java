package cs10.apps.web.statsforspotify.utils;

import com.google.gson.JsonArray;
import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.miscellaneous.Device;
import com.wrapper.spotify.model_objects.specification.*;
import cs10.apps.desktop.statsforspotify.model.Ranking;
import cs10.apps.desktop.statsforspotify.model.Song;
import cs10.apps.web.statsforspotify.app.Private;
import cs10.apps.web.statsforspotify.core.RankingImprover;
import cs10.apps.web.statsforspotify.view.OptionPanes;
import cs10.apps.web.statsforspotify.view.histogram.DailyMixesFrame;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ApiUtils {
    private final SpotifyApi spotifyApi;
    private final Random random;
    private final ArrayList<Track> missedTracks = new ArrayList<>();
    private final RankingImprover rankingImprover = new RankingImprover();
    private final Set<String> enqueuedUris = new HashSet<>();
    private final boolean ready;

    // This URI should equal to the saved URI on the App Dashboard
    private static final URI redirectUri = SpotifyHttpManager.makeUri("http://localhost:8080");
    private static final String SCOPE = "user-top-read " +
            "user-read-currently-playing user-read-playback-state user-read-recently-played " +
            "user-modify-playback-state playlist-read-private playlist-modify-public playlist-modify-private";


    public ApiUtils(){
        spotifyApi = new SpotifyApi.Builder()
                .setClientId(Private.CLIENT_ID)
                .setClientSecret(Private.CLIENT_SECRET_ID)
                .setRedirectUri(redirectUri)
                .build();

        this.ready = authenticate();
        this.random = new Random();
    }

    private boolean authenticate(){
        try {
            ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
            spotifyApi.setAccessToken(credentials.getAccessToken());
            return true;
        } catch (SpotifyWebApiException e){
            Maintenance.writeErrorFile(e, false);
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

        return false;
    }

    public boolean isReady() {
        return ready;
    }

    public void openGrantPermissionPage() throws IOException {
        URI uri = spotifyApi.authorizationCodeUri()
                .scope(SCOPE)
                .build().execute();

        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        }
    }

    public boolean refreshToken(String code) {
        try {
            AuthorizationCodeCredentials credentials = spotifyApi.
                    authorizationCode(code).build().execute();
            System.out.println("These credentials expires in " + credentials.getExpiresIn());
            spotifyApi.setAccessToken(credentials.getAccessToken());
            spotifyApi.setRefreshToken(credentials.getRefreshToken());
            return true;
        } catch (Exception e){
            OptionPanes.showError("ApiUtils - Refresh Token", e);
            Maintenance.writeErrorFile(e, true);
            return false;
        }
    }

    public CurrentlyPlaying getCurrentSong() {
        try {
            return spotifyApi.getUsersCurrentlyPlayingTrack().build().execute();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return null;
        }
    }

    private Track findPopularTrackOfArtist(String artistId){
        try {
            Track[] tracks = spotifyApi.getArtistsTopTracks(artistId, CountryCode.AR).build().execute();
            if (tracks.length > 0) {
                System.out.println(tracks.length + " tracks found in Artist Top Tracks");
                return tracks[random.nextInt(tracks.length)];
            }
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

        return null;
    }

    private void queue(String uri){
        try {
            if (enqueuedUris.contains(uri)) return;
            spotifyApi.addItemToUsersPlaybackQueue(uri).build().execute();
            enqueuedUris.add(uri);
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }
    }

    public List<String> autoQueue(Ranking ranking, Track current, boolean queue){
        Song song1 = ranking.getRandomElement();
        Song song2 = ranking.getRandomElement();
        List<String> selectedIds = new LinkedList<>();

        Track[] tracks1;
        TrackSimplified t2;

        if (!missedTracks.isEmpty()){
            try {
                Track mt = missedTracks.remove(0);
                if (missedTracks.size() % 3 == 2){
                    Track pt = findPopularTrackOfArtist(mt.getArtists()[0].getId());
                    if (pt != null){
                        TrackSimplified at = pickRandomTrackFromAlbum(pt.getAlbum().getId());
                        System.out.println(CommonUtils.toString(at) + " selected from " + pt.getAlbum().getName());
                        selectedIds.add(at.getId());
                        if (queue) queue(at.getUri());
                    }
                } else {
                    System.out.println(CommonUtils.toString(mt) + " selected from Missed Tracks");
                    selectedIds.add(mt.getId());
                    if (queue) queue(mt.getUri());
                }
            } catch (Exception e){
                Maintenance.writeErrorFile(e, true);
            }

            // do not continue
            return selectedIds;
        }

        try {
            Recommendations r = getRecommendations(song1.getId(), song2.getId(),
                    (current == null) ? ranking.getRandomElement().getId() : current.getId());
            t2 = r.getTracks()[random.nextInt(r.getTracks().length)];
            tracks1 = spotifyApi.getArtistsTopTracks(t2.getArtists()[0].getId(), CountryCode.AR).build().execute();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return selectedIds;
        }

        if (tracks1[0].getPopularity() < 70) {
            System.err.println("AU || Bad Recommendation: " + CommonUtils.toString(tracks1[0]));
            System.err.println("AU || Cause: popularity is " + tracks1[0].getPopularity());
            return selectedIds;
        }

        Track t1 = tracks1[random.nextInt(tracks1.length)];
        Song t3 = IOUtils.pickRandomSongFromLibrary();

        ArrayList<String> uris = new ArrayList<>();

        if (IOUtils.existsArtist(t1.getArtists()[0].getName())){
            Maintenance.log("AU || Added from Artist Top Tracks: " + CommonUtils.toString(t1));
            uris.add(t1.getUri());
            selectedIds.add(t1.getId());
            if (!t2.getName().equals(t1.getName())) {
                Maintenance.log("AU || Added from Recommendations: " + CommonUtils.toString(t2));
                selectedIds.add(t2.getId());
                uris.add(t2.getUri());
            }
        } else {
            Maintenance.log("AU || Added from Recommendations: " + CommonUtils.toString(t2));
            uris.add(t2.getUri());
            selectedIds.add(t2.getId());
            if (t3 != null){
                Maintenance.log("AU || Added from Library: " + t3.toStringWithArtist());
                selectedIds.add(t3.getId());
                uris.add("spotify:track:"+t3.getId());
            }
        }

        if (queue) try {
            for (String uri : uris){
                queue(uri);
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e){
            Maintenance.writeErrorFile(e, false);
        }

        return selectedIds;
    }

    private int findMostPopular(Track[] tracks){
        int max = 0, result = 0;
        for (int i=0; i<tracks.length; i++){
            Track track = tracks[i];
            if (track.getPopularity() > max){
                max = track.getPopularity();
                result = i;
            }
        }

        return result;
    }

    public Track[] getUntilPosition(String termKey, int pos){
        int min = Math.min(pos, 49);

        try {
            Track[] tracks1 = spotifyApi.getUsersTopTracks().time_range(termKey).limit(min).build().execute().getItems();
            if (min < pos){
                Track[] tracks2 = spotifyApi.getUsersTopTracks().time_range(termKey).limit(pos-min).offset(min).build().execute().getItems();
                Track[] result = new Track[tracks1.length + tracks2.length];
                System.arraycopy(tracks1, 0, result, 0, tracks1.length);
                System.arraycopy(tracks2, 0, result, tracks1.length, tracks2.length);
                return result;
            } else return tracks1;
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return new Track[0];
        }
    }

    public Track[] getUntilMostPopular(String termKey, int min){
        min = Math.min(min, 49);

        try {
            Track[] tracks1 = spotifyApi.getUsersTopTracks().time_range(termKey).limit(min).build().execute().getItems();
            Track[] tracks2 = spotifyApi.getUsersTopTracks().time_range(termKey).limit(50).offset(min).build().execute().getItems();
            int mostPopularIndex2 = findMostPopular(tracks2);
            Track[] result = new Track[tracks1.length + mostPopularIndex2 + 1];
            System.arraycopy(tracks1, 0, result, 0, tracks1.length);
            System.arraycopy(tracks2, 0, result, tracks1.length, mostPopularIndex2 + 1);

            if (mostPopularIndex2 < 8) {
                System.err.println("Most popular track of " + termKey + " is at #" + (mostPopularIndex2+50));
                rankingImprover.addBlockedTracks(result);
                missedTracks.add(rankingImprover.getTargetTrack());
            } else {
                missedTracks.addAll(Arrays.asList(tracks2).subList(mostPopularIndex2 + 1, tracks2.length));
                Collections.shuffle(missedTracks);
            }
            return result;
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return new Track[0];
        }
    }

    public Track[] getTopTracks(String termKey){

        try {
            return spotifyApi.getUsersTopTracks().time_range(termKey).limit(50).build().execute().getItems();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return new Track[0];
        }
    }

    public Recommendations getRecommendations(String... ids){
        try {
            StringBuilder sb = new StringBuilder(ids[0]);
            for (int i=1; i<ids.length; i++) sb.append(",").append(ids[i]);

            return spotifyApi.getRecommendations()
                    .seed_tracks(sb.toString()).build().execute();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return null;
        }
    }

    public boolean skipCurrentTrack(){
        try {
            spotifyApi.skipUsersPlaybackToNextTrack().build().execute();
            return true;
        } catch (SpotifyWebApiException e){
            Maintenance.writeErrorFile(e, false);
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

        return false;
    }

    /**
     * @param trackId the Spotify ID of the target track
     * @param immediately true if you want to interrupt current playing track
     * @return true if the request was successful
     */
    public boolean playThis(String trackId, boolean immediately){
        try {
            Device[] devices = spotifyApi.getUsersAvailableDevices().build().execute();
            if (devices == null || devices.length == 0) return false;

            JsonArray array = new JsonArray();
            array.add("spotify:track:"+trackId);

            if (immediately){
                Device active = null;

                for (Device device : devices){
                    if (device.getIs_active()){
                        active = device;
                        break;
                    }
                }

                if (active == null) active = devices[0];
                spotifyApi.startResumeUsersPlayback().device_id(active.getId())
                        .uris(array).build().execute();
                return true;
            }

            queue("spotify:track:"+trackId);
            return true;
        } catch (SpotifyWebApiException e){
            Maintenance.writeErrorFile(e, false);
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

         return false;
    }

    public User getUser() throws Exception {
        return spotifyApi.getCurrentUsersProfile().build().execute();
    }

    public PlayHistory[] getRecentTracks(){
        try {
            // This returns only the last 30 played tracks
            return spotifyApi.getCurrentUsersRecentlyPlayedTracks()
                    .limit(50).build().execute().getItems();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return new PlayHistory[0];
        }
    }

    public Track getTrackByID(String id) throws Exception {
        return spotifyApi.getTrack(id).build().execute();
    }

    public Collection<Track> findDailyMix(int number, int minPopularity){
        Collection<Track> result = new HashSet<>();
        if (number < 1 || number > 6) number = random.nextInt(6) + 1;

        try {
            PlaylistSimplified[] ps = spotifyApi.getListOfCurrentUsersPlaylists()
                    .limit(49).build().execute().getItems();

            for (PlaylistSimplified p : ps){
                if (p.getName().equals("Daily Mix " + number)){
                    System.out.println(p.getName() + " was selected for Missed Tracks");
                    Playlist playlist = spotifyApi.getPlaylist(p.getId()).build().execute();
                    for (PlaylistTrack pt : playlist.getTracks().getItems()){
                        Track track = (Track) pt.getTrack();
                        if (track.getPopularity() > minPopularity)
                            result.add(track);
                    }
                    break;
                }
            }
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

        return result;
    }

    public List<Playlist> getDailyMixes(){
        List<Playlist> result = new ArrayList<>(6);

        try {
            PlaylistSimplified[] ps = spotifyApi.getListOfCurrentUsersPlaylists()
                    .limit(49).build().execute().getItems();

            for (PlaylistSimplified p : ps){
                if (p.getName().startsWith("Daily Mix")){
                    Playlist playlist = spotifyApi.getPlaylist(p.getId()).build().execute();
                    result.add(playlist);
                    if (result.size() == 6) break;
                }
            }
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

        return result;
    }

    public Artist getArtist(String id){
        try {
            return spotifyApi.getArtist(id).build().execute();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return null;
        }
    }

    public void analyzeDailyMixes(){
        int dailyMixes = 6, count = 0;
        int[] tracks = new int[dailyMixes];
        int[] sizes = new int[dailyMixes];
        int[] artists = new int[dailyMixes];
        int[] times = new int[dailyMixes];

        try {
            PlaylistSimplified[] ps = spotifyApi.getListOfCurrentUsersPlaylists()
                    .limit(49).build().execute().getItems();
            for (PlaylistSimplified p : ps){
                if (p.getName().startsWith("Daily Mix")){
                    int popularitySum = 0;
                    sizes[count] = p.getTracks().getTotal();
                    Playlist playlist = spotifyApi.getPlaylist(p.getId()).build().execute();
                    for (PlaylistTrack pt : playlist.getTracks().getItems()){
                        Track t = (Track) pt.getTrack();
                        popularitySum += t.getPopularity();
                        int ts = IOUtils.getTimesOnRanking(t.getArtists()[0].getName(), t.getId());
                        if (ts > 0) {tracks[count]++; times[count] += ts;}
                        if (IOUtils.existsArtist(t.getArtists()[0].getName()))
                            artists[count]++;
                    }
                    System.out.println(p.getName() + " average popularity: " +
                            popularitySum / p.getTracks().getTotal());
                    if (++count == dailyMixes) break;
                }
            }
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }

        for (int i=0; i<dailyMixes; i++){
            System.out.println("DAILY MIX " + (i+1));
            System.out.println(tracks[i] + "/" + sizes[i] + " tracks are already in your library");
            int artistsPercentage = sizes[i] > 0 ? artists[i] * 100 / sizes[i] : 0;
            System.out.println(artistsPercentage + "% artists that you already know");
        }

        new DailyMixesFrame(times).init();
    }

    public Album getAlbum(String id){
        try {
            return spotifyApi.getAlbum(id).build().execute();
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return null;
        }
    }

    public void enqueueTwoTracksOfTheSameAlbum(Track track){
        int actualNumber = track.getTrackNumber() - 1;  // 1 -> 0
        if (track.getAlbum().getAlbumType() == AlbumType.SINGLE){
            System.err.println("This track is a single - Unable to enqueue album");
            return;
        }

        try {
            Album album = getAlbum(track.getAlbum().getId());
            int max = album.getTracks().getItems().length;
            if (max < 6) {
                System.err.println("This album has less than 3 songs. That's not enough");
                return;
            }

            int n1, n2;

            do {
                n1 = random.nextInt(max);
                n2 = random.nextInt(max);
            } while (n1 == actualNumber || n2 == actualNumber || n1 == n2);

            playThis(album.getTracks().getItems()[n1].getId(), false);
            playThis(album.getTracks().getItems()[n2].getId(), false);
            System.out.println("Two tracks from " + album.getName() + " enqueued!");
        } catch (Exception e) {
            Maintenance.writeErrorFile(e, true);
            e.printStackTrace();
        }
    }

    public void createPlaylist(String name, Set<String> trackIDs){
        System.out.println("Creating playlist with " + trackIDs.size() + " tracks");

        try {
            Playlist p = spotifyApi.createPlaylist(getUser().getId(), name).public_(false).build().execute();
            String[] uris = new String[trackIDs.size()];
            int i = 0;
            for (String id : trackIDs) uris[i++] = "spotify:track:" + id;
            spotifyApi.addItemsToPlaylist(p.getId(), uris).build().execute();
            OptionPanes.message("Playlist " + name + " created successfully");
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
        }
    }

    public TrackSimplified pickRandomTrackFromAlbum(String albumId){
        try {
            Album a = spotifyApi.getAlbum(albumId).build().execute();
            return a.getTracks().getItems()[random.nextInt(a.getTracks().getTotal())];
        } catch (Exception e){
            Maintenance.writeErrorFile(e, true);
            return null;
        }
    }

    public RankingImprover getRankingImprover() {
        return rankingImprover;
    }
}

package cs10.apps.web.statsforspotify.model;

import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.Track;
import cs10.apps.desktop.statsforspotify.model.Ranking;
import cs10.apps.desktop.statsforspotify.model.Song;
import cs10.apps.desktop.statsforspotify.model.Status;
import cs10.apps.web.statsforspotify.utils.CommonUtils;

public class BigRanking extends Ranking {
    private static final int TOP_INDEX = 1;

    public void add(Paging<Track> paging){
        if (super.isEmpty()) addWithoutCheckingRepeats(paging);
        else addCheckingRepeats(paging);
    }

    private void addWithoutCheckingRepeats(Paging<Track> paging){
        for (Track track : paging.getItems()){
            add(track);
        }
    }

    private void addCheckingRepeats(Paging<Track> paging){
        int limit = super.size();

        for (Track track : paging.getItems()){
            if (!alreadyAdded(track.getId(), limit)){
                add(track);
            }
        }
    }

    private boolean alreadyAdded(String id, int limit){
        int checked = 0;

        for (Song s : this){
            if (s.getId().equals(id)) return true;
            if (++checked == limit) return false;
        }

        return false;
    }

    private void add(Track track){
        Song song = new Song();
        song.setId(track.getId());
        song.setRank(size()+TOP_INDEX);
        song.setName(track.getName());
        song.setArtists(CommonUtils.combineArtists(track.getArtists()));
        song.setImageUrl(track.getAlbum().getImages()[0].getUrl());
        song.setPopularity(track.getPopularity());
        song.setStatus(Status.NOTHING);
        super.add(song);
    }

    @Override
    public long getCode() {
        long sum = 0;

        for (Song s : this)
            sum += s.getPopularity();

        return sum;
    }
}

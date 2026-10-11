package com.naviglass.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class AlbumPlaybackPolicyTest {
    @Test public void repeatMatchesOriginalOrder() { assertEquals(2,AlbumPlaybackPolicy.nextRepeatMode(0));assertEquals(1,AlbumPlaybackPolicy.nextRepeatMode(2));assertEquals(0,AlbumPlaybackPolicy.nextRepeatMode(1)); }
    @Test public void automaticContinuesOnlyRepeatOff() { assertTrue(AlbumPlaybackPolicy.canAdvance(true,false,0));assertFalse(AlbumPlaybackPolicy.canAdvance(true,false,1));assertFalse(AlbumPlaybackPolicy.canAdvance(true,false,2)); }
    @Test public void manualNextDoesNotRepeatCurrentSong() { assertTrue(AlbumPlaybackPolicy.canAdvance(false,false,1));assertTrue(AlbumPlaybackPolicy.canAdvance(false,false,0));assertFalse(AlbumPlaybackPolicy.canAdvance(false,false,2)); }
    @Test public void shuffleNeverSilentlyChangesAlbumQueue() { for(int mode=0;mode<=2;mode++){assertFalse(AlbumPlaybackPolicy.canAdvance(true,true,mode));assertFalse(AlbumPlaybackPolicy.canAdvance(false,true,mode));} }
    @Test public void previousStartsAtLastSongAndNextAtFirst() { assertEquals(11,AlbumPlaybackPolicy.trackIndex(-1,12));assertEquals(0,AlbumPlaybackPolicy.trackIndex(1,12));assertEquals(0,AlbumPlaybackPolicy.trackIndex(-1,0)); }
}

package com.naviglass.android;
/** Pure queue decisions, shared with native controls; Media3 modes: off=0, song=1, all=2. */
final class AlbumPlaybackPolicy {
    static int nextRepeatMode(int mode) { return mode == 0 ? 2 : mode == 2 ? 1 : 0; }
    static boolean canAdvance(boolean automatic, boolean shuffle, int repeatMode) {
        return !shuffle && (automatic ? repeatMode == 0 : repeatMode != 2);
    }
    static int trackIndex(int direction, int count) { return direction < 0 ? Math.max(0, count - 1) : 0; }
}

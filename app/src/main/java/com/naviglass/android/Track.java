package com.naviglass.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONObject;

final class Track {
    String id, uri, source, albumId, title, artist, album, year, mime;
    int number, disc;
    long duration, size, modified;

    ContentValues values(long generation) {
        ContentValues v = new ContentValues();
        v.put("id",id); v.put("uri",uri); v.put("source",source); v.put("album_id",albumId);
        v.put("title",title); v.put("artist",artist); v.put("album",album); v.put("year",year); v.put("mime",mime);
        v.put("number",number); v.put("disc",disc); v.put("duration",duration); v.put("size",size);
        v.put("modified",modified); v.put("generation",generation);
        return v;
    }
    static Track read(Cursor c) {
        Track t = new Track();
        t.id=text(c,"id"); t.uri=text(c,"uri"); t.source=text(c,"source"); t.albumId=text(c,"album_id");
        t.title=text(c,"title"); t.artist=text(c,"artist"); t.album=text(c,"album");
        t.year=text(c,"year"); t.mime=text(c,"mime"); t.number=(int)value(c,"number"); t.disc=(int)value(c,"disc");
        t.duration=value(c,"duration"); t.size=value(c,"size"); t.modified=value(c,"modified");
        return t;
    }
    static String text(Cursor c,String key) { String s=c.getString(c.getColumnIndexOrThrow(key)); return s==null?"":s; }
    static long value(Cursor c,String key) { return c.getLong(c.getColumnIndexOrThrow(key)); }
    JSONObject json(boolean favourite) {
        JSONObject j = new JSONObject();
        try { j.put("id",id).put("albumId",albumId).put("title",title).put("artist",artist).put("album",album)
            .put("year",year).put("number",number).put("disc",disc).put("duration",duration).put("favourite",favourite).put("mime",mime).put("size",size).put("sourceLabel",source!=null&&source.startsWith("navidrome:")?"Navidrome":"Local library"); }
        catch(Exception ignored) {}
        return j;
    }
    MediaItem mediaItem() {
        return new MediaItem.Builder().setMediaId(id).setUri(Uri.parse(uri))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
                .build()).build();
    }
}

package com.naviglass.android;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

final class Library extends SQLiteOpenHelper {
    private static Library instance;
    private final Context context;
    static synchronized Library get(Context c) {
        if(instance==null) instance=new Library(c.getApplicationContext());
        return instance;
    }
    private Library(Context c) { super(c,"library.db",null,1); context=c; setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,uri TEXT NOT NULL,source TEXT,album_id TEXT,title TEXT,artist TEXT,album TEXT,year TEXT,mime TEXT,number INTEGER,disc INTEGER,duration INTEGER,size INTEGER,modified INTEGER,generation INTEGER)");
        db.execSQL("CREATE INDEX album_tracks ON tracks(album_id,disc,number,title)");
        db.execSQL("CREATE INDEX track_source ON tracks(source,generation)");
        db.execSQL("CREATE TABLE favourites(track_id TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE playlist_tracks(playlist_id TEXT,track_id TEXT,position INTEGER,PRIMARY KEY(playlist_id,track_id))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("Unsupported library schema"); }
    Track track(String id) {
        try(Cursor c=getReadableDatabase().query("tracks",null,"id=?",new String[]{id},null,null,null)) {
            return c.moveToFirst()?Track.read(c):null;
        }
    }
    boolean favourite(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM favourites WHERE track_id=?",new String[]{id})) {return c.moveToFirst();}
    }
    boolean toggleFavourite(String id) {
        if(track(id)==null)throw new IllegalArgumentException("Song is not in the selected library");
        SQLiteDatabase db=getWritableDatabase();
        if(favourite(id)){db.delete("favourites","track_id=?",new String[]{id});return false;}
        ContentValues v=new ContentValues();v.put("track_id",id);db.insertOrThrow("favourites",null,v);return true;
    }
    JSONArray albums() {
        JSONArray result=new JSONArray();
        String query="SELECT album_id,MIN(album) album,MIN(artist) artist,MIN(year) year,COUNT(*) count,MIN(id) cover_id FROM tracks GROUP BY album_id ORDER BY album COLLATE NOCASE,year";
        try(Cursor c=getReadableDatabase().rawQuery(query,null)) {
            while(c.moveToNext()) {JSONObject j=new JSONObject();try {
                j.put("id",Track.text(c,"album_id")).put("title",Track.text(c,"album")).put("artist",Track.text(c,"artist"))
                    .put("year",Track.text(c,"year")).put("count",Track.value(c,"count")).put("coverId",Track.text(c,"cover_id"));result.put(j);
            }catch(Exception ignored){}}
        }
        return result;
    }
    JSONArray tracks(String mode,String value) {
        String query="SELECT t.*,CASE WHEN f.track_id IS NULL THEN 0 ELSE 1 END fav FROM tracks t LEFT JOIN favourites f ON f.track_id=t.id ";
        String[] args;
        if(mode.equals("album")) {query+="WHERE t.album_id=? ORDER BY t.disc,t.number,t.title COLLATE NOCASE";args=new String[]{value};}
        else if(mode.equals("favourites")) {query+="WHERE f.track_id IS NOT NULL ORDER BY t.album COLLATE NOCASE,t.disc,t.number";args=new String[0];}
        else if(mode.equals("playlist")) {query+="JOIN playlist_tracks p ON p.track_id=t.id WHERE p.playlist_id=? ORDER BY p.position";args=new String[]{value};}
        else {query+="WHERE t.title LIKE ? ESCAPE '\\' OR t.artist LIKE ? ESCAPE '\\' OR t.album LIKE ? ESCAPE '\\' ORDER BY t.album,t.number LIMIT 200";
            String term="%"+value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";args=new String[]{term,term,term};}
        JSONArray result=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery(query,args)) {while(c.moveToNext())result.put(Track.read(c).json(Track.value(c,"fav")!=0));}
        return result;
    }
    JSONArray playlists() {
        JSONArray result=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,p.name,COUNT(t.id) count FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id=p.id LEFT JOIN tracks t ON t.id=pt.track_id GROUP BY p.id ORDER BY p.name",null)) {
            while(c.moveToNext())try {result.put(new JSONObject().put("id",Track.text(c,"id")).put("name",Track.text(c,"name")).put("count",Track.value(c,"count")));}catch(Exception ignored){}
        }return result;
    }
    String createPlaylist(String name) {
        String clean=name.trim();if(clean.isEmpty()||clean.length()>100)throw new IllegalArgumentException("Playlist name must be 1–100 characters");
        String id=java.util.UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("name",clean);
        getWritableDatabase().insertOrThrow("playlists",null,v);return id;
    }
    void addToPlaylist(String playlist,String track) {
        if(track(track)==null)throw new IllegalArgumentException("Unknown song");
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor check=db.rawQuery("SELECT 1 FROM playlists WHERE id=?",new String[]{playlist})) {if(!check.moveToFirst())throw new IllegalArgumentException("Unknown playlist");}
        long position=0;try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?",new String[]{playlist})) {if(c.moveToFirst())position=c.getLong(0);}
        ContentValues v=new ContentValues();v.put("playlist_id",playlist);v.put("track_id",track);v.put("position",position);
        db.insertWithOnConflict("playlist_tracks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    static String key(String value) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b));return s.substring(0,32);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    File artwork(String id) {
        Track t=track(id);if(t==null)return null;
        File folder=new File(context.getCacheDir(),"covers");folder.mkdirs();
        File output=new File(folder,key(id+":"+t.modified+":"+t.size)+".jpg");
        if(output.isFile()) {output.setLastModified(System.currentTimeMillis());return output;}
        try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()) {
            retriever.setDataSource(context,Uri.parse(t.uri));byte[] image=retriever.getEmbeddedPicture();
            if(image==null||image.length>32*1024*1024)return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(image,0,image.length,options);
            if(options.outWidth<=0||options.outHeight<=0)return null;
            options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;
            Bitmap original=BitmapFactory.decodeByteArray(image,0,image.length,options);if(original==null)return null;
            float scale=Math.min(1f,512f/Math.max(original.getWidth(),original.getHeight()));
            Bitmap small=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),Math.max(1,Math.round(original.getHeight()*scale)),true);
            File temporary=File.createTempFile("cover-",".tmp",folder);
            try(FileOutputStream stream=new FileOutputStream(temporary)) {small.compress(Bitmap.CompressFormat.JPEG,90,stream);}
            if(small!=original)small.recycle();original.recycle();
            if(!temporary.renameTo(output)){temporary.delete();return null;}
            trimCache(folder,output);return output;
        }catch(Exception|OutOfMemoryError e){return null;}
    }
    private void trimCache(File folder,File keep) {
        File[] files=folder.listFiles();if(files==null||files.length%32!=0)return;
        long size=0;for(File f:files)size+=f.length();if(size<=256L*1024*1024)return;
        java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));
        for(File f:files)if(size>192L*1024*1024&&!f.equals(keep)){long bytes=f.length();if(f.delete())size-=bytes;}
    }
}

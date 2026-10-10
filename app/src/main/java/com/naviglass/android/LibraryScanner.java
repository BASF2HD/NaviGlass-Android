package com.naviglass.android;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class LibraryScanner {
    interface Progress {void update(int checked,int indexed,int skipped,String label);}
    final Context context;final Library library;final AtomicBoolean cancelled;
    LibraryScanner(Context c,Library l,AtomicBoolean flag){context=c;library=l;cancelled=flag;}
    private void check(){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Scan cancelled; previous library kept");}
    void device(Progress progress) {
        String source="device";long generation=System.currentTimeMillis();SQLiteDatabase db=library.getWritableDatabase();int count=0;
        Uri collection=Build.VERSION.SDK_INT>=29?MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL):MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ID,MediaStore.Audio.Media.YEAR,MediaStore.Audio.Media.TRACK,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.DATE_MODIFIED,MediaStore.Audio.Media.MIME_TYPE};
        String where=Build.VERSION.SDK_INT>=29?MediaStore.Audio.Media.IS_PENDING+"=0":null;
        db.beginTransaction();
        try(Cursor cursor=context.getContentResolver().query(collection,projection,where,null,null)) {
            if(cursor==null)throw new IllegalStateException("Android did not return its music index");
            while(cursor.moveToNext()) {check();Track t=new Track();
                long mediaId=Track.value(cursor,MediaStore.Audio.Media._ID);t.id="ms_"+mediaId;t.uri=ContentUris.withAppendedId(collection,mediaId).toString();t.source=source;
                t.title=clean(Track.text(cursor,MediaStore.Audio.Media.TITLE),"Untitled");t.artist=clean(Track.text(cursor,MediaStore.Audio.Media.ARTIST),"Unknown artist");
                t.album=clean(Track.text(cursor,MediaStore.Audio.Media.ALBUM),"Unknown album");t.year=clean(Track.text(cursor,MediaStore.Audio.Media.YEAR),"");
                t.albumId=Library.key(source+":"+Track.value(cursor,MediaStore.Audio.Media.ALBUM_ID));
                int nr=(int)Track.value(cursor,MediaStore.Audio.Media.TRACK);t.number=nr%1000;t.disc=nr/1000;
                t.duration=Track.value(cursor,MediaStore.Audio.Media.DURATION);t.size=Track.value(cursor,MediaStore.Audio.Media.SIZE);t.modified=Track.value(cursor,MediaStore.Audio.Media.DATE_MODIFIED);
                t.mime=Track.text(cursor,MediaStore.Audio.Media.MIME_TYPE);
                db.insertWithOnConflict("tracks",null,t.values(generation),SQLiteDatabase.CONFLICT_REPLACE);
                count++;if(count%100==0)progress.update(count,count,0,"Android music library");
            }check();finish(db,source,generation);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        progress.update(count,count,0,"Android music library");
    }
    void folder(Uri tree,Progress progress) {
        String source="folder_"+Library.key(tree.toString());long generation=System.currentTimeMillis();
        SQLiteDatabase db=library.getWritableDatabase();ArrayDeque<String> queue=new ArrayDeque<>();Set<String> visited=new HashSet<>();
        queue.add(DocumentsContract.getTreeDocumentId(tree));int checked=0,indexed=0,skipped=0;
        String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        db.beginTransaction();
        try {
            while(!queue.isEmpty()){check();String parent=queue.remove();if(!visited.add(parent))continue;
                if(visited.size()>50000)throw new IllegalStateException("Folder tree is too large; choose a smaller music folder");
                Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);
                try(Cursor c=context.getContentResolver().query(children,projection,null,null,null)) {
                    if(c==null)throw new IllegalStateException("Selected folder is unavailable");
                    while(c.moveToNext()){check();checked++;
                        String document=Track.text(c,DocumentsContract.Document.COLUMN_DOCUMENT_ID),name=Track.text(c,DocumentsContract.Document.COLUMN_DISPLAY_NAME),mime=Track.text(c,DocumentsContract.Document.COLUMN_MIME_TYPE);
                        if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){queue.add(document);continue;}
                        if(!supported(name))continue;
                        if(indexed>=100000)throw new IllegalStateException("More than 100,000 songs; choose a smaller folder");
                        Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,document);String id="saf_"+Library.key(uri.toString());
                        long size=Track.value(c,DocumentsContract.Document.COLUMN_SIZE),modified=Track.value(c,DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                        Track t=library.track(id);
                        if(t==null||modified<=0||t.modified!=modified||t.size!=size){
                            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
                                r.setDataSource(context,uri);t=new Track();t.id=id;t.uri=uri.toString();t.source=source;t.size=size;t.modified=modified;t.mime=mime;
                                t.title=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),name.replaceFirst("\\.[^.]+$",""));
                                t.artist=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),"Unknown artist");
                                t.album=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),parent.substring(parent.lastIndexOf('/')+1));
                                t.year=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),"");
                                t.number=number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));t.disc=number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));
                                t.duration=longNumber(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                                t.albumId=Library.key(source+":"+parent+":"+t.album);
                            }catch(Exception e){skipped++;continue;}
                        }
                        db.insertWithOnConflict("tracks",null,t.values(generation),SQLiteDatabase.CONFLICT_REPLACE);indexed++;
                        if(indexed%20==0)progress.update(checked,indexed,skipped,name);
                    }
                }
            }check();finish(db,source,generation);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        progress.update(checked,indexed,skipped,"Folder scan complete");
    }
    private static void finish(SQLiteDatabase db,String source,long generation){
        db.delete("tracks","source=? AND generation<>?",new String[]{source,String.valueOf(generation)});
        db.execSQL("DELETE FROM favourites WHERE track_id NOT IN (SELECT id FROM tracks)");
        db.execSQL("DELETE FROM playlist_tracks WHERE track_id NOT IN (SELECT id FROM tracks)");
    }
    static boolean supported(String name){return name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp3|flac|wav|wave|m4a|m4b|aac|ogg|oga|opus)$");}
    static String clean(String text,String fallback){return text==null||text.trim().isEmpty()||text.equals("<unknown>")?fallback:text.trim();}
    static int number(String s){try{return Integer.parseInt(s.split("/")[0]);}catch(Exception e){return 0;}}
    static long longNumber(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
}

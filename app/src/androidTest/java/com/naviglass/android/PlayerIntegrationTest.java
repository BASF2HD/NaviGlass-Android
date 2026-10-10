package com.naviglass.android;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerIntegrationTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Test public void localIndexPlaybackSeekBackgroundAndPersistence()throws Exception{
        ContentValues values=new ContentValues();values.put(MediaStore.Audio.Media.DISPLAY_NAME,"naviglass-synthetic-test.mp3");
        values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mpeg");values.put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/NaviGlass_Test");values.put(MediaStore.Audio.Media.IS_PENDING,1);
        Uri uri=context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
        try{
            try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("synthetic.mp3");OutputStream output=context.getContentResolver().openOutputStream(uri)){input.transferTo(output);}
            values.clear();values.put(MediaStore.Audio.Media.IS_PENDING,0);context.getContentResolver().update(uri,values,null,null);
            Library library=Library.get(context);new LibraryScanner(context,library,new AtomicBoolean()).device((checked,indexed,skipped,label)->{});
            JSONArray albums=library.albums();assertTrue(albums.length()>0);JSONArray songs=library.tracks("search","First Light");assertTrue(songs.length()>0);
            String id=songs.getJSONObject(0).getString("id");Track t=library.track(id);assertTrue(t.duration>15000);assertNotNull(library.artwork(id));
            assertTrue(library.toggleFavourite(id));assertTrue(library.favourite(id));String playlist=library.createPlaylist("Synthetic test playlist");library.addToPlaylist(playlist,id);assertEquals(1,library.tracks("playlist",playlist).length());
            final MediaController[] connected=new MediaController[1];final com.google.common.util.concurrent.ListenableFuture<MediaController>[] future=new com.google.common.util.concurrent.ListenableFuture[1];
            main(()->future[0]=new MediaController.Builder(context,new SessionToken(context,new android.content.ComponentName(context,PlaybackService.class))).buildAsync());
            MediaController controller=future[0].get(20,java.util.concurrent.TimeUnit.SECONDS);connected[0]=controller;
            try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
                Thread.sleep(1500);
                assertEquals("true",javascript(activity,"Boolean(window.nativeBridge && window.naviglassDebug && window.naviglassDebug.albums > 0)"));
                javascript(activity,"document.querySelector('#songs').click(); true");Thread.sleep(400);
                assertEquals("true",javascript(activity,"document.querySelectorAll('.track').length > 0"));
                javascript(activity,"window.closePanel(); true");
                main(()->{controller.setMediaItems(Arrays.asList(new MediaItem.Builder().setMediaId(id).build(),new MediaItem.Builder().setMediaId(id).build()),0,0);controller.prepare();controller.play();});
                awaitPlaying(controller);Thread.sleep(1200);
                final long[] positions=new long[2];main(()->positions[0]=controller.getCurrentPosition());assertTrue(positions[0]>200);
                main(()->controller.seekTo(4000));Thread.sleep(500);main(()->positions[1]=controller.getCurrentPosition());assertTrue(positions[1]>=4000);
                activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(1200);
                main(()->{assertTrue(controller.isPlaying());positions[0]=controller.getCurrentPosition();});assertTrue(positions[0]>positions[1]);
                main(()->{controller.seekToNextMediaItem();controller.setShuffleModeEnabled(true);controller.setRepeatMode(Player.REPEAT_MODE_ALL);});Thread.sleep(500);
                main(()->{assertEquals(1,controller.getCurrentMediaItemIndex());assertTrue(controller.getShuffleModeEnabled());assertEquals(Player.REPEAT_MODE_ALL,controller.getRepeatMode());controller.pause();});
                Thread.sleep(250);assertTrue(context.getSharedPreferences("playback",Context.MODE_PRIVATE).getString("queue","[]").contains(id));
                assertTrue(library.favourite(id));assertEquals(1,library.tracks("playlist",playlist).length());
            }finally{main(controller::release);context.stopService(new Intent(context,PlaybackService.class));}
            AtomicBoolean cancelled=new AtomicBoolean(true);try{new LibraryScanner(context,library,cancelled).device((a,b,c,d)->{});fail("Cancelled scan must fail");}catch(java.util.concurrent.CancellationException expected){}
            assertNotNull(library.track(id));assertNotNull(library.track(id).mediaItem().localConfiguration);
        }finally{context.getContentResolver().delete(uri,null,null);}
    }
    private void awaitPlaying(MediaController controller)throws Exception{for(int i=0;i<100;i++){final boolean[] ready={false};main(()->ready[0]=controller.isPlaying());if(ready[0])return;Thread.sleep(100);}fail("Native audio did not start");}
    private String javascript(ActivityScenario<MainActivity> activity,String script)throws Exception{
        java.util.concurrent.CountDownLatch complete=new java.util.concurrent.CountDownLatch(1);final String[] result={""};
        activity.onActivity(a->{android.view.ViewGroup content=a.findViewById(android.R.id.content);android.view.ViewGroup root=(android.view.ViewGroup)content.getChildAt(0);
            ((android.webkit.WebView)root.getChildAt(0)).evaluateJavascript(script,value->{result[0]=value;complete.countDown();});});
        assertTrue("Native WebView did not reply",complete.await(10,java.util.concurrent.TimeUnit.SECONDS));return result[0];
    }
}

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
            assertTrue(library.tracks("all","0").length()>0);assertEquals(0,library.tracks("all","1000000").length());
            assertEquals(4,RadioDirectory.stations(context).length());assertNull(RadioDirectory.item(context,"https://untrusted.invalid/stream"));
            assertTrue(library.toggleFavourite(id));assertTrue(library.favourite(id));String playlist=library.createPlaylist("Synthetic test playlist");library.addToPlaylist(playlist,id);assertEquals(1,library.tracks("playlist",playlist).length());
            final MediaController[] connected=new MediaController[1];final com.google.common.util.concurrent.ListenableFuture<MediaController>[] future=new com.google.common.util.concurrent.ListenableFuture[1];
            main(()->future[0]=new MediaController.Builder(context,new SessionToken(context,new android.content.ComponentName(context,PlaybackService.class))).buildAsync());
            MediaController controller=future[0].get(20,java.util.concurrent.TimeUnit.SECONDS);connected[0]=controller;
            try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
                Thread.sleep(1500);
                assertEquals("true",javascript(activity,"Boolean(window.nativeBridge && window.naviglassDebug && window.naviglassDebug.albums > 0)"));
                assertEquals("6",javascript(activity,"document.querySelectorAll('#mainNav button').length"));
                javascript(activity,"document.querySelector('[data-view=songs]').click();true");Thread.sleep(400);
                assertEquals("true",javascript(activity,"!document.querySelector('#content').hidden && Array.from(document.querySelectorAll('#content button')).some(b=>b.textContent.includes('All Songs'))"));
                javascript(activity,"Array.from(document.querySelectorAll('#content button')).find(b=>b.textContent.startsWith('All Songs')).click();true");Thread.sleep(700);
                assertEquals("true",javascript(activity,"naviglassDebug.browseMode==='songs' && naviglassDebug.entry.track && document.querySelector('#content').hidden && !document.querySelector('#scene').hidden"));
                javascript(activity,"document.querySelector('[data-view=albums]').click();true");Thread.sleep(400);
                javascript(activity,"Array.from(document.querySelectorAll('#content button')).find(b=>b.textContent.startsWith('All Albums')).click();true");Thread.sleep(400);
                javascript(activity,"document.querySelector('#songs').click(); true");Thread.sleep(400);
                assertEquals("true",javascript(activity,"document.querySelectorAll('.track').length > 0"));
                assertEquals("true",javascript(activity,"!document.querySelector('#drawerFavourite').hidden && !Array.from(document.querySelectorAll('#panelBody button')).some(b=>b.textContent==='Play all')"));
                String previousStar=javascript(activity,"document.querySelector('#drawerFavourite').getAttribute('aria-pressed')");javascript(activity,"document.querySelector('#drawerFavourite').click();true");Thread.sleep(400);String savedStar=javascript(activity,"document.querySelector('#drawerFavourite').getAttribute('aria-pressed')");assertNotEquals(previousStar,savedStar);
                javascript(activity,"window.closePanel();document.querySelector('#songs').click();true");Thread.sleep(400);assertEquals(savedStar,javascript(activity,"document.querySelector('#drawerFavourite').getAttribute('aria-pressed')"));
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
                activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);Thread.sleep(500);
                javascript(activity,"document.querySelector('[data-view=more]').click();true");Thread.sleep(400);
                javascript(activity,"Array.from(document.querySelectorAll('#content button')).find(b=>b.textContent.startsWith('Internet radio browser')).click();true");Thread.sleep(400);
                assertEquals("4",javascript(activity,"document.querySelectorAll('.station').length"));
                javascript(activity,"document.querySelector('[aria-label=\"Station name\"]').value='BBC';document.querySelector('[aria-label=\"Station name\"]').dispatchEvent(new Event('input',{bubbles:true}));true");
                boolean searched=false;for(int wait=0;wait<200;wait++){Thread.sleep(100);if(javascript(activity,"Array.from(document.querySelectorAll('#content .station strong')).some(s=>/bbc/i.test(s.textContent))").equals("true")){searched=true;break;}}
                assertTrue("Real radio search must display BBC results in the native UI",searched);
                JSONArray bbc=RadioDirectory.search(context,new org.json.JSONObject().put("name","BBC"));boolean hls=false;for(int i=0;i<bbc.length();i++)hls|=bbc.getJSONObject(i).optBoolean("hls");assertTrue("HLS stations must no longer be filtered out",hls);
                javascript(activity,"Array.from(document.querySelectorAll('#content button')).find(b=>b.textContent==='Starter stations').click();true");Thread.sleep(300);
                JSONArray found=RadioDirectory.search(context,new org.json.JSONObject().put("name","Groove Salad"));assertTrue("Radio browser should return verified HTTPS stations",found.length()>0);for(int i=0;i<found.length();i++){org.json.JSONObject station=found.getJSONObject(i);assertTrue(station.getString("url").startsWith("https://"));assertNotNull(RadioDirectory.item(context,station.getString("id")));}
                javascript(activity,"document.querySelector('.station button').click();true");awaitPlaying(controller);Thread.sleep(700);
                main(()->{assertEquals("radio:groovesalad",controller.getCurrentMediaItem().mediaId);assertFalse(controller.getShuffleModeEnabled());assertEquals(Player.REPEAT_MODE_OFF,controller.getRepeatMode());});
                assertEquals("true",javascript(activity,"document.querySelector('#seek').disabled && document.querySelector('#shuffle').disabled && document.querySelector('#nowFavourite').disabled && document.querySelector('#duration').textContent === 'LIVE'"));
                activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(1000);main(()->assertTrue(controller.isPlaying()));
                activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);Thread.sleep(500);
                javascript(activity,"document.querySelector('#next').click();true");awaitPlaying(controller);Thread.sleep(500);
                main(()->assertEquals("radio:dronezone",controller.getCurrentMediaItem().mediaId));
                javascript(activity,"document.querySelector('#play').click();true");Thread.sleep(500);main(()->assertFalse(controller.getPlayWhenReady()));
            }finally{main(controller::release);context.stopService(new Intent(context,PlaybackService.class));}
            AtomicBoolean cancelled=new AtomicBoolean(true);try{new LibraryScanner(context,library,cancelled).device((a,b,c,d)->{});fail("Cancelled scan must fail");}catch(java.util.concurrent.CancellationException expected){}
            assertNotNull(library.track(id));assertNotNull(library.track(id).mediaItem().localConfiguration);
        }finally{context.getContentResolver().delete(uri,null,null);}
    }
    private void awaitPlaying(MediaController controller)throws Exception{for(int i=0;i<300;i++){final boolean[] ready={false};main(()->ready[0]=controller.isPlaying());if(ready[0])return;Thread.sleep(100);}fail("Native audio did not start");}
    private String javascript(ActivityScenario<MainActivity> activity,String script)throws Exception{
        java.util.concurrent.CountDownLatch complete=new java.util.concurrent.CountDownLatch(1);final String[] result={""};
        activity.onActivity(a->{android.view.ViewGroup content=a.findViewById(android.R.id.content);android.view.ViewGroup root=(android.view.ViewGroup)content.getChildAt(0);
            ((android.webkit.WebView)root.getChildAt(0)).evaluateJavascript(script,value->{result[0]=value;complete.countDown();});});
        assertTrue("Native WebView did not reply",complete.await(10,java.util.concurrent.TimeUnit.SECONDS));return result[0];
    }
}

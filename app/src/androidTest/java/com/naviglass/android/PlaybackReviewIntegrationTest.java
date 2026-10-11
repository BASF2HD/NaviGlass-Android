package com.naviglass.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Synthetic local audio only; run on the isolated development emulator. */
@RunWith(AndroidJUnit4.class)
public class PlaybackReviewIntegrationTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 @Test public void nativeModeIconsAndAdjacentAlbumPlayback()throws Exception{
  File audio=File.createTempFile("naviglass-playback-fixture-",".mp3",context.getCacheDir());
  File tone=File.createTempFile("naviglass-second-album-",".wav",context.getCacheDir());writeTone(tone);
  Library library=Library.get(context);MediaController controller=null;
  try{
   try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("synthetic.mp3");FileOutputStream out=new FileOutputStream(audio)){in.transferTo(out);}
   NetworkSources.local(context);ArrayList<MediaItem> first=new ArrayList<>();
   for(int i=0;i<4;i++){Track t=new Track();t.id="playback-review-"+i;t.source="playback-review-fixture";t.uri=Uri.fromFile(i<2?audio:tone).toString();t.albumId=i<2?"playback-review-album-a":"playback-review-album-b";t.album=i<2?"Playback Review A":"Playback Review B";t.title="Fixture Song "+i;t.artist="Synthetic Ensemble";t.year="2026";t.mime=i<2?"audio/mpeg":"audio/wav";t.number=i%2+1;t.duration=20000;library.getWritableDatabase().insertWithOnConflict("tracks",null,t.values(0),android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);if(i<2)first.add(t.mediaItem());}
   final int expectedAlbums=library.albums().length();
   context.getSharedPreferences("playback",0).edit().clear().putString("albumOrder","[\"playback-review-album-a\",\"playback-review-album-b\"]").putLong("contextRevision",123).commit();
   try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
    Thread.sleep(1200);
    final com.google.common.util.concurrent.ListenableFuture<MediaController>[] future=new com.google.common.util.concurrent.ListenableFuture[1];
    main(()->future[0]=new MediaController.Builder(context,new SessionToken(context,new android.content.ComponentName(context,PlaybackService.class))).buildAsync());
    controller=future[0].get(15,TimeUnit.SECONDS);final MediaController p=controller;
    main(()->{System.out.println("NATIVE_COMMANDS connected="+p.isConnected()+" size="+p.getAvailableCommands().size()+" change="+p.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)+" play="+p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE));p.setRepeatMode(Player.REPEAT_MODE_OFF);p.setShuffleModeEnabled(false);p.setMediaItems(first,1,0);p.prepare();p.play();});awaitTrack(p,"playback-review-1");
    js(activity,"document.querySelector('#next').click();true");awaitTrack(p,"playback-review-2");
    js(activity,"document.querySelector('#previous').click();true");awaitTrack(p,"playback-review-1");
    main(()->p.seekTo(Math.max(0,p.getDuration()-50)));awaitTrack(p,"playback-review-2");
    assertEquals("true",js(activity,"getComputedStyle(document.querySelector('#shuffle img')).opacity==='0.45' && getComputedStyle(document.querySelector('#repeat img')).opacity==='0.45'"));
    js(activity,"document.querySelector('#shuffle').click();true");Thread.sleep(600);
    assertEquals("true",js(activity,"getComputedStyle(document.querySelector('#shuffle img')).opacity==='1'"));
    main(()->assertTrue(p.getShuffleModeEnabled()));assertEquals("true",js(activity,"document.querySelector('#shuffle').classList.contains('active') && document.querySelector('#shuffle').getAttribute('aria-pressed')==='true'"));
    js(activity,"document.querySelector('#shuffle').click();true");Thread.sleep(500);main(()->assertFalse(p.getShuffleModeEnabled()));
    js(activity,"document.querySelector('#repeat').click();true");Thread.sleep(500);main(()->assertEquals(Player.REPEAT_MODE_ALL,p.getRepeatMode()));assertEquals("true",js(activity,"document.querySelector('#repeat').classList.contains('active') && document.querySelector('#repeat').getAttribute('aria-label')==='Repeat All' && document.querySelector('#repeat img').getAttribute('src').endsWith('/repeat.svg')"));
    js(activity,"document.querySelector('#repeat').click();true");Thread.sleep(500);main(()->assertEquals(Player.REPEAT_MODE_ONE,p.getRepeatMode()));assertEquals("true",js(activity,"document.querySelector('#repeat').getAttribute('aria-label')==='Repeat Song' && document.querySelector('#repeat img').getAttribute('src').endsWith('/repeat-song.svg')"));
    js(activity,"document.querySelector('#repeat').click();true");Thread.sleep(500);main(()->assertEquals(Player.REPEAT_MODE_OFF,p.getRepeatMode()));assertEquals("true",js(activity,"!document.querySelector('#repeat').classList.contains('active') && document.querySelector('#repeat').getAttribute('aria-pressed')==='false'"));
    assertEquals("true",js(activity,"getComputedStyle(document.querySelector('#repeat img')).opacity==='0.45'"));
    js(activity,"document.querySelector('#volume').click();var slider=document.querySelector('.volumeSliderShell input');slider.value='35';slider.dispatchEvent(new Event('input',{bubbles:true}));true");Thread.sleep(400);
    main(()->assertEquals(.35f,p.getVolume(),.01f));
    js(activity,"document.querySelector('.volumePopover').hidePopover();true");main(()->p.setVolume(1f));
    // Select albums through the same drawer/play path used by the user.
    for(int album=0;album<2;album++){
     String albumId=album==0?"playback-review-album-a":"playback-review-album-b";
     js(activity,"for(let i=0;i<naviglassDebug.albums;i++){naviglassDebug.selectAlbum(i);if(naviglassDebug.entry.id==='"+albumId+"')break;}true");
     assertEquals("Selected cover must be the requested fixture album",org.json.JSONObject.quote(albumId),js(activity,"naviglassDebug.entry.id"));
     js(activity,"document.querySelector('#songs').click();true");
     String expectedTrack=album==0?"playback-review-0":"playback-review-2";
     for(int attempt=0;attempt<30;attempt++){if("true".equals(js(activity,"document.querySelector('#panelBody .track')?.dataset.id==='"+expectedTrack+"'")))break;Thread.sleep(100);}
     assertEquals("Drawer rows must match selected album","true",js(activity,"document.querySelector('#panelBody .track')?.dataset.id==='"+expectedTrack+"'"));
     js(activity,"document.querySelector('#panelBody .trackPlay').click();true");awaitTrack(p,album==0?"playback-review-0":"playback-review-2");Thread.sleep(500);
     main(()->{assertTrue("Audio stream must be active, not just a playing UI",((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).isMusicActive());assertTrue("Player must not be muted",p.getVolume()>0);assertNull(p.getPlayerError());});
     final long[] position={0};main(()->position[0]=p.getCurrentPosition());Thread.sleep(300);main(()->assertTrue("Native audio position must advance",p.getCurrentPosition()>position[0]+100));
     js(activity,"window.closePanel();true");Thread.sleep(200);
    }
    activity.recreate();
    String restored="";
    for(int attempt=0;attempt<30;attempt++){
     Thread.sleep(300);
     restored=js(activity,"JSON.stringify({ready:typeof naviglassDebug!=='undefined',loading:document.body.classList.contains('libraryLoading'),hidden:document.querySelector('#welcome')?.hidden,albums:typeof naviglassDebug==='undefined'?-1:naviglassDebug.albums})");
     if("true".equals(js(activity,"typeof naviglassDebug!=='undefined' && document.querySelector('#welcome').hidden && naviglassDebug.albums==="+expectedAlbums)))break;
    }
    assertEquals("Local albums restore after recreation without source selection: "+restored,"true",js(activity,"typeof naviglassDebug!=='undefined' && document.querySelector('#welcome').hidden && naviglassDebug.albums==="+expectedAlbums));
    activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(500);main(()->assertTrue(p.isPlaying()));activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);main(p::pause);
   }
  }finally{if(controller!=null){final MediaController p=controller;main(()->{p.pause();p.clearMediaItems();p.release();});}context.stopService(new Intent(context,PlaybackService.class));library.getWritableDatabase().delete("tracks","source=?",new String[]{"playback-review-fixture"});context.getSharedPreferences("playback",0).edit().clear().commit();assertTrue(audio.delete());assertTrue(tone.delete());}
 }
 private void writeTone(File file)throws Exception{
  int rate=48000,channels=2,frames=rate*20,bytes=frames*channels*2;
  java.nio.ByteBuffer header=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36+bytes).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(bytes);
  try(FileOutputStream out=new FileOutputStream(file)){out.write(header.array());byte[] frame=new byte[4];for(int i=0;i<frames;i++){short sample=(short)(Math.sin(2*Math.PI*440*i/rate)*8000);frame[0]=frame[2]=(byte)sample;frame[1]=frame[3]=(byte)(sample>>8);out.write(frame);}}
 }
 private void awaitTrack(MediaController p,String id)throws Exception{for(int i=0;i<150;i++){boolean[] ready={false};main(()->ready[0]=p.isPlaying()&&p.getCurrentMediaItem()!=null&&id.equals(p.getCurrentMediaItem().mediaId));if(ready[0])return;Thread.sleep(100);}final String[] detail={""};main(()->detail[0]=" item="+(p.getCurrentMediaItem()==null?"none":p.getCurrentMediaItem().mediaId)+" state="+p.getPlaybackState()+" ready="+p.getPlayWhenReady()+" suppression="+p.getPlaybackSuppressionReason()+" error="+p.getPlayerError()+" commands="+p.getAvailableCommands());fail("Native player did not advance to "+id+detail[0]);}
 private String js(ActivityScenario<MainActivity> activity,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};activity.onActivity(a->{android.view.ViewGroup c=a.findViewById(android.R.id.content);android.webkit.WebView w=(android.webkit.WebView)((android.view.ViewGroup)c.getChildAt(0)).getChildAt(0);w.evaluateJavascript(script,r->{result[0]=r;done.countDown();});});assertTrue(done.await(10,TimeUnit.SECONDS));return result[0];}
}

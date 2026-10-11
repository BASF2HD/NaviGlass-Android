package com.naviglass.android;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Actual Android touch/orientation checks, not synthetic JS pointer events. */
@RunWith(AndroidJUnit4.class)
public class LayoutIntegrationTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 @Test public void landscapeSwipeCoverDrawerAndDropups()throws Exception {
  Library library=Library.get(context);NetworkSources.local(context);
  try {
   for(int i=0;i<8;i++){Track t=new Track();t.id="ui-fixture-"+i;t.source="ui-parity-fixture";t.uri="content://unplayed-fixture/"+i;t.albumId="ui-fixture-album-"+i;t.album="Fixture "+i;t.artist="Test Ensemble";t.year="2026";t.title="Test Song "+i;t.number=1;t.duration=20000;t.mime="audio/mpeg";library.getWritableDatabase().insertWithOnConflict("tracks",null,t.values(0),android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}
   for(char letter='A';letter<='Z';letter++) {
    for(int rank=0;rank<2;rank++){Track t=new Track();t.id="search-fixture-"+letter+rank;t.source="ui-parity-fixture";t.uri="content://unplayed-search/"+letter+rank;t.albumId="search-fixture";t.album=rank==0?"Z album":"A "+letter+" album";t.artist="Test Ensemble";t.title=rank==0?letter+" prefix song":"0 metadata match";t.number=1;t.duration=20000;t.mime="audio/mpeg";library.getWritableDatabase().insertWithOnConflict("tracks",null,t.values(0),android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}
    assertTrue("Title-prefix hits first for "+letter,library.tracks("search",String.valueOf(letter)).getJSONObject(0).getString("title").startsWith(String.valueOf(letter)));
   }
   assertEquals(0,library.tracks("search","%_unmatched").length());
   library.getWritableDatabase().delete("tracks","id LIKE ?",new String[]{"search-fixture-%"});
   try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
    Thread.sleep(1800);activity.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1400);
    assertEquals("true",js(activity,"innerWidth>innerHeight"));
    js(activity,"naviglassDebug.selectAlbum(2);true");Thread.sleep(600);
    assertEquals("true",js(activity,"document.querySelector('#controls').getBoundingClientRect().height<=84"));
    assertEquals("Landscape menu labels must fit their buttons without touching", "true",js(activity,"Array.from(document.querySelectorAll('#mainNav button')).every(b=>b.lastElementChild.getBoundingClientRect().width<=b.getBoundingClientRect().width-2)"));
    assertEquals("true",js(activity,"document.querySelector('#playbackStrip').getBoundingClientRect().width<=innerWidth-16"));
    assertEquals(js(activity,"JSON.stringify({viewport:[innerWidth,innerHeight],cover:naviglassDebug.bounds(),caption:document.querySelector('#caption').getBoundingClientRect(),controls:document.querySelector('#controls').getBoundingClientRect()})"),"true",js(activity,"document.querySelector('#caption').getBoundingClientRect().bottom<document.querySelector('#controls').getBoundingClientRect().top"));
    assertEquals("Landscape seek strip must use the extra width", "true",js(activity,"document.querySelector('#playbackStrip').getBoundingClientRect().width>naviglassDebug.bounds().width*1.5"));
    js(activity,"document.querySelector('#volume').click();true");Thread.sleep(200);
    assertEquals("true",js(activity,"document.querySelector('.volumePopover').matches(':popover-open') && !document.querySelector('#panel').open"));
    assertEquals("true",js(activity,"document.querySelector('.volumePopover').getBoundingClientRect().height>document.querySelector('.volumePopover').getBoundingClientRect().width*2 && Math.abs(document.querySelector('.volumePopover').getBoundingClientRect().top-document.querySelector('#volume').getBoundingClientRect().bottom-6)<1"));
    js(activity,"document.querySelector('.volumePopover').hidePopover();true");
    int before=Integer.parseInt(js(activity,"naviglassDebug.selectedAlbum"));
    float x=Float.parseFloat(js(activity,"naviglassDebug.bounds().left+naviglassDebug.bounds().width*.8")),y=Float.parseFloat(js(activity,"naviglassDebug.bounds().top+naviglassDebug.bounds().height*.5")),dx=Float.parseFloat(js(activity,"naviglassDebug.bounds().width*.5"));
    final float[] scale={1};final int[] location=new int[2];activity.onActivity(a->{WebView view=web(a);view.getLocationOnScreen(location);scale[0]=view.getWidth()/(float)view.getMeasuredWidth()*context.getResources().getDisplayMetrics().density;});
    long down=SystemClock.uptimeMillis();send(down,MotionEvent.ACTION_DOWN,location[0]+x*scale[0],location[1]+y*scale[0]);
    for(int i=1;i<=10;i++){Thread.sleep(30);send(down,MotionEvent.ACTION_MOVE,location[0]+(x-dx*i/10)*scale[0],location[1]+y*scale[0]);}
    send(down,MotionEvent.ACTION_UP,location[0]+(x-dx)*scale[0],location[1]+y*scale[0]);Thread.sleep(800);
    assertTrue("Native landscape touch swipe must move Cover Flow",Integer.parseInt(js(activity,"naviglassDebug.selectedAlbum"))>before);
    js(activity,"document.querySelector('#songs').click();true");Thread.sleep(400);
    assertEquals("true",js(activity,"Math.abs(document.querySelector('#panel').getBoundingClientRect().width-Math.min(innerWidth*.7,500,innerWidth-64))<1"));
    assertEquals("true",js(activity,"document.querySelector('#panelBody').scrollWidth<=document.querySelector('#panelBody').clientWidth"));
    saveScreenshot("native-drawer-landscape.png");js(activity,"window.closePanel();document.querySelector('[data-view=songs]').click();true");Thread.sleep(300);
    assertEquals("true",js(activity,"!document.querySelector('#content').hidden && document.querySelector('#content').getBoundingClientRect().bottom<document.querySelector('[data-view=songs]').getBoundingClientRect().top"));
    saveScreenshot("native-dropup-landscape.png");js(activity,"Array.from(document.querySelectorAll('#content button')).find(b=>b.textContent.startsWith('All Songs')).click();true");Thread.sleep(700);
    assertEquals("true",js(activity,"naviglassDebug.browseMode==='songs' && Boolean(naviglassDebug.entry.track) && document.querySelector('#content').hidden"));saveScreenshot("native-song-landscape.png");
    activity.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1000);
    assertEquals("true",js(activity,"innerHeight>innerWidth && document.documentElement.scrollWidth<=innerWidth"));
    js(activity,"document.querySelector('#songs').click();true");Thread.sleep(300);
    assertEquals("true",js(activity,"Math.abs(document.querySelector('#panel').getBoundingClientRect().width-innerWidth)<1 && Math.abs(document.querySelector('#panel').getBoundingClientRect().bottom-innerHeight)<1"));saveScreenshot("native-drawer-portrait.png");
    js(activity,"document.querySelector('#panelBody .songMenu').click();true");Thread.sleep(250);
    assertEquals("true",js(activity,"document.querySelector('.songContext').matches(':popover-open') && document.querySelectorAll('.songContext button').length===3"));
    assertEquals("true",js(activity,"document.querySelector('.songContext').getBoundingClientRect().bottom<innerHeight && document.querySelector('.songContext').getBoundingClientRect().left>=0"));
    saveScreenshot("native-song-actions.png");
    js(activity,"Array.from(document.querySelectorAll('.songContext button')).find(b=>b.textContent==='More info').click();true");Thread.sleep(200);
    assertEquals("true",js(activity,"document.querySelector('.songDetails').textContent.includes('Length')"));
    js(activity,"Array.from(document.querySelectorAll('#panelBody button')).find(b=>b.textContent==='Back to songs').click();true");Thread.sleep(200);
    assertEquals("true",js(activity,"document.querySelector('#panel').classList.contains('songDrawer') && document.querySelector('#panelBody .songMenu')!==null"));
    assertEquals("true",js(activity,"Math.abs(document.querySelector('#play').getBoundingClientRect().width-document.querySelector('#play').getBoundingClientRect().height)<1"));
   }
  }finally{library.getWritableDatabase().delete("tracks","source=?",new String[]{"ui-parity-fixture"});}
 }
 private void send(long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);InstrumentationRegistry.getInstrumentation().sendPointerSync(e);e.recycle();}
 private WebView web(MainActivity a){android.view.ViewGroup content=a.findViewById(android.R.id.content);return (WebView)((android.view.ViewGroup)content.getChildAt(0)).getChildAt(0);}
 private String js(ActivityScenario<MainActivity> a,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};a.onActivity(activity->web(activity).evaluateJavascript(script,r->{value[0]=r;done.countDown();}));assertTrue(done.await(10,TimeUnit.SECONDS));return value[0];}
 private void saveScreenshot(String name)throws Exception{android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();java.io.File folder=context.getExternalFilesDir("test-evidence");try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}

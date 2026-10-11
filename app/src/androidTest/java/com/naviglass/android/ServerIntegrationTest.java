package com.naviglass.android;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaController;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionToken;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Pinned synthetic HTTPS server; no test TLS override or key is packaged in the player. */
@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class ServerIntegrationTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void main(Runnable task){InstrumentationRegistry.getInstrumentation().runOnMainSync(task);}
    @Test public void completeLibraryFavouritesMenusAndNegotiatedNativeAudio()throws Exception{exercise(true);}
    @Test public void olderServerFallbackAndEncryptedRestore()throws Exception{exercise(false);}
    private void exercise(boolean negotiation)throws Exception{
        try(Fixture server=new Fixture(negotiation);ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            NetworkSources.disconnect(context);
            try{NetworkSources.connect(context,new JSONObject().put("url","http://localhost").put("username","fixture").put("password","fixture-only"));fail("Unapproved HTTP must be refused");}catch(IllegalArgumentException expected){}
            NetworkSources.connect(context,new JSONObject().put("url",server.address()).put("username","fixture").put("password","fixture-only"));
            assertTrue(NetworkSources.status(context).getString("url").endsWith("/navidrome"));
            assertEquals("Every album page must be loaded",501,NetworkSources.albums(context).length());
            assertTrue("Starred album beyond page one",NetworkSources.albums(context).getJSONObject(500).getBoolean("starred"));
            assertEquals("All starred songs, not first search page",301,NetworkSources.tracks(context,"favourites","").length());
            assertEquals(2,NetworkSources.cached(context,"artists").length());
            assertEquals(2,NetworkSources.playlists(context).length());
            assertEquals(2,RadioDirectory.saved(context).length());
            assertNotNull(RadioDirectory.station(context,RadioDirectory.saved(context).getJSONObject(0).getString("id")));
            String encrypted=context.getSharedPreferences("server",0).getString("account","");assertFalse(encrypted.contains("fixture-only"));assertFalse(encrypted.contains("localhost"));
            // Existing 0.2.5 device playlists retain legacy remote track IDs during upgrade.
            Track legacy=new Track();legacy.id="nd_"+Library.key(NetworkSources.account(context).getString("url")+":fixture-0");legacy.source="navidrome:"+Library.key(NetworkSources.account(context).getString("url"));legacy.uri="navidrome://track/fixture-0?cover=fixture-cover";legacy.albumId="nd:fixture-album";legacy.title="Legacy Song";legacy.artist="Synthetic Ensemble";legacy.album="Fixture Album";legacy.mime="audio/mp4";Library.get(context).getWritableDatabase().insertWithOnConflict("tracks",null,legacy.values(0),android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);String localMix=Library.get(context).createPlaylist("Upgrade fixture");Library.get(context).addToPlaylist(localMix,legacy.id);assertNotNull(NetworkSources.item(context,legacy));NetworkSources.ensureSynced(context);
            JSONArray songs=NetworkSources.tracks(context,"album","nd:fixture-album");assertEquals(legacy.id,songs.getJSONObject(0).getString("id"));assertEquals(legacy.id,Library.get(context).tracks("playlist",localMix).getJSONObject(0).getString("id"));Library.get(context).getWritableDatabase().delete("playlist_tracks","playlist_id=?",new String[]{localMix});Library.get(context).getWritableDatabase().delete("playlists","id=?",new String[]{localMix});assertEquals(4,songs.length());assertTrue(songs.getJSONObject(0).getBoolean("favourite"));
            assertEquals(4,NetworkSources.tracks(context,"artist","Synthetic Ensemble").length());
            assertEquals(4,NetworkSources.tracks(context,"serverPlaylist","mix").length());
            assertEquals(0,NetworkSources.tracks(context,"search","zz-no-prefix").length());
            assertNotNull(NetworkSources.cover(context,NetworkSources.albums(context).getJSONObject(0).getString("coverId")));
            String songId=songs.getJSONObject(0).getString("id");assertFalse(NetworkSources.toggleSong(context,songId));assertFalse(NetworkSources.favourite(context,Library.get(context).track(songId)));assertTrue(NetworkSources.toggleSong(context,songId));
            assertFalse(NetworkSources.toggleAlbum(context,"nd:album-500"));assertTrue(NetworkSources.toggleAlbum(context,"nd:album-500"));
            NetworkSources.local(context);assertFalse("Server favourites must not leak into device favourites",Library.get(context).tracks("favourites","").toString().contains("nd_"));context.getSharedPreferences("server",0).edit().putBoolean("active",true).commit();
            java.lang.reflect.Field saved=NetworkSources.class.getDeclaredField("account");saved.setAccessible(true);saved.set(null,null);assertTrue(NetworkSources.status(context).getBoolean("connected"));
            activity.recreate();awaitJs(activity,"typeof naviglassDebug !== 'undefined' && naviglassDebug.albums===501");
            clickMenu(activity,"albums");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='501'");
            assertEquals("true",js(activity,"!document.querySelector('#content .browseItem small') && [...document.querySelectorAll('#content .browseItem')].some(b=>b.textContent.includes('All Albums')&&b.getAttribute('aria-pressed')==='true'&&!b.querySelector('.selectionTick').hidden)"));
            clickRow(activity,"Favourite");clickMenu(activity,"albums");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='1'");
            clickMenu(activity,"songs");clickRow(activity,"Favourite");clickMenu(activity,"songs");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='301'");
            clickRow(activity,"Group by album");clickMenu(activity,"songs");assertEquals("true",js(activity,"[...document.querySelectorAll('#content .browseItem')].filter(b=>['Favourite','Group by album'].includes(b.querySelector('strong')?.textContent)).every(b=>b.getAttribute('aria-pressed')==='true'&&!b.querySelector('.selectionTick').hidden)"));
            clickMenu(activity,"artists");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='2'");
            clickMenu(activity,"playlists");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='2'");
            clickMenu(activity,"more");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='2'");
            clickRow(activity,"Favourite songs");clickMenu(activity,"more");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='301'");assertEquals("true",js(activity,"[...document.querySelectorAll('#content .browseItem')].find(b=>b.querySelector('strong')?.textContent==='Favourite songs').getAttribute('aria-pressed')==='true'"));clickMenu(activity,"songs");awaitJs(activity,"document.querySelector('#content .menuCount')?.textContent==='301'");clickMenu(activity,"more");
            final com.google.common.util.concurrent.ListenableFuture<MediaController>[] future=new com.google.common.util.concurrent.ListenableFuture[1];main(()->future[0]=new MediaController.Builder(context,new SessionToken(context,new android.content.ComponentName(context,PlaybackService.class))).buildAsync());
            MediaController controller=future[0].get(15,TimeUnit.SECONDS);
            try{
                final ExoPlayer[] observed={null};java.lang.reflect.Field sessions=MediaSession.class.getDeclaredField("SESSION_ID_TO_SESSION_MAP");sessions.setAccessible(true);main(()->{try{for(Object value:((Map<?,?>)sessions.get(null)).values()){MediaSession session=(MediaSession)value;if(session.getPlayer() instanceof ExoPlayer)observed[0]=(ExoPlayer)session.getPlayer();}}catch(Exception e){throw new AssertionError(e);}});assertNotNull(observed[0]);ExoPlayer player=observed[0];
                if(negotiation){
                    // Grouped favourites queue every selected album, without pulling unstarred tracks.
                    clickMenu(activity,"songs"); // More is open, so this opens Songs.
                    js(activity,"document.querySelector('#content .browseClose').click();naviglassDebug.selectAlbum(0);document.querySelector('#songs').click();true");
                    awaitJs(activity,"document.querySelectorAll('#panelBody .trackPlay').length===4");js(activity,"document.querySelectorAll('#panelBody .trackPlay')[3].click();true");
                    awaitAudio(player,songs.getJSONObject(3).getString("id"));
                    final String[] next={""};main(()->{assertEquals(301,player.getMediaItemCount());next[0]=player.getMediaItemAt(4).mediaId;assertEquals("nd:album-500",Library.get(context).track(next[0]).albumId);assertEquals(Player.REPEAT_MODE_OFF,player.getRepeatMode());assertFalse(player.getShuffleModeEnabled());player.seekTo(player.getDuration()-50);});awaitAudio(player,next[0]);js(activity,"window.closePanel();true");
                }
                for(int i=0;i<songs.length();i++){
                    String id=songs.getJSONObject(i).getString("id");main(()->{controller.setVolume(1);controller.setMediaItem(new androidx.media3.common.MediaItem.Builder().setMediaId(id).build());controller.prepare();controller.play();});
                    awaitAudio(player,id);main(()->{assertNull(player.getPlayerError());assertTrue(((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).isMusicActive());});
                    if(i==1){main(()->controller.seekTo(2000));awaitAudio(player,id);}
                    if(i==2){activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(400);main(()->assertTrue(player.isPlaying()));activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);}
                }
                main(()->{controller.pause();controller.clearMediaItems();});
            }finally{main(controller::release);}
            assertTrue(negotiation?server.decisions>=4:server.legacyStreams>=4);
            if(server.failure.get()!=null)throw new AssertionError(server.failure.get());
            NetworkSources.disconnect(context);assertNull(NetworkSources.item(context,Library.get(context).track(songId)));
        }finally{NetworkSources.disconnect(context);context.stopService(new android.content.Intent(context,PlaybackService.class));context.getSharedPreferences("playback",0).edit().clear().commit();Library.get(context).getWritableDatabase().delete("tracks","source LIKE ?",new String[]{"navidrome:%"});}
    }
    private void clickMenu(ActivityScenario<MainActivity> a,String name)throws Exception{js(a,"document.querySelector('[data-view=\""+name+"\"]').click();true");Thread.sleep(250);}
    private void clickRow(ActivityScenario<MainActivity> a,String title)throws Exception{js(a,"[...document.querySelectorAll('#content .browseItem')].find(b=>b.querySelector('strong')?.textContent==="+JSONObject.quote(title)+").click();true");Thread.sleep(400);}
    private void awaitJs(ActivityScenario<MainActivity> activity,String condition)throws Exception{for(int i=0;i<80;i++){if("true".equals(js(activity,condition)))return;Thread.sleep(100);}fail("UI did not satisfy: "+condition+"; "+js(activity,"document.querySelector('#content')?.textContent"));}
    private String js(ActivityScenario<MainActivity> a,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};a.onActivity(activity->{android.view.ViewGroup root=activity.findViewById(android.R.id.content);android.webkit.WebView web=(android.webkit.WebView)((android.view.ViewGroup)root.getChildAt(0)).getChildAt(0);web.evaluateJavascript(script,r->{result[0]=r;done.countDown();});});assertTrue(done.await(10,TimeUnit.SECONDS));return result[0];}
    private void awaitAudio(ExoPlayer player,String id)throws Exception{
        for(int i=0;i<150;i++){boolean[] ready={false};int[] buffers={0};long[] position={0};main(()->{androidx.media3.exoplayer.DecoderCounters c=player.getAudioDecoderCounters();if(c!=null)c.ensureUpdated();buffers[0]=c==null?0:c.renderedOutputBufferCount;position[0]=player.getCurrentPosition();ready[0]=player.isPlaying()&&player.getCurrentMediaItem()!=null&&id.equals(player.getCurrentMediaItem().mediaId)&&player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)&&buffers[0]>0;});if(ready[0]){Thread.sleep(300);boolean[] advanced={false};main(()->{androidx.media3.exoplayer.DecoderCounters c=player.getAudioDecoderCounters();if(c!=null)c.ensureUpdated();advanced[0]=c!=null&&c.renderedOutputBufferCount>buffers[0]&&player.getCurrentPosition()>position[0]+100&&player.getVolume()>0&&player.getPlayerError()==null;});if(advanced[0])return;}Thread.sleep(100);}
        main(()->fail("Native decoder did not render progressing audio for "+id+"; error="+player.getPlayerError()));
    }
    private static final class Fixture implements AutoCloseable {
        final SSLServerSocket server;final ExecutorService worker=Executors.newCachedThreadPool();final AtomicReference<Throwable> failure=new AtomicReference<>();final SSLSocketFactory previous;final Thread accept;final boolean negotiation;volatile int decisions,legacyStreams;final Set<String> starred=ConcurrentHashMap.newKeySet();volatile boolean albumStarred=true;
        Fixture(boolean negotiation)throws Exception{
            this.negotiation=negotiation;for(int i=0;i<301;i++)starred.add(i<4?"fixture-"+i:"extra-"+i);
            KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("test-server.p12")){keys.load(in,"fixture-only".toCharArray());}X509Certificate cert=(X509Certificate)keys.getCertificate("fixture");KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"fixture-only".toCharArray());SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[]{cert};}public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{if(c.length==0||!cert.equals(c[0]))throw new java.security.cert.CertificateException("Untrusted fixture");cert.checkValidity();}}},new SecureRandom());previous=HttpsURLConnection.getDefaultSSLSocketFactory();HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,20,java.net.InetAddress.getByName("127.0.0.1"));accept=new Thread(()->{while(!server.isClosed())try{SSLSocket socket=(SSLSocket)server.accept();worker.submit(()->serve(socket));}catch(IOException ignored){}});accept.start();
        }
        String address(){return "https://localhost:"+server.getLocalPort();}
        JSONObject song(String id)throws Exception{int i=id.startsWith("fixture-")?Integer.parseInt(id.substring(8)):0;return new JSONObject().put("id",id).put("albumId",id.startsWith("extra-")?"album-500":"fixture-album").put("title",id.startsWith("extra-")?"First Light "+id:new String[]{"Zulu", "First Light", "Beta", "Alpha"}[i]).put("artist","Synthetic Ensemble").put("album",id.startsWith("extra-")?"Album 500":"Fixture Album").put("year",id.startsWith("extra-")?2025:2026).put("track",i+1).put("duration",6).put("coverArt","fixture-cover").put("contentType",new String[]{"audio/mp4","audio/wav","audio/flac","audio/alac"}[i]);}
        JSONArray songs()throws Exception{JSONArray out=new JSONArray();for(int i=0;i<4;i++)out.put(song("fixture-"+i));return out;}
        void serve(SSLSocket socket){try(socket){BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String request=reader.readLine();if(request==null)return;int length=0;String header;while((header=reader.readLine())!=null&&!header.isEmpty())if(header.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());char[] body=new char[length];int at=0;while(at<length){int n=reader.read(body,at,length-at);if(n<0)break;at+=n;}
            Uri uri=Uri.parse(address()+request.split(" ")[1]);String endpoint=uri.getLastPathSegment();assertEquals("fixture",uri.getQueryParameter("u"));assertEquals(hex(MessageDigest.getInstance("MD5").digest(("fixture-only"+uri.getQueryParameter("s")).getBytes("UTF-8"))),uri.getQueryParameter("t"));
            if(uri.getPath().startsWith("/rest/")||endpoint.equals("getTranscodeDecision.view")&&!negotiation){send(socket,404,new byte[0],"application/json");return;}
            if(endpoint.equals("stream.view")||endpoint.equals("getTranscodeStream.view")){String id=uri.getQueryParameter(endpoint.startsWith("getTranscode")?"mediaId":"id");int i=id.startsWith("extra-")?3:Integer.parseInt(id.substring(8));if(endpoint.equals("stream.view")){legacyStreams++;assertEquals(i==0||i==3?"flac":"raw",uri.getQueryParameter("format"));}else{assertEquals("song",uri.getQueryParameter("mediaType"));assertEquals("fixture-token:"+id,uri.getQueryParameter("transcodeParams"));}String asset= i==0&&negotiation?"synthetic.m4a":i==1?"synthetic.wav":"synthetic.flac";try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(asset)){send(socket,200,in.readAllBytes(),asset.endsWith("m4a")?"audio/mp4":asset.endsWith("wav")?"audio/wav":"audio/flac");}return;}
            if(endpoint.equals("getCoverArt.view")){android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);image.eraseColor(0xff62779a);ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,out);image.recycle();send(socket,200,out.toByteArray(),"image/jpeg");return;}
            JSONObject root=new JSONObject().put("status","ok");
            switch(endpoint){
                case "getAlbumList2.view":JSONArray albums=new JSONArray();int offset=Integer.parseInt(uri.getQueryParameter("offset"));for(int i=offset;i<Math.min(501,offset+500);i++)albums.put(new JSONObject().put("id",i==0?"fixture-album":"album-"+i).put("name","Album "+i).put("artist","Synthetic Ensemble").put("songCount",i==0?4:1).put("coverArt","fixture-cover"));root.put("albumList2",new JSONObject().put("album",albums));break;
                case "getStarred2.view":JSONArray fav=new JSONArray();for(String id:starred)fav.put(song(id));root.put("starred2",new JSONObject().put("song",fav).put("album",albumStarred?new JSONArray().put(new JSONObject().put("id","album-500")):new JSONArray()));break;
                case "getArtists.view":root.put("artists",new JSONObject().put("index",new JSONArray().put(new JSONObject().put("artist",new JSONArray().put(new JSONObject().put("id","ensemble").put("name","Synthetic Ensemble")).put(new JSONObject().put("id","outside").put("name","Other Artist"))))));break;
                case "getArtist.view":root.put("artist",new JSONObject().put("album",new JSONArray().put(new JSONObject().put("id","fixture-album"))));break;
                case "getInternetRadioStations.view":root.put("internetRadioStations",new JSONObject().put("internetRadioStation",new JSONArray().put(new JSONObject().put("id","radio-a").put("name","Server Radio").put("streamUrl",address()+"/fixture-radio")).put(new JSONObject().put("id","radio-b").put("name","HTTP Radio").put("streamUrl","http://example.com/radio"))));break;
                case "getAlbum.view":root.put("album",new JSONObject().put("coverArt","fixture-cover").put("song",songs()));break;
                case "search3.view":root.put("searchResult3",new JSONObject().put("song",songs()));break;
                case "getSong.view":root.put("song",song(uri.getQueryParameter("id")));break;
                case "getPlaylists.view":root.put("playlists",new JSONObject().put("playlist",new JSONArray().put(new JSONObject().put("id","mix").put("name","Server Mix").put("songCount",4)).put(new JSONObject().put("id","other").put("name","Other Mix").put("songCount",0))));break;
                case "getPlaylist.view":root.put("playlist",new JSONObject().put("entry",songs()));break;
                case "star.view":case "unstar.view":boolean star=endpoint.equals("star.view");String id=uri.getQueryParameter("id");if(id!=null){if(star)starred.add(id);else starred.remove(id);}else albumStarred=star;break;
                case "getTranscodeDecision.view":decisions++;assertTrue(request.startsWith("POST "));assertEquals("song",uri.getQueryParameter("mediaType"));JSONObject profile=new JSONObject(new String(body));assertTrue(profile.getJSONArray("directPlayProfiles").length()>0);assertEquals("flac",profile.getJSONArray("transcodingProfiles").getJSONObject(0).getString("audioCodec"));root.put("transcodeDecision",new JSONObject().put("canDirectPlay",!"fixture-3".equals(uri.getQueryParameter("mediaId"))).put("canTranscode",true).put("transcodeParams","fixture-token:"+uri.getQueryParameter("mediaId")));break;
                case "ping.view":break;
                default:throw new AssertionError("Unexpected fixture endpoint "+endpoint);
            }
            send(socket,200,new JSONObject().put("subsonic-response",root).toString().getBytes("UTF-8"),"application/json");
        }catch(Throwable error){failure.compareAndSet(null,error);}}
        private void send(SSLSocket socket,int status,byte[] bytes,String type)throws Exception{OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nConnection: close\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes("UTF-8"));out.write(bytes);out.flush();}
        @Override public void close()throws Exception{server.close();worker.shutdownNow();accept.join(1000);HttpsURLConnection.setDefaultSSLSocketFactory(previous);}
        private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}
    }
}

package com.naviglass.android;
import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Test-only pinned HTTPS fixture. No TLS override is present in the production APK. */
@RunWith(AndroidJUnit4.class)
public class ServerIntegrationTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 @Test public void httpsLoginArtworkStreamingEncryptedPersistenceAndDisconnect()throws Exception{
  Context tests=InstrumentationRegistry.getInstrumentation().getContext();KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=tests.getAssets().open("test-server.p12")){store.load(in,"fixture-only".toCharArray());}
  X509Certificate cert=(X509Certificate)store.getCertificate(store.aliases().nextElement());KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,"fixture-only".toCharArray());SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[]{cert};}public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{if(c.length==0||!cert.equals(c[0]))throw new java.security.cert.CertificateException("Not the pinned test fixture");cert.checkValidity();}}},new SecureRandom());
  SSLSocketFactory previous=HttpsURLConnection.getDefaultSSLSocketFactory();HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());SSLServerSocket server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,20,java.net.InetAddress.getByName("127.0.0.1"));ExecutorService worker=Executors.newCachedThreadPool();java.util.concurrent.atomic.AtomicReference<Throwable> failed=new java.util.concurrent.atomic.AtomicReference<>();byte[] tone;try(InputStream in=tests.getAssets().open("synthetic.mp3")){tone=in.readAllBytes();}
  Thread accept=new Thread(()->{while(!server.isClosed())try{SSLSocket socket=(SSLSocket)server.accept();worker.submit(()->{try(socket){BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));String request=reader.readLine();String header;while((header=reader.readLine())!=null&&!header.isEmpty()){}if(request==null)return;Uri uri=Uri.parse("https://localhost"+request.split(" ")[1]);String path=uri.getPath();assertEquals("fixture",uri.getQueryParameter("u"));
    if(path.startsWith("/encoded/") && uri.getQueryParameter("p")==null){byte[] refusal="{\"subsonic-response\":{\"status\":\"failed\",\"error\":{\"code\":40}}}".getBytes("UTF-8");OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: "+refusal.length+"\r\n\r\n").getBytes("UTF-8"));out.write(refusal);out.flush();return;}
    if(path.startsWith("/encoded/"))assertEquals("enc:"+hex("fixture-only".getBytes("UTF-8")),uri.getQueryParameter("p"));
    else {String expected=hex(MessageDigest.getInstance("MD5").digest(("fixture-only"+uri.getQueryParameter("s")).getBytes("UTF-8")));assertEquals(expected,uri.getQueryParameter("t"));}if(path.startsWith("/rest/")){OutputStream out=socket.getOutputStream();out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));out.flush();return;}byte[] bytes;String type="application/json";
    if(path.contains("stream")){assertEquals("raw",uri.getQueryParameter("format"));bytes=tone;type="audio/mpeg";}
    else if(path.contains("getCoverArt")){android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);image.eraseColor(0xff62779a);ByteArrayOutputStream output=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,output);image.recycle();bytes=output.toByteArray();type="image/jpeg";}
    else {JSONObject root=new JSONObject().put("status","ok");JSONObject song=new JSONObject().put("id","fixture-song").put("albumId","fixture-album").put("title","First Light").put("artist","Test Ensemble").put("album","Fixture Album").put("year",2026).put("track",1).put("duration",20).put("coverArt","fixture-cover");
      if(path.contains("getAlbumList2"))root.put("albumList2",new JSONObject().put("album",new JSONArray().put(new JSONObject().put("id","fixture-album").put("name","Fixture Album").put("artist","Test Ensemble").put("songCount",1))));
      else if(path.contains("getAlbum"))root.put("album",new JSONObject().put("coverArt","fixture-cover").put("song",new JSONArray().put(song)));
      else if(path.contains("search3"))root.put("searchResult3",new JSONObject().put("song",new JSONArray().put(song)));
      else if(path.contains("getPlaylists"))root.put("playlists",new JSONObject().put("playlist",new JSONArray().put(new JSONObject().put("id","mix").put("name","Server mix").put("songCount",1))));
      else if(path.contains("getPlaylist"))root.put("playlist",new JSONObject().put("entry",new JSONArray().put(song)));
      bytes=new JSONObject().put("subsonic-response",root).toString().getBytes("UTF-8");}
    OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes("UTF-8"));out.write(bytes);out.flush();}catch(Throwable e){failed.compareAndSet(null,e);}});}catch(IOException ignored){}});accept.start();
  MediaController controller=null;
  try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
   if(!BuildConfig.NAVIDROME_HTTP_HOST.isEmpty()){assertTrue(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(BuildConfig.NAVIDROME_HTTP_HOST));assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"));try{NetworkSources.connect(context,new JSONObject().put("url","http://"+BuildConfig.NAVIDROME_HTTP_HOST+":"+(BuildConfig.NAVIDROME_HTTP_PORT==65535?1:BuildConfig.NAVIDROME_HTTP_PORT+1)).put("username","fixture").put("password","fixture-only"));fail("Different HTTP port must be refused");}catch(IllegalArgumentException expected){}}
   NetworkSources.disconnect(context);try{NetworkSources.connect(context,new JSONObject().put("url","http://localhost").put("username","fixture").put("password","fixture-only"));fail("HTTP must be refused");}catch(IllegalArgumentException expected){}
   NetworkSources.connect(context,new JSONObject().put("url","https://localhost:"+server.getLocalPort()).put("username","fixture").put("password","fixture-only"));assertTrue(NetworkSources.active(context));assertTrue("Original-player proxy must be detected",NetworkSources.status(context).getString("url").endsWith("/navidrome"));assertEquals(1,NetworkSources.albums(context).length());NetworkSources.connect(context,new JSONObject().put("url","https://localhost:"+server.getLocalPort()+"/encoded").put("username","fixture").put("password","fixture-only"));assertEquals("encoded",NetworkSources.account(context).getString("authMode"));
   String encrypted=context.getSharedPreferences("server",0).getString("account","");assertFalse(encrypted.contains("fixture-only"));assertFalse(encrypted.contains("localhost"));
   String cover=NetworkSources.albums(context).getJSONObject(0).getString("coverId");assertNotNull(NetworkSources.cover(context,cover));JSONArray tracks=NetworkSources.tracks(context,"album","nd:fixture-album");String id=tracks.getJSONObject(0).getString("id");assertEquals(1,NetworkSources.tracks(context,"all","0").length());assertEquals(1,NetworkSources.playlists(context).length());assertEquals(1,NetworkSources.tracks(context,"serverPlaylist","mix").length());assertFalse(tracks.toString().contains("fixture-only"));
   java.lang.reflect.Field account=NetworkSources.class.getDeclaredField("account");account.setAccessible(true);account.set(null,null);assertTrue(NetworkSources.status(context).getBoolean("connected"));
   final com.google.common.util.concurrent.ListenableFuture<MediaController>[] future=new com.google.common.util.concurrent.ListenableFuture[1];main(()->future[0]=new MediaController.Builder(context,new SessionToken(context,new android.content.ComponentName(context,PlaybackService.class))).buildAsync());controller=future[0].get(15,TimeUnit.SECONDS);final MediaController player=controller;main(()->{player.setMediaItem(new androidx.media3.common.MediaItem.Builder().setMediaId(id).build());player.prepare();player.play();});boolean ready=false;for(int i=0;i<150;i++){final boolean[] playing={false};main(()->playing[0]=player.isPlaying());if(playing[0]){ready=true;break;}Thread.sleep(100);}assertTrue("Native server audio must play",ready);Thread.sleep(500);main(()->{assertTrue(player.getCurrentPosition()>100);player.pause();player.clearMediaItems();});NetworkSources.disconnect(context);assertFalse(NetworkSources.status(context).getBoolean("connected"));assertNull(NetworkSources.item(context,Library.get(context).track(id)));if(failed.get()!=null)throw new AssertionError(failed.get());
  }finally{if(controller!=null){final MediaController player=controller;main(player::release);}NetworkSources.disconnect(context);context.stopService(new android.content.Intent(context,PlaybackService.class));server.close();worker.shutdownNow();accept.join(1000);HttpsURLConnection.setDefaultSSLSocketFactory(previous);context.getSharedPreferences("playback",0).edit().clear().commit();Library.get(context).getWritableDatabase().delete("tracks","source LIKE ?",new String[]{"navidrome:%"});}
 }
 private static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format("%02x",b));return s.toString();}
}

package com.naviglass.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.media3.common.MediaItem;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
import org.json.JSONArray;

/** Native-only network access. Credentials and authenticated URLs never enter the WebView. */
final class NetworkSources {
    private static JSONObject account;
    private static JSONArray remoteAlbums=new JSONArray();
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("server",Context.MODE_PRIVATE);}
    private static SecretKey key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias("naviglass-server")){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder("naviglass-server",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
        return (SecretKey)store.getKey("naviglass-server",null);}
    private static String encrypt(String text)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(text.getBytes("UTF-8")),Base64.NO_WRAP);}
    private static String decrypt(String text)throws Exception{String[] parts=text.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");}
    static synchronized JSONObject account(Context c)throws Exception{if(account==null){String saved=prefs(c).getString("account","");if(saved.isEmpty())throw new IllegalStateException("Connect to Navidrome in Settings first");account=new JSONObject(decrypt(saved));}return account;}
    static boolean active(Context c){return prefs(c).getBoolean("active",false);}
    static JSONObject status(Context c)throws Exception{JSONObject j=new JSONObject().put("active",active(c)).put("connected",false);try{JSONObject a=account(c);j.put("connected",true).put("url",a.getString("url")).put("username",a.getString("username"));}catch(Exception ignored){}return j;}
    static void local(Context c){prefs(c).edit().putBoolean("active",false).apply();}
    static synchronized void disconnect(Context c){account=null;remoteAlbums=new JSONArray();prefs(c).edit().clear().apply();}
    static void connect(Context c,JSONObject r)throws Exception{
        String base=r.getString("url").trim().replaceAll("/+$","");Uri u=Uri.parse(base);boolean plain="http".equals(u.getScheme());
        boolean approvedPlain=plain&&!BuildConfig.NAVIDROME_HTTP_HOST.isEmpty()&&BuildConfig.NAVIDROME_HTTP_HOST.equals(u.getHost())&&BuildConfig.NAVIDROME_HTTP_PORT==u.getPort();
        if((!"https".equals(u.getScheme())&&!approvedPlain)||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||base.length()>2048)throw new IllegalArgumentException("Enter an HTTPS Navidrome URL, or the exact Tailscale host and port explicitly approved for this test build");
        String user=r.getString("username").trim(),password=r.getString("password");if(user.isEmpty()||user.length()>200||password.isEmpty()||password.length()>4096)throw new IllegalArgumentException("Enter your Navidrome username and password");
        JSONObject proposed=new JSONObject().put("url",base).put("username",user).put("password",password);
        try { login(proposed); }
        catch (EndpointException notNavidrome) {
            // Original NaviGlass exposes Subsonic under this same-origin proxy.
            // Never send credentials to a different host, or retry an authentication refusal.
            if (!"https".equals(u.getScheme()) || base.endsWith("/navidrome")) throw notNavidrome;
            proposed.put("url",base+"/navidrome");login(proposed);
        }
        JSONArray initial=albumPage(proposed,0);String encrypted=encrypt(proposed.toString());synchronized(NetworkSources.class){account=proposed;remoteAlbums=initial;}
        prefs(c).edit().putString("account",encrypted).putString("albums",initial.toString()).putBoolean("active",true).apply();
    }
    private static void login(JSONObject proposed)throws Exception {
        try {api(proposed,"ping",new JSONObject());}
        catch(AuthenticationException refused) {
            // Original NaviGlass retries encoded-password auth when token auth is refused.
            // Only do so under HTTPS; the approved HTTP exception is token-only.
            if(!"https".equals(Uri.parse(proposed.getString("url")).getScheme()))throw refused;
            proposed.put("authMode","encoded");api(proposed,"ping",new JSONObject());
        }
    }
    private static final class AuthenticationException extends IllegalStateException {
        AuthenticationException(){super("Reached Navidrome, but it rejected the username/password (code 40). Use your Navidrome login, not your Tailscale login.");}
    }
    static JSONArray albums(Context c)throws Exception{if(remoteAlbums.length()==0)remoteAlbums=new JSONArray(prefs(c).getString("albums","[]"));return new JSONArray(remoteAlbums.toString());}
    static JSONArray more(Context c)throws Exception{JSONArray page=albumPage(account(c),albums(c).length());synchronized(NetworkSources.class){for(int i=0;i<page.length();i++)remoteAlbums.put(page.getJSONObject(i));}prefs(c).edit().putString("albums",remoteAlbums.toString()).apply();return albums(c);}
    private static JSONArray albumPage(JSONObject account,int offset)throws Exception{JSONArray input=api(account,"getAlbumList2",new JSONObject().put("type","alphabeticalByName").put("size",500).put("offset",offset)).optJSONObject("albumList2").optJSONArray("album");JSONArray out=new JSONArray();if(input==null)return out;
        for(int i=0;i<input.length();i++){JSONObject a=input.getJSONObject(i);String id=a.getString("id");out.put(new JSONObject().put("id","nd:"+id).put("title",a.optString("name",a.optString("title"))).put("artist",a.optString("artist")).put("year",a.optString("year")).put("count",a.optInt("songCount")).put("coverId","nd_"+Library.key(id)).put("remote",true));}return out;}
    static JSONArray tracks(Context c,String mode,String value)throws Exception{
        JSONObject result;JSONArray songs;
        if(mode.equals("album")&&value.startsWith("nd:")){result=api(account(c),"getAlbum",new JSONObject().put("id",value.substring(3)));songs=result.getJSONObject("album").optJSONArray("song");}
        else if(mode.equals("serverPlaylist")){result=api(account(c),"getPlaylist",new JSONObject().put("id",value));songs=result.getJSONObject("playlist").optJSONArray("entry");}
        else if(mode.equals("favourites")||mode.equals("playlist"))return Library.get(c).tracks(mode,value);
        else {int offset=0;try{if(mode.equals("all"))offset=Integer.parseInt(value);}catch(Exception ignored){}result=api(account(c),"search3",new JSONObject().put("query",mode.equals("all")?"":value).put("songCount",200).put("songOffset",Math.max(0,offset)).put("albumCount",0).put("artistCount",0));songs=result.getJSONObject("searchResult3").optJSONArray("song");}
        JSONArray out=new JSONArray();if(songs==null)return out;Library library=Library.get(c);
        for(int i=0;i<songs.length();i++){JSONObject s=songs.getJSONObject(i);if(mode.equals("search") && !s.optString("title").trim().toLowerCase(java.util.Locale.ROOT).startsWith(value.trim().toLowerCase(java.util.Locale.ROOT)))continue;Track t=new Track();String song=s.getString("id");t.id="nd_"+Library.key(account(c).getString("url")+":"+song);t.source="navidrome:"+Library.key(account(c).getString("url"));t.uri=new Uri.Builder().scheme("navidrome").authority("track").appendPath(song).appendQueryParameter("cover",s.optString("coverArt")).build().toString();t.albumId="nd:"+s.optString("albumId");t.title=s.optString("title","Untitled");t.artist=s.optString("artist");t.album=s.optString("album");t.year=s.optString("year");t.number=s.optInt("track");t.disc=s.optInt("discNumber");t.duration=s.optLong("duration")*1000;t.mime=s.optString("contentType");library.getWritableDatabase().insertWithOnConflict("tracks",null,t.values(0),android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);out.put(t.json(library.favourite(t.id)));}return out;
    }
    static JSONArray playlists(Context c)throws Exception{JSONArray input=api(account(c),"getPlaylists",new JSONObject()).getJSONObject("playlists").optJSONArray("playlist"),out=new JSONArray();if(input!=null)for(int i=0;i<input.length();i++){JSONObject p=input.getJSONObject(i);out.put(new JSONObject().put("id",p.getString("id")).put("name",p.optString("name")).put("count",p.optInt("songCount")).put("remote",true));}return out;}
    static MediaItem item(Context c,Track t){if(t==null)return null;if(!t.source.startsWith("navidrome:"))return t.mediaItem();try{JSONObject a=account(c);if(!t.source.equals("navidrome:"+Library.key(a.getString("url"))))return null;String id=Uri.parse(t.uri).getLastPathSegment();return t.mediaItem().buildUpon().setUri(url(a,"stream",new JSONObject().put("id",id).put("format","raw"))).build();}catch(Exception e){return null;}}
    static File cover(Context c,String coverId)throws Exception{JSONObject a=account(c);String id="";
        if(coverId.startsWith("nd_")&&Library.get(c).track(coverId)==null){JSONArray list=albums(c);for(int i=0;i<list.length();i++){JSONObject album=list.getJSONObject(i);if(coverId.equals(album.getString("coverId"))){JSONObject data=api(a,"getAlbum",new JSONObject().put("id",album.getString("id").substring(3))).getJSONObject("album");id=data.optString("coverArt");break;}}}
        else {Track t=Library.get(c).track(coverId);if(t!=null&&t.source.equals("navidrome:"+Library.key(a.getString("url"))))id=Uri.parse(t.uri).getQueryParameter("cover");}
        if(id==null||id.isEmpty())return null;File dir=new File(c.getCacheDir(),"server-covers");dir.mkdirs();File f=new File(dir,Library.key(a.getString("url")+":"+id)+".jpg");if(f.isFile()){f.setLastModified(System.currentTimeMillis());return f;}
        byte[] bytes=get(url(a,"getCoverArt",new JSONObject().put("id",id).put("size",512)),4*1024*1024);android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>2048||options.outHeight>2048)return null;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)return null;
        if(bitmap.getWidth()>2048||bitmap.getHeight()>2048){bitmap.recycle();return null;}try(FileOutputStream stream=new FileOutputStream(f)){bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,stream);}finally{bitmap.recycle();}
        File[] files=dir.listFiles();if(files!=null&&files.length>300){java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));for(int i=0;i<files.length-300;i++)files[i].delete();}return f;
    }
    private static String url(JSONObject a,String endpoint,JSONObject params)throws Exception{byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);String salt=hex(bytes),token=hex(MessageDigest.getInstance("MD5").digest((a.getString("password")+salt).getBytes("UTF-8")));
        Uri.Builder b=Uri.parse(a.getString("url")+"/rest/"+endpoint+".view").buildUpon().appendQueryParameter("u",a.getString("username")).appendQueryParameter("t",token).appendQueryParameter("s",salt).appendQueryParameter("v","1.16.1").appendQueryParameter("c","NaviGlassAndroid").appendQueryParameter("f","json");if("encoded".equals(a.optString("authMode"))) {b.clearQuery().appendQueryParameter("u",a.getString("username")).appendQueryParameter("p","enc:"+hex(a.getString("password").getBytes("UTF-8"))).appendQueryParameter("v","1.16.1").appendQueryParameter("c","NaviGlassAndroid").appendQueryParameter("f","json");}
        java.util.Iterator<String> keys=params.keys();while(keys.hasNext()){String key=keys.next();b.appendQueryParameter(key,params.getString(key));}return b.build().toString();}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}
    private static final class EndpointException extends IllegalStateException {
        EndpointException(){super("This URL is not a Navidrome API. Check the server path (for NaviGlass, use /navidrome).");}
    }
    static JSONObject api(JSONObject account,String endpoint,JSONObject params)throws Exception {
        byte[] bytes;
        try { bytes=get(url(account,endpoint,params),4*1024*1024); }
        catch(EndpointException e){throw e;}
        catch(java.net.SocketTimeoutException e){throw new IllegalStateException("Navidrome connection timed out. Check Tailscale is connected on this device and the server is reachable.");}
        catch(javax.net.ssl.SSLException e){throw new IllegalStateException("Could not verify the server HTTPS certificate. Check the server address and certificate; TLS protection has not been bypassed.");}
        catch(java.io.IOException e){throw new IllegalStateException("Cannot reach Navidrome. Check Wi-Fi/Tailscale on this device and the host/port. No login was saved.");}
        JSONObject root;
        try {root=new JSONObject(new String(bytes,"UTF-8")).getJSONObject("subsonic-response");}
        catch(org.json.JSONException e){throw new EndpointException();}
        if(!"ok".equals(root.optString("status"))) {
            int code=root.optJSONObject("error")==null?0:root.optJSONObject("error").optInt("code");
            if(code==40)throw new AuthenticationException();
            if(code==50)throw new IllegalStateException("Navidrome denied access to this account (code 50). Check the account's permissions.");
            throw new IllegalStateException("Navidrome refused this request (code "+code+"). Check the server and account permissions.");
        }
        return root;
    }
    static byte[] get(String address,int limit)throws Exception{HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","NaviGlassAndroid/"+BuildConfig.VERSION_NAME);try{int status=connection.getResponseCode();if(status==404)throw new EndpointException();if(status!=200)throw new IllegalStateException("The server returned HTTP "+status+". Check the server address/path and availability.");try(InputStream stream=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1){if(out.size()+count>limit)throw new IllegalStateException("Server response too large");out.write(buffer,0,count);}return out.toByteArray();}}finally{connection.disconnect();}}
}

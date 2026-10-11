package com.naviglass.android;
import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Only packaged or validated directory station IDs can request network playback. */
final class RadioDirectory {
    private static JSONArray stations;
    static synchronized JSONArray stations(Context context) throws Exception {
        if(stations==null){
            try(InputStream stream=context.getAssets().open("radio-stations.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;while((count=stream.read(buffer))!=-1){if(bytes.size()+count>65536)throw new IllegalStateException("Radio directory too large");bytes.write(buffer,0,count);}
                JSONArray parsed=new JSONArray(bytes.toString("UTF-8"));
                for(int i=0;i<parsed.length();i++){JSONObject station=parsed.getJSONObject(i);Uri uri=Uri.parse(station.getString("url"));
                    if(!station.getString("id").matches("radio:[a-z0-9]+")||!"https".equals(uri.getScheme())||!"ice6.somafm.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1)
                        throw new IllegalArgumentException("Invalid packaged radio station");}
                stations=parsed;
            }
        }
        JSONArray result=new JSONArray(stations.toString());for(int i=0;i<result.length();i++)result.getJSONObject(i).put("saved",context.getSharedPreferences("saved-radio",0).contains(result.getJSONObject(i).getString("id")));return result;
    }
    static JSONObject station(Context context,String id){try{JSONArray items=stations(context);for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("id").equals(id))return items.getJSONObject(i);String saved=context.getSharedPreferences("saved-radio",0).getString(id,context.getSharedPreferences("radio-directory",Context.MODE_PRIVATE).getString(id,""));if(!saved.isEmpty()&&id.matches("radio:[a-f0-9-]{36}"))return new JSONObject(saved);}catch(Exception ignored){}return null;}
    static MediaItem item(Context context,String id){JSONObject s=station(context,id);if(s==null)return null;
        MediaItem.Builder builder=new MediaItem.Builder().setMediaId(id).setUri(s.optString("url"));
        if(s.optBoolean("hls"))builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
        return builder
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(s.optString("title")).setArtist(s.optString("provider")+" · Live").build()).build();}
    static List<MediaItem> queue(Context context)throws Exception{ArrayList<MediaItem> result=new ArrayList<>();JSONArray items=stations(context);for(int i=0;i<items.length();i++)result.add(item(context,items.getJSONObject(i).getString("id")));return result;}
    static JSONObject state(Context context,String id){JSONObject s=station(context,id);if(s==null)return null;JSONObject output=new JSONObject();try{
        output.put("id",id).put("title",s.getString("title")).put("artist",s.getString("provider")+" · Live").put("radio",true).put("favourite",false).put("album","Internet radio").put("albumId","").put("duration",0);
    }catch(Exception ignored){}return output;}
    static JSONArray search(Context context,JSONObject request)throws Exception{
        Uri.Builder b=Uri.parse("https://de1.api.radio-browser.info/json/stations/search").buildUpon().appendQueryParameter("limit","60").appendQueryParameter("offset",String.valueOf(Math.max(0,request.optInt("offset")))).appendQueryParameter("hidebroken","true").appendQueryParameter("order","clickcount").appendQueryParameter("reverse","true");
        for(String field:new String[]{"name","country","language","tag"}){String value=request.optString(field).trim();if(value.length()>100)throw new IllegalArgumentException("Filter is too long");if(!value.isEmpty())b.appendQueryParameter(field,value);}
        byte[] response=null;
        // Official discovery, HTTPS only; fail over instead of relying on a single mirror.
        java.util.LinkedHashSet<String> hosts=new java.util.LinkedHashSet<>();
        try {for(java.net.InetAddress address:java.net.InetAddress.getAllByName("all.api.radio-browser.info")) {
            String host=address.getCanonicalHostName();if(host.matches("[a-z0-9-]+\\.api\\.radio-browser\\.info"))hosts.add(host);
        }}catch(java.net.UnknownHostException ignored){}
        hosts.add("de1.api.radio-browser.info");hosts.add("de2.api.radio-browser.info");
        java.util.ArrayList<String> mirrors=new java.util.ArrayList<>(hosts);java.util.Collections.shuffle(mirrors);
        for(String host:mirrors)try{response=NetworkSources.get(b.authority(host).build().toString(),2*1024*1024);break;}catch(Exception ignored){}
        if(response==null)throw new IllegalStateException("Radio directory unreachable. Check internet access, then retry. Starter stations remain available.");
        JSONArray result=new JSONArray(new String(response,"UTF-8")),out=new JSONArray();android.content.SharedPreferences prefs=context.getSharedPreferences("radio-directory",Context.MODE_PRIVATE);android.content.SharedPreferences.Editor save=prefs.edit();
        if(prefs.getAll().size()>600)save.clear();for(int i=0;i<result.length();i++){JSONObject s=result.getJSONObject(i);String uuid=s.optString("stationuuid"),address=s.optString("url_resolved",s.optString("url"));Uri uri=Uri.parse(address);String host=uri.getHost();
            if(!uuid.matches("[a-f0-9-]{36}")||!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||host.equals("localhost")||host.endsWith(".local")||host.matches("(127|10|0|192\\.168|169\\.254|172\\.(1[6-9]|2[0-9]|3[01]))\\..*")||host.contains(":"))continue;
            JSONObject station=new JSONObject().put("id","radio:"+uuid).put("title",s.optString("name","Radio")).put("genre",s.optString("tags")).put("provider",s.optString("country")).put("url",address).put("hls",s.optInt("hls")!=0).put("favicon",s.optString("favicon")).put("homepage",s.optString("homepage")).put("codec",s.optString("codec")).put("bitRate",s.optInt("bitrate")).put("saved",context.getSharedPreferences("saved-radio",0).contains("radio:"+uuid));out.put(station);save.putString(station.getString("id"),station.toString());}
        save.apply();return out;
    }
    static JSONArray saved(Context context)throws Exception{JSONArray result=new JSONArray();for(String value:context.getSharedPreferences("saved-radio",0).getAll().values().stream().map(String::valueOf).toArray(String[]::new)){JSONObject item=new JSONObject(value);item.put("saved",true);result.put(item);}return result;}
    static boolean save(Context context,String id,boolean save)throws Exception{JSONObject item=station(context,id);if(item==null)throw new IllegalArgumentException("Station unavailable");android.content.SharedPreferences prefs=context.getSharedPreferences("saved-radio",0);if(save){if(prefs.getAll().size()>=500&&!prefs.contains(id))throw new IllegalStateException("500 stations saved; remove one first");prefs.edit().putString(id,item.toString()).apply();}else prefs.edit().remove(id).apply();return save;}
    static java.io.File artwork(Context context,String id)throws Exception{JSONObject item=station(context,id);if(item==null)return null;String address=item.optString("favicon");if(address.isEmpty())return null;Uri uri=Uri.parse(address);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))return null;for(java.net.InetAddress resolved:java.net.InetAddress.getAllByName(host))if(resolved.isAnyLocalAddress()||resolved.isLoopbackAddress()||resolved.isLinkLocalAddress()||resolved.isSiteLocalAddress()||resolved.isMulticastAddress())return null;
        java.io.File folder=new java.io.File(context.getCacheDir(),"radio-art");folder.mkdirs();java.io.File out=new java.io.File(folder,Library.key(id+address)+".jpg");if(out.exists())return out;byte[] bytes=NetworkSources.get(address,3*1024*1024);android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>4096||options.outHeight>4096)return null;options.inJustDecodeBounds=false;options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/512);android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)return null;try(java.io.FileOutputStream stream=new java.io.FileOutputStream(out)){bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,stream);}finally{bitmap.recycle();}java.io.File[] cached=folder.listFiles();if(cached!=null&&cached.length>200){java.util.Arrays.sort(cached,java.util.Comparator.comparingLong(java.io.File::lastModified));for(int i=0;i<cached.length-200;i++)cached[i].delete();}return out;
    }

}

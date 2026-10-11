package com.naviglass.android;

import android.content.Context;
import android.media.MediaCodecList;
import android.net.Uri;
import androidx.media3.datasource.DataSpec;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Resolve authenticated streams on the ExoPlayer loader, never the UI thread or WebView. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class NavidromeAudio {
    private NavidromeAudio() {}
    static JSONObject profile() throws Exception {
        JSONArray direct=new JSONArray();
        String[][] formats={{"mp3","mp3","audio/mpeg"},{"mp4","aac","audio/mp4a-latm"},{"flac","flac","audio/flac"},{"ogg","opus","audio/opus"},{"ogg","vorbis","audio/vorbis"},{"wav","wav","audio/raw"},{"mp4","alac","audio/alac"}};
        for(String[] f:formats)if(new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(android.media.MediaFormat.createAudioFormat(f[2],44100,2))!=null)
            direct.put(new JSONObject().put("containers",new JSONArray().put(f[0])).put("audioCodecs",new JSONArray().put(f[1])).put("protocols",new JSONArray().put("http")).put("maxAudioChannels",2));
        // Lossless FLAC first; MP3 remains available on servers without a FLAC preset.
        JSONArray transcode=new JSONArray().put(new JSONObject().put("container","flac").put("audioCodec","flac").put("protocol","http").put("maxAudioChannels",2)).put(new JSONObject().put("container","mp3").put("audioCodec","mp3").put("protocol","http").put("maxAudioChannels",2));
        return new JSONObject().put("name","NaviGlass Android").put("directPlayProfiles",direct).put("transcodingProfiles",transcode);
    }
    static DataSpec resolve(Context context,DataSpec spec)throws java.io.IOException {
        if(!"navidrome".equals(spec.uri.getScheme()))return spec;
        try {
            JSONObject account=NetworkSources.account(context);if(!Library.key(account.getString("url")+":"+account.getString("username")).equals(spec.uri.getQueryParameter("account")))throw new java.io.IOException("This song belongs to another account");String id=spec.uri.getLastPathSegment();
            JSONObject parameters=new JSONObject().put("mediaId",id).put("mediaType","song");
            HttpURLConnection connection=(HttpURLConnection)new URL(NetworkSources.url(account,"getTranscodeDecision",parameters)).openConnection();
            connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
            JSONObject decision=null;int status;
            try {
                byte[] body=profile().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);try(java.io.OutputStream out=connection.getOutputStream()){out.write(body);}status=connection.getResponseCode();
                if(status==200)try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new java.io.IOException("Playback response too large");out.write(b,0,n);}JSONObject root=new JSONObject(out.toString("UTF-8")).getJSONObject("subsonic-response");if(!"ok".equals(root.optString("status")))throw new java.io.IOException("Navidrome refused playback negotiation");decision=root.optJSONObject("transcodeDecision");}
            }finally{connection.disconnect();}
            if(decision!=null){String token=decision.optString("transcodeParams");if(token.isEmpty()||!decision.optBoolean("canDirectPlay")&&!decision.optBoolean("canTranscode"))throw new java.io.IOException("Navidrome could not supply a playable audio format");return spec.withUri(Uri.parse(NetworkSources.url(account,"getTranscodeStream",parameters.put("transcodeParams",token))));}
            if(status!=404&&status!=405&&status!=501&&decision==null)throw new java.io.IOException("Navidrome playback negotiation failed");
            // Older servers: let Navidrome transcode rather than sending unsupported ALAC to Android.
            Track track=null;try(android.database.Cursor rows=Library.get(context).getReadableDatabase().query("tracks",null,"uri=?",new String[]{spec.uri.toString()},null,null,null)){if(rows.moveToFirst())track=Track.read(rows);}
            String mime=track==null?"":track.mime.toLowerCase(java.util.Locale.ROOT);
            boolean raw=mime.equals("audio/mpeg")||mime.equals("audio/flac")||mime.equals("audio/x-flac")||mime.equals("audio/wav")||mime.equals("audio/x-wav")||mime.equals("audio/ogg")||mime.equals("audio/opus")||mime.equals("audio/aac");
            return spec.withUri(Uri.parse(NetworkSources.url(account,"stream",new JSONObject().put("id",id).put("format",raw?"raw":"flac"))));
        }catch(Exception e){throw new java.io.IOException("Cannot obtain playable Navidrome audio. Check connectivity and the server's transcoding settings.");}
    }
}

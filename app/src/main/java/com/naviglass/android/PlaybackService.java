package com.naviglass.android;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Audio lives outside the WebView and continues with the screen locked. */
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private final Runnable checkpoint=new Runnable(){public void run(){save();handler.postDelayed(this,5000);}};
    @Override public void onCreate() {
        super.onCreate();prefs=getSharedPreferences("playback",MODE_PRIVATE);
        player=new ExoPlayer.Builder(this).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build();
        session=new MediaSession.Builder(this,player).setSessionActivity(PendingIntent.getActivity(this,0,
            new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
            .setCallback(new MediaSession.Callback(){
                @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
                @Override public MediaSession.ConnectionResult onConnect(MediaSession s,MediaSession.ControllerInfo c){
                    if(!getPackageName().equals(c.getPackageName())&&!c.isTrusted())return MediaSession.ConnectionResult.reject();
                    return MediaSession.Callback.super.onConnect(s,c);
                }
                @Override public ListenableFuture<List<MediaItem>> onAddMediaItems(MediaSession s,
                    MediaSession.ControllerInfo c,List<MediaItem> requested) {
                    ArrayList<MediaItem> safe=new ArrayList<>();
                    if(requested.size()>10000)return Futures.immediateFailedFuture(new IllegalArgumentException("Queue too large"));
                    for(MediaItem item:requested){Track t=Library.get(PlaybackService.this).track(item.mediaId);
                        if(t==null)return Futures.immediateFailedFuture(new IllegalArgumentException("Song unavailable; refresh library"));
                        safe.add(t.mediaItem());}
                    return Futures.immediateFuture(safe);
                }
            }).build();
        restore();
        player.addListener(new Player.Listener(){@Override public void onEvents(Player p,Player.Events events){save();}});
        handler.postDelayed(checkpoint,5000);
    }
    private void restore(){
        try {JSONArray saved=new JSONArray(prefs.getString("queue","[]"));ArrayList<MediaItem> items=new ArrayList<>();
            for(int i=0;i<saved.length()&&i<10000;i++){Track t=Library.get(this).track(saved.getString(i));if(t!=null)items.add(t.mediaItem());}
            if(!items.isEmpty())player.setMediaItems(items,Math.min(Math.max(0,prefs.getInt("index",0)),items.size()-1),Math.max(0,prefs.getLong("position",0)));
            player.setShuffleModeEnabled(prefs.getBoolean("shuffle",false));player.setRepeatMode(prefs.getInt("repeat",Player.REPEAT_MODE_OFF));
            // Restoring a session never starts audio unexpectedly.
        }catch(Exception ignored){}
    }
    private void save(){if(player==null)return;JSONArray ids=new JSONArray();for(int i=0;i<player.getMediaItemCount();i++)ids.put(player.getMediaItemAt(i).mediaId);
        prefs.edit().putString("queue",ids.toString()).putInt("index",player.getCurrentMediaItemIndex())
            .putLong("position",Math.max(0,player.getCurrentPosition())).putBoolean("shuffle",player.getShuffleModeEnabled())
            .putInt("repeat",player.getRepeatMode()).apply();}
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller){return session;}
    @Override public void onTaskRemoved(Intent rootIntent){if(!player.getPlayWhenReady())stopSelf();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);save();session.release();player.release();super.onDestroy();}
}

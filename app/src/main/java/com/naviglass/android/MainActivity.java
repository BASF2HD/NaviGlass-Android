package com.naviglass.android;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends androidx.activity.ComponentActivity {
    static final String ORIGIN="https://appassets.androidplatform.net";
    private WebView web;
    private JavaScriptReplyProxy bridge;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean scanning,foreground;
    private JSONObject scanStatus;
    private String scanError="";
    private final Runnable ticker=new Runnable(){public void run(){if(foreground){state();handler.postDelayed(this,500);}}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);web=new WebView(this);
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.setBackgroundColor(0xff08080f);root.addView(web);setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout()|androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){web.evaluateJavascript("window.closePanel && window.closePanel()",value->{
                if(!"true".equals(value)){setEnabled(false);getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}
            });}
        });
        web.setBackgroundColor(0xff08080f);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/art/",path->{
                if(!path.matches("[a-zA-Z0-9_-]{1,80}\\.jpg"))return missing();
                File f=Library.get(this).artwork(path.substring(0,path.length()-4));
                try {return f==null?missing():new WebResourceResponse("image/jpeg",null,new FileInputStream(f));}catch(Exception e){return missing();}
            }).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();if(!"https".equals(u.getScheme())||!"appassets.androidplatform.net".equals(u.getHost()))return missing();
                WebResourceResponse response=assets.shouldInterceptRequest(u);return response==null?missing():response;
            }
        });
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
            android.widget.TextView message=new android.widget.TextView(this);message.setText("Please update Android System WebView, then reopen NaviGlass.");
            message.setTextColor(0xffc8cedb);message.setPadding(24,80,24,24);setContentView(message);return;
        }
        WebViewCompat.addWebMessageListener(web,"nativeBridge",Collections.singleton(ORIGIN),(v,message,origin,isMain,reply)->{
            if(!isMain||!ORIGIN.equals(origin.toString()))return;bridge=reply;
            try{String raw=message.getData();if(raw==null||raw.length()>1048576)throw new IllegalArgumentException("Request too large");
                JSONObject request=new JSONObject(raw);try{handle(request);}catch(Exception e){failure(request.optString("id"),e);}
            }catch(Exception e){event("error",new JSONObject(),friendly(e));}
        });
        controllerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        controllerFuture.addListener(()->{try{player=controllerFuture.get();player.addListener(new Player.Listener(){
            @Override public void onEvents(Player p,Player.Events events){state();}
        });state();}catch(Exception e){event("error",new JSONObject(),"Cannot start audio service");}},getMainExecutor());
        web.loadUrl(ORIGIN+"/assets/index.html");
    }
    private static WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private void handle(JSONObject request)throws Exception {
        String id=request.optString("id");if(id.length()>80)throw new IllegalArgumentException("Invalid request");
        String action=request.optString("action");
        switch(action){
            case "ready": respond(id,new JSONObject().put("native",true).put("scanning",scanning));catalog();state();if(scanStatus!=null)event("progress",scanStatus,scanError);break;
            case "device": permissionAndScan();respond(id,new JSONObject());break;
            case "folder": chooseFolder();respond(id,new JSONObject());break;
            case "rescan": if(scanning)throw new IllegalStateException("A scan is already running");
                String tree=getPreferences(MODE_PRIVATE).getString("tree","");if(tree.isEmpty())permissionAndScan();else scan(Uri.parse(tree));respond(id,new JSONObject());break;
            case "cancel": cancelled.set(true);respond(id,new JSONObject());break;
            case "play":
                requirePlayer();JSONArray ids=request.getJSONArray("ids");if(ids.length()==0||ids.length()>10000)throw new IllegalArgumentException("Choose 1–10000 songs");
                ArrayList<MediaItem> items=new ArrayList<>();for(int i=0;i<ids.length();i++){Track t=Library.get(this).track(ids.getString(i));if(t==null)throw new IllegalArgumentException("Song unavailable; refresh library");items.add(t.mediaItem());}
                int index=request.optInt("index",0);if(index<0||index>=items.size())throw new IllegalArgumentException("Invalid song selection");
                player.setMediaItems(items,index,0);player.prepare();player.play();respond(id,new JSONObject());break;
            case "toggle": requirePlayer();if(player.isPlaying())player.pause();else{player.prepare();player.play();}respond(id,new JSONObject());break;
            case "next": requirePlayer();player.seekToNextMediaItem();respond(id,new JSONObject());break;
            case "previous": requirePlayer();player.seekToPrevious();respond(id,new JSONObject());break;
            case "seek": requirePlayer();long duration=player.getDuration();long position=Math.max(0,request.optLong("position"));player.seekTo(duration>0?Math.min(position,duration):position);respond(id,new JSONObject());break;
            case "shuffle": requirePlayer();player.setShuffleModeEnabled(!player.getShuffleModeEnabled());respond(id,new JSONObject());break;
            case "repeat": requirePlayer();player.setRepeatMode((player.getRepeatMode()+1)%3);respond(id,new JSONObject());break;
            default:
                worker.execute(()->{try{
                    Library library=Library.get(this);JSONObject result=new JSONObject();
                    switch(action){
                        case "tracks": String mode=request.optString("mode","album");if(!java.util.Arrays.asList("album","favourites","playlist","search").contains(mode))throw new IllegalArgumentException("Unknown view");
                            result.put("tracks",library.tracks(mode,request.optString("value").substring(0,Math.min(request.optString("value").length(),200))));break;
                        case "favourite":result.put("favourite",library.toggleFavourite(request.getString("track")));break;
                        case "playlists":result.put("playlists",library.playlists());break;
                        case "createPlaylist":result.put("playlist",library.createPlaylist(request.getString("name")));break;
                        case "addPlaylist":library.addToPlaylist(request.getString("playlist"),request.getString("track"));break;
                        default:throw new IllegalArgumentException("Unknown action");
                    }
                    runOnUiThread(()->respond(id,result));
                }catch(Exception e){runOnUiThread(()->failure(id,e));}});
        }
    }
    private void requirePlayer(){if(player==null)throw new IllegalStateException("Audio service is still connecting");}
    private void respond(String id,JSONObject data){try{send(new JSONObject().put("type","response").put("id",id).put("data",data));}catch(Exception ignored){}}
    private void failure(String id,Exception e){try{send(new JSONObject().put("type","response").put("id",id).put("error",friendly(e)));}catch(Exception ignored){}}
    private String friendly(Exception e){if(e instanceof SecurityException)return "Library access expired or was denied. Choose the folder again or grant music permission.";
        if(e instanceof java.util.concurrent.CancellationException)return "Scan cancelled. Previously indexed songs were kept.";
        String text=e.getMessage();return text==null?"Operation failed; try choosing the source again.":text.substring(0,Math.min(240,text.length()));}
    private void event(String type,JSONObject data,String error){try{send(new JSONObject().put("type",type).put("data",data).put("error",error));}catch(Exception ignored){}}
    private void send(JSONObject data){if(bridge!=null&&!isDestroyed()&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))bridge.postMessage(data.toString());}
    private void state(){if(player==null||bridge==null||!foreground)return;try{
        JSONObject s=new JSONObject().put("playing",player.isPlaying()).put("buffering",player.getPlaybackState()==Player.STATE_BUFFERING)
            .put("position",Math.max(0,player.getCurrentPosition())).put("duration",Math.max(0,player.getDuration()==C.TIME_UNSET?0:player.getDuration()))
            .put("shuffle",player.getShuffleModeEnabled()).put("repeat",player.getRepeatMode());
        MediaItem current=player.getCurrentMediaItem();Track t=current==null?null:Library.get(this).track(current.mediaId);
        if(t!=null){s.put("track",t.json(Library.get(this).favourite(t.id)));if(s.optLong("duration")==0)s.put("duration",t.duration);}
        if(player.getPlayerError()!=null)s.put("error","This song could not be played. Check source access and audio format.");
        event("state",s,"");
    }catch(Exception ignored){}}
    private void catalog(){worker.execute(()->{try{JSONObject data=new JSONObject().put("albums",Library.get(this).albums());runOnUiThread(()->event("catalog",data,""));}
        catch(Exception e){runOnUiThread(()->event("error",new JSONObject(),friendly(e)));}});}
    private void permissionAndScan(){if(scanning){event("error",new JSONObject(),"A scan is already running");return;}
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED)scan(null);else requestPermissions(new String[]{permission},41);}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);
        if(code==41){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)scan(null);else event("error",new JSONObject(),"Music permission denied. You can still choose a specific folder.");}}
    private void chooseFolder(){if(scanning)throw new IllegalStateException("Wait for this scan or cancel it first");
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),42);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri tree=data.getData();try{
            getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getPreferences(MODE_PRIVATE).edit().putString("tree",tree.toString()).apply();scan(tree);
        }catch(Exception e){event("error",new JSONObject(),friendly(e));}}}
    private void scan(Uri tree){if(scanning)return;scanning=true;cancelled.set(false);scanError="";progress(0,0,0,"Starting scan",false);
        worker.execute(()->{try {LibraryScanner scanner=new LibraryScanner(this,Library.get(this),cancelled);
            LibraryScanner.Progress updates=(checked,indexed,skipped,label)->runOnUiThread(()->progress(checked,indexed,skipped,label,false));
            if(tree==null)scanner.device(updates);else scanner.folder(tree,updates);
            runOnUiThread(()->{scanning=false;progressDone("");catalog();
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},43);});
        }catch(Exception e){runOnUiThread(()->{scanning=false;progressDone(friendly(e));});}});
    }
    private void progress(long checked,long indexed,long skipped,String label,boolean done){try{scanStatus=new JSONObject().put("checked",checked).put("indexed",indexed).put("skipped",skipped).put("label",label).put("done",done);event("progress",scanStatus,scanError);}catch(Exception ignored){}}
    private void progressDone(String error){scanError=error;progress(scanStatus.optLong("checked"),scanStatus.optLong("indexed"),scanStatus.optLong("skipped"),error.isEmpty()?"Library ready":"Scan stopped",true);}
    @Override protected void onResume(){super.onResume();foreground=true;web.onResume();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){foreground=false;handler.removeCallbacks(ticker);web.onPause();super.onPause();}
    @Override protected void onDestroy(){foreground=false;cancelled.set(true);handler.removeCallbacksAndMessages(null);worker.shutdownNow();
        if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);if(web!=null){bridge=null;web.destroy();}super.onDestroy();}
}

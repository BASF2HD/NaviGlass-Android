package com.naviglass.android;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import org.json.JSONObject;

/** Bounded, account/generation-scoped metadata cache. Never stores audio or credentials. */
final class CollectionCache {
    private static final Object[] LOCKS = new Object[16];
    static { for (int i=0;i<LOCKS.length;i++) LOCKS[i]=new Object(); }
    interface Loader { JSONObject load() throws Exception; }
    static JSONObject get(Context context,String key,Loader loader)throws Exception {
        String name=Library.key(key);
        synchronized(LOCKS[(name.hashCode()&0x7fffffff)%LOCKS.length]) {
            File directory=new File(context.getCacheDir(),"collection-metadata");directory.mkdirs();
            File file=new File(directory,name+".json");
            if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<1800000) {
                try {return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
                catch(Exception corrupt){file.delete();}
            }
            JSONObject result=loader.load();
            byte[] bytes=result.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length<=4*1024*1024) {
                // Atomic replacement prevents another reader observing a partial response.
                File pending=File.createTempFile("metadata-",".tmp",directory);
                try {Files.write(pending.toPath(),bytes);Files.move(pending.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                finally {pending.delete();}
                prune(directory);
            }
            return result;
        }
    }
    private static synchronized void prune(File directory) {
        File[] files=directory.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        long size=0;for(File file:files)size+=file.length();
        int count=files.length;
        for(File file:files) {if(count<=128&&size<=32*1024*1024)break;long length=file.length();if(file.delete()){count--;size-=length;}}
    }
}

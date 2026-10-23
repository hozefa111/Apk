package com.hozefa.threepattipro.nativeapp;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** App-private, disposable cache. Call only from the gateway's worker executor.
 * Each coherent tracker snapshot commits changed rows/deletions and its completeness
 * marker in one transaction. Never contains a pending write queue or replays writes.
 */
final class TrackerCache extends SQLiteOpenHelper {
    private Map<String,String> persisted;
    TrackerCache(Context context){this(context,"3patti_tracker_cache_v1.db");}
    TrackerCache(Context context,String name){super(context,name,null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE records (path TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE partitions (name TEXT PRIMARY KEY NOT NULL, boundaries TEXT NOT NULL)");
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported cache version");}
    Map<String,String> read(){
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor marker=db.rawQuery("SELECT value FROM metadata WHERE key='complete'",null)){
            if(!marker.moveToFirst()){persisted=new LinkedHashMap<>();return null;}
        }
        Map<String,String> result=new LinkedHashMap<>();
        try(Cursor rows=db.rawQuery("SELECT path,payload FROM records ORDER BY path",null)){
            while(rows.moveToNext())result.put(rows.getString(0),rows.getString(1));
        }
        persisted=result;return new LinkedHashMap<>(result);
    }
    Map<String,List<String>> boundaries(){
        Map<String,List<String>> result=new HashMap<>();
        try(Cursor rows=getReadableDatabase().rawQuery("SELECT name,boundaries FROM partitions",null)){
            while(rows.moveToNext()){
                org.json.JSONArray array=new org.json.JSONArray(rows.getString(1));List<String> values=new ArrayList<>();
                for(int i=0;i<array.length();i++)values.add(array.getString(i));result.put(rows.getString(0),values);
            }
        }catch(org.json.JSONException invalid){return Collections.emptyMap();}
        return result;
    }
    /** Returns exactly how many individual record rows were changed. */
    int write(Map<String,String> next){return write(next,Collections.emptyMap());}
    int write(Map<String,String> next,Map<String,List<String>> boundaries){
        if(persisted==null)read();
        SQLiteDatabase db=getWritableDatabase();int changed=0;
        db.beginTransaction();
        try{
            for(Map.Entry<String,String> row:next.entrySet())if(!Objects.equals(persisted.get(row.getKey()),row.getValue())){
                ContentValues value=new ContentValues();value.put("path",row.getKey());value.put("payload",row.getValue());
                if(db.insertWithOnConflict("records",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cache row rejected");changed++;
            }
            for(String path:persisted.keySet())if(!next.containsKey(path)){db.delete("records","path=?",new String[]{path});changed++;}
            db.delete("partitions",null,null);
            for(Map.Entry<String,List<String>> range:boundaries.entrySet()){
                ContentValues value=new ContentValues();value.put("name",range.getKey());value.put("boundaries",new org.json.JSONArray(range.getValue()).toString());
                if(db.insertWithOnConflict("partitions",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cache boundaries rejected");
            }
            ContentValues marker=new ContentValues();marker.put("key","complete");marker.put("value",System.currentTimeMillis());
            if(db.insertWithOnConflict("metadata",null,marker,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cache marker rejected");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        persisted=new LinkedHashMap<>(next);return changed;
    }
}

package com.hozefa.threepattipro.nativeapp;
import java.time.Instant;
import java.util.*;
/** Firestore query ordering, preserving sub-millisecond timestamps and document-ID ties. */
public final class GameOrder {
    private GameOrder(){}
    private static Instant instant(Object value){try{return Instant.parse(String.valueOf(value));}catch(Exception invalid){return Instant.EPOCH;}}
    private static String id(Map<String,Object> row){return String.valueOf(row.get("id"));}
    public static final Comparator<Map<String,Object>> PLAYERS=(a,b)->{
        int time=instant(a.get("createdAt")).compareTo(instant(b.get("createdAt")));return time!=0?time:id(a).compareTo(id(b));
    };
    public static final Comparator<Map<String,Object>> ROUNDS=(a,b)->{
        int time=instant(b.get("timestamp")).compareTo(instant(a.get("timestamp")));return time!=0?time:id(b).compareTo(id(a));
    };
    public static final Comparator<Map<String,Object>> SESSIONS=(a,b)->{
        boolean left=a.get("endTime")==null,right=b.get("endTime")==null;
        if(left!=right)return left?-1:1;
        String key=left?"startTime":"endTime";return instant(b.get(key)).compareTo(instant(a.get(key)));
    };
}

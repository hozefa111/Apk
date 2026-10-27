package com.hozefa.threepattipro.nativeapp;
import java.util.*;
/** Metadata pointer only; no scoring, private cards or live room state. */
final class ManualSessionGuard {
 static boolean active(String id,Map<String,Object> row){return row!=null&&!"online".equals(row.get("source"))&&!id.startsWith("cf2_")&&row.get("endTime")==null&&!Arrays.asList("stopped","deleted","deleting").contains(row.get("status"));}
 static String next(String pointer,Map<String,Map<String,Object>> after){
  String current=pointer;if(current!=null&&after.containsKey(current)&&!active(current,after.get(current)))current=null;
  for(Map.Entry<String,Map<String,Object>> item:after.entrySet())if(active(item.getKey(),item.getValue())){if(current!=null&&!current.equals(item.getKey()))throw new IllegalStateException("A manual session is already active. Refresh before starting another.");current=item.getKey();}
  return current;
 }
}

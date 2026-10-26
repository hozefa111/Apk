package com.hozefa.threepattipro.nativeapp;
import java.nio.charset.StandardCharsets;import java.nio.ByteBuffer;import java.security.MessageDigest;import java.util.*;
/** Integrity of local bytes AND checkpoint. No Firebase operations or scoring. */
final class TrackerCacheIntegrity {
 final String sha256;final Map<String,Long> counts=new LinkedHashMap<>();final long total;
 TrackerCacheIntegrity(Map<String,String> rows,long epoch,long revision){
  try{MessageDigest hash=MessageDigest.getInstance("SHA-256");add(hash,"tracker-cache-v1:"+epoch+":"+revision);for(String c:Arrays.asList("players","rounds","sessions"))counts.put(c,0L);
   for(String path:new TreeSet<>(rows.keySet())){if(!path.matches("^(players|rounds|sessions)/[a-zA-Z0-9_-]{1,240}$"))throw new IllegalArgumentException("Invalid cache path");String c=path.substring(0,path.indexOf('/'));counts.put(c,counts.get(c)+1);add(hash,path);add(hash,rows.get(path));}
   total=rows.size();StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));sha256=out.toString();
  }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private static void add(MessageDigest hash,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);}
}

package com.hozefa.threepattipro.nativeapp;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Shared by automatic reads, manual refresh and foreground resumes. Never replays writes. */
final class SyncRetryPolicy {
    private long retryAt;
    private boolean quota;
    private int quotaFailures,networkFailures;
    SyncRetryPolicy(long now,long savedDeadline,int savedFailures){
        quotaFailures=Math.max(0,Math.min(savedFailures,16));
        if(savedDeadline>now){retryAt=Math.min(savedDeadline,now+86400000L);quota=true;}
    }
    synchronized long remaining(long now){return Math.max(0,retryAt-now);}
    synchronized long deadline(){return retryAt;}
    synchronized boolean quota(){return quota;}
    synchronized int quotaFailures(){return quotaFailures;}
    synchronized boolean rateLimited(long now,String retryAfter){
        long server=retryAfterMillis(retryAfter,now);
        if(quota&&retryAt>now){retryAt=Math.max(retryAt,now+server);return false;}
        long delay=Math.min(3600000L,60000L*(1L<<Math.min(quotaFailures,6)));
        quotaFailures=Math.min(16,quotaFailures+1);quota=true;
        retryAt=now+Math.max(delay,server);return true;
    }
    synchronized void unavailable(long now){
        if(quota&&retryAt>now)return;
        quota=false;networkFailures=Math.min(networkFailures+1,5);
        retryAt=now+Math.min(300000L,15000L*(1L<<networkFailures));
    }
    synchronized void succeeded(){retryAt=0;quota=false;quotaFailures=networkFailures=0;}
    static long retryAfterMillis(String value,long now){
        if(value==null)return 0;
        try{return Math.max(0,Math.min(86400L,Long.parseLong(value.trim())))*1000L;}
        catch(NumberFormatException ignored){
            try{return Math.max(0,Math.min(86400000L,ZonedDateTime.parse(value.trim(),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now));}
            catch(RuntimeException invalid){return 0;}
        }
    }
}

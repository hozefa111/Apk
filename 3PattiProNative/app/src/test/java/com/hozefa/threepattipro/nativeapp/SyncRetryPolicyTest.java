package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import static org.junit.Assert.*;
public class SyncRetryPolicyTest {
    @Test public void quotaStartsAtOneMinuteAndDuplicateFailuresDoNotExtendIt(){
        SyncRetryPolicy p=new SyncRetryPolicy(1000,0,0);assertTrue(p.rateLimited(1000,null));assertEquals(60000,p.remaining(1000));
        assertFalse(p.rateLimited(2000,null));assertEquals(61000,p.deadline());assertEquals(1,p.quotaFailures());
    }
    @Test public void quotaBackoffIncreasesAndCapsAtOneHour(){
        SyncRetryPolicy p=new SyncRetryPolicy(0,0,0);long now=1;
        for(int i=0;i<10;i++){p.rateLimited(now,null);assertEquals(Math.min(3600000,60000L*(1L<<Math.min(i,6))),p.remaining(now));now=p.deadline()+1;}
    }
    @Test public void validRetryAfterSecondsAndHttpDateAreRespected(){
        SyncRetryPolicy p=new SyncRetryPolicy(0,0,0);p.rateLimited(1000,"120");assertEquals(120000,p.remaining(1000));
        assertEquals(120000,SyncRetryPolicy.retryAfterMillis("Thu, 01 Jan 1970 00:02:00 GMT",0));
        assertEquals(86400000,SyncRetryPolicy.retryAfterMillis("999999999",0));
    }
    @Test public void malformedAndPastRetryAfterFallBackWithoutCrashing(){
        for(String v:new String[]{null,"oops","-5","999999999999999999999999999","Wed, 31 Dec 1969 23:59:00 GMT"})assertEquals(0,SyncRetryPolicy.retryAfterMillis(v,0));
    }
    @Test public void recreationRetainsQuotaAndItsAttemptCount(){
        SyncRetryPolicy p=new SyncRetryPolicy(1,0,0);p.rateLimited(1,null);
        SyncRetryPolicy restored=new SyncRetryPolicy(1000,p.deadline(),p.quotaFailures());assertTrue(restored.quota());assertEquals(59001,restored.remaining(1000));
        restored.rateLimited(61002,null);assertEquals(120000,restored.remaining(61002));
    }
    @Test public void successfulServerSnapshotClearsBothCooldowns(){
        SyncRetryPolicy p=new SyncRetryPolicy(0,0,0);p.rateLimited(1,null);p.succeeded();assertFalse(p.quota());assertEquals(0,p.remaining(1));assertEquals(0,p.quotaFailures());
        p.unavailable(2);assertEquals(30000,p.remaining(2));p.succeeded();assertEquals(0,p.deadline());
    }
    @Test public void genericNetworkFailureCannotShortenActiveQuota(){
        SyncRetryPolicy p=new SyncRetryPolicy(0,0,0);p.rateLimited(1000,"3600");p.unavailable(2000);assertTrue(p.quota());assertEquals(3601000,p.deadline());
    }
    @Test public void invalidStoredDeadlineIsBoundedAndExpiredDeadlineDoesNotBlock(){
        SyncRetryPolicy p=new SyncRetryPolicy(1000,Long.MAX_VALUE,999);assertEquals(86400000,p.remaining(1000));
        p=new SyncRetryPolicy(1000,500,2);assertFalse(p.quota());assertEquals(0,p.remaining(1000));
    }
}

package com.hozefa.threepattipro.nativeapp;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MotionEventsTest {
    private final long now=1_000_000;
    private Map<String,Long> rounds(Object... items) {
        Map<String,Long> result=new HashMap<>();for(int i=0;i<items.length;i+=2)result.put((String)items[i],(Long)items[i+1]);return result;
    }
    @Test public void initialLiveSnapshotAndCachedSnapshotAreNotEvents() {
        MotionEvents e=new MotionEvents();
        assertFalse(e.update(rounds("a",now),false,now));
        assertFalse(e.update(rounds("a",now,"b",now),true,now));
        assertFalse(e.update(rounds("a",now,"b",now),true,now));
    }
    @Test public void onlyNewRecentRoundAfterPrimingIsAnEvent() {
        MotionEvents e=new MotionEvents();e.update(rounds("a",now),true,now);
        assertTrue(e.update(rounds("a",now,"b",now),true,now));
        assertFalse(e.update(rounds("a",now,"b",now),true,now));
    }
    @Test public void backfillAndBadFutureDatesDoNotCelebrate() {
        MotionEvents e=new MotionEvents();e.update(rounds(),true,now);
        assertFalse(e.update(rounds("old",now-120001,"future",now+5001,"invalid",0L),true,now));
    }
    @Test public void reconnectSeedsBeforeNewLiveChanges() {
        MotionEvents e=new MotionEvents();e.update(rounds("a",now),true,now);e.invalidate();
        assertFalse(e.update(rounds("a",now,"b",now),true,now));
        assertTrue(e.update(rounds("a",now,"b",now,"c",now),true,now));
    }
    @Test public void backgroundChangesAreConsumedInsteadOfQueued() {
        MotionEvents e=new MotionEvents();e.update(rounds("a",now),true,now);
        assertFalse(e.update(rounds("a",now,"b",now),false,now));
        assertFalse(e.update(rounds("a",now,"b",now,"c",now),true,now));
        assertFalse(e.update(rounds("a",now,"b",now,"c",now),true,now));
    }
    @Test public void refreshAndTimestampResolutionDoNotReplayRound() {
        MotionEvents e=new MotionEvents();e.update(rounds("a",now-1),true,now);
        assertFalse(e.update(rounds("a",now),true,now));
        assertFalse(e.update(rounds(),true,now));
    }
    @Test public void legitimateClockSkewAndWindowBoundaryAreAllowed() {
        MotionEvents e=new MotionEvents();e.update(rounds(),true,now);
        assertTrue(e.update(rounds("a",now+5000,"b",now-120000),true,now));
    }
    @Test public void recentlyDeletedAndRestoredRoundIsNotANewWin() {
        MotionEvents e=new MotionEvents();e.update(rounds("a",now),true,now);
        assertFalse(e.update(rounds(),true,now+1000));
        assertFalse(e.update(rounds("a",now),true,now+2000));
        assertTrue(e.update(rounds("a",now,"b",now+3000),true,now+3000));
    }
}

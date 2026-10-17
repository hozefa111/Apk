package com.hozefa.threepattipro.nativeapp;

import java.util.*;

/** Snapshot acknowledgement, never a queue: cache/reconnect/backfill must not celebrate. */
final class MotionEvents {
    private Set<String> previous = Collections.emptySet();
    private final Map<String,Long> recentlySeen = new HashMap<>();
    private boolean primed;
    void invalidate() { primed = false; }
    boolean update(Map<String,Long> rounds, boolean liveForeground, long now) {
        recentlySeen.entrySet().removeIf(entry->entry.getValue()<now-125_000);
        boolean changed = false;
        if (primed && liveForeground) for (Map.Entry<String,Long> entry : rounds.entrySet()) {
            long at = entry.getValue();
            if (!previous.contains(entry.getKey()) && !recentlySeen.containsKey(entry.getKey()) && at > 0 && at <= now + 5_000 && at >= now - 120_000) changed = true;
        }
        for(Map.Entry<String,Long> entry:rounds.entrySet())if(entry.getValue()>0&&entry.getValue()>=now-120_000&&entry.getValue()<=now+5_000)recentlySeen.putIfAbsent(entry.getKey(),entry.getValue());
        previous = new HashSet<>(rounds.keySet());
        primed = liveForeground;
        return changed;
    }
}

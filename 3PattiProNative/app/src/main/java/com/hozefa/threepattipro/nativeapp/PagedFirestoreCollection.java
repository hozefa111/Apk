package com.hozefa.threepattipro.nativeapp;

import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Bounded live cursors. All callbacks run on one supplied executor, never the UI thread.
 * Document ID ordering includes legacy rows without timestamps and needs no custom index.
 * A moved page boundary invalidates the suffix before a coherent view can be published.
 */
final class PagedFirestoreCollection implements AutoCloseable {
    interface Observer {
        void changed(List<QuerySnapshot> pages, boolean complete, int loaded);
        void failed(FirebaseFirestoreException error);
        default void requested() {}
        default void response(long elapsedNanos) {}
    }
    private final Query base;
    private final int size;
    private final Executor executor;
    private final Observer observer;
    private final List<Page> pages=new ArrayList<>();
    private boolean closed;
    private static final class Page {
        final String cursor;
        ListenerRegistration registration;
        QuerySnapshot snapshot;
        final long started=System.nanoTime();
        boolean measured;
        Page(String cursor){this.cursor=cursor;}
    }
    PagedFirestoreCollection(Query base,int size,Executor executor,Observer observer){
        if(size<2||size>500)throw new IllegalArgumentException("Page size must be 2–500");
        this.base=base.orderBy(FieldPath.documentId());this.size=size;this.executor=executor;this.observer=observer;
    }
    void start(){append(null);}
    private void append(String cursor){
        Page page=new Page(cursor);pages.add(page);observer.requested();
        Query query=cursor==null?base:base.startAfter(cursor);
        page.registration=query.limit(size).addSnapshotListener(executor,MetadataChanges.INCLUDE,(snapshot,error)->{
            if(closed||!pages.contains(page))return;
            if(error!=null){observer.failed(error);return;}
            if(snapshot==null)return;
            page.snapshot=snapshot;
            int index=pages.indexOf(page);
            String end=snapshot.isEmpty()?null:snapshot.getDocuments().get(snapshot.size()-1).getId();
            boolean full=snapshot.size()==size;
            if(index+1<pages.size()&&(!full||!Objects.equals(pages.get(index+1).cursor,end)))trim(index+1);
            // Wait for server confirmation before extending a cached cursor. Cache-only
            // short pages cannot establish completeness or authorize mutations.
            boolean confirmed=!snapshot.getMetadata().isFromCache()&&!snapshot.getMetadata().hasPendingWrites();
            if(confirmed&&!page.measured){page.measured=true;observer.response(System.nanoTime()-page.started);}
            if(full&&confirmed&&index+1==pages.size())append(end);
            List<QuerySnapshot> result=new ArrayList<>();int loaded=0;boolean complete=true;
            for(Page p:pages){
                if(p.snapshot==null){complete=false;break;}
                result.add(p.snapshot);loaded+=p.snapshot.size();
                if(p.snapshot.getMetadata().isFromCache()||p.snapshot.getMetadata().hasPendingWrites())complete=false;
            }
            Page tail=pages.get(pages.size()-1);
            if(tail.snapshot==null||tail.snapshot.size()==size)complete=false;
            observer.changed(result,complete,loaded);
        });
    }
    private void trim(int from){
        while(pages.size()>from){Page p=pages.remove(pages.size()-1);if(p.registration!=null)p.registration.remove();}
    }
    @Override public void close(){closed=true;trim(0);}
}

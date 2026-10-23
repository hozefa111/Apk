package com.hozefa.threepattipro.nativeapp;

import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.nio.charset.StandardCharsets;

/** Bounded, stable document-ID ranges. A full range splits in half; unrelated
 * listeners never move when a record is inserted/deleted. Saved range boundaries
 * avoid repeating initial discovery on later launches. One executor owns all state.
 */
final class PagedFirestoreCollection implements AutoCloseable {
    interface Observer {
        void changed(List<Snapshot> pages,boolean complete,int loaded,List<String> boundaries);
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
    static final class Snapshot {
        private final QuerySnapshot source;
        private final List<DocumentSnapshot> documents=new ArrayList<>();
        private final List<DocumentChange> changes=new ArrayList<>();
        Snapshot(QuerySnapshot source,String upper){
            this.source=source;
            for(DocumentSnapshot doc:source.getDocuments())if(upper==null||compare(doc.getId(),upper)<=0)documents.add(doc);
            for(DocumentChange change:source.getDocumentChanges())if(upper==null||compare(change.getDocument().getId(),upper)<=0)changes.add(change);
        }
        List<DocumentSnapshot> getDocuments(){return documents;}
        List<DocumentChange> getDocumentChanges(){return changes;}
        SnapshotMetadata getMetadata(){return source.getMetadata();}
        int size(){return documents.size();}
    }
    private static final class Page {
        final String lower;
        String upper;
        ListenerRegistration registration;
        Snapshot snapshot;
        final long started=System.nanoTime();
        boolean measured,boundedQuery;
        Page(String lower,String upper){this.lower=lower;this.upper=upper;}
    }
    PagedFirestoreCollection(Query base,int size,Executor executor,Observer observer){
        if(size<2||size>500)throw new IllegalArgumentException("Page size must be 2–500");
        this.base=base.orderBy(FieldPath.documentId());this.size=size;this.executor=executor;this.observer=observer;
    }
    private static int compare(String a,String b){
        byte[] left=a.getBytes(StandardCharsets.UTF_8),right=b.getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<Math.min(left.length,right.length);i++){int d=(left[i]&255)-(right[i]&255);if(d!=0)return d;}
        return left.length-right.length;
    }
    void start(List<String> cached){
        if(closed)return;
        String previous=null;boolean valid=cached!=null;
        if(valid)for(String boundary:cached){
            if(boundary==null||boundary.isEmpty()||boundary.contains("/")||(previous!=null&&compare(previous,boundary)>=0)){valid=false;break;}
            previous=boundary;
        }
        previous=null;
        if(valid)for(String boundary:cached){pages.add(new Page(previous,boundary));previous=boundary;}
        pages.add(new Page(previous,null));
        for(Page page:new ArrayList<>(pages))listen(page);
    }
    private void listen(Page page){
        observer.requested();Query query=base;
        if(page.lower!=null)query=query.startAfter(page.lower);
        page.boundedQuery=page.upper!=null;
        if(page.upper!=null)query=query.endAt(page.upper);
        page.registration=query.limit(size).addSnapshotListener(executor,MetadataChanges.INCLUDE,(snapshot,error)->{
            if(closed||!pages.contains(page))return;
            if(error!=null){observer.failed(error);return;}
            if(snapshot==null)return;
            boolean confirmed=!snapshot.getMetadata().isFromCache()&&!snapshot.getMetadata().hasPendingWrites();
            if(confirmed&&!page.measured){page.measured=true;observer.response(System.nanoTime()-page.started);}
            if(confirmed&&snapshot.size()==size){
                String last=snapshot.getDocuments().get(size-1).getId();
                if(page.upper==null){
                    // Discover a full initial page without rereading it. This query
                    // retains its original limit; its logical upper bound freezes.
                    // Future filler cards beyond that bound are excluded below.
                    page.upper=last;
                    Page tail=new Page(last,null);pages.add(tail);listen(tail);
                }else if(compare(last,page.upper)<0){
                    // A range overflowed. Split only this range at the middle,
                    // leaving capacity for future inserts. Other queries keep their
                    // cursors, even when boundary documents are deleted.
                    String middle=snapshot.getDocuments().get(size/2-1).getId();
                    int index=pages.indexOf(page);pages.remove(index);page.registration.remove();
                    Page left=new Page(page.lower,middle),right=new Page(middle,page.upper);
                    pages.add(index,left);pages.add(index+1,right);listen(left);listen(right);
                }
            }
            boolean rebound=false;
            if(confirmed&&pages.contains(page)&&page.upper!=null&&!page.boundedQuery&&!snapshot.isEmpty()
                    &&compare(snapshot.getDocuments().get(snapshot.size()-1).getId(),page.upper)>0){
                // A deletion made the discovery query borrow from its neighbour.
                // Bind that one range explicitly once, avoiding ongoing duplicate
                // delivery from neighbouring ranges without reattaching a suffix.
                page.registration.remove();page.snapshot=null;listen(page);rebound=true;
            }
            if(pages.contains(page)&&!rebound)page.snapshot=new Snapshot(snapshot,page.upper);
            List<Snapshot> result=new ArrayList<>();List<String> boundaries=new ArrayList<>();
            int loaded=0;boolean complete=true;
            for(Page p:pages){
                if(p.upper!=null)boundaries.add(p.upper);
                if(p.snapshot==null){complete=false;continue;}
                result.add(p.snapshot);loaded+=p.snapshot.size();
                if(p.snapshot.getMetadata().isFromCache()||p.snapshot.getMetadata().hasPendingWrites())complete=false;
            }
            observer.changed(result,complete,loaded,boundaries);
        });
    }
    @Override public void close(){closed=true;for(Page page:pages)if(page.registration!=null)page.registration.remove();pages.clear();}
}

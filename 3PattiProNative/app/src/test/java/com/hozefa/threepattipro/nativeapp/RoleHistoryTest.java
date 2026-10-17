package com.hozefa.threepattipro.nativeapp;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoleHistoryTest {
    private Map<String,Object> round(String id,String hukum,List<String> partners,String time){
        Map<String,Object> r=new HashMap<>();r.put("id",id);r.put("type","hukum");r.put("hukumId",hukum);r.put("partnerIds",partners);r.put("playerIds",Arrays.asList("a","b","c"));r.put("timestamp",time);return r;
    }
    private Map<String,Object> round(String id,String hukum,String partner){return round(id,hukum,Arrays.asList(partner),"2026-10-17T12:00:00Z");}
    @Test public void allThreeRolesAreMutuallyExclusive(){
        List<Map<String,Object>> rows=Arrays.asList(round("h","a","b"),round("p","b","a"),round("n","b","c"));
        assertEquals("h",RoleHistory.select("a","hukum",rows).get(0).get("id"));
        assertEquals("p",RoleHistory.select("a","partner",rows).get(0).get("id"));
        assertEquals("n",RoleHistory.select("a","nonpartner",rows).get(0).get("id"));
    }
    @Test public void nonParticipantMustNotAppearAsNonPartner(){assertTrue(RoleHistory.select("removed","nonpartner",Arrays.asList(round("r","a","b"))).isEmpty());}
    @Test public void settlementExcludedAndZeroScoreNonPartnerRetained(){
        Map<String,Object> game=round("game","a","b"),adjustment=round("adjustment","a","b");adjustment.put("type","settlement");
        game.put("scoreChanges",Collections.singletonMap("c",0));
        List<Map<String,Object>> found=RoleHistory.select("c","nonpartner",Arrays.asList(adjustment,game));assertEquals(1,found.size());assertEquals("game",found.get(0).get("id"));
    }
    @Test public void newestFirstUsesNanosecondsThenDescendingId(){
        List<Map<String,Object>> rows=Arrays.asList(round("a","a",Arrays.asList("b"),"2026-10-17T12:00:00.000000001Z"),round("b","a",Arrays.asList("b"),"2026-10-17T12:00:00.000000002Z"),round("c","a",Arrays.asList("b"),"2026-10-17T12:00:00.000000002Z"));
        List<Map<String,Object>> found=RoleHistory.select("a","hukum",rows);assertEquals(Arrays.asList("c","b","a"),Arrays.asList(found.get(0).get("id"),found.get(1).get("id"),found.get(2).get("id")));
    }
    @Test public void filteringDoesNotMutateSourceOrderingOrRecords(){
        List<Map<String,Object>> rows=Arrays.asList(round("a","a","b"),round("b","a","b"));List<Map<String,Object>> copy=new ArrayList<>(rows);
        RoleHistory.select("a","hukum",rows);assertEquals(copy,rows);assertEquals("a",rows.get(0).get("id"));
    }
    @Test public void emptyAndUnknownRolesStayEmpty(){assertTrue(RoleHistory.select("a","hukum",Collections.emptyList()).isEmpty());assertTrue(RoleHistory.select("a","unknown",Arrays.asList(round("a","a","b"))).isEmpty());}
}

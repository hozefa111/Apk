package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class WebScoringParityTest {
    private List<String> ids(JSONArray array)throws Exception{List<String> out=new ArrayList<>();for(int i=0;i<array.length();i++)out.add(array.getString(i));return out;}
    @Test public void matchesOriginalWebFunctionAcrossAllBidsRolesAndResults()throws Exception{
        try(java.io.InputStream in=getClass().getResourceAsStream("/web-scoring-vectors.json")){
            JSONArray vectors=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("vectors");assertEquals(840,vectors.length());
            for(int i=0;i<vectors.length();i++){JSONObject v=vectors.getJSONObject(i);Map<String,Integer> result=ScoreRules.calculate(ids(v.getJSONArray("players")),v.getInt("bid"),v.getString("hukum"),ids(v.getJSONArray("partners")),v.getString("result"));JSONObject expected=v.getJSONObject("scores");for(String pid:result.keySet())assertEquals("vector "+i+" player "+pid,expected.getInt(pid),(int)result.get(pid));}
        }
    }
}

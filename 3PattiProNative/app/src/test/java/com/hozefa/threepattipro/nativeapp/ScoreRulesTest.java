package com.hozefa.threepattipro.nativeapp;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class ScoreRulesTest {
  private final java.util.List<String> all=Arrays.asList("a","b","c","d");
  @Test public void hukumWinMatchesOriginalWeb(){
    Map<String,Integer> scores=ScoreRules.calculate(all,155,"a",Collections.singletonList("b"),"win");
    assertEquals(310,(int)scores.get("a"));
    assertEquals(155,(int)scores.get("b"));
    assertEquals(0,(int)scores.get("c"));
    assertEquals(0,(int)scores.get("d"));
  }
  @Test public void hukumLossRoundsHalfUpLikeJavaScript(){
    Map<String,Integer> scores=ScoreRules.calculate(all,155,"a",Collections.singletonList("b"),"loss");
    assertEquals(-155,(int)scores.get("a"));
    assertEquals(-78,(int)scores.get("b"));
    assertEquals(155,(int)scores.get("c"));
    assertEquals(155,(int)scores.get("d"));
  }
  @Test public void multiplePartners(){
    Map<String,Integer> scores=ScoreRules.calculate(all,250,"a",Arrays.asList("b","c"),"loss");
    assertEquals(-250,(int)scores.get("a"));
    assertEquals(-125,(int)scores.get("b"));
    assertEquals(-125,(int)scores.get("c"));
    assertEquals(250,(int)scores.get("d"));
  }
  @Test(expected=IllegalArgumentException.class)
  public void invalidBidRejected(){ScoreRules.calculate(all,154,"a",Collections.singletonList("b"),"win");}
}

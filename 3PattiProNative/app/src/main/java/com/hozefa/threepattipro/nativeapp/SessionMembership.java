package com.hozefa.threepattipro.nativeapp;
import java.util.Map;
/** Explicit online/manual session identity with timestamp fallback for legacy rounds only. */
final class SessionMembership {
 static boolean includes(Map<String,Object> round,Map<String,Object> session){
  String explicit=AdvancedAnalytics.text(round,"sessionId");
  if(!explicit.isEmpty())return explicit.equals(AdvancedAnalytics.text(session,"id"));
  if("online".equals(round.get("source"))||"online".equals(session.get("source")))return false;
  long t=AdvancedAnalytics.time(round.get("timestamp")),start=AdvancedAnalytics.time(session.get("startTime"));
  long end=session.get("endTime")==null?Long.MAX_VALUE:AdvancedAnalytics.time(session.get("endTime"));
  return t!=Long.MIN_VALUE&&start!=Long.MIN_VALUE&&t>=start&&t<=end;
 }
}

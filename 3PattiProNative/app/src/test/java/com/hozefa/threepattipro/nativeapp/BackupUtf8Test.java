package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import static org.junit.Assert.*;
public class BackupUtf8Test {
 private int count(String value)throws Exception{Method m=BackupArchive.class.getDeclaredMethod("utf8",String.class);m.setAccessible(true);try{return (Integer)m.invoke(null,value);}catch(InvocationTargetException e){throw (Exception)e.getCause();}}
 @Test public void allocationFreeLengthMatchesStrictEncoderForUnicodeAndBoundaries()throws Exception{
  for(String s:new String[]{"","ascii\u0000","é","हुकुम","中文","♠♥♣♦","😀",new String(Character.toChars(0x10ffff))})assertEquals(s.getBytes(StandardCharsets.UTF_8).length,count(s));
  Random random=new Random(155);for(int n=0;n<2000;n++){StringBuilder s=new StringBuilder();for(int i=0;i<60;i++){int code=random.nextInt(0x110000);if(code>=0xd800&&code<=0xdfff)code='a';s.appendCodePoint(code);}String value=s.toString();assertTrue(StandardCharsets.UTF_8.newEncoder().canEncode(value));assertEquals(value.getBytes(StandardCharsets.UTF_8).length,count(value));}
 }
 @Test public void unpairedSurrogatesRemainRejected()throws Exception{
  for(String s:new String[]{"\ud800","\udc00","x\udbffy","\ud800\ud800","\udc00\udfff"}){assertFalse(StandardCharsets.UTF_8.newEncoder().canEncode(s));try{count(s);fail("Malformed Unicode accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("Unicode"));}}
 }
}

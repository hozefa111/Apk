package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineEndpointTest {
 @Test public void sameBundledPublicOriginDrivesNativeHandoff(){assertEquals("https://three-patti.example.workers.dev/kaliApi",OnlineEndpoint.api("window.KALI_API_BASE='https://three-patti.example.workers.dev/';"));}
 @Test public void legacyConfigurationRemainsCompatible(){assertEquals("https://asia-south1-patti-pro-a61d8.cloudfunctions.net/kaliApi",OnlineEndpoint.api("window.KALI_API_BASE='';"));}
 @Test public void credentialsCannotBeSentToUnapprovedOrMalformedOrigins(){for(String base:new String[]{"http://example.workers.dev/","https://attacker.example/","https://user@example.workers.dev/","https://example.workers.dev/?token=bad","https://example.workers.dev/#bad","https://example.workers.dev/path/","https://example.workers.dev:8080/"}){try{OnlineEndpoint.api("window.KALI_API_BASE='"+base+"';");fail(base);}catch(IllegalArgumentException expected){}}}
}

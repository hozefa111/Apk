package com.hozefa.threepattipro.nativeapp;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public endpoint shared with the bundled browser client; never contains credentials. */
final class OnlineEndpoint {
 private OnlineEndpoint() {}
 static String api(String config) {
  Matcher value=Pattern.compile("window\\.KALI_API_BASE\\s*=\\s*'([^']*)'").matcher(config);
  if(!value.find()||value.group(1).isEmpty())return "https://asia-south1-patti-pro-a61d8.cloudfunctions.net/kaliApi";
  URI base=URI.create(value.group(1));
  if(!"https".equals(base.getScheme())||base.getHost()==null||!base.getHost().endsWith(".workers.dev")||base.getRawUserInfo()!=null||base.getRawQuery()!=null||base.getRawFragment()!=null||!"/".equals(base.getPath())||base.getPort()!=-1)throw new IllegalArgumentException("Invalid online server configuration");
  return base.resolve("kaliApi").toString();
 }
}

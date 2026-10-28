package com.hozefa.threepattipro.nativeapp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionHistoryHttpTest {
    @Test public void responseWindowDoesNotRetainDefaultTenSecondReadTimeout() {
        OkHttpClient http=SessionHistoryHttp.clientBuilder().build();
        assertEquals(25000,http.callTimeoutMillis());assertEquals(25000,http.readTimeoutMillis());
        assertFalse(http.retryOnConnectionFailure());
    }
    @Test public void lostResponseAfterAcceptedDeleteRetriesExactOriginalCommand()throws Exception { retryFixture("delete",false); }
    @Test public void slowDeleteResponseRetriesExactOriginalCommand()throws Exception { retryFixture("delete",true); }
    @Test public void previewCanRetryWithoutChangingConfirmationRequest()throws Exception { retryFixture("previewDelete",false); }
    private void retryFixture(String action,boolean slow)throws Exception {
        List<String> received=Collections.synchronizedList(new ArrayList<>());Set<String> committed=ConcurrentHashMap.newKeySet();
        AtomicInteger attempts=new AtomicInteger();
        OkHttpClient http=SessionHistoryHttp.clientBuilder().readTimeout(300,TimeUnit.MILLISECONDS).build();
        try(Server server=new Server((socket,body)->{
            received.add(body);committed.add(new JSONObject(body).getString("commandId"));
            if(attempts.incrementAndGet()==1) {if(slow)Thread.sleep(700);return;}
            respond(socket,200,"{\"ok\":true,\"pending\":false}");
        })) {
            String payload=new JSONObject().put("action",action).put("sessionId","cf2_fixture")
                    .put("previewToken","a".repeat(64)).put("commandId",UUID.randomUUID().toString()).toString();
            JSONObject result=SessionHistoryHttp.execute(http,request(server,payload),action);
            assertTrue(result.getBoolean("ok"));assertFalse(result.getBoolean("pending"));
            assertEquals(2,attempts.get());assertEquals(Arrays.asList(payload,payload),received);assertEquals(1,committed.size());
        }finally{http.connectionPool().evictAll();}
    }
    @Test public void repeatedTransportFailureIsUncertainAndBounded()throws Exception {
        AtomicInteger attempts=new AtomicInteger();OkHttpClient http=SessionHistoryHttp.clientBuilder().build();
        try(Server server=new Server((socket,body)->attempts.incrementAndGet())) {
            try{SessionHistoryHttp.execute(http,request(server,"{}"),"delete");fail("Must not claim deletion failed or completed");}
            catch(SessionHistoryHttp.PendingDeletion expected){assertTrue(expected.getMessage().contains("confirmation delayed"));}
            assertEquals(2,attempts.get());
        }finally{http.connectionPool().evictAll();}
    }
    @Test public void expiredOrUnauthorizedAuthenticationIsNotRetried()throws Exception {
        AtomicInteger attempts=new AtomicInteger();OkHttpClient http=SessionHistoryHttp.clientBuilder().build();
        try(Server server=new Server((socket,body)->{attempts.incrementAndGet();respond(socket,401,"{\"error\":\"Sign in again\"}");})) {
            try{SessionHistoryHttp.execute(http,request(server,"{}"),"delete");fail("Unauthorized operation accepted");}
            catch(IOException expected){assertEquals("Sign in again",expected.getMessage());assertFalse(expected instanceof SessionHistoryHttp.PendingDeletion);}
            assertEquals(1,attempts.get());
        }finally{http.connectionPool().evictAll();}
    }
    private Request request(Server server,String payload) {
        return new Request.Builder().url("http://127.0.0.1:"+server.socket.getLocalPort()+"/")
                .post(RequestBody.create(payload,MediaType.get("application/json"))).build();
    }
    private static void respond(Socket socket,int status,String body)throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();
        out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    private interface Handler {void run(Socket socket,String body)throws Exception;}
    private static final class Server implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        final ExecutorService workers=Executors.newCachedThreadPool();
        Server(Handler handler)throws IOException {
            workers.submit(()->{while(!socket.isClosed())try {
                Socket peer=socket.accept();workers.submit(()->{try(Socket s=peer) {
                    s.setSoTimeout(3000);InputStream in=s.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int end=0;
                    while(end!=0x0d0a0d0a){int b=in.read();if(b<0||header.size()>20000)throw new EOFException();header.write(b);end=(end<<8)|b;}
                    int length=0;for(String line:header.toString("UTF-8").split("\r\n"))if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                    byte[] bytes=new byte[length];new DataInputStream(in).readFully(bytes);handler.run(s,new String(bytes,StandardCharsets.UTF_8));
                }catch(Exception ignored) {}});
            }catch(IOException closed){if(!socket.isClosed())throw new UncheckedIOException(closed);}});
        }
        @Override public void close()throws IOException {socket.close();workers.shutdownNow();}
    }
}

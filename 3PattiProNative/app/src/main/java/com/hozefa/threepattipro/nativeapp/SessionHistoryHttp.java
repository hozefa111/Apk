package com.hozefa.threepattipro.nativeapp;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import okhttp3.*;
import org.json.JSONObject;

/** Transport retries reuse the exact confirmed request, never a new deletion. */
final class SessionHistoryHttp {
    static OkHttpClient.Builder clientBuilder() {
        // OkHttp's default read timeout is 10s, even with a longer call timeout.
        return new OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS)
                .readTimeout(25,TimeUnit.SECONDS).retryOnConnectionFailure(false);
    }
    static final class PendingDeletion extends IOException {
        PendingDeletion(IOException cause) {
            super("Deletion confirmation delayed. It may still be running. Reopen History and retry the same session; points will not be removed twice.",cause);
        }
    }
    private static final class Rejected extends IOException {
        Rejected(String message) { super(message); }
    }
    static JSONObject execute(OkHttpClient client,Request request,String action)throws Exception {
        boolean retrySafe="delete".equals(action)||"previewDelete".equals(action);
        for(int attempt=0;;attempt++) {
            try(Response response=client.newCall(request).execute()) {
                JSONObject result=new JSONObject(response.body().string());
                if(!response.isSuccessful())throw new Rejected(result.optString("error","Session update failed"));
                return result;
            }catch(IOException error) {
                if(error instanceof Rejected||error instanceof SSLException||error instanceof ProtocolException
                        ||Thread.currentThread().isInterrupted())throw error;
                if(retrySafe&&attempt==0)continue;
                if("delete".equals(action))throw new PendingDeletion(error);
                throw error;
            }
        }
    }
}

package com.hozefa.threepattipro.nativeapp;

/** Installed only in the separate test APK, to verify cross-UID FileProvider grants. */
public final class VerificationShareReceiver extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);android.content.Intent result=new android.content.Intent("threepatti.verification.SHARE_RESULT").setPackage("com.hozefa.threepattipro.nativeapp.sandbox");
        int count=0;
        try{
            java.util.List<android.net.Uri> images=new java.util.ArrayList<>();
            if(android.content.Intent.ACTION_SEND.equals(getIntent().getAction()))images.add(getIntent().getParcelableExtra(android.content.Intent.EXTRA_STREAM));
            else images.addAll(getIntent().getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM));
            for(android.net.Uri image:images){
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(java.io.InputStream in=getContentResolver().openInputStream(image)){android.graphics.BitmapFactory.decodeStream(in,null,options);}
                if(options.outWidth<=0||options.outHeight<=0||!"image/png".equals(options.outMimeType))throw new java.io.IOException("Receiver cannot decode PNG");count++;
            }
        }catch(Exception error){result.putExtra("error",error.toString());}
        result.putExtra("count",count).putExtra("uid",android.os.Process.myUid());sendBroadcast(result);finish();
    }
}

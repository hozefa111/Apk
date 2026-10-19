package com.hozefa.threepattipro.nativeapp;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.tts.*;
import android.widget.*;
import java.util.*;

/** Optional guided input; recognizer audio belongs to the chosen Android speech provider. */
final class VoiceRoundGuide {
    static final int REQUEST=730;
    interface Host { boolean save(VoiceRoundDraft draft); }
    private final Activity activity;
    private final MotionEngine motion;
    private final Host host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private VoiceRoundDraft draft;
    private AlertDialog dialog;
    private TextToSpeech tts;
    private boolean ttsReady,ttsStarting,auto,resumed,recognizing,closed,english;
    private int generation;
    private String requestStep;
    private TextView status;
    private String lastHeard="",message="";
    VoiceRoundGuide(Activity a,MotionEngine motion,Host host){activity=a;this.motion=motion;this.host=host;}
    void open(VoiceRoundDraft value){closeDraft();closed=false;draft=value;message="Start voice guide dabao. Ya jawab type karke Use answer dabao.";show();}
    boolean active(){return draft!=null;}
    void saveState(Bundle state){if(draft!=null){state.putString("voiceDraft",draft.encode());state.putBoolean("voiceEnglish",english);state.putString("voiceRequest",recognizing?requestStep:null);}}
    void restore(Bundle state){if(state==null||!state.containsKey("voiceDraft"))return;try{
        draft=VoiceRoundDraft.decode(state.getString("voiceDraft"));english=state.getBoolean("voiceEnglish");requestStep=state.getString("voiceRequest");recognizing=requestStep!=null;message="Voice draft restored. Preview check karke voice dobara start karo.";show();
    }catch(IllegalArgumentException invalid){draft=null;}}
    void resume(){resumed=true;}
    void pause(){resumed=false;generation++;handler.removeCallbacksAndMessages(null);if(tts!=null)tts.stop();if(!recognizing)auto=false;}
    void destroy(){closed=true;closeDraft();if(tts!=null){tts.shutdown();tts=null;}handler.removeCallbacksAndMessages(null);}
    void closeDraft(){auto=false;generation++;recognizing=false;requestStep=null;if(tts!=null)tts.stop();handler.removeCallbacksAndMessages(null);draft=null;if(dialog!=null){dialog.dismiss();dialog=null;}}
    private TextView button(String label,Runnable action){TextView b=DialogUi.text(activity,label,15,DialogUi.GOLD,true);b.setPadding(DialogUi.dp(activity,14),DialogUi.dp(activity,12),DialogUi.dp(activity,14),DialogUi.dp(activity,12));b.setMinHeight(DialogUi.dp(activity,48));b.setBackground(DialogUi.background(activity,DialogUi.CARD,12));b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout body,String label,Runnable action){body.addView(button(label,action),new LinearLayout.LayoutParams(-1,-2));DialogUi.gap(body,8);}
    private void show(){
        if(draft==null||closed||activity.isFinishing())return;
        if(dialog!=null)dialog.dismiss();
        LinearLayout body=DialogUi.body(activity);
        DialogUi.fact(body,"Step "+(draft.step.ordinal()+1)+" / 6",draft.prompt(),DialogUi.WHITE);
        status=DialogUi.text(activity,message,13,DialogUi.GOLD,false);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);DialogUi.gap(body,10);
        if(!lastHeard.isEmpty()){body.addView(DialogUi.text(activity,"Heard: "+lastHeard,13,DialogUi.MUTED,false));DialogUi.gap(body,10);}
        if(draft.step==VoiceRoundDraft.Step.REVIEW){
            DialogUi.fact(body,"Hukum / Bid",draft.name(draft.hukum)+" · "+draft.bid,DialogUi.GOLD);
            DialogUi.fact(body,"Partners",draft.joined(draft.partners),DialogUi.WHITE);
            DialogUi.fact(body,"Result","win".equals(draft.result)?"Hukum win":"Hukum loss",DialogUi.WHITE);
            for(Map.Entry<String,Integer> entry:draft.scores().entrySet())DialogUi.score(body,draft.name(entry.getKey()),entry.getKey().equals(draft.hukum)?"Hukum":draft.partners.contains(entry.getKey())?"Partner":"Opponent",entry.getValue());
            body.addView(DialogUi.text(activity,"Save karo bolne ya Confirm Save dabane par ek round save hoga.",12,DialogUi.MUTED,false));DialogUi.gap(body,10);
            addButton(body,"Confirm Save",()->accept("save karo",false));
            addButton(body,"Change a detail",this::changeDetail);
        }else{
            StringBuilder names=new StringBuilder();int n=0;for(Map.Entry<String,String> p:draft.roster.entrySet()){
                n++;boolean allowed=draft.step==VoiceRoundDraft.Step.PLAYERS||draft.players.contains(p.getKey());
                if(allowed)names.append(n).append(" · ").append(p.getValue()).append(p.getKey().equals(draft.hukum)?" (Hukum)":"").append('\n');
            }
            DialogUi.fact(body,"Naam clear na ho to player number bolo",names.toString().trim(),DialogUi.WHITE);
        }
        addButton(body,auto?"Pause voice":"Start voice guide",()->{if(auto){stopVoice();message="Voice paused. Manual jawab ya Speak answer use karo.";show();}else{auto=true;ask();}});
        addButton(body,"Speak answer",()->{stopVoice();listen();});
        addButton(body,english?"Language · English (India)":"Language · Hindi / Hinglish",()->{stopVoice();english=!english;message="Language changed. Start voice guide dabao.";show();});
        EditText answer=new EditText(activity);answer.setSingleLine(true);answer.setTextColor(DialogUi.WHITE);answer.setHintTextColor(DialogUi.MUTED);answer.setHint("Naam, player number ya jawab");answer.setContentDescription("Voice answer fallback");body.addView(answer,new LinearLayout.LayoutParams(-1,-2));DialogUi.gap(body,8);
        addButton(body,"Use answer",()->{stopVoice();accept(answer.getText().toString(),false);});
        body.addView(DialogUi.text(activity,"Commands: peeche · bid badlo · hukum badlo · partner badlo · result badlo · cancel.\nSpeech provider ko audio mil sakta hai; network/offline support phone par depend karta hai. App audio record ya save nahi karti. Purana manual form available hai.",12,DialogUi.MUTED,false));
        dialog=new MotionDialogBuilder(activity,motion).setTitle("Voice Round · "+draft.step.name()).setView(DialogUi.scroll(activity,body))
                .setNegativeButton("Cancel voice",(d,w)->closeDraft()).setNeutralButton("Peechhe",null).create();
        dialog.setOnCancelListener(d->closeDraft());dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(draft.step!=VoiceRoundDraft.Step.PLAYERS);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{stopVoice();draft.back();message="Pichhla step. Jawab dobara bolo.";show();});
    }
    private void changeDetail(){
        stopVoice();new MotionDialogBuilder(activity,motion).setTitle("Change voice detail")
            .setItems(new String[]{"Players","Hukum","Bid","Partners","Result"},(d,which)->{if(draft!=null){draft.step=VoiceRoundDraft.Step.values()[which];message="Detail dobara bolo.";show();}}).setNegativeButton("Keep preview",null).show();
    }
    private void stopVoice(){auto=false;generation++;handler.removeCallbacksAndMessages(null);if(tts!=null)tts.stop();}
    private void ask(){
        if(draft==null||closed||!resumed)return;
        message="Sawaal suno. Mic khulne par jawab bolo.";show();
        if(tts==null&&!ttsStarting){
            ttsStarting=true;tts=new TextToSpeech(activity.getApplicationContext(),result->handler.post(()->{ttsStarting=false;ttsReady=result==TextToSpeech.SUCCESS;if(draft!=null&&auto&&resumed)ask();}));return;
        }
        if(ttsStarting)return;
        if(!ttsReady){message="Phone par question audio available nahi. Sawaal screen par padho.";show();listen();return;}
        int supported=tts.setLanguage(english?new Locale("en","IN"):new Locale("hi","IN"));
        if(supported==TextToSpeech.LANG_MISSING_DATA||supported==TextToSpeech.LANG_NOT_SUPPORTED){listen();return;}
        int expected=++generation;String utterance="voice-round-"+expected;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){handler.post(()->{if(id.equals(utterance)&&generation==expected&&auto&&resumed&&draft!=null)listen();});}
            public void onError(String id){handler.post(()->{if(generation==expected&&auto&&resumed&&draft!=null)listen();});}
        });
        String prompt=english?englishPrompt():draft.spokenPrompt();
        if(tts.speak(prompt,TextToSpeech.QUEUE_FLUSH,null,utterance)==TextToSpeech.ERROR)listen();
    }
    private String englishPrompt(){switch(draft.step){
        case PLAYERS:return "Who played this round? Say names, player numbers, all players, or previous players.";
        case HUKUM:return "Who took Hukum? Say one name or player number.";
        case BID:return "What was the bid? 155 to 250 in steps of five.";
        case PARTNERS:return "Who were Hukum's partners? Say names or player numbers.";
        case RESULT:return "Did Hukum win or lose?";
        default:return "Hukum "+draft.name(draft.hukum)+". Bid "+draft.bid+". Partners "+draft.joined(draft.partners)+". Hukum "+draft.result+". Check every player's points on screen. Say save round to confirm, or change bid or partners.";
    }}
    private void listen(){
        if(draft==null||closed||!resumed||recognizing)return;
        if(tts!=null)tts.stop();generation++;
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,english?"en-IN":"hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,draft.prompt());intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        recognizing=true;requestStep=draft.step.name();
        try{activity.startActivityForResult(intent,REQUEST);}
        catch(ActivityNotFoundException|SecurityException unavailable){recognizing=false;requestStep=null;auto=false;message="Speech recognition available nahi. Phone ka speech provider enable karo, ya type/manual entry use karo.";show();}
    }
    boolean result(int request,int result,Intent data){
        if(request!=REQUEST)return false;
        if(draft==null||!recognizing)return true;
        recognizing=false;String expected=requestStep;requestStep=null;
        if(!draft.step.name().equals(expected)){auto=false;message="Step badal gaya. Dobara jawab bolo.";show();return true;}
        ArrayList<String> alternatives=data==null?null:data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(result!=Activity.RESULT_OK||alternatives==null||alternatives.isEmpty()){auto=false;message="Koi jawab nahi mila. Speak answer dabao ya type karo.";show();return true;}
        String spoken=alternatives.get(0);boolean continueVoice=auto;VoiceRoundDraft expectedDraft=draft;int expectedGeneration=generation;
        // onActivityResult precedes onResume. Keep input staged and wait for a visible host.
        handler.postDelayed(()->{if(!closed&&draft==expectedDraft&&generation==expectedGeneration&&draft.step.name().equals(expected))accept(spoken,continueVoice);},250);return true;
    }
    private void accept(String spoken,boolean continueVoice){
        if(draft==null)return;lastHeard=spoken==null?"":spoken.substring(0,Math.min(spoken.length(),200));
        VoiceRoundDraft.Action action=draft.answer(spoken);
        if(action==VoiceRoundDraft.Action.CANCEL){closeDraft();return;}
        if(action==VoiceRoundDraft.Action.SAVE){
            stopVoice();if(host.save(draft)){closeDraft();return;}message="Save nahi hua. Connection, admin login aur manual pending draft check karo.";show();return;
        }
        if(action==VoiceRoundDraft.Action.RETRY){auto=false;message=draft.error;show();return;}
        message="Jawab selected. Preview mein check kar sakte ho.";show();
        if(continueVoice&&resumed){auto=true;handler.postDelayed(this::ask,350);}else auto=false;
    }
}

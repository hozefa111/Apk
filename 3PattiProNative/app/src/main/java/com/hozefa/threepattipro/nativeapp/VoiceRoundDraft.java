package com.hozefa.threepattipro.nativeapp;

import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** A staged conversation. It has no Android, microphone or Firebase access. */
final class VoiceRoundDraft {
    enum Step { PLAYERS, HUKUM, BID, PARTNERS, RESULT, REVIEW }
    enum Action { NEXT, RETRY, SAVE, CANCEL }
    final LinkedHashMap<String,String> roster;
    final List<String> previous;
    final LinkedHashSet<String> players=new LinkedHashSet<>(),partners=new LinkedHashSet<>();
    String hukum,result,error=""; int bid; Step step=Step.PLAYERS;
    VoiceRoundDraft(Map<String,String> roster,Collection<String> previous){
        this.roster=new LinkedHashMap<>(roster);this.previous=new ArrayList<>(previous);
        this.previous.retainAll(roster.keySet());
    }
    static String normalize(String value){
        StringBuilder out=new StringBuilder();
        for(char c:Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).toCharArray()){
            int digit=Character.digit(c,10);out.append(digit>=0?(char)('0'+digit):Character.isLetter(c)||(Character.getType(c)==Character.NON_SPACING_MARK||Character.getType(c)==Character.COMBINING_SPACING_MARK||Character.getType(c)==Character.ENCLOSING_MARK)?c:' ');
        }
        return out.toString().trim().replaceAll("\\s+"," ");
    }
    private static boolean is(String s,String... values){return Arrays.asList(values).contains(s);}
    Action answer(String spoken){
        error="";if(spoken==null||spoken.length()>512){error="Chhota, clear jawab bolo.";return Action.RETRY;}String s=normalize(spoken);
        if(is(s,"cancel","cancel karo","band karo","रद्द करो","कैंसल","कैंसल करो"))return Action.CANCEL;
        if(is(s,"back","go back","peeche","peeche jao","pichhe jao","पीछे","पीछे जाओ")){back();return Action.NEXT;}
        if(is(s,"players badlo","player badlo","change players","खिलाड़ी बदलो")){step=Step.PLAYERS;return Action.NEXT;}
        if(is(s,"hukum badlo","change hukum","हुकुम बदलो")){return change(Step.HUKUM);}
        if(is(s,"bid badlo","change bid","बिड बदलो","बोली बदलो")){return change(Step.BID);}
        if(is(s,"partner badlo","partners badlo","change partners","पार्टनर बदलो")){return change(Step.PARTNERS);}
        if(is(s,"result badlo","change result","रिजल्ट बदलो","नतीजा बदलो")){return change(Step.RESULT);}
        try{
            switch(step){
                case PLAYERS:{
                    Set<String> selected;
                    if(is(s,"all","all players","sab","sabhi","सब","सभी","सभी खिलाड़ी"))selected=new LinkedHashSet<>(roster.keySet());
                    else if(is(s,"same players","previous players","pichhle wale players","pichle wale players","पिछले वाले","पिछले वाले प्लेयर्स","पिछले वाले खिलाड़ी"))selected=new LinkedHashSet<>(previous);
                    else selected=names(s,roster.keySet());
                    if(selected.size()<3)throw new IllegalArgumentException("Kam se kam 3 active players chahiye. Naam ya player numbers bolo.");
                    players.clear();players.addAll(selected);hukum=null;partners.clear();result=null;step=Step.HUKUM;break;
                }
                case HUKUM:{
                    Set<String> selected=names(s,players);
                    if(selected.size()!=1)throw new IllegalArgumentException("Sirf ek Hukum ka naam ya player number bolo.");
                    hukum=selected.iterator().next();partners.remove(hukum);step=Step.BID;break;
                }
                case BID:bid=parseBid(spoken);step=Step.PARTNERS;break;
                case PARTNERS:{
                    Set<String> selected=names(s,players);
                    if(selected.isEmpty()||selected.contains(hukum)||selected.size()>players.size()-2)
                        throw new IllegalArgumentException("Hukum ko partner nahi bana sakte. Kam se kam ek partner aur ek opponent chahiye.");
                    partners.clear();partners.addAll(selected);step=Step.RESULT;break;
                }
                case RESULT:
                    if(is(s,"win","won","hukum win","hukum won","jeeta","jeet gaya","jeet gaye","hukum jeeta","hukum jeet gaya","जीता","जीत गया","जीत गए","हुकुम जीता","हुकुम जीत गया","हुकुम जीते"))result="win";
                    else if(is(s,"loss","lost","lose","hukum lost","hukum lose","haara","hara","haar gaya","hukum haara","hukum haar gaya","हारा","हार गया","हुकुम हारा","हुकुम हार गया"))result="loss";
                    else throw new IllegalArgumentException("Hukum jeeta ya haara? Sirf win ya loss bolo.");
                    scores();step=Step.REVIEW;break;
                case REVIEW:
                    if(is(s,"save","save karo","save round","confirm save","सेव","सेव करो","सेव राउंड")){scores();return Action.SAVE;}
                    throw new IllegalArgumentException("Preview check karo. Save karo, bid badlo, partner badlo, peeche ya cancel bolo.");
            }
            return Action.NEXT;
        }catch(IllegalArgumentException invalid){error=invalid.getMessage();return Action.RETRY;}
    }
    private Action change(Step target){
        if(players.size()<3||(target.ordinal()>=Step.BID.ordinal()&&hukum==null)){
            error="Pehle players aur Hukum select karo.";return Action.RETRY;
        }
        step=target;return Action.NEXT;
    }
    void back(){error="";if(step.ordinal()>0)step=Step.values()[step.ordinal()-1];}
    Map<String,Integer> scores(){return ScoreRules.calculate(new ArrayList<>(players),bid,hukum,new ArrayList<>(partners),result);}
    String name(String id){return roster.getOrDefault(id,id);}
    String joined(Collection<String> ids){List<String> names=new ArrayList<>();for(String id:ids)names.add(name(id));return String.join(", ",names);}
    String prompt(){switch(step){
        case PLAYERS:return "Is round mein kaun-kaun khela? Naam, player numbers, sabhi ya pichhle wale players bolo.";
        case HUKUM:return "Hukum kisne liya? Ek naam ya player number bolo.";
        case BID:return "Kitne points ki bid thi? 155 se 250, 5 ke steps mein.";
        case PARTNERS:return "Hukum ke partner kaun-kaun the? Naam ya player numbers bolo.";
        case RESULT:return "Hukum jeeta ya haara?";
        default:return "Preview check karo. Sahi hai to Save karo bolo. Galti ho to bid badlo, partner badlo ya peeche bolo.";
    }}
    String spokenPrompt(){switch(step){
        case PLAYERS:return "इस राउंड में कौन कौन खेला? नाम या प्लेयर नंबर बोलिए। सभी या पिछले वाले प्लेयर्स भी बोल सकते हैं।";
        case HUKUM:return "हुकुम किसने लिया? एक नाम या प्लेयर नंबर बोलिए।";
        case BID:return "कितने पॉइंट की बिड थी? 155 से 250 के बीच, पांच के अंतर में।";
        case PARTNERS:return "हुकुम के पार्टनर कौन कौन थे? नाम या प्लेयर नंबर बोलिए।";
        case RESULT:return "हुकुम जीता या हारा?";
        default:return "हुकुम "+name(hukum)+"। बिड "+bid+"। पार्टनर "+joined(partners)+"। हुकुम "+("win".equals(result)?"जीता":"हारा")+"। स्क्रीन पर हर खिलाड़ी के पॉइंट देखिए। सही है तो सेव करो बोलिए।";
    }}
    private Set<String> names(String input,Collection<String> allowed){
        // Match complete roster names; never choose a fuzzy/partial name or a duplicate.
        String[] words=input.split(" ");boolean[] used=new boolean[words.length];Set<String> selected=new LinkedHashSet<>();
        List<String> ids=new ArrayList<>(roster.keySet());ids.sort((a,b)->Integer.compare(normalize(name(b)).length(),normalize(name(a)).length()));
        for(String id:ids){
            String n=normalize(name(id));if(n.isEmpty())continue;String[] tokens=n.split(" ");
            for(int i=0;i+tokens.length<=words.length;i++){
                boolean match=true;for(int k=0;k<tokens.length;k++)if(used[i+k]||!tokens[k].equals(words[i+k]))match=false;
                if(!match)continue;
                long duplicates=roster.values().stream().filter(v->normalize(v).equals(n)).count();
                if(duplicates>1)throw new IllegalArgumentException("Ek naam ke multiple players hain. Player number bolo.");
                if(!allowed.contains(id))throw new IllegalArgumentException("Yeh player is round mein selected nahi hai.");
                selected.add(id);for(int k=0;k<tokens.length;k++)used[i+k]=true;
            }
        }
        List<String> numbered=new ArrayList<>(roster.keySet());
        Set<String> fillers=new HashSet<>(Arrays.asList("and","aur","or","और","एंड","player","players","number","numbers","plеyer","प्लेयर","प्लेयर्स","नंबर","नम्बर","ne","ने","hai","है","hain","हैं","the","थे","thi","थी","hukum","हुकुम","partner","partners","पार्टनर","पार्टनर्स"));
        for(int i=0;i<words.length;i++)if(!used[i]){
            String w=words[i];if(fillers.contains(w))continue;Integer number=smallNumber(w);
            if(number==null||number<1||number>numbered.size())throw new IllegalArgumentException("Naam clear nahi hua. Screen par dikhaya player number bolo.");
            String id=numbered.get(number-1);if(!allowed.contains(id))throw new IllegalArgumentException("Yeh player is round mein selected nahi hai.");selected.add(id);
        }
        return selected;
    }
    private static final String[] EN={"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
    private static final String[] HI={"शून्य","एक","दो","तीन","चार","पांच","छह","सात","आठ","नौ","दस","ग्यारह","बारह","तेरह","चौदह","पंद्रह","सोलह","सत्रह","अठारह","उन्नीस"};
    static Integer smallNumber(String s){
        try{return Integer.valueOf(s);}catch(NumberFormatException ignored){}
        for(int i=0;i<EN.length;i++)if(EN[i].equals(s)||HI[i].equals(s))return i;
        if("पाँच".equals(s))return 5;if("छः".equals(s))return 6;return null;
    }
    static int parseBid(String input){
        if(input==null||input.matches(".*[-−+.].*"))throw new IllegalArgumentException("Bid positive whole number honi chahiye.");
        String s=normalize(input).replaceAll("\\b(bid|points|point|ki|thi|hai|ka)\\b","").replaceAll("बिड|बोली|पॉइंट्स|पॉइंट|अंक|की|थी|है","").trim().replaceAll("\\s+"," ");
        Integer n=null;try{n=Integer.valueOf(s);}catch(NumberFormatException ignored){}
        if(n==null){
            String compact=s.replace(" ","");String[] hi={"एकसौपचपन","एकसौसाठ","एकसौपैंसठ","एकसौसत्तर","एकसौपचहत्तर","एकसौअस्सी","एकसौपचासी","एकसौनब्बे","एकसौपचानवे","दोसौ","दोसौपांच","दोसौदस","दोसौपंद्रह","दोसौबीस","दोसौपच्चीस","दोसौतीस","दोसौपैंतीस","दोसौचालीस","दोसौपैंतालीस","दोसौपचास"};
            for(int i=0;i<hi.length;i++)if(compact.equals(hi[i]))n=155+5*i;
            if(compact.equals("डेढ़सौपांच"))n=155;if(compact.equals("ढाईसौ"))n=250;
        }
        if(n==null){
            String[] words=s.split(" ");
            Map<String,Integer> tens=new HashMap<>();String[] ts={"twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};for(int i=0;i<ts.length;i++)tens.put(ts[i],20+i*10);
            int pos=0,value=0;Integer first=smallNumber(words[0]);
            if(first!=null&&(first==1||first==2)&&words.length>1){
                if(words[1].equals("hundred")){value=first*100;pos=2;if(pos<words.length&&words[pos].equals("and"))pos++;}
                else if(tens.containsKey(words[1])){value=first*100;pos=1;}
            }
            int left=words.length-pos;
            if(left==0&&value>0)n=value;
            else if(left==1){Integer tail=smallNumber(words[pos]);if(tail==null)tail=tens.get(words[pos]);if(tail!=null)n=value+tail;}
            else if(left==2&&tens.containsKey(words[pos])){Integer unit=smallNumber(words[pos+1]);if(unit!=null&&unit>0&&unit<10)n=value+tens.get(words[pos])+unit;}

        }
        if(n==null||n<155||n>250||(n-155)%5!=0)throw new IllegalArgumentException("Bid 155 se 250 tak, 5 ke steps mein bolo. Jaise 170.");return n;
    }
    String encode(){try{
        JSONObject j=new JSONObject();j.put("roster",new JSONObject(roster));j.put("order",new JSONArray(roster.keySet()));j.put("previous",new JSONArray(previous));j.put("players",new JSONArray(players));j.put("partners",new JSONArray(partners));j.put("hukum",hukum);j.put("result",result);j.put("bid",bid);j.put("step",step.name());return j.toString();
    }catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    static VoiceRoundDraft decode(String encoded){try{
        JSONObject j=new JSONObject(encoded);JSONObject names=j.getJSONObject("roster");Map<String,String> roster=new LinkedHashMap<>();
        // Preserve displayed numbers across rotation (JSONObject key iteration is not ordered).
        JSONArray ordered=j.getJSONArray("order");for(int i=0;i<ordered.length();i++){String id=ordered.getString(i);roster.put(id,names.getString(id));}
        VoiceRoundDraft d=new VoiceRoundDraft(roster,strings(j.getJSONArray("previous")));d.players.addAll(strings(j.getJSONArray("players")));d.partners.addAll(strings(j.getJSONArray("partners")));d.hukum=j.optString("hukum",null);d.result=j.optString("result",null);d.bid=j.getInt("bid");d.step=Step.valueOf(j.getString("step"));
        if(!roster.keySet().containsAll(d.players)||!d.players.containsAll(d.partners))throw new IllegalArgumentException("Invalid voice draft");
        if(d.step==Step.REVIEW)d.scores();return d;
    }catch(Exception invalid){throw new IllegalArgumentException("Cannot restore voice draft",invalid);}}
    private static List<String> strings(JSONArray array)throws JSONException{List<String> values=new ArrayList<>();for(int i=0;i<array.length();i++)values.add(array.getString(i));return values;}
}

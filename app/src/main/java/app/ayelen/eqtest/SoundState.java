package app.ayelen.eqtest;

import java.io.*;
import java.util.*;

/** Parameters only. No platform calls and no work on an audio thread. */
final class SoundState {
    static final float[] FREQUENCIES = {31,62,125,250,500,1000,2000,4000,8000,11500};
    static final String[] LABELS = {"31","62","125","250","500","1k","2k","4k","8k","11.5k"};
    float[] bands = new float[10];
    float[] simple = new float[3]; // Additional user EQ: 37 Hz, 1 kHz, 10 kHz.
    static final float[] SIMPLE_DEFAULTS = {37,1000,10000};
    static final int SIMPLE_MAX = 10;
    float[] simpleFrequencies = SIMPLE_DEFAULTS.clone();
    static final class SimpleBand {
        final int id; float frequency,gain;
        SimpleBand(int id,float frequency,float gain){this.id=id;this.frequency=frequency;this.gain=gain;}
    }
    final ArrayList<SimpleBand> simpleAdded = new ArrayList<>();
    int nextSimpleId=3;
    int simpleCount(){return 3+simpleAdded.size();}
    SimpleBand addedSimple(int id){for(SimpleBand b:simpleAdded)if(b.id==id)return b;return null;}
    boolean hasSimple(int id){return id>=0&&id<3 || addedSimple(id)!=null;}
    float simpleFrequency(int id){SimpleBand b=addedSimple(id);return id>=0&&id<3?simpleFrequencies[id]:b==null?1000:b.frequency;}
    float simpleGain(int id){SimpleBand b=addedSimple(id);return id>=0&&id<3?simple[id]:b==null?0:b.gain;}
    void simpleFrequency(int id,float value){value=clamp(value,20,20000,simpleFrequency(id));if(id>=0&&id<3)simpleFrequencies[id]=value;else{SimpleBand b=addedSimple(id);if(b!=null)b.frequency=value;}}
    void simpleGain(int id,float value){value=clamp(value,-12,12,0);if(id>=0&&id<3)simple[id]=value;else{SimpleBand b=addedSimple(id);if(b!=null)b.gain=value;}}
    boolean addSimple(float frequency){
        if(simpleCount()>=SIMPLE_MAX || !Float.isFinite(frequency) || frequency<20 || frequency>20000)return false;
        while(hasSimple(nextSimpleId))nextSimpleId++;
        simpleAdded.add(new SimpleBand(nextSimpleId++,frequency,0));return true;
    }
    void removeSimple(int id){simpleAdded.removeIf(b->b.id==id);}
    void flatSimple(){Arrays.fill(simple,0);for(SimpleBand b:simpleAdded)b.gain=0;}
    void resetSimple(){simple=new float[3];simpleFrequencies=SIMPLE_DEFAULTS.clone();simpleAdded.clear();}
    Integer[] sortedSimpleIds(){
        Integer[] ids=new Integer[simpleCount()];for(int i=0;i<3;i++)ids[i]=i;
        for(int i=0;i<simpleAdded.size();i++)ids[i+3]=simpleAdded.get(i).id;
        Arrays.sort(ids,(a,b)->{int order=Float.compare(simpleFrequency(a),simpleFrequency(b));return order==0?Integer.compare(a,b):order;});return ids;
    }
    void copySimpleTo(SoundState to){
        to.simple=simple.clone();to.simpleFrequencies=simpleFrequencies.clone();to.nextSimpleId=nextSimpleId;
        to.simpleAdded.clear();for(SimpleBand b:simpleAdded)to.simpleAdded.add(new SimpleBand(b.id,b.frequency,b.gain));
    }
    boolean sameSimpleEq(SoundState s){
        if(!Arrays.equals(simple,s.simple)||!Arrays.equals(simpleFrequencies,s.simpleFrequencies)||simpleAdded.size()!=s.simpleAdded.size())return false;
        for(int i=0;i<simpleAdded.size();i++){SimpleBand a=simpleAdded.get(i),b=s.simpleAdded.get(i);if(a.id!=b.id||a.frequency!=b.frequency||a.gain!=b.gain)return false;}return true;
    }
    boolean eqOn = true;
    String surroundReturn = ""; // Snapshot without nested SURROUND.
    float[] frequencies = FREQUENCIES.clone(), bandQs = new float[10];
    int[] types = {-1,0,0,0,0,0,0,0,0,1};
    boolean[] bandOn = {true,true,true,true,true,true,true,true,true,true};
    float spread; // Complementary spectral L/R contrast, NOT Mid/Side gain.

    float bandQ(int i) { return bandQs[i] > 0 ? bandQs[i] : q; }
    void resetBand(int i) {
        bands[i]=0; frequencies[i]=FREQUENCIES[i]; bandQs[i]=0;
        types[i]=i==0?-1:i==9?1:0; bandOn[i]=true;
    }
    void defaultShape() {
        frequencies=FREQUENCIES.clone(); bandQs=new float[10];
        types=new int[]{-1,0,0,0,0,0,0,0,0,1}; Arrays.fill(bandOn,true);
    }
    float preamp, output, presence, air;
    float q = 1.15f;
    boolean compressor, virtualizer, surround, modified;
    float threshold = -18, ratio = 3, attack = 10, release = 180, knee = 6, makeup;
    int strength = 60;
    String tone = "flat", savedId = "", savedName = "";

    static SoundState surround() {
        SoundState s = new SoundState();
        s.frequencies = new float[]{37,62,125,250,500,1000,2000,4000,8000,10500};
        s.bands = new float[]{0,0,0,2,1,3.5f,3.5f,2.5f,1,2};
        s.q=.55f; s.preamp=-1.5f; s.output=4; s.presence=3; s.air=1;
        s.compressor=true; s.threshold=-17; s.ratio=6.5f;
        s.attack=62; s.release=30; s.knee=0; s.makeup=6;
        s.virtualizer=true; s.strength=100; s.spread=42;
        // Only the recipe builder exposes these bands; user sliders stay independent.
        s.surround=true; s.tone="surround";
        return s;
    }

    void setTone(String id) {
        defaultShape();
        if ("rock".equals(id)) bands = new float[]{1.5f,2,1.5f,0,-1,-.5f,1,2,1.5f,.5f};
        else if ("pop".equals(id)) bands = new float[]{0,1,1,0,-.5f,.5f,1,1,1,.5f};
        else if ("jazz".equals(id)) bands = new float[]{0,.5f,1,.5f,0,0,.5f,1,1,.5f};
        else { bands = new float[10]; id = "flat"; }
        flatSimple(); eqOn=true; tone = id; modified = false; savedId = ""; savedName = "";
    }

    void edited(boolean tonal) {
        modified = true;
        if (tonal) tone = "custom";
    }

    SoundState copy() {
        SoundState s = new SoundState();
        s.bands = bands.clone(); copySimpleTo(s); s.eqOn=eqOn; s.surroundReturn=surroundReturn; s.frequencies=frequencies.clone(); s.bandQs=bandQs.clone();
        s.types=types.clone(); s.bandOn=bandOn.clone(); s.spread=spread; s.preamp = preamp; s.output = output;
        s.presence = presence; s.air = air; s.q = q;
        s.compressor = compressor; s.virtualizer = virtualizer; s.surround = surround;
        s.modified = modified; s.threshold = threshold; s.ratio = ratio; s.attack = attack;
        s.release = release; s.knee = knee; s.makeup = makeup; s.strength = strength;
        s.tone = tone; s.savedId = savedId; s.savedName = savedName;
        return s;
    }

    void sanitize() {
        if(simple==null||simple.length!=3) simple=new float[3];
        for(int i=0;i<3;i++) simple[i]=clamp(simple[i],-12,12,0);
        if(simpleFrequencies==null||simpleFrequencies.length!=3)simpleFrequencies=SIMPLE_DEFAULTS.clone();
        for(int i=0;i<3;i++)simpleFrequencies[i]=clamp(simpleFrequencies[i],20,20000,SIMPLE_DEFAULTS[i]);
        Set<Integer> ids=new HashSet<>();
        simpleAdded.removeIf(b->b==null||b.id<3||b.id>1000000||!ids.add(b.id));
        while(simpleAdded.size()>7)simpleAdded.remove(simpleAdded.size()-1);
        for(SimpleBand b:simpleAdded){b.frequency=clamp(b.frequency,20,20000,1000);b.gain=clamp(b.gain,-12,12,0);}
        nextSimpleId=Math.max(3,Math.min(1000000,nextSimpleId));
        while(hasSimple(nextSimpleId))nextSimpleId=nextSimpleId>=1000000?3:nextSimpleId+1;
        if(surroundReturn==null||surroundReturn.length()>4096) surroundReturn="";
        if (bands == null || bands.length != 10) bands = new float[10];
        if(frequencies==null||frequencies.length!=10) frequencies=FREQUENCIES.clone();
        if(bandQs==null||bandQs.length!=10) bandQs=new float[10];
        if(types==null||types.length!=10) types=new int[]{-1,0,0,0,0,0,0,0,0,1};
        if(bandOn==null||bandOn.length!=10) { bandOn=new boolean[10];Arrays.fill(bandOn,true); }
        for (int i=0;i<10;i++) {
            bands[i] = clamp(bands[i],-12,12,0);
            frequencies[i]=clamp(frequencies[i],20,20000,FREQUENCIES[i]);
            bandQs[i]=bandQs[i]==0?0:clamp(bandQs[i],.4f,3,0);
            if(types[i]<-1||types[i]>1) types[i]=0;
        }
        spread=clamp(spread,0,100,0);
        preamp = clamp(preamp,-12,6,0); output = clamp(output,-12,9,0);
        presence = clamp(presence,-6,6,0); air = clamp(air,-6,9,0); q = clamp(q,.4f,3,1.15f);
        threshold = clamp(threshold,-45,0,-18); ratio = clamp(ratio,1,12,3);
        attack = clamp(attack,1,150,10); release = clamp(release,30,1000,180);
        knee = clamp(knee,0,40,6); makeup = clamp(makeup,0,6,0);
        strength = Math.max(0,Math.min(100,strength));
        tone = trim(tone,30); savedId = trim(savedId,80); savedName = trim(savedName,40);
    }

    String title() {
        if (!savedName.isEmpty()) return savedName + (modified ? " · modificado" : "");
        String label = "rock".equals(tone) ? "Rock" : "pop".equals(tone) ? "Pop" : "jazz".equals(tone) ? "Jazz" : "custom".equals(tone) ? "EQ personalizado" : "Flat";
        return (surround?"SURROUND · ":"") + label + (virtualizer && !surround ? " + Virtualizer" : "");
    }

    String pack() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(4);
            for (float v : bands) out.writeFloat(v);
            for (float v : new float[]{preamp,output,presence,air,q,threshold,ratio,attack,release,knee,makeup}) out.writeFloat(v);
            out.writeBoolean(compressor); out.writeBoolean(virtualizer); out.writeBoolean(surround); out.writeBoolean(modified);
            out.writeInt(strength); out.writeUTF(tone); out.writeUTF(savedId); out.writeUTF(savedName);
            for(int i=0;i<10;i++) { out.writeFloat(frequencies[i]);out.writeFloat(bandQs[i]);out.writeInt(types[i]);out.writeBoolean(bandOn[i]); }
            out.writeFloat(spread);
            for(float v:simple)out.writeFloat(v);out.writeBoolean(eqOn);out.writeUTF(surroundReturn);
            for(float v:simpleFrequencies)out.writeFloat(v);
            out.writeInt(nextSimpleId);out.writeInt(simpleAdded.size());
            for(SimpleBand b:simpleAdded){out.writeInt(b.id);out.writeFloat(b.frequency);out.writeFloat(b.gain);}
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    static SoundState unpack(String text) {
        if (text == null || text.length() > 8192) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));
            int version=in.readInt(); if(version<1||version>4) return null;
            SoundState s = new SoundState();
            for (int i=0;i<10;i++) s.bands[i] = in.readFloat();
            s.preamp=in.readFloat(); s.output=in.readFloat(); s.presence=in.readFloat(); s.air=in.readFloat(); s.q=in.readFloat();
            s.threshold=in.readFloat(); s.ratio=in.readFloat(); s.attack=in.readFloat(); s.release=in.readFloat(); s.knee=in.readFloat(); s.makeup=in.readFloat();
            s.compressor=in.readBoolean(); s.virtualizer=in.readBoolean(); s.surround=in.readBoolean(); s.modified=in.readBoolean();
            s.strength=in.readInt(); s.tone=in.readUTF(); s.savedId=in.readUTF(); s.savedName=in.readUTF();
            if(version>=2) {
                for(int i=0;i<10;i++) { s.frequencies[i]=in.readFloat();s.bandQs[i]=in.readFloat();s.types[i]=in.readInt();s.bandOn[i]=in.readBoolean(); }
                s.spread=in.readFloat();
            }
            if(version>=3) {
                for(int i=0;i<3;i++)s.simple[i]=in.readFloat();s.eqOn=in.readBoolean();s.surroundReturn=in.readUTF();
            } else if(s.surround) {
                // Preserve legacy presets' audible settings without adding the NEW hidden recipe.
                s.surround=false;if("surround".equals(s.tone))s.tone="custom";
            }
            if(version>=4){
                for(int i=0;i<3;i++)s.simpleFrequencies[i]=in.readFloat();
                s.nextSimpleId=in.readInt();int count=in.readInt();if(count<0||count>7)return null;
                for(int i=0;i<count;i++)s.simpleAdded.add(new SimpleBand(in.readInt(),in.readFloat(),in.readFloat()));
            }
            if (in.available() != 0) return null;
            s.sanitize(); return s;
        } catch (IOException | IllegalArgumentException e) { return null; }
    }

    static float clamp(float v,float min,float max,float fallback) {
        return Float.isNaN(v) || Float.isInfinite(v) ? fallback : Math.max(min,Math.min(max,v));
    }
    private static String trim(String text,int max) { return text == null ? "" : text.substring(0,Math.min(max,text.length())); }
}

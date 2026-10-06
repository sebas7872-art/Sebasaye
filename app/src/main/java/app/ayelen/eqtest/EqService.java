package app.ayelen.eqtest;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioPlaybackConfiguration;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Virtualizer;
import android.os.*;
import java.util.*;

public final class EqService extends Service {
    static final String ENABLE = "app.ayelen.eqtest.ENABLE";
    static final String STOP = "app.ayelen.eqtest.STOP";
    private static final String CHANNEL = "equalizer";
    private static final int NOTIFICATION = 41;
    final class LocalBinder extends Binder { EqService service() { return EqService.this; } }
    private final LocalBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<Integer, String> sessions = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, HeldEffect> effects = new LinkedHashMap<>();
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private SharedPreferences preferences;
    private boolean receiverRegistered;
    private boolean enabled;
    private boolean foreground;
    private boolean destroyed;
    private boolean applying;
    private boolean globalOnly;
    private boolean modern;
    private boolean headphoneMode, deviceCallbackRegistered, playbackCallbackRegistered, lastMusicActive;
    // Observed on Motorola / Android 15 / NXP: session 0 can remain reported
    // BINAURAL after Spotify closes its track, without audible virtualization.
    // Bind ONLY that Virtualizer to the announced player session; DP stays global.
    private boolean sessionVirtualizerCompatibility;
    private long virtualSessionReadyAt;
    private AudioManager audioManager;
    private final SoundModel model = new SoundModel();
    private float[] curve = new float[EqResponse.COUNT];
    private boolean updateQueued, reconnectPending, switchingOff;
    private float transitionDb;
    private final ModeTransition transition = new ModeTransition(new ModeTransition.Clock(){
        public void post(Runnable r,int delay){handler.postDelayed(r,delay);}
        public void remove(Runnable r){handler.removeCallbacks(r);}
    },this::transitionGain);
    private void transitionGain(float db){
        transitionDb=db;
        for(HeldEffect h:effects.values())if(h.effect instanceof DynamicsProcessing)try{
            if(h.effect.hasControl()){
                SoundState actual=h.applied==null?model.current:h.applied;
                ((DynamicsProcessing)h.effect).setPostEqAllChannelsTo(outputStage(actual.makeup+actual.output+db));
            }
        }catch(RuntimeException ignored){}
    }
    private void flushNow(){
        handler.removeCallbacks(update);updateQueued=false;
        curve=EqResponse.gains(model.current);
        if(reconnectPending){reconnectPending=false;reconnect();}
        else if(enabled)for(Map.Entry<Integer,HeldEffect> entry:effects.entrySet())apply(entry.getKey(),entry.getValue());
        changed();
    }
    private void modeChanged(){
        switchingOff=false;
        handler.removeCallbacks(update);updateQueued=false;
        handler.removeCallbacks(persist);handler.postDelayed(persist,400);
        boolean playing=false;try{playing=audioManager!=null&&audioManager.isMusicActive();}catch(RuntimeException ignored){}
        if(enabled&&modern&&playing&&!effects.isEmpty())transition.start(this::flushNow);else flushNow();
        changed();
    }
    void editSwitch(SoundState next,boolean tonal){model.edit(next,tonal);modeChanged();}
    void disableWithTransition(){
        if(enabled&&modern&&!effects.isEmpty())transition.start(this::neutralForSwitchOff,this::disable);else disable();
    }
    private void neutralForSwitchOff(){
        switchingOff=true;
        // Neutralize under attenuation; restore dry audio before releasing our effect.
        for(HeldEffect held:effects.values())if(held.effect instanceof DynamicsProcessing)try{
            if(!held.effect.hasControl())continue;
            DynamicsProcessing dp=(DynamicsProcessing)held.effect;
            DynamicsProcessing.Eq eq=dp.getPreEqByChannelIndex(0);eq.setEnabled(false);dp.setPreEqAllChannelsTo(eq);
            for(float[] channel:held.appliedGains)Arrays.fill(channel,Float.NaN);
            SoundState neutral=new SoundState();
            dp.setInputGainAllChannelsTo(0);dp.setMbcAllChannelsTo(compressor(neutral));
            held.applied=neutral;
            dp.setPostEqAllChannelsTo(outputStage(transitionDb));
            if(held.activation!=null)held.activation.cancel();
            if(held.virtual!=null&&held.virtual.hasControl())held.virtual.setEnabled(false);
            held.appliedMode=-1;
        }catch(RuntimeException ignored){}
    }
    private void surroundRouting(){
        if(model.current.surround){
            if(!preferences.getBoolean("surroundRouting5",false))preferences.edit()
                .putBoolean("returnGlobal5",globalOnly).putBoolean("returnModern5",modern)
                .putBoolean("returnHeadphones5",headphoneMode).putBoolean("surroundRouting5",true).apply();
            reconnectPending|=!globalOnly||!modern;globalOnly=true;modern=true;headphoneMode=true;
        }else if(preferences.getBoolean("surroundRouting5",false)){
            boolean g=preferences.getBoolean("returnGlobal5",true),m=preferences.getBoolean("returnModern5",true);
            reconnectPending|=g!=globalOnly||m!=modern;globalOnly=g;modern=m;
            headphoneMode=preferences.getBoolean("returnHeadphones5",true);
            preferences.edit().putBoolean("surroundRouting5",false).apply();
        }
        preferences.edit().putBoolean("global",globalOnly).putBoolean("modern",modern).putBoolean("virtualHeadphones",headphoneMode).apply();
    }
    private final Runnable persist = () -> saveState();
    private final Runnable update = () -> {
        updateQueued = false;
        if(transition.busy())queueUpdate();else flushNow();
    };
    private String lastError = "";
    private Runnable observer;
    private int virtualTargetSession(int processorSession) {
        if(processorSession!=0 || !sessionVirtualizerCompatibility) return processorSession;
        int latest=0;
        for(int id:sessions.keySet()) latest=id;
        return latest; // No session announced yet: preserve the initial global fallback.
    }
    private final Runnable refreshVirtualBinding = () -> {
        if(destroyed || switchingOff || !enabled || !globalOnly || !model.current.virtualizer) return;
        HeldEffect held=effects.get(0);
        if(held==null)return;
        held.virtualFailed=false;
        applyVirtualizer(0,held);
        changed();
    };
    private void renewVirtualBinding(String reason) {
        if(!sessionVirtualizerCompatibility || !globalOnly)return;
        handler.removeCallbacks(refreshVirtualBinding);
        virtualSessionReadyAt=SystemClock.uptimeMillis()+250;
        HeldEffect held=effects.get(0);
        if(held!=null) {
            releaseVirtualizer(held);held.virtualFailed=false;held.virtualCreateFailures=0;
        }
        if(enabled && !destroyed && model.current.virtualizer) {
            event("Virtualizer · nueva conexión por "+reason+" · destino="+virtualTargetSession(0));
            handler.postDelayed(refreshVirtualBinding,250);
        }
    }
    private final Runnable refreshVirtualRoute = () -> {
        if(destroyed || !enabled || !model.current.virtualizer) return;
        if(sessionVirtualizerCompatibility && globalOnly) { renewVirtualBinding("cambio de salida");return; }
        for(Map.Entry<Integer,HeldEffect> entry:effects.entrySet()) {
            HeldEffect held=entry.getValue();
            held.virtualFailed=false;held.appliedMode=-1;applyVirtualizer(entry.getKey(),held);
        }
        changed();
    };
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { virtualRouteChanged(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { virtualRouteChanged(); }
    };
    private final Runnable refreshVirtualPlayback = () -> {
        if(destroyed || !enabled || !model.current.virtualizer) return;
        for(Map.Entry<Integer,HeldEffect> entry:effects.entrySet()) {
            HeldEffect held=entry.getValue();
            if(held.activation!=null && held.activation.running()) continue;
            try {
                if(held.virtual!=null && held.virtualSession==virtualTargetSession(entry.getKey()) && held.virtual.hasControl()
                    && held.virtual.getEnabled() && held.virtual.getVirtualizationMode()!=Virtualizer.VIRTUALIZATION_MODE_OFF) continue;
            } catch(RuntimeException ignored) {}
            held.virtualFailed=false;held.appliedMode=-1;applyVirtualizer(entry.getKey(),held);
        }
    };
    private final AudioManager.AudioPlaybackCallback playbackCallback=new AudioManager.AudioPlaybackCallback() {
        @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> ignored) {
            if(audioManager==null || destroyed) return;
            try {
                boolean active=audioManager.isMusicActive();
                if(active && !lastMusicActive && enabled && model.current.virtualizer) {
                    handler.removeCallbacks(refreshVirtualPlayback);
                    handler.postDelayed(refreshVirtualPlayback,100);
                }
                lastMusicActive=active;
            } catch(RuntimeException ignoredException) {}
        }
    };

    private void virtualRouteChanged() {
        // Connected-device events are not proof of the current media route.
        // Only reapply the selected effect mode; never reroute audio or rebuild DP.
        handler.removeCallbacks(refreshVirtualRoute);
        if(enabled && !destroyed) handler.postDelayed(refreshVirtualRoute,200);
    }

    private static final class HeldEffect {
        final AudioEffect effect;
        final Equalizer.Settings original;
        final boolean originalEnabled;
        final float[][] appliedGains;
        SoundState applied;
        Virtualizer virtual;
        int virtualSession=-1, virtualCreateFailures;
        short originalStrength;
        boolean originalVirtualEnabled, virtualFailed, virtualCaptured;
        int appliedStrength = -1;
        int appliedMode = -1;
        String binauralSupport="sin consultar", transauralSupport="sin consultar", forceResult="sin solicitar";
        VirtualizerStart activation;
        HeldEffect(Equalizer e) {
            effect = e;
            original = e.getProperties();
            originalEnabled = e.getEnabled();
            appliedGains = null;
        }
        HeldEffect(DynamicsProcessing e) {
            effect = e;
            original = null;
            originalEnabled = false;
            appliedGains = new float[e.getChannelCount()][EqResponse.COUNT];
            for(float[] channel:appliedGains) Arrays.fill(channel, Float.NaN);
        }
    }

    static DynamicsProcessing.Config modernConfig() {
        // Fixed native topology. Disabled stages are bypassed; no per-touch rebuilding.
        // No limiter/AGC/LoudnessEnhancer. Compression is explicitly user controlled.
        DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, EqResponse.COUNT,
            true, 1, true, 1, false);
        DynamicsProcessing.Eq eq = new DynamicsProcessing.Eq(true, false, EqResponse.COUNT);
        for (int i=0;i<EqResponse.COUNT;i++) eq.setBand(i,new DynamicsProcessing.EqBand(true,EqResponse.UPPER[i],0));
        builder.setPreEqAllChannelsTo(eq);
        builder.setInputGainAllChannelsTo(0);
        builder.setMbcAllChannelsTo(compressor(new SoundState()));
        builder.setPostEqAllChannelsTo(outputStage(0));
        return builder.build();
    }

    private static DynamicsProcessing.Mbc compressor(SoundState s) {
        DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true,s.compressor,1);
        // Band-enabled=false MUTES a band's frequencies, so bypass the STAGE instead.
        mbc.setBand(0,new DynamicsProcessing.MbcBand(true,24000,s.attack,s.release,s.ratio,
            s.threshold,s.knee,-120,1,0,0));
        return mbc;
    }
    private static DynamicsProcessing.Eq outputStage(float gain) {
        DynamicsProcessing.Eq eq = new DynamicsProcessing.Eq(true,gain!=0,1);
        eq.setBand(0,new DynamicsProcessing.EqBand(true,24000,gain));
        return eq;
    }

    private final BroadcastReceiver sessionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
            String owner = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            if (id <= 0 || owner == null || owner.length() > 180) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                noteSession(id, owner);
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                // Ignore a close sent for a different application's session.
                if (owner.equals(sessions.get(id))) {
                    boolean closesVirtual=sessionVirtualizerCompatibility && globalOnly && virtualTargetSession(0)==id;
                    sessions.remove(id);
                    release(id);
                    event("Sesión cerrada: " + id);
                    if(closesVirtual)renewVirtualBinding("cierre de sesión "+id);
                    if (enabled) reconcile();
                    changed();
                }
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("eq-test", MODE_PRIVATE);
        SoundState saved = SoundState.unpack(preferences.getString("sound3",null));
        if (saved != null) model.current = saved;
        else {
            int b=preferences.getInt("bass",0),m=preferences.getInt("mids",0),t=preferences.getInt("treble",0);
            for (int i=0;i<10;i++) model.current.bands[i]=ToneCurve.milliBels((int)(SoundState.FREQUENCIES[i]*1000),b,m,t,-1200,600)/100f;
            if (b!=0 || m!=0 || t!=0) model.current.tone="custom";
        }
        model.beforeSurround = SoundState.unpack(preferences.getString("beforeSurround3",null));
        if (model.beforeSurround!=null && model.beforeSurround.surround) model.beforeSurround=null;
        curve=EqResponse.gains(model.current);
        globalOnly = preferences.getBoolean("global", false);
        modern = preferences.getBoolean("modern", true);
        headphoneMode = preferences.getBoolean("virtualHeadphones",true);
        // Start with SURROUND off; saved personal presets still retain its explicit state.
        if(model.current.surround)model.leaveSurround();
        surroundRouting();reconnectPending=false;
        audioManager = getSystemService(AudioManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Ecualizador en uso", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantiene el ecualizador activo mientras escuchás otra aplicación.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(sessionReceiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(sessionReceiver, filter);
        receiverRegistered = true;
        try {
            if(audioManager!=null) { audioManager.registerAudioDeviceCallback(deviceCallback,handler);deviceCallbackRegistered=true; }
        } catch(RuntimeException e) { event("Avisos de salida no disponibles: "+e.getClass().getSimpleName()); }
        try {
            if(audioManager!=null) {
                lastMusicActive=audioManager.isMusicActive();
                audioManager.registerAudioPlaybackCallback(playbackCallback,handler);playbackCallbackRegistered=true;
            }
        } catch(RuntimeException e) { event("Avisos de reproducción no disponibles: "+e.getClass().getSimpleName()); }
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            disable();
            return START_NOT_STICKY;
        }
        if (ENABLE.equals(intent.getAction())) {
            try {
                switchingOff=false;
                if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                else startForeground(NOTIFICATION, notification());
                foreground = true;
                enabled = true;
                lastError = "";
                if(modern){transitionDb=-80;reconcile();transition.startMuted(this::flushNow);}
                else reconcile();
                event("Ecualizador activado");
            } catch (RuntimeException e) {
                fail("No pude iniciar el servicio", e);
                disable();
            }
            changed();
        }
        return START_NOT_STICKY;
    }

    boolean isEnabled() { return enabled; }
    boolean isGlobalOnly() { return globalOnly; }
    boolean isModern() { return modern; }
    boolean isHeadphoneMode() { return headphoneMode; }
    void setHeadphoneMode(boolean value) {
        if(headphoneMode==value) return;
        headphoneMode=value;preferences.edit().putBoolean("virtualHeadphones",value).apply();
        handler.removeCallbacks(refreshVirtualRoute);
        for(HeldEffect h:effects.values())h.appliedMode=-1;
        modeChanged();
    }
    void setModern(boolean value) {
        if (modern == value || model.current.surround) return;
        modern = value;
        preferences.edit().putBoolean("modern", value).apply();
        reconnect();
    }
    int visualSession() {
        if(!enabled || effects.isEmpty()) return -1;
        int selected=-1;
        for(Map.Entry<Integer,HeldEffect> e:effects.entrySet()) try {
            if(e.getValue().effect.hasControl() && e.getValue().effect.getEnabled()) selected=e.getKey();
        } catch(RuntimeException ignored) {}
        return selected;
    }
    void setSpread(float amount) {
        model.current.spread=SoundState.clamp(amount,0,100,0);
        model.current.edited(false); queueUpdate();
    }
    SoundState sound() { return model.current.copy(); }
    void observe(Runnable listener) { observer = listener; }

    void preset(String name) { model.preset(name); surroundRouting(); modeChanged(); saveState(); }
    void setVirtualizer(boolean on) { model.virtualize(on); modeChanged(); }
    void edit(SoundState next,boolean tonal) { model.edit(next,tonal); queueUpdate(); }

    private void queueUpdate() {
        if (!updateQueued) { updateQueued=true; handler.postDelayed(update,32); }
        handler.removeCallbacks(persist); handler.postDelayed(persist,400);
        changed();
    }
    private void saveState() {
        if (preferences==null) return;
        preferences.edit().putString("sound3",model.current.pack())
            .putString("beforeSurround3",model.beforeSurround==null?null:model.beforeSurround.pack()).apply();
    }
    List<SoundState> savedSounds() {
        List<SoundState> list=new ArrayList<>();
        for (String id:preferences.getStringSet("sounds3",Collections.emptySet())) {
            SoundState s=SoundState.unpack(preferences.getString("sound3:"+id,null));
            if (s!=null && id.equals(s.savedId)) list.add(s);
        }
        list.sort((a,b)->a.savedName.compareToIgnoreCase(b.savedName)); return list;
    }
    boolean saveSound(String name,boolean replace) {
        name=name.trim(); if(name.isEmpty() || name.length()>40) return false;
        Set<String> ids=new HashSet<>(preferences.getStringSet("sounds3",Collections.emptySet()));
        String id=model.current.savedId;
        if (!replace || id.isEmpty() || !ids.contains(id)) {
            if(ids.size()>=30) return false;
            id=UUID.randomUUID().toString();
        }
        model.current.savedId=id; model.current.savedName=name; model.current.modified=false;
        ids.add(id); preferences.edit().putStringSet("sounds3",ids).putString("sound3:"+id,model.current.pack()).apply();
        saveState(); changed(); return true;
    }
    void loadSound(SoundState s) { model.load(s); surroundRouting(); modeChanged(); saveState(); }
    void deleteSound(String id) {
        Set<String> ids=new HashSet<>(preferences.getStringSet("sounds3",Collections.emptySet()));
        ids.remove(id); preferences.edit().putStringSet("sounds3",ids).remove("sound3:"+id).apply();
        if(id.equals(model.current.savedId)) { model.current.savedId="";model.current.savedName="";saveState(); }
        changed();
    }

    void setGlobalOnly(boolean value) {
        if (globalOnly == value || model.current.surround) return;
        globalOnly = value;
        preferences.edit().putBoolean("global", value).apply();
        reconnect();
    }

    void noteSession(int id, String owner) {
        if (id <= 0 || destroyed) return;
        if (owner == null || owner.isEmpty()) owner = "Reproductor";
        boolean added = !sessions.containsKey(id);
        sessions.put(id, owner);
        while (sessions.size() > 8) {
            int first = sessions.keySet().iterator().next();
            sessions.remove(first);
            release(first);
        }
        if (added) event("Sesión " + id + " · " + owner);
        boolean reusingGlobal=globalOnly && effects.containsKey(0);
        if (enabled) reconcile();
        if(added && enabled && model.current.virtualizer && reusingGlobal) {
            if(sessionVirtualizerCompatibility) {
                // CLOSE -> OPEN may reuse the same numeric ID. The old native
                // handle was released at CLOSE; do not mistake it for a duplicate.
                renewVirtualBinding("apertura de sesión "+id);
            } else {
            // DP remains attached to session 0; only revalidate its Virtualizer
            // when Spotify opens a new session without stopping the global mix.
            handler.removeCallbacks(refreshVirtualPlayback);
            handler.postDelayed(refreshVirtualPlayback,250);
            }
        }
        changed();
    }

    void reconnect() {
        releaseAll();
        lastError = "";
        if (enabled) reconcile();
        changed();
    }

    private void reconcile() {
        if (!enabled || destroyed) return;
        if (globalOnly) {
            for (int id : new ArrayList<>(effects.keySet())) if (id != 0) release(id);
            attach(0);
            return;
        }
        if (!sessions.isEmpty()) {
            // Global processing and per-session processing must never run together.
            release(0);
            for (int id : sessions.keySet()) attach(id);
            if (!effects.isEmpty()) return;
        }
        // Compatibility fallback. Acceptance by Android is not proof of audible effect.
        attach(0);
    }

    private void attach(final int id) {
        if (effects.containsKey(id)) return;
        AudioEffect eq = null;
        try {
            final HeldEffect held;
            if (modern) {
                DynamicsProcessing processor = new DynamicsProcessing(10, id, modernConfig());
                eq = processor;
                held = new HeldEffect(processor);
                if (processor.getChannelCount() < 1) throw new IllegalStateException("Sin canales de audio");
                if (processor.getConfig().getPreEqBandCount()!=EqResponse.COUNT) throw new IllegalStateException("El motor no aceptó la curva de EQ");
            } else {
                Equalizer classic = new Equalizer(10, id);
                eq = classic;
                held = new HeldEffect(classic);
                if (classic.getNumberOfBands() <= 0) throw new IllegalStateException("Sin bandas de EQ");
            }
            effects.put(id, held);
            eq.setControlStatusListener((effect, granted) -> handler.post(() -> {
                if (destroyed || effects.get(id) != held) return;
                event(granted ? "Control disponible: " + id : "Otro ecualizador tiene el control: " + id);
                if (granted && held.appliedGains != null) for(float[] channel:held.appliedGains) Arrays.fill(channel, Float.NaN);
                if (granted) held.applied=null;
                if (granted && enabled) apply(id,held);
                changed();
            }));
            eq.setEnableStatusListener((effect, value) -> handler.post(() -> {
                if (!destroyed && effects.get(id) == held) changed();
            }));
            curve=EqResponse.gains(model.current);
            apply(id,held);
            event((modern ? "DynamicsProcessing" : "Equalizer clásico") + ": " + (id == 0 ? "salida global" : "sesión " + id));
        } catch (RuntimeException e) {
            effects.remove(id);
            if (eq != null) { try { eq.release(); } catch (RuntimeException ignored) {} }
            fail((modern ? "DynamicsProcessing " : "EQ clásico ") + (id == 0 ? "global" : "sesión " + id), e);
        }
    }

    private void apply(int id,HeldEffect held) {
        if (applying||switchingOff) return;
        applying = true;
        try {
            if (!held.effect.hasControl()) return;
            SoundState s=model.current, old=held.applied;
            if (held.effect instanceof DynamicsProcessing) {
                DynamicsProcessing eq = (DynamicsProcessing) held.effect;
                // Reuse the existing native regions and nodes; only update changed parameters.
                for(int channel=0;channel<held.appliedGains.length;channel++) {
                    boolean tonal=false;
                    for(int band=0;band<EqResponse.COUNT;band++) {
                        float desired=curve[band]+(held.appliedGains.length==2?StereoSpread.offset(EqResponse.CENTERS[band],s.spread,channel):0);
                        if(Math.abs(desired)>.0001f) tonal=true;
                    }
                    if(old==null || !Arrays.equals(old.bands,s.bands) || !Arrays.equals(old.frequencies,s.frequencies)
                        || !Arrays.equals(old.bandQs,s.bandQs) || !Arrays.equals(old.types,s.types) || !Arrays.equals(old.bandOn,s.bandOn)
                        || old.surround!=s.surround || old.eqOn!=s.eqOn || !old.sameSimpleEq(s)
                        || old.q!=s.q || old.presence!=s.presence || old.air!=s.air || old.spread!=s.spread) {
                        DynamicsProcessing.Eq stage=eq.getPreEqByChannelIndex(channel);
                        if(stage.isEnabled()!=tonal) { stage.setEnabled(tonal);eq.setPreEqByChannelIndex(channel,stage); }
                    }
                    for(int band=0;band<EqResponse.COUNT;band++) {
                        float desired=curve[band]+(held.appliedGains.length==2?StereoSpread.offset(EqResponse.CENTERS[band],s.spread,channel):0);
                        if(held.appliedGains[channel][band]!=desired) {
                            eq.setPreEqBandByChannelIndex(channel,band,new DynamicsProcessing.EqBand(true,EqResponse.UPPER[band],desired));
                            held.appliedGains[channel][band]=desired;
                        }
                    }
                }
                if(old==null || old.preamp!=s.preamp) eq.setInputGainAllChannelsTo(s.preamp);
                if(old==null || old.compressor!=s.compressor || old.threshold!=s.threshold || old.ratio!=s.ratio || old.attack!=s.attack || old.release!=s.release || old.knee!=s.knee)
                    eq.setMbcAllChannelsTo(compressor(s));
                // Makeup remains effective when the compressor is OFF.
                if(old==null || old.makeup+old.output!=s.makeup+s.output) eq.setPostEqAllChannelsTo(outputStage(s.makeup+s.output+transitionDb));
            } else {
                Equalizer eq = (Equalizer) held.effect;
                short[] range = eq.getBandLevelRange();
                short count = eq.getNumberOfBands();
                for (short band = 0; band < count; band++) {
                    short desired=(short)Math.max(range[0],Math.min(range[1],Math.round(EqResponse.at(s,eq.getCenterFreq(band)/1000f)*100)));
                    if (eq.getBandLevel(band) != desired) eq.setBandLevel(band, desired);
                }
            }
            int result = held.effect.setEnabled(enabled);
            if (result != AudioEffect.SUCCESS) throw new IllegalStateException("Activación rechazada: " + result);
            if(s.surround && old!=null && !old.surround)held.appliedMode=-1;
            held.applied=s.copy();
            applyVirtualizer(id,held);
        } catch (RuntimeException e) {
            fail("No pude aplicar el ajuste", e);
        } finally { applying = false; }
    }

    private void applyVirtualizer(int id,HeldEffect held) {
        if(switchingOff)return;
        if (!model.current.virtualizer || !enabled) {
            releaseVirtualizer(held); held.virtualFailed=false; return;
        }
        if(held.virtualFailed) return;
        if(id==0 && sessionVirtualizerCompatibility) {
            long wait=virtualSessionReadyAt-SystemClock.uptimeMillis();
            if(wait>0) {
                handler.removeCallbacks(refreshVirtualBinding);
                handler.postDelayed(refreshVirtualBinding,wait);return;
            }
        }
        try {
            int target=virtualTargetSession(id);
            if(held.virtual!=null && held.virtualSession!=target)releaseVirtualizer(held);
            if(held.virtual==null) {
                Virtualizer v=new Virtualizer(10,target);
                held.virtual=v;held.virtualSession=target;held.originalStrength=v.getRoundedStrength();
                held.originalVirtualEnabled=v.getEnabled(); held.virtualCaptured=true; held.appliedStrength=-1;held.appliedMode=-1;
                if(id==0 && !sessionVirtualizerCompatibility
                    && "motorola".equalsIgnoreCase(Build.MANUFACTURER)
                    && UUID.fromString("1d4033c0-8557-11df-9f2d-0002a5d5c51b").equals(v.getDescriptor().uuid)) {
                    sessionVirtualizerCompatibility=true;
                    event("Virtualizer · compatibilidad Motorola/NXP por sesión; DP global sin cambios");
                    if(virtualTargetSession(0)!=target) { renewVirtualBinding("sesión ya anunciada");return; }
                }
                held.binauralSupport=virtualCapability(v,Virtualizer.VIRTUALIZATION_MODE_BINAURAL);
                held.transauralSupport=virtualCapability(v,Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL);
                v.setControlStatusListener((effect,granted)->handler.post(()->{
                    if(destroyed || effects.get(id)!=held || held.virtual!=effect) return;
                    held.appliedStrength=-1;held.appliedMode=-1;
                    if(!granted && held.activation!=null) held.activation.cancel();
                    if(granted && enabled) applyVirtualizer(id,held);
                    changed();
                }));
                v.setEnableStatusListener((effect,on)->handler.post(()->{
                    if(destroyed || effects.get(id)!=held || held.virtual!=effect) return;
                    event("Virtualizer "+held.virtualSession+" · enabled="+on);
                    // Do not turn our own delayed OFF/ON events into fresh retries.
                    if(held.activation!=null)held.activation.check();
                    changed();
                }));
                event("Virtualizer nativo: "+held.virtualSession+" · "+v.getDescriptor().name);
            }
            if(!held.virtual.hasControl()) return;
            int desired=model.current.strength*10;
            if(held.appliedStrength!=desired) { held.virtual.setStrength((short)desired);held.appliedStrength=desired; }
            int requested=headphoneMode?Virtualizer.VIRTUALIZATION_MODE_BINAURAL:Virtualizer.VIRTUALIZATION_MODE_AUTO;
            if(held.appliedMode!=requested) {
                // This caches the REQUEST only. Activation is confirmed separately
                // against the native mode and retried within a bounded window.
                held.appliedMode=requested;held.forceResult="pendiente";
                if(held.activation==null) held.activation=createVirtualActivation(id,held,held.virtual);
                held.activation.start(requested);
            }
            held.virtualCreateFailures=0;
        } catch(RuntimeException e) {
            releaseVirtualizer(held);held.virtualFailed=true;
            fail("Virtualizer no disponible; EQ sigue activo",e);
            // A new AudioTrack may not accept an effect immediately. At most
            // three creation attempts per binding event, never a permanent timer.
            if(id==0 && sessionVirtualizerCompatibility && virtualTargetSession(0)>0
                && ++held.virtualCreateFailures<3) {
                long delay=500L*held.virtualCreateFailures;
                virtualSessionReadyAt=SystemClock.uptimeMillis()+delay;
                handler.removeCallbacks(refreshVirtualBinding);
                handler.postDelayed(refreshVirtualBinding,delay);
            }
        }
    }

    private VirtualizerStart createVirtualActivation(int id,HeldEffect held,Virtualizer expected) {
        return new VirtualizerStart(new VirtualizerStart.Engine() {
            public boolean current() { return !destroyed && !switchingOff && enabled && model.current.virtualizer && effects.get(id)==held && held.virtual==expected; }
            public boolean control() { return expected.hasControl(); }
            public boolean enabled() { return expected.getEnabled(); }
            public void enable() {
                if(expected.setEnabled(true)!=AudioEffect.SUCCESS) throw new IllegalStateException("Activación rechazada");
            }
            public void disable() {
                if(expected.setEnabled(false)!=AudioEffect.SUCCESS) throw new IllegalStateException("Recuperación rechazada");
            }
            public boolean playing() {
                try { return audioManager!=null && audioManager.isMusicActive(); }
                catch(RuntimeException ignored) { return false; }
            }
            public boolean force(int mode) { return expected.forceVirtualizationMode(mode); }
            public int mode() { return expected.getVirtualizationMode(); }
            public void refreshStrength() {
                int value=model.current.strength*10;
                expected.setStrength((short)value);held.appliedStrength=value;
            }
        },new VirtualizerStart.Scheduler() {
            public void post(Runnable task,int delay) { handler.postDelayed(task,delay); }
            public void remove(Runnable task) { handler.removeCallbacks(task); }
        },(phase,accepted,actual,attempt)->{
            if(destroyed || effects.get(id)!=held || held.virtual!=expected) return;
            if(accepted!=null) held.forceResult=Boolean.toString(accepted);
            event("Virtualizer "+held.virtualSession+" · "+phase+" · intento="+attempt+" · aceptado="+held.forceResult+" · modo="+virtualModeName(actual));
            changed();
        });
    }

    private void releaseVirtualizer(HeldEffect held) {
        if(held.activation!=null) held.activation.cancel();
        held.activation=null;
        Virtualizer v=held.virtual; held.virtual=null;held.appliedStrength=-1;held.appliedMode=-1;
        held.virtualSession=-1;
        if(v==null) return;
        try {
            v.setControlStatusListener(null);
            v.setEnableStatusListener(null);
            if(held.virtualCaptured && v.hasControl()) {
                // Remove our forced mode before restoring the captured effect state.
                try { v.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO); } catch(RuntimeException ignored) {}
                v.setStrength(held.originalStrength);v.setEnabled(held.originalVirtualEnabled);
            }
        } catch(RuntimeException ignored) {
        } finally { held.virtualCaptured=false; try { v.release(); } catch(RuntimeException ignored) {} }
    }

    private static String virtualCapability(Virtualizer v,int mode) {
        try { return Boolean.toString(v.canVirtualize(AudioFormat.CHANNEL_OUT_STEREO,mode)); }
        catch(RuntimeException e) { return "error "+e.getClass().getSimpleName(); }
    }
    private static String virtualModeName(int mode) {
        if(mode==Virtualizer.VIRTUALIZATION_MODE_BINAURAL) return "BINAURAL";
        if(mode==Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL) return "TRANSAURAL";
        if(mode==Virtualizer.VIRTUALIZATION_MODE_AUTO) return "AUTO";
        return mode==Virtualizer.VIRTUALIZATION_MODE_OFF?"OFF":"desconocido("+mode+")";
    }
    private String virtualDescription(int id,HeldEffect h) {
        Virtualizer v=h.virtual;
        if(v==null) return "Virtualizer "+id+" · sin instancia";
        try {
            return "Virtualizer "+h.virtualSession+" (EQ en "+id+") · control="+v.hasControl()+" · enabled="+v.getEnabled()
                +" · pedido="+virtualModeName(h.appliedMode)+" · aceptado="+h.forceResult
                +" · modo informado="+virtualModeName(v.getVirtualizationMode())
                +" · activación="+(h.activation==null?"sin iniciar":h.activation.phase()+"/"+h.activation.attempts())
                +" · recuperación OFF/ON="+(h.activation!=null && h.activation.rearmed())
                +" · intensidad="+v.getRoundedStrength()+"/1000 · regulable="+v.getStrengthSupported();
        } catch(RuntimeException e) { return "Virtualizer "+id+" · consulta falló: "+e.getClass().getSimpleName(); }
    }

    String virtualStatus() {
        if(!model.current.virtualizer) return "Virtualizer apagado";
        if(!enabled) return "Virtualizer preparado · activá el EQ para escucharlo";
        boolean failed=false;
        for(HeldEffect h:effects.values()) {
            failed|=h.virtualFailed;
            if(h.virtual!=null) try {
                if(!h.virtual.hasControl()) return "Virtualizer sin control · revisá otros ecualizadores";
                if(h.activation!=null && h.activation.running()) return "Activando Virtualizer · "+(headphoneMode?"Auriculares":"Automático");
                if(!h.virtual.getEnabled()) return "Virtualizer deshabilitado por el motor · revisá el diagnóstico";
                int mode=h.virtual.getVirtualizationMode();
                if(mode==Virtualizer.VIRTUALIZATION_MODE_OFF) return "Virtualizer informa OFF · modo "+(headphoneMode?"Auriculares":"Automático")+" · revisá el diagnóstico";
                return "Virtualizer "+(mode==Virtualizer.VIRTUALIZATION_MODE_BINAURAL?"auriculares":"parlantes")+" · "+Math.round(h.virtual.getRoundedStrength()/10f)+"%"+
                    (h.virtual.getStrengthSupported()?"":" · intensidad fija del dispositivo");
            } catch(RuntimeException ignored) {}
        }
        return failed?"Virtualizer no disponible · el EQ continúa":"Virtualizer esperando conexión";
    }

    private void release(int id) {
        HeldEffect held = effects.remove(id);
        if (held == null) return;
        releaseVirtualizer(held);
        try {
            held.effect.setControlStatusListener(null);
            held.effect.setEnableStatusListener(null);
            if (held.effect.hasControl()) {
                if (held.effect instanceof Equalizer) {
                    ((Equalizer) held.effect).setProperties(held.original);
                    held.effect.setEnabled(held.originalEnabled);
                } else {
                    held.effect.setEnabled(false);
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            try { held.effect.release(); } catch (RuntimeException ignored) {}
        }
    }

    private void releaseAll() {
        handler.removeCallbacks(refreshVirtualBinding);
        for (int id : new ArrayList<>(effects.keySet())) release(id);
    }

    void disable() {
        transition.cancel();
        switchingOff=false;
        enabled = false;
        saveState();
        releaseAll();
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        stopSelf();
        changed();
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, EqService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Sanavera EQ · activo")
            .setContentText("Tocá para comparar el sonido. Podés detener el EQ acá.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Apagar", stop).build())
            .build();
    }

    String headline() {
        if (!enabled) return "Ecualizador apagado";
        for (HeldEffect held : effects.values()) {
            try {
                if (held.effect.hasControl() && held.effect.getEnabled())
                    return (modern ? "DP" : "Clásico") + (effects.containsKey(0) ? " · salida global activa" : " · sesión activa");
            } catch (RuntimeException ignored) {}
        }
        return effects.isEmpty() ? "Motor no conectado" : "Sin control del ecualizador";
    }

    String details() {
        StringBuilder text = new StringBuilder();
        if (!enabled) {
            text.append("Activá el EQ, abrí Spotify y poné una canción.");
        } else if (effects.containsKey(0)) {
            text.append("Android aceptó el motor global. Eso no garantiza que Spotify pase por él: compará ON/OFF.");
        } else if (effects.isEmpty()) {
            text.append("El teléfono no aceptó este motor en la conexión elegida. Probá reabrir Spotify y reconectar; enviame el diagnóstico si sigue igual.");
        } else {
            for (int id : effects.keySet()) text.append(sessions.get(id)).append(" · sesión ").append(id).append('\n');
        }
        if (!lastError.isEmpty()) text.append("\n\nÚltimo aviso: ").append(lastError);
        if (!modern) text.append("\nModo clásico: sólo aplica EQ; ganancia y compresor no disponibles.");
        return text.toString().trim();
    }

    String diagnostics() {
        StringBuilder out = new StringBuilder("Sanavera EQ 1.0.0\n");
        out.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE).append('\n');
        out.append(headline()).append("\nModo: ").append(globalOnly ? "global" : "automático");
        out.append("\nMotor solicitado: ").append(modern ? "DynamicsProcessing" : "Equalizer clásico");
        SoundState s=model.current;
        out.append("\nSonido: ").append(s.title()).append("\nBandas: ").append(Arrays.toString(s.bands));
        out.append("\nFrecuencias: ").append(Arrays.toString(s.frequencies));
        out.append("\nApertura espectral L/R: ").append(s.spread).append("% · no Mid/Side");
        out.append("\nPreamp: ").append(s.preamp).append("; salida: ").append(s.output).append("; makeup: ").append(s.makeup).append(" dB; Q: ").append(s.q);
        out.append("\nPresencia: ").append(s.presence).append("; brillo 10 kHz: ").append(s.air);
        out.append("\nCompresor: ").append(s.compressor).append("; umbral: ").append(s.threshold).append("; ratio: ").append(s.ratio);
        out.append("; ataque/release: ").append(s.attack).append('/').append(s.release).append(" ms; rodilla: ").append(s.knee);
        out.append("\nLimiter: ausente; AGC: ausente\n").append(virtualStatus()).append('\n');
        out.append("Modo espacial solicitado: ").append(headphoneMode?"Auriculares / BINAURAL":"AUTO").append('\n');
        out.append("Conexión Virtualizer: ").append(sessionVirtualizerCompatibility?"Motorola/NXP · sesión del reproductor; global si aún no fue anunciada":"junto al motor EQ").append('\n');
        out.append("Sesiones anunciadas: ").append(sessions).append('\n');
        out.append("EQ visible: ").append(s.eqOn?"ON":"OFF").append(" · Simple: ").append(Arrays.toString(s.simple)).append('\n');
        if(s.surround){
            SoundState recipe=SoundState.surround();
            out.append("SURROUND · capa EQ interna: ").append(Arrays.toString(recipe.bands)).append(" dB\n");
            out.append("Frecuencias internas: ").append(Arrays.toString(recipe.frequencies)).append(" Hz; Q interna: ").append(recipe.q).append('\n');
        }
        appendDetectedOutputs(out);
        try { out.append("Música activa: ").append(getSystemService(AudioManager.class).isMusicActive()).append('\n'); } catch (RuntimeException ignored) {}
        for (Map.Entry<Integer, HeldEffect> entry : effects.entrySet()) {
            try {
                AudioEffect eq = entry.getValue().effect;
                out.append("Sesión ").append(entry.getKey()).append(": ").append(eq.getDescriptor().name);
                out.append(" · control=").append(eq.hasControl()).append(" · enabled=").append(eq.getEnabled()).append('\n');
                out.append("Implementación: ").append(eq.getDescriptor().implementor).append(" · ").append(eq.getDescriptor().uuid).append('\n');
                HeldEffect h=entry.getValue();
                if(h.virtual!=null) {
                    out.append(virtualDescription(entry.getKey(),h)).append('\n');
                    out.append("Virtualizer capacidades estéreo: binaural=").append(h.binauralSupport).append("; transaural=").append(h.transauralSupport).append('\n');
                    try { AudioEffect.Descriptor vd=h.virtual.getDescriptor();out.append("Virtualizer implementación: ").append(vd.name).append(" · ").append(vd.implementor).append(" · ").append(vd.uuid).append('\n'); }
                    catch(RuntimeException e) { out.append("Virtualizer descriptor no disponible\n"); }
                }
                if (eq instanceof DynamicsProcessing) {
                    DynamicsProcessing dp = (DynamicsProcessing) eq;
                    DynamicsProcessing.Config config = dp.getConfig();
                    out.append("Canales: ").append(dp.getChannelCount()).append("; bandas: ").append(config.getPreEqBandCount()).append('\n');
                    for (int channel = 0; channel < dp.getChannelCount(); channel++) {
                        out.append("Ch").append(channel).append(" entrada: ").append(dp.getInputGainByChannelIndex(channel)).append(" dB; ");
                        out.append("MBC en uso: ").append(dp.getMbcByChannelIndex(channel).isInUse());
                        out.append(" / activo: ").append(dp.getMbcByChannelIndex(channel).isEnabled());
                        out.append("; salida efectiva: ").append(dp.getPostEqBandByChannelIndex(channel,0).getGain()).append(" dB");
                        out.append("; limiter en uso: ").append(dp.getLimiterByChannelIndex(channel).isInUse()).append('\n');
                    }
                    for (int band = 0; band < EqResponse.COUNT; band++) {
                        DynamicsProcessing.EqBand b = dp.getPreEqBandByChannelIndex(0, band);
                        out.append(Math.round(EqResponse.CENTERS[band])).append(" Hz: ").append(String.format(Locale.ROOT,"%.2f",b.getGain())).append(" dB; ");
                    }
                } else {
                    Equalizer classic = (Equalizer) eq;
                    for (short band = 0; band < classic.getNumberOfBands(); band++)
                        out.append(classic.getCenterFreq(band)/1000).append(" Hz: ").append(classic.getBandLevel(band)/100.0).append(" dB; ");
                }
                out.append('\n');
            } catch (RuntimeException e) { out.append("Motor no disponible\n"); }
        }
        for (String line : events) out.append(line).append('\n');
        return out.toString();
    }

    private void appendDetectedOutputs(StringBuilder out) {
        out.append("Salidas detectadas (disponibles; no confirma la ruta activa): ");
        try {
            if(audioManager==null) { out.append("sin AudioManager\n");return; }
            for(AudioDeviceInfo device:audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                String type;
                switch(device.getType()) {
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:type="Bluetooth A2DP";break;
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:type="Bluetooth SCO";break;
                    case AudioDeviceInfo.TYPE_BLE_HEADSET:type="Bluetooth LE auriculares";break;
                    case AudioDeviceInfo.TYPE_BLE_SPEAKER:type="Bluetooth LE parlante";break;
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:type="Auriculares cable";break;
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:type="Headset cable";break;
                    case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:type="Parlante integrado";break;
                    case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:type="Auricular llamadas";break;
                    case AudioDeviceInfo.TYPE_USB_HEADSET:type="Auriculares USB";break;
                    default:type="tipo "+device.getType();
                }
                out.append(type).append(" [").append(device.getId()).append("]; ");
            }
        } catch(RuntimeException e) { out.append("consulta no disponible: ").append(e.getClass().getSimpleName()); }
        out.append('\n');
    }

    private void fail(String where, RuntimeException e) {
        lastError = where + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " · " + e.getMessage());
        event(lastError);
    }
    private void event(String line) {
        events.addLast(line);
        while (events.size() > 12) events.removeFirst();
    }
    private void changed() { if (observer != null && !destroyed) observer.run(); }

    @Override public void onDestroy() {
        saveState();
        transition.cancel();
        destroyed = true;
        observer = null;
        enabled = false;
        releaseAll();
        if(audioManager!=null) {
            if(deviceCallbackRegistered) try { audioManager.unregisterAudioDeviceCallback(deviceCallback); } catch(RuntimeException ignored) {}
            if(playbackCallbackRegistered) try { audioManager.unregisterAudioPlaybackCallback(playbackCallback); } catch(RuntimeException ignored) {}
        }
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) unregisterReceiver(sessionReceiver);
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}

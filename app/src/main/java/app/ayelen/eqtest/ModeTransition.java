package app.ayelen.eqtest;

/** Short finite control envelope. Never touches transport or system media volume. */
final class ModeTransition {
    interface Clock { void post(Runnable r,int delay);void remove(Runnable r); }
    interface Output { void attenuation(float db); }
    private final Clock clock;private final Output output;
    private Runnable tick,commit,finish;private int frame,generation;private boolean busy;
    ModeTransition(Clock c,Output o){clock=c;output=o;}
    boolean busy(){return busy;}
    void start(Runnable action){begin(action,false,null);}
    void start(Runnable action,Runnable done){begin(action,false,done);}
    void startMuted(Runnable action){begin(action,true,null);}
    private void begin(Runnable action,boolean muted,Runnable done){
        commit=action;finish=done;
        if(busy){if(frame>=3){frame=3;}return;}
        busy=true;frame=muted?3:0;final int token=++generation;
        tick=new Runnable(){public void run(){
            if(token!=generation||!busy)return;
            // 36 ms down, 36 ms hold, 72 ms recovery. Amplitude-shaped fades.
            float amplitude=frame<3?(3-frame)/3f:frame<=6?0:(frame-6)/6f;
            output.attenuation(amplitude<=0?-80:(float)(20*Math.log10(Math.min(1,amplitude))));
            if(frame==3&&commit!=null){Runnable job=commit;commit=null;job.run();if(token!=generation)return;}
            if(frame++>=12){busy=false;tick=null;Runnable complete=finish;finish=null;output.attenuation(0);if(complete!=null)complete.run();return;}
            clock.post(this,12);
        }};
        tick.run();
    }
    void cancel(){generation++;if(tick!=null)clock.remove(tick);tick=null;commit=null;finish=null;busy=false;output.attenuation(0);}
}

package app.ayelen.eqtest;

/** Bounded native-effect activation, separate from EQ, UI and continuous slider edits.
 * A framework enabled=true/accepted=true does not confirm the audio engine's mode. */
final class VirtualizerStart {
    static final int AUTO=1, OFF=0;
    private static final int[] DELAYS={60,140,300,600,1000};
    private static final int VERIFY_MS=160, REARM_OFF_MS=80;
    interface Engine {
        boolean current();
        boolean control();
        boolean enabled();
        void enable();
        void disable();
        boolean playing();
        boolean force(int mode);
        int mode();
        void refreshStrength();
    }
    interface Scheduler { void post(Runnable task,int delay);void remove(Runnable task); }
    interface Listener { void state(String phase,Boolean accepted,int reported,int attempt); }
    private final Engine engine;
    private final Scheduler scheduler;
    private final Listener listener;
    private Runnable pending;
    private int generation,requested,attempt;
    private boolean running,verifying,reenable,rearmed;
    private Boolean lastAccepted;
    private String phase="sin iniciar";

    VirtualizerStart(Engine engine,Scheduler scheduler,Listener listener) {
        this.engine=engine;this.scheduler=scheduler;this.listener=listener;
    }
    boolean running() { return running; }
    String phase() { return phase; }
    int attempts() { return attempt; }
    boolean rearmed() { return rearmed; }

    // Native enable callbacks can include our own delayed OFF/ON notifications.
    // Only a previously confirmed effect may open a new recovery window here.
    void check() {
        if(running || !"confirmado por Android".equals(phase) || !engine.current())return;
        try { if(!engine.control() || active(engine.mode()))return; }
        catch(RuntimeException ignored) {}
        start(requested);
    }

    void start(int mode) {
        cancel();requested=mode;attempt=0;running=true;
        verifying=false;reenable=false;rearmed=false;lastAccepted=null;
        try {
            if(!engine.current() || !engine.control()) { finish("sin control",null,-1);return; }
            if(!engine.enabled()) engine.enable();
            // Reproduce the useful AUTO -> selected-mode transition once per
            // activation. Leave the audio engine time to process ENABLE first.
            if(mode!=AUTO) engine.force(AUTO);
            phase="preparando";listener.state(phase,null,-1,0);
            schedule(DELAYS[0]);
        } catch(RuntimeException e) {
            if(engine.current()) { phase="esperando motor";listener.state(phase,null,-1,0);schedule(DELAYS[0]); }
            else cancel();
        }
    }

    void cancel() {
        generation++;running=false;
        if(pending!=null) scheduler.remove(pending);
        pending=null;phase="cancelado";
    }

    private void schedule(int delay) {
        final int token=generation;
        pending=()->{if(!running || token!=generation)return;pending=null;step();};
        scheduler.post(pending,delay);
    }

    private void step() {
        if(!engine.current()) { cancel();return; }
        // A failed getter must consume the budget as well as a failed setter.
        if(!verifying && !reenable)attempt++;
        Boolean accepted=null;int actual=-1;
        try {
            if(!engine.control()) { finish("sin control",null,-1);return; }
            if(reenable) {
                reenable=false;engine.enable();engine.refreshStrength();
                phase="reactivando";listener.state(phase,null,-1,attempt);
                schedule(DELAYS[0]);return;
            }
            if(verifying) {
                verifying=false;accepted=lastAccepted;actual=engine.mode();
                if(active(actual)) {
                    // Native confirmation is not acoustic proof. Do not cycle
                    // a healthy reported state just because of the manufacturer.
                    finish("confirmado por Android",accepted,actual);return;
                }
            } else {
                if(!engine.enabled()) engine.enable();
                accepted=engine.force(requested);lastAccepted=accepted;
                actual=engine.mode();
                if(Boolean.TRUE.equals(accepted) && active(actual)) {
                    // Preserve the working Samsung order: mode, then strength.
                    engine.refreshStrength();
                    // Read again later WITHOUT a setter just before the read:
                    // some engines report ON before the route has settled.
                    verifying=true;phase="comprobando estabilidad";
                    listener.state(phase,accepted,actual,attempt);
                    schedule(VERIFY_MS);return;
                }
            }
            if(attempt>=3 && attempt<DELAYS.length && !rearmed && engine.playing()) { rearm();return; }
        } catch(RuntimeException e) {
            verifying=false;reenable=false;
            phase="respuesta pendiente ("+e.getClass().getSimpleName()+")";
        }
        if(attempt>=DELAYS.length) { finish("sin confirmar",accepted,actual);return; }
        phase="verificando";listener.state(phase,accepted,actual,attempt);
        schedule(DELAYS[attempt]);
    }

    private boolean active(int mode) {
        return engine.enabled() && mode!=OFF && (requested==AUTO || mode==requested);
    }
    private void rearm() {
        rearmed=true; // One OFF/ON maximum per real activation/route/session event.
        engine.disable();reenable=true;verifying=false;
        phase="recuperación OFF/ON";listener.state(phase,null,OFF,attempt);
        schedule(REARM_OFF_MS);
    }

    private void finish(String value,Boolean accepted,int mode) {
        running=false;pending=null;phase=value;
        listener.state(value,accepted,mode,attempt);
    }
}

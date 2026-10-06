package app.ayelen.eqtest;

import android.content.Context;
import android.graphics.*;
import android.media.audiofx.Visualizer;
import android.os.*;
import android.view.View;

/** Optional mono analysis from Android's Visualizer, not microphone PCM or L/R meters.
 * A single 15 fps polling callback, only while the visible Activity requests it. */
final class SpectrumView extends View {
    private final UiColors ui;
    interface Listener { void state(String message); }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final float density, bins[]=new float[32], peaks[]=new float[32], targets[]=new float[32];
    private final Visualizer.MeasurementPeakRms measurement=new Visualizer.MeasurementPeakRms();
    private Visualizer visual;
    private Listener listener;
    private byte[] fft;
    private int session=-1,failedSession=-2,rate=44100,frames;
    private boolean measurementAvailable;
    private String measured="", lastMessage="";
    private final Runnable tick=new Runnable(){public void run(){
        if(visual==null)return;
        try {
            int result=visual.getFft(fft);
            if(result!=Visualizer.SUCCESS){fail("Android no entrega espectro en esta salida");return;}
            SpectrumMath.decode(fft,rate,targets);
            boolean signal=false;
            for(int i=0;i<bins.length;i++) { bins[i]+= (targets[i]-bins[i])*(targets[i]>bins[i]?.65f:.26f);peaks[i]=Math.max(bins[i],peaks[i]-.024f);if(targets[i]>.01f)signal=true; }
            if(++frames%15==1) {
                if(measurementAvailable&&visual.getMeasurementPeakRms(measurement)==Visualizer.SUCCESS)
                    measured=String.format(java.util.Locale.ROOT,"Peak %.1f   ·   RMS %.1f dBFS",measurement.mPeak/100f,measurement.mRms/100f);
                message(signal?"Espectro real · mezcla mono":"Esperando señal de Android");
            }
            invalidate();handler.postDelayed(this,67);
        } catch(RuntimeException e){fail("Espectro no disponible · el EQ sigue activo");}
    }};
    SpectrumView(Context c){super(c);ui=new UiColors(c);density=getResources().getDisplayMetrics().density;setContentDescription("Espectro de la señal que entrega Android. No son medidores izquierdo y derecho separados.");}
    void listen(Listener l){listener=l;}
    void retry(){failedSession=-2;}
    boolean running(){return visual!=null;}
    void start(int id) {
        if(visual!=null&&session==id || id==failedSession)return;
        stop();if(id<0)return;
        session=id;
        try {
            visual=new Visualizer(id);
            visual.setEnabled(false);
            int[] size=Visualizer.getCaptureSizeRange();int count=1024;
            while(count>size[1])count/=2;while(count<size[0])count*=2;
            if(visual.setCaptureSize(count)!=Visualizer.SUCCESS)throw new IllegalStateException("capture");
            fft=new byte[count];
            visual.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
            measurementAvailable=visual.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS)==Visualizer.SUCCESS;
            int sampling=visual.getSamplingRate()/1000;if(sampling>0)rate=sampling;
            if(visual.setEnabled(true)!=Visualizer.SUCCESS)throw new IllegalStateException("enabled");
            frames=0;message("Leyendo señal de Android…");handler.post(tick);
        }catch(RuntimeException e){fail("Android no permite el espectro en esta sesión");}
    }
    private void fail(String message){failedSession=session;stop();message(message);}
    void stop(){
        handler.removeCallbacks(tick);
        if(visual!=null){try{visual.setEnabled(false);}catch(RuntimeException ignored){}try{visual.release();}catch(RuntimeException ignored){}visual=null;}
        session=-1;measured="";java.util.Arrays.fill(bins,0);java.util.Arrays.fill(peaks,0);invalidate();
    }
    private void message(String text){if(!text.equals(lastMessage)){lastMessage=text;if(listener!=null)listener.state(text);}}
    private float d(float v){return density*v;}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float left=d(2),right=getWidth()-d(2),bottom=getHeight()-d(28),top=d(8),step=(right-left)/bins.length;
        p.setStrokeWidth(d(1));p.setColor(ui.line);c.drawLine(left,bottom,right,bottom,p);
        for(int i=0;i<bins.length;i++) {
            float x=left+i*step,w=Math.max(d(1),step-d(2.3f)),h=Math.max(d(2),bins[i]*(bottom-top));
            p.setColor(UiColors.ACCENT);
            c.drawRoundRect(x,bottom-h,x+w,bottom,d(2),d(2),p);
            if(peaks[i]>.03){p.setColor(ui.value);c.drawRoundRect(x,bottom-peaks[i]*(bottom-top)-d(3),x+w,bottom-peaks[i]*(bottom-top)-d(1),d(1),d(1),p);}
        }
        p.setColor(ui.muted);p.setTextSize(d(9));p.setTypeface(Typeface.create("sans-serif",0));p.setTextAlign(Paint.Align.LEFT);
        c.drawText("60 Hz",0,getHeight()-d(11),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText("16 kHz",getWidth(),getHeight()-d(11),p);
        p.setTextAlign(Paint.Align.CENTER);c.drawText(measured,getWidth()/2f,getHeight()-d(11),p);
    }
}

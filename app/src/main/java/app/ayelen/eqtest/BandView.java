package app.ayelen.eqtest;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

/** Ten visible bands. No animator, audio sampling, bitmap allocations or render loop. */
final class BandView extends View {
    private final UiColors ui;
    interface Listener { void changed(int band,float value); void edit(int band); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final float[] bands=new float[10];
    private float[] frequencies=SoundState.FREQUENCIES.clone();
    private boolean[] enabledBands={true,true,true,true,true,true,true,true,true,true};
    private final float density;
    private Listener listener;
    private int selected=-1;
    private float offset;
    private final int ink=UiColors.ACCENT;
    BandView(Context context) {
        super(context); ui=new UiColors(context); density=getResources().getDisplayMetrics().density;
        setContentDescription("Ecualizador de diez bandas. Arrastrá cada punto o usá Editar banda para controles accesibles.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void listen(Listener value) { listener=value; }
    void values(SoundState s) {
        float[] value=s.bands;
        boolean different=!java.util.Arrays.equals(frequencies,s.frequencies)||!java.util.Arrays.equals(enabledBands,s.bandOn);
        frequencies=s.frequencies.clone();enabledBands=s.bandOn.clone();
        for(int i=0;i<10;i++) if(bands[i]!=value[i]) { bands[i]=value[i];different=true; }
        if(different) invalidate();
    }
    private float d(float n) { return n*density; }
    private float top() { return d(36); }
    private float bottom() { return getHeight()-d(33); }
    private float x(int i) { return getWidth()*(i+.5f)/10; }
    private float y(float gain) { return top()+(12-gain)/24*(bottom()-top()); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStrokeWidth(d(1)); paint.setColor(ui.line);
        canvas.drawLine(x(0),y(0),x(9),y(0),paint);
        for(int i=0;i<10;i++) canvas.drawLine(x(i),top(),x(i),bottom(),paint);
        path.reset();
        for(int i=0;i<10;i++) { if(i==0)path.moveTo(x(i),y(bands[i]));else path.lineTo(x(i),y(bands[i])); }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2));paint.setColor(ink);canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        for(int i=0;i<10;i++) {
            paint.setColor(enabledBands[i]?ink:ui.muted);canvas.drawCircle(x(i),y(bands[i]),d(selected==i?7:5),paint);
            paint.setTextSize(d(10));paint.setColor(ui.value);
            canvas.drawText(String.format(Locale.ROOT,bands[i]>0?"+%.1f":"%.1f",bands[i]),x(i),d(19),paint);
            paint.setColor(ui.muted);paint.setTextSize(d(10));paint.setColor(ui.value);
            canvas.drawText(EqCoordinates.label(frequencies[i]),x(i),getHeight()-d(9),paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!isEnabled()) return false;
        int i=Math.max(0,Math.min(9,(int)(e.getX()/Math.max(1,getWidth())*10)));
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selected=i;
                if(e.getY()>bottom()+d(6) || e.getY()<d(28)) {
                    selected=-1; if(listener!=null)listener.edit(i);performClick();return true;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                offset=Math.abs(e.getY()-y(bands[i]))<d(24)?e.getY()-y(bands[i]):0;
                adjust(e.getY());return true;
            case MotionEvent.ACTION_MOVE:
                if(selected>=0)adjust(e.getY());return true;
            case MotionEvent.ACTION_UP:
                if(selected>=0)adjust(e.getY());
                selected=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                selected=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
        }
        return true;
    }
    private void adjust(float position) {
        if(selected<0) return;
        float gain=Math.max(-12,Math.min(12,12-(position-offset-top())/Math.max(1,bottom()-top())*24));
        gain=Math.round(gain*2)/2f;
        if(bands[selected]!=gain) { bands[selected]=gain;if(listener!=null)listener.changed(selected,gain);invalidate(); }
    }
    @Override public boolean performClick() { super.performClick();return true; }
}

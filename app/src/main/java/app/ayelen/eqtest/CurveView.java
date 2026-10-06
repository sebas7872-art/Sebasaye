package app.ayelen.eqtest;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Arrays;

/** Log-frequency editor. Drag changes the existing ten bands, not a second EQ. */
final class CurveView extends View {
    private final UiColors ui;
    interface Listener { void select(int index); void change(int index,float hz,float db); }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final float density, values[]=new float[145];
    private SoundState sound=new SoundState();
    private Listener listener;
    private int selected=3;
    private boolean dragging;
    private float dx,dy;
    CurveView(Context c) { super(c);ui=new UiColors(c);density=getResources().getDisplayMetrics().density;compute();setContentDescription("Curva de ecualización. Mové puntos en frecuencia y ganancia. También podés editar con los controles de abajo."); }
    void listen(Listener l) { listener=l; }
    void select(int i) { selected=i;invalidate(); }
    void values(SoundState s) {
        if(Arrays.equals(s.bands,sound.bands)&&Arrays.equals(s.frequencies,sound.frequencies)&&Arrays.equals(s.bandQs,sound.bandQs)
            &&Arrays.equals(s.types,sound.types)&&Arrays.equals(s.bandOn,sound.bandOn)&&s.q==sound.q&&s.eqOn==sound.eqOn&&Arrays.equals(s.simple,sound.simple)) return;
        sound=s.copy();compute();invalidate();
    }
    private void compute() { for(int i=0;i<values.length;i++) values[i]=EqResponse.userAt(sound,EqCoordinates.frequency(i/(float)(values.length-1))); }
    private float d(float v) { return v*density; }
    private float left(){return d(24);} private float right(){return Math.max(left()+1,getWidth()-d(14));}
    private float top(){return d(17);} private float bottom(){return Math.max(top()+1,getHeight()-d(31));}
    private float x(float f) { return left()+(right()-left())*EqCoordinates.fraction(f); }
    private float y(float g) { return top()+(12-Math.max(-12,Math.min(12,g)))/24*(bottom()-top()); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);p.setStyle(Paint.Style.FILL);p.setColor(ui.canvas);c.drawRoundRect(0,0,getWidth(),getHeight(),d(18),d(18),p);
        p.setStrokeWidth(d(1));p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(d(9));p.setTextAlign(Paint.Align.RIGHT);
        for(int db=-12;db<=12;db+=6) { p.setColor(db==0?ui.zero:ui.line);c.drawLine(left(),y(db),right(),y(db),p);p.setColor(ui.muted);c.drawText(db==0?"0":Integer.toString(db),left()-d(5),y(db)+d(3),p); }
        p.setTextAlign(Paint.Align.CENTER);
        for(int f:new int[]{20,100,1000,10000,20000}) { p.setColor(ui.line);c.drawLine(x(f),top(),x(f),bottom(),p);p.setColor(ui.muted);c.drawText(EqCoordinates.label(f),x(f),getHeight()-d(10),p); }
        path.reset();for(int i=0;i<values.length;i++){float xx=left()+i*(right()-left())/(values.length-1);if(i==0)path.moveTo(xx,y(values[i]));else path.lineTo(xx,y(values[i]));}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(2));p.setColor(UiColors.ACCENT);c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",0));
        for(int i=0;i<10;i++)if(i!=selected)drawPoint(c,i,false);
        drawPoint(c,selected,true);
    }
    private void drawPoint(Canvas c,int i,boolean active) {
        float xx=x(sound.frequencies[i]),yy=y(sound.bands[i]);
        if(active){p.setColor(UiColors.RIPPLE);c.drawCircle(xx,yy,d(17),p);}
        p.setColor(!sound.bandOn[i]?ui.muted:active?UiColors.ACCENT:0xffad5d70);c.drawCircle(xx,yy,d(active?10:6),p);
        if(active){p.setColor(0xffffffff);p.setTextSize(d(11));c.drawText(Integer.toString(i+1),xx,yy+d(4),p);}
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!isEnabled())return false;
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                int closest=selected;float distance=Float.MAX_VALUE;
                for(int i=0;i<10;i++){float xx=e.getX()-x(sound.frequencies[i]),yy=e.getY()-y(sound.bands[i]);float dd=xx*xx+yy*yy;if(dd<distance){distance=dd;closest=i;}}
                selected=closest;dragging=distance<d(32)*d(32);dx=e.getX()-x(sound.frequencies[selected]);dy=e.getY()-y(sound.bands[selected]);
                if(listener!=null)listener.select(selected);if(dragging)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE: if(dragging)adjust(e);return true;
            case MotionEvent.ACTION_UP: if(dragging)adjust(e);dragging=false;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
            case MotionEvent.ACTION_CANCEL:dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return true;
    }
    private void adjust(MotionEvent e) {
        float frequency=Math.round(EqCoordinates.frequency((e.getX()-dx-left())/(right()-left())));
        float db=Math.round(Math.max(-12,Math.min(12,12-(e.getY()-dy-top())/(bottom()-top())*24))*4)/4f;
        if(listener!=null&&(frequency!=sound.frequencies[selected]||db!=sound.bands[selected]))listener.change(selected,frequency,db);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

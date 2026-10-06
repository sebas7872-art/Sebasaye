package app.ayelen.eqtest;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

/** Full-height touch lanes; stable IDs survive visual sorting. No animation loop. */
final class SimpleEqView extends View {
    private final UiColors ui;
    interface Listener{void change(int id,float gain);void edit(int id);}
    private Listener listener;
    private SoundState state=new SoundState();
    private Integer[] ids=state.sortedSimpleIds();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve=new Path();
    private final float density;
    private int selected=-1,tapped=-1;
    private float offset,downX,downY;
    SimpleEqView(Context c){super(c);ui=new UiColors(c);density=getResources().getDisplayMetrics().density;setContentDescription("Ecualizador Simple. Arrastrá una banda; tocá su frecuencia para editarla.");}
    void listen(Listener l){listener=l;}
    void values(SoundState value){
        if(state.sameSimpleEq(value))return;
        int previous=ids.length;state=value.copy();ids=state.sortedSimpleIds();
        if(!state.hasSimple(selected))selected=-1;
        if(previous!=ids.length && getLayoutParams() instanceof ViewGroup.MarginLayoutParams){
            ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)getLayoutParams();
            lp.leftMargin=lp.rightMargin=-Math.round(d(8*(ids.length-3)/7f));setLayoutParams(lp);
        }
        setContentDescription("Ecualizador Simple, "+ids.length+" bandas. Tocá una frecuencia para editarla.");invalidate();
    }
    private float d(float n){return density*n;}
    private float lane(){return getWidth()/(float)ids.length;}
    private float x(int i){return lane()*(i+.5f);}
    private float compact(){return Math.max(0,Math.min(1,(lane()/density-26)/68));}
    private float top(){return d(32);}
    private float bottom(){return getHeight()-d(55);}
    private float y(float gain){return top()+(12-gain)/24*(bottom()-top());}
    static String frequency(float value,boolean units){
        if(value<1000)return Math.round(value)+(units?" Hz":"");
        String number=String.format(Locale.ROOT,"%.2f",value/1000).replaceAll("0+$","").replaceAll("\\.$","");
        return number+(units?" kHz":"k");
    }
    private void icon(Canvas c,float x,float y,float scale){
        p.setColor(ui.ink);p.setStrokeWidth(d(1.2f));
        for(int i=-1;i<=1;i++){float xx=x+d(i*4*scale);c.drawLine(xx,y-d(5*scale),xx,y+d(5*scale),p);c.drawCircle(xx,y+d((i==0?-2:2)*scale),d(1.7f*scale),p);}
    }
    @Override protected void onDraw(Canvas c){
        float scale=compact(),radius=d(5+3*scale);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1.2f+.8f*scale));p.setColor(ui.line);
        for(int i=0;i<ids.length;i++)c.drawLine(x(i),top(),x(i),bottom(),p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<ids.length;i++)for(int db=-12;db<=12;db+=6){p.setColor(ui.line);c.drawCircle(x(i),y(db),d(1.5f+1.5f*scale),p);}
        curve.reset();curve.moveTo(x(0),y(state.simpleGain(ids[0])));
        for(int i=1;i<ids.length;i++){float mid=(x(i-1)+x(i))/2;curve.cubicTo(mid,y(state.simpleGain(ids[i-1])),mid,y(state.simpleGain(ids[i])),x(i),y(state.simpleGain(ids[i])));}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1.5f+scale));p.setColor(UiColors.ACCENT);c.drawPath(curve,p);p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);
        String[] names={"Graves","Medios","Agudos"};
        for(int i=0;i<ids.length;i++){
            int id=ids[i];float gain=state.simpleGain(id);
            p.setColor(ui.card);c.drawCircle(x(i),y(gain),radius+d(3),p);p.setColor(UiColors.ACCENT);c.drawCircle(x(i),y(gain),radius+(selected==id?d(1):0),p);
            p.setColor(ui.value);p.setTextSize(d(9+3*scale));
            String value=String.format(Locale.ROOT,"%+.1f",gain),full=value+" dB";
            c.drawText(p.measureText(full)<lane()-d(5)?full:value,x(i),d(16),p);
            p.setColor(ui.ink);p.setTextSize(d(12+2*scale));
            if(id<3 && p.measureText(names[id])<=lane()-d(9))c.drawText(names[id],x(i),getHeight()-d(24),p);
            else icon(c,x(i),getHeight()-d(28),.75f+.25f*scale);
            p.setColor(ui.muted);p.setTextSize(d(9+scale));
            String hz=frequency(state.simpleFrequency(id),true);
            if(p.measureText(hz)>lane()-d(3))hz=frequency(state.simpleFrequency(id),false);
            float text=p.measureText(hz);if(text>lane()-d(2))p.setTextSize(p.getTextSize()*(lane()-d(2))/text);
            c.drawText(hz,x(i),getHeight()-d(7),p);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled())return false;
        int position=Math.max(0,Math.min(ids.length-1,(int)(e.getX()/Math.max(1,lane()))));
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=e.getX();downY=e.getY();tapped=-1;
                if(e.getY()>bottom()+d(4)){tapped=ids[position];return true;}
                selected=ids[position];getParent().requestDisallowInterceptTouchEvent(true);
                offset=Math.abs(e.getY()-y(state.simpleGain(selected)))<d(24)?e.getY()-y(state.simpleGain(selected)):0;adjust(e.getY());return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(e.getX()-downX)>d(8)||Math.abs(e.getY()-downY)>d(8))tapped=-1;
                adjust(e.getY());return true;
            case MotionEvent.ACTION_UP:
                if(tapped>=0){int id=tapped;tapped=-1;if(listener!=null&&state.hasSimple(id))listener.edit(id);}else adjust(e.getY());
                selected=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();performClick();return true;
            case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_POINTER_DOWN:
                selected=tapped=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
        }return true;
    }
    private void adjust(float at){
        if(selected<0||!state.hasSimple(selected))return;
        float gain=Math.round(Math.max(-12,Math.min(12,12-(at-offset-top())/Math.max(1,bottom()-top())*24))*2)/2f;
        if(gain!=state.simpleGain(selected)){state.simpleGain(selected,gain);if(listener!=null)listener.change(selected,gain);invalidate();}
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

package app.ayelen.eqtest;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.ImageView;

/** Draws the visible artwork in pixel coordinates, independent of drawable density. */
final class DeviceImageView extends ImageView {
    private Bitmap cached;
    private final Rect source=new Rect();
    private final RectF target=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private boolean logo;
    DeviceImageView(Context c){super(c);}
    void logo(){logo=true;invalidate();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        super.onMeasure(widthSpec,heightSpec);
        if(logo)setMeasuredDimension(Math.min(getMeasuredWidth(),Math.round(160*getResources().getDisplayMetrics().density)),getMeasuredHeight());
    }
    @Override protected void onDraw(Canvas canvas){
        Drawable drawable=getDrawable();
        if(!(drawable instanceof BitmapDrawable)){
            if(drawable==null)return;
            int w=drawable.getIntrinsicWidth(),h=drawable.getIntrinsicHeight();if(w<=0||h<=0){super.onDraw(canvas);return;}
            float scale=Math.min(getWidth()*.72f/w,getHeight()*.76f/h);
            int saved=canvas.save();canvas.translate((getWidth()-w*scale)/2,(getHeight()-h*scale)/2);canvas.scale(scale,scale);
            drawable.setBounds(0,0,w,h);drawable.draw(canvas);canvas.restoreToCount(saved);return;
        }
        Bitmap bitmap=((BitmapDrawable)drawable).getBitmap();
        if(bitmap!=cached){
            cached=bitmap;
            int w=bitmap.getWidth(),h=bitmap.getHeight(),left=w,top=h,right=-1,bottom=-1;
            int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((pixels[y*w+x]>>>24)>12){
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
            if(right>=left)source.set(left,top,right+1,bottom+1);else source.setEmpty();
        }
        if(source.isEmpty())return;
        float width=getWidth()-getPaddingLeft()-getPaddingRight();
        float height=getHeight()-getPaddingTop()-getPaddingBottom();
        float scale=Math.min(width*(logo?.96f:.72f)/source.width(),height*(logo?.94f:.76f)/source.height());
        float w=source.width()*scale,h=source.height()*scale;
        float left=getPaddingLeft()+(logo?0:(width-w)/2),top=getPaddingTop()+(height-h)/2;
        target.set(left,top,left+w,top+h);
        canvas.drawBitmap(bitmap,source,target,paint);
    }
}

package app.ayelen.eqtest;
import static org.junit.Assert.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.android.controller.*;import org.robolectric.shadows.ShadowAlertDialog;
import android.app.*;import android.view.*;import android.widget.*;import android.graphics.*;import android.os.*;
import java.lang.reflect.*;import java.io.*;import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34,qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SimpleUiTest {
 MainActivity a;EqService s;ActivityController<MainActivity> controller;
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static void call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
 @Before public void setup()throws Exception{
  RuntimeEnvironment.getApplication().getSharedPreferences("eq-test",0).edit().clear().commit();
  RuntimeEnvironment.getApplication().getSharedPreferences("appearance",0).edit().clear().commit();
  s=Robolectric.buildService(EqService.class).create().get();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindService(new android.content.ComponentName(RuntimeEnvironment.getApplication(),EqService.class),s.onBind(new android.content.Intent()));
  controller=Robolectric.buildActivity(MainActivity.class);a=controller.get();controller.setup().visible();bind();
 }
 void bind()throws Exception{set(a,"service",s);s.observe(()->{try{call(a,"sync");}catch(Exception e){throw new RuntimeException(e);}});call(a,"sync");}
 @After public void cleanup(){ThemeBinding.release();if(s!=null)s.observe(null);if(controller!=null)controller.pause().stop().destroy();if(s!=null)s.onDestroy();}
 View root(){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 void idle(int ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
 void layout(int width,int height){idle(170);View r=root();r.forceLayout();r.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));r.layout(0,0,width,height);}
 void shot(String n)throws Exception{View r=root();Bitmap b=Bitmap.createBitmap(r.getWidth(),r.getHeight(),Bitmap.Config.ARGB_8888);r.draw(new Canvas(b));new File("build/ui-screenshots").mkdirs();try(FileOutputStream out=new FileOutputStream("build/ui-screenshots/"+n)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 <T extends View>T find(View v,Class<T> type){if(type.isInstance(v))return type.cast(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){T result=find(((ViewGroup)v).getChildAt(i),type);if(result!=null)return result;}return null;}
 AlertDialog dialog(){idle(1);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());return d;}
 EditText input(AlertDialog d){return find(d.getWindow().getDecorView(),EditText.class);}
 SeekBar frequency(AlertDialog d){return find(d.getWindow().getDecorView(),SeekBar.class);}
 void add(float hz)throws Exception{((Button)field(a,"addSimpleButton")).performClick();AlertDialog d=dialog();input(d).setText(Float.toString(hz));d.getButton(-1).performClick();assertFalse(d.isShowing());}
 void edit(int id)throws Exception{Method m=MainActivity.class.getDeclaredMethod("editSimple",int.class);m.setAccessible(true);m.invoke(a,id);}
 void setting(int action)throws Exception{((Button)field(a,"simpleSettingsButton")).performClick();AlertDialog d=dialog();d.getListView().performItemClick(null,action,action);idle(1);}
 DeviceImageView logo(){LinearLayout heading=(LinearLayout)((ViewGroup)root()).getChildAt(0);return (DeviceImageView)((LinearLayout)heading.getChildAt(0)).getChildAt(0);}
 void images()throws Exception{
  String base=System.getProperty("sanavera.resources","src/main/res");
  logo().setImageBitmap(BitmapFactory.decodeFile(base+"/drawable-nodpi/sanavera_logo.png"));
  ((ImageView)field(a,"deviceImage")).setImageBitmap(BitmapFactory.decodeFile(base+"/drawable-nodpi/device_charge5.png"));
 }
 @Test public void creationCancelFrequencyEditingAndOriginalRestore()throws Exception{
  String initial=s.sound().pack();((Button)field(a,"addSimpleButton")).performClick();AlertDialog d=dialog();input(d).setText("350");assertEquals(initial,s.sound().pack());
  assertEquals(Math.round(EqCoordinates.fraction(350)*1000),frequency(d).getProgress());
  Field listener=SeekBar.class.getDeclaredField("mOnSeekBarChangeListener");listener.setAccessible(true);((SeekBar.OnSeekBarChangeListener)listener.get(frequency(d))).onProgressChanged(frequency(d),650,true);assertEquals(Math.round(EqCoordinates.frequency(.65f)),Float.parseFloat(input(d).getText().toString()),0);assertEquals(initial,s.sound().pack());d.cancel();assertEquals(initial,s.sound().pack());
  ((Button)field(a,"addSimpleButton")).performClick();d=dialog();input(d).setText("20001");assertFalse(d.getButton(-1).isEnabled());input(d).setText("350");d.getButton(-1).performClick();assertEquals(4,s.sound().simpleCount());
  int id=s.sound().simpleAdded.get(0).id;SoundState changed=s.sound();changed.simpleGain(id,4.5f);changed.simple[0]=2;s.edit(changed,true);
  edit(id);d=dialog();input(d).setText("14000");assertEquals(14000,s.sound().simpleFrequency(id),0);assertEquals(4.5,s.sound().simpleGain(id),0);assertEquals(id,(int)s.sound().sortedSimpleIds()[3]);d.getButton(-3).performClick();assertEquals(3,s.sound().simpleCount());
  edit(0);d=dialog();input(d).setText("62");assertEquals(62,s.sound().simpleFrequency(0),0);d.getButton(-3).performClick();assertEquals(37,s.sound().simpleFrequency(0),0);assertEquals(2,s.sound().simple[0],0);d.getButton(-1).performClick();
 }
 @Test public void allTenFitAndTouchesKeepIdentityAtSmallWidths()throws Exception{
  images();int[] widths={320,360,375,393};
  for(int count=3;count<=10;count++){
   if(count>3)add(new float[]{125,250,350,500,2000,4000,8000}[count-4]);
   for(int width:widths){layout(width,780);SimpleEqView eq=(SimpleEqView)field(a,"simpleEq");assertEquals(count,((Integer[])field(eq,"ids")).length);assertTrue(eq.getWidth()>=width-68);assertTrue(eq.getWidth()<width);assertTrue(eq.getWidth()/count>=26);if(count==3||count==4||count==6||count==10)shot("simple-"+count+"-"+width+".png");}
  }
  assertFalse(((Button)field(a,"addSimpleButton")).isEnabled());assertEquals(10,s.sound().simpleCount());
  layout(320,780);SimpleEqView eq=(SimpleEqView)field(a,"simpleEq");Integer[] ids=(Integer[])field(eq,"ids");int selected=ids[3];float x=eq.getWidth()*3.5f/10,y=32+(eq.getHeight()-87)*.375f;
  MotionEvent down=MotionEvent.obtain(0,0,0,x,32+(eq.getHeight()-87)*.5f,0),move=MotionEvent.obtain(0,10,2,x,y,0),up=MotionEvent.obtain(0,20,1,x,y,0);eq.onTouchEvent(down);eq.onTouchEvent(move);eq.onTouchEvent(up);down.recycle();move.recycle();up.recycle();assertEquals(3,s.sound().simpleGain(selected),.001);
  assertFalse(((ModeTransition)field(s,"transition")).busy());
  down=MotionEvent.obtain(0,30,0,x,eq.getHeight()-12,0);up=MotionEvent.obtain(0,50,1,x,eq.getHeight()-12,0);eq.onTouchEvent(down);eq.onTouchEvent(up);down.recycle();up.recycle();assertEquals(s.sound().simpleFrequency(selected),Float.parseFloat(input(dialog()).getText().toString()),0);dialog().cancel();
  setting(1);layout(320,780);assertEquals(3,s.sound().simpleCount());assertEquals(0,((ViewGroup.MarginLayoutParams)eq.getLayoutParams()).leftMargin);assertTrue(((Button)field(a,"addSimpleButton")).isEnabled());
 }
 @Test public void settingsPresetsPersistenceAndAdvancedRemainCompatible()throws Exception{
  add(350);SoundState edited=s.sound();edited.simpleFrequency(0,62);edited.simple[0]=3;edited.simpleGain(3,4);edited.bands[4]=2;s.edit(edited,true);s.preset("surround");idle(250);
  setting(0);assertEquals(4,s.sound().simpleCount());assertEquals(62,s.sound().simpleFrequency(0),0);assertEquals(0,s.sound().simple[0],0);assertEquals(2,s.sound().bands[4],0);assertTrue(s.sound().surround);
  edited=s.sound();edited.simple[0]=3;s.edit(edited,true);setting(1);assertEquals(3,s.sound().simpleCount());assertEquals(3,s.sound().simple[0],0);assertEquals(62,s.sound().simpleFrequency(0),0);
  setting(2);dialog().getButton(-2).performClick();idle(1);assertEquals(62,s.sound().simpleFrequency(0),0);
  setting(2);dialog().getButton(-1).performClick();idle(1);assertArrayEquals(SoundState.SIMPLE_DEFAULTS,s.sound().simpleFrequencies,0);assertEquals(0,s.sound().simple[0],0);assertTrue(s.sound().surround);assertEquals(2,s.sound().bands[4],0);
  add(450);edited=s.sound();edited.simpleGain(edited.simpleAdded.get(0).id,3.5f);s.edit(edited,true);assertTrue(s.saveSound("Personal",false));String saved=s.sound().pack();
  ((Button)field(a,"advancedTab")).performClick();assertEquals(View.VISIBLE,((View)field(a,"laboratory")).getVisibility());((Button)field(a,"simpleTab")).performClick();assertEquals(saved,s.sound().pack());
  controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class);a=controller.get();controller.setup().visible();bind();assertEquals(saved,s.sound().pack());
  idle(500);s.observe(null);s.onDestroy();s=Robolectric.buildService(EqService.class).create().get();bind();assertEquals(4,s.sound().simpleCount());assertEquals(3.5,s.sound().simpleGain(s.sound().simpleAdded.get(0).id),0);assertEquals(1,s.savedSounds().size());assertEquals(saved,s.savedSounds().get(0).pack());
 }
 @Test public void transparentLogoBothThemesKeepsSound()throws Exception{
  add(750);String state=s.sound().pack();images();layout(393,780);assertNull(logo().getBackground());shot("light-logo.png");
  ((Button)field(a,"themeButton")).performClick();controller.recreate();a=controller.get();bind();images();layout(393,780);assertTrue(new UiColors(a).dark);assertNull(logo().getBackground());assertEquals(state,s.sound().pack());shot("dark-logo.png");
  Bitmap b=Bitmap.createBitmap(logo().getWidth(),logo().getHeight(),Bitmap.Config.ARGB_8888);logo().draw(new Canvas(b));assertEquals(0,Color.alpha(b.getPixel(0,0)));b.recycle();
 }
}

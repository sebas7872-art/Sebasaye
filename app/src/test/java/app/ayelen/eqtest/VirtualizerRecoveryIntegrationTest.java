package app.ayelen.eqtest;
import static org.junit.Assert.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadow.api.Shadow;import org.robolectric.shadows.*;
import android.content.*;import android.media.*;import android.media.audiofx.*;import android.os.*;
import java.lang.reflect.*;import java.time.Duration;import java.util.*;

/** Session/ownership tests with simulated native effects. No acoustic claims. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34,shadows={VirtualizerRecoveryIntegrationTest.Core.class,VirtualizerRecoveryIntegrationTest.Driver.class,VirtualizerRecoveryIntegrationTest.DP.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class VirtualizerRecoveryIntegrationTest {
 EqService s;AudioManager audio;
 static Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 @Implements(AudioEffect.class) public static class Core extends ShadowDynamicsProcessing {
  @RealObject AudioEffect effect;
  @Implementation protected boolean hasControl(){return true;}
  @Implementation protected AudioEffect.Descriptor getDescriptor(){return new AudioEffect.Descriptor(AudioEffect.EFFECT_TYPE_VIRTUALIZER.toString(),Driver.nxp?"1d4033c0-8557-11df-9f2d-0002a5d5c51b":AudioEffect.EFFECT_TYPE_VIRTUALIZER.toString(),"Insert","Virtualizer",Driver.nxp?"NXP Software Ltd.":"Test");}
  @Implementation protected int setEnabled(boolean on){if(effect instanceof Virtualizer)return ((Driver)Shadow.extract(effect)).native_setEnabled(on);return super.native_setEnabled(on);}
  @Implementation protected boolean getEnabled(){if(effect instanceof Virtualizer)return ((Driver)Shadow.extract(effect)).enabled;return super.native_getEnabled();}
  @Implementation protected void setEnableStatusListener(AudioEffect.OnEnableStatusChangeListener l){if(effect instanceof Virtualizer)((Driver)Shadow.extract(effect)).enableListener=l;}
  @Implementation protected void release(){if(effect instanceof Virtualizer){Driver d=Shadow.extract(effect);d.released=true;d.enabled=false;}else if(effect instanceof DynamicsProcessing)((DP)Shadow.extract(effect)).released=true;super.native_release();}
 }
 @Implements(DynamicsProcessing.class) public static class DP extends Core {
  static final List<DP> all=new ArrayList<>();
  DynamicsProcessing.Config config;int session,writes;boolean released;
  DynamicsProcessing.Eq[] bands={new DynamicsProcessing.Eq(true,false,32),new DynamicsProcessing.Eq(true,false,32)};
  @Implementation protected void __constructor__(int priority,int id,DynamicsProcessing.Config value){session=id;config=value;all.add(this);}
  @Implementation protected int getChannelCount(){return 2;}
  @Implementation protected DynamicsProcessing.Config getConfig(){return config;}
  @Implementation protected DynamicsProcessing.Eq getPreEqByChannelIndex(int ch){return bands[ch];}
  @Implementation protected void setPreEqByChannelIndex(int ch,DynamicsProcessing.Eq eq){writes++;bands[ch]=eq;}
  @Implementation protected void setPreEqAllChannelsTo(DynamicsProcessing.Eq eq){writes++;bands[0]=eq;bands[1]=eq;}
  @Implementation protected void setPreEqBandByChannelIndex(int ch,int band,DynamicsProcessing.EqBand eq){writes++;bands[ch].setBand(band,eq);}
  @Implementation protected void setInputGainAllChannelsTo(float value){writes++;}
  @Implementation protected void setMbcAllChannelsTo(DynamicsProcessing.Mbc value){writes++;}
  @Implementation protected void setPostEqAllChannelsTo(DynamicsProcessing.Eq value){writes++;}
  @Implementation protected boolean native_hasControl(){return true;}
  @Implementation protected boolean hasControl(){return true;}
 }
 @Implements(Virtualizer.class) public static class Driver extends Core {
  @RealObject Virtualizer real;boolean enabled,released;short strength;int session,offs,ons,forces;
  AudioEffect.OnEnableStatusChangeListener enableListener;
  static boolean nxp=true;static int rejectPositive,positiveAttempts;
  static final List<Driver> all=new ArrayList<>();
  @Implementation protected void __constructor__(int priority,int id){session=id;if(id>0){positiveAttempts++;if(rejectPositive-->0)throw new UnsupportedOperationException("AudioTrack not ready");}all.add(this);}
  @Implementation protected boolean native_hasControl(){return true;}
  @Implementation protected boolean hasControl(){return true;}
  @Implementation protected boolean native_getEnabled(){return enabled;}
  @Implementation protected int native_setEnabled(boolean on){if(released)throw new IllegalStateException("released handle");if(!on&&enabled)offs++;if(on&&!enabled)ons++;enabled=on;if(enableListener!=null)enableListener.onEnableStatusChange(real,on);return 0;}
  @Implementation protected void native_release(){released=true;enabled=false;super.native_release();}
  @Implementation protected void setEnableStatusListener(AudioEffect.OnEnableStatusChangeListener l){enableListener=l;}
  @Implementation protected short getRoundedStrength(){return strength;}
  @Implementation protected void setStrength(short value){if(released)throw new IllegalStateException("released handle");strength=value;}
  @Implementation protected boolean getStrengthSupported(){return true;}
  @Implementation protected boolean canVirtualize(int channels,int mode){return mode==Virtualizer.VIRTUALIZATION_MODE_BINAURAL;}
  @Implementation protected boolean forceVirtualizationMode(int mode){if(released)throw new IllegalStateException("released handle");forces++;return true;}
  // Deliberately reports BINAURAL even for the old session-0 handle. The test
  // verifies binding lifetime instead of inferring sound from this getter.
  @Implementation protected int getVirtualizationMode(){return enabled?2:0;}
 }
 @Before public void reset(){RuntimeEnvironment.getApplication().getSharedPreferences("eq-test",0).edit().clear().commit();Driver.all.clear();DP.all.clear();Driver.nxp=true;Driver.rejectPositive=0;Driver.positiveAttempts=0;}
 @After public void cleanup(){if(s!=null)s.onDestroy();}
 void start(String vendor){ShadowBuild.setManufacturer(vendor);audio=RuntimeEnvironment.getApplication().getSystemService(AudioManager.class);Shadows.shadowOf(audio).setIsMusicActive(true);s=Robolectric.buildService(EqService.class).create().get();s.preset("surround");enable();}
 void enable(){s.onStartCommand(new Intent(s,EqService.class).setAction(EqService.ENABLE),0,1);idle(3000);}
 void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
 Map<?,?> effects()throws Exception{return (Map<?,?>)field(s,"effects");}
 Object held()throws Exception{assertEquals(1,effects().size());assertTrue(effects().containsKey(0));return effects().get(0);}
 Driver driver()throws Exception{Virtualizer v=(Virtualizer)field(held(),"virtual");assertNotNull(v);return Shadow.extract(v);}
 DP dp()throws Exception{return Shadow.extract((DynamicsProcessing)field(held(),"effect"));}
 void active(int session)throws Exception{Driver d=driver();assertEquals(session,d.session);assertTrue(d.enabled);assertFalse(d.released);assertEquals(1,Driver.all.stream().filter(v->!v.released).count());assertFalse(((VirtualizerStart)field(held(),"activation")).running());assertEquals(0,dp().session);assertFalse(dp().released);}
 void close(int id,String owner)throws Exception{((BroadcastReceiver)field(s,"sessionReceiver")).onReceive(s,new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).putExtra(AudioEffect.EXTRA_AUDIO_SESSION,id).putExtra(AudioEffect.EXTRA_PACKAGE_NAME,owner));}
 @Test public void sameIdCloseOpenReplacesOnlyVirtualizerDespiteReportedOn()throws Exception{
  start("motorola");active(0);Driver global=driver();DP processor=dp();int writes=processor.writes;String sound=s.sound().pack();
  assertEquals(0,global.offs);s.noteSession(801,"com.spotify.music");idle(3000);active(801);assertTrue(global.released);
  Driver first=driver();close(801,"com.spotify.music");assertTrue(first.released);s.noteSession(801,"com.spotify.music");idle(3000);active(801);assertNotSame(first,driver());
  assertSame(processor,dp());assertEquals(writes,processor.writes);assertEquals(sound,s.sound().pack());assertEquals(0,driver().offs);
 }
 @Test public void duplicateOpenAndSliderDoNotRecreateWhileTrackChangesDo()throws Exception{
  start("motorola");s.noteSession(801,"com.spotify.music");idle(3000);Driver first=driver();int count=Driver.all.size();s.noteSession(801,"com.spotify.music");idle(3000);assertSame(first,driver());assertEquals(count,Driver.all.size());
  for(int amount:new int[]{0,35,100}){SoundState sound=s.sound();sound.strength=amount;s.edit(sound,false);idle(100);assertSame(first,driver());assertEquals(amount*10,first.strength);}
  DP processor=dp();int writes=processor.writes;
  for(int i=0;i<20;i++){close(801,"com.spotify.music");s.noteSession(801,"com.spotify.music");idle(700);active(801);}
  assertSame(processor,dp());assertEquals(writes,processor.writes);assertEquals(count+20,Driver.all.size());
 }
 @Test public void rapidSessionsDebounceAndIgnoreWrongOwnerClose()throws Exception{
  start("motorola");s.noteSession(801,"com.spotify.music");idle(3000);Driver first=driver();close(801,"unrelated.app");idle(500);assertSame(first,driver());
  close(801,"com.spotify.music");s.noteSession(802,"com.spotify.music");idle(80);close(802,"com.spotify.music");s.noteSession(803,"com.spotify.music");idle(3000);active(803);
  assertFalse(Driver.all.stream().anyMatch(v->v.session==802));assertEquals(0,driver().offs);
 }
 @Test public void masterOffSwitchOffSurroundAndReconnectKeepSession()throws Exception{
  start("motorola");s.noteSession(801,"com.spotify.music");idle(3000);Driver first=driver();
  // The unchanged SoundModel intentionally updates modified/return-preset metadata.
  SoundModel original=(SoundModel)field(s,"model"), expected=new SoundModel();expected.current=original.current.copy();expected.beforeSurround=original.beforeSurround.copy();
  s.setVirtualizer(false);idle(1000);assertTrue(first.released);s.setVirtualizer(true);idle(3000);active(801);expected.virtualize(false);expected.virtualize(true);assertEquals(expected.current.pack(),s.sound().pack());
  s.preset("surround");idle(1000);s.preset("surround");idle(3000);active(801);expected.preset("surround");expected.preset("surround");assertEquals(expected.current.pack(),s.sound().pack());String sound=s.sound().pack();
  Driver old=driver();s.disableWithTransition();idle(2000);assertTrue(old.released);assertTrue(effects().isEmpty());enable();active(801);assertEquals(sound,s.sound().pack());
  old=driver();s.reconnect();idle(3000);active(801);assertTrue(old.released);assertEquals(sound,s.sound().pack());
 }
 @Test public void routeReconnectAndLateCallbacksCannotTouchReleasedHandle()throws Exception{
  start("motorola");s.noteSession(801,"com.spotify.music");idle(3000);Driver old=driver();AudioEffect.OnEnableStatusChangeListener stale=old.enableListener;DP processor=dp();
  ((AudioDeviceCallback)field(s,"deviceCallback")).onAudioDevicesAdded(new AudioDeviceInfo[0]);idle(3000);active(801);assertTrue(old.released);assertSame(processor,dp());int count=Driver.all.size();
  stale.onEnableStatusChange(old.real,false);idle(1000);active(801);assertEquals(count,Driver.all.size());
 }
 @Test public void shutdownCancelsPendingBindings()throws Exception{
  start("motorola");s.noteSession(801,"com.spotify.music");s.disable();int count=Driver.all.size();idle(10000);assertTrue(effects().isEmpty());assertEquals(count,Driver.all.size());assertEquals(0,Driver.all.stream().filter(v->!v.released).count());
 }
 @Test public void transientCreationFailuresRetryOnlyVirtualizerAndPermanentFailureStops()throws Exception{
  start("motorola");DP processor=dp();int writes=processor.writes;Driver.rejectPositive=2;s.noteSession(801,"com.spotify.music");idle(5000);active(801);assertEquals(3,Driver.positiveAttempts);assertSame(processor,dp());assertEquals(writes,processor.writes);
  close(801,"com.spotify.music");Driver.rejectPositive=100;s.noteSession(802,"com.spotify.music");idle(15000);assertNull(field(held(),"virtual"));assertEquals(6,Driver.positiveAttempts);assertSame(processor,dp());assertFalse(processor.released);
  idle(15000);assertEquals(6,Driver.positiveAttempts);Driver.rejectPositive=0;s.setVirtualizer(false);idle(500);s.setVirtualizer(true);idle(3000);active(802);
 }
 @Test public void knownSessionBeforeEnableAvoidsActivatingGlobalVirtualizer()throws Exception{
  ShadowBuild.setManufacturer("motorola");s=Robolectric.buildService(EqService.class).create().get();s.noteSession(801,"com.spotify.music");s.preset("surround");enable();active(801);
  assertEquals(0,Driver.all.get(0).session);assertEquals(0,Driver.all.get(0).ons);assertTrue(Driver.all.get(0).released);
 }
 @Test public void samsungNxpStillKeepsOriginalGlobalPath()throws Exception{
  start("samsung");active(0);Driver original=driver();int forces=original.forces;String sound=s.sound().pack();s.noteSession(801,"com.spotify.music");idle(1000);close(801,"com.spotify.music");s.noteSession(801,"com.spotify.music");idle(3000);active(0);assertSame(original,driver());assertEquals(forces,original.forces);assertEquals(0,original.offs);assertEquals(sound,s.sound().pack());
 }
 @Test public void motorolaOtherImplementationIsNotRebound()throws Exception{
  Driver.nxp=false;start("motorola");Driver original=driver();s.noteSession(801,"com.spotify.music");idle(3000);active(0);assertSame(original,driver());assertEquals(0,original.offs);
 }
 @Test public void dynamicSimpleEditsUseSameEngineAndOnlyEnableNeededEq()throws Exception{
  start("samsung");s.preset("reset");idle(500);DP processor=dp();assertFalse(processor.bands[0].isEnabled());int engines=DP.all.size();
  SoundState state=s.sound();state.addSimple(350);int id=state.simpleAdded.get(0).id;state.simpleGain(id,4);s.edit(state,true);idle(50);
  assertSame(processor,dp());assertTrue(processor.bands[0].isEnabled());assertEquals(32,processor.bands[0].getBandCount());
  for(int i=0;i<70;i++){state=s.sound();state.simpleFrequency(id,200+i*20);state.simpleGain(id,(i%20)*.5f);s.edit(state,true);}
  idle(80);assertSame(processor,dp());assertEquals(engines,DP.all.size());assertArrayEquals(EqResponse.gains(s.sound()),(float[])field(s,"curve"),.00001f);
  state=s.sound();state.removeSimple(id);s.edit(state,true);idle(50);assertFalse(processor.bands[0].isEnabled());assertSame(processor,dp());
 }
}

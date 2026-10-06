package app.ayelen.eqtest;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;import java.nio.file.*;
public class SimpleBandsTest {
 @Test public void originalIdentityAdditionLimitOrderAndDeletion(){
  SoundState s=new SoundState();assertArrayEquals(new float[]{37,1000,10000},s.simpleFrequencies,0);assertEquals(3,s.simpleCount());
  for(float hz:new float[]{125,250,350,500,2000,4000,8000})assertTrue(s.addSimple(hz));assertEquals(10,s.simpleCount());assertFalse(s.addSimple(600));
  float last=0;for(int id:s.sortedSimpleIds()){assertTrue(s.simpleFrequency(id)>=last);last=s.simpleFrequency(id);}
  int id=s.simpleAdded.get(2).id;s.simpleGain(id,4.5f);s.simpleFrequency(id,15000);assertEquals(4.5,s.simpleGain(id),0);assertEquals(id,(int)s.sortedSimpleIds()[9]);
  s.removeSimple(id);assertFalse(s.hasSimple(id));assertEquals(9,s.simpleCount());s.removeSimple(0);assertEquals(9,s.simpleCount());
 }
 @Test public void flatRemoveAndResetHaveDifferentSemantics(){
  SoundState s=new SoundState();s.surround=true;s.output=4;s.makeup=6;s.simpleFrequency(0,50);s.simple[0]=3;s.bands[5]=4;s.addSimple(750);int id=s.simpleAdded.get(0).id;s.simpleGain(id,6);
  SoundState copy=s.copy();s.flatSimple();assertEquals(4,s.simpleCount());assertEquals(50,s.simpleFrequency(0),0);for(int i:s.sortedSimpleIds())assertEquals(0,s.simpleGain(i),0);assertEquals(4,s.bands[5],0);assertTrue(s.surround);
  s=copy.copy();s.simpleAdded.clear();assertEquals(3,s.simple[0],0);assertEquals(50,s.simpleFrequency(0),0);
  s.resetSimple();assertEquals(3,s.simpleCount());assertArrayEquals(SoundState.SIMPLE_DEFAULTS,s.simpleFrequencies,0);assertArrayEquals(new float[3],s.simple,0);assertEquals(6,s.makeup,0);assertTrue(s.surround);
 }
 @Test public void v4RoundTripCopyAndSurroundIndependence(){
  SoundModel m=new SoundModel();m.current.simpleFrequency(2,9500);m.current.addSimple(400);int id=m.current.simpleAdded.get(0).id;m.current.simpleGain(id,2.5f);m.preset("surround");SoundState recipe=SoundState.surround();
  SoundState s=SoundState.unpack(m.current.pack());assertNotNull(s);assertEquals(m.current.pack(),s.pack());assertEquals(4,s.simpleCount());
  m.current.simpleFrequency(id,450);m.current.simpleGain(id,4);m.leaveSurround();assertEquals(450,m.current.simpleFrequency(id),0);assertEquals(4,m.current.simpleGain(id),0);assertEquals(9500,m.current.simpleFrequency(2),0);assertEquals(0,m.current.output,0);assertEquals(recipe.pack(),SoundState.surround().pack());
  SoundState a=m.current.copy();a.simpleGain(id,-3);assertEquals(4,m.current.simpleGain(id),0);
 }
 @Test public void legacyStateAndSoundMatchActual054Fixtures()throws Exception{
  Path fixture=Paths.get(getClass().getResource("/legacy-054.txt").toURI());
  for(String line:Files.readAllLines(fixture)){
   String[] data=line.split("\\|");SoundState s=SoundState.unpack(data[0]);assertNotNull(s);assertEquals(3,s.simpleCount());assertArrayEquals(SoundState.SIMPLE_DEFAULTS,s.simpleFrequencies,0);
   float[] gains=EqResponse.gains(s);for(int i=0;i<32;i++)assertEquals(Float.parseFloat(data[i+1]),gains[i],.00001f);
   assertEquals(s.pack(),SoundState.unpack(s.pack()).pack());
  }
 }
 @Test public void filtersSumOnlyIntoUserEqAndRemovalIsNeutral(){
  SoundModel m=new SoundModel();m.preset("surround");SoundState s=m.current;float[] initial=EqResponse.gains(s);s.addSimple(350);int id=s.simpleAdded.get(0).id;s.simpleGain(id,4);
  for(int i=0;i<32;i++)assertEquals(initial[i]+EqResponse.response(EqResponse.CENTERS[i],350,4,.8,0),EqResponse.gains(s)[i],.00001);
  s.eqOn=false;assertArrayEquals(initial,EqResponse.gains(s),.00001f);s.eqOn=true;s.removeSimple(id);assertArrayEquals(initial,EqResponse.gains(s),0);
 }
 @Test public void invalidAndTruncatedStorageIsBounded(){
  SoundState s=new SoundState();assertFalse(s.addSimple(Float.NaN));assertFalse(s.addSimple(0));assertFalse(s.addSimple(20001));
  s.simpleAdded.add(new SoundState.SimpleBand(0,100,5));s.simpleAdded.add(new SoundState.SimpleBand(3,Float.NaN,Float.POSITIVE_INFINITY));s.simpleAdded.add(new SoundState.SimpleBand(3,500,5));s.sanitize();assertEquals(4,s.simpleCount());assertEquals(1000,s.simpleFrequency(3),0);assertEquals(0,s.simpleGain(3),0);
  String packed=s.pack();assertNull(SoundState.unpack(packed.substring(0,packed.length()-8)));assertNull(SoundState.unpack("bad"));
 }
}

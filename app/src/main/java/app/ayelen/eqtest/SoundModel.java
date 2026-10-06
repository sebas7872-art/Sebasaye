package app.ayelen.eqtest;

/** User EQ and hidden SURROUND recipe share one native engine. */
final class SoundModel {
    SoundState current = new SoundState();
    SoundState beforeSurround;

    void preset(String name) {
        if ("reset".equals(name)) { current=new SoundState();beforeSurround=null;return; }
        if ("surround".equals(name)) {
            if(current.surround) leaveSurround();
            else {
                beforeSurround=current.copy();beforeSurround.surround=false;beforeSurround.surroundReturn="";
                SoundState next=SoundState.surround();copyUserEq(current,next);
                next.surroundReturn=beforeSurround.pack();next.tone=current.tone;
                next.savedId=current.savedId;next.savedName=current.savedName;next.modified=true;
                current=next;
            }
            return;
        }
        current.setTone(name);
    }
    static void copyUserEq(SoundState from,SoundState to) {
        to.q=from.q;to.bands=from.bands.clone();to.frequencies=from.frequencies.clone();to.bandQs=from.bandQs.clone();
        to.types=from.types.clone();to.bandOn=from.bandOn.clone();from.copySimpleTo(to);to.eqOn=from.eqOn;
    }
    void leaveSurround() {
        if(!current.surround)return;
        SoundState back=beforeSurround!=null?beforeSurround:SoundState.unpack(current.surroundReturn);
        if(back==null)back=new SoundState();else back=back.copy();
        // Keep all user EQ edits made while listening to SURROUND.
        copyUserEq(current,back);back.tone=current.tone;
        back.savedId=current.savedId;back.savedName=current.savedName;back.modified=true;
        back.surround=false;back.surroundReturn="";current=back;beforeSurround=null;
    }
    void virtualize(boolean on) { current.virtualizer=on;current.edited(false); }
    void edit(SoundState next,boolean tonal) { next.sanitize();next.edited(tonal);current=next.copy(); }
    void load(SoundState saved) {
        current=saved.copy();current.modified=false;
        beforeSurround=current.surround?SoundState.unpack(current.surroundReturn):null;
    }
}

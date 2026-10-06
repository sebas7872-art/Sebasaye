package app.ayelen.eqtest;
import android.content.Context;

/** UI-only palette. The accent is sampled from the supplied SANAVERA logo. */
final class UiColors {
    static final int ACCENT=0xff7b011e, RIPPLE=0x337b011e;
    final boolean dark;
    final int card,ink,muted,line,canvas,zero,top,bottom,value;
    UiColors(Context context){
        dark=context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark",false);
        card=dark?0xff25262a:0xffffffff;ink=dark?0xfff0eef0:0xff35383d;
        muted=dark?0xffb5b3b9:0xff74787e;line=dark?0xff57575f:0xffe5e6e8;
        canvas=dark?0xff1c1d21:0xfffafafa;zero=dark?0xff85838b:0xffb9bbbf;
        top=dark?0xff37363d:0xffb2b3b5;bottom=dark?0xff101114:0xff696b6e;
        value=dark?0xffe7d4da:ACCENT;
    }
}

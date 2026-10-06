package app.ayelen.eqtest;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.text.InputType;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.AudioEffect;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

public final class MainActivity extends Activity {
    private int CARD=0xffffffff, INK=0xff35383d;
    private UiColors colors;
    private Button themeButton;
    private ScrollView mainScroll;
    private int MUTED=0xff74787e, LINE=0xffe5e6e8;
    private final int MINT=UiColors.ACCENT;
    private EqService service;
    private boolean bound,syncing,showDiagnostic;
    private long lastDiagnostic;
    private int pendingSession=-1;
    private String pendingOwner;
    private Button power;
    private Switch surround, userEqSwitch;
    private SimpleEqView simpleEq;
    private Button addSimpleButton,simpleSettingsButton;
    private AlertDialog simpleDialog;
    private final Control[] simpleAdvancedControls=new Control[3];
    private DeviceMonitor deviceMonitor;
    private TextView deviceName,deviceKind;
    private ImageView deviceImage;
    private String deviceIdentity="phone",deviceAutoKey="generic",artKey="";
    private LinearLayout laboratory,deviceHero;
    private final List<View> advancedSections=new ArrayList<>();
    private TextView state,status,soundTitle,virtualStatus,diagnostic;
    private Spinner mode,engine,spatialMode;
    private Switch virtual,compressor;
    private BandView bands;
    private CurveView curveView;
    private SpectrumView spectrum;
    private LinearLayout simpleBox,advancedBox;
    private Button simpleTab,advancedTab,analyzerButton,frequencyValue,gainValue,qValue;
    private TextView analyzerStatus,bandCaption;
    private Spinner bandChoice,bandType;
    private Switch bandEnabled;
    private SeekBar frequencySlider,gainSlider,qSlider;
    private final Rect spectrumBounds=new Rect();
    private boolean advanced,resumed,analyzerWanted;
    private int selectedBand=3;
    private String choiceSignature="";
    private final String[] bandNames=new String[10];
    private ArrayAdapter<String> bandAdapter;
    private final Map<String,Button> tones=new LinkedHashMap<>();
    private final List<Control> controls=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable() {
        @Override public void run() { sync();handler.postDelayed(this,2000); }
    };
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            service=((EqService.LocalBinder)binder).service();service.observe(()->sync());deliverSession();sync();ThemeBinding.release();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service=null;sync(); }
    };

    @Override public void onCreate(Bundle bundle) {
        colors=new UiColors(this);CARD=colors.card;INK=colors.ink;MUTED=colors.muted;LINE=colors.line;
        int style=getResources().getIdentifier(colors.dark?"AppThemeDark":"AppTheme","style",getPackageName());
        setTheme(style!=0?style:colors.dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(bundle);
        getWindow().setStatusBarColor(colors.top);getWindow().setNavigationBarColor(colors.bottom);readPanel(getIntent());
        advanced=bundle!=null&&bundle.getBoolean("advanced",false);
        analyzerWanted=getPreferences(MODE_PRIVATE).getBoolean("analyzer",false);
        if(bundle!=null)selectedBand=Math.max(0,Math.min(9,bundle.getInt("band",3)));
        LinearLayout root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{colors.top,colors.bottom}));
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(18),dp(12),dp(18),dp(10));
        LinearLayout brand=column();brand.setPadding(0,0,dp(10),0);
        DeviceImageView logo=new DeviceImageView(this);logo.logo();logo.setContentDescription("Sanavera");
        logo.setImageResource(getResources().getIdentifier("sanavera_logo","drawable",getPackageName()));
        logo.setPadding(dp(3),0,dp(3),0);
        brand.addView(logo,new LinearLayout.LayoutParams(-1,dp(28)));
        TextView sub=label("EQ STUDIO  /  1.0.0",10,MUTED,true);sub.setLetterSpacing(.13f);brand.addView(sub);
        heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        themeButton=button(colors.dark?"☀":"☾",false);themeButton.setTextSize(22);
        themeButton.setContentDescription(colors.dark?"Cambiar a modo claro":"Cambiar a modo oscuro");
        themeButton.setOnClickListener(v->{getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("dark",!colors.dark).apply();ThemeBinding.hold(this);recreate();});
        LinearLayout.LayoutParams themeSize=new LinearLayout.LayoutParams(dp(42),dp(42));themeSize.rightMargin=dp(7);heading.addView(themeButton,themeSize);
        power=button("EQ OFF",false);power.setTextSize(12);power.setContentDescription("Activar o desactivar todo el ecualizador");
        power.setOnClickListener(v->{if(service!=null){if(service.isEnabled())service.disableWithTransition();else enable();}});
        heading.addView(power,new LinearLayout.LayoutParams(dp(80),dp(44)));root.addView(heading);
        LinearLayout tabs=row();tabs.setGravity(Gravity.CENTER);tabs.setPadding(0,dp(2),0,dp(2));
        simpleTab=button("Simple",false);advancedTab=button("Avanzado",false);
        simpleTab.setOnClickListener(v->showAdvanced(false,true));advancedTab.setOnClickListener(v->showAdvanced(true,true));
        simpleTab.setTextSize(13);advancedTab.setTextSize(13);
        LinearLayout.LayoutParams tabSize=new LinearLayout.LayoutParams(dp(110),dp(42));tabSize.setMargins(dp(3),0,dp(3),0);
        tabs.addView(simpleTab,tabSize);tabs.addView(advancedTab,new LinearLayout.LayoutParams(tabSize));
        simpleSettingsButton=button("⚙",false);simpleSettingsButton.setTextSize(21);simpleSettingsButton.setPadding(0,0,0,0);
        simpleSettingsButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227b011e),bg(CARD,20),null));
        simpleSettingsButton.setContentDescription("Ajustes de Simple");simpleSettingsButton.setOnClickListener(v->simpleSettings());
        LinearLayout.LayoutParams settingsSize=new LinearLayout.LayoutParams(dp(40),dp(40));settingsSize.leftMargin=dp(6);tabs.addView(simpleSettingsButton,settingsSize);
        ScrollView scroll=new ScrollView(this);mainScroll=scroll;scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout page=column();page.setPadding(dp(14),0,dp(14),dp(28));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.setOnScrollChangeListener((v,x,y,oldx,oldy)->syncAnalyzer());

        deviceHero=column();deviceHero.setGravity(Gravity.CENTER);deviceHero.setPadding(0,dp(8),0,dp(8));page.addView(deviceHero);
        deviceName=label("Este teléfono",24,Color.WHITE,true);deviceName.setGravity(Gravity.CENTER);deviceHero.addView(deviceName);
        deviceImage=new DeviceImageView(this);
        deviceImage.setContentDescription("Imagen del dispositivo. Tocá para elegir el modelo.");
        int imageHeight=getResources().getConfiguration().screenHeightDp<650?104:136;
        deviceHero.addView(deviceImage,new LinearLayout.LayoutParams(-1,dp(imageHeight)));deviceImage.setOnClickListener(v->chooseDeviceArt());
        deviceKind=label("Altavoz integrado",12,Color.WHITE,false);deviceKind.setGravity(Gravity.CENTER);deviceHero.addView(deviceKind);
        deviceMonitor=new DeviceMonitor(this,(name,kind,identity)->{
            deviceName.setText(name);deviceKind.setText(kind);deviceIdentity=identity;deviceAutoKey=DeviceCatalog.match(name);syncDeviceArt();
        });syncDeviceArt();
        page.addView(tabs);
        LinearLayout surroundCard=card(page);surround=toggle("SURROUND");surroundCard.addView(surround);
        surround.setOnCheckedChangeListener((v,on)->{if(!syncing&&service!=null&&on!=service.sound().surround)preset("surround");});
        surroundCard.addView(label("Tu sonido envolvente · combinable con tu EQ",11,MUTED,false));
        LinearLayout simpleCard=card(page);simpleBox=column();simpleCard.addView(simpleBox);
        simpleCard.setClipChildren(false);simpleCard.setClipToPadding(false);simpleBox.setClipChildren(false);simpleBox.setClipToPadding(false);
        LinearLayout simpleHeader=row();simpleHeader.setGravity(Gravity.CENTER_VERTICAL);
        userEqSwitch=toggle("Ecualizador");simpleHeader.addView(userEqSwitch,new LinearLayout.LayoutParams(0,-2,1));
        addSimpleButton=button("+",false);addSimpleButton.setTextSize(25);addSimpleButton.setPadding(0,0,0,0);addSimpleButton.setContentDescription("Agregar banda");
        addSimpleButton.setOnClickListener(v->editSimple(-1));LinearLayout.LayoutParams addSize=new LinearLayout.LayoutParams(dp(40),dp(44));addSize.leftMargin=dp(7);simpleHeader.addView(addSimpleButton,addSize);simpleBox.addView(simpleHeader);
        userEqSwitch.setOnCheckedChangeListener((v,on)->{if(!syncing&&service!=null){SoundState sound=service.sound();sound.eqOn=on;service.editSwitch(sound,true);}});
        simpleEq=new SimpleEqView(this);simpleEq.listen(new SimpleEqView.Listener(){
            public void change(int i,float value){if(service!=null){SoundState sound=service.sound();if(sound.hasSimple(i)){sound.simpleGain(i,value);service.edit(sound,true);}}}
            public void edit(int i){editSimple(i);}
        });simpleBox.addView(simpleEq,new LinearLayout.LayoutParams(-1,dp(232)));
        advancedSections.add(simpleCard); // handled separately in showAdvanced
        laboratory=column();page.addView(laboratory);
        LinearLayout info=card(laboratory);
        GradientDrawable hero=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{CARD,colors.dark?0xff1f2024:0xfff5f5f6});hero.setCornerRadius(dp(24));info.setBackground(hero);
        LinearLayout infoTop=row();infoTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow=label("TU SONIDO",10,MUTED,true);eyebrow.setLetterSpacing(.15f);infoTop.addView(eyebrow,new LinearLayout.LayoutParams(0,-2,1));
        analyzerButton=button("Espectro",false);analyzerButton.setTextSize(11);analyzerButton.setOnClickListener(v->toggleAnalyzer());infoTop.addView(analyzerButton,new LinearLayout.LayoutParams(dp(94),dp(38)));info.addView(infoTop);
        soundTitle=label("Flat",22,INK,true);info.addView(soundTitle);state=label("Conectando…",12,colors.value,true);info.addView(state);
        spectrum=new SpectrumView(this);spectrum.listen(message->analyzerStatus.setText(message));info.addView(spectrum,new LinearLayout.LayoutParams(-1,dp(115)));
        analyzerStatus=label("Activá Espectro para ver tu música",10,MUTED,false);info.addView(analyzerStatus);
        status=label("",11,MUTED,false);info.addView(status);

        section(laboratory,"MODOS DE SONIDO");
        LinearLayout shortcuts=row();
        for(String id:new String[]{"rock","pop","jazz","reset"}) {
            Button b=button(id.toUpperCase(Locale.ROOT),false);b.setTextSize(12);b.setPadding(dp(2),0,dp(2),0);
            b.setOnClickListener(v->preset(id));tones.put(id,b);shortcuts.addView(b,weighted());
        }
        laboratory.addView(shortcuts);
        LinearLayout library=row();Button sounds=button("Mis sonidos",false),save=button("Guardar sonido",false);
        sounds.setOnClickListener(v->showSounds());save.setOnClickListener(v->saveMenu());library.addView(sounds,weighted());library.addView(save,weighted());laboratory.addView(library);

        LinearLayout eq=card(laboratory);LinearLayout eqHeader=row();eqHeader.setGravity(Gravity.CENTER_VERTICAL);
        eqHeader.addView(label("Ecualizador",20,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Button edit=button("Bandas",false);edit.setTextSize(11);edit.setOnClickListener(v->chooseBand());eqHeader.addView(edit,new LinearLayout.LayoutParams(dp(78),dp(40)));eq.addView(eqHeader);
        advancedBox=column();eq.addView(advancedBox);
        advancedBox.addView(label("Tu EQ · independiente de la receta SURROUND",11,MUTED,false));
        advancedBox.addView(label("10 bandas · tocá una frecuencia para editar",11,MUTED,false));
        bands=new BandView(this);bands.listen(new BandView.Listener(){
            public void changed(int band,float value){if(service!=null){SoundState s=service.sound();s.bands[band]=value;s.bandOn[band]=true;service.edit(s,true);}}
            public void edit(int band){editBand(band);}
        });advancedBox.addView(bands,new LinearLayout.LayoutParams(-1,dp(245)));
        advancedBox.addView(label("Arrastrá un punto: frecuencia ↔  ganancia ↕",11,MUTED,false));
        curveView=new CurveView(this);curveView.select(selectedBand);curveView.listen(new CurveView.Listener(){
            public void select(int i){selectedBand=i;syncEditor();}
            public void change(int i,float hz,float db){if(service!=null){SoundState s=service.sound();s.frequencies[i]=hz;s.bands[i]=db;s.bandOn[i]=true;service.edit(s,true);}}
        });advancedBox.addView(curveView,new LinearLayout.LayoutParams(-1,dp(230)));
        for(int i=0;i<10;i++)bandNames[i]="Banda "+(i+1);
        bandChoice=new Spinner(this);bandAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,bandNames);bandChoice.setAdapter(bandAdapter);bandChoice.setContentDescription("Banda seleccionada");advancedBox.addView(bandChoice,new LinearLayout.LayoutParams(-1,dp(48)));
        bandChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(!syncing){selectedBand=i;curveView.select(i);syncEditor();}}public void onNothingSelected(AdapterView<?> p){}});
        LinearLayout typeRow=row();bandType=new Spinner(this);bandType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Campana","Shelf graves","Shelf agudos"}));bandType.setContentDescription("Tipo de filtro");typeRow.addView(bandType,new LinearLayout.LayoutParams(0,dp(48),1));
        bandEnabled=toggle("Activo");bandEnabled.setTextSize(13);typeRow.addView(bandEnabled,new LinearLayout.LayoutParams(dp(98),dp(48)));advancedBox.addView(typeRow);
        bandType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(!syncing&&service!=null){SoundState s=service.sound();int type=i==1?-1:i==2?1:0;if(s.types[selectedBand]!=type){s.types[selectedBand]=type;service.edit(s,true);}}}public void onNothingSelected(AdapterView<?> p){}});
        bandEnabled.setOnCheckedChangeListener((v,on)->{if(!syncing&&service!=null){SoundState s=service.sound();s.bandOn[selectedBand]=on;service.editSwitch(s,true);}});
        frequencyValue=editorRow(advancedBox,"Frecuencia",0);frequencySlider=editorSlider(advancedBox,1000,0);
        gainValue=editorRow(advancedBox,"Ganancia",1);gainSlider=editorSlider(advancedBox,96,1);
        qValue=editorRow(advancedBox,"Q · ancho de campana",2);qSlider=editorSlider(advancedBox,52,2);
        bandCaption=label("",11,MUTED,false);advancedBox.addView(bandCaption);
        Button restoreBand=button("Restaurar esta banda",false);restoreBand.setTextSize(12);restoreBand.setOnClickListener(v->{if(service!=null){SoundState s=service.sound();s.resetBand(selectedBand);service.edit(s,true);}});advancedBox.addView(restoreBand,fullButton());
        LinearLayout quick=card(laboratory);quick.addView(label("Ajustes del modo Simple",18,INK,true));
        for(int i=0;i<3;i++){final int index=i;String[] names={"Graves · 37 Hz","Medios · 1 kHz","Agudos · 10 kHz"};
            control(quick,names[i],-12,12,.5f,"dB",state->state.simple[index],(state,value)->state.simple[index]=value,true,false);simpleAdvancedControls[i]=controls.get(controls.size()-1);}


        LinearLayout spatial=card(laboratory);virtual=toggle("Virtualizer");spatial.addView(virtual);virtualStatus=label("",12,colors.value,false);spatial.addView(virtualStatus);
        spatialMode=new Spinner(this);spatialMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Auriculares (Bluetooth / cable)","Automático"}));spatialMode.setContentDescription("Modo del virtualizador");spatial.addView(spatialMode,new LinearLayout.LayoutParams(-1,dp(50)));
        spatialMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!syncing&&service!=null)service.setHeadphoneMode(position==0);}public void onNothingSelected(AdapterView<?> p){}});
        virtual.setOnCheckedChangeListener((v,on)->{if(!syncing&&service!=null){service.setVirtualizer(on);if(on&&!service.isEnabled())enable();}});
        control(spatial,"Intensidad espacial",0,100,1,"%",s->s.strength,(s,v)->s.strength=Math.round(v),false,false);
        TextView spreadTitle=label("Apertura L/R",17,INK,true);spatial.addView(spreadTitle);
        spatial.addView(label("Experimental · contraste de frecuencias entre izquierda y derecha. Complementa al Virtualizer; no equivale a Mid/Side.",11,MUTED,false));
        control(spatial,"Apertura espectral",0,100,1,"%",s->s.spread,(s,v)->s.spread=v,false,true);
        Button neutralSpread=button("Apertura neutra",false);neutralSpread.setTextSize(12);neutralSpread.setOnClickListener(v->{if(service!=null)service.setSpread(0);});spatial.addView(neutralSpread,fullButton());

        LinearLayout gain=card(laboratory);gain.addView(label("Ganancia y color",20,INK,true));
        gain.addView(label("Control directo, sin compensación automática.",11,MUTED,false));
        control(gain,"Volumen extra",-12,9,.5f,"dB",s->s.output,(s,v)->s.output=v,false,true);
        control(gain,"Preamplificador",-12,6,.5f,"dB",s->s.preamp,(s,v)->s.preamp=v,false,true);
        control(gain,"Presencia · 2.8 kHz",-6,6,.5f,"dB",s->s.presence,(s,v)->s.presence=v,false,false);
        control(gain,"Brillo · 10 kHz",-6,9,.5f,"dB",s->s.air,(s,v)->s.air=v,false,false);
        control(gain,"Q global · reinicia Q individuales",.4f,3,.05f,"",s->s.q,(s,v)->{s.q=v;Arrays.fill(s.bandQs,0);},true,false);

        LinearLayout dynamics=card(laboratory);compressor=toggle("Compresor");dynamics.addView(compressor);
        dynamics.addView(label("Makeup también funciona con el compresor apagado.",11,MUTED,false));
        compressor.setOnCheckedChangeListener((v,on)->{if(!syncing&&service!=null){SoundState s=service.sound();s.compressor=on;service.editSwitch(s,false);}});
        control(dynamics,"Makeup independiente",0,6,.5f,"dB",s->s.makeup,(s,v)->s.makeup=v,false,true);
        LinearLayout compDetail=column();compDetail.setVisibility(View.GONE);Button compOpen=button("Ajustar dinámica",false);compOpen.setOnClickListener(v->reveal(compDetail,compDetail.getVisibility()!=View.VISIBLE));dynamics.addView(compOpen,fullButton());dynamics.addView(compDetail);
        control(compDetail,"Umbral",-45,0,1,"dB",s->s.threshold,(s,v)->s.threshold=v,false,true);
        control(compDetail,"Ratio",1,12,.5f,":1",s->s.ratio,(s,v)->s.ratio=v,false,true);
        control(compDetail,"Ataque",1,150,1,"ms",s->s.attack,(s,v)->s.attack=v,false,true);
        control(compDetail,"Release",30,1000,10,"ms",s->s.release,(s,v)->s.release=v,false,true);
        control(compDetail,"Rodilla",0,40,1,"dB",s->s.knee,(s,v)->s.knee=v,false,true);

        Button spotify=button("Abrir Spotify  ↗",true);spotify.setOnClickListener(v->{Intent launch=getPackageManager().getLaunchIntentForPackage("com.spotify.music");if(launch!=null){try{startActivity(launch);}catch(RuntimeException e){toast("Abrí Spotify desde su icono.");}}else toast("Abrí Spotify desde su icono.");});page.addView(spotify,fullButton());
        Button about=button("Acerca del sonido",false);about.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Sanavera EQ Studio")
            .setMessage("Simple ajusta graves a 37 Hz, medios a 1 kHz y agudos a 10 kHz. Avanzado permite editar esas mismas tres ganancias y sumar diez bandas ajustables. La curva es una aproximación sobre 32 regiones de DynamicsProcessing; un filtro muy angosto puede variar respecto a Web Audio.\n\nSURROUND aplica tu receta interna sin mover las bandas visibles. El EQ de Simple y Laboratorio se suma encima. Apagarlo recupera ganancia, color y dinámica anteriores y conserva tus cambios de EQ. Su motor es DP global.\n\nApertura L/R usa curvas complementarias suaves en los dos canales. No es una matriz Mid/Side ni promete ensanchar cualquier grabación. A cero vuelve a la curva original.\n\nEspectro muestra la mezcla mono que permite Android: no inventa medidores L/R. Necesita el permiso de audio del sistema, pero no se usa AudioRecord, no se graba el micrófono ni se guardan muestras. Se detiene al salir de la pantalla.\n\nRESET deja todo neutro. No hay limiter automático; la suma de realces puede saturar. Probá con el volumen moderado.")
            .setPositiveButton("Listo",null).show());laboratory.addView(about,fullButton());
        LinearLayout settings=card(laboratory);Button settingsOpen=button("Conexión y diagnóstico",false);settings.addView(settingsOpen,fullButton());LinearLayout detail=column();detail.setVisibility(View.GONE);settings.addView(detail);
        settingsOpen.setOnClickListener(v->{showDiagnostic=!showDiagnostic;lastDiagnostic=0;reveal(detail,showDiagnostic);sync();});
        engine=new Spinner(this);engine.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"DynamicsProcessing","Equalizer clásico · sólo EQ"}));engine.setContentDescription("Motor de procesamiento");detail.addView(engine,new LinearLayout.LayoutParams(-1,dp(52)));
        engine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!syncing&&service!=null)service.setModern(position==0);}public void onNothingSelected(AdapterView<?> p){}});
        mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Automático · sesión o global","Sólo salida global"}));mode.setContentDescription("Método de conexión");detail.addView(mode,new LinearLayout.LayoutParams(-1,dp(52)));
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!syncing&&service!=null)service.setGlobalOnly(position==1);}public void onNothingSelected(AdapterView<?> p){}});
        Button reconnect=button("Reconectar motor",false);reconnect.setOnClickListener(v->{if(service!=null)service.reconnect();});detail.addView(reconnect,fullButton());
        Button copy=button("Copiar diagnóstico",false);copy.setOnClickListener(v->{if(service!=null){getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Sanavera EQ",service.diagnostics()));toast("Diagnóstico copiado");}});detail.addView(copy,fullButton());
        diagnostic=label("",10,MUTED,false);diagnostic.setTextIsSelectable(true);detail.addView(diagnostic);
        TextView footer=label("SANAVERA  ·  HECHO PARA ESCUCHAR",10,MUTED,true);footer.setLetterSpacing(.12f);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(18),0,0);page.addView(footer);
        showAdvanced(advanced,false);
        setContentView(root);
        if(bundle!=null&&bundle.getInt("scroll",0)>0){
            final int savedScroll=bundle.getInt("scroll",0);
            scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
                @Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                    if(b<=t)return;
                    scroll.removeOnLayoutChangeListener(this);scroll.scrollTo(0,savedScroll);
                }
            });
        }
    }

    private void section(LinearLayout parent,String title){TextView v=label(title,10,MUTED,true);v.setLetterSpacing(.12f);v.setPadding(dp(2),dp(20),0,dp(3));parent.addView(v);}
    private void reveal(View view,boolean show){view.animate().cancel();view.setVisibility(show?View.VISIBLE:View.GONE);if(show){view.setAlpha(0);view.setTranslationY(dp(5));view.animate().alpha(1).translationY(0).setDuration(160).start();}}
    private void showAdvanced(boolean value,boolean animate){
        advanced=value;getPreferences(MODE_PRIVATE).edit().putBoolean("advanced",value).apply();
        ((View)simpleBox.getParent()).setVisibility(value?View.GONE:View.VISIBLE);laboratory.setVisibility(value?View.VISIBLE:View.GONE);
        deviceHero.setVisibility(value?View.GONE:View.VISIBLE);
        selected(simpleTab,!value);selected(advancedTab,value);
        simpleSettingsButton.setVisibility(value?View.GONE:View.VISIBLE);
        if(animate)reveal(value?laboratory:simpleBox,true);syncEditor();syncAnalyzer();
    }
    private Button editorRow(LinearLayout box,String title,int kind){
        LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);TextView text=label(title,12,INK,false);r.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        Button b=button("",false);b.setTextSize(12);b.setOnClickListener(v->numericBand(kind));r.addView(b,new LinearLayout.LayoutParams(dp(106),dp(40)));box.addView(r);return b;
    }
    private SeekBar editorSlider(LinearLayout box,int maximum,int kind){
        SeekBar slider=new SeekBar(this);slider.setMax(maximum);slider.setThumbTintList(ColorStateList.valueOf(MINT));slider.setProgressTintList(ColorStateList.valueOf(MINT));
        slider.setContentDescription(kind==0?"Frecuencia de la banda":kind==1?"Ganancia de la banda":"Q de la banda");box.addView(slider,new LinearLayout.LayoutParams(-1,dp(42)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean user){if(user&&!syncing&&service!=null){float value=kind==0?Math.round(EqCoordinates.frequency(progress/1000f)):kind==1?progress/4f-12:.4f+progress*.05f;changeBand(kind,value);}}
            public void onStartTrackingTouch(SeekBar b){b.getParent().requestDisallowInterceptTouchEvent(true);}
            public void onStopTrackingTouch(SeekBar b){b.getParent().requestDisallowInterceptTouchEvent(false);}
        });return slider;
    }
    private void changeBand(int kind,float value){
        if(service==null)return;SoundState s=service.sound();
        if(kind==0)s.frequencies[selectedBand]=value;else if(kind==1)s.bands[selectedBand]=value;else s.bandQs[selectedBand]=value;
        s.bandOn[selectedBand]=true;service.edit(s,true);
    }
    private void numericBand(int kind){
        if(service==null)return;final int band=selectedBand;SoundState s=service.sound();
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(INK);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(kind==0?Integer.toString(Math.round(s.frequencies[band])):Float.toString(kind==1?s.bands[band]:s.bandQ(band)));input.selectAll();
        LinearLayout box=column();box.setPadding(dp(20),dp(10),dp(20),0);box.addView(input);box.addView(label(kind==0?"20 – 20.000 Hz":kind==1?"−12 a +12 dB":"0,4 a 3 · menor Q = curva más ancha",12,MUTED,false));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(kind==0?"Frecuencia exacta":kind==1?"Ganancia exacta":"Q de la campana").setView(box).setPositiveButton("Aplicar",null).setNegativeButton("Cancelar",null).create();
        d.setOnShowListener(dialog->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                float value=Float.parseFloat(input.getText().toString().trim().replace(',','.'));
                float min=kind==0?20:kind==1?-12:.4f,max=kind==0?20000:kind==1?12:3;
                if(!Float.isFinite(value)||value<min||value>max){input.setError("Ingresá un valor dentro del rango");return;}
                if(service!=null){SoundState now=service.sound();if(kind==0)now.frequencies[band]=value;else if(kind==1)now.bands[band]=value;else now.bandQs[band]=value;now.bandOn[band]=true;service.edit(now,true);}
                d.dismiss();
            }catch(NumberFormatException e){input.setError("Ingresá un número");}
        }));d.show();
    }
    private void syncEditor(){
        if(service==null||bandChoice==null||frequencySlider==null)return;
        boolean previous=syncing;syncing=true;
        try{
            SoundState s=service.sound();curveView.select(selectedBand);curveView.values(s);
            String signature=Arrays.toString(s.frequencies)+Arrays.toString(s.bandOn);
            if(!signature.equals(choiceSignature)){
                choiceSignature=signature;for(int i=0;i<10;i++)bandNames[i]="Banda "+(i+1)+" · "+EqCoordinates.label(s.frequencies[i])+" Hz"+(s.bandOn[i]?"":" · OFF");bandAdapter.notifyDataSetChanged();
            }
            if(bandChoice.getSelectedItemPosition()!=selectedBand)bandChoice.setSelection(selectedBand);
            int type=s.types[selectedBand]==-1?1:s.types[selectedBand]==1?2:0;if(bandType.getSelectedItemPosition()!=type)bandType.setSelection(type);
            bandEnabled.setChecked(s.bandOn[selectedBand]);
            frequencyValue.setText(Integer.toString(Math.round(s.frequencies[selectedBand]))+" Hz");gainValue.setText(format(s.bands[selectedBand],"dB"));qValue.setText(format(s.bandQ(selectedBand),""));
            frequencySlider.setProgress(Math.round(EqCoordinates.fraction(s.frequencies[selectedBand])*1000));gainSlider.setProgress(Math.round((s.bands[selectedBand]+12)*4));qSlider.setProgress(Math.round((s.bandQ(selectedBand)-.4f)/.05f));
            qSlider.setEnabled(type==0);qValue.setEnabled(type==0);qValue.setAlpha(type==0?1:.4f);
            bandCaption.setText(type==0?"Q individual · curva de tu EQ, sin la receta interna de SURROUND":"Shelf de pendiente fija · Q sólo modifica campanas");
        }finally{syncing=previous;}
    }
    private void toggleAnalyzer(){
        if(analyzerWanted){analyzerWanted=false;getPreferences(MODE_PRIVATE).edit().putBoolean("analyzer",false).apply();syncAnalyzer();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){analyzerWanted=true;spectrum.retry();getPreferences(MODE_PRIVATE).edit().putBoolean("analyzer",true).apply();syncAnalyzer();return;}
        new AlertDialog.Builder(this).setTitle("Espectro de tu música")
            .setMessage("Android pide permiso de audio para el visualizador. Sanavera sólo lee la señal de reproducción que ofrece el sistema: no graba el micrófono, no guarda muestras y no envía audio. El EQ funciona igual si preferís no activarlo.")
            .setPositiveButton("Continuar",(d,w)->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},21)).setNegativeButton("Ahora no",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] result){
        super.onRequestPermissionsResult(code,permissions,result);
        if(code==21){analyzerWanted=result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED;getPreferences(MODE_PRIVATE).edit().putBoolean("analyzer",analyzerWanted).apply();spectrum.retry();syncAnalyzer();}
    }
    private void syncAnalyzer(){
        if(spectrum==null)return;
        boolean permission=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        selected(analyzerButton,analyzerWanted&&permission);
        boolean visible=resumed&&spectrum.isShown()&&spectrum.getGlobalVisibleRect(spectrumBounds)&&spectrumBounds.height()>dp(24);
        boolean playing=false;try{AudioManager a=getSystemService(AudioManager.class);playing=a!=null&&a.isMusicActive();}catch(RuntimeException ignored){}
        int id=service==null?-1:service.visualSession();
        if(analyzerWanted&&permission&&visible&&playing&&id>=0)spectrum.start(id);
        else{
            if(spectrum.running())spectrum.stop();
            if(!analyzerWanted||!permission)analyzerStatus.setText("Activá Espectro para ver tu música");
            else if(!playing)analyzerStatus.setText("Espectro en pausa · reproducí una canción");
            else if(id<0)analyzerStatus.setText("Activá el EQ para conectar el espectro");
        }
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(()->syncAnalyzer());}
    @Override protected void onPause(){resumed=false;if(spectrum!=null)spectrum.stop();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("band",selectedBand);state.putBoolean("advanced",advanced);if(mainScroll!=null)state.putInt("scroll",mainScroll.getScrollY());super.onSaveInstanceState(state);}

    private void preset(String name) {
        if(service==null)return;
        
        service.preset(name);
        if(!"reset".equals(name)&&!service.isEnabled())enable();
    }
    private void editSimple(int id){
        if(service==null)return;
        final boolean creating=id<0;
        if(creating?service.sound().simpleCount()>=SoundState.SIMPLE_MAX:!service.sound().hasSimple(id))return;
        if(simpleDialog!=null)simpleDialog.dismiss();
        final float[] draft={creating?1000:service.sound().simpleFrequency(id)};
        final boolean[] updating={false},valid={true};
        LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(4));
        TextView hz=label(SimpleEqView.frequency(draft[0],true),22,colors.value,true);hz.setGravity(Gravity.CENTER);box.addView(hz);
        SeekBar slider=new SeekBar(this);slider.setMax(1000);slider.setThumbTintList(ColorStateList.valueOf(MINT));slider.setProgressTintList(ColorStateList.valueOf(MINT));slider.setContentDescription("Frecuencia");box.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(INK);input.setHint("Hz");input.setContentDescription("Frecuencia en Hz");input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});box.addView(input);
        Consumer<Float> render=value->{updating[0]=true;draft[0]=value;hz.setText(SimpleEqView.frequency(value,true));slider.setProgress(Math.round(EqCoordinates.fraction(value)*1000));input.setText(Float.toString(value).replaceAll("\\.0$",""));input.setSelection(input.length());input.setError(null);valid[0]=true;updating[0]=false;};
        Consumer<Float> apply=value->{if(!creating&&service!=null){SoundState now=service.sound();if(now.hasSimple(id)){now.simpleFrequency(id,value);service.edit(now,true);}}};
        render.accept(draft[0]);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(creating?"Agregar banda":id<3?new String[]{"Graves","Medios","Agudos"}[id]:"Frecuencia").setView(box).setPositiveButton(creating?"Agregar":"Listo",null).setNegativeButton(creating?"Cancelar":"Cerrar",null);
        if(!creating)builder.setNeutralButton(id<3?"Restaurar":"Eliminar",null);
        final AlertDialog dialog=builder.create();simpleDialog=dialog;
        dialog.setOnDismissListener(v->{if(simpleDialog==dialog)simpleDialog=null;});
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user&&!updating[0]){float value=Math.round(EqCoordinates.frequency(progress/1000f));render.accept(value);apply.accept(value);if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
        input.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(android.text.Editable text){
                if(updating[0])return;
                try{float value=Float.parseFloat(text.toString().replace(',','.'));valid[0]=Float.isFinite(value)&&value>=20&&value<=20000;
                    if(valid[0]){draft[0]=value;updating[0]=true;slider.setProgress(Math.round(EqCoordinates.fraction(value)*1000));hz.setText(SimpleEqView.frequency(value,true));updating[0]=false;input.setError(null);apply.accept(value);}
                }catch(NumberFormatException e){valid[0]=false;}
                if(!valid[0]&&text.length()>0)input.setError("20 – 20.000 Hz");
                if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!creating||valid[0]);
            }
        });
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                if(creating){if(service==null||!valid[0])return;SoundState now=service.sound();if(!now.addSimple(draft[0])){dialog.dismiss();return;}service.edit(now,true);}dialog.dismiss();
            });
            if(!creating)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button->{
                if(service==null)return;SoundState now=service.sound();if(!now.hasSimple(id)){dialog.dismiss();return;}
                if(id<3){float value=SoundState.SIMPLE_DEFAULTS[id];now.simpleFrequency(id,value);service.edit(now,true);render.accept(value);}
                else{now.removeSimple(id);service.edit(now,true);dialog.dismiss();}
            });
        });dialog.show();
    }
    private void simpleSettings(){
        if(service==null)return;
        new AlertDialog.Builder(this).setTitle("Simple").setItems(new String[]{"Flat","Eliminar bandas agregadas","Restablecer todo"},(dialog,which)->{
            if(service==null)return;
            if(which==2){new AlertDialog.Builder(this).setTitle("¿Restablecer el modo Simple?").setNegativeButton("Cancelar",null).setPositiveButton("Restablecer",(d,w)->{if(service!=null){SoundState s=service.sound();s.resetSimple();service.edit(s,true);}}).show();return;}
            SoundState s=service.sound();if(which==0)s.flatSimple();else s.simpleAdded.clear();service.edit(s,true);
        }).show();
    }
    private void syncDeviceArt(){
        String key=getPreferences(MODE_PRIVATE).getString("deviceArt5:"+deviceIdentity,deviceAutoKey);
        if(key.equals(artKey))return;artKey=key;
        int id=getResources().getIdentifier("device_"+key,"drawable",getPackageName());
        if(id==0)id=getResources().getIdentifier("device_generic","drawable",getPackageName());
        if(id!=0)deviceImage.setImageResource(id);
    }
    private void chooseDeviceArt(){
        String[] options=new String[DeviceCatalog.NAMES.length+2];options[0]="Reconocimiento automático";
        System.arraycopy(DeviceCatalog.NAMES,0,options,1,DeviceCatalog.NAMES.length);options[options.length-1]="Parlante genérico";
        new AlertDialog.Builder(this).setTitle("Imagen de este dispositivo").setItems(options,(d,i)->{
            android.content.SharedPreferences.Editor e=getPreferences(MODE_PRIVATE).edit();
            if(i==0)e.remove("deviceArt5:"+deviceIdentity);else e.putString("deviceArt5:"+deviceIdentity,i==options.length-1?"generic":DeviceCatalog.KEYS[i-1]);
            e.apply();artKey="";syncDeviceArt();
        }).setNegativeButton("Cerrar",null).show();
    }
    private void chooseBand() {
        if(service==null)return;
        String[] labels=new String[10];for(int i=0;i<10;i++)labels[i]=EqCoordinates.label(service.sound().frequencies[i])+" Hz · "+format(service.sound().bands[i],"dB");
        new AlertDialog.Builder(this).setTitle("Elegí una banda").setItems(labels,(d,i)->{selectedBand=i;if(advanced)syncEditor();else editBand(i);}).setNegativeButton("Cerrar",null).show();
    }
    private void editBand(int i) {
        if(service==null)return;
        LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView value=label(format(service.sound().bands[i],"dB"),20,colors.value,true);box.addView(value);
        SeekBar slider=new SeekBar(this);slider.setMax(48);slider.setProgress(Math.round((service.sound().bands[i]+12)*2));
        slider.setContentDescription("Ganancia de "+EqCoordinates.label(service.sound().frequencies[i])+" Hz");box.addView(slider,new LinearLayout.LayoutParams(-1,dp(54)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(user&&service!=null){SoundState s=service.sound();s.bands[i]=p/2f-12;s.bandOn[i]=true;service.edit(s,true);value.setText(format(s.bands[i],"dB"));}}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle(EqCoordinates.label(service.sound().frequencies[i])+" Hz").setView(box).setPositiveButton("Listo",null)
            .setNeutralButton("0 dB",(d,w)->{if(service!=null){SoundState s=service.sound();s.bands[i]=0;service.edit(s,true);}}).show();
    }
    private void showSounds() {
        if(service==null)return;
        List<SoundState> saved=service.savedSounds();
        if(saved.isEmpty()) {new AlertDialog.Builder(this).setTitle("Mis sonidos").setMessage("Ajustá el sonido y tocá Guardar. Podés conservar hasta 30 configuraciones.").setPositiveButton("Guardar este sonido",(d,w)->saveMenu()).setNegativeButton("Cerrar",null).show();return;}
        String[] names=new String[saved.size()];for(int i=0;i<names.length;i++)names[i]=saved.get(i).savedName;
        new AlertDialog.Builder(this).setTitle("Mis sonidos").setItems(names,(d,i)->{if(service!=null){service.loadSound(saved.get(i));if(!service.isEnabled())enable();}})
            .setPositiveButton("Guardar actual",(d,w)->saveMenu()).setNeutralButton("Eliminar…",(d,w)->deleteSounds(saved)).setNegativeButton("Cerrar",null).show();
    }
    private void saveMenu() {
        if(service==null)return;
        SoundState s=service.sound();
        if(s.savedId.isEmpty()){saveNew();return;}
        new AlertDialog.Builder(this).setTitle(s.savedName+(s.modified?" · modificado":""))
            .setItems(new String[]{"Actualizar "+s.savedName,"Guardar como nuevo"},(d,i)->{
                if(i==1)saveNew();else if(service!=null){if(service.saveSound(s.savedName,true))toast("Sonido actualizado");else toast("No se pudo guardar");}
            }).setNegativeButton("Cancelar",null).show();
    }
    private void saveNew() {
        if(service==null)return;
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Nombre del sonido");name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);box.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Guardar como nuevo").setView(box).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(service==null){dialog.dismiss();return;}
            if(name.getText().toString().trim().isEmpty()){name.setError("Escribí un nombre");return;}
            if(service.saveSound(name.getText().toString(),false)){dialog.dismiss();toast("Sonido guardado");}
            else name.setError("Límite de 30 sonidos. Eliminá uno o actualizá uno existente.");
        }));dialog.show();
    }
    private void deleteSounds(List<SoundState> saved) {
        String[] names=new String[saved.size()];for(int i=0;i<names.length;i++)names[i]=saved.get(i).savedName;
        new AlertDialog.Builder(this).setTitle("Eliminar un sonido").setItems(names,(d,i)->new AlertDialog.Builder(this)
            .setTitle("¿Eliminar "+saved.get(i).savedName+"?").setMessage("El sonido actual no se modifica.")
            .setPositiveButton("Eliminar",(dd,w)->{if(service!=null)service.deleteSound(saved.get(i).savedId);})
            .setNegativeButton("Cancelar",null).show()).setNegativeButton("Cerrar",null).show();
    }

    private final class Control {
        final SeekBar slider;final TextView value,name;final float min,step;final String unit;final ToDoubleFunction<SoundState> getter;final boolean modernOnly;
        Control(SeekBar slider,TextView value,TextView name,float min,float step,String unit,ToDoubleFunction<SoundState> getter,boolean modernOnly){this.slider=slider;this.value=value;this.name=name;this.min=min;this.step=step;this.unit=unit;this.getter=getter;this.modernOnly=modernOnly;}
        void sync(SoundState s){float v=(float)getter.applyAsDouble(s);slider.setProgress(Math.round((v-min)/step));value.setText(format(v,unit));slider.setEnabled(service!=null&&(!modernOnly||service.isModern()));}
    }
    private void control(LinearLayout parent,String title,float min,float max,float step,String unit,ToDoubleFunction<SoundState> getter,BiConsumer<SoundState,Float> setter,boolean tonal,boolean modernOnly) {
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(title,13,INK,false),value=label("",12,colors.value,true);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(value);LinearLayout.LayoutParams gap=wrap();gap.topMargin=dp(10);parent.addView(row,gap);
        SeekBar slider=new SeekBar(this);slider.setMax(Math.round((max-min)/step));slider.setProgressTintList(ColorStateList.valueOf(MINT));slider.setThumbTintList(ColorStateList.valueOf(MINT));slider.setContentDescription(title);
        parent.addView(slider,new LinearLayout.LayoutParams(-1,dp(42)));controls.add(new Control(slider,value,name,min,step,unit,getter,modernOnly));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(user&&!syncing&&service!=null){SoundState s=service.sound();setter.accept(s,min+p*step);if("Apertura espectral".equals(title))service.setSpread(s.spread);else service.edit(s,tonal);}}
            public void onStartTrackingTouch(SeekBar b){b.getParent().requestDisallowInterceptTouchEvent(true);}
            public void onStopTrackingTouch(SeekBar b){b.getParent().requestDisallowInterceptTouchEvent(false);}
        });
    }

    private void enable() {
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        try{startForegroundService(new Intent(this,EqService.class).setAction(EqService.ENABLE));}catch(RuntimeException e){toast("No pude iniciar el EQ: "+e.getClass().getSimpleName());}
    }
    private void readPanel(Intent intent) {
        if(intent==null||!AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL.equals(intent.getAction()))return;
        pendingSession=intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,-1);pendingOwner=intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);if(pendingOwner==null)pendingOwner=getCallingPackage();
    }
    private void deliverSession(){if(service!=null&&pendingSession>0){service.noteSession(pendingSession,pendingOwner);pendingSession=-1;}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readPanel(intent);deliverSession();}
    @Override protected void onStart(){super.onStart();bound=bindService(new Intent(this,EqService.class),connection,BIND_AUTO_CREATE);handler.post(refresh);if(deviceMonitor!=null)deviceMonitor.start();}
    @Override protected void onStop(){if(deviceMonitor!=null)deviceMonitor.stop();handler.removeCallbacks(refresh);if(service!=null)service.observe(null);if(bound){unbindService(connection);bound=false;}service=null;super.onStop();}
    @Override protected void onDestroy(){if(simpleDialog!=null)simpleDialog.dismiss();super.onDestroy();}

    private void sync() {
        if(power==null)return;power.setEnabled(service!=null);bands.setEnabled(service!=null);curveView.setEnabled(service!=null);
        if(service==null)return;
        syncing=true;
        try {
            SoundState s=service.sound();power.setText(service.isEnabled()?"EQ ON":"EQ OFF");selected(power,service.isEnabled());
            state.setText(service.headline());
            // The first two lines are enough during normal use; full native data stays in Diagnostics.
            status.setText(service.isEnabled()?(!service.isModern()?"Clásico · sólo aplica la ecualización":"DynamicsProcessing · sin compensación automática"):"Activá el EQ y reproducí música en Spotify.");
            soundTitle.setText(s.title());bands.values(s);curveView.values(s);syncEditor();virtual.setChecked(s.virtualizer);compressor.setChecked(s.compressor);compressor.setEnabled(service.isModern());
            virtualStatus.setText(service.virtualStatus());
            if(spatialMode.getSelectedItemPosition()!=(service.isHeadphoneMode()?0:1))spatialMode.setSelection(service.isHeadphoneMode()?0:1);
            surround.setChecked(s.surround);userEqSwitch.setChecked(s.eqOn);simpleEq.values(s);simpleEq.setEnabled(s.eqOn);simpleEq.setAlpha(s.eqOn?1:.4f);
            addSimpleButton.setEnabled(s.simpleCount()<SoundState.SIMPLE_MAX);addSimpleButton.setAlpha(addSimpleButton.isEnabled()?1:.35f);
            String[] simpleNames={"Graves","Medios","Agudos"};for(int i=0;i<3;i++)if(simpleAdvancedControls[i]!=null){String title=simpleNames[i]+" · "+SimpleEqView.frequency(s.simpleFrequencies[i],true);simpleAdvancedControls[i].name.setText(title);simpleAdvancedControls[i].slider.setContentDescription(title);}
            for(Map.Entry<String,Button> e:tones.entrySet())selected(e.getValue(),e.getKey().equals(s.tone));
            for(Control c:controls)c.sync(s);
            mode.setEnabled(!s.surround);engine.setEnabled(!s.surround);
            if(mode.getSelectedItemPosition()!=(service.isGlobalOnly()?1:0))mode.setSelection(service.isGlobalOnly()?1:0);
            if(engine.getSelectedItemPosition()!=(service.isModern()?0:1))engine.setSelection(service.isModern()?0:1);
            if(showDiagnostic && SystemClock.uptimeMillis()-lastDiagnostic>1800) {
                lastDiagnostic=SystemClock.uptimeMillis();diagnostic.setText(service.details()+"\n\n"+service.diagnostics());
            }
        } finally { syncing=false; }
        syncAnalyzer();
    }
    private void selected(Button b,boolean selected){if(b.isSelected()!=selected){b.setTextColor(selected?Color.WHITE:INK);b.setBackground(ripple(selected?MINT:CARD));b.setSelected(selected);}}
    private String format(float value,String unit){String number=Math.abs(value-Math.round(value))<.001?Integer.toString(Math.round(value)):String.format(Locale.ROOT,"%.2f",value).replaceAll("0$","");return (value>0&&"dB".equals(unit)?"+":"")+number+("%".equals(unit)||":1".equals(unit)?unit:" "+unit);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){return new LinearLayout(this);}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout card(LinearLayout parent){LinearLayout l=column();l.setPadding(dp(14),dp(12),dp(14),dp(14));l.setBackground(bg(CARD,20));LinearLayout.LayoutParams p=wrap();p.topMargin=dp(12);p.bottomMargin=dp(6);parent.addView(l,p);return l;}
    private LinearLayout.LayoutParams fullButton(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.topMargin=dp(7);p.bottomMargin=dp(5);return p;}
    private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(2),dp(6),dp(2),dp(5));return p;}
    private RippleDrawable ripple(int color){return new RippleDrawable(ColorStateList.valueOf(UiColors.RIPPLE),bg(color,14),bg(Color.WHITE,14));}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(color==CARD)d.setStroke(dp(1),LINE);return d;}
    private TextView label(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(3),0,dp(3));v.setLineSpacing(dp(2),1);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(ripple(primary?MINT:CARD));b.setPadding(dp(8),0,dp(8),0);b.setMinWidth(0);b.setMinimumWidth(0);return b;}
    private Switch toggle(String title){
        Switch v=new Switch(this);v.setText(title);v.setTextSize(17);v.setTextColor(INK);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setMinHeight(dp(48));v.setSplitTrack(false);
        android.graphics.drawable.StateListDrawable track=new android.graphics.drawable.StateListDrawable();
        GradientDrawable on=bg(MINT,16);on.setSize(dp(52),dp(28));
        GradientDrawable off=bg(0xff9da1a6,16);off.setSize(dp(52),dp(28));
        track.addState(new int[]{android.R.attr.state_checked},on);track.addState(new int[]{},off);
        GradientDrawable thumb=bg(Color.WHITE,12);thumb.setStroke(dp(1),0xffe5e6e8);thumb.setSize(dp(24),dp(24));
        v.setTrackTintList(null);v.setThumbTintList(null);v.setTrackDrawable(track);
        v.setThumbDrawable(new android.graphics.drawable.InsetDrawable(thumb,dp(2)));
        v.setThumbTextPadding(dp(9));v.setSwitchMinWidth(dp(52));return v;
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
}

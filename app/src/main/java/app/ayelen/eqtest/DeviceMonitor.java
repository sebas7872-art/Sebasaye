package app.ayelen.eqtest;
import android.content.*;
import android.media.*;
import android.os.*;

/** Connection identity only. Never reroutes music or restarts the effects. */
final class DeviceMonitor {
    interface Listener{void changed(String name,String kind,String identity);}
    private final Context context;private final AudioManager audio;private final MediaRouter router;
    private final Handler handler=new Handler(Looper.getMainLooper());private final Listener listener;
    private boolean started;private String signature="";
    private final AudioDeviceCallback callback=new AudioDeviceCallback(){
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d){refresh();}
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d){refresh();}
    };
    private final MediaRouter.SimpleCallback routeCallback=new MediaRouter.SimpleCallback(){
        @Override public void onRouteSelected(MediaRouter r,int type,MediaRouter.RouteInfo info){refresh();}
        @Override public void onRouteChanged(MediaRouter r,MediaRouter.RouteInfo info){refresh();}
    };
    DeviceMonitor(Context c,Listener l){context=c;listener=l;audio=c.getSystemService(AudioManager.class);router=c.getSystemService(MediaRouter.class);}
    void start(){if(started)return;started=true;try{if(audio!=null)audio.registerAudioDeviceCallback(callback,handler);}catch(RuntimeException ignored){}
        try{if(router!=null)router.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO,routeCallback);}catch(RuntimeException ignored){}refresh();}
    void stop(){if(!started)return;started=false;try{if(audio!=null)audio.unregisterAudioDeviceCallback(callback);}catch(RuntimeException ignored){}
        try{if(router!=null)router.removeCallback(routeCallback);}catch(RuntimeException ignored){}handler.removeCallbacksAndMessages(null);}
    void refresh(){
        String name="Este teléfono",kind="Altavoz integrado",id="phone";AudioDeviceInfo selected=null;int score=0;
        String routeName="";try{if(router!=null){MediaRouter.RouteInfo r=router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);if(r!=null&&r.getName()!=null)routeName=r.getName().toString();}}catch(RuntimeException ignored){}
        try{if(audio!=null)for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
            int type=d.getType();boolean bt=type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||type==AudioDeviceInfo.TYPE_BLE_HEADSET||type==AudioDeviceInfo.TYPE_BLE_SPEAKER;
            boolean wire=type==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||type==AudioDeviceInfo.TYPE_WIRED_HEADSET||type==AudioDeviceInfo.TYPE_USB_HEADSET||type==AudioDeviceInfo.TYPE_USB_DEVICE;
            if(!bt&&!wire)continue;
            int rank=bt?2:3;String n=String.valueOf(d.getProductName());if(!routeName.isEmpty()&&routeName.equals(n))rank+=10;
            if(rank>score){score=rank;selected=d;}
        }}catch(RuntimeException ignored){}
        if(selected!=null){
            name=String.valueOf(selected.getProductName()).trim();int type=selected.getType();
            boolean bt=type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||type==AudioDeviceInfo.TYPE_BLE_HEADSET||type==AudioDeviceInfo.TYPE_BLE_SPEAKER;
            kind=bt?"Bluetooth · conectado":"Audio por cable / USB";
            if(name.isEmpty()||"null".equals(name))name=bt?"Dispositivo Bluetooth":"Auriculares";
            String address="";try{address=selected.getAddress();}catch(RuntimeException ignored){}
            id=address==null||address.isEmpty()?type+":"+name:type+":"+address;
        }
        String next=name+"|"+kind+"|"+id;if(!signature.equals(next)){signature=next;listener.changed(name,kind,id);}
    }
}

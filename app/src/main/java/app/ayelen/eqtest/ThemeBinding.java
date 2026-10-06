package app.ayelen.eqtest;
import android.content.*;
import android.os.*;

/** Keeps the existing service bound across the UI-only theme recreation. */
final class ThemeBinding {
    private static Context owner;
    private static ServiceConnection connection;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static final Runnable expiry=ThemeBinding::release;
    static void hold(Context context){
        if(connection!=null)return;
        owner=context.getApplicationContext();
        connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){}
            public void onServiceDisconnected(ComponentName name){release();}
        };
        try{
            if(!owner.bindService(new Intent(owner,EqService.class),connection,Context.BIND_AUTO_CREATE)){owner=null;connection=null;return;}
            handler.postDelayed(expiry,5000);
        }catch(RuntimeException e){owner=null;connection=null;}
    }
    static void release(){
        handler.removeCallbacks(expiry);Context context=owner;ServiceConnection held=connection;owner=null;connection=null;
        if(context!=null&&held!=null)try{context.unbindService(held);}catch(RuntimeException ignored){}
    }
}

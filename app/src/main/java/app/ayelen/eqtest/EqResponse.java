package app.ayelen.eqtest;

/** Samples the requested biquad magnitude curve into DP frequency regions.
 * DP is an FFT equalizer; magnitude approximation is not Web Audio phase parity. */
final class EqResponse {
    static final int COUNT = 32;
    static final float[] CENTERS = new float[COUNT], UPPER = new float[COUNT];
    static {
        for (int i=0;i<COUNT;i++) CENTERS[i]=(float)(20*Math.pow(1000,i/(double)(COUNT-1)));
        for (int i=0;i<COUNT-1;i++) UPPER[i]=(float)Math.sqrt(CENTERS[i]*CENTERS[i+1]);
        UPPER[COUNT-1]=24000;
    }

    private static final SoundState SURROUND = SoundState.surround();

    static float userAt(SoundState s,double hz) {
        if(!s.eqOn)return 0;
        double gain=0;
        for (int i=0;i<10;i++) if (s.bandOn[i] && s.bands[i]!=0)
            gain+=response(hz,s.frequencies[i],s.bands[i],s.bandQ(i),s.types[i]);
        gain+=response(hz,s.simpleFrequencies[0],s.simple[0],.8,-1)+response(hz,s.simpleFrequencies[1],s.simple[1],.8,0)+response(hz,s.simpleFrequencies[2],s.simple[2],.8,1);
        for(SoundState.SimpleBand b:s.simpleAdded)if(b.gain!=0)gain+=response(hz,b.frequency,b.gain,.8,0);
        return (float)gain;
    }

    static float at(SoundState s,double hz) {
        double gain=userAt(s,hz);
        if(s.surround) for(int i=0;i<10;i++)
            gain+=response(hz,SURROUND.frequencies[i],SURROUND.bands[i],SURROUND.q,SURROUND.types[i]);
        if (s.presence!=0) gain+=response(hz,2800,s.presence,.8,0);
        if (s.air!=0) gain+=response(hz,10000,s.air,1,0);
        return (float)gain;
    }

    static float[] gains(SoundState s) {
        float[] out=new float[COUNT];
        for (int i=0;i<COUNT;i++) out[i]=at(s,CENTERS[i]);
        return out;
    }

    // RBJ magnitude response at a nominal 48 kHz. Shelves use slope 1 as Web Audio.
    static double response(double hz,double f,double db,double q,int type) {
        if (db==0) return 0;
        double a=Math.pow(10,db/40),w=2*Math.PI*f/48000,c=Math.cos(w),sin=Math.sin(w);
        double alpha=sin/(2*q),b0,b1,b2,a0,a1,a2;
        if (type==0) {
            b0=1+alpha*a;b1=-2*c;b2=1-alpha*a;
            a0=1+alpha/a;a1=-2*c;a2=1-alpha/a;
        } else {
            double beta=2*Math.sqrt(a)*sin/Math.sqrt(2);
            if (type<0) {
                b0=a*((a+1)-(a-1)*c+beta); b1=2*a*((a-1)-(a+1)*c); b2=a*((a+1)-(a-1)*c-beta);
                a0=(a+1)+(a-1)*c+beta; a1=-2*((a-1)+(a+1)*c); a2=(a+1)+(a-1)*c-beta;
            } else {
                b0=a*((a+1)+(a-1)*c+beta); b1=-2*a*((a-1)+(a+1)*c); b2=a*((a+1)+(a-1)*c-beta);
                a0=(a+1)-(a-1)*c+beta; a1=2*((a-1)-(a+1)*c); a2=(a+1)-(a-1)*c-beta;
            }
        }
        double omega=2*Math.PI*Math.min(23990,Math.max(1,hz))/48000;
        double cr=Math.cos(omega),ci=-Math.sin(omega),cr2=Math.cos(2*omega),ci2=-Math.sin(2*omega);
        double nr=b0+b1*cr+b2*cr2, ni=b1*ci+b2*ci2, dr=a0+a1*cr+a2*cr2, di=a1*ci+a2*ci2;
        return 10*Math.log10(Math.max(1e-30,(nr*nr+ni*ni)/(dr*dr+di*di)));
    }
}

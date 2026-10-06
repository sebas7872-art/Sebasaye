package app.ayelen.eqtest;

final class SpectrumMath {
    static void decode(byte[] fft,int sampleRate,float[] output) {
        int n=fft.length;
        for(int i=0;i<output.length;i++) {
            double a=60*Math.pow(16000.0/60,i/(double)output.length),b=60*Math.pow(16000.0/60,(i+1)/(double)output.length);
            int start=Math.max(1,Math.min(n/2-1,(int)(a*n/sampleRate)));
            int end=Math.max(start+1,Math.min(n/2,(int)Math.ceil(b*n/sampleRate)));
            double peak=0;
            for(int k=start;k<end;k++){double re=fft[2*k],im=fft[2*k+1];peak=Math.max(peak,Math.sqrt(re*re+im*im));}
            output[i]=(float)Math.max(0,Math.min(1,(20*Math.log10(Math.max(1,peak)/128)+42)/42));
        }
    }
}

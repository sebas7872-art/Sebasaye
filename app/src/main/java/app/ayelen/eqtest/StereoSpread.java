package app.ayelen.eqtest;

/** Gentle complementary spectral contrast. No matrix, delay or extra audio engine.
 * At a given frequency, amplitudes for identical L/R sum to 2 (mono-neutral
 * before any nonlinear downstream effects). This is not a Mid/Side width matrix.
 * Per-channel boost is bounded at +2.28 dB; user EQ can still clip independently. */
final class StereoSpread {
    static float offset(double hz,float amount,int channel) {
        if(amount<=0 || channel>1 || hz<=180 || hz>=12000) return 0;
        double low=Math.min(1,(hz-180)/300),high=Math.min(1,(12000-hz)/5000);
        double taper=Math.sin(low*Math.PI/2)*Math.sin(high*Math.PI/2);
        double delta=.30*Math.min(100,amount)/100*taper*Math.sin(2*Math.PI*Math.log(hz/350)/Math.log(2)/2.75);
        return (float)(20*Math.log10(1+(channel==0?delta:-delta)));
    }
}

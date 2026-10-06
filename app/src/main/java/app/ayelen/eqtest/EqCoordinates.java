package app.ayelen.eqtest;
final class EqCoordinates {
    static float fraction(float hz) { return (float)(Math.log(Math.max(20,Math.min(20000,hz))/20)/Math.log(1000)); }
    static float frequency(float fraction) { return (float)(20*Math.pow(1000,Math.max(0,Math.min(1,fraction)))); }
    static String label(float hz) {
        if(hz>=1000) return String.format(java.util.Locale.ROOT,"%.1fk",hz/1000).replace(".0k","k");
        return Integer.toString(Math.round(hz));
    }
}

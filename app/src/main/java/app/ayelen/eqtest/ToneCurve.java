package app.ayelen.eqtest;

final class ToneCurve {
    private ToneCurve() {}
    static int milliBels(int frequencyMilliHz, int bass, int mids, int treble, int min, int max) {
        double hz = Math.max(1, frequencyMilliHz / 1000.0);
        double position = Math.log10(hz / 100.0);
        double db = position <= 0 ? bass : position >= 2 ? treble
            : position < 1 ? bass + (mids - bass) * position
            : mids + (treble - mids) * (position - 1);
        return Math.max(min, Math.min(max, (int) Math.round(db * 100)));
    }
}

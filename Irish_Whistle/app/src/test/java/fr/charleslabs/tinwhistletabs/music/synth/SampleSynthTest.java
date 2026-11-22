package fr.charleslabs.tinwhistletabs.music.synth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public class SampleSynthTest {
    private static final int SR = 22050;
    private static SampleSynth synth;

    @BeforeClass
    public static void load() throws Exception {
        try (InputStream in = new BufferedInputStream(
                new FileInputStream("src/main/assets/instruments/piccolo.bin"))) {
            synth = new SampleSynth(in);
        }
    }

    /** Pitch by autocorrelation around the expected period, with parabolic interpolation. */
    private static double pitch(float[] x, int from, int to, double expected) {
        final int p0 = (int) (SR / expected);
        double best = -1;
        int lag = 0;
        final double[] ac = new double[2 * p0 + 2];
        for (int l = p0 / 2; l <= 2 * p0; l++) {
            double s = 0;
            for (int i = from; i + l < to; i++) s += x[i] * x[i + l];
            ac[l] = s;
            if (s > best) { best = s; lag = l; }
        }
        final double d = 0.5 * (ac[lag - 1] - ac[lag + 1]) / (ac[lag - 1] - 2 * ac[lag] + ac[lag + 1]);
        return SR / (lag + d);
    }

    @Test
    public void everyNoteIsInTune() {
        // D whistle range (MIDI 74-98) and transposed whistles (low D to high G)
        for (int midi = 62; midi <= 103; midi++) {
            final double f = 440 * Math.pow(2, (midi - 69) / 12.0);
            final int n = SR / 2;
            final float[] out = synth.render(new float[]{(float) f}, new int[]{n}, SR);
            assertEquals(n, out.length);
            final double got = pitch(out, n / 4, n * 3 / 4, f);
            final double cents = 1200 * Math.log(got / f) / Math.log(2);
            assertTrue("MIDI " + midi + ": " + Math.round(cents) + " cents", Math.abs(cents) < 25);
        }
    }

    @Test
    public void melodyHasTheExactLengthAndNoClipping() {
        final float[] f = {587.33f, 0, 659.26f, 739.99f, 783.99f};
        final int[] l = {5000, 2000, 3000, 30000, 100};
        final float[] out = synth.render(f, l, SR);
        assertEquals(40100, out.length);
        float max = 0;
        for (float v : out) max = Math.max(max, Math.abs(v));
        assertTrue(max > 0.5f && max <= 0.86f);
    }
}

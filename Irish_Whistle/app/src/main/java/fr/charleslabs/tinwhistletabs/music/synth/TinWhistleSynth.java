package fr.charleslabs.tinwhistletabs.music.synth;

import java.util.Random;

/**
 * Tin whistle synthesiser.
 *
 * A tin whistle is a fipple flute: an almost pure tone (strong fundamental, weak harmonics)
 * mixed with breath noise, a short noisy "chiff" when a note starts, notes played legato
 * in one breath with light tonguing between them, and a slow, light pitch vibrato on long
 * notes only.
 *
 * Pure Java (no Android dependency) so it can be tested and listened to on a computer.
 */
public final class TinWhistleSynth {
    // Tone: relative amplitudes of harmonics 1, 2, 3...
    private static final float[] HARMONICS = {1f, 0.22f, 0.09f, 0.05f, 0.02f};
    private static final float TONE_AMP = 0.5f;

    // Breath: noise filtered around the note, plus a little broadband air
    private static final float BREATH_TONAL_AMP = 0.10f, BREATH_Q = 6f;
    private static final float AIR_AMP = 0.022f, AIR_CUTOFF = 4000f;

    // Articulation (seconds)
    private static final float ATTACK = 0.025f, TONGUE = 0.018f, RELEASE = 0.045f;
    private static final float TONGUE_LEVEL = 0.35f; // level between two tongued notes
    private static final float CHIFF_TIME = 0.035f, CHIFF_AMP = 0.35f;

    // Pitch vibrato on long notes
    private static final float VIBRATO_FREQ = 5.2f, VIBRATO_DEPTH = 0.0035f; // ~6 cents
    private static final float VIBRATO_DELAY = 0.35f, VIBRATO_FADE_IN = 0.4f;

    // Room
    private static final float[][] REVERB = {{0.031f, 0.18f}, {0.047f, 0.14f}, {0.071f, 0.10f},
            {0.113f, 0.07f}, {0.167f, 0.05f}};

    private static final float PEAK = 0.85f;

    private TinWhistleSynth() {}

    /**
     * Render a melody.
     *
     * @param frequencies note frequencies in Hz, 0 for a rest
     * @param lengths     note lengths in samples (the result has exactly their sum)
     */
    public static float[] render(float[] frequencies, int[] lengths, int sampleRate) {
        int total = 0;
        for (int l : lengths) total += l;
        final float[] out = new float[total];
        final Random random = new Random(42); // same sound at each generation

        double phase = 0;
        int offset = 0;
        for (int i = 0; i < frequencies.length; i++) {
            final float f = frequencies[i];
            final int n = lengths[i];
            if (f > 0 && n > 0) {
                final boolean afterNote = i > 0 && frequencies[i - 1] > 0;
                final boolean beforeNote = i + 1 < frequencies.length && frequencies[i + 1] > 0;
                phase = note(out, offset, n, f, sampleRate, afterNote, beforeNote, phase, random);
            }
            offset += n;
        }

        reverb(out, sampleRate);
        normalize(out);
        return out;
    }

    /** Render one note; returns the oscillator phase so that legato notes join smoothly. */
    private static double note(float[] out, int offset, int n, float f, int sr,
                               boolean afterNote, boolean beforeNote, double phase, Random random) {
        final int attack = Math.min((int) (ATTACK * sr), n / 3);
        final int end = Math.min((int) ((beforeNote ? TONGUE : RELEASE) * sr), n / 3);
        final float startLevel = afterNote ? TONGUE_LEVEL : 0f;
        final float endLevel = beforeNote ? TONGUE_LEVEL : 0f;
        final int chiff = (int) (CHIFF_TIME * sr);
        final float duration = (float) n / sr;
        final boolean vibrato = duration > VIBRATO_DELAY + 0.15f;

        // Harmonics above ~10 kHz are useless (and alias at 22 kHz); high notes are purer
        final float brightness = f > 1100f ? 0.5f : 1f;

        // Band-pass filter (RBJ biquad) centred on the note, for the breath noise
        final double w0 = 2 * Math.PI * f / sr;
        final double alpha = Math.sin(w0) / (2 * BREATH_Q);
        final double a0 = 1 + alpha;
        final double b0 = alpha / a0, b2 = -alpha / a0;
        final double a1 = -2 * Math.cos(w0) / a0, a2 = (1 - alpha) / a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        // One-pole low-pass for the broadband air
        final double lp = Math.exp(-2 * Math.PI * AIR_CUTOFF / sr);
        double air = 0;

        for (int i = 0; i < n && offset + i < out.length; i++) {
            final float t = (float) i / sr;

            // Amplitude envelope: smooth attack, flat sustain (blown note), tonguing/release
            float env = 1f;
            if (i < attack) env = startLevel + (1f - startLevel) * smooth((float) i / attack);
            final int fromEnd = n - 1 - i;
            if (fromEnd < end) env *= endLevel + (1f - endLevel) * smooth((float) fromEnd / end);

            // Pitch with delayed vibrato
            double freq = f;
            if (vibrato && t > VIBRATO_DELAY) {
                final float depth = VIBRATO_DEPTH * Math.min(1f, (t - VIBRATO_DELAY) / VIBRATO_FADE_IN);
                freq *= 1 + depth * Math.sin(2 * Math.PI * VIBRATO_FREQ * (t - VIBRATO_DELAY));
            }
            phase += 2 * Math.PI * freq / sr;
            if (phase > 2 * Math.PI * 1000) phase -= 2 * Math.PI * 1000;

            double tone = 0;
            for (int h = 0; h < HARMONICS.length; h++) {
                if (f * (h + 1) > 10000) break;
                tone += (h == 0 ? 1 : brightness) * HARMONICS[h] * Math.sin((h + 1) * phase);
            }

            // Breath noise, stronger during the chiff at the start of the note
            final double white = random.nextDouble() * 2 - 1;
            final double band = b0 * white + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = white; y2 = y1; y1 = band;
            air = (1 - lp) * white + lp * air;
            double noiseAmp = BREATH_TONAL_AMP;
            if (i < chiff && !afterNote) noiseAmp += CHIFF_AMP * (1 - (float) i / chiff);
            else if (i < chiff) noiseAmp += CHIFF_AMP * 0.3f * (1 - (float) i / chiff);

            out[offset + i] += (float) (env * (TONE_AMP * tone + noiseAmp * band + AIR_AMP * air * 4));
        }
        return phase;
    }

    /** Smooth step from 0 to 1. */
    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    /** Small room: a few decaying echoes. */
    private static void reverb(float[] music, int sampleRate) {
        final float[] dry = music.clone();
        for (float[] tap : REVERB) {
            final int delay = (int) (tap[0] * sampleRate);
            for (int i = delay; i < music.length; i++) music[i] += tap[1] * dry[i - delay];
        }
    }

    private static void normalize(float[] music) {
        float max = 0;
        for (float v : music) max = Math.max(max, Math.abs(v));
        if (max <= 0) return;
        final float gain = PEAK / max;
        for (int i = 0; i < music.length; i++) music[i] *= gain;
    }
}

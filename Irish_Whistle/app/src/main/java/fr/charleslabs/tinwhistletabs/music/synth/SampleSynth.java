package fr.charleslabs.tinwhistletabs.music.synth;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sampler: plays real instrument recordings, transposed to each note and looped to hold
 * long notes. The bank (assets/instruments/piccolo.bin) holds the piccolo of the FluidR3_GM
 * SoundFont (MIT licence, see assets/licenses/FluidR3_GM.txt), exported by
 * database_generator/export_samples.py with the measured pitch of each recording.
 *
 * Bank format (big endian): "TWSB", version, count, then for each sample: lowest key,
 * highest key (MIDI), pitch (float, MIDI note), loop start, loop end, sample rate, length,
 * and the 16-bit samples (up to the end of the loop).
 *
 * Pure Java (no Android dependency) so it can be unit tested.
 */
public final class SampleSynth {
    private static final float LEGATO_RELEASE = 0.012f, FINAL_RELEASE = 0.05f; // seconds
    private static final float LEGATO_ATTACK = 0.010f, LEGATO_START_LEVEL = 0.3f;
    private static final float[][] REVERB = {{0.031f, 0.18f}, {0.047f, 0.14f}, {0.071f, 0.10f},
            {0.113f, 0.07f}, {0.167f, 0.05f}};
    private static final float PEAK = 0.85f;

    private static final class Sample {
        int low, high, loopStart, loopEnd, rate;
        float pitch;
        float[] data;
    }

    private final List<Sample> samples = new ArrayList<>();

    public SampleSynth(InputStream bank) throws IOException {
        final DataInputStream in = new DataInputStream(bank);
        final byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'T' || magic[1] != 'W' || magic[2] != 'S' || magic[3] != 'B')
            throw new IOException("Not a sample bank");
        if (in.readInt() != 1) throw new IOException("Unsupported sample bank version");
        final int count = in.readInt();
        for (int i = 0; i < count; i++) {
            final Sample s = new Sample();
            s.low = in.readInt();
            s.high = in.readInt();
            s.pitch = in.readFloat();
            s.loopStart = in.readInt();
            s.loopEnd = in.readInt();
            s.rate = in.readInt();
            final int length = in.readInt();
            s.data = new float[length];
            for (int k = 0; k < length; k++) s.data[k] = in.readShort() / 32768f;
            if (s.loopStart < 0 || s.loopEnd > length || s.loopEnd - s.loopStart < 2)
                throw new IOException("Invalid loop in sample bank");
            samples.add(s);
        }
        if (samples.isEmpty()) throw new IOException("Empty sample bank");
    }

    private Sample sampleFor(double midi) {
        final int key = (int) Math.round(midi);
        Sample best = samples.get(0);
        for (Sample s : samples) {
            if (key >= s.low && key <= s.high) return s;
            if (Math.abs(s.pitch - midi) < Math.abs(best.pitch - midi)) best = s;
        }
        return best;
    }

    /**
     * Render a melody.
     *
     * @param frequencies note frequencies in Hz, 0 for a rest
     * @param lengths     note lengths in samples (the result has exactly their sum)
     */
    public float[] render(float[] frequencies, int[] lengths, int sampleRate) {
        int total = 0;
        for (int l : lengths) total += l;
        final float[] out = new float[total];

        int offset = 0;
        for (int i = 0; i < frequencies.length; i++) {
            final float f = frequencies[i];
            final int n = lengths[i];
            if (f > 0 && n > 0) {
                final boolean afterNote = i > 0 && frequencies[i - 1] > 0;
                final boolean beforeNote = i + 1 < frequencies.length && frequencies[i + 1] > 0;
                note(out, offset, n, f, sampleRate, afterNote, beforeNote);
            }
            offset += n;
        }

        reverb(out, sampleRate);
        normalize(out);
        return out;
    }

    private void note(float[] out, int offset, int n, float f, int sr,
                      boolean afterNote, boolean beforeNote) {
        final double midi = 69 + 12 * Math.log(f / 440.0) / Math.log(2);
        final Sample s = sampleFor(midi);
        final double step = Math.pow(2, (midi - s.pitch) / 12) * s.rate / sr;
        // The note rings a little after its end and overlaps the next one (legato)
        final int release = (int) ((beforeNote ? LEGATO_RELEASE : FINAL_RELEASE) * sr);
        final int attack = afterNote ? (int) (LEGATO_ATTACK * sr) : 0;
        final int loopLength = s.loopEnd - s.loopStart;

        double position = 0;
        for (int i = 0; i < n + release && offset + i < out.length; i++) {
            // Position in the recording, looping between the loop points
            double p = position;
            if (p >= s.loopEnd) p = s.loopStart + (p - s.loopStart) % loopLength;
            final int i0 = (int) p;
            final double frac = p - i0;
            final int i1 = i0 + 1 < s.loopEnd ? i0 + 1 : s.loopStart;
            final double value = s.data[i0] * (1 - frac) + s.data[i1] * frac;

            double env = 1;
            if (i < attack) env = LEGATO_START_LEVEL + (1 - LEGATO_START_LEVEL) * i / attack;
            if (i >= n) env *= 1 - (double) (i - n) / release;

            out[offset + i] += (float) (value * env);
            position += step;
        }
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

package fr.charleslabs.tinwhistletabs.music;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class WhistleTransposerTest {

    private static List<MusicNote> notes(int... pitches) {
        final List<MusicNote> list = new ArrayList<>();
        for (int p : pitches) list.add(new MusicNote(p, 100));
        return list;
    }

    /** Sound of the fingerings played on the given whistle. */
    private static List<Integer> sound(List<MusicNote> stored, String tuneWhistle, String myWhistle) {
        final int shift = WhistleTransposer.fingeringShift(stored, tuneWhistle, myWhistle);
        final List<Integer> out = new ArrayList<>();
        for (MusicNote n : WhistleTransposer.transposed(stored, shift))
            out.add(n.getPitch() + WhistleTransposer.soundShift(myWhistle));
        return out;
    }

    @Test
    public void recommendedWhistleKeepsTheFingerings() {
        assertEquals(0, WhistleTransposer.fingeringShift(notes(54, 58, 61), "D", WhistleTransposer.RECOMMENDED));
    }

    @Test
    public void cWhistleFingersAToneHigherForTheSamePitch() {
        // D whistle tune: on a C whistle, D (low D fingering on a D whistle) is fingered E
        final List<MusicNote> tune = notes(54, 56, 58, 59, 61);
        assertEquals(2, WhistleTransposer.fingeringShift(tune, "D", "High C"));
        // Same pitch as on the D whistle (playback of the D whistle is the stored notes)
        assertEquals(sound(tune, "D", "High D"), sound(tune, "D", "High C"));
    }

    @Test
    public void duetSoundsTheSameModuloOctave() {
        final List<MusicNote> tune = notes(54, 56, 58, 59, 61, 63, 65, 66, 68, 70);
        final List<Integer> d = sound(tune, "D", "High D");
        for (String key : MusicSettings.WHISTLE_KEYS) {
            final List<Integer> other = sound(tune, "D", key);
            for (int i = 0; i < d.size(); i++)
                assertEquals(key, 0, Math.floorMod(other.get(i) - d.get(i), 12));
        }
    }

    @Test
    public void tuneForAGWhistleOnADWhistle() {
        // Stored as G whistle fingerings (shift 7): on a D whistle, fingerings move by 5 or -7
        final List<MusicNote> tune = notes(54, 56, 58, 59);
        final int shift = WhistleTransposer.fingeringShift(tune, "G", "High D");
        assertEquals(0, Math.floorMod(shift + 7, 12));
        assertEquals(0, WhistleTransposer.outOfRange(tune, shift));
    }

    @Test
    public void lowWhistleUsesTheSameFingeringsAnOctaveDown() {
        final List<MusicNote> tune = notes(54, 58, 61, 66);
        assertEquals(0, WhistleTransposer.fingeringShift(tune, "D", "Low D"));
        assertEquals(-12, WhistleTransposer.soundShift("Low D"));
    }

    @Test
    public void notesOutOfRangeAreCounted() {
        assertEquals(2, WhistleTransposer.outOfRange(notes(50, 54, 80, 0), 0));
    }
}

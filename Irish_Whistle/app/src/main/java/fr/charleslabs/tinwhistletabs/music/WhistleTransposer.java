package fr.charleslabs.tinwhistletabs.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fingerings for the whistle the player has.
 *
 * Tunes are stored as D whistle fingerings for the recommended whistle of the tune (a tune
 * for a G whistle is stored as the fingerings to play on a G whistle). When the player has
 * another whistle, the fingerings are transposed so that the tune sounds at the same pitch:
 * a C whistle and a D whistle can play it together. The octave is chosen to keep the notes
 * in the range of the whistle and to avoid half-holed notes.
 *
 * Pure Java, unit tested.
 */
public final class WhistleTransposer {
    /** Setting value: use the whistle recommended for each tune. */
    public static final String RECOMMENDED = "";

    private static final int MIN = 54, MAX = 78; // playable range, in D whistle fingerings
    private static final List<Integer> HALF_HOLED = Arrays.asList(55, 57, 60, 62, 67, 69, 72, 74, 76);

    private WhistleTransposer() {}

    /**
     * Semitones to add to the stored notes to get the fingerings on {@code myWhistle}.
     *
     * @param tuneWhistle recommended whistle of the tune ("D", "G", "Bb"...)
     * @param myWhistle   a key of {@link MusicSettings#WHISTLE_KEYS}, or {@link #RECOMMENDED}
     */
    public static int fingeringShift(List<MusicNote> notes, String tuneWhistle, String myWhistle) {
        if (myWhistle == null || myWhistle.equals(RECOMMENDED)) return 0;
        final int tuneShift = AbcParser.whistleShift(tuneWhistle);
        if (tuneShift < 0) return 0;
        // Stored note = written pitch + tuneShift. My whistle sounds a fingering + offset:
        // fingering = written pitch - offset = stored - tuneShift - offset (+ octaves)
        final int base = -tuneShift - MusicSettings.getShift(myWhistle);
        int best = base;
        long bestScore = Long.MAX_VALUE;
        for (int octave = -3; octave <= 3; octave++) {
            final int shift = base + 12 * octave;
            final long score = outOfRange(notes, shift) * 1000L + halfHoled(notes, shift) * 10L
                    + Math.abs(shift);
            if (score < bestScore) {
                bestScore = score;
                best = shift;
            }
        }
        return best;
    }

    /** Semitones between the fingerings and the sound of the whistle, for playback. */
    public static int soundShift(String myWhistle) {
        if (myWhistle == null || myWhistle.equals(RECOMMENDED)) return 0;
        return MusicSettings.getShift(myWhistle);
    }

    /** Number of notes that cannot be played on the whistle once transposed. */
    public static int outOfRange(List<MusicNote> notes, int shift) {
        int count = 0;
        for (MusicNote n : notes) {
            if (n.isRest()) continue;
            final int p = n.getPitch() + shift;
            if (p < MIN || p > MAX) count++;
        }
        return count;
    }

    private static int halfHoled(List<MusicNote> notes, int shift) {
        int count = 0;
        for (MusicNote n : notes)
            if (!n.isRest() && HALF_HOLED.contains(n.getPitch() + shift)) count++;
        return count;
    }

    /** Copy of the notes transposed by {@code shift} semitones (rests unchanged). */
    public static List<MusicNote> transposed(List<MusicNote> notes, int shift) {
        final List<MusicNote> out = new ArrayList<>(notes.size());
        for (MusicNote n : notes) {
            final MusicNote copy = new MusicNote(n.getPitch(), n.getDuration());
            copy.transpose(shift);
            out.add(copy);
        }
        return out;
    }

    /** Display name of a whistle key: "High D" -> "D", "Low G" -> "low G". */
    public static String shortName(String key) {
        if (key.startsWith("High ")) return key.substring(5);
        if (key.startsWith("Low ")) return "low " + key.substring(4);
        return key;
    }
}

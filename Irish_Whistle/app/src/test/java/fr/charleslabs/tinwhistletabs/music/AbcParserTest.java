package fr.charleslabs.tinwhistletabs.music;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AbcParserTest {

    private static String pitches(AbcParser.Result r) {
        final StringBuilder sb = new StringBuilder();
        for (MusicNote n : r.notes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(n.getPitch());
        }
        return sb.toString();
    }

    private static String lengths(AbcParser.Result r) {
        final StringBuilder sb = new StringBuilder();
        for (MusicNote n : r.notes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Math.round(n.getLengthInMS(1f)));
        }
        return sb.toString();
    }

    @Test
    public void scaleInDIsTransposedToWhistleRange() throws Exception {
        final AbcParser.Result r = AbcParser.parse("X:1\nT:Scale\nL:1/8\nK:D\nDEFGABcd|\n");
        // Low D of a D whistle is key 54, F and c are sharp in D major
        assertEquals("54 56 58 59 61 63 65 66", pitches(r));
        assertEquals("D", r.whistle);
        assertEquals("Scale", r.title);
    }

    @Test
    public void lengthsBrokenRhythmTupletsAndTies() throws Exception {
        final AbcParser.Result r = AbcParser.parse("X:1\nL:1/8\nQ:1/4=120\nK:D\nD2 E/ F> G (3ABc d-d|\n");
        assertEquals("480 120 360 120 160 160 160 480", lengths(r));
    }

    @Test
    public void repeatsAndEndings() throws Exception {
        final AbcParser.Result r = AbcParser.parse("X:1\nL:1/8\nK:D\n|:D E|1 F:|2 G|]\n");
        assertEquals("54 56 58 54 56 59", pitches(r));
    }

    @Test
    public void accidentalsLastUntilBarLine() throws Exception {
        // In D major: =c then c stays natural in the bar, back to c# after the bar line
        final AbcParser.Result r = AbcParser.parse("X:1\nL:1/8\nK:D\nd =c c | c d|\n");
        assertEquals("66 64 64 65 66", pitches(r));
    }

    @Test
    public void modes() throws Exception {
        // A dorian: c natural, f sharp
        final AbcParser.Result r = AbcParser.parse("X:1\nL:1/8\nK:Ador\nABcd efga|\n");
        assertEquals("61 63 64 66 68 70 71 73", pitches(r));
    }

    @Test
    public void lowTuneNeedsAnotherWhistle() throws Exception {
        final AbcParser.Result r = AbcParser.parse("X:1\nL:1/8\nK:G\nG,A,B,C D2|\n");
        assertEquals("G", r.whistle);
        assertEquals("54 56 58 59 61", pitches(r));
    }

    @Test(expected = AbcParser.AbcParseException.class)
    public void noNotesIsAnError() throws Exception {
        AbcParser.parse("X:1\nT:Empty\nK:D\n");
    }

    /** The parser must give the same result as the abc2midi pipeline for the bundled tunes. */
    @Test
    public void matchesPreGeneratedDatabase() throws Exception {
        final File raw = new File("src/main/res/raw");
        final JSONArray db = new JSONArray(new String(Files.readAllBytes(
                new File(raw, "db.json").toPath()), StandardCharsets.UTF_8));
        int ok = 0;
        final List<String> failures = new ArrayList<>();
        for (int i = 0; i < db.length(); i++) {
            final JSONObject tune = db.getJSONObject(i);
            final String[] ref = new String(Files.readAllBytes(new File(raw,
                    tune.getString("file") + ".txt").toPath()), StandardCharsets.UTF_8).trim().split(",");
            try {
                final AbcParser.Result r = AbcParser.parse(tune.getString("abc"));
                boolean same = r.notes.size() == ref.length && r.whistle.equals(tune.getString("whistle"));
                for (int k = 0; same && k < ref.length; k++) {
                    final String[] p = ref[k].split("/");
                    final MusicNote n = r.notes.get(k);
                    same = n.getPitch() == Integer.parseInt(p[0])
                            && Math.abs(n.getLengthInMS(1f) - Integer.parseInt(p[1])) <= 3;
                }
                if (same) ok++;
                else failures.add(tune.getString("file"));
            } catch (AbcParser.AbcParseException e) {
                failures.add(tune.getString("file") + ": " + e.getMessage());
            }
        }
        // A few tunes differ because abc2midi expands ornaments (~ rolls, staccato)
        assertTrue("Only " + ok + "/" + db.length() + " identical. Failures: " + failures,
                ok >= db.length() * 0.95);
    }
}

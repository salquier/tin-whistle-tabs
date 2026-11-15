package fr.charleslabs.tinwhistletabs.music;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;

public class BackupTest {

    @Test
    public void roundTrip() throws Exception {
        final MusicSheet a = new MusicSheet("Mo Jig", "Jig", "X:1\nT:Mo Jig\nK:D\nDEF|\n", "D", "u_1");
        final MusicSheet b = new MusicSheet("Air \"quoted\"", "Air", "X:1\nK:G\nGAB|\n", "G", "u_2");
        final String json = new Backup(Arrays.asList(a, b), Arrays.asList("u_1", "m_amazinggrace")).toJson();

        final Backup read = Backup.fromJson(json);
        assertEquals(2, read.tunes.size());
        assertEquals("Air \"quoted\"", read.tunes.get(1).getTitle());
        assertEquals("X:1\nK:G\nGAB|\n", read.tunes.get(1).getABC());
        assertEquals("G", read.tunes.get(1).getWhistle());
        assertTrue(read.tunes.get(0).isUserTune());
        assertEquals(2, read.favorites.size());
        assertTrue(read.favorites.contains("m_amazinggrace"));
    }

    @Test
    public void builtInAndPreviewTunesAreNotRestored() throws Exception {
        final MusicSheet builtIn = new MusicSheet("X", "Reel", "X:1\nK:D\nD|\n", "D", "m_x");
        final MusicSheet preview = new MusicSheet("P", "Reel", "X:1\nK:D\nD|\n", "D", MusicSheet.PREVIEW_FILE);
        final Backup read = Backup.fromJson(new Backup(Arrays.asList(builtIn, preview),
                Arrays.<String>asList()).toJson());
        assertEquals(0, read.tunes.size());
    }

    @Test(expected = JSONException.class)
    public void otherJsonIsRejected() throws Exception {
        Backup.fromJson("{\"tunes\": []}");
    }
}

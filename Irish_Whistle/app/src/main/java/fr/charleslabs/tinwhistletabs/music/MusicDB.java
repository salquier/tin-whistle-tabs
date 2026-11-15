package fr.charleslabs.tinwhistletabs.music;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import fr.charleslabs.tinwhistletabs.R;
import fr.charleslabs.tinwhistletabs.utils.Utils;

public class MusicDB {
    private static final String USER_TUNES_FILE = "user_tunes.json";

    /** Built-in tunes followed by user tunes, sorted by title. */
    public List<MusicSheet> musicDB = new ArrayList<>();
    private final List<MusicSheet> builtinTunes = new ArrayList<>();
    private final List<MusicSheet> userTunes = new ArrayList<>();
    private final File userFile;

    //Singleton
    private static MusicDB instance;
    public static MusicDB getInstance(Context c){
        if(instance == null){
            instance = new MusicDB(c.getApplicationContext());
        }
        return instance;
    }
    private MusicDB(Context c){
        userFile = new File(c.getFilesDir(), USER_TUNES_FILE);
        try {
            String fileContent =  Utils.fileToString(c.getResources().openRawResource(R.raw.db));

            JSONArray jsonSheets = new JSONArray(fileContent);

            for (int i=0; i < jsonSheets.length(); i++) {
                builtinTunes.add(new MusicSheet(jsonSheets.getJSONObject(i)));
            }
        } catch (IOException | JSONException e) {
            // @TODO : REACT TO THIS
            e.printStackTrace();
        }
        loadUserTunes();
        rebuild();
    }

    // ================= User tunes =================

    private void loadUserTunes() {
        userTunes.clear();
        if (!userFile.exists()) return;
        try {
            final JSONArray array = new JSONArray(Utils.fileToString(new FileInputStream(userFile)));
            for (int i = 0; i < array.length(); i++)
                userTunes.add(new MusicSheet(array.getJSONObject(i)));
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveUserTunes() throws IOException {
        final JSONArray array = new JSONArray();
        try {
            for (MusicSheet sheet : userTunes) array.put(sheet.toJSON());
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        // Write to a temporary file then rename, to never lose tunes on a crash
        final File tmp = new File(userFile.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("UTF-8"))) {
            w.write(array.toString());
        }
        if (!tmp.renameTo(userFile)) {
            if (!userFile.delete() || !tmp.renameTo(userFile))
                throw new IOException("Cannot write " + userFile);
        }
    }

    /** Add or replace (same file id) a user tune, then persist. */
    public void saveUserTune(MusicSheet sheet) throws IOException {
        for (int i = 0; i < userTunes.size(); i++) {
            if (userTunes.get(i).getFile().equals(sheet.getFile())) {
                userTunes.set(i, sheet);
                saveUserTunes();
                rebuild();
                return;
            }
        }
        userTunes.add(sheet);
        saveUserTunes();
        rebuild();
    }

    public void deleteUserTune(MusicSheet sheet) throws IOException {
        for (int i = 0; i < userTunes.size(); i++) {
            if (userTunes.get(i).getFile().equals(sheet.getFile())) {
                userTunes.remove(i);
                saveUserTunes();
                rebuild();
                return;
            }
        }
    }

    public static String newUserTuneId() {
        return MusicSheet.USER_TUNE_PREFIX + System.currentTimeMillis();
    }

    private void rebuild() {
        final List<MusicSheet> all = new ArrayList<>(builtinTunes);
        all.addAll(userTunes);
        Collections.sort(all, new Comparator<MusicSheet>() {
            @Override
            public int compare(MusicSheet a, MusicSheet b) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
        });
        musicDB = all;
    }

    // ================= Notes =================

    public static List<MusicNote> open(Context c, MusicSheet sheet) throws IOException {
        if (sheet.isUserTune()) {
            try {
                return AbcParser.parse(sheet.getABC()).notes;
            } catch (AbcParser.AbcParseException e) {
                throw new IOException(e.getMessage());
            }
        }
        return open(c, sheet.getFile());
    }

    public static List<MusicNote> open(Context c, String filename) throws IOException {
        List<MusicNote> notes = new ArrayList<>();
        String fileContent =  Utils.fileToString(c.getResources().openRawResource(
                c.getResources().getIdentifier(filename,"raw",c.getPackageName())));

        String[] notesArray = fileContent.split(",");
        for (String note : notesArray){
            final String[] split = note.split("/");
            notes.add(new MusicNote(Integer.parseInt(split[0]),Integer.parseInt(split[1])));
        }

        return notes;
    }
}

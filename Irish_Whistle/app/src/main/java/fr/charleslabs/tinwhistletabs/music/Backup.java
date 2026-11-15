package fr.charleslabs.tinwhistletabs.music;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Backup file of the user's data: tunes created in the app and favourites.
 *
 * {"format": "tin-whistle-tabs-backup", "version": 1,
 *  "tunes": [ {title, type, whistle, abc, file}... ], "favorites": [ file ids ]}
 */
public final class Backup {
    public static final String FORMAT = "tin-whistle-tabs-backup";
    public static final int VERSION = 1;

    public final List<MusicSheet> tunes;
    public final Set<String> favorites;

    public Backup(List<MusicSheet> tunes, Collection<String> favorites) {
        this.tunes = tunes;
        this.favorites = new LinkedHashSet<>(favorites);
    }

    public String toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("format", FORMAT);
        o.put("version", VERSION);
        final JSONArray t = new JSONArray();
        for (MusicSheet sheet : tunes) t.put(sheet.toJSON());
        o.put("tunes", t);
        final JSONArray f = new JSONArray();
        for (String id : favorites) f.put(id);
        o.put("favorites", f);
        return o.toString(2);
    }

    public static Backup fromJson(String json) throws JSONException {
        final JSONObject o = new JSONObject(json);
        if (!FORMAT.equals(o.optString("format")))
            throw new JSONException("Not a Tin Whistle Tabs backup file");
        if (o.optInt("version", 0) > VERSION)
            throw new JSONException("Backup made by a newer version of the app");
        final List<MusicSheet> tunes = new ArrayList<>();
        final JSONArray t = o.optJSONArray("tunes");
        if (t != null) {
            for (int i = 0; i < t.length(); i++) {
                final MusicSheet sheet = new MusicSheet(t.getJSONObject(i));
                // Only tunes created in the app can be restored (built-in ones are in the app)
                if (sheet.isUserTune() && !MusicSheet.PREVIEW_FILE.equals(sheet.getFile()))
                    tunes.add(sheet);
            }
        }
        final List<String> favorites = new ArrayList<>();
        final JSONArray f = o.optJSONArray("favorites");
        if (f != null) for (int i = 0; i < f.length(); i++) favorites.add(f.getString(i));
        return new Backup(tunes, favorites);
    }
}

package fr.charleslabs.tinwhistletabs.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts an ABC tune into the note list used by the app (pitch as piano key number,
 * duration in the same pseudo-milliseconds as the pre-generated database).
 *
 * This is an in-app replacement for the "abc2midi + abc2db.py" pipeline, so that tunes
 * typed in the editor can be played and tabbed. It supports the subset of ABC used by
 * traditional tunes: key signatures and modes, accidentals, note lengths, broken rhythm,
 * ties, tuplets, chords (highest note kept), repeats and alternate endings, inline fields.
 * Grace notes, decorations, chord symbols, lyrics and secondary voices are ignored.
 *
 * Pure Java (no Android dependency) so it can be unit tested on the JVM.
 */
public final class AbcParser {

    /** Reference unit: a quarter note at 120 BPM is 480 (abc2midi ticks, as in db files). */
    private static final double WHOLE_NOTE_AT_120 = 1920.0;

    // Whistle range and half-holed notes on a D whistle (same as abc2db.py)
    private static final int D_WHISTLE_MIN = 54;
    private static final int D_WHISTLE_MAX = 78;
    private static final Set<Integer> HALF_HOLED =
            new HashSet<>(Arrays.asList(55, 57, 60, 62, 67, 69, 72, 74, 76));
    private static final String[] OFFSETS_D =
            {"D", "Db", "C", "B", "Bb", "A", "G#", "G", "F#", "F", "E", "Eb"};

    private AbcParser() {}

    /** Parse result. */
    public static final class Result {
        public final List<MusicNote> notes;
        /** Transposition (semitones) applied to fit a D whistle. */
        public final int shift;
        /** Whistle required to play the tune at its written pitch. */
        public final String whistle;
        public final String title;
        public final String rhythm;
        public final String composer;

        Result(List<MusicNote> notes, int shift, String title, String rhythm, String composer) {
            this.notes = notes;
            this.shift = shift;
            this.whistle = getWhistle(shift);
            this.title = title;
            this.rhythm = rhythm;
            this.composer = composer;
        }
    }

    public static class AbcParseException extends Exception {
        public AbcParseException(String message) {
            super(message);
        }
    }

    /** Parse an ABC tune and transpose it to the D whistle range. */
    public static Result parse(String abc) throws AbcParseException {
        if (abc == null) throw new AbcParseException("Empty tune");
        final Tokenizer tokenizer = new Tokenizer();
        tokenizer.run(abc);
        final List<int[]> raw = expand(tokenizer.events);

        // Merge consecutive rests, drop leading/trailing rests of zero length and trailing rests
        final List<int[]> cleaned = new ArrayList<>();
        for (int[] n : raw) {
            if (n[1] <= 0) continue;
            if (n[0] == 0 && !cleaned.isEmpty() && cleaned.get(cleaned.size() - 1)[0] == 0) {
                cleaned.get(cleaned.size() - 1)[1] += n[1];
            } else {
                cleaned.add(new int[]{n[0], n[1]});
            }
        }
        while (!cleaned.isEmpty() && cleaned.get(cleaned.size() - 1)[0] == 0)
            cleaned.remove(cleaned.size() - 1);

        boolean hasNote = false;
        for (int[] n : cleaned) if (n[0] != 0) { hasNote = true; break; }
        if (!hasNote) throw new AbcParseException("No notes found (is there a K: line before the music?)");

        final int shift = autoTranspose(cleaned);
        final List<MusicNote> notes = new ArrayList<>(cleaned.size());
        for (int[] n : cleaned)
            notes.add(new MusicNote(n[0] == 0 ? 0 : n[0] + shift, n[1]));
        return new Result(notes, shift, tokenizer.title, tokenizer.rhythm, tokenizer.composer);
    }

    // ===================== Transposition (port of abc2db.py) =====================

    static int autoTranspose(List<int[]> notes) throws AbcParseException {
        int r = autoTranspose(notes, false);
        if (r == Integer.MIN_VALUE) r = autoTranspose(notes, true);
        if (r == Integer.MIN_VALUE)
            throw new AbcParseException("Tune range is too wide for a tin whistle (more than 2 octaves)");
        return r;
    }

    private static int autoTranspose(List<int[]> notes, boolean allowHalfHoled) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] n : notes) {
            if (n[0] == 0) continue;
            min = Math.min(min, n[0]);
            max = Math.max(max, n[0]);
        }
        if (fits(notes, min, max, 0, allowHalfHoled)) return 0;
        if (fits(notes, min, max, 12, allowHalfHoled)) return 12;
        // Same search order as abc2db.py (upwards from 0), then downwards for high tunes
        for (int shift = 0; max + shift <= D_WHISTLE_MAX; shift++) {
            if (fits(notes, min, max, shift, allowHalfHoled)) return shift;
        }
        for (int shift = -1; min + shift >= D_WHISTLE_MIN - 12; shift--) {
            if (fits(notes, min, max, shift, allowHalfHoled)) return shift;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean fits(List<int[]> notes, int min, int max, int shift, boolean allowHalfHoled) {
        if (min + shift < D_WHISTLE_MIN || max + shift > D_WHISTLE_MAX) return false;
        if (allowHalfHoled) return true;
        for (int[] n : notes)
            if (n[0] != 0 && HALF_HOLED.contains(n[0] + shift)) return false;
        return true;
    }

    public static String getWhistle(int shift) {
        return OFFSETS_D[((shift % 12) + 12) % 12];
    }

    // ===================== Repeat expansion =====================

    private static final int EV_NOTE = 0, EV_BAR = 1, EV_REPEAT_START = 2, EV_REPEAT_END = 3,
            EV_ENDING = 4, EV_SECTION_END = 5;

    private static final class Event {
        final int type;
        int pitch;       // key number, 0 = rest
        double length;   // pseudo ms
        Set<Integer> endings;

        Event(int type) { this.type = type; }
    }

    private static List<int[]> expand(List<Event> events) {
        final List<int[]> out = new ArrayList<>();
        final Set<Integer> jumped = new HashSet<>();
        int start = 0, pass = 1, pc = 0, guard = 0;
        boolean skipping = false, inEnding = false, explicitStart = false;
        final int maxSteps = events.size() * 8 + 16;

        while (pc < events.size() && guard++ < maxSteps) {
            final Event ev = events.get(pc);
            switch (ev.type) {
                case EV_NOTE:
                    if (!skipping) out.add(new int[]{ev.pitch, (int) Math.round(ev.length)});
                    break;
                case EV_REPEAT_START:
                    // "|: A |: B" without ":|" : the first section is still repeated (abc2midi)
                    if (explicitStart && pass == 1 && !skipping && !inEnding && !jumped.contains(pc)) {
                        jumped.add(pc);
                        pass++;
                        pc = start;
                        continue;
                    }
                    explicitStart = true;
                    start = pc + 1;
                    pass = 1;
                    skipping = false;
                    inEnding = false;
                    break;
                case EV_SECTION_END:
                    if (skipping || inEnding) {
                        skipping = false;
                        inEnding = false;
                        explicitStart = false;
                        pass = 1;
                        start = pc + 1;
                    } else if (pass == 1 && !explicitStart) {
                        start = pc + 1;
                    }
                    break;
                case EV_REPEAT_END:
                    if (skipping) break; // ":|" closing a first ending we are skipping
                    if (!jumped.contains(pc) && !(inEnding && pass >= 2)) {
                        jumped.add(pc);
                        pass++;
                        inEnding = false;
                        pc = start;
                        continue;
                    }
                    pass = 1;
                    inEnding = false;
                    explicitStart = false;
                    start = pc + 1;
                    break;
                case EV_ENDING:
                    skipping = !ev.endings.contains(pass);
                    inEnding = !skipping;
                    break;
                default:
                    break;
            }
            pc++;
        }
        return out;
    }

    // ===================== Tokenizer =====================

    private static final class Tokenizer {
        final List<Event> events = new ArrayList<>();
        String title = null, rhythm = null, composer = null;

        // Musical state
        double unitLength = -1; // fraction of a whole note, -1 = not set yet
        double meter = 1.0;
        double headerMeter = 1.0; // default L is derived from the M: field of the header only
        boolean compoundMeter = false;
        double tempoQpm = 120; // quarter notes per minute
        double tempoUnit = -1; // fraction for "Q:120" style, -1 = use L
        double tempoBeats = -1;
        final int[] keyAcc = new int[7]; // accidental per letter C..B
        final Map<Integer, Integer> barAcc = new HashMap<>(); // natural pitch -> accidental

        // Voice handling
        String firstVoice = null;
        boolean ignoreVoice = false;

        // Note-level state
        Event lastNote = null;
        boolean tiePending = false;
        double brokenNext = 1.0;
        int tupletLeft = 0;
        double tupletFactor = 1.0;
        boolean inBody = false;
        boolean finished = false;

        void run(String abc) {
            final String[] lines = abc.replace("\r\n", "\n").replace('\r', '\n').split("\n");
            for (String line : lines) {
                if (finished) break;
                if (line.startsWith("%%")) continue;
                line = stripComment(line);
                if (line.trim().isEmpty()) continue;
                if (line.length() >= 2 && line.charAt(1) == ':' && isFieldLetter(line.charAt(0))
                        && !(line.charAt(0) == '|' )) {
                    field(line.charAt(0), line.substring(2).trim());
                    continue;
                }
                if (!ignoreVoice) music(line);
            }
        }

        private static boolean isFieldLetter(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '+';
        }

        private static String stripComment(String line) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '%' && (i == 0 || line.charAt(i - 1) != '\\'))
                    return line.substring(0, i);
            }
            return line;
        }

        double unit() {
            if (unitLength > 0) return unitLength;
            return headerMeter < 0.75 ? 1.0 / 16 : 1.0 / 8;
        }

        double msPerWhole() {
            double qpm = tempoQpm;
            if (tempoBeats > 0) {
                final double u = tempoUnit > 0 ? tempoUnit : unit();
                qpm = tempoBeats * u * 4;
            }
            return WHOLE_NOTE_AT_120 * 120.0 / qpm;
        }

        void field(char f, String value) {
            switch (f) {
                case 'X':
                    if (inBody) finished = true; // only the first tune
                    break;
                case 'T':
                    if (title == null) title = value;
                    break;
                case 'R':
                    if (rhythm == null) rhythm = value;
                    break;
                case 'C':
                    if (composer == null) composer = value;
                    break;
                case 'K':
                    parseKey(value);
                    inBody = true;
                    break;
                case 'L':
                    final double l = parseFraction(value);
                    if (l > 0) unitLength = l;
                    break;
                case 'M':
                    parseMeter(value);
                    if (!inBody) headerMeter = meter;
                    break;
                case 'Q':
                    parseTempo(value);
                    break;
                case 'V':
                    final String id = value.split("\\s+")[0];
                    if (firstVoice == null) firstVoice = id;
                    ignoreVoice = !firstVoice.equals(id);
                    break;
                default:
                    // w:, W:, N:, S:, Z:, P:, I:... ignored
                    break;
            }
        }

        void parseMeter(String v) {
            v = v.trim();
            if (v.equals("C")) { meter = 1.0; compoundMeter = false; return; }
            if (v.equals("C|")) { meter = 1.0; compoundMeter = false; return; }
            if (v.equalsIgnoreCase("none") || v.isEmpty()) { meter = 1.0; return; }
            final String[] parts = v.split("/");
            if (parts.length == 2) {
                try {
                    int num = 0;
                    for (String s : parts[0].split("[+ ]")) if (!s.isEmpty()) num += Integer.parseInt(s.trim());
                    final int den = Integer.parseInt(parts[1].trim());
                    meter = (double) num / den;
                    compoundMeter = num % 3 == 0 && num > 3;
                } catch (NumberFormatException ignored) {}
            }
        }

        void parseTempo(String v) {
            v = v.replaceAll("\"[^\"]*\"", " ").trim();
            if (v.isEmpty()) return;
            final int eq = v.indexOf('=');
            try {
                if (eq < 0) {
                    tempoBeats = Double.parseDouble(v.trim());
                    tempoUnit = -1;
                } else {
                    double beat = 0;
                    for (String s : v.substring(0, eq).trim().split("\\s+")) {
                        final double fr = parseFraction(s);
                        if (fr > 0) beat += fr;
                    }
                    final double bpm = Double.parseDouble(v.substring(eq + 1).trim().split("\\s+")[0]);
                    if (beat <= 0) beat = 0.25;
                    tempoBeats = bpm;
                    tempoUnit = beat;
                }
            } catch (NumberFormatException ignored) {}
        }

        static double parseFraction(String s) {
            s = s.trim();
            final int slash = s.indexOf('/');
            try {
                if (slash < 0) return Double.parseDouble(s);
                return Double.parseDouble(s.substring(0, slash)) / Double.parseDouble(s.substring(slash + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        // Sharps order F C G D A E B, as letter indexes in C D E F G A B
        private static final int[] SHARP_ORDER = {3, 0, 4, 1, 5, 2, 6};
        private static final int[] LETTER_FIFTHS = {0, 2, 4, -1, 1, 3, 5}; // C D E F G A B

        void parseKey(String v) {
            Arrays.fill(keyAcc, 0);
            barAcc.clear();
            String s = v.trim();
            // Remove clef and other key=value parameters
            s = s.replaceAll("\\b[a-z]+=\\S+", "").trim();
            if (s.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith("none")) return;
            if (s.startsWith("HP")) return;
            if (s.startsWith("Hp")) { keyAcc[3] = 1; keyAcc[0] = 1; return; }

            final char tonic = Character.toUpperCase(s.charAt(0));
            final int letter = "CDEFGAB".indexOf(tonic);
            if (letter < 0) return;
            int i = 1;
            int fifths = LETTER_FIFTHS[letter];
            if (i < s.length() && s.charAt(i) == '#') { fifths += 7; i++; }
            else if (i < s.length() && s.charAt(i) == 'b') { fifths -= 7; i++; }

            final String rest = s.substring(i).trim();
            final String[] words = rest.isEmpty() ? new String[0] : rest.split("\\s+");
            int w = 0;
            if (words.length > 0 && !isAccidentalToken(words[0])) {
                fifths += modeOffset(words[0].toLowerCase(Locale.ROOT));
                w = 1;
            }
            if (fifths > 0) for (int k = 0; k < Math.min(fifths, 7); k++) keyAcc[SHARP_ORDER[k]] = 1;
            if (fifths < 0) for (int k = 0; k < Math.min(-fifths, 7); k++) keyAcc[SHARP_ORDER[6 - k]] = -1;

            // Explicit accidentals, e.g. "K:D ^g" or "K:D exp ^f ^c"
            for (; w < words.length; w++) {
                final String t = words[w];
                if (t.equalsIgnoreCase("exp")) { Arrays.fill(keyAcc, 0); continue; }
                if (!isAccidentalToken(t)) continue;
                int acc = 0, j = 0;
                while (j < t.length() && "^_=".indexOf(t.charAt(j)) >= 0) {
                    acc += t.charAt(j) == '^' ? 1 : t.charAt(j) == '_' ? -1 : 0;
                    j++;
                }
                if (j < t.length()) {
                    final int l = "CDEFGAB".indexOf(Character.toUpperCase(t.charAt(j)));
                    if (l >= 0) keyAcc[l] = acc;
                }
            }
        }

        private static boolean isAccidentalToken(String t) {
            return !t.isEmpty() && "^_=".indexOf(t.charAt(0)) >= 0;
        }

        private static int modeOffset(String mode) {
            if (mode.isEmpty()) return 0;
            if (mode.startsWith("maj") || mode.startsWith("ion")) return 0;
            if (mode.startsWith("mix")) return -1;
            if (mode.startsWith("dor")) return -2;
            if (mode.startsWith("m") || mode.startsWith("aeo")) return -3; // m, min, minor
            if (mode.startsWith("phr")) return -4;
            if (mode.startsWith("loc")) return -5;
            if (mode.startsWith("lyd")) return 1;
            return 0;
        }

        // ---- Music line ----

        private String line;
        private int pos;

        void music(String l) {
            inBody = true;
            line = l;
            pos = 0;
            while (pos < line.length()) {
                final char c = line.charAt(pos);
                if (isNoteStart(c)) {
                    note();
                } else if (c == '[') {
                    bracket();
                } else if (c == '|' || c == ':') {
                    bar();
                } else if (c == '"') {
                    skipUntil('"');
                } else if (c == '!') {
                    skipUntil('!');
                } else if (c == '+') {
                    skipUntil('+');
                } else if (c == '{') {
                    skipUntil('}');
                } else if (c == '(') {
                    tuplet();
                } else if (c == '-') {
                    tiePending = true;
                    pos++;
                } else if (c == '>' || c == '<') {
                    broken();
                } else {
                    pos++; // spaces, slur ends, decorations (~ . H T u v...), y, `, \, &...
                }
            }
        }

        private void skipUntil(char end) {
            final int idx = line.indexOf(end, pos + 1);
            pos = idx < 0 ? line.length() : idx + 1;
        }

        private static boolean isNoteStart(char c) {
            return "^_=".indexOf(c) >= 0 || (c >= 'A' && c <= 'G') || (c >= 'a' && c <= 'g')
                    || c == 'z' || c == 'x' || c == 'Z' || c == 'X';
        }

        private void tuplet() {
            pos++;
            if (pos >= line.length() || !Character.isDigit(line.charAt(pos))) return; // slur
            final int p = readInt(-1);
            int q = -1, r = -1;
            if (pos < line.length() && line.charAt(pos) == ':') {
                pos++;
                q = readInt(-1);
                if (pos < line.length() && line.charAt(pos) == ':') {
                    pos++;
                    r = readInt(-1);
                }
            }
            if (q < 0) {
                switch (p) {
                    case 2: case 4: case 8: q = 3; break;
                    case 3: case 6: q = 2; break;
                    default: q = compoundMeter ? 3 : 2; break;
                }
            }
            if (r < 0) r = p;
            if (p > 0) {
                tupletFactor = (double) q / p;
                tupletLeft = r;
            }
        }

        private void broken() {
            final char c = line.charAt(pos);
            int n = 0;
            while (pos < line.length() && line.charAt(pos) == c) { n++; pos++; }
            final double shortF = Math.pow(0.5, n);
            final double longF = 2 - shortF;
            if (lastNote != null) {
                lastNote.length *= c == '>' ? longF : shortF;
            }
            brokenNext = c == '>' ? shortF : longF;
        }

        private int readInt(int def) {
            final int start = pos;
            while (pos < line.length() && Character.isDigit(line.charAt(pos))) pos++;
            if (pos == start) return def;
            try {
                return Integer.parseInt(line.substring(start, pos));
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /** Length multiplier after a note: "3", "/", "//", "3/2", "/4". */
        private double readLength() {
            double num = readInt(1);
            double den = 1;
            while (pos < line.length() && line.charAt(pos) == '/') {
                pos++;
                final int d = readInt(-1);
                den *= d > 0 ? d : 2;
            }
            return num / den;
        }

        /** Parses one note (or rest) and returns {pitch, lengthMultiplier}. pitch = 0 for rests. */
        private double[] readNote() {
            int acc = Integer.MIN_VALUE;
            while (pos < line.length() && "^_=".indexOf(line.charAt(pos)) >= 0) {
                if (acc == Integer.MIN_VALUE) acc = 0;
                final char a = line.charAt(pos);
                acc += a == '^' ? 1 : a == '_' ? -1 : 0;
                pos++;
            }
            if (pos >= line.length()) return null;
            final char c = line.charAt(pos++);
            if (c == 'z' || c == 'x') {
                return new double[]{0, readLength()};
            }
            if (c == 'Z' || c == 'X') {
                final int bars = readInt(1);
                return new double[]{0, bars * meter / unit()};
            }
            final int letter = "CDEFGAB".indexOf(Character.toUpperCase(c));
            if (letter < 0) return null;
            int octave = Character.isLowerCase(c) ? 1 : 0;
            while (pos < line.length() && (line.charAt(pos) == '\'' || line.charAt(pos) == ',')) {
                octave += line.charAt(pos) == '\'' ? 1 : -1;
                pos++;
            }
            final int[] semis = {0, 2, 4, 5, 7, 9, 11};
            // MIDI 60 = middle C = 'C'; key number = MIDI - 20
            final int natural = 40 + semis[letter] + 12 * octave;
            if (acc != Integer.MIN_VALUE) barAcc.put(natural, acc);
            final int effAcc = barAcc.containsKey(natural) ? barAcc.get(natural) : keyAcc[letter];
            return new double[]{natural + effAcc, readLength()};
        }

        private void note() {
            final double[] n = readNote();
            if (n == null) return;
            addNote((int) n[0], n[1]);
        }

        private void addNote(int pitch, double mult) {
            double len = mult * unit() * msPerWhole();
            len *= brokenNext;
            brokenNext = 1.0;
            if (tupletLeft > 0) {
                len *= tupletFactor;
                tupletLeft--;
            }
            if (ignoreVoice) return;
            if (tiePending && lastNote != null && lastNote.pitch == pitch && pitch != 0) {
                lastNote.length += len;
                tiePending = false;
                return;
            }
            tiePending = false;
            final Event ev = new Event(EV_NOTE);
            ev.pitch = pitch;
            ev.length = len;
            events.add(ev);
            lastNote = ev;
        }

        private void bracket() {
            // "[" may start: inline field [K:..], ending [1, thick bar [|, or chord [ceg]
            if (pos + 2 < line.length() && Character.isLetter(line.charAt(pos + 1))
                    && line.charAt(pos + 2) == ':') {
                final int end = line.indexOf(']', pos);
                final String inner = line.substring(pos + 1, end < 0 ? line.length() : end);
                field(inner.charAt(0), inner.substring(2).trim());
                pos = end < 0 ? line.length() : end + 1;
                return;
            }
            if (pos + 1 < line.length() && Character.isDigit(line.charAt(pos + 1))) {
                pos++;
                ending();
                return;
            }
            if (pos + 1 < line.length() && line.charAt(pos + 1) == '|') {
                bar();
                return;
            }
            chord();
        }

        private void chord() {
            pos++; // '['
            int best = -1;
            double firstLen = -1;
            while (pos < line.length() && line.charAt(pos) != ']') {
                final char c = line.charAt(pos);
                if (c == '|') break; // not a chord, e.g. a dangling '['
                if (c == '"') { skipUntil('"'); continue; }
                if (c == '!') { skipUntil('!'); continue; }
                if (isNoteStart(c)) {
                    final double[] n = readNote();
                    if (n == null) continue;
                    if (firstLen < 0) firstLen = n[1];
                    if ((int) n[0] > best) best = (int) n[0];
                } else {
                    pos++;
                }
            }
            if (pos < line.length() && line.charAt(pos) == ']') pos++;
            final double outer = readLength();
            if (best >= 0) addNote(best, (firstLen < 0 ? 1 : firstLen) * outer);
        }

        private void bar() {
            final int start = pos;
            while (pos < line.length()) {
                final char c = line.charAt(pos);
                if (c == '|' || c == ':') { pos++; continue; }
                if (c == ']' && pos > start && line.charAt(pos - 1) == '|') { pos++; continue; }
                if (c == '[' && pos == start && pos + 1 < line.length() && line.charAt(pos + 1) == '|') { pos++; continue; }
                break;
            }
            final String b = line.substring(start, pos);
            int leading = 0, trailing = 0;
            while (leading < b.length() && b.charAt(leading) == ':') leading++;
            while (trailing < b.length() - leading && b.charAt(b.length() - 1 - trailing) == ':') trailing++;
            final boolean onlyColons = leading == b.length();

            barAcc.clear();
            events.add(new Event(EV_BAR));
            if (onlyColons) {
                // "::" means end + start of repeat; a lone ":" is ignored
                if (b.length() >= 2) {
                    events.add(new Event(EV_REPEAT_END));
                    events.add(new Event(EV_REPEAT_START));
                }
            } else {
                if (leading > 0) events.add(new Event(EV_REPEAT_END));
                if (b.contains("||") || b.contains("|]") || b.contains("[|"))
                    events.add(new Event(EV_SECTION_END));
                if (trailing > 0) events.add(new Event(EV_REPEAT_START));
            }
            // Ending right after the bar: "|1", ":|2", "| [2" handled by bracket()
            if (pos < line.length() && Character.isDigit(line.charAt(pos))) ending();
        }

        private void ending() {
            final Set<Integer> numbers = new HashSet<>();
            while (pos < line.length()) {
                final int a = readInt(-1);
                if (a < 0) break;
                int b = a;
                if (pos < line.length() && line.charAt(pos) == '-') {
                    pos++;
                    b = readInt(a);
                }
                for (int k = a; k <= b; k++) numbers.add(k);
                if (pos < line.length() && line.charAt(pos) == ',') pos++;
                else break;
            }
            final Event ev = new Event(EV_ENDING);
            ev.endings = numbers;
            events.add(ev);
        }
    }
}

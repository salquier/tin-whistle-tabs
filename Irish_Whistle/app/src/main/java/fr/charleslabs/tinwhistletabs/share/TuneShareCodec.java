package fr.charleslabs.tinwhistletabs.share;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Turns a tune (title, type, ABC) into a short text that fits in a QR code, and back.
 *
 * Format: "TWT1:" + base64url(deflate(title \u001F type \u001F abc)).
 * The prefix identifies the app and the format version. Plain ABC text (with a K: line)
 * is also accepted by {@link #decode(String)} so tunes shared as text can be imported too.
 *
 * Pure Java (no Android dependency) so it can be unit tested on the JVM.
 */
public final class TuneShareCodec {
    public static final String PREFIX = "TWT1:";
    /** Byte-mode capacity of a version 40 QR code with low error correction is 2953. */
    public static final int MAX_QR_LENGTH = 2900;

    private static final char SEPARATOR = '\u001F';
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private TuneShareCodec() {}

    public static final class SharedTune {
        public final String title;
        public final String type;
        public final String abc;

        public SharedTune(String title, String type, String abc) {
            this.title = title == null ? "" : title;
            this.type = type == null ? "" : type;
            this.abc = abc == null ? "" : abc;
        }
    }

    public static class InvalidShareException extends Exception {
        public InvalidShareException(String message) {
            super(message);
        }
    }

    public static String encode(SharedTune tune) {
        final String raw = tune.title.replace(SEPARATOR, ' ') + SEPARATOR
                + tune.type.replace(SEPARATOR, ' ') + SEPARATOR + tune.abc;
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw.getBytes(UTF8));
        deflater.finish();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[1024];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return PREFIX + base64Encode(out.toByteArray());
    }

    public static SharedTune decode(String text) throws InvalidShareException {
        if (text == null) throw new InvalidShareException("Empty content");
        final String t = text.trim();
        if (t.startsWith(PREFIX)) {
            final byte[] data = base64Decode(t.substring(PREFIX.length()));
            final Inflater inflater = new Inflater(true);
            inflater.setInput(data);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            try {
                while (!inflater.finished()) {
                    final int n = inflater.inflate(buf);
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                    out.write(buf, 0, n);
                    if (out.size() > 1_000_000) throw new InvalidShareException("Content too large");
                }
            } catch (DataFormatException e) {
                throw new InvalidShareException("Corrupted tune code");
            } finally {
                inflater.end();
            }
            final String raw = new String(out.toByteArray(), UTF8);
            final int a = raw.indexOf(SEPARATOR);
            final int b = a < 0 ? -1 : raw.indexOf(SEPARATOR, a + 1);
            if (a < 0 || b < 0) throw new InvalidShareException("Corrupted tune code");
            return new SharedTune(raw.substring(0, a), raw.substring(a + 1, b), raw.substring(b + 1));
        }
        // Plain ABC text (drop anything a messaging app put before the X: line)
        if (t.matches("(?s).*(^|\\n)K:.*")) {
            final java.util.regex.Matcher x = java.util.regex.Pattern.compile("(?m)^X:").matcher(t);
            final String abc = x.find() ? t.substring(x.start()) : t;
            String title = "";
            for (String line : abc.split("\n")) {
                if (line.startsWith("T:")) { title = line.substring(2).trim(); break; }
            }
            return new SharedTune(title, "", abc);
        }
        throw new InvalidShareException("This is not a tin whistle tune");
    }

    // Minimal URL-safe base64 without padding (java.util.Base64 needs API 26)

    static String base64Encode(byte[] data) {
        final StringBuilder sb = new StringBuilder((data.length * 4 + 2) / 3);
        int i = 0;
        for (; i + 2 < data.length; i += 3) {
            final int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
            sb.append(B64.charAt(v >> 18)).append(B64.charAt((v >> 12) & 63))
                    .append(B64.charAt((v >> 6) & 63)).append(B64.charAt(v & 63));
        }
        final int rest = data.length - i;
        if (rest == 1) {
            final int v = (data[i] & 0xFF) << 16;
            sb.append(B64.charAt(v >> 18)).append(B64.charAt((v >> 12) & 63));
        } else if (rest == 2) {
            final int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8);
            sb.append(B64.charAt(v >> 18)).append(B64.charAt((v >> 12) & 63))
                    .append(B64.charAt((v >> 6) & 63));
        }
        return sb.toString();
    }

    static byte[] base64Decode(String s) throws InvalidShareException {
        s = s.replace("=", "").replace('+', '-').replace('/', '_').replaceAll("\\s", "");
        if (s.length() % 4 == 1) throw new InvalidShareException("Corrupted tune code");
        final ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() * 3 / 4);
        int buffer = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            final int v = B64.indexOf(s.charAt(i));
            if (v < 0) throw new InvalidShareException("Corrupted tune code");
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}

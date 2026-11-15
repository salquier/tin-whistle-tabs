package fr.charleslabs.tinwhistletabs.share;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

public class TuneShareCodecTest {
    private static final String ABC = "X:1\nT:Mo Tune é\nM:6/8\nL:1/8\nK:D\n|:dAA fAA|eAA fAA:|\n";

    @Test
    public void roundTrip() throws Exception {
        final String code = TuneShareCodec.encode(new TuneShareCodec.SharedTune("Mo Tune é", "Jig", ABC));
        assertTrue(code.startsWith(TuneShareCodec.PREFIX));
        final TuneShareCodec.SharedTune t = TuneShareCodec.decode(code);
        assertEquals("Mo Tune é", t.title);
        assertEquals("Jig", t.type);
        assertEquals(ABC, t.abc);
    }

    @Test
    public void base64RoundTripAllLengths() throws Exception {
        final Random r = new Random(1);
        for (int len = 0; len < 40; len++) {
            final byte[] data = new byte[len];
            r.nextBytes(data);
            assertArrayEquals(data, TuneShareCodec.base64Decode(TuneShareCodec.base64Encode(data)));
        }
    }

    @Test
    public void plainAbcTextIsAccepted() throws Exception {
        final TuneShareCodec.SharedTune t = TuneShareCodec.decode("Look at this one!\n" + ABC);
        assertEquals("Mo Tune é", t.title);
        assertEquals(ABC.trim(), t.abc);
    }

    @Test(expected = TuneShareCodec.InvalidShareException.class)
    public void randomTextIsRejected() throws Exception {
        TuneShareCodec.decode("https://example.com");
    }

    @Test(expected = TuneShareCodec.InvalidShareException.class)
    public void corruptedCodeIsRejected() throws Exception {
        TuneShareCodec.decode(TuneShareCodec.PREFIX + "abc$def");
    }

    /** Encode as a QR code and read it back, as the app does. */
    @Test
    public void qrCodeRoundTrip() throws Exception {
        final String code = TuneShareCodec.encode(new TuneShareCodec.SharedTune("Mo Tune é", "Jig", ABC));
        final Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.MARGIN, 2);
        final BitMatrix m = new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 600, 600, hints);
        final int[] pixels = new int[m.getWidth() * m.getHeight()];
        for (int y = 0; y < m.getHeight(); y++)
            for (int x = 0; x < m.getWidth(); x++)
                pixels[y * m.getWidth() + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        final String read = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(m.getWidth(), m.getHeight(), pixels)))).getText();
        assertEquals(code, read);
        assertEquals(ABC, TuneShareCodec.decode(read).abc);
    }

    /** Every bundled tune must fit in a QR code. */
    @Test
    public void bundledTunesFitInQrCode() throws Exception {
        final JSONArray db = new JSONArray(new String(Files.readAllBytes(
                new File("src/main/res/raw/db.json").toPath()), StandardCharsets.UTF_8));
        int longest = 0;
        for (int i = 0; i < db.length(); i++) {
            final JSONObject tune = db.getJSONObject(i);
            final String code = TuneShareCodec.encode(new TuneShareCodec.SharedTune(
                    tune.getString("title"), tune.getString("type"), tune.getString("abc")));
            longest = Math.max(longest, code.length());
            assertEquals(tune.getString("abc"), TuneShareCodec.decode(code).abc);
        }
        assertTrue("Longest code: " + longest, longest <= TuneShareCodec.MAX_QR_LENGTH);
    }
}

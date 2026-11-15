package fr.charleslabs.tinwhistletabs;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import fr.charleslabs.tinwhistletabs.music.MusicSheet;
import fr.charleslabs.tinwhistletabs.share.QrCodes;
import fr.charleslabs.tinwhistletabs.share.TuneShareCodec;

/** Shows a tune as a QR code that another phone can scan, and shares it as image or text. */
public class ShareActivity extends AppCompatActivity {
    public static final String EXTRA_SHEET = "fr.charleslabs.tinwhistletabs.SHARE_SHEET";
    private static final int QR_SIZE = 1024;

    private MusicSheet sheet;
    private Bitmap qr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        sheet = (MusicSheet) getIntent().getSerializableExtra(EXTRA_SHEET);
        if (sheet == null || sheet.getABC() == null) {
            finish();
            return;
        }
        try {
            final ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.share_activity_title);
        } catch (Exception ignored) {}

        // Full brightness makes the code easier to scan
        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        ((TextView) findViewById(R.id.share_title)).setText(sheet.getTitle());

        final String code = TuneShareCodec.encode(new TuneShareCodec.SharedTune(
                sheet.getTitle(), sheet.getType(), sheet.getABC()));
        if (code.length() <= TuneShareCodec.MAX_QR_LENGTH) {
            try {
                qr = QrCodes.encode(code, QR_SIZE);
                ((ImageView) findViewById(R.id.share_qr)).setImageBitmap(qr);
            } catch (Exception e) {
                qr = null;
            }
        }
        if (qr == null) {
            Toast.makeText(this, R.string.share_error_too_long, Toast.LENGTH_LONG).show();
            findViewById(R.id.share_image).setEnabled(false);
        }

        findViewById(R.id.share_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareImage();
            }
        });
        findViewById(R.id.share_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareText();
            }
        });
    }

    private void shareImage() {
        try {
            final File dir = new File(getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            final File file = new File(dir, "tune-qr.png");
            try (OutputStream out = new FileOutputStream(file)) {
                qr.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            final Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, sheet.getTitle());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_send_image)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.share_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void shareText() {
        // Plain ABC: readable by people and by other ABC software, importable by this app
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, sheet.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, sheet.getABC());
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_send_text)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.share_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return true;
    }
}

package fr.charleslabs.tinwhistletabs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.List;

import fr.charleslabs.tinwhistletabs.android.SheetsAdapter;
import fr.charleslabs.tinwhistletabs.dialogs.AppCreditsDialog;
import fr.charleslabs.tinwhistletabs.music.Backup;
import fr.charleslabs.tinwhistletabs.music.MusicDB;
import fr.charleslabs.tinwhistletabs.music.MusicSheet;
import fr.charleslabs.tinwhistletabs.share.QrCodes;
import fr.charleslabs.tinwhistletabs.share.TuneShareCodec;
import fr.charleslabs.tinwhistletabs.utils.Utils;

public class MainActivity extends AppCompatActivity  {
    public static final String EXTRA_SHEET= "fr.charleslabs.tinwhistletabs.SHEET";
    private SheetsAdapter adapter;
    private ListView listView;
    private SearchView searchView;
    private boolean firstResume = true;

    // Import: QR code scanned with the camera, or read from an image
    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), new ActivityResultCallback<ScanIntentResult>() {
                @Override
                public void onActivityResult(ScanIntentResult result) {
                    if (result.getContents() != null) importText(result.getContents());
                }
            });
    // Backup: export to / import from a file chosen by the user
    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    new ActivityResultCallback<Uri>() {
                        @Override
                        public void onActivityResult(Uri uri) {
                            if (uri != null) exportBackup(uri);
                        }
                    });
    private final ActivityResultLauncher<String[]> restoreLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    new ActivityResultCallback<Uri>() {
                        @Override
                        public void onActivityResult(Uri uri) {
                            if (uri != null) readBackup(uri);
                        }
                    });
    private final ActivityResultLauncher<String> imageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) importImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up ListView
        listView = findViewById(R.id.sheetsList) ;

        // Get result list
        refreshList();

        // Long press: edit/delete user tunes, copy built-in tunes into the editor
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showTuneActions((MusicSheet) parent.getItemAtPosition(position));
                return true;
            }
        });

        // Handle click on item
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id){
                MusicSheet a = (MusicSheet) parent.getItemAtPosition(position);
                Intent intent = new Intent(getApplicationContext(), TabActivity.class);
                intent.putExtra(EXTRA_SHEET, a);
                startActivity(intent);
            }
        });

        // Tune shared from another app (text or QR code image)
        handleSharedIntent(getIntent());

        // Handle contact button
        findViewById(R.id.mainActivity_contact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                            "mailto",getString(R.string.dev_email), null));
                    intent.putExtra(Intent.EXTRA_SUBJECT, "TinWhistle App Contact");
                    startActivity(intent);
                }catch (Exception e){
                    Toast.makeText(getApplicationContext(), getString(R.string.error_contact,e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tunes may have been added, edited or deleted in the editor
        if (!firstResume) refreshList();
        firstResume = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedIntent(intent);
    }

    // ================= Backup =================

    private SharedPreferences favoritesPreferences() {
        return getSharedPreferences(SheetsAdapter.FAVORITES_PREFERENCES, Context.MODE_PRIVATE);
    }

    private void exportBackup(Uri uri) {
        try {
            final List<String> favorites = new ArrayList<>();
            for (Map.Entry<String, ?> e : favoritesPreferences().getAll().entrySet())
                if (Boolean.TRUE.equals(e.getValue())) favorites.add(e.getKey());
            final Backup backup = new Backup(MusicDB.getInstance(this).getUserTunes(), favorites);
            OutputStream out;
            try {
                out = getContentResolver().openOutputStream(uri, "wt"); // truncate existing file
            } catch (Exception e) {
                out = getContentResolver().openOutputStream(uri);
            }
            if (out == null) throw new IOException("Cannot open the file");
            try {
                out.write(backup.toJson().getBytes(Charset.forName("UTF-8")));
            } finally {
                out.close();
            }
            Toast.makeText(this, getString(R.string.backup_exported, backup.tunes.size(),
                    backup.favorites.size()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.backup_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void readBackup(Uri uri) {
        final Backup backup;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open the file");
            backup = Backup.fromJson(Utils.fileToString(in));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.backup_error, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.backup_restore_confirm, backup.tunes.size(), backup.favorites.size()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.backup_restore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        restoreBackup(backup);
                    }
                }).show();
    }

    private void restoreBackup(Backup backup) {
        try {
            MusicDB.getInstance(this).restoreUserTunes(backup.tunes);
            final SharedPreferences.Editor editor = favoritesPreferences().edit();
            for (String id : backup.favorites) editor.putBoolean(id, true);
            editor.apply();
            refreshList();
            Toast.makeText(this, getString(R.string.backup_restored, backup.tunes.size(),
                    backup.favorites.size()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.backup_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    // ================= Import =================

    private void handleSharedIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        final String type = intent.getType();
        if (type != null && type.startsWith("image/")) {
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) importImage(uri);
        } else {
            final CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null) importText(text.toString());
        }
        // Do not import again on configuration change
        intent.setAction(Intent.ACTION_MAIN);
    }

    private void startScan() {
        final ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.import_scan_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void importImage(Uri uri) {
        try {
            final String text = QrCodes.decode(this, uri);
            if (text == null) {
                Toast.makeText(this, R.string.import_error_no_qr, Toast.LENGTH_LONG).show();
                return;
            }
            importText(text);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.import_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void importClipboard() {
        final ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = cm == null ? null : cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).coerceToText(this) == null) {
            Toast.makeText(this, R.string.import_error_clipboard_empty, Toast.LENGTH_LONG).show();
            return;
        }
        importText(clip.getItemAt(0).coerceToText(this).toString());
    }

    /** Decode a shared tune and open it in the editor, where the user checks and saves it. */
    private void importText(String text) {
        try {
            final TuneShareCodec.SharedTune t = TuneShareCodec.decode(text);
            final String type = t.type.isEmpty() ? "Misc." : t.type;
            // Not a user tune id: the editor treats it as a new tune
            openEditor(new MusicSheet(t.title, type, t.abc, "D", "import"));
        } catch (TuneShareCodec.InvalidShareException e) {
            Toast.makeText(this, getString(R.string.import_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshList() {
        final List<MusicSheet> listData = MusicDB.getInstance(this).musicDB;
        if (adapter == null) {
            adapter = new SheetsAdapter(this, listData, findViewById(R.id.noResultsFoundView));
            listView.setAdapter(adapter);
        } else {
            // Update in place: keeps the scroll position and the current search
            adapter.updateSheets(listData);
        }
    }

    private void openEditor(MusicSheet sheet) {
        final Intent intent = new Intent(getApplicationContext(), EditorActivity.class);
        if (sheet != null) intent.putExtra(EditorActivity.EXTRA_SHEET, sheet);
        startActivity(intent);
    }

    private void openShare(MusicSheet sheet) {
        final Intent intent = new Intent(getApplicationContext(), ShareActivity.class);
        intent.putExtra(ShareActivity.EXTRA_SHEET, sheet);
        startActivity(intent);
    }

    private void showTuneActions(final MusicSheet sheet) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(sheet.getTitle());
        if (sheet.isUserTune()) {
            builder.setItems(new CharSequence[]{getString(R.string.main_longpress_edit),
                    getString(R.string.menu_share),
                    getString(R.string.main_longpress_delete)}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) openEditor(sheet);
                    else if (which == 1) openShare(sheet);
                    else confirmDelete(sheet);
                }
            });
        } else {
            builder.setItems(new CharSequence[]{getString(R.string.main_longpress_copy),
                            getString(R.string.menu_share)},
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) openEditor(sheet);
                            else openShare(sheet);
                        }
                    });
        }
        builder.show();
    }

    private void confirmDelete(final MusicSheet sheet) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.editor_delete_confirm, sheet.getTitle()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.editor_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            MusicDB.getInstance(MainActivity.this).deleteUserTune(sheet);
                            refreshList();
                        } catch (IOException e) {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.editor_error_save, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        //final MenuItem aboutItem = menu.findItem(R.id.mainAction_about);
        final MenuItem searchItem = menu.findItem(R.id.mainAction_search);
        searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });

        /*searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                aboutItem.setVisible(false);
                return true;
            }
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                aboutItem.setVisible(true);
                return true;
            }
        });*/

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.mainAction_add) {
            return false; // opens the sub-menu
        } else if (id == R.id.mainAction_new) {
            openEditor(null);
        } else if (id == R.id.mainAction_scan) {
            startScan();
        } else if (id == R.id.mainAction_importImage) {
            imageLauncher.launch("image/*");
        } else if (id == R.id.mainAction_importClipboard) {
            importClipboard();
        } else if (id == R.id.mainAction_exportBackup) {
            exportLauncher.launch("tin-whistle-tabs-backup-"
                    + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json");
        } else if (id == R.id.mainAction_importBackup) {
            restoreLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
        } else if (item.getItemId() == R.id.mainAction_about) {
            DialogFragment dialogFragment = new AppCreditsDialog();
            dialogFragment.show(getSupportFragmentManager(),"dialog");
        }

        return true;
    }
}

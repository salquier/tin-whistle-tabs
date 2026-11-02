package fr.charleslabs.tinwhistletabs;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.charleslabs.tinwhistletabs.music.AbcParser;
import fr.charleslabs.tinwhistletabs.music.MusicDB;
import fr.charleslabs.tinwhistletabs.music.MusicSheet;

/**
 * Tune editor: write ABC notation (keyboard or note palette), see the score and the
 * tin whistle tabs update live, then save the tune in the user's library.
 */
public class EditorActivity extends AppCompatActivity {
    /** Optional MusicSheet to edit (user tune) or to copy (built-in tune). */
    public static final String EXTRA_SHEET = "fr.charleslabs.tinwhistletabs.EDIT_SHEET";

    private static final long PREVIEW_DELAY_MS = 400;
    private static final String TEMPLATE = "X:1\nM:4/4\nL:1/8\nQ:1/4=100\nK:D\n";
    private static final List<String> TYPES = Arrays.asList("Reel", "Jig", "Hornpipe", "Polka",
            "Slide", "Slip Jig", "Waltz", "March", "Song", "Air", "Barndance", "Misc.");
    private static final String[] NOTES = {"D", "E", "F", "G", "A", "B", "c", "d", "e", "f",
            "g", "a", "b", "z"};
    private static final String[] SYMBOLS = {"2", "3", "4", "/2", ">", "(3", "-", "^", "_",
            "=", " ", "|", "|:", ":|", "[1", "[2", "|]", "\n", "⌫"};

    private EditText titleView, abcView;
    private Spinner typeView;
    private TextView statusView, tabsView;
    private WebView preview;
    private boolean pageLoaded = false;
    private boolean dirty = false;

    /** File id of the user tune being edited, null for a new tune. */
    private String editedFile = null;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updatePreview();
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        titleView = findViewById(R.id.editor_title);
        abcView = findViewById(R.id.editor_abc);
        typeView = findViewById(R.id.editor_type);
        statusView = findViewById(R.id.editor_status);
        tabsView = findViewById(R.id.editor_tabs);
        preview = findViewById(R.id.editor_preview);

        final ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TYPES);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeView.setAdapter(typeAdapter);

        // Initial content
        final MusicSheet source = (MusicSheet) getIntent().getSerializableExtra(EXTRA_SHEET);
        if (savedInstanceState == null) {
            if (source != null) {
                titleView.setText(source.getTitle());
                abcView.setText(source.getABC() != null ? source.getABC() : TEMPLATE);
                final int typeIndex = TYPES.indexOf(source.getType());
                typeView.setSelection(typeIndex >= 0 ? typeIndex : TYPES.size() - 1);
            } else {
                abcView.setText(TEMPLATE);
            }
            abcView.setSelection(abcView.getText().length());
        }
        if (source != null && source.isUserTune()) editedFile = source.getFile();

        try {
            final ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(editedFile != null ? R.string.editor_title_edit : R.string.editor_title_new);
        } catch (Exception ignored) {}

        // Score preview
        final WebSettings settings = preview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        preview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                updatePreview();
            }
        });
        preview.loadUrl("file:///android_asset/editor.html");

        // Palettes
        fillPalette((LinearLayout) findViewById(R.id.editor_palette), NOTES);
        fillPalette((LinearLayout) findViewById(R.id.editor_palette_symbols), SYMBOLS);

        // Live update
        final TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                dirty = true;
                handler.removeCallbacks(refresh);
                handler.postDelayed(refresh, PREVIEW_DELAY_MS);
            }
        };
        abcView.addTextChangedListener(watcher);
        titleView.addTextChangedListener(watcher);
        dirty = false;
        updatePreview();
    }

    private void fillPalette(LinearLayout container, String[] keys) {
        for (final String key : keys) {
            final Button b = new Button(this, null, android.R.attr.buttonStyleSmall);
            final String label = key.equals(" ") ? "␣" : key.equals("\n") ? "↵" : key;
            b.setText(label);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinimumWidth((int) (44 * getResources().getDisplayMetrics().density));
            b.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (key.equals("⌫")) backspace();
                    else insert(key);
                }
            });
            container.addView(b);
        }
    }

    private void insert(String text) {
        final Editable e = abcView.getText();
        final int start = Math.max(0, Math.min(abcView.getSelectionStart(), abcView.getSelectionEnd()));
        final int end = Math.max(0, Math.max(abcView.getSelectionStart(), abcView.getSelectionEnd()));
        e.replace(start, end, text);
        abcView.requestFocus();
    }

    private void backspace() {
        final Editable e = abcView.getText();
        final int start = abcView.getSelectionStart();
        final int end = abcView.getSelectionEnd();
        if (start != end) e.delete(Math.min(start, end), Math.max(start, end));
        else if (start > 0) e.delete(start - 1, start);
    }

    /** Re-render the score, the tabs and the status line. */
    private void updatePreview() {
        final String abc = buildAbc();
        try {
            final AbcParser.Result r = AbcParser.parse(abc);
            tabsView.setText(MusicSheet.notesToTabs(r.notes));
            int count = 0;
            for (int i = 0; i < r.notes.size(); i++) if (!r.notes.get(i).isRest()) count++;
            statusView.setTextColor(0xFF00602D);
            statusView.setText(getString(R.string.editor_status_ok, r.whistle, count));
        } catch (AbcParser.AbcParseException e) {
            tabsView.setText("");
            statusView.setTextColor(0xFFB00020);
            statusView.setText(e.getMessage());
        }
        if (pageLoaded) {
            final String js = "render(\"" + SheetActivity.escape(abc) + "\");";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                preview.evaluateJavascript(js, null);
            } else {
                preview.loadUrl("javascript:" + js.replace("%", "%25"));
            }
        }
    }

    /** ABC text with the T: line synchronized with the title field. */
    private String buildAbc() {
        String abc = abcView.getText().toString();
        final String title = titleView.getText().toString().trim();
        if (title.isEmpty()) return abc;
        final Matcher m = Pattern.compile("(?m)^T:.*$").matcher(abc);
        if (m.find()) {
            return abc.substring(0, m.start()) + "T:" + title + abc.substring(m.end());
        }
        // Insert after the X: line (or at the beginning)
        final Matcher x = Pattern.compile("(?m)^X:.*$").matcher(abc);
        if (x.find()) {
            final int at = x.end();
            return abc.substring(0, at) + "\nT:" + title + abc.substring(at);
        }
        return "T:" + title + "\n" + abc;
    }

    private MusicSheet buildSheet(String file) throws AbcParser.AbcParseException {
        final String abc = buildAbc();
        final AbcParser.Result r = AbcParser.parse(abc);
        String title = titleView.getText().toString().trim();
        if (title.isEmpty() && r.title != null) title = r.title.trim();
        if (title.isEmpty()) throw new AbcParser.AbcParseException(getString(R.string.editor_error_no_title));
        return new MusicSheet(title, (String) typeView.getSelectedItem(), abc, r.whistle, file);
    }

    private void save() {
        try {
            final MusicSheet sheet = buildSheet(editedFile != null ? editedFile : MusicDB.newUserTuneId());
            MusicDB.getInstance(this).saveUserTune(sheet);
            Toast.makeText(this, getString(R.string.editor_saved, sheet.getTitle()), Toast.LENGTH_SHORT).show();
            dirty = false;
            finish();
        } catch (AbcParser.AbcParseException e) {
            Toast.makeText(this, getString(R.string.editor_error_invalid, e.getMessage()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.editor_error_save, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void tryTabs() {
        try {
            final MusicSheet sheet = buildSheet(MusicSheet.USER_TUNE_PREFIX + "preview");
            final Intent intent = new Intent(getApplicationContext(), TabActivity.class);
            intent.putExtra(MainActivity.EXTRA_SHEET, sheet);
            startActivity(intent);
        } catch (AbcParser.AbcParseException e) {
            Toast.makeText(this, getString(R.string.editor_error_invalid, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.editor_delete_confirm, titleView.getText().toString()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.editor_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            final MusicSheet s = new MusicSheet("", "", "", "", editedFile);
                            MusicDB.getInstance(EditorActivity.this).deleteUserTune(s);
                            dirty = false;
                            finish();
                        } catch (IOException e) {
                            Toast.makeText(EditorActivity.this,
                                    getString(R.string.editor_error_save, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.editor_help_title)
                .setMessage(R.string.editor_help)
                .setPositiveButton(R.string.sheetInfoDialog_ok, null)
                .show();
    }

    private void closeWithConfirmation() {
        if (!dirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.editor_discard_confirm)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.editor_discard, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        menu.findItem(R.id.editorAction_delete).setVisible(editedFile != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.editorAction_save) save();
        else if (id == R.id.editorAction_play) tryTabs();
        else if (id == R.id.editorAction_delete) confirmDelete();
        else if (id == R.id.editorAction_help) showHelp();
        else if (id == android.R.id.home) closeWithConfirmation();
        return true;
    }

    @Override
    public void onBackPressed() {
        closeWithConfirmation();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }
}

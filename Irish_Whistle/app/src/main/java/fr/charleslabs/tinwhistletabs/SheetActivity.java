package fr.charleslabs.tinwhistletabs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import fr.charleslabs.tinwhistletabs.music.AbcParser;

/** Score of a tune, optionally with the tin whistle fingering under each note. */
public class SheetActivity extends AppCompatActivity {
    private static final String PREFERENCES = "settings";
    private static final String PREF_SCORE_TABS = "score_with_tabs";
    private static final int TAB_FONT_SIZE = 30;

    private String abc;
    private WebView webView;
    private boolean pageLoaded = false;
    private boolean showTabs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sheet);

        // Fetch intent
        final Intent intent = getIntent();
        if (!intent.hasExtra(TabActivity.EXTRA_ABC) ||
                !intent.hasExtra(TabActivity.EXTRA_SHEET_TITLE)) {
            finish();
            return;
        }
        abc = (String) intent.getSerializableExtra(TabActivity.EXTRA_ABC);
        final String title = (String) intent.getSerializableExtra(TabActivity.EXTRA_SHEET_TITLE);
        showTabs = preferences().getBoolean(PREF_SCORE_TABS, false);

        // Set action bar title
        try{
            ActionBar actionBar = this.getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getString(R.string.sheetActivity_title,title));
        }catch (Exception ignored){}

        // Render page
        webView = findViewById(R.id.sheetActivity_sheet);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView web, String url) {
                pageLoaded = true;
                render();
            }
        });
        webView.loadUrl("file:///android_asset/sheet.html");
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private void render() {
        if (!pageLoaded || abc == null) return;
        String toRender = abc;
        if (showTabs) {
            try {
                toRender = AbcParser.annotateWithTabs(abc, TAB_FONT_SIZE);
            } catch (AbcParser.AbcParseException e) {
                Toast.makeText(this, getString(R.string.sheet_tabs_error, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        }
        webView.evaluateJavascript("render(\"" + escape(toRender) + "\");", null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sheet_menu, menu);
        menu.findItem(R.id.sheetAction_tabs).setChecked(showTabs);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.sheetAction_tabs) {
            showTabs = !showTabs;
            item.setChecked(showTabs);
            preferences().edit().putBoolean(PREF_SCORE_TABS, showTabs).apply();
            render();
        }
        return true;
    }

    /**
     * escape()
     *
     * Escape a give String to make it safe to be printed or stored.
     *
     * @param s The input String.
     * @return The output String.
     **/
    public static String escape(String s){
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace(" ", "\\u2028")
                .replace(" ", "\\u2029");
    }

}

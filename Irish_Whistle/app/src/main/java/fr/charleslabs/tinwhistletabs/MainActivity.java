package fr.charleslabs.tinwhistletabs;

import android.content.DialogInterface;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;

import java.io.IOException;
import java.util.List;

import fr.charleslabs.tinwhistletabs.android.SheetsAdapter;
import fr.charleslabs.tinwhistletabs.dialogs.AppCreditsDialog;
import fr.charleslabs.tinwhistletabs.music.MusicDB;
import fr.charleslabs.tinwhistletabs.music.MusicSheet;

public class MainActivity extends AppCompatActivity  {
    public static final String EXTRA_SHEET= "fr.charleslabs.tinwhistletabs.SHEET";
    private SheetsAdapter adapter;
    private ListView listView;
    private SearchView searchView;
    private boolean firstResume = true;

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

    private void refreshList() {
        final List<MusicSheet> listData = MusicDB.getInstance(this).musicDB;
        adapter = new SheetsAdapter(this, listData, findViewById(R.id.noResultsFoundView));
        listView.setAdapter(adapter);
        if (searchView != null && searchView.getQuery().length() > 0)
            adapter.getFilter().filter(searchView.getQuery());
    }

    private void openEditor(MusicSheet sheet) {
        final Intent intent = new Intent(getApplicationContext(), EditorActivity.class);
        if (sheet != null) intent.putExtra(EditorActivity.EXTRA_SHEET, sheet);
        startActivity(intent);
    }

    private void showTuneActions(final MusicSheet sheet) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(sheet.getTitle());
        if (sheet.isUserTune()) {
            builder.setItems(new CharSequence[]{getString(R.string.main_longpress_edit),
                    getString(R.string.main_longpress_delete)}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) openEditor(sheet);
                    else confirmDelete(sheet);
                }
            });
        } else {
            builder.setItems(new CharSequence[]{getString(R.string.main_longpress_copy)},
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openEditor(sheet);
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
        if (item.getItemId() == R.id.mainAction_add) {
            openEditor(null);
        } else if (item.getItemId() == R.id.mainAction_about) {
            DialogFragment dialogFragment = new AppCreditsDialog();
            dialogFragment.show(getSupportFragmentManager(),"dialog");
        }

        return true;
    }
}

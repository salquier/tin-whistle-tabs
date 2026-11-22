package fr.charleslabs.tinwhistletabs.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import fr.charleslabs.tinwhistletabs.R;
import fr.charleslabs.tinwhistletabs.music.MusicSettings;
import fr.charleslabs.tinwhistletabs.music.WhistleTransposer;

/**
 * Choice of the player's whistle: the one recommended for the tune, or a given key (the
 * fingerings are then transposed so that the tune sounds at the same pitch).
 */
public class KeyDialog extends DialogFragment {
    /**
     * Callback interface.
     */
    public interface KeyChangeCallback{
        /** @param newKey a key of {@link MusicSettings#WHISTLE_KEYS}, or "" for recommended */
        void keyChangeCallback(String newKey);
    }

    private final String tuneWhistle;
    private final KeyChangeCallback caller;
    private int selectedItem;

    public KeyDialog(String tuneWhistle, String currentKey, KeyChangeCallback caller) {
        this.tuneWhistle = tuneWhistle;
        this.caller = caller;
        // Item 0 is "recommended", then the whistle keys
        selectedItem = WhistleTransposer.RECOMMENDED.equals(currentKey) ? 0
                : java.util.Arrays.asList(MusicSettings.WHISTLE_KEYS).indexOf(currentKey) + 1;
        if (selectedItem < 0) selectedItem = 0;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        final String[] items = new String[MusicSettings.WHISTLE_KEYS.length + 1];
        items[0] = getString(R.string.keyDialog_recommended, tuneWhistle);
        System.arraycopy(MusicSettings.WHISTLE_KEYS, 0, items, 1, MusicSettings.WHISTLE_KEYS.length);
        return new AlertDialog.Builder(requireActivity())
                .setTitle(getText(R.string.keyDialog_title))
                .setSingleChoiceItems(items, selectedItem,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        selectedItem = item;
                    }
                })
                .setCancelable(true)
                .setNegativeButton(getText(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dismiss();
                    }
                })
                .setPositiveButton(getText(R.string.dialog_set), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        caller.keyChangeCallback(selectedItem == 0 ? WhistleTransposer.RECOMMENDED
                                : MusicSettings.WHISTLE_KEYS[selectedItem - 1]);
                    }
                })
                .create();
    }
}

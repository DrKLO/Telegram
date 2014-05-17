/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.objects.VibrationSpeed;

import java.io.Serializable;

public class VibrationCountDialog extends DialogFragment {

    public static final int DEFAULT_VIBRATION_COUNT = 2;
    public static final String KEY_CURRENT_COUNT = "currentCount";
    public static final String KEY_LISTENER = "mListener";

    public static interface VibrationCountSelectionListener extends Serializable {
        public void onCountSelected(DialogFragment dialog, int selectedCount);
    }

    private int count;
    private VibrationCountSelectionListener mListener;

    public VibrationCountDialog() {
        count = DEFAULT_VIBRATION_COUNT;
    }

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);

        count = args.getInt(KEY_CURRENT_COUNT, DEFAULT_VIBRATION_COUNT);
        try {
            mListener = (VibrationCountSelectionListener) args.get(KEY_LISTENER);
        } catch (ClassCastException e) {}
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if(mListener == null) {
            // Verify that the host activity implements the callback interface
            try {
                mListener = (VibrationCountSelectionListener) activity;
            } catch (ClassCastException e) {
                // The activity doesn't implement the interface, throw exception
                throw new ClassCastException(activity.toString() + " must implement VibrationCountSelectionListener");
            }
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String counts[] = new String[10];
        for(int i = 0, l = counts.length; i < l; i++)
            counts[i] = String.valueOf(i + 1);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
            .setTitle(LocaleController.getString("VibrateCountTitle", R.string.VibrateCountTitle))
            .setSingleChoiceItems(counts, count - 1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mListener.onCountSelected(VibrationCountDialog.this, which + 1);

                    VibrationCountDialog.this.dismiss();
                }
            });

        return builder.create();
    }
}

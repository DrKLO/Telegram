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

public class VibrationSpeedDialog extends DialogFragment {

    public static final String KEY_CURRENT_SPEED = "currentSpeed";
    public static final String KEY_LISTENER = "mListener";

    public static interface VibrationSpeedSelectionListener extends Serializable {
        public void onSpeedSelected(DialogFragment dialog, VibrationSpeed selectedSpeed);
    }

    private VibrationSpeedSelectionListener mListener;
    private VibrationSpeed currentSpeed;

    public VibrationSpeedDialog() {
        this.currentSpeed = VibrationSpeed.getDefault();
    }

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);

        try {
            this.currentSpeed = (VibrationSpeed) args.get(KEY_CURRENT_SPEED);
        } catch (ClassCastException e) {}

        try {
            this.mListener = (VibrationSpeedSelectionListener) args.get(KEY_LISTENER);
        } catch (ClassCastException e) {}
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if(mListener == null) {
            // Verify that the host activity implements the callback interface
            try {
                mListener = (VibrationSpeedSelectionListener) activity;
            } catch (ClassCastException e) {
                // The activity doesn't implement the interface, throw exception
                throw new ClassCastException(activity.toString() + " must implement VibrationSpeedSelectionListener");
            }
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        VibrationSpeed[] vibrationSpeeds = VibrationSpeed.values();
        String speeds[] = new String[vibrationSpeeds.length];
        for(int i = 0, l = vibrationSpeeds.length; i < l; i++) {
            VibrationSpeed speed = vibrationSpeeds[i];
            speeds[i] = LocaleController.getString(speed.getLocaleKey(), speed.getResourceId());
        }

        int currentSpeedIndex = currentSpeed.getValue();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
            .setTitle(LocaleController.getString("VibrateSpeedTitle", R.string.VibrateSpeedTitle))
            .setSingleChoiceItems(speeds, currentSpeedIndex, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    VibrationSpeed speed = VibrationSpeed.fromValue(which);

                    mListener.onSpeedSelected(VibrationSpeedDialog.this, speed);

                    VibrationSpeedDialog.this.dismiss();
                }
            });

        return builder.create();
    }
}

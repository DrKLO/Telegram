package org.telegram.ui.Views;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Window;

import org.telegram.messenger.R;

public class ColorPickerDialog implements ColorPickerView.OnCenterPressedListener, DialogInterface.OnCancelListener {
	Dialog dialog;
	ColorPickerView colorPicker;
	OnColorSelectedListener onColorSelectedListener;
	int defaultColor;
	
	public ColorPickerDialog(Context context, int defaultColor, OnColorSelectedListener listener) {
		onColorSelectedListener = listener;
		this.defaultColor = defaultColor;
		
		dialog = new Dialog(context);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
        dialog.setContentView(R.layout.settings_color_dialog_layout);
		
        colorPicker = (ColorPickerView) dialog.findViewById(R.id.color_picker);
        colorPicker.setOldCenterColor(defaultColor);
        colorPicker.setColor(defaultColor);
		colorPicker.setOnCenterPressedListener(this);
	}
	
	public void show() {
		dialog.show();
	}
	
	public void onCancel(DialogInterface dialog) {
		onColorSelectedListener.onColorSelected(defaultColor);
	}
	
	@Override
    public void onCenterPressed(int color) {
        dialog.dismiss();
		onColorSelectedListener.onColorSelected(color);
    }

    public interface OnColorSelectedListener {
        public void onColorSelected(int color);
    }
}

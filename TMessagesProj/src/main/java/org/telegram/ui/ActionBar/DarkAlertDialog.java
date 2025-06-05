package org.telegram.ui.ActionBar;

import android.content.Context;

public class DarkAlertDialog extends AlertDialog {

	public DarkAlertDialog(Context context, int progressStyle) {
		super(context, progressStyle);
	}

	@Override
	protected int getThemedColor(int key) {
		if (key == Theme.key_dialogBackground) {
			return 0xFF262626;
		} else if (key == Theme.key_dialogTextBlack || key == Theme.key_dialogButton || key == Theme.key_dialogScrollGlow) {
			return 0xFFFFFFFF;
		}
		return super.getThemedColor(key);
	}

	public static class Builder extends AlertDialog.Builder {

		public Builder(Context context) {
			super(new DarkAlertDialog(context, 0));
		}

		public Builder(Context context, int progressViewStyle) {
			super(new DarkAlertDialog(context, progressViewStyle));
		}
	}
}

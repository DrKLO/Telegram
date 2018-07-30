package org.telegram.ui.ActionBar;

import android.content.Context;

/**
 * Created by grishka on 27.09.2017.
 */

public class DarkAlertDialog extends AlertDialog{
	public DarkAlertDialog(Context context, int progressStyle){
		super(context, progressStyle);
	}

	@Override
	protected int getThemeColor(String key){
		switch(key){
			case Theme.key_dialogBackground:
				return 0xFF262626;
			case Theme.key_dialogTextBlack:
			case Theme.key_dialogButton:
			case Theme.key_dialogScrollGlow:
				return 0xFFFFFFFF;
		}
		return super.getThemeColor(key);
	}

	public static class Builder extends AlertDialog.Builder{

		public Builder(Context context){
			super(new DarkAlertDialog(context, 0));
		}

		public Builder(Context context, int progressViewStyle){
			super(new DarkAlertDialog(context, progressViewStyle));
		}
	}
}

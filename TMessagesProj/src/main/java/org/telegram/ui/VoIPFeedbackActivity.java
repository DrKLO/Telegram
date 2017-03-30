package org.telegram.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BetterRatingView;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPFeedbackActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		super.onCreate(savedInstanceState);

		overridePendingTransition(0, 0);

		setContentView(new View(this));

		LinearLayout ll = new LinearLayout(this);
		ll.setOrientation(LinearLayout.VERTICAL);
		int pad = AndroidUtilities.dp(16);
		ll.setPadding(pad, pad, pad, pad);

		TextView text = new TextView(this);
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		text.setGravity(Gravity.CENTER);
		text.setText(LocaleController.getString("VoipRateCallAlert", R.string.VoipRateCallAlert));
		ll.addView(text);

		final BetterRatingView bar = new BetterRatingView(this);
		ll.addView(bar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

		final EditText commentBox = new EditText(this);
		commentBox.setHint(LocaleController.getString("VoipFeedbackCommentHint", R.string.VoipFeedbackCommentHint));
		commentBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		commentBox.setVisibility(View.GONE);
		commentBox.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		commentBox.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
		commentBox.setBackgroundDrawable(Theme.createEditTextDrawable(this, true));
		ll.addView(commentBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

		AlertDialog alert = new AlertDialog.Builder(this)
				.setTitle(LocaleController.getString("AppName", R.string.AppName))
				.setView(ll)
				.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						TLRPC.TL_phone_setCallRating req = new TLRPC.TL_phone_setCallRating();
						req.rating = bar.getRating();
						if (req.rating < 5)
							req.comment = commentBox.getText().toString();
						else
							req.comment="";
						req.peer = new TLRPC.TL_inputPhoneCall();
						req.peer.access_hash = getIntent().getLongExtra("call_access_hash", 0);
						req.peer.id = getIntent().getLongExtra("call_id", 0);
						ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
							@Override
							public void run(TLObject response, TLRPC.TL_error error) {
								if (response instanceof TLRPC.TL_updates) {
									TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
									MessagesController.getInstance().processUpdates(updates, false);
								}
							}
						});
						finish();
					}
				})
				.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				})
				.show();
		alert.setCanceledOnTouchOutside(true);
		alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		final View btn = alert.getButton(DialogInterface.BUTTON_POSITIVE);
		btn.setEnabled(false);
		bar.setOnRatingChangeListener(new BetterRatingView.OnRatingChangeListener() {
			@Override
			public void onRatingChanged(int rating) {
				btn.setEnabled(rating > 0);
				commentBox.setVisibility(rating < 5 && rating > 0 ? View.VISIBLE : View.GONE);
				if (commentBox.getVisibility() == View.GONE) {
					((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(commentBox.getWindowToken(), 0);
				}
			}
		});
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, 0);
	}
}

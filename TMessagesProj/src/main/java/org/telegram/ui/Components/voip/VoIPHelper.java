package org.telegram.ui.Components.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BetterRatingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.VoIPActivity;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class VoIPHelper{

	public static long lastCallTime=0;

	private static final int VOIP_SUPPORT_ID=4244000;

	public static void startCall(TLRPC.User user, final Activity activity, TLRPC.TL_userFull userFull){
		if(userFull!=null && userFull.phone_calls_private){
			new AlertDialog.Builder(activity)
					.setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
					.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable,
							ContactsController.formatName(user.first_name, user.last_name))))
					.setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
					.show();
			return;
		}
		if (ConnectionsManager.getInstance().getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
			boolean isAirplaneMode = Settings.System.getInt(activity.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
			AlertDialog.Builder bldr = new AlertDialog.Builder(activity)
					.setTitle(isAirplaneMode ? LocaleController.getString("VoipOfflineAirplaneTitle", R.string.VoipOfflineAirplaneTitle) : LocaleController.getString("VoipOfflineTitle", R.string.VoipOfflineTitle))
					.setMessage(isAirplaneMode ? LocaleController.getString("VoipOfflineAirplane", R.string.VoipOfflineAirplane) : LocaleController.getString("VoipOffline", R.string.VoipOffline))
					.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
			if (isAirplaneMode) {
				final Intent settingsIntent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
				if (settingsIntent.resolveActivity(activity.getPackageManager()) != null) {
					bldr.setNeutralButton(LocaleController.getString("VoipOfflineOpenSettings", R.string.VoipOfflineOpenSettings), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							activity.startActivity(settingsIntent);
						}
					});
				}
			}
			bldr.show();
			return;
		}
		if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
		} else {
			initiateCall(user, activity);
		}
	}

	private static void initiateCall(final TLRPC.User user, final Activity activity) {
		if (activity == null || user==null) {
			return;
		}
		if (VoIPService.getSharedInstance() != null) {
			TLRPC.User callUser = VoIPService.getSharedInstance().getUser();
			if (callUser.id != user.id) {
				new AlertDialog.Builder(activity)
						.setTitle(LocaleController.getString("VoipOngoingAlertTitle", R.string.VoipOngoingAlertTitle))
						.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("VoipOngoingAlert", R.string.VoipOngoingAlert,
								ContactsController.formatName(callUser.first_name, callUser.last_name),
								ContactsController.formatName(user.first_name, user.last_name))))
						.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (VoIPService.getSharedInstance() != null) {
									VoIPService.getSharedInstance().hangUp(new Runnable() {
										@Override
										public void run() {
											doInitiateCall(user, activity);
										}
									});
								} else {
									doInitiateCall(user, activity);
								}
							}
						})
						.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
						.show();
			} else {
				activity.startActivity(new Intent(activity, VoIPActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			}
		} else if(VoIPService.callIShouldHavePutIntoIntent==null) {
			doInitiateCall(user, activity);
		}
	}

	private static void doInitiateCall(TLRPC.User user, Activity activity) {
		if (activity == null || user==null) {
			return;
		}
		if(System.currentTimeMillis()-lastCallTime<2000)
			return;
		lastCallTime=System.currentTimeMillis();
		Intent intent = new Intent(activity, VoIPService.class);
		intent.putExtra("user_id", user.id);
		intent.putExtra("is_outgoing", true);
		intent.putExtra("start_incall_activity", true);
		activity.startService(intent);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public static void permissionDenied(final Activity activity, final Runnable onFinish){
		if(!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
			AlertDialog dlg=new AlertDialog.Builder(activity)
					.setTitle(LocaleController.getString("AppName", R.string.AppName))
					.setMessage(LocaleController.getString("VoipNeedMicPermission", R.string.VoipNeedMicPermission))
					.setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
					.setNegativeButton(LocaleController.getString("Settings", R.string.Settings), new DialogInterface.OnClickListener(){
						@Override
						public void onClick(DialogInterface dialog, int which){
							Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
							Uri uri=Uri.fromParts("package", activity.getPackageName(), null);
							intent.setData(uri);
							activity.startActivity(intent);
						}
					})
					.show();
			dlg.setOnDismissListener(new DialogInterface.OnDismissListener(){
				@Override
				public void onDismiss(DialogInterface dialog){
					if(onFinish!=null)
						onFinish.run();
				}
			});
		}
	}

	public static File getLogsDir(){
		//File logsDir=new File(ApplicationLoader.applicationContext.getExternalCacheDir(), "voip_logs");
		File logsDir=new File(ApplicationLoader.applicationContext.getCacheDir(), "voip_logs");
		if(!logsDir.exists())
			logsDir.mkdirs();
		return logsDir;
	}

	public static boolean canRateCall(TLRPC.TL_messageActionPhoneCall call){
		if(!(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) && !(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed)){
			SharedPreferences prefs=ApplicationLoader.applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE);
			Set<String> hashes=prefs.getStringSet("calls_access_hashes", (Set<String>)Collections.EMPTY_SET);
			for(String hash:hashes){
				String[] d=hash.split(" ");
				if(d.length<2)
					continue;
				if(d[0].equals(call.call_id+"")){
					return true;
				}
			}
		}
		return false;
	}

	public static void showRateAlert(Context context, TLRPC.TL_messageActionPhoneCall call){
		SharedPreferences prefs=context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
		Set<String> hashes=prefs.getStringSet("calls_access_hashes", (Set<String>)Collections.EMPTY_SET);
		for(String hash:hashes){
			String[] d=hash.split(" ");
			if(d.length<2)
				continue;
			if(d[0].equals(call.call_id+"")){
				try{
					long accessHash=Long.parseLong(d[1]);
					showRateAlert(context, null, call.call_id, accessHash);
				}catch(Exception x){}
				return;
			}
		}
	}

	public static void showRateAlert(final Context context, final Runnable onDismiss, final long callID, final long accessHash){
		final File log=new File(getLogsDir(), callID+".log");
		LinearLayout alertView=new LinearLayout(context);
		alertView.setOrientation(LinearLayout.VERTICAL);

		int pad = AndroidUtilities.dp(16);
		alertView.setPadding(pad, pad, pad, 0);

		TextView text = new TextView(context);
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		text.setGravity(Gravity.CENTER);
		text.setText(LocaleController.getString("VoipRateCallAlert", R.string.VoipRateCallAlert));
		alertView.addView(text);

		final BetterRatingView bar = new BetterRatingView(context);
		alertView.addView(bar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

		final EditText commentBox = new EditText(context);
		commentBox.setHint(LocaleController.getString("CallReportHint", R.string.CallReportHint));
		commentBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		commentBox.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		commentBox.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
		commentBox.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
		commentBox.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
		commentBox.setTextSize(18);
		commentBox.setVisibility(View.GONE);
		alertView.addView(commentBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 0));

		final boolean[] includeLogs={true};
		final CheckBoxCell checkbox=new CheckBoxCell(context, true);
		View.OnClickListener checkClickListener=new View.OnClickListener(){
			@Override
			public void onClick(View v){
				includeLogs[0]=!includeLogs[0];
				checkbox.setChecked(includeLogs[0], true);
			}
		};
		checkbox.setText(LocaleController.getString("CallReportIncludeLogs", R.string.CallReportIncludeLogs), null, true, false);
		checkbox.setClipToPadding(false);
		checkbox.setOnClickListener(checkClickListener);
		alertView.addView(checkbox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8, 0, -8, 0));

		final TextView logsText = new TextView(context);
		logsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		logsText.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
		logsText.setText(LocaleController.getString("CallReportLogsExplain", R.string.CallReportLogsExplain));
		logsText.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
		logsText.setOnClickListener(checkClickListener);
		alertView.addView(logsText);

		checkbox.setVisibility(View.GONE);
		logsText.setVisibility(View.GONE);
		if(!log.exists()){
			includeLogs[0]=false;
		}

		AlertDialog alert=new AlertDialog.Builder(context)
				.setTitle(LocaleController.getString("CallMessageReportProblem", R.string.CallMessageReportProblem))
				.setView(alertView)
				.setPositiveButton(LocaleController.getString("Send", R.string.Send), new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface dialog, int which){
						final TLRPC.TL_phone_setCallRating req = new TLRPC.TL_phone_setCallRating();
						req.rating = bar.getRating();
						if (req.rating < 5)
							req.comment = commentBox.getText().toString();
						else
							req.comment="";
						req.peer = new TLRPC.TL_inputPhoneCall();
						req.peer.access_hash = accessHash;
						req.peer.id = callID;
						ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
							@Override
							public void run(TLObject response, TLRPC.TL_error error) {
								if (response instanceof TLRPC.TL_updates) {
									TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
									MessagesController.getInstance().processUpdates(updates, false);
									if(includeLogs[0] && log.exists() && req.rating<4){
										SendMessagesHelper.prepareSendingDocument(log.getAbsolutePath(), log.getAbsolutePath(), null, "text/plain", VOIP_SUPPORT_ID, null, null);
										Toast.makeText(context, LocaleController.getString("CallReportSent", R.string.CallReportSent), Toast.LENGTH_LONG).show();
									}
								}
							}
						});
						//SendMessagesHelper.getInstance().sendMessage(commentBox.getText().toString(), VOIP_SUPPORT_ID, null, null, true, null, null, null);
					}
				})
				.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
				.setOnDismissListener(new DialogInterface.OnDismissListener(){
					@Override
					public void onDismiss(DialogInterface dialog){
						if(onDismiss!=null)
							onDismiss.run();
					}
				})
				.show();

		final View btn = alert.getButton(DialogInterface.BUTTON_POSITIVE);
		btn.setEnabled(false);
		bar.setOnRatingChangeListener(new BetterRatingView.OnRatingChangeListener() {
			@Override
			public void onRatingChanged(int rating) {
				btn.setEnabled(rating > 0);
				commentBox.setHint(rating<4 ? LocaleController.getString("CallReportHint", R.string.CallReportHint) : LocaleController.getString("VoipFeedbackCommentHint", R.string.VoipFeedbackCommentHint));
				commentBox.setVisibility(rating < 5 && rating > 0 ? View.VISIBLE : View.GONE);
				if (commentBox.getVisibility() == View.GONE) {
					((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(commentBox.getWindowToken(), 0);
				}
				if(log.exists()){
					checkbox.setVisibility(rating<4 ? View.VISIBLE : View.GONE);
					logsText.setVisibility(rating<4 ? View.VISIBLE : View.GONE);
				}
			}
		});
	}

	public static void upgradeP2pSetting(){
		SharedPreferences prefs=ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
		if(prefs.contains("calls_p2p")){
			SharedPreferences.Editor e=prefs.edit();
			if(!prefs.getBoolean("calls_p2p", true)){
				e.putInt("calls_p2p_new", 2);
			}
			e.remove("calls_p2p").apply();
		}
	}
}

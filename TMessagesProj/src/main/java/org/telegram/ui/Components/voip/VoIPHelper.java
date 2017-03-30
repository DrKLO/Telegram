package org.telegram.ui.Components.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.VoIPActivity;

public class VoIPHelper{

	private static long lastCallRequestTime=0;

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
		if(System.currentTimeMillis()-lastCallRequestTime<1000)
			return;
		lastCallRequestTime=System.currentTimeMillis();
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
}

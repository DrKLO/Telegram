package org.telegram.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.util.ArrayList;

@TargetApi(23)
public class VoIPPermissionActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		VoIPService service = VoIPService.getSharedInstance();
		boolean isVideoCall = service != null && service.privateCall != null && service.privateCall.video;

		ArrayList<String> permissions = new ArrayList<>();
		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO);
		}
		if (isVideoCall && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.CAMERA);
		}
		if (!permissions.isEmpty()) {
			try {
				requestPermissions(permissions.toArray(new String[0]), isVideoCall ? 102 : 101);
			} catch (Exception e) {
				FileLog.e(e);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 101 || requestCode == 102) {
			boolean allGranted = true;
			for (int a = 0; a < grantResults.length; a++) {
				if (grantResults[a] != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}
			if (grantResults.length > 0 && allGranted) {
				if (VoIPService.getSharedInstance() != null) {
					VoIPService.getSharedInstance().acceptIncomingCall();
				}
				finish();
				startActivity(new Intent(this, LaunchActivity.class).setAction("voip"));
			} else {
				if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
					if (VoIPService.getSharedInstance() != null) {
						VoIPService.getSharedInstance().declineIncomingCall();
					}
					VoIPHelper.permissionDenied(this, this::finish, requestCode);
				} else {
					finish();
				}
			}
		}
	}
}

package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.Components.voip.VoIPHelper;

/**
 * Created by grishka on 22.11.16.
 */

public class VoIPPermissionActivity extends Activity{
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
		if(requestCode==101){
			if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
				if(VoIPService.getSharedInstance()!=null)
					VoIPService.getSharedInstance().acceptIncomingCall();
				finish();
				startActivity(new Intent(this, VoIPActivity.class));
			}else{
				if(!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
					if(VoIPService.getSharedInstance()!=null)
						VoIPService.getSharedInstance().declineIncomingCall();
					VoIPHelper.permissionDenied(this, new Runnable(){
						@Override
						public void run(){
							finish();
						}
					});
					return;
				}else{
					finish();
				}
			}
		}
	}
}

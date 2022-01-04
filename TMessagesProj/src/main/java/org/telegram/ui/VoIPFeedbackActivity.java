package org.telegram.ui;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import org.telegram.ui.Components.voip.VoIPHelper;

public class VoIPFeedbackActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		super.onCreate(savedInstanceState);

		overridePendingTransition(0, 0);

		setContentView(new View(this));

		VoIPHelper.showRateAlert(this, this::finish, getIntent().getBooleanExtra("call_video", false), getIntent().getLongExtra("call_id", 0), getIntent().getLongExtra("call_access_hash", 0), getIntent().getIntExtra("account", 0), false);
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, 0);
	}
}

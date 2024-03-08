package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.telegram.ui.LaunchActivity;

public class OpenAttachedMenuBotReceiver extends Activity {

    public static String ACTION = "com.tmessages.openshortcutbot";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        if (intent.getAction() == null || !intent.getAction().startsWith(ACTION)) {
            finish();
            return;
        }
        try {
            long botId = intent.getLongExtra("botId", 0);
            if (botId == 0) {
                return;
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return;
        }
        Intent intent2 = new Intent(this, LaunchActivity.class);
        intent2.setAction(intent.getAction());
        intent2.putExtras(intent);
        startActivity(intent2);
        finish();
    }
}

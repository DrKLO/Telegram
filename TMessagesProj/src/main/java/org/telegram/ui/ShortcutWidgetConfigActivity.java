package org.telegram.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ShortcutWidgetProvider;

public class ShortcutWidgetConfigActivity extends ExternalActionActivity {

    private int creatingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected boolean handleIntent(Intent intent, boolean isNew, boolean restore, boolean fromPassword, int intentAccount, int state) {
        if (!checkPasscode(intent, isNew, restore, fromPassword, intentAccount, state)) {
            return false;
        }
        Bundle extras = intent.getExtras();
        if (extras != null) {
            creatingAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (creatingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", 10);
            args.putBoolean("allowSwitchAccount", true);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param) -> {
                AccountInstance.getInstance(fragment1.getCurrentAccount()).getMessagesStorage().putWidgetDialogs(creatingAppWidgetId, dids);

                SharedPreferences preferences = ShortcutWidgetConfigActivity.this.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
                preferences.edit().putInt("account" + creatingAppWidgetId, fragment1.getCurrentAccount()).commit();

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ShortcutWidgetConfigActivity.this);
                ShortcutWidgetProvider.updateWidget(ShortcutWidgetConfigActivity.this, appWidgetManager, creatingAppWidgetId);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, creatingAppWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            });

            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout.fragmentsStack.isEmpty()) {
                    layersActionBarLayout.addFragmentToStack(fragment);
                }
            } else {
                if (actionBarLayout.fragmentsStack.isEmpty()) {
                    actionBarLayout.addFragmentToStack(fragment);
                }
            }
            if (!AndroidUtilities.isTablet()) {
                backgroundTablet.setVisibility(View.GONE);
            }
            actionBarLayout.showLastFragment();
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.showLastFragment();
            }
            intent.setAction(null);
        } else {
            finish();
        }
        return true;
    }
}

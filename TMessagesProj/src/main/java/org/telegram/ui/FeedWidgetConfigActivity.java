package org.telegram.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FeedWidgetProvider;

public class FeedWidgetConfigActivity extends ExternalActionActivity {

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
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY);
            args.putBoolean("allowSwitchAccount", true);
            args.putBoolean("checkCanWrite", false);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, topicsFragment) -> {
                AccountInstance.getInstance(fragment1.getCurrentAccount()).getMessagesStorage().putWidgetDialogs(creatingAppWidgetId, dids);

                SharedPreferences preferences = FeedWidgetConfigActivity.this.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("account" + creatingAppWidgetId, fragment1.getCurrentAccount());
                editor.putLong("dialogId" + creatingAppWidgetId, dids.get(0).dialogId);
                editor.commit();

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(FeedWidgetConfigActivity.this);
                FeedWidgetProvider.updateWidget(FeedWidgetConfigActivity.this, appWidgetManager, creatingAppWidgetId);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, creatingAppWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
                return true;
            });

            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout.getFragmentStack().isEmpty()) {
                    layersActionBarLayout.addFragmentToStack(fragment);
                }
            } else {
                if (actionBarLayout.getFragmentStack().isEmpty()) {
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

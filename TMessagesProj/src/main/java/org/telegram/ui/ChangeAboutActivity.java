/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

public class ChangeAboutActivity extends BaseFragment {

    private EditText aboutField;
    private View doneButton;

    private final static int done_button = 1;

    @Override
    public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("EditBio", R.string.EditBio));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        if (aboutField.getText().length() != 0) {
                            saveAbout();
                            finishFragment();
                        }
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            //doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
            Drawable done = getParentActivity().getResources().getDrawable(R.drawable.ic_done);
            done.setColorFilter(themePrefs.getInt("prefHeaderIconsColor", 0xffffffff), PorterDuff.Mode.SRC_IN);
            doneButton = menu.addItemWithWidth(done_button, done, AndroidUtilities.dp(56));

            TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
            if (user == null) {
                user = UserConfig.getCurrentUser();
            }

        LinearLayout linearLayout = new LinearLayout(context);
        fragmentView = linearLayout;
            fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ((LinearLayout) fragmentView).setOrientation(LinearLayout.VERTICAL);
            fragmentView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

        aboutField = new EditText(context);
        aboutField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        aboutField.setHintTextColor(0xff979797);
        aboutField.setTextColor(0xff212121);
        aboutField.setPadding(0, 0, 0, 10);
        aboutField.getBackground().setColorFilter(AndroidUtilities.getIntColor("themeColor"), PorterDuff.Mode.SRC_IN);
        aboutField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("plusconfig", Activity.MODE_PRIVATE);
        boolean showEmojiBtn = preferences.getBoolean("showEmojiKbBtn", false);
        aboutField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        if(showEmojiBtn){
            aboutField.setInputType(aboutField.getInputType() | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        }
        //aboutField.setImeOptions(EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(250);
        aboutField.setFilters(inputFilters);
        aboutField.setHint(LocaleController.getString("Info", R.string.Info));
            AndroidUtilities.clearCursorDrawable(aboutField);
        linearLayout.addView(aboutField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 24, 24, 0));
        aboutField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });

            if (user != null) {
                aboutField.setText(MessagesController.getInstance().getUserAbout(user.id));
            }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            aboutField.requestFocus();
            AndroidUtilities.showKeyboard(aboutField);
        }
        updateTheme();
    }

    private void updateTheme(){
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int def = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
        actionBar.setBackgroundColor(themePrefs.getInt("prefHeaderColor", def));
        actionBar.setTitleColor(themePrefs.getInt("prefHeaderTitleColor", 0xffffffff));

        Drawable back = getParentActivity().getResources().getDrawable(R.drawable.ic_ab_back);
        back.setColorFilter(themePrefs.getInt("prefHeaderIconsColor", 0xffffffff), PorterDuff.Mode.MULTIPLY);
        actionBar.setBackButtonDrawable(back);
    }

    private void saveAbout() {
        TLRPC.User currentUser = UserConfig.getCurrentUser();
        String about = MessagesController.getInstance().getUserAbout(currentUser.id);
        if (aboutField.getText() == null) {
            return;
        }
        String newAbout = aboutField.getText().toString();
        if (about != null && about.equals(newAbout)) {
            return;
        }
        TLRPC.TL_account_updateProfile req = new TLRPC.TL_account_updateProfile();
        req.flags |= 4;
        req.about = newAbout;

        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if(error != null){
                    Log.e("ChangeNameAbout","error " + error.toString());
                }
                if(response != null){
                    Log.e("ChangeNameAbout","response " + response.toString());
                    MessagesController.getInstance().loadFullUser(UserConfig.getCurrentUser(), classGuid, true);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.userInfoDidLoaded, UserConfig.getCurrentUser().id);
                            UserConfig.saveConfig(true);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (aboutField != null) {
                        aboutField.requestFocus();
        AndroidUtilities.showKeyboard(aboutField);
    }
}
            }, 100);
        }
    }
}

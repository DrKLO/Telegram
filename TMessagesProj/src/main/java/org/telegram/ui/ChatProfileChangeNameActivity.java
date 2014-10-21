/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.SettingsSectionLayout;

public class ChatProfileChangeNameActivity extends BaseFragment {
    private EditText firstNameField;
    private View headerLabelView;
    private int chat_id;
    private View doneButton;

    public ChatProfileChangeNameActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        chat_id = getArguments().getInt("chat_id", 0);
        return true;
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setCustomView(R.layout.settings_do_action_layout);

            Button cancelButton = (Button)actionBarLayer.findViewById(R.id.cancel_button);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finishFragment();
                }
            });
            doneButton = actionBarLayer.findViewById(R.id.done_button);
            doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (firstNameField.getText().length() != 0) {
                        saveName();
                        finishFragment();
                    }
                }
            });

            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            TextView textView = (TextView)doneButton.findViewById(R.id.done_button_text);
            textView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            TLRPC.Chat currentChat = MessagesController.getInstance().getChat(chat_id);

            fragmentView = new LinearLayout(inflater.getContext());
            fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fragmentView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), 0);
            ((LinearLayout) fragmentView).setOrientation(LinearLayout.VERTICAL);

            SettingsSectionLayout settingsSectionLayout = new SettingsSectionLayout(inflater.getContext());
            ((LinearLayout) fragmentView).addView(settingsSectionLayout);

            firstNameField = new EditText(inflater.getContext());
            firstNameField.setText(currentChat.title);
            firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            firstNameField.setHintTextColor(0xffa3a3a3);
            firstNameField.setTextColor(0xff000000);
            firstNameField.setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            firstNameField.setMaxLines(3);
            firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            firstNameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
            AndroidUtilities.clearCursorDrawable(firstNameField);
            firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });
            if (LocaleController.isRTL) {
                firstNameField.setGravity(Gravity.RIGHT);
            }

            ((LinearLayout) fragmentView).addView(firstNameField);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)firstNameField.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(15);
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            firstNameField.setLayoutParams(layoutParams);

            if (chat_id > 0) {
                settingsSectionLayout.setText(LocaleController.getString("EnterGroupNameTitle", R.string.EnterGroupNameTitle));
                firstNameField.setHint(LocaleController.getString("GroupName", R.string.GroupName));
            } else {
                settingsSectionLayout.setText(LocaleController.getString("EnterListName", R.string.EnterListName).toUpperCase());
                firstNameField.setHint(LocaleController.getString("EnterListName", R.string.EnterListName));
            }
            firstNameField.setSelection(firstNameField.length());
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        firstNameField.requestFocus();
        AndroidUtilities.showKeyboard(firstNameField);
    }

    private void saveName() {
        MessagesController.getInstance().changeChatTitle(chat_id, firstNameField.getText().toString());
    }
}

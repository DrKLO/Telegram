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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.ActionBar.BaseFragment;

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

            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel));
            TextView textView = (TextView)doneButton.findViewById(R.id.done_button_text);
            textView.setText(LocaleController.getString("Done", R.string.Done));

            fragmentView = inflater.inflate(R.layout.chat_profile_change_name_layout, container, false);

            TLRPC.Chat currentChat = MessagesController.getInstance().chats.get(chat_id);

            firstNameField = (EditText)fragmentView.findViewById(R.id.first_name_field);
            firstNameField.setHint(LocaleController.getString("GroupName", R.string.GroupName));
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
            firstNameField.setText(currentChat.title);
            firstNameField.setSelection(firstNameField.length());

            TextView headerLabel = (TextView)fragmentView.findViewById(R.id.settings_section_text);
            headerLabel.setText(LocaleController.getString("EnterGroupNameTitle", R.string.EnterGroupNameTitle));
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
            Utilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        firstNameField.requestFocus();
        Utilities.showKeyboard(firstNameField);
    }

    private void saveName() {
        MessagesController.getInstance().changeChatTitle(chat_id, firstNameField.getText().toString());
    }
}

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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.ActionBar.BaseFragment;

public class SettingsChangeNameActivity extends BaseFragment {
    private EditText firstNameField;
    private EditText lastNameField;
    private View headerLabelView;
    private View doneButton;

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

            fragmentView = inflater.inflate(R.layout.settings_change_name_layout, container, false);

            TLRPC.User user = MessagesController.getInstance().users.get(UserConfig.clientUserId);
            if (user == null) {
                user = UserConfig.currentUser;
            }

            firstNameField = (EditText)fragmentView.findViewById(R.id.first_name_field);
            firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
            firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        lastNameField.requestFocus();
                        lastNameField.setSelection(lastNameField.length());
                        return true;
                    }
                    return false;
                }
            });
            lastNameField = (EditText)fragmentView.findViewById(R.id.last_name_field);
            lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
            lastNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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
                firstNameField.setText(user.first_name);
                firstNameField.setSelection(firstNameField.length());
                lastNameField.setText(user.last_name);
            }

            TextView headerLabel = (TextView)fragmentView.findViewById(R.id.settings_section_text);
            headerLabel.setText(LocaleController.getString("YourFirstNameAndLastName", R.string.YourFirstNameAndLastName));
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

    private void saveName() {
        TLRPC.TL_account_updateProfile req = new TLRPC.TL_account_updateProfile();
        if (UserConfig.currentUser == null || lastNameField.getText() == null || firstNameField.getText() == null) {
            return;
        }
        UserConfig.currentUser.first_name = req.first_name = firstNameField.getText().toString();
        UserConfig.currentUser.last_name = req.last_name = lastNameField.getText().toString();
        TLRPC.User user = MessagesController.getInstance().users.get(UserConfig.clientUserId);
        if (user != null) {
            user.first_name = req.first_name;
            user.last_name = req.last_name;
        }
        UserConfig.saveConfig(true);
        NotificationCenter.getInstance().postNotificationName(MessagesController.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    @Override
    public void onOpenAnimationEnd() {
        firstNameField.requestFocus();
        Utilities.showKeyboard(firstNameField);
    }
}

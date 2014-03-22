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
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;

public class ContactAddActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private int user_id;
    private String phone = null;
    private View doneButton;
    private EditText firstNameField;
    private EditText lastNameField;
    private BackupImageView avatarImage;
    private TextView onlineText;
    private TextView phoneText;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        user_id = getArguments().getInt("user_id", 0);
        phone = getArguments().getString("phone");
        TLRPC.User user = MessagesController.getInstance().users.get(user_id);
        return user != null;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.contact_add_layout, container, false);

            TLRPC.User user = MessagesController.getInstance().users.get(user_id);
            if (user.phone == null) {
                if (phone != null) {
                    user.phone = PhoneFormat.stripExceptNumbers(phone);
                }
            }

            onlineText = (TextView)fragmentView.findViewById(R.id.settings_online);
            avatarImage = (BackupImageView)fragmentView.findViewById(R.id.settings_avatar_image);
            phoneText = (TextView)fragmentView.findViewById(R.id.settings_name);
            Typeface typeface = Utilities.getTypeface("fonts/rmedium.ttf");
            phoneText.setTypeface(typeface);

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

            updateAvatarLayout();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void updateAvatarLayout() {
        if (phoneText == null) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance().users.get(user_id);
        if (user == null) {
            return;
        }
        phoneText.setText(PhoneFormat.getInstance().format("+" + user.phone));
        if (user.status == null) {
            onlineText.setText(LocaleController.getString("Offline", R.string.Offline));
        } else {
            int currentTime = ConnectionsManager.getInstance().getCurrentTime();
            if (user.status.expires > currentTime) {
                onlineText.setText(LocaleController.getString("Online", R.string.Online));
            } else {
                if (user.status.expires <= 10000) {
                    onlineText.setText(LocaleController.getString("Invisible", R.string.Invisible));
                } else {
                    onlineText.setText(Utilities.formatDateOnline(user.status.expires));
                }
            }
        }

        TLRPC.FileLocation photo = null;
        if (user.photo != null) {
            photo = user.photo.photo_small;
        }
        avatarImage.setImage(photo, "50_50", Utilities.getUserAvatarForId(user.id));
    }

    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateAvatarLayout();
            }
        }
    }

    @Override
    public boolean canApplyUpdateStatus() {
        return false;
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }

        actionBar.setCustomView(R.layout.settings_do_action_layout);
        Button cancelButton = (Button)actionBar.getCustomView().findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFragment();
            }
        });
        doneButton = actionBar.getCustomView().findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (firstNameField.getText().length() != 0) {
                    TLRPC.User user = MessagesController.getInstance().users.get(user_id);
                    user.first_name = firstNameField.getText().toString();
                    user.last_name = lastNameField.getText().toString();
                    ContactsController.getInstance().addContact(user);
                    finishFragment();
                    NotificationCenter.getInstance().postNotificationName(MessagesController.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                }
            }
        });

        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel));
        TextView textView = (TextView)doneButton.findViewById(R.id.done_button_text);
        textView.setText(LocaleController.getString("Done", R.string.Done));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) {
            return;
        }
        ((LaunchActivity)parentActivity).updateActionBar();

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            Utilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim != 0) {
            Animation anim = AnimationUtils.loadAnimation(getActivity(), nextAnim);

            anim.setAnimationListener(new Animation.AnimationListener() {

                public void onAnimationStart(Animation animation) {
                    ContactAddActivity.this.onAnimationStart();
                }

                public void onAnimationRepeat(Animation animation) {

                }

                public void onAnimationEnd(Animation animation) {
                    ContactAddActivity.this.onAnimationEnd();
                    firstNameField.requestFocus();
                    Utilities.showKeyboard(firstNameField);
                }
            });

            return anim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }
}

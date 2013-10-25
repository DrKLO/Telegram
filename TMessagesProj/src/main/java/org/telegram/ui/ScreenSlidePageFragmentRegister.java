/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.AvatarUpdater;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.SlideFragment;

import java.util.ArrayList;
import java.util.HashMap;

public class ScreenSlidePageFragmentRegister extends SlideFragment {
    private EditText firstNameField;
    private EditText lastNameField;
    private String requestPhone;
    private String phoneHash;
    private String phoneCode;
    private BackupImageView avatarImage;
    public AvatarUpdater avatarUpdater = new AvatarUpdater();
    private TLRPC.PhotoSize avatarPhoto = null;
    private TLRPC.PhotoSize avatarPhotoBig = null;
    private HashMap<String, String> currentParams;

    public void resetAvatar() {
        avatarPhoto = null;
        avatarPhotoBig = null;
        if (avatarImage != null) {
            avatarImage.setImageResource(R.drawable.user_placeholder);
        }
    }

    @SuppressWarnings("unchecked")
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.login_register_layout, container, false);

        avatarUpdater.parentActivity = (Activity)delegate;
        avatarUpdater.delegate = new AvatarUpdater.AvatarUpdaterDelegate() {
            @Override
            public void didUploadedPhoto(TLRPC.TL_inputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big) {
                avatarPhotoBig = big;
                avatarPhoto = small;
                if (avatarImage != null) {
                    avatarImage.setImage(small.location, null, R.drawable.user_placeholder);
                }
            }
        };
        avatarUpdater.returnOnly = true;

        Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
        TextView registerInfo = (TextView)rootView.findViewById(R.id.login_register_info);
        registerInfo.setTypeface(typeface);
        ImageButton avatarButton = (ImageButton) rootView.findViewById(R.id.settings_change_avatar_button);
        firstNameField = (EditText)rootView.findViewById(R.id.login_first_name_field);
        lastNameField = (EditText)rootView.findViewById(R.id.login_last_name_field);
        avatarImage = (BackupImageView)rootView.findViewById(R.id.settings_avatar_image);

        firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    return true;
                }
                return false;
            }
        });

        avatarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getSherlockActivity());

                CharSequence[] items;

                if (avatarPhoto != null) {
                    items = new CharSequence[]{getString(R.string.FromCamera), getString(R.string.FromGalley), getString(R.string.DeletePhoto)};
                } else {
                    items = new CharSequence[]{getString(R.string.FromCamera), getString(R.string.FromGalley)};
                }

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 0) {
                            avatarUpdater.openCamera();
                        } else if (i == 1) {
                            avatarUpdater.openGallery();
                        } else if (i == 2) {
                            resetAvatar();
                        }
                    }
                });
                builder.show().setCanceledOnTouchOutside(true);
            }
        });

        if (savedInstanceState != null) {
            currentParams = (HashMap<String, String>)savedInstanceState.getSerializable("params");
            if (currentParams != null) {
                setParams(currentParams);
            }
            firstNameField.setText(savedInstanceState.getString("firstName"));
            lastNameField.setText(savedInstanceState.getString("lastName"));
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (avatarUpdater != null) {
            avatarUpdater.clear();
            avatarUpdater = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (avatarUpdater != null) {
            avatarUpdater.clear();
            avatarUpdater = null;
        }
        delegate = null;
    }

    @Override
    public String getHeaderName() {
        return getResources().getString(R.string.YourName);
    }

    @Override
    public void setParams(HashMap<String, String> params) {
        firstNameField.setText("");
        lastNameField.setText("");
        requestPhone = params.get("phoneFormated");
        phoneHash = params.get("phoneHash");
        phoneCode = params.get("code");
        currentParams = params;
        resetAvatar();
    }

    @Override
    public void onNextPressed() {
        TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
        req.phone_code = phoneCode;
        req.phone_code_hash = phoneHash;
        req.phone_number = requestPhone;
        req.first_name = firstNameField.getText().toString();
        req.last_name = lastNameField.getText().toString();
        delegate.needShowProgress();
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                delegate.needHideProgress();
                if (error == null) {
                    final TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization)response;
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_userSelf user = (TLRPC.TL_userSelf)res.user;
                            UserConfig.clearConfig();
                            MessagesStorage.Instance.cleanUp();
                            MessagesController.Instance.cleanUp();
                            ConnectionsManager.Instance.cleanUp();
                            UserConfig.currentUser = user;
                            UserConfig.clientActivated = true;
                            UserConfig.clientUserId = user.id;
                            UserConfig.saveConfig();
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(user);
                            MessagesStorage.Instance.putUsersAndChats(users, null, true, true);
                            MessagesController.Instance.uploadAndApplyUserAvatar(avatarPhotoBig);
                            MessagesController.Instance.users.put(res.user.id, res.user);
                            MessagesController.Instance.checkAppAccount();
                            delegate.needFinishActivity();
                        }
                    });
                } else {
                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                        delegate.needShowAlert(Utilities.applicationContext.getString(R.string.InvalidPhoneNumber));
                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        delegate.needShowAlert(Utilities.applicationContext.getString(R.string.InvalidCode));
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        delegate.needShowAlert(Utilities.applicationContext.getString(R.string.CodeExpired));
                    } else if (error.text.contains("FIRSTNAME_INVALID")) {
                        delegate.needShowAlert(Utilities.applicationContext.getString(R.string.FirstName));
                    } else if (error.text.contains("LASTNAME_INVALID")) {
                        delegate.needShowAlert(Utilities.applicationContext.getString(R.string.LastName));
                    } else {
                        delegate.needShowAlert(error.text);
                    }
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("params", currentParams);
        outState.putString("firstName", firstNameField.getText().toString());
        outState.putSerializable("lastName", lastNameField.getText().toString());
    }
}

/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
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
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.LayoutHelper;

import java.util.concurrent.Semaphore;

public class ChannelEditActivity extends BaseFragment implements AvatarUpdater.AvatarUpdaterDelegate, NotificationCenter.NotificationCenterDelegate {

    private View doneButton;
    private EditText nameTextView;
    private EditText descriptionTextView;
    private EditText userNameTextView;
    private BackupImageView avatarImage;
    private AvatarDrawable avatarDrawable;
    private AvatarUpdater avatarUpdater;
    private TextView checkTextView;
    private ProgressDialog progressDialog = null;

    private TLRPC.FileLocation avatar;
    private int checkReqId = 0;
    private String lastCheckName = null;
    private Runnable checkRunnable = null;
    private boolean lastNameAvailable = false;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private int chatId;
    private boolean allowComments = true;
    private TLRPC.InputFile uploadedAvatar;
    private boolean wasPrivate;
    private boolean privateAlertShown;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    public ChannelEditActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        avatarUpdater = new AvatarUpdater();
        chatId = args.getInt("chat_id", 0);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        currentChat = MessagesController.getInstance().getChat(chatId);
        if (currentChat == null) {
            final Semaphore semaphore = new Semaphore(0);
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    currentChat = MessagesStorage.getInstance().getChat(chatId);
                    semaphore.release();
                }
            });
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (currentChat != null) {
                MessagesController.getInstance().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                MessagesStorage.getInstance().loadChatInfo(chatId, semaphore, false, false);
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (info == null) {
                    return false;
                }
            }
        }
        wasPrivate = currentChat.username == null || currentChat.username.length() == 0;
        avatarUpdater.parentFragment = this;
        avatarUpdater.delegate = this;
        allowComments = !currentChat.broadcast;
        return super.onFragmentCreate();
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (avatarUpdater != null) {
            avatarUpdater.clear();
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    donePressed = true;
                    if (nameTextView.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(nameTextView, 2, 0);
                        return;
                    }
                    if (userNameTextView != null) {
                        if ((currentChat.username == null && userNameTextView.length() != 0) || (currentChat.username != null && !currentChat.username.equalsIgnoreCase(userNameTextView.getText().toString()))) {
                            if (userNameTextView.length() != 0 && !lastNameAvailable) {
                                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                                if (v != null) {
                                    v.vibrate(200);
                                }
                                AndroidUtilities.shakeView(checkTextView, 2, 0);
                                return;
                            }
                        }
                    }

                    if (avatarUpdater.uploadingAvatar != null) {
                        createAfterUpload = true;
                        progressDialog = new ProgressDialog(getParentActivity());
                        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                        progressDialog.setCanceledOnTouchOutside(false);
                        progressDialog.setCancelable(false);
                        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                createAfterUpload = false;
                                progressDialog = null;
                                donePressed = false;
                                try {
                                    dialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                        progressDialog.show();
                        return;
                    }
                    boolean currentAllowComments = !currentChat.broadcast;
                    if (allowComments != currentAllowComments) {
                        MessagesController.getInstance().toogleChannelComments(chatId, allowComments);
                    }
                    if (!currentChat.title.equals(nameTextView.getText().toString())) {
                        MessagesController.getInstance().changeChatTitle(chatId, nameTextView.getText().toString());
                    }
                    if (info != null && !info.about.equals(descriptionTextView.getText().toString())) {
                        MessagesController.getInstance().updateChannelAbout(chatId, descriptionTextView.getText().toString(), info);
                    }
                    if (userNameTextView != null) {
                        String oldUserName = currentChat.username != null ? currentChat.username : "";
                        if (!oldUserName.equals(userNameTextView.getText().toString())) {
                            MessagesController.getInstance().updateChannelUserName(chatId, userNameTextView.getText().toString());
                        }
                    }
                    if (uploadedAvatar != null) {
                        MessagesController.getInstance().changeChatAvatar(chatId, uploadedAvatar);
                    } else if (avatar == null && currentChat.photo instanceof TLRPC.TL_chatPhoto) {
                        MessagesController.getInstance().changeChatAvatar(chatId, null);
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        LinearLayout linearLayout;

        fragmentView = new ScrollView(context);
        fragmentView.setBackgroundColor(0xfff0f0f0);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle(LocaleController.getString("ChannelEdit", R.string.ChannelEdit));

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setBackgroundColor(0xffffffff);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout frameLayout = new FrameLayoutFixed(context);
        linearLayout2.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable.setInfo(5, null, null, false);
        avatarDrawable.setDrawPhoto(true);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));
        avatarImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                CharSequence[] items;

                if (avatar != null) {
                    items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("DeletePhoto", R.string.DeletePhoto)};
                } else {
                    items = new CharSequence[]{LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley)};
                }

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 0) {
                            avatarUpdater.openCamera();
                        } else if (i == 1) {
                            avatarUpdater.openGallery();
                        } else if (i == 2) {
                            avatar = null;
                            uploadedAvatar = null;
                            avatarImage.setImage(avatar, "50_50", avatarDrawable);
                        }
                    }
                });
                showDialog(builder.create());
            }
        });

        nameTextView = new EditText(context);
        if (currentChat.megagroup) {
            nameTextView.setHint(LocaleController.getString("GroupName", R.string.GroupName));
        } else {
            nameTextView.setHint(LocaleController.getString("EnterChannelName", R.string.EnterChannelName));
        }
        nameTextView.setMaxLines(4);
        nameTextView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setHintTextColor(0xff979797);
        nameTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        nameTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameTextView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        nameTextView.setFilters(inputFilters);
        AndroidUtilities.clearCursorDrawable(nameTextView);
        nameTextView.setTextColor(0xff212121);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 16 : 96, 0, LocaleController.isRTL ? 96 : 16, 0));
        nameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                avatarDrawable.setInfo(5, nameTextView.length() > 0 ? nameTextView.getText().toString() : null, null, false);
                avatarImage.invalidate();
            }
        });

        ShadowSectionCell sectionCell = new ShadowSectionCell(context);
        linearLayout.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setBackgroundColor(0xffffffff);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        descriptionTextView = new EditText(context);
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        descriptionTextView.setHintTextColor(0xff979797);
        descriptionTextView.setTextColor(0xff212121);
        descriptionTextView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
        descriptionTextView.setBackgroundDrawable(null);
        descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        descriptionTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(120);
        descriptionTextView.setFilters(inputFilters);
        descriptionTextView.setHint(LocaleController.getString("DescriptionPlaceholder", R.string.DescriptionPlaceholder));
        AndroidUtilities.clearCursorDrawable(descriptionTextView);
        linearLayout2.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 17, 12, 17, 6));
        descriptionTextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                    doneButton.performClick();
                    return true;
                }
                return false;
            }
        });
        descriptionTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        if (currentChat.megagroup) {
            infoCell.setText(LocaleController.getString("DescriptionInfoMega", R.string.DescriptionInfoMega));
        } else {
            infoCell.setText(LocaleController.getString("DescriptionInfo", R.string.DescriptionInfo));
        }
        infoCell.setBackgroundResource(R.drawable.greydivider);
        linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!currentChat.megagroup) {
            linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(LinearLayout.VERTICAL);
            linearLayout2.setBackgroundColor(0xffffffff);
            linearLayout2.setPadding(0, 0, 0, AndroidUtilities.dp(7));
            linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout publicContainer = new LinearLayout(context);
            publicContainer.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout2.addView(publicContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 17, 7, 17, 0));

            EditText editText = new EditText(context);
            editText.setText("telegram.me/");
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            editText.setHintTextColor(0xff979797);
            editText.setTextColor(0xff212121);
            editText.setMaxLines(1);
            editText.setLines(1);
            editText.setEnabled(false);
            editText.setBackgroundDrawable(null);
            editText.setPadding(0, 0, 0, 0);
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            publicContainer.addView(editText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));

            userNameTextView = new EditText(context);
            userNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            userNameTextView.setHintTextColor(0xff979797);
            userNameTextView.setTextColor(0xff212121);
            userNameTextView.setMaxLines(1);
            userNameTextView.setLines(1);
            userNameTextView.setBackgroundDrawable(null);
            userNameTextView.setPadding(0, 0, 0, 0);
            userNameTextView.setSingleLine(true);
            userNameTextView.setText(currentChat.username);
            userNameTextView.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            userNameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            userNameTextView.setHint(LocaleController.getString("ChannelUsernamePlaceholder", R.string.ChannelUsernamePlaceholder));
            userNameTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (wasPrivate && hasFocus && !privateAlertShown) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("ChannelWasPrivateAlert", R.string.ChannelWasPrivateAlert));
                        builder.setPositiveButton(LocaleController.getString("Close", R.string.Close), null);
                        showDialog(builder.create());
                    }
                }
            });
            AndroidUtilities.clearCursorDrawable(userNameTextView);
            publicContainer.addView(userNameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
            userNameTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    checkUserName(userNameTextView.getText().toString(), false);
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            checkTextView = new TextView(context);
            checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            checkTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            checkTextView.setVisibility(View.GONE);
            linearLayout2.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 17, 3, 17, 7));

            infoCell = new TextInfoPrivacyCell(context);
            infoCell.setBackgroundResource(R.drawable.greydivider);
            infoCell.setText(LocaleController.getString("ChannelUsernameHelp", R.string.ChannelUsernameHelp));
            linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }


        /*frameLayout = new FrameLayoutFixed(context);
        frameLayout.setBackgroundColor(0xffffffff);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckCell commentsCell = new TextCheckCell(context);
        commentsCell.setTextAndCheck(LocaleController.getString("Comments", R.string.Comments), allowComments, false);
        commentsCell.setBackgroundResource(R.drawable.list_selector);
        frameLayout.addView(commentsCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        commentsCell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allowComments = !allowComments;
                ((TextCheckCell) v).setChecked(allowComments);
            }
        });

        infoCell = new TextInfoPrivacyCell(context);
        infoCell.setText(LocaleController.getString("CommentsInfo", R.string.CommentsInfo));
        infoCell.setBackgroundResource(R.drawable.greydivider);
        linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));*/

        frameLayout = new FrameLayoutFixed(context);
        frameLayout.setBackgroundColor(0xffffffff);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell textCell = new TextSettingsCell(context);
        textCell.setTextColor(0xffed3d39);
        textCell.setBackgroundResource(R.drawable.list_selector);
        if (currentChat.megagroup) {
            textCell.setText(LocaleController.getString("DeleteMega", R.string.DeleteMega), false);
        } else {
            textCell.setText(LocaleController.getString("ChannelDelete", R.string.ChannelDelete), false);
        }
        frameLayout.addView(textCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        textCell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (currentChat.megagroup) {
                    builder.setMessage(LocaleController.getString("MegaDeleteAlert", R.string.MegaDeleteAlert));
                } else {
                    builder.setMessage(LocaleController.getString("ChannelDeleteAlert", R.string.ChannelDeleteAlert));
                }
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
                        if (AndroidUtilities.isTablet()) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats, -(long) chatId);
                        } else {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                        }
                        MessagesController.getInstance().deleteUserFromChat(chatId, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), info);
                        finishFragment();
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
        });

        infoCell = new TextInfoPrivacyCell(context);
        infoCell.setBackgroundResource(R.drawable.greydivider_bottom);
        if (currentChat.megagroup) {
            infoCell.setText(LocaleController.getString("MegaDeleteInfo", R.string.MegaDeleteInfo));
        } else {
            infoCell.setText(LocaleController.getString("ChannelDeleteInfo", R.string.ChannelDeleteInfo));
        }
        linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        nameTextView.setText(currentChat.title);
        nameTextView.setSelection(nameTextView.length());
        if (info != null) {
            descriptionTextView.setText(info.about);
        }
        if (currentChat.photo != null) {
            avatar = currentChat.photo.photo_small;
            avatarImage.setImage(avatar, "50_50", avatarDrawable);
        } else {
            avatarImage.setImageDrawable(avatarDrawable);
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null) {
                    descriptionTextView.setText(chatFull.about);
                }
                info = chatFull;
            }
        }
    }

    @Override
    public void didUploadedPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize small, final TLRPC.PhotoSize big) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                uploadedAvatar = file;
                avatar = small.location;
                avatarImage.setImage(avatar, "50_50", avatarDrawable);
                if (createAfterUpload) {
                    try {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    doneButton.performClick();
                }
            }
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (avatarUpdater != null && avatarUpdater.currentPicturePath != null) {
            args.putString("path", avatarUpdater.currentPicturePath);
        }
        if (nameTextView != null) {
            String text = nameTextView.getText().toString();
            if (text != null && text.length() != 0) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (avatarUpdater != null) {
            avatarUpdater.currentPicturePath = args.getString("path");
        }
    }

    private boolean checkUserName(final String name, boolean alert) {
        if (name != null && name.length() > 0) {
            checkTextView.setVisibility(View.VISIBLE);
        } else {
            checkTextView.setVisibility(View.GONE);
        }
        if (alert && name.length() == 0) {
            return true;
        }
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                ConnectionsManager.getInstance().cancelRequest(checkReqId, true);
            }
        }
        lastNameAvailable = false;
        if (name != null) {
            if (name.startsWith("_") || name.endsWith("_")) {
                checkTextView.setText(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                checkTextView.setTextColor(0xffcf3030);
                return false;
            }
            for (int a = 0; a < name.length(); a++) {
                char ch = name.charAt(a);
                if (a == 0 && ch >= '0' && ch <= '9') {
                    if (alert) {
                        showErrorAlert(LocaleController.getString("LinkInvalidStartNumber", R.string.LinkInvalidStartNumber));
                    } else {
                        checkTextView.setText(LocaleController.getString("LinkInvalidStartNumber", R.string.LinkInvalidStartNumber));
                        checkTextView.setTextColor(0xffcf3030);
                    }
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    if (alert) {
                        showErrorAlert(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                    } else {
                        checkTextView.setText(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                        checkTextView.setTextColor(0xffcf3030);
                    }
                    return false;
                }
            }
        }
        if (name == null || name.length() < 5) {
            if (alert) {
                showErrorAlert(LocaleController.getString("LinkInvalidShort", R.string.LinkInvalidShort));
            } else {
                checkTextView.setText(LocaleController.getString("LinkInvalidShort", R.string.LinkInvalidShort));
                checkTextView.setTextColor(0xffcf3030);
            }
            return false;
        }
        if (name.length() > 32) {
            if (alert) {
                showErrorAlert(LocaleController.getString("LinkInvalidLong", R.string.LinkInvalidLong));
            } else {
                checkTextView.setText(LocaleController.getString("LinkInvalidLong", R.string.LinkInvalidLong));
                checkTextView.setTextColor(0xffcf3030);
            }
            return false;
        }

        if (!alert) {
            checkTextView.setText(LocaleController.getString("LinkChecking", R.string.LinkChecking));
            checkTextView.setTextColor(0xff6d6d72);
            lastCheckName = name;
            checkRunnable = new Runnable() {
                @Override
                public void run() {
                    TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
                    req.username = name;
                    req.channel = MessagesController.getInputChannel(chatId);
                    checkReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(final TLObject response, final TLRPC.TL_error error) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    checkReqId = 0;
                                    if (lastCheckName != null && lastCheckName.equals(name)) {
                                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                            checkTextView.setText(LocaleController.formatString("LinkAvailable", R.string.LinkAvailable, name));
                                            checkTextView.setTextColor(0xff26972c);
                                            lastNameAvailable = true;
                                        } else {
                                            if (error != null && error.text.equals("CHANNELS_ADMIN_PUBLIC_TOO_MUCH")) {
                                                checkTextView.setText(LocaleController.getString("ChannelPublicLimitReached", R.string.ChannelPublicLimitReached));
                                            } else {
                                                checkTextView.setText(LocaleController.getString("LinkInUse", R.string.LinkInUse));
                                            }
                                            checkTextView.setTextColor(0xffcf3030);
                                            lastNameAvailable = false;
                                        }
                                    }
                                }
                            });
                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            };
            AndroidUtilities.runOnUIThread(checkRunnable, 300);
        }
        return true;
    }

    private void showErrorAlert(String error) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        switch (error) {
            case "USERNAME_INVALID":
                builder.setMessage(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                break;
            case "USERNAME_OCCUPIED":
                builder.setMessage(LocaleController.getString("LinkInUse", R.string.LinkInUse));
                break;
            case "USERNAMES_UNAVAILABLE":
                builder.setMessage(LocaleController.getString("FeatureUnavailable", R.string.FeatureUnavailable));
                break;
            default:
                builder.setMessage(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
                break;
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }
}

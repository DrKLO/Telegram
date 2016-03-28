/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.ContactsAdapter;
import org.telegram.ui.Adapters.SearchAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextBlockCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChipSpan;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterSectionsListView;

import java.util.ArrayList;
import java.util.HashMap;

public class ChannelCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AvatarUpdater.AvatarUpdaterDelegate {

    private View doneButton;
    private EditText nameTextView;
    private ProgressDialog progressDialog = null;

    private BackupImageView avatarImage;
    private AvatarDrawable avatarDrawable;
    private AvatarUpdater avatarUpdater;
    private EditText descriptionTextView;
    private TLRPC.FileLocation avatar;
    private String nameToSet = null;

    private LinearLayout linkContainer;
    private LinearLayout publicContainer;
    private TextBlockCell privateContainer;
    private RadioButtonCell radioButtonCell1;
    private RadioButtonCell radioButtonCell2;
    private TextInfoPrivacyCell typeInfoCell;
    private TextView checkTextView;
    private HeaderCell headerCell;
    private int checkReqId = 0;
    private String lastCheckName = null;
    private Runnable checkRunnable = null;
    private boolean lastNameAvailable = false;
    private boolean isPrivate = false;
    private boolean loadingInvite;
    private TLRPC.ExportedChatInvite invite;

    private ContactsAdapter listViewAdapter;
    private TextView emptyTextView;
    private LetterSectionsListView listView;
    private SearchAdapter searchListViewAdapter;
    private boolean searchWas;
    private boolean searching;
    private HashMap<Integer, ChipSpan> selectedContacts = new HashMap<>();
    private ArrayList<ChipSpan> allSpans = new ArrayList<>();
    private int beforeChangeIndex;
    private boolean ignoreChange;
    private CharSequence changeString;

    private int currentStep;
    private int chatId;
    private boolean allowComments = false;
    private boolean canCreatePublic = true;
    private TLRPC.InputFile uploadedAvatar;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    public ChannelCreateActivity(Bundle args) {
        super(args);
        currentStep = args.getInt("step", 0);
        if (currentStep == 0) {
            avatarDrawable = new AvatarDrawable();
            avatarUpdater = new AvatarUpdater();

            TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
            req.username = "1";
            req.channel = new TLRPC.TL_inputChannelEmpty();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            canCreatePublic = error == null || !error.text.equals("CHANNELS_ADMIN_PUBLIC_TOO_MUCH");
                        }
                    });
                }
            });
        } else {
            if (currentStep == 1) {
                canCreatePublic = args.getBoolean("canCreatePublic", true);
                isPrivate = !canCreatePublic;
            }
            chatId = args.getInt("chat_id", 0);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatDidFailCreate);
        if (currentStep == 2) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        } else if (currentStep == 1) {
            generateLink();
        }
        if (avatarUpdater != null) {
            avatarUpdater.parentFragment = this;
            avatarUpdater.delegate = this;
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatDidFailCreate);
        if (currentStep == 2) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        }
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
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (currentStep == 0) {
                        if (donePressed) {
                            return;
                        }
                        if (nameTextView.length() == 0) {
                            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                            if (v != null) {
                                v.vibrate(200);
                            }
                            AndroidUtilities.shakeView(nameTextView, 2, 0);
                            return;
                        }
                        donePressed = true;
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
                        final int reqId = MessagesController.getInstance().createChat(nameTextView.getText().toString(), new ArrayList<Integer>(), descriptionTextView.getText().toString(), ChatObject.CHAT_TYPE_CHANNEL, ChannelCreateActivity.this);
                        progressDialog = new ProgressDialog(getParentActivity());
                        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                        progressDialog.setCanceledOnTouchOutside(false);
                        progressDialog.setCancelable(false);
                        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ConnectionsManager.getInstance().cancelRequest(reqId, true);
                                donePressed = false;
                                try {
                                    dialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                        progressDialog.show();
                    } else if (currentStep == 1) {
                        if (!isPrivate) {
                            if (nameTextView.length() == 0) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setMessage(LocaleController.getString("ChannelPublicEmptyUsername", R.string.ChannelPublicEmptyUsername));
                                builder.setPositiveButton(LocaleController.getString("Close", R.string.Close), null);
                                showDialog(builder.create());
                                return;
                            } else {
                                if (!lastNameAvailable) {
                                    Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                                    if (v != null) {
                                        v.vibrate(200);
                                    }
                                    AndroidUtilities.shakeView(checkTextView, 2, 0);
                                    return;
                                } else {
                                    MessagesController.getInstance().updateChannelUserName(chatId, lastCheckName);
                                }
                            }
                        }
                        if (allowComments) {
                            MessagesController.getInstance().toogleChannelComments(chatId, allowComments);
                        }
                        Bundle args = new Bundle();
                        args.putInt("step", 2);
                        args.putInt("chat_id", chatId);
                        presentFragment(new ChannelCreateActivity(args), true);
                    } else {
                        ArrayList<TLRPC.InputUser> result = new ArrayList<>();
                        for (Integer uid : selectedContacts.keySet()) {
                            TLRPC.InputUser user = MessagesController.getInputUser(MessagesController.getInstance().getUser(uid));
                            if (user != null) {
                                result.add(user);
                            }
                        }
                        MessagesController.getInstance().addUsersToChannel(chatId, result, null);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                        Bundle args2 = new Bundle();
                        args2.putInt("chat_id", chatId);
                        presentFragment(new ChatActivity(args2), true);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        LinearLayout linearLayout;
        if (currentStep != 2) {
            fragmentView = new ScrollView(context);
            ScrollView scrollView = (ScrollView) fragmentView;
            scrollView.setFillViewport(true);
            linearLayout = new LinearLayout(context);
            scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            fragmentView = new LinearLayout(context);
            fragmentView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            linearLayout = (LinearLayout) fragmentView;
        }
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        if (currentStep == 0) {
            actionBar.setTitle(LocaleController.getString("NewChannel", R.string.NewChannel));
            fragmentView.setBackgroundColor(0xffffffff);
            FrameLayout frameLayout = new FrameLayoutFixed(context);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            avatarImage = new BackupImageView(context);
            avatarImage.setRoundRadius(AndroidUtilities.dp(32));
            avatarDrawable.setInfo(5, null, null, false);
            avatarDrawable.setDrawPhoto(true);
            avatarImage.setImageDrawable(avatarDrawable);
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
            nameTextView.setHint(LocaleController.getString("EnterChannelName", R.string.EnterChannelName));
            if (nameToSet != null) {
                nameTextView.setText(nameToSet);
                nameToSet = null;
            }
            nameTextView.setMaxLines(4);
            nameTextView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setHintTextColor(0xff979797);
            nameTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            nameTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(100);
            nameTextView.setFilters(inputFilters);
            nameTextView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
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

            descriptionTextView = new EditText(context);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            descriptionTextView.setHintTextColor(0xff979797);
            descriptionTextView.setTextColor(0xff212121);
            descriptionTextView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
            descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            descriptionTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(120);
            descriptionTextView.setFilters(inputFilters);
            descriptionTextView.setHint(LocaleController.getString("DescriptionPlaceholder", R.string.DescriptionPlaceholder));
            AndroidUtilities.clearCursorDrawable(descriptionTextView);
            linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 18, 24, 0));
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

            TextView helpTextView = new TextView(context);
            helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            helpTextView.setTextColor(0xff6d6d72);
            helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            helpTextView.setText(LocaleController.getString("DescriptionInfo", R.string.DescriptionInfo));
            linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 20));

            /*helpTextView = new TextView(context);
            helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            helpTextView.setTextColor(0xff3d93d5);
            helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            helpTextView.setText(LocaleController.getString("ChannelAlertTitle", R.string.ChannelAlertTitle));
            linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 14, 24, 20));
            helpTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("ChannelAlertTitle", R.string.ChannelAlertTitle));
                    builder.setMessage(LocaleController.getString("ChannelAlertText", R.string.ChannelAlertText));
                    builder.setPositiveButton(LocaleController.getString("Close", R.string.Close), null);
                    showDialog(builder.create());
                }
            });*/
        } else if (currentStep == 1) {
            actionBar.setTitle(LocaleController.getString("ChannelSettings", R.string.ChannelSettings));
            fragmentView.setBackgroundColor(0xfff0f0f0);

            LinearLayout linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(LinearLayout.VERTICAL);
            linearLayout2.setBackgroundColor(0xffffffff);
            linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            radioButtonCell1 = new RadioButtonCell(context);
            radioButtonCell1.setBackgroundResource(R.drawable.list_selector);
            radioButtonCell1.setTextAndValue(LocaleController.getString("ChannelPublic", R.string.ChannelPublic), LocaleController.getString("ChannelPublicInfo", R.string.ChannelPublicInfo), !isPrivate, false);
            linearLayout2.addView(radioButtonCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            radioButtonCell1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!canCreatePublic) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("ChannelPublicLimitReached", R.string.ChannelPublicLimitReached));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                    if (!isPrivate) {
                        return;
                    }
                    isPrivate = false;
                    updatePrivatePublic();
                }
            });

            radioButtonCell2 = new RadioButtonCell(context);
            radioButtonCell2.setBackgroundResource(R.drawable.list_selector);
            radioButtonCell2.setTextAndValue(LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate), LocaleController.getString("ChannelPrivateInfo", R.string.ChannelPrivateInfo), isPrivate, false);
            linearLayout2.addView(radioButtonCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            radioButtonCell2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isPrivate) {
                        return;
                    }
                    isPrivate = true;
                    updatePrivatePublic();
                }
            });

            ShadowSectionCell sectionCell = new ShadowSectionCell(context);
            linearLayout.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            linkContainer = new LinearLayout(context);
            linkContainer.setOrientation(LinearLayout.VERTICAL);
            linkContainer.setBackgroundColor(0xffffffff);
            linearLayout.addView(linkContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            headerCell = new HeaderCell(context);
            linkContainer.addView(headerCell);

            publicContainer = new LinearLayout(context);
            publicContainer.setOrientation(LinearLayout.HORIZONTAL);
            linkContainer.addView(publicContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 17, 7, 17, 0));

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

            nameTextView = new EditText(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            nameTextView.setHintTextColor(0xff979797);
            nameTextView.setTextColor(0xff212121);
            nameTextView.setMaxLines(1);
            nameTextView.setLines(1);
            nameTextView.setBackgroundDrawable(null);
            nameTextView.setPadding(0, 0, 0, 0);
            nameTextView.setSingleLine(true);
            nameTextView.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            nameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            nameTextView.setHint(LocaleController.getString("ChannelUsernamePlaceholder", R.string.ChannelUsernamePlaceholder));
            AndroidUtilities.clearCursorDrawable(nameTextView);
            publicContainer.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
            nameTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    checkUserName(nameTextView.getText().toString(), false);
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            privateContainer = new TextBlockCell(context);
            privateContainer.setBackgroundResource(R.drawable.list_selector);
            linkContainer.addView(privateContainer);
            privateContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (invite == null) {
                        return;
                    }
                    try {
                        if (Build.VERSION.SDK_INT < 11) {
                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setText(invite.link);
                        } else {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", invite.link);
                            clipboard.setPrimaryClip(clip);
                        }
                        Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            });

            checkTextView = new TextView(context);
            checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            checkTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            checkTextView.setVisibility(View.GONE);
            linkContainer.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 17, 3, 17, 7));

            typeInfoCell = new TextInfoPrivacyCell(context);
            //typeInfoCell.setBackgroundResource(R.drawable.greydivider);
            typeInfoCell.setBackgroundResource(R.drawable.greydivider_bottom);
            linearLayout.addView(typeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            /*FrameLayout frameLayout = new FrameLayoutFixed(context);
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

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
            infoCell.setText(LocaleController.getString("CommentsInfo", R.string.CommentsInfo));
            infoCell.setBackgroundResource(R.drawable.greydivider_bottom);
            linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));*/
            updatePrivatePublic();
        } else if (currentStep == 2) {
            actionBar.setTitle(LocaleController.getString("ChannelAddMembers", R.string.ChannelAddMembers));
            actionBar.setSubtitle(LocaleController.formatPluralString("Members", selectedContacts.size()));

            searchListViewAdapter = new SearchAdapter(context, null, false, false, false, false);
            searchListViewAdapter.setCheckedMap(selectedContacts);
            searchListViewAdapter.setUseUserCell(true);
            listViewAdapter = new ContactsAdapter(context, 1, false, null, false);
            listViewAdapter.setCheckedMap(selectedContacts);

            FrameLayout frameLayout = new FrameLayout(context);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            nameTextView = new EditText(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setHintTextColor(0xff979797);
            nameTextView.setTextColor(0xff212121);
            nameTextView.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            nameTextView.setMinimumHeight(AndroidUtilities.dp(54));
            nameTextView.setSingleLine(false);
            nameTextView.setLines(2);
            nameTextView.setMaxLines(2);
            nameTextView.setVerticalScrollBarEnabled(true);
            nameTextView.setHorizontalScrollBarEnabled(false);
            nameTextView.setPadding(0, 0, 0, 0);
            nameTextView.setHint(LocaleController.getString("AddMutual", R.string.AddMutual));
            if (Build.VERSION.SDK_INT >= 11) {
                nameTextView.setTextIsSelectable(false);
            }
            nameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            AndroidUtilities.clearCursorDrawable(nameTextView);
            frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));

            nameTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                    if (!ignoreChange) {
                        beforeChangeIndex = nameTextView.getSelectionStart();
                        changeString = new SpannableString(charSequence);
                    }
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (!ignoreChange) {
                        boolean search = false;
                        int afterChangeIndex = nameTextView.getSelectionEnd();
                        if (editable.toString().length() < changeString.toString().length()) {
                            String deletedString = "";
                            try {
                                deletedString = changeString.toString().substring(afterChangeIndex, beforeChangeIndex);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            if (deletedString.length() > 0) {
                                if (searching && searchWas) {
                                    search = true;
                                }
                                Spannable span = nameTextView.getText();
                                for (int a = 0; a < allSpans.size(); a++) {
                                    ChipSpan sp = allSpans.get(a);
                                    if (span.getSpanStart(sp) == -1) {
                                        allSpans.remove(sp);
                                        selectedContacts.remove(sp.uid);
                                    }
                                }
                                actionBar.setSubtitle(LocaleController.formatPluralString("Members", selectedContacts.size()));
                                listView.invalidateViews();
                            } else {
                                search = true;
                            }
                        } else {
                            search = true;
                        }
                        if (search) {
                            String text = nameTextView.getText().toString().replace("<", "");
                            if (text.length() != 0) {
                                searching = true;
                                searchWas = true;
                                if (listView != null) {
                                    listView.setAdapter(searchListViewAdapter);
                                    searchListViewAdapter.notifyDataSetChanged();
                                    if (android.os.Build.VERSION.SDK_INT >= 11) {
                                        listView.setFastScrollAlwaysVisible(false);
                                    }
                                    listView.setFastScrollEnabled(false);
                                    listView.setVerticalScrollBarEnabled(true);
                                }
                                if (emptyTextView != null) {
                                    emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                                }
                                searchListViewAdapter.searchDialogs(text);
                            } else {
                                searchListViewAdapter.searchDialogs(null);
                                searching = false;
                                searchWas = false;
                                listView.setAdapter(listViewAdapter);
                                listViewAdapter.notifyDataSetChanged();
                                if (android.os.Build.VERSION.SDK_INT >= 11) {
                                    listView.setFastScrollAlwaysVisible(true);
                                }
                                listView.setFastScrollEnabled(true);
                                listView.setVerticalScrollBarEnabled(false);
                                emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                            }
                        }
                    }
                }
            });

            LinearLayout emptyTextLayout = new LinearLayout(context);
            emptyTextLayout.setVisibility(View.INVISIBLE);
            emptyTextLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.addView(emptyTextLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            emptyTextLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            emptyTextView = new TextView(context);
            emptyTextView.setTextColor(0xff808080);
            emptyTextView.setTextSize(20);
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
            emptyTextLayout.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f));

            FrameLayout frameLayout2 = new FrameLayout(context);
            emptyTextLayout.addView(frameLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f));

            listView = new LetterSectionsListView(context);
            listView.setEmptyView(emptyTextLayout);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setFastScrollEnabled(true);
            listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
            listView.setAdapter(listViewAdapter);
            if (Build.VERSION.SDK_INT >= 11) {
                listView.setFastScrollAlwaysVisible(true);
                listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
            }
            linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    TLRPC.User user;
                    if (searching && searchWas) {
                        user = (TLRPC.User) searchListViewAdapter.getItem(i);
                    } else {
                        int section = listViewAdapter.getSectionForPosition(i);
                        int row = listViewAdapter.getPositionInSectionForPosition(i);
                        if (row < 0 || section < 0) {
                            return;
                        }
                        user = (TLRPC.User) listViewAdapter.getItem(section, row);
                    }
                    if (user == null) {
                        return;
                    }

                    boolean check = true;
                    if (selectedContacts.containsKey(user.id)) {
                        check = false;
                        try {
                            ChipSpan span = selectedContacts.get(user.id);
                            selectedContacts.remove(user.id);
                            SpannableStringBuilder text = new SpannableStringBuilder(nameTextView.getText());
                            text.delete(text.getSpanStart(span), text.getSpanEnd(span));
                            allSpans.remove(span);
                            ignoreChange = true;
                            nameTextView.setText(text);
                            nameTextView.setSelection(text.length());
                            ignoreChange = false;
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else {
                        ignoreChange = true;
                        ChipSpan span = createAndPutChipForUser(user);
                        if (span != null) {
                            span.uid = user.id;
                        }
                        ignoreChange = false;
                        if (span == null) {
                            return;
                        }
                    }
                    actionBar.setSubtitle(LocaleController.formatPluralString("Members", selectedContacts.size()));
                    if (searching || searchWas) {
                        ignoreChange = true;
                        SpannableStringBuilder ssb = new SpannableStringBuilder("");
                        for (ImageSpan sp : allSpans) {
                            ssb.append("<<");
                            ssb.setSpan(sp, ssb.length() - 2, ssb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        nameTextView.setText(ssb);
                        nameTextView.setSelection(ssb.length());
                        ignoreChange = false;

                        searchListViewAdapter.searchDialogs(null);
                        searching = false;
                        searchWas = false;
                        listView.setAdapter(listViewAdapter);
                        listViewAdapter.notifyDataSetChanged();
                        if (android.os.Build.VERSION.SDK_INT >= 11) {
                            listView.setFastScrollAlwaysVisible(true);
                        }
                        listView.setFastScrollEnabled(true);
                        listView.setVerticalScrollBarEnabled(false);
                        emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                    } else {
                        if (view instanceof UserCell) {
                            ((UserCell) view).setChecked(check, true);
                        }
                    }
                }
            });
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == SCROLL_STATE_TOUCH_SCROLL) {
                        AndroidUtilities.hideKeyboard(nameTextView);
                    }
                    if (listViewAdapter != null) {
                        listViewAdapter.setIsScrolling(i != SCROLL_STATE_IDLE);
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (absListView.isFastScrollEnabled()) {
                        AndroidUtilities.clearDrawableAnimation(absListView);
                    }
                }
            });
        }

        return fragmentView;
    }

    private void generateLink() {
        if (loadingInvite || invite != null) {
            return;
        }
        loadingInvite = true;
        TLRPC.TL_channels_exportInvite req = new TLRPC.TL_channels_exportInvite();
        req.channel = MessagesController.getInputChannel(chatId);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            invite = (TLRPC.ExportedChatInvite) response;
                        }
                        loadingInvite = false;
                        privateContainer.setText(invite != null ? invite.link : LocaleController.getString("Loading", R.string.Loading), false);
                    }
                });
            }
        });
    }

    private void updatePrivatePublic() {
        radioButtonCell1.setChecked(!isPrivate, true);
        radioButtonCell2.setChecked(isPrivate, true);
        typeInfoCell.setText(isPrivate ? LocaleController.getString("ChannelPrivateLinkHelp", R.string.ChannelPrivateLinkHelp) : LocaleController.getString("ChannelUsernameHelp", R.string.ChannelUsernameHelp));
        headerCell.setText(isPrivate ? LocaleController.getString("ChannelInviteLinkTitle", R.string.ChannelInviteLinkTitle) : LocaleController.getString("ChannelLinkTitle", R.string.ChannelLinkTitle));
        publicContainer.setVisibility(isPrivate ? View.GONE : View.VISIBLE);
        privateContainer.setVisibility(isPrivate ? View.VISIBLE : View.GONE);
        linkContainer.setPadding(0, 0, 0, isPrivate ? 0 : AndroidUtilities.dp(7));
        privateContainer.setText(invite != null ? invite.link : LocaleController.getString("Loading", R.string.Loading), false);
        nameTextView.clearFocus();
        checkTextView.setVisibility(!isPrivate && checkTextView.length() != 0 ? View.VISIBLE : View.GONE);
        AndroidUtilities.hideKeyboard(nameTextView);
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
        if (currentStep == 0) {
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
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (currentStep == 0) {
            if (avatarUpdater != null) {
                avatarUpdater.currentPicturePath = args.getString("path");
            }
            String text = args.getString("nameTextView");
            if (text != null) {
                if (nameTextView != null) {
                    nameTextView.setText(text);
                } else {
                    nameToSet = text;
                }
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && currentStep != 1) {
            nameTextView.requestFocus();
            AndroidUtilities.showKeyboard(nameTextView);
        }
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.chatDidFailCreate) {
            if (progressDialog != null) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            donePressed = false;
        } else if (id == NotificationCenter.chatDidCreated) {
            if (progressDialog != null) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            int chat_id = (Integer) args[0];
            Bundle bundle = new Bundle();
            bundle.putInt("step", 1);
            bundle.putInt("chat_id", chat_id);
            bundle.putBoolean("canCreatePublic", canCreatePublic);
            if (uploadedAvatar != null) {
                MessagesController.getInstance().changeChatAvatar(chat_id, uploadedAvatar);
            }
            presentFragment(new ChannelCreateActivity(bundle), true);
        } else if (id == NotificationCenter.contactsDidLoaded) {
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
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

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            }
        }
    }

    private ChipSpan createAndPutChipForUser(TLRPC.User user) {
        try {
            LayoutInflater lf = (LayoutInflater) ApplicationLoader.applicationContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            View textView = lf.inflate(R.layout.group_create_bubble, null);
            TextView text = (TextView)textView.findViewById(R.id.bubble_text_view);
            String name = UserObject.getUserName(user);
            if (name.length() == 0 && user.phone != null && user.phone.length() != 0) {
                name = PhoneFormat.getInstance().format("+" + user.phone);
            }
            text.setText(name + ", ");

            int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            textView.measure(spec, spec);
            textView.layout(0, 0, textView.getMeasuredWidth(), textView.getMeasuredHeight());
            Bitmap b = Bitmap.createBitmap(textView.getWidth(), textView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            canvas.translate(-textView.getScrollX(), -textView.getScrollY());
            textView.draw(canvas);
            textView.setDrawingCacheEnabled(true);
            Bitmap cacheBmp = textView.getDrawingCache();
            Bitmap viewBmp = cacheBmp.copy(Bitmap.Config.ARGB_8888, true);
            textView.destroyDrawingCache();

            final BitmapDrawable bmpDrawable = new BitmapDrawable(b);
            bmpDrawable.setBounds(0, 0, b.getWidth(), b.getHeight());

            SpannableStringBuilder ssb = new SpannableStringBuilder("");
            ChipSpan span = new ChipSpan(bmpDrawable, ImageSpan.ALIGN_BASELINE);
            allSpans.add(span);
            selectedContacts.put(user.id, span);
            for (ImageSpan sp : allSpans) {
                ssb.append("<<");
                ssb.setSpan(sp, ssb.length() - 2, ssb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            nameTextView.setText(ssb);
            nameTextView.setSelection(ssb.length());
            return span;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }
}

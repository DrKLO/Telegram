/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextBlockCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.concurrent.Semaphore;

public class ChannelEditTypeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private LinearLayout linkContainer;
    private LinearLayout publicContainer;
    private TextBlockCell privateContainer;
    private RadioButtonCell radioButtonCell1;
    private RadioButtonCell radioButtonCell2;
    private TextInfoPrivacyCell typeInfoCell;
    private TextView checkTextView;
    private HeaderCell headerCell;
    private EditText nameTextView;
    private boolean isPrivate = false;
    private boolean loadingInvite;
    private TLRPC.ExportedChatInvite invite;

    private int checkReqId = 0;
    private String lastCheckName = null;
    private Runnable checkRunnable = null;
    private boolean lastNameAvailable = false;
    private TLRPC.Chat currentChat;
    private int chatId;

    private boolean donePressed;

    private final static int done_button = 1;

    public ChannelEditTypeActivity(Bundle args) {
        super(args);
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
        }
        isPrivate = currentChat.username == null || currentChat.username.length() == 0;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
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

                    if (!isPrivate && ((currentChat.username == null && nameTextView.length() != 0) || (currentChat.username != null && !currentChat.username.equalsIgnoreCase(nameTextView.getText().toString())))) {
                        if (nameTextView.length() != 0 && !lastNameAvailable) {
                            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                            if (v != null) {
                                v.vibrate(200);
                            }
                            AndroidUtilities.shakeView(checkTextView, 2, 0);
                            return;
                        }
                    }
                    donePressed = true;

                    String oldUserName = currentChat.username != null ? currentChat.username : "";
                    String newUserName = isPrivate ? "" : nameTextView.getText().toString();
                    if (!oldUserName.equals(newUserName)) {
                        MessagesController.getInstance().updateChannelUserName(chatId, newUserName);
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        LinearLayout linearLayout;

        fragmentView = new ScrollView(context);
        fragmentView.setBackgroundColor(0xfff0f0f0);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        if (currentChat.megagroup) {
            actionBar.setTitle(LocaleController.getString("GroupType", R.string.GroupType));
        } else {
            actionBar.setTitle(LocaleController.getString("ChannelType", R.string.ChannelType));
        }

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setBackgroundColor(0xffffffff);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        radioButtonCell1 = new RadioButtonCell(context);
        radioButtonCell1.setBackgroundResource(R.drawable.list_selector);
        if (currentChat.megagroup) {
            radioButtonCell1.setTextAndValue(LocaleController.getString("MegaPublic", R.string.MegaPublic), LocaleController.getString("MegaPublicInfo", R.string.MegaPublicInfo), !isPrivate, false);
        } else {
            radioButtonCell1.setTextAndValue(LocaleController.getString("ChannelPublic", R.string.ChannelPublic), LocaleController.getString("ChannelPublicInfo", R.string.ChannelPublicInfo), !isPrivate, false);
        }
        linearLayout2.addView(radioButtonCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioButtonCell1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPrivate) {
                    return;
                }
                isPrivate = false;
                updatePrivatePublic();
            }
        });

        radioButtonCell2 = new RadioButtonCell(context);
        radioButtonCell2.setBackgroundResource(R.drawable.list_selector);
        if (currentChat.megagroup) {
            radioButtonCell2.setTextAndValue(LocaleController.getString("MegaPrivate", R.string.MegaPrivate), LocaleController.getString("MegaPrivateInfo", R.string.MegaPrivateInfo), isPrivate, false);
        } else {
            radioButtonCell2.setTextAndValue(LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate), LocaleController.getString("ChannelPrivateInfo", R.string.ChannelPrivateInfo), isPrivate, false);
        }
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
        if (!isPrivate) {
            nameTextView.setText(currentChat.username);
        }
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
        typeInfoCell.setBackgroundResource(R.drawable.greydivider_bottom);
        linearLayout.addView(typeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updatePrivatePublic();

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                invite = chatFull.exported_invite;
                updatePrivatePublic();
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        if (chatFull != null) {
            if (chatFull.exported_invite instanceof TLRPC.TL_chatInviteExported) {
                invite = chatFull.exported_invite;
            } else {
                generateLink();
            }
        }
    }

    private void updatePrivatePublic() {
        radioButtonCell1.setChecked(!isPrivate, true);
        radioButtonCell2.setChecked(isPrivate, true);
        if (currentChat.megagroup) {
            typeInfoCell.setText(isPrivate ? LocaleController.getString("MegaPrivateLinkHelp", R.string.MegaPrivateLinkHelp) : LocaleController.getString("MegaUsernameHelp", R.string.MegaUsernameHelp));
            headerCell.setText(isPrivate ? LocaleController.getString("ChannelInviteLinkTitle", R.string.ChannelInviteLinkTitle) : LocaleController.getString("ChannelLinkTitle", R.string.ChannelLinkTitle));
        } else {
            typeInfoCell.setText(isPrivate ? LocaleController.getString("ChannelPrivateLinkHelp", R.string.ChannelPrivateLinkHelp) : LocaleController.getString("ChannelUsernameHelp", R.string.ChannelUsernameHelp));
            headerCell.setText(isPrivate ? LocaleController.getString("ChannelInviteLinkTitle", R.string.ChannelInviteLinkTitle) : LocaleController.getString("ChannelLinkTitle", R.string.ChannelLinkTitle));
        }
        publicContainer.setVisibility(isPrivate ? View.GONE : View.VISIBLE);
        privateContainer.setVisibility(isPrivate ? View.VISIBLE : View.GONE);
        linkContainer.setPadding(0, 0, 0, isPrivate ? 0 : AndroidUtilities.dp(7));
        privateContainer.setText(invite != null ? invite.link : LocaleController.getString("Loading", R.string.Loading), false);
        nameTextView.clearFocus();
        checkTextView.setVisibility(!isPrivate && checkTextView.length() != 0 ? View.VISIBLE : View.GONE);
        AndroidUtilities.hideKeyboard(nameTextView);
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
                    if (currentChat.megagroup) {
                        if (alert) {
                            showErrorAlert(LocaleController.getString("LinkInvalidStartNumberMega", R.string.LinkInvalidStartNumberMega));
                        } else {
                            checkTextView.setText(LocaleController.getString("LinkInvalidStartNumberMega", R.string.LinkInvalidStartNumberMega));
                            checkTextView.setTextColor(0xffcf3030);
                        }
                    } else {
                        if (alert) {
                            showErrorAlert(LocaleController.getString("LinkInvalidStartNumber", R.string.LinkInvalidStartNumber));
                        } else {
                            checkTextView.setText(LocaleController.getString("LinkInvalidStartNumber", R.string.LinkInvalidStartNumber));
                            checkTextView.setTextColor(0xffcf3030);
                        }
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
            if (currentChat.megagroup) {
                if (alert) {
                    showErrorAlert(LocaleController.getString("LinkInvalidShortMega", R.string.LinkInvalidShortMega));
                } else {
                    checkTextView.setText(LocaleController.getString("LinkInvalidShortMega", R.string.LinkInvalidShortMega));
                    checkTextView.setTextColor(0xffcf3030);
                }
            } else {
                if (alert) {
                    showErrorAlert(LocaleController.getString("LinkInvalidShort", R.string.LinkInvalidShort));
                } else {
                    checkTextView.setText(LocaleController.getString("LinkInvalidShort", R.string.LinkInvalidShort));
                    checkTextView.setTextColor(0xffcf3030);
                }
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
                                                checkTextView.setText(LocaleController.getString("ChangePublicLimitReached", R.string.ChangePublicLimitReached));
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

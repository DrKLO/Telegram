/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AdminedChannelCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.InviteLinkBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class ChatEditTypeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private EditTextBoldCursor usernameTextView;
    private EditTextBoldCursor editText;

    private TextInfoPrivacyCell typeInfoCell;
    private HeaderCell headerCell;
    private HeaderCell headerCell2;
    private TextInfoPrivacyCell checkTextView;
    private LinearLayout linearLayout;
    private ActionBarMenuItem doneButton;

    private LinearLayout linearLayoutTypeContainer;
    private RadioButtonCell radioButtonCell1;
    private RadioButtonCell radioButtonCell2;
    private LinearLayout adminnedChannelsLayout;
    private LinearLayout linkContainer;
    private LinearLayout publicContainer;
    private LinearLayout privateContainer;
    private LinkActionView permanentLinkView;
    private TextCell manageLinksTextView;
    private TextInfoPrivacyCell manageLinksInfoCell;
    private ShadowSectionCell sectionCell2;
    private TextInfoPrivacyCell infoCell;
    private TextSettingsCell textCell;
    private TextSettingsCell textCell2;

    // Saving content restrictions block
    private LinearLayout saveContainer;
    private HeaderCell saveHeaderCell;
    private TextCheckCell saveRestrictCell;
    private TextInfoPrivacyCell saveRestrictInfoCell;

    private boolean isPrivate;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private boolean isChannel;
    private boolean isSaveRestricted;

    private boolean canCreatePublic = true;
    private boolean loadingAdminedChannels;
    private ShadowSectionCell adminedInfoCell;
    private ArrayList<AdminedChannelCell> adminedChannelCells = new ArrayList<>();
    private LoadingCell loadingAdminedCell;

    private int checkReqId;
    private String lastCheckName;
    private Runnable checkRunnable;
    private boolean lastNameAvailable;
    private boolean loadingInvite;
    private TLRPC.TL_chatInviteExported invite;

    private boolean ignoreTextChanges;

    private boolean isForcePublic;
    HashMap<Long, TLRPC.User> usersMap = new HashMap<>();

    private final static int done_button = 1;
    private InviteLinkBottomSheet inviteLinkBottomSheet;

    public ChatEditTypeActivity(long id, boolean forcePublic) {
        chatId = id;
        isForcePublic = forcePublic;
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            currentChat = getMessagesStorage().getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = getMessagesStorage().loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }
        isPrivate = !isForcePublic && TextUtils.isEmpty(currentChat.username);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        isSaveRestricted = currentChat.noforwards;
        if (isForcePublic && TextUtils.isEmpty(currentChat.username) || isPrivate && currentChat.creator) {
            TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
            req.username = "1";
            req.channel = new TLRPC.TL_inputChannelEmpty();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                canCreatePublic = error == null || !error.text.equals("CHANNELS_ADMIN_PUBLIC_TOO_MUCH");
                if (!canCreatePublic) {
                    loadAdminedChannels();
                }
            }));
        }
        if (isPrivate && info != null) {
            getMessagesController().loadFullChat(chatId, classGuid, true);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (textCell2 != null && info != null) {
            if (info.stickerset != null) {
                textCell2.setTextAndValue(LocaleController.getString("GroupStickers", R.string.GroupStickers), info.stickerset.title, false);
            } else {
                textCell2.setText(LocaleController.getString("GroupStickers", R.string.GroupStickers), false);
            }
        }
        if (info != null) {
            invite = info.exported_invite;
            permanentLinkView.setLink(invite == null ? null : invite.link);
            permanentLinkView.loadUsers(invite, chatId);
        }
    }

    @Override
    protected void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        if (isForcePublic && usernameTextView != null) {
            usernameTextView.requestFocus();
            AndroidUtilities.showKeyboard(usernameTextView);
        }
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
                    processDone();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        fragmentView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        if (isForcePublic) {
            actionBar.setTitle(LocaleController.getString("TypeLocationGroup", R.string.TypeLocationGroup));
        } else if (isChannel) {
            actionBar.setTitle(LocaleController.getString("ChannelSettingsTitle", R.string.ChannelSettingsTitle));
        } else {
            actionBar.setTitle(LocaleController.getString("GroupSettingsTitle", R.string.GroupSettingsTitle));
        }

        linearLayoutTypeContainer = new LinearLayout(context);
        linearLayoutTypeContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayoutTypeContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(linearLayoutTypeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        headerCell2 = new HeaderCell(context, 23);
        headerCell2.setHeight(46);
        if (isChannel) {
            headerCell2.setText(LocaleController.getString("ChannelTypeHeader", R.string.ChannelTypeHeader));
        } else {
            headerCell2.setText(LocaleController.getString("GroupTypeHeader", R.string.GroupTypeHeader));
        }
        linearLayoutTypeContainer.addView(headerCell2);

        radioButtonCell2 = new RadioButtonCell(context);
        radioButtonCell2.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        if (isChannel) {
            radioButtonCell2.setTextAndValue(LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate), LocaleController.getString("ChannelPrivateInfo", R.string.ChannelPrivateInfo), false, isPrivate);
        } else {
            radioButtonCell2.setTextAndValue(LocaleController.getString("MegaPrivate", R.string.MegaPrivate), LocaleController.getString("MegaPrivateInfo", R.string.MegaPrivateInfo), false, isPrivate);
        }
        linearLayoutTypeContainer.addView(radioButtonCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioButtonCell2.setOnClickListener(v -> {
            if (isPrivate) {
                return;
            }
            isPrivate = true;
            updatePrivatePublic();
        });

        radioButtonCell1 = new RadioButtonCell(context);
        radioButtonCell1.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        if (isChannel) {
            radioButtonCell1.setTextAndValue(LocaleController.getString("ChannelPublic", R.string.ChannelPublic), LocaleController.getString("ChannelPublicInfo", R.string.ChannelPublicInfo), false, !isPrivate);
        } else {
            radioButtonCell1.setTextAndValue(LocaleController.getString("MegaPublic", R.string.MegaPublic), LocaleController.getString("MegaPublicInfo", R.string.MegaPublicInfo), false, !isPrivate);
        }
        linearLayoutTypeContainer.addView(radioButtonCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioButtonCell1.setOnClickListener(v -> {
            if (!isPrivate) {
                return;
            }
            isPrivate = false;
            updatePrivatePublic();
        });

        sectionCell2 = new ShadowSectionCell(context);
        linearLayout.addView(sectionCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (isForcePublic) {
            radioButtonCell2.setVisibility(View.GONE);
            radioButtonCell1.setVisibility(View.GONE);
            sectionCell2.setVisibility(View.GONE);
            headerCell2.setVisibility(View.GONE);
        }

        linkContainer = new LinearLayout(context);
        linkContainer.setOrientation(LinearLayout.VERTICAL);
        linkContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(linkContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        headerCell = new HeaderCell(context, 23);
        linkContainer.addView(headerCell);

        publicContainer = new LinearLayout(context);
        publicContainer.setOrientation(LinearLayout.HORIZONTAL);
        linkContainer.addView(publicContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 23, 7, 23, 0));

        editText = new EditTextBoldCursor(context);
        editText.setText(getMessagesController().linkPrefix + "/");
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setEnabled(false);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, 0, 0);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        publicContainer.addView(editText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));

        usernameTextView = new EditTextBoldCursor(context);
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        usernameTextView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        usernameTextView.setMaxLines(1);
        usernameTextView.setLines(1);
        usernameTextView.setBackgroundDrawable(null);
        usernameTextView.setPadding(0, 0, 0, 0);
        usernameTextView.setSingleLine(true);
        usernameTextView.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        usernameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        usernameTextView.setHint(LocaleController.getString("ChannelUsernamePlaceholder", R.string.ChannelUsernamePlaceholder));
        usernameTextView.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        usernameTextView.setCursorSize(AndroidUtilities.dp(20));
        usernameTextView.setCursorWidth(1.5f);
        publicContainer.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
        usernameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                if (ignoreTextChanges) {
                    return;
                }
                checkUserName(usernameTextView.getText().toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkDoneButton();
            }
        });

        privateContainer = new LinearLayout(context);
        privateContainer.setOrientation(LinearLayout.VERTICAL);
        linkContainer.addView(privateContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        permanentLinkView = new LinkActionView(context, this, null, chatId, true, ChatObject.isChannel(currentChat));
        permanentLinkView.setDelegate(new LinkActionView.Delegate() {
            @Override
            public void revokeLink() {
                ChatEditTypeActivity.this.generateLink(true);
            }

            @Override
            public void showUsersForPermanentLink() {
                inviteLinkBottomSheet = new InviteLinkBottomSheet(context, invite, info, usersMap, ChatEditTypeActivity.this, chatId, true, ChatObject.isChannel(currentChat));
                inviteLinkBottomSheet.show();
            }
        });
        permanentLinkView.setUsers(0, null);
        privateContainer.addView(permanentLinkView);

        checkTextView = new TextInfoPrivacyCell(context);
        checkTextView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        checkTextView.setBottomPadding(6);
        linearLayout.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        typeInfoCell = new TextInfoPrivacyCell(context);
        linearLayout.addView(typeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        loadingAdminedCell = new LoadingCell(context);
        linearLayout.addView(loadingAdminedCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        adminnedChannelsLayout = new LinearLayout(context);
        adminnedChannelsLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        adminnedChannelsLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(adminnedChannelsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        adminedInfoCell = new ShadowSectionCell(context);
        linearLayout.addView(adminedInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        manageLinksTextView = new TextCell(context);
        manageLinksTextView.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        manageLinksTextView.setTextAndIcon(LocaleController.getString("ManageInviteLinks", R.string.ManageInviteLinks), R.drawable.actions_link, false);
        manageLinksTextView.setOnClickListener(v -> {
            ManageLinksActivity fragment = new ManageLinksActivity(chatId, 0, 0);
            fragment.setInfo(info, invite);
            presentFragment(fragment);
        });
        linearLayout.addView(manageLinksTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        manageLinksInfoCell = new TextInfoPrivacyCell(context);
        linearLayout.addView(manageLinksInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        saveContainer = new LinearLayout(context);
        saveContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(saveContainer);

        saveHeaderCell = new HeaderCell(context, 23);
        saveHeaderCell.setHeight(46);
        saveHeaderCell.setText(LocaleController.getString("SavingContentTitle", R.string.SavingContentTitle));
        saveHeaderCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        saveContainer.addView(saveHeaderCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        saveRestrictCell = new TextCheckCell(context);
        saveRestrictCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        saveRestrictCell.setTextAndCheck(LocaleController.getString("RestrictSavingContent", R.string.RestrictSavingContent), isSaveRestricted, false);
        saveRestrictCell.setOnClickListener(v -> {
            isSaveRestricted = !isSaveRestricted;
            ((TextCheckCell) v).setChecked(isSaveRestricted);
        });
        saveContainer.addView(saveRestrictCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        saveRestrictInfoCell = new TextInfoPrivacyCell(context);
        if (isChannel && !ChatObject.isMegagroup(currentChat)) {
            saveRestrictInfoCell.setText(LocaleController.getString("RestrictSavingContentInfoChannel", R.string.RestrictSavingContentInfoChannel));
        } else {
            saveRestrictInfoCell.setText(LocaleController.getString("RestrictSavingContentInfoGroup", R.string.RestrictSavingContentInfoGroup));
        }

        saveContainer.addView(saveRestrictInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!isPrivate && currentChat.username != null) {
            ignoreTextChanges = true;
            usernameTextView.setText(currentChat.username);
            usernameTextView.setSelection(currentChat.username.length());
            ignoreTextChanges = false;
        }
        updatePrivatePublic();

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                info = chatFull;
                invite = chatFull.exported_invite;
                updatePrivatePublic();
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (chatFull != null) {
            if (chatFull.exported_invite != null) {
                invite = chatFull.exported_invite;
            } else {
                generateLink(false);
            }
        }
    }

    private void processDone() {
        if (currentChat.noforwards != isSaveRestricted) {
            getMessagesController().toggleChatNoForwards(chatId, currentChat.noforwards = isSaveRestricted);
        }
        if (trySetUsername()) {
            finishFragment();
        }
    }

    private boolean trySetUsername() {
        if (getParentActivity() == null) {
            return false;
        }
        if (!isPrivate && ((currentChat.username == null && usernameTextView.length() != 0) || (currentChat.username != null && !currentChat.username.equalsIgnoreCase(usernameTextView.getText().toString())))) {
            if (usernameTextView.length() != 0 && !lastNameAvailable) {
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                AndroidUtilities.shakeView(checkTextView, 2, 0);
                return false;
            }
        }

        String oldUserName = currentChat.username != null ? currentChat.username : "";
        String newUserName = isPrivate ? "" : usernameTextView.getText().toString();
        if (!oldUserName.equals(newUserName)) {
            if (!ChatObject.isChannel(currentChat)) {
                getMessagesController().convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                    if (param != 0) {
                        chatId = param;
                        currentChat = getMessagesController().getChat(param);
                        processDone();
                    }
                });
                return false;
            } else {
                getMessagesController().updateChannelUserName(chatId, newUserName);
                currentChat.username = newUserName;
            }
        }
        return true;
    }

    private void loadAdminedChannels() {
        if (loadingAdminedChannels || adminnedChannelsLayout == null) {
            return;
        }
        loadingAdminedChannels = true;
        updatePrivatePublic();
        TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingAdminedChannels = false;
            if (response != null) {
                if (getParentActivity() == null) {
                    return;
                }
                for (int a = 0; a < adminedChannelCells.size(); a++) {
                    linearLayout.removeView(adminedChannelCells.get(a));
                }
                adminedChannelCells.clear();
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;

                for (int a = 0; a < res.chats.size(); a++) {
                    AdminedChannelCell adminedChannelCell = new AdminedChannelCell(getParentActivity(), view -> {
                        AdminedChannelCell cell = (AdminedChannelCell) view.getParent();
                        final TLRPC.Chat channel = cell.getCurrentChannel();
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        if (isChannel) {
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, getMessagesController().linkPrefix + "/" + channel.username, channel.title)));
                        } else {
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, getMessagesController().linkPrefix + "/" + channel.username, channel.title)));
                        }
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), (dialogInterface, i) -> {
                            TLRPC.TL_channels_updateUsername req1 = new TLRPC.TL_channels_updateUsername();
                            req1.channel = MessagesController.getInputChannel(channel);
                            req1.username = "";
                            getConnectionsManager().sendRequest(req1, (response1, error1) -> {
                                if (response1 instanceof TLRPC.TL_boolTrue) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        canCreatePublic = true;
                                        if (usernameTextView.length() > 0) {
                                            checkUserName(usernameTextView.getText().toString());
                                        }
                                        updatePrivatePublic();
                                    });
                                }
                            }, ConnectionsManager.RequestFlagInvokeAfter);
                        });
                        showDialog(builder.create());
                    });
                    adminedChannelCell.setChannel(res.chats.get(a), a == res.chats.size() - 1);
                    adminedChannelCells.add(adminedChannelCell);
                    adminnedChannelsLayout.addView(adminedChannelCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 72));
                }
                updatePrivatePublic();
            }
        }));
    }

    private void updatePrivatePublic() {
        if (sectionCell2 == null) {
            return;
        }
        if (!isPrivate && !canCreatePublic) {
            typeInfoCell.setText(LocaleController.getString("ChangePublicLimitReached", R.string.ChangePublicLimitReached));
            typeInfoCell.setTag(Theme.key_windowBackgroundWhiteRedText4);
            typeInfoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
            linkContainer.setVisibility(View.GONE);
            checkTextView.setVisibility(View.GONE);
            sectionCell2.setVisibility(View.GONE);
            adminedInfoCell.setVisibility(View.VISIBLE);
            if (loadingAdminedChannels) {
                loadingAdminedCell.setVisibility(View.VISIBLE);
                adminnedChannelsLayout.setVisibility(View.GONE);
                typeInfoCell.setBackgroundDrawable(checkTextView.getVisibility() == View.VISIBLE ? null : Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                adminedInfoCell.setBackgroundDrawable(null);
            } else {
                adminedInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(adminedInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                loadingAdminedCell.setVisibility(View.GONE);
                adminnedChannelsLayout.setVisibility(View.VISIBLE);
            }
        } else {
            typeInfoCell.setTag(Theme.key_windowBackgroundWhiteGrayText4);
            typeInfoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            if (isForcePublic) {
                sectionCell2.setVisibility(View.GONE);
            } else {
                sectionCell2.setVisibility(View.VISIBLE);
            }
            adminedInfoCell.setVisibility(View.GONE);
            typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            adminnedChannelsLayout.setVisibility(View.GONE);
            linkContainer.setVisibility(View.VISIBLE);
            loadingAdminedCell.setVisibility(View.GONE);
            if (isChannel) {
                typeInfoCell.setText(isPrivate ? LocaleController.getString("ChannelPrivateLinkHelp", R.string.ChannelPrivateLinkHelp) : LocaleController.getString("ChannelUsernameHelp", R.string.ChannelUsernameHelp));
                headerCell.setText(isPrivate ? LocaleController.getString("ChannelInviteLinkTitle", R.string.ChannelInviteLinkTitle) : LocaleController.getString("ChannelLinkTitle", R.string.ChannelLinkTitle));
            } else {
                typeInfoCell.setText(isPrivate ? LocaleController.getString("MegaPrivateLinkHelp", R.string.MegaPrivateLinkHelp) : LocaleController.getString("MegaUsernameHelp", R.string.MegaUsernameHelp));
                headerCell.setText(isPrivate ? LocaleController.getString("ChannelInviteLinkTitle", R.string.ChannelInviteLinkTitle) : LocaleController.getString("ChannelLinkTitle", R.string.ChannelLinkTitle));
            }
            publicContainer.setVisibility(isPrivate ? View.GONE : View.VISIBLE);
            privateContainer.setVisibility(isPrivate ? View.VISIBLE : View.GONE);
            saveContainer.setVisibility(View.VISIBLE);
            manageLinksTextView.setVisibility(View.VISIBLE);
            manageLinksInfoCell.setVisibility(View.VISIBLE);
            linkContainer.setPadding(0, 0, 0, isPrivate ? 0 : AndroidUtilities.dp(7));
            permanentLinkView.setLink(invite != null ? invite.link : null);
            permanentLinkView.loadUsers(invite, chatId);
            checkTextView.setVisibility(!isPrivate && checkTextView.length() != 0 ? View.VISIBLE : View.GONE);
            if (isPrivate) {
                typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                manageLinksInfoCell.setBackground(Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                manageLinksInfoCell.setText(LocaleController.getString("ManageLinksInfoHelp", R.string.ManageLinksInfoHelp));
            } else {
                typeInfoCell.setBackgroundDrawable(checkTextView.getVisibility() == View.VISIBLE ? null : Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }
        radioButtonCell1.setChecked(!isPrivate, true);
        radioButtonCell2.setChecked(isPrivate, true);
        usernameTextView.clearFocus();
        checkDoneButton();
    }

    private void checkDoneButton() {
        if (isPrivate || usernameTextView.length() > 0) {
            doneButton.setEnabled(true);
            doneButton.setAlpha(1.0f);
        } else {
            doneButton.setEnabled(false);
            doneButton.setAlpha(0.5f);
        }
    }

    private boolean checkUserName(final String name) {
        if (name != null && name.length() > 0) {
            checkTextView.setVisibility(View.VISIBLE);
        } else {
            checkTextView.setVisibility(View.GONE);
        }
        typeInfoCell.setBackgroundDrawable(checkTextView.getVisibility() == View.VISIBLE ? null : Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                getConnectionsManager().cancelRequest(checkReqId, true);
            }
        }
        lastNameAvailable = false;
        if (name != null) {
            if (name.startsWith("_") || name.endsWith("_")) {
                checkTextView.setText(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                checkTextView.setTextColor(Theme.key_windowBackgroundWhiteRedText4);
                return false;
            }
            for (int a = 0; a < name.length(); a++) {
                char ch = name.charAt(a);
                if (a == 0 && ch >= '0' && ch <= '9') {
                    if (isChannel) {
                        checkTextView.setText(LocaleController.getString("LinkInvalidStartNumber", R.string.LinkInvalidStartNumber));
                    } else {
                        checkTextView.setText(LocaleController.getString("LinkInvalidStartNumberMega", R.string.LinkInvalidStartNumberMega));
                    }
                    checkTextView.setTextColor(Theme.key_windowBackgroundWhiteRedText4);
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    checkTextView.setText(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                    checkTextView.setTextColor(Theme.key_windowBackgroundWhiteRedText4);
                    return false;
                }
            }
        }
        if (name == null || name.length() < 5) {
            if (isChannel) {
                checkTextView.setText(LocaleController.getString("LinkInvalidShort", R.string.LinkInvalidShort));
            } else {
                checkTextView.setText(LocaleController.getString("LinkInvalidShortMega", R.string.LinkInvalidShortMega));
            }
            checkTextView.setTextColor(Theme.key_windowBackgroundWhiteRedText4);
            return false;
        }
        if (name.length() > 32) {
            checkTextView.setText(LocaleController.getString("LinkInvalidLong", R.string.LinkInvalidLong));
            checkTextView.setTextColor(Theme.key_windowBackgroundWhiteRedText4);
            return false;
        }

        checkTextView.setText(LocaleController.getString("LinkChecking", R.string.LinkChecking));
        checkTextView.setTextColor(Theme.key_windowBackgroundWhiteGrayText8);
        lastCheckName = name;
        checkRunnable = () -> {
            TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
            req.username = name;
            req.channel = getMessagesController().getInputChannel(chatId);
            checkReqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                checkReqId = 0;
                if (lastCheckName != null && lastCheckName.equals(name)) {
                    if (error == null && response instanceof TLRPC.TL_boolTrue) {
                        checkTextView.setText(LocaleController.formatString("LinkAvailable", R.string.LinkAvailable, name));
                        checkTextView.setTextColor(Theme.key_windowBackgroundWhiteGreenText);
                        lastNameAvailable = true;
                    } else {
                        if (error != null && error.text.equals("CHANNELS_ADMIN_PUBLIC_TOO_MUCH")) {
                            canCreatePublic = false;
                            loadAdminedChannels();
                        } else {
                            checkTextView.setText(LocaleController.getString("LinkInUse", R.string.LinkInUse));
                        }
                        checkTextView.setTextColor(Theme.key_windowBackgroundWhiteRedText4);
                        lastNameAvailable = false;
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        };
        AndroidUtilities.runOnUIThread(checkRunnable, 300);
        return true;
    }

    private void generateLink(final boolean newRequest) {
        loadingInvite = true;
        TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
        req.legacy_revoke_permanent = true;
        req.peer = getMessagesController().getInputPeer(-chatId);
        final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                invite = (TLRPC.TL_chatInviteExported) response;
                if (info != null) {
                    info.exported_invite = invite;
                }
                if (newRequest) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("RevokeAlertNewLink", R.string.RevokeAlertNewLink));
                    builder.setTitle(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                    builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                    showDialog(builder.create());
                }
            }
            loadingInvite = false;
            if (permanentLinkView != null) {
                permanentLinkView.setLink(invite != null ? invite.link : null);
                permanentLinkView.loadUsers(invite, chatId);
            }
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (adminnedChannelsLayout != null) {
                int count = adminnedChannelsLayout.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = adminnedChannelsLayout.getChildAt(a);
                    if (child instanceof AdminedChannelCell) {
                        ((AdminedChannelCell) child).update();
                    }
                }
            }

            permanentLinkView.updateColors();
            manageLinksTextView.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            if (inviteLinkBottomSheet != null) {
                inviteLinkBottomSheet.updateColors();
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(sectionCell2, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(infoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(infoCell, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(textCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(textCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(textCell2, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(textCell2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(usernameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(usernameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(linearLayoutTypeContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(linkContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(headerCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(headerCell2, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(saveHeaderCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(saveRestrictCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(saveRestrictCell, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(saveRestrictCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(saveRestrictCell, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText4));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText));

        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText4));

        themeDescriptions.add(new ThemeDescription(manageLinksInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(manageLinksInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(manageLinksInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText4));

        themeDescriptions.add(new ThemeDescription(saveRestrictInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(saveRestrictInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(saveRestrictInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText4));

        themeDescriptions.add(new ThemeDescription(adminedInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(loadingAdminedCell, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(radioButtonCell1, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(radioButtonCell2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_LINKCOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        themeDescriptions.add(new ThemeDescription(adminnedChannelsLayout, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{AdminedChannelCell.class}, new String[]{"deleteButton"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(manageLinksTextView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(manageLinksTextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(manageLinksTextView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));


        return themeDescriptions;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
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
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.InviteLinkBottomSheet;
import org.telegram.ui.Components.JoinToSendSettingsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private CrossfadeDrawable doneButtonDrawable;

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
    private UsernamesListView usernamesListView;

    private boolean ignoreScroll;

    private ArrayList<TLRPC.TL_username> editableUsernames = new ArrayList<>();
    private ArrayList<TLRPC.TL_username> usernames = new ArrayList<>();
    private ChangeUsernameActivity.UsernameCell editableUsernameCell;
    private ArrayList<String> loadingUsernames = new ArrayList<>();

    // Saving content restrictions block
    private LinearLayout saveContainer;
    private HeaderCell saveHeaderCell;
    private TextCheckCell saveRestrictCell;
    private TextInfoPrivacyCell saveRestrictInfoCell;

    private JoinToSendSettingsView joinContainer;

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
        isPrivate = !isForcePublic && !ChatObject.isPublic(currentChat);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        isSaveRestricted = currentChat.noforwards;
        if (isForcePublic && !ChatObject.isPublic(currentChat) || isPrivate && currentChat.creator) {
            TLRPC.TL_channels_checkUsername req = new TLRPC.TL_channels_checkUsername();
            req.username = "1";
            req.channel = new TLRPC.TL_inputChannelEmpty();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                canCreatePublic = error == null || !error.text.equals("CHANNELS_ADMIN_PUBLIC_TOO_MUCH");
                if (!canCreatePublic && getUserConfig().isPremium()) {
                    loadAdminedChannels();
                }
            }));
        }
        if (isPrivate && info != null) {
            getMessagesController().loadFullChat(chatId, classGuid, true);
        }
        if (currentChat != null) {
            editableUsernames.clear();
            usernames.clear();
            for (int i = 0; i < currentChat.usernames.size(); ++i) {
                if (currentChat.usernames.get(i).active)
                    usernames.add(currentChat.usernames.get(i));
            }
            for (int i = 0; i < currentChat.usernames.size(); ++i) {
                if (!currentChat.usernames.get(i).active)
                    usernames.add(currentChat.usernames.get(i));
            }
//            for (int i = 0; i < usernames.size(); ++i) {
//                if (usernames.get(i) == null ||
//                    currentChat.username != null && currentChat.username.equals(usernames.get(i).username) ||
//                    usernames.get(i).editable
//                ) {
//                    editableUsernames.add(usernames.remove(i--));
//                }
//            }
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
    public void onBecomeFullyVisible() {
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
        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = menu.addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        fragmentView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        return !ignoreScroll && super.onTouchEvent(ev);
                    default:
                        return super.onTouchEvent(ev);
                }
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return !ignoreScroll && super.onInterceptTouchEvent(ev);
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
            if (!canCreatePublic) {
                showPremiumIncreaseLimitDialog();
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

        usernameTextView = new EditTextBoldCursor(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                StringBuilder sb = new StringBuilder();
                sb.append(getText());
                if (checkTextView != null && checkTextView.getTextView() != null && !TextUtils.isEmpty(checkTextView.getTextView().getText())) {
                    sb.append("\n");
                    sb.append(checkTextView.getTextView().getText());
                }
                info.setText(sb);
            }
        };
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
                String username = usernameTextView.getText().toString();
                if (editableUsernameCell != null) {
                    editableUsernameCell.updateUsername(username);
                }
                checkUserName(username);
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

        checkTextView = new TextInfoPrivacyCell(context) {
            @Override
            public void setText(CharSequence text) {
                if (text != null) {
                    SpannableStringBuilder tagsString = AndroidUtilities.replaceTags(text.toString());
                    int index = tagsString.toString().indexOf('\n');
                    if (index >= 0) {
                        tagsString.replace(index, index + 1, " ");
                        tagsString.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_text_RedRegular)), 0, index, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    TypefaceSpan[] spans = tagsString.getSpans(0, tagsString.length(), TypefaceSpan.class);
                    final String username = usernameTextView == null || usernameTextView.getText() == null ? "" : usernameTextView.getText().toString();
                    for (int i = 0; i < spans.length; ++i) {
                        tagsString.setSpan(
                            new ClickableSpan() {
                                @Override
                                public void onClick(@NonNull View view) {
                                    Browser.openUrl(getContext(), "https://fragment.com/username/" + username);
                                }
                                @Override
                                public void updateDrawState(@NonNull TextPaint ds) {
                                    super.updateDrawState(ds);
                                    ds.setUnderlineText(false);
                                }
                            },
                            tagsString.getSpanStart(spans[i]),
                            tagsString.getSpanEnd(spans[i]),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                        tagsString.removeSpan(spans[i]);
                    }
                    text = tagsString;
                }
                super.setText(text);
            }

            ValueAnimator translateAnimator;
            int prevHeight = -1;

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if (prevHeight != -1 && linearLayout != null) {
                    ArrayList<View> viewsToTranslate = new ArrayList<>();
                    boolean passedMe = false;
                    for (int i = 0; i < linearLayout.getChildCount(); ++i) {
                        View child = linearLayout.getChildAt(i);
                        if (passedMe) {
                            viewsToTranslate.add(child);
                        } else if (child == this) {
                            passedMe = true;
                        }
                    }

                    float diff = prevHeight - getHeight();
                    if (translateAnimator != null) {
                        translateAnimator.cancel();
                    }
                    translateAnimator = ValueAnimator.ofFloat(0, 1);
                    translateAnimator.addUpdateListener(anm -> {
                        float t = 1f - (float) anm.getAnimatedValue();
                        for (int i = 0; i < viewsToTranslate.size(); ++i) {
                            View view = viewsToTranslate.get(i);
                            if (view != null) {
                                view.setTranslationY(diff * t);
                            }
                        }
                    });
                    translateAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    translateAnimator.setDuration(350);
                    translateAnimator.start();
                }
                prevHeight = getHeight();
            }
        };
        checkTextView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        checkTextView.setBottomPadding(6);
        linearLayout.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        typeInfoCell = new TextInfoPrivacyCell(context);
        typeInfoCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        linearLayout.addView(typeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        loadingAdminedCell = new LoadingCell(context);
        linearLayout.addView(loadingAdminedCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        adminnedChannelsLayout = new LinearLayout(context);
        adminnedChannelsLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        adminnedChannelsLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(adminnedChannelsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        adminedInfoCell = new ShadowSectionCell(context);
        linearLayout.addView(adminedInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayout.addView(usernamesListView = new UsernamesListView(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        usernamesListView.setVisibility(isPrivate || usernames.isEmpty() ? View.GONE : View.VISIBLE);

        manageLinksTextView = new TextCell(context);
        manageLinksTextView.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        manageLinksTextView.setTextAndIcon(LocaleController.getString("ManageInviteLinks", R.string.ManageInviteLinks), R.drawable.msg_link2, false);
        manageLinksTextView.setOnClickListener(v -> {
            ManageLinksActivity fragment = new ManageLinksActivity(chatId, 0, 0);
            fragment.setInfo(info, invite);
            presentFragment(fragment);
        });
        linearLayout.addView(manageLinksTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        manageLinksInfoCell = new TextInfoPrivacyCell(context);
        linearLayout.addView(manageLinksInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        joinContainer = new JoinToSendSettingsView(context, currentChat);
        joinContainer.showJoinToSend(info != null && info.linked_chat_id != 0);
        linearLayout.addView(joinContainer);

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

        String username = ChatObject.getPublicUsername(currentChat, true);
        if (!isPrivate && username != null) {
            ignoreTextChanges = true;
            usernameTextView.setText(username);
            usernameTextView.setSelection(username.length());
            ignoreTextChanges = false;
        }
        updatePrivatePublic();

        return fragmentView;
    }

    private Runnable enableDoneLoading = () -> updateDoneProgress(true);
    private ValueAnimator doneButtonDrawableAnimator;
    private void updateDoneProgress(boolean loading) {
        if (!loading) {
            AndroidUtilities.cancelRunOnUIThread(enableDoneLoading);
        }
        if (doneButtonDrawable != null) {
            if (doneButtonDrawableAnimator != null) {
                doneButtonDrawableAnimator.cancel();
            }
            doneButtonDrawableAnimator = ValueAnimator.ofFloat(doneButtonDrawable.getProgress(), loading ? 1f : 0);
            doneButtonDrawableAnimator.addUpdateListener(a -> {
                doneButtonDrawable.setProgress((float) a.getAnimatedValue());
                doneButtonDrawable.invalidateSelf();
            });
            doneButtonDrawableAnimator.setDuration((long) (200 * Math.abs(doneButtonDrawable.getProgress() - (loading ? 1f : 0))));
            doneButtonDrawableAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            doneButtonDrawableAnimator.start();
        }
    }

    private void showPremiumIncreaseLimitDialog() {
        if (getParentActivity() == null) {
            return;
        }
        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, getParentActivity(), LimitReachedBottomSheet.TYPE_PUBLIC_LINKS, currentAccount);
        limitReachedBottomSheet.parentIsChannel = isChannel;
        limitReachedBottomSheet.onSuccessRunnable = () -> {
            canCreatePublic = true;
            updatePrivatePublic();
        };
        showDialog(limitReachedBottomSheet);
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
        AndroidUtilities.runOnUIThread(enableDoneLoading, 200);
        if (currentChat.noforwards != isSaveRestricted) {
            getMessagesController().toggleChatNoForwards(chatId, currentChat.noforwards = isSaveRestricted);
        }
        if (trySetUsername() && tryUpdateJoinSettings()) {
            finishFragment();
        }
    }

    private boolean tryUpdateJoinSettings() {
        if (isChannel || joinContainer == null) {
            return true;
        }
        if (getParentActivity() == null) {
            return false;
        }
        boolean needToMigrate = !ChatObject.isChannel(currentChat) && (joinContainer.isJoinToSend || joinContainer.isJoinRequest);
        if (needToMigrate) {
            getMessagesController().convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                if (param != 0) {
                    chatId = param;
                    currentChat = getMessagesController().getChat(param);
                    processDone();
                }
            });
            return false;
        } else {
            if (currentChat.join_to_send != joinContainer.isJoinToSend) {
                getMessagesController().toggleChatJoinToSend(chatId, currentChat.join_to_send = joinContainer.isJoinToSend, null, null);
            }
            if (currentChat.join_request != joinContainer.isJoinRequest) {
                getMessagesController().toggleChatJoinRequest(chatId, currentChat.join_request = joinContainer.isJoinRequest, null, null);
            }
            return true;
        }
    }

    private Boolean editableUsernameWasActive, editableUsernameUpdated;

    private class UsernamesListView extends RecyclerListView {
        private final int VIEW_TYPE_HEADER = 0;
        private final int VIEW_TYPE_USERNAME = 1;
        private final int VIEW_TYPE_HELP = 2;

        private Adapter adapter;
        private LinearLayoutManager layoutManager;
        private ItemTouchHelper itemTouchHelper;

        public UsernamesListView(Context context) {
            super(context);

            setAdapter(adapter = new Adapter());
            setLayoutManager(layoutManager = new LinearLayoutManager(context));
            setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    if (view instanceof ChangeUsernameActivity.UsernameCell) {
                        TLRPC.TL_username username = ((ChangeUsernameActivity.UsernameCell) view).currentUsername;
                        if (username == null) {
                            return;
                        }
                        if (username.editable) {
                            if (fragmentView instanceof ScrollView) {
                                ((ScrollView) fragmentView).smoothScrollTo(0, linkContainer.getTop() - AndroidUtilities.dp(128));
                            }
                            usernameTextView.requestFocus();
                            AndroidUtilities.showKeyboard(usernameTextView);
                            return;
                        }

                        new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(username.active ? LocaleController.getString("UsernameDeactivateLink", R.string.UsernameDeactivateLink) : LocaleController.getString("UsernameActivateLink", R.string.UsernameActivateLink))
                            .setMessage(username.active ? LocaleController.getString("UsernameDeactivateLinkChannelMessage", R.string.UsernameDeactivateLinkChannelMessage) : LocaleController.getString("UsernameActivateLinkChannelMessage", R.string.UsernameActivateLinkChannelMessage))
                            .setPositiveButton(username.active ? LocaleController.getString("Hide", R.string.Hide) : LocaleController.getString("Show", R.string.Show), (di, e) -> {
                                if (username.editable) {
                                    if (editableUsernameWasActive == null) {
                                        editableUsernameWasActive = username.active;
                                    }
                                    editableUsernameUpdated = (username.active = !username.active);
                                } else {
                                    TLRPC.TL_channels_toggleUsername req = new TLRPC.TL_channels_toggleUsername();
                                    TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
                                    inputChannel.channel_id = currentChat.id;
                                    inputChannel.access_hash = currentChat.access_hash;
                                    req.channel = inputChannel;
                                    req.username = username.username;
                                    final boolean wasActive = username.active;
                                    req.active = !username.active;
                                    getConnectionsManager().sendRequest(req, (res, err) -> {
                                        AndroidUtilities.runOnUIThread(() -> {
                                            loadingUsernames.remove(req.username);
                                            if (res instanceof TLRPC.TL_boolTrue) {
                                                toggleUsername(username, !wasActive);
                                            } else if (err != null && "USERNAMES_ACTIVE_TOO_MUCH".equals(err.text)) {
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    new AlertDialog.Builder(getContext(), resourcesProvider)
                                                        .setTitle(LocaleController.getString("UsernameActivateErrorTitle", R.string.UsernameActivateErrorTitle))
                                                        .setMessage(LocaleController.getString("UsernameActivateErrorMessage", R.string.UsernameActivateErrorMessage))
                                                        .setPositiveButton(LocaleController.getString("OK", R.string.OK), (d, v) -> {
                                                            toggleUsername(username, wasActive, true);
                                                            checkDoneButton();
                                                        })
                                                        .show();
                                                });
                                            } else {
                                                toggleUsername(username, wasActive, true);
                                                checkDoneButton();
                                            }
                                            getMessagesController().updateUsernameActiveness(currentChat, username.username, username.active);
                                        });
                                    });
                                    loadingUsernames.add(username.username);
                                    ((ChangeUsernameActivity.UsernameCell) view).setLoading(true);
                                }
                                checkDoneButton();
//                                toggleUsername(username, username.active);
                            })
                            .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (di, e) -> {
                                di.dismiss();
                            })
                            .show();
                    }
                }
            });

            itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
            itemTouchHelper.attachToRecyclerView(this);
        }

        public void toggleUsername(TLRPC.TL_username username, boolean newActive) {
            toggleUsername(username, newActive, false);
        }

        public void toggleUsername(TLRPC.TL_username username, boolean newActive, boolean shake) {
            for (int i = 0; i < usernames.size(); ++i) {
                if (usernames.get(i) == username) {
                    toggleUsername(1 + i, newActive, shake);
                    break;
                }
            }
        }

        public void toggleUsername(int position, boolean newActive) {
            toggleUsername(position, newActive, false);
        }

        public void toggleUsername(int position, boolean newActive, boolean shake) {
            if (position - 1 < 0 || position - 1 >= usernames.size()) {
                return;
            }
            TLRPC.TL_username username = usernames.get(position - 1);
            if (username == null) {
                return;
            }

            int toIndex = -1;
            boolean changed = username.active != newActive;
            if (changed) {
                if (username.active = newActive) {
                    int firstInactive = -1;
                    for (int i = 0; i < usernames.size(); ++i) {
                        if (!usernames.get(i).active) {
                            firstInactive = i;
                            break;
                        }
                    }
                    if (firstInactive >= 0) {
                        toIndex = 1 + Math.max(0, firstInactive - 1);
                    }
                } else {
                    int lastActive = -1;
                    for (int i = 0; i < usernames.size(); ++i) {
                        if (usernames.get(i).active) {
                            lastActive = i;
                        }
                    }
                    if (lastActive >= 0) {
                        toIndex = 1 + Math.min(usernames.size() - 1, lastActive + 1);
                    }
                }
            }

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (getChildAdapterPosition(child) == position) {
                    if (shake) {
                        AndroidUtilities.shakeView(child);
                    }
                    if (child instanceof ChangeUsernameActivity.UsernameCell) {
                        ((ChangeUsernameActivity.UsernameCell) child).setLoading(loadingUsernames.contains(username.username));
                        ((ChangeUsernameActivity.UsernameCell) child).update();
                    }
                    break;
                }
            }

            if (toIndex >= 0 && position != toIndex) {
                adapter.moveElement(position, toIndex);
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(9999999, MeasureSpec.AT_MOST));
        }

        public class TouchHelperCallback extends ItemTouchHelper.Callback {

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getItemViewType() != VIEW_TYPE_USERNAME || !((ChangeUsernameActivity.UsernameCell) viewHolder.itemView).active) {
                    return makeMovementFlags(0, 0);
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType() ||
                    target.itemView instanceof ChangeUsernameActivity.UsernameCell && !((ChangeUsernameActivity.UsernameCell) target.itemView).active) {
                    return false;
                }
                adapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSelectedChanged(ViewHolder viewHolder, int actionState) {
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    ignoreScroll = false;
                    sendReorder();
                } else {
                    ignoreScroll = true;
                    cancelClickRunnables(false);
                    viewHolder.itemView.setPressed(true);
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setPressed(false);
            }
        }

        private boolean needReorder = false;
        private void sendReorder() {
            if (!needReorder || currentChat == null) {
                return;
            }
            needReorder = false;
            TLRPC.TL_channels_reorderUsernames req = new TLRPC.TL_channels_reorderUsernames();
            TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
            inputChannel.channel_id = currentChat.id;
            inputChannel.access_hash = currentChat.access_hash;
            req.channel = inputChannel;
            ArrayList<String> usernames = new ArrayList<>();
            for (int i = 0; i < editableUsernames.size(); ++i) {
                if (editableUsernames.get(i).active)
                    usernames.add(editableUsernames.get(i).username);
            }
            for (int i = 0; i < ChatEditTypeActivity.this.usernames.size(); ++i) {
                if (ChatEditTypeActivity.this.usernames.get(i).active)
                    usernames.add(ChatEditTypeActivity.this.usernames.get(i).username);
            }
            req.order = usernames;
            getConnectionsManager().sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_boolTrue) {}
            });
            updateChat();
        }

        private void updateChat() {
            currentChat.usernames.clear();
            currentChat.usernames.addAll(editableUsernames);
            currentChat.usernames.addAll(ChatEditTypeActivity.this.usernames);
            getMessagesController().putChat(currentChat, true);
        }

        private class Adapter extends RecyclerListView.SelectionAdapter {

            public void swapElements(int fromIndex, int toIndex) {
                int index1 = fromIndex - 1;
                int index2 = toIndex - 1;
                if (index1 >= usernames.size() || index2 >= usernames.size()) {
                    return;
                }
                if (fromIndex != toIndex) {
                    needReorder = true;
                }

                swapListElements(usernames, index1, index2);

                notifyItemMoved(fromIndex, toIndex);

                int end = 1 + usernames.size() - 1;
                if (fromIndex == end || toIndex == end) {
                    notifyItemChanged(fromIndex, 3);
                    notifyItemChanged(toIndex, 3);
                }
            }

            private void swapListElements(List<TLRPC.TL_username> list, int index1, int index2) {
                TLRPC.TL_username username1 = list.get(index1);
                list.set(index1, list.get(index2));
                list.set(index2, username1);
            }

            public void moveElement(int fromIndex, int toIndex) {
                int index1 = fromIndex - 1;
                int index2 = toIndex - 1;
                if (index1 >= usernames.size() || index2 >= usernames.size()) {
                    return;
                }

                TLRPC.TL_username username = usernames.remove(index1);
                usernames.add(index2, username);

                notifyItemMoved(fromIndex, toIndex);

                for (int i = 0; i < usernames.size(); ++i)
                    notifyItemChanged(1 + i);
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                switch (viewType) {
                    case VIEW_TYPE_HEADER:
                        return new RecyclerListView.Holder(new HeaderCell(getContext(), resourcesProvider));
                    case VIEW_TYPE_USERNAME:
                        return new RecyclerListView.Holder(new ChangeUsernameActivity.UsernameCell(getContext(), resourcesProvider) {
                            @Override
                            protected String getUsernameEditable() {
                                if (usernameTextView == null)
                                    return null;
                                return usernameTextView.getText().toString();
                            }
                        });
                    case VIEW_TYPE_HELP:
                        return new RecyclerListView.Holder(new TextInfoPrivacyCell(getContext(), resourcesProvider));
                }
                return null;
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                switch (holder.getItemViewType()) {
                    case VIEW_TYPE_HEADER:
                        ((HeaderCell) holder.itemView).setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                        ((HeaderCell) holder.itemView).setText(LocaleController.getString("UsernamesChannelHeader", R.string.UsernamesChannelHeader));
                        break;
                    case VIEW_TYPE_USERNAME:
                        TLRPC.TL_username username = usernames.get(position - 1);
                        if (((ChangeUsernameActivity.UsernameCell) holder.itemView).editable) {
                            editableUsernameCell = null;
                        }
                        ((ChangeUsernameActivity.UsernameCell) holder.itemView).set(username, position < usernames.size(), false);
                        if (username != null && username.editable) {
                            editableUsernameCell = (ChangeUsernameActivity.UsernameCell) holder.itemView;
                        }
                        break;
                    case VIEW_TYPE_HELP:
                        ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("UsernamesChannelHelp", R.string.UsernamesChannelHelp));
                        ((TextInfoPrivacyCell) holder.itemView).setBackgroundDrawable(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        break;
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return VIEW_TYPE_HEADER;
                } else if (position <= usernames.size()) {
                    return VIEW_TYPE_USERNAME;
                } else {
                    return VIEW_TYPE_HELP;
                }
            }

            @Override
            public int getItemCount() {
                return 2 + usernames.size();
            }

            @Override
            public boolean isEnabled(ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_USERNAME;
            }
        }

        private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override
        protected void dispatchDraw(Canvas canvas) {
            int fromIndex = 1, toIndex = 1 + usernames.size() - 1;

            int top = Integer.MAX_VALUE;
            int bottom = Integer.MIN_VALUE;

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child == null) {
                    continue;
                }
                int position = getChildAdapterPosition(child);
                if (position >= fromIndex && position <= toIndex) {
                    top = Math.min(child.getTop(), top);
                    bottom = Math.max(child.getBottom(), bottom);
                }
            }

            if (top < bottom) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                canvas.drawRect(0, top, getWidth(), bottom, backgroundPaint);
            }

            super.dispatchDraw(canvas);
        }
    }

    private boolean trySetUsername() {
        if (getParentActivity() == null) {
            return false;
        }
        String wasUsername = ChatObject.getPublicUsername(currentChat, true);
        if (!isPrivate && ((wasUsername == null && usernameTextView.length() != 0) || (wasUsername != null && !wasUsername.equalsIgnoreCase(usernameTextView.getText().toString())))) {
            if (usernameTextView.length() != 0 && !lastNameAvailable) {
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                AndroidUtilities.shakeView(checkTextView);
                updateDoneProgress(false);
                return false;
            }
        }

        String oldUserName = wasUsername != null ? wasUsername : "";
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
                getMessagesController().updateChannelUserName(this, chatId, newUserName, () -> {
                    currentChat = getMessagesController().getChat(chatId);
                    processDone();
                }, () -> {
                    updateDoneProgress(false);
                });
                return false;
            }
        }

        if (!tryDeactivateAllLinks()/* || !tryActivateEditableUsername()*/) {
            return false;
        }
        return true;
    }

    private boolean deactivatingLinks = false;
    private boolean tryDeactivateAllLinks() {
        if (!isPrivate || currentChat.usernames == null) {
            return true;
        }
        if (deactivatingLinks) {
            return false;
        }
        deactivatingLinks = true;
        boolean hasActive = false;
        for (int i = 0; i < currentChat.usernames.size(); ++i) {
            final TLRPC.TL_username username = currentChat.usernames.get(i);
            if (username != null && username.active && !username.editable) {
                hasActive = true;
            }
        }
        if (hasActive) {
            TLRPC.TL_channels_deactivateAllUsernames req = new TLRPC.TL_channels_deactivateAllUsernames();
            req.channel = MessagesController.getInputChannel(currentChat);
            getConnectionsManager().sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_boolTrue) {
                    for (int i = 0; i < currentChat.usernames.size(); ++i) {
                        final TLRPC.TL_username username = currentChat.usernames.get(i);
                        if (username != null && username.active && !username.editable) {
                            username.active = false;
                        }
                    }
                }
                deactivatingLinks = false;
                AndroidUtilities.runOnUIThread(this::processDone);
            });
        }
        return !hasActive;
    }

    private boolean activatingEditableLink = false;
    private boolean tryActivateEditableUsername() {
        if (isPrivate || usernames == null || editableUsernameWasActive == null || editableUsernameUpdated == null || editableUsernameWasActive == editableUsernameUpdated) {
            return true;
        }
        if (activatingEditableLink) {
            return false;
        }
        activatingEditableLink = true;
        String username = null;
        for (int i = 0; i < usernames.size(); ++i) {
            if (usernames.get(i) != null && usernames.get(i).editable) {
                username = usernames.get(i).username;
            }
        }
        if (username == null) {
            activatingEditableLink = false;
            return true;
        }
        TLRPC.TL_channels_toggleUsername req = new TLRPC.TL_channels_toggleUsername();
        req.channel = MessagesController.getInputChannel(currentChat);
        req.active = editableUsernameUpdated;
        req.username = username;
        getConnectionsManager().sendRequest(req, (res, err) -> {
            activatingEditableLink = false;
            if (err == null) {
                AndroidUtilities.runOnUIThread(this::processDone);
            } else {
                updateDoneProgress(false);
            }
        });
        return false;
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
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, getMessagesController().linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
                        } else {
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, getMessagesController().linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
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
                    }, false, 0);
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
        if (!isPrivate && !canCreatePublic && getUserConfig().isPremium()) {
            typeInfoCell.setText(LocaleController.getString("ChangePublicLimitReached", R.string.ChangePublicLimitReached));
            typeInfoCell.setTag(Theme.key_text_RedRegular);
            typeInfoCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
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
            manageLinksInfoCell.setText(LocaleController.getString("ManageLinksInfoHelp", R.string.ManageLinksInfoHelp));
            if (isPrivate) {
                typeInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                manageLinksInfoCell.setBackground(Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            } else {
                typeInfoCell.setBackgroundDrawable(checkTextView.getVisibility() == View.VISIBLE ? null : Theme.getThemedDrawable(typeInfoCell.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }
        radioButtonCell1.setChecked(!isPrivate, true);
        radioButtonCell2.setChecked(isPrivate, true);
        usernameTextView.clearFocus();
        if (joinContainer != null) {
            joinContainer.setVisibility(!isChannel && !isPrivate ? View.VISIBLE : View.GONE);
            joinContainer.showJoinToSend(info != null && info.linked_chat_id != 0);
        }
        if (usernamesListView != null) {
            usernamesListView.setVisibility(isPrivate || usernames.isEmpty() ? View.GONE : View.VISIBLE);
        }
        checkDoneButton();
    }

    private void checkDoneButton() {
        if (isPrivate || usernameTextView.length() > 0 || hasActiveLink()) {
            doneButton.setEnabled(true);
            doneButton.setAlpha(1.0f);
        } else {
            doneButton.setEnabled(false);
            doneButton.setAlpha(0.5f);
        }
    }

    public boolean hasActiveLink() {
        if (usernames == null) {
            return false;
        }
        for (int i = 0; i < usernames.size(); ++i) {
            TLRPC.TL_username u = usernames.get(i);
            if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                return true;
            }
        }
        return false;
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
                checkTextView.setTextColor(Theme.key_text_RedRegular);
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
                    checkTextView.setTextColor(Theme.key_text_RedRegular);
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    checkTextView.setText(LocaleController.getString("LinkInvalid", R.string.LinkInvalid));
                    checkTextView.setTextColor(Theme.key_text_RedRegular);
                    return false;
                }
            }
        }
        if (name == null || name.length() < 4) {
            if (isChannel) {
                checkTextView.setText(LocaleController.getString("LinkInvalidShort", R.string.LinkInvalidShort));
            } else {
                checkTextView.setText(LocaleController.getString("LinkInvalidShortMega", R.string.LinkInvalidShortMega));
            }
            checkTextView.setTextColor(Theme.key_text_RedRegular);
            return false;
        }
        if (name.length() > 32) {
            checkTextView.setText(LocaleController.getString("LinkInvalidLong", R.string.LinkInvalidLong));
            checkTextView.setTextColor(Theme.key_text_RedRegular);
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
                        if (error != null && "USERNAME_INVALID".equals(error.text) && req.username.length() == 4) {
                            checkTextView.setText(LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
                            checkTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        } else if (error != null && "USERNAME_PURCHASE_AVAILABLE".equals(error.text)) {
                            if (req.username.length() == 4) {
                                checkTextView.setText(LocaleController.getString("UsernameInvalidShortPurchase", R.string.UsernameInvalidShortPurchase));
                            } else {
                                checkTextView.setText(LocaleController.getString("UsernameInUsePurchase", R.string.UsernameInUsePurchase));
                            }
                            checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
                        } else if (error != null && "CHANNELS_ADMIN_PUBLIC_TOO_MUCH".equals(error.text)) {
                            canCreatePublic = false;
                            showPremiumIncreaseLimitDialog();
                        } else {
                            checkTextView.setText(LocaleController.getString("LinkInUse", R.string.LinkInUse));
                            checkTextView.setTextColor(Theme.key_text_RedRegular);
                        }
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
        themeDescriptions.add(new ThemeDescription(textCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));
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

        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText));

        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(typeInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));

        themeDescriptions.add(new ThemeDescription(manageLinksInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(manageLinksInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(manageLinksInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));

        themeDescriptions.add(new ThemeDescription(saveRestrictInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(saveRestrictInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(saveRestrictInfoCell, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));

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

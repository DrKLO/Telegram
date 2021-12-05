/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PrivacyControlActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private View doneButton;
    private RecyclerListView listView;
    private MessageCell messageCell;

    private int initialRulesType;
    private int initialRulesSubType;
    private ArrayList<Long> initialPlus = new ArrayList<>();
    private ArrayList<Long> initialMinus = new ArrayList<>();

    private int rulesType;
    private ArrayList<Long> currentPlus;
    private ArrayList<Long> currentMinus;

    private int currentType;
    private int currentSubType;

    private boolean prevSubtypeContacts;

    private int messageRow;
    private int sectionRow;
    private int everybodyRow;
    private int myContactsRow;
    private int nobodyRow;
    private int detailRow;
    private int shareSectionRow;
    private int alwaysShareRow;
    private int neverShareRow;
    private int shareDetailRow;
    private int phoneSectionRow;
    private int phoneEverybodyRow;
    private int phoneContactsRow;
    private int phoneDetailRow;
    private int p2pSectionRow;
    private int p2pRow;
    private int p2pDetailRow;
    private int rowCount;

    private final static int done_button = 1;

    public final static int PRIVACY_RULES_TYPE_LASTSEEN = 0;
    public final static int PRIVACY_RULES_TYPE_INVITE = 1;
    public final static int PRIVACY_RULES_TYPE_CALLS = 2;
    public final static int PRIVACY_RULES_TYPE_P2P = 3;
    public final static int PRIVACY_RULES_TYPE_PHOTO = 4;
    public final static int PRIVACY_RULES_TYPE_FORWARDS = 5;
    public final static int PRIVACY_RULES_TYPE_PHONE = 6;
    public final static int PRIVACY_RULES_TYPE_ADDED_BY_PHONE = 7;

    public final static int TYPE_EVERYBODY = 0;
    public final static int TYPE_NOBODY = 1;
    public final static int TYPE_CONTACTS = 2;

    private class MessageCell extends FrameLayout {

        private final Runnable invalidateRunnable = this::invalidate;

        private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;

        private ChatMessageCell cell;
        private Drawable backgroundDrawable;
        private Drawable shadowDrawable;
        private HintView hintView;
        private MessageObject messageObject;

        public MessageCell(Context context) {
            super(context);

            setWillNotDraw(false);
            setClipToPadding(false);

            shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
            setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

            TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());

            TLRPC.Message message = new TLRPC.TL_message();
            message.message = LocaleController.getString("PrivacyForwardsMessageLine", R.string.PrivacyForwardsMessageLine);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 257 + TLRPC.MESSAGE_FLAG_FWD;
            message.from_id = new TLRPC.TL_peerUser();
            message.id = 1;
            message.fwd_from = new TLRPC.TL_messageFwdHeader();
            message.fwd_from.from_name = ContactsController.formatName(currentUser.first_name, currentUser.last_name);
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            messageObject = new MessageObject(currentAccount, message, true, false);
            messageObject.eventId = 1;
            messageObject.resetLayout();

            cell = new ChatMessageCell(context);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

            });
            cell.isChat = false;
            cell.setFullyDraw(true);
            cell.setMessageObject(messageObject, null, false, false);
            addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            hintView = new HintView(context, 1, true);
            addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            hintView.showForMessageCell(cell, false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
            if (newDrawable != null && backgroundDrawable != newDrawable) {
                if (backgroundGradientDisposable != null) {
                    backgroundGradientDisposable.dispose();
                    backgroundGradientDisposable = null;
                }
                backgroundDrawable = newDrawable;
            }
            if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable || backgroundDrawable instanceof MotionBackgroundDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                    backgroundGradientDisposable = ((BackgroundGradientDrawable) backgroundDrawable).drawExactBoundsSize(canvas, this);
                } else {
                    backgroundDrawable.draw(canvas);
                }
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) backgroundDrawable;
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    backgroundDrawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                } else {
                    int viewHeight = getMeasuredHeight();
                    float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (viewHeight - height) / 2;
                    canvas.save();
                    canvas.clipRect(0, 0, width, getMeasuredHeight());
                    backgroundDrawable.setBounds(x, y, x + width, y + height);
                }
                backgroundDrawable.draw(canvas);
                canvas.restore();
            } else {
                super.onDraw(canvas);
            }

            shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            shadowDrawable.draw(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        protected void dispatchSetPressed(boolean pressed) {

        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            cell.invalidate();
        }
    }

    public PrivacyControlActivity(int type) {
        this(type, false);
    }

    public PrivacyControlActivity(int type, boolean load) {
        super();
        rulesType = type;
        if (load) {
            ContactsController.getInstance(currentAccount).loadPrivacySettings();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        checkPrivacy();
        updateRows(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.privacyRulesUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.privacyRulesUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public View createView(Context context) {
        if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            messageCell = new MessageCell(context);
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            actionBar.setTitle(LocaleController.getString("PrivacyPhone", R.string.PrivacyPhone));
        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            actionBar.setTitle(LocaleController.getString("PrivacyForwards", R.string.PrivacyForwards));
        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
            actionBar.setTitle(LocaleController.getString("PrivacyProfilePhoto", R.string.PrivacyProfilePhoto));
        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
            actionBar.setTitle(LocaleController.getString("PrivacyP2P", R.string.PrivacyP2P));
        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
            actionBar.setTitle(LocaleController.getString("Calls", R.string.Calls));
        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
            actionBar.setTitle(LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels));
        } else {
            actionBar.setTitle(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
        boolean hasChanges = hasChanges();
        doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
        doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
        doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        doneButton.setEnabled(hasChanges);

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == nobodyRow || position == everybodyRow || position == myContactsRow) {
                int newType;
                if (position == nobodyRow) {
                    newType = TYPE_NOBODY;
                } else if (position == everybodyRow) {
                    newType = TYPE_EVERYBODY;
                } else {
                    newType = TYPE_CONTACTS;
                }
                if (newType == currentType) {
                    return;
                }
                currentType = newType;
                updateDoneButton();
                updateRows(true);
            } else if (position == phoneContactsRow || position == phoneEverybodyRow) {
                int newType;
                if (position == phoneEverybodyRow) {
                    newType = 0;
                } else {
                    newType = 1;
                }
                if (newType == currentSubType) {
                    return;
                }
                currentSubType = newType;
                updateDoneButton();
                updateRows(true);
            } else if (position == neverShareRow || position == alwaysShareRow) {
                ArrayList<Long> createFromArray;
                if (position == neverShareRow) {
                    createFromArray = currentMinus;
                } else {
                    createFromArray = currentPlus;
                }
                if (createFromArray.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putBoolean(position == neverShareRow ? "isNeverShare" : "isAlwaysShare", true);
                    args.putInt("chatAddType", rulesType != PRIVACY_RULES_TYPE_LASTSEEN ? 1 : 0);
                    GroupCreateActivity fragment = new GroupCreateActivity(args);
                    fragment.setDelegate(ids -> {
                        if (position == neverShareRow) {
                            currentMinus = ids;
                            for (int a = 0; a < currentMinus.size(); a++) {
                                currentPlus.remove(currentMinus.get(a));
                            }
                        } else {
                            currentPlus = ids;
                            for (int a = 0; a < currentPlus.size(); a++) {
                                currentMinus.remove(currentPlus.get(a));
                            }
                        }
                        updateDoneButton();
                        listAdapter.notifyDataSetChanged();
                    });
                    presentFragment(fragment);
                } else {
                    PrivacyUsersActivity fragment = new PrivacyUsersActivity(PrivacyUsersActivity.TYPE_PRIVACY, createFromArray, rulesType != PRIVACY_RULES_TYPE_LASTSEEN, position == alwaysShareRow);
                    fragment.setDelegate((ids, added) -> {
                        if (position == neverShareRow) {
                            currentMinus = ids;
                            if (added) {
                                for (int a = 0; a < currentMinus.size(); a++) {
                                    currentPlus.remove(currentMinus.get(a));
                                }
                            }
                        } else {
                            currentPlus = ids;
                            if (added) {
                                for (int a = 0; a < currentPlus.size(); a++) {
                                    currentMinus.remove(currentPlus.get(a));
                                }
                            }
                        }
                        updateDoneButton();
                        listAdapter.notifyDataSetChanged();
                    });
                    presentFragment(fragment);
                }
            } else if (position == p2pRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_P2P));
            }
        });

        setMessageText();

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            checkPrivacy();
        } else if (id == NotificationCenter.emojiLoaded) {
            listView.invalidateViews();
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (messageCell != null) {
                messageCell.invalidate();
            }
        }
    }

    private void updateDoneButton() {
        boolean hasChanges = hasChanges();
        doneButton.setEnabled(hasChanges);
        doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
    }

    private void applyCurrentPrivacySettings() {
        TLRPC.TL_account_setPrivacy req = new TLRPC.TL_account_setPrivacy();
        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneNumber();
            if (currentType == TYPE_NOBODY) {
                TLRPC.TL_account_setPrivacy req2 = new TLRPC.TL_account_setPrivacy();
                req2.key = new TLRPC.TL_inputPrivacyKeyAddedByPhone();
                if (currentSubType == 0) {
                    req2.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                } else {
                    req2.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        TLRPC.TL_account_privacyRules privacyRules = (TLRPC.TL_account_privacyRules) response;
                        ContactsController.getInstance(currentAccount).setPrivacyRules(privacyRules.rules, PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            req.key = new TLRPC.TL_inputPrivacyKeyForwards();
        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
            req.key = new TLRPC.TL_inputPrivacyKeyProfilePhoto();
        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneP2P();
        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
            req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
        } else {
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        }
        if (currentType != 0 && currentPlus.size() > 0) {
            TLRPC.TL_inputPrivacyValueAllowUsers usersRule = new TLRPC.TL_inputPrivacyValueAllowUsers();
            TLRPC.TL_inputPrivacyValueAllowChatParticipants chatsRule = new TLRPC.TL_inputPrivacyValueAllowChatParticipants();
            for (int a = 0; a < currentPlus.size(); a++) {
                long id = currentPlus.get(a);
                if (DialogObject.isUserDialog(id)) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(id);
                    if (user != null) {
                        TLRPC.InputUser inputUser = MessagesController.getInstance(currentAccount).getInputUser(user);
                        if (inputUser != null) {
                            usersRule.users.add(inputUser);
                        }
                    }
                } else {
                    chatsRule.chats.add(-id);
                }
            }
            req.rules.add(usersRule);
            req.rules.add(chatsRule);
        }
        if (currentType != 1 && currentMinus.size() > 0) {
            TLRPC.TL_inputPrivacyValueDisallowUsers usersRule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
            TLRPC.TL_inputPrivacyValueDisallowChatParticipants chatsRule = new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();
            for (int a = 0; a < currentMinus.size(); a++) {
                long id = currentMinus.get(a);
                if (DialogObject.isUserDialog(id)) {
                    TLRPC.User user = getMessagesController().getUser(id);
                    if (user != null) {
                        TLRPC.InputUser inputUser = getMessagesController().getInputUser(user);
                        if (inputUser != null) {
                            usersRule.users.add(inputUser);
                        }
                    }
                } else {
                    chatsRule.chats.add(-id);
                }
            }
            req.rules.add(usersRule);
            req.rules.add(chatsRule);
        }
        if (currentType == TYPE_EVERYBODY) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
        } else if (currentType == TYPE_NOBODY) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        } else if (currentType == TYPE_CONTACTS) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
        }
        AlertDialog progressDialog = null;
        if (getParentActivity() != null) {
            progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.show();
        }
        final AlertDialog progressDialogFinal = progressDialog;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                if (progressDialogFinal != null) {
                    progressDialogFinal.dismiss();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (error == null) {
                TLRPC.TL_account_privacyRules privacyRules = (TLRPC.TL_account_privacyRules) response;
                MessagesController.getInstance(currentAccount).putUsers(privacyRules.users, false);
                MessagesController.getInstance(currentAccount).putChats(privacyRules.chats, false);
                ContactsController.getInstance(currentAccount).setPrivacyRules(privacyRules.rules, rulesType);
                finishFragment();
            } else {
                showErrorAlert();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void showErrorAlert() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(LocaleController.getString("PrivacyFloodControlError", R.string.PrivacyFloodControlError));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    private void checkPrivacy() {
        currentPlus = new ArrayList<>();
        currentMinus = new ArrayList<>();
        ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(rulesType);
        if (privacyRules == null || privacyRules.size() == 0) {
            currentType = TYPE_NOBODY;
        } else {
            int type = -1;
            for (int a = 0; a < privacyRules.size(); a++) {
                TLRPC.PrivacyRule rule = privacyRules.get(a);
                if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                    TLRPC.TL_privacyValueAllowChatParticipants privacyValueAllowChatParticipants = (TLRPC.TL_privacyValueAllowChatParticipants) rule;
                    for (int b = 0, N = privacyValueAllowChatParticipants.chats.size(); b < N; b++) {
                        currentPlus.add(-privacyValueAllowChatParticipants.chats.get(b));
                    }
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                    TLRPC.TL_privacyValueDisallowChatParticipants privacyValueDisallowChatParticipants = (TLRPC.TL_privacyValueDisallowChatParticipants) rule;
                    for (int b = 0, N = privacyValueDisallowChatParticipants.chats.size(); b < N; b++) {
                        currentMinus.add(-privacyValueDisallowChatParticipants.chats.get(b));
                    }
                } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                    TLRPC.TL_privacyValueAllowUsers privacyValueAllowUsers = (TLRPC.TL_privacyValueAllowUsers) rule;
                    currentPlus.addAll(privacyValueAllowUsers.users);
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                    TLRPC.TL_privacyValueDisallowUsers privacyValueDisallowUsers = (TLRPC.TL_privacyValueDisallowUsers) rule;
                    currentMinus.addAll(privacyValueDisallowUsers.users);
                } else if (type == -1) {
                    if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                        type = 0;
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                        type = 1;
                    } else {
                        type = 2;
                    }
                }
            }
            if (type == TYPE_EVERYBODY || type == -1 && currentMinus.size() > 0) {
                currentType = TYPE_EVERYBODY;
            } else if (type == TYPE_CONTACTS || type == -1 && currentMinus.size() > 0 && currentPlus.size() > 0) {
                currentType = TYPE_CONTACTS;
            } else if (type == TYPE_NOBODY || type == -1 && currentPlus.size() > 0) {
                currentType = TYPE_NOBODY;
            }
            if (doneButton != null) {
                doneButton.setAlpha(0.0f);
                doneButton.setScaleX(0.0f);
                doneButton.setScaleY(0.0f);
                doneButton.setEnabled(false);
            }
        }
        initialPlus.clear();
        initialMinus.clear();
        initialRulesType = currentType;
        initialPlus.addAll(currentPlus);
        initialMinus.addAll(currentMinus);

        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
            if (privacyRules == null || privacyRules.size() == 0) {
                currentSubType = 0;
            } else {
                for (int a = 0; a < privacyRules.size(); a++) {
                    TLRPC.PrivacyRule rule = privacyRules.get(a);
                    if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                        currentSubType = 0;
                        break;
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                        currentSubType = 2;
                        break;
                    } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                        currentSubType = 1;
                        break;
                    }
                }
            }
            initialRulesSubType = currentSubType;
        }

        updateRows(false);
    }

    private boolean hasChanges() {
        if (initialRulesType != currentType) {
            return true;
        }
        if (rulesType == PRIVACY_RULES_TYPE_PHONE && currentType == TYPE_NOBODY && initialRulesSubType != currentSubType) {
            return true;
        }
        if (initialMinus.size() != currentMinus.size()) {
            return true;
        }
        if (initialPlus.size() != currentPlus.size()) {
            return true;
        }
        Collections.sort(initialPlus);
        Collections.sort(currentPlus);
        if (!initialPlus.equals(currentPlus)) {
            return true;
        }
        Collections.sort(initialMinus);
        Collections.sort(currentMinus);
        if (!initialMinus.equals(currentMinus)) {
            return true;
        }
        return false;
    }

    private void updateRows(boolean animated) {
        int prevAlwaysShareRow = alwaysShareRow;
        int prevNeverShareRow = neverShareRow;
        int prevPhoneDetailRow = phoneDetailRow;
        int prevDetailRow = detailRow;
        boolean newSubtype = currentType == TYPE_NOBODY && currentSubType == 1;

        rowCount = 0;
        if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            messageRow = rowCount++;
        } else {
            messageRow = -1;
        }
        sectionRow = rowCount++;
        everybodyRow = rowCount++;
        myContactsRow = rowCount++;
        if (rulesType != PRIVACY_RULES_TYPE_LASTSEEN && rulesType != PRIVACY_RULES_TYPE_CALLS && rulesType != PRIVACY_RULES_TYPE_P2P && rulesType != PRIVACY_RULES_TYPE_FORWARDS && rulesType != PRIVACY_RULES_TYPE_PHONE) {
            nobodyRow = -1;
        } else {
            nobodyRow = rowCount++;
        }
        if (rulesType == PRIVACY_RULES_TYPE_PHONE && currentType == TYPE_NOBODY) {
            phoneDetailRow = rowCount++;
            phoneSectionRow = rowCount++;
            phoneEverybodyRow = rowCount++;
            phoneContactsRow = rowCount++;
        } else {
            phoneDetailRow = -1;
            phoneSectionRow = -1;
            phoneEverybodyRow = -1;
            phoneContactsRow = -1;
        }
        detailRow = rowCount++;
        shareSectionRow = rowCount++;
        if (currentType == TYPE_NOBODY || currentType == TYPE_CONTACTS) {
            alwaysShareRow = rowCount++;
        } else {
            alwaysShareRow = -1;
        }
        if (currentType == TYPE_EVERYBODY || currentType == TYPE_CONTACTS) {
            neverShareRow = rowCount++;
        } else {
            neverShareRow = -1;
        }
        shareDetailRow = rowCount++;
        if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
            p2pSectionRow = rowCount++;
            p2pRow = rowCount++;
            p2pDetailRow = rowCount++;
        } else {
            p2pSectionRow = -1;
            p2pRow = -1;
            p2pDetailRow = -1;
        }

        setMessageText();

        if (listAdapter != null) {
            if (animated) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (!(child instanceof RadioCell)) {
                        continue;
                    }
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                    if (holder == null) {
                        continue;
                    }
                    int position = holder.getAdapterPosition();
                    RadioCell radioCell = (RadioCell) child;
                    if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
                        int checkedType;
                        if (position == everybodyRow) {
                            checkedType = TYPE_EVERYBODY;
                        } else if (position == myContactsRow) {
                            checkedType = TYPE_CONTACTS;
                        } else {
                            checkedType = TYPE_NOBODY;
                        }
                        radioCell.setChecked(currentType == checkedType, true);
                    } else {
                        int checkedType;
                        if (position == phoneContactsRow) {
                            checkedType = 1;
                        } else {
                            checkedType = 0;
                        }
                        radioCell.setChecked(currentSubType == checkedType, true);
                    }
                }
                if (prevSubtypeContacts != newSubtype) {
                    listAdapter.notifyItemChanged(prevDetailRow);
                }
                if (alwaysShareRow == -1 && prevAlwaysShareRow != -1 && neverShareRow != -1 && prevNeverShareRow == -1 || alwaysShareRow != -1 && prevAlwaysShareRow == -1 && neverShareRow == -1 && prevNeverShareRow != -1) {
                    listAdapter.notifyItemChanged(alwaysShareRow == -1 ? prevAlwaysShareRow : prevNeverShareRow);
                    if (phoneDetailRow == -1 && prevPhoneDetailRow != -1) {
                        listAdapter.notifyItemRangeRemoved(prevPhoneDetailRow, 4);
                    } else if (phoneDetailRow != -1 && prevPhoneDetailRow == -1) {
                        listAdapter.notifyItemRangeInserted(phoneDetailRow, 4);
                    }
                } else {
                    if (alwaysShareRow == -1 && prevAlwaysShareRow != -1) {
                        listAdapter.notifyItemRemoved(prevAlwaysShareRow);
                    } else if (alwaysShareRow != -1 && prevAlwaysShareRow == -1) {
                        listAdapter.notifyItemInserted(alwaysShareRow);
                    }
                    if (neverShareRow == -1 && prevNeverShareRow != -1) {
                        listAdapter.notifyItemRemoved(prevNeverShareRow);
                        if (phoneDetailRow == -1 && prevPhoneDetailRow != -1) {
                            listAdapter.notifyItemRangeRemoved(prevPhoneDetailRow, 4);
                        } else if (phoneDetailRow != -1 && prevPhoneDetailRow == -1) {
                            listAdapter.notifyItemRangeInserted(phoneDetailRow, 4);
                        }
                    } else if (neverShareRow != -1 && prevNeverShareRow == -1) {
                        if (phoneDetailRow == -1 && prevPhoneDetailRow != -1) {
                            listAdapter.notifyItemRangeRemoved(prevPhoneDetailRow, 4);
                        } else if (phoneDetailRow != -1 && prevPhoneDetailRow == -1) {
                            listAdapter.notifyItemRangeInserted(phoneDetailRow, 4);
                        }
                        listAdapter.notifyItemInserted(neverShareRow);
                    }
                }
            } else {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void setMessageText() {
        if (messageCell != null) {
            messageCell.messageObject.messageOwner.fwd_from.from_id = new TLRPC.TL_peerUser();
            if (currentType == TYPE_EVERYBODY) {
                messageCell.hintView.setOverrideText(LocaleController.getString("PrivacyForwardsEverybody", R.string.PrivacyForwardsEverybody));
                messageCell.messageObject.messageOwner.fwd_from.from_id.user_id = 1;
            } else if (currentType == TYPE_NOBODY) {
                messageCell.hintView.setOverrideText(LocaleController.getString("PrivacyForwardsNobody", R.string.PrivacyForwardsNobody));
                messageCell.messageObject.messageOwner.fwd_from.from_id.user_id = 0;
            } else {
                messageCell.hintView.setOverrideText(LocaleController.getString("PrivacyForwardsContacts", R.string.PrivacyForwardsContacts));
                messageCell.messageObject.messageOwner.fwd_from.from_id.user_id = 1;
            }
            messageCell.cell.forceResetMessageObject();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    private void processDone() {
        if (getParentActivity() == null) {
            return;
        }

        if (currentType != 0 && rulesType == PRIVACY_RULES_TYPE_LASTSEEN) {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean showed = preferences.getBoolean("privacyAlertShowed", false);
            if (!showed) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                    builder.setMessage(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo));
                } else {
                    builder.setMessage(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                }
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                    applyCurrentPrivacySettings();
                    preferences.edit().putBoolean("privacyAlertShowed", true).commit();
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                return;
            }
        }
        applyCurrentPrivacySettings();
    }

    private boolean checkDiscard() {
        if (doneButton.getAlpha() == 1.0f) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            builder.setMessage(LocaleController.getString("PrivacySettingsChangedAlert", R.string.PrivacySettingsChangedAlert));
            builder.setPositiveButton(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    @Override
    public boolean canBeginSlide() {
        return checkDiscard();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == nobodyRow || position == everybodyRow || position == myContactsRow || position == neverShareRow || position == alwaysShareRow ||
                    position == p2pRow && !ContactsController.getInstance(currentAccount).getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_P2P);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = messageCell;
                    break;
                case 5:
                default:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        private int getUsersCount(ArrayList<Long> arrayList) {
            int count = 0;
            for (int a = 0; a < arrayList.size(); a++) {
                long id = arrayList.get(a);
                if (id > 0) {
                    count++;
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-id);
                    if (chat != null) {
                        count += chat.participants_count;
                    }
                }
            }
            return count;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == alwaysShareRow) {
                        String value;
                        if (currentPlus.size() != 0) {
                            value = LocaleController.formatPluralString("Users", getUsersCount(currentPlus));
                        } else {
                            value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                        }
                        if (rulesType != PRIVACY_RULES_TYPE_LASTSEEN) {
                            textCell.setTextAndValue(LocaleController.getString("AlwaysAllow", R.string.AlwaysAllow), value, neverShareRow != -1);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("AlwaysShareWith", R.string.AlwaysShareWith), value, neverShareRow != -1);
                        }
                    } else if (position == neverShareRow) {
                        String value;
                        int count = 0;
                        if (currentMinus.size() != 0) {
                            value = LocaleController.formatPluralString("Users", getUsersCount(currentMinus));
                        } else {
                            value = LocaleController.getString("EmpryUsersPlaceholder", R.string.EmpryUsersPlaceholder);
                        }
                        if (rulesType != PRIVACY_RULES_TYPE_LASTSEEN) {
                            textCell.setTextAndValue(LocaleController.getString("NeverAllow", R.string.NeverAllow), value, false);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("NeverShareWith", R.string.NeverShareWith), value, false);
                        }
                    } else if (position == p2pRow) {
                        String value;
                        if (ContactsController.getInstance(currentAccount).getLoadingPrivicyInfo(ContactsController.PRIVACY_RULES_TYPE_P2P)) {
                            value = LocaleController.getString("Loading", R.string.Loading);
                        } else {
                            value = PrivacySettingsActivity.formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_P2P);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PrivacyP2P2", R.string.PrivacyP2P2), value, false);
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    int backgroundResId = 0;
                    if (position == detailRow) {
                        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
                            if (prevSubtypeContacts = (currentType == TYPE_NOBODY && currentSubType == 1)) {
                                privacyCell.setText(LocaleController.getString("PrivacyPhoneInfo3", R.string.PrivacyPhoneInfo3));
                            } else {
                                privacyCell.setText(LocaleController.getString("PrivacyPhoneInfo", R.string.PrivacyPhoneInfo));
                            }
                        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
                            privacyCell.setText(LocaleController.getString("PrivacyForwardsInfo", R.string.PrivacyForwardsInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
                            privacyCell.setText(LocaleController.getString("PrivacyProfilePhotoInfo", R.string.PrivacyProfilePhotoInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                            privacyCell.setText(LocaleController.getString("PrivacyCallsP2PHelp", R.string.PrivacyCallsP2PHelp));
                        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            privacyCell.setText(LocaleController.getString("WhoCanCallMeInfo", R.string.WhoCanCallMeInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                            privacyCell.setText(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString("CustomHelp", R.string.CustomHelp));
                        }
                        backgroundResId = R.drawable.greydivider;
                    } else if (position == shareDetailRow) {
                        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
                            privacyCell.setText(LocaleController.getString("PrivacyPhoneInfo2", R.string.PrivacyPhoneInfo2));
                        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
                            privacyCell.setText(LocaleController.getString("PrivacyForwardsInfo2", R.string.PrivacyForwardsInfo2));
                        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
                            privacyCell.setText(LocaleController.getString("PrivacyProfilePhotoInfo2", R.string.PrivacyProfilePhotoInfo2));
                        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                            privacyCell.setText(LocaleController.getString("CustomP2PInfo", R.string.CustomP2PInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            privacyCell.setText(LocaleController.getString("CustomCallInfo", R.string.CustomCallInfo));
                        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                            privacyCell.setText(LocaleController.getString("CustomShareInfo", R.string.CustomShareInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString("CustomShareSettingsHelp", R.string.CustomShareSettingsHelp));
                        }
                        if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            backgroundResId = R.drawable.greydivider;
                        } else {
                            backgroundResId = R.drawable.greydivider_bottom;
                        }
                    } else if (position == p2pDetailRow) {
                        backgroundResId = R.drawable.greydivider_bottom;
                    }
                    if (backgroundResId != 0) {
                        Drawable drawable = Theme.getThemedDrawable(mContext, backgroundResId, Theme.key_windowBackgroundGrayShadow);
                        CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                        combinedDrawable.setFullsize(true);
                        privacyCell.setBackgroundDrawable(combinedDrawable);
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == sectionRow) {
                        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
                            headerCell.setText(LocaleController.getString("PrivacyPhoneTitle", R.string.PrivacyPhoneTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
                            headerCell.setText(LocaleController.getString("PrivacyForwardsTitle", R.string.PrivacyForwardsTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
                            headerCell.setText(LocaleController.getString("PrivacyProfilePhotoTitle", R.string.PrivacyProfilePhotoTitle));
                        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                            headerCell.setText(LocaleController.getString("P2PEnabledWith", R.string.P2PEnabledWith));
                        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
                            headerCell.setText(LocaleController.getString("WhoCanCallMe", R.string.WhoCanCallMe));
                        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
                            headerCell.setText(LocaleController.getString("WhoCanAddMe", R.string.WhoCanAddMe));
                        } else {
                            headerCell.setText(LocaleController.getString("LastSeenTitle", R.string.LastSeenTitle));
                        }
                    } else if (position == shareSectionRow) {
                        headerCell.setText(LocaleController.getString("AddExceptions", R.string.AddExceptions));
                    } else if (position == p2pSectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyP2PHeader", R.string.PrivacyP2PHeader));
                    } else if (position == phoneSectionRow) {
                        headerCell.setText(LocaleController.getString("PrivacyPhoneTitle2", R.string.PrivacyPhoneTitle2));
                    }
                    break;
                case 3:
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
                        if (position == everybodyRow) {
                            if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                                radioCell.setText(LocaleController.getString("P2PEverybody", R.string.P2PEverybody), currentType == TYPE_EVERYBODY, true);
                            } else {
                                radioCell.setText(LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody), currentType == TYPE_EVERYBODY, true);
                            }
                        } else if (position == myContactsRow) {
                            if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                                radioCell.setText(LocaleController.getString("P2PContacts", R.string.P2PContacts), currentType == TYPE_CONTACTS, nobodyRow != -1);
                            } else {
                                radioCell.setText(LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts), currentType == TYPE_CONTACTS, nobodyRow != -1);
                            }
                        } else {
                            if (rulesType == PRIVACY_RULES_TYPE_P2P) {
                                radioCell.setText(LocaleController.getString("P2PNobody", R.string.P2PNobody), currentType == TYPE_NOBODY, false);
                            } else {
                                radioCell.setText(LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody), currentType == TYPE_NOBODY, false);
                            }
                        }
                    } else {
                        if (position == phoneContactsRow) {
                            radioCell.setText(LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts), currentSubType == 1, false);
                        } else if (position == phoneEverybodyRow) {
                            radioCell.setText(LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody), currentSubType == 0, true);
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == alwaysShareRow || position == neverShareRow || position == p2pRow) {
                return 0;
            } else if (position == shareDetailRow || position == detailRow || position == p2pDetailRow) {
                return 1;
            } else if (position == sectionRow || position == shareSectionRow || position == p2pSectionRow || position == phoneSectionRow) {
                return 2;
            } else if (position == everybodyRow || position == myContactsRow || position == nobodyRow || position == phoneEverybodyRow || position == phoneContactsRow) {
                return 3;
            } else if (position == messageRow) {
                return 4;
            } else if (position == phoneDetailRow) {
                return 5;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, RadioCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgInDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgInMediaDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient1));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient2));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient3));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgOutDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, Theme.chat_msgOutMediaDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextIn));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextOut));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inTimeSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outTimeSelectedText));

        return themeDescriptions;
    }
}

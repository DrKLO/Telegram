package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Components.TextStyleSpan.FLAG_STYLE_SPOILER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_chatlists;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FolderBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.QRCodeBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;

public class FilterChatlistActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;

    MessagesController.DialogFilter filter;
    TL_chatlists.TL_exportedChatlistInvite invite;

    private static final int MAX_NAME_LENGTH = 32;

    private ArrayList<Long> selectedPeers = new ArrayList<>();
    private ArrayList<Long> allowedPeers = new ArrayList<>();
    private ArrayList<Long> peers = new ArrayList<>();

    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    private int shiftDp = -5;
    private long lastClickedDialogId, lastClicked;

    public FilterChatlistActivity(MessagesController.DialogFilter filter, TL_chatlists.TL_exportedChatlistInvite invite) {
        super();

        this.filter = filter;
        this.invite = invite;
    }

    private Utilities.Callback<TL_chatlists.TL_exportedChatlistInvite> onDelete;
    private Utilities.Callback<TL_chatlists.TL_exportedChatlistInvite> onEdit;

    public void setOnDelete(Utilities.Callback<TL_chatlists.TL_exportedChatlistInvite> onDelete) {
        this.onDelete = onDelete;
    }

    public void setOnEdit(Utilities.Callback<TL_chatlists.TL_exportedChatlistInvite> onEdit) {
        this.onEdit = onEdit;
    }

    private void updateActionBarTitle(boolean animated) {
        String title = TextUtils.isEmpty(invite == null ? null : invite.title) ? LocaleController.getString(R.string.FilterShare) : invite.title;
        if (animated) {
            actionBar.setTitleAnimated(title, false, 220);
        } else {
            actionBar.setTitle(title);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        updateActionBarTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == 1) {
                    if (Math.abs(doneButtonAlpha - 1) < 0.1f) {
                        save();
                    } else if (Math.abs(doneButtonAlpha - 0.5f) < 0.1f) {
                        shakeHeader();
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = menu.addItemWithWidth(1, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        checkDoneButton();

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context) {
            @Override
            public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
                return false;
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter());
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }

            if (view instanceof GroupCreateUserCell) {
                long did = peers.get(position - chatsStartRow);
                if (selectedPeers.contains(did)) {
                    selectedPeers.remove(did);
                    peersChanged = true;
                    checkDoneButton();
                    ((GroupCreateUserCell) view).setChecked(false, true);
                } else if (allowedPeers.contains(did)) {
                    if (selectedPeers.size() + 1 > getMaxChats()) {
                        showDialog(new LimitReachedBottomSheet(this, getContext(), LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, currentAccount, null));
                        return;
                    }
                    selectedPeers.add(did);
                    peersChanged = true;
                    checkDoneButton();
                    ((GroupCreateUserCell) view).setChecked(true, true);
                } else {
                    AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    ArrayList<TLObject> array = new ArrayList<>();
                    String text;
                    if (did >= 0) {
                        array.add(getMessagesController().getUser(did));
                        TLRPC.User user = getMessagesController().getUser(did);
                        if (user != null && user.bot) {
                            text = LocaleController.getString(R.string.FilterInviteBotToast);
                        } else {
                            text = LocaleController.getString(R.string.FilterInviteUserToast);
                        }
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-did);
                        if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                            if (ChatObject.isPublic(chat)) {
                                text = LocaleController.getString(R.string.FilterInviteChannelToast);
                            } else {
                                text = LocaleController.getString(R.string.FilterInvitePrivateChannelToast);
                            }
                        } else {
                            if (ChatObject.isPublic(chat)) {
                                text = LocaleController.getString(R.string.FilterInviteGroupToast);
                            } else {
                                text = LocaleController.getString(R.string.FilterInvitePrivateGroupToast);
                            }
                        }
                        array.add(chat);
                    }
                    if (lastClickedDialogId != did || System.currentTimeMillis() - lastClicked > Bulletin.DURATION_SHORT) {
                        lastClickedDialogId = did;
                        lastClicked = System.currentTimeMillis();
                        BulletinFactory.of(this).createChatsBulletin(array, text, null).show();
                    }
                    return;
                }
                checkPeersChanged();

                updateHeaderCell(true);
                updateHintCell(true);
            }
        });

        getMessagesController().updateFilterDialogs(filter);
        peers.clear();
        if (invite != null) {
            for (int i = 0; i < invite.peers.size(); ++i) {
                TLRPC.Peer peer = invite.peers.get(i);
                long did = DialogObject.getPeerDialogId(peer);
                peers.add(did);
                selectedPeers.add(did);
                allowedPeers.add(did);
            }
        }
        for (int i = 0; i < filter.dialogs.size(); ++i) {
            TLRPC.Dialog dialog = filter.dialogs.get(i);
            if (dialog != null && !DialogObject.isEncryptedDialog(dialog.id) && !peers.contains(dialog.id)) {
                boolean canInvite = dialog.id < 0;
                if (dialog.id < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
                    canInvite = FilterCreateActivity.canAddToFolder(chat);
                }
                if (canInvite) {
                    peers.add(dialog.id);
                    allowedPeers.add(dialog.id);
                }
            }
        }
        for (int i = 0; i < filter.dialogs.size(); ++i) {
            TLRPC.Dialog dialog = filter.dialogs.get(i);
            if (dialog != null && !DialogObject.isEncryptedDialog(dialog.id) && !peers.contains(dialog.id) && !allowedPeers.contains(dialog.id)) {
                peers.add(dialog.id);
            }
        }

        updateRows();

        return fragmentView;
    }

    private void checkPeersChanged() {
        if (invite != null && invite.url != null && peersChanged) {
            boolean changed = selectedPeers.size() != invite.peers.size();
            if (!changed) {
                for (int i = 0; i < invite.peers.size(); ++i) {
                    TLRPC.Peer peer = invite.peers.get(i);
                    if (!selectedPeers.contains(DialogObject.getPeerDialogId(peer))) {
                        changed = true;
                        break;
                    }
                }
            }
            if (!changed) {
                peersChanged = false;
                checkDoneButton();
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    @Override
    public boolean canBeginSlide() {
        return checkDiscard();
    }

    private boolean saving = false;
    private void save() {
        if (invite == null || saving || !peersChanged) {
            return;
        }

        updateDoneProgress(true);
        saving = true;

        invite.peers.clear();
        for (int i = 0; i < selectedPeers.size(); ++i) {
            invite.peers.add(getMessagesController().getPeer(selectedPeers.get(i)));
        }

        TL_chatlists.TL_chatlists_editExportedInvite req = new TL_chatlists.TL_chatlists_editExportedInvite();
        req.chatlist = new TL_chatlists.TL_inputChatlistDialogFilter();
        req.chatlist.filter_id = filter.id;
        req.slug = getSlug();
        req.revoked = invite.revoked;
        req.flags |= 4;
        for (int i = 0; i < selectedPeers.size(); ++i) {
            req.peers.add(getMessagesController().getInputPeer(selectedPeers.get(i)));
        }
        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            updateDoneProgress(false);
            saving = false;
            if (err != null && "INVITES_TOO_MUCH".equals(err.text)) {
                showDialog(new LimitReachedBottomSheet(this, getContext(), LimitReachedBottomSheet.TYPE_FOLDER_INVITES, currentAccount, null));
            } else if (err != null && "INVITE_PEERS_TOO_MUCH".equals(err.text)) {
                showDialog(new LimitReachedBottomSheet(this, getContext(), LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, currentAccount, null));
            } else if (err != null && "CHATLISTS_TOO_MUCH".equals(err.text)) {
                showDialog(new LimitReachedBottomSheet(this, getContext(), LimitReachedBottomSheet.TYPE_SHARED_FOLDERS, currentAccount, null));
            } else {
                finishFragment();
            }
        }));

        if (onEdit != null) {
            onEdit.run(invite);
        }
    }

    private int savingTitleReqId;
    private void saveTitle() {
        if (savingTitleReqId != 0) {
            getConnectionsManager().cancelRequest(savingTitleReqId, true);
            savingTitleReqId = 0;
        }
        TL_chatlists.TL_chatlists_editExportedInvite req = new TL_chatlists.TL_chatlists_editExportedInvite();
        req.chatlist = new TL_chatlists.TL_inputChatlistDialogFilter();
        req.chatlist.filter_id = filter.id;
        req.slug = getSlug();
        req.revoked = invite.revoked;
        req.flags |= 2;
        req.title = invite.title;
        savingTitleReqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            savingTitleReqId = 0;
            if (err == null) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.FilterInviteNameEdited)).show();
            }
        }));

        if (onEdit != null) {
            onEdit.run(invite);
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        if (savingTitleReqId != 0) {
            getConnectionsManager().cancelRequest(savingTitleReqId, true);
            savingTitleReqId = 0;
        }
    }

    private boolean titleChanged;
    private boolean peersChanged;
    private int rowsCount = 0;

    private int hintRow = -1;
    private int linkRow = -1;
    private int linkHeaderRow = -1;
    private int linkSectionRow = -1;
    private int chatsHeaderRow = -1;
    private int chatsStartRow = -1;
    private int chatsEndRow = -1;
    private int chatsSectionRow = -1;

    private FolderBottomSheet.HeaderCell headerCountCell;
    private HintInnerCell hintCountCell;

    private void updateHintCell(boolean animated) {
        if (hintCountCell == null) {
            return;
        }

        if (invite == null) {
            hintCountCell.setText(LocaleController.getString(R.string.FilterInviteHeaderNo), animated);
        } else {
            hintCountCell.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("FilterInviteHeader", selectedPeers.size(), filter.name)), animated);
        }
    }

    private void updateHeaderCell(boolean animated) {
        if (headerCountCell == null) {
            return;
        }

        headerCountCell.setText(selectedPeers.size() <= 0 ? LocaleController.getString("FilterInviteHeaderChatsEmpty") : LocaleController.formatPluralString("FilterInviteHeaderChats", selectedPeers.size()), animated);
        if (allowedPeers.size() > 1) {
            final boolean deselect = selectedPeers.size() >= Math.min(getMaxChats(), allowedPeers.size());
            headerCountCell.setAction(!deselect ? LocaleController.getString(R.string.SelectAll) : LocaleController.getString(R.string.DeselectAll), () -> deselectAll(headerCountCell, deselect));
        } else {
            headerCountCell.setAction("", null);
        }

        if (animated) {
            AndroidUtilities.makeAccessibilityAnnouncement(
                headerCountCell.textView.getText() + ", " + headerCountCell.actionTextView.getText()
            );
        }
    }

    public void updateRows() {
        rowsCount = 0;
        hintRow = rowsCount++;
        if (invite != null) {
            linkHeaderRow = rowsCount++;
            linkRow = rowsCount++;
            linkSectionRow = rowsCount++;
        } else {
            linkHeaderRow = -1;
            linkRow = -1;
            linkSectionRow = -1;
        }
        if (invite == null && peers.isEmpty()) {
            chatsHeaderRow = -1;
            chatsStartRow = -1;
            chatsEndRow = -1;
            chatsSectionRow = -1;
        } else {
            chatsHeaderRow = rowsCount++;
            chatsStartRow = rowsCount++;
            chatsEndRow = (rowsCount += (peers.size() - 1));
            chatsSectionRow = rowsCount++;
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String getSlug() {
        if (invite == null || invite.url == null) {
            return null;
        }
        return invite.url.substring(invite.url.lastIndexOf('/') + 1);
    }

    class ListAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            if (viewType == 0) {
                view = new HintInnerCell(getContext(), R.raw.folder_share);
            } else if (viewType == 2) {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(getContext());
                view = cell;
            } else if (viewType == 3) {
                InviteLinkCell actionView = new InviteLinkCell(getContext(), FilterChatlistActivity.this) {
                    @Override
                    protected void revoke(boolean revoke) {
                        if (invite == null || invite.url == null) {
                            return;
                        }

                        TL_chatlists.TL_chatlists_editExportedInvite req = new TL_chatlists.TL_chatlists_editExportedInvite();
                        req.chatlist = new TL_chatlists.TL_inputChatlistDialogFilter();
                        req.chatlist.filter_id = filter.id;
                        req.revoked = revoke;
                        req.slug = getSlug();
                        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(180);
                        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            invite.revoked = revoke;
                            progressDialog.dismiss();
                            if (revoke) {
                                finishFragment();
                            }
                        }));
                    }

                    @Override
                    protected void deleteLink() {
                        TL_chatlists.TL_chatlists_deleteExportedInvite req = new TL_chatlists.TL_chatlists_deleteExportedInvite();
                        req.chatlist = new TL_chatlists.TL_inputChatlistDialogFilter();
                        req.chatlist.filter_id = filter.id;
                        req.slug = getSlug();
                        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(180);
                        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();
                            if (onDelete != null) {
                                onDelete.run(invite);
                            }
                            finishFragment();
                        }));
                    }

                    @Override
                    public void editname() {
                        if (invite == null || invite.url == null) {
                            return;
                        }

                        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
                        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getContext(), true));

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setDialogButtonColorKey(Theme.key_dialogButton);
                        builder.setTitle(LocaleController.getString(R.string.FilterInviteEditName));
//                        builder.setCheckFocusable(false);
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> AndroidUtilities.hideKeyboard(editText));

                        LinearLayout linearLayout = new LinearLayout(getContext());
                        linearLayout.setOrientation(LinearLayout.VERTICAL);
                        builder.setView(linearLayout);

                        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        editText.setMaxLines(1);
                        editText.setLines(1);
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                        editText.setGravity(Gravity.LEFT | Gravity.TOP);
                        editText.setSingleLine(true);
                        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                        editText.setHint(filter.name);
                        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
                        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
                        editText.setCursorSize(AndroidUtilities.dp(20));
                        editText.setCursorWidth(1.5f);
                        editText.setPadding(0, AndroidUtilities.dp(4), 0, 0);
                        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));
                        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
                            AndroidUtilities.hideKeyboard(textView);
                            builder.create().getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                            return false;
                        });
                        editText.addTextChangedListener(new TextWatcher() {

                            boolean ignoreTextChange;

                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {}
                            @Override
                            public void afterTextChanged(Editable s) {
                                if (ignoreTextChange) {
                                    return;
                                }
                                if (s.length() > MAX_NAME_LENGTH) {
                                    ignoreTextChange = true;
                                    s.delete(MAX_NAME_LENGTH, s.length());
                                    AndroidUtilities.shakeView(editText);
                                    editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                    ignoreTextChange = false;
                                }
                            }
                        });
                        if (!TextUtils.isEmpty(invite.title)) {
                            editText.setText(invite.title);
                            editText.setSelection(editText.length());
                        }
                        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                            AndroidUtilities.hideKeyboard(editText);
//                            call.setTitle(editText.getText().toString());
                            builder.getDismissRunnable().run();

                            invite.title = editText.getText().toString();
                            titleChanged = true;
                            updateActionBarTitle(true);
                            saveTitle();
                        });

                        final AlertDialog alertDialog = builder.create();
                        alertDialog.setOnShowListener(dialog -> AndroidUtilities.runOnUIThread(() -> {
                            editText.requestFocus();
                            AndroidUtilities.showKeyboard(editText);
                        }));
                        alertDialog.setOnDismissListener(dialog -> AndroidUtilities.hideKeyboard(editText));
                        alertDialog.show();
                        alertDialog.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        editText.requestFocus();
                    }

                    @Override
                    protected boolean isRevoked() {
                        return invite != null && invite.revoked;
                    }
                };
                actionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                actionView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                view = actionView;
            } else if (viewType == 4) {
                GroupCreateUserCell userCell = new GroupCreateUserCell(getContext(), 1, 0, false);
                userCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                view = userCell;
            } else if (viewType == 5) {
                FolderBottomSheet.HeaderCell headerCell = new FolderBottomSheet.HeaderCell(getContext());
                headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                view = headerCell;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int viewType = holder.getItemViewType();
            if (viewType == 0) {
                hintCountCell = (HintInnerCell) holder.itemView;
                updateHintCell(false);
            } else if (viewType == 2) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), position == chatsSectionRow ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                if (position == chatsSectionRow) {
                    cell.setFixedSize(0);
                    if (invite == null || allowedPeers.isEmpty()) {
                        cell.setText(LocaleController.getString(R.string.FilterInviteHintNo));
                    } else {
                        cell.setText(LocaleController.getString(R.string.FilterInviteHint));
                    }
                } else {
                    cell.setFixedSize(12);
                }
            } else if (viewType == 3) {
                InviteLinkCell actionView = (InviteLinkCell) holder.itemView;
                actionView.setLink(invite == null ? null : invite.url, false);
            } else if (viewType == 4) {
                GroupCreateUserCell userCell = (GroupCreateUserCell) holder.itemView;
                long did = peers.get(position - chatsStartRow);
                TLObject object;
                CharSequence name = null, status = null;
                if (did >= 0) {
                    TLRPC.User user = getMessagesController().getUser(did);
                    if (user != null) {
                        name = UserObject.getUserName(user);
                    }
                    object = user;
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-did);
                    if (chat != null) {
                        name = chat.title;
                        if (chat.participants_count != 0) {
                            if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                                status = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                            } else {
                                status = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                            }
                        } else {
                            if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                                status = LocaleController.getString("ChannelPublic");
                            } else {
                                status = LocaleController.getString("MegaPublic");
                            }
                        }
                    }
                    object = chat;
                }
                if (allowedPeers.contains(did)) {
                    userCell.setForbiddenCheck(false);
                    userCell.setChecked(selectedPeers.contains(did), false);
                } else {
                    userCell.setForbiddenCheck(true);
                    userCell.setChecked(false, false);
                    if (object instanceof TLRPC.User) {
                        if (((TLRPC.User) object).bot) {
                            status = LocaleController.getString(R.string.FilterInviteBot);
                        } else {
                            status = LocaleController.getString(R.string.FilterInviteUser);
                        }
                    } else if (object instanceof TLRPC.Chat) {
                        if (ChatObject.isChannelAndNotMegaGroup((TLRPC.Chat) object)) {
                            status = LocaleController.getString(R.string.FilterInviteChannel);
                        } else {
                            status = LocaleController.getString(R.string.FilterInviteGroup);
                        }
                    }
                }
                userCell.setTag(did);
                userCell.setObject(object, name, status);
            } else if (viewType == 5) {
                FolderBottomSheet.HeaderCell headerCell = (FolderBottomSheet.HeaderCell) holder.itemView;
                if (headerCell == headerCountCell) {
                    headerCountCell = null;
                }
                if (position == linkHeaderRow) {
                    headerCell.setText(LocaleController.getString(R.string.InviteLink), false);
                    headerCell.setAction("", null);
                } else {
                    headerCountCell = headerCell;
                    if (invite == null || allowedPeers.isEmpty()) {
                        headerCell.setText(LocaleController.getString(R.string.FilterInviteHeaderChatsNo), false);
                        headerCell.setAction("", null);
                    } else {
                        updateHeaderCell(false);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return rowsCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            } else if (position == chatsSectionRow || position == linkSectionRow) {
                return 2;
            } else if (position == linkRow) {
                return 3;
            } else if (position >= chatsStartRow && position < chatsEndRow) {
                return 4;
            } else if (position == chatsHeaderRow || position == linkHeaderRow) {
                return 5;
            }
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 4;
        }
    }

    private int getMaxChats() {
        return getUserConfig().isPremium() ? getMessagesController().dialogFiltersChatsLimitPremium : getMessagesController().dialogFiltersChatsLimitDefault;
    }

    private void deselectAll(FolderBottomSheet.HeaderCell headerCell, boolean deselect) {
        selectedPeers.clear();
        if (!deselect) {
            selectedPeers.addAll(allowedPeers.subList(0, Math.min(getMaxChats(), allowedPeers.size())));
        }
        final boolean newDeselect = selectedPeers.size() >= Math.min(getMaxChats(), allowedPeers.size());
        headerCell.setAction(!newDeselect ? LocaleController.getString(R.string.SelectAll) : LocaleController.getString(R.string.DeselectAll), () -> deselectAll(headerCell, !deselect));
        peersChanged = true;
        checkPeersChanged();
        checkDoneButton();
        updateHeaderCell(true);
        updateHintCell(true);
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof GroupCreateUserCell) {
                Object tag = child.getTag();
                if (tag instanceof Long) {
                    ((GroupCreateUserCell) child).setChecked(selectedPeers.contains((long) tag),true);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @SuppressWarnings("FieldCanBeLocal")
    public static class HintInnerCell extends FrameLayout {

        private RLottieImageView imageView;
        private TextView subtitleTextView;

        public HintInnerCell(Context context, int resId) {
            super(context);

            imageView = new RLottieImageView(context);
            imageView.setAnimation(resId, 90, 90);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.playAnimation();
            imageView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(imageView, LayoutHelper.createFrame(90, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

            subtitleTextView = new TextView(context);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleTextView.setGravity(Gravity.CENTER);
            subtitleTextView.setLines(2);
            addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 40, 121, 40, 24));
        }

        public void setText(CharSequence text, boolean animated) {
            subtitleTextView.setText(text);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
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

    private float doneButtonAlpha = 1;
    private void checkDoneButton() {
        boolean shown = peersChanged;
        boolean enabled = !selectedPeers.isEmpty();
        float alpha = shown ? (enabled ? 1 : .5f) : 0;

        if (Math.abs(doneButtonAlpha - alpha) > .1f) {
            doneButton.clearAnimation();
            doneButton.animate().alpha(doneButtonAlpha = alpha).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }

    private void shakeHeader() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            int position = listView.getChildAdapterPosition(child);
            if (position == chatsHeaderRow && child instanceof FolderBottomSheet.HeaderCell) {
                AndroidUtilities.shakeViewSpring(child, shiftDp = -shiftDp);
                break;
            }
        }
    }

    private boolean checkDiscard() {
        if (selectedPeers.isEmpty()) {
            return true;
        }
        if (peersChanged) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
            builder.setMessage(LocaleController.getString(R.string.UnsavedChangesMessage));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> save());
            builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    public static class InviteLinkCell extends FrameLayout {

        FrameLayout linkBox;
        SimpleTextView spoilerTextView;
        SimpleTextView textView;
        ImageView optionsIcon;

        ButtonsBox buttonsBox;
        TextView copyButton, shareButton, generateButton;

        BaseFragment parentFragment;

        class ButtonsBox extends FrameLayout {

            private Paint paint = new Paint();
            private float[] radii = new float[8];
            private Path path = new Path();

            public ButtonsBox(Context context) {
                super(context);
                setWillNotDraw(false);
                paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            }

            private float t;
            public void setT(float t) {
                this.t = t;
                invalidate();
            }

            private void setRadii(float left, float right) {
                radii[0] = radii[1] = radii[6] = radii[7] = left;
                radii[2] = radii[3] = radii[4] = radii[5] = right;
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                final float cx = getMeasuredWidth() / 2f;

                path.rewind();
                AndroidUtilities.rectTmp.set(0, 0, cx - lerp(0, dp(4), t), getMeasuredHeight());
                setRadii(dp(8), lerp(0, dp(8), t));
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                canvas.drawPath(path, paint);

                path.rewind();
                AndroidUtilities.rectTmp.set(cx + lerp(0, dp(4), t), 0, getMeasuredWidth(), getMeasuredHeight());
                setRadii(lerp(0, dp(8), t), dp(8));
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                canvas.drawPath(path, paint);
            }
        }

        public InviteLinkCell(Context context, BaseFragment fragment) {
            super(context);

            parentFragment = fragment;

            linkBox = new FrameLayout(context);
            linkBox.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_graySection), Theme.blendOver(Theme.getColor(Theme.key_graySection), Theme.getColor(Theme.key_listSelector))));
            linkBox.setOnClickListener(e -> copy());
            addView(linkBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 9, 22, 0));

            spoilerTextView = new SimpleTextView(context);
            spoilerTextView.setTextSize(16);
            spoilerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            SpannableString spoileredText = new SpannableString("t.me/folder/N3k/dImA/bIo");
            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags |= FLAG_STYLE_SPOILER;
            spoileredText.setSpan(new TextStyleSpan(run), 0, spoileredText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spoilerTextView.setText(spoileredText);
            spoilerTextView.setAlpha(1);
            linkBox.addView(spoilerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 40, 0));

            textView = new SimpleTextView(context);
            textView.setTextSize(16);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setText(spoileredText);
            textView.setAlpha(0);
            linkBox.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 40, 0));

            optionsIcon = new ImageView(context);
            optionsIcon.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_ab_other));
            optionsIcon.setScaleType(ImageView.ScaleType.CENTER);
            optionsIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3), PorterDuff.Mode.SRC_IN));
            optionsIcon.setAlpha(0f);
            optionsIcon.setVisibility(GONE);
            optionsIcon.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
            optionsIcon.setOnClickListener(e -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && linkBox.getBackground() instanceof RippleDrawable) {
                    linkBox.getBackground().setState(new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled });
                    postDelayed(() -> linkBox.getBackground().setState(new int[] {}), 180);
                }
                options();
            });
            linkBox.addView(optionsIcon, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 4, 4, 4, 4));

            buttonsBox = new ButtonsBox(context);
            addView(buttonsBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.FILL_HORIZONTAL | Gravity.TOP, 22, 69, 22, 0));

            copyButton = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(widthMeasureSpec) - dp(8)) / 2, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(dp(42), MeasureSpec.EXACTLY)
                    );
                }
            };
            copyButton.setGravity(Gravity.CENTER);
            copyButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            copyButton.setBackground(Theme.createRadSelectorDrawable(0x30ffffff, 8, 8));
            copyButton.setTypeface(AndroidUtilities.bold());
            copyButton.setTextSize(14);
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_copy_filled)), 0, 1, 0);
            spannableStringBuilder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(8)), 1, 2, 0);
            spannableStringBuilder.append(LocaleController.getString(R.string.LinkActionCopy));
            spannableStringBuilder.append(".").setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(5)), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            copyButton.setText(spannableStringBuilder);
            copyButton.setOnClickListener(e -> copy());
            copyButton.setAlpha(0);
            copyButton.setVisibility(GONE);
            buttonsBox.addView(copyButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT));

            shareButton = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                            MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(widthMeasureSpec) - dp(8)) / 2, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(dp(42), MeasureSpec.EXACTLY)
                    );
                }
            };
            shareButton.setGravity(Gravity.CENTER);
            shareButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            shareButton.setBackground(Theme.createRadSelectorDrawable(0x30ffffff, 8, 8));
            shareButton.setTypeface(AndroidUtilities.bold());
            shareButton.setTextSize(14);
            spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_share_filled)), 0, 1, 0);
            spannableStringBuilder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(8)), 1, 2, 0);
            spannableStringBuilder.append(LocaleController.getString(R.string.LinkActionShare));
            spannableStringBuilder.append(".").setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(5)), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            shareButton.setText(spannableStringBuilder);
            shareButton.setOnClickListener(e -> share());
            shareButton.setAlpha(0);
            shareButton.setVisibility(GONE);
            buttonsBox.addView(shareButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));

            generateButton = new TextView(context);
            generateButton.setGravity(Gravity.CENTER);
            generateButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            generateButton.setBackground(Theme.createRadSelectorDrawable(0x30ffffff, 8, 8));
            generateButton.setTypeface(AndroidUtilities.bold());
            generateButton.setTextSize(14);
            generateButton.setText("Generate Invite Link");
            generateButton.setOnClickListener(e -> generate());
            generateButton.setAlpha(1);
            generateButton.setVisibility(VISIBLE);
            buttonsBox.addView(generateButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        protected void generate() {

        }

        protected boolean isRevoked() {
            return false;
        }

        private String lastUrl;

        private float changeAlpha;
        private ValueAnimator changeAnimator;
        public void setLink(String _url, boolean animated) {
            lastUrl = _url;
            if (_url != null) {
                if (_url.startsWith("http://"))
                    _url = _url.substring(7);
                if (_url.startsWith("https://"))
                    _url = _url.substring(8);
            }
            final String url = _url;
            textView.setText(url);
            if (changeAlpha != (url != null ? 1 : 0)) {
                if (changeAnimator != null) {
                    changeAnimator.cancel();
                    changeAnimator = null;
                }

                if (animated) {
                    generateButton.setVisibility(VISIBLE);
                    optionsIcon.setVisibility(VISIBLE);
                    copyButton.setVisibility(VISIBLE);
                    shareButton.setVisibility(VISIBLE);

                    changeAnimator = ValueAnimator.ofFloat(changeAlpha, url != null ? 1 : 0);
                    changeAnimator.addUpdateListener(anm -> {
                        changeAlpha = (float) anm.getAnimatedValue();
                        updateChangeAlpha();
                    });
                    changeAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (url == null) {
                                generateButton.setVisibility(VISIBLE);
                                optionsIcon.setVisibility(GONE);
                                copyButton.setVisibility(GONE);
                                shareButton.setVisibility(GONE);
                            } else {
                                generateButton.setVisibility(GONE);
                                optionsIcon.setVisibility(VISIBLE);
                                copyButton.setVisibility(VISIBLE);
                                shareButton.setVisibility(VISIBLE);
                            }
                        }
                    });
                    changeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    changeAnimator.setDuration(320);
                    changeAnimator.start();
                } else {
                    changeAlpha = url != null ? 1 : 0;
                    updateChangeAlpha();

                    if (url == null) {
                        generateButton.setVisibility(VISIBLE);
                        optionsIcon.setVisibility(GONE);
                        copyButton.setVisibility(GONE);
                        shareButton.setVisibility(GONE);
                    } else {
                        generateButton.setVisibility(GONE);
                        optionsIcon.setVisibility(VISIBLE);
                        copyButton.setVisibility(VISIBLE);
                        shareButton.setVisibility(VISIBLE);
                    }
                }
            }
        }

        private void updateChangeAlpha() {
            buttonsBox.setT(changeAlpha);

            copyButton.setAlpha(changeAlpha);
            shareButton.setAlpha(changeAlpha);
            optionsIcon.setAlpha(changeAlpha);
            generateButton.setAlpha(1f - changeAlpha);

            textView.setAlpha(changeAlpha);
            spoilerTextView.setAlpha(1f - changeAlpha);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(127), MeasureSpec.EXACTLY)
            );
        }

        private ActionBarPopupWindow actionBarPopupWindow;
        private float[] point = new float[2];

        public void options() {
            if (actionBarPopupWindow != null || lastUrl == null) {
                return;
            }
            ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());

            ActionBarMenuSubItem subItem;

            subItem = new ActionBarMenuSubItem(getContext(), true, false);
            subItem.setTextAndIcon(LocaleController.getString(R.string.EditName), R.drawable.msg_edit);
            layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            subItem.setOnClickListener(view12 -> {
                if (actionBarPopupWindow != null) {
                    actionBarPopupWindow.dismiss();
                }
                editname();
            });

            subItem = new ActionBarMenuSubItem(getContext(), false, false);
            subItem.setTextAndIcon(LocaleController.getString(R.string.GetQRCode), R.drawable.msg_qrcode);
            layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            subItem.setOnClickListener(view12 -> {
                if (actionBarPopupWindow != null) {
                    actionBarPopupWindow.dismiss();
                }
                qrcode();
            });

            subItem = new ActionBarMenuSubItem(getContext(), false, true);
//            if (!isRevoked()) {
//                subItem.setTextAndIcon(LocaleController.getString(R.string.RevokeLink), R.drawable.msg_delete);
//                subItem.setColors(Theme.getColor(Theme.key_text_RedRegular), Theme.getColor(Theme.key_text_RedRegular));
//                subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
//                subItem.setOnClickListener(view1 -> {
//                    if (actionBarPopupWindow != null) {
//                        actionBarPopupWindow.dismiss();
//                    }
//                    revoke(true);
//                });
//            } else {
//                subItem = new ActionBarMenuSubItem(getContext(), false, true);
//                subItem.setTextAndIcon("Unrevoke", R.drawable.msg_reset);
//                subItem.setOnClickListener(view1 -> {
//                    if (actionBarPopupWindow != null) {
//                        actionBarPopupWindow.dismiss();
//                    }
//                    revoke(false);
//                });
//            }
            subItem.setTextAndIcon(LocaleController.getString(R.string.DeleteLink), R.drawable.msg_delete);
            subItem.setColors(Theme.getColor(Theme.key_text_RedRegular), Theme.getColor(Theme.key_text_RedRegular));
            subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
            subItem.setOnClickListener(view1 -> {
                if (actionBarPopupWindow != null) {
                    actionBarPopupWindow.dismiss();
                }
                deleteLink();
            });
            layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            FrameLayout container = parentFragment.getParentLayout().getOverlayContainerView();

            if (container != null) {
                float x = 0;
                float y;
                getPointOnScreen(linkBox, container, point);
                y = point[1];

                final FrameLayout finalContainer = container;
                View dimView = new View(getContext()) {

                    @Override
                    protected void onDraw(Canvas canvas) {
                        canvas.drawColor(0x33000000);
                        getPointOnScreen(linkBox, finalContainer, point);
                        canvas.save();
                        float clipTop = ((View) linkBox.getParent()).getY() + linkBox.getY();
                        if (clipTop < 1) {
                            canvas.clipRect(0, point[1] - clipTop + 1, getMeasuredWidth(), getMeasuredHeight());
                        }
                        canvas.translate(point[0], point[1]);

                        linkBox.draw(canvas);
                        canvas.restore();
                    }
                };

                ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        dimView.invalidate();
                        return true;
                    }
                };
                finalContainer.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
                container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                dimView.setAlpha(0);
                dimView.animate().alpha(1f).setDuration(150);
                layout.measure(MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), MeasureSpec.UNSPECIFIED));

                actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                actionBarPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        actionBarPopupWindow = null;
                        dimView.animate().cancel();
                        dimView.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (dimView.getParent() != null) {
                                    finalContainer.removeView(dimView);
                                }
                                finalContainer.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                            }
                        });
                    }
                });
                actionBarPopupWindow.setOutsideTouchable(true);
                actionBarPopupWindow.setFocusable(true);
                actionBarPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                actionBarPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

                layout.setDispatchKeyEventListener(keyEvent -> {
                    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow.isShowing()) {
                        actionBarPopupWindow.dismiss(true);
                    }
                });

                if (AndroidUtilities.isTablet()) {
                    y += container.getPaddingTop();
                    x -= container.getPaddingLeft();
                }
                actionBarPopupWindow.showAtLocation(container, 0, (int) (container.getMeasuredWidth() - layout.getMeasuredWidth() - AndroidUtilities.dp(16) + container.getX() + x), (int) (y + linkBox.getMeasuredHeight() + container.getY()));
            }
        }


        public void copy() {
            if (lastUrl == null) {
                return;
            }

            AndroidUtilities.addToClipboard(lastUrl);
            BulletinFactory.of(parentFragment).createCopyBulletin(LocaleController.getString(R.string.LinkCopied)).show();
        }

        protected void share() {
            if (lastUrl == null) {
                return;
            }

            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, lastUrl);
                parentFragment.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.InviteToGroupByLink)), 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        protected void revoke(boolean revoke) {}

        protected void deleteLink() {}

        public void editname() {}

        public void qrcode() {
            if (lastUrl == null) {
                return;
            }

            QRCodeBottomSheet qrCodeBottomSheet = new QRCodeBottomSheet(getContext(), LocaleController.getString(R.string.InviteByQRCode), lastUrl, LocaleController.getString(R.string.QRCodeLinkHelpFolder), false);
            qrCodeBottomSheet.setCenterAnimation(R.raw.qr_code_logo);
            qrCodeBottomSheet.show();
        }

        private void getPointOnScreen(FrameLayout frameLayout, FrameLayout finalContainer, float[] point) {
            float x = 0;
            float y = 0;
            View v = frameLayout;
            while (v != finalContainer) {
                y += v.getY();
                x += v.getX();
                if (v instanceof ScrollView) {
                    y -= v.getScrollY();
                }
                if (!(v.getParent() instanceof View)) {
                    break;
                }
                v = (View) v.getParent();
                if (!(v instanceof ViewGroup)) {
                    return;
                }
            }
            x -= finalContainer.getPaddingLeft();
            y -= finalContainer.getPaddingTop();
            point[0] = x;
            point[1] = y;
        }
    }

}

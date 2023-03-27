package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;

public class FilterCreateActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private ActionBarMenuItem doneItem;

    private int imageRow;
    private int namePreSectionRow;
    private int nameRow;
    private int nameSectionRow;
    private int includeHeaderRow;
    private int includeAddRow;
    private int includeContactsRow;
    private int includeNonContactsRow;
    private int includeGroupsRow;
    private int includeChannelsRow;
    private int includeBotsRow;
    private int includeStartRow;
    private int includeEndRow;
    private int includeShowMoreRow;
    private int includeSectionRow;
    private int excludeHeaderRow;
    private int excludeAddRow;
    private int excludeMutedRow;
    private int excludeReadRow;
    private int excludeArchivedRow;
    private int excludeStartRow;
    private int excludeEndRow;
    private int excludeShowMoreRow;
    private int excludeSectionRow;
    private int removeRow;
    private int removeSectionRow;
    private int rowCount = 0;

    private boolean includeExpanded;
    private boolean excludeExpanded;
    private boolean hasUserChanged;

    private boolean nameChangedManually;

    private MessagesController.DialogFilter filter;
    private boolean creatingNew;
    private String newFilterName;
    private int newFilterFlags;
    private ArrayList<Long> newAlwaysShow;
    private ArrayList<Long> newNeverShow;
    private LongSparseIntArray newPinned;

    private static final int MAX_NAME_LENGTH = 12;

    private static final int done_button = 1;

    @SuppressWarnings("FieldCanBeLocal")
    public static class HintInnerCell extends FrameLayout {

        private RLottieImageView imageView;

        public HintInnerCell(Context context) {
            super(context);

            imageView = new RLottieImageView(context);
            imageView.setAnimation(R.raw.filter_new, 100, 100);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.playAnimation();
            addView(imageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 0, 0, 0, 0));
            imageView.setOnClickListener(v -> {
                if (!imageView.isPlaying()) {
                    imageView.setProgress(0.0f);
                    imageView.playAnimation();
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(156), MeasureSpec.EXACTLY));
        }
    }

    public FilterCreateActivity() {
        this(null, null);
    }

    public FilterCreateActivity(MessagesController.DialogFilter dialogFilter) {
        this(dialogFilter, null);
    }

    public FilterCreateActivity(MessagesController.DialogFilter dialogFilter, ArrayList<Long> alwaysShow) {
        super();
        filter = dialogFilter;
        if (filter == null) {
            filter = new MessagesController.DialogFilter();
            filter.id = 2;
            while (getMessagesController().dialogFiltersById.get(filter.id) != null) {
                filter.id++;
            }
            filter.name = "";
            creatingNew = true;
        }
        newFilterName = filter.name;
        newFilterFlags = filter.flags;
        newAlwaysShow = new ArrayList<>(filter.alwaysShow);
        if (alwaysShow != null) {
            newAlwaysShow.addAll(alwaysShow);
        }
        newNeverShow = new ArrayList<>(filter.neverShow);
        newPinned = filter.pinnedDialogs.clone();
    }

    @Override
    public boolean onFragmentCreate() {
        updateRows();
        return super.onFragmentCreate();
    }

    private void updateRows() {
        rowCount = 0;

        if (creatingNew) {
            imageRow = rowCount++;
            namePreSectionRow = -1;
        } else {
            imageRow = -1;
            namePreSectionRow = rowCount++;
        }
        nameRow = rowCount++;
        nameSectionRow = rowCount++;
        includeHeaderRow = rowCount++;
        includeAddRow = rowCount++;

        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
            includeContactsRow = rowCount++;
        } else {
            includeContactsRow = -1;
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
            includeNonContactsRow = rowCount++;
        } else {
            includeNonContactsRow = -1;
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
            includeGroupsRow = rowCount++;
        } else {
            includeGroupsRow = -1;
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
            includeChannelsRow = rowCount++;
        } else {
            includeChannelsRow = -1;
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
            includeBotsRow = rowCount++;
        } else {
            includeBotsRow = -1;
        }

        if (!newAlwaysShow.isEmpty()) {
            includeStartRow = rowCount;
            int count = includeExpanded || newAlwaysShow.size() < 8 ? newAlwaysShow.size() : Math.min(5, newAlwaysShow.size());
            rowCount += count;
            includeEndRow = rowCount;
            if (count != newAlwaysShow.size()) {
                includeShowMoreRow = rowCount++;
            } else {
                includeShowMoreRow = -1;
            }
        } else {
            includeStartRow = -1;
            includeEndRow = -1;
            includeShowMoreRow = -1;
        }
        includeSectionRow = rowCount++;
        excludeHeaderRow = rowCount++;
        excludeAddRow = rowCount++;
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
            excludeMutedRow = rowCount++;
        } else {
            excludeMutedRow = -1;
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
            excludeReadRow = rowCount++;
        } else {
            excludeReadRow = -1;
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0) {
            excludeArchivedRow = rowCount++;
        } else {
            excludeArchivedRow = -1;
        }
        if (!newNeverShow.isEmpty()) {
            excludeStartRow = rowCount;
            int count = excludeExpanded || newNeverShow.size() < 8 ? newNeverShow.size() : Math.min(5, newNeverShow.size());
            rowCount += count;
            excludeEndRow = rowCount;
            if (count != newNeverShow.size()) {
                excludeShowMoreRow = rowCount++;
            } else {
                excludeShowMoreRow = -1;
            }
        } else {
            excludeStartRow = -1;
            excludeEndRow = -1;
            excludeShowMoreRow = -1;
        }
        excludeSectionRow = rowCount++;

        if (!creatingNew) {
            removeRow = rowCount++;
            removeSectionRow = rowCount++;
        } else {
            removeRow = -1;
            removeSectionRow = -1;
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        ActionBarMenu menu = actionBar.createMenu();
        if (creatingNew) {
            actionBar.setTitle(LocaleController.getString("FilterNew", R.string.FilterNew));
        } else {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(AndroidUtilities.dp(20));
            actionBar.setTitle(Emoji.replaceEmoji(filter.name, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false));
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
        doneItem = menu.addItem(done_button, LocaleController.getString("Save", R.string.Save).toUpperCase());

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
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == includeShowMoreRow) {
                includeExpanded = true;
                updateRows();
            } else if (position == excludeShowMoreRow) {
                excludeExpanded = true;
                updateRows();
            } else if (position == includeAddRow || position == excludeAddRow) {
                ArrayList<Long> arrayList = position == excludeAddRow ? newNeverShow : newAlwaysShow;
                UsersSelectActivity fragment = new UsersSelectActivity(position == includeAddRow, arrayList, newFilterFlags);
                fragment.setDelegate((ids, flags) -> {
                    newFilterFlags = flags;
                    if (position == excludeAddRow) {
                        newNeverShow = ids;
                        for (int a = 0; a < newNeverShow.size(); a++) {
                            Long id = newNeverShow.get(a);
                            newAlwaysShow.remove(id);
                            newPinned.delete(id);
                        }
                    } else {
                        newAlwaysShow = ids;
                        for (int a = 0; a < newAlwaysShow.size(); a++) {
                            newNeverShow.remove(newAlwaysShow.get(a));
                        }
                        ArrayList<Long> toRemove = new ArrayList<>();
                        for (int a = 0, N = newPinned.size(); a < N; a++) {
                            Long did = newPinned.keyAt(a);
                            if (DialogObject.isEncryptedDialog(did)) {
                                continue;
                            }
                            if (newAlwaysShow.contains(did)) {
                                continue;
                            }
                            toRemove.add(did);
                        }
                        for (int a = 0, N = toRemove.size(); a < N; a++) {
                            newPinned.delete(toRemove.get(a));
                        }
                    }
                    fillFilterName();
                    checkDoneButton(false);
                    updateRows();
                });
                presentFragment(fragment);
            } else if (position == removeRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("FilterDelete", R.string.FilterDelete));
                builder.setMessage(LocaleController.getString("FilterDeleteAlert", R.string.FilterDeleteAlert));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
                    AlertDialog progressDialog = null;
                    if (getParentActivity() != null) {
                        progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.setCanCancel(false);
                        progressDialog.show();
                    }
                    final AlertDialog progressDialogFinal = progressDialog;
                    TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
                    req.id = filter.id;
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (progressDialogFinal != null) {
                                progressDialogFinal.dismiss();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        getMessagesController().removeFilter(filter);
                        getMessagesStorage().deleteDialogFilter(filter);
                        finishFragment();
                    }));
                });
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                }
            } else if (position == nameRow) {
                PollEditTextCell cell = (PollEditTextCell) view;
                cell.getTextView().requestFocus();
                AndroidUtilities.showKeyboard(cell.getTextView());
            } else if (view instanceof UserCell) {
                UserCell cell = (UserCell) view;
                showRemoveAlert(position, cell.getName(), cell.getCurrentObject(), position < includeSectionRow);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof UserCell) {
                UserCell cell = (UserCell) view;
                showRemoveAlert(position, cell.getName(), cell.getCurrentObject(), position < includeSectionRow);
                return true;
            }
            return false;
        });

        checkDoneButton(false);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    private void fillFilterName() {
        if (!creatingNew || !TextUtils.isEmpty(newFilterName) && nameChangedManually) {
            return;
        }
        int flags = newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        String newName = "";
        if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
            if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                newName = LocaleController.getString("FilterNameUnread", R.string.FilterNameUnread);
            } else if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                newName = LocaleController.getString("FilterNameNonMuted", R.string.FilterNameNonMuted);
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
            flags &=~ MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
            if (flags == 0) {
                newName = LocaleController.getString("FilterContacts", R.string.FilterContacts);
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
            flags &=~ MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
            if (flags == 0) {
                newName = LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts);
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
            flags &=~ MessagesController.DIALOG_FILTER_FLAG_GROUPS;
            if (flags == 0) {
                newName = LocaleController.getString("FilterGroups", R.string.FilterGroups);
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
            flags &=~ MessagesController.DIALOG_FILTER_FLAG_BOTS;
            if (flags == 0) {
                newName = LocaleController.getString("FilterBots", R.string.FilterBots);
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
            flags &=~ MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
            if (flags == 0) {
                newName = LocaleController.getString("FilterChannels", R.string.FilterChannels);
            }
        }
        if (newName != null && newName.length() > MAX_NAME_LENGTH) {
            newName = "";
        }
        newFilterName = newName;
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(nameRow);
        if (holder != null) {
            adapter.onViewAttachedToWindow(holder);
        }
    }

    private boolean checkDiscard() {
        if (doneItem.getAlpha() == 1.0f) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            if (creatingNew) {
                builder.setTitle(LocaleController.getString("FilterDiscardNewTitle", R.string.FilterDiscardNewTitle));
                builder.setMessage(LocaleController.getString("FilterDiscardNewAlert", R.string.FilterDiscardNewAlert));
                builder.setPositiveButton(LocaleController.getString("FilterDiscardNewSave", R.string.FilterDiscardNewSave), (dialogInterface, i) -> processDone());
            } else {
                builder.setTitle(LocaleController.getString("FilterDiscardTitle", R.string.FilterDiscardTitle));
                builder.setMessage(LocaleController.getString("FilterDiscardAlert", R.string.FilterDiscardAlert));
                builder.setPositiveButton(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            }
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    private void showRemoveAlert(int position, CharSequence name, Object object, boolean include) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        if (include) {
            builder.setTitle(LocaleController.getString("FilterRemoveInclusionTitle", R.string.FilterRemoveInclusionTitle));
            if (object instanceof String) {
                builder.setMessage(LocaleController.formatString("FilterRemoveInclusionText", R.string.FilterRemoveInclusionText, name));
            } else if (object instanceof TLRPC.User) {
                builder.setMessage(LocaleController.formatString("FilterRemoveInclusionUserText", R.string.FilterRemoveInclusionUserText, name));
            } else {
                builder.setMessage(LocaleController.formatString("FilterRemoveInclusionChatText", R.string.FilterRemoveInclusionChatText, name));
            }
        } else {
            builder.setTitle(LocaleController.getString("FilterRemoveExclusionTitle", R.string.FilterRemoveExclusionTitle));
            if (object instanceof String) {
                builder.setMessage(LocaleController.formatString("FilterRemoveExclusionText", R.string.FilterRemoveExclusionText, name));
            } else if (object instanceof TLRPC.User) {
                builder.setMessage(LocaleController.formatString("FilterRemoveExclusionUserText", R.string.FilterRemoveExclusionUserText, name));
            } else {
                builder.setMessage(LocaleController.formatString("FilterRemoveExclusionChatText", R.string.FilterRemoveExclusionChatText, name));
            }
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("StickersRemove", R.string.StickersRemove), (dialogInterface, i) -> {
            if (position == includeContactsRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
            } else if (position == includeNonContactsRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
            } else if (position == includeGroupsRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_GROUPS;
            } else if (position == includeChannelsRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
            } else if (position == includeBotsRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_BOTS;
            } else if (position == excludeArchivedRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
            } else if (position == excludeMutedRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
            } else if (position == excludeReadRow) {
                newFilterFlags &=~ MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
            } else {
                if (include) {
                    newAlwaysShow.remove(position - includeStartRow);
                } else {
                    newNeverShow.remove(position - excludeStartRow);
                }
            }
            fillFilterName();
            updateRows();
            checkDoneButton(true);
        });
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        }
    }

    private void processDone() {
        saveFilterToServer(filter, newFilterFlags, newFilterName, newAlwaysShow, newNeverShow, newPinned, creatingNew, false, hasUserChanged, true, true, this, () -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            finishFragment();
        });
    }

    private static void processAddFilter(MessagesController.DialogFilter filter, int newFilterFlags, String newFilterName, ArrayList<Long> newAlwaysShow, ArrayList<Long> newNeverShow, boolean creatingNew, boolean atBegin, boolean hasUserChanged, boolean resetUnreadCounter, BaseFragment fragment, Runnable onFinish) {
        if (filter.flags != newFilterFlags || hasUserChanged) {
            filter.pendingUnreadCount = -1;
            if (resetUnreadCounter) {
                filter.unreadCount = -1;
            }
        }
        filter.flags = newFilterFlags;
        filter.name = newFilterName;
        filter.neverShow = newNeverShow;
        filter.alwaysShow = newAlwaysShow;
        if (creatingNew) {
            fragment.getMessagesController().addFilter(filter, atBegin);
        } else {
            fragment.getMessagesController().onFilterUpdate(filter);
        }
        fragment.getMessagesStorage().saveDialogFilter(filter, atBegin, true);
        if (onFinish != null) {
            onFinish.run();
        }
    }

    public static void saveFilterToServer(MessagesController.DialogFilter filter, int newFilterFlags, String newFilterName, ArrayList<Long> newAlwaysShow, ArrayList<Long> newNeverShow, LongSparseIntArray newPinned, boolean creatingNew, boolean atBegin, boolean hasUserChanged, boolean resetUnreadCounter, boolean progress, BaseFragment fragment, Runnable onFinish) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog;
        if (progress) {
            progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.setCanCancel(false);
            progressDialog.show();
        } else {
            progressDialog = null;
        }
        TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
        req.id = filter.id;
        req.flags |= 1;
        req.filter = new TLRPC.TL_dialogFilter();
        req.filter.contacts = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0;
        req.filter.non_contacts = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0;
        req.filter.groups = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0;
        req.filter.broadcasts = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0;
        req.filter.bots = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0;
        req.filter.exclude_muted = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0;
        req.filter.exclude_read = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0;
        req.filter.exclude_archived = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0;
        req.filter.id = filter.id;
        req.filter.title = newFilterName;
        MessagesController messagesController = fragment.getMessagesController();
        ArrayList<Long> pinArray = new ArrayList<>();
        if (newPinned.size() != 0) {
            for (int a = 0, N = newPinned.size(); a < N; a++) {
                long key = newPinned.keyAt(a);
                if (DialogObject.isEncryptedDialog(key)) {
                    continue;
                }
                pinArray.add(key);
            }
            Collections.sort(pinArray, (o1, o2) -> {
                int idx1 = newPinned.get(o1);
                int idx2 = newPinned.get(o2);
                if (idx1 > idx2) {
                    return 1;
                } else if (idx1 < idx2) {
                    return -1;
                }
                return 0;
            });
        }
        for (int b = 0; b < 3; b++) {
            ArrayList<Long> fromArray;
            ArrayList<TLRPC.InputPeer> toArray;
            if (b == 0) {
                fromArray = newAlwaysShow;
                toArray = req.filter.include_peers;
            } else if (b == 1) {
                fromArray = newNeverShow;
                toArray = req.filter.exclude_peers;
            } else {
                fromArray = pinArray;
                toArray = req.filter.pinned_peers;
            }
            for (int a = 0, N = fromArray.size(); a < N; a++) {
                long did = fromArray.get(a);
                if (b == 0 && newPinned.indexOfKey(did) >= 0) {
                    continue;
                }
                if (!DialogObject.isEncryptedDialog(did)) {
                    if (did > 0) {
                        TLRPC.User user = messagesController.getUser(did);
                        if (user != null) {
                            TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerUser();
                            inputPeer.user_id = did;
                            inputPeer.access_hash = user.access_hash;
                            toArray.add(inputPeer);
                        }
                    } else {
                        TLRPC.Chat chat = messagesController.getChat(-did);
                        if (chat != null) {
                            if (ChatObject.isChannel(chat)) {
                                TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerChannel();
                                inputPeer.channel_id = -did;
                                inputPeer.access_hash = chat.access_hash;
                                toArray.add(inputPeer);
                            } else {
                                TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerChat();
                                inputPeer.chat_id = -did;
                                toArray.add(inputPeer);
                            }
                        }
                    }
                }
            }
        }
        fragment.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (progress) {
                try {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                processAddFilter(filter, newFilterFlags, newFilterName, newAlwaysShow, newNeverShow, creatingNew, atBegin, hasUserChanged, resetUnreadCounter, fragment, onFinish);
            }
        }));
        if (!progress) {
            processAddFilter(filter, newFilterFlags, newFilterName, newAlwaysShow, newNeverShow, creatingNew, atBegin, hasUserChanged, resetUnreadCounter, fragment, onFinish);
        }
    }

    @Override
    public boolean canBeginSlide() {
        return checkDiscard();
    }

    private boolean hasChanges() {
        hasUserChanged = false;
        if (filter.alwaysShow.size() != newAlwaysShow.size()) {
            hasUserChanged = true;
        }
        if (filter.neverShow.size() != newNeverShow.size()) {
            hasUserChanged = true;
        }
        if (!hasUserChanged) {
            Collections.sort(filter.alwaysShow);
            Collections.sort(newAlwaysShow);
            if (!filter.alwaysShow.equals(newAlwaysShow)) {
                hasUserChanged = true;
            }
            Collections.sort(filter.neverShow);
            Collections.sort(newNeverShow);
            if (!filter.neverShow.equals(newNeverShow)) {
                hasUserChanged = true;
            }
        }
        if (!TextUtils.equals(filter.name, newFilterName)) {
            return true;
        }
        if (filter.flags != newFilterFlags) {
            return true;
        }
        return hasUserChanged;
    }

    private void checkDoneButton(boolean animated) {
        boolean enabled = !TextUtils.isEmpty(newFilterName) && newFilterName.length() <= MAX_NAME_LENGTH;
        if (enabled) {
            enabled = (newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) != 0 || !newAlwaysShow.isEmpty();
            if (enabled && !creatingNew) {
                enabled = hasChanges();
            }
        }
        if (doneItem.isEnabled() == enabled) {
            return;
        }
        doneItem.setEnabled(enabled);
        if (animated) {
            doneItem.animate().alpha(enabled ? 1.0f : 0.0f).scaleX(enabled ? 1.0f : 0.0f).scaleY(enabled ? 1.0f : 0.0f).setDuration(180).start();
        } else {
            doneItem.setAlpha(enabled ? 1.0f : 0.0f);
            doneItem.setScaleX(enabled ? 1.0f : 0.0f);
            doneItem.setScaleY(enabled ? 1.0f : 0.0f);
        }
    }

    private void setTextLeft(View cell) {
        if (cell instanceof PollEditTextCell) {
            PollEditTextCell textCell = (PollEditTextCell) cell;
            int left = MAX_NAME_LENGTH - (newFilterName != null ? newFilterName.length() : 0);
            if (left <= MAX_NAME_LENGTH - MAX_NAME_LENGTH * 0.7f) {
                textCell.setText2(String.format("%d", left));
                SimpleTextView textView = textCell.getTextView2();
                String key = left < 0 ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteGrayText3;
                textView.setTextColor(Theme.getColor(key));
                textView.setTag(key);
                textView.setAlpha(((PollEditTextCell) cell).getTextView().isFocused() || left < 0 ? 1.0f : 0.0f);
            } else {
                textCell.setText2("");
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != 3 && type != 0 && type != 2 && type != 5;
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
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1: {
                    UserCell cell = new UserCell(mContext, 6, 0, false);
                    cell.setSelfAsSavedMessages(true);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                    break;
                }
                case 2: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, null);
                    cell.createErrorTextView();
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (cell.getTag() != null) {
                                return;
                            }
                            String newName = s.toString();
                            if (!TextUtils.equals(newName, newFilterName)) {
                                nameChangedManually = !TextUtils.isEmpty(newName);
                                newFilterName = newName;
                            }
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(nameRow);
                            if (holder != null) {
                                setTextLeft(holder.itemView);
                            }
                            checkDoneButton(true);
                        }
                    });
                    EditTextBoldCursor editText = cell.getTextView();
                    cell.setShowNextButton(true);
                    editText.setOnFocusChangeListener((v, hasFocus) -> cell.getTextView2().setAlpha(hasFocus || newFilterName.length() > MAX_NAME_LENGTH ? 1.0f : 0.0f));
                    editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    view = cell;
                    break;
                }
                case 3:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 4:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new HintInnerCell(mContext);
                    break;
                case 6:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 2) {
                setTextLeft(holder.itemView);
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setTextAndHint(newFilterName != null ? newFilterName : "", LocaleController.getString("FilterNameHint", R.string.FilterNameHint), false);
                textCell.setTag(null);
            }
        }

        @Override
        public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 2) {
                PollEditTextCell editTextCell = (PollEditTextCell) holder.itemView;
                EditTextBoldCursor editText = editTextCell.getTextView();
                if (editText.isFocused()) {
                    editText.clearFocus();
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == includeHeaderRow) {
                        headerCell.setText(LocaleController.getString("FilterInclude", R.string.FilterInclude));
                    } else if (position == excludeHeaderRow) {
                        headerCell.setText(LocaleController.getString("FilterExclude", R.string.FilterExclude));
                    }
                    break;
                }
                case 1: {
                    UserCell userCell = (UserCell) holder.itemView;
                    Long id;
                    boolean divider;
                    if (position >= includeStartRow && position < includeEndRow) {
                        id = newAlwaysShow.get(position - includeStartRow);
                        divider = includeShowMoreRow != -1 || position != includeEndRow - 1;
                    } else if (position >= excludeStartRow && position < excludeEndRow) {
                        id = newNeverShow.get(position - excludeStartRow);
                        divider = excludeShowMoreRow != -1 || position != excludeEndRow - 1;
                    } else {
                        Object object;
                        int flag;
                        String name;
                        if (position == includeContactsRow) {
                            name = LocaleController.getString("FilterContacts", R.string.FilterContacts);
                            object = "contacts";
                            divider = position + 1 != includeSectionRow;
                        } else if (position == includeNonContactsRow) {
                            name = LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts);
                            object = "non_contacts";
                            divider = position + 1 != includeSectionRow;
                        } else if (position == includeGroupsRow) {
                            name = LocaleController.getString("FilterGroups", R.string.FilterGroups);
                            object = "groups";
                            divider = position + 1 != includeSectionRow;
                        } else if (position == includeChannelsRow) {
                            name = LocaleController.getString("FilterChannels", R.string.FilterChannels);
                            object = "channels";
                            divider = position + 1 != includeSectionRow;
                        } else if (position == includeBotsRow) {
                            name = LocaleController.getString("FilterBots", R.string.FilterBots);
                            object = "bots";
                            divider = position + 1 != includeSectionRow;
                        } else if (position == excludeMutedRow) {
                            name = LocaleController.getString("FilterMuted", R.string.FilterMuted);
                            object = "muted";
                            divider = position + 1 != excludeSectionRow;
                        } else if (position == excludeReadRow) {
                            name = LocaleController.getString("FilterRead", R.string.FilterRead);
                            object = "read";
                            divider = position + 1 != excludeSectionRow;
                        } else {
                            name = LocaleController.getString("FilterArchived", R.string.FilterArchived);
                            object = "archived";
                            divider = position + 1 != excludeSectionRow;
                        }
                        userCell.setData(object, name, null, 0, divider);
                        return;
                    }
                    if (id > 0) {
                        TLRPC.User user = getMessagesController().getUser(id);
                        if (user != null) {
                            String status;
                            if (user.bot) {
                                status = LocaleController.getString("Bot", R.string.Bot);
                            } else if (user.contact) {
                                status = LocaleController.getString("FilterContact", R.string.FilterContact);
                            } else {
                                status = LocaleController.getString("FilterNonContact", R.string.FilterNonContact);
                            }
                            userCell.setData(user, null, status, 0, divider);
                        }
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-id);
                        if (chat != null) {
                            String status;
                            if (chat.participants_count != 0) {
                                status = LocaleController.formatPluralString("Members", chat.participants_count);
                            } else if (!ChatObject.isPublic(chat)) {
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    status = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate);
                                } else {
                                    status = LocaleController.getString("MegaPrivate", R.string.MegaPrivate);
                                }
                            } else {
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    status = LocaleController.getString("ChannelPublic", R.string.ChannelPublic);
                                } else {
                                    status = LocaleController.getString("MegaPublic", R.string.MegaPublic);
                                }
                            }
                            userCell.setData(chat, null, status, 0, divider);
                        }
                    }
                    break;
                }
                case 3: {
                    if (position == removeSectionRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 4: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == removeRow) {
                        textCell.setColors(null, Theme.key_windowBackgroundWhiteRedText5);
                        textCell.setText(LocaleController.getString("FilterDelete", R.string.FilterDelete), false);
                    } else if (position == includeShowMoreRow) {
                        textCell.setColors(Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextAndIcon(LocaleController.formatPluralString("FilterShowMoreChats", newAlwaysShow.size() - 5), R.drawable.arrow_more, false);
                    } else if (position == excludeShowMoreRow) {
                        textCell.setColors(Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextAndIcon(LocaleController.formatPluralString("FilterShowMoreChats", newNeverShow.size() - 5), R.drawable.arrow_more, false);
                    } else if (position == includeAddRow) {
                        textCell.setColors(Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextAndIcon(LocaleController.getString("FilterAddChats", R.string.FilterAddChats), R.drawable.msg_chats_add, position + 1 != includeSectionRow);
                    } else if (position == excludeAddRow) {
                        textCell.setColors(Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextAndIcon(LocaleController.getString("FilterRemoveChats", R.string.FilterRemoveChats), R.drawable.msg_chats_add, position + 1 != excludeSectionRow);
                    }
                    break;
                }
                case 6: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == includeSectionRow) {
                        cell.setText(LocaleController.getString("FilterIncludeInfo", R.string.FilterIncludeInfo));
                    } else if (position == excludeSectionRow) {
                        cell.setText(LocaleController.getString("FilterExcludeInfo", R.string.FilterExcludeInfo));
                    }
                    if (position == excludeSectionRow && removeSectionRow == -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == includeHeaderRow || position == excludeHeaderRow) {
                return 0;
            } else if (position >= includeStartRow && position < includeEndRow || position >= excludeStartRow && position < excludeEndRow ||
                        position == includeContactsRow || position == includeNonContactsRow || position == includeGroupsRow || position == includeChannelsRow || position == includeBotsRow ||
                        position == excludeReadRow || position == excludeArchivedRow || position == excludeMutedRow) {
                return 1;
            } else if (position == nameRow) {
                return 2;
            } else if (position == nameSectionRow || position == namePreSectionRow || position == removeSectionRow) {
                return 3;
            } else if (position == imageRow) {
                return 5;
            } else if (position == includeSectionRow || position == excludeSectionRow) {
                return 6;
            } else {
                return 4;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate themeDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCell.class, PollEditTextCell.class, UserCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"ImageView"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{UserCell.class}, new String[]{"adminTextView"}, null, null, null, Theme.key_profile_creatorIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, themeDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, themeDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }
}

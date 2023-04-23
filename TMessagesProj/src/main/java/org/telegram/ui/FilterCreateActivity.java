package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FolderBottomSheet;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.QRCodeBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public class FilterCreateActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private ActionBarMenuItem doneItem;
    private UndoView undoView;

    private int nameRow = -1;

    private boolean includeExpanded;
    private boolean excludeExpanded;
    private boolean hasUserChanged;

    private boolean nameChangedManually;

    private MessagesController.DialogFilter filter;
    private boolean creatingNew;
    private boolean doNotCloseWhenSave;
    private String newFilterName;
    private int newFilterFlags;
    private ArrayList<Long> newAlwaysShow;
    private ArrayList<Long> newNeverShow;
    private LongSparseIntArray newPinned;
    private CreateLinkCell createLinkCell;
    private boolean canCreateLink() {
        return (
            (!TextUtils.isEmpty(newFilterName) || !TextUtils.isEmpty(filter.name)) &&
            (newFilterFlags & ~(MessagesController.DIALOG_FILTER_FLAG_CHATLIST | MessagesController.DIALOG_FILTER_FLAG_CHATLIST_ADMIN)) == 0 &&
            newNeverShow.isEmpty() &&
            !newAlwaysShow.isEmpty()
        );
    }

    private ArrayList<TLRPC.TL_exportedChatlistInvite> invites = new ArrayList<>();

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
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(156), MeasureSpec.EXACTLY));
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

    private int requestingInvitesReqId;

    @Override
    public boolean onFragmentCreate() {
        updateRows();
        return super.onFragmentCreate();
    }

    private boolean loadingInvites;
    public void loadInvites() {
        if (loadingInvites) {
            return;
        }
        if (filter == null || !filter.isChatlist()) {
            return;
        }
        loadingInvites = true;
        TLRPC.TL_chatlists_getExportedInvites req = new TLRPC.TL_chatlists_getExportedInvites();
        req.chatlist = new TLRPC.TL_inputChatlistDialogFilter();
        req.chatlist.filter_id = filter.id;
        requestingInvitesReqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loadingInvites = false;
            if (res instanceof TLRPC.TL_chatlists_exportedInvites) {
                TLRPC.TL_chatlists_exportedInvites invs = (TLRPC.TL_chatlists_exportedInvites) res;
                getMessagesController().putChats(invs.chats, false);
                getMessagesController().putUsers(invs.users, false);
                invites.clear();
                invites.addAll(((TLRPC.TL_chatlists_exportedInvites) res).invites);
                updateRows();
            }
            requestingInvitesReqId = 0;
        }));
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (requestingInvitesReqId != 0) {
            getConnectionsManager().cancelRequest(requestingInvitesReqId, true);
        }
    }

    private void updateRows() {
        updateRows(true);
    }

    private ArrayList<ItemInner> oldItems = new ArrayList<>();
    private ArrayList<ItemInner> items = new ArrayList<>();

    private void updateRows(boolean animated) {

        oldItems.clear();
        oldItems.addAll(items);

        items.clear();

        items.add(new ItemInner(VIEW_TYPE_HINT, false));
        nameRow = items.size();
        items.add(ItemInner.asEdit());
        items.add(ItemInner.asShadow(null));
        items.add(ItemInner.asHeader(LocaleController.getString("FilterInclude", R.string.FilterInclude)));
        items.add(ItemInner.asButton(R.drawable.msg2_chats_add, LocaleController.getString("FilterAddChats", R.string.FilterAddChats), false).whenClicked(v -> selectChatsFor(true)));

        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
            items.add(ItemInner.asChat(true, LocaleController.getString("FilterContacts", R.string.FilterContacts), "contacts", MessagesController.DIALOG_FILTER_FLAG_CONTACTS));
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
            items.add(ItemInner.asChat(true, LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts), "non_contacts", MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS));
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
            items.add(ItemInner.asChat(true, LocaleController.getString("FilterGroups", R.string.FilterGroups), "groups", MessagesController.DIALOG_FILTER_FLAG_GROUPS));
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
            items.add(ItemInner.asChat(true, LocaleController.getString("FilterChannels", R.string.FilterChannels), "channels", MessagesController.DIALOG_FILTER_FLAG_CHANNELS));
        }
        if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
            items.add(ItemInner.asChat(true, LocaleController.getString("FilterBots", R.string.FilterBots), "bots", MessagesController.DIALOG_FILTER_FLAG_BOTS));
        }

        if (!newAlwaysShow.isEmpty()) {
            int count = includeExpanded || newAlwaysShow.size() < 8 ? newAlwaysShow.size() : Math.min(5, newAlwaysShow.size());
            for (int i = 0; i < count; ++i) {
                items.add(ItemInner.asChat(true, newAlwaysShow.get(i)));
            }
            if (count != newAlwaysShow.size()) {
                items.add(
                    ItemInner.asButton(R.drawable.arrow_more, LocaleController.formatPluralString("FilterShowMoreChats", newAlwaysShow.size() - 5), false)
                        .whenClicked(v -> {
                            includeExpanded = true;
                            updateRows();
                        })
                );
            }
        }
        items.add(ItemInner.asShadow(LocaleController.getString("FilterIncludeInfo", R.string.FilterIncludeInfo)));
        if (!filter.isChatlist()) {
            items.add(ItemInner.asHeader(LocaleController.getString("FilterExclude", R.string.FilterExclude)));
            items.add(ItemInner.asButton(R.drawable.msg2_chats_add, LocaleController.getString("FilterRemoveChats", R.string.FilterRemoveChats), false).whenClicked(v -> selectChatsFor(false)));
            if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                items.add(ItemInner.asChat(false, LocaleController.getString("FilterMuted", R.string.FilterMuted), "muted", MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED));
            }
            if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                items.add(ItemInner.asChat(false, LocaleController.getString("FilterRead", R.string.FilterRead), "read", MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ));
            }
            if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0) {
                items.add(ItemInner.asChat(false, LocaleController.getString("FilterArchived", R.string.FilterArchived), "archived", MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED));
            }
            if (!newNeverShow.isEmpty()) {
                int count = excludeExpanded || newNeverShow.size() < 8 ? newNeverShow.size() : Math.min(5, newNeverShow.size());
                for (int i = 0; i < count; ++i) {
                    items.add(ItemInner.asChat(false, newNeverShow.get(i)));
                }
                if (count != newNeverShow.size()) {
                    items.add(
                        ItemInner.asButton(R.drawable.arrow_more, LocaleController.formatPluralString("FilterShowMoreChats", newNeverShow.size() - 5), false)
                            .whenClicked(v -> {
                                excludeExpanded = true;
                                updateRows();
                            })
                    );
                }
            }
            items.add(ItemInner.asShadow(LocaleController.getString("FilterExcludeInfo", R.string.FilterExcludeInfo)));
        }

        if (invites.isEmpty()) {
            items.add(ItemInner.asHeader(LocaleController.getString("FilterShareFolder", R.string.FilterShareFolder), true));
            items.add(ItemInner.asButton(R.drawable.msg2_link2, LocaleController.getString("FilterShareFolderButton", R.string.FilterShareFolderButton), false));
            items.add(ItemInner.asShadow(LocaleController.getString("FilterInviteLinksHintNew", R.string.FilterInviteLinksHintNew)));
        } else {
            items.add(ItemInner.asHeader(LocaleController.getString("FilterInviteLinks", R.string.FilterInviteLinks), true));
            items.add(ItemInner.asCreateLink());
            for (int i = 0; i < invites.size(); ++i) {
                items.add(ItemInner.asLink(invites.get(i)));
            }
            items.add(ItemInner.asShadow(
                filter != null && filter.isChatlist() ?
                    LocaleController.getString("FilterInviteLinksHintNew", R.string.FilterInviteLinksHintNew) :
                    LocaleController.getString("FilterInviteLinksHint", R.string.FilterInviteLinksHint)
            ));
        }

        if (!creatingNew) {
            items.add(ItemInner.asButton(0, LocaleController.getString("FilterDelete", R.string.FilterDelete), true).whenClicked(this::deleteFolder));
            items.add(ItemInner.asShadow(null));
        }

        if (adapter != null) {
            if (animated) {
                adapter.setItems(oldItems, items);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    float shiftDp = -5;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        ActionBarMenu menu = actionBar.createMenu();
        if (creatingNew) {
            actionBar.setTitle(LocaleController.getString("FilterNew", R.string.FilterNew));
        } else {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(dp(20));
            actionBar.setTitle(Emoji.replaceEmoji(filter.name, paint.getFontMetricsInt(), dp(20), false));
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
            public Integer getSelectorColor(int position) {
                ItemInner item = position < 0 || position >= items.size() ? null : items.get(position);
                if (item != null && item.isRed) {
                    return Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .12f);
                }
                return getThemedColor(Theme.key_listSelector);
            }

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
            ItemInner item = items.get(position);
            if (item == null) {
                return;
            }
            if (item.onClickListener != null) {
                item.onClickListener.onClick(view);
            } else if (item.viewType == VIEW_TYPE_CHAT) {
                UserCell cell = (UserCell) view;
                showRemoveAlert(item, cell.getName(), cell.getCurrentObject(), item.include);
            } else if (item.viewType == VIEW_TYPE_LINK) {
                Runnable open = () -> {
                    FilterChatlistActivity fragment = new FilterChatlistActivity(filter, item.link);
                    fragment.setOnEdit(this::onEdit);
                    fragment.setOnDelete(this::onDelete);
                    presentFragment(fragment);
                };
                if (doneItem.isEnabled()) {
                    save(false, open);
                } else {
                    open.run();
                }
            } else if (item.viewType == VIEW_TYPE_CREATE_LINK || item.viewType == VIEW_TYPE_BUTTON && item.iconResId == R.drawable.msg2_link2) {
                onClickCreateLink(view);
            } else if (item.viewType == VIEW_TYPE_EDIT) {
                PollEditTextCell cell = (PollEditTextCell) view;
                cell.getTextView().requestFocus();
                AndroidUtilities.showKeyboard(cell.getTextView());
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            ItemInner item = items.get(position);
            if (item == null) {
                return false;
            }
            if (view instanceof UserCell) {
                UserCell cell = (UserCell) view;
                showRemoveAlert(item, cell.getName(), cell.getCurrentObject(), item.include);
                return true;
            }
            return false;
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);

        checkDoneButton(false);

        loadInvites();

        return fragmentView;
    }


    public UndoView getUndoView() {
        if (getContext() == null) {
            return null;
        }
        if (undoView == null) {
            ((FrameLayout) fragmentView).addView(undoView = new UndoView(getContext()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }
        return undoView;
    }

    private void onClickCreateLink(View view) {
        if (creatingNew && doneItem.getAlpha() > 0) {
            AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            doNotCloseWhenSave = true;
            showSaveHint();
            return;
        }
        if (!canCreateLink()) {
            AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            if (TextUtils.isEmpty(newFilterName) && TextUtils.isEmpty(filter.name)) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("FilterInviteErrorEmptyName", R.string.FilterInviteErrorEmptyName)).show();
            } else if ((newFilterFlags & ~(MessagesController.DIALOG_FILTER_FLAG_CHATLIST | MessagesController.DIALOG_FILTER_FLAG_CHATLIST_ADMIN)) != 0) {
                if (!newNeverShow.isEmpty()) {
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("FilterInviteErrorTypesExcluded", R.string.FilterInviteErrorTypesExcluded)).show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("FilterInviteErrorTypes", R.string.FilterInviteErrorTypes)).show();
                }
            } else if (newAlwaysShow.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("FilterInviteErrorEmpty", R.string.FilterInviteErrorEmpty)).show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("FilterInviteErrorExcluded", R.string.FilterInviteErrorExcluded)).show();
            }
            return;
        }
        save(false, () -> {
            getMessagesController().updateFilterDialogs(filter);

            ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();
            for (int i = 0; i < filter.alwaysShow.size(); ++i) {
                long did = filter.alwaysShow.get(i);
                if (did < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-did);
                    if (canAddToFolder(chat)) {
                        peers.add(getMessagesController().getInputPeer(did));
                    }
                }
            }

            final int maxCount = getUserConfig().isPremium() ? getMessagesController().dialogFiltersChatsLimitPremium : getMessagesController().dialogFiltersChatsLimitDefault;
            if (peers.size() > maxCount) {
                showDialog(new LimitReachedBottomSheet(this, getContext(), LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, currentAccount));
                return;
            }

            if (!peers.isEmpty()) {
                TLRPC.TL_chatlists_exportChatlistInvite req = new TLRPC.TL_chatlists_exportChatlistInvite();
                req.chatlist = new TLRPC.TL_inputChatlistDialogFilter();
                req.chatlist.filter_id = filter.id;
                req.peers = peers;
                req.title = "";
                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (
                        processErrors(err, FilterCreateActivity.this, BulletinFactory.of(FilterCreateActivity.this)) &&
                        res instanceof TLRPC.TL_chatlists_exportedChatlistInvite
                    ) {
                        FilterCreateActivity.hideNew(0);

                        getMessagesController().loadRemoteFilters(true);
                        TLRPC.TL_chatlists_exportedChatlistInvite inv = (TLRPC.TL_chatlists_exportedChatlistInvite) res;
                        FilterChatlistActivity fragment = new FilterChatlistActivity(filter, inv.invite);
                        fragment.setOnEdit(this::onEdit);
                        fragment.setOnDelete(this::onDelete);
                        presentFragment(fragment);

                        AndroidUtilities.runOnUIThread(() -> onEdit(inv.invite), 200);
                    }
                }));
            } else {
                FilterChatlistActivity fragment = new FilterChatlistActivity(filter, null);
                fragment.setOnEdit(this::onEdit);
                fragment.setOnDelete(this::onDelete);
                presentFragment(fragment);
            }
        });
    }

    private HintView saveHintView;
    private void showSaveHint() {
        if (saveHintView != null && saveHintView.getVisibility() == View.VISIBLE) {
            return;
        }

        saveHintView = new HintView(getContext(), 6, true) {
            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility != VISIBLE) {
                    try {
                        ((ViewGroup) getParent()).removeView(this);
                    } catch (Exception ignore) {}
                }
            }
        };
        saveHintView.textView.setMaxWidth(AndroidUtilities.displaySize.x);
        saveHintView.setExtraTranslationY(AndroidUtilities.dp(-16));
        saveHintView.setText(LocaleController.getString("FilterFinishCreating", R.string.FilterFinishCreating));
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = AndroidUtilities.dp(3);
        getParentLayout().getOverlayContainerView().addView(saveHintView, params);
        saveHintView.showForView(doneItem, true);
    }

    public static boolean canAddToFolder(TLRPC.Chat chat) {
        return ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) || ChatObject.isPublic(chat) && !chat.join_request;
    }

    private void onDelete(TLRPC.TL_exportedChatlistInvite editedInvite) {
        if (editedInvite == null) {
            return;
        }

        int index = -1;
        for (int i = 0; i < invites.size(); ++i) {
            TLRPC.TL_exportedChatlistInvite invite = invites.get(i);
            if (TextUtils.equals(invite.url, editedInvite.url)) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            invites.remove(index);

            if (invites.isEmpty()) {
                filter.flags &= ~MessagesController.DIALOG_FILTER_FLAG_CHATLIST;
            }

            updateRows();
        }
    }

    private void onEdit(TLRPC.TL_exportedChatlistInvite editedInvite) {
        if (editedInvite == null) {
            return;
        }

        int index = -1;
        for (int i = 0; i < invites.size(); ++i) {
            TLRPC.TL_exportedChatlistInvite invite = invites.get(i);
            if (TextUtils.equals(invite.url, editedInvite.url)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            invites.add(editedInvite);
        } else {
            invites.set(index, editedInvite);
        }
        updateRows();
    }

    private void deleteFolder(View view) {
        if (filter != null && filter.isChatlist()) {
            FolderBottomSheet.showForDeletion(this, filter.id, success -> {
                finishFragment();
            });
        } else {
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
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }
    }

    private void onUpdate(boolean include, ArrayList<Long> prev, ArrayList<Long> next) {
        int added = 0, removed = 0;
        if (prev != null && next != null) {
            for (int i = 0; i < prev.size(); ++i) {
                if (!next.contains(prev.get(i))) {
                    removed++;
                }
            }
            for (int i = 0; i < next.size(); ++i) {
                if (!prev.contains(next.get(i))) {
                    added++;
                }
            }
        } else if (prev != null) {
            removed = prev.size();
        } else if (next != null) {
            added = next.size();
        }
        if (include) {
            if (added > 0 && added > removed) {
                onUpdate(true, added);
            } else if (removed > 0) {
                onUpdate(false, removed);
            }
        } else if (added > 0) {
            onUpdate(false, added);
        }
    }

    private void selectChatsFor(boolean include) {
        ArrayList<Long> arrayList = include ? newAlwaysShow : newNeverShow;
        UsersSelectActivity fragment = new UsersSelectActivity(include, arrayList, newFilterFlags);
        fragment.noChatTypes = filter.isChatlist();
        fragment.setDelegate((ids, flags) -> {
            newFilterFlags = flags;
            if (include) {
                onUpdate(true, newAlwaysShow, ids);
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
            } else {
                onUpdate(false, newNeverShow, ids);
                newNeverShow = ids;
                for (int a = 0; a < newNeverShow.size(); a++) {
                    Long id = newNeverShow.get(a);
                    newAlwaysShow.remove(id);
                    newPinned.delete(id);
                }
            }
            fillFilterName();
            checkDoneButton(false);
            updateRows();
        });
        presentFragment(fragment);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();

        if (showBulletinOnResume != null) {
            showBulletinOnResume.run();
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

    private void showRemoveAlert(ItemInner item, CharSequence name, Object object, boolean include) {
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
            if (item.flags > 0) {
                newFilterFlags &=~ item.flags;
            } else {
                (include ? newAlwaysShow : newNeverShow).remove((Long) item.did);
            }
            fillFilterName();
            updateRows();
            checkDoneButton(true);

            if (include) {
                onUpdate(false, 1);
            }
        });
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void processDone() {
        if (saveHintView != null) {
            saveHintView.hide(true);
            saveHintView = null;
        }
        save(true, () -> {
            if (doNotCloseWhenSave) {
                doNotCloseWhenSave = false;
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                paint.setTextSize(dp(20));
                actionBar.setTitleAnimated(Emoji.replaceEmoji(filter.name, paint.getFontMetricsInt(), dp(20), false), true, 220);
                return;
            }
            finishFragment();
        });
    }

    private void save(boolean progress, Runnable after) {
        saveFilterToServer(filter, newFilterFlags, newFilterName, newAlwaysShow, newNeverShow, newPinned, creatingNew, false, hasUserChanged, true, progress, this, () -> {

            hasUserChanged = false;
            creatingNew = false;
            filter.flags = newFilterFlags;
            checkDoneButton(true);

            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);

            if (after != null) {
                after.run();
            }
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
        if (atBegin) {
            TLRPC.TL_messages_updateDialogFiltersOrder req = new TLRPC.TL_messages_updateDialogFiltersOrder();
            ArrayList<MessagesController.DialogFilter> filters = fragment.getMessagesController().getDialogFilters();
            for (int a = 0, N = filters.size(); a < N; a++) {
                req.order.add(filters.get(a).id);
            }
            fragment.getConnectionsManager().sendRequest(req, null);
        }
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
            } else if (onFinish != null) {
                onFinish.run();
            }
        }));
        if (!progress) {
            processAddFilter(filter, newFilterFlags, newFilterName, newAlwaysShow, newNeverShow, creatingNew, atBegin, hasUserChanged, resetUnreadCounter, fragment, null);
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
                String key = left < 0 ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText3;
                textView.setTextColor(Theme.getColor(key));
                textView.setTag(key);
                textView.setAlpha(((PollEditTextCell) cell).getTextView().isFocused() || left < 0 ? 1.0f : 0.0f);
            } else {
                textCell.setText2("");
            }
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHAT = 1;
    private static final int VIEW_TYPE_EDIT = 2;
    private static final int VIEW_TYPE_SHADOW = 3;
    private static final int VIEW_TYPE_BUTTON = 4;
    private static final int VIEW_TYPE_HINT = 5;
    private static final int VIEW_TYPE_SHADOW_TEXT = 6;
    private static final int VIEW_TYPE_LINK = 7;
    private static final int VIEW_TYPE_CREATE_LINK = 8;

    private static class ItemInner extends AdapterWithDiffUtils.Item {

        private View.OnClickListener onClickListener;

        private CharSequence text;
        private boolean newSpan;

        private boolean include; // or exclude
        private long did;
        private String chatType;
        private int flags;

        private int iconResId;
        private boolean isRed;

        private TLRPC.TL_exportedChatlistInvite link;

        public ItemInner(int viewType, boolean selectable) {
            super(viewType, selectable);
        }

        public static ItemInner asHeader(CharSequence text) {
            ItemInner item = new ItemInner(VIEW_TYPE_HEADER, false);
            item.text = text;
            return item;
        }

        public static ItemInner asHeader(CharSequence text, boolean newSpan) {
            ItemInner item = new ItemInner(VIEW_TYPE_HEADER, false);
            item.text = text;
            item.newSpan = newSpan;
            return item;
        }

        public static ItemInner asChat(boolean include, long did) {
            ItemInner item = new ItemInner(VIEW_TYPE_CHAT, false);
            item.include = include;
            item.did = did;
            return item;
        }

        public static ItemInner asChat(boolean include, CharSequence name, String chatType, int flags) {
            ItemInner item = new ItemInner(VIEW_TYPE_CHAT, false);
            item.include = include;
            item.text = name;
            item.chatType = chatType;
            item.flags = flags;
            return item;
        }

        public static ItemInner asEdit() {
            return new ItemInner(VIEW_TYPE_EDIT, false);
        }

        public static ItemInner asShadow(CharSequence text) {
            ItemInner item = new ItemInner(TextUtils.isEmpty(text) ? VIEW_TYPE_SHADOW : VIEW_TYPE_SHADOW_TEXT, false);
            item.text = text;
            return item;
        }

        public static ItemInner asLink(TLRPC.TL_exportedChatlistInvite invite) {
            ItemInner item = new ItemInner(VIEW_TYPE_LINK, false);
            item.link = invite;
            return item;
        }

        public static ItemInner asButton(int iconResId, CharSequence text, boolean red) {
            ItemInner item = new ItemInner(VIEW_TYPE_BUTTON, false);
            item.iconResId = iconResId;
            item.text = text;
            item.isRed = red;
            return item;
        }

        public static ItemInner asCreateLink() {
            return new ItemInner(VIEW_TYPE_CREATE_LINK, false);
        }

        public ItemInner whenClicked(View.OnClickListener onClickListener) {
            this.onClickListener = onClickListener;
            return this;
        }

        public boolean isShadow() {
            return viewType == VIEW_TYPE_SHADOW || viewType == VIEW_TYPE_SHADOW_TEXT;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner other = (ItemInner) o;
            if (this.viewType != other.viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_HEADER || viewType == VIEW_TYPE_CHAT || viewType == VIEW_TYPE_SHADOW || viewType == VIEW_TYPE_BUTTON) {
                if (!TextUtils.equals(text, other.text)) {
                    return false;
                }
            }
            if (viewType == VIEW_TYPE_HEADER) {
                return newSpan == other.newSpan;
            }
            if (viewType == VIEW_TYPE_CHAT) {
                return (
                    did == other.did &&
                    TextUtils.equals(chatType, other.chatType) &&
                    flags == other.flags
                );
            }
            if (viewType == VIEW_TYPE_LINK) {
                return (
                    link == other.link ||
                    TextUtils.equals(link.url, other.link.url) &&
                    link.revoked == other.link.revoked &&
                    TextUtils.equals(link.title, other.link.title) &&
                    link.peers.size() == other.link.peers.size()
                );
            }
            return true;
        }
    }

    private class ListAdapter extends AdapterWithDiffUtils {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return (
                type != VIEW_TYPE_SHADOW &&
                type != VIEW_TYPE_HEADER &&
                type != VIEW_TYPE_EDIT &&
                type != VIEW_TYPE_HINT
            );
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext, 22);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHAT: {
                    UserCell cell = new UserCell(mContext, 6, 0, false);
                    cell.setSelfAsSavedMessages(true);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                    break;
                }
                case VIEW_TYPE_EDIT: {
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
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case VIEW_TYPE_BUTTON:
                    view = new ButtonCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HINT:
                    view = new HintInnerCell(mContext);
                    break;
                case VIEW_TYPE_LINK:
                    view = new LinkCell(mContext, FilterCreateActivity.this, currentAccount, filter.id) {
                        @Override
                        protected void onDelete(TLRPC.TL_exportedChatlistInvite invite) {
                            FilterCreateActivity.this.onDelete(invite);
                        }

                        @Override
                        protected void reload() {
                            FilterCreateActivity.this.loadInvites();
                        }
                    };
                    break;
                case VIEW_TYPE_CREATE_LINK:
                    view = new CreateLinkCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SHADOW_TEXT:
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
            ItemInner item = items.get(position);
            if (item == null) {
                return;
            }
            boolean divider = position + 1 < items.size() && !items.get(position + 1).isShadow();
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (item.newSpan) {
                        headerCell.setText(withNew(0, item.text, false));
                    } else {
                        headerCell.setText(item.text);
                    }
                    break;
                }
                case VIEW_TYPE_CHAT: {
                    UserCell userCell = (UserCell) holder.itemView;
                    if (item.chatType != null) {
                        userCell.setData(item.chatType, item.text, null, 0, divider);
                        return;
                    }
                    long id = item.did;
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
                                if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                                    status = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                                } else {
                                    status = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                                }
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
                case VIEW_TYPE_SHADOW: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, divider ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case VIEW_TYPE_BUTTON: {
                    ButtonCell buttonCell = (ButtonCell) holder.itemView;
                    buttonCell.setRed(item.isRed);
                    buttonCell.set(item.iconResId, item.text, divider);
                    break;
                }
                case VIEW_TYPE_SHADOW_TEXT: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(item.text);
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, divider ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case VIEW_TYPE_LINK: {
                    LinkCell linkCell = (LinkCell) holder.itemView;
                    linkCell.setInvite(item.link, divider);
                    break;
                }
                case VIEW_TYPE_CREATE_LINK: {
                    createLinkCell = (CreateLinkCell) holder.itemView;
                    createLinkCell.setDivider(divider);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            ItemInner item = items.get(position);
            if (item == null) {
                return VIEW_TYPE_SHADOW;
            }
            return item.viewType;
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
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));
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

    private static class ButtonCell extends FrameLayout {
        private ImageView imageView;
        private TextView textView;
        public ButtonCell(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 24, 0, 24, 0));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setSingleLine();
            textView.setPadding(LocaleController.isRTL ? 24 : 0, 0, LocaleController.isRTL ? 0 : 24, 0);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 0 : 72, 0, LocaleController.isRTL ? 72 : 0, 0));
        }

        public void setRed(boolean red) {
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(red ? Theme.key_text_RedBold : Theme.key_windowBackgroundWhiteBlueText2), PorterDuff.Mode.MULTIPLY));
            textView.setTextColor(Theme.getColor(red ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlueText4));
        }

        private int lastIconResId;
        public void set(int iconResId, CharSequence text, boolean divider) {
            final int rtl = LocaleController.isRTL ? -1 : 1;

            if (iconResId == 0) {
                imageView.setVisibility(View.GONE);
            } else {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(iconResId);
            }
            if (LocaleController.isRTL) {
                ((MarginLayoutParams) textView.getLayoutParams()).rightMargin = dp(iconResId == 0 ? 24 : 72);
            } else {
                ((MarginLayoutParams) textView.getLayoutParams()).leftMargin = dp(iconResId == 0 ? 24 : 72);
            }
            textView.setText(text);

            boolean translateText = !divider && iconResId != 0;
            if (this.translateText == null || this.translateText != translateText) {
                this.translateText = translateText;
                if (lastIconResId == iconResId) {
                    textView.clearAnimation();
                    textView.animate().translationX(translateText ? dp(-7 * rtl) : 0).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                } else {
                    textView.setTranslationX(translateText ? dp(-7 * rtl) : 0);
                }
            }
            setWillNotDraw(!(this.divider = divider));
            lastIconResId = iconResId;
        }

        private boolean divider = true;
        private Boolean translateText = null;

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (divider) {
                canvas.drawRect(textView.getLeft(), getMeasuredHeight() - 1, textView.getRight(), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }
    }

    private static class CreateLinkCell extends FrameLayout {
        TextView textView;
        ImageView imageView;
        public CreateLinkCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setText(LocaleController.getString("CreateNewLink", R.string.CreateNewLink));
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setPadding(LocaleController.isRTL ? 16 : 0, 0, LocaleController.isRTL ? 0 : 16, 0);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 0 : 64, 0, LocaleController.isRTL ? 64 : 0, 0));

            imageView = new ImageView(context);
            Drawable drawable1 = context.getResources().getDrawable(R.drawable.poll_add_circle);
            Drawable drawable2 = context.getResources().getDrawable(R.drawable.poll_add_plus);
            drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.MULTIPLY));
            drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
            imageView.setImageDrawable(new CombinedDrawable(drawable1, drawable2));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 0, LocaleController.isRTL ? 16 : 0, 0));
        }

        public void setText(String text) {
            textView.setText(text);
        }

        boolean needDivider;

        public void setDivider(boolean divider) {
            if (needDivider != divider) {
                setWillNotDraw(!(needDivider = divider));
            }
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            textView.setAlpha(enabled ? 1f : 0.5f);
            imageView.setAlpha(enabled ? 1f : 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (needDivider) {
                canvas.drawRect(textView.getLeft(), getMeasuredHeight() - 1, textView.getRight(), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(45), MeasureSpec.EXACTLY));
        }
    }

    private static class LinkCell extends FrameLayout {

        private BaseFragment fragment;
        private int currentAccount;
        private int filterId;

        Drawable linkIcon, revokedLinkIcon;
        AnimatedTextView titleTextView;
        AnimatedTextView subtitleTextView;
        ImageView optionsIcon;
        Paint paint, revokedPaint;

        float revokeT;

        boolean needDivider;

        public LinkCell(Context context, BaseFragment fragment, int currentAccount, int filterId) {
            super(context);

            this.fragment = fragment;
            this.currentAccount = currentAccount;
            this.filterId = filterId;

            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            titleTextView = new AnimatedTextView(context, true, true, false);
            titleTextView.setTextSize(AndroidUtilities.dp(15.66f));
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleTextView.setEllipsizeByGradient(true);
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,  20, Gravity.TOP | Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 56 : 64, 10.33f, LocaleController.isRTL ? 64 : 56, 0));

            subtitleTextView = new AnimatedTextView(context, false, false, false);
            subtitleTextView.setTextSize(AndroidUtilities.dp(13));
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.TOP | Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 56 : 64, 33.33f, LocaleController.isRTL ? 64 : 56, 0));

            optionsIcon = new ImageView(context);
            optionsIcon.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_ab_other));
            optionsIcon.setScaleType(ImageView.ScaleType.CENTER);
            optionsIcon.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
            optionsIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.SRC_IN));
            optionsIcon.setOnClickListener(e -> options());
            optionsIcon.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            addView(optionsIcon, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 8 : 4, 4, LocaleController.isRTL ? 4 : 8, 4));

            paint = new Paint();
            paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            revokedPaint = new Paint();
            revokedPaint.setColor(Theme.getColor(Theme.key_color_red));
            linkIcon = getContext().getResources().getDrawable(R.drawable.msg_link_1).mutate();
            linkIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            revokedLinkIcon = getContext().getResources().getDrawable(R.drawable.msg_link_2).mutate();
            revokedLinkIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));

            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int cx = LocaleController.isRTL ? getMeasuredWidth() - dp(32) : dp(32);

            canvas.drawCircle(cx, getMeasuredHeight() / 2f, dp(16), paint);
            if (revokeT > 0) {
                canvas.drawCircle(cx, getMeasuredHeight() / 2f, dp(16) * revokeT, revokedPaint);
            }

            if (revokeT < 1) {
                linkIcon.setAlpha((int) (0xFF * (1f - revokeT)));
                linkIcon.setBounds(cx - dp(14), getMeasuredHeight() / 2 - dp(14), cx + dp(14), getMeasuredHeight() / 2 + dp(14));
                linkIcon.draw(canvas);
            }
            if (revokeT > 0) {
                revokedLinkIcon.setAlpha((int) (0xFF * revokeT));
                revokedLinkIcon.setBounds(cx - dp(14), getMeasuredHeight() / 2 - dp(14), cx + dp(14), getMeasuredHeight() / 2 + dp(14));
                revokedLinkIcon.draw(canvas);
            }

            if (needDivider) {
                canvas.drawRect(LocaleController.isRTL ? 0 : dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(64) : 0), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        private boolean lastRevoked;
        private ValueAnimator valueAnimator;
        public void setRevoked(boolean value, boolean animated) {
            lastRevoked = value;
            if ((value ? 1 : 0) != revokeT) {
                if (valueAnimator != null) {
                    valueAnimator.cancel();
                    valueAnimator = null;
                }

                if (animated) {
                    valueAnimator = ValueAnimator.ofFloat(revokeT, value ? 1 : 0);
                    valueAnimator.addUpdateListener(anm -> {
                        revokeT = (float) anm.getAnimatedValue();
                        invalidate();
                    });
                    valueAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator anm) {
                            revokeT = value ? 1 : 0;
                            invalidate();
                        }
                    });
                    valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    valueAnimator.setDuration(350);
                    valueAnimator.start();
                } else {
                    revokeT = value ? 1 : 0;
                    invalidate();
                }
            }
        }

        protected String lastUrl;
        private TLRPC.TL_exportedChatlistInvite lastInvite;

        public void setInvite(TLRPC.TL_exportedChatlistInvite invite, boolean divider) {
            boolean animated = lastInvite == invite;
            lastInvite = invite;
            String url = lastUrl = invite.url;
            if (url.startsWith("http://"))
                url = url.substring(7);
            if (url.startsWith("https://"))
                url = url.substring(8);
            if (TextUtils.isEmpty(invite.title)) {
                titleTextView.setText(url, animated);
            } else {
                titleTextView.setText(invite.title, animated);
            }
            subtitleTextView.setText(LocaleController.formatPluralString("FilterInviteChats", invite.peers.size()), animated);
            if (needDivider != divider) {
                needDivider = divider;
                invalidate();
            }
            setRevoked(invite.revoked, animated);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
        }

        public void options() {
            if (fragment == null) {
                return;
            }
            ItemOptions options = ItemOptions.makeOptions(fragment, this);
            options.add(R.drawable.msg_qrcode, LocaleController.getString("GetQRCode", R.string.GetQRCode), this::qrcode);
            options.add(R.drawable.msg_delete, LocaleController.getString("DeleteLink", R.string.DeleteLink), true, this::deleteLink);
            if (LocaleController.isRTL) {
                options.setGravity(Gravity.LEFT);
            }
            options.show();
        }

        private String getSlug() {
            if (lastUrl == null) {
                return null;
            }
            return lastUrl.substring(lastUrl.lastIndexOf('/') + 1);
        }

        private void revoke(boolean revoke) {
            String slug = getSlug();
            if (slug == null) {
                return;
            }

            TLRPC.TL_chatlists_editExportedInvite req = new TLRPC.TL_chatlists_editExportedInvite();
            req.chatlist = new TLRPC.TL_inputChatlistDialogFilter();
            req.chatlist.filter_id = filterId;
            req.revoked = revoke;
            req.slug = getSlug();
            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(180);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                setRevoked(revoke, true);
                if (lastInvite != null) {
                    lastInvite.revoked = revoke;
                }
                progressDialog.dismiss();
            }));
        }

        public void deleteLink() {
            String slug = getSlug();
            if (slug == null) {
                return;
            }

            TLRPC.TL_chatlists_deleteExportedInvite req = new TLRPC.TL_chatlists_deleteExportedInvite();
            req.chatlist = new TLRPC.TL_inputChatlistDialogFilter();
            req.chatlist.filter_id = filterId;
            req.slug = slug;
            Runnable update = () -> onDelete(lastInvite);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (err != null) {
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString("UnknownError", R.string.UnknownError)).show();
                    AndroidUtilities.cancelRunOnUIThread(update);
                }
            }));

            AndroidUtilities.runOnUIThread(update, 150);
        }

        protected void onDelete(TLRPC.TL_exportedChatlistInvite invite) {

        }

        protected void reload() {

        }

        public void qrcode() {
            if (lastUrl == null) {
                return;
            }

            QRCodeBottomSheet qrCodeBottomSheet = new QRCodeBottomSheet(getContext(), LocaleController.getString("InviteByQRCode", R.string.InviteByQRCode), lastUrl, LocaleController.getString("QRCodeLinkHelpFolder", R.string.QRCodeLinkHelpFolder), false);
            qrCodeBottomSheet.setCenterAnimation(R.raw.qr_code_logo);
            qrCodeBottomSheet.show();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setContentDescription(
                (lastInvite != null && !TextUtils.isEmpty(lastInvite.title) ? lastInvite.title + "\n " : "") +
                LocaleController.getString("InviteLink", R.string.InviteLink) + ", " + subtitleTextView.getText() +
                (lastInvite != null && TextUtils.isEmpty(lastInvite.title) ? "\n\n" + lastInvite.url : "")
            );
        }
    }

    public static void hideNew(int type) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("n_" + type, true).apply();
    }

    public static CharSequence withNew(int type, CharSequence string, boolean outline) {
        if (type < 0 || MessagesController.getGlobalMainSettings().getBoolean("n_" + type, false)) {
            return string;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return string;
        }

        SpannableStringBuilder text = new SpannableStringBuilder(string);
        text.append("  ");
        SpannableString newText = new SpannableString("NEW"); // new SpannableString(LocaleController.getString("New", R.string.New));
        if (outline) {
            Drawable drawable = context.getResources().getDrawable(R.drawable.msg_other_new_outline).mutate();
            drawable.setBounds(0, -dp(8), drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight() - dp(8));
            newText.setSpan(new ColorImageSpan(drawable, DynamicDrawableSpan.ALIGN_BOTTOM), 0, newText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            Drawable bg = context.getResources().getDrawable(R.drawable.msg_other_new_filled).mutate();
            Drawable txt = context.getResources().getDrawable(R.drawable.msg_other_new_filled_text).mutate();
            bg.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_unread), PorterDuff.Mode.MULTIPLY));
            txt.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.MULTIPLY));
            Drawable drawable = new CombinedDrawable(bg, txt);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            newText.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_BOTTOM), 0, newText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
//        newText.setSpan(new NewSpan(outline), 0, newText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(newText);
        return text;
    }

    public static class ColorImageSpan extends ImageSpan {
        public ColorImageSpan(Drawable drawable) {
            super(drawable);
        }
        public ColorImageSpan(Drawable drawable, int align) {
            super(drawable, align);
        }

        int lastColor;
        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            if (paint.getColor() != lastColor) {
                if (getDrawable() != null) {
                    getDrawable().setColorFilter(new PorterDuffColorFilter(lastColor = paint.getColor(), PorterDuff.Mode.MULTIPLY));
                }
            }
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
        }
    }

    public static class NewSpan extends ReplacementSpan {

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        StaticLayout layout;
        float width, height;

        private boolean outline;

        public NewSpan(boolean outline) {
            this.outline = outline;

            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            if (outline) {
                bgPaint.setStyle(Paint.Style.STROKE);
                bgPaint.setStrokeWidth(AndroidUtilities.dpf2(1.33f));
                textPaint.setTextSize(dp(10));
                textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                textPaint.setStrokeWidth(AndroidUtilities.dpf2(0.2f));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    textPaint.setLetterSpacing(.03f);
                }
            } else {
                bgPaint.setStyle(Paint.Style.FILL);
                textPaint.setTextSize(dp(12));
            }
        }

        public StaticLayout makeLayout() {
            if (layout == null) {
                layout = new StaticLayout("NEW"/*LocaleController.getString("New", R.string.New)*/, textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
                width = layout.getLineWidth(0);
                height = layout.getHeight();
            }
            return layout;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            makeLayout();
            return (int) (dp(10) + width);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float _x, int top, int _y, int bottom, @NonNull Paint paint) {
            makeLayout();

            int color = paint.getColor();
            bgPaint.setColor(color);
            if (outline) {
                textPaint.setColor(color);
            } else {
                textPaint.setColor(AndroidUtilities.computePerceivedBrightness(color) > .721f ? Color.BLACK : Color.WHITE);
            }

            float x = _x + dp(2), y = _y - height + dp(1);
            AndroidUtilities.rectTmp.set(x, y, x + width, y + height);
            float r;
            if (outline) {
                r = dp(3.66f);
                AndroidUtilities.rectTmp.left -= dp(4);
                AndroidUtilities.rectTmp.top -= dp(2.33f);
                AndroidUtilities.rectTmp.right += dp(3.66f);
                AndroidUtilities.rectTmp.bottom += dp(1.33f);
            } else {
                r = dp(4.4f);
                AndroidUtilities.rectTmp.inset(dp(-4), dp(-2.33f));
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, bgPaint);

            canvas.save();
            canvas.translate(x, y);
            layout.draw(canvas);
            canvas.restore();
        }
    }

    private boolean showedUpdateBulletin;
    private Runnable showBulletinOnResume;

    private void onUpdate(boolean add, int count) {
        if (showedUpdateBulletin) {
            return;
        }
        
        if (filter != null && filter.isChatlist() && filter.isMyChatlist()) {
            showedUpdateBulletin = true;
            showBulletinOnResume = () -> {
                BulletinFactory.of(this).createSimpleBulletin(
                    add ? R.raw.folder_in : R.raw.folder_out,
                    add ?
                        LocaleController.formatPluralString("FolderLinkAddedChats", count) :
                        LocaleController.formatPluralString("FolderLinkRemovedChats", count),
                    LocaleController.getString("FolderLinkChatlistUpdate", R.string.FolderLinkChatlistUpdate)
                ).setDuration(Bulletin.DURATION_PROLONG).show();
            };
            if (getLayoutContainer() != null) {
                showBulletinOnResume.run();
                showBulletinOnResume = null;
            }
        }
    }

    public static class FilterInvitesBottomSheet extends BottomSheetWithRecyclerListView {

        public static void show(BaseFragment fragment, MessagesController.DialogFilter filter, Runnable onLoaded) {
            long start = System.currentTimeMillis();
            TLRPC.TL_chatlists_getExportedInvites req = new TLRPC.TL_chatlists_getExportedInvites();
            req.chatlist = new TLRPC.TL_inputChatlistDialogFilter();
            req.chatlist.filter_id = filter.id;
            fragment.getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (fragment == null || fragment.getContext() == null) {
                    return;
                }
                if (res instanceof TLRPC.TL_chatlists_exportedInvites) {
                    TLRPC.TL_chatlists_exportedInvites invs = (TLRPC.TL_chatlists_exportedInvites) res;
                    fragment.getMessagesController().putChats(invs.chats, false);
                    fragment.getMessagesController().putUsers(invs.users, false);
                    new FilterCreateActivity.FilterInvitesBottomSheet(fragment, filter, ((TLRPC.TL_chatlists_exportedInvites) res).invites).show();
                } else if (err != null && "FILTER_ID_INVALID".equals(err.text) && !filter.isDefault()) {
                    new FilterCreateActivity.FilterInvitesBottomSheet(fragment, filter, null).show();
                } else {
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString("UnknownError", R.string.UnknownError)).show();
                }
                if (onLoaded != null) {
                    AndroidUtilities.runOnUIThread(onLoaded, Math.max(0, 200 - (System.currentTimeMillis() - start)));
                }
            }));
        }

        private MessagesController.DialogFilter filter;
        private ArrayList<TLRPC.TL_exportedChatlistInvite> invites = new ArrayList<>();

        private FrameLayout bulletinContainer;

        private AdapterWithDiffUtils adapter;

        private TextView button;

        public FilterInvitesBottomSheet(BaseFragment fragment, MessagesController.DialogFilter filter, ArrayList<TLRPC.TL_exportedChatlistInvite> loadedInvites) {
            super(fragment, false, false);

            this.filter = filter;

            if (loadedInvites != null) {
                invites.addAll(loadedInvites);
            }
            updateRows(false);

            actionBar.setTitle(getTitle());

            fixNavigationBar(Theme.getColor(Theme.key_dialogBackground));

            button = new TextView(getContext());
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            button.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 8));
            button.setText(LocaleController.getString("FolderLinkShareButton", R.string.FolderLinkShareButton));
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(e -> createLink());
            containerView.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 16, 10, 16, 10));

            bulletinContainer = new FrameLayout(getContext());
            containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM, 6, 0, 6, 0));

            updateCreateInviteButton();
        }

        private void updateCreateInviteButton() {
            button.setVisibility(invites.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerListView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), invites.isEmpty() ? AndroidUtilities.dp(68) : 0);
        }

        @Override
        protected CharSequence getTitle() {
            return LocaleController.formatString("FolderLinkShareTitle", R.string.FolderLinkShareTitle, filter == null ? "" : filter.name);
        }

        private ArrayList<ItemInner> oldItems = new ArrayList<>();
        private ArrayList<ItemInner> items = new ArrayList<>();

        private void updateRows(boolean animated) {
            oldItems.clear();
            oldItems.addAll(items);

            items.clear();

            items.add(ItemInner.asHeader(null));
            if (!invites.isEmpty()) {
                items.add(ItemInner.asShadow(null));
                items.add(ItemInner.asCreateLink());
                for (int i = 0; i < invites.size(); ++i) {
                    items.add(ItemInner.asLink(invites.get(i)));
                }
            }

            if (adapter != null) {
                if (animated) {
                    adapter.setItems(oldItems, items);
                } else {
                    notifyDataSetChanged();
                }
            }
        }

        @Override
        protected RecyclerListView.SelectionAdapter createAdapter() {
            return adapter = new AdapterWithDiffUtils() {

                private RecyclerListView.Adapter realAdapter() {
                    return recyclerListView.getAdapter();
                }

                @Override
                public void notifyItemChanged(int position) {
                    realAdapter().notifyItemChanged(position + 1);
                }

                @Override
                public void notifyItemChanged(int position, @Nullable Object payload) {
                    realAdapter().notifyItemChanged(position + 1, payload);
                }

                @Override
                public void notifyItemInserted(int position) {
                    realAdapter().notifyItemInserted(position + 1);
                }

                @Override
                public void notifyItemMoved(int fromPosition, int toPosition) {
                    realAdapter().notifyItemMoved(fromPosition + 1, toPosition);
                }

                @Override
                public void notifyItemRangeChanged(int positionStart, int itemCount) {
                    realAdapter().notifyItemRangeChanged(positionStart + 1, itemCount);
                }

                @Override
                public void notifyItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                    realAdapter().notifyItemRangeChanged(positionStart + 1, itemCount, payload);
                }

                @Override
                public void notifyItemRangeInserted(int positionStart, int itemCount) {
                    realAdapter().notifyItemRangeInserted(positionStart + 1, itemCount);
                }

                @Override
                public void notifyItemRangeRemoved(int positionStart, int itemCount) {
                    realAdapter().notifyItemRangeRemoved(positionStart + 1, itemCount);
                }

                @Override
                public void notifyItemRemoved(int position) {
                    realAdapter().notifyItemRemoved(position + 1);
                }

                @Override
                public void notifyDataSetChanged() {
                    realAdapter().notifyDataSetChanged();
                }

                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    int viewType = holder.getItemViewType();
                    return viewType == VIEW_TYPE_CREATE_LINK || viewType == VIEW_TYPE_LINK;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view;
                    if (viewType == VIEW_TYPE_CREATE_LINK) {
                        view = new CreateLinkCell(getContext());
                        view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
                    } else if (viewType == VIEW_TYPE_LINK) {
                        view = new LinkCell(getContext(), null, currentAccount, filter.id) {
                            @Override
                            public void options() {
                                ItemOptions options = ItemOptions.makeOptions(container, this);
                                options.add(R.drawable.msg_copy, LocaleController.getString("CopyLink", R.string.CopyLink), this::copy);
                                options.add(R.drawable.msg_qrcode, LocaleController.getString("GetQRCode", R.string.GetQRCode), this::qrcode);
                                options.add(R.drawable.msg_delete, LocaleController.getString("DeleteLink", R.string.DeleteLink), true, this::deleteLink);
                                if (LocaleController.isRTL) {
                                    options.setGravity(Gravity.LEFT);
                                }
                                options.show();
                            }

                            public void copy() {
                                if (lastUrl == null) {
                                    return;
                                }

                                if (AndroidUtilities.addToClipboard(lastUrl)) {
                                    BulletinFactory.of(bulletinContainer, null).createCopyLinkBulletin().show();
                                }
                            }

                            @Override
                            protected void onDelete(TLRPC.TL_exportedChatlistInvite invite) {
                                invites.remove(invite);
                                updateCreateInviteButton();
                                updateRows(true);
                            }
                        };
                        view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
                    } else if (viewType == VIEW_TYPE_SHADOW_TEXT || viewType == VIEW_TYPE_SHADOW) {
                        view = new TextInfoPrivacyCell(getContext());
                        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    } else {
                        view = new HeaderView(getContext());
//                        TextView textView = new TextView(getContext());
//                        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
//                        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
//                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
//                        textView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(16), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
//                        view = textView;
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public int getItemViewType(int position) {
                    return items.get(position).viewType;
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    int viewType = holder.getItemViewType();
                    ItemInner item = items.get(position);
                    boolean divider = position + 1 < items.size() && !items.get(position + 1).isShadow();
                    if (viewType == VIEW_TYPE_LINK) {
                        LinkCell linkCell = (LinkCell) holder.itemView;
                        linkCell.setInvite(item.link, divider);
                    } else if (viewType == VIEW_TYPE_SHADOW_TEXT || viewType == VIEW_TYPE_SHADOW) {
                        TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                        if (viewType == VIEW_TYPE_SHADOW_TEXT) {
                            cell.setFixedSize(0);
                            cell.setText(item.text);
                        } else {
                            cell.setFixedSize(12);
                            cell.setText("");
                        }
                        cell.setForeground(Theme.getThemedDrawable(getContext(), divider ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (viewType == VIEW_TYPE_HEADER) {
//                        HeaderView headerV = (HeaderView) holder.itemView;
//                        textView.setText(item.text);
                    } else if (viewType == VIEW_TYPE_CREATE_LINK) {
                        CreateLinkCell createLinkCell = (CreateLinkCell) holder.itemView;
                        createLinkCell.setText(LocaleController.getString("CreateNewInviteLink", R.string.CreateNewInviteLink));
                        createLinkCell.setDivider(divider);
                    }
                }

                @Override
                public int getItemCount() {
                    return items.size();
                }
            };
        }

        private class HeaderView extends FrameLayout {

            private final ImageView imageView;
            private final TextView titleView;
            private final TextView subtitleView;
            private final ImageView closeImageView;

            public HeaderView(Context context) {
                super(context);

                imageView = new ImageView(context);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setImageResource(R.drawable.msg_limit_links);
                imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                imageView.setBackground(Theme.createRoundRectDrawable(dp(22), Theme.getColor(Theme.key_featuredStickers_addButton)));
                addView(imageView, LayoutHelper.createFrame(54, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 22, 0, 0));

                titleView = new TextView(context);
                titleView.setText(getTitle());
                titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                titleView.setGravity(Gravity.CENTER_HORIZONTAL);
                addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 84, 20, 0));

                subtitleView = new TextView(context);
                subtitleView.setText(invites.isEmpty() ?
                    LocaleController.getString("FolderLinkShareSubtitleEmpty", R.string.FolderLinkShareSubtitleEmpty) :
                    LocaleController.getString("FolderLinkShareSubtitle", R.string.FolderLinkShareSubtitle)
                );
                subtitleView.setLines(2);
                subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 30, 117, 30, 0));

                closeImageView = new ImageView(context);
                closeImageView.setScaleType(ImageView.ScaleType.CENTER);
                closeImageView.setImageResource(R.drawable.msg_close);
                closeImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5), PorterDuff.Mode.MULTIPLY));
                closeImageView.setOnClickListener(e -> dismiss());
                addView(closeImageView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, -4, 2, 0));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(171), MeasureSpec.EXACTLY)
                );
            }
        }

        private void createLink() {
            ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();
            for (int i = 0; i < filter.alwaysShow.size(); ++i) {
                long did = filter.alwaysShow.get(i);
                if (did < 0) {
                    TLRPC.Chat chat = getBaseFragment().getMessagesController().getChat(-did);
                    if (canAddToFolder(chat)) {
                        peers.add(getBaseFragment().getMessagesController().getInputPeer(did));
                    }
                }
            }

            if (peers.isEmpty()) {
                dismiss();
                getBaseFragment().presentFragment(new FilterChatlistActivity(filter, null));
            } else {
                TLRPC.TL_chatlists_exportChatlistInvite req = new TLRPC.TL_chatlists_exportChatlistInvite();
                req.chatlist = new TLRPC.TL_inputChatlistDialogFilter();
                req.chatlist.filter_id = filter.id;
                req.peers = peers;
                req.title = "";
                getBaseFragment().getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (
                        processErrors(err, getBaseFragment(), BulletinFactory.of(bulletinContainer, null)) &&
                        res instanceof TLRPC.TL_chatlists_exportedChatlistInvite
                    ) {
                        FilterCreateActivity.hideNew(0);
                        dismiss();

                        getBaseFragment().getMessagesController().loadRemoteFilters(true);
                        TLRPC.TL_chatlists_exportedChatlistInvite inv = (TLRPC.TL_chatlists_exportedChatlistInvite) res;
                        getBaseFragment().presentFragment(new FilterChatlistActivity(filter, inv.invite));
                    }
                }));
            }
        }

        @Override
        public void onViewCreated(FrameLayout containerView) {
            super.onViewCreated(containerView);
            recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            recyclerListView.setOnItemClickListener((view, position) -> {
                position--;
                if (position < 0 || position >= items.size()) {
                    return;
                }
                ItemInner item = items.get(position);
                if (item.viewType == VIEW_TYPE_LINK) {
                    dismiss();
                    getBaseFragment().presentFragment(new FilterChatlistActivity(filter, item.link));
                } else if (item.viewType == VIEW_TYPE_CREATE_LINK) {
                    createLink();
                }
            });

            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            recyclerListView.setItemAnimator(itemAnimator);
        }
    }

    public static boolean processErrors(TLRPC.TL_error err, BaseFragment fragment, BulletinFactory factory) {
        if (err == null || TextUtils.isEmpty(err.text)) {
            return true;
        }
        if ("INVITE_PEERS_TOO_MUCH".equals(err.text)) {
            new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, fragment.getCurrentAccount()).show();
        } else if ("PEERS_LIST_EMPTY".equals(err.text)) {
            factory.createErrorBulletin(LocaleController.getString("FolderLinkNoChatsError", R.string.FolderLinkNoChatsError)).show();
        } else if ("USER_CHANNELS_TOO_MUCH".equals(err.text)) {
            factory.createErrorBulletin(LocaleController.getString("FolderLinkOtherAdminLimitError", R.string.FolderLinkOtherAdminLimitError)).show();
        } else if ("CHANNELS_TOO_MUCH".equals(err.text)) {
            new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_TO0_MANY_COMMUNITIES, fragment.getCurrentAccount()).show();
        } else if ("INVITES_TOO_MUCH".equals(err.text)) {
            new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_FOLDER_INVITES, fragment.getCurrentAccount()).show();
        } else if ("CHATLISTS_TOO_MUCH".equals(err.text)) {
            new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_SHARED_FOLDERS, fragment.getCurrentAccount()).show();
        } else if ("INVITE_SLUG_EXPIRED".equals(err.text)) {
            factory.createErrorBulletin(LocaleController.getString("NoFolderFound", R.string.NoFolderFound)).show();
        } else if ("FILTER_INCLUDE_TOO_MUCH".equals(err.text)) {
            new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, fragment.getCurrentAccount()).show();
        } else if ("DIALOG_FILTERS_TOO_MUCH".equals(err.text)) {
            new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_FOLDERS, fragment.getCurrentAccount()).show();
        } else {
            factory.createErrorBulletin(LocaleController.getString("UnknownError", R.string.UnknownError)).show();
        }
        return true;
    }
}

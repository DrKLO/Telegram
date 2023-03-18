/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.FeaturedStickerSetCell2;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReorderingBulletinLayout;
import org.telegram.ui.Components.ReorderingHintDrawable;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.TrendingStickersAlert;
import org.telegram.ui.Components.TrendingStickersLayout;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_ARCHIVE = 0;
    private static final int MENU_DELETE = 1;
    private static final int MENU_SHARE = 2;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private DefaultItemAnimator itemAnimator;
    private ItemTouchHelper itemTouchHelper;
    private NumberTextView selectedCountTextView;
    private TrendingStickersAlert trendingStickersAlert;

    private ActionBarMenuItem archiveMenuItem;
    private ActionBarMenuItem deleteMenuItem;
    private ActionBarMenuItem shareMenuItem;

    private int activeReorderingRequests;
    private boolean needReorder;
    private int currentType;

    private int dynamicPackOrder;
    private int dynamicPackOrderInfo;
    private int suggestRow;
    private int suggestAnimatedEmojiRow;
    private int suggestAnimatedEmojiInfoRow;
    private int loopRow;
    private int loopInfoRow;
    private int largeEmojiRow;
    private int reactionsDoubleTapRow;
    private int stickersBotInfo;
    private int featuredRow;
    private int masksRow;
    private int emojiPacksRow;
    private int masksInfoRow;
    private int archivedRow;
    private int archivedInfoRow;

    private int featuredStickersHeaderRow;
    private int featuredStickersStartRow;
    private int featuredStickersEndRow;
    private int featuredStickersShowMoreRow;
    private int featuredStickersShadowRow;
    private int stickersSettingsRow;

    private int stickersHeaderRow;
    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersShadowRow;
    private int rowCount;

    private boolean updateSuggestStickers;

    private boolean isListeningForFeaturedUpdate;
    ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks;

    private ArrayList<TLRPC.TL_messages_stickerSet> emojiPacks;
    private List<TLRPC.StickerSetCovered> getFeaturedSets() {
        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        List<TLRPC.StickerSetCovered> featuredStickerSets;
        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            featuredStickerSets = new ArrayList<>(mediaDataController.getFeaturedEmojiSets());
            for (int i = 0; i < featuredStickerSets.size(); ++i) {
                if (featuredStickerSets.get(i) == null || mediaDataController.isStickerPackInstalled(featuredStickerSets.get(i).set.id, false)) {
                    featuredStickerSets.remove(i--);
                }
            }
        } else {
            featuredStickerSets = mediaDataController.getFeaturedStickerSets();
        }
        return featuredStickerSets;
    }

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return listAdapter.hasSelected();
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != ListAdapter.TYPE_STICKER_SET) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            listAdapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                sendReorder();
            } else {
                listView.cancelClickRunnables(false);
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

    public StickersActivity(int type, ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks) {
        super();
        currentType = type;
        this.frozenEmojiPacks = frozenEmojiPacks;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        MediaDataController.getInstance(currentAccount).checkStickers(currentType);
        if (currentType == MediaDataController.TYPE_IMAGE) {
            MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_MASK);
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
        } else if (currentType == MediaDataController.TYPE_FEATURED_EMOJIPACKS) {
            MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredEmojiDidLoad);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.archivedStickersCountDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        updateRows(false);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (currentType == MediaDataController.TYPE_FEATURED_EMOJIPACKS) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredEmojiDidLoad);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.archivedStickersCountDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        if (currentType == MediaDataController.TYPE_IMAGE) {
            actionBar.setTitle(LocaleController.getString("StickersName", R.string.StickersName));
        } else if (currentType == MediaDataController.TYPE_MASK) {
            actionBar.setTitle(LocaleController.getString("Masks", R.string.Masks));
        } else if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            actionBar.setTitle(LocaleController.getString("Emoji", R.string.Emoji));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                } else if (id == MENU_ARCHIVE || id == MENU_DELETE || id == MENU_SHARE) {
                    if (!needReorder) {
                        if (activeReorderingRequests == 0) {
                            listAdapter.processSelectionMenu(id);
                        }
                    } else {
                        sendReorder();
                    }
                }
            }
        });


        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedCountTextView.setOnTouchListener((v, event) -> true);

        shareMenuItem = actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        if (currentType != MediaDataController.TYPE_EMOJIPACKS) {
            archiveMenuItem = actionMode.addItemWithWidth(MENU_ARCHIVE, R.drawable.msg_archive, AndroidUtilities.dp(54));
        }
        deleteMenuItem = actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));

        ArrayList<TLRPC.TL_messages_stickerSet> sets;
        if (currentType == MediaDataController.TYPE_EMOJIPACKS && frozenEmojiPacks != null) {
            sets = frozenEmojiPacks;
        } else {
            sets = new ArrayList<>(MessagesController.getInstance(currentAccount).filterPremiumStickers(MediaDataController.getInstance(currentAccount).getStickerSets(currentType)));
        }
        List<TLRPC.StickerSetCovered> featured = getFeaturedSets();
        listAdapter = new ListAdapter(context, sets, featured);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (actionBar.isActionModeShowed()) {
                    drawSectionBackground(canvas, stickersHeaderRow, stickersEndRow, getThemedColor(Theme.key_windowBackgroundWhite));
                }
                super.dispatchDraw(canvas);
            }
        };
        listView.setFocusable(true);
        listView.setTag(7);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                listView.invalidate();
            }
        };
        itemAnimator.setMoveDuration(350);
        itemAnimator.setMoveInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        listView.setItemAnimator(itemAnimator);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
                extraLayoutSpace[1] = listView.getHeight();
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        itemAnimator = (DefaultItemAnimator) listView.getItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= featuredStickersStartRow && position < featuredStickersEndRow && getParentActivity() != null) {
                TLRPC.StickerSetCovered setCovered = listAdapter.featuredStickerSets.get(position - featuredStickersStartRow);
                TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                inputStickerSetID.id = setCovered.set.id;
                inputStickerSetID.access_hash = setCovered.set.access_hash;
                if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                    ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>(1);
                    inputStickerSets.add(inputStickerSetID);
                    showDialog(new EmojiPacksAlert(StickersActivity.this, getParentActivity(), getResourceProvider(), inputStickerSets));
                } else {
                    showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, inputStickerSetID, null, null));
                }
            } else if (position == featuredStickersShowMoreRow || position == featuredRow) {
                if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                    ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>();
                    List<TLRPC.StickerSetCovered> featuredStickerSets = getFeaturedSets();
                    if (featuredStickerSets != null) {
                        for (int i = 0; featuredStickerSets != null && i < featuredStickerSets.size(); ++i) {
                            TLRPC.StickerSetCovered set = featuredStickerSets.get(i);
                            if (set != null && set.set != null) {
                                TLRPC.TL_inputStickerSetID inputStickerSet = new TLRPC.TL_inputStickerSetID();
                                inputStickerSet.id = set.set.id;
                                inputStickerSet.access_hash = set.set.access_hash;
                                inputStickerSets.add(inputStickerSet);
                            }
                        }
                    }
                    MediaDataController.getInstance(currentAccount).markFeaturedStickersAsRead(true, true);
                    showDialog(new EmojiPacksAlert(StickersActivity.this, getParentActivity(), getResourceProvider(), inputStickerSets));
                } else {
                    TrendingStickersLayout.Delegate trendingDelegate = new TrendingStickersLayout.Delegate() {
                        @Override
                        public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary) {
                            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, 2, StickersActivity.this, false, false);
                        }

                        @Override
                        public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, 0, StickersActivity.this, false, false);
                        }
                    };
                    trendingStickersAlert = new TrendingStickersAlert(context, this, new TrendingStickersLayout(context, trendingDelegate), null);
                    trendingStickersAlert.show();
                }
            } else if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                if (!listAdapter.hasSelected()) {
                    TLRPC.TL_messages_stickerSet stickerSet = listAdapter.stickerSets.get(position - stickersStartRow);
                    ArrayList<TLRPC.Document> stickers = stickerSet.documents;
                    if (stickers == null || stickers.isEmpty()) {
                        return;
                    }
                    if (stickerSet.set != null && stickerSet.set.emojis) {
                        ArrayList<TLRPC.InputStickerSet> inputs = new ArrayList<>();
                        TLRPC.TL_inputStickerSetID inputId = new TLRPC.TL_inputStickerSetID();
                        inputId.id = stickerSet.set.id;
                        inputId.access_hash = stickerSet.set.access_hash;
                        inputs.add(inputId);
                        showDialog(new EmojiPacksAlert(StickersActivity.this, getParentActivity(), getResourceProvider(), inputs));
                    } else {
                        showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, null, stickerSet, null));
                    }
                } else {
                    listAdapter.toggleSelected(position);
                }
            } else if (position == archivedRow) {
                presentFragment(new ArchivedStickersActivity(currentType));
            } else if (position == masksRow) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_MASK, null));
            } else if (position == emojiPacksRow) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_EMOJIPACKS, null));
            } else if (position == suggestRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SuggestStickers", R.string.SuggestStickers));
                String[] items = new String[]{
                        LocaleController.getString("SuggestStickersAll", R.string.SuggestStickersAll),
                        LocaleController.getString("SuggestStickersInstalled", R.string.SuggestStickersInstalled),
                        LocaleController.getString("SuggestStickersNone", R.string.SuggestStickersNone),
                };

                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                for (int a = 0; a < items.length; a++) {
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                    cell.setTag(a);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[a], SharedConfig.suggestStickers == a);
                    cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        Integer which = (Integer) v.getTag();
                        SharedConfig.setSuggestStickers(which);
                        updateSuggestStickers = true;
                        listAdapter.notifyItemChanged(suggestRow);
                        builder.getDismissRunnable().run();
                    });
                }
                showDialog(builder.create());
            } else if (position == loopRow) {
                SharedConfig.toggleLoopStickers();
                listAdapter.notifyItemChanged(loopRow, ListAdapter.UPDATE_LOOP_STICKERS);
            } else if (position == largeEmojiRow) {
                SharedConfig.toggleBigEmoji();
                ((TextCheckCell) view).setChecked(SharedConfig.allowBigEmoji);
            } else if (position == suggestAnimatedEmojiRow) {
                SharedConfig.toggleSuggestAnimatedEmoji();
                ((TextCheckCell) view).setChecked(SharedConfig.suggestAnimatedEmoji);
            } else if (position == reactionsDoubleTapRow) {
                presentFragment(new ReactionsDoubleTapManageActivity());
            } else if (position == dynamicPackOrder) {
                SharedConfig.toggleUpdateStickersOrderOnSend();
                ((TextCheckCell) view).setChecked(SharedConfig.updateStickersOrderOnSend);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (!listAdapter.hasSelected() && position >= stickersStartRow && position < stickersEndRow) {
                listAdapter.toggleSelected(position);
                return true;
            } else {
                return false;
            }
        });

        return fragmentView;
    }


    @Override
    public boolean onBackPressed() {
        if (listAdapter.hasSelected()) {
            listAdapter.clearSelected();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            int type = (int) args[0];
            if (type == currentType) {
                listAdapter.loadingFeaturedStickerSets.clear();
                updateRows((Boolean) args[1]);
            } else if (currentType == MediaDataController.TYPE_IMAGE && type == MediaDataController.TYPE_MASK) {
                listAdapter.notifyItemChanged(masksRow);
            }
        } else if (id == NotificationCenter.featuredStickersDidLoad || id == NotificationCenter.featuredEmojiDidLoad) {
            updateRows(false);
        } else if (id == NotificationCenter.archivedStickersCountDidLoad) {
            if ((Integer) args[0] == currentType) {
                updateRows(false);
            }
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {

        }
    }

    private void sendReorder() {
        if (!needReorder) {
            return;
        }
        MediaDataController.getInstance(currentAccount).calcNewHash(currentType);
        needReorder = false;
        activeReorderingRequests++;
        TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
        req.masks = currentType == MediaDataController.TYPE_MASK;
        req.emojis = currentType == MediaDataController.TYPE_EMOJIPACKS;
        for (int a = 0; a < listAdapter.stickerSets.size(); a++) {
            req.order.add(listAdapter.stickerSets.get(a).set.id);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> activeReorderingRequests--));
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoad, currentType, true);

        if (SharedConfig.updateStickersOrderOnSend && dynamicPackOrder != -1) {
            SharedConfig.toggleUpdateStickersOrderOnSend();
            BulletinFactory.of(StickersActivity.this).createSimpleBulletin(R.raw.filter_reorder, LocaleController.getString("DynamicPackOrderOff", R.string.DynamicPackOrderOff), LocaleController.getString("DynamicPackOrderOffInfo", R.string.DynamicPackOrderOffInfo)).show();

            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                int position = listView.getChildAdapterPosition(child);
                if (position == dynamicPackOrder && child instanceof TextCheckCell) {
                    ((TextCheckCell) child).setChecked(SharedConfig.updateStickersOrderOnSend);
                    break;
                }
            }
        }
    }

    private void updateRows(boolean updateEmojipacks) {
        MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        List<TLRPC.TL_messages_stickerSet> newList;
        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            if (updateEmojipacks || frozenEmojiPacks == null) {
                frozenEmojiPacks = new ArrayList<>(MessagesController.getInstance(currentAccount).filterPremiumStickers(mediaDataController.getStickerSets(currentType)));
            }
            newList = frozenEmojiPacks;
        } else {
            newList = new ArrayList<>(MessagesController.getInstance(currentAccount).filterPremiumStickers(mediaDataController.getStickerSets(currentType)));
        }


        boolean truncatedFeaturedStickers = false;
        List<TLRPC.StickerSetCovered> featuredStickerSets = getFeaturedSets();
        if (featuredStickerSets.size() > 3) {
            featuredStickerSets = featuredStickerSets.subList(0, 3);
            truncatedFeaturedStickers = true;
        }
        List<TLRPC.StickerSetCovered> featuredStickersList = new ArrayList<>(featuredStickerSets);
        DiffUtil.DiffResult diffResult = null;
        DiffUtil.DiffResult featuredDiffResult = null;

        boolean hasUsefulPacks = false;
        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            if (UserConfig.getInstance(currentAccount).isPremium()) {
                hasUsefulPacks = true;
            }
            if (!hasUsefulPacks) {
                for (int i = 0; i < newList.size(); ++i) {
                    if (!MessageObject.isPremiumEmojiPack(newList.get(i))) {
                        hasUsefulPacks = true;
                        break;
                    }
                }
            }
            if (!hasUsefulPacks) {
                for (int i = 0; i < featuredStickerSets.size(); ++i) {
                    if (!MessageObject.isPremiumEmojiPack(featuredStickerSets.get(i))) {
                        hasUsefulPacks = true;
                        break;
                    }
                }
            }
        }

        if (listAdapter != null) {
            if (!isPaused) {
                diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {

                    List<TLRPC.TL_messages_stickerSet> oldList = listAdapter.stickerSets;

                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return oldList.get(oldItemPosition).set.id == newList.get(newItemPosition).set.id;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        TLRPC.StickerSet oldSet = oldList.get(oldItemPosition).set;
                        TLRPC.StickerSet newSet = newList.get(newItemPosition).set;
                        return TextUtils.equals(oldSet.title, newSet.title) && oldSet.count == newSet.count;
                    }
                });
                featuredDiffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    List<TLRPC.StickerSetCovered> oldList = listAdapter.featuredStickerSets;

                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return featuredStickersList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return oldList.get(oldItemPosition).set.id == featuredStickersList.get(newItemPosition).set.id;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        TLRPC.StickerSet oldSet = oldList.get(oldItemPosition).set;
                        TLRPC.StickerSet newSet = featuredStickersList.get(newItemPosition).set;
                        return TextUtils.equals(oldSet.title, newSet.title) && oldSet.count == newSet.count && oldSet.installed == newSet.installed;
                    }
                });
            }
            listAdapter.setStickerSets(newList);
            listAdapter.setFeaturedStickerSets(featuredStickersList);
        }

        rowCount = 0;

        loopRow = -1;
        loopInfoRow = -1;

        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            suggestAnimatedEmojiRow = rowCount++;
            suggestAnimatedEmojiInfoRow = rowCount++;
        } else {
            suggestAnimatedEmojiRow = -1;
            suggestAnimatedEmojiInfoRow = -1;
        }

        if (currentType == MediaDataController.TYPE_IMAGE) {
            featuredRow = rowCount++;
            masksRow = -1;
            if (mediaDataController.getArchivedStickersCount(currentType) != 0) {
                boolean inserted = archivedRow == -1;
                archivedRow = rowCount++;
                if (listAdapter != null && inserted) {
                    listAdapter.notifyItemRangeInserted(archivedRow, 1);
                }
            }
            archivedInfoRow = -1;
            emojiPacksRow = rowCount++;
        } else {
            featuredRow = -1;
            masksRow = -1;
            emojiPacksRow = -1;

            if (mediaDataController.getArchivedStickersCount(currentType) != 0 && currentType != MediaDataController.TYPE_EMOJIPACKS) {
                boolean inserted = archivedRow == -1;

                archivedRow = rowCount++;
                archivedInfoRow = currentType == MediaDataController.TYPE_MASK ? rowCount++ : -1;

                if (listAdapter != null && inserted) {
                    listAdapter.notifyItemRangeInserted(archivedRow, archivedInfoRow != -1 ? 2 : 1);
                }
            } else {
                int oldArchivedRow = archivedRow;
                int oldArchivedInfoRow = archivedInfoRow;

                archivedRow = -1;
                archivedInfoRow = -1;

                if (listAdapter != null && oldArchivedRow != -1) {
                    listAdapter.notifyItemRangeRemoved(oldArchivedRow, oldArchivedInfoRow != -1 ? 2 : 1);
                }
            }
        }

        if (currentType == MediaDataController.TYPE_IMAGE) {
            reactionsDoubleTapRow = rowCount++;
        } else {
            reactionsDoubleTapRow = -1;
        }

        stickersBotInfo = -1;
        if (currentType == MediaDataController.TYPE_IMAGE) {
            stickersBotInfo = rowCount++;
        }

        featuredStickersHeaderRow = -1;
        featuredStickersStartRow = -1;
        featuredStickersEndRow = -1;
        featuredStickersShowMoreRow = -1;
        featuredStickersShadowRow = -1;
//        if (!featuredStickersList.isEmpty() && (currentType == MediaDataController.TYPE_IMAGE)) {
//            featuredStickersHeaderRow = rowCount++;
//            featuredStickersStartRow = rowCount;
//            rowCount += featuredStickersList.size();
//            featuredStickersEndRow = rowCount;
//
//            if (truncatedFeaturedStickers) {
//                featuredStickersShowMoreRow = rowCount++;
//            }
//            featuredStickersShadowRow = rowCount++;
//        }

        if (currentType == MediaDataController.TYPE_IMAGE) {
            stickersSettingsRow = rowCount++;
            suggestRow = rowCount++;
            largeEmojiRow = rowCount++;
            dynamicPackOrder = rowCount++;
            dynamicPackOrderInfo = rowCount++;
        } else {
            stickersSettingsRow = -1;
            suggestRow = -1;
            largeEmojiRow = -1;
            dynamicPackOrder = -1;
            dynamicPackOrderInfo = -1;
        }

        int stickerSetsCount = newList.size();
        if (stickerSetsCount > 0) {
            if (currentType == MediaDataController.TYPE_EMOJIPACKS || !featuredStickersList.isEmpty() && currentType == MediaDataController.TYPE_IMAGE) {
                stickersHeaderRow = rowCount++;
            } else {
                stickersHeaderRow = -1;
            }

            stickersStartRow = rowCount;
            rowCount += stickerSetsCount;
            stickersEndRow = rowCount;

            if (currentType != MediaDataController.TYPE_MASK && currentType != MediaDataController.TYPE_EMOJIPACKS) {
                stickersShadowRow = rowCount++;
                masksInfoRow = -1;
            } else if (currentType == MediaDataController.TYPE_MASK) {
                masksInfoRow = rowCount++;
                stickersShadowRow = -1;
            } else {
                stickersShadowRow = -1;
                masksInfoRow = -1;
            }
        } else {
            stickersHeaderRow = -1;
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersShadowRow = -1;
            masksInfoRow = -1;
        }

        if (!featuredStickersList.isEmpty() && (currentType == MediaDataController.TYPE_EMOJIPACKS)) {
            if (stickerSetsCount > 0) {
                featuredStickersShadowRow = rowCount++;
            }

            featuredStickersHeaderRow = rowCount++;
            featuredStickersStartRow = rowCount;
            rowCount += featuredStickersList.size();
            featuredStickersEndRow = rowCount;

            if (truncatedFeaturedStickers) {
                featuredStickersShowMoreRow = rowCount++;
            }
        }

        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            stickersBotInfo = rowCount++;
        }

        if (listAdapter != null) {
            if (diffResult != null) {
                int startRow = stickersStartRow >= 0 ? stickersStartRow : rowCount;
                listAdapter.notifyItemRangeChanged(0, startRow);
                diffResult.dispatchUpdatesTo(new ListUpdateCallback() {
                    @Override
                    public void onInserted(int position, int count) {
                        listAdapter.notifyItemRangeInserted(startRow + position, count);
                    }

                    @Override
                    public void onRemoved(int position, int count) {
                        listAdapter.notifyItemRangeRemoved(startRow + position, count);
                    }

                    @Override
                    public void onMoved(int fromPosition, int toPosition) {
                        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                            listAdapter.notifyItemMoved(startRow + fromPosition, startRow + toPosition);
                        }
                    }

                    @Override
                    public void onChanged(int position, int count, @Nullable Object payload) {
                        listAdapter.notifyItemRangeChanged(startRow + position, count);
                    }
                });
            }
            if (featuredDiffResult != null) {
                int startRow = featuredStickersStartRow >= 0 ? featuredStickersStartRow : rowCount;
                listAdapter.notifyItemRangeChanged(0, startRow);
                featuredDiffResult.dispatchUpdatesTo(new ListUpdateCallback() {
                    @Override
                    public void onInserted(int position, int count) {
                        listAdapter.notifyItemRangeInserted(startRow + position, count);
                    }

                    @Override
                    public void onRemoved(int position, int count) {
                        listAdapter.notifyItemRangeRemoved(startRow + position, count);
                    }

                    @Override
                    public void onMoved(int fromPosition, int toPosition) {
                    }

                    @Override
                    public void onChanged(int position, int count, @Nullable Object payload) {
                        listAdapter.notifyItemRangeChanged(startRow + position, count);
                    }
                });
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int TYPE_STICKER_SET = 0,
                TYPE_INFO = 1,
                TYPE_TEXT_AND_VALUE = 2,
                TYPE_SHADOW = 3,
                TYPE_SWITCH = 4,
                TYPE_DOUBLE_TAP_REACTIONS = 5,
                TYPE_HEADER = 6,
                TYPE_FEATURED_STICKER_SET = 7;

        public static final int UPDATE_LOOP_STICKERS = 0;
        public static final int UPDATE_SELECTION = 1;
        public static final int UPDATE_REORDERABLE = 2;
        public static final int UPDATE_DIVIDER = 3;
        public static final int UPDATE_FEATURED_ANIMATED = 4;

        private final LongSparseArray<Boolean> selectedItems = new LongSparseArray<>();
        private final List<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
        private final List<TLRPC.StickerSetCovered> featuredStickerSets = new ArrayList<>();
        private final List<Long> loadingFeaturedStickerSets = new ArrayList<>();

        private Context mContext;

        public ListAdapter(Context context, List<TLRPC.TL_messages_stickerSet> stickerSets, List<TLRPC.StickerSetCovered> featuredStickerSets) {
            mContext = context;
            setStickerSets(stickerSets);
            if (featuredStickerSets.size() > 3) {
                setFeaturedStickerSets(featuredStickerSets.subList(0, 3));
            } else {
                setFeaturedStickerSets(featuredStickerSets);
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();

            if (isListeningForFeaturedUpdate) {
                isListeningForFeaturedUpdate = false;
            }
        }

        public void setStickerSets(List<TLRPC.TL_messages_stickerSet> stickerSets) {
            this.stickerSets.clear();
            this.stickerSets.addAll(stickerSets);
        }

        public void setFeaturedStickerSets(List<TLRPC.StickerSetCovered> featuredStickerSets) {
            this.featuredStickerSets.clear();
            this.featuredStickerSets.addAll(featuredStickerSets);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public long getItemId(int i) {
            if (i >= featuredStickersStartRow && i < featuredStickersEndRow) {
                return featuredStickerSets.get(i - featuredStickersStartRow).set.id;
            } else if (i >= stickersStartRow && i < stickersEndRow) {
                return stickerSets.get(i - stickersStartRow).set.id;
            }
            return i;
        }

        private void processSelectionMenu(int which) {
            if (which == MENU_SHARE) {
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0, size = stickerSets.size(); i < size; i++) {
                    TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(i);
                    if (selectedItems.get(stickerSet.set.id, false)) {
                        if (stringBuilder.length() != 0) {
                            stringBuilder.append("\n");
                        }
                        stringBuilder.append(getLinkForSet(stickerSet));
                    }
                }
                String link = stringBuilder.toString();
                ShareAlert shareAlert = ShareAlert.createShareAlert(fragmentView.getContext(), null, link, false, link, false);
                shareAlert.setDelegate(new ShareAlert.ShareAlertDelegate() {
                    @Override
                    public void didShare() {
                        clearSelected();
                    }

                    @Override
                    public boolean didCopy() {
                        clearSelected();
                        return true;
                    }
                });
                shareAlert.show();
            } else if (which == MENU_ARCHIVE || which == MENU_DELETE) {
                ArrayList<TLRPC.StickerSet> stickerSetList = new ArrayList<>(selectedItems.size());

                for (int i = 0, size = stickerSets.size(); i < size; i++) {
                    TLRPC.StickerSet stickerSet = stickerSets.get(i).set;
                    if (selectedItems.get(stickerSet.id, false)) {
                        stickerSetList.add(stickerSet);
                    }
                }

                int count = stickerSetList.size();

                switch (count) {
                    case 0:
                        break;
                    case 1:
                        for (int i = 0, size = stickerSets.size(); i < size; i++) {
                            TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(i);
                            if (selectedItems.get(stickerSet.set.id, false)) {
                                processSelectionOption(which, stickerSet);
                                break;
                            }
                        }
                        listAdapter.clearSelected();
                        break;
                    default:
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                        String buttonText;
                        if (which == MENU_DELETE) {
                            builder.setTitle(LocaleController.formatString("DeleteStickerSetsAlertTitle", R.string.DeleteStickerSetsAlertTitle, LocaleController.formatPluralString("StickerSets", count)));
                            builder.setMessage(LocaleController.formatString("DeleteStickersAlertMessage", R.string.DeleteStickersAlertMessage, count));
                            buttonText = LocaleController.getString("Delete", R.string.Delete);
                        } else {
                            builder.setTitle(LocaleController.formatString("ArchiveStickerSetsAlertTitle", R.string.ArchiveStickerSetsAlertTitle, LocaleController.formatPluralString("StickerSets", count)));
                            builder.setMessage(LocaleController.formatString("ArchiveStickersAlertMessage", R.string.ArchiveStickersAlertMessage, count));
                            buttonText = LocaleController.getString("Archive", R.string.Archive);
                        }
                        builder.setPositiveButton(buttonText, (dialog, which1) -> {
                            listAdapter.clearSelected();
                            MediaDataController.getInstance(currentAccount).toggleStickerSets(stickerSetList, currentType, which == MENU_DELETE ? 0 : 1, StickersActivity.this, true);
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);

                        AlertDialog dialog = builder.create();
                        showDialog(dialog);
                        if (which == MENU_DELETE) {
                            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                            }
                        }
                        break;
                }
            }
        }

        private void processSelectionOption(int which, TLRPC.TL_messages_stickerSet stickerSet) {
            if (which == MENU_ARCHIVE) {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, !stickerSet.set.archived ? 1 : 2, StickersActivity.this, true, true);
            } else if (which == MENU_DELETE) {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, 0, StickersActivity.this, true, true);
            } else if (which == 2) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, getLinkForSet(stickerSet));
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("StickersShare", R.string.StickersShare)), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (which == 3) {
                try {
                    String link = String.format(Locale.US, "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + (stickerSet.set.emojis ? "addemoji" : "addstickers") + "/%s", stickerSet.set.short_name);
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", link);
                    clipboard.setPrimaryClip(clip);
                    BulletinFactory.createCopyLinkBulletin(StickersActivity.this).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (which == 4) {
                int index = stickerSets.indexOf(stickerSet);
                if (index >= 0) {
                    listAdapter.toggleSelected(stickersStartRow + index);
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == featuredStickersHeaderRow) {
                        headerCell.setText(LocaleController.getString(currentType == MediaDataController.TYPE_EMOJIPACKS ? R.string.FeaturedEmojiPacks : R.string.FeaturedStickers));
                    } else if (position == stickersHeaderRow) {
                        headerCell.setText(LocaleController.getString(currentType == MediaDataController.TYPE_EMOJIPACKS ? R.string.ChooseStickerMyEmojiPacks : R.string.ChooseStickerMyStickerSets));
                    } else if (position == stickersSettingsRow) {
                        headerCell.setText(LocaleController.getString("StickersSettings", R.string.StickersSettings));
                    }
                    break;
                case TYPE_FEATURED_STICKER_SET: {
                    FeaturedStickerSetCell2 stickerSetCell = (FeaturedStickerSetCell2) holder.itemView;
                    int row = position - featuredStickersStartRow;
                    TLRPC.StickerSetCovered setCovered = featuredStickerSets.get(row);
                    boolean animated = isListeningForFeaturedUpdate || stickerSetCell.getStickerSet() != null && stickerSetCell.getStickerSet().set.id == setCovered.set.id;
                    stickerSetCell.setStickersSet(setCovered, true, false, false, animated);
                    stickerSetCell.setDrawProgress(loadingFeaturedStickerSets.contains(setCovered.set.id), animated);

                    stickerSetCell.setAddOnClickListener(v -> {
                        FeaturedStickerSetCell2 cell = (FeaturedStickerSetCell2) v.getParent();
                        TLRPC.StickerSetCovered pack = cell.getStickerSet();
                        if (loadingFeaturedStickerSets.contains(pack.set.id)) {
                            return;
                        }

                        isListeningForFeaturedUpdate = true;
                        loadingFeaturedStickerSets.add(pack.set.id);

                        cell.setDrawProgress(true, true);
                        if (cell.isInstalled()) {
                            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), pack, 0, StickersActivity.this, false, false);
                        } else {
                            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), pack, 2, StickersActivity.this, false, false);
                        }
                    });

                    break;
                }
                case TYPE_STICKER_SET:
                    StickerSetCell stickerSetCell = (StickerSetCell) holder.itemView;
                    int row = position - stickersStartRow;
                    TLRPC.TL_messages_stickerSet set = stickerSets.get(row);
                    boolean sameSet = (stickerSetCell.getStickersSet() == null && set == null) || (stickerSetCell.getStickersSet() != null && set != null && stickerSetCell.getStickersSet().set.id == set.set.id);
                    stickerSetCell.setStickersSet(set, row != stickerSets.size() - 1);
                    stickerSetCell.setChecked(selectedItems.get(getItemId(position), false), false);
                    stickerSetCell.setReorderable(hasSelected(), false);
                    if (set != null && set.set != null && set.set.emojis) {
                        boolean installed = getMediaDataController().isStickerPackInstalled(set.set.id);
                        boolean unlock = !UserConfig.getInstance(currentAccount).isPremium();
                        if (unlock) {
                            boolean premium = false;
                            for (int i = 0; i < set.documents.size(); ++i) {
                                if (!MessageObject.isFreeEmoji(set.documents.get(i))) {
                                    premium = true;
                                    break;
                                }
                            }
                            if (!premium) {
                                unlock = false;
                            }
                        }
                        stickerSetCell.updateButtonState(
                            unlock ? (
                                installed && !set.set.official ? StickerSetCell.BUTTON_STATE_LOCKED_RESTORE : StickerSetCell.BUTTON_STATE_LOCKED
                            ) : (
                                installed ? StickerSetCell.BUTTON_STATE_REMOVE : StickerSetCell.BUTTON_STATE_ADD
                            ),
                            sameSet
                        );
                    }
                    break;
                case TYPE_INFO:
                    TextInfoPrivacyCell infoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    infoPrivacyCell.setFixedSize(0);
                    if (position == stickersBotInfo) {
                        infoPrivacyCell.setText(addStickersBotSpan(
                            currentType == MediaDataController.TYPE_EMOJIPACKS ?
                                LocaleController.getString("EmojiBotInfo", R.string.EmojiBotInfo) :
                                LocaleController.getString("StickersBotInfo", R.string.StickersBotInfo)
                        ));
                    } else if (position == archivedInfoRow) {
                        if (currentType == MediaDataController.TYPE_IMAGE) {
                            infoPrivacyCell.setText(LocaleController.getString("ArchivedStickersInfo", R.string.ArchivedStickersInfo));
                        } else {
                            infoPrivacyCell.setText(LocaleController.getString("ArchivedMasksInfo", R.string.ArchivedMasksInfo));
                        }
                    } else if (position == loopInfoRow) {
//                        infoPrivacyCell.setText(LocaleController.getString("LoopAnimatedStickersInfo", R.string.LoopAnimatedStickersInfo));
                        infoPrivacyCell.setText(null);
                        infoPrivacyCell.setFixedSize(12);
                    } else if (position == suggestAnimatedEmojiInfoRow) {
                        infoPrivacyCell.setText(LocaleController.getString("SuggestAnimatedEmojiInfo", R.string.SuggestAnimatedEmojiInfo));
                    } else if (position == masksInfoRow) {
                        infoPrivacyCell.setText(LocaleController.getString("MasksInfo", R.string.MasksInfo));
                    } else if (position == dynamicPackOrderInfo) {
                        infoPrivacyCell.setText(LocaleController.getString("DynamicPackOrderInfo"));
                    }
                    break;
                case TYPE_TEXT_AND_VALUE: {
                    TextCell settingsCell = (TextCell) holder.itemView;
                    if (position == featuredStickersShowMoreRow) {
                        settingsCell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                        if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                            settingsCell.setTextAndIcon(LocaleController.getString(R.string.ShowMoreEmojiPacks), R.drawable.msg2_trending, false);
                        } else {
                            settingsCell.setTextAndIcon(LocaleController.getString(R.string.ShowMoreStickers), R.drawable.msg2_trending, false);
                        }
                    } else {
                        settingsCell.imageView.setTranslationX(0);
                        settingsCell.textView.setTranslationX(0);
                        settingsCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        if (position == archivedRow) {
                            int count = MediaDataController.getInstance(currentAccount).getArchivedStickersCount(currentType);
                            String value = count > 0 ? Integer.toString(count) : "";
                            if (currentType == MediaDataController.TYPE_IMAGE) {
                                settingsCell.setTextAndValueAndIcon(LocaleController.getString(R.string.ArchivedStickers), value, R.drawable.msg2_archived_stickers, true);
                            } else if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
                                settingsCell.setTextAndValue(LocaleController.getString("ArchivedEmojiPacks", R.string.ArchivedEmojiPacks), value, false, true);
                            } else {
                                settingsCell.setTextAndValue(LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks), value, false, true);
                            }
                        } else if (position == masksRow) {
                            int type = MediaDataController.TYPE_MASK;
                            MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                            int count = MessagesController.getInstance(currentAccount).filterPremiumStickers(mediaDataController.getStickerSets(type)).size() + mediaDataController.getArchivedStickersCount(type);
                            settingsCell.setTextAndValueAndIcon(LocaleController.getString("Masks", R.string.Masks), count > 0 ? Integer.toString(count) : "", R.drawable.msg_mask, true);
                        } else if (position == featuredRow) {
                            List<TLRPC.StickerSetCovered> sets = getFeaturedSets();
                            settingsCell.setTextAndValueAndIcon(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers), sets != null ? "" + sets.size() : "", R.drawable.msg2_trending, true);
                        } else if (position == emojiPacksRow) {
                            int type = MediaDataController.TYPE_EMOJIPACKS;
                            MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                            int count = mediaDataController.getStickerSets(type).size();
                            settingsCell.imageView.setTranslationX(-AndroidUtilities.dp(2));
                            settingsCell.setTextAndValueAndIcon(LocaleController.getString("Emoji", R.string.Emoji), count > 0 ? Integer.toString(count) : "", R.drawable.msg2_smile_status, true);
                        } else if (position == suggestRow) {
                            String value;
                            switch (SharedConfig.suggestStickers) {
                                case 0:
                                    value = LocaleController.getString("SuggestStickersAll", R.string.SuggestStickersAll);
                                    break;
                                case 1:
                                    value = LocaleController.getString("SuggestStickersInstalled", R.string.SuggestStickersInstalled);
                                    break;
                                case 2:
                                default:
                                    value = LocaleController.getString("SuggestStickersNone", R.string.SuggestStickersNone);
                                    break;
                            }
                            if (!LocaleController.isRTL) {
                                settingsCell.textView.setTranslationX(AndroidUtilities.dp(-2));
                            }
                            settingsCell.setTextAndValue(LocaleController.getString("SuggestStickers", R.string.SuggestStickers), value, updateSuggestStickers, true);
                            updateSuggestStickers = false;
                        }
                    }
                    break;
                }
                case TYPE_SHADOW:
                    if (position == stickersShadowRow) {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case TYPE_SWITCH:
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == loopRow) {
                        cell.setTextAndCheck(LocaleController.getString("LoopAnimatedStickers", R.string.LoopAnimatedStickers), SharedConfig.loopStickers(), true);
                    } else if (position == largeEmojiRow) {
                        cell.setTextAndCheck(LocaleController.getString("LargeEmoji", R.string.LargeEmoji), SharedConfig.allowBigEmoji, true);
                    } else if (position == suggestAnimatedEmojiRow) {
                        cell.setTextAndCheck(LocaleController.getString("SuggestAnimatedEmoji", R.string.SuggestAnimatedEmoji), SharedConfig.suggestAnimatedEmoji, false);
                    } else if (position == dynamicPackOrder) {
                        cell.setTextAndCheck(LocaleController.getString("DynamicPackOrder"), SharedConfig.updateStickersOrderOnSend, false);
                    }
                    break;
                case TYPE_DOUBLE_TAP_REACTIONS: {
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    settingsCell.setText(LocaleController.getString("DoubleTapSetting", R.string.DoubleTapSetting), false);
                    settingsCell.setIcon(R.drawable.msg2_reactions2);
                    String reaction = MediaDataController.getInstance(currentAccount).getDoubleTapReaction();
                    if (reaction != null) {
                        if (reaction.startsWith("animated_")) {
                            try {
                                long documentId = Long.parseLong(reaction.substring(9));
                                AnimatedEmojiDrawable drawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, documentId);
                                drawable.addView(settingsCell.getValueBackupImageView());
                                settingsCell.getValueBackupImageView().setImageDrawable(drawable);
                            } catch (Exception e) {}
                        } else {
                            TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction);
                            if (availableReaction != null) {
                                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(availableReaction.static_icon.thumbs, Theme.key_windowBackgroundGray, 1.0f);
                                settingsCell.getValueBackupImageView().getImageReceiver().setImage(ImageLocation.getForDocument(availableReaction.center_icon), "100_100_lastreactframe", svgThumb, "webp", availableReaction, 1);
                            }
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position);
            } else {
                switch (holder.getItemViewType()) {
                    case TYPE_STICKER_SET:
                        if (position >= stickersStartRow && position < stickersEndRow) {
                            StickerSetCell stickerSetCell = (StickerSetCell) holder.itemView;
                            if (payloads.contains(UPDATE_SELECTION)) {
                                stickerSetCell.setChecked(selectedItems.get(getItemId(position), false));
                            }
                            if (payloads.contains(UPDATE_REORDERABLE)) {
                                stickerSetCell.setReorderable(hasSelected());
                            }
                            if (payloads.contains(UPDATE_DIVIDER)) {
                                stickerSetCell.setNeedDivider(position - stickersStartRow != stickerSets.size() - 1);
                            }
                        }
                        break;
                    case TYPE_SWITCH:
                        if (payloads.contains(UPDATE_LOOP_STICKERS) && position == loopRow) {
                            ((TextCheckCell) holder.itemView).setChecked(SharedConfig.loopStickers());
                        }
                        break;
                    case TYPE_FEATURED_STICKER_SET:
                        if (payloads.contains(UPDATE_FEATURED_ANIMATED) && position >= featuredStickersStartRow && position <= featuredStickersEndRow) {
                            ((FeaturedStickerSetCell2) holder.itemView).setStickersSet(featuredStickerSets.get(position - featuredStickersStartRow), true, false, false, true);
                        }
                        break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_STICKER_SET || type == TYPE_FEATURED_STICKER_SET || type == TYPE_TEXT_AND_VALUE || type == TYPE_SWITCH || type == TYPE_DOUBLE_TAP_REACTIONS;
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_FEATURED_STICKER_SET:
                    view = new FeaturedStickerSetCell2(mContext, getResourceProvider()) {
                        @Override
                        protected void onPremiumButtonClick() {
                            showDialog(new PremiumFeatureBottomSheet(StickersActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
                        }
                    };
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((FeaturedStickerSetCell2) view).getTextView().setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                    break;
                case TYPE_STICKER_SET:
                    view = new StickerSetCell(mContext, 1) {
                        @Override
                        protected void onPremiumButtonClick() {
                            showDialog(new PremiumFeatureBottomSheet(StickersActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
                        }

                        @Override
                        protected void onAddButtonClick() {
                            TLRPC.TL_messages_stickerSet set = getStickersSet();
                            if (set == null || set.set == null) {
                                return;
                            }
                            ArrayList<TLRPC.StickerSetCovered> featured = getMediaDataController().getFeaturedEmojiSets();
                            TLRPC.StickerSetCovered covered = null;
                            for (int i = 0; i < featured.size(); ++i) {
                                if (set.set.id == featured.get(i).set.id) {
                                    covered = featured.get(i);
                                    break;
                                }
                            }
                            if (covered != null) {
                                if (loadingFeaturedStickerSets.contains(covered.set.id)) {
                                    return;
                                }
                                loadingFeaturedStickerSets.add(covered.set.id);
                            }
//                            updateButtonState(BUTTON_STATE_REMOVE, true);
                            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), covered == null ? set : covered, 2, StickersActivity.this, false, false);
                        }

                        @Override
                        protected void onRemoveButtonClick() {
//                            updateButtonState(BUTTON_STATE_ADD, true);
                            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), getStickersSet(), 0, StickersActivity.this, false, true);
                        }
                    };
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    StickerSetCell stickerSetCell = (StickerSetCell) view;
                    stickerSetCell.setOnReorderButtonTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            itemTouchHelper.startDrag(listView.getChildViewHolder(stickerSetCell));
                        }
                        return false;
                    });
                    stickerSetCell.setOnOptionsClick(v -> {
                        StickerSetCell cell = (StickerSetCell) v.getParent();
                        TLRPC.TL_messages_stickerSet stickerSet = cell.getStickersSet();
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(stickerSet.set.title);
                        int[] options;
                        CharSequence[] items;
                        int[] icons;
                        if (stickerSet.set.official) {
                            options = new int[]{MENU_ARCHIVE, 4};
                            items = new CharSequence[]{
                                    LocaleController.getString("StickersHide", R.string.StickersHide),
                                    LocaleController.getString("StickersReorder", R.string.StickersReorder)
                            };
                            icons = new int[]{R.drawable.msg_archive, R.drawable.msg_reorder};
                        } else {
                            options = new int[]{MENU_ARCHIVE, 3, 4, 2, MENU_DELETE};
                            items = new CharSequence[]{
                                    LocaleController.getString("StickersHide", R.string.StickersHide),
                                    LocaleController.getString("StickersCopy", R.string.StickersCopy),
                                    LocaleController.getString("StickersReorder", R.string.StickersReorder),
                                    LocaleController.getString("StickersShare", R.string.StickersShare),
                                    LocaleController.getString("StickersRemove", R.string.StickersRemove),
                            };
                            icons = new int[]{
                                    R.drawable.msg_archive,
                                    R.drawable.msg_link,
                                    R.drawable.msg_reorder,
                                    R.drawable.msg_share,
                                    R.drawable.msg_delete
                            };
                        }
                        builder.setItems(items, icons, (dialog, which) -> processSelectionOption(options[which], stickerSet));

                        AlertDialog dialog = builder.create();
                        showDialog(dialog);

                        if (options[options.length - 1] == MENU_DELETE) {
                            dialog.setItemColor(items.length - 1, Theme.getColor(Theme.key_dialogTextRed), Theme.getColor(Theme.key_dialogRedIcon));
                        }
                    });
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case TYPE_TEXT_AND_VALUE:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case TYPE_DOUBLE_TAP_REACTIONS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SWITCH:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= featuredStickersStartRow && i < featuredStickersEndRow) {
                return TYPE_FEATURED_STICKER_SET;
            } else if (i >= stickersStartRow && i < stickersEndRow) {
                return TYPE_STICKER_SET;
            } else if (i == stickersBotInfo || i == archivedInfoRow || i == loopInfoRow || i == suggestAnimatedEmojiInfoRow || i == masksInfoRow || i == dynamicPackOrderInfo) {
                return TYPE_INFO;
            } else if (i == archivedRow || i == masksRow || i == featuredRow || i == emojiPacksRow || i == suggestRow || i == featuredStickersShowMoreRow) {
                return TYPE_TEXT_AND_VALUE;
            } else if (i == stickersShadowRow || i == featuredStickersShadowRow) {
                return TYPE_SHADOW;
            } else if (i == loopRow || i == largeEmojiRow || i == suggestAnimatedEmojiRow || i == dynamicPackOrder) {
                return TYPE_SWITCH;
            } else if (i == reactionsDoubleTapRow) {
                return TYPE_DOUBLE_TAP_REACTIONS;
            } else if (i == featuredStickersHeaderRow || i == stickersHeaderRow || i == stickersSettingsRow) {
                return TYPE_HEADER;
            }
            return TYPE_STICKER_SET;
        }

        public void swapElements(int fromIndex, int toIndex) {
            if (fromIndex != toIndex) {
                needReorder = true;
            }

            MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);

            int index1 = fromIndex - stickersStartRow;
            int index2 = toIndex - stickersStartRow;

            swapListElements(stickerSets, index1, index2);
            Collections.sort(mediaDataController.getStickerSets(currentType), (o1, o2) -> {
                int i1 = stickerSets.indexOf(o1);
                int i2 = stickerSets.indexOf(o2);
                if (i1 >= 0 && i2 >= 0) {
                    return i1 - i2;
                }
                return 0;
            });

            notifyItemMoved(fromIndex, toIndex);

            if (fromIndex == stickersEndRow - 1 || toIndex == stickersEndRow - 1) {
                notifyItemRangeChanged(fromIndex, UPDATE_DIVIDER);
                notifyItemRangeChanged(toIndex, UPDATE_DIVIDER);
            }
        }

        private void swapListElements(List<TLRPC.TL_messages_stickerSet> list, int index1, int index2) {
            TLRPC.TL_messages_stickerSet set1 = list.get(index1);
            list.set(index1, list.get(index2));
            list.set(index2, set1);
        }

        public void toggleSelected(int position) {
            long id = getItemId(position);
            selectedItems.put(id, !selectedItems.get(id, false));
            notifyItemChanged(position, UPDATE_SELECTION);
            checkActionMode();
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyStickersItemsChanged(UPDATE_SELECTION);
            checkActionMode();
        }

        public boolean hasSelected() {
            return selectedItems.indexOfValue(true) != -1;
        }

        public int getSelectedCount() {
            int count = 0;
            for (int i = 0, size = selectedItems.size(); i < size; i++) {
                if (selectedItems.valueAt(i)) {
                    count++;
                }
            }
            return count;
        }

        private void checkActionMode() {
            int selectedCount = listAdapter.getSelectedCount();
            boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                checkActionModeIcons();
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                    notifyStickersItemsChanged(UPDATE_REORDERABLE);
                    if (!SharedConfig.stickersReorderingHintUsed && currentType != MediaDataController.TYPE_EMOJIPACKS) {
                        SharedConfig.setStickersReorderingHintUsed(true);
                        String stickersReorderHint = LocaleController.getString("StickersReorderHint", R.string.StickersReorderHint);
                        Bulletin.make(parentLayout.getLastFragment(), new ReorderingBulletinLayout(mContext, stickersReorderHint, null), ReorderingHintDrawable.DURATION * 2 + 250).show();
                    }
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
                notifyStickersItemsChanged(UPDATE_REORDERABLE);
            }
        }

        private void checkActionModeIcons() {
            if (hasSelected()) {
                boolean canDelete = true;
                for (int i = 0, size = stickerSets.size(); i < size; i++) {
                    if (selectedItems.get(stickerSets.get(i).set.id, false) && stickerSets.get(i).set.official && !stickerSets.get(i).set.emojis) {
                        canDelete = false;
                        break;
                    }
                }
                int visibility = canDelete ? View.VISIBLE : View.GONE;
                if (deleteMenuItem.getVisibility() != visibility) {
                    deleteMenuItem.setVisibility(visibility);
                }
            }
        }

        private void notifyStickersItemsChanged(Object payload) {
            notifyItemRangeChanged(stickersStartRow, stickersEndRow - stickersStartRow, payload);
        }

        private CharSequence addStickersBotSpan(String text) {
            String botName = "@stickers";
            int index = text.indexOf(botName);
            if (index != -1) {
                try {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
                    URLSpanNoUnderline urlSpan = new URLSpanNoUnderline("@stickers") {
                        @Override
                        public void onClick(View widget) {
                            MessagesController.getInstance(currentAccount).openByUserName("stickers", StickersActivity.this, 3);
                        }
                    };
                    stringBuilder.setSpan(urlSpan, index, index + botName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    return stringBuilder;
                } catch (Exception e) {
                    FileLog.e(e);
                    return text;
                }
            } else {
                return text;
            }
        }
    }

    private void checkPack(TLRPC.TL_messages_stickerSet set) {
        if (set == null) {
            return;
        }
        if (emojiPacks == null) {
            emojiPacks = new ArrayList<>();
            emojiPacks.add(set);
            return;
        }
        boolean found = false;
        for (int i = 0; i < emojiPacks.size(); ++i) {
            if (emojiPacks.get(i).set.id == set.set.id) {
                found = true;
                break;
            }
        }
        if (!found) {
            emojiPacks.add(set);
        }
    }
    private void checkPack(TLRPC.StickerSetCovered covered) {
        if (covered == null) {
            return;
        }
        if (emojiPacks == null) {
            emojiPacks = new ArrayList<>();
            emojiPacks.add(convertFeatured(covered));
            return;
        }
        boolean found = false;
        for (int i = 0; i < emojiPacks.size(); ++i) {
            if (emojiPacks.get(i).set.id == covered.set.id) {
                found = true;
                break;
            }
        }
        if (!found) {
            emojiPacks.add(convertFeatured(covered));
        }
    }
    private TLRPC.TL_messages_stickerSet convertFeatured(TLRPC.StickerSetCovered covered) {
        if (covered == null) {
            return null;
        }
        TLRPC.TL_messages_stickerSet stickerSet = new TLRPC.TL_messages_stickerSet();
        stickerSet.set = covered.set;
        if (covered instanceof TLRPC.TL_stickerSetFullCovered) {
            stickerSet.documents = ((TLRPC.TL_stickerSetFullCovered) covered).documents;
            stickerSet.packs = ((TLRPC.TL_stickerSetFullCovered) covered).packs;
        } else {
            stickerSet.documents = covered.covers;
        }
        return stickerSet;
    }

    private String getLinkForSet(TLRPC.TL_messages_stickerSet stickerSet) {
        return String.format(Locale.US, "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + (stickerSet.set.emojis ? "addemoji" : "addstickers") + "/%s", stickerSet.set.short_name);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSetCell.class, TextSettingsCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        themeDescriptions.add(new ThemeDescription(selectedCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menuSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"reorderButton"}, null, null, null, Theme.key_stickers_menu));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{StickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{StickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

        if (trendingStickersAlert != null) {
            themeDescriptions.addAll(trendingStickersAlert.getThemeDescriptions());
        }

        return themeDescriptions;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MediaDataController.TYPE_EMOJIPACKS;
import static org.telegram.messenger.MediaDataController.TYPE_IMAGE;
import static org.telegram.messenger.MediaDataController.TYPE_MASK;

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

import androidx.annotation.Keep;
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
import org.telegram.ui.Business.QuickRepliesController;
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
import org.telegram.ui.Components.ItemOptions;
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
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class StickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_ARCHIVE = 0;
    private static final int MENU_DELETE = 1;
    private static final int MENU_SHARE = 2;
    private static final int MENU_COPY = 3;
    private static final int MENU_REORDER = 4;

    private UniversalRecyclerView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private NumberTextView selectedCountTextView;
    private TrendingStickersAlert trendingStickersAlert;

    private ArrayList<TLRPC.TL_messages_stickerSet> sets;
    private ArrayList<TLRPC.StickerSetCovered> featured;
    private final List<Long> loadingFeaturedStickerSets = new ArrayList<>();

    private ActionBarMenuItem archiveMenuItem;
    private ActionBarMenuItem deleteMenuItem;
    private ActionBarMenuItem shareMenuItem;

    private int activeReorderingRequests;
    private boolean needReorder;
    private int currentType;

    @Keep
    private int dynamicPackOrder;
    private int dynamicPackOrderInfo;
    @Keep
    private int suggestRow;
    private int suggestAnimatedEmojiRow;
    private int suggestAnimatedEmojiInfoRow;
    private int loopRow;
    private int loopInfoRow;
    @Keep
    private int largeEmojiRow;
    private int reactionsDoubleTapRow;
    private int stickersBotInfo;
    @Keep
    private int featuredRow;
    private int masksRow;
    private int emojiPacksRow;
    private int masksInfoRow;
    @Keep
    private int archivedRow;
    private int archivedInfoRow;

    ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks;

    private ArrayList<TLRPC.TL_messages_stickerSet> emojiPacks;
    private ArrayList<TLRPC.StickerSetCovered> getFeaturedSets() {
        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        ArrayList<TLRPC.StickerSetCovered> featuredStickerSets;
        if (currentType == TYPE_EMOJIPACKS) {
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

    public StickersActivity(int type, ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks) {
        super();
        currentType = type;
        this.frozenEmojiPacks = frozenEmojiPacks;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        MediaDataController.getInstance(currentAccount).checkStickers(currentType);
        if (currentType == TYPE_IMAGE) {
            MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
            MediaDataController.getInstance(currentAccount).checkStickers(TYPE_MASK);
            MediaDataController.getInstance(currentAccount).checkStickers(TYPE_EMOJIPACKS);
        } else if (currentType == MediaDataController.TYPE_FEATURED_EMOJIPACKS) {
            MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredEmojiDidLoad);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.archivedStickersCountDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
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
        if (currentType == TYPE_IMAGE) {
            actionBar.setTitle(getString(R.string.StickersName));
        } else if (currentType == TYPE_MASK) {
            actionBar.setTitle(getString(R.string.Masks));
        } else if (currentType == TYPE_EMOJIPACKS) {
            actionBar.setTitle(getString(R.string.Emoji));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment();
                    }
                } else {
                    processSelectionMenu(id);
                }
            }
        });


        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.bold());
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedCountTextView.setOnTouchListener((v, event) -> true);

        shareMenuItem = actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, dp(54));
        archiveMenuItem = actionMode.addItemWithWidth(MENU_ARCHIVE, R.drawable.msg_archive, dp(54));
        deleteMenuItem = actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, dp(54));

        if (currentType == TYPE_EMOJIPACKS && frozenEmojiPacks != null) {
            sets = frozenEmojiPacks;
        } else {
            sets = new ArrayList<>(MessagesController.getInstance(currentAccount).filterPremiumStickers(MediaDataController.getInstance(currentAccount).getStickerSets(currentType)));
        }
        featured = getFeaturedSets();

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        listView.setFocusable(true);
        listView.setTag(7);
        listView.listenReorder(this::whenReordered);
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
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }


    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!selectedSets.isEmpty()) {
            if (invoked) clearSelected();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            int type = (int) args[0];
            if (type == currentType) {
                loadingFeaturedStickerSets.clear();
            }
            listView.adapter.update(true);
        } else if (id == NotificationCenter.featuredStickersDidLoad || id == NotificationCenter.featuredEmojiDidLoad) {
            listView.adapter.update(true);
        } else if (id == NotificationCenter.archivedStickersCountDidLoad) {
            if ((Integer) args[0] == currentType) {
                listView.adapter.update(true);
            }
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {

        }
    }

    private String suggestStickersName() {
        switch (SharedConfig.suggestStickers) {
            case 0: return getString(R.string.SuggestStickersAll);
            case 1: return getString(R.string.SuggestStickersInstalled);
            default:
            case 2: return getString(R.string.SuggestStickersNone);
        }
    }

    private final HashSet<Long> selectedSets = new HashSet<>();

    private static final int ID_FEATURED = 1;
    private static final int ID_ARCHIVED = 2;
    private static final int ID_EMOJI = 3;
    private static final int ID_QUICK_REACTION = 4;
    private static final int ID_SUGGEST_STICKERS = 5;
    private static final int ID_LARGE_EMOJI = 6;
    private static final int ID_DYNAMIC_PACK_ORDER = 7;
    private static final int ID_SHOW_MORE_FEATURED = 8;
    private static final int ID_SUGGEST_EMOJI = 9;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        if (listView == null || !listView.isReorderAllowed() && !needReorder && activeReorderingRequests <= 0) {
            if (currentType == TYPE_EMOJIPACKS) {
                if (true || frozenEmojiPacks == null) {
                    frozenEmojiPacks = new ArrayList<>(MessagesController.getInstance(currentAccount).filterPremiumStickers(mediaDataController.getStickerSets(currentType)));
                }
                sets = frozenEmojiPacks;
            } else {
                sets = new ArrayList<>(MessagesController.getInstance(currentAccount).filterPremiumStickers(mediaDataController.getStickerSets(currentType)));
            }
        }

        boolean truncatedFeaturedStickers = false;
        featured = new ArrayList<>(getFeaturedSets());
        for (int i = 0; i < featured.size(); ++i) {
            if (loadingFeaturedStickerSets.contains(featured.get(i).set.id)) {
                featured.remove(i);
                i--;
            }
        }

        final int featuredCount = featured.size();
        final int archivedCount = mediaDataController.getArchivedStickersCount(currentType);
        final int emojiCount    = mediaDataController.getStickerSets(TYPE_EMOJIPACKS).size();

        if (currentType == TYPE_IMAGE) {
            featuredRow = items.size();
            items.add(UItem.asButton(ID_FEATURED, R.drawable.msg2_trending, getString(R.string.FeaturedStickers), featuredCount > 0 ? formatNumber(featuredCount, ',') : ""));
            if (archivedCount > 0) {
                archivedRow = items.size();
                if (currentType == TYPE_IMAGE) {
                    items.add(UItem.asButton(ID_ARCHIVED, R.drawable.msg2_archived_stickers, getString(R.string.ArchivedStickers), formatNumber(archivedCount, ',')));
                } else {
                    items.add(UItem.asButton(ID_ARCHIVED, getString(currentType == TYPE_EMOJIPACKS ? R.string.ArchivedEmojiPacks : R.string.ArchivedMasks), formatNumber(archivedCount, ',')));
                }
            }
            emojiPacksRow = items.size();
            items.add(UItem.asSettingsCell(ID_EMOJI, R.drawable.msg2_smile_status, getString(R.string.Emoji), emojiCount > 0 ? LocaleController.formatNumber(emojiCount, ',') : ""));
        } else {
            if (archivedCount > 0) {
                archivedRow = items.size();
                if (currentType == TYPE_IMAGE) {
                    items.add(UItem.asButton(ID_ARCHIVED, R.drawable.msg2_archived_stickers, getString(R.string.ArchivedStickers), formatNumber(archivedCount, ',')));
                } else {
                    items.add(UItem.asButton(ID_ARCHIVED, getString(currentType == TYPE_EMOJIPACKS ? R.string.ArchivedEmojiPacks : R.string.ArchivedMasks), formatNumber(archivedCount, ',')));
                }
                if (currentType == TYPE_MASK) {
                    items.add(UItem.asShadow(getString(R.string.ArchivedMasksInfo)));
                }
            }
        }

        if (currentType == TYPE_IMAGE) {
            reactionsDoubleTapRow = items.size();
            items.add(UItem.asSettingsCell(ID_QUICK_REACTION, R.drawable.msg2_reactions2, getString(R.string.DoubleTapSetting)).onBind(this::setQuickReactionImage));
            items.add(UItem.asShadow(addStickersBotSpan(getString(currentType == TYPE_EMOJIPACKS ? R.string.EmojiBotInfo : R.string.StickersBotInfo))));

            items.add(UItem.asHeader(getString(R.string.StickersSettings)));
            suggestRow = items.size();
            items.add(UItem.asSettingsCell(ID_SUGGEST_STICKERS, getString(R.string.SuggestStickers), suggestStickersName()));
            largeEmojiRow = items.size();
            items.add(UItem.asCheck(ID_LARGE_EMOJI, getString(R.string.LargeEmoji)).setChecked(SharedConfig.allowBigEmoji));
            dynamicPackOrder = items.size();
            items.add(UItem.asCheck(ID_DYNAMIC_PACK_ORDER, getString(R.string.DynamicPackOrder)).setChecked(SharedConfig.updateStickersOrderOnSend));
            items.add(UItem.asShadow(getString(R.string.DynamicPackOrderInfo)));
        }

        if (currentType == TYPE_EMOJIPACKS) {
            items.add(UItem.asCheck(ID_SUGGEST_EMOJI, getString(R.string.SuggestAnimatedEmoji)).setChecked(SharedConfig.suggestAnimatedEmoji));
            items.add(UItem.asShadow(getString(R.string.SuggestAnimatedEmojiInfo)));
        }

        if (sets.size() > 0) {
            adapter.whiteSectionStart();
            if (currentType == TYPE_EMOJIPACKS || !featured.isEmpty() && currentType == TYPE_IMAGE) {
                items.add(UItem.asHeader(getString(currentType == TYPE_EMOJIPACKS ? R.string.ChooseStickerMyEmojiPacks : R.string.ChooseStickerMyStickerSets)));
            }

            adapter.reorderSectionStart();
            for (TLRPC.TL_messages_stickerSet set : sets) {
                items.add(
                    StickerSetCell.Factory.of(set)
                        .setClickCallback(this::openStickerSetOptions)
                        .setClickCallback2(this::onStickerSetButtonClick)
                        .setChecked(selectedSets.contains(set.set.id))
                );
            }
            adapter.reorderSectionEnd();

            adapter.whiteSectionEnd();

            if (currentType != TYPE_MASK && currentType != TYPE_EMOJIPACKS) {
                items.add(UItem.asShadow(null));
            } else if (currentType == TYPE_MASK) {
                items.add(UItem.asShadow(getString(R.string.MasksInfo)));
            }
        }

        if (featured.size() > 3) {
            featured.removeAll(featured.subList(3, featured.size()));
            truncatedFeaturedStickers = true;
        }
        if (currentType == TYPE_EMOJIPACKS && !featured.isEmpty()) {
            if (sets.size() > 0) {
                items.add(UItem.asShadow(null));
            }

            items.add(UItem.asHeader(getString(currentType == TYPE_EMOJIPACKS ? R.string.FeaturedEmojiPacks : R.string.FeaturedStickers)));
            for (TLRPC.StickerSetCovered set : featured) {
                final UItem featuredItem =
                    FeaturedStickerSetCell2.Factory.of(set)
                        .setClickCallback(this::onFeaturedAddClick)
                        .setLocked(loadingFeaturedStickerSets.contains(set.set.id));
                items.add(featuredItem);
            }

            if (truncatedFeaturedStickers) {
                items.add(UItem.asButton(ID_SHOW_MORE_FEATURED, R.drawable.msg2_trending, getString(R.string.ShowMoreEmojiPacks)).accent());
            }
        }

        if (currentType == TYPE_EMOJIPACKS) {
            items.add(UItem.asShadow(addStickersBotSpan(getString(currentType == TYPE_EMOJIPACKS ? R.string.EmojiBotInfo : R.string.StickersBotInfo))));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.TL_messages_stickerSet) {
            final TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) item.object;
            if (selectedSets.isEmpty()) {
                final ArrayList<TLRPC.Document> stickers = set.documents;
                if (stickers == null || stickers.isEmpty()) {
                    return;
                }
                if (set.set != null && set.set.emojis) {
                    final ArrayList<TLRPC.InputStickerSet> inputs = new ArrayList<>();
                    final TLRPC.TL_inputStickerSetID inputId = new TLRPC.TL_inputStickerSetID();
                    inputId.id = set.set.id;
                    inputId.access_hash = set.set.access_hash;
                    inputs.add(inputId);
                    showDialog(new EmojiPacksAlert(StickersActivity.this, getParentActivity(), getResourceProvider(), inputs));
                } else {
                    showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, null, set, null, false));
                }
            } else {
                toggleSelected((StickerSetCell) view);
            }
            return;
        }
        if (item.object instanceof TLRPC.StickerSetCovered) {
            final TLRPC.StickerSetCovered set = (TLRPC.StickerSetCovered) item.object;
            final TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
            inputStickerSetID.id = set.set.id;
            inputStickerSetID.access_hash = set.set.access_hash;
            if (currentType == TYPE_EMOJIPACKS) {
                final ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>(1);
                inputStickerSets.add(inputStickerSetID);
                showDialog(new EmojiPacksAlert(StickersActivity.this, getParentActivity(), getResourceProvider(), inputStickerSets));
            } else {
                showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, inputStickerSetID, null, null, false));
            }
            return;
        }
        switch (item.id) {
            case ID_ARCHIVED:
                presentFragment(new ArchivedStickersActivity(currentType));
                break;
            case ID_EMOJI:
                presentFragment(new StickersActivity(TYPE_EMOJIPACKS, null));
                break;
            case ID_QUICK_REACTION:
                presentFragment(new ReactionsDoubleTapManageActivity());
                break;
            case ID_DYNAMIC_PACK_ORDER:
                SharedConfig.toggleUpdateStickersOrderOnSend();
                ((TextCheckCell) view).setChecked(SharedConfig.updateStickersOrderOnSend);
                break;
            case ID_SUGGEST_EMOJI:
                SharedConfig.toggleSuggestAnimatedEmoji();
                ((TextCheckCell) view).setChecked(SharedConfig.suggestAnimatedEmoji);
                break;
            case ID_LARGE_EMOJI:
                SharedConfig.toggleBigEmoji();
                ((TextCheckCell) view).setChecked(SharedConfig.allowBigEmoji);
                break;
            case ID_SUGGEST_STICKERS:
                ItemOptions.makeOptions(this, view)
                    .addChecked(SharedConfig.suggestStickers == 0, getString(R.string.SuggestStickersAll),       () -> { SharedConfig.setSuggestStickers(0); ((TextSettingsCell) view).setValue(getString(R.string.SuggestStickersAll), true); })
                    .addChecked(SharedConfig.suggestStickers == 1, getString(R.string.SuggestStickersInstalled), () -> { SharedConfig.setSuggestStickers(1); ((TextSettingsCell) view).setValue(getString(R.string.SuggestStickersInstalled), true); })
                    .addChecked(SharedConfig.suggestStickers == 2, getString(R.string.SuggestStickersNone),      () -> { SharedConfig.setSuggestStickers(2); ((TextSettingsCell) view).setValue(getString(R.string.SuggestStickersNone), true); })
                    .show();
                break;
            case ID_FEATURED:
            case ID_SHOW_MORE_FEATURED:
                if (currentType == TYPE_EMOJIPACKS) {
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
                    trendingStickersAlert = new TrendingStickersAlert(getContext(), this, new TrendingStickersLayout(getContext(), trendingDelegate), null);
                    trendingStickersAlert.show();
                }
                break;
//            case ID_MASKS:
//                presentFragment(new StickersActivity(MediaDataController.TYPE_MASK, null));
//                break;
//            case ID_LOOP:
//                SharedConfig.toggleLoopStickers();
//                listAdapter.notifyItemChanged(loopRow, ListAdapter.UPDATE_LOOP_STICKERS);
//                break;
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (!selectedSets.isEmpty()) {
            return false;
        }
        if (item.object instanceof TLRPC.TL_messages_stickerSet) {
            toggleSelected((StickerSetCell) view);
            return true;
        }
        return false;
    }

    private void whenReordered(int sectionId, ArrayList<UItem> items) {
        final ArrayList<TLRPC.TL_messages_stickerSet> newSets = new ArrayList<>();
        for (UItem item : items) {
            if (item.object instanceof TLRPC.TL_messages_stickerSet) {
                newSets.add((TLRPC.TL_messages_stickerSet) item.object);
            }
        }
        sets = newSets;
        needReorder = true;

        Collections.sort(MediaDataController.getInstance(currentAccount).getStickerSets(currentType), (o1, o2) -> {
            int i1 = sets.indexOf(o1);
            int i2 = sets.indexOf(o2);
            if (i1 >= 0 && i2 >= 0) {
                return i1 - i2;
            }
            return 0;
        });

        AndroidUtilities.cancelRunOnUIThread(sendReorderRunnable);
        AndroidUtilities.runOnUIThread(sendReorderRunnable, 1000);
    }

    private Runnable sendReorderRunnable = () -> sendReorder();
    private void sendReorder() {
        if (!needReorder) return;

        needReorder = false;

        MediaDataController.getInstance(currentAccount).calcNewHash(currentType);
        activeReorderingRequests++;

        final TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
        req.masks = currentType == TYPE_MASK;
        req.emojis = currentType == TYPE_EMOJIPACKS;
        for (int a = 0; a < sets.size(); a++) {
            req.order.add(sets.get(a).set.id);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> activeReorderingRequests--));
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoad, currentType, true);

        if (SharedConfig.updateStickersOrderOnSend) {
            SharedConfig.toggleUpdateStickersOrderOnSend();
            BulletinFactory.of(StickersActivity.this).createSimpleBulletin(R.raw.filter_reorder, getString(R.string.DynamicPackOrderOff), getString(R.string.DynamicPackOrderOffInfo)).show();
            listView.adapter.update(true);
        }
    }

    private void openStickerSetOptions(View view) {
        if (view == null || !(view.getParent() instanceof StickerSetCell)) return;
        final StickerSetCell cell = (StickerSetCell) view.getParent();
        final TLRPC.TL_messages_stickerSet set = cell.getStickersSet();
        ItemOptions.makeOptions(StickersActivity.this, cell)
            .add(R.drawable.msg_archive, LocaleController.getString(R.string.StickersHide), () -> {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), set, !set.set.archived ? 1 : 2, StickersActivity.this, true, true);
            })
            .addIf(!set.set.official, R.drawable.msg_link, LocaleController.getString(R.string.StickersCopy), () -> {
                try {
                    final android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    final android.content.ClipData clip = android.content.ClipData.newPlainText("label", getLinkForSet(set));
                    clipboard.setPrimaryClip(clip);
                    BulletinFactory.createCopyLinkBulletin(StickersActivity.this).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            })
            .add(R.drawable.msg_reorder, LocaleController.getString(R.string.StickersReorder), () -> {
                toggleSelected(cell);
            })
            .addIf(!set.set.official, R.drawable.msg_share, LocaleController.getString(R.string.StickersShare), () -> {
                try {
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, getLinkForSet(set));
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.StickersShare)), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            })
            .addIf(!set.set.official, R.drawable.msg_delete, LocaleController.getString(R.string.StickersRemove), true, () -> {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), set, 0, StickersActivity.this, true, true);
            })
            .setMinWidth(190)
            .show();
    }

    private void onStickerSetButtonClick(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        final ViewGroup parent = (ViewGroup) view.getParent();
        if (!(parent.getParent() instanceof StickerSetCell)) return;
        final StickerSetCell cell = (StickerSetCell) parent.getParent();

        final TLRPC.TL_messages_stickerSet set = cell.getStickersSet();
        if (set == null || set.set == null)
            return;

        if (cell.addButtonView == view) {
            final ArrayList<TLRPC.StickerSetCovered> featured = getMediaDataController().getFeaturedEmojiSets();
            TLRPC.StickerSetCovered covered = null;
            for (int i = 0; i < featured.size(); ++i) {
                if (set.set.id == featured.get(i).set.id) {
                    covered = featured.get(i);
                    break;
                }
            }
            if (covered != null) {
                if (loadingFeaturedStickerSets.contains(covered.set.id))
                    return;
                loadingFeaturedStickerSets.add(covered.set.id);
            }
            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), covered == null ? set : covered, 2, this, false, false);
        } else if (cell.removeButtonView == view) {
            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), set, 0, this, false, true);
        } else if (cell.premiumButtonView == view) {
            showDialog(new PremiumFeatureBottomSheet(this, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
        }
    }

    private void onFeaturedAddClick(View view) {
        final FeaturedStickerSetCell2 cell = (FeaturedStickerSetCell2) view.getParent();
        final TLRPC.StickerSetCovered pack = cell.getStickerSet();
        if (loadingFeaturedStickerSets.contains(pack.set.id)) {
            return;
        }
        loadingFeaturedStickerSets.add(pack.set.id);
        cell.setDrawProgress(true, true);
        if (cell.isInstalled()) {
            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), pack, 0, StickersActivity.this, false, false);
        } else {
            MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), pack, 2, StickersActivity.this, false, false);
        }
    }

    private void toggleSelected(StickerSetCell cell) {
        final TLRPC.TL_messages_stickerSet set = cell.getStickersSet();
        if (set == null) return;
        if (selectedSets.contains(set.set.id)) {
            selectedSets.remove(set.set.id);
            cell.setChecked(false, true);
        } else {
            selectedSets.add(set.set.id);
            cell.setChecked(true, true);
        }
        listView.adapter.update(true);
        checkActionMode();
    }

    private void toggleSelected(TLRPC.TL_messages_stickerSet set) {
        if (set == null) return;
        if (selectedSets.contains(set.set.id)) {
            selectedSets.remove(set.set.id);
        } else {
            selectedSets.add(set.set.id);
        }
        listView.adapter.update(true);
        checkActionMode();
    }

    private void setQuickReactionImage(View view) {
        if (view instanceof TextSettingsCell) {
            final TextSettingsCell cell = (TextSettingsCell) view;
            final String reaction = MediaDataController.getInstance(currentAccount).getDoubleTapReaction();
            if (reaction != null) {
                if (reaction.startsWith("animated_")) {
                    try {
                        final long documentId = Long.parseLong(reaction.substring(9));
                        final AnimatedEmojiDrawable drawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, documentId);
                        drawable.addView(cell.getValueBackupImageView());
                        cell.getValueBackupImageView().setImageDrawable(drawable);
                    } catch (Exception e) {}
                } else {
                    final TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction);
                    if (availableReaction != null) {
                        final SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(availableReaction.static_icon.thumbs, Theme.key_windowBackgroundGray, 1.0f);
                        cell.getValueBackupImageView().getImageReceiver().setImage(ImageLocation.getForDocument(availableReaction.center_icon), "100_100_lastreactframe", svgThumb, "webp", availableReaction, 1);
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    public void clearSelected() {
        selectedSets.clear();
        listView.adapter.update(true);
        checkActionMode();
    }

    public boolean hasSelected() {
        return selectedSets.size() > 0;
    }

    public int getSelectedCount() {
        return selectedSets.size();
    }

    private void checkActionMode() {
        final int selectedCount = getSelectedCount();
        final boolean actionModeShowed = actionBar.isActionModeShowed();
        if (selectedCount > 0) {
            checkActionModeIcons();
            selectedCountTextView.setNumber(selectedCount, actionModeShowed);
            if (!actionModeShowed) {
                actionBar.showActionMode();
                listView.allowReorder(true);
                if (!SharedConfig.stickersReorderingHintUsed && currentType != MediaDataController.TYPE_EMOJIPACKS) {
                    SharedConfig.setStickersReorderingHintUsed(true);
                    String stickersReorderHint = LocaleController.getString(R.string.StickersReorderHint);
                    Bulletin.make(this, new ReorderingBulletinLayout(getContext(), stickersReorderHint, null), ReorderingHintDrawable.DURATION * 2 + 250).show();
                }
            }
        } else if (actionModeShowed) {
            actionBar.hideActionMode();
            listView.allowReorder(false);
            sendReorder();
        }
    }

    private void checkActionModeIcons() {
        if (hasSelected()) {
            boolean canDelete = true;
            for (TLRPC.TL_messages_stickerSet set : sets) {
                if (selectedSets.contains(set.set.id) && set.set.official && !set.set.emojis) {
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

    private void processSelectionMenu(int which) {
        if (which == MENU_SHARE) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0, size = sets.size(); i < size; i++) {
                final TLRPC.TL_messages_stickerSet stickerSet = sets.get(i);
                if (selectedSets.contains(stickerSet.set.id)) {
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
            ArrayList<TLRPC.StickerSet> stickerSetList = new ArrayList<>(selectedSets.size());
            for (int i = 0, size = sets.size(); i < size; i++) {
                final TLRPC.TL_messages_stickerSet stickerSet = sets.get(i);
                if (selectedSets.contains(stickerSet.set.id)) {
                    stickerSetList.add(stickerSet.set);
                }
            }

            final int count = stickerSetList.size();

            switch (count) {
                case MENU_ARCHIVE:
                    break;
                case MENU_DELETE:
                    for (int i = 0, size = sets.size(); i < size; i++) {
                        final TLRPC.TL_messages_stickerSet stickerSet = sets.get(i);
                        if (selectedSets.contains(stickerSet.set.id)) {
                            processSelectionOption(which, stickerSet);
                            break;
                        }
                    }
                    clearSelected();
                    break;
                default:
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                    String buttonText;
                    if (which == MENU_DELETE) {
                        builder.setTitle(LocaleController.formatString(R.string.DeleteStickerSetsAlertTitle, LocaleController.formatPluralString("StickerSets", count)));
                        builder.setMessage(LocaleController.formatString(R.string.DeleteStickersAlertMessage, count));
                        buttonText = LocaleController.getString(R.string.Delete);
                    } else {
                        builder.setTitle(LocaleController.formatString(R.string.ArchiveStickerSetsAlertTitle, LocaleController.formatPluralString("StickerSets", count)));
                        builder.setMessage(LocaleController.formatString(R.string.ArchiveStickersAlertMessage, count));
                        buttonText = LocaleController.getString(R.string.Archive);
                    }
                    builder.setPositiveButton(buttonText, (dialog, which1) -> {
                        clearSelected();
                        MediaDataController.getInstance(currentAccount).toggleStickerSets(stickerSetList, currentType, which == MENU_DELETE ? 0 : 1, StickersActivity.this, true);
                    });
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);

                    final AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    if (which == MENU_DELETE) {
                        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
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
        } else if (which == MENU_SHARE) {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, getLinkForSet(stickerSet));
                getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.StickersShare)), 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (which == MENU_COPY) {
            try {
                final android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                final android.content.ClipData clip = android.content.ClipData.newPlainText("label", getLinkForSet(stickerSet));
                clipboard.setPrimaryClip(clip);
                BulletinFactory.createCopyLinkBulletin(StickersActivity.this).show();
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (which == MENU_REORDER) {
            toggleSelected(stickerSet);
        }
    }


    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSetCell.class, TextSettingsCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

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

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }
}

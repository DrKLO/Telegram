/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.loadVCardFromStream;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.TextWatcherImpl;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

import androidx.recyclerview.widget.RecyclerView;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatAttachAlertAudioLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {
    private static final int ANIMATOR_ID_FADE_VISIBLE = 0;

    private final BoolAnimator animatorFadeVisible = new BoolAnimator(ANIMATOR_ID_FADE_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final FrameLayout frameLayout;
    private final FragmentSearchField searchField;
    private UniversalRecyclerView listView;
    private final View fadeView;

    private DialogsActivityTopPanelLayout topPanelLayout;
    private FrameLayout fragmentContextViewWrapper;
    private FragmentContextView fragmentContextView;

    private String query;

    private int maxSelectedFiles = -1;

    private boolean sendPressed;

    private boolean loadingAudio;

    private ArrayList<MediaController.AudioEntry> audioEntries = new ArrayList<>();
    private final HashSet<MediaController.AudioEntry> selectedAudios = new HashSet<>();

    private ArrayList<MediaController.AudioEntry> foundInChats = new ArrayList<>();
    private ArrayList<MediaController.AudioEntry> foundGlobal = new ArrayList<>();

    private AudioSelectDelegate delegate;

    private MessageObject playingAudio;
    private float currentPanTranslationProgress;

    public interface AudioSelectDelegate {
        void didSelectAudio(ArrayList<MessageObject> audios, CharSequence caption, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, long payStars);
    }

    public ChatAttachAlertAudioLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);

        NotificationCenter.getInstance(parentAlert.currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(parentAlert.currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(parentAlert.currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        loadAudio();

        fadeView = new ChatAttachAlert.SearchFadeView(context, Theme.key_windowBackgroundWhite, resourcesProvider);
        fadeView.setVisibility(INVISIBLE);

        frameLayout = new FrameLayout(context);
        searchField = new ChatAttachAlert.AttachSearchField(context, parentAlert, resourcesProvider);
        searchField.setPadding(dp(4), dp(4), dp(4), dp(4));
        searchField.editText.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
                final boolean wasEmpty = TextUtils.isEmpty(query);
                query = s.toString().trim();

                AndroidUtilities.cancelRunOnUIThread(searchChatsRunnable);
                if (!TextUtils.isEmpty(query)) {
                    loadingSearchChats = query != null && query.length() >= 0;
                    if (!TextUtils.equals(lastSearchChatsQuery, query)) {
                        foundInChats.clear();
                        searchChatsNextRate = 0;
                        searchChatsHasMore = false;
                    }
                    AndroidUtilities.runOnUIThread(searchChatsRunnable, 1500);
                }

                AndroidUtilities.cancelRunOnUIThread(searchGlobalRunnable);
                if (!TextUtils.isEmpty(query)) {
                    loadingSearchGlobal = query != null && query.length() >= 3 && !TextUtils.isEmpty(MessagesController.getInstance(parentAlert.currentAccount).config.musicSearchUsername.get());
                    if (!TextUtils.equals(lastSearchGlobalQuery, query)) {
                        foundGlobal.clear();
                        searchGlobalHasMore = false;
                    }
                    AndroidUtilities.runOnUIThread(searchGlobalRunnable, 1500);
                }

                updateWithSavingScroll();
            }
        });
        searchField.editText.setHint(LocaleController.getString(R.string.SearchMusic));
        frameLayout.addView(fadeView, LayoutHelper.createFrameMatchParent());
        MarginLayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 7, 8, 7, 4);
        lp.topMargin += AndroidUtilities.statusBarHeight;
        frameLayout.addView(searchField, lp);

        topPanelLayout = new DialogsActivityTopPanelLayout(context);
        topPanelLayout.setPadding(dp(11), dp(21), dp(11), dp(21));
        topPanelLayout.setOnAnimatedHeightChangedListener(() -> {
            alert.blur3_InvalidateBlur();
            checkUi_listViewPadding();

            parentAlert.updateLayout(ChatAttachAlertAudioLayout.this, true, 0);
        });

        fragmentContextViewWrapper = new FrameLayout(context);
        topPanelLayout.addView(fragmentContextViewWrapper);
        topPanelLayout.setViewVisible(fragmentContextViewWrapper, true, false);
        fragmentContextView = new FragmentContextView(context, alert.baseFragment, frameLayout, false, resourcesProvider) {
            @Override
            public void setVisibility(int visibility) {
                topPanelLayout.setViewVisible(fragmentContextViewWrapper, visibility == VISIBLE);
            }
        };
        fragmentContextView.isInsideBubble = true;
        fragmentContextViewWrapper.addView(fragmentContextView);
        lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, 8, 0, 4);
        lp.topMargin += AndroidUtilities.statusBarHeight + dp(48 - 21);
        frameLayout.addView(topPanelLayout, lp);

        listView = new UniversalRecyclerView(context, alert.currentAccount, 0, this::fillItems, this::onItemClick, this::onItemLongClick, resourcesProvider) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= parentAlert.scrollOffsetY[0] + AndroidUtilities.dp(30) + (!parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            }
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                parentAlert.updateLayout(ChatAttachAlertAudioLayout.this, true, 0);
            }
            @Override
            protected void onLayoutUpdate() {
                parentAlert.updateLayout(ChatAttachAlertAudioLayout.this, true, 0);
            }
        };
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        iBlur3Capture = listView;
        iBlur3CaptureView = listView;
        occupyStatusBar = true;
        occupyNavigationBar = true;
        listView.setClipToPadding(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertAudioLayout.this, true, dy);
//                if (listView.scrollingByUser) {
//                    AndroidUtilities.hideKeyboard(searchField.editText);
//                }
            }
        });

        lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.LEFT | Gravity.TOP);
        addView(frameLayout, lp);

        listView.adapter.update(false);
        checkUi_listViewPadding();
    }

    private void checkUi_listViewPadding() {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = AndroidUtilities.dp(8);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (preMeasuredAvailableHeight / 3.5f);
            } else {
                padding = (preMeasuredAvailableHeight / 5 * 2);
            }
            parentAlert.setAllowNestedScroll(true);
        }
        padding += AndroidUtilities.statusBarHeight;
        padding += dp(56);
        padding += topPanelLayout.getAnimatedHeightWithPadding(0);
        listView.setPadding/*WithoutRequestLayout*/(0, padding, 0, listPaddingBottom);
    }

    private boolean needPlayMessage(MessageObject messageObject) {
        playingAudio = messageObject;
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(messageObject);
        return MediaController.getInstance().setPlaylist(arrayList, messageObject, 0);
    }

    private int LOAD_MORE_SEARCH_CHATS = 1;
    private int LOAD_MORE_SEARCH_GLOBAL = 2;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSpace(-100, dp(1)));
        int firstIndex = items.size();
        if (TextUtils.isEmpty(query)) {
            adapter.whiteSectionStart();
            for (int i = 0; i < audioEntries.size(); ++i) {
                final MediaController.AudioEntry audioEntry = audioEntries.get(i);
                audioEntry.messageObject.setQuery(null);
                items.add(
                    SharedAudioCell.Factory.as(audioEntry, this::needPlayMessage)
                        .setChecked(selectedAudios.contains(audioEntry))
                        .setId(-1)
                );
            }
            if (loadingAudio) {
                items.add(UItem.asFlicker(11, FlickerLoadingView.AUDIO_TYPE));
                items.add(UItem.asFlicker(12, FlickerLoadingView.AUDIO_TYPE));
                items.add(UItem.asFlicker(13, FlickerLoadingView.AUDIO_TYPE));
            }
            adapter.whiteSectionEnd();
        } else {
            final String q = query.toLowerCase();
            final String tq = AndroidUtilities.translitSafe(q);

            boolean addedHeader = false;
            for (int i = 0; i < audioEntries.size(); ++i) {
                final MediaController.AudioEntry audioEntry = audioEntries.get(i);

                boolean matches = false;
                if (audioEntry.author != null) {
                    final String a = audioEntry.author.toLowerCase();
                    final String ta = AndroidUtilities.translitSafe(a);
                    matches = matches || a.startsWith(q) || a.contains(" " + q) || ta.startsWith(tq) || ta.contains(" " + tq);
                }
                if (audioEntry.title != null) {
                    final String t =  audioEntry.title.toLowerCase();
                    final String tt = AndroidUtilities.translitSafe(t);
                    matches = matches || t.startsWith(q) || t.contains(" " + q) || tt.startsWith(tq) || tt.contains(" " + tq);
                }

                if (matches) {
                    if (!addedHeader) {
                        if (items.size() > firstIndex) {
                            items.add(UItem.asShadow(-97, null));
                        }
                        adapter.whiteSectionStart();
                        items.add(UItem.asHeader(10, getString(R.string.AudioSearchLocal)));
                        addedHeader = true;
                    }
                    audioEntry.messageObject.setQuery(query);
                    items.add(
                        SharedAudioCell.Factory.as(audioEntry, this::needPlayMessage)
                            .setChecked(selectedAudios.contains(audioEntry))
                            .setId(10)
                    );
                }
            }
            adapter.whiteSectionEnd();

            if (foundInChats != null && (!foundInChats.isEmpty() || searchChatsRequestId >= 0 || loadingSearchChats)) {
                if (items.size() > firstIndex) {
                    items.add(UItem.asShadow(-98, null));
                }
                adapter.whiteSectionStart();
                items.add(UItem.asHeader((searchChatsRequestId >= 0 || loadingSearchChats) && foundInChats.isEmpty() ? 25 : 20, getString(R.string.AudioSearchChats)));
                for (int i = 0; i < foundInChats.size(); ++i) {
                    final MediaController.AudioEntry audioEntry = foundInChats.get(i);
                    audioEntry.messageObject.setQuery(query);
                    items.add(
                        SharedAudioCell.Factory.as(audioEntry, this::needPlayMessage)
                            .setChecked(selectedAudios.contains(audioEntry))
                    );
                }
                if (searchChatsRequestId >= 0 || loadingSearchChats) {
                    items.add(UItem.asFlicker(21, FlickerLoadingView.AUDIO_TYPE));
                    items.add(UItem.asFlicker(22, FlickerLoadingView.AUDIO_TYPE));
                    items.add(UItem.asFlicker(23, FlickerLoadingView.AUDIO_TYPE));
                }
                if (searchChatsHasMore) {
                    items.add(UItem.asButton(LOAD_MORE_SEARCH_CHATS, R.drawable.arrow_more, getString(R.string.ShowMore)).accent());
                }
                adapter.whiteSectionEnd();
            }

            if (foundGlobal != null && (!foundGlobal.isEmpty() || searchGlobalRequestId >= 0 || loadingSearchGlobal)) {
                if (items.size() > firstIndex) {
                    items.add(UItem.asShadow(-96, null));
                }
                adapter.whiteSectionStart();
                items.add(UItem.asHeader((searchGlobalRequestId >= 0 || loadingSearchGlobal) && foundGlobal.isEmpty() ? 35 : 30, getString(R.string.AudioSearchGlobal)));
                int count = foundGlobal.size();
                for (int i = 0; i < count; ++i) {
                    final MediaController.AudioEntry audioEntry = foundGlobal.get(i);
                    audioEntry.messageObject.setQuery(query);
                    items.add(
                        SharedAudioCell.Factory.as(audioEntry, this::needPlayMessage)
                            .setChecked(selectedAudios.contains(audioEntry))
                    );
                }
                if (searchGlobalRequestId >= 0 || loadingSearchGlobal) {
                    items.add(UItem.asFlicker(31, FlickerLoadingView.AUDIO_TYPE));
                    items.add(UItem.asFlicker(32, FlickerLoadingView.AUDIO_TYPE));
                    items.add(UItem.asFlicker(33, FlickerLoadingView.AUDIO_TYPE));
                }
                if (searchGlobalHasMore) {
                    items.add(UItem.asButton(LOAD_MORE_SEARCH_GLOBAL, R.drawable.arrow_more, getString(R.string.ShowMore)).accent());
                }
                adapter.whiteSectionEnd();
            }
        }
        if (items.size() <= firstIndex && !loadingAudio) {
            if (isSearching()) {
                items.add(EmptyView.Factory.as(getString(R.string.NoAudioFound), AndroidUtilities.replaceTags(formatString(query.length() >= 3 ? R.string.NoAudioFoundInfo2 : R.string.NoAudioFoundInfo, query))));
            } else {
                items.add(EmptyView.Factory.as(getString(R.string.NoAudioFiles), getString(R.string.NoAudioFilesInfo)));
            }
        }
        items.add(UItem.asShadow(-99, null));
    }

    private void updateWithSavingScroll() {
        AndroidUtilities.cancelRunOnUIThread(updateWithSavingScrollRunnable);
        AndroidUtilities.runOnUIThread(updateWithSavingScrollRunnable);
    }

    private final Runnable updateWithSavingScrollRunnable = () -> {
        boolean atTop = !listView.canScrollVertically(-1);
        int position = -1, top = -1;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            position = listView.getChildAdapterPosition(child);
            top = child.getTop();
            if (position >= 0) break;
        }
        listView.adapter.update(true);
        if (atTop) {
            listView.layoutManager.scrollToPositionWithOffset(0, 0);
        } else if (position >= 0) {
            final int savedPosition = position;
            final int savedTop = top;
//            listView.post(() -> {
                listView.layoutManager.scrollToPositionWithOffset(savedPosition, savedTop - listView.getPaddingTop());
//            });
        }
    };

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item != null && item.id == LOAD_MORE_SEARCH_CHATS) {
            searchChats();
            return;
        } else if (item != null && item.id == LOAD_MORE_SEARCH_GLOBAL) {
            searchGlobal();
            return;
        }
        if (!(view instanceof SharedAudioCell)) {
            return;
        }
        SharedAudioCell audioCell = (SharedAudioCell) view;
        MediaController.AudioEntry audioEntry = (MediaController.AudioEntry) audioCell.getTag();
        boolean add;
        if (parentAlert.isStoryAudioPicker || parentAlert.isPollAttach) {
            sendPressed = true;
            ArrayList<MessageObject> audios = new ArrayList<>();
            audios.add(audioEntry.messageObject);
            delegate.didSelectAudio(audios, parentAlert.getCommentView().getText(), false, 0, 0, 0, false, 0);
            add = true;
        } else if (selectedAudios.contains(audioEntry)) {
            selectedAudios.remove(audioEntry);
            item.checked = false;
            audioCell.setChecked(false, true);
            add = false;
        } else {
            if (maxSelectedFiles >= 0 && selectedAudios.size() >= maxSelectedFiles) {
                showErrorBox(LocaleController.formatString(R.string.PassportUploadMaxReached, LocaleController.formatPluralString("Files", maxSelectedFiles)));
                return;
            }
            item.checked = true;
            selectedAudios.add(audioEntry);
            audioCell.setChecked(true, true);
            add = true;
        }
        parentAlert.updateCountButton(add ? 1 : 2);
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        onItemClick(item, view, position, x, y);
        return true;
    }

    public void setupBlurredSearchField(BlurredBackgroundDrawableViewFactory factory) {
        if (searchField != null) {
            searchField.setupBlurredBackground(factory.create(searchField, BlurredBackgroundProviderImpl.topPanel(resourcesProvider)));
        }
        if (topPanelLayout != null) {
            final BlurredBackgroundDrawable topPanelLayoutBackground = factory.create(topPanelLayout, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
            topPanelLayoutBackground.setRadius(dp(24));
            topPanelLayoutBackground.setPadding(dp(7));
            topPanelLayout.setBlurredBackground(topPanelLayoutBackground);
        }
    }

    @Override
    public void onDestroy() {
        onHide();
        NotificationCenter.getInstance(parentAlert.currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(parentAlert.currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(parentAlert.currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
    }

    @Override
    public void onHide() {
        if (playingAudio != null && MediaController.getInstance().isPlayingMessage(playingAudio)) {
            MediaController.getInstance().cleanupPlayer(true, true);
        }
        playingAudio = null;
    }

    private boolean isSearching() {
        return !TextUtils.isEmpty(query);
    }

    public void setMaxSelectedFiles(int value) {
        maxSelectedFiles = value;
    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    @Override
    public int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        boolean hadFirstChild = false;
        int top = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            final int position = listView.getChildAdapterPosition(child);
            if (position == 0) {
                hadFirstChild = true;
            }
            if (position >= 0 && (int) child.getTop() < top) {
                top = (int) child.getTop();
            }
        }
        if (top == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        top = top - dp(56) - (int) topPanelLayout.getAnimatedHeightWithPadding(0) - AndroidUtilities.statusBarHeight - dp(8);
        int newOffset = top > 0 && hadFirstChild ? top : 0;
        if (top >= 0 && hadFirstChild) {
            newOffset = top;
            animatorFadeVisible.setValue(false, true);
        } else {
            animatorFadeVisible.setValue(true, true);
        }
        frameLayout.setTranslationY(newOffset);
        return newOffset + dp(12);
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(4);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    public boolean onDismiss() {
        if (playingAudio != null && MediaController.getInstance().isPlayingMessage(playingAudio)) {
            MediaController.getInstance().cleanupPlayer(true, true);
        }
        return super.onDismiss();
    }

    @Override
    public int getListTopPadding() {
        return listView.getPaddingTop() - dp(56) - (int) topPanelLayout.getAnimatedHeightWithPadding(0);
    }

    private int preMeasuredAvailableHeight;
    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        preMeasuredAvailableHeight = availableHeight;
        checkUi_listViewPadding();
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        listView.layoutManager.scrollToPositionWithOffset(0, 0);
        listView.adapter.update(false);
    }

    @Override
    public void onHidden() {
        selectedAudios.clear();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof SharedAudioCell) {
                        SharedAudioCell cell = (SharedAudioCell) view;
                        MessageObject messageObject = cell.getMessage();
                        if (messageObject != null) {
                            cell.updateButtonState(false, true);
                        }
                    }
                }
            } else if (id == NotificationCenter.messagePlayingDidStart) {
                MessageObject messageObject = (MessageObject) args[0];
                if (messageObject.eventId != 0) {
                    return;
                }
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof SharedAudioCell) {
                        SharedAudioCell cell = (SharedAudioCell) view;
                        MessageObject messageObject1 = cell.getMessage();
                        if (messageObject1 != null) {
                            cell.updateButtonState(false, true);
                        }
                    }
                }
            }
        }
    }

    private void showErrorBox(String error) {
        new AlertDialog.Builder(getContext(), resourcesProvider).setTitle(LocaleController.getString(R.string.AppName)).setMessage(error).setPositiveButton(LocaleController.getString(R.string.OK), null).show();
    }

    @Override
    public int getSelectedItemsCount() {
        return selectedAudios.size();
    }

    @Override
    public boolean sendSelectedItems(boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia) {
        if (selectedAudios.size() == 0 || delegate == null || sendPressed) {
            return false;
        }
        sendPressed = true;
        ArrayList<MessageObject> audios = new ArrayList<>();
        for (MediaController.AudioEntry entry : selectedAudios) {
            audios.add(entry.messageObject);
        }
        return AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), audios.size() + parentAlert.getAdditionalMessagesCount(), payStars -> {
            delegate.didSelectAudio(audios, parentAlert.getCommentView().getText(), notify, scheduleDate, scheduleRepeatPeriod, effectId, invertMedia, payStars);
            parentAlert.dismiss(true);
        });
    }

    public ArrayList<MessageObject> getSelected() {
        ArrayList<MessageObject> audios = new ArrayList<>();
        for (MediaController.AudioEntry entry : selectedAudios) {
            audios.add(entry.messageObject);
        }
        return audios;
    }

    public void setDelegate(AudioSelectDelegate audioSelectDelegate) {
        delegate = audioSelectDelegate;
    }

    private void loadAudio() {
        loadingAudio = true;
        Utilities.globalQueue.postRunnable(() -> {
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM
            };

            final ArrayList<MediaController.AudioEntry> newAudioEntries = new ArrayList<>();
            try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE)) {
                int id = -2000000000;
                while (cursor.moveToNext()) {
                    MediaController.AudioEntry audioEntry = new MediaController.AudioEntry();
                    audioEntry.id = cursor.getInt(0);
                    audioEntry.author = cursor.getString(1);
                    audioEntry.title = cursor.getString(2);
                    audioEntry.path = cursor.getString(3);
                    audioEntry.duration = (int) (cursor.getLong(4) / 1000);
                    audioEntry.genre = cursor.getString(5);

                    File file = new File(audioEntry.path);

                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.out = true;
                    message.id = id;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.from_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = message.from_id.user_id = UserConfig.getInstance(parentAlert.currentAccount).getClientUserId();
                    message.date = (int) (System.currentTimeMillis() / 1000);
                    message.message = "";
                    message.attachPath = audioEntry.path;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = new TLRPC.TL_document();
                    message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

                    String ext = FileLoader.getFileExtension(file);

                    message.media.document.id = 0;
                    message.media.document.access_hash = 0;
                    message.media.document.file_reference = new byte[0];
                    message.media.document.date = message.date;
                    message.media.document.mime_type = "audio/" + (ext.length() > 0 ? ext : "mp3");
                    message.media.document.size = (int) file.length();
                    message.media.document.dc_id = 0;

                    TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                    attributeAudio.duration = audioEntry.duration;
                    attributeAudio.title = audioEntry.title;
                    attributeAudio.performer = audioEntry.author;
                    attributeAudio.flags |= 3;
                    message.media.document.attributes.add(attributeAudio);

                    TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                    fileName.file_name = file.getName();
                    message.media.document.attributes.add(fileName);

                    audioEntry.messageObject = new MessageObject(parentAlert.currentAccount, message, false, true);

                    newAudioEntries.add(audioEntry);
                    id--;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                loadingAudio = false;
                audioEntries = newAudioEntries;
                updateWithSavingScroll();
            });
        });
    }

    private int searchChatsRequestId = -1;
    private String lastSearchChatsQuery;
    private boolean loadingSearchChats;
    private final Runnable searchChatsRunnable = this::searchChats;
    private int searchChatsNextRate;
    private boolean searchChatsHasMore;
    private void searchChats() {
        AndroidUtilities.cancelRunOnUIThread(searchChatsRunnable);

        if (TextUtils.isEmpty(query) || query.length() < 3) {
            if (loadingSearchChats) {
                loadingSearchChats = false;
                updateWithSavingScroll();
            }
            return;
        }
        if (!TextUtils.equals(lastSearchChatsQuery, query)) {
            foundInChats.clear();
            searchChatsNextRate = 0;
            searchChatsHasMore = false;
        }
        if (!foundInChats.isEmpty() && !searchChatsHasMore) {
            if (loadingSearchChats) {
                loadingSearchChats = false;
                updateWithSavingScroll();
            }
            return;
        }

        final int account = parentAlert.currentAccount;
        final MessagesController messagesController = MessagesController.getInstance(account);
        final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);

        if (searchChatsRequestId >= 0) {
            connectionsManager.cancelRequest(searchChatsRequestId, true);
            searchChatsRequestId = -1;
        }

        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.filter = new TLRPC.TL_inputMessagesFilterMusic();
        req.q = lastSearchChatsQuery = query == null ? "" : query;
        req.limit = foundInChats.isEmpty() ? 3 : 15;
        if (foundInChats.size() > 0) {
            final MessageObject lastMessage = foundInChats.get(foundInChats.size() - 1).messageObject;
            req.offset_id = lastMessage.getId();
            req.offset_rate = searchChatsNextRate;
            req.offset_peer = messagesController.getInputPeer(MessageObject.getPeerId(lastMessage.messageOwner.peer_id));
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }

        searchChatsRequestId = connectionsManager.sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            searchChatsRequestId = -1;
            loadingSearchChats = false;
            if (res != null) {
                messagesController.putUsers(res.users, false);
                messagesController.putChats(res.chats, false);

                for (TLRPC.Message message : res.messages) {
                    final MediaController.AudioEntry audioEntry = new MediaController.AudioEntry();
                    audioEntry.messageObject = new MessageObject(account, message, false, true);

                    final TLRPC.Document document = audioEntry.messageObject.getDocument();
                    if (document == null) continue;

                    TLRPC.TL_documentAttributeAudio attr = null;
                    for (int i = 0; i < document.attributes.size(); ++i) {
                        if (document.attributes.get(i) instanceof TLRPC.TL_documentAttributeAudio) {
                            attr = (TLRPC.TL_documentAttributeAudio) document.attributes.get(i);
                            break;
                        }
                    }
                    if (attr == null) continue;

                    audioEntry.author = attr.performer;
                    audioEntry.title = attr.title;
                    audioEntry.duration = (int) attr.duration;

                    foundInChats.add(audioEntry);
                }

                searchChatsNextRate = res.next_rate;
                searchChatsHasMore = res.next_rate != 0 || (res.count > 0 && foundInChats.size() < res.count);

                updateWithSavingScroll();
            }
        });

        updateWithSavingScroll();
    }

    private int searchGlobalRequestId = -1;
    private String lastSearchGlobalQuery;
    private final Runnable searchGlobalRunnable = this::searchGlobal;
    private boolean searchGlobalHasMore;
    private TLRPC.User globalAudioBot;
    private boolean resolvingGlobalAudioBot;
    private boolean failedToResolveGlobalAudioBot;
    private String globalAudioOffset;
    private int globalAudioMessageId = -1000000000;
    private boolean loadingSearchGlobal;
    private void searchGlobal() {
        AndroidUtilities.cancelRunOnUIThread(searchGlobalRunnable);

        if (TextUtils.isEmpty(query) || query.length() < 3) {
            if (loadingSearchGlobal) {
                loadingSearchGlobal = false;
                updateWithSavingScroll();
            }
            return;
        }
        if (!TextUtils.equals(lastSearchGlobalQuery, query)) {
            foundGlobal.clear();
            searchGlobalHasMore = false;
        }

        final int account = parentAlert.currentAccount;
        final MessagesController messagesController = MessagesController.getInstance(account);
        final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);

        if (searchGlobalRequestId >= 0) {
            connectionsManager.cancelRequest(searchGlobalRequestId, true);
            searchGlobalRequestId = -1;
        }

        final String botUsername = messagesController.config.musicSearchUsername.get();
        if (TextUtils.isEmpty(botUsername)) {
            return;
        }
        if (globalAudioBot == null) {
            globalAudioBot = messagesController.getUser(botUsername);
        }
        if (globalAudioBot == null) {
            if (resolvingGlobalAudioBot || failedToResolveGlobalAudioBot) return;
            resolvingGlobalAudioBot = true;
            messagesController.getUserNameResolver().resolve(botUsername, botId -> {
                resolvingGlobalAudioBot = false;
                globalAudioBot = botId == null ? null : messagesController.getUser(botId);
                failedToResolveGlobalAudioBot = globalAudioBot == null;
                if (globalAudioBot != null) {
                    searchGlobal();
                }
            });
            return;
        }

        final TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        final TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
        req.bot = messagesController.getInputUser(globalAudioBot);
        req.peer = MessagesController.getInputPeer(self);
        req.offset = foundGlobal.isEmpty() || globalAudioOffset == null ? "" : globalAudioOffset;
        req.query = lastSearchGlobalQuery = query == null ? "" : query;
        searchGlobalRequestId = connectionsManager.sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            searchGlobalRequestId = -1;
            loadingSearchGlobal = false;
            if (res != null) {
                messagesController.putUsers(res.users, false);
                for (TLRPC.BotInlineResult r : res.results) {
                    if (r instanceof TLRPC.TL_botInlineMediaResult) {
                        final TLRPC.TL_botInlineMediaResult rMedia = (TLRPC.TL_botInlineMediaResult) r;
                        if (rMedia.document != null) {
                            final TLRPC.TL_message message = new TLRPC.TL_message();
                            message.out = true;
                            message.id = globalAudioMessageId--;
                            message.peer_id = new TLRPC.TL_peerUser();
                            message.from_id = new TLRPC.TL_peerUser();
                            message.peer_id.user_id = message.from_id.user_id = UserConfig.getInstance(account).getClientUserId();
                            message.date = (int) (System.currentTimeMillis() / 1000);
                            message.message = "";
                            message.media = new TLRPC.TL_messageMediaDocument();
                            message.media.flags |= 3;
                            message.media.document = rMedia.document;
                            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

                            final MediaController.AudioEntry audioEntry = new MediaController.AudioEntry();
                            audioEntry.messageObject = new MessageObject(account, message, false, true);

                            final TLRPC.Document document = audioEntry.messageObject.getDocument();
                            if (document == null) continue;

                            TLRPC.TL_documentAttributeAudio attr = null;
                            for (int i = 0; i < document.attributes.size(); ++i) {
                                if (document.attributes.get(i) instanceof TLRPC.TL_documentAttributeAudio) {
                                    attr = (TLRPC.TL_documentAttributeAudio) document.attributes.get(i);
                                    break;
                                }
                            }
                            if (attr == null) continue;

                            audioEntry.author = attr.performer;
                            audioEntry.title = attr.title;
                            audioEntry.duration = (int) attr.duration;

                            foundGlobal.add(audioEntry);
                        }
                    }
                }
                globalAudioOffset = res.next_offset;
                searchGlobalHasMore = !TextUtils.isEmpty(globalAudioOffset);

                updateWithSavingScroll();
            }
        });

        updateWithSavingScroll();
    }

    @Override
    public void onContainerTranslationUpdated(float currentPanTranslationY) {
        this.currentPanTranslationProgress = currentPanTranslationY;
        super.onContainerTranslationUpdated(currentPanTranslationY);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_FADE_VISIBLE) {
            fadeView.setAlpha(factor);
            fadeView.setVisibility(factor > 0 ? VISIBLE : INVISIBLE);
        }
    }

    public static final class EmptyView extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;

        public LinearLayout layout;
        public BackupImageView imageView;
        public TextView titleView;
        public TextView subtitleView;

        public EmptyView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setPadding(0, dp(42), 0, dp(42));
            setTag(RecyclerListView.TAG_NOT_SECTION);

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            imageView = new BackupImageView(context);
            imageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(120), dp(120)));
            layout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER, 0, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setGravity(Gravity.CENTER);
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 12, 32, 8));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setGravity(Gravity.CENTER);
            layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

            updateColors();
        }

        public void set(CharSequence title, CharSequence subtitle) {
            titleView.setText(title);
            subtitleView.setText(subtitle);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        }

        public static final class Factory extends UItem.UItemFactory<EmptyView> {
            static { setup(new Factory()); }

            @Override
            public boolean isShadow() {
                return true;
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public EmptyView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new EmptyView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((EmptyView) view).set(item.text, item.subtext);
            }

            public static UItem as(CharSequence title, CharSequence subtitle) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.text = title;
                item.subtext = subtitle;
                return item;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_titleTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_descriptionTextPaint, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.TextWatcherImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;

import java.io.File;
import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatAttachAlertAudioLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {
    private static final int ANIMATOR_ID_FADE_VISIBLE = 0;

    private final BoolAnimator animatorFadeVisible = new BoolAnimator(ANIMATOR_ID_FADE_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final FrameLayout frameLayout;
    private final FragmentSearchField searchField;
    private final ListAdapter listAdapter;
    private final SearchAdapter searchAdapter;
    private final LinearLayoutManager layoutManager;
    private final EmptyTextProgressView progressView;
    private final LinearLayout emptyView;
    private final ImageView emptyImageView;
    private final TextView emptyTitleTextView;
    private final TextView emptySubtitleTextView;
    private final RecyclerListView listView;
    private final View fadeView;
    private View currentEmptyView;

    private int maxSelectedFiles = -1;

    private boolean sendPressed;

    private boolean loadingAudio;

    private ArrayList<MediaController.AudioEntry> audioEntries = new ArrayList<>();
    private final ArrayList<MediaController.AudioEntry> selectedAudiosOrder = new ArrayList<>();
    private final LongSparseArray<MediaController.AudioEntry> selectedAudios = new LongSparseArray<>();

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

        fadeView = new ChatAttachAlert.SearchFadeView(context, resourcesProvider);
        fadeView.setVisibility(INVISIBLE);

        frameLayout = new FrameLayout(context);
        searchField = new ChatAttachAlert.AttachSearchField(context, parentAlert, resourcesProvider);
        searchField.setPadding(dp(4), dp(4), dp(4), dp(4));
        searchField.editText.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (text.isEmpty()) {
                    if (listView.getAdapter() != listAdapter) {
                        listView.setAdapter(listAdapter);
                        listAdapter.notifyDataSetChanged();
                    }
                }
                if (searchAdapter != null) {
                    searchAdapter.search(text);
                }
            }
        });
        searchField.editText.setHint(LocaleController.getString(R.string.SearchMusic));
        frameLayout.addView(fadeView, LayoutHelper.createFrameMatchParent());
        MarginLayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 7, 8, 7, 4);
        lp.topMargin += AndroidUtilities.statusBarHeight;
        frameLayout.addView(searchField, lp);

        progressView = new EmptyTextProgressView(context, null, resourcesProvider);
        progressView.showProgress();
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.music_empty);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setTypeface(AndroidUtilities.bold());
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= parentAlert.scrollOffsetY[0] + AndroidUtilities.dp(30) + (!parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            }
        };
        listView.setSections();
        iBlur3Capture = listView;
        iBlur3CaptureView = listView;
        occupyStatusBar = true;
        occupyNavigationBar = true;
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(9), listView) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (listView.getPaddingTop() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(7));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        listView.setOnItemClickListener((view, position) -> onItemClick(view));
        listView.setOnItemLongClickListener((view, position) -> {
            onItemClick(view);
            return true;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertAudioLayout.this, true, dy);
                updateEmptyViewPosition();
            }
        });

        searchAdapter = new SearchAdapter(context);
        lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.LEFT | Gravity.TOP);
        lp.height += AndroidUtilities.statusBarHeight;
        addView(frameLayout, lp);

        updateEmptyView();
    }

    public void setupBlurredSearchField(BlurredBackgroundDrawableViewFactory factory) {
        if (searchField != null) {
            searchField.setupBlurredBackground(factory.create(searchField, BlurredBackgroundProviderImpl.attachMenuSearch(resourcesProvider)));
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

    private void updateEmptyViewPosition() {
        if (currentEmptyView.getVisibility() != VISIBLE) {
            return;
        }
        View child = listView.getChildAt(0);
        if (child == null) {
            return;
        }
        currentEmptyView.setTranslationY((currentEmptyView.getMeasuredHeight() - getMeasuredHeight() + child.getTop()) / 2 - currentPanTranslationProgress / 2);
    }

    private void updateEmptyView() {
        if (loadingAudio) {
            currentEmptyView = progressView;
            emptyView.setVisibility(View.GONE);
        } else {
            if (listView.getAdapter() == searchAdapter) {
                emptyTitleTextView.setText(LocaleController.getString(R.string.NoAudioFound));
            } else {
                emptyTitleTextView.setText(LocaleController.getString(R.string.NoAudioFiles));
                emptySubtitleTextView.setText(LocaleController.getString(R.string.NoAudioFilesInfo));
            }
            currentEmptyView = emptyView;
            progressView.setVisibility(View.GONE);
        }

        boolean visible;
        if (listView.getAdapter() == searchAdapter) {
            visible = searchAdapter.searchResult.isEmpty();
        } else {
            visible = audioEntries.isEmpty();
        }
        currentEmptyView.setVisibility(visible ? VISIBLE :  GONE);
        updateEmptyViewPosition();
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
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            animatorFadeVisible.setValue(false, true);
        } else {
            animatorFadeVisible.setValue(true, true);
        }
        frameLayout.setTranslationY(newOffset);
        return newOffset + AndroidUtilities.dp(12);
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
        return listView.getPaddingTop();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmptyViewPosition();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = AndroidUtilities.dp(8);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = (availableHeight / 5 * 2);
            }
            parentAlert.setAllowNestedScroll(true);
        }
        padding += AndroidUtilities.statusBarHeight;
        listView.setPaddingWithoutRequestLayout(0, padding, 0, listPaddingBottom);
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        layoutManager.scrollToPositionWithOffset(0, 0);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onHidden() {
        selectedAudios.clear();
        selectedAudiosOrder.clear();
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

    private void onItemClick(View view) {
        if (!(view instanceof SharedAudioCell)) {
            return;
        }
        SharedAudioCell audioCell = (SharedAudioCell) view;
        MediaController.AudioEntry audioEntry = (MediaController.AudioEntry) audioCell.getTag();
        boolean add;
        if (parentAlert.isStoryAudioPicker) {
            sendPressed = true;
            ArrayList<MessageObject> audios = new ArrayList<>();
            audios.add(audioEntry.messageObject);
            delegate.didSelectAudio(audios, parentAlert.getCommentView().getText(), false, 0, 0, 0, false, 0);
            add = true;
        } else if (selectedAudios.indexOfKey(audioEntry.id) >= 0) {
            selectedAudios.remove(audioEntry.id);
            selectedAudiosOrder.remove(audioEntry);
            audioCell.setChecked(false, true);
            add = false;
        } else {
            if (maxSelectedFiles >= 0 && selectedAudios.size() >= maxSelectedFiles) {
                showErrorBox(LocaleController.formatString("PassportUploadMaxReached", R.string.PassportUploadMaxReached, LocaleController.formatPluralString("Files", maxSelectedFiles)));
                return;
            }
            selectedAudios.put(audioEntry.id, audioEntry);
            selectedAudiosOrder.add(audioEntry);
            audioCell.setChecked(true, true);
            add = true;
        }
        parentAlert.updateCountButton(add ? 1 : 2);
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
        for (int a = 0; a < selectedAudiosOrder.size(); a++) {
            audios.add(selectedAudiosOrder.get(a).messageObject);
        }
        return AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), audios.size() + parentAlert.getAdditionalMessagesCount(), payStars -> {
            delegate.didSelectAudio(audios, parentAlert.getCommentView().getText(), notify, scheduleDate, scheduleRepeatPeriod, effectId, invertMedia, payStars);
            parentAlert.dismiss(true);
        });
    }

    public ArrayList<MessageObject> getSelected() {
        ArrayList<MessageObject> audios = new ArrayList<>();
        for (int a = 0; a < selectedAudiosOrder.size(); a++) {
            audios.add(selectedAudiosOrder.get(a).messageObject);
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
                listAdapter.notifyDataSetChanged();
            });
        });
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return 1 + audioEntries.size() + (audioEntries.isEmpty() ? 0 : 1);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    SharedAudioCell sharedAudioCell = new SharedAudioCell(mContext, resourcesProvider) {
                        @Override
                        public boolean needPlayMessage(MessageObject messageObject) {
                            playingAudio = messageObject;
                            ArrayList<MessageObject> arrayList = new ArrayList<>();
                            arrayList.add(messageObject);
                            return MediaController.getInstance().setPlaylist(arrayList, messageObject, 0);
                        }
                    };
                    sharedAudioCell.setCheckForButtonPress(true);
                    view = sharedAudioCell;
                    break;
                case 1:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
                default:
                    view = new View(mContext);
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                position--;
                MediaController.AudioEntry audioEntry = audioEntries.get(position);

                SharedAudioCell audioCell = (SharedAudioCell) holder.itemView;
                audioCell.setTag(audioEntry);
                audioCell.setMessageObject(audioEntry.messageObject, position != audioEntries.size() - 1);
                audioCell.setChecked(selectedAudios.indexOfKey(audioEntry.id) >= 0, false);
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == getItemCount() - 1) {
                return 2;
            }
            if (i == 0) {
                return 1;
            }
            return 0;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    public class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<MediaController.AudioEntry> searchResult = new ArrayList<>();
        private Runnable searchRunnable;
        private int lastSearchId;
        private int reqId = 0;
        private int lastReqId;

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }
            if (TextUtils.isEmpty(query)) {
                if (!searchResult.isEmpty()) {
                    searchResult.clear();
                }
                if (listView.getAdapter() != listAdapter) {
                    listView.setAdapter(listAdapter);
                }
                notifyDataSetChanged();
            } else {
                int searchId = ++lastSearchId;
                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    final ArrayList<MediaController.AudioEntry> copy = new ArrayList<>(audioEntries);
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), query, lastSearchId);
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<MediaController.AudioEntry> resultArray = new ArrayList<>();

                        for (int a = 0; a < copy.size(); a++) {
                            MediaController.AudioEntry entry = copy.get(a);
                            for (int b = 0; b < search.length; b++) {
                                String q = search[b];

                                boolean ok = false;
                                if (entry.author != null) {
                                    ok = entry.author.toLowerCase().contains(q);
                                }
                                if (!ok && entry.title != null) {
                                    ok = entry.title.toLowerCase().contains(q);
                                }
                                if (ok) {
                                    resultArray.add(entry);
                                    break;
                                }
                            }
                        }

                        updateSearchResults(resultArray, query, searchId);
                    });
                }, 300);
            }
        }

        private void updateSearchResults(final ArrayList<MediaController.AudioEntry> result, String query, final int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                if (searchId != -1 && listView.getAdapter() != searchAdapter) {
                    listView.setAdapter(searchAdapter);
                }
                if (listView.getAdapter() == searchAdapter) {
                    emptySubtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("NoAudioFoundInfo", R.string.NoAudioFoundInfo, query)));
                }
                searchResult = result;
                notifyDataSetChanged();
            });
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEmptyView();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return 1 + searchResult.size() + (searchResult.isEmpty() ? 0 : 1);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    SharedAudioCell sharedAudioCell = new SharedAudioCell(mContext, resourcesProvider) {
                        @Override
                        public boolean needPlayMessage(MessageObject messageObject) {
                            playingAudio = messageObject;
                            ArrayList<MessageObject> arrayList = new ArrayList<>();
                            arrayList.add(messageObject);
                            return MediaController.getInstance().setPlaylist(arrayList, messageObject, 0);
                        }
                    };
                    sharedAudioCell.setCheckForButtonPress(true);
                    view = sharedAudioCell;
                    break;
                case 1:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
                default:
                    view = new View(mContext);
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                position--;
                MediaController.AudioEntry audioEntry = searchResult.get(position);

                SharedAudioCell audioCell = (SharedAudioCell) holder.itemView;
                audioCell.setTag(audioEntry);
                audioCell.setMessageObject(audioEntry.messageObject, position != searchResult.size() - 1);
                audioCell.setChecked(selectedAudios.indexOfKey(audioEntry.id) >= 0, false);
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == getItemCount() - 1) {
                return 2;
            }
            if (i == 0) {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public void onContainerTranslationUpdated(float currentPanTranslationY) {
        this.currentPanTranslationProgress = currentPanTranslationY;
        super.onContainerTranslationUpdated(currentPanTranslationY);
        updateEmptyViewPosition();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_FADE_VISIBLE) {
            fadeView.setAlpha(factor);
            fadeView.setVisibility(factor > 0 ? VISIBLE : INVISIBLE);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(emptyTitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(emptySubtitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(progressView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_titleTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_descriptionTextPaint, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }
}

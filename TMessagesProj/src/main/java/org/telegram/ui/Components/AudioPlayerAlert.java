/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AudioPlayerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener {

    private ActionBar actionBar;
    private View actionBarShadow;
    private View playerShadow;
    private boolean searchWas;
    private boolean searching;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;

    private FrameLayout playerLayout;
    private CoverContainer coverContainer;
    private ClippingTextViewSwitcher titleTextView;
    private RLottieImageView prevButton;
    private RLottieImageView nextButton;
    private ClippingTextViewSwitcher authorTextView;
    private ActionBarMenuItem optionsButton;
    private LineProgressView progressView;
    private SeekBarView seekBarView;
    private SimpleTextView timeTextView;
    private ActionBarMenuItem playbackSpeedButton;
    private ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[4];
    private TextView durationTextView;
    private ActionBarMenuItem repeatButton;
    private ActionBarMenuSubItem repeatSongItem;
    private ActionBarMenuSubItem repeatListItem;
    private ActionBarMenuSubItem shuffleListItem;
    private ActionBarMenuSubItem reverseOrderItem;
    private ImageView playButton;
    private PlayPauseDrawable playPauseDrawable;
    private FrameLayout blurredView;
    private BackupImageView bigAlbumConver;
    private ActionBarMenuItem searchItem;
    private boolean blurredAnimationInProgress;
    private View[] buttons = new View[5];

    private boolean draggingSeekBar;

    private long lastBufferedPositionCheck;
    private boolean currentAudioFinishedLoading;

    private boolean scrollToSong = true;

    private int searchOpenPosition = -1;
    private int searchOpenOffset;

    private ArrayList<MessageObject> playlist;
    private MessageObject lastMessageObject;

    private int scrollOffsetY = Integer.MAX_VALUE;
    private int topBeforeSwitch;

    private boolean inFullSize;

    private String currentFile;

    private AnimatorSet actionBarAnimation;

    private int lastTime;
    private int lastDuration;

    private int TAG;

    private LaunchActivity parentActivity;
    int rewindingState;
    float rewindingProgress = -1;

    int rewindingForwardPressedCount;
    long lastRewindingTime;
    long lastUpdateRewindingPlayerTime;

    private final static int menu_speed_slow = 1;
    private final static int menu_speed_normal = 2;
    private final static int menu_speed_fast = 3;
    private final static int menu_speed_veryfast = 4;

    private final Runnable forwardSeek = new Runnable() {
        @Override
        public void run() {
            long duration = MediaController.getInstance().getDuration();
            if (duration == 0 || duration == C.TIME_UNSET) {
                lastRewindingTime = System.currentTimeMillis();
                return;
            }
            float currentProgress = rewindingProgress;

            long t = System.currentTimeMillis();
            long dt = t - lastRewindingTime;
            lastRewindingTime = t;
            long updateDt = t - lastUpdateRewindingPlayerTime;
            if (rewindingForwardPressedCount == 1) {
                dt = dt * 3 - dt;
            } else if (rewindingForwardPressedCount == 2) {
                dt = dt * 6 - dt;
            } else {
                dt = dt * 12 - dt;
            }
            long currentTime = (long) (duration * currentProgress + dt);
            currentProgress = currentTime / (float) duration;
            if (currentProgress < 0) {
                currentProgress = 0;
            }
            rewindingProgress = currentProgress;
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && messageObject.isMusic()) {
                if (!MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().getPlayingMessageObject().audioProgress = rewindingProgress;
                }
                updateProgress(messageObject);
            }
            if (rewindingState == 1 && rewindingForwardPressedCount > 0 && MediaController.getInstance().isMessagePaused()) {
                if (updateDt > 200 || rewindingProgress == 0) {
                    lastUpdateRewindingPlayerTime = t;
                    MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), currentProgress);
                }
                if (rewindingForwardPressedCount > 0 && rewindingProgress > 0) {
                    AndroidUtilities.runOnUIThread(forwardSeek, 16);
                }
            }
        }
    };

    public AudioPlayerAlert(final Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);

        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null) {
            currentAccount = messageObject.currentAccount;
        } else {
            currentAccount = UserConfig.selectedAccount;
        }

        parentActivity = (LaunchActivity) context;

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.musicDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.moreMusicDidLoad);

        containerView = new FrameLayout(context) {

            private RectF rect = new RectF();
            private boolean ignoreLayout = false;
            private int lastMeasturedHeight;
            private int lastMeasturedWidth;

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                int w = MeasureSpec.getSize(widthMeasureSpec);
                if (totalHeight != lastMeasturedHeight || w != lastMeasturedWidth) {
                    if (blurredView.getTag() != null) {
                        showAlbumCover(false, false);
                    }
                    lastMeasturedWidth = w;
                    lastMeasturedHeight = totalHeight;
                }
                ignoreLayout = true;
                if (Build.VERSION.SDK_INT >= 21 && !isFullscreen) {
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                }
                playerLayout.setVisibility(searchWas || keyboardVisible ? INVISIBLE : VISIBLE);
                playerShadow.setVisibility(playerLayout.getVisibility());
                int availableHeight = totalHeight - getPaddingTop();

                LayoutParams layoutParams = (LayoutParams) listView.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                layoutParams = (LayoutParams) blurredView.getLayoutParams();
                layoutParams.topMargin = -getPaddingTop();

                int contentSize = AndroidUtilities.dp(179);
                if (playlist.size() > 1) {
                    contentSize += backgroundPaddingTop + playlist.size() * AndroidUtilities.dp(56);
                }
                int padding;
                if (searching || keyboardVisible) {
                    padding = AndroidUtilities.dp(8);
                } else {
                    padding = (contentSize < availableHeight ? availableHeight - contentSize : availableHeight - (int) (availableHeight / 5 * 3.5f)) + AndroidUtilities.dp(8);
                    if (padding > availableHeight - AndroidUtilities.dp(179 + 150)) {
                        padding = availableHeight - AndroidUtilities.dp(179 + 150);
                    }
                    if (padding < 0) {
                        padding = 0;
                    }
                }
                if (listView.getPaddingTop() != padding) {
                    listView.setPadding(0, padding, 0, searching && keyboardVisible ? 0 : listView.getPaddingBottom());
                }
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
                inFullSize = getMeasuredHeight() >= totalHeight;
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
                updateEmptyViewPosition();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && actionBar.getAlpha() == 0.0f) {
                    boolean dismiss;
                    if (listAdapter.getItemCount() > 0) {
                        dismiss = ev.getY() < scrollOffsetY + AndroidUtilities.dp(12);
                    } else {
                        dismiss = ev.getY() < getMeasuredHeight() - AndroidUtilities.dp(179 + 12);
                    }
                    if (dismiss) {
                        dismiss();
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (playlist.size() <= 1) {
                    shadowDrawable.setBounds(0, getMeasuredHeight() - playerLayout.getMeasuredHeight() - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                } else {
                    int offset = AndroidUtilities.dp(13);
                    int top = scrollOffsetY - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1) {
                        top += listView.getTranslationY();
                    }
                    int y = top + AndroidUtilities.dp(20);

                    int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
                    float rad = 1.0f;

                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        float toMove = offset + AndroidUtilities.dp(11 - 7);
                        float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);
                        float availableToMove = ActionBar.getCurrentActionBarHeight() - toMove;

                        int diff = (int) (availableToMove * moveProgress);
                        top -= diff;
                        y -= diff;
                        height += diff;
                        rad = 1.0f - moveProgress;
                    }

                    if (Build.VERSION.SDK_INT >= 21) {
                        top += AndroidUtilities.statusBarHeight;
                        y += AndroidUtilities.statusBarHeight;
                    }

                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                    shadowDrawable.draw(canvas);

                    if (rad != 1.0f) {
                        Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_dialogBackground));
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                    }

                    if (rad != 0) {
                        float alphaProgress = 1.0f;
                        int w = AndroidUtilities.dp(36);
                        rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                        int color = getThemedColor(Theme.key_sheet_scrollUp);
                        int alpha = Color.alpha(color);
                        Theme.dialogs_onlineCirclePaint.setColor(color);
                        Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                    }

                    int color1 = getThemedColor(Theme.key_dialogBackground);
                    int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(this, new Bulletin.Delegate() {
                    @Override
                    public int getBottomOffset(int tag) {
                        return playerLayout.getHeight();
                    }
                });
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(this);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        actionBar = new ActionBar(context, resourcesProvider) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setBackgroundColor(getThemedColor(Theme.key_player_actionBar));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(getThemedColor(Theme.key_player_actionBarTitle), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_player_actionBarSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_player_actionBarTitle));
        actionBar.setTitle(LocaleController.getString("AttachMusic", R.string.AttachMusic));
        actionBar.setSubtitleColor(getThemedColor(Theme.key_player_actionBarSubtitle));
        actionBar.setOccupyStatusBar(false);
        actionBar.setAlpha(0.0f);

        if (messageObject != null && !MediaController.getInstance().currentPlaylistIsGlobalSearch()) {
            long did = messageObject.getDialogId();
            if (DialogObject.isEncryptedDialog(did)) {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(did));
                if (encryptedChat != null) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                    if (user != null) {
                        actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                    }
                }
            } else if (DialogObject.isUserDialog(did)) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                if (user != null) {
                    actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                if (chat != null) {
                    actionBar.setTitle(chat.title);
                }
            }
        }

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchCollapse() {
                if (searching) {
                    searchWas = false;
                    searching = false;
                    setAllowNestedScroll(true);
                    listAdapter.search(null);
                }
            }

            @Override
            public void onSearchExpand() {
                searchOpenPosition = layoutManager.findLastVisibleItemPosition();
                View firstVisView = layoutManager.findViewByPosition(searchOpenPosition);
                searchOpenOffset = firstVisView == null ? 0 : firstVisView.getTop();
                searching = true;
                setAllowNestedScroll(false);
                listAdapter.notifyDataSetChanged();
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (editText.length() > 0) {
                    listAdapter.search(editText.getText().toString());
                } else {
                    searchWas = false;
                    listAdapter.search(null);
                }
            }
        });
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setHint(LocaleController.getString("Search", R.string.Search));
        editText.setTextColor(getThemedColor(Theme.key_player_actionBarTitle));
        editText.setHintTextColor(getThemedColor(Theme.key_player_time));
        editText.setCursorColor(getThemedColor(Theme.key_player_actionBarTitle));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                } else {
                    onSubItemClick(id);
                }
            }
        });

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundResource(R.drawable.header_shadow);

        playerShadow = new View(context);
        playerShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        
        playerLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (playbackSpeedButton != null && durationTextView != null) {
                    int x = durationTextView.getLeft() - AndroidUtilities.dp(4) - playbackSpeedButton.getMeasuredWidth();
                    playbackSpeedButton.layout(x, playbackSpeedButton.getTop(), x + playbackSpeedButton.getMeasuredWidth(), playbackSpeedButton.getBottom());
                }
            }
        };

        coverContainer = new CoverContainer(context) {

            private long pressTime;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (getImageReceiver().hasBitmapImage()) {
                        showAlbumCover(true, true);
                        pressTime = SystemClock.elapsedRealtime();
                    }
                } else if (action != MotionEvent.ACTION_MOVE) {
                    if (SystemClock.elapsedRealtime() - pressTime >= 400) {
                        showAlbumCover(false, true);
                    }
                }
                return true;
            }

            @Override
            protected void onImageUpdated(ImageReceiver imageReceiver) {
                if (blurredView.getTag() != null) {
                    bigAlbumConver.setImageBitmap(imageReceiver.getBitmap());
                }
            }
        };
        playerLayout.addView(coverContainer, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.RIGHT, 0, 20, 20, 0));

        titleTextView = new ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                final TextView textView = new TextView(context);
                textView.setTextColor(getThemedColor(Theme.key_player_actionBarTitle));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                return textView;
            }
        };
        playerLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 20, 72, 0));

        authorTextView = new ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                final TextView textView = new TextView(context);
                textView.setTextColor(getThemedColor(Theme.key_player_time));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), AndroidUtilities.dp(1));
                textView.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));

                textView.setOnClickListener(view -> {
                    int dialogsCount = MessagesController.getInstance(currentAccount).getTotalDialogsCount();
                    if (dialogsCount <= 10 || TextUtils.isEmpty(textView.getText().toString())) {
                        return;
                    }
                    String query = textView.getText().toString();
                    if (parentActivity.getActionBarLayout().getLastFragment() instanceof DialogsActivity) {
                        DialogsActivity dialogsActivity = (DialogsActivity) parentActivity.getActionBarLayout().getLastFragment();
                        if (!dialogsActivity.onlyDialogsAdapter()) {
                            dialogsActivity.setShowSearch(query, 4);
                            dismiss();
                            return;
                        }
                    }
                    DialogsActivity fragment = new DialogsActivity(null);
                    fragment.setSearchString(query);
                    fragment.setInitialSearchType(4);
                    parentActivity.presentFragment(fragment, false, false);
                    dismiss();
                });
                return textView;
            }
        };
        playerLayout.addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 14, 47, 72, 0));

        seekBarView = new SeekBarView(context, resourcesProvider) {
            @Override
            boolean onTouch(MotionEvent ev) {
                if (rewindingState != 0) {
                    return false;
                }
                return super.onTouch(ev);
            }
        };
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (stop) {
                    MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), progress);
                }
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isMusic()) {
                    updateProgress(messageObject);
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
                draggingSeekBar = pressed;
            }

            @Override
            public CharSequence getContentDescription() {
                final String time = LocaleController.formatPluralString("Minutes", lastTime / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastTime % 60);
                final String totalTime = LocaleController.formatPluralString("Minutes", lastDuration / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastDuration % 60);
                return LocaleController.formatString("AccDescrPlayerDuration", R.string.AccDescrPlayerDuration, time, totalTime);
            }
        });
        seekBarView.setReportChanges(true);
        playerLayout.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 5, 70, 5, 0));

        progressView = new LineProgressView(context);
        progressView.setVisibility(View.INVISIBLE);
        progressView.setBackgroundColor(getThemedColor(Theme.key_player_progressBackground));
        progressView.setProgressColor(getThemedColor(Theme.key_player_progress));
        playerLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.TOP | Gravity.LEFT, 21, 90, 21, 0));

        timeTextView = new SimpleTextView(context);
        timeTextView.setTextSize(12);
        timeTextView.setText("0:00");
        timeTextView.setTextColor(getThemedColor(Theme.key_player_time));
        timeTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        playerLayout.addView(timeTextView, LayoutHelper.createFrame(100, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 98, 0, 0));

        durationTextView = new TextView(context);
        durationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        durationTextView.setTextColor(getThemedColor(Theme.key_player_time));
        durationTextView.setGravity(Gravity.CENTER);
        durationTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        playerLayout.addView(durationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 96, 20, 0));

        playbackSpeedButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_dialogTextBlack), false, resourcesProvider);
        playbackSpeedButton.setLongClickEnabled(false);
        playbackSpeedButton.setShowSubmenuByMove(false);
        playbackSpeedButton.setAdditionalYOffset(-AndroidUtilities.dp(224));
        playbackSpeedButton.setContentDescription(LocaleController.getString("AccDescrPlayerSpeed", R.string.AccDescrPlayerSpeed));
        playbackSpeedButton.setDelegate(id -> {
            float oldSpeed = MediaController.getInstance().getPlaybackSpeed(true);
            if (id == menu_speed_slow) {
                MediaController.getInstance().setPlaybackSpeed(true, 0.5f);
            } else if (id == menu_speed_normal) {
                MediaController.getInstance().setPlaybackSpeed(true, 1.0f);
            } else if (id == menu_speed_fast) {
                MediaController.getInstance().setPlaybackSpeed(true, 1.5f);
            } else {
                MediaController.getInstance().setPlaybackSpeed(true, 1.8f);
            }
            updatePlaybackButton();
        });
        speedItems[0] = playbackSpeedButton.addSubItem(menu_speed_slow, R.drawable.msg_speed_0_5, LocaleController.getString("SpeedSlow", R.string.SpeedSlow));
        speedItems[1] = playbackSpeedButton.addSubItem(menu_speed_normal, R.drawable.msg_speed_1, LocaleController.getString("SpeedNormal", R.string.SpeedNormal));
        speedItems[2] = playbackSpeedButton.addSubItem(menu_speed_fast, R.drawable.msg_speed_1_5, LocaleController.getString("SpeedFast", R.string.SpeedFast));
        speedItems[3] = playbackSpeedButton.addSubItem(menu_speed_veryfast, R.drawable.msg_speed_2, LocaleController.getString("SpeedVeryFast", R.string.SpeedVeryFast));
        if (AndroidUtilities.density >= 3.0f) {
            playbackSpeedButton.setPadding(0, 1, 0, 0);
        }
        playbackSpeedButton.setAdditionalXOffset(AndroidUtilities.dp(8));
        playbackSpeedButton.setShowedFromBottom(true);
        playerLayout.addView(playbackSpeedButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 0, 86, 20, 0));
        playbackSpeedButton.setOnClickListener(v -> {
            float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
            if (Math.abs(currentPlaybackSpeed - 1.0f) > 0.001f) {
                MediaController.getInstance().setPlaybackSpeed(true, 1.0f);
            } else {
                MediaController.getInstance().setPlaybackSpeed(true, MediaController.getInstance().getFastPlaybackSpeed(true));
            }
            updatePlaybackButton();
        });
        playbackSpeedButton.setOnLongClickListener(view -> {
            playbackSpeedButton.toggleSubMenu();
            return true;
        });
        updatePlaybackButton();

        FrameLayout bottomView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int dist = ((right - left) - AndroidUtilities.dp(8 + 48 * 5)) / 4;
                for (int a = 0; a < 5; a++) {
                    int l = AndroidUtilities.dp(4 + 48 * a) + dist * a;
                    int t = AndroidUtilities.dp(9);
                    buttons[a].layout(l, t, l + buttons[a].getMeasuredWidth(), t + buttons[a].getMeasuredHeight());
                }
            }
        };
        playerLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66, Gravity.TOP | Gravity.LEFT, 0, 111, 0, 0));

        buttons[0] = repeatButton = new ActionBarMenuItem(context, null, 0, 0, false, resourcesProvider);
        repeatButton.setLongClickEnabled(false);
        repeatButton.setShowSubmenuByMove(false);
        repeatButton.setAdditionalYOffset(-AndroidUtilities.dp(166));
        if (Build.VERSION.SDK_INT >= 21) {
            repeatButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(18)));
        }
        bottomView.addView(repeatButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        repeatButton.setOnClickListener(v -> {
            updateSubMenu();
            repeatButton.toggleSubMenu();
        });
        repeatSongItem = repeatButton.addSubItem(3, R.drawable.player_new_repeatone, LocaleController.getString("RepeatSong", R.string.RepeatSong));
        repeatListItem = repeatButton.addSubItem(4, R.drawable.player_new_repeatall, LocaleController.getString("RepeatList", R.string.RepeatList));
        shuffleListItem = repeatButton.addSubItem(2, R.drawable.player_new_shuffle, LocaleController.getString("ShuffleList", R.string.ShuffleList));
        reverseOrderItem = repeatButton.addSubItem(1, R.drawable.player_new_order, LocaleController.getString("ReverseOrder", R.string.ReverseOrder));
        repeatButton.setShowedFromBottom(true);

        repeatButton.setDelegate(id -> {
            if (id == 1 || id == 2) {
                boolean oldReversed = SharedConfig.playOrderReversed;
                if (SharedConfig.playOrderReversed && id == 1 || SharedConfig.shuffleMusic && id == 2) {
                    MediaController.getInstance().setPlaybackOrderType(0);
                } else {
                    MediaController.getInstance().setPlaybackOrderType(id);
                }
                listAdapter.notifyDataSetChanged();
                if (oldReversed != SharedConfig.playOrderReversed) {
                    listView.stopScroll();
                    scrollToCurrentSong(false);
                }
            } else {
                if (id == 4) {
                    if (SharedConfig.repeatMode == 1) {
                        SharedConfig.setRepeatMode(0);
                    } else {
                        SharedConfig.setRepeatMode(1);
                    }
                } else {
                    if (SharedConfig.repeatMode == 2) {
                        SharedConfig.setRepeatMode(0);
                    } else {
                        SharedConfig.setRepeatMode(2);
                    }
                }
            }
            updateRepeatButton();
        });

        final int iconColor = getThemedColor(Theme.key_player_button);
        float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        buttons[1] = prevButton = new RLottieImageView(context) {
            float startX;
            float startY;

            int pressedCount = 0;

            long lastTime;
            long lastUpdateTime;

            private final Runnable pressedRunnable = new Runnable() {
                @Override
                public void run() {
                    pressedCount++;
                    if (pressedCount == 1) {
                        rewindingState = -1;
                        rewindingProgress = MediaController.getInstance().getPlayingMessageObject().audioProgress;
                        lastTime = System.currentTimeMillis();
                        AndroidUtilities.runOnUIThread(this, 2000);
                        AndroidUtilities.runOnUIThread(backSeek);
                    } else if (pressedCount == 2) {
                        AndroidUtilities.runOnUIThread(this, 2000);
                    }
                }
            };

            private final Runnable backSeek = new Runnable() {
                @Override
                public void run() {
                    long duration = MediaController.getInstance().getDuration();
                    if (duration == 0 || duration == C.TIME_UNSET) {
                        lastTime = System.currentTimeMillis();
                        return;
                    }
                    float currentProgress = rewindingProgress;

                    long t = System.currentTimeMillis();
                    long dt = t - lastTime;
                    lastTime = t;
                    long updateDt = t - lastUpdateTime;
                    if (pressedCount == 1) {
                        dt *= 3;
                    } else if (pressedCount == 2) {
                        dt *= 6;
                    } else {
                        dt *= 12;
                    }
                    long currentTime = (long) (duration * currentProgress - dt);
                    currentProgress = currentTime / (float) duration;
                    if (currentProgress < 0) {
                        currentProgress = 0;
                    }
                    rewindingProgress = currentProgress;
                    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (messageObject != null && messageObject.isMusic()) {
                        updateProgress(messageObject);
                    }
                    if (rewindingState == -1 && pressedCount > 0) {
                        if (updateDt > 200 || rewindingProgress == 0) {
                            lastUpdateTime = t;
                            if (rewindingProgress == 0) {
                                MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), 0);
                                MediaController.getInstance().pauseByRewind();
                            } else {
                                MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), currentProgress);
                            }
                        }
                        if (pressedCount > 0 && rewindingProgress > 0) {
                            AndroidUtilities.runOnUIThread(backSeek, 16);
                        }
                    }
                }
            };

            long startTime;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (seekBarView.isDragging() || rewindingState == 1) {
                    return false;
                }
                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = x;
                        startY = y;
                        startTime = System.currentTimeMillis();
                        rewindingState = 0;
                        AndroidUtilities.runOnUIThread(pressedRunnable, 300);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getBackground() != null) {
                            getBackground().setHotspot(startX, startY);
                        }
                        setPressed(true);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - startX;
                        float dy = y - startY;

                        if ((dx * dx + dy * dy) > touchSlop * touchSlop && rewindingState == 0) {
                            AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                            setPressed(false);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                        AndroidUtilities.cancelRunOnUIThread(backSeek);
                        if (rewindingState == 0 && event.getAction() == MotionEvent.ACTION_UP && (System.currentTimeMillis() - startTime < 300)) {
                            MediaController.getInstance().playPreviousMessage();
                            prevButton.setProgress(0f);
                            prevButton.playAnimation();
                        }
                        if (pressedCount > 0) {
                            lastUpdateTime = 0;
                            backSeek.run();
                            MediaController.getInstance().resumeByRewind();
                        }
                        rewindingProgress = -1;
                        setPressed(false);
                        rewindingState = 0;
                        pressedCount = 0;
                        break;
                }
                return true;
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        };
        prevButton.setScaleType(ImageView.ScaleType.CENTER);
        prevButton.setAnimation(R.raw.player_prev, 20, 20);
        prevButton.setLayerColor("Triangle 3.**", iconColor);
        prevButton.setLayerColor("Triangle 4.**", iconColor);
        prevButton.setLayerColor("Rectangle 4.**", iconColor);
        if (Build.VERSION.SDK_INT >= 21) {
            prevButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(22)));
        }
        bottomView.addView(prevButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        prevButton.setContentDescription(LocaleController.getString("AccDescrPrevious", R.string.AccDescrPrevious));

        buttons[2] = playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageDrawable(playPauseDrawable = new PlayPauseDrawable(28));
        playPauseDrawable.setPause(!MediaController.getInstance().isMessagePaused(), false);
        playButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            playButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(24)));
        }
        bottomView.addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        playButton.setOnClickListener(v -> {
            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                return;
            }
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
            } else {
                MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            }
        });

        buttons[3] = nextButton = new RLottieImageView(context) {

            float startX;
            float startY;
            boolean pressed;

            private final Runnable pressedRunnable = new Runnable() {
                @Override
                public void run() {
                    if (MediaController.getInstance().getPlayingMessageObject() == null) {
                        return;
                    }
                    rewindingForwardPressedCount++;
                    if (rewindingForwardPressedCount == 1) {
                        pressed = true;
                        rewindingState = 1;
                        if (MediaController.getInstance().isMessagePaused()) {
                            startForwardRewindingSeek();
                        } else if (rewindingState == 1) {
                            AndroidUtilities.cancelRunOnUIThread(forwardSeek);
                            lastUpdateRewindingPlayerTime = 0;
                        }
                        MediaController.getInstance().setPlaybackSpeed(true, 4);
                        AndroidUtilities.runOnUIThread(this, 2000);
                    } else if (rewindingForwardPressedCount == 2) {
                        MediaController.getInstance().setPlaybackSpeed(true, 7);
                        AndroidUtilities.runOnUIThread(this, 2000);
                    } else {
                        MediaController.getInstance().setPlaybackSpeed(true, 13);
                    }
                }
            };

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (seekBarView.isDragging() || rewindingState == -1) {
                    return false;
                }
                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressed = false;
                        startX = x;
                        startY = y;
                        AndroidUtilities.runOnUIThread(pressedRunnable, 300);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getBackground() != null) {
                            getBackground().setHotspot(startX, startY);
                        }
                        setPressed(true);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - startX;
                        float dy = y - startY;

                        if ((dx * dx + dy * dy) > touchSlop * touchSlop && !pressed) {
                            AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                            setPressed(false);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        if (!pressed && event.getAction() == MotionEvent.ACTION_UP && isPressed()) {
                            MediaController.getInstance().playNextMessage();
                            nextButton.setProgress(0f);
                            nextButton.playAnimation();
                        }
                        AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                        if (rewindingForwardPressedCount > 0) {
                            MediaController.getInstance().setPlaybackSpeed(true, 1f);
                            if (MediaController.getInstance().isMessagePaused()) {
                                lastUpdateRewindingPlayerTime = 0;
                                forwardSeek.run();
                            }
                        }
                        rewindingState = 0;
                        setPressed(false);
                        rewindingForwardPressedCount = 0;
                        rewindingProgress = -1;
                        break;
                }
                return true;
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

        };
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setAnimation(R.raw.player_prev, 20, 20);
        nextButton.setLayerColor("Triangle 3.**", iconColor);
        nextButton.setLayerColor("Triangle 4.**", iconColor);
        nextButton.setLayerColor("Rectangle 4.**", iconColor);
        nextButton.setRotation(180f);
        if (Build.VERSION.SDK_INT >= 21) {
            nextButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(22)));
        }
        bottomView.addView(nextButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        nextButton.setContentDescription(LocaleController.getString("Next", R.string.Next));

        buttons[4] = optionsButton = new ActionBarMenuItem(context, null, 0, iconColor, false, resourcesProvider);
        optionsButton.setLongClickEnabled(false);
        optionsButton.setShowSubmenuByMove(false);
        optionsButton.setIcon(R.drawable.ic_ab_other);
        optionsButton.setSubMenuOpenSide(2);
        optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(157));
        if (Build.VERSION.SDK_INT >= 21) {
            optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(18)));
        }
        bottomView.addView(optionsButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        optionsButton.addSubItem(1, R.drawable.msg_forward, LocaleController.getString("Forward", R.string.Forward));
        optionsButton.addSubItem(2, R.drawable.msg_shareout, LocaleController.getString("ShareFile", R.string.ShareFile));
        optionsButton.addSubItem(5, R.drawable.msg_download, LocaleController.getString("SaveToMusic", R.string.SaveToMusic));
        optionsButton.addSubItem(4, R.drawable.msg_message, LocaleController.getString("ShowInChat", R.string.ShowInChat));
        optionsButton.setShowedFromBottom(true);
        optionsButton.setOnClickListener(v -> optionsButton.toggleSubMenu());
        optionsButton.setDelegate(this::onSubItemClick);
        optionsButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.music_empty);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setText(LocaleController.getString("NoAudioFound", R.string.NoAudioFound));
        emptyTitleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context) {

            boolean ignoreLayout;

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);

                if (searchOpenPosition != -1 && !actionBar.isSearchFieldVisible()) {
                    ignoreLayout = true;
                    layoutManager.scrollToPositionWithOffset(searchOpenPosition, searchOpenOffset - listView.getPaddingTop());
                    super.onLayout(false, l, t, r, b);
                    ignoreLayout = false;
                    searchOpenPosition = -1;
                } else if (scrollToSong) {
                    scrollToSong = false;
                    ignoreLayout = true;
                    if (scrollToCurrentSong(true)) {
                        super.onLayout(false, l, t, r, b);
                    }
                    ignoreLayout = false;
                }
            }

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y < playerLayout.getY() - listView.getTop();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof AudioPlayerCell) {
                ((AudioPlayerCell) view).didPressedButton();
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13);
                    int top = scrollOffsetY - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() && listView.canScrollVertically(1)) {
                        View child = listView.getChildAt(0);
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(7)) {
                            listView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(7));
                        }
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
                updateEmptyViewPosition();

                if (!searchWas) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    int totalItemCount = recyclerView.getAdapter().getItemCount();

                    MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (SharedConfig.playOrderReversed) {
                        if (firstVisibleItem < 10) {
                            MediaController.getInstance().loadMoreMusic();
                        }
                    } else {
                        if (firstVisibleItem + visibleItemCount > totalItemCount - 10) {
                            MediaController.getInstance().loadMoreMusic();
                        }
                    }
                }
            }
        });

        playlist = MediaController.getInstance().getPlaylist();
        listAdapter.notifyDataSetChanged();

        containerView.addView(playerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 179, Gravity.LEFT | Gravity.BOTTOM));
        containerView.addView(playerShadow, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM));
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) playerShadow.getLayoutParams();
        layoutParams.bottomMargin = AndroidUtilities.dp(179);
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));
        containerView.addView(actionBar);

        blurredView = new FrameLayout(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (blurredView.getTag() != null) {
                    showAlbumCover(false, true);
                }
                return true;
            }
        };
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.INVISIBLE);
        getContainer().addView(blurredView);

        bigAlbumConver = new BackupImageView(context);
        bigAlbumConver.setAspectFit(true);
        bigAlbumConver.setRoundRadius(AndroidUtilities.dp(8));
        bigAlbumConver.setScaleX(0.9f);
        bigAlbumConver.setScaleY(0.9f);
        blurredView.addView(bigAlbumConver, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 30, 30, 30, 30));

        updateTitle(false);
        updateRepeatButton();
        updateEmptyView();
    }

    private void startForwardRewindingSeek() {
        if (rewindingState == 1) {
            lastRewindingTime = System.currentTimeMillis();
            rewindingProgress = MediaController.getInstance().getPlayingMessageObject().audioProgress;
            AndroidUtilities.cancelRunOnUIThread(forwardSeek);
            AndroidUtilities.runOnUIThread(forwardSeek);
        }
    }

    private void updateEmptyViewPosition() {
        if (emptyView.getVisibility() != View.VISIBLE) {
            return;
        }
        int h = playerLayout.getVisibility() == View.VISIBLE ? AndroidUtilities.dp(150) : -AndroidUtilities.dp(30);
        emptyView.setTranslationY((emptyView.getMeasuredHeight() - containerView.getMeasuredHeight() - h) / 2);
    }

    private void updateEmptyView() {
        emptyView.setVisibility(searching && listAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        updateEmptyViewPosition();
    }

    private boolean scrollToCurrentSong(boolean search) {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null) {
            boolean found = false;
            if (search) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof AudioPlayerCell) {
                        if (((AudioPlayerCell) child).getMessageObject() == playingMessageObject) {
                            if (child.getBottom() <= listView.getMeasuredHeight()) {
                                found = true;
                            }
                            break;
                        }
                    }
                }
            }
            if (!found) {
                int idx = playlist.indexOf(playingMessageObject);
                if (idx >= 0) {
                    if (SharedConfig.playOrderReversed) {
                        layoutManager.scrollToPosition(idx);
                    } else {
                        layoutManager.scrollToPosition(playlist.size() - idx);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onCustomMeasure(View view, int width, int height) {
        boolean isPortrait = width < height;
        if (view == blurredView) {
            blurredView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        int width = (right - left);
        int height = (bottom - top);
        boolean isPortrait = width < height;
        if (view == blurredView) {
            blurredView.layout(left, 0, left + width, height);
            return true;
        }
        return false;
    }

    private void setMenuItemChecked(ActionBarMenuSubItem item, boolean checked) {
        if (checked) {
            item.setTextColor(getThemedColor(Theme.key_player_buttonActive));
            item.setIconColor(getThemedColor(Theme.key_player_buttonActive));
        } else {
            item.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
            item.setIconColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        }
    }

    private void updateSubMenu() {
        setMenuItemChecked(shuffleListItem, SharedConfig.shuffleMusic);
        setMenuItemChecked(reverseOrderItem, SharedConfig.playOrderReversed);
        setMenuItemChecked(repeatListItem, SharedConfig.repeatMode == 1);
        setMenuItemChecked(repeatSongItem, SharedConfig.repeatMode == 2);
    }

    private void updatePlaybackButton() {
        float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        String key;
        if (Math.abs(currentPlaybackSpeed - 1.0f) > 0.001f) {
            key = Theme.key_inappPlayerPlayPause;
        } else {
            key = Theme.key_inappPlayerClose;
        }
        playbackSpeedButton.setTag(key);
        float speed = MediaController.getInstance().getFastPlaybackSpeed(true);
        if (Math.abs(speed - 1.8f) < 0.001f) {
            playbackSpeedButton.setIcon(R.drawable.voice_mini_2_0);
        } else if (Math.abs(speed - 1.5f) < 0.001f) {
            playbackSpeedButton.setIcon(R.drawable.voice_mini_1_5);
        } else {
            playbackSpeedButton.setIcon(R.drawable.voice_mini_0_5);
        }
        playbackSpeedButton.setIconColor(getThemedColor(key));
        if (Build.VERSION.SDK_INT >= 21) {
            playbackSpeedButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(key) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
        }
        for (int a = 0; a < speedItems.length; a++) {
            if (a == 0 && Math.abs(currentPlaybackSpeed - 0.5f) < 0.001f ||
                    a == 1 && Math.abs(currentPlaybackSpeed - 1.0f) < 0.001f ||
                    a == 2 && Math.abs(currentPlaybackSpeed - 1.5f) < 0.001f ||
                    a == 3 && Math.abs(currentPlaybackSpeed - 1.8f) < 0.001f) {
                speedItems[a].setColors(getThemedColor(Theme.key_inappPlayerPlayPause), getThemedColor(Theme.key_inappPlayerPlayPause));
            } else {
                speedItems[a].setColors(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon));
            }
        }
    }

    private void onSubItemClick(int id) {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null || parentActivity == null) {
            return;
        }
        if (id == 1) {
            if (UserConfig.selectedAccount != currentAccount) {
                parentActivity.switchToAccount(currentAccount, true);
            }
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", 3);
            DialogsActivity fragment = new DialogsActivity(args);
            final ArrayList<MessageObject> fmessages = new ArrayList<>();
            fmessages.add(messageObject);
            fragment.setDelegate((fragment1, dids, message, param) -> {
                if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
                    for (int a = 0; a < dids.size(); a++) {
                        long did = dids.get(a);
                        if (message != null) {
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null);
                        }
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did, false, false, true, 0);
                    }
                    fragment1.finishFragment();
                } else {
                    long did = dids.get(0);
                    Bundle args1 = new Bundle();
                    args1.putBoolean("scrollToTopOnResume", true);
                    if (DialogObject.isEncryptedDialog(did)) {
                        args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                    } else if (DialogObject.isUserDialog(did)) {
                        args1.putLong("user_id", did);
                    } else {
                        args1.putLong("chat_id", -did);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                    ChatActivity chatActivity = new ChatActivity(args1);
                    if (parentActivity.presentFragment(chatActivity, true, false)) {
                        chatActivity.showFieldPanelForForward(true, fmessages);
                    } else {
                        fragment1.finishFragment();
                    }
                }
            });
            parentActivity.presentFragment(fragment);
            dismiss();
        } else if (id == 2) {
            try {
                File f = null;
                boolean isVideo = false;

                if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
                    f = new File(messageObject.messageOwner.attachPath);
                    if (!f.exists()) {
                        f = null;
                    }
                }
                if (f == null) {
                    f = FileLoader.getPathToMessage(messageObject.messageOwner);
                }

                if (f.exists()) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(messageObject.getMimeType());
                    if (Build.VERSION.SDK_INT >= 24) {
                        try {
                            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ApplicationLoader.applicationContext, BuildConfig.APPLICATION_ID + ".provider", f));
                            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignore) {
                            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        }
                    } else {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    }

                    parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                    builder.show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (id == 4) {
            if (UserConfig.selectedAccount != currentAccount) {
                parentActivity.switchToAccount(currentAccount, true);
            }
            
            Bundle args = new Bundle();
            long did = messageObject.getDialogId();
            if (DialogObject.isEncryptedDialog(did)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
            } else if (DialogObject.isUserDialog(did)) {
                args.putLong("user_id", did);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                if (chat != null && chat.migrated_to != null) {
                    args.putLong("migrated_to", did);
                    did = -chat.migrated_to.channel_id;
                }
                args.putLong("chat_id", -did);
            }
            args.putInt("message_id", messageObject.getId());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
            parentActivity.presentFragment(new ChatActivity(args), false, false);
            dismiss();
        } else if (id == 5) {
            if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parentActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                return;
            }
            String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
            if (TextUtils.isEmpty(fileName)) {
                fileName = messageObject.getFileName();
            }
            String path = messageObject.messageOwner.attachPath;
            if (path != null && path.length() > 0) {
                File temp = new File(path);
                if (!temp.exists()) {
                    path = null;
                }
            }
            if (path == null || path.length() == 0) {
                path = FileLoader.getPathToMessage(messageObject.messageOwner).toString();
            }
            MediaController.saveFile(path, parentActivity, 3, fileName, messageObject.getDocument() != null ? messageObject.getDocument().mime_type : "", () -> BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createDownloadBulletin(BulletinFactory.FileType.AUDIO).show());
        }
    }

    private void showAlbumCover(boolean show, boolean animated) {
        if (show) {
            if (blurredView.getVisibility() == View.VISIBLE || blurredAnimationInProgress) {
                return;
            }
            blurredView.setTag(1);
            bigAlbumConver.setImageBitmap(coverContainer.getImageReceiver().getBitmap());
            blurredAnimationInProgress = true;
            BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
            View fragmentView = fragment.getFragmentView();
            int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
            int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
            fragmentView.draw(canvas);
            canvas.translate(containerView.getLeft() - getLeftInset(), 0);
            containerView.draw(canvas);
            Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
            blurredView.setBackground(new BitmapDrawable(bitmap));
            blurredView.setVisibility(View.VISIBLE);
            blurredView.animate().alpha(1.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    blurredAnimationInProgress = false;
                }
            }).start();
            bigAlbumConver.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        } else {
            if (blurredView.getVisibility() != View.VISIBLE) {
                return;
            }
            blurredView.setTag(null);
            if (animated) {
                blurredAnimationInProgress = true;
                blurredView.animate().alpha(0.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        blurredView.setVisibility(View.INVISIBLE);
                        bigAlbumConver.setImageBitmap(null);
                        blurredAnimationInProgress = false;
                    }
                }).start();
                bigAlbumConver.animate().scaleX(0.9f).scaleY(0.9f).setDuration(180).start();
            } else {
                blurredView.setAlpha(0.0f);
                blurredView.setVisibility(View.INVISIBLE);
                bigAlbumConver.setImageBitmap(null);
                bigAlbumConver.setScaleX(0.9f);
                bigAlbumConver.setScaleY(0.9f);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            updateTitle(id == NotificationCenter.messagePlayingDidReset && (Boolean) args[1]);
            if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof AudioPlayerCell) {
                        AudioPlayerCell cell = (AudioPlayerCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
                            cell.updateButtonState(false, true);
                        }
                    }
                }
                if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                    if (MediaController.getInstance().getPlayingMessageObject() != null) {
                        if (MediaController.getInstance().isMessagePaused()) {
                            startForwardRewindingSeek();
                        } else if (rewindingState == 1 && rewindingProgress != -1f) {
                            AndroidUtilities.cancelRunOnUIThread(forwardSeek);
                            lastUpdateRewindingPlayerTime = 0;
                            forwardSeek.run();
                            rewindingProgress = -1f;
                        }
                    }
                }
            } else {
                MessageObject messageObject = (MessageObject) args[0];
                if (messageObject.eventId != 0) {
                    return;
                }
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof AudioPlayerCell) {
                        AudioPlayerCell cell = (AudioPlayerCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null && (messageObject1.isVoice() || messageObject1.isMusic())) {
                            cell.updateButtonState(false, true);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && messageObject.isMusic()) {
                updateProgress(messageObject);
            }
        } else if (id == NotificationCenter.musicDidLoad) {
            playlist = MediaController.getInstance().getPlaylist();
            listAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.moreMusicDidLoad) {
            playlist = MediaController.getInstance().getPlaylist();
            listAdapter.notifyDataSetChanged();
            if (SharedConfig.playOrderReversed) {
                listView.stopScroll();
                int addedCount = (Integer) args[0];
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int position = layoutManager.findLastVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION) {
                    View firstVisView = layoutManager.findViewByPosition(position);
                    int offset = firstVisView == null ? 0 : firstVisView.getTop();
                    layoutManager.scrollToPositionWithOffset(position + addedCount, offset);
                }
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String name = (String) args[0];
            if (name.equals(currentFile)) {
                updateTitle(false);
                currentAudioFinishedLoading = true;
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            String name = (String) args[0];
            if (name.equals(currentFile)) {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject == null) {
                    return;
                }
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float bufferedProgress;
                if (currentAudioFinishedLoading) {
                    bufferedProgress = 1.0f;
                } else {
                    long newTime = SystemClock.elapsedRealtime();
                    if (Math.abs(newTime - lastBufferedPositionCheck) >= 500) {
                        bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1.0f;
                        lastBufferedPositionCheck = newTime;
                    } else {
                        bufferedProgress = -1;
                    }
                }
                if (bufferedProgress != -1) {
                    seekBarView.setBufferedProgress(bufferedProgress);
                }
            }
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = AndroidUtilities.dp(7);
        if (top >= AndroidUtilities.dp(7) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        boolean show = newOffset <= AndroidUtilities.dp(12);
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }
            actionBarAnimation = new AnimatorSet();
            actionBarAnimation.setDuration(180);
            actionBarAnimation.playTogether(
                    ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
            actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {

                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    actionBarAnimation = null;
                }
            });
            actionBarAnimation.start();
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        newOffset += layoutParams.topMargin - AndroidUtilities.dp(11);
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset((scrollOffsetY = newOffset) - layoutParams.topMargin);
            containerView.invalidate();
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.musicDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.moreMusicDidLoad);
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
    }

    @Override
    public void onBackPressed() {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
            return;
        }
        if (blurredView.getTag() != null) {
            showAlbumCover(false, true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {

    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        progressView.setProgress(Math.min(1f, downloadedSize / (float) totalSize), true);
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    private void updateRepeatButton() {
        int mode = SharedConfig.repeatMode;
        if (mode == 0 || mode == 1) {
            if (SharedConfig.shuffleMusic) {
                if (mode == 0) {
                    repeatButton.setIcon(R.drawable.player_new_shuffle);
                } else {
                    repeatButton.setIcon(R.drawable.player_new_repeat_shuffle);
                }
            } else if (SharedConfig.playOrderReversed) {
                if (mode == 0) {
                    repeatButton.setIcon(R.drawable.player_new_order);
                } else {
                    repeatButton.setIcon(R.drawable.player_new_repeat_reverse);
                }
            } else {
                repeatButton.setIcon(R.drawable.player_new_repeatall);
            }
            if (mode == 0 && !SharedConfig.shuffleMusic && !SharedConfig.playOrderReversed) {
                repeatButton.setTag(Theme.key_player_button);
                repeatButton.setIconColor(getThemedColor(Theme.key_player_button));
                Theme.setSelectorDrawableColor(repeatButton.getBackground(), getThemedColor(Theme.key_listSelector), true);
                repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatOff", R.string.AccDescrRepeatOff));
            } else {
                repeatButton.setTag(Theme.key_player_buttonActive);
                repeatButton.setIconColor(getThemedColor(Theme.key_player_buttonActive));
                Theme.setSelectorDrawableColor(repeatButton.getBackground(), getThemedColor(Theme.key_player_buttonActive) & 0x19ffffff, true);
                if (mode == 0) {
                    if (SharedConfig.shuffleMusic) {
                        repeatButton.setContentDescription(LocaleController.getString("ShuffleList", R.string.ShuffleList));
                    } else {
                        repeatButton.setContentDescription(LocaleController.getString("ReverseOrder", R.string.ReverseOrder));
                    }
                } else {
                    repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatList", R.string.AccDescrRepeatList));
                }
            }
        } else if (mode == 2) {
            repeatButton.setIcon(R.drawable.player_new_repeatone);
            repeatButton.setTag(Theme.key_player_buttonActive);
            repeatButton.setIconColor(getThemedColor(Theme.key_player_buttonActive));
            Theme.setSelectorDrawableColor(repeatButton.getBackground(), getThemedColor(Theme.key_player_buttonActive) & 0x19ffffff, true);
            repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatOne", R.string.AccDescrRepeatOne));
        }
    }

    private void updateProgress(MessageObject messageObject) {
        updateProgress(messageObject, false);
    }

    private void updateProgress(MessageObject messageObject, boolean animated) {
        if (seekBarView != null) {
            int newTime;
            if (seekBarView.isDragging()) {
                newTime = (int) (messageObject.getDuration() * seekBarView.getProgress());
            } else {
                boolean updateRewinding = rewindingProgress >= 0 && (rewindingState == -1 || (rewindingState == 1 && MediaController.getInstance().isMessagePaused()));
                if (updateRewinding) {
                    seekBarView.setProgress(rewindingProgress, animated);
                } else {
                    seekBarView.setProgress(messageObject.audioProgress, animated);
                }

                float bufferedProgress;
                if (currentAudioFinishedLoading) {
                    bufferedProgress = 1.0f;
                } else {
                    long time = SystemClock.elapsedRealtime();
                    if (Math.abs(time - lastBufferedPositionCheck) >= 500) {
                        bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1.0f;
                        lastBufferedPositionCheck = time;
                    } else {
                        bufferedProgress = -1;
                    }
                }
                if (bufferedProgress != -1) {
                    seekBarView.setBufferedProgress(bufferedProgress);
                }
                if (updateRewinding) {
                    newTime = (int) (messageObject.getDuration() * seekBarView.getProgress());
                    messageObject.audioProgressSec = newTime;
                } else {
                    newTime = messageObject.audioProgressSec;
                }
            }
            if (lastTime != newTime) {
                lastTime = newTime;
                timeTextView.setText(AndroidUtilities.formatShortDuration(newTime));
            }
        }
    }

    private void checkIfMusicDownloaded(MessageObject messageObject) {
        File cacheFile = null;
        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() > 0) {
            cacheFile = new File(messageObject.messageOwner.attachPath);
            if (!cacheFile.exists()) {
                cacheFile = null;
            }
        }
        if (cacheFile == null) {
            cacheFile = FileLoader.getPathToMessage(messageObject.messageOwner);
        }
        boolean canStream = SharedConfig.streamMedia && (int) messageObject.getDialogId() != 0 && messageObject.isMusic();
        if (!cacheFile.exists() && !canStream) {
            String fileName = messageObject.getFileName();
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
            Float progress = ImageLoader.getInstance().getFileProgress(fileName);
            progressView.setProgress(progress != null ? progress : 0, false);
            progressView.setVisibility(View.VISIBLE);
            seekBarView.setVisibility(View.INVISIBLE);
            playButton.setEnabled(false);
        } else {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            progressView.setVisibility(View.INVISIBLE);
            seekBarView.setVisibility(View.VISIBLE);
            playButton.setEnabled(true);
        }
    }

    private void updateTitle(boolean shutdown) {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null && shutdown || messageObject != null && !messageObject.isMusic()) {
            dismiss();
        } else {
            if (messageObject == null) {
                lastMessageObject = null;
                return;
            }
            final boolean sameMessageObject = messageObject == lastMessageObject;
            lastMessageObject = messageObject;
            if (messageObject.eventId != 0 || messageObject.getId() <= -2000000000) {
                optionsButton.setVisibility(View.INVISIBLE);
            } else {
                optionsButton.setVisibility(View.VISIBLE);
            }
            if (MessagesController.getInstance(currentAccount).isChatNoForwards(messageObject.getChatId())) {
                optionsButton.hideSubItem(1);
                optionsButton.hideSubItem(2);
                optionsButton.hideSubItem(5);
                optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(16));
            } else {
                optionsButton.showSubItem(1);
                optionsButton.showSubItem(2);
                optionsButton.showSubItem(5);
                optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(157));
            }

            checkIfMusicDownloaded(messageObject);
            updateProgress(messageObject, !sameMessageObject);
            updateCover(messageObject, !sameMessageObject);

            if (MediaController.getInstance().isMessagePaused()) {
                playPauseDrawable.setPause(false);
                playButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                playPauseDrawable.setPause(true);
                playButton.setContentDescription(LocaleController.getString("AccActionPause", R.string.AccActionPause));
            }
            String title = messageObject.getMusicTitle();
            String author = messageObject.getMusicAuthor();
            titleTextView.setText(title);
            authorTextView.setText(author);

            int duration = lastDuration = messageObject.getDuration();

            if (durationTextView != null) {
                durationTextView.setText(duration != 0 ? AndroidUtilities.formatShortDuration(duration) : "-:--");
            }

            if (duration > 60 * 10) {
                playbackSpeedButton.setVisibility(View.VISIBLE);
            } else {
                playbackSpeedButton.setVisibility(View.GONE);
            }

            if (!sameMessageObject) {
                preloadNeighboringThumbs();
            }
        }
    }

    private void updateCover(MessageObject messageObject, boolean animated) {
        final BackupImageView imageView = animated ? coverContainer.getNextImageView() : coverContainer.getImageView();
        final AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
        if (audioInfo != null && audioInfo.getCover() != null) {
            imageView.setImageBitmap(audioInfo.getCover());
            currentFile = null;
            currentAudioFinishedLoading = true;
        } else {
            TLRPC.Document document = messageObject.getDocument();
            currentFile = FileLoader.getAttachFileName(document);
            currentAudioFinishedLoading = false;
            String artworkUrl = messageObject.getArtworkUrl(false);
            final ImageLocation thumbImageLocation = getArtworkThumbImageLocation(messageObject);
            if (!TextUtils.isEmpty(artworkUrl)) {
                imageView.setImage(ImageLocation.getForPath(artworkUrl), null, thumbImageLocation, null, null, 0, 1, messageObject);
            } else if (thumbImageLocation != null) {
                imageView.setImage(null, null, thumbImageLocation, null, null, 0, 1, messageObject);
            } else {
                imageView.setImageDrawable(null);
            }
            imageView.invalidate();
        }
        if (animated) {
            coverContainer.switchImageViews();
        }
    }

    private ImageLocation getArtworkThumbImageLocation(MessageObject messageObject) {
        final TLRPC.Document document = messageObject.getDocument();
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360) : null;
        if (!(thumb instanceof TLRPC.TL_photoSize) && !(thumb instanceof TLRPC.TL_photoSizeProgressive)) {
            thumb = null;
        }
        if (thumb != null) {
            return ImageLocation.getForDocument(thumb, document);
        } else {
            final String smallArtworkUrl = messageObject.getArtworkUrl(true);
            if (smallArtworkUrl != null) {
                return ImageLocation.getForPath(smallArtworkUrl);
            }
        }
        return null;
    }

    private void preloadNeighboringThumbs() {
        final MediaController mediaController = MediaController.getInstance();
        final List<MessageObject> playlist = mediaController.getPlaylist();
        if (playlist.size() <= 1) {
            return;
        }

        final List<MessageObject> neighboringItems = new ArrayList<>();
        final int playingIndex = mediaController.getPlayingMessageObjectNum();

        int nextIndex = playingIndex + 1;
        int prevIndex = playingIndex - 1;
        if (nextIndex >= playlist.size()) {
            nextIndex = 0;
        }
        if (prevIndex <= -1) {
            prevIndex = playlist.size() - 1;
        }

        neighboringItems.add(playlist.get(nextIndex));
        if (nextIndex != prevIndex) {
            neighboringItems.add(playlist.get(prevIndex));
        }

        for (int i = 0, N = neighboringItems.size(); i < N; i++) {
            final MessageObject messageObject = neighboringItems.get(i);
            final ImageLocation thumbImageLocation = getArtworkThumbImageLocation(messageObject);
            if (thumbImageLocation != null) {
                if (thumbImageLocation.path != null) {
                    ImageLoader.getInstance().preloadArtwork(thumbImageLocation.path);
                } else {
                    FileLoader.getInstance(currentAccount).loadFile(thumbImageLocation, messageObject, null, 0, 1);
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<MessageObject> searchResult = new ArrayList<>();
        private Runnable searchRunnable;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (playlist.size() > 1) {
                playerLayout.setBackgroundColor(getThemedColor(Theme.key_player_background));
                playerShadow.setVisibility(View.VISIBLE);
                listView.setPadding(0, listView.getPaddingTop(), 0, AndroidUtilities.dp(179));
            } else {
                playerLayout.setBackground(null);
                playerShadow.setVisibility(View.INVISIBLE);
                listView.setPadding(0, listView.getPaddingTop(), 0, 0);
            }
            updateEmptyView();
        }

        @Override
        public int getItemCount() {
            if (searchWas) {
                return searchResult.size();
            }
            return playlist.size() > 1 ? playlist.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new AudioPlayerCell(context, MediaController.getInstance().currentPlaylistIsGlobalSearch() ? AudioPlayerCell.VIEW_TYPE_GLOBAL_SEARCH  : AudioPlayerCell.VIEW_TYPE_DEFAULT, resourcesProvider);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            AudioPlayerCell cell = (AudioPlayerCell) holder.itemView;
            if (searchWas) {
                cell.setMessageObject(searchResult.get(position));
            } else {
                if (SharedConfig.playOrderReversed) {
                    cell.setMessageObject(playlist.get(position));
                } else {
                    cell.setMessageObject(playlist.get(playlist.size() - position - 1));
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (query == null) {
                searchResult.clear();
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                    searchRunnable = null;
                    processSearch(query);
                }, 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                final ArrayList<MessageObject> copy = new ArrayList<>(playlist);
                Utilities.searchQueue.postRunnable(() -> {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        updateSearchResults(new ArrayList<>(), query);
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

                    ArrayList<MessageObject> resultArray = new ArrayList<>();

                    for (int a = 0; a < copy.size(); a++) {
                        MessageObject messageObject = copy.get(a);
                        for (int b = 0; b < search.length; b++) {
                            String q = search[b];
                            String name = messageObject.getDocumentName();
                            if (name == null || name.length() == 0) {
                                continue;
                            }
                            name = name.toLowerCase();
                            if (name.contains(q)) {
                                resultArray.add(messageObject);
                                break;
                            }
                            TLRPC.Document document;
                            if (messageObject.type == 0) {
                                document = messageObject.messageOwner.media.webpage.document;
                            } else {
                                document = messageObject.messageOwner.media.document;
                            }
                            boolean ok = false;
                            for (int c = 0; c < document.attributes.size(); c++) {
                                TLRPC.DocumentAttribute attribute = document.attributes.get(c);
                                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                    if (attribute.performer != null) {
                                        ok = attribute.performer.toLowerCase().contains(q);
                                    }
                                    if (!ok && attribute.title != null) {
                                        ok = attribute.title.toLowerCase().contains(q);
                                    }
                                    break;
                                }
                            }
                            if (ok) {
                                resultArray.add(messageObject);
                                break;
                            }
                        }
                    }

                    updateSearchResults(resultArray, query);
                });
            });
        }

        private void updateSearchResults(final ArrayList<MessageObject> documents, String query) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchWas = true;
                searchResult = documents;
                notifyDataSetChanged();
                layoutManager.scrollToPosition(0);
                emptySubtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("NoAudioFoundPlayerInfo", R.string.NoAudioFoundPlayerInfo, query)));
            });
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            EditTextBoldCursor editText = searchItem.getSearchField();
            editText.setCursorColor(getThemedColor(Theme.key_player_actionBarTitle));

            repeatButton.setIconColor(getThemedColor((String) repeatButton.getTag()));
            Theme.setSelectorDrawableColor(repeatButton.getBackground(), getThemedColor(Theme.key_listSelector), true);

            optionsButton.setIconColor(getThemedColor(Theme.key_player_button));
            Theme.setSelectorDrawableColor(optionsButton.getBackground(), getThemedColor(Theme.key_listSelector), true);

            progressView.setBackgroundColor(getThemedColor(Theme.key_player_progressBackground));
            progressView.setProgressColor(getThemedColor(Theme.key_player_progress));

            updateSubMenu();
            repeatButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

            optionsButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);
            optionsButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), true);
            optionsButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
        };

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_player_actionBar));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, delegate, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_player_actionBarSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_player_time));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inLoader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_outLoader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inLoaderSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inMediaIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inMediaIconSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inAudioSelectedProgress));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AudioPlayerCell.class}, null, null, null, Theme.key_chat_inAudioProgress));

        themeDescriptions.add(new ThemeDescription(containerView, 0, null, null, new Drawable[]{shadowDrawable}, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_player_progressBackground));
        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_player_progress));
        themeDescriptions.add(new ThemeDescription(seekBarView, 0, null, null, null, null, Theme.key_player_progressBackground));
        themeDescriptions.add(new ThemeDescription(seekBarView, 0, null, null, null, null, Theme.key_player_progressCachedBackground));
        themeDescriptions.add(new ThemeDescription(seekBarView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_player_progress));

        themeDescriptions.add(new ThemeDescription(playbackSpeedButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_inappPlayerPlayPause));
        themeDescriptions.add(new ThemeDescription(playbackSpeedButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_inappPlayerClose));

        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_player_buttonActive));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(repeatButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(optionsButton, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuBackground));

        themeDescriptions.add(new ThemeDescription(prevButton, 0, null, new RLottieDrawable[]{prevButton.getAnimatedDrawable()}, "Triangle 3", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, 0, null, new RLottieDrawable[]{prevButton.getAnimatedDrawable()}, "Triangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, 0, null, new RLottieDrawable[]{prevButton.getAnimatedDrawable()}, "Rectangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(nextButton, 0, null, new RLottieDrawable[]{nextButton.getAnimatedDrawable()}, "Triangle 3", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(nextButton, 0, null, new RLottieDrawable[]{nextButton.getAnimatedDrawable()}, "Triangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(nextButton, 0, null, new RLottieDrawable[]{nextButton.getAnimatedDrawable()}, "Rectangle 4", Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(nextButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(playerLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_player_background));

        themeDescriptions.add(new ThemeDescription(playerShadow, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine));

        themeDescriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(emptyTitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(emptySubtitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(progressView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(durationTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));
        themeDescriptions.add(new ThemeDescription(timeTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));
        themeDescriptions.add(new ThemeDescription(titleTextView.getTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(titleTextView.getNextTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(authorTextView.getTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));
        themeDescriptions.add(new ThemeDescription(authorTextView.getNextTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));

        themeDescriptions.add(new ThemeDescription(containerView, 0, null, null, null, null, Theme.key_sheet_scrollUp));

        return themeDescriptions;
    }

    private static abstract class CoverContainer extends FrameLayout {

        private final BackupImageView[] imageViews = new BackupImageView[2];

        private int activeIndex;
        private AnimatorSet animatorSet;

        public CoverContainer(@NonNull Context context) {
            super(context);
            for (int i = 0; i < 2; i++) {
                imageViews[i] = new BackupImageView(context);
                final int index = i;
                imageViews[i].getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
                    if (index == activeIndex) {
                        onImageUpdated(imageReceiver);
                    }
                });
                imageViews[i].setRoundRadius(AndroidUtilities.dp(4));
                if (i == 1) {
                    imageViews[i].setVisibility(GONE);
                }
                addView(imageViews[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
        }

        public final void switchImageViews() {
            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            activeIndex = activeIndex == 0 ? 1 : 0;

            final BackupImageView prevImageView = imageViews[activeIndex == 0 ? 1 : 0];
            final BackupImageView currImageView = imageViews[activeIndex];

            final boolean hasBitmapImage = prevImageView.getImageReceiver().hasBitmapImage();

            currImageView.setAlpha(hasBitmapImage ? 1f : 0f);
            currImageView.setScaleX(0.8f);
            currImageView.setScaleY(0.8f);
            currImageView.setVisibility(VISIBLE);

            if (hasBitmapImage) {
                prevImageView.bringToFront();
            } else {
                prevImageView.setVisibility(GONE);
                prevImageView.setImageDrawable(null);
            }

            final ValueAnimator expandAnimator = ValueAnimator.ofFloat(0.8f, 1f);
            expandAnimator.setDuration(125);
            expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            expandAnimator.addUpdateListener(a -> {
                float animatedValue = (float) a.getAnimatedValue();
                currImageView.setScaleX(animatedValue);
                currImageView.setScaleY(animatedValue);
                if (!hasBitmapImage) {
                    currImageView.setAlpha(a.getAnimatedFraction());
                }
            });

            if (hasBitmapImage) {
                final ValueAnimator collapseAnimator = ValueAnimator.ofFloat(prevImageView.getScaleX(), 0.8f);
                collapseAnimator.setDuration(125);
                collapseAnimator.setInterpolator(CubicBezierInterpolator.EASE_IN);
                collapseAnimator.addUpdateListener(a -> {
                    float animatedValue = (float) a.getAnimatedValue();
                    prevImageView.setScaleX(animatedValue);
                    prevImageView.setScaleY(animatedValue);
                    final float fraction = a.getAnimatedFraction();
                    if (fraction > 0.25f && !currImageView.getImageReceiver().hasBitmapImage()) {
                        prevImageView.setAlpha(1f - (fraction - 0.25f) * (1f / 0.75f));
                    }
                });
                collapseAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        prevImageView.setVisibility(GONE);
                        prevImageView.setImageDrawable(null);
                        prevImageView.setAlpha(1f);
                    }
                });

                animatorSet.playSequentially(collapseAnimator, expandAnimator);
            } else {
                animatorSet.play(expandAnimator);
            }

            animatorSet.start();
        }

        public final BackupImageView getImageView() {
            return imageViews[activeIndex];
        }

        public final BackupImageView getNextImageView() {
            return imageViews[activeIndex == 0 ? 1 : 0];
        }

        public final ImageReceiver getImageReceiver() {
            return getImageView().getImageReceiver();
        }

        protected abstract void onImageUpdated(ImageReceiver imageReceiver);
    }

    public abstract static class ClippingTextViewSwitcher extends FrameLayout {

        private final TextView[] textViews = new TextView[2];
        private final float[] clipProgress = new float[]{0f, 0.75f};
        private final int gradientSize = AndroidUtilities.dp(24);

        private final Matrix gradientMatrix;
        private final Paint gradientPaint;
        private final Paint erasePaint;

        private int activeIndex;
        private AnimatorSet animatorSet;
        private LinearGradient gradientShader;
        private int stableOffest = -1;
        private final RectF rectF = new RectF();

        public ClippingTextViewSwitcher(@NonNull Context context) {
            super(context);
            for (int i = 0; i < 2; i++) {
                textViews[i] = createTextView();
                if (i == 1) {
                    textViews[i].setAlpha(0f);
                    textViews[i].setVisibility(GONE);
                }
                addView(textViews[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            }
            gradientMatrix = new Matrix();
            gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            gradientShader = new LinearGradient(gradientSize, 0, 0, 0, 0, 0xFF000000, Shader.TileMode.CLAMP);
            gradientPaint.setShader(gradientShader);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            final int index = child == textViews[0] ? 0 : 1;
            final boolean result;
            boolean hasStableRect = false;
            if (stableOffest > 0 && textViews[activeIndex].getAlpha() != 1f && textViews[activeIndex].getLayout() != null) {
                float x1 = textViews[activeIndex].getLayout().getPrimaryHorizontal(0);
                float x2 = textViews[activeIndex].getLayout().getPrimaryHorizontal(stableOffest);
                hasStableRect = true;
                if (x1 == x2) {
                    hasStableRect = false;
                } else if (x2 > x1) {
                    rectF.set(x1, 0, x2, getMeasuredHeight());
                } else {
                    rectF.set(x2, 0, x1, getMeasuredHeight());
                }

                if (hasStableRect && index == activeIndex) {
                    canvas.save();
                    canvas.clipRect(rectF);
                    textViews[0].draw(canvas);
                    canvas.restore();
                }
            }
            if (clipProgress[index] > 0f || hasStableRect) {
                final int width = child.getWidth();
                final int height = child.getHeight();
                final int saveCount = canvas.saveLayer(0, 0, width, height, null, Canvas.ALL_SAVE_FLAG);
                result = super. drawChild(canvas, child, drawingTime);
                final float gradientStart = width * (1f - clipProgress[index]);
                final float gradientEnd = gradientStart + gradientSize;
                gradientMatrix.setTranslate(gradientStart, 0);
                gradientShader.setLocalMatrix(gradientMatrix);
                canvas.drawRect(gradientStart, 0, gradientEnd, height, gradientPaint);
                if (width > gradientEnd) {
                    canvas.drawRect(gradientEnd, 0, width, height, erasePaint);
                }
                if (hasStableRect) {
                    canvas.drawRect(rectF, erasePaint);
                }
                canvas.restoreToCount(saveCount);
            } else {
                result = super.drawChild(canvas, child, drawingTime);
            }
            return result;
        }

        public void setText(CharSequence text) {
            setText(text, true);
        }

        public void setText(CharSequence text, boolean animated) {
            final CharSequence currentText = textViews[activeIndex].getText();

            if (TextUtils.isEmpty(currentText) || !animated) {
                textViews[activeIndex].setText(text);
                return;
            } else if (TextUtils.equals(text, currentText)) {
                return;
            }

            stableOffest = 0;
            int n = Math.min(text.length(), currentText.length());
            for (int i = 0; i < n; i++) {
                if (text.charAt(i) != currentText.charAt(i)) {
                    break;
                }
                stableOffest++;
            }
            if (stableOffest <= 3) {
                stableOffest = -1;
            }

            final int index = activeIndex == 0 ? 1 : 0;
            final int prevIndex = activeIndex;
            activeIndex = index;

            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    textViews[prevIndex].setVisibility(GONE);
                }
            });

            textViews[index].setText(text);
            textViews[index].bringToFront();
            textViews[index].setVisibility(VISIBLE);

            final int duration = 300;

            final ValueAnimator collapseAnimator = ValueAnimator.ofFloat(clipProgress[prevIndex], 0.75f);
            collapseAnimator.setDuration(duration / 3 * 2); // 0.66
            collapseAnimator.addUpdateListener(a -> {
                clipProgress[prevIndex] = (float) a.getAnimatedValue();
                invalidate();
            });

            final ValueAnimator expandAnimator = ValueAnimator.ofFloat(clipProgress[index], 0f);
            expandAnimator.setStartDelay(duration / 3); // 0.33
            expandAnimator.setDuration(duration / 3 * 2); // 0.66
            expandAnimator.addUpdateListener(a -> {
                clipProgress[index] = (float) a.getAnimatedValue();
                invalidate();
            });

            final ObjectAnimator fadeOutAnimator = ObjectAnimator.ofFloat(textViews[prevIndex], View.ALPHA, 0f);
            fadeOutAnimator.setStartDelay(duration / 4); // 0.25
            fadeOutAnimator.setDuration(duration / 2); // 0.5

            final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(textViews[index], View.ALPHA, 1f);
            fadeInAnimator.setStartDelay(duration / 4); // 0.25
            fadeInAnimator.setDuration(duration / 2); // 0.5

            animatorSet.playTogether(collapseAnimator, expandAnimator, fadeOutAnimator, fadeInAnimator);
            animatorSet.start();
        }

        public TextView getTextView() {
            return textViews[activeIndex];
        }

        public TextView getNextTextView() {
            return textViews[activeIndex == 0 ? 1 : 0];
        }

        protected abstract TextView createTextView();
    }
}
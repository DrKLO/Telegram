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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
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
    private BackupImageView placeholderImageView;
    private TextView titleTextView;
    private ImageView prevButton;
    private ImageView nextButton;
    private TextView authorTextView;
    private ActionBarMenuItem optionsButton;
    private LineProgressView progressView;
    private SeekBarView seekBarView;
    private SimpleTextView timeTextView;
    private ImageView playbackSpeedButton;
    private TextView durationTextView;
    private ActionBarMenuItem repeatButton;
    private ActionBarMenuSubItem repeatSongItem;
    private ActionBarMenuSubItem repeatListItem;
    private ActionBarMenuSubItem shuffleListItem;
    private ActionBarMenuSubItem reverseOrderItem;
    private ImageView playButton;
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

    private int scrollOffsetY = Integer.MAX_VALUE;
    private int topBeforeSwitch;

    private boolean inFullSize;

    private String currentFile;

    private AnimatorSet actionBarAnimation;

    private int lastTime;
    private int lastDuration;

    private int TAG;

    private LaunchActivity parentActivity;

    public AudioPlayerAlert(final Context context) {
        super(context, true);

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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileLoadProgressChanged);
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
                        Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                    }

                    if (rad != 0) {
                        float alphaProgress = 1.0f;
                        int w = AndroidUtilities.dp(36);
                        rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                        int color = Theme.getColor(Theme.key_sheet_scrollUp);
                        int alpha = Color.alpha(color);
                        Theme.dialogs_onlineCirclePaint.setColor(color);
                        Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                    }

                    int color1 = Theme.getColor(Theme.key_dialogBackground);
                    int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        actionBar = new ActionBar(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_player_actionBar));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarTitle), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_player_actionBarSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_player_actionBarTitle));
        actionBar.setTitle(LocaleController.getString("AttachMusic", R.string.AttachMusic));
        actionBar.setSubtitleColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        actionBar.setOccupyStatusBar(false);
        actionBar.setAlpha(0.0f);

        if (messageObject != null) {
            long did = messageObject.getDialogId();
            int lower_id = (int) did;
            int high_id = (int) (did >> 32);
            if (lower_id != 0) {
                if (lower_id > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                    if (user != null) {
                        actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                    if (chat != null) {
                        actionBar.setTitle(chat.title);
                    }
                }
            } else {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                if (encryptedChat != null) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                    if (user != null) {
                        actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                    }
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
        editText.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        editText.setHintTextColor(Theme.getColor(Theme.key_player_time));
        editText.setCursorColor(Theme.getColor(Theme.key_player_actionBarTitle));

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
        playerShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        
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

        placeholderImageView = new BackupImageView(context) {

            private long pressTime;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (imageReceiver.hasBitmapImage()) {
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
        };
        placeholderImageView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (blurredView.getTag() != null) {
                bigAlbumConver.setImageBitmap(placeholderImageView.imageReceiver.getBitmap());
            }
        });
        placeholderImageView.setRoundRadius(AndroidUtilities.dp(4));
        playerLayout.addView(placeholderImageView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.RIGHT, 0, 20, 20, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setSingleLine(true);
        playerLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 20, 72, 0));

        authorTextView = new TextView(context);
        authorTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        authorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        authorTextView.setEllipsize(TextUtils.TruncateAt.END);
        authorTextView.setSingleLine(true);
        playerLayout.addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 47, 72, 0));

        seekBarView = new SeekBarView(context);
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
        progressView.setBackgroundColor(Theme.getColor(Theme.key_player_progressBackground));
        progressView.setProgressColor(Theme.getColor(Theme.key_player_progress));
        playerLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.TOP | Gravity.LEFT, 21, 90, 21, 0));

        timeTextView = new SimpleTextView(context);
        timeTextView.setTextSize(12);
        timeTextView.setText("0:00");
        timeTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        timeTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        playerLayout.addView(timeTextView, LayoutHelper.createFrame(100, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 98, 0, 0));

        durationTextView = new TextView(context);
        durationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        durationTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        durationTextView.setGravity(Gravity.CENTER);
        durationTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        playerLayout.addView(durationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 96, 20, 0));

        playbackSpeedButton = new ImageView(context);
        playbackSpeedButton.setScaleType(ImageView.ScaleType.CENTER);
        playbackSpeedButton.setImageResource(R.drawable.voice2x);
        playbackSpeedButton.setContentDescription(LocaleController.getString("AccDescrPlayerSpeed", R.string.AccDescrPlayerSpeed));
        if (AndroidUtilities.density >= 3.0f) {
            playbackSpeedButton.setPadding(0, 1, 0, 0);
        }
        playerLayout.addView(playbackSpeedButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 0, 86, 20, 0));
        playbackSpeedButton.setOnClickListener(v -> {
            float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
            if (currentPlaybackSpeed > 1) {
                MediaController.getInstance().setPlaybackSpeed(true, 1.0f);
            } else {
                MediaController.getInstance().setPlaybackSpeed(true, 1.8f);
            }
            updatePlaybackButton();
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

        buttons[0] = repeatButton = new ActionBarMenuItem(context, null, 0, 0);
        repeatButton.setLongClickEnabled(false);
        repeatButton.setShowSubmenuByMove(false);
        repeatButton.setAdditionalYOffset(-AndroidUtilities.dp(166));
        if (Build.VERSION.SDK_INT >= 21) {
            repeatButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(18)));
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

        buttons[1] = prevButton = new ImageView(context);
        prevButton.setScaleType(ImageView.ScaleType.CENTER);
        prevButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        prevButton.setImageResource(R.drawable.player_new_previous);
        if (Build.VERSION.SDK_INT >= 21) {
            prevButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(22)));
        }
        bottomView.addView(prevButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        prevButton.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
        prevButton.setContentDescription(LocaleController.getString("AccDescrPrevious", R.string.AccDescrPrevious));

        buttons[2] = playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageResource(R.drawable.player_new_play);
        playButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            playButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(24)));
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

        buttons[3] = nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        nextButton.setImageResource(R.drawable.player_new_next);
        if (Build.VERSION.SDK_INT >= 21) {
            nextButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(22)));
        }
        bottomView.addView(nextButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        nextButton.setContentDescription(LocaleController.getString("Next", R.string.Next));

        buttons[4] = optionsButton = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_player_button));
        optionsButton.setLongClickEnabled(false);
        optionsButton.setShowSubmenuByMove(false);
        optionsButton.setIcon(R.drawable.ic_ab_other);
        optionsButton.setSubMenuOpenSide(2);
        optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(157));
        if (Build.VERSION.SDK_INT >= 21) {
            optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(18)));
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
        emptyImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(Theme.getColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setText(LocaleController.getString("NoAudioFound", R.string.NoAudioFound));
        emptyTitleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogEmptyText));
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
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
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
        blurredView.addView(bigAlbumConver, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 30, 30, 30, 30));

        updateTitle(false);
        updateRepeatButton();
        updateEmptyView();
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
            item.setTextColor(Theme.getColor(Theme.key_player_buttonActive));
            item.setIconColor(Theme.getColor(Theme.key_player_buttonActive));
        } else {
            item.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            item.setIconColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
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
        if (currentPlaybackSpeed > 1) {
            playbackSpeedButton.setTag(Theme.key_inappPlayerPlayPause);
            playbackSpeedButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerPlayPause), PorterDuff.Mode.MULTIPLY));
        } else {
            playbackSpeedButton.setTag(Theme.key_inappPlayerClose);
            playbackSpeedButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerClose), PorterDuff.Mode.MULTIPLY));
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
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, true, null, null, null, true, 0);
                        }
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did, true, 0);
                    }
                    fragment1.finishFragment();
                } else {
                    long did = dids.get(0);
                    int lower_part = (int) did;
                    int high_part = (int) (did >> 32);
                    Bundle args1 = new Bundle();
                    args1.putBoolean("scrollToTopOnResume", true);
                    if (lower_part != 0) {
                        if (lower_part > 0) {
                            args1.putInt("user_id", lower_part);
                        } else if (lower_part < 0) {
                            args1.putInt("chat_id", -lower_part);
                        }
                    } else {
                        args1.putInt("enc_id", high_part);
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
                    if (messageObject != null) {
                        intent.setType(messageObject.getMimeType());
                    } else {
                        intent.setType("audio/mp3");
                    }
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
            int lower_part = (int) did;
            int high_id = (int) (did >> 32);
            if (lower_part != 0) {
                if (lower_part > 0) {
                    args.putInt("user_id", lower_part);
                } else if (lower_part < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_part);
                    if (chat != null && chat.migrated_to != null) {
                        args.putInt("migrated_to", lower_part);
                        lower_part = -chat.migrated_to.channel_id;
                    }
                    args.putInt("chat_id", -lower_part);
                }
            } else {
                args.putInt("enc_id", high_id);
            }
            args.putInt("message_id", messageObject.getId());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
            parentActivity.presentFragment(new ChatActivity(args), false, false);
            dismiss();
        } else if (id == 5) {
            if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
            MediaController.saveFile(path, parentActivity, 3, fileName, messageObject.getDocument() != null ? messageObject.getDocument().mime_type : "");
        }
    }

    private void showAlbumCover(boolean show, boolean animated) {
        if (show) {
            if (blurredView.getVisibility() == View.VISIBLE || blurredAnimationInProgress) {
                return;
            }
            blurredView.setTag(1);
            bigAlbumConver.setImageBitmap(placeholderImageView.imageReceiver.getBitmap());
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
            } else {
                blurredView.setAlpha(0.0f);
                blurredView.setVisibility(View.INVISIBLE);
                bigAlbumConver.setImageBitmap(null);
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
            } else if (id == NotificationCenter.messagePlayingDidStart) {
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
        } else if (id == NotificationCenter.fileDidLoad) {
            String name = (String) args[0];
            if (name.equals(currentFile)) {
                updateTitle(false);
                currentAudioFinishedLoading = true;
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileLoadProgressChanged);
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
                repeatButton.setIconColor(Theme.getColor(Theme.key_player_button));
                Theme.setSelectorDrawableColor(repeatButton.getBackground(), Theme.getColor(Theme.key_listSelector), true);
                repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatOff", R.string.AccDescrRepeatOff));
            } else {
                repeatButton.setTag(Theme.key_player_buttonActive);
                repeatButton.setIconColor(Theme.getColor(Theme.key_player_buttonActive));
                Theme.setSelectorDrawableColor(repeatButton.getBackground(), Theme.getColor(Theme.key_player_buttonActive) & 0x19ffffff, true);
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
            repeatButton.setIconColor(Theme.getColor(Theme.key_player_buttonActive));
            Theme.setSelectorDrawableColor(repeatButton.getBackground(), Theme.getColor(Theme.key_player_buttonActive) & 0x19ffffff, true);
            repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatOne", R.string.AccDescrRepeatOne));
        }
    }

    private void updateProgress(MessageObject messageObject) {
        if (seekBarView != null) {
            int newTime;
            if (seekBarView.isDragging()) {
                newTime = (int) (messageObject.getDuration() * seekBarView.getProgress());
            } else {
                seekBarView.setProgress(messageObject.audioProgress);

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
                newTime = messageObject.audioProgressSec;
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
                return;
            }
            if (messageObject.eventId != 0 || messageObject.getId() <= -2000000000) {
                optionsButton.setVisibility(View.INVISIBLE);
            } else {
                optionsButton.setVisibility(View.VISIBLE);
            }
            checkIfMusicDownloaded(messageObject);
            updateProgress(messageObject);

            if (MediaController.getInstance().isMessagePaused()) {
                playButton.setImageResource(R.drawable.player_new_play);
                playButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                playButton.setImageResource(R.drawable.player_new_pause);
                playButton.setContentDescription(LocaleController.getString("AccActionPause", R.string.AccActionPause));
            }
            String title = messageObject.getMusicTitle();
            String author = messageObject.getMusicAuthor();
            titleTextView.setText(title);
            authorTextView.setText(author);

            String loadTitle = author + " " + title;
            AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
            if (audioInfo != null && audioInfo.getCover() != null) {
                placeholderImageView.setImageBitmap(audioInfo.getCover());
                currentFile = null;
                currentAudioFinishedLoading = true;
            } else {
                TLRPC.Document document = messageObject.getDocument();
                currentFile = FileLoader.getAttachFileName(document);
                currentAudioFinishedLoading = false;
                TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 240) : null;
                if (!(thumb instanceof TLRPC.TL_photoSize)) {
                    thumb = null;
                }
                String artworkUrl = messageObject.getArtworkUrl(false);
                if (!TextUtils.isEmpty(artworkUrl)) {
                    if (thumb != null) {
                        placeholderImageView.setImage(ImageLocation.getForPath(artworkUrl), null, ImageLocation.getForDocument(thumb, document), null, null, 0, 1, messageObject);
                    } else {
                        placeholderImageView.setImage(artworkUrl, null, null);
                    }
                } else if (thumb != null) {
                    placeholderImageView.setImage(null, null, ImageLocation.getForDocument(thumb, document), null, null, 0, 1, messageObject);
                } else {
                    placeholderImageView.setImageDrawable(null);
                }
                placeholderImageView.invalidate();
            }

            int duration = lastDuration = messageObject.getDuration();

            if (durationTextView != null) {
                durationTextView.setText(duration != 0 ? AndroidUtilities.formatShortDuration(duration) : "-:--");
            }

            if (duration > 60 * 20) {
                playbackSpeedButton.setVisibility(View.VISIBLE);
            } else {
                playbackSpeedButton.setVisibility(View.GONE);
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
                playerLayout.setBackgroundColor(Theme.getColor(Theme.key_player_background));
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
            View view = new AudioPlayerCell(context);
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
            editText.setCursorColor(Theme.getColor(Theme.key_player_actionBarTitle));

            repeatButton.setIconColor(Theme.getColor((String) repeatButton.getTag()));
            Theme.setSelectorDrawableColor(repeatButton.getBackground(), Theme.getColor(Theme.key_listSelector), true);

            optionsButton.setIconColor(Theme.getColor(Theme.key_player_button));
            Theme.setSelectorDrawableColor(optionsButton.getBackground(), Theme.getColor(Theme.key_listSelector), true);

            progressView.setBackgroundColor(Theme.getColor(Theme.key_player_progressBackground));
            progressView.setProgressColor(Theme.getColor(Theme.key_player_progress));

            updateSubMenu();
            repeatButton.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

            optionsButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
            optionsButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), true);
            optionsButton.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
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
        themeDescriptions.add(new ThemeDescription(seekBarView, 0, null, null, null, null, Theme.key_player_progress));
        themeDescriptions.add(new ThemeDescription(seekBarView, 0, null, null, null, null, Theme.key_player_progressCachedBackground));

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

        themeDescriptions.add(new ThemeDescription(prevButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(prevButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button));
        themeDescriptions.add(new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(nextButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button));
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
        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(authorTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time));

        themeDescriptions.add(new ThemeDescription(containerView, 0, null, null, null, null, Theme.key_sheet_scrollUp));

        return themeDescriptions;
    }
}
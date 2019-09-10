/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AudioPlayerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class AudioPlayerAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener {

    private ActionBar actionBar;
    private View shadow;
    private View shadow2;
    private ChatAvatarContainer avatarContainer;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem menuItem;
    private boolean searchWas;
    private boolean searching;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;

    private FrameLayout playerLayout;
    private BackupImageView placeholderImageView;
    private TextView titleTextView;
    private TextView authorTextView;
    private ActionBarMenuItem optionsButton;
    private LineProgressView progressView;
    private SeekBarView seekBarView;
    private SimpleTextView timeTextView;
    private TextView durationTextView;
    private ActionBarMenuItem shuffleButton;
    private ImageView playButton;
    private ImageView repeatButton;
    private View[] buttons = new View[5];
    private Drawable[] playOrderButtons = new Drawable[2];
    private boolean hasOptions = true;

    private boolean scrollToSong = true;

    private boolean isInFullMode;
    private AnimatorSet animatorSet;
    private float fullAnimationProgress;
    private float startTranslation;
    private float endTranslation;
    private float panelStartTranslation;
    private float panelEndTranslation;

    private int searchOpenPosition = -1;
    private int searchOpenOffset;

    private int hasNoCover;
    private Drawable noCoverDrawable;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float thumbMaxScale;
    private int thumbMaxX;
    private int thumbMaxY;

    private ArrayList<MessageObject> playlist;

    private int scrollOffsetY = Integer.MAX_VALUE;
    private int topBeforeSwitch;
    private Drawable shadowDrawable;

    private boolean inFullSize;

    private AnimatorSet actionBarAnimation;

    private int lastTime;
    private int TAG;

    private LaunchActivity parentActivity;

    public AudioPlayerAlert(final Context context) {
        super(context, true, 0);

        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null) {
            currentAccount = messageObject.currentAccount;
        } else {
            currentAccount = UserConfig.selectedAccount;
        }

        parentActivity = (LaunchActivity) context;
        noCoverDrawable = context.getResources().getDrawable(R.drawable.nocover).mutate();
        noCoverDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_placeholder), PorterDuff.Mode.MULTIPLY));

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.musicDidLoad);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_background), PorterDuff.Mode.MULTIPLY));
        paint.setColor(Theme.getColor(Theme.key_player_placeholderBackground));

        containerView = new FrameLayout(context) {

            private boolean ignoreLayout = false;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY && placeholderImageView.getTranslationX() == 0) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                int contentSize = AndroidUtilities.dp(178) + playlist.size() * AndroidUtilities.dp(56) + backgroundPaddingTop + ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                int padding;
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                if (searching) {
                    padding = AndroidUtilities.dp(178) + ActionBar.getCurrentActionBarHeight() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                } else {
                    if (contentSize < height) {
                        padding = height - contentSize;
                    } else {
                        padding = (contentSize < height ? 0 : height - (height / 5 * 3));
                    }
                    padding += ActionBar.getCurrentActionBarHeight() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                }
                if (listView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, AndroidUtilities.dp(8));
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                inFullSize = getMeasuredHeight() >= height;
                int availableHeight = height - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.dp(120);
                int maxSize = Math.max(availableHeight, getMeasuredWidth());
                thumbMaxX = (getMeasuredWidth() - maxSize) / 2 - AndroidUtilities.dp(17);
                thumbMaxY = AndroidUtilities.dp(19);
                panelEndTranslation = getMeasuredHeight() - playerLayout.getMeasuredHeight();
                thumbMaxScale = maxSize / (float) placeholderImageView.getMeasuredWidth() - 1.0f;

                endTranslation = ActionBar.getCurrentActionBarHeight() + (AndroidUtilities.statusBarHeight - AndroidUtilities.dp(19));
                int scaledHeight = (int) Math.ceil(placeholderImageView.getMeasuredHeight() * (1.0f + thumbMaxScale));
                if (scaledHeight > availableHeight) {
                    endTranslation -= (scaledHeight - availableHeight);
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int y = actionBar.getMeasuredHeight();
                shadow.layout(shadow.getLeft(), y, shadow.getRight(), y + shadow.getMeasuredHeight());
                updateLayout();

                setFullAnimationProgress(fullAnimationProgress);
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
                shadowDrawable.setBounds(0, Math.max(actionBar.getMeasuredHeight(), scrollOffsetY) - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        actionBar = new ActionBar(context);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_player_actionBar));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarItems), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_player_actionBarSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_player_actionBarTitle));
        actionBar.setSubtitleColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        actionBar.setAlpha(0.0f);
        actionBar.setTitle("1");
        actionBar.setSubtitle("1");
        actionBar.getTitleTextView().setAlpha(0.0f);
        actionBar.getSubtitleTextView().setAlpha(0.0f);
        avatarContainer = new ChatAvatarContainer(context, null, false);
        avatarContainer.setEnabled(false);
        avatarContainer.setTitleColors(Theme.getColor(Theme.key_player_actionBarTitle), Theme.getColor(Theme.key_player_actionBarSubtitle));
        if (messageObject != null) {
            long did = messageObject.getDialogId();
            int lower_id = (int) did;
            int high_id = (int) (did >> 32);
            if (lower_id != 0) {
                if (lower_id > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                    if (user != null) {
                        avatarContainer.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                        avatarContainer.setUserAvatar(user);
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                    if (chat != null) {
                        avatarContainer.setTitle(chat.title);
                        avatarContainer.setChatAvatar(chat);
                    }
                }
            } else {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                if (encryptedChat != null) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                    if (user != null) {
                        avatarContainer.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                        avatarContainer.setUserAvatar(user);
                    }
                }
            }
        }
        avatarContainer.setSubtitle(LocaleController.getString("AudioTitle", R.string.AudioTitle));
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

        ActionBarMenu menu = actionBar.createMenu();
        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.addSubItem(1, R.drawable.msg_forward, LocaleController.getString("Forward", R.string.Forward));
        menuItem.addSubItem(2, R.drawable.msg_shareout, LocaleController.getString("ShareFile", R.string.ShareFile));
        menuItem.addSubItem(4, R.drawable.msg_message, LocaleController.getString("ShowInChat", R.string.ShowInChat));
        menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        menuItem.setTranslationX(AndroidUtilities.dp(48));
        menuItem.setAlpha(0.0f);

        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchCollapse() {
                avatarContainer.setVisibility(View.VISIBLE);
                if (hasOptions) {
                    menuItem.setVisibility(View.INVISIBLE);
                }
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
                searchOpenOffset = ((firstVisView == null) ? 0 : firstVisView.getTop()) - listView.getPaddingTop();

                avatarContainer.setVisibility(View.GONE);
                if (hasOptions) {
                    menuItem.setVisibility(View.GONE);
                }
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

        if (!AndroidUtilities.isTablet()) {
            actionBar.showActionModeTop();
            actionBar.setActionModeTopColor(Theme.getColor(Theme.key_player_actionBarTop));
        }
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

        shadow = new View(context);
        shadow.setAlpha(0.0f);
        shadow.setBackgroundResource(R.drawable.header_shadow);

        shadow2 = new View(context);
        shadow2.setAlpha(0.0f);
        shadow2.setBackgroundResource(R.drawable.header_shadow);

        playerLayout = new FrameLayout(context);
        playerLayout.setBackgroundColor(Theme.getColor(Theme.key_player_background));

        placeholderImageView = new BackupImageView(context) {

            private RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                if (hasNoCover == 1 || hasNoCover == 2 && (!getImageReceiver().hasBitmapImage() || getImageReceiver().getCurrentAlpha() != 1.0f)) {
                    rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    canvas.drawRoundRect(rect, getRoundRadius(), getRoundRadius(), paint);
                    float plusScale = thumbMaxScale / getScaleX() / 3;
                    int s = (int) (AndroidUtilities.dp(63) * Math.max(plusScale / thumbMaxScale, 1.0f / thumbMaxScale));
                    int x = (int) (rect.centerX() - s / 2);
                    int y = (int) (rect.centerY() - s / 2);
                    noCoverDrawable.setBounds(x, y, x + s, y + s);
                    noCoverDrawable.draw(canvas);
                }
                if (hasNoCover != 1) {
                    super.onDraw(canvas);
                }
            }
        };
        placeholderImageView.setRoundRadius(AndroidUtilities.dp(20));
        placeholderImageView.setPivotX(0);
        placeholderImageView.setPivotY(0);
        placeholderImageView.setOnClickListener(view -> {
            if (animatorSet != null) {
                animatorSet.cancel();
                animatorSet = null;
            }
            animatorSet = new AnimatorSet();
            if (scrollOffsetY <= actionBar.getMeasuredHeight()) {
                animatorSet.playTogether(ObjectAnimator.ofFloat(AudioPlayerAlert.this, "fullAnimationProgress", isInFullMode ? 0.0f : 1.0f));
            } else {
                animatorSet.playTogether(ObjectAnimator.ofFloat(AudioPlayerAlert.this, "fullAnimationProgress", isInFullMode ? 0.0f : 1.0f),
                        ObjectAnimator.ofFloat(actionBar, "alpha", isInFullMode ? 0.0f : 1.0f),
                        ObjectAnimator.ofFloat(shadow, "alpha", isInFullMode ? 0.0f : 1.0f),
                        ObjectAnimator.ofFloat(shadow2, "alpha", isInFullMode ? 0.0f : 1.0f));
            }

            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(250);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        if (!isInFullMode) {
                            listView.setScrollEnabled(true);
                            if (hasOptions) {
                                menuItem.setVisibility(View.INVISIBLE);
                            }
                            searchItem.setVisibility(View.VISIBLE);
                        } else {
                            if (hasOptions) {
                                menuItem.setVisibility(View.VISIBLE);
                            }
                            searchItem.setVisibility(View.INVISIBLE);
                        }
                        animatorSet = null;
                    }
                }
            });
            animatorSet.start();
            if (hasOptions) {
                menuItem.setVisibility(View.VISIBLE);
            }
            searchItem.setVisibility(View.VISIBLE);
            isInFullMode = !isInFullMode;
            listView.setScrollEnabled(false);
            if (isInFullMode) {
                shuffleButton.setAdditionalYOffset(-AndroidUtilities.dp(20 + 48));
            } else {
                shuffleButton.setAdditionalYOffset(-AndroidUtilities.dp(10));
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setSingleLine(true);
        playerLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 72, 18, 60, 0));

        authorTextView = new TextView(context);
        authorTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        authorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        authorTextView.setEllipsize(TextUtils.TruncateAt.END);
        authorTextView.setSingleLine(true);
        playerLayout.addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 72, 40, 60, 0));

        optionsButton = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_player_actionBarItems));
        optionsButton.setLongClickEnabled(false);
        optionsButton.setIcon(R.drawable.ic_ab_other);
        optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(120));
        playerLayout.addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 19, 10, 0));
        optionsButton.addSubItem(1, R.drawable.msg_forward, LocaleController.getString("Forward", R.string.Forward));
        optionsButton.addSubItem(2, R.drawable.msg_shareout, LocaleController.getString("ShareFile", R.string.ShareFile));
        optionsButton.addSubItem(4, R.drawable.msg_message, LocaleController.getString("ShowInChat", R.string.ShowInChat));
        optionsButton.setOnClickListener(v -> optionsButton.toggleSubMenu());
        optionsButton.setDelegate(this::onSubItemClick);
        optionsButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));

        seekBarView = new SeekBarView(context);
        seekBarView.setDelegate(progress -> MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), progress));
        playerLayout.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.TOP | Gravity.LEFT, 8, 62, 8, 0));

        progressView = new LineProgressView(context);
        progressView.setVisibility(View.INVISIBLE);
        progressView.setBackgroundColor(Theme.getColor(Theme.key_player_progressBackground));
        progressView.setProgressColor(Theme.getColor(Theme.key_player_progress));
        playerLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.TOP | Gravity.LEFT, 20, 78, 20, 0));

        timeTextView = new SimpleTextView(context);
        timeTextView.setTextSize(12);
        timeTextView.setText("0:00");
        timeTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        playerLayout.addView(timeTextView, LayoutHelper.createFrame(100, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 92, 0, 0));

        durationTextView = new TextView(context);
        durationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        durationTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        durationTextView.setGravity(Gravity.CENTER);
        playerLayout.addView(durationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 90, 20, 0));

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
        playerLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66, Gravity.TOP | Gravity.LEFT, 0, 106, 0, 0));

        buttons[0] = shuffleButton = new ActionBarMenuItem(context, null, 0, 0);
        shuffleButton.setLongClickEnabled(false);
        shuffleButton.setAdditionalYOffset(-AndroidUtilities.dp(10));
        bottomView.addView(shuffleButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        shuffleButton.setOnClickListener(v -> shuffleButton.toggleSubMenu());

        TextView textView = shuffleButton.addSubItem(1, LocaleController.getString("ReverseOrder", R.string.ReverseOrder));
        textView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16), 0);
        playOrderButtons[0] = context.getResources().getDrawable(R.drawable.music_reverse).mutate();
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        textView.setCompoundDrawablesWithIntrinsicBounds(playOrderButtons[0], null, null, null);

        textView = shuffleButton.addSubItem(2, LocaleController.getString("Shuffle", R.string.Shuffle));
        textView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16), 0);
        playOrderButtons[1] = context.getResources().getDrawable(R.drawable.pl_shuffle).mutate();
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        textView.setCompoundDrawablesWithIntrinsicBounds(playOrderButtons[1], null, null, null);

        shuffleButton.setDelegate(id -> {
            MediaController.getInstance().toggleShuffleMusic(id);
            updateShuffleButton();
            listAdapter.notifyDataSetChanged();
        });

        ImageView prevButton;
        buttons[1] = prevButton = new ImageView(context);
        prevButton.setScaleType(ImageView.ScaleType.CENTER);
        prevButton.setImageDrawable(Theme.createSimpleSelectorDrawable(context, R.drawable.pl_previous, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
        bottomView.addView(prevButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        prevButton.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
        prevButton.setContentDescription(LocaleController.getString("AccDescrPrevious", R.string.AccDescrPrevious));

        buttons[2] = playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageDrawable(Theme.createSimpleSelectorDrawable(context, R.drawable.pl_play, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
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

        ImageView nextButton;
        buttons[3] = nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(Theme.createSimpleSelectorDrawable(context, R.drawable.pl_next, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
        bottomView.addView(nextButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        nextButton.setContentDescription(LocaleController.getString("Next", R.string.Next));

        buttons[4] = repeatButton = new ImageView(context);
        repeatButton.setScaleType(ImageView.ScaleType.CENTER);
        repeatButton.setPadding(0, 0, AndroidUtilities.dp(8), 0);
        bottomView.addView(repeatButton, LayoutHelper.createFrame(50, 48, Gravity.LEFT | Gravity.TOP));
        repeatButton.setOnClickListener(v -> {
            SharedConfig.toggleRepeatMode();
            updateRepeatButton();
        });

        listView = new RecyclerListView(context) {

            boolean ignoreLayout;

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);

                if (searchOpenPosition != -1 && !actionBar.isSearchFieldVisible()) {
                    ignoreLayout = true;
                    layoutManager.scrollToPositionWithOffset(searchOpenPosition, searchOpenOffset);
                    super.onLayout(false, l, t, r, b);
                    ignoreLayout = false;
                    searchOpenPosition = -1;
                } else if (scrollToSong) {
                    scrollToSong = false;
                    boolean found = false;
                    MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (playingMessageObject != null) {
                        int count = listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = listView.getChildAt(a);
                            if (child instanceof AudioPlayerCell) {
                                if (((AudioPlayerCell) child).getMessageObject() == playingMessageObject) {
                                    if (child.getBottom() <= getMeasuredHeight()) {
                                        found = true;
                                    }
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            int idx = playlist.indexOf(playingMessageObject);
                            if (idx >= 0) {
                                ignoreLayout = true;
                                if (SharedConfig.playOrderReversed) {
                                    layoutManager.scrollToPosition(idx);
                                } else {
                                    layoutManager.scrollToPosition(playlist.size() - idx);
                                }
                                super.onLayout(false, l, t, r, b);
                                ignoreLayout = false;
                            }
                        }
                    }
                }
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return playerLayout == null || y > playerLayout.getY() + playerLayout.getMeasuredHeight();
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                canvas.save();
                canvas.clipRect(0, (actionBar != null ? actionBar.getMeasuredHeight() : 0) + AndroidUtilities.dp(50), getMeasuredWidth(), getMeasuredHeight());
                boolean result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return result;
            }
        };
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
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
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
                    AndroidUtilities.hideKeyboard(getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });

        playlist = MediaController.getInstance().getPlaylist();
        listAdapter.notifyDataSetChanged();

        containerView.addView(playerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 178));
        containerView.addView(shadow2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));
        containerView.addView(placeholderImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT, 17, 19, 0, 0));
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));
        containerView.addView(actionBar);

        updateTitle(false);
        updateRepeatButton();
        updateShuffleButton();
    }

    @Keep
    public void setFullAnimationProgress(float value) {
        fullAnimationProgress = value;
        placeholderImageView.setRoundRadius(AndroidUtilities.dp(20 * (1.0f - fullAnimationProgress)));
        float scale = 1.0f + thumbMaxScale * fullAnimationProgress;
        placeholderImageView.setScaleX(scale);
        placeholderImageView.setScaleY(scale);
        float translationY = placeholderImageView.getTranslationY();
        placeholderImageView.setTranslationX(thumbMaxX * fullAnimationProgress);
        placeholderImageView.setTranslationY(startTranslation + (endTranslation - startTranslation) * fullAnimationProgress);
        playerLayout.setTranslationY(panelStartTranslation + (panelEndTranslation - panelStartTranslation) * fullAnimationProgress);
        shadow2.setTranslationY(panelStartTranslation + (panelEndTranslation - panelStartTranslation) * fullAnimationProgress + playerLayout.getMeasuredHeight());
        menuItem.setAlpha(fullAnimationProgress);
        searchItem.setAlpha(1.0f - fullAnimationProgress);
        avatarContainer.setAlpha(1.0f - fullAnimationProgress);
        actionBar.getTitleTextView().setAlpha(fullAnimationProgress);
        actionBar.getSubtitleTextView().setAlpha(fullAnimationProgress);
    }

    @Keep
    public float getFullAnimationProgress() {
        return fullAnimationProgress;
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
        }
    }

    private int getCurrentTop() {
        if (listView.getChildCount() != 0) {
            View child = listView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
            if (holder != null) {
                return listView.getPaddingTop() - (holder.getAdapterPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
            }
        }
        return -1000;
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
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (searchWas || searching) {
            newOffset = 0;
        }
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            playerLayout.setTranslationY(Math.max(actionBar.getMeasuredHeight(), scrollOffsetY));
            placeholderImageView.setTranslationY(Math.max(actionBar.getMeasuredHeight(), scrollOffsetY));
            shadow2.setTranslationY(Math.max(actionBar.getMeasuredHeight(), scrollOffsetY) + playerLayout.getMeasuredHeight());
            containerView.invalidate();

            if (inFullSize && scrollOffsetY <= actionBar.getMeasuredHeight() || searchWas) {
                if (actionBar.getTag() == null) {
                    if (actionBarAnimation != null) {
                        actionBarAnimation.cancel();
                    }
                    actionBar.setTag(1);
                    actionBarAnimation = new AnimatorSet();
                    actionBarAnimation.playTogether(
                            ObjectAnimator.ofFloat(actionBar, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(shadow, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(shadow2, "alpha", 1.0f));
                    actionBarAnimation.setDuration(180);
                    actionBarAnimation.start();
                }
            } else {
                if (actionBar.getTag() != null) {
                    if (actionBarAnimation != null) {
                        actionBarAnimation.cancel();
                    }
                    actionBar.setTag(null);
                    actionBarAnimation = new AnimatorSet();
                    actionBarAnimation.playTogether(
                            ObjectAnimator.ofFloat(actionBar, "alpha", 0.0f),
                            ObjectAnimator.ofFloat(shadow, "alpha", 0.0f),
                            ObjectAnimator.ofFloat(shadow2, "alpha", 0.0f));
                    actionBarAnimation.setDuration(180);
                    actionBarAnimation.start();
                }
            }
        }

        startTranslation = Math.max(actionBar.getMeasuredHeight(), scrollOffsetY);
        panelStartTranslation = Math.max(actionBar.getMeasuredHeight(), scrollOffsetY);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.musicDidLoad);
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
    }

    @Override
    public void onBackPressed() {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
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
    public void onProgressDownload(String fileName, float progress) {
        progressView.setProgress(progress, true);
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    private void updateShuffleButton() {
        if (SharedConfig.shuffleMusic) {
            Drawable drawable = getContext().getResources().getDrawable(R.drawable.pl_shuffle).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
            shuffleButton.setIcon(drawable);
            shuffleButton.setContentDescription(LocaleController.getString("Shuffle", R.string.Shuffle));
        } else {
            Drawable drawable = getContext().getResources().getDrawable(R.drawable.music_reverse).mutate();
            if (SharedConfig.playOrderReversed) {
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
            } else {
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
            }
            shuffleButton.setIcon(drawable);
            shuffleButton.setContentDescription(LocaleController.getString("ReverseOrder", R.string.ReverseOrder));
        }

        playOrderButtons[0].setColorFilter(new PorterDuffColorFilter(Theme.getColor(SharedConfig.playOrderReversed ? Theme.key_player_buttonActive : Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        playOrderButtons[1].setColorFilter(new PorterDuffColorFilter(Theme.getColor(SharedConfig.shuffleMusic ? Theme.key_player_buttonActive : Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
    }

    private void updateRepeatButton() {
        int mode = SharedConfig.repeatMode;
        if (mode == 0) {
            repeatButton.setImageResource(R.drawable.pl_repeat);
            repeatButton.setTag(Theme.key_player_button);
            repeatButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
            repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatOff", R.string.AccDescrRepeatOff));
        } else if (mode == 1) {
            repeatButton.setImageResource(R.drawable.pl_repeat);
            repeatButton.setTag(Theme.key_player_buttonActive);
            repeatButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
            repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatList", R.string.AccDescrRepeatList));
        } else if (mode == 2) {
            repeatButton.setImageResource(R.drawable.pl_repeat1);
            repeatButton.setTag(Theme.key_player_buttonActive);
            repeatButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
            repeatButton.setContentDescription(LocaleController.getString("AccDescrRepeatOne", R.string.AccDescrRepeatOne));
        }
    }

    private void updateProgress(MessageObject messageObject) {
        if (seekBarView != null) {
            if (!seekBarView.isDragging()) {
                seekBarView.setProgress(messageObject.audioProgress);
                seekBarView.setBufferedProgress(messageObject.bufferedProgress);
            }
            if (lastTime != messageObject.audioProgressSec) {
                lastTime = messageObject.audioProgressSec;
                timeTextView.setText(String.format("%d:%02d", messageObject.audioProgressSec / 60, messageObject.audioProgressSec % 60));
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
                hasOptions = false;
                menuItem.setVisibility(View.INVISIBLE);
                optionsButton.setVisibility(View.INVISIBLE);
            } else {
                hasOptions = true;
                if (!actionBar.isSearchFieldVisible()) {
                    menuItem.setVisibility(View.VISIBLE);
                }
                optionsButton.setVisibility(View.VISIBLE);
            }
            checkIfMusicDownloaded(messageObject);
            updateProgress(messageObject);

            if (MediaController.getInstance().isMessagePaused()) {
                playButton.setImageDrawable(Theme.createSimpleSelectorDrawable(playButton.getContext(), R.drawable.pl_play, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
                playButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                playButton.setImageDrawable(Theme.createSimpleSelectorDrawable(playButton.getContext(), R.drawable.pl_pause, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
                playButton.setContentDescription(LocaleController.getString("AccActionPause", R.string.AccActionPause));
            }
            String title = messageObject.getMusicTitle();
            String author = messageObject.getMusicAuthor();
            titleTextView.setText(title);
            authorTextView.setText(author);
            actionBar.setTitle(title);
            actionBar.setSubtitle(author);

            String loadTitle = author + " " + title;
            AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
            if (audioInfo != null && audioInfo.getCover() != null) {
                hasNoCover = 0;
                placeholderImageView.setImageBitmap(audioInfo.getCover());
            } else {
                String artworkUrl = messageObject.getArtworkUrl(false);
                if (!TextUtils.isEmpty(artworkUrl)) {
                    placeholderImageView.setImage(artworkUrl, null, null);
                    hasNoCover = 2;
                } else {
                    placeholderImageView.setImageDrawable(null);
                    hasNoCover = 1;
                }
                placeholderImageView.invalidate();
            }

            if (durationTextView != null) {
                int duration = messageObject.getDuration();
                durationTextView.setText(duration != 0 ? String.format("%d:%02d", duration / 60, duration % 60) : "-:--");
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<MessageObject> searchResult = new ArrayList<>();
        private Timer searchTimer;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            if (searchWas) {
                return searchResult.size();
            } else if (searching) {
                return playlist.size();
            }
            return 1 + playlist.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return searchWas || holder.getAdapterPosition() > 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(178)));
                    break;
                case 1:
                default:
                    view = new AudioPlayerCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                AudioPlayerCell cell = (AudioPlayerCell) holder.itemView;
                if (searchWas) {
                    cell.setMessageObject(searchResult.get(position));
                } else if (searching) {
                    if (SharedConfig.playOrderReversed) {
                        cell.setMessageObject(playlist.get(position));
                    } else {
                        cell.setMessageObject(playlist.get(playlist.size() - position - 1));
                    }
                } else if (position > 0) {
                    if (SharedConfig.playOrderReversed) {
                        cell.setMessageObject(playlist.get(position - 1));
                    } else {
                        cell.setMessageObject(playlist.get(playlist.size() - position));
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (searchWas || searching) {
                return 1;
            }
            if (i == 0) {
                return 0;
            }
            return 1;
        }

        public void search(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null) {
                searchResult.clear();
                notifyDataSetChanged();
            } else {
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        processSearch(query);
                    }
                }, 200, 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                final ArrayList<MessageObject> copy = new ArrayList<>(playlist);
                Utilities.searchQueue.postRunnable(() -> {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        updateSearchResults(new ArrayList<>());
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

                    updateSearchResults(resultArray);
                });
            });
        }

        private void updateSearchResults(final ArrayList<MessageObject> documents) {
            AndroidUtilities.runOnUIThread(() -> {
                searchWas = true;
                searchResult = documents;
                notifyDataSetChanged();
                layoutManager.scrollToPosition(0);
            });
        }
    }
}
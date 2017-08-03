/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.io.File;

public class AudioPlayerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MediaController.FileDownloadProgressListener {

    private MessageObject lastMessageObject;
    private ImageView placeholder;
    private ImageView playButton;
    private ImageView shuffleButton;
    private LineProgressView progressView;
    private ImageView repeatButton;
    private ImageView[] buttons = new ImageView[5];
    private TextView durationTextView;
    private TextView timeTextView;
    private SeekBarView seekBarView;
    private FrameLayout seekBarContainer;
    private FrameLayout bottomView;
    private ImageView prevButton;
    private ImageView nextButton;

    private int TAG;

    private String lastTimeString;

    private class SeekBarView extends FrameLayout {

        private Paint innerPaint1;
        private Paint outerPaint1;
        private int thumbWidth;
        private int thumbHeight;
        public int thumbX = 0;
        public int thumbDX = 0;
        private boolean pressed = false;

        public SeekBarView(Context context) {
            super(context);
            setWillNotDraw(false);
            innerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerPaint1.setColor(Theme.getColor(Theme.key_player_progressBackground));

            outerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerPaint1.setColor(Theme.getColor(Theme.key_player_progress));

            thumbWidth = AndroidUtilities.dp(24);
            thumbHeight = AndroidUtilities.dp(24);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return onTouch(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return onTouch(event);
        }

        boolean onTouch(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                int additionWidth = (getMeasuredHeight() - thumbWidth) / 2;
                if (thumbX - additionWidth <= ev.getX() && ev.getX() <= thumbX + thumbWidth + additionWidth && ev.getY() >= 0 && ev.getY() <= getMeasuredHeight()) {
                    pressed = true;
                    thumbDX = (int) (ev.getX() - thumbX);
                    invalidate();
                    return true;
                }
            } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                if (pressed) {
                    if (ev.getAction() == MotionEvent.ACTION_UP) {
                        onSeekBarDrag((float) thumbX / (float) (getMeasuredWidth() - thumbWidth));
                    }
                    pressed = false;
                    invalidate();
                    return true;
                }
            } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressed) {
                    thumbX = (int) (ev.getX() - thumbDX);
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > getMeasuredWidth() - thumbWidth) {
                        thumbX = getMeasuredWidth() - thumbWidth;
                    }
                    invalidate();
                    return true;
                }
            }
            return false;
        }

        public void setProgress(float progress) {
            int newThumbX = (int) Math.ceil((getMeasuredWidth() - thumbWidth) * progress);
            if (thumbX != newThumbX) {
                thumbX = newThumbX;
                if (thumbX < 0) {
                    thumbX = 0;
                } else if (thumbX > getMeasuredWidth() - thumbWidth) {
                    thumbX = getMeasuredWidth() - thumbWidth;
                }
                invalidate();
            }
        }

        public boolean isDragging() {
            return pressed;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int y = (getMeasuredHeight() - thumbHeight) / 2;
            canvas.drawRect(thumbWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() - thumbWidth / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), innerPaint1);
            canvas.drawRect(thumbWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), thumbWidth / 2 + thumbX, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint1);
            canvas.drawCircle(thumbX + thumbWidth / 2, y + thumbHeight / 2, AndroidUtilities.dp(pressed ? 8 : 6), outerPaint1);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        TAG = MediaController.getInstance().generateObserverTag();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidStarted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        MediaController.getInstance().removeLoadingFileObserver(this);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        frameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        fragmentView = frameLayout;

        actionBar.setBackgroundColor(Theme.getColor(Theme.key_player_actionBar));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarItems), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_player_actionBarSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_player_actionBarTitle));
        actionBar.setSubtitleColor(Theme.getColor(Theme.key_player_actionBarSubtitle));

        if (!AndroidUtilities.isTablet()) {
            actionBar.showActionModeTop();
            actionBar.setActionModeTopColor(Theme.getColor(Theme.key_player_actionBarTop));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        placeholder = new ImageView(context);
        frameLayout.addView(placeholder, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 66));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 96));

        seekBarContainer = new FrameLayout(context);
        seekBarContainer.setBackgroundColor(Theme.getColor(Theme.key_player_seekBarBackground));
        frameLayout.addView(seekBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 66));

        timeTextView = new TextView(context);
        timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeTextView.setTextColor(Theme.getColor(Theme.key_player_time));
        timeTextView.setGravity(Gravity.CENTER);
        timeTextView.setText("0:00");
        seekBarContainer.addView(timeTextView, LayoutHelper.createFrame(44, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        durationTextView = new TextView(context);
        durationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        durationTextView.setTextColor(Theme.getColor(Theme.key_player_duration));
        durationTextView.setGravity(Gravity.CENTER);
        durationTextView.setText("3:00");
        seekBarContainer.addView(durationTextView, LayoutHelper.createFrame(44, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        seekBarView = new SeekBarView(context);
        seekBarContainer.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 32, 0, 32, 0));

        progressView = new LineProgressView(context);
        progressView.setVisibility(View.INVISIBLE);
        progressView.setBackgroundColor(Theme.getColor(Theme.key_player_progressBackground));
        progressView.setProgressColor(Theme.getColor(Theme.key_player_progress));
        seekBarContainer.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.CENTER_VERTICAL | Gravity.LEFT, 44, 0, 44, 0));

        bottomView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int dist = ((right - left) - AndroidUtilities.dp(30 + 48 * 5)) / 4;
                for (int a = 0; a < 5; a++) {
                    int l = AndroidUtilities.dp(15 + 48 * a) + dist * a;
                    int t = AndroidUtilities.dp(9);
                    buttons[a].layout(l, t, l + buttons[a].getMeasuredWidth(), t + buttons[a].getMeasuredHeight());
                }
            }
        };
        bottomView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        frameLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66, Gravity.BOTTOM | Gravity.LEFT));

        buttons[0] = repeatButton = new ImageView(context);
        repeatButton.setScaleType(ImageView.ScaleType.CENTER);
        bottomView.addView(repeatButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaController.getInstance().toggleRepeatMode();
                updateRepeatButton();
            }
        });

        buttons[1] = prevButton = new ImageView(context);
        prevButton.setScaleType(ImageView.ScaleType.CENTER);
        prevButton.setImageDrawable(Theme.createSimpleSelectorDrawable(context, R.drawable.pl_previous, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
        bottomView.addView(prevButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaController.getInstance().playPreviousMessage();
            }
        });

        buttons[2] = playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageDrawable(Theme.createSimpleSelectorDrawable(context, R.drawable.pl_play, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
        bottomView.addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                    return;
                }
                if (MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
                } else {
                    MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
                }
            }
        });

        buttons[3] = nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(Theme.createSimpleSelectorDrawable(context, R.drawable.pl_next, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
        bottomView.addView(nextButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaController.getInstance().playNextMessage();
            }
        });

        buttons[4] = shuffleButton = new ImageView(context);
        shuffleButton.setImageResource(R.drawable.pl_shuffle);
        shuffleButton.setScaleType(ImageView.ScaleType.CENTER);
        bottomView.addView(shuffleButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaController.getInstance().toggleShuffleMusic();
                updateShuffleButton();
            }
        });

        updateTitle(false);
        updateRepeatButton();
        updateShuffleButton();

        return frameLayout;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStarted || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            updateTitle(id == NotificationCenter.messagePlayingDidReset && (Boolean) args[1]);
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && messageObject.isMusic()) {
                updateProgress(messageObject);
            }
        }
    }

    @Override
    public void onFailedDownload(String fileName) {

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

    private void onSeekBarDrag(float progress) {
        MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), progress);
    }

    private void updateShuffleButton() {
        if (MediaController.getInstance().isShuffleMusic()) {
            shuffleButton.setTag(Theme.key_player_buttonActive);
            shuffleButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
        } else {
            shuffleButton.setTag(Theme.key_player_button);
            shuffleButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        }
    }

    private void updateRepeatButton() {
        int mode = MediaController.getInstance().getRepeatMode();
        if (mode == 0) {
            repeatButton.setImageResource(R.drawable.pl_repeat);
            repeatButton.setTag(Theme.key_player_button);
            repeatButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_button), PorterDuff.Mode.MULTIPLY));
        } else if (mode == 1) {
            repeatButton.setImageResource(R.drawable.pl_repeat);
            repeatButton.setTag(Theme.key_player_buttonActive);
            repeatButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
        } else if (mode == 2) {
            repeatButton.setImageResource(R.drawable.pl_repeat1);
            repeatButton.setTag(Theme.key_player_buttonActive);
            repeatButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_buttonActive), PorterDuff.Mode.MULTIPLY));
        }
    }

    private void updateProgress(MessageObject messageObject) {
        if (seekBarView != null) {
            if (!seekBarView.isDragging()) {
                seekBarView.setProgress(messageObject.audioProgress);
            }
            String timeString = String.format("%d:%02d", messageObject.audioProgressSec / 60, messageObject.audioProgressSec % 60);
            if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
                lastTimeString = timeString;
                timeTextView.setText(timeString);
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
        if (!cacheFile.exists()) {
            String fileName = messageObject.getFileName();
            MediaController.getInstance().addLoadingFileObserver(fileName, this);
            Float progress = ImageLoader.getInstance().getFileProgress(fileName);
            progressView.setProgress(progress != null ? progress : 0, false);
            progressView.setVisibility(View.VISIBLE);
            seekBarView.setVisibility(View.INVISIBLE);
            playButton.setEnabled(false);
        } else {
            MediaController.getInstance().removeLoadingFileObserver(this);
            progressView.setVisibility(View.INVISIBLE);
            seekBarView.setVisibility(View.VISIBLE);
            playButton.setEnabled(true);
        }
    }

    private void updateTitle(boolean shutdown) {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null && shutdown || messageObject != null && !messageObject.isMusic()) {
            if (parentLayout != null && !parentLayout.fragmentsStack.isEmpty() && parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) == this) {
                finishFragment();
            } else {
                removeSelfFromStack();
            }
        } else {
            if (messageObject == null) {
                return;
            }
            checkIfMusicDownloaded(messageObject);
            updateProgress(messageObject);

            if (MediaController.getInstance().isMessagePaused()) {
                playButton.setImageDrawable(Theme.createSimpleSelectorDrawable(playButton.getContext(), R.drawable.pl_play, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
            } else {
                playButton.setImageDrawable(Theme.createSimpleSelectorDrawable(playButton.getContext(), R.drawable.pl_pause, Theme.getColor(Theme.key_player_button), Theme.getColor(Theme.key_player_buttonActive)));
            }
            if (actionBar != null) {
                actionBar.setTitle(messageObject.getMusicTitle());
                actionBar.setSubtitle(messageObject.getMusicAuthor());
            }
            AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
            if (audioInfo != null && audioInfo.getCover() != null) {
                placeholder.setImageBitmap(audioInfo.getCover());
                placeholder.setPadding(0, 0, 0, 0);
                placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
                placeholder.setTag(null);
                placeholder.setColorFilter(null);
            } else {
                placeholder.setImageResource(R.drawable.nocover);
                placeholder.setPadding(0, 0, 0, AndroidUtilities.dp(30));
                placeholder.setScaleType(ImageView.ScaleType.CENTER);
                placeholder.setTag(Theme.key_player_placeholder);
                placeholder.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_player_placeholder), PorterDuff.Mode.MULTIPLY));
            }

            if (durationTextView != null) {
                int duration = 0;
                TLRPC.Document document = messageObject.getDocument();
                if (document != null) {
                    for (int a = 0; a < document.attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                            duration = attribute.duration;
                            break;
                        }
                    }
                }
                durationTextView.setText(duration != 0 ? String.format("%d:%02d", duration / 60, duration % 60) : "-:--");
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(bottomView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_player_actionBar),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_player_actionBarItems),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_player_actionBarSubtitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_player_actionBarSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_player_actionBarTop),

                new ThemeDescription(seekBarContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_player_seekBarBackground),

                new ThemeDescription(timeTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time),
                new ThemeDescription(durationTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_time),

                new ThemeDescription(progressView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_player_progressBackground),
                new ThemeDescription(progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_player_progress),

                new ThemeDescription(seekBarView, 0, null, seekBarView.innerPaint1, null, null, Theme.key_player_progressBackground),
                new ThemeDescription(seekBarView, 0, null, seekBarView.outerPaint1, null, null, Theme.key_player_progress),

                new ThemeDescription(repeatButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_buttonActive),
                new ThemeDescription(repeatButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button),

                new ThemeDescription(shuffleButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_buttonActive),
                new ThemeDescription(shuffleButton, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_button),

                new ThemeDescription(placeholder, ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_player_placeholder),

                new ThemeDescription(prevButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_player_button),
                new ThemeDescription(prevButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_player_buttonActive),
                new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_player_button),
                new ThemeDescription(playButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_player_buttonActive),
                new ThemeDescription(nextButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_player_button),
                new ThemeDescription(nextButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_player_buttonActive),
        };
    }
}

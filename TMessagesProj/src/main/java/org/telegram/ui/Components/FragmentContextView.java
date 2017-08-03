/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AudioPlayerActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.VoIPActivity;

public class FragmentContextView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private ImageView playButton;
    private TextView titleTextView;
    private MessageObject lastMessageObject;
    private AnimatorSet animatorSet;
    private float yPosition;
    private BaseFragment fragment;
    private float topPadding;
    private boolean visible;
    private FrameLayout frameLayout;
    private ImageView closeButton;
    private int currentStyle = -1;

    public FragmentContextView(Context context, BaseFragment parentFragment) {
        super(context);

        fragment = parentFragment;
        visible = true;
        ((ViewGroup) fragment.getFragmentView()).setClipToPadding(false);

        setTag(1);
        frameLayout = new FrameLayout(context);
        frameLayout.setWillNotDraw(false);
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow);
        addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.TOP, 0, 36, 0, 0));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerPlayPause), PorterDuff.Mode.MULTIPLY));
        addView(playButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
                } else {
                    MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
                }
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setMaxLines(1);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 0, 36, 0));

        closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.miniplayer_close);
        closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerClose), PorterDuff.Mode.MULTIPLY));
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(closeButton, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP));
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
        });

        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentStyle == 0) {
                    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (fragment != null && messageObject != null) {
                        if (messageObject.isMusic()) {
                            fragment.presentFragment(new AudioPlayerActivity());
                        } else {
                            long dialog_id = 0;
                            if (fragment instanceof ChatActivity) {
                                dialog_id = ((ChatActivity) fragment).getDialogId();
                            }
                            if (messageObject.getDialogId() == dialog_id) {
                                ((ChatActivity) fragment).scrollToMessageId(messageObject.getId(), 0, false, 0, true);
                            } else {
                                dialog_id = messageObject.getDialogId();
                                Bundle args = new Bundle();
                                int lower_part = (int) dialog_id;
                                int high_id = (int) (dialog_id >> 32);
                                if (lower_part != 0) {
                                    if (high_id == 1) {
                                        args.putInt("chat_id", lower_part);
                                    } else {
                                        if (lower_part > 0) {
                                            args.putInt("user_id", lower_part);
                                        } else if (lower_part < 0) {
                                            args.putInt("chat_id", -lower_part);
                                        }
                                    }
                                } else {
                                    args.putInt("enc_id", high_id);
                                }
                                args.putInt("message_id", messageObject.getId());
                                fragment.presentFragment(new ChatActivity(args), fragment instanceof ChatActivity);
                            }
                        }
                    }
                } else if (currentStyle == 1) {
                    Intent intent = new Intent(getContext(), VoIPActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    getContext().startActivity(intent);
                }
            }
        });
    }

    public float getTopPadding() {
        return topPadding;
    }

    public void setTopPadding(float value) {
        topPadding = value;
        if (fragment != null) {
            View view = fragment.getFragmentView();
            if (view != null) {
                view.setPadding(0, (int) topPadding, 0, 0);
            }
        }
    }

    private void updateStyle(int style) {
        if (currentStyle == style) {
            return;
        }
        currentStyle = style;
        if (style == 0) {
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_inappPlayerBackground));
            titleTextView.setTextColor(Theme.getColor(Theme.key_inappPlayerTitle));
            closeButton.setVisibility(VISIBLE);
            playButton.setVisibility(VISIBLE);
            titleTextView.setTypeface(Typeface.DEFAULT);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 0, 36, 0));
        } else if (style == 1) {
            titleTextView.setText(LocaleController.getString("ReturnToCall", R.string.ReturnToCall));
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_returnToCallBackground));
            titleTextView.setTextColor(Theme.getColor(Theme.key_returnToCallText));
            closeButton.setVisibility(GONE);
            playButton.setVisibility(GONE);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 2));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        topPadding = 0;
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidStarted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didStartedCall);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didEndedCall);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didStartedCall);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didEndedCall);
        boolean callAvailable = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING;
        if (callAvailable) {
            checkCall(true);
        } else {
            checkPlayer(true);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, AndroidUtilities.dp2(39));
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStarted || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.didEndedCall) {
            checkPlayer(false);
        } else if (id == NotificationCenter.didStartedCall) {
            checkCall(false);
        } else {
            checkPlayer(false);
        }
    }

    private void checkPlayer(boolean create) {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        View fragmentView = fragment.getFragmentView();
        if (!create && fragmentView != null) {
            if (fragmentView.getParent() == null || ((View) fragmentView.getParent()).getVisibility() != VISIBLE) {
                create = true;
            }
        }
        if (messageObject == null || messageObject.getId() == 0/* || !messageObject.isMusic()*/) {
            lastMessageObject = null;
            if (visible) {
                visible = false;
                if (create) {
                    if (getVisibility() != GONE) {
                        setVisibility(GONE);
                    }
                    setTopPadding(0);
                } else {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "translationY", -AndroidUtilities.dp2(36)),
                            ObjectAnimator.ofFloat(this, "topPadding", 0));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                setVisibility(GONE);
                                animatorSet = null;
                            }
                        }
                    });
                    animatorSet.start();
                }
            }
        } else {
            int prevStyle = currentStyle;
            updateStyle(0);
            if (create && topPadding == 0) {
                setTopPadding(AndroidUtilities.dp2(36));
                setTranslationY(0);
                yPosition = 0;
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "translationY", -AndroidUtilities.dp2(36), 0),
                            ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(36)));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                animatorSet = null;
                            }
                        }
                    });
                    animatorSet.start();
                }
                visible = true;
                setVisibility(VISIBLE);
            }
            if (MediaController.getInstance().isMessagePaused()) {
                playButton.setImageResource(R.drawable.miniplayer_play);
            } else {
                playButton.setImageResource(R.drawable.miniplayer_pause);
            }
            if (lastMessageObject != messageObject || prevStyle != 0) {
                lastMessageObject = messageObject;
                SpannableStringBuilder stringBuilder;
                if (lastMessageObject.isVoice() || lastMessageObject.isRoundVideo()) {
                    stringBuilder = new SpannableStringBuilder(String.format("%s %s", messageObject.getMusicAuthor(), messageObject.getMusicTitle()));
                    titleTextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                } else {
                    stringBuilder = new SpannableStringBuilder(String.format("%s - %s", messageObject.getMusicAuthor(), messageObject.getMusicTitle()));
                    titleTextView.setEllipsize(TextUtils.TruncateAt.END);
                }
                TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, Theme.getColor(Theme.key_inappPlayerPerformer));
                stringBuilder.setSpan(span, 0, messageObject.getMusicAuthor().length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                titleTextView.setText(stringBuilder);
            }
        }
    }

    private void checkCall(boolean create) {
        View fragmentView = fragment.getFragmentView();
        if (!create && fragmentView != null) {
            if (fragmentView.getParent() == null || ((View) fragmentView.getParent()).getVisibility() != VISIBLE) {
                create = true;
            }
        }
        boolean callAvailable = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING;
        if (!callAvailable) {
            if (visible) {
                visible = false;
                if (create) {
                    if (getVisibility() != GONE) {
                        setVisibility(GONE);
                    }
                    setTopPadding(0);
                } else {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "translationY", -AndroidUtilities.dp2(36)),
                            ObjectAnimator.ofFloat(this, "topPadding", 0));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                setVisibility(GONE);
                                animatorSet = null;
                            }
                        }
                    });
                    animatorSet.start();
                }
            }
        } else {
            updateStyle(1);
            if (create && topPadding == 0) {
                setTopPadding(AndroidUtilities.dp2(36));
                setTranslationY(0);
                yPosition = 0;
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "translationY", -AndroidUtilities.dp2(36), 0),
                            ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(36)));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                animatorSet = null;
                            }
                        }
                    });
                    animatorSet.start();
                }
                visible = true;
                setVisibility(VISIBLE);
            }
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        yPosition = translationY;
        invalidate();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int restoreToCount = canvas.save();
        if (yPosition < 0) {
            canvas.clipRect(0, (int) -yPosition, child.getMeasuredWidth(), AndroidUtilities.dp2(39));
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreToCount);
        return result;
    }
}

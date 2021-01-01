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
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Keep;

import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VoIPBaseService;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LocationActivity;

import java.util.ArrayList;

public class FragmentContextView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, VoIPBaseService.StateListener {

    private ImageView playButton;
    private PlayPauseDrawable playPauseDrawable;
    private TextView titleTextView;
    private AudioPlayerAlert.ClippingTextViewSwitcher subtitleTextView;
    private AnimatorSet animatorSet;
    private BaseFragment fragment;
    private View applyingView;
    private FrameLayout frameLayout;
    private View shadow;
    private View selector;
    private RLottieImageView muteButton;
    private RLottieDrawable muteDrawable;
    private ImageView closeButton;
    private ImageView playbackSpeedButton;
    private FragmentContextView additionalContextView;
    private TextView joinButton;

    private boolean isMuted;

    private MessageObject lastMessageObject;
    private float topPadding;
    private boolean visible;
    private int currentStyle = -1;
    private String lastString;
    private boolean isMusic;
    private boolean supportsCalls = true;
    private AvatarsImageView avatars;

    private final int account = UserConfig.selectedAccount;

    private boolean isLocation;

    private FragmentContextViewDelegate delegate;

    private boolean firstLocationsLoaded;
    private int lastLocationSharingCount = -1;
    private Runnable checkLocationRunnable = new Runnable() {
        @Override
        public void run() {
            checkLocationString();
            AndroidUtilities.runOnUIThread(checkLocationRunnable, 1000);
        }
    };
    private int animationIndex = -1;

    boolean checkCallAfterAnimation;
    boolean checkPlayerAfterAnimation;

    @Override
    public void onAudioSettingsChanged() {
        boolean newMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
        if (isMuted != newMuted) {
            isMuted = newMuted;
            muteDrawable.setCustomEndFrame(isMuted ? 15 : 29);
            muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame(), false, true);
            muteButton.invalidate();
            Theme.getFragmentContextViewWavesDrawable().updateState(visible);
        }
        if (isMuted) {
            micAmplitude = 0;
            Theme.getFragmentContextViewWavesDrawable().setAmplitude(0);
        }
    }

    public interface FragmentContextViewDelegate {
        void onAnimation(boolean start, boolean show);
    }

    public FragmentContextView(Context context, BaseFragment parentFragment, boolean location) {
        this(context, parentFragment, null, location);
    }

    public FragmentContextView(Context context, BaseFragment parentFragment, View paddingView, boolean location) {
        super(context);

        fragment = parentFragment;
        applyingView = paddingView;
        visible = true;
        isLocation = location;
        if (applyingView == null) {
            ((ViewGroup) fragment.getFragmentView()).setClipToPadding(false);
        }

        setTag(1);
        frameLayout = new FrameLayout(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                if (avatars != null && avatars.getVisibility() == VISIBLE) {
                    avatars.invalidate();
                }
            }
        };
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        selector = new View(context);
        frameLayout.addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.blockpanel_shadow);
        addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.LEFT | Gravity.TOP, 0, 36, 0, 0));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerPlayPause), PorterDuff.Mode.MULTIPLY));
        playButton.setImageDrawable(playPauseDrawable = new PlayPauseDrawable(14));
        if (Build.VERSION.SDK_INT >= 21) {
            playButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_inappPlayerPlayPause) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
        }
        addView(playButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT));
        playButton.setOnClickListener(v -> {
            if (currentStyle == 0) {
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

        subtitleTextView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                TextView textView = new TextView(context);
                textView.setMaxLines(1);
                textView.setLines(1);
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setGravity(Gravity.LEFT);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                textView.setTextColor(Theme.getColor(Theme.key_inappPlayerClose));
                return textView;
            }
        };
        addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 10, 36, 0));

        joinButton = new TextView(context);
        joinButton.setText(LocaleController.getString("VoipChatJoin", R.string.VoipChatJoin));
        joinButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        joinButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        joinButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        joinButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        joinButton.setGravity(Gravity.CENTER);
        joinButton.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        addView(joinButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.RIGHT, 0, 10, 14, 0));
        joinButton.setOnClickListener(v -> FragmentContextView.this.callOnClick());

        if (!location) {
            playbackSpeedButton = new ImageView(context);
            playbackSpeedButton.setScaleType(ImageView.ScaleType.CENTER);
            playbackSpeedButton.setImageResource(R.drawable.voice2x);
            playbackSpeedButton.setContentDescription(LocaleController.getString("AccDescrPlayerSpeed", R.string.AccDescrPlayerSpeed));
            if (AndroidUtilities.density >= 3.0f) {
                playbackSpeedButton.setPadding(0, 1, 0, 0);
            }
            addView(playbackSpeedButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 0, 0, 36, 0));
            playbackSpeedButton.setOnClickListener(v -> {
                float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(isMusic);
                if (currentPlaybackSpeed > 1) {
                    MediaController.getInstance().setPlaybackSpeed(isMusic, 1.0f);
                } else {
                    MediaController.getInstance().setPlaybackSpeed(isMusic, 1.8f);
                }
            });
            updatePlaybackButton();
        }

        avatars = new AvatarsImageView(context);
        avatars.setDelegate(() -> updateAvatars(true));
        avatars.setVisibility(GONE);
        addView(avatars, LayoutHelper.createFrame(108, 36, Gravity.LEFT | Gravity.TOP));

        muteDrawable = new RLottieDrawable(R.raw.voice_muted, "" + R.raw.voice_muted, AndroidUtilities.dp(16), AndroidUtilities.dp(20), true, null);

        muteButton = new RLottieImageView(context) {
            boolean scheduled;
            boolean pressed;

            private final Runnable toggleMicRunnable = () -> {
                if (VoIPService.getSharedInstance() == null) {
                    return;
                }
                VoIPService.getSharedInstance().setMicMute(false, true, false);
                if (muteDrawable.setCustomEndFrame(isMuted ? 15 : 29)) {
                    if (isMuted) {
                        muteDrawable.setCurrentFrame(0);
                    } else {
                        muteDrawable.setCurrentFrame(14);
                    }
                }
                muteButton.playAnimation();

                Theme.getFragmentContextViewWavesDrawable().updateState(true);
            };


            private final Runnable pressRunnable = () -> {
                if (!scheduled || VoIPService.getSharedInstance() == null) {
                    return;
                }
                scheduled = false;
                pressed = true;
                isMuted = false;

                AndroidUtilities.runOnUIThread(toggleMicRunnable, 90);
                muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            };


            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentStyle == 3) {
                    VoIPService service = VoIPService.getSharedInstance();
                    if (service == null) {
                        AndroidUtilities.cancelRunOnUIThread(pressRunnable);
                        AndroidUtilities.cancelRunOnUIThread(toggleMicRunnable);
                        scheduled = false;
                        pressed = false;
                        return true;
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN && service.isMicMute()) {
                        AndroidUtilities.runOnUIThread(pressRunnable, 300);
                        scheduled = true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        AndroidUtilities.cancelRunOnUIThread(toggleMicRunnable);
                        if (scheduled) {
                            AndroidUtilities.cancelRunOnUIThread(pressRunnable);
                            scheduled = false;
                        } else if (pressed) {
                            isMuted = true;
                            if (muteDrawable.setCustomEndFrame(isMuted ? 15 : 29)) {
                                if (isMuted) {
                                    muteDrawable.setCurrentFrame(0);
                                } else {
                                    muteDrawable.setCurrentFrame(14);
                                }
                            }
                            muteButton.playAnimation();
                            if (VoIPService.getSharedInstance() != null) {
                                VoIPService.getSharedInstance().setMicMute(true, true, false);
                                muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                            pressed = false;
                            Theme.getFragmentContextViewWavesDrawable().updateState(true);
                            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                            super.onTouchEvent(cancel);
                            cancel.recycle();
                            return true;
                        }
                    }
                    return super.onTouchEvent(event);
                } else {
                    return super.onTouchEvent(event);
                }
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName(Button.class.getName());
                info.setText(isMuted ? LocaleController.getString("VoipUnmute", R.string.VoipUnmute) : LocaleController.getString("VoipMute", R.string.VoipMute));
            }
        };
        muteButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_returnToCallText), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            muteButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_inappPlayerClose) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
        }
        muteButton.setAnimation(muteDrawable);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        muteButton.setVisibility(GONE);
        addView(muteButton, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 0, 0, 2, 0));
        muteButton.setOnClickListener(v -> {
            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService == null) {
                return;
            }
            ChatObject.Call call = voIPService.groupCall;
            AccountInstance accountInstance = AccountInstance.getInstance(voIPService.getAccount());
            TLRPC.Chat chat = voIPService.getChat();
            TLRPC.TL_groupCallParticipant participant = call.participants.get(accountInstance.getUserConfig().getClientUserId());
            if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(chat)) {
                return;
            }

            isMuted = !voIPService.isMicMute();
            voIPService.setMicMute(isMuted, false, true);
            if (muteDrawable.setCustomEndFrame(isMuted ? 15 : 29)) {
                if (isMuted) {
                    muteDrawable.setCurrentFrame(0);
                } else {
                    muteDrawable.setCurrentFrame(14);
                }
            }
            muteButton.playAnimation();
            Theme.getFragmentContextViewWavesDrawable().updateState(true);
            muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        });

        closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.miniplayer_close);
        closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerClose), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            closeButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_inappPlayerClose) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
        }
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(closeButton, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 0, 0, 2, 0));
        closeButton.setOnClickListener(v -> {
            if (currentStyle == 2) {
                AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
                builder.setTitle(LocaleController.getString("StopLiveLocationAlertToTitle", R.string.StopLiveLocationAlertToTitle));
                if (fragment instanceof DialogsActivity) {
                    builder.setMessage(LocaleController.getString("StopLiveLocationAlertAllText", R.string.StopLiveLocationAlertAllText));
                } else {
                    ChatActivity activity = (ChatActivity) fragment;
                    TLRPC.Chat chat = activity.getCurrentChat();
                    TLRPC.User user = activity.getCurrentUser();
                    if (chat != null) {
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("StopLiveLocationAlertToGroupText", R.string.StopLiveLocationAlertToGroupText, chat.title)));
                    } else if (user != null) {
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("StopLiveLocationAlertToUserText", R.string.StopLiveLocationAlertToUserText, UserObject.getFirstName(user))));
                    } else {
                        builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                    }
                }
                builder.setPositiveButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    if (fragment instanceof DialogsActivity) {
                        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                            LocationController.getInstance(a).removeAllLocationSharings();
                        }
                    } else {
                        LocationController.getInstance(fragment.getCurrentAccount()).removeSharingLocation(((ChatActivity) fragment).getDialogId());
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                builder.show();
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            } else {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
        });

        setOnClickListener(v -> {
            if (currentStyle == 0) {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (fragment != null && messageObject != null) {
                    if (messageObject.isMusic()) {
                        fragment.showDialog(new AudioPlayerAlert(getContext()));
                    } else {
                        long dialog_id = 0;
                        if (fragment instanceof ChatActivity) {
                            dialog_id = ((ChatActivity) fragment).getDialogId();
                        }
                        if (messageObject.getDialogId() == dialog_id) {
                            ((ChatActivity) fragment).scrollToMessageId(messageObject.getId(), 0, false, 0, true, 0);
                        } else {
                            dialog_id = messageObject.getDialogId();
                            Bundle args = new Bundle();
                            int lower_part = (int) dialog_id;
                            int high_id = (int) (dialog_id >> 32);
                            if (lower_part != 0) {
                                if (lower_part > 0) {
                                    args.putInt("user_id", lower_part);
                                } else {
                                    args.putInt("chat_id", -lower_part);
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
                Intent intent = new Intent(getContext(), LaunchActivity.class).setAction("voip");
                getContext().startActivity(intent);
            } else if (currentStyle == 2) {
                long did = 0;
                int account = UserConfig.selectedAccount;
                if (fragment instanceof ChatActivity) {
                    did = ((ChatActivity) fragment).getDialogId();
                    account = fragment.getCurrentAccount();
                } else if (LocationController.getLocationsCount() == 1) {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        ArrayList<LocationController.SharingLocationInfo> arrayList = LocationController.getInstance(a).sharingLocationsUI;
                        if (!arrayList.isEmpty()) {
                            LocationController.SharingLocationInfo info = LocationController.getInstance(a).sharingLocationsUI.get(0);
                            did = info.did;
                            account = info.messageObject.currentAccount;
                            break;
                        }
                    }
                }
                if (did != 0) {
                    openSharingLocation(LocationController.getInstance(account).getSharingLocationInfo(did));
                } else {
                    fragment.showDialog(new SharingLocationsAlert(getContext(), this::openSharingLocation));
                }
            } else if (currentStyle == 3) {
             //   long d = Theme.getFragmentContextViewWavesDrawable().getRippleFinishedDelay();
               // AndroidUtilities.runOnUIThread(() -> {
                    if (VoIPService.getSharedInstance() != null && getContext() instanceof LaunchActivity) {
                        GroupCallActivity.create((LaunchActivity) getContext(), AccountInstance.getInstance(VoIPService.getSharedInstance().getAccount()));
                    }
               // }, d);
            } else if (currentStyle == 4) {
                if (fragment.getParentActivity() == null) {
                    return;
                }
                ChatActivity chatActivity = (ChatActivity) fragment;
                ChatObject.Call call = chatActivity.getGroupCall();
                if (call == null) {
                    return;
                }
                VoIPHelper.startCall(chatActivity.getMessagesController().getChat(call.chatId), false, fragment.getParentActivity());
            }
        });
    }

    public void setSupportsCalls(boolean value) {
        supportsCalls = value;
    }

    public void setDelegate(FragmentContextViewDelegate fragmentContextViewDelegate) {
        delegate = fragmentContextViewDelegate;
    }

    private void updatePlaybackButton() {
        if (playbackSpeedButton == null) {
            return;
        }
        float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(isMusic);
        String key;
        if (currentPlaybackSpeed > 1) {
            key = Theme.key_inappPlayerPlayPause;
        } else {
            key = Theme.key_inappPlayerClose;
        }
        playbackSpeedButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(key), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            playbackSpeedButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(key) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
        }
    }

    public void setAdditionalContextView(FragmentContextView contextView) {
        additionalContextView = contextView;
    }

    private void openSharingLocation(final LocationController.SharingLocationInfo info) {
        if (info == null || !(fragment.getParentActivity() instanceof LaunchActivity)) {
            return;
        }
        LaunchActivity launchActivity = ((LaunchActivity) fragment.getParentActivity());
        launchActivity.switchToAccount(info.messageObject.currentAccount, true);

        LocationActivity locationActivity = new LocationActivity(2);
        locationActivity.setMessageObject(info.messageObject);
        final long dialog_id = info.messageObject.getDialogId();
        locationActivity.setDelegate((location, live, notify, scheduleDate) -> SendMessagesHelper.getInstance(info.messageObject.currentAccount).sendMessage(location, dialog_id, null, null, null, null, notify, scheduleDate));
        launchActivity.presentFragment(locationActivity);
    }

    @Keep
    public float getTopPadding() {
        return topPadding;
    }

    private void checkVisibility() {
        boolean show = false;
        if (isLocation) {
            if (fragment instanceof DialogsActivity) {
                show = LocationController.getLocationsCount() != 0;
            } else {
                show = LocationController.getInstance(fragment.getCurrentAccount()).isSharingLocation(((ChatActivity) fragment).getDialogId());
            }
        } else {
            if (VoIPService.getSharedInstance() != null && !VoIPService.getSharedInstance().isHangingUp() && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING) {
                show = true;
            } else if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getGroupCall() != null) {
                show = true;
            } else {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.getId() != 0) {
                    show = true;
                }
            }
        }
        setVisibility(show ? VISIBLE : GONE);
    }

    @Keep
    public void setTopPadding(float value) {
        topPadding = value;
        if (fragment != null && getParent() != null) {
            View view = applyingView != null ? applyingView : fragment.getFragmentView();
            int additionalPadding = 0;
            if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE && additionalContextView.getParent() != null) {
                additionalPadding = AndroidUtilities.dp(additionalContextView.getStyleHeight());
            }
            if (view != null && getParent() != null) {
                view.setPadding(0, (int) (getVisibility() == View.VISIBLE ? topPadding : 0) + additionalPadding, 0, 0);
            }
        }
    }

    private void updateStyle(int style) {
        if (currentStyle == style) {
            return;
        }
        if (currentStyle == 3) {
            Theme.getFragmentContextViewWavesDrawable().removeParent(this);
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().unregisterStateListener(this);
            }
        }
        currentStyle = style;


        if (avatars != null) {
            avatars.setStyle(currentStyle);
            avatars.setLayoutParams(LayoutHelper.createFrame(108, getStyleHeight(), Gravity.LEFT | Gravity.TOP));
        }
        frameLayout.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, getStyleHeight(), Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        shadow.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.LEFT | Gravity.TOP, 0, getStyleHeight(), 0, 0));

        if (topPadding > 0 && topPadding != AndroidUtilities.dp2(getStyleHeight())) {
            updatePaddings();
            setTopPadding(AndroidUtilities.dp2(getStyleHeight()));
        }
        if (style == 0 || style == 2) {
            selector.setBackground(Theme.getSelectorDrawable(false));
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_inappPlayerBackground));
            frameLayout.setTag(Theme.key_inappPlayerBackground);
            titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            titleTextView.setTextColor(Theme.getColor(Theme.key_inappPlayerTitle));
            titleTextView.setTag(Theme.key_inappPlayerTitle);
            subtitleTextView.setVisibility(GONE);
            joinButton.setVisibility(GONE);
            closeButton.setVisibility(VISIBLE);
            playButton.setVisibility(VISIBLE);
            muteButton.setVisibility(GONE);
            avatars.setVisibility(GONE);
            titleTextView.setTypeface(Typeface.DEFAULT);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            if (style == 0) {
                playButton.setLayoutParams(LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 0, 36, 0));
                if (playbackSpeedButton != null) {
                    playbackSpeedButton.setVisibility(VISIBLE);
                }
                closeButton.setContentDescription(LocaleController.getString("AccDescrClosePlayer", R.string.AccDescrClosePlayer));
            } else {
                playButton.setLayoutParams(LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 8, 0, 0, 0));
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35 + 16, 0, 36, 0));
                closeButton.setContentDescription(LocaleController.getString("AccDescrStopLiveLocation", R.string.AccDescrStopLiveLocation));
            }
        } else if (style == 4) {
            selector.setBackground(Theme.getSelectorDrawable(false));
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_inappPlayerBackground));
            frameLayout.setTag(Theme.key_inappPlayerBackground);
            muteButton.setVisibility(GONE);

            subtitleTextView.setVisibility(VISIBLE);
            joinButton.setVisibility(VISIBLE);

            titleTextView.setTextColor(Theme.getColor(Theme.key_inappPlayerPerformer));
            titleTextView.setTag(Theme.key_inappPlayerPerformer);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleTextView.setPadding(0, 0, 0, 0);
            titleTextView.setText(LocaleController.getString("VoipGroupVoiceChat", R.string.VoipGroupVoiceChat));
            titleTextView.setGravity(Gravity.TOP | Gravity.LEFT);

            avatars.setVisibility(VISIBLE);
            updateAvatars(false);

            closeButton.setVisibility(GONE);
            playButton.setVisibility(GONE);
            if (playbackSpeedButton != null) {
                playbackSpeedButton.setVisibility(GONE);
            }
        } else if (style == 1 || style == 3) {
            selector.setBackground(null);
            if (style == 3) {
                updateGroupCallTitle();
                muteButton.setVisibility(VISIBLE);
                avatars.setVisibility(VISIBLE);
                updateAvatars(false);
                isMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
                muteDrawable.setCustomEndFrame(isMuted ? 15 : 29);
                muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true);
                muteButton.invalidate();
                frameLayout.setBackground(null);
                Theme.getFragmentContextViewWavesDrawable().addParent(this);
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().registerStateListener(this);
                }
                invalidate();
            } else {
                frameLayout.setTag(Theme.key_returnToCallBackground);
                titleTextView.setText(LocaleController.getString("ReturnToCall", R.string.ReturnToCall));
                muteButton.setVisibility(GONE);
                avatars.setVisibility(GONE);
                frameLayout.setBackgroundColor(Theme.getColor(Theme.key_returnToCallBackground));
            }
            titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            titleTextView.setTextColor(Theme.getColor(Theme.key_returnToCallText));
            titleTextView.setTag(Theme.key_returnToCallText);
            closeButton.setVisibility(GONE);
            playButton.setVisibility(GONE);
            subtitleTextView.setVisibility(GONE);
            joinButton.setVisibility(GONE);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 2));
            titleTextView.setPadding(AndroidUtilities.dp(112), 0, AndroidUtilities.dp(112), 0);
            if (playbackSpeedButton != null) {
                playbackSpeedButton.setVisibility(GONE);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        visible = false;
        NotificationCenter.getInstance(account).onAnimationFinish(animationIndex);
        topPadding = 0;
        if (isLocation) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.liveLocationsChanged);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.liveLocationsCacheChanged);
        } else {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.groupCallUpdated);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.groupCallTypingsUpdated);
            }
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingSpeedChanged);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didStartedCall);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didEndCall);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.groupCallVisibilityChanged);
        }

        if (currentStyle == 3) {
            Theme.getFragmentContextViewWavesDrawable().removeParent(this);
        }
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().unregisterStateListener(this);
        }
        wasDraw = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isLocation) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.liveLocationsChanged);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.liveLocationsCacheChanged);
            if (additionalContextView != null) {
                additionalContextView.checkVisibility();
            }
            checkLiveLocation(true);
        } else {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidStart);
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.groupCallUpdated);
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.groupCallTypingsUpdated);
            }
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingSpeedChanged);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didStartedCall);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didEndCall);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.groupCallVisibilityChanged);
            if (additionalContextView != null) {
                additionalContextView.checkVisibility();
            }

            if (VoIPService.getSharedInstance() != null && !VoIPService.getSharedInstance().isHangingUp() && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING && !GroupCallPip.isShowing()) {
                checkCall(true);
            } else if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getGroupCall() != null && !GroupCallPip.isShowing() && !isPlayingVoice()) {
                checkCall(true);
            } else {
                checkPlayer(true);
                updatePlaybackButton();
            }
        }

        if (currentStyle == 3) {
            Theme.getFragmentContextViewWavesDrawable().addParent(this);
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().registerStateListener(this);
            }
            boolean newMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
            if (isMuted != newMuted) {
                isMuted = newMuted;
                muteDrawable.setCustomEndFrame(isMuted ? 15 : 29);
                muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true);
                muteButton.invalidate();
            }
        }

        if (visible && topPadding == 0) {
            updatePaddings();
            setTopPadding(AndroidUtilities.dp2(getStyleHeight()));
        }

         speakerAmplitude = 0;
         micAmplitude = 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, AndroidUtilities.dp2(getStyleHeight() + 2));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.liveLocationsChanged) {
            checkLiveLocation(false);
        } else if (id == NotificationCenter.liveLocationsCacheChanged) {
            if (fragment instanceof ChatActivity) {
                long did = (Long) args[0];
                if (((ChatActivity) fragment).getDialogId() == did) {
                    checkLocationString();
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.didEndCall) {
            if (currentStyle == 3 || currentStyle == 4) {
                checkCall(false);
            }
            checkPlayer(false);
        } else if (id == NotificationCenter.didStartedCall || id == NotificationCenter.groupCallUpdated || id == NotificationCenter.groupCallVisibilityChanged) {
            checkCall(false);
            if (currentStyle == 3) {
                VoIPService sharedInstance = VoIPService.getSharedInstance();
                if (sharedInstance != null && sharedInstance.groupCall != null) {
                    if (id == NotificationCenter.didStartedCall) {
                        sharedInstance.registerStateListener(this);
                    }
                    int currentCallState = sharedInstance.getCallState();
                    if (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING) {

                    } else {
                        TLRPC.TL_groupCallParticipant participant = sharedInstance.groupCall.participants.get(AccountInstance.getInstance(sharedInstance.getAccount()).getUserConfig().getClientUserId());
                        if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(sharedInstance.getChat())) {
                            sharedInstance.setMicMute(true, false, false);
                            final long now = SystemClock.uptimeMillis();
                            final MotionEvent e = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                            muteButton.dispatchTouchEvent(e);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.groupCallTypingsUpdated) {
            if (visible && currentStyle == 4) {
                ChatObject.Call call = ((ChatActivity) fragment).getGroupCall();
                if (call != null) {
                    if (call.call.participants_count == 0) {
                        subtitleTextView.setText(LocaleController.getString("MembersTalkingNobody", R.string.MembersTalkingNobody));
                    } else {
                        subtitleTextView.setText(LocaleController.formatPluralString("Participants", call.call.participants_count));
                    }
                }
                updateAvatars(true);
            }
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updatePlaybackButton();
        } else if (id == NotificationCenter.webRtcMicAmplitudeEvent) {
            if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().isMicMute()) {
                micAmplitude = 0;
            } else {
                micAmplitude = (Math.min(GroupCallActivity.MAX_AMPLITUDE, ((float) args[0]) * 4000) / GroupCallActivity.MAX_AMPLITUDE);
            }
            if (VoIPService.getSharedInstance() != null) {
                Theme.getFragmentContextViewWavesDrawable().setAmplitude(Math.max(speakerAmplitude, micAmplitude));
            }
        } else if (id == NotificationCenter.webRtcSpeakerAmplitudeEvent) {
            float a = (float) args[0] * 15f / 80f;
            speakerAmplitude = Math.max(0, Math.min(a, 1));
            if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().isMicMute()) {
                micAmplitude = 0;
            }
            if (VoIPService.getSharedInstance() != null) {
                Theme.getFragmentContextViewWavesDrawable().setAmplitude(Math.max(speakerAmplitude, micAmplitude));
            }
            avatars.invalidate();
        }
    }

    float speakerAmplitude;
    float micAmplitude;

    public int getStyleHeight() {
        return currentStyle == 4 ? 48 : 36;
    }

    private void checkLiveLocation(boolean create) {
        View fragmentView = fragment.getFragmentView();
        if (!create && fragmentView != null) {
            if (fragmentView.getParent() == null || ((View) fragmentView.getParent()).getVisibility() != VISIBLE) {
                create = true;
            }
        }
        boolean show;
        if (fragment instanceof DialogsActivity) {
            show = LocationController.getLocationsCount() != 0;
        } else {
            show = LocationController.getInstance(fragment.getCurrentAccount()).isSharingLocation(((ChatActivity) fragment).getDialogId());
        }
        if (!show) {
            lastLocationSharingCount = -1;
            AndroidUtilities.cancelRunOnUIThread(checkLocationRunnable);
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
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0));
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
            updateStyle(2);
            playButton.setImageDrawable(new ShareLocationDrawable(getContext(), 1));
            if (create && topPadding == 0) {
                setTopPadding(AndroidUtilities.dp2(getStyleHeight()));
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(getStyleHeight())));
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

            if (fragment instanceof DialogsActivity) {
                String liveLocation = LocaleController.getString("LiveLocationContext", R.string.LiveLocationContext);
                String param;
                String str;
                ArrayList<LocationController.SharingLocationInfo> infos = new ArrayList<>();
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    infos.addAll(LocationController.getInstance(a).sharingLocationsUI);
                }
                if (infos.size() == 1) {
                    LocationController.SharingLocationInfo info = infos.get(0);
                    int lower_id = (int) info.messageObject.getDialogId();
                    if (lower_id > 0) {
                        TLRPC.User user = MessagesController.getInstance(info.messageObject.currentAccount).getUser(lower_id);
                        param = UserObject.getFirstName(user);
                        str = LocaleController.getString("AttachLiveLocationIsSharing", R.string.AttachLiveLocationIsSharing);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(info.messageObject.currentAccount).getChat(-lower_id);
                        if (chat != null) {
                            param = chat.title;
                        } else {
                            param = "";
                        }
                        str = LocaleController.getString("AttachLiveLocationIsSharingChat", R.string.AttachLiveLocationIsSharingChat);
                    }
                } else {
                    param = LocaleController.formatPluralString("Chats", infos.size());
                    str = LocaleController.getString("AttachLiveLocationIsSharingChats", R.string.AttachLiveLocationIsSharingChats);
                }
                String fullString = String.format(str, liveLocation, param);
                int start = fullString.indexOf(liveLocation);
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(fullString);
                titleTextView.setEllipsize(TextUtils.TruncateAt.END);
                TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, Theme.getColor(Theme.key_inappPlayerPerformer));
                stringBuilder.setSpan(span, start, start + liveLocation.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                titleTextView.setText(stringBuilder);
            } else {
                checkLocationRunnable.run();
                checkLocationString();
            }
        }
    }

    private void checkLocationString() {
        if (!(fragment instanceof ChatActivity) || titleTextView == null) {
            return;
        }
        ChatActivity chatActivity = (ChatActivity) fragment;
        long dialogId = chatActivity.getDialogId();
        int currentAccount = chatActivity.getCurrentAccount();
        ArrayList<TLRPC.Message> messages = LocationController.getInstance(currentAccount).locationsCache.get(dialogId);
        if (!firstLocationsLoaded) {
            LocationController.getInstance(currentAccount).loadLiveLocations(dialogId);
            firstLocationsLoaded = true;
        }

        int locationSharingCount = 0;
        TLRPC.User notYouUser = null;
        if (messages != null) {
            int currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            int date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            for (int a = 0; a < messages.size(); a++) {
                TLRPC.Message message = messages.get(a);
                if (message.media == null) {
                    continue;
                }
                if (message.date + message.media.period > date) {
                    int fromId = MessageObject.getFromChatId(message);
                    if (notYouUser == null && fromId != currentUserId) {
                        notYouUser = MessagesController.getInstance(currentAccount).getUser(fromId);
                    }
                    locationSharingCount++;
                }
            }
        }
        if (lastLocationSharingCount == locationSharingCount) {
            return;
        }
        lastLocationSharingCount = locationSharingCount;

        String liveLocation = LocaleController.getString("LiveLocationContext", R.string.LiveLocationContext);
        String fullString;
        if (locationSharingCount == 0) {
            fullString = liveLocation;
        } else {
            int otherSharingCount = locationSharingCount - 1;
            if (LocationController.getInstance(currentAccount).isSharingLocation(dialogId)) {
                if (otherSharingCount != 0) {
                    if (otherSharingCount == 1 && notYouUser != null) {
                        fullString = String.format("%1$s - %2$s", liveLocation, LocaleController.formatString("SharingYouAndOtherName", R.string.SharingYouAndOtherName, UserObject.getFirstName(notYouUser)));
                    } else {
                        fullString = String.format("%1$s - %2$s %3$s", liveLocation, LocaleController.getString("ChatYourSelfName", R.string.ChatYourSelfName), LocaleController.formatPluralString("AndOther", otherSharingCount));
                    }
                } else {
                    fullString = String.format("%1$s - %2$s", liveLocation, LocaleController.getString("ChatYourSelfName", R.string.ChatYourSelfName));
                }
            } else {
                if (otherSharingCount != 0) {
                    fullString = String.format("%1$s - %2$s %3$s", liveLocation, UserObject.getFirstName(notYouUser), LocaleController.formatPluralString("AndOther", otherSharingCount));
                } else {
                    fullString = String.format("%1$s - %2$s", liveLocation, UserObject.getFirstName(notYouUser));
                }
            }
        }
        if (fullString.equals(lastString)) {
            return;
        }
        lastString = fullString;
        int start = fullString.indexOf(liveLocation);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(fullString);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        if (start >= 0) {
            TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, Theme.getColor(Theme.key_inappPlayerPerformer));
            stringBuilder.setSpan(span, start, start + liveLocation.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        titleTextView.setText(stringBuilder);
    }

    private void checkPlayer(boolean create) {
        if (visible && (currentStyle == 3 || currentStyle == 4 && !isPlayingVoice())) {
            return;
        }
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        View fragmentView = fragment.getFragmentView();
        if (!create && fragmentView != null) {
            if (fragmentView.getParent() == null || ((View) fragmentView.getParent()).getVisibility() != VISIBLE) {
                create = true;
            }
        }
        if (messageObject == null || messageObject.getId() == 0 || messageObject.isVideo()) {
            lastMessageObject = null;
            boolean callAvailable = supportsCalls && VoIPService.getSharedInstance() != null && !VoIPService.getSharedInstance().isHangingUp() && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING && !GroupCallPip.isShowing();
            if (!isPlayingVoice() && !callAvailable && fragment instanceof ChatActivity && ((ChatActivity) fragment).getGroupCall() != null && !GroupCallPip.isShowing()) {
                callAvailable = true;
            }
            if (callAvailable) {
                checkCall(false);
                return;
            }
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
                    animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null);
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0));
                    animatorSet.setDuration(200);
                    if (delegate != null) {
                        delegate.onAnimation(true, false);
                    }
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            NotificationCenter.getInstance(account).onAnimationFinish(animationIndex);
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                setVisibility(GONE);
                                if (delegate != null) {
                                    delegate.onAnimation(false, false);
                                }
                                animatorSet = null;
                                if (checkCallAfterAnimation) {
                                    checkCall(false);
                                } else if (checkPlayerAfterAnimation) {
                                    checkPlayer(false);
                                }
                                checkCallAfterAnimation = false;
                                checkPlayerAfterAnimation = false;
                            }
                        }
                    });
                    animatorSet.start();
                }
            } else {
                setVisibility(View.GONE);
            }
        } else {
            if (currentStyle != 0 && animatorSet != null && !create) {
                checkPlayerAfterAnimation = true;
                return;
            }
            int prevStyle = currentStyle;
            updateStyle(0);
            if (create && topPadding == 0) {
                updatePaddings();
                setTopPadding(AndroidUtilities.dp2(getStyleHeight()));
                if (delegate != null) {
                    delegate.onAnimation(true, true);
                    delegate.onAnimation(false, true);
                }
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null);
                    animatorSet = new AnimatorSet();
                    if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(getStyleHeight() + additionalContextView.getStyleHeight());
                    } else {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(getStyleHeight());
                    }
                    if (delegate != null) {
                        delegate.onAnimation(true, true);
                    }
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(getStyleHeight())));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            NotificationCenter.getInstance(account).onAnimationFinish(animationIndex);
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                if (delegate != null) {
                                    delegate.onAnimation(false, true);
                                }
                                animatorSet = null;
                                if (checkCallAfterAnimation) {
                                    checkCall(false);
                                } else if (checkPlayerAfterAnimation) {
                                    checkPlayer(false);
                                }
                                checkCallAfterAnimation = false;
                                checkPlayerAfterAnimation = false;
                            }
                        }
                    });
                    animatorSet.start();
                }
                visible = true;
                setVisibility(VISIBLE);
            }
            if (MediaController.getInstance().isMessagePaused()) {
                playPauseDrawable.setPause(false, !create);
                playButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                playPauseDrawable.setPause(true, !create);
                playButton.setContentDescription(LocaleController.getString("AccActionPause", R.string.AccActionPause));
            }
            if (lastMessageObject != messageObject || prevStyle != 0) {
                lastMessageObject = messageObject;
                SpannableStringBuilder stringBuilder;
                if (lastMessageObject.isVoice() || lastMessageObject.isRoundVideo()) {
                    isMusic = false;
                    if (playbackSpeedButton != null) {
                        playbackSpeedButton.setAlpha(1.0f);
                        playbackSpeedButton.setEnabled(true);
                    }
                    titleTextView.setPadding(0, 0, AndroidUtilities.dp(44), 0);
                    stringBuilder = new SpannableStringBuilder(String.format("%s %s", messageObject.getMusicAuthor(), messageObject.getMusicTitle()));
                    titleTextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    updatePlaybackButton();
                } else {
                    isMusic = true;
                    if (playbackSpeedButton != null) {
                        if (messageObject.getDuration() >= 20 * 60) {
                            playbackSpeedButton.setAlpha(1.0f);
                            playbackSpeedButton.setEnabled(true);
                            titleTextView.setPadding(0, 0, AndroidUtilities.dp(44), 0);
                            updatePlaybackButton();
                        } else {
                            playbackSpeedButton.setAlpha(0.0f);
                            playbackSpeedButton.setEnabled(false);
                            titleTextView.setPadding(0, 0, 0, 0);
                        }
                    } else {
                        titleTextView.setPadding(0, 0, 0, 0);
                    }
                    stringBuilder = new SpannableStringBuilder(String.format("%s - %s", messageObject.getMusicAuthor(), messageObject.getMusicTitle()));
                    titleTextView.setEllipsize(TextUtils.TruncateAt.END);
                }
                TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, Theme.getColor(Theme.key_inappPlayerPerformer));
                stringBuilder.setSpan(span, 0, messageObject.getMusicAuthor().length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                titleTextView.setText(stringBuilder);
            }
        }
    }

    private boolean isPlayingVoice() {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        return messageObject != null && messageObject.isVoice();
    }

    public void checkCall(boolean create) {
        View fragmentView = fragment.getFragmentView();
        if (!create && fragmentView != null) {
            if (fragmentView.getParent() == null || ((View) fragmentView.getParent()).getVisibility() != VISIBLE) {
                create = true;
            }
        }
        boolean callAvailable;
        boolean groupActive;
        if (GroupCallPip.isShowing()) {
            callAvailable = false;
            groupActive = false;
        } else {
            callAvailable = !GroupCallActivity.groupCallUiVisible && supportsCalls && VoIPService.getSharedInstance() != null && !VoIPService.getSharedInstance().isHangingUp();
            if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().groupCall != null && VoIPService.getSharedInstance().groupCall.call instanceof TLRPC.TL_groupCallDiscarded) {
                callAvailable = false;
            }
            groupActive = false;
            if (!isPlayingVoice() && !GroupCallActivity.groupCallUiVisible && supportsCalls && !callAvailable && fragment instanceof ChatActivity && ((ChatActivity) fragment).getGroupCall() != null) {
                callAvailable = true;
                groupActive = true;
            }
        }

        if (!callAvailable) {
            if (visible && (create && currentStyle == -1 || currentStyle == 4 || currentStyle == 3)) {
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
                    final int currentAccount = account;
                    animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0));
                    animatorSet.setDuration(220);
                    animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                setVisibility(GONE);
                                animatorSet = null;
                                if (checkCallAfterAnimation) {
                                    checkCall(false);
                                } else if (checkPlayerAfterAnimation) {
                                    checkPlayer(false);
                                }
                                checkCallAfterAnimation = false;
                                checkPlayerAfterAnimation = false;
                            }
                        }
                    });
                    animatorSet.start();
                }
            } else if (currentStyle == -1 || currentStyle == 4 || currentStyle == 3) {
                visible = false;
                setVisibility(GONE);
            }
        } else {
            int newStyle;
            if (groupActive) {
                newStyle = 4;
            } else if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().groupCall != null) {
                newStyle = 3;
            } else {
                newStyle = 1;
            }
            if (newStyle != currentStyle && animatorSet != null && !create) {
                checkCallAfterAnimation = true;
                return;
            }
            if (newStyle != currentStyle && visible && !create) {
                if (animatorSet != null) {
                    animatorSet.cancel();
                    animatorSet = null;
                }
                final int currentAccount = account;
                animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
                animatorSet = new AnimatorSet();
                animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0));
                animatorSet.setDuration(220);
                animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                        if (animatorSet != null && animatorSet.equals(animation)) {
                            visible = false;
                            animatorSet = null;
                            checkCall(false);
                        }
                    }
                });
                animatorSet.start();
                return;
            }
            if (groupActive) {
                boolean updateAnimated = currentStyle == 4 && visible;
                updateStyle(4);

                ChatObject.Call call = ((ChatActivity) fragment).getGroupCall();

                if (call.call.participants_count == 0) {
                    subtitleTextView.setText(LocaleController.getString("MembersTalkingNobody", R.string.MembersTalkingNobody));
                } else {
                    subtitleTextView.setText(LocaleController.formatPluralString("Participants", call.call.participants_count));
                }

                updateAvatars(avatars.wasDraw && updateAnimated);
            } else {
                if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().groupCall != null) {
                    updateAvatars(currentStyle == 3);
                    updateStyle(3);
                } else {
                    updateStyle(1);
                }
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(getStyleHeight() + additionalContextView.getStyleHeight());
                    } else {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(getStyleHeight());
                    }
                    final int currentAccount = account;
                    animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(getStyleHeight())));
                    animatorSet.setDuration(220);
                    animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                animatorSet = null;
                            }
                            if (checkCallAfterAnimation) {
                                checkCall(false);
                            } else if (checkPlayerAfterAnimation) {
                                checkPlayer(false);
                            }
                            checkCallAfterAnimation = false;
                            checkPlayerAfterAnimation = false;
                        }
                    });
                    animatorSet.start();
                } else {
                    updatePaddings();
                    setTopPadding(AndroidUtilities.dp2(getStyleHeight()));
                }
                visible = true;
                setVisibility(VISIBLE);
            }
        }
    }

    private void updateAvatars(boolean animated) {
        if (!animated) {
            if (avatars.transitionProgressAnimator != null) {
                avatars.transitionProgressAnimator.cancel();
                avatars.transitionProgressAnimator = null;
            }
        }
        ChatObject.Call call;
        if (avatars.transitionProgressAnimator == null) {
            int currentAccount;
            if (currentStyle == 4) {
                if (fragment instanceof ChatActivity) {
                    ChatActivity chatActivity = (ChatActivity) fragment;
                    call = chatActivity.getGroupCall();
                    currentAccount = chatActivity.getCurrentAccount();
                } else {
                    call = null;
                    currentAccount = account;
                }
            } else {
                if (VoIPService.getSharedInstance() != null) {
                    call = VoIPService.getSharedInstance().groupCall;
                    currentAccount = VoIPService.getSharedInstance().getAccount();
                } else {
                    call = null;
                    currentAccount = account;
                }
            }
            if (call != null) {
                for (int a = 0, N = call.sortedParticipants.size(); a < 3; a++) {
                    if (a < N) {
                        avatars.setObject(a, currentAccount, call.sortedParticipants.get(a));
                    } else {
                        avatars.setObject(a, currentAccount, null);
                    }
                }
                avatars.commitTransition(animated);
            } else {
                for (int a = 0; a < 3; a++) {
                    avatars.setObject(a, currentAccount, null);
                }
                avatars.commitTransition(animated);
            }

            if (currentStyle == 4 && call != null) {
                int N = Math.min(3, call.sortedParticipants.size());
                int x = N == 0 ? 10 : (10 + 24 * (N - 1) + 32 + 10);
                if (animated) {
                    int leftMargin = ((LayoutParams) titleTextView.getLayoutParams()).leftMargin;
                    if (AndroidUtilities.dp(x) != leftMargin) {
                        float dx = titleTextView.getTranslationX() + leftMargin - AndroidUtilities.dp(x);
                        titleTextView.setTranslationX(dx);
                        subtitleTextView.setTranslationX(dx);
                        titleTextView.animate().translationX(0).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT);
                        subtitleTextView.animate().translationX(0).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    }
                } else {
                    titleTextView.animate().cancel();
                    subtitleTextView.animate().cancel();
                    titleTextView.setTranslationX(0);
                    subtitleTextView.setTranslationX(0);
                }
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT | Gravity.TOP, x, 5, 36, 0));
                subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT | Gravity.TOP, x, 25, 36, 0));
            }
        } else {
            avatars.updateAfterTransitionEnd();
        }
    }


    boolean collapseTransition;
    float extraHeight;
    float collapseProgress;
    boolean wasDraw;

    public void setCollapseTransition(boolean show, float extraHeight, float progress) {
        collapseTransition = show;
        this.extraHeight = extraHeight;
        this.collapseProgress = progress;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawOverlay && getVisibility() != View.VISIBLE) {
            return;
        }
        boolean clipped = false;
        if (currentStyle == 3 && drawOverlay) {
            Theme.getFragmentContextViewWavesDrawable().updateState(wasDraw);
            float progress = topPadding / AndroidUtilities.dp((getStyleHeight()));

            if (collapseTransition) {
                Theme.getFragmentContextViewWavesDrawable().draw(0, AndroidUtilities.dp((getStyleHeight())) - topPadding + extraHeight, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(2), canvas, null, Math.min(progress, (1f - collapseProgress)));
            } else {
                Theme.getFragmentContextViewWavesDrawable().draw(0, AndroidUtilities.dp((getStyleHeight())) - topPadding, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(2), canvas, this, progress);
            }
            float clipTop = AndroidUtilities.dp((getStyleHeight())) - topPadding;
            if (collapseTransition) {
                clipTop += extraHeight;
            }
            if (clipTop > getMeasuredHeight()) {
                return;
            }
            clipped = true;
            canvas.save();
            canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
            invalidate();
        }
        super.dispatchDraw(canvas);
        if (clipped) {
            canvas.restore();
        }
        wasDraw = true;
    }

    boolean drawOverlay;

    public void setDrawOverlay(boolean drawOverlay) {
        this.drawOverlay = drawOverlay;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (currentStyle == 3) {
            if (getParent() != null) {
                ((View) getParent()).invalidate();
            }
        }
    }

    public int getCurrentStyle() {
        return currentStyle;
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updatePaddings();
        setTopPadding(topPadding);
        if (visibility == View.GONE) {
            wasDraw = false;
        }
    }

    private void updatePaddings() {
        int margin = 0;
        if (getVisibility() == VISIBLE) {
            margin -= AndroidUtilities.dp(getStyleHeight());
        }
        if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
            margin -= AndroidUtilities.dp(additionalContextView.getStyleHeight());
            ((LayoutParams) getLayoutParams()).topMargin = margin;
            ((LayoutParams) additionalContextView.getLayoutParams()).topMargin = margin;
        } else {
            ((LayoutParams) getLayoutParams()).topMargin = margin;
        }
    }

    @Override
    public void onStateChanged(int state) {
        updateGroupCallTitle();
    }

    private void updateGroupCallTitle() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && currentStyle == 3) {
            int currentCallState = service.getCallState();
            if (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING) {
                titleTextView.setText(LocaleController.getString("VoipGroupConnecting", R.string. VoipGroupConnecting));
            } else if (service.getChat() != null) {
                if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getCurrentChat() != null && ((ChatActivity) fragment).getCurrentChat().id == service.getChat().id) {
                    titleTextView.setText(LocaleController.getString("VoipGroupViewVoiceChat", R.string.VoipGroupViewVoiceChat));
                } else {
                    titleTextView.setText(service.getChat().title);
                }
            }
        }
    }

    public float hotspotX;
    public float hotspotY;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            hotspotX = ev.getX();
            hotspotY = ev.getY();
        }
        return super.onInterceptTouchEvent(ev);
    }

}

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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Keep;
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
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LocationActivity;
import org.telegram.ui.VoIPActivity;

import java.util.ArrayList;

public class FragmentContextView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private ImageView playButton;
    private TextView titleTextView;
    private AnimatorSet animatorSet;
    private BaseFragment fragment;
    private View applyingView;
    private FrameLayout frameLayout;
    private View selector;
    private ImageView closeButton;
    private ImageView playbackSpeedButton;
    private FragmentContextView additionalContextView;

    private MessageObject lastMessageObject;
    private float yPosition;
    private float topPadding;
    private boolean visible;
    private int currentStyle = -1;
    private String lastString;
    private boolean isMusic;

    private boolean isLocation;

    private FragmentContextViewDelegate delegate;

    private boolean firstLocationsLoaded;
    private boolean loadingSharingCount;
    private int lastLocationSharingCount = -1;
    private Runnable checkLocationRunnable = new Runnable() {
        @Override
        public void run() {
            checkLocationString();
            AndroidUtilities.runOnUIThread(checkLocationRunnable, 1000);
        }
    };

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
        frameLayout = new FrameLayout(context);
        frameLayout.setWillNotDraw(false);
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        selector = new View(context);
        frameLayout.addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow);
        addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.TOP, 0, 36, 0, 0));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerPlayPause), PorterDuff.Mode.MULTIPLY));
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

        closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.miniplayer_close);
        closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_inappPlayerClose), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            closeButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_inappPlayerClose) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
        }
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(closeButton, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP));
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
                            ((ChatActivity) fragment).scrollToMessageId(messageObject.getId(), 0, false, 0, true);
                        } else {
                            dialog_id = messageObject.getDialogId();
                            Bundle args = new Bundle();
                            int lower_part = (int) dialog_id;
                            int high_id = (int) (dialog_id >> 32);
                            if (lower_part != 0) {
                                if (lower_part > 0) {
                                    args.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
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
                Intent intent = new Intent(getContext(), VoIPActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
                } else {
                    did = 0;
                }
                if (did != 0) {
                    openSharingLocation(LocationController.getInstance(account).getSharingLocationInfo(did));
                } else {
                    fragment.showDialog(new SharingLocationsAlert(getContext(), this::openSharingLocation));
                }
            }
        });
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
        if (info == null || fragment.getParentActivity() == null) {
            return;
        }
        LaunchActivity launchActivity = ((LaunchActivity) fragment.getParentActivity());
        launchActivity.switchToAccount(info.messageObject.currentAccount, true);

        LocationActivity locationActivity = new LocationActivity(2);
        locationActivity.setMessageObject(info.messageObject);
        final long dialog_id = info.messageObject.getDialogId();
        locationActivity.setDelegate((location, live, notify, scheduleDate) -> SendMessagesHelper.getInstance(info.messageObject.currentAccount).sendMessage(location, dialog_id, null, null, null, notify, scheduleDate));
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
            if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING) {
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
                additionalPadding = AndroidUtilities.dp(36);
            }
            if (view != null && getParent() != null) {
                view.setPadding(0, (int) topPadding + additionalPadding, 0, 0);
            }
            if (isLocation && additionalContextView != null) {
                ((LayoutParams) additionalContextView.getLayoutParams()).topMargin = -AndroidUtilities.dp(36) - (int) topPadding;
            }
        }
    }

    private void updateStyle(int style) {
        if (currentStyle == style) {
            return;
        }
        currentStyle = style;
        if (style == 0 || style == 2) {
            selector.setBackground(Theme.getSelectorDrawable(false));
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_inappPlayerBackground));
            frameLayout.setTag(Theme.key_inappPlayerBackground);
            titleTextView.setTextColor(Theme.getColor(Theme.key_inappPlayerTitle));
            titleTextView.setTag(Theme.key_inappPlayerTitle);
            closeButton.setVisibility(VISIBLE);
            playButton.setVisibility(VISIBLE);
            titleTextView.setTypeface(Typeface.DEFAULT);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            if (style == 0) {
                playButton.setLayoutParams(LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 0, 36, 0));
                if (playbackSpeedButton != null) {
                    playbackSpeedButton.setVisibility(VISIBLE);
                }
                closeButton.setContentDescription(LocaleController.getString("AccDescrClosePlayer", R.string.AccDescrClosePlayer));
            } else if (style == 2) {
                playButton.setLayoutParams(LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 8, 0, 0, 0));
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35 + 16, 0, 36, 0));
                closeButton.setContentDescription(LocaleController.getString("AccDescrStopLiveLocation", R.string.AccDescrStopLiveLocation));
            }
        } else if (style == 1) {
            selector.setBackground(null);
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_returnToCallBackground));
            frameLayout.setTag(Theme.key_returnToCallBackground);
            titleTextView.setText(LocaleController.getString("ReturnToCall", R.string.ReturnToCall));
            titleTextView.setTextColor(Theme.getColor(Theme.key_returnToCallText));
            titleTextView.setTag(Theme.key_returnToCallText);
            closeButton.setVisibility(GONE);
            playButton.setVisibility(GONE);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 2));
            titleTextView.setPadding(0, 0, 0, 0);
            if (playbackSpeedButton != null) {
                playbackSpeedButton.setVisibility(GONE);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        topPadding = 0;
        if (isLocation) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.liveLocationsChanged);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.liveLocationsCacheChanged);
        } else {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            }
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingSpeedChanged);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didStartedCall);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didEndCall);
        }
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
            }
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingSpeedChanged);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didStartedCall);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didEndCall);
            if (additionalContextView != null) {
                additionalContextView.checkVisibility();
            }
            if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING) {
                checkCall(true);
            } else {
                checkPlayer(true);
                updatePlaybackButton();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, AndroidUtilities.dp2(39));
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
            checkPlayer(false);
        } else if (id == NotificationCenter.didStartedCall) {
            checkCall(false);
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updatePlaybackButton();
        }
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
                setTopPadding(AndroidUtilities.dp2(36));
                yPosition = 0;
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(36)));
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
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(info.messageObject.currentAccount).getChat(-lower_id);
                        if (chat != null) {
                            param = chat.title;
                        } else {
                            param = "";
                        }
                    }
                } else {
                    param = LocaleController.formatPluralString("Chats", infos.size());
                }
                String fullString = String.format(LocaleController.getString("AttachLiveLocationIsSharing", R.string.AttachLiveLocationIsSharing), liveLocation, param);
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
                    if (notYouUser == null && message.from_id != currentUserId) {
                        notYouUser = MessagesController.getInstance(currentAccount).getUser(message.from_id);
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
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        View fragmentView = fragment.getFragmentView();
        if (!create && fragmentView != null) {
            if (fragmentView.getParent() == null || ((View) fragmentView.getParent()).getVisibility() != VISIBLE) {
                create = true;
            }
        }
        if (messageObject == null || messageObject.getId() == 0 || messageObject.isVideo()) {
            lastMessageObject = null;
            boolean callAvailable = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING;
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
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0));
                    animatorSet.setDuration(200);
                    if (delegate != null) {
                        delegate.onAnimation(true, false);
                    }
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                setVisibility(GONE);
                                if (delegate != null) {
                                    delegate.onAnimation(false, false);
                                }
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
                if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                } else {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                }
                yPosition = 0;
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
                    animatorSet = new AnimatorSet();
                    if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                    } else {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                    }
                    if (delegate != null) {
                        delegate.onAnimation(true, true);
                    }
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(36)));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                if (delegate != null) {
                                    delegate.onAnimation(false, true);
                                }
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
                playButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                playButton.setImageResource(R.drawable.miniplayer_pause);
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
            updateStyle(1);
            if (create && topPadding == 0) {
                setTopPadding(AndroidUtilities.dp2(36));
                if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                } else {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                }
                yPosition = 0;
            }
            if (!visible) {
                if (!create) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                    } else {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                    }
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", AndroidUtilities.dp2(36)));
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
}

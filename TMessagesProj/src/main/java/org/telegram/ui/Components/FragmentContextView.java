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
import android.content.DialogInterface;
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
import org.telegram.ui.LocationActivity;
import org.telegram.ui.VoIPActivity;

import java.util.ArrayList;

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
    private String lastString;

    private FragmentContextView additionalContextView;

    private boolean isLocation;

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

    public FragmentContextView(Context context, BaseFragment parentFragment, boolean location) {
        super(context);

        fragment = parentFragment;
        visible = true;
        isLocation = location;
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
                if (currentStyle == 0) {
                    if (MediaController.getInstance().isMessagePaused()) {
                        MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
                    } else {
                        MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
                    }
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
                if (currentStyle == 2) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    if (fragment instanceof DialogsActivity) {
                        builder.setMessage(LocaleController.getString("StopLiveLocationAlertAll", R.string.StopLiveLocationAlertAll));
                    } else {
                        ChatActivity activity = (ChatActivity) fragment;
                        TLRPC.Chat chat = activity.getCurrentChat();
                        TLRPC.User user = activity.getCurrentUser();
                        if (chat != null) {
                            builder.setMessage(LocaleController.formatString("StopLiveLocationAlertToGroup", R.string.StopLiveLocationAlertToGroup, chat.title));
                        } else if (user != null) {
                            builder.setMessage(LocaleController.formatString("StopLiveLocationAlertToUser", R.string.StopLiveLocationAlertToUser, UserObject.getFirstName(user)));
                        } else {
                            builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                        }
                    }
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (fragment instanceof DialogsActivity) {
                                LocationController.getInstance().removeAllLocationSharings();
                            } else {
                                LocationController.getInstance().removeSharingLocation(((ChatActivity) fragment).getDialogId());
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.show();
                } else {
                    MediaController.getInstance().cleanupPlayer(true, true);
                }
            }
        });

        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                } else if (currentStyle == 2) {
                    long did;
                    if (fragment instanceof ChatActivity) {
                        did = ((ChatActivity) fragment).getDialogId();
                    } else if (LocationController.getInstance().sharingLocationsUI.size() == 1) {
                        did = LocationController.getInstance().sharingLocationsUI.get(0).did;
                    } else {
                        did = 0;
                    }
                    if (did != 0) {
                        openSharingLocation(LocationController.getInstance().getSharingLocationInfo(did));
                    } else {
                        fragment.showDialog(new SharingLocationsAlert(getContext(), new SharingLocationsAlert.SharingLocationsAlertDelegate() {
                            @Override
                            public void didSelectLocation(LocationController.SharingLocationInfo info) {
                                openSharingLocation(info);
                            }
                        }));
                    }
                }
            }
        });
    }

    public void setAdditionalContextView(FragmentContextView contextView) {
        additionalContextView = contextView;
    }

    private void openSharingLocation(LocationController.SharingLocationInfo info) {
        if (info == null) {
            return;
        }
        LocationActivity locationActivity = new LocationActivity(2);
        locationActivity.setMessageObject(info.messageObject);
        final long dialog_id = info.messageObject.getDialogId();
        locationActivity.setDelegate(new LocationActivity.LocationActivityDelegate() {
            @Override
            public void didSelectLocation(TLRPC.MessageMedia location, int live) {
                SendMessagesHelper.getInstance().sendMessage(location, dialog_id, null, null, null);
            }
        });
        fragment.presentFragment(locationActivity);
    }

    public float getTopPadding() {
        return topPadding;
    }

    private void checkVisibility() {
        boolean show = false;
        if (isLocation) {
            if (fragment instanceof DialogsActivity) {
                show = !LocationController.getInstance().sharingLocationsUI.isEmpty();
            } else {
                show = LocationController.getInstance().isSharingLocation(((ChatActivity) fragment).getDialogId());
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

    public void setTopPadding(float value) {
        topPadding = value;
        if (fragment != null) {
            View view = fragment.getFragmentView();
            int additionalPadding = 0;
            if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                additionalPadding = AndroidUtilities.dp(36);
            }
            if (view != null) {
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
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_inappPlayerBackground));
            titleTextView.setTextColor(Theme.getColor(Theme.key_inappPlayerTitle));
            closeButton.setVisibility(VISIBLE);
            playButton.setVisibility(VISIBLE);
            titleTextView.setTypeface(Typeface.DEFAULT);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 0, 36, 0));
            if (style == 0) {
                playButton.setLayoutParams(LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35, 0, 36, 0));
            } else if (style == 2) {
                playButton.setLayoutParams(LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 8, 0, 0, 0));
                titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 35 + 16, 0, 36, 0));
            }
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
        if (isLocation) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.liveLocationsChanged);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.liveLocationsCacheChanged);
        } else {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidStarted);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didStartedCall);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didEndedCall);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isLocation) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.liveLocationsChanged);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.liveLocationsCacheChanged);
            if (additionalContextView != null) {
                additionalContextView.checkVisibility();
            }
            checkLiveLocation(true);
        } else {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidStarted);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didStartedCall);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didEndedCall);
            if (additionalContextView != null) {
                additionalContextView.checkVisibility();
            }
            if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getCallState() != VoIPService.STATE_WAITING_INCOMING) {
                checkCall(true);
            } else {
                checkPlayer(true);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, AndroidUtilities.dp2(39));
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.liveLocationsChanged) {
            checkLiveLocation(false);
        } else if (id == NotificationCenter.liveLocationsCacheChanged) {
            if (fragment instanceof ChatActivity) {
                long did = (Long) args[0];
                if (((ChatActivity) fragment).getDialogId() == did) {
                    checkLocationString();
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidStarted || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.didEndedCall) {
            checkPlayer(false);
        } else if (id == NotificationCenter.didStartedCall) {
            checkCall(false);
        } else {
            checkPlayer(false);
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
            show = !LocationController.getInstance().sharingLocationsUI.isEmpty();
        } else {
            show = LocationController.getInstance().isSharingLocation(((ChatActivity) fragment).getDialogId());
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
            updateStyle(2);
            playButton.setImageDrawable(new ShareLocationDrawable(getContext(), true));
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

            if (fragment instanceof DialogsActivity) {
                String liveLocation = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                String param;
                ArrayList<LocationController.SharingLocationInfo> infos = LocationController.getInstance().sharingLocationsUI;
                if (infos.size() == 1) {
                    LocationController.SharingLocationInfo info = infos.get(0);
                    int lower_id = (int) info.messageObject.getDialogId();
                    if (lower_id > 0) {
                        TLRPC.User user = MessagesController.getInstance().getUser(lower_id);
                        param = UserObject.getFirstName(user);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
                        if (chat != null) {
                            param = chat.title;
                        } else {
                            param = "";
                        }
                    }
                } else {
                    param = LocaleController.formatPluralString("Chats", LocationController.getInstance().sharingLocationsUI.size());
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
        ArrayList<TLRPC.Message> messages = LocationController.getInstance().locationsCache.get(dialogId);
        if (!firstLocationsLoaded) {
            LocationController.getInstance().loadLiveLocations(dialogId);
            firstLocationsLoaded = true;
        }

        int locationSharingCount = 0;
        TLRPC.User notYouUser = null;
        if (messages != null) {
            int currentUserId = UserConfig.getClientUserId();
            int date = ConnectionsManager.getInstance().getCurrentTime();
            for (int a = 0; a < messages.size(); a++) {
                TLRPC.Message message = messages.get(a);
                if (message.media == null) {
                    continue;
                }
                if (message.date + message.media.period > date) {
                    if (notYouUser == null && message.from_id != currentUserId) {
                        notYouUser = MessagesController.getInstance().getUser(message.from_id);
                    }
                    locationSharingCount++;
                }
            }
        }
        if (lastLocationSharingCount == locationSharingCount) {
            return;
        }
        lastLocationSharingCount = locationSharingCount;

        String liveLocation = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
        String fullString;
        if (locationSharingCount == 0) {
            fullString = liveLocation;
        } else {
            int otherSharingCount = locationSharingCount - 1;
            if (LocationController.getInstance().isSharingLocation(dialogId)) {
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
        if (lastString != null && fullString.equals(lastString)) {
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
                if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                } else {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                }
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
                    if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                    } else {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                    }
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
                if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                } else {
                    ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                }
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
                    if (additionalContextView != null && additionalContextView.getVisibility() == VISIBLE) {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(72);
                    } else {
                        ((LayoutParams) getLayoutParams()).topMargin = -AndroidUtilities.dp(36);
                    }
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

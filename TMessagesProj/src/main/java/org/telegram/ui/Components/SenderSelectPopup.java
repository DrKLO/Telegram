package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SenderSelectPopup extends ActionBarPopupWindow {
    public final static float SPRING_STIFFNESS = 750f;
    public final static int AVATAR_SIZE_DP = 40;
    private final static int SHADOW_DURATION = 150;
    private final static float SCALE_START = 0.25f;

    public View dimView;
    public LinearLayout recyclerContainer;
    public TextView headerText;

    protected boolean runningCustomSprings;

    private TLRPC.ChatFull chatFull;
    private TLRPC.TL_channels_sendAsPeers sendAsPeers;
    private final int currentAccount;

    private FrameLayout scrimPopupContainerLayout;
    private View headerShadow;
    private RecyclerListView recyclerView;
    private LinearLayoutManager layoutManager;

    private Boolean isHeaderShadowVisible;

    private boolean clicked;

    protected List<SpringAnimation> springAnimations = new ArrayList<>();
    private boolean dismissed;

    private FrameLayout bulletinContainer;
    private Runnable bulletinHideCallback;
    private boolean isDismissingByBulletin;
    private int popupX, popupY;

    private List<Bulletin> bulletins = new ArrayList<>();

    @SuppressLint("WrongConstant")
    public SenderSelectPopup(Context context, ChatActivity parentFragment, MessagesController messagesController, TLRPC.ChatFull chatFull, TLRPC.TL_channels_sendAsPeers sendAsPeers, OnSelectCallback selectCallback) {
        super(context);

        this.chatFull = chatFull;
        this.sendAsPeers = sendAsPeers;
        this.currentAccount = parentFragment == null ? UserConfig.selectedAccount : parentFragment.getCurrentAccount();

        scrimPopupContainerLayout = new BackButtonFrameLayout(context);
        scrimPopupContainerLayout.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        setContentView(scrimPopupContainerLayout);

        setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        setHeight(WindowManager.LayoutParams.WRAP_CONTENT);

        setBackgroundDrawable(null);

        Drawable shadowDrawable = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        scrimPopupContainerLayout.setBackground(shadowDrawable);

        Rect padding = new Rect();
        shadowDrawable.getPadding(padding);
        scrimPopupContainerLayout.setPadding(padding.left, padding.top, padding.right, padding.bottom);

        dimView = new View(context);
        dimView.setBackgroundColor(0x33000000);

        int maxHeight = AndroidUtilities.dp(450);
        int maxWidth = (int) (parentFragment.contentView.getWidth() * 0.75f);
        recyclerContainer = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthMeasureSpec), maxWidth), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeight), MeasureSpec.getMode(heightMeasureSpec)));
            }

            @Override
            protected int getSuggestedMinimumWidth() {
                return AndroidUtilities.dp(260);
            }
        };
        recyclerContainer.setOrientation(LinearLayout.VERTICAL);
        headerText = new TextView(context);
        headerText.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        headerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        headerText.setText(LocaleController.getString(R.string.SendMessageAsTitle));
        headerText.setTypeface(AndroidUtilities.bold(), Typeface.BOLD);
        int dp = AndroidUtilities.dp(18);
        headerText.setPadding(dp, AndroidUtilities.dp(12), dp, AndroidUtilities.dp(12));
        recyclerContainer.addView(headerText);

        FrameLayout recyclerFrameLayout = new FrameLayout(context);

        List<TLRPC.TL_sendAsPeer> peers = sendAsPeers.peers;

        recyclerView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new SenderView(parent.getContext()));
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                SenderView senderView = (SenderView) holder.itemView;
                TLRPC.TL_sendAsPeer peerObj = peers.get(position);
                TLRPC.Peer peer = peerObj.peer;
                long peerId = 0;

                if (peer.channel_id != 0)  {
                    peerId = -peer.channel_id;
                }
                if (peerId == 0 && peer.user_id != 0)  {
                    peerId = peer.user_id;
                }

                if (peerId < 0) {
                    TLRPC.Chat chat = messagesController.getChat(-peerId);
                    if (chat != null) {
                        if (peerObj.premium_required) {
                            SpannableString str = new SpannableString(TextUtils.ellipsize(chat.title, senderView.title.getPaint(), maxWidth - AndroidUtilities.dp(100), TextUtils.TruncateAt.END) + " d");
                            ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_mini_premiumlock);
                            span.setTopOffset(1);
                            span.setSize(AndroidUtilities.dp(14));
                            span.setColorKey(Theme.key_windowBackgroundWhiteGrayText5);
                            str.setSpan(span, str.length() - 1, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                            senderView.title.setEllipsize(null);
                            senderView.title.setText(str);
                        } else {
                            senderView.title.setEllipsize(TextUtils.TruncateAt.END);
                            senderView.title.setText(chat.title);
                        }
                        senderView.subtitle.setText(LocaleController.formatPluralString(ChatObject.isChannel(chat) && !chat.megagroup ? "Subscribers" : "Members", chat.participants_count));
                        senderView.avatar.setAvatar(chat);
                    }
                    senderView.avatar.setSelected(chatFull.default_send_as != null ? chatFull.default_send_as.channel_id == peer.channel_id : position == 0, false);
                } else {
                    TLRPC.User user = messagesController.getUser(peerId);
                    if (user != null) {
                        senderView.title.setText(UserObject.getUserName(user));
                        senderView.subtitle.setText(LocaleController.getString(R.string.VoipGroupPersonalAccount));
                        senderView.avatar.setAvatar(user);
                    }
                    senderView.avatar.setSelected(chatFull.default_send_as != null ? chatFull.default_send_as.user_id == peer.user_id : position == 0, false);
                }
            }

            @Override
            public int getItemCount() {
                return peers.size();
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                boolean show = layoutManager.findFirstCompletelyVisibleItemPosition() != 0;
                if (isHeaderShadowVisible == null || show != isHeaderShadowVisible) {
                    headerShadow.animate().cancel();
                    headerShadow.animate().alpha(show ? 1 : 0).setDuration(SHADOW_DURATION).start();
                    isHeaderShadowVisible = show;
                }
            }
        });
        recyclerView.setOnItemClickListener((view, position) -> {
            TLRPC.TL_sendAsPeer peerObj = peers.get(position);
            if (clicked) {
                return;
            }
            if (peerObj.premium_required && !UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}

                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (bulletinContainer == null) {
                    bulletinContainer = new FrameLayout(context) {
                        @SuppressLint("ClickableViewAccessibility")
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            View contentView = getContentView();
                            int[] contentXY = new int[2];
                            contentView.getLocationInWindow(contentXY);
                            contentXY[0] += popupX;
                            contentXY[1] += popupY;
                            int[] viewXY = new int[2];
                            getLocationInWindow(viewXY);
                            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getX() <= contentXY[0] ||
                                    event.getX() >= contentXY[0] + contentView.getWidth() || event.getY() <= contentXY[1] ||
                                    event.getY() >= contentXY[1] + contentView.getHeight()) {
                                if (dismissed || isDismissingByBulletin) {
                                    return true;
                                }
                                isDismissingByBulletin = true;

                                startDismissAnimation();

                                return true;
                            }
                            event.offsetLocation(viewXY[0] - contentXY[0], AndroidUtilities.statusBarHeight + viewXY[1] - contentXY[1]);
                            return contentView.dispatchTouchEvent(event);
                        }
                    };
                }
                if (bulletinHideCallback != null) {
                    AndroidUtilities.cancelRunOnUIThread(bulletinHideCallback);
                }

                if (bulletinContainer.getParent() == null) {
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                    params.width = params.height = WindowManager.LayoutParams.MATCH_PARENT;
                    params.format = PixelFormat.TRANSLUCENT;
                    params.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        params.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                    AndroidUtilities.setPreferredMaxRefreshRate(windowManager, bulletinContainer, params);
                    windowManager.addView(bulletinContainer, params);
                }

                final TLRPC.Chat chat = chatFull == null ? null : MessagesController.getInstance(currentAccount).getChat(chatFull.id);
                final boolean toChannel = ChatObject.isChannelAndNotMegaGroup(chat);
                Bulletin bulletin = Bulletin.make(bulletinContainer, new SelectSendAsPremiumHintBulletinLayout(context, parentFragment.themeDelegate, toChannel, () -> {
                    if (parentFragment != null) {
                        parentFragment.presentFragment(new PremiumPreviewFragment("select_sender"));
                        dismiss();
                    }
                }), Bulletin.DURATION_SHORT);
                bulletin.getLayout().addCallback(new Bulletin.Layout.Callback() {
                    @Override
                    public void onShow(@NonNull Bulletin.Layout layout) {
                        bulletins.add(bulletin);
                    }

                    @Override
                    public void onHide(@NonNull Bulletin.Layout layout) {
                        bulletins.remove(bulletin);
                    }
                });
                bulletin.show();

                AndroidUtilities.runOnUIThread(bulletinHideCallback = () -> windowManager.removeView(bulletinContainer), Bulletin.DURATION_SHORT + 1000);
                return;
            }
            clicked = true;
            selectCallback.onPeerSelected(recyclerView, (SenderView) view, peerObj.peer);
        });
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        recyclerFrameLayout.addView(recyclerView);

        headerShadow = new View(context);
        shadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow);
        shadowDrawable.setAlpha(0x99);
        headerShadow.setBackground(shadowDrawable);
        headerShadow.setAlpha(0);
        recyclerFrameLayout.addView(headerShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 4));

        recyclerContainer.addView(recyclerFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        scrimPopupContainerLayout.addView(recyclerContainer);
    }

    @Override
    public void dismiss() {
        if (dismissed) {
            return;
        }
        if (bulletinContainer != null && bulletinContainer.getAlpha() == 1) {
            WindowManager windowManager = (WindowManager) bulletinContainer.getContext().getSystemService(Context.WINDOW_SERVICE);
            bulletinContainer.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    try {
                        windowManager.removeViewImmediate(bulletinContainer);
                    } catch (Exception e) {

                    }

                    if (bulletinHideCallback != null) {
                        AndroidUtilities.cancelRunOnUIThread(bulletinHideCallback);
                    }
                }
            });
        }
        dismissed = true;
        super.dismiss();
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, popupX = x, popupY = y);
    }

    public void startShowAnimation() {
        for (SpringAnimation springAnimation : springAnimations) {
            springAnimation.cancel();
        }
        springAnimations.clear();

        scrimPopupContainerLayout.setPivotX(AndroidUtilities.dp(8));
        scrimPopupContainerLayout.setPivotY(scrimPopupContainerLayout.getMeasuredHeight() - AndroidUtilities.dp(8));

        recyclerContainer.setPivotX(0);
        recyclerContainer.setPivotY(0);

        List<TLRPC.TL_sendAsPeer> peers = sendAsPeers.peers;
        TLRPC.Peer defPeer = chatFull.default_send_as != null ? chatFull.default_send_as : null;
        if (defPeer != null) {
            int itemHeight = AndroidUtilities.dp(14 + AVATAR_SIZE_DP);
            int totalRecyclerHeight = peers.size() * itemHeight;
            for (int i = 0; i < peers.size(); i++) {
                TLRPC.Peer p = peers.get(i).peer;
                if (p.channel_id != 0 && p.channel_id == defPeer.channel_id || p.user_id != 0 && p.user_id == defPeer.user_id ||
                        p.chat_id != 0 && p.chat_id == defPeer.chat_id) {
                    int off = 0;
                    if (i != peers.size() - 1 && recyclerView.getMeasuredHeight() < totalRecyclerHeight) {
                        off = recyclerView.getMeasuredHeight() % itemHeight;
                    }

                    layoutManager.scrollToPositionWithOffset(i, off + AndroidUtilities.dp(7) + (totalRecyclerHeight - (peers.size() - 2) * itemHeight));
                    if (recyclerView.computeVerticalScrollOffset() > 0) {
                        headerShadow.animate().cancel();
                        headerShadow.animate().alpha(1).setDuration(SHADOW_DURATION).start();
                    }
                    break;
                }
            }
        }

        scrimPopupContainerLayout.setScaleX(SCALE_START);
        scrimPopupContainerLayout.setScaleY(SCALE_START);
        recyclerContainer.setAlpha(SCALE_START);

        dimView.setAlpha(0);

        List<SpringAnimation> newSpringAnimations = Arrays.asList(
                new SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_X)
                        .setSpring(new SpringForce(1f)
                            .setStiffness(SPRING_STIFFNESS)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addUpdateListener((animation, value, velocity) -> recyclerContainer.setScaleX(1f / value)),
                new SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_Y)
                        .setSpring(new SpringForce(1f)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addUpdateListener((animation, value, velocity) -> recyclerContainer.setScaleY(1f / value)),
                new SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.ALPHA)
                        .setSpring(new SpringForce(1f)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                new SpringAnimation(recyclerContainer, DynamicAnimation.ALPHA)
                        .setSpring(new SpringForce(1f)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                new SpringAnimation(dimView, DynamicAnimation.ALPHA)
                        .setSpring(new SpringForce(1f)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
        );

        for (SpringAnimation animation : newSpringAnimations) {
            springAnimations.add(animation);
            animation.addEndListener((animation1, canceled, value, velocity) -> {
                if (!canceled) {
                    springAnimations.remove(animation);
                    animation1.cancel();
                }
            });
            animation.start();
        }
    }

    public void startDismissAnimation(SpringAnimation... animations) {
        for (SpringAnimation springAnimation : new ArrayList<>(springAnimations)) {
            springAnimation.cancel();
        }
        springAnimations.clear();

        scrimPopupContainerLayout.setPivotX(AndroidUtilities.dp(8));
        scrimPopupContainerLayout.setPivotY(scrimPopupContainerLayout.getMeasuredHeight() - AndroidUtilities.dp(8));
        recyclerContainer.setPivotX(0);
        recyclerContainer.setPivotY(0);

        scrimPopupContainerLayout.setScaleX(1);
        scrimPopupContainerLayout.setScaleY(1);
        recyclerContainer.setAlpha(1);
        dimView.setAlpha(1);

        List<SpringAnimation> newSpringAnimations = new ArrayList<>();
        newSpringAnimations.addAll(Arrays.asList(
                new SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_X)
                        .setSpring(new SpringForce(SCALE_START)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addUpdateListener((animation, value, velocity) -> recyclerContainer.setScaleX(1f / value)),
                new SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.SCALE_Y)
                        .setSpring(new SpringForce(SCALE_START)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addUpdateListener((animation, value, velocity) -> recyclerContainer.setScaleY(1f / value)),
                new SpringAnimation(scrimPopupContainerLayout, DynamicAnimation.ALPHA)
                        .setSpring(new SpringForce(0f)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                new SpringAnimation(recyclerContainer, DynamicAnimation.ALPHA)
                        .setSpring(new SpringForce(SCALE_START)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                new SpringAnimation(dimView, DynamicAnimation.ALPHA)
                        .setSpring(new SpringForce(0f)
                                .setStiffness(SPRING_STIFFNESS)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addEndListener((animation, canceled, value, velocity) -> {
                            if (dimView.getParent() != null) {
                                ((ViewGroup)dimView.getParent()).removeView(dimView);
                            }
                            dismiss();
                        })
        ));
        newSpringAnimations.addAll(Arrays.asList(animations));

        runningCustomSprings = animations.length > 0;
        newSpringAnimations.get(0).addEndListener((animation, canceled, value, velocity) -> runningCustomSprings = false);
        for (SpringAnimation springAnimation : newSpringAnimations) {
            springAnimations.add(springAnimation);
            springAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled) {
                    springAnimations.remove(springAnimation);
                    animation.cancel();
                }
            });
            springAnimation.start();
        }
    }

    public final static class SenderView extends LinearLayout {
        public final SimpleAvatarView avatar;
        public final TextView title;
        public final TextView subtitle;

        public SenderView(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);

            int padding = AndroidUtilities.dp(14);
            setPadding(padding, padding / 2, padding, padding / 2);

            avatar = new SimpleAvatarView(context);
            addView(avatar, LayoutHelper.createFrame(AVATAR_SIZE_DP, AVATAR_SIZE_DP));

            LinearLayout textRow = new LinearLayout(context);
            textRow.setOrientation(VERTICAL);
            addView(textRow, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 12, 0, 0, 0));

            title = new TextView(context);
            title.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTag(this.title);
            title.setMaxLines(1);
            textRow.addView(title);

            subtitle = new TextView(context);
            subtitle.setTextColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), 0x66));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setTag(this.subtitle);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            textRow.addView(subtitle);
        }
    }

    public interface OnSelectCallback {
        void onPeerSelected(RecyclerView recyclerView, SenderView senderView, TLRPC.Peer peer);
    }

    private class BackButtonFrameLayout extends FrameLayout {

        public BackButtonFrameLayout(@NonNull Context context) {
            super(context);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && isShowing()) {
                dismiss();
            }
            return super.dispatchKeyEvent(event);
        }
    }
}

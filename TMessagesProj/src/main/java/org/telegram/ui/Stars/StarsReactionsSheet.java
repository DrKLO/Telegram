package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.premiumText;
import static org.telegram.messenger.AndroidUtilities.rectTmp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class StarsReactionsSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount;

    private final LinearLayout layout;
    private final FrameLayout topLayout;
    private final LinearLayout toptopLayout;
    private final StarsSlider slider;
    private final FrameLayout dialogSelectorLayout;
    private final FrameLayout dialogSelectorInnerLayout;
    private final BackupImageView dialogImageView;
    private final ImageView dialogSelectorIconView;
//    private final Space beforeTitleSpace;
    private final TextView titleView;
//    private final StarsIntroActivity.StarsBalanceView balanceView;
    private final ImageView closeView;
    private final TextView statusView;
    private final ButtonWithCounterView buttonView;
    @Nullable
    private final View separatorView;
    @Nullable
    private final TopSendersView topSendersView;

    public long peer;
    public long lastSelectedPeer;

    private final View checkSeparatorView;
    private final LinearLayout checkLayout;
    private final CheckBox2 checkBox;
    private final TextView checkTextView;

    private final GLIconTextureView icon3dView;

    private final MessageObject messageObject;
    private final ArrayList<TLRPC.MessageReactor> reactors;

    private final BalanceCloud balanceCloud;

    public static class BalanceCloud extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

        private final int currentAccount;
        private final TextView textView1;
        private final LinkSpanDrawable.LinksTextView textView2;

        public BalanceCloud(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;

            setOrientation(VERTICAL);
            setPadding(dp(18), dp(9), dp(18), dp(9));
            setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_undo_background, resourcesProvider)));

            textView1 = new TextView(context);
            textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView1.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
            textView1.setGravity(Gravity.CENTER);
            addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER, 0, 0, 0, 0));

            textView2 = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView2.setTextColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            textView2.setLinkTextColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            textView2.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.Gift2MessageStarsInfoLink), () -> {
                new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show();
            }), true, dp(8f / 3f), dp(1)));
            textView2.setGravity(Gravity.CENTER);
            addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER, 0, 1, 0, 0));

            updateBalance(false);
        }

        private void updateBalance(boolean animated) {
            final StarsController c = StarsController.getInstance(currentAccount);
            final long stars = c.getBalance().amount;
            textView1.setText(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatString(R.string.Gift2MessageStarsInfo, LocaleController.formatNumber(stars, ',')), .60f));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateBalance(false);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botStarsUpdated);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botStarsUpdated);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starBalanceUpdated) {
                updateBalance(true);
            }
        }
    }

    @Override
    protected void appendOpenAnimator(boolean opening, ArrayList<Animator> animators) {
        animators.add(ObjectAnimator.ofFloat(balanceCloud, View.ALPHA, opening ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(balanceCloud, View.SCALE_X, opening ? 1.0f : 0.6f));
        animators.add(ObjectAnimator.ofFloat(balanceCloud, View.SCALE_Y, opening ? 1.0f : 0.6f));
    }

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (x >= balanceCloud.getX() && x <= balanceCloud.getX() + balanceCloud.getWidth() && y >= balanceCloud.getY() && y <= balanceCloud.getY() + balanceCloud.getHeight())
            return false;
        return super.isTouchOutside(x, y);
    }

    public StarsReactionsSheet(
        Context context,
        int currentAccount,
        long dialogId,
        ChatActivity chatActivity,
        MessageObject messageObject,
        ArrayList<TLRPC.MessageReactor> reactors,
        boolean sendEnabled,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, false, resourcesProvider);

        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;
        this.messageObject = messageObject;
        this.reactors = reactors;

        balanceCloud = new BalanceCloud(context, currentAccount, resourcesProvider);
        balanceCloud.setScaleX(0.6f);
        balanceCloud.setScaleY(0.6f);
        balanceCloud.setAlpha(0.0f);
        container.addView(balanceCloud, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));
        ScaleStateListAnimator.apply(balanceCloud);
        balanceCloud.setOnClickListener(v -> {
            new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show();
        });

        TLRPC.MessageReactor me = null;
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (reactors != null) {
            for (TLRPC.MessageReactor reactor : reactors) {
                long reactorDialogId = DialogObject.getPeerDialogId(reactor.peer_id);
                if (reactor.anonymous && reactor.my) {
                    reactorDialogId = selfId;
                }
                if (reactor.my || reactorDialogId == selfId) {
                    me = reactor;
                }
            }
        }
        final boolean withTopSenders = reactors != null && !reactors.isEmpty();
        peer = StarsController.getInstance(currentAccount).getPaidReactionsDialogId(messageObject);
        lastSelectedPeer = peer == UserObject.ANONYMOUS ? selfId : peer;

        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        topLayout = new FrameLayout(context);
        layout.addView(topLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        slider = new StarsSlider(context) {
            @Override
            public void onValueChanged(int value) {
                updateSenders(value);
                if (buttonView != null) {
                    buttonView.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.StarsReactionSend, LocaleController.formatNumber(value, ',')), starRef), true);
                }
            }
        };
        int[] steps_arr = new int[] { 1, 50, 100, /*250,*/ 500, 1_000, 2_000, 5_000, 7_500, 10_000 };
        final long max = MessagesController.getInstance(currentAccount).starsPaidReactionAmountMax;
        ArrayList<Integer> steps = new ArrayList<>();
        for (int i = 0; i < steps_arr.length; ++i) {
            if (steps_arr[i] > max) {
                steps.add((int) max);
                break;
            }
            steps.add(steps_arr[i]);
            if (steps_arr[i] == max) break;
        }
        steps_arr = new int[ steps.size() ];
        for (int i = 0; i < steps.size(); ++i) steps_arr[i] = steps.get(i);
        slider.setSteps(100, steps_arr);
        if (sendEnabled) {
            topLayout.addView(slider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        toptopLayout = new LinearLayout(context);
        toptopLayout.setOrientation(LinearLayout.HORIZONTAL);
        topLayout.addView(toptopLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
//
//        balanceView = new StarsIntroActivity.StarsBalanceView(context, currentAccount);
//        balanceView.setDialogId(selfId);

        dialogSelectorLayout = new FrameLayout(context);
        dialogSelectorInnerLayout = new FrameLayout(context);
        dialogSelectorInnerLayout.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider)));
        dialogImageView = new BackupImageView(context);
        dialogImageView.setRoundRadius(dp(14));
        dialogImageView.getImageReceiver().setCrossfadeWithOldImage(true);
        updatePeerDialog();
        dialogSelectorInnerLayout.addView(dialogImageView, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.FILL_VERTICAL));
        dialogSelectorIconView = new ImageView(context);
        dialogSelectorIconView.setScaleType(ImageView.ScaleType.CENTER);
        dialogSelectorIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
        dialogSelectorIconView.setImageResource(R.drawable.arrows_select);
        dialogSelectorInnerLayout.addView(dialogSelectorIconView, LayoutHelper.createFrame(18, 18, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        dialogSelectorLayout.addView(dialogSelectorInnerLayout, LayoutHelper.createFrame(52, 28, Gravity.CENTER));
        dialogSelectorLayout.setPadding(dp(8), dp(4), dp(8), 0);
        toptopLayout.addView(dialogSelectorLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, Gravity.LEFT | Gravity.FILL_VERTICAL, 6, 4, 6, 0));
        ScaleStateListAnimator.apply(dialogSelectorLayout);
        BotStarsController.getInstance(currentAccount).loadAdminedChannels();

        titleView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(ActionBar.getCurrentActionBarHeight(), MeasureSpec.EXACTLY));
            }
        };
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.StarsReactionTitle2));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setEllipsize(TextUtils.TruncateAt.END);
//        toptopLayout.addView(beforeTitleSpace = new Space(context), LayoutHelper.createLinear(0, 0, 1, Gravity.FILL));
        toptopLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL, 2, 0, 2, 0));
//        toptopLayout.addView(new Space(context), LayoutHelper.createLinear(0, 0, 1, Gravity.FILL));
        updateCanSwitchPeer(false);

        closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeView);
        closeView.setOnClickListener(v -> dismiss());

//        ScaleStateListAnimator.apply(balanceView);
//        balanceView.setOnClickListener(v -> {
//            dismiss();
//            chatActivity.presentFragment(new StarsIntroActivity() {
//                @Override
//                public void onFragmentDestroy() {
//                    super.onFragmentDestroy();
//                    if (chatActivity.isFullyVisible) {
//                        StarsReactionsSheet.this.show();
//                    }
//                }
//            });
//        });
//        toptopLayout.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.TOP | Gravity.RIGHT, 6, 0, 6, 0));
        toptopLayout.addView(closeView, LayoutHelper.createLinear(48, 48, 0, Gravity.TOP | Gravity.RIGHT, 0, 6, 6, 0));

        LinearLayout topLayoutTextLayout = new LinearLayout(context);
        topLayoutTextLayout.setOrientation(LinearLayout.VERTICAL);
        topLayout.addView(topLayoutTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, sendEnabled ? 135 + 44 : 45, 0, 15));

        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        statusView = new TextView(context);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setSingleLine(false);
        statusView.setMaxLines(3);
        statusView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(me != null ? LocaleController.formatPluralStringComma("StarsReactionTextSent", me.count) : LocaleController.formatString(R.string.StarsReactionText, chat == null ? "" : chat.title)), statusView.getPaint().getFontMetricsInt(), false));
        if (sendEnabled) {
            topLayoutTextLayout.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 40,  0, 40, 0));
        }

        if (withTopSenders) {
            separatorView = new View(context) {
                private final LinearGradient gradient = new LinearGradient(0, 0, 255, 0, new int[]{0xFFEEAC0D, 0xFFF9D316}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                private final Matrix gradientMatrix = new Matrix();
                private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Text text = new Text(getString(R.string.StarsReactionTopSenders), 14.16f, AndroidUtilities.bold());

                @Override
                public void dispatchDraw(Canvas canvas) {
                    gradientMatrix.reset();
                    gradientMatrix.postTranslate(dp(14), 0);
                    gradientMatrix.postScale((getWidth() - dp(14 * 2)) / 255f, 1f);
                    gradient.setLocalMatrix(gradientMatrix);
                    backgroundPaint.setShader(gradient);

                    final float textWidth = text.getCurrentWidth() + dp(15 + 15);

                    separatorPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                    canvas.drawRect(dp(24), getHeight() / 2f - 1, (getWidth() - textWidth) / 2f - dp(8), getHeight() / 2f, separatorPaint);
                    canvas.drawRect((getWidth() + textWidth) / 2f + dp(8), getHeight() / 2f - 1, getWidth() - dp(24), getHeight() / 2f, separatorPaint);

                    AndroidUtilities.rectTmp.set((getWidth() - textWidth) / 2f, 0, (getWidth() + textWidth) / 2f, getHeight());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, getHeight() / 2f, getHeight() / 2f, backgroundPaint);
                    text.draw(canvas, (getWidth() - text.getCurrentWidth()) / 2f, getHeight() / 2f, 0xFFFFFFFF, 1f);
                }
            };
            topLayoutTextLayout.addView(separatorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 30, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 20, 0, 0));

            topSendersView = new TopSendersView(context);
            topSendersView.setOnSenderClickListener(senderDialogId -> {
                if (senderDialogId >= 0) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", senderDialogId);
                    if (senderDialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        args.putBoolean("my_profile", true);
                    }
                    chatActivity.presentFragment(new ProfileActivity(args) {
                        @Override
                        public void onFragmentDestroy() {
                            super.onFragmentDestroy();
                            StarsReactionsSheet.this.show();
                        }
                    });
                    dismiss();
                } else {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", -senderDialogId);
                    chatActivity.presentFragment(new ChatActivity(args) {
                        @Override
                        public void onFragmentDestroy() {
                            super.onFragmentDestroy();
                            StarsReactionsSheet.this.show();
                        }
                    });
                }
                dismiss();
            });
            layout.addView(topSendersView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 110));

            checkSeparatorView = new View(context);
            checkSeparatorView.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            if (sendEnabled || me != null) {
                layout.addView(checkSeparatorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL, 24, 0, 24, 0));
            }
        } else {
            separatorView = null;
            topSendersView = null;
            checkSeparatorView = null;
        }

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(true);
        checkBox.setChecked(peer != UserObject.ANONYMOUS, false);
        if (topSendersView != null) {
            topSendersView.setMyPrivacy(peer);
        }
        checkBox.setDrawBackgroundAsArc(10);

        checkTextView = new TextView(context);
        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        checkTextView.setText(LocaleController.getString(R.string.StarsReactionShowMeInTopSenders));

        checkLayout = new LinearLayout(context);
        checkLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        checkLayout.addView(checkBox, LayoutHelper.createLinear(21, 21, Gravity.CENTER_VERTICAL, 0, 0, 9, 0));
        checkLayout.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        checkLayout.setOnClickListener(v -> {
            checkBox.setChecked(!checkBox.isChecked(), true);
            peer = checkBox.isChecked() ? lastSelectedPeer : UserObject.ANONYMOUS;
            updatePeerDialog();
            if (topSendersView != null) {
                topSendersView.setMyPrivacy(peer);
            }
        });
        ScaleStateListAnimator.apply(checkLayout, .05f, 1.2f);
        checkLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));

        if (sendEnabled || me != null) {
            layout.addView(checkLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, withTopSenders ? 10 : 4, 0, 10));
        }

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        if (sendEnabled) {
            layout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 0));
        }
        updateSenders(0);
        buttonView.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.StarsReactionSend, LocaleController.formatNumber(50, ',')), starRef), true);
        buttonView.setOnClickListener(v -> {
            if (messageObject == null || chatActivity == null || iconAnimator != null) {
                return;
            }
            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                AccountFrozenAlert.show(currentAccount);
                return;
            }

            final long totalStars = slider.getValue();
            final StarsController starsController = StarsController.getInstance(currentAccount);

            final Runnable send = () -> {
                StarsController.PendingPaidReactions pending = starsController.sendPaidReaction(messageObject, chatActivity, totalStars, false, true, peer);
                if (pending == null) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    sending = true;
                    animate3dIcon(pending::apply);
                    AndroidUtilities.runOnUIThread(this::dismiss, 240);
                });
            };

            if (starsController.balanceAvailable() && starsController.getBalance().amount < totalStars) {
                new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, totalStars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, chat == null ? "" : chat.title, send).show();
            } else {
                send.run();
            }
        });

        dialogSelectorLayout.setOnClickListener(v -> {
            final ArrayList<TLObject> chats = BotStarsController.getInstance(currentAccount).getAdminedChannels();
            chats.add(0, UserConfig.getInstance(currentAccount).getCurrentUser());

            ItemOptions i = ItemOptions.makeOptions(containerView, resourcesProvider, dialogSelectorInnerLayout);
            for (TLObject obj : chats) {
                long did;
                if (obj instanceof TLRPC.User) {
                    did = ((TLRPC.User) obj).id;
                } else if (obj instanceof TLRPC.Chat) {
                    TLRPC.Chat lchat = (TLRPC.Chat) obj;
                    if (!ChatObject.isChannelAndNotMegaGroup(lchat))
                        continue;
                    did = -lchat.id;
                } else continue;
                if (did == dialogId) continue;
                i.addChat(obj, did == peer || peer == 0 && did == UserConfig.getInstance(currentAccount).getClientUserId(), () -> {
                    peer = lastSelectedPeer = did;
                    updatePeerDialog();
                    checkBox.setChecked(true, true);
                    if (topSendersView != null) {
                        topSendersView.setMyPrivacy(peer);
                    }
                });
            }
            i
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .setDimAlpha(0)
                .setGravity(Gravity.RIGHT)
                .show();
        });

        LinkSpanDrawable.LinksTextView termsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        termsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        termsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        termsView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsReactionTerms), () -> {
            Browser.openUrl(context, getString(R.string.StarsReactionTermsLink));
        }));
        termsView.setGravity(Gravity.CENTER);
        termsView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        if (sendEnabled) {
            layout.addView(termsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 14, 14, 14, 12));
        }

        setCustomView(layout);

        icon3dView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_GOLDEN_STAR) {
            @Override
            protected void startIdleAnimation() {}
        };
        icon3dView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        icon3dView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        icon3dView.mRenderer.updateColors();
        icon3dView.mRenderer.white = 1f;
        icon3dView.setVisibility(View.INVISIBLE);
        icon3dView.setPaused(true);
        container.addView(icon3dView, LayoutHelper.createFrame(150, 150));
        slider.setValue(50);

        if (reactors != null) {
            long top = 0;
            for (int i = 0; i < reactors.size(); ++i) {
                final TLRPC.MessageReactor reactor = reactors.get(i);
                long count = reactor.count;
                if (count > top) top = count;
            }
            if (me != null) {
                top -= me.count;
            }
            if (top > 0) {
                slider.setStarsTop(1 + top);
            }
        }
    }

    private void updatePeerDialog() {
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setScaleSize(.42f);
        if (peer == UserObject.ANONYMOUS) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
            avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundGray), Theme.getColor(Theme.key_avatar_backgroundGray));
            dialogImageView.setForUserOrChat(null, avatarDrawable);
        } else if (peer >= 0) {
            TLRPC.User _user = MessagesController.getInstance(currentAccount).getUser(peer);
            avatarDrawable.setInfo(_user);
            dialogImageView.setForUserOrChat(_user, avatarDrawable);
        } else {
            TLRPC.Chat _chat = MessagesController.getInstance(currentAccount).getChat(-peer);
            avatarDrawable.setInfo(_chat);
            dialogImageView.setForUserOrChat(_chat, avatarDrawable);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.adminedChannelsLoaded);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.adminedChannelsLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.adminedChannelsLoaded) {
            updateCanSwitchPeer(true);
        }
    }

    private boolean canSwitchPeer() {
        final ArrayList<TLObject> objects = BotStarsController.getInstance(currentAccount).getAdminedChannels();
        for (Object o : objects) {
            if (o instanceof TLRPC.Chat && ChatObject.isChannelAndNotMegaGroup((TLRPC.Chat) o)) {
                return true;
            }
        }
        return false;
    }

    private void updateCanSwitchPeer(boolean animated) {
        if ((dialogSelectorLayout.getVisibility() == View.VISIBLE) != canSwitchPeer()) {
//            beforeTitleSpace.setVisibility(canSwitchPeer() ? View.VISIBLE : View.GONE);
            dialogSelectorLayout.setVisibility(canSwitchPeer() ? View.VISIBLE : View.GONE);
            if (animated) {
                if (canSwitchPeer()) {
                    dialogSelectorLayout.setScaleX(0.4f);
                    dialogSelectorLayout.setScaleY(0.4f);
                    dialogSelectorLayout.setAlpha(0.0f);
                    dialogSelectorLayout.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).start();
                }
                final ChangeBounds transition = new ChangeBounds();
                transition.setDuration(200);
                TransitionManager.beginDelayedTransition(toptopLayout, transition);
            }
        }
    }

    private final ColoredImageSpan[] starRef = new ColoredImageSpan[1];
    public void updateSenders(long my_stars) {
        if (topSendersView != null) {
            ArrayList<SenderData> array = new ArrayList<>();
            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            long existingStars = 0;
            if (reactors != null) {
                for (int i = 0; i < reactors.size(); ++i) {
                    final TLRPC.MessageReactor reactor = reactors.get(i);
                    long dialogId = DialogObject.getPeerDialogId(reactor.peer_id);
                    if (reactor.anonymous) {
                        if (reactor.my) {
                            dialogId = selfId;
                        } else {
                            dialogId = -i-1;
                        }
                    }
                    if (reactor.my || dialogId == selfId) {
                        existingStars = reactor.count;
                        continue;
                    }
                    array.add(SenderData.of(reactor.anonymous, false, dialogId, reactor.count));
                }
            }
            if (existingStars + my_stars > 0) {
                array.add(SenderData.of(peer == UserObject.ANONYMOUS, true, selfId, existingStars + my_stars));
            }
            Collections.sort(array, (a1, a2) -> (int) (a2.stars - a1.stars));
            topSendersView.setSenders(new ArrayList<>(array.subList(0, Math.min(3, array.size()))));
        }
    }

    private boolean sending;
    private boolean checkedVisiblity = false;
    private void checkVisibility() {
        if (checkedVisiblity) return;
        checkedVisiblity = true;
        if (messageObject == null) return;
        final Long currentPeer = messageObject.getMyPaidReactionPeer();
        if (currentPeer == null || currentPeer != peer) {
            messageObject.setMyPaidReactionDialogId(peer);

            final StarsController.MessageId key = StarsController.MessageId.from(messageObject);
            TLRPC.TL_messages_togglePaidReactionPrivacy req = new TLRPC.TL_messages_togglePaidReactionPrivacy();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(key.did);
            req.msg_id = key.mid;
            if (peer == 0) {
                req.privacy = new TL_stars.paidReactionPrivacyDefault();
            } else if (peer == UserObject.ANONYMOUS) {
                req.privacy = new TL_stars.paidReactionPrivacyAnonymous();
            } else {
                req.privacy = new TL_stars.paidReactionPrivacyPeer();
                req.privacy.peer = MessagesController.getInstance(currentAccount).getInputPeer(peer);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starReactionAnonymousUpdate, key.did, key.mid, peer);

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_boolTrue) {
                    MessagesStorage.getInstance(currentAccount).putMessages(new ArrayList<>(Arrays.asList(messageObject.messageOwner)), true, true, true, 0, 0, 0);
                }
            });
        }
    }

    @Override
    public void dismiss() {
        if (!sending) checkVisibility();
        super.dismiss();
    }

    private ChatActivity chatActivity;
    private int messageId;
    private View messageCell;

    public void setMessageCell(ChatActivity chatActivity, int id, View cell) {
        this.chatActivity = chatActivity;
        this.messageId = id;
        this.messageCell = cell;
    }

    public void setValue(int value) {
        slider.setValue(value);
        updateSenders(value);
        if (buttonView != null) {
            buttonView.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.StarsReactionSend, LocaleController.formatNumber(value, ',')), starRef), true);
        }
    }

    private ValueAnimator iconAnimator;
    private void animate3dIcon(Runnable pushed) {
        if (messageObject == null || chatActivity.fragmentView == null || !chatActivity.fragmentView.isAttachedToWindow()) return;
        View _cell = messageCell;
        ReactionsLayoutInBubble.ReactionButton _button = null;
        ReactionsLayoutInBubble reactionsLayoutInBubble;
        if (_cell instanceof ChatMessageCell) {
            reactionsLayoutInBubble = ((ChatMessageCell) _cell).reactionsLayoutInBubble;
            _button = reactionsLayoutInBubble.getReactionButton(ReactionsLayoutInBubble.VisibleReaction.asStar());
        } else if (_cell instanceof ChatActionCell) {
            reactionsLayoutInBubble = ((ChatActionCell) _cell).reactionsLayoutInBubble;
            _button = reactionsLayoutInBubble.getReactionButton(ReactionsLayoutInBubble.VisibleReaction.asStar());
        } else return;
        if (_button == null) {
            MessageObject.GroupedMessages group = chatActivity.getValidGroupedMessage(messageObject);
            if (group != null && !group.posArray.isEmpty()) {
                MessageObject msg = null;
                for (MessageObject m : group.messages) {
                    MessageObject.GroupedMessagePosition pos = group.getPosition(m);
                    if (pos != null && (pos.flags & MessageObject.POSITION_FLAG_LEFT) != 0 && (pos.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                        msg = m;
                        break;
                    }
                }
                if (msg != null) {
                    _cell = chatActivity.findMessageCell(msg.getId(), false);
                }
            }
            if (_cell == null) return;
            if (_cell instanceof ChatMessageCell) {
                reactionsLayoutInBubble = ((ChatMessageCell) _cell).reactionsLayoutInBubble;
                _button = reactionsLayoutInBubble.getReactionButton(ReactionsLayoutInBubble.VisibleReaction.asStar());
            }
        }
        if (_button == null) {
            return;
        }
        final View cell = _cell;
        final ReactionsLayoutInBubble reactionsLayout = reactionsLayoutInBubble;
        final ReactionsLayoutInBubble.ReactionButton button = _button;

        final int[] loc = new int[2];

        final RectF from = new RectF();
        slider.getLocationInWindow(loc);
        from.set(slider.counterImage.getBounds());
        from.inset(-dp(3.5f), -dp(3.5f));
        from.offset(loc[0], loc[1]);
        icon3dView.whenReady(() -> {
            slider.drawCounterImage = false;
            slider.invalidate();
        });
        button.drawImage = false;
        cell.invalidate();

        final RectF to = new RectF();
        final Runnable updateTo = () -> {
            cell.getLocationInWindow(loc);
            to.set(
            loc[0] + reactionsLayout.x + button.x + dp(4),
            loc[1] + reactionsLayout.y + button.y + (button.height - dp(22)) / 2f,
            loc[0] + reactionsLayout.x + button.x + dp(4 + 22),
            loc[1] + reactionsLayout.y + button.y + (button.height + dp(22)) / 2f
            );
        };
        updateTo.run();

        icon3dView.setPaused(false);
        icon3dView.setVisibility(View.VISIBLE);

        final RectF rect = new RectF();
        rect.set(from);
        icon3dView.setTranslationX(rect.centerX() - dp(150) / 2f);
        icon3dView.setTranslationY(rect.centerY() - dp(150) / 2f);
        icon3dView.setScaleX(rect.width() / dp(150));
        icon3dView.setScaleY(rect.height() / dp(150));

        if (iconAnimator != null) {
            iconAnimator.cancel();
        }
        final boolean[] doneRipple = new boolean[1];
        iconAnimator = ValueAnimator.ofFloat(0, 1);
        iconAnimator.addUpdateListener(anm -> {
            float t = (float) anm.getAnimatedValue();
            updateTo.run();
            AndroidUtilities.lerp(from, to, t, rect);
            icon3dView.setTranslationX(rect.centerX() - dp(150) / 2f);
            icon3dView.setTranslationY(rect.centerY() - dp(150) / 2f);
            float s = Math.max(rect.width() / dp(150), rect.height() / dp(150));
            s = lerp(s, 1f, (float) Math.sin(t * Math.PI));
            icon3dView.setScaleX(s);
            icon3dView.setScaleY(s);
            icon3dView.mRenderer.angleX = 360 * t;
            icon3dView.mRenderer.white = Math.max(0, 1 - 4f * t);

            if (!doneRipple[0] && t > .95f) {
                doneRipple[0] = true;
                LaunchActivity.makeRipple(to.centerX(), to.centerY(), 1.5f);
                try {
                    container.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {}
                if (pushed != null) {
                    pushed.run();
                }
            }
        });
        iconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                icon3dView.setVisibility(View.INVISIBLE);
                icon3dView.setPaused(true);
                button.drawImage = true;
                if (cell != null) {
                    cell.invalidate();
                }

                StarsReactionsSheet.super.dismissInternal();

                if (!doneRipple[0]) {
                    doneRipple[0] = true;
                    LaunchActivity.makeRipple(to.centerX(), to.centerY(), 1.5f);
                    try {
                        container.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                    if (pushed != null) {
                        pushed.run();
                    }
                }
                if (LaunchActivity.instance != null && LaunchActivity.instance.getFireworksOverlay() != null) {
                    LaunchActivity.instance.getFireworksOverlay().start(true);
                }
            }
        });
        iconAnimator.setDuration(800);
        iconAnimator.setInterpolator(new Interpolator() {
            @Override
            public float getInterpolation(float x) {
                return (float) Math.pow(x, 2);
            }
        });
        iconAnimator.start();
    }

    @Override
    public void dismissInternal() {
        if (iconAnimator != null && iconAnimator.isRunning()) {
            return;
        }
        super.dismissInternal();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        if (slider.tracking) return false;
        return super.canDismissWithSwipe();
    }

    public static class StarsSlider extends View {

        private final Paint sliderInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sliderCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Particles sliderParticles = new Particles(Particles.TYPE_RIGHT, 300);
        private final Particles textParticles = new Particles(Particles.TYPE_RADIAL_INSIDE, 30);

        private final LinearGradient gradient = new LinearGradient(0, 0, 255, 0, new int[] {0xFFEEAC0D, 0xFFF9D316}, new float[] {0, 1}, Shader.TileMode.CLAMP);
        private final Matrix gradientMatrix = new Matrix();

        public boolean drawCounterImage = true;
        private final Drawable counterImage;
        private final AnimatedTextView.AnimatedTextDrawable counterText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);

        private final ColoredImageSpan[] starRef = new ColoredImageSpan[1];

        private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Text topText = new Text(getString(R.string.StarsReactionTop), 14, AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
        private final AnimatedFloat overTop = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat overTopText = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public StarsSlider(Context context) {
            super(context);

            counterImage = context.getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
            counterImage.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));

            counterText.setTextColor(0xFFFFFFFF);
            counterText.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            counterText.setTextSize(AndroidUtilities.dp(21));
            counterText.setCallback(this);
            counterText.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counterText.setGravity(Gravity.CENTER);

            topPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
            topPaint.setStyle(Paint.Style.STROKE);
            topPaint.setStrokeWidth(dp(1));
        }

        private long currentTop = -1;

        public void setStarsTop(long top) {
            currentTop = top;
            invalidate();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == counterText || super.verifyDrawable(who);
        }

        private final RectF sliderInnerRect = new RectF();
        private final RectF sliderRect = new RectF();
        private final RectF sliderCircleRect = new RectF();
        private final RectF arc = new RectF();

        private final Path sliderInnerPath = new Path();
        private final Path sliderPath = new Path();

        private final RectF textRect = new RectF();
        private final Path textPath = new Path();

        public float progress = 0;
        public float aprogress;
        public int steps;
        public int[] stops;

        public void setSteps(int steps, int... stops) {
            this.steps = steps;
            this.stops = stops;
        }

        public void setValue(int value) {
            setValue(value, false);
        }
        public void setValue(int value, boolean byScroll) {
            this.progress = getProgress(value);
            if (!byScroll) {
                this.aprogress = this.progress;
            }
            updateText(true);
        }

        public int getValue() {
            return getValue(progress);
        }

        public float getProgress() {
            return progress;
        }

        public int getValue(float progress) {
            if (progress <= 0f) return stops[0];
            if (progress >= 1f) return stops[stops.length - 1];
            float scaledProgress = progress * (stops.length - 1);
            int index = (int) scaledProgress;
            float localProgress = scaledProgress - index;
            return Math.round(stops[index] + localProgress * (stops[index + 1] - stops[index]));
        }

        public float getProgress(int value) {
            for (int i = 1; i < stops.length; ++i) {
                if (value <= stops[i]) {
                    float local = (float) (value - stops[i - 1]) / (stops[i] - stops[i - 1]);
                    return (i - 1 + local) / (stops.length - 1);
                }
            }
            return 1f;
        }

        public void updateText(boolean animated) {
            counterText.cancelAnimation();
            counterText.setText(StarsIntroActivity.replaceStars(LocaleController.formatNumber(getValue(), ','), starRef), animated);
        }

        protected void onValueChanged(int value) {}

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(220));
            final int w = getMeasuredWidth();
            final int h = getMeasuredHeight();

            final int pad = dp(14);
            final int top = dp(135);

            sliderInnerRect.set(pad, top, w - pad, top + dp(24));

            sliderInnerPaint.setColor(0x26EFAD0D);
            sliderPaint.setColor(0xFFEFAD0D);
            sliderCirclePaint.setColor(0xFFFFFFFF);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            gradientMatrix.reset();
            gradientMatrix.postTranslate(sliderInnerRect.left, 0);
            gradientMatrix.postScale(sliderInnerRect.width() / 255f, 1f);
            gradient.setLocalMatrix(gradientMatrix);
            sliderPaint.setShader(gradient);

            sliderInnerPath.rewind();
            sliderInnerPath.addRoundRect(sliderInnerRect, dp(12), dp(12), Path.Direction.CW);
            canvas.drawPath(sliderInnerPath, sliderInnerPaint);

            sliderRect.set(sliderInnerRect);
            final float roundedValue = getProgress(getValue());
            sliderRect.right = lerp(sliderRect.left + dp(24), sliderRect.right, roundedValue);

            sliderPath.rewind();
            sliderPath.addRoundRect(sliderRect, dp(12), dp(12), Path.Direction.CW);

            sliderParticles.setBounds(sliderInnerRect);
            sliderParticles.setSpeed(1f + progress * 15f);
            sliderParticles.setVisible(.15f + .85f * progress);
            sliderParticles.process();
            canvas.save();
            canvas.clipPath(sliderInnerPath);
            sliderParticles.draw(canvas, 0xFFF5B90E);
            if (currentTop != -1 && getProgress((int) currentTop) < 1f && getProgress((int) currentTop) > 0) {
                final float topX = sliderInnerRect.left + dp(12) + (sliderInnerRect.width() - dp(24)) * Utilities.clamp01(getProgress((int) currentTop));
                final float isOverTop = overTop.set(Math.abs(sliderRect.right - dp(10) - topX) < dp(14));
                final float textPad = lerp(dp(9), dp(16), overTopText.set(Math.abs(sliderRect.right - dp(10) - topX) < dp(12)));
                final float topTextX = topX + topText.getCurrentWidth() + 2 * dp(16) > sliderInnerRect.right ? topX - textPad - topText.getCurrentWidth() : topX + textPad;
                topPaint.setStrokeWidth(dp(1));
                topPaint.setColor(Theme.multAlpha(0xFFF5B90E, .6f));
                canvas.drawLine(topX, lerp(sliderInnerRect.top, sliderInnerRect.centerY(), isOverTop), topX, lerp(sliderInnerRect.bottom, sliderInnerRect.centerY(), isOverTop), topPaint);
                topText.draw(canvas, topTextX, sliderInnerRect.centerY(), 0xFFF5B90E, .6f);
            }
            canvas.drawPath(sliderPath, sliderPaint);
            canvas.clipPath(sliderPath);
            sliderParticles.draw(canvas, Color.WHITE);
            if (currentTop != -1 && getProgress((int) currentTop) < 1f && getProgress((int) currentTop) > 0) {
                final float topX = sliderInnerRect.left + dp(12) + (sliderInnerRect.width() - dp(24)) * Utilities.clamp01(getProgress((int) currentTop));
                final float isOverTop = overTop.set(Math.abs(sliderRect.right - dp(10) - topX) < dp(14));
                final float textPad = lerp(dp(9), dp(16), overTopText.set(Math.abs(sliderRect.right - dp(10) - topX) < dp(12)));
                final float topTextX = topX + topText.getCurrentWidth() + 2 * dp(16) > sliderInnerRect.right ? topX - textPad - topText.getCurrentWidth() : topX + textPad;
                topPaint.setStrokeWidth(dp(1));
                topPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogBackground), .4f));
                canvas.drawLine(topX, lerp(sliderInnerRect.top, sliderInnerRect.centerY(), isOverTop), topX, lerp(sliderInnerRect.bottom, sliderInnerRect.centerY(), isOverTop), topPaint);
                topText.draw(canvas, topTextX, sliderInnerRect.centerY(), Color.WHITE, .75f);
            }
            canvas.restore();
            invalidate();

            sliderCircleRect.set(
                    sliderRect.right - dp(16) - dp(16 - 12),
                    (sliderRect.centerY() - dp(16) / 2f),
                    sliderRect.right - dp(16 - 12),
                    (sliderRect.centerY() + dp(16) / 2f)
            );
            canvas.drawRoundRect(sliderCircleRect, dp(12), dp(12), sliderCirclePaint);

            final float slide = dp(9) / sliderInnerRect.width();
            final float pointerX = lerp(
                lerp(sliderCircleRect.left, sliderCircleRect.right, roundedValue),
                lerp(sliderCircleRect.left + dp(9), sliderCircleRect.right - dp(9), roundedValue),
                Math.min(Utilities.clamp01(roundedValue / slide), Utilities.clamp01((1f - roundedValue) / slide))
            ); // slide < dp(12) ? sliderInnerRect.left + dp(12) : slide > (sliderInnerRect.width() - dp(12)) ? sliderInnerRect.right - dp(12) : sliderCircleRect.centerX();
            final float textWidth = counterText.getCurrentWidth() + dp(24 + 26);
            final float textHeight = dp(44);
            final float left = Utilities.clamp(pointerX - textWidth / 2f, sliderInnerRect.right - textWidth - dp(4), sliderInnerRect.left + dp(4));
            textRect.set(left, sliderInnerRect.top - dp(21) - textHeight, left + textWidth, sliderInnerRect.top - dp(21));

            float d = textRect.height(), r = d / 2f;

            final float px = Utilities.clamp(pointerX, textRect.right, textRect.left);
            final float lpx = Utilities.clamp(px - dp(9), textRect.right, textRect.left);
            final float rpx = Utilities.clamp(px + dp(9), textRect.right, textRect.left);


            final float rotate = Utilities.clamp(progress - aprogress, 1, -1) * 60;
            final float rotateCx = px, rotateCy = textRect.bottom + dp(8);

            textPath.rewind();
            arc.set(textRect.left, textRect.top, textRect.left + d, textRect.top + d);
            textPath.arcTo(arc, -180, 90);
            arc.set(textRect.right - d, textRect.top, textRect.right, textRect.top + d);
            textPath.arcTo(arc, -90, 90);
            arc.set(textRect.right - d, textRect.bottom - d, textRect.right, textRect.bottom);
            float rr = Utilities.clamp01((rpx - arc.centerX()) / r);
            textPath.arcTo(arc, 0, (float) Utilities.clamp(.85f * Math.acos(rr) / Math.PI * 180, 90, 0));
            if (lpx < textRect.right - d * .7f) {
                textPath.lineTo(rpx, textRect.bottom);
                textPath.lineTo(px + 2, textRect.bottom + dp(8));
            }
            textPath.lineTo(px, textRect.bottom + dp(8) + 1);
            if (rpx > textRect.left + d * .7f) {
                textPath.lineTo(px - 2, textRect.bottom + dp(8));
                textPath.lineTo(lpx, textRect.bottom);
            }
            arc.set(textRect.left, textRect.bottom - d, textRect.left + d, textRect.bottom);
            float lr = Utilities.clamp01((lpx - arc.left) / r);
            float a = 90 + (float) Utilities.clamp(.85f * Math.acos(lr) / Math.PI * 180, 90, 0);
            textPath.arcTo(arc, a, 180 - a);
            textPath.lineTo(textRect.left, textRect.bottom);

            textPath.close();

            AndroidUtilities.rectTmp.set(textRect);
            AndroidUtilities.rectTmp.inset(-dp(12), -dp(12));
            textParticles.setBounds(AndroidUtilities.rectTmp);
            textParticles.setSpeed(1f + progress * 15f);
            textParticles.process();
            canvas.save();
//            canvas.translate(textRect.centerX(), textRect.centerY());
            textParticles.draw(canvas, 0xFFF5B90E);
            canvas.restore();

            canvas.save();
            canvas.rotate(rotate, rotateCx, rotateCy);
            if (Math.abs(progress - aprogress) > .001f) {
                aprogress = AndroidUtilities.lerp(aprogress, progress, .1f);
                invalidate();
            }

            textBackgroundPaint.setShader(gradient);
            canvas.drawPath(textPath, textBackgroundPaint);

            canvas.save();
            canvas.clipPath(textPath);
            canvas.rotate(-rotate, rotateCx, rotateCy);
//            canvas.translate(textRect.centerX(), textRect.centerY());
            textParticles.draw(canvas, Color.WHITE);
            canvas.restore();

            counterImage.setBounds((int) (textRect.left + dp(13)), (int) (textRect.centerY() - dp(10)), (int) (textRect.left + dp(13 + 20)), (int) (textRect.centerY() + dp(10)));
            if (drawCounterImage) {
                counterImage.draw(canvas);
            }
            counterText.setBounds(textRect.left + dp(24), textRect.top, textRect.right, textRect.bottom);
            counterText.draw(canvas);

            canvas.restore();

        }

        private float lastX, lastY;
        private long pressTime;
        private int pointerId;
        private boolean tracking;

        public boolean isTracking() {
            return tracking;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
                pointerId = event.getPointerId(0);
                pressTime = System.currentTimeMillis();
                tracking = false;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && event.getPointerId(0) == pointerId) {
                final float dx = event.getX() - lastX;
                final float dy = event.getY() - lastY;
                if (!tracking && Math.abs(dx) > Math.abs(1.5f * dy) && Math.abs(dx) > AndroidUtilities.touchSlop) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    tracking = true;
                    if (progressAnimator != null) {
                        progressAnimator.cancel();
                    }
                }
                if (tracking) {
                    final int pastValue = getValue();
                    progress = Utilities.clamp01(progress + dx / (1f * getWidth()));
                    if (getValue() != pastValue) {
                        onValueChanged(getValue());
                        updateText(true);
                    }
                    lastX = event.getX();
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!tracking && event.getPointerId(0) == pointerId && MathUtils.distance(lastX, lastY, event.getX(), event.getY()) < AndroidUtilities.touchSlop && System.currentTimeMillis() - pressTime <= ViewConfiguration.getTapTimeout() * 1.5f) {
                    // tap
                    float newProgress = Utilities.clamp01((event.getX() - sliderInnerRect.left) / (float) sliderInnerRect.width());
                    if (currentTop > 0 && Math.abs(getProgress((int) currentTop) - newProgress) < 0.035f) {
                        newProgress = Utilities.clamp01(getProgress((int) currentTop));
                    }
                    animateProgressTo(newProgress);
                }
                tracking = false;
            }
            return true;
        }

        private ValueAnimator progressAnimator;
        private void animateProgressTo(float toProgress) {
            if (progressAnimator != null) {
                progressAnimator.cancel();
            }
            progressAnimator = ValueAnimator.ofFloat(progress, toProgress);
            progressAnimator.addUpdateListener(anm -> {
                progress = (float) anm.getAnimatedValue();
                invalidate();
            });
            progressAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    final int pastValue = getValue();
                    progress = toProgress;
                    if (getValue() != pastValue) {
                        onValueChanged(getValue());
                    }
                    invalidate();
                }
            });
            progressAnimator.setDuration(320);
            progressAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            progressAnimator.start();

            final int pastValue = getValue();
            if (getValue(toProgress) != pastValue) {
                onValueChanged(getValue(toProgress));
            }

            counterText.cancelAnimation();
            counterText.setText(StarsIntroActivity.replaceStars(LocaleController.formatNumber(getValue(toProgress), ','), starRef), true);
        }
    }

    public static class Particles {

        public static final int TYPE_RIGHT = 0;
        public static final int TYPE_RADIAL = 1;
        public static final int TYPE_RADIAL_INSIDE = 2;

        public final int type;
        public final ArrayList<Particle> particles;
        public final RectF bounds = new RectF();

        public final Bitmap b;
        private int bPaintColor;
        public final Paint bPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        public final Rect rect = new Rect();

        private float speed = 1f;
        private int visibleCount;

        private boolean firstDraw = true;

        public Particles(int type, int n) {
            this.type = type;
            this.visibleCount = n;
            particles = new ArrayList<>(n);
            for (int i = 0; i < n; ++i) {
                particles.add(new Particle());
            }

            final int size = dp(10);
            final float k = .85f;
            b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Path path = new Path();
            int sizeHalf = size >> 1;
            int mid = (int) (sizeHalf * k);
            path.moveTo(0, sizeHalf);
            path.lineTo(mid, mid);
            path.lineTo(sizeHalf, 0);
            path.lineTo(size - mid, mid);
            path.lineTo(size, sizeHalf);
            path.lineTo(size - mid, size - mid);
            path.lineTo(sizeHalf, size);
            path.lineTo(mid, size - mid);
            path.lineTo(0, sizeHalf);
            path.close();
            Canvas canvas = new Canvas(b);
            Paint paint = new Paint();
            paint.setColor(Theme.multAlpha(Color.WHITE, .75f));
            canvas.drawPath(path, paint);
        }

        public void setVisible(float x) {
            this.visibleCount = (int) (particles.size() * x);
        }

        public void setBounds(RectF bounds) {
            this.bounds.set(bounds);
            removeParticlesOutside();
        }

        public void setBounds(Rect bounds) {
            this.bounds.set(bounds);
            removeParticlesOutside();
        }

        public void setBounds(int l, int t, int r, int b) {
            this.bounds.set(l, t, r, b);
            removeParticlesOutside();
        }

        public void removeParticlesOutside() {
            if (type == TYPE_RADIAL_INSIDE) {
                final long now = System.currentTimeMillis();
                for (int i = 0; i < particles.size(); ++i) {
                    final Particle p = particles.get(i);
                    if (!bounds.contains((int) p.x, (int) p.y)) gen(p, now, firstDraw);
                }
            }
        }

        public void setSpeed(float speed) {
            this.speed = speed;
        }

        private long lastTime;
        public void process() {
            final long now = System.currentTimeMillis();
            final float deltaTime = Math.min(lastTime - now, 16) / 1000f * speed;
            for (int i = 0; i < Math.min(visibleCount, particles.size()); ++i) {
                final Particle p = particles.get(i);
                float lifetime = p.lifetime <= 0 ? 2f : (now - p.start) / (float) p.lifetime;
                if (lifetime > 1f) {
                    gen(p, now, firstDraw);
                    lifetime = 0f;
                }
                p.x += p.vx * deltaTime;
                p.y += p.vy * deltaTime;
                p.la = 4f * lifetime - 4f * lifetime * lifetime;
            }
            lastTime = now;
        }

        public void draw(Canvas canvas, int color) {
            if (bPaintColor != color) {
                bPaint.setColorFilter(new PorterDuffColorFilter(bPaintColor = color, PorterDuff.Mode.SRC_IN));
            }
            for (int i = 0; i < Math.min(visibleCount, particles.size()); ++i) {
                final Particle p = particles.get(i);
                p.draw(canvas, color, p.la);
            }
            firstDraw = false;
        }

        public void gen(Particle p, final long now, boolean prefire) {
            p.start = now;
            p.lifetime = lerp(500, 2500, Utilities.fastRandom.nextFloat());
            if (prefire) {
                p.start -= (long) (p.lifetime * Utilities.clamp01(Utilities.fastRandom.nextFloat()));
            }
            p.x = lerp(bounds.left, bounds.right, Utilities.fastRandom.nextFloat());
            p.y = lerp(bounds.top, bounds.bottom, Utilities.fastRandom.nextFloat());
            if (type == TYPE_RIGHT) {
                p.vx = dp(lerp(-7f, -18f, Utilities.fastRandom.nextFloat()));
                p.vy = dp(lerp(-2f, 2f, Utilities.fastRandom.nextFloat()));
            } else {
                p.vx = bounds.centerX() - p.x;
                p.vy = bounds.centerY() - p.y;
                final float d = dp(lerp(1f, 4f, Utilities.fastRandom.nextFloat())) / (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
                p.vx *= d;
                p.vy *= d;
            }
            p.a = lerp(.4f, 1f, Utilities.fastRandom.nextFloat());
            p.s = .7f * lerp(.8f, 1.2f, Utilities.fastRandom.nextFloat());
        }

        public class Particle {
            public float x, y;
            public float vx, vy;
            public float s;
            public long start, lifetime;
            public float la, a;

            public void draw(Canvas canvas, int color, float alpha) {
                bPaint.setAlpha((int) (0xFF * alpha));
                rect.set(
                        (int) (x - b.getWidth() / 2f * a * s * alpha),
                        (int) (y - b.getHeight() / 2f * a * s * alpha),
                        (int) (x + b.getWidth() / 2f * a * s * alpha),
                        (int) (y + b.getHeight() / 2f * a * s * alpha)
                );
                canvas.drawBitmap(b, null, rect, bPaint);
            }
        }
    }

    public static class SenderData {
        public boolean anonymous;
        public boolean my;
        public long did;
        public long stars;
        public static SenderData of(boolean anonymous, boolean my, long did, long stars) {
            SenderData d = new SenderData();
            d.anonymous = anonymous;
            d.my = my;
            d.did = did;
            d.stars = stars;
            return d;
        }
    }

    public class TopSendersView extends View {

        public final ArrayList<Sender> senders = new ArrayList<>();
        public final ArrayList<Sender> oldSenders = new ArrayList<>();

        public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Paint starsBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public final AnimatedFloat animatedCount = new AnimatedFloat(TopSendersView.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public float count;

        public TopSendersView(Context context) {
            super(context);

            backgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            backgroundPaint.setStrokeWidth(dp(3));
            backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
            starsBackgroundPaint.setColor(0xFFF0B302);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (int i = 0; i < this.senders.size(); ++i) {
                Sender sender = this.senders.get(i);
                sender.imageReceiver.onAttachedToWindow();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            for (int i = 0; i < this.senders.size(); ++i) {
                Sender sender = this.senders.get(i);
                sender.imageReceiver.onDetachedFromWindow();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            count = animatedCount.set(senders.size());
            for (int i = 0; i < oldSenders.size(); ++i) {
                oldSenders.get(i).draw(canvas);
            }
            for (int i = 0; i < senders.size(); ++i) {
                senders.get(i).draw(canvas);
            }
        }

        private Sender pressedSender;
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (pressedSender != null) {
                    pressedSender.bounce.setPressed(false);
                }
                pressedSender = null;
                for (int i = 0; i < senders.size(); ++i) {
                    if (senders.get(i).clickBounds.contains(event.getX(), event.getY())) {
                        pressedSender = senders.get(i);
                        break;
                    }
                }
                if (pressedSender != null) {
                    pressedSender.bounce.setPressed(true);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (pressedSender != null && !pressedSender.anonymous && pressedSender.clickBounds.contains(event.getX(), event.getY()) && clickListener != null) {
                        clickListener.run(pressedSender.did);
                    }
                }
                if (pressedSender != null) {
                    pressedSender.bounce.setPressed(false);
                }
                pressedSender = null;
            }
            return pressedSender != null;
        }

        public void setMyPrivacy(long peer) {
            for (int i = 0; i < this.senders.size(); ++i) {
                Sender sender = this.senders.get(i);
                if (sender.my) {
                    sender.setPrivacy(peer);
                    return;
                }
            }
        }

        public void setSenders(ArrayList<SenderData> senders) {
            // remove old
            for (int i = 0; i < this.senders.size(); ++i) {
                Sender sender = this.senders.get(i);
                SenderData senderData = null;
                for (int j = 0; j < senders.size(); ++j) {
                    final SenderData sd = senders.get(j);
                    if (sd.my && sender.my || !sender.my && !sd.my && sd.did == sender.did) {
                        senderData = senders.get(j);
                        break;
                    }
                }
                if (senderData == null) {
                    sender.imageReceiver.onDetachedFromWindow();
                    this.senders.remove(i);
                    i--;
                    sender.index = -1;
                    this.oldSenders.add(sender);
                }
            }

            // insert new, update existing
            for (int i = 0; i < senders.size(); ++i) {
                SenderData senderData = senders.get(i);
                Sender sender = null;
                for (int j = 0; j < this.senders.size(); ++j) {
                    final Sender s = this.senders.get(j);
                    if (s.my && senderData.my || !s.my && !senderData.my && s.did == senderData.did) {
                        sender = this.senders.get(j);
                        break;
                    }
                }
                if (sender == null) {
                    for (int j = 0; j < oldSenders.size(); ++j) {
                        final Sender os = oldSenders.get(j);
                        if (os.my && senderData.my || !os.my && !senderData.my && os.did == senderData.did) {
                            sender = oldSenders.get(j);
                            break;
                        }
                    }
                    if (sender != null) {
                        oldSenders.remove(sender);
                        sender.imageReceiver.onAttachedToWindow();
                        this.senders.add(sender);
                    }
                }
                if (sender == null) {
                    sender = new Sender(senderData.my, senderData.did);
                    sender.animatedScale.set(0f, true);
                    this.senders.add(sender);
                    sender.animatedPosition.set(senders.size() - 1 - i, true);
                }
                sender.setStars(senderData.stars);
                if (senderData.my) {
                    sender.setPrivacy(peer);
                } else {
                    sender.setAnonymous(senderData.anonymous);
                }
                sender.index = senders.size() - 1 - i;
            }

            invalidate();
        }

        private Utilities.Callback<Long> clickListener;
        public void setOnSenderClickListener(Utilities.Callback<Long> listener) {
            clickListener = listener;
        }

        public class Sender {

            public int index;
            public final RectF clickBounds = new RectF();
            public final AnimatedFloat animatedPosition = new AnimatedFloat(TopSendersView.this, 0, 600, CubicBezierInterpolator.EASE_OUT_QUINT);
            public final AnimatedFloat animatedScale = new AnimatedFloat(TopSendersView.this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
            public final AnimatedFloat animatedAnonymous = new AnimatedFloat(TopSendersView.this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

            public final boolean my;
            public long did;
            public final ImageReceiver imageReceiver = new ImageReceiver(TopSendersView.this);
            public final AvatarDrawable avatarDrawable = new AvatarDrawable();
            public final AvatarDrawable anonymousAvatarDrawable = new AvatarDrawable();
            public Text text;
            public Text starsText;
            public boolean anonymous;

            public final ButtonBounce bounce = new ButtonBounce(TopSendersView.this);

            public Sender(boolean my, long did) {
                this.my = my;
                this.did = did;

                String name;
                if (did >= 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                    name = UserObject.getForcedFirstName(user);

                    avatarDrawable.setInfo(user);
                    imageReceiver.setForUserOrChat(user, avatarDrawable);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    name = chat == null ? "" : chat.title;

                    avatarDrawable.setInfo(chat);
                    imageReceiver.setForUserOrChat(chat, avatarDrawable);
                }
                imageReceiver.setRoundRadius(dp(56));
                imageReceiver.onAttachedToWindow();
                imageReceiver.setCrossfadeWithOldImage(true);

                anonymousAvatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
                anonymousAvatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundGray));

                text = new Text(name, 12);
            }

            public void detach() {
                imageReceiver.onDetachedFromWindow();
            }

            private long getPrivacy() {
                if (anonymous) {
                    return UserObject.ANONYMOUS;
                } else if (did == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    return 0;
                } else {
                    return did;
                }
            }

            public void setAnonymous(boolean anonymous) {
                if (my) return;
                if (this.anonymous != anonymous) {
                    this.anonymous = anonymous;
                    final String name;
                    if (anonymous) {
                        name = LocaleController.getString(R.string.StarsReactionAnonymous);
                    } else {
                        name = DialogObject.getShortName(did);
                    }
                    text = new Text(name, 12);
                    TopSendersView.this.invalidate();
                }
            }

            public void setPrivacy(long peer) {
                if (!my) return;
                if (getPrivacy() != peer) {
                    anonymous = peer == UserObject.ANONYMOUS;
                    did = peer == 0 || peer == UserObject.ANONYMOUS ? UserConfig.getInstance(currentAccount).getClientUserId() : peer;

                    String name;
                    if (anonymous) {
                        name = LocaleController.getString(R.string.StarsReactionAnonymous);
                    } else if (did >= 0) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                        name = UserObject.getForcedFirstName(user);

                        avatarDrawable.setInfo(user);
                        imageReceiver.setForUserOrChat(user, avatarDrawable);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                        name = chat == null ? "" : chat.title;

                        avatarDrawable.setInfo(chat);
                        imageReceiver.setForUserOrChat(chat, avatarDrawable);
                    }

                    text = new Text(name, 12);

                    TopSendersView.this.invalidate();
                }
            }

            public void setStars(long stars) {
                starsText = new Text(StarsIntroActivity.replaceStars("⭐️" + LocaleController.formatNumber(stars, ','), .85f), 12, AndroidUtilities.getTypeface("fonts/num.otf"));
            }

            public void draw(Canvas canvas) {
                final float position = animatedPosition.set(index);
                final float alpha = animatedScale.set(index >= 0 && index < senders.size());

                canvas.save();
                final float w = (TopSendersView.this.getWidth() - dp(80)) / Math.max(1, count);
                final float cx = dp(40) + w * (count - (.5f + position));
                final float cy = dp(40);

                clickBounds.set(cx - w / 2f, cy - dp(50), cx + w / 2f, cy + dp(50));

                canvas.scale(.7f + .3f * alpha, .7f + .3f * alpha, cx, cy);
                final float s = bounce.getScale(0.04f);
                canvas.scale(s, s, cx, cy);

                if (alpha > 0) {
                    final float anonymous = animatedAnonymous.set(this.anonymous);
                    if (anonymous < 1) {
                        imageReceiver.setImageCoords(cx - dp(56) / 2f, cy - dp(56) / 2f, dp(56), dp(56));
                        imageReceiver.setAlpha(alpha);
                        imageReceiver.draw(canvas);
                        imageReceiver.setAlpha(1f);
                    }
                    if (anonymous > 0) {
                        anonymousAvatarDrawable.setBounds((int) cx - dp(56) / 2, (int) cy - dp(56) / 2, (int) cx + dp(56) / 2, (int) cy + dp(56) / 2);
                        anonymousAvatarDrawable.setAlpha((int) (0xFF * alpha * anonymous));
                        anonymousAvatarDrawable.draw(canvas);
                        anonymousAvatarDrawable.setAlpha(0xFF);
                    }
                }

                rectTmp.set(cx - starsText.getCurrentWidth() / 2f - dp(5.66f), cy + dp(23) - dp(16) / 2f, cx + starsText.getCurrentWidth() / 2f + dp(5.66f), cy + dp(23) + dp(16) / 2f);
                canvas.drawRoundRect(rectTmp, rectTmp.height() / 2f, rectTmp.height() / 2f, backgroundPaint);
                starsBackgroundPaint.setAlpha((int) (0xFF * alpha));
                canvas.drawRoundRect(rectTmp, rectTmp.height() / 2f, rectTmp.height() / 2f, starsBackgroundPaint);
                starsText.draw(canvas, cx - starsText.getCurrentWidth() / 2f, cy + dp(23), 0xFFFFFFFF, alpha);

                text.ellipsize(w - dp(4)).draw(canvas, cx - text.getWidth() / 2f, cy + dp(42), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), alpha);

                canvas.restore();
            }

        }

    }

}

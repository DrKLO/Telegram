/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.RawRes;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.TextViewSwitcher;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

@SuppressWarnings("FieldCanBeLocal")
public class DialogsEmptyCell extends LinearLayout {
    public final static int TYPE_WELCOME_NO_CONTACTS = 0,
        TYPE_WELCOME_WITH_CONTACTS = 1,
        TYPE_FILTER_NO_CHATS_TO_DISPLAY = 2,
        TYPE_FILTER_ADDING_CHATS = 3;
    private final static int TYPE_UNSPECIFIED = -1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TYPE_UNSPECIFIED,
            TYPE_WELCOME_NO_CONTACTS,
            TYPE_WELCOME_WITH_CONTACTS,
            TYPE_FILTER_NO_CHATS_TO_DISPLAY,
            TYPE_FILTER_ADDING_CHATS
    })
    public @interface EmptyType {}

    // Utyan is our special guest here
    private float utyanCollapseProgress;
    private Runnable onUtyanAnimationEndListener;
    private Consumer<Float> onUtyanAnimationUpdateListener;
    private boolean utyanAnimationTriggered;
    private ValueAnimator utyanAnimator;

    private RLottieImageView imageView;
    private TextView titleView;
    private TextViewSwitcher subtitleView;

    @EmptyType
    private int currentType = TYPE_UNSPECIFIED;

    @RawRes
    private int prevIcon;

    private int currentAccount = UserConfig.selectedAccount;

    public DialogsEmptyCell(Context context) {
        super(context);

        setGravity(Gravity.CENTER);
        setOrientation(VERTICAL);
        setOnTouchListener((v, event) -> true);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 52, 4, 52, 0));
        imageView.setOnClickListener(v -> {
            if (!imageView.isPlaying()) {
                imageView.setProgress(0.0f);
                imageView.playAnimation();
            }
        });

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_chats_nameMessage_threeLines));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 10, 52, 0));

        subtitleView = new TextViewSwitcher(context);
        subtitleView.setFactory(() -> {
            TextView tv = new TextView(context);
            tv.setTextColor(Theme.getColor(Theme.key_chats_message));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tv.setGravity(Gravity.CENTER);
            tv.setLineSpacing(AndroidUtilities.dp(2), 1);
            return tv;
        });
        subtitleView.setInAnimation(context, R.anim.alpha_in);
        subtitleView.setOutAnimation(context, R.anim.alpha_out);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 7, 52, 0));
    }

    public void setOnUtyanAnimationEndListener(Runnable onUtyanAnimationEndListener) {
        this.onUtyanAnimationEndListener = onUtyanAnimationEndListener;
    }

    public void setOnUtyanAnimationUpdateListener(Consumer<Float> onUtyanAnimationUpdateListener) {
        this.onUtyanAnimationUpdateListener = onUtyanAnimationUpdateListener;
    }

    public void setType(@EmptyType int value, boolean forward) {
        if (currentType == value) {
            return;
        }
        currentType = value;
        String help;
        int icon;
        switch (currentType) {
            case TYPE_WELCOME_WITH_CONTACTS:
            case TYPE_WELCOME_NO_CONTACTS:
                icon = R.raw.utyan_newborn;
                help = LocaleController.getString("NoChatsHelp", R.string.NoChatsHelp);
                titleView.setText(LocaleController.getString("NoChats", R.string.NoChats));
                break;
            case TYPE_FILTER_NO_CHATS_TO_DISPLAY:
                imageView.setAutoRepeat(false);
                icon = R.raw.filter_no_chats;
                if (forward) {
                    titleView.setText(LocaleController.getString("FilterNoChatsToForward", R.string.FilterNoChatsToForward));
                    help = LocaleController.getString("FilterNoChatsToForwardInfo", R.string.FilterNoChatsToForwardInfo);
                } else {
                    titleView.setText(LocaleController.getString("FilterNoChatsToDisplay", R.string.FilterNoChatsToDisplay));
                    help = LocaleController.getString("FilterNoChatsToDisplayInfo", R.string.FilterNoChatsToDisplayInfo);
                }
                break;
            default:
            case TYPE_FILTER_ADDING_CHATS:
                imageView.setAutoRepeat(true);
                icon = R.raw.filter_new;
                help = LocaleController.getString("FilterAddingChatsInfo", R.string.FilterAddingChatsInfo);
                titleView.setText(LocaleController.getString("FilterAddingChats", R.string.FilterAddingChats));
                break;
        }
        if (icon != 0) {
            imageView.setVisibility(VISIBLE);
            if (currentType == TYPE_WELCOME_WITH_CONTACTS) {
                if (isUtyanAnimationTriggered()) {
                    utyanCollapseProgress = 1f;
                    String noChatsContactsHelp = LocaleController.getString("NoChatsContactsHelp", R.string.NoChatsContactsHelp);
                    if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
                        noChatsContactsHelp = noChatsContactsHelp.replace('\n', ' ');
                    }
                    subtitleView.setText(noChatsContactsHelp, true);
                    requestLayout();
                } else {
                    startUtyanCollapseAnimation(true);
                }
            }
            if (prevIcon != icon) {
                imageView.setAnimation(icon, 100, 100);
                imageView.playAnimation();
                prevIcon = icon;
            }
        } else {
            imageView.setVisibility(GONE);
        }
        if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
            help = help.replace('\n', ' ');
        }
        subtitleView.setText(help, false);
    }

    public boolean isUtyanAnimationTriggered() {
        return utyanAnimationTriggered;
    }

    public void startUtyanExpandAnimation() {
        if (utyanAnimator != null) {
            utyanAnimator.cancel();
        }
        utyanAnimationTriggered = false;
        utyanAnimator = ValueAnimator.ofFloat(utyanCollapseProgress, 0).setDuration(250);
        utyanAnimator.setInterpolator(Easings.easeOutQuad);
        utyanAnimator.addUpdateListener(animation -> {
            utyanCollapseProgress = (float) animation.getAnimatedValue();
            requestLayout();
            if (onUtyanAnimationUpdateListener != null) {
                onUtyanAnimationUpdateListener.accept(utyanCollapseProgress);
            }
        });
        utyanAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onUtyanAnimationEndListener != null) {
                    onUtyanAnimationEndListener.run();
                }
                if (animation == utyanAnimator) {
                    utyanAnimator = null;
                }
            }
        });
        utyanAnimator.start();
    }

    public void startUtyanCollapseAnimation(boolean changeContactsHelp) {
        if (utyanAnimator != null) {
            utyanAnimator.cancel();
        }
        utyanAnimationTriggered = true;
        if (changeContactsHelp) {
            String noChatsContactsHelp = LocaleController.getString("NoChatsContactsHelp", R.string.NoChatsContactsHelp);
            if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
                noChatsContactsHelp = noChatsContactsHelp.replace('\n', ' ');
            }
            subtitleView.setText(noChatsContactsHelp, true);
        }

        utyanAnimator = ValueAnimator.ofFloat(utyanCollapseProgress, 1).setDuration(250);
        utyanAnimator.setInterpolator(Easings.easeOutQuad);
        utyanAnimator.addUpdateListener(animation -> {
            utyanCollapseProgress = (float) animation.getAnimatedValue();
            requestLayout();
            if (onUtyanAnimationUpdateListener != null) {
                onUtyanAnimationUpdateListener.accept(utyanCollapseProgress);
            }
        });
        utyanAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onUtyanAnimationEndListener != null) {
                    onUtyanAnimationEndListener.run();
                }
                if (animation == utyanAnimator) {
                    utyanAnimator = null;
                }
            }
        });
        utyanAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateLayout();
    }

    @Override
    public void offsetTopAndBottom(int offset) {
        super.offsetTopAndBottom(offset);
        updateLayout();
    }

    public void updateLayout() {
        int offset = 0;
        if (getParent() instanceof View && (currentType == TYPE_FILTER_NO_CHATS_TO_DISPLAY || currentType == TYPE_FILTER_ADDING_CHATS)) {
            View view = (View) getParent();
            int paddingTop = view.getPaddingTop();
            if (paddingTop != 0) {
                offset -= getTop() / 2;
            }
        }
        if (currentType == TYPE_WELCOME_NO_CONTACTS || currentType == TYPE_WELCOME_WITH_CONTACTS) {
            offset -= (int) (ActionBar.getCurrentActionBarHeight() / 2f) * (1f - utyanCollapseProgress);
        }
        imageView.setTranslationY(offset);
        titleView.setTranslationY(offset);
        subtitleView.setTranslationY(offset);
    }

    private int measureUtyanHeight(int heightMeasureSpec) {
        int totalHeight;
        if (getParent() instanceof View) {
            View view = (View) getParent();
            totalHeight = view.getMeasuredHeight();
            if (view.getPaddingTop() != 0 && Build.VERSION.SDK_INT >= 21) {
                totalHeight -= AndroidUtilities.statusBarHeight;
            }
        } else {
            totalHeight = MeasureSpec.getSize(heightMeasureSpec);
        }
        if (totalHeight == 0) {
            totalHeight = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        }
        if (getParent() instanceof BlurredRecyclerView) {
            totalHeight -= ((BlurredRecyclerView) getParent()).blurTopPadding;
        }

        return (int) (totalHeight + (AndroidUtilities.dp(320) - totalHeight) * utyanCollapseProgress);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentType == TYPE_WELCOME_NO_CONTACTS || currentType == TYPE_WELCOME_WITH_CONTACTS) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measureUtyanHeight(heightMeasureSpec), MeasureSpec.EXACTLY));
        } else if (currentType == TYPE_FILTER_NO_CHATS_TO_DISPLAY || currentType == TYPE_FILTER_ADDING_CHATS) {
            int totalHeight;
            if (getParent() instanceof View) {
                View view = (View) getParent();
                totalHeight = view.getMeasuredHeight();
                if (view.getPaddingTop() != 0 && Build.VERSION.SDK_INT >= 21) {
                    totalHeight -= AndroidUtilities.statusBarHeight;
                }
            } else {
                totalHeight = MeasureSpec.getSize(heightMeasureSpec);
            }
            if (totalHeight == 0) {
                totalHeight = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            }

            if (getParent() instanceof BlurredRecyclerView) {
                totalHeight -= ((BlurredRecyclerView) getParent()).blurTopPadding;
            }

            ArrayList<TLRPC.RecentMeUrl> arrayList = MessagesController.getInstance(currentAccount).hintDialogs;
            if (!arrayList.isEmpty()) {
                totalHeight -= AndroidUtilities.dp(72) * arrayList.size() + arrayList.size() - 1 + AndroidUtilities.dp(12 + 38);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(166), MeasureSpec.EXACTLY));
        }
    }
}

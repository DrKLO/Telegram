package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.ToggleButton;

import java.util.Arrays;
import java.util.Collections;

public class ItemOptions {

    public static ItemOptions makeOptions(@NonNull BaseFragment fragment, @NonNull View scrimView) {
        return new ItemOptions(fragment, scrimView, false);
    }
    public static ItemOptions makeOptions(@NonNull BaseFragment fragment, @NonNull View scrimView, boolean swipeback) {
        return new ItemOptions(fragment, scrimView, swipeback);
    }

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @NonNull View scrimView) {
        return makeOptions(container, null, scrimView);
    }

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @Nullable Theme.ResourcesProvider resourcesProvider, @NonNull View scrimView) {
        return new ItemOptions(container, resourcesProvider, scrimView, false);
    }
    public static ItemOptions makeOptions(@NonNull ViewGroup container, @Nullable Theme.ResourcesProvider resourcesProvider, @NonNull View scrimView, boolean swipeback) {
        return new ItemOptions(container, resourcesProvider, scrimView, swipeback);
    }

    private ViewGroup container;
    private ViewGroup pointContainer;
    private BaseFragment fragment;
    private Theme.ResourcesProvider resourcesProvider;

    private Context context;
    private View scrimView;
    private Drawable scrimViewBackground;
    private int gravity = Gravity.RIGHT;
    private boolean ignoreX;

    private int scrimViewPadding;
    private int scrimViewRoundRadius;

    public ItemOptions setRoundRadius(int r) {
        return setRoundRadius(r, 0);
    }

    public ItemOptions setRoundRadius(int r, int p) {
        scrimViewRoundRadius = r;
        scrimViewPadding = p;
        return this;
    }

    private ActionBarPopupWindow actionBarPopupWindow;
    private final float[] point = new float[2];

    private Runnable dismissListener;

    private float translateX, translateY;
    private int dimAlpha;
    private boolean drawScrim = true;

    private boolean blur;

    public ItemOptions setBlur(boolean b) {
        this.blur = b;
        return this;
    }

    private DimView dimView;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    private android.graphics.Rect viewAdditionalOffsets = new android.graphics.Rect();
    private ViewGroup layout;
    private LinearLayout linearLayout;
    private int foregroundIndex;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout lastLayout;

    public boolean swipeback;

    private ItemOptions(BaseFragment fragment, View scrimView, boolean swipeback) {
        if (fragment.getContext() == null) {
            return;
        }

        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
        this.context = fragment.getContext();
        this.scrimView = scrimView;
        this.dimAlpha = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) > .705 ? 0x66 : 0x33;
        this.swipeback = swipeback;

        init();
    }

    private ItemOptions(ViewGroup container, Theme.ResourcesProvider resourcesProvider, View scrimView, boolean swipeback) {
        if (container == null || container.getContext() == null) {
            return;
        }

        this.container = container;
        this.resourcesProvider = resourcesProvider;
        this.context = container.getContext();
        this.scrimView = scrimView;
        this.dimAlpha = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) > .705 ? 0x66 : 0x33;
        this.swipeback = swipeback;

        init();
    }

    // for swipeback
    private ItemOptions(ActionBarPopupWindow.ActionBarPopupWindowLayout parentLayout, Theme.ResourcesProvider resourcesProvider) {
        this.context = parentLayout.getContext();
        this.linearLayout = new LinearLayout(context);
        this.linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.resourcesProvider = resourcesProvider;
    }

    private void init() {
        lastLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert2, resourcesProvider, swipeback ? ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK : 0) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (this == layout && maxHeight > 0) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.getMode(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        lastLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow != null && actionBarPopupWindow.isShowing()) {
                dismiss();
            }
        });
        layout = lastLayout;
    }

    public ItemOptions makeSwipeback() {
        ItemOptions options = new ItemOptions(lastLayout, resourcesProvider);
        options.foregroundIndex = lastLayout.addViewToSwipeBack(options.linearLayout);
        return options;
    }

    public void openSwipeback(ItemOptions options) {
        dontDismiss();
        lastLayout.getSwipeBack().openForeground(options.foregroundIndex);
    }

    public void closeSwipeback() {
        dontDismiss();
        lastLayout.getSwipeBack().closeForeground();
    }

    public ItemOptions setSwipebackGravity(boolean right, boolean bottom) {
        lastLayout.swipeBackGravityRight = right;
        lastLayout.swipeBackGravityBottom = bottom;
        return this;
    }

    public ItemOptions ignoreX() {
        ignoreX = true;
        return this;
    }

    public ItemOptions addIf(boolean condition, int iconResId, CharSequence text, boolean isRed, Runnable onClickListener) {
        if (!condition) {
            return this;
        }
        return add(iconResId, text, isRed, onClickListener);
    }

    public ItemOptions addIf(boolean condition, int iconResId, CharSequence text, Runnable onClickListener) {
        if (!condition) {
            return this;
        }
        return add(iconResId, text, Theme.key_actionBarDefaultSubmenuItemIcon, Theme.key_actionBarDefaultSubmenuItem, onClickListener);
    }

    public ItemOptions addIf(boolean condition, int iconResId, Drawable iconDrawable, CharSequence text, Runnable onClickListener) {
        if (!condition) {
            return this;
        }
        return add(iconResId, iconDrawable, text, Theme.key_actionBarDefaultSubmenuItemIcon, Theme.key_actionBarDefaultSubmenuItem, onClickListener);
    }

    public ItemOptions add(CharSequence text, Runnable onClickListener) {
        return add(0, text, false, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, Runnable onClickListener) {
        return add(iconResId, text, false, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, boolean isRed, Runnable onClickListener) {
        return add(iconResId, text, isRed ? Theme.key_text_RedRegular : Theme.key_actionBarDefaultSubmenuItemIcon, isRed ? Theme.key_text_RedRegular : Theme.key_actionBarDefaultSubmenuItem, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, int color, Runnable onClickListener) {
        return add(iconResId, text, color, color, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, int iconColorKey, int textColorKey, Runnable onClickListener) {
        return add(iconResId, null, text, iconColorKey, textColorKey, onClickListener);
    }

    public ItemOptions add(int iconResId, Drawable iconDrawable, CharSequence text, int iconColorKey, int textColorKey, Runnable onClickListener) {
        if (context == null) {
            return this;
        }

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);
        if (iconResId != 0 || iconDrawable != null) {
            subItem.setTextAndIcon(text, iconResId, iconDrawable);
        } else {
            subItem.setText(text);
        }

        subItem.setColors(textColor != null ? textColor : Theme.getColor(textColorKey, resourcesProvider), iconColor != null ? iconColor : Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(selectorColor != null ? selectorColor : Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

        subItem.setOnClickListener(view1 -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            if (dismissWithButtons) dismiss();
        });
        if (minWidthDp > 0) {
            subItem.setMinimumWidth(dp(minWidthDp));
            addView(subItem, LayoutHelper.createLinear(minWidthDp, LayoutHelper.WRAP_CONTENT));
        } else {
            addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        return this;
    }

    public ActionBarMenuSubItem add() {

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);

        subItem.setColors(textColor != null ? textColor : Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), iconColor != null ? iconColor : Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
        subItem.setSelectorColor(selectorColor != null ? selectorColor : Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), .12f));

        if (minWidthDp > 0) {
            subItem.setMinimumWidth(dp(minWidthDp));
            addView(subItem, LayoutHelper.createLinear(minWidthDp, LayoutHelper.WRAP_CONTENT));
        } else {
            addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        return subItem;
    }

    public ItemOptions addCheckedIf(boolean condition, boolean checked, CharSequence text, Runnable onClickListener) {
        if (!condition) return this;
        return addChecked(checked, text, onClickListener);
    }

    public ItemOptions addChecked(boolean checked, CharSequence text, Runnable onClickListener) {
        return addChecked(checked, text, onClickListener, null);
    }


    public ItemOptions addCheckedIf(boolean condition, boolean checked, CharSequence text, Runnable onClickListener, Runnable onLongClickRunnable) {
        if (!condition) return this;
        return addChecked(checked, text, onClickListener, onLongClickRunnable);
    }

    public ItemOptions addChecked(boolean checked, CharSequence text, Runnable onClickListener, Runnable onLongClickRunnable) {
        if (context == null) {
            return this;
        }

        final int textColorKey = Theme.key_actionBarDefaultSubmenuItem;
        final int iconColorKey = Theme.key_actionBarDefaultSubmenuItemIcon;

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);
        subItem.setText(text);
        subItem.setChecked(checked);

        subItem.setColors(textColor != null ? textColor : Theme.getColor(textColorKey, resourcesProvider), iconColor != null ? iconColor : Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(selectorColor != null ? selectorColor : Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

        subItem.setOnClickListener(view1 -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            if (dismissWithButtons) dismiss();
        });
        if (onLongClickRunnable != null) {
            subItem.setOnLongClickListener(view1 -> {
                if (onLongClickRunnable != null) {
                    onLongClickRunnable.run();
                }
                if (dismissWithButtons) dismiss();
                return true;
            });
        }
        if (minWidthDp > 0) {
            subItem.setMinimumWidth(dp(minWidthDp));
            addView(subItem, LayoutHelper.createLinear(minWidthDp, LayoutHelper.WRAP_CONTENT));
        } else {
            addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        return this;
    }

    public ActionBarMenuSubItem addChecked() {

        final int textColorKey = Theme.key_actionBarDefaultSubmenuItem;
        final int iconColorKey = Theme.key_actionBarDefaultSubmenuItemIcon;

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);

        subItem.setColors(textColor != null ? textColor : Theme.getColor(textColorKey, resourcesProvider), iconColor != null ? iconColor : Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(selectorColor != null ? selectorColor : Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

        if (minWidthDp > 0) {
            subItem.setMinimumWidth(dp(minWidthDp));
            addView(subItem, LayoutHelper.createLinear(minWidthDp, LayoutHelper.WRAP_CONTENT));
        } else {
            addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        return subItem;
    }

    public boolean dismissWithButtons = true;
    public ItemOptions setDismissWithButtons(boolean enable) {
        this.dismissWithButtons = enable;
        return this;
    }

    public ItemOptions addChat(TLObject obj, boolean checked, Runnable onClickListener) {
        if (context == null) {
            return this;
        }

        final int textColorKey = Theme.key_actionBarDefaultSubmenuItem;
        final int iconColorKey = Theme.key_actionBarDefaultSubmenuItemIcon;

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);
        if (obj instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) obj;
            subItem.setText(chat == null ? "" : chat.title);
            subItem.setSubtext(ChatObject.isChannelAndNotMegaGroup(chat) ? getString(R.string.DiscussChannel) : getString(R.string.AccDescrGroup).toLowerCase());
        } else if (obj instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) obj;
            subItem.setText(UserObject.getUserName(user));
            if (user.id == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
                subItem.setSubtext(getString(R.string.VoipGroupPersonalAccount));
            } else if (UserObject.isBot(user)) {
                subItem.setSubtext(getString(R.string.Bot));
            }
        }

        subItem.setClipToPadding(false);
        subItem.textView.setPadding(subItem.checkViewLeft ? (subItem.checkView != null ? dp(43) : 0) : dp(43), 0, subItem.checkViewLeft ? dp(43) : (subItem.checkView != null ? dp(43) : 0), 0);
        BackupImageView imageView = new BackupImageView(context);
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(obj);
        imageView.setRoundRadius(dp(34));
        imageView.setForUserOrChat(obj, avatarDrawable);
        imageView.setScaleX(checked ? 0.84f : 1.0f);
        imageView.setScaleY(checked ? 0.84f : 1.0f);
        subItem.addView(imageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), -5, 0, -5, 0));

        if (checked) {
            final float strokeWidth = 2;
            View checkView = new View(context);
            checkView.setBackground(Theme.createOutlineCircleDrawable(dp(34), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), dp(strokeWidth)));
            subItem.addView(checkView, LayoutHelper.createFrame(34 + strokeWidth, 34 + strokeWidth, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), -5 - strokeWidth / 2.0f, 0, -5, 0));
        }

        subItem.setColors(textColor != null ? textColor : Theme.getColor(textColorKey, resourcesProvider), iconColor != null ? iconColor : Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(selectorColor != null ? selectorColor : Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

        subItem.setOnClickListener(view1 -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            if (dismissWithButtons) dismiss();
        });
        if (minWidthDp > 0) {
            subItem.setMinimumWidth(dp(minWidthDp));
            addView(subItem, LayoutHelper.createLinear(minWidthDp, LayoutHelper.WRAP_CONTENT));
        } else {
            addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        return this;
    }

    public ItemOptions add(CharSequence text, CharSequence subtext, Runnable onClickListener) {
        if (context == null) {
            return this;
        }

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(dp(18), 0, dp(18), 0);
        subItem.setText(text);
        subItem.setSubtext(subtext);

        subItem.setColors(textColor != null ? textColor : Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), iconColor != null ? iconColor : Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
        subItem.setSelectorColor(selectorColor != null ? selectorColor : Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), .12f));

        subItem.setOnClickListener(view1 -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            if (dismissWithButtons) dismiss();
        });
        if (minWidthDp > 0) {
            subItem.setMinimumWidth(dp(minWidthDp));
            addView(subItem, LayoutHelper.createLinear(minWidthDp, LayoutHelper.WRAP_CONTENT));
        } else {
            addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        return this;
    }

    public ItemOptions makeMultiline(boolean changeSize) {
        if (context == null || lastLayout.getItemsCount() <= 0) {
            return this;
        }

        View lastChild = lastLayout.getItemAt(lastLayout.getItemsCount() - 1);
        if (lastChild instanceof ActionBarMenuSubItem) {
            ((ActionBarMenuSubItem) lastChild).setMultiline(changeSize);
        }
        return this;
    }

    public ItemOptions cutTextInFancyHalf() {
        if (context == null || lastLayout.getItemsCount() <= 0) {
            return this;
        }

        View lastChild = lastLayout.getItemAt(lastLayout.getItemsCount() - 1);
        if (lastChild instanceof ActionBarMenuSubItem) {
            TextView textView = ((ActionBarMenuSubItem) lastChild).getTextView();
            textView.setMaxWidth(
                HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()) + textView.getPaddingLeft() + textView.getPaddingRight()
            );
        }

        return this;
    }

    private int shiftDp = -4;
    public ItemOptions putPremiumLock(Runnable onLockClick) {
        if (onLockClick == null || context == null || lastLayout.getItemsCount() <= 0) {
            return this;
        }
        View lastChild = lastLayout.getItemAt(lastLayout.getItemsCount() - 1);
        if (!(lastChild instanceof ActionBarMenuSubItem)) {
            return this;
        }
        ActionBarMenuSubItem lastSubItem = (ActionBarMenuSubItem) lastChild;
        lastSubItem.setRightIcon(R.drawable.msg_mini_lock3);
        lastSubItem.getRightIcon().setAlpha(.4f);
        lastSubItem.setOnClickListener(view1 -> {
            if (onLockClick != null) {
                AndroidUtilities.shakeViewSpring(view1, shiftDp = -shiftDp);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                onLockClick.run();
            }
        });
        return this;
    }

    public ItemOptions putCheck() {
        if (context == null || lastLayout.getItemsCount() <= 0) {
            return this;
        }
        View lastChild = lastLayout.getItemAt(lastLayout.getItemsCount() - 1);
        if (!(lastChild instanceof ActionBarMenuSubItem)) {
            return this;
        }
        ActionBarMenuSubItem lastSubItem = (ActionBarMenuSubItem) lastChild;
        lastSubItem.setRightIcon(R.drawable.msg_text_check);
        lastSubItem.getRightIcon().setColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY);
        lastSubItem.getRightIcon().setScaleX(.85f);
        lastSubItem.getRightIcon().setScaleY(.85f);
        return this;
    }

    public ItemOptions addGapIf(boolean condition) {
        if (!condition) return this;
        return addGap();
    }

    public ItemOptions addGap() {
        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider);
        gap.setTag(R.id.fit_width_tag, 1);
        if (gapBackgroundColor != null) {
            gap.setColor(gapBackgroundColor);
        }
        addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        return this;
    }

    public ItemOptions addSpaceGap() {
        if (!(layout instanceof LinearLayout)) {
            layout = new LinearLayout(context);
            ((LinearLayout) layout).setOrientation(LinearLayout.VERTICAL);
            layout.addView(lastLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        lastLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider);
        lastLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow != null && actionBarPopupWindow.isShowing()) {
                dismiss();
            }
        });
        layout.addView(lastLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -8, 0, 0));
        return this;
    }

    public ItemOptions addView(View view) {
        if (view == null) {
            return this;
        }
        view.setTag(R.id.fit_width_tag, 1);
        addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return this;
    }

    public ItemOptions addFrom(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, ActionBar.ActionBarMenuOnItemClick clickListener) {
        for (int i = 0; i < popupLayout.getItemsCount(); ++i) {
            View child = popupLayout.getItemAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof ActionBarMenuSubItem) {
                ActionBarMenuSubItem item = (ActionBarMenuSubItem) child;
                final int id = (Integer) item.getTag();
                add(item.getIconResId(), item.getTextView().getText(), () -> {
                    if (clickListener != null) {
                        clickListener.onItemClick(id);
                    }
                });
            }
        }
        return this;
    }

    public ItemOptions addView(View view, LinearLayout.LayoutParams lp) {
        if (view == null) {
            return this;
        }
        if (linearLayout != null) {
            linearLayout.addView(view, lp);
        } else {
            lastLayout.addView(view, lp);
        }
        return this;
    }

    public ItemOptions addProfile(TLObject obj, CharSequence subtitle, Runnable onClickListener) {
        final FrameLayout userButton = new FrameLayout(context);
        userButton.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 6));

        final BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(17));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(obj);
        imageView.setForUserOrChat(obj, avatarDrawable);
        userButton.addView(imageView, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.CENTER_VERTICAL, 13, 0, 0, 0));

        final TextView titleText = new TextView(context);
        titleText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setSingleLine(true);
        if (obj instanceof TLRPC.User) {
            titleText.setText(UserObject.getUserName((TLRPC.User) obj));
        } else if (obj instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) obj;
            titleText.setText(chat.title);
        }
        userButton.addView(titleText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 59, 6, 16, 0));

        final TextView subtitleText = new TextView(context);
        subtitleText.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleText.setText(AndroidUtilities.replaceArrows(subtitle, false, dp(1), dp(.66f)));
        userButton.addView(subtitleText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 59, 27, 16, 0));

        userButton.setOnClickListener(v -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
        });
        addView(userButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        return this;
    }


    public ItemOptions addText(CharSequence text, int textSizeDp) {
        return addText(text, textSizeDp, -1);
    }

    public ItemOptions addText(CharSequence text, int textSizeDp, int maxWidth) {
        final TextView textView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setPadding(dp(13), dp(8), dp(13), dp(8));
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
        textView.setTag(R.id.fit_width_tag, 1);
        NotificationCenter.listenEmojiLoading(textView);
        if (maxWidth > 0) {
            textView.setMaxWidth(maxWidth);
        }
        addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return this;
    }

    public ItemOptions setScrimViewBackground(Drawable drawable) {
        this.scrimViewBackground = drawable;
        return this;
    }

    private boolean allowMoveScrim;
    public ItemOptions allowMoveScrim() {
        this.allowMoveScrim = true;
        return this;
    }

    private int animateToWidth;
    private int animateToHeight;
    public ItemOptions animateToSize(int w, int h) {
        this.animateToWidth = w;
        this.animateToHeight = h;
        return this;
    }

    private boolean hideScrimUnder;
    public ItemOptions hideScrimUnder() {
        hideScrimUnder = true;
        return this;
    }

    public ItemOptions setGravity(int gravity) {
        this.gravity = gravity;
        if (gravity == Gravity.RIGHT && swipeback && layout instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
            ((ActionBarPopupWindow.ActionBarPopupWindowLayout) layout).swipeBackGravityRight = true;
        }
        return this;
    }

    public ItemOptions translate(float x, float y) {
        this.translateX += x;
        this.translateY += y;
        return this;
    }

    private int minWidthDp;
    private int fixedWidthDp;

    public ItemOptions setMinWidth(int minWidthDp) {
        this.minWidthDp = minWidthDp;
        return this;
    }

    public ItemOptions setFixedWidth(int fixedWidthDp) {
        this.fixedWidthDp = fixedWidthDp;
        return this;
    }


    public ItemOptions setDimAlpha(int dimAlpha) {
        this.dimAlpha = dimAlpha;
        return this;
    }

    public ItemOptions setDrawScrim(boolean draw) {
        this.drawScrim = draw;
        return this;
    }

    private boolean forceTop;
    public ItemOptions forceTop(boolean force) {
        forceTop = force;
        return this;
    }

    private boolean allowCenter;
    public ItemOptions allowCenter(boolean allow) {
        allowCenter = allow;
        return this;
    }

    private boolean forceBottom;
    public ItemOptions forceBottom(boolean force) {
        forceBottom = force;
        return this;
    }

    private int maxHeight;
    public ItemOptions setMaxHeight(int px) {
        this.maxHeight = px;
        return this;
    }

    public boolean onTopOfScrim;
    public ItemOptions setOnTopOfScrim() {
        this.onTopOfScrim = true;
        return this;
    }

    public ActionBarMenuSubItem getLast() {
        if (linearLayout != null) {
            if (linearLayout.getChildCount() <= 0) return null;
            View lastChild = linearLayout.getChildAt(linearLayout.getChildCount() - 1);
            if (!(lastChild instanceof ActionBarMenuSubItem)) return null;
            return (ActionBarMenuSubItem) lastChild;
        } else if (lastLayout != null) {
            if (lastLayout.getItemsCount() <= 0) return null;
            View lastChild = lastLayout.getItemAt(lastLayout.getItemsCount() - 1);
            if (!(lastChild instanceof ActionBarMenuSubItem)) return null;
            return (ActionBarMenuSubItem) lastChild;
        }
        return null;
    }

    public ViewGroup getLayout() {
        return layout;
    }

    public ItemOptions setBlurBackground(BlurringShader.BlurManager blurManager, float ox, float oy) {
        Drawable baseDrawable = context.getResources().getDrawable(R.drawable.popup_fixed_alert2).mutate();
        if (layout instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
            layout.setBackgroundDrawable(
                new BlurringShader.StoryBlurDrawer(blurManager, layout, BlurringShader.StoryBlurDrawer.BLUR_TYPE_MENU_BACKGROUND)
                    .makeDrawable(offsetX + ox + layout.getX(), offsetY + oy + layout.getY(), baseDrawable, dp(6))
            );
        } else {
            for (int i = 0; i < layout.getChildCount(); ++i) {
                View child = layout.getChildAt(i);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    child.setBackgroundDrawable(
                        new BlurringShader.StoryBlurDrawer(blurManager, child, BlurringShader.StoryBlurDrawer.BLUR_TYPE_MENU_BACKGROUND)
                            .makeDrawable(offsetX + ox + layout.getX() + child.getX(), offsetY + oy + layout.getY() + child.getY(), baseDrawable, dp(6))
                    );
                }
            }
        }
        return this;
    }

    public int getItemsCount() {
        if (lastLayout == null && layout == null)
            return 0;
        if (lastLayout == layout) {
            return lastLayout.getItemsCount();
        } else {
            int itemsCount = 0;
            for (int j = 0; j < layout.getChildCount() - 1; ++j) {
                View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                    itemsCount += popupLayout.getItemsCount();
                }
            }
            return itemsCount;
        }
    }

    public View getItemAt(int index) {
        if (lastLayout == null && layout == null)
            return null;
        if (lastLayout == layout) {
            return lastLayout.getItemAt(index);
        } else {
            for (int j = 0; j < layout.getChildCount() - 1; ++j) {
                View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                    View item = popupLayout.getItemAt(index);
                    if (item != null) return item;
                    index -= popupLayout.getItemsCount();
                }
            }
            return null;
        }
    }

    private float offsetX, offsetY;

    public void setupSelectors() {
        if (layout == null) return;

        for (int j = 0; j < layout.getChildCount(); ++j) {
            View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
            if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                if (popupLayout.getItemsCount() <= 0) {
                    continue;
                }
                View first = popupLayout.getItemAt(0), last = popupLayout.getItemAt(popupLayout.getItemsCount() - 1);
                if (first instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) first).updateSelectorBackground(true, first == last);
                } else if (first instanceof MessagePreviewView.ToggleButton || first instanceof FrameLayout) {
                    first.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider), 6, first == last ? 6 : 0));
                }
                if (last instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) last).updateSelectorBackground(last == first, true);
                } else if (last instanceof MessagePreviewView.ToggleButton || last instanceof FrameLayout) {
                    last.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider), first == last ? 6 : 0, 6));
                }
            }
        }
    }
    public ItemOptions show() {
        if (actionBarPopupWindow != null || linearLayout != null) {
            return this;
        }

        if (getItemsCount() <= 0) {
            return this;
        }

        setupSelectors();

        if (fixedWidthDp > 0) {
            for (int j = 0; j < layout.getChildCount() - 1; ++j) {
                View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                    for (int i = 0; i < popupLayout.getItemsCount(); ++i) {
                        ViewGroup.LayoutParams lp = popupLayout.getItemAt(i).getLayoutParams();
                        lp.width = dp(fixedWidthDp);
                    }
                }
            }
        } else if (minWidthDp > 0) {
            for (int j = 0; j < layout.getChildCount() - 1; ++j) {
                View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                    for (int i = 0; i < popupLayout.getItemsCount(); ++i) {
                        popupLayout.getItemAt(i).setMinimumWidth(dp(minWidthDp));
                    }
                }
            }
        }

        ViewGroup container = pointContainer = this.container == null ? fragment.getParentLayout().getOverlayContainerView() : this.container;

        if (context == null || container == null) {
            return this;
        }

        float x = 0;
        float y = AndroidUtilities.displaySize.y / 2f;
        if (scrimView != null) {
            getPointOnScreen(scrimView, container, point);
            y = point[1];
            x = point[0];
        }
        RectF scrimViewBounds = new RectF();
        if (scrimView instanceof ScrimView) {
            ((ScrimView) scrimView).getBounds(scrimViewBounds);
        } else if (animateToWidth != 0 && animateToHeight != 0) {
            scrimViewBounds.set(0, 0, animateToWidth, animateToHeight);
        } else {
            scrimViewBounds.set(0, 0, scrimView.getMeasuredWidth(), scrimView.getMeasuredHeight());
        }
        x += scrimViewBounds.left;
        y += scrimViewBounds.top;
        if (ignoreX) {
            x = point[0] = 0;
        }

        if (dimAlpha > 0) {
            DimView dimViewLocal = dimView = new DimView(context);
            preDrawListener = () -> {
                dimViewLocal.invalidate();
                return true;
            };
            container.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            dimView.setProgress(0);
            if (hideScrimUnder) {
                scrimView.setVisibility(View.INVISIBLE);
            }
            if (dimAnimator != null) {
                dimAnimator.cancel();
                dimAnimator = null;
            }
            dimAnimator = ValueAnimator.ofFloat(0, 1);
            dimAnimator.addUpdateListener((anm) -> {
                final float t = (float) anm.getAnimatedValue();
                if (dimView != null) {
                    dimView.setProgress(t);
                }
            });
            dimAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (dimView != null) {
                        dimView.setProgress(1);
                        dimView.invalidate();
                    }
                    dimAnimator = null;
                }
            });
            if (allowMoveScrim) {
                dimAnimator.setDuration(380);
                dimAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                dimAnimator.setDuration(150);
            }
            dimAnimator.start();
        }
        if (allowMoveScrim && dimView != null) {
            if (animateToWidth != 0) {
                dimView.moveToX = (container.getWidth() - animateToWidth) / 2f;
                x += -point[0] + (container.getWidth() - animateToWidth) / 2f;
            }
        }
        layout.measure(View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.AT_MOST));
        final RectF layoutBounds = new RectF();
        final android.graphics.Rect layoutPadding = lastLayout.getPadding();
        layoutBounds.set(layoutPadding.left, layoutPadding.top, layout.getMeasuredWidth() - layoutPadding.right, layout.getMeasuredHeight() - layoutPadding.bottom);

        actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                ItemOptions.this.dismissDim(container);

                if (dismissListener != null) {
                    dismissListener.run();
                    dismissListener = null;
                }
            }
        };
        actionBarPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                actionBarPopupWindow = null;
                dismissDim(container);

                if (dismissListener != null) {
                    dismissListener.run();
                    dismissListener = null;
                }
            }
        });
        actionBarPopupWindow.setOutsideTouchable(true);
        actionBarPopupWindow.setFocusable(true);
        actionBarPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        actionBarPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        if (AndroidUtilities.isTablet()) {
            y += container.getPaddingTop();
            x -= container.getPaddingLeft();
        }
        int X;
        if (scrimView != null) {
            if (gravity == Gravity.RIGHT) {
                X = (int) (container.getX() + x + scrimViewBounds.width() - layoutBounds.right);
            } else if (gravity == Gravity.CENTER_HORIZONTAL) {
                X = (int) (container.getX() + x + scrimViewBounds.width() / 2.0f - layout.getMeasuredWidth() / 2.0f);
            } else {
                if (x + layoutBounds.width() > container.getWidth()) {
                    X = (int) (container.getX() + x + scrimViewBounds.width() - layoutBounds.right);
                } else {
                    X = (int) (container.getX() + x - layoutBounds.left);
                }
            }
        } else {
            X = (container.getWidth() - layout.getMeasuredWidth()) / 2; // at the center
        }
        int Y;
        float scrimHeight = onTopOfScrim ? 0 : scrimViewBounds.height();
        if (forceBottom) {
            if (allowMoveScrim) {
                Y = (int) (y + scrimHeight);
            } else {
                Y = (int) (Math.min(y + scrimHeight, AndroidUtilities.displaySize.y) - layout.getMeasuredHeight() + container.getY());
            }
        } else if (scrimView != null) {
            if (forceTop || y + scrimHeight + layout.getMeasuredHeight() + dp(16) > AndroidUtilities.displaySize.y - AndroidUtilities.navigationBarHeight) {
                // put above scrimView
                y -= scrimHeight;
                y -= layout.getMeasuredHeight();
                if (allowCenter && Math.max(0, y + scrimHeight) + layout.getMeasuredHeight() > point[1] + scrimViewBounds.top && scrimViewBounds.height() == scrimView.getHeight()) {
                    y = (container.getHeight() - layout.getMeasuredHeight()) / 2f - scrimHeight - container.getY();
                }
            }
            Y = (int) (y + scrimHeight + container.getY()); // under scrimView
        } else {
            Y = (container.getHeight() - layout.getMeasuredHeight()) / 2; // at the center
        }

        if (allowMoveScrim && dimView != null) {
//            float layoutTop = Utilities.clamp(Y + this.translateY, container.getHeight() - layout.getMeasuredHeight(), 0);
//            float layoutBottom = layoutTop + layout.getMeasuredHeight();
//            if (AndroidUtilities.intersect1d(layoutTop, layoutBottom, point[1], point[1] + scrimViewBounds.bottom)) {
//                if (forceBottom || layoutTop < point[1] + scrimViewBounds.bottom) {
//                    dimView.moveToY = layoutTop - scrimViewBounds.bottom;
//                } else if (layoutBottom > point[1]) {
//                    dimView.moveToY = layoutBottom;
//                } else {
//                    dimView.moveToY = point[1];
//                }
//            } else {
//                dimView.moveToY = point[1];
//            }
            dimView.moveToY = (container.getHeight() - (layout.getMeasuredHeight() + scrimViewBounds.bottom)) / 2f;
            Y = (int) (dimView.moveToY + scrimViewBounds.bottom);
            X = (int) (dimView.moveToX + scrimViewBounds.right - layout.getMeasuredWidth() + dp(4));
        }

        // discard all scrolls/gestures
        if (fragment != null && fragment.getFragmentView() != null) {
            fragment.getFragmentView().getRootView().dispatchTouchEvent(AndroidUtilities.emptyMotionEvent());
        } else if (this.container != null) {
            container.dispatchTouchEvent(AndroidUtilities.emptyMotionEvent());
        }

        actionBarPopupWindow.showAtLocation(
            container,
            0,
            (int) (offsetX = (X + this.translateX)),
            (int) (offsetY = (Y + this.translateY))
        );
        return this;
    }

    public ItemOptions setBackgroundColor(int color) {
        for (int j = 0; j < layout.getChildCount(); ++j) {
            View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
            if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                child.setBackgroundColor(color);
            }
        }
        return this;
    }

    private Integer gapBackgroundColor;
    public ItemOptions setGapBackgroundColor(int color) {
        gapBackgroundColor = color;
        for (int j = 0; j < layout.getChildCount(); ++j) {
            View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
            if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                ActionBarPopupWindow.ActionBarPopupWindowLayout l = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                for (int i = 0; i < l.getItemsCount(); ++i) {
                    View child2 = l.getItemAt(i);
                    if (child2 instanceof ActionBarPopupWindow.GapView) {
                        ((ActionBarPopupWindow.GapView) child2).setColor(color);
                    }
                }
            } else if (child instanceof ActionBarPopupWindow.GapView) {
                ((ActionBarPopupWindow.GapView) child).setColor(color);
            }
        }
        return this;
    }

    private Integer textColor, iconColor;
    public ItemOptions setColors(int textColor, int iconColor) {
        this.textColor = textColor;
        this.iconColor = iconColor;
        for (int j = 0; j < layout.getChildCount(); ++j) {
            View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
            if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                ActionBarPopupWindow.ActionBarPopupWindowLayout l = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                for (int i = 0; i < l.getItemsCount(); ++i) {
                    View child2 = l.getItemAt(i);
                    if (child2 instanceof ActionBarMenuSubItem) {
                        ((ActionBarMenuSubItem) child2).setColors(textColor, iconColor);
                    }
                }
            } else if (child instanceof ActionBarMenuSubItem) {
                ((ActionBarMenuSubItem) child).setColors(textColor, iconColor);
            }
        }
        return this;
    }

    private Integer selectorColor;
    public ItemOptions setSelectorColor(int selectorColor) {
        this.selectorColor = selectorColor;
        for (int j = 0; j < layout.getChildCount(); ++j) {
            View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
            if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                ActionBarPopupWindow.ActionBarPopupWindowLayout l = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                for (int i = 0; i < l.getItemsCount(); ++i) {
                    View child2 = l.getItemAt(i);
                    if (child2 instanceof ActionBarMenuSubItem) {
                        ((ActionBarMenuSubItem) child2).setSelectorColor(selectorColor);
                    }
                }
            } else if (child instanceof ActionBarMenuSubItem) {
                ((ActionBarMenuSubItem) child).setSelectorColor(selectorColor);
            }
        }
        return this;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    private ValueAnimator dimAnimator;
    private void dismissDim(ViewGroup container) {
        if (dimView == null) {
            return;
        }
        DimView dimViewFinal = dimView;
        dimView = null;
        if (dimAnimator != null) {
            dimAnimator.cancel();
        }
        dimAnimator = ValueAnimator.ofFloat(dimViewFinal.dimProgress, 0);
        dimAnimator.addUpdateListener((anm) -> {
            final float t = (float) anm.getAnimatedValue();
            dimViewFinal.setProgress(t);
        });
        dimAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dimViewFinal.setProgress(0);
                dimViewFinal.invalidate();

                AndroidUtilities.removeFromParent(dimViewFinal);
                container.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                if (hideScrimUnder) {
                    scrimView.setVisibility(View.VISIBLE);
                    if (scrimView instanceof GiftSheet.GiftCell) {
                        ((GiftSheet.GiftCell) scrimView).invalidateCustom();
                    }
                }
            }
        });
        if (allowMoveScrim) {
            dimAnimator.setDuration(380);
            dimAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        } else {
            dimAnimator.setDuration(150);
        }
        dimAnimator.start();
    }

    public void updateColors() {
        // TODO
    }

    public boolean isShown() {
        return actionBarPopupWindow != null && actionBarPopupWindow.isShowing();
    }

    public ItemOptions setOnDismiss(Runnable dismissListener) {
        this.dismissListener = dismissListener;
        return this;
    }

    public void dismiss() {
        if (dontDismiss) {
            dontDismiss = false;
            return;
        }
        if (actionBarPopupWindow != null) {
            actionBarPopupWindow.dismiss();
        } else if (dismissListener != null) {
            dismissListener.run();
        }
    }

    private boolean dontDismiss;
    public void dontDismiss() {
        dontDismiss = true;
    }

    public static void getPointOnScreen(View v, ViewGroup finalContainer, float[] point) {
        if (v == null || finalContainer == null) return;
        float x = 0;
        float y = 0;
        while (v != finalContainer) {
            y += v.getY();
            x += v.getX();
            if (v instanceof ScrollView) {
                x -= v.getScrollX();
                y -= v.getScrollY();
            }
            if (!(v.getParent() instanceof View)) {
                break;
            }
            v = (View) v.getParent();
            if (!(v instanceof ViewGroup)) {
                return;
            }
        }
        x -= finalContainer.getPaddingLeft();
        y -= finalContainer.getPaddingTop();
        point[0] = x;
        point[1] = y;
    }

    public ItemOptions setViewAdditionalOffsets(int left, int top, int right, int bottom) {
        viewAdditionalOffsets.set(left, top, right, bottom);
        return this;
    }

    public class DimView extends View {

        private final Bitmap cachedBitmap;
        private final Paint cachedBitmapPaint;

        private Bitmap blurBitmap;
        private Paint blurPaint;

        public final float clipTop;
        public final float clipBottom;
        private final int dim;

        private final Path clipPath = new Path();
        private final RectF bounds = new RectF();

        private float moveToX, moveToY;

        public float dimProgress;
        public void setProgress(float progress) {
            if (dimProgress == progress) return;
            dimProgress = progress;
            invalidate();
        }

        public DimView(Context context) {
            super(context);

            if (scrimView != null && scrimView.getParent() instanceof View) {
                clipTop = ((View) scrimView.getParent()).getY() + scrimView.getY();
                clipBottom = allowMoveScrim ? Math.min(dp(10+48+10), Math.max(0, ((View) scrimView.getParent()).getY() + scrimView.getY() + scrimView.getHeight())) : 0; // TODO
            } else {
                clipTop = 0;
                clipBottom = 0;
            }
            dim = ColorUtils.setAlphaComponent(0x00000000, dimAlpha);

            if (drawScrim && scrimView instanceof UserCell && fragment instanceof ProfileActivity) {
                cachedBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                cachedBitmap = Bitmap.createBitmap(scrimView.getWidth() + viewAdditionalOffsets.width(), scrimView.getHeight() + viewAdditionalOffsets.height(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(cachedBitmap);
                canvas.translate(viewAdditionalOffsets.left, viewAdditionalOffsets.top);
                scrimView.draw(canvas);
            } else {
                cachedBitmapPaint = null;
                cachedBitmap = null;
            }

            if (blur) {
                blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                scrimView.setAlpha(0.0f);
                AndroidUtilities.makeGlobalBlurBitmap(b -> {
                    scrimView.setAlpha(1.0f);
                    blurBitmap = b;
                }, 12.0f);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (blurBitmap != null) {
                canvas.save();
                final float scale = Math.max((float) getWidth() / blurBitmap.getWidth(), (float) getHeight() / blurBitmap.getHeight());
                canvas.scale(scale, scale);
                blurPaint.setAlpha((int) (0xFF * dimProgress));
                canvas.drawBitmap(blurBitmap, 0, 0, blurPaint);
                canvas.restore();
            } else {
                canvas.drawColor(Theme.multAlpha(dim, dimProgress));
            }

            if (!drawScrim) {
            } else if (cachedBitmap != null && scrimView.getParent() instanceof View) {
                canvas.save();
                if (clipTop < 1) {
                    canvas.clipRect(-viewAdditionalOffsets.left, -viewAdditionalOffsets.top + point[1] - clipTop * (blur ? 1.0f - dimProgress : 1.0f) + 1, getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom);
                }
                if (allowMoveScrim) {
                    getPointOnScreen(scrimView, pointContainer, point);
                    canvas.translate(lerp(point[0], moveToX, dimProgress), lerp(point[1], moveToY, dimProgress));
                } else {
                    canvas.translate(point[0], point[1]);
                }

                if (scrimViewBackground != null) {
                    if (scrimViewBackground.getIntrinsicWidth() > 0 && scrimViewBackground.getIntrinsicHeight() > 0) {
                        scrimViewBackground.setBounds(
                            -viewAdditionalOffsets.left + (scrimView.getWidth() + viewAdditionalOffsets.right - scrimViewBackground.getIntrinsicWidth()) / 2,
                            -viewAdditionalOffsets.top + (scrimView.getHeight() + viewAdditionalOffsets.bottom - scrimViewBackground.getIntrinsicHeight()) / 2,
                            -viewAdditionalOffsets.left + (scrimView.getWidth() + viewAdditionalOffsets.right + scrimViewBackground.getIntrinsicWidth()) / 2,
                            -viewAdditionalOffsets.top + (scrimView.getHeight() + viewAdditionalOffsets.bottom + scrimViewBackground.getIntrinsicHeight()) / 2
                        );
                    } else {
                        scrimViewBackground.setBounds(
                            -viewAdditionalOffsets.left,
                            -viewAdditionalOffsets.top,
                            scrimView.getWidth() + viewAdditionalOffsets.right,
                            scrimView.getHeight() + viewAdditionalOffsets.bottom
                        );
                    }
                    scrimViewBackground.draw(canvas);
                }
                if (scrimViewPadding > 0 || scrimViewRoundRadius > 0) {
                    clipPath.rewind();
                    AndroidUtilities.rectTmp.set(-viewAdditionalOffsets.left + scrimViewPadding * dimProgress, -viewAdditionalOffsets.top + scrimViewPadding * getAlpha(), -viewAdditionalOffsets.left + cachedBitmap.getWidth() - scrimViewPadding * getAlpha(), -viewAdditionalOffsets.top + cachedBitmap.getHeight() - scrimViewPadding * getAlpha());
                    clipPath.addRoundRect(AndroidUtilities.rectTmp, scrimViewRoundRadius * dimProgress, scrimViewRoundRadius * dimProgress, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                }
                cachedBitmapPaint.setAlpha((int) (0xFF * dimProgress));
                canvas.drawBitmap(cachedBitmap, -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, cachedBitmapPaint);
                canvas.restore();
            } else if (scrimView != null && scrimView.getParent() instanceof View) {
                canvas.save();
                if (clipTop < 1 || clipBottom != 0) {
                    if (allowMoveScrim) {
                        canvas.clipRect(-viewAdditionalOffsets.left, lerp(-viewAdditionalOffsets.top + point[1] - clipTop * (blur ? 1.0f - dimProgress : 1.0f) + 1, 0, dimProgress), getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom - clipBottom * (1f - dimProgress));
                    } else {
                        canvas.clipRect(-viewAdditionalOffsets.left, -viewAdditionalOffsets.top + point[1] - clipTop * (blur ? 1.0f - dimProgress : 1.0f) + 1, getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom);
                    }
                }
                final float moveProgress = dimProgress;
                if (allowMoveScrim) {
                    getPointOnScreen(scrimView, pointContainer, point);
                    canvas.translate(lerp(point[0], moveToX, moveProgress), lerp(point[1], moveToY, moveProgress));
                } else {
                    canvas.translate(point[0], point[1]);
                }

                final float w, h;
                if (animateToWidth != 0 && animateToHeight != 0) {
                    w = lerp(scrimView.getWidth(), animateToWidth, moveProgress);
                    h = lerp(scrimView.getHeight(), animateToHeight, moveProgress);
                } else {
                    w = scrimView.getWidth();
                    h = scrimView.getHeight();
                }

                if (scrimViewBackground != null) {
                    if (scrimViewBackground.getIntrinsicWidth() > 0 && scrimViewBackground.getIntrinsicHeight() > 0) {
                        scrimViewBackground.setBounds(
                            -viewAdditionalOffsets.left + (scrimView.getWidth() + viewAdditionalOffsets.right - scrimViewBackground.getIntrinsicWidth()) / 2,
                            -viewAdditionalOffsets.top + (scrimView.getHeight() + viewAdditionalOffsets.bottom - scrimViewBackground.getIntrinsicHeight()) / 2,
                            -viewAdditionalOffsets.left + (scrimView.getWidth() + viewAdditionalOffsets.right + scrimViewBackground.getIntrinsicWidth()) / 2,
                            -viewAdditionalOffsets.top + (scrimView.getHeight() + viewAdditionalOffsets.bottom + scrimViewBackground.getIntrinsicHeight()) / 2
                        );
                    } else {
                        scrimViewBackground.setBounds(
                            -viewAdditionalOffsets.left,
                            -viewAdditionalOffsets.top,
                            scrimView.getWidth() + viewAdditionalOffsets.right,
                            scrimView.getHeight() + viewAdditionalOffsets.bottom
                        );
                    }
                    scrimViewBackground.setAlpha((int) (0xFF * dimProgress));
                    scrimViewBackground.draw(canvas);
                }
                if (scrimViewPadding > 0 || scrimViewRoundRadius > 0) {
                    clipPath.rewind();
                    if (scrimView instanceof ScrimView) {
                        ((ScrimView) scrimView).getBounds(bounds);
                    } else {
                        bounds.set(0, 0, getWidth(), getHeight());
                    }
                    AndroidUtilities.rectTmp.set(
                        -viewAdditionalOffsets.left + bounds.left + scrimViewPadding * dimProgress,
                        -viewAdditionalOffsets.top + bounds.top + scrimViewPadding * dimProgress,
                        -viewAdditionalOffsets.left + bounds.right - scrimViewPadding * dimProgress,
                        -viewAdditionalOffsets.top + bounds.bottom - scrimViewPadding * dimProgress
                    );
                    clipPath.addRoundRect(AndroidUtilities.rectTmp, scrimViewRoundRadius * dimProgress, scrimViewRoundRadius * dimProgress, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                }
                if (scrimView instanceof GiftSheet.GiftCell && animateToWidth != 0 && animateToHeight != 0) {
                    if (scrimView.getAlpha() >= 1) {
                        ((GiftSheet.GiftCell) scrimView).customDraw(this, canvas, w, h, dimProgress);
                    } else {
                        canvas.saveLayerAlpha(0, 0, w, h, (int) (0xFF * dimProgress), Canvas.ALL_SAVE_FLAG);
                        final float s = lerp(1.0f, 0.9f, dimProgress);
                        canvas.scale(s, s, w / 2.0f, h / 2.0f);
                        ((GiftSheet.GiftCell) scrimView).customDraw(this, canvas, w, h, dimProgress);
                        canvas.restore();
                    }
                } else {
                    canvas.saveLayerAlpha(0, 0, scrimView.getWidth(), scrimView.getHeight(), (int) (0xFF * dimProgress), Canvas.ALL_SAVE_FLAG);
                    if (scrimView instanceof ScrimView) {
                        ((ScrimView) scrimView).drawScrim(canvas, dimProgress);
                    } else {
                        scrimView.draw(canvas);
                    }
                    canvas.restore();
                }
                canvas.restore();
            }
        }
    }

    public static interface ScrimView {
        default void drawScrim(Canvas canvas, float progress) {
            if (this instanceof View) {
                ((View) this).draw(canvas);
            }
        }
        default void getBounds(RectF bounds) {
            if (this instanceof View) {
                View view = (View) this;
                bounds.set(0, 0, view.getWidth(), view.getHeight());
            }
        }
    }
}

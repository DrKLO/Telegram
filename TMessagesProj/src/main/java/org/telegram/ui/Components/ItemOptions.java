package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.ToggleButton;

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
    private BaseFragment fragment;
    private Theme.ResourcesProvider resourcesProvider;

    private Context context;
    private View scrimView;
    private Drawable scrimViewBackground;
    private int gravity = Gravity.RIGHT;
    private boolean ignoreX;

    private ActionBarPopupWindow actionBarPopupWindow;
    private final float[] point = new float[2];

    private Runnable dismissListener;

    private float translateX, translateY;
    private int dimAlpha;

    private View dimView;
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

        subItem.setColors(Theme.getColor(textColorKey, resourcesProvider), Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

        subItem.setOnClickListener(view1 -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            dismiss();
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

    public ItemOptions addGap() {
        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider);
        gap.setTag(R.id.fit_width_tag, 1);
        addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        return this;
    }

    public ItemOptions addSpaceGap() {
        if (layout == lastLayout) {
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
        addView(lastLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -8, 0, 0));
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

    public ItemOptions setGravity(int gravity) {
        this.gravity = gravity;
        return this;
    }

    public ItemOptions translate(float x, float y) {
        this.translateX += x;
        this.translateY += y;
        return this;
    }

    private int minWidthDp;

    public ItemOptions setMinWidth(int minWidthDp) {
        this.minWidthDp = minWidthDp;
        return this;
    }

    public ItemOptions setDimAlpha(int dimAlpha) {
        this.dimAlpha = dimAlpha;
        return this;
    }

    private boolean forceTop;
    public ItemOptions forceTop(boolean force) {
        forceTop = force;
        return this;
    }

    private int maxHeight;
    public ItemOptions setMaxHeight(int px) {
        this.maxHeight = px;
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

        if (minWidthDp > 0) {
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

        ViewGroup container = this.container == null ? fragment.getParentLayout().getOverlayContainerView() : this.container;

        if (context == null || container == null) {
            return this;
        }

        float x = 0;
        float y = AndroidUtilities.displaySize.y / 2f;
        if (scrimView != null) {
            getPointOnScreen(scrimView, container, point);
            y = point[1];
        }
        if (ignoreX) {
            point[0] = 0;
        }

        if (dimAlpha > 0) {
            View dimViewLocal = dimView = new DimView(context);
            preDrawListener = () -> {
                dimViewLocal.invalidate();
                return true;
            };
            container.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            dimView.setAlpha(0);
            dimView.animate().alpha(1f).setDuration(150);
        }
        layout.measure(View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.AT_MOST));

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
                X = (int) (point[0] + scrimView.getMeasuredWidth() - layout.getMeasuredWidth() + container.getX());
            } else {
                X = (int) (container.getX() + point[0]);
            }
        } else {
            X = (container.getWidth() - layout.getMeasuredWidth()) / 2; // at the center
        }
        int Y;
        if (scrimView != null) {
            if (forceTop || y + layout.getMeasuredHeight() + dp(16) > AndroidUtilities.displaySize.y) {
                // put above scrimView
                y -= scrimView.getMeasuredHeight();
                y -= layout.getMeasuredHeight();
            }
            Y = (int) (y + scrimView.getMeasuredHeight() + container.getY()); // under scrimView
        } else {
            Y = (container.getHeight() - layout.getMeasuredHeight()) / 2; // at the center
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

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    private void dismissDim(ViewGroup container) {
        if (dimView == null) {
            return;
        }
        View dimViewFinal = dimView;
        dimView = null;
        dimViewFinal.animate().cancel();
        dimViewFinal.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.removeFromParent(dimViewFinal);
                container.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            }
        });
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

        private final float clipTop;
        private final int dim;

        public DimView(Context context) {
            super(context);

            if (scrimView != null && scrimView.getParent() instanceof View) {
                clipTop = ((View) scrimView.getParent()).getY() + scrimView.getY();
            } else {
                clipTop = 0;
            }
            dim = ColorUtils.setAlphaComponent(0x00000000, dimAlpha);

            if (scrimView instanceof UserCell && fragment instanceof ProfileActivity) {
                cachedBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                cachedBitmap = Bitmap.createBitmap(scrimView.getWidth() + viewAdditionalOffsets.width(), scrimView.getHeight() + viewAdditionalOffsets.height(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(cachedBitmap);
                canvas.translate(viewAdditionalOffsets.left, viewAdditionalOffsets.top);
                scrimView.draw(canvas);
            } else {
                cachedBitmapPaint = null;
                cachedBitmap = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(dim);

            if (cachedBitmap != null && scrimView.getParent() instanceof View) {
                canvas.save();
                if (clipTop < 1) {
                    canvas.clipRect(-viewAdditionalOffsets.left, -viewAdditionalOffsets.top + point[1] - clipTop + 1, getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom);
                }
                canvas.translate(point[0], point[1]);

                if (scrimViewBackground != null) {
                    scrimViewBackground.setBounds( -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, scrimView.getWidth() + viewAdditionalOffsets.right, scrimView.getHeight() + viewAdditionalOffsets.bottom);
                    scrimViewBackground.draw(canvas);
                }
                canvas.drawBitmap(cachedBitmap, -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, cachedBitmapPaint);
                canvas.restore();
            } else if (scrimView != null && scrimView.getParent() instanceof View) {
                canvas.save();
                if (clipTop < 1) {
                    canvas.clipRect(-viewAdditionalOffsets.left, -viewAdditionalOffsets.top + point[1] - clipTop + 1, getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom);
                }
                canvas.translate(point[0], point[1]);

                if (scrimViewBackground != null) {
                    scrimViewBackground.setBounds( -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, scrimView.getWidth() + viewAdditionalOffsets.right, scrimView.getHeight() + viewAdditionalOffsets.bottom);
                    scrimViewBackground.draw(canvas);
                }
                scrimView.draw(canvas);
                canvas.restore();
            }
        }
    }
}

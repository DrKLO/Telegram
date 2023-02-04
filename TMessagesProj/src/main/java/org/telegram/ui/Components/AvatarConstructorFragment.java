package org.telegram.ui.Components;

import static org.telegram.ui.Components.ImageUpdater.FOR_TYPE_CHANNEL;
import static org.telegram.ui.Components.ImageUpdater.FOR_TYPE_GROUP;
import static org.telegram.ui.Components.ImageUpdater.TYPE_SUGGEST_PHOTO_FOR_USER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.Objects;

public class AvatarConstructorFragment extends BaseFragment {

    public final static float STICKER_DEFAULT_SCALE = 0.7f;
    public final static float STICKER_DEFAULT_ROUND_RADIUS = 0.13f;
    PreviewView previewView;
    private SelectAnimatedEmojiDialog selectAnimatedEmojiDialog;

    int collapsedHeight;
    int expandedHeight;
    View colorPickerPreviewView;
    boolean colorPickerInAnimatoin;
    boolean drawForBlur;
    boolean wasChanged;
    LinearLayout linearLayout;

    boolean forGroup;
    private FrameLayout button;

    float progressToExpand;
    boolean expandWithKeyboard;
    ValueAnimator expandAnimator;

    protected ActionBar overlayActionBar;

    Delegate delegate;
    private BackgroundSelectView backgroundSelectView;
    CanvasButton avatarClickableArea;
    boolean keyboardVisible;

    ValueAnimator keyboardVisibilityAnimator;
    float keyboardVisibleProgress;
    Paint actionBarPaint = new Paint();
    private int gradientBackgroundItemWidth;

    public static final int[][] defaultColors = new int[][]{
            new int[]{0xFF4D8DFF, 0xFF2BBFFF, 0xFF20E2CD, 0xFF0EE1F1},
            new int[]{0xFF5EB6FB, 0xFF1FCEEB, 0xFF45F7B7, 0xFF1FF1D9},
            new int[]{0xFF09D260, 0xFF5EDC40, 0xFFC1E526, 0xFF80DF2B},
            new int[]{0xFFF5694E, 0xFFF5772C, 0xFFFFD412, 0xFFFFA743},
            new int[]{0xFFF64884, 0xFFEF5B41, 0xFFF6A730, 0xFFFF7742},
            new int[]{0xFFF94BA0, 0xFFFB5C80, 0xFFFFB23A, 0xFFFE7E62},
            new int[]{0xFF837CFF, 0xFFB063FF, 0xFFFF72A9, 0xFFE269FF}
    };
    public boolean finishOnDone = true;
    private ActionBarMenuItem setPhotoItem;
    private BottomSheet bottomSheet;
    final ImageUpdater.AvatarFor avatarFor;
    boolean isLandscapeMode;
    private TextView chooseEmojiHint;
    private TextView chooseBackgroundHint;
    ImageUpdater imageUpdater;

    public AvatarConstructorFragment(ImageUpdater imageUpdater, ImageUpdater.AvatarFor avatarFor) {
        this.imageUpdater = imageUpdater;
        this.avatarFor = avatarFor;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        actionBar.setBackgroundDrawable(null);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString("PhotoEditor", R.string.PhotoEditor));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    discardEditor();
                }
            }
        });
        actionBar.getTitleTextView().setAlpha(0);

        overlayActionBar = new ActionBar(getContext());
        overlayActionBar.setCastShadows(false);
        overlayActionBar.setAddToContainer(false);
        overlayActionBar.setOccupyStatusBar(true);
        overlayActionBar.setClipChildren(false);
        int selectorColor = ColorUtils.setAlphaComponent(Color.WHITE, 60);
        overlayActionBar.setItemsColor(Color.WHITE, false);

        overlayActionBar.setBackButtonDrawable(new BackDrawable(false));
        overlayActionBar.setAllowOverlayTitle(false);
        overlayActionBar.setItemsBackgroundColor(selectorColor, false);
        ActionBarMenu menuOverlay = overlayActionBar.createMenu();
        menuOverlay.setClipChildren(false);
        setPhotoItem = menuOverlay.addItem(1, avatarFor != null && avatarFor.type == TYPE_SUGGEST_PHOTO_FOR_USER ?
                LocaleController.getString("SuggestPhoto", R.string.SuggestPhoto) :
                LocaleController.getString("SetPhoto", R.string.SetPhoto)
        );
        setPhotoItem.setBackground(Theme.createSelectorDrawable(selectorColor, Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE));
        overlayActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    discardEditor();
                }
                if (id == 1) {
                    onDonePressed();
                }
            }
        });

        linearLayout = new LinearLayout(getContext()) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == previewView) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };

        ContainerLayout nestedSizeNotifierLayout = new ContainerLayout(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                measureKeyboardHeight();
                boolean isLandscapeModeLocal = MeasureSpec.getSize(widthMeasureSpec) > (MeasureSpec.getSize(heightMeasureSpec) + keyboardHeight);
                if (isLandscapeModeLocal != isLandscapeMode) {
                    isLandscapeMode = isLandscapeModeLocal;
                    AndroidUtilities.removeFromParent(previewView);
                    AndroidUtilities.requestAdjustNothing(getParentActivity(), getClassGuid());
                    if (isLandscapeMode) {
                        setProgressToExpand(0, false);
                        previewView.setExpanded(false);
                        addView(previewView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    } else {
                        linearLayout.addView(previewView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }
                    AndroidUtilities.requestAdjustResize(getParentActivity(), getClassGuid());
                }
                if (isLandscapeMode) {
                    int avatarWidth = (int) (MeasureSpec.getSize(widthMeasureSpec) * 0.45f);
                    int contentWidth = (int) (MeasureSpec.getSize(widthMeasureSpec) * 0.55f);
                    ((MarginLayoutParams) linearLayout.getLayoutParams()).bottomMargin = 0;
                    ((MarginLayoutParams) linearLayout.getLayoutParams()).leftMargin = avatarWidth;
                    ((MarginLayoutParams) previewView.getLayoutParams()).rightMargin = contentWidth;
                    ((MarginLayoutParams) button.getLayoutParams()).rightMargin = contentWidth + AndroidUtilities.dp(16);
                    ((MarginLayoutParams) chooseBackgroundHint.getLayoutParams()).topMargin = 0;
                    ((MarginLayoutParams) chooseEmojiHint.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
                } else {
                    ((MarginLayoutParams) linearLayout.getLayoutParams()).bottomMargin = AndroidUtilities.dp(64);
                    ((MarginLayoutParams) linearLayout.getLayoutParams()).leftMargin = 0;
                    ((MarginLayoutParams) previewView.getLayoutParams()).rightMargin = 0;
                    ((MarginLayoutParams) button.getLayoutParams()).rightMargin = AndroidUtilities.dp(16);
                    ((MarginLayoutParams) chooseBackgroundHint.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
                    ((MarginLayoutParams) chooseEmojiHint.getLayoutParams()).topMargin = AndroidUtilities.dp(18);
                }
                boolean oldKeyboardVisible = keyboardVisible;
                keyboardVisible = keyboardHeight >= AndroidUtilities.dp(20);

                if (oldKeyboardVisible != keyboardVisible) {
                    int newMargin;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (keyboardVisible) {
                        newMargin = -selectAnimatedEmojiDialog.getTop() + actionBar.getMeasuredHeight() + AndroidUtilities.dp(8);
                    } else {
                        newMargin = 0;
                    }
                    linearLayout.setTranslationY(linearLayout.getTranslationY() + ((MarginLayoutParams) linearLayout.getLayoutParams()).topMargin - newMargin);
                    ((MarginLayoutParams) linearLayout.getLayoutParams()).topMargin = newMargin;
                    createKeyboardVisibleAnimator(keyboardVisible);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                collapsedHeight = previewView.getMeasuredHeight();
                expandedHeight = previewView.getMeasuredWidth();
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == overlayActionBar) {
                    return true;
                }
                if (child == actionBar && keyboardVisibleProgress > 0) {
                    actionBarPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    actionBarPaint.setAlpha((int) (255 * keyboardVisibleProgress));
                    canvas.drawRect(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight(), actionBarPaint);
                    getParentLayout().drawHeaderShadow(canvas, (int) (255 * keyboardVisibleProgress), child.getMeasuredHeight());
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                int count = canvas.save();
                super.dispatchDraw(canvas);
                if (!isLandscapeMode) {
                    if (!drawForBlur) {
                        canvas.save();
                        float x = linearLayout.getX() + previewView.getX();
                        float y = linearLayout.getY() + previewView.getY();
                        int additionalH = expandedHeight - collapsedHeight;
                        int yKeyboardVisible = AndroidUtilities.statusBarHeight + ((ActionBar.getCurrentActionBarHeight() - collapsedHeight) >> 1);
                        y = AndroidUtilities.lerp(y, yKeyboardVisible, keyboardVisibleProgress);
                        canvas.translate(x, y);
                        previewView.draw(canvas);
                        AndroidUtilities.rectTmp.set(x, y - additionalH / 2f * progressToExpand, x + previewView.getMeasuredWidth(), y + previewView.getMeasuredHeight() + additionalH / 2f * progressToExpand);
                        float cx = x + previewView.cx;
                        float cy = y + previewView.cy;
                        avatarClickableArea.setRect((int) (cx - previewView.size), (int) (cy - previewView.size), (int) (cx + previewView.size), (int) (cy + previewView.size));
                        canvas.restore();
                    }
                    canvas.restoreToCount(count);


                    float alpha = previewView.expandProgress.get() * (1f - (colorPickerPreviewView.getVisibility() == View.VISIBLE ? colorPickerPreviewView.getAlpha() : 0));
                    if (alpha != 0) {
                        overlayActionBar.setVisibility(View.VISIBLE);
                        count = canvas.save();
                        canvas.translate(overlayActionBar.getX(), overlayActionBar.getY());

                        if (alpha != 1) {
                            canvas.saveLayerAlpha(0, 0, overlayActionBar.getMeasuredWidth(), overlayActionBar.getMeasuredHeight(), (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                        }
                        overlayActionBar.draw(canvas);
                        canvas.restoreToCount(count);
                    } else {
                        overlayActionBar.setVisibility(View.GONE);
                    }
                }
                if (colorPickerInAnimatoin) {
                    invalidate();
                }
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (keyboardVisibleProgress == 0 && AndroidUtilities.findClickableView(this, ev.getX(), ev.getY())) {
                    return false;
                }
                return onTouchEvent(ev);
            }

            boolean maybeScroll;
            boolean isScrolling;
            float startFromProgressToExpand;
            float scrollFromX, scrollFromY;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (avatarClickableArea.checkTouchEvent(event)) {
                    return true;
                }

                if (!isLandscapeMode) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        selectAnimatedEmojiDialog.getHitRect(AndroidUtilities.rectTmp2);
                        AndroidUtilities.rectTmp2.offset(0, (int) linearLayout.getY());
                        if (keyboardVisibleProgress == 0 && !AndroidUtilities.rectTmp2.contains((int) event.getX(), (int) event.getY())) {
                            maybeScroll = true;
                            scrollFromX = event.getX();
                            scrollFromY = event.getY();
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE && (maybeScroll || isScrolling)) {
                        if (maybeScroll) {
                            if (Math.abs(scrollFromY - event.getY()) > AndroidUtilities.touchSlop) {
                                maybeScroll = false;
                                isScrolling = true;
                                startFromProgressToExpand = progressToExpand;
                                scrollFromX = event.getX();
                                scrollFromY = event.getY();
                            }
                        } else {
                            float dy = scrollFromY - event.getY();
                            float progressToExpand = startFromProgressToExpand + (-dy / (float) expandedHeight);
                            progressToExpand = Utilities.clamp(progressToExpand, 1f, 0f);
                            setProgressToExpand(progressToExpand, true);
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (isScrolling) {
                            setExpanded(progressToExpand > 0.5f, false, false);
                        }
                        maybeScroll = false;
                        isScrolling = false;
                    }
                }
                return isScrolling || super.onTouchEvent(event) || maybeScroll;
            }
        };
        nestedSizeNotifierLayout.setFitsSystemWindows(true);
        nestedSizeNotifierLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));


        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        linearLayout.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(previewView = new PreviewView(getContext()) {
            @Override
            public void invalidate() {
                super.invalidate();
                nestedSizeNotifierLayout.invalidate();
            }
        });

        chooseBackgroundHint = new TextView(getContext());
        chooseBackgroundHint.setText(LocaleController.getString("ChooseBackground", R.string.ChooseBackground));
        chooseBackgroundHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        chooseBackgroundHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        chooseBackgroundHint.setGravity(Gravity.CENTER);
        linearLayout.addView(chooseBackgroundHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 10, 21, 10));

        FrameLayout backgroundContainer = new FrameLayout(getContext()) {

            private Path path = new Path();
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(Canvas canvas) {
                Theme.applyDefaultShadow(paint);
                paint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, getResourceProvider()));
                paint.setAlpha((int) (255 * getAlpha()));

                AndroidUtilities.rectTmp.set(
                        0, 0, getMeasuredWidth(), getMeasuredHeight()
                );
                path.rewind();
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
                canvas.drawPath(path, paint);
                super.dispatchDraw(canvas);
            }
        };
        backgroundContainer.addView(backgroundSelectView = new BackgroundSelectView(getContext()));
        linearLayout.addView(backgroundContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 12, 0));

        chooseEmojiHint = new TextView(getContext());
        chooseEmojiHint.setText(LocaleController.getString("ChooseEmojiOrSticker", R.string.ChooseEmojiOrSticker));
        chooseEmojiHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        chooseEmojiHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        chooseEmojiHint.setGravity(Gravity.CENTER);
        linearLayout.addView(chooseEmojiHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 18, 21, 10));

        selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(this, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_AVATAR_CONSTRUCTOR, null) {

            private boolean firstLayout = true;

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (firstLayout) {
                    firstLayout = false;
                    selectAnimatedEmojiDialog.onShow(null);
                }
            }

            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, Integer until) {
                long docId = documentId == null ? 0 : documentId;
                previewView.documentId = docId;
                previewView.document = document;
                if (docId == 0) {
                    previewView.backupImageView.setAnimatedEmojiDrawable(null);
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    previewView.backupImageView.getImageReceiver().setImage(ImageLocation.getForDocument(document), "100_100", null, null, svgThumb, 0, "tgs", document, 0);
                } else {
                    previewView.backupImageView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_AVATAR_CONSTRUCTOR_PREVIEW, currentAccount, docId));
                    previewView.backupImageView.getImageReceiver().clearImage();
                }
                if (previewView.getImageReceiver() != null && previewView.getImageReceiver().getAnimation() != null) {
                    previewView.getImageReceiver().getAnimation().seekTo(0, true);
                }
                if (previewView.getImageReceiver() != null && previewView.getImageReceiver().getLottieAnimation() != null) {
                    previewView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false, true);
                }
                wasChanged = true;
            }
        };
        selectAnimatedEmojiDialog.forUser = !forGroup;

        selectAnimatedEmojiDialog.setAnimationsEnabled(fragmentBeginToShow);
        selectAnimatedEmojiDialog.setClipChildren(false);
        linearLayout.addView(selectAnimatedEmojiDialog, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 12, 0, 12, 12));

        linearLayout.setClipChildren(false);
        nestedSizeNotifierLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0, 64));

        colorPickerPreviewView = new View(getContext());
        colorPickerPreviewView.setVisibility(View.GONE);


        button = new FrameLayout(getContext());
        button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 8));

        TextView textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        if (imageUpdater.setForType == FOR_TYPE_CHANNEL) {
            textView.setText(LocaleController.getString("SetChannelPhoto", R.string.SetChannelPhoto));
        } else if (imageUpdater.setForType == FOR_TYPE_GROUP) {
            textView.setText(LocaleController.getString("SetGroupPhoto", R.string.SetGroupPhoto));
        } else if (avatarFor != null && avatarFor.type == TYPE_SUGGEST_PHOTO_FOR_USER) {
            textView.setText(LocaleController.getString("SuggestPhoto", R.string.SuggestPhoto));
        } else {
            textView.setText(LocaleController.getString("SetProfilePhotoAvatarConstructor", R.string.SetProfilePhotoAvatarConstructor));
        }
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        button.setOnClickListener(v -> onDonePressed());

        nestedSizeNotifierLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16));
        nestedSizeNotifierLayout.addView(actionBar);

        nestedSizeNotifierLayout.addView(overlayActionBar);
        nestedSizeNotifierLayout.addView(colorPickerPreviewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        avatarClickableArea = new CanvasButton(nestedSizeNotifierLayout);
        avatarClickableArea.setDelegate(() -> {
            onPreviewClick();
        });
        fragmentView = nestedSizeNotifierLayout;
        return fragmentView;
    }

    private void discardEditor() {
        if (getParentActivity() == null) {
            return;
        }
        if (wasChanged) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.getString("PhotoEditorDiscardAlert", R.string.PhotoEditorDiscardAlert));
            builder.setTitle(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
            builder.setPositiveButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialogInterface, i) -> finishFragment());
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            dialog.redPositive();
        } else {
            finishFragment();
        }
    }

    private void createKeyboardVisibleAnimator(boolean keyboardVisible) {
        if (isLandscapeMode) {
            return;
        }
        keyboardVisibilityAnimator = ValueAnimator.ofFloat(keyboardVisibleProgress, keyboardVisible ? 1f : 0f);
        float offsetY = (expandedHeight - collapsedHeight - AndroidUtilities.statusBarHeight) * progressToExpand;
        float translationYFrom, translationYTo;
        if (keyboardVisible) {
            previewView.setExpanded(false);
            translationYFrom = linearLayout.getTranslationY();
            translationYTo = 0;
        } else {
            translationYFrom = offsetY;
            translationYTo = linearLayout.getTranslationY();
        }
        if (expandWithKeyboard && !keyboardVisible) {
            previewView.setExpanded(true);
        } else {
            expandWithKeyboard = false;
        }
        keyboardVisibilityAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                keyboardVisibleProgress = (float) animation.getAnimatedValue();
                float offset = AndroidUtilities.lerp(translationYFrom, translationYTo, keyboardVisibleProgress);
                actionBar.getTitleTextView().setAlpha(keyboardVisibleProgress);
                if (expandWithKeyboard && !keyboardVisible) {
                    setProgressToExpand(1f - keyboardVisibleProgress, false);
                }
                linearLayout.setTranslationY(offset);
                button.setTranslationY(offset);
                fragmentView.invalidate();
                actionBar.invalidate();
            }
        });
        keyboardVisibilityAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setProgressToExpand(expandWithKeyboard ? 1f : 0f, false);
                expandWithKeyboard = false;
            }
        });
        keyboardVisibilityAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
        keyboardVisibilityAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
        keyboardVisibilityAnimator.start();
    }

    private void onDonePressed() {
        if (previewView.getImageReceiver() == null || !previewView.getImageReceiver().hasImageLoaded()) {
            return;
        }
        if (delegate != null) {
            delegate.onDone(previewView.backgroundGradient, previewView.documentId, previewView.document, previewView);
        }
        if (finishOnDone) {
            finishFragment();
        }
    }


    private void setExpanded(boolean expanded, boolean fromClick, boolean withColorPicker) {
        if (isLandscapeMode) {
            return;
        }
//        if (this.expanded != expanded) {
//            this.expanded = expanded;
        cancelExpandAnimator();
        expandAnimator = ValueAnimator.ofFloat(progressToExpand, expanded ? 1f : 0f);
        if (fromClick) {
            previewView.overrideExpandProgress = progressToExpand;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }
        }
        expandAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setProgressToExpand(progress, false);
            if (fromClick) {
                previewView.overrideExpandProgress = progress;
                previewView.invalidate();
            }
        });
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                expandAnimator = null;
                setProgressToExpand(expanded ? 1f : 0f, false);
                if (fromClick) {
                    previewView.overrideExpandProgress = -1;
                    previewView.setExpanded(expanded);
                }

            }
        });
        if (withColorPicker) {
            expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            expandAnimator.setDuration(350);
            expandAnimator.setStartDelay(150);
        } else {
            expandAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            expandAnimator.setDuration(250);
        }
        expandAnimator.start();
        //  }
    }

    void cancelExpandAnimator() {
        if (expandAnimator != null) {
            expandAnimator.removeAllListeners();
            expandAnimator.cancel();
            expandAnimator = null;
        }
    }

    private void setProgressToExpand(float progressToExpand, boolean fromScroll) {
        this.progressToExpand = progressToExpand;

        float offsetY = (expandedHeight - collapsedHeight - AndroidUtilities.statusBarHeight) * progressToExpand;
        if (keyboardVisibleProgress == 0) {
            linearLayout.setTranslationY(offsetY);
            button.setTranslationY(offsetY);
        }
        previewView.setTranslationY(-(expandedHeight - collapsedHeight) / 2f * progressToExpand);
        fragmentView.invalidate();
        if (fromScroll) {
            previewView.setExpanded(progressToExpand > 0.5f);
        }
    }

    public void startFrom(AvatarConstructorPreviewCell previewCell) {
        BackgroundGradient gradient = previewCell.getBackgroundGradient();
        previewView.setGradient(gradient);
        if (previewCell.getAnimatedEmoji() != null) {
            long docId = previewCell.getAnimatedEmoji().getDocumentId();
            previewView.documentId = docId;
            previewView.backupImageView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_AVATAR_CONSTRUCTOR_PREVIEW, currentAccount, docId));
        }
        backgroundSelectView.selectGradient(gradient);
        selectAnimatedEmojiDialog.setForUser(previewCell.forUser);
    }

    public class PreviewView extends FrameLayout {

        public long documentId;
        public TLRPC.Document document;
        BackupImageView backupImageView;
        GradientTools gradientTools = new GradientTools();
        GradientTools outGradientTools = new GradientTools();
        float changeBackgroundProgress = 1f;
        BackgroundGradient backgroundGradient;

        AnimatedFloat expandProgress = new AnimatedFloat(this, 200, CubicBezierInterpolator.EASE_OUT);
        boolean expanded;
        float overrideExpandProgress = -1f;
        private float size;
        private float cx, cy;

        public PreviewView(Context context) {
            super(context);
            backupImageView = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    PreviewView.this.invalidate();
                }

                @Override
                public void invalidate(Rect dirty) {
                    super.invalidate(dirty);
                    PreviewView.this.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    PreviewView.this.invalidate();
                }
            };
            backupImageView.getImageReceiver().setAutoRepeatCount(1);
            backupImageView.getImageReceiver().setAspectFit(true);
            setClipChildren(false);
            addView(backupImageView, LayoutHelper.createFrame(70, 70, Gravity.CENTER));
        }

        public void setExpanded(boolean expanded) {
            if (this.expanded == expanded) {
                return;
            }
            this.expanded = expanded;
            if (expanded) {
                if (backupImageView.animatedEmojiDrawable != null && backupImageView.animatedEmojiDrawable.getImageReceiver() != null) {
                    backupImageView.animatedEmojiDrawable.getImageReceiver().startAnimation();
                }
                backupImageView.imageReceiver.startAnimation();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (isLandscapeMode) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            } else {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(140), MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            cx = getMeasuredWidth() / 2f;
            cy = getMeasuredHeight() / 2f;
            float radius = isLandscapeMode ? getMeasuredWidth() * 0.3f : AndroidUtilities.dp(50);
            expandProgress.set(expanded ? 1f : 0f);
            if (overrideExpandProgress >= 0) {
                expandProgress.set(overrideExpandProgress, true);
            }
            size = AndroidUtilities.lerp(radius, getMeasuredWidth() / 2f, expandProgress.get());
            size = AndroidUtilities.lerp(size, AndroidUtilities.dp(21), keyboardVisibleProgress);
            cx = AndroidUtilities.lerp(cx, getMeasuredWidth() - AndroidUtilities.dp(12) - AndroidUtilities.dp(21), keyboardVisibleProgress);


            canvas.save();
            int additionalH = expandedHeight - collapsedHeight;
            canvas.clipRect(0, -additionalH / 2f, getMeasuredWidth(), getMeasuredHeight() + additionalH / 2f * progressToExpand);
            if (backgroundGradient != null) {
                gradientTools.setColors(backgroundGradient.color1, backgroundGradient.color2, backgroundGradient.color3, backgroundGradient.color4);
                gradientTools.setBounds(cx - size, cy - size, cx + size, cy + size);
                if (changeBackgroundProgress != 1f) {
                    outGradientTools.setBounds(cx - size, cy - size, cx + size, cy + size);
                    outGradientTools.paint.setAlpha(255);
                    drawBackround(canvas, cx, cy, radius, size, outGradientTools.paint);
                    gradientTools.paint.setAlpha((int) (255 * changeBackgroundProgress));
                    drawBackround(canvas, cx, cy, radius, size, gradientTools.paint);
                    changeBackgroundProgress += 16 / 250f;
                    if (changeBackgroundProgress > 1f) {
                        changeBackgroundProgress = 1f;
                    }
                    invalidate();
                } else {
                    gradientTools.paint.setAlpha(255);
                    drawBackround(canvas, cx, cy, radius, size, gradientTools.paint);
                }
            }
            int imageHeight = isLandscapeMode ? (int) (radius * 2 * STICKER_DEFAULT_SCALE) : AndroidUtilities.dp(70);
            int imageHeightExpanded = (int) (getMeasuredWidth() * STICKER_DEFAULT_SCALE);
            int imageHeightKeyboardVisible = (int) (AndroidUtilities.dp(42) * STICKER_DEFAULT_SCALE);
            float imageSize = AndroidUtilities.lerp(imageHeight, imageHeightExpanded, expandProgress.get());
            imageSize = AndroidUtilities.lerp(imageSize, imageHeightKeyboardVisible, keyboardVisibleProgress);
            imageSize /= 2;
            if (backupImageView.animatedEmojiDrawable != null) {
                if (backupImageView.animatedEmojiDrawable.getImageReceiver() != null) {
                    backupImageView.animatedEmojiDrawable.getImageReceiver().setRoundRadius((int) (imageSize * 2 * STICKER_DEFAULT_ROUND_RADIUS));
                }
                backupImageView.animatedEmojiDrawable.setBounds((int) (cx - imageSize), (int) (cy - imageSize), (int) (cx + imageSize), (int) (cy + imageSize));
                backupImageView.animatedEmojiDrawable.draw(canvas);

            } else {
                backupImageView.imageReceiver.setImageCoords(cx - imageSize, cy - imageSize, imageSize * 2, imageSize * 2);
                backupImageView.imageReceiver.setRoundRadius((int) (imageSize * 2 * STICKER_DEFAULT_ROUND_RADIUS));
                backupImageView.imageReceiver.draw(canvas);
            }
        }

        private void drawBackround(Canvas canvas, float cx, float cy, float radius, float size, Paint paint) {
            float p = expandProgress.get();
            if (p == 0) {
                canvas.drawCircle(cx, cy, size, paint);
            } else {
                float roundRadius = AndroidUtilities.lerp(radius, 0, p);
                AndroidUtilities.rectTmp.set(cx - size, cy - size, cx + size, cy + size);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, roundRadius, roundRadius, paint);
            }
        }

        public void setGradient(BackgroundGradient backgroundGradient) {
            if (this.backgroundGradient != null) {
                outGradientTools.setColors(this.backgroundGradient.color1, this.backgroundGradient.color2, this.backgroundGradient.color3, this.backgroundGradient.color4);
                changeBackgroundProgress = 0f;
                wasChanged = true;
            }
            this.backgroundGradient = backgroundGradient;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }
            invalidate();
        }

        public long getDuration() {
            ImageReceiver imageReceiver = backupImageView.getImageReceiver();
            if (backupImageView.animatedEmojiDrawable != null) {
                imageReceiver = backupImageView.animatedEmojiDrawable.getImageReceiver();
            }
            if (imageReceiver == null) {
                return 5000;
            }
            if (imageReceiver.getLottieAnimation() != null) {
                return imageReceiver.getLottieAnimation().getDuration();
            }
            return 5000;
        }

        public ImageReceiver getImageReceiver() {
            ImageReceiver imageReceiver = backupImageView.getImageReceiver();
            if (backupImageView.animatedEmojiDrawable != null) {
                imageReceiver = backupImageView.animatedEmojiDrawable.getImageReceiver();
            }
            return imageReceiver;
        }

        public boolean hasAnimation() {
            return getImageReceiver().getAnimation() != null || getImageReceiver().getLottieAnimation() != null;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            fragmentView.invalidate();
        }
    }

    private class BackgroundSelectView extends RecyclerListView {

        ArrayList<BackgroundGradient> gradients = new ArrayList<>();

        int stableIdPointer = 200;

        int selectedItemId = -1;

        Adapter adapter;
        BackgroundGradient customSelectedGradient;

        public BackgroundSelectView(Context context) {
            super(context);
            LinearLayoutManager layoutManager = new LinearLayoutManager(context);
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            setLayoutManager(layoutManager);
            for (int i = 0; i < defaultColors.length; i++) {
                BackgroundGradient backgroundGradient = new BackgroundGradient();
                backgroundGradient.stableId = stableIdPointer++;
                backgroundGradient.color1 = defaultColors[i][0];
                backgroundGradient.color2 = defaultColors[i][1];
                backgroundGradient.color3 = defaultColors[i][2];
                backgroundGradient.color4 = defaultColors[i][3];
                gradients.add(backgroundGradient);
            }
            setOnItemClickListener((view, position) -> {
                if (view instanceof GradientSelectorView && !((GradientSelectorView) view).isCustom) {
                    selectedItemId = ((GradientSelectorView) view).backgroundGradient.stableId;
                    previewView.setGradient(((GradientSelectorView) view).backgroundGradient);
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                } else {
                    if (selectedItemId != 1 && customSelectedGradient != null) {
                        selectedItemId = 1;
                        previewView.setGradient(customSelectedGradient);
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    } else {
                        showColorPicker();
                    }
                }
            });
            setAdapter(adapter = new Adapter() {

                private final static int VIEW_TYPE_GRADIENT = 0;
                private final static int VIEW_TYPE_ADD_CUSTOM = 1;

                @NonNull
                @Override
                public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view;
                    switch (viewType) {
                        case VIEW_TYPE_ADD_CUSTOM:
                        case VIEW_TYPE_GRADIENT:
                        default:
                            view = new GradientSelectorView(getContext());
                            break;
                    }
                    return new Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                    if (holder.getItemViewType() == VIEW_TYPE_GRADIENT) {
                        GradientSelectorView gradientSelectorView = (GradientSelectorView) holder.itemView;
                        gradientSelectorView.setGradient(gradients.get(position));
                        gradientSelectorView.setSelectedInternal(selectedItemId == gradients.get(position).stableId, true);
                    } else {
                        GradientSelectorView gradientSelectorView = (GradientSelectorView) holder.itemView;
                        gradientSelectorView.setCustom(true);
                        gradientSelectorView.setGradient(customSelectedGradient);
                        gradientSelectorView.setSelectedInternal(selectedItemId == 1, true);
                    }
                }

                @Override
                public int getItemCount() {
                    return gradients.size() + 1;
                }

                @Override
                public long getItemId(int position) {
                    if (position >= gradients.size()) {
                        return 1;
                    }
                    return gradients.get(position).stableId;
                }

                @Override
                public int getItemViewType(int position) {
                    if (position >= gradients.size()) {
                        return VIEW_TYPE_ADD_CUSTOM;
                    }
                    return VIEW_TYPE_GRADIENT;
                }
            });
            setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int availableWidth = MeasureSpec.getSize(widthSpec);
            int itemsCount = adapter.getItemCount();
            gradientBackgroundItemWidth = availableWidth / itemsCount;
            if (gradientBackgroundItemWidth < AndroidUtilities.dp(36)) {
                gradientBackgroundItemWidth = AndroidUtilities.dp(36);
            } else if (gradientBackgroundItemWidth > AndroidUtilities.dp(150)) {
                gradientBackgroundItemWidth = AndroidUtilities.dp(48);
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        public void selectGradient(BackgroundGradient gradient) {
            boolean isDefault = false;
            for (int i = 0; i < gradients.size(); i++) {
                if (gradients.get(i).equals(gradient)) {
                    selectedItemId = gradients.get(i).stableId;
                    isDefault = true;
                    break;
                }
            }
            if (!isDefault) {
                customSelectedGradient = gradient;
                selectedItemId = 1;
            }
            adapter.notifyDataSetChanged();
        }
    }

    BackgroundGradient colorPickerGradient;

    private void showColorPicker() {
        if (bottomSheet != null) {
            return;
        }
        if (!previewView.expanded) {
            setExpanded(true, true, true);
        }

        BackgroundGradient prevGradient = null;
        if (previewView.backgroundGradient != null) {
            prevGradient = previewView.backgroundGradient;
        }
        boolean[] onDoneButtonPressed = new boolean[]{false};
        BackgroundGradient finalPrevGradient = prevGradient;
        AndroidUtilities.requestAdjustNothing(getParentActivity(), getClassGuid());
        bottomSheet = new BottomSheet(getContext(), true) {
            @Override
            public void dismiss() {
                super.dismiss();
                backgroundSelectView.selectGradient(colorPickerGradient);
                colorPickerInAnimatoin = true;
                fragmentView.invalidate();
                colorPickerPreviewView.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        colorPickerInAnimatoin = false;
                        colorPickerPreviewView.setVisibility(View.GONE);
                    }
                }).alpha(0f).setDuration(200).start();
            }

            @Override
            public void dismissInternal() {
                super.dismissInternal();
                AndroidUtilities.requestAdjustResize(getParentActivity(), getClassGuid());
                bottomSheet = null;
            }
        };
        bottomSheet.fixNavigationBar();
        bottomSheet.pauseAllHeavyOperations = false;

        drawForBlur = true;
        colorPickerPreviewView.setBackground(new BitmapDrawable(getContext().getResources(), AndroidUtilities.makeBlurBitmap(fragmentView, 12f, 10)));
        drawForBlur = false;
        colorPickerPreviewView.setVisibility(View.VISIBLE);
        colorPickerPreviewView.setAlpha(0);
        colorPickerInAnimatoin = true;
        fragmentView.invalidate();
        colorPickerPreviewView.animate().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                colorPickerInAnimatoin = false;
            }
        }).alpha(1f).setDuration(200).start();

        colorPickerGradient = new BackgroundGradient();
        ColorPicker colorPicker = new ColorPicker(getContext(), false, (color, num, applyNow) -> {
            switch (num) {
                case 0:
                    if (colorPickerGradient.color1 != color && (colorPickerGradient.color1 == 0 || color == 0)) {
                        colorPickerGradient = colorPickerGradient.copy();
                        previewView.setGradient(colorPickerGradient);
                    }
                    colorPickerGradient.color1 = color;
                    break;
                case 1:
                    if (colorPickerGradient.color2 != color && (colorPickerGradient.color2 == 0 || color == 0)) {
                        colorPickerGradient = colorPickerGradient.copy();
                        previewView.setGradient(colorPickerGradient);
                    }
                    colorPickerGradient.color2 = color;
                    break;
                case 2:
                    if (colorPickerGradient.color3 != color && (colorPickerGradient.color3 == 0 || color == 0)) {
                        colorPickerGradient = colorPickerGradient.copy();
                        previewView.setGradient(colorPickerGradient);
                    }
                    colorPickerGradient.color3 = color;
                    break;
                case 3:
                    if (colorPickerGradient.color4 != color && (colorPickerGradient.color4 == 0 || color == 0)) {
                        colorPickerGradient = colorPickerGradient.copy();
                        previewView.setGradient(colorPickerGradient);
                    }
                    colorPickerGradient.color4 = color;
                    break;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }
            previewView.invalidate();
        }) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
            }
        };

        if (previewView.backgroundGradient != null) {
            colorPicker.setColor(colorPickerGradient.color4 = previewView.backgroundGradient.color4, 3);
            colorPicker.setColor(colorPickerGradient.color3 = previewView.backgroundGradient.color3, 2);
            colorPicker.setColor(colorPickerGradient.color2 = previewView.backgroundGradient.color2, 1);
            colorPicker.setColor(colorPickerGradient.color1 = previewView.backgroundGradient.color1, 0);
        }

        colorPicker.setType(-1, true, 4, colorPickerGradient.colorsCount(), false, 0, false);

        previewView.setGradient(colorPickerGradient);

        LinearLayout colorPickerContainer = new LinearLayout(getContext());
        colorPickerContainer.setOrientation(LinearLayout.VERTICAL);
        colorPickerContainer.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        colorPickerContainer.addView(colorPicker);

        FrameLayout button = new FrameLayout(getContext());
        button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 8));

        TextView textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(LocaleController.getString("SetColor", R.string.SetColor));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        colorPickerContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, -8, 16, 16));
        button.setOnClickListener(v -> {
            onDoneButtonPressed[0] = true;
            backgroundSelectView.selectGradient(colorPickerGradient);
            bottomSheet.dismiss();
        });
        bottomSheet.setCustomView(colorPickerContainer);
        bottomSheet.smoothKeyboardAnimationEnabled = true;
        bottomSheet.setDimBehind(false);
        bottomSheet.show();
        isLightStatusBar();
    }

    public static class BackgroundGradient {

        public int stableId;

        int color1;
        int color2;
        int color3;
        int color4;

        public BackgroundGradient copy() {
            BackgroundGradient backgroundGradient = new BackgroundGradient();
            backgroundGradient.color1 = color1;
            backgroundGradient.color2 = color2;
            backgroundGradient.color3 = color3;
            backgroundGradient.color4 = color4;
            return backgroundGradient;
        }

        public int colorsCount() {
            if (color4 != 0) {
                return 4;
            }
            if (color3 != 0) {
                return 3;
            }
            if (color2 != 0) {
                return 2;
            }
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BackgroundGradient)) return false;
            BackgroundGradient that = (BackgroundGradient) o;
            return color1 == that.color1 && color2 == that.color2 && color3 == that.color3 && color4 == that.color4;
        }

        @Override
        public int hashCode() {
            return Objects.hash(stableId, color1, color2, color3, color4);
        }

        public int getAverageColor() {
            int color = color1;
            if (color2 != 0) {
                color = ColorUtils.blendARGB(color, color2, 0.5f);
            }
            if (color3 != 0) {
                color = ColorUtils.blendARGB(color, color3, 0.5f);
            }
            if (color4 != 0) {
                color = ColorUtils.blendARGB(color, color4, 0.5f);
            }
            return color;
        }
    }

    private class GradientSelectorView extends View {

        BackgroundGradient backgroundGradient;

        AnimatedFloat progressToSelect = new AnimatedFloat(400, AndroidUtilities.overshootInterpolator);
        boolean selected;
        boolean isCustom;

        GradientTools gradientTools = new GradientTools();
        Drawable addIcon;
        Paint optionsPaint;
        Paint defaultPaint;

        public GradientSelectorView(Context context) {
            super(context);
            progressToSelect.setParent(this);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(gradientBackgroundItemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            progressToSelect.set(selected ? 1f : 0, false);
            float cx = getMeasuredWidth() / 2f;
            float cy = getMeasuredHeight() / 2f;

            Paint paint;
            if (backgroundGradient != null) {
                gradientTools.setColors(backgroundGradient.color1, backgroundGradient.color2, backgroundGradient.color3, backgroundGradient.color4);
                gradientTools.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                paint = gradientTools.paint;
            } else {
                if (defaultPaint == null) {
                    defaultPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    defaultPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
                }
                paint = defaultPaint;
            }
            if (progressToSelect.get() == 0) {
                canvas.drawCircle(cx, cy, AndroidUtilities.dp(15), paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2));
                canvas.drawCircle(cx, cy, AndroidUtilities.dpf2(13.5f), paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, AndroidUtilities.dp(10) + AndroidUtilities.dp(5) * (1f - progressToSelect.get()), paint);
            }

            if (isCustom) {
                if (backgroundGradient == null) {
                    if (addIcon == null) {
                        addIcon = ContextCompat.getDrawable(getContext(), R.drawable.msg_filled_plus);
                        addIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiSearchIcon), PorterDuff.Mode.MULTIPLY));
                    }
                    addIcon.setBounds((int) (cx - addIcon.getIntrinsicWidth() / 2f), (int) (cy - addIcon.getIntrinsicHeight() / 2f),
                            (int) (cx + addIcon.getIntrinsicWidth() / 2f), (int) (cy + addIcon.getIntrinsicHeight() / 2f));
                    addIcon.draw(canvas);
                } else {
                    if (optionsPaint == null) {
                        optionsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        optionsPaint.setColor(0xffffffff);
                    }
                    optionsPaint.setAlpha(Math.round(255f * Utilities.clamp(progressToSelect.get(), 1f, 0f)));
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(1.5f), optionsPaint);
                    canvas.drawCircle(cx - AndroidUtilities.dp(5) * progressToSelect.get(), cy, AndroidUtilities.dp(1.5f), optionsPaint);
                    canvas.drawCircle(cx + AndroidUtilities.dp(5) * progressToSelect.get(), cy, AndroidUtilities.dp(1.5f), optionsPaint);

                }
            }
        }

        void setGradient(BackgroundGradient backgroundGradient) {
            this.backgroundGradient = backgroundGradient;
        }

        void setSelectedInternal(boolean selected, boolean animated) {
            if (this.selected != selected) {
                this.selected = selected;
                invalidate();
            }
            if (!animated) {
                progressToSelect.set(selected ? 1f : 0, false);
            }
        }

        public void setCustom(boolean b) {
            isCustom = b;
        }
    }

    boolean isLightInternal = false;
    float progressToLightStatusBar = 0f;
    ValueAnimator lightProgressAnimator;

    @Override
    public boolean isLightStatusBar() {
        boolean isLight;
        if (previewView != null && (previewView.expanded || previewView.overrideExpandProgress >= 0 && previewView.backgroundGradient != null)) {
            int averageColor = previewView.backgroundGradient.getAverageColor();
            isLight = AndroidUtilities.computePerceivedBrightness(averageColor) > 0.721f;
        } else {
            isLight = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundGray)) > 0.721f;
        }
        if (isLightInternal != isLight) {
            isLightInternal = isLight;
            if (actionBar.getAlpha() == 0) {
                setProgressToLightStatusBar(isLight ? 0f : 1f);
            } else {
                if (lightProgressAnimator != null) {
                    lightProgressAnimator.removeAllListeners();
                    lightProgressAnimator.cancel();
                }
                lightProgressAnimator = ValueAnimator.ofFloat(progressToLightStatusBar, isLight ? 0f : 1f);
                lightProgressAnimator.addUpdateListener(animation -> {
                    setProgressToLightStatusBar((Float) animation.getAnimatedValue());
                });
                lightProgressAnimator.setDuration(150).start();
            }
        }
        if (bottomSheet != null) {
            AndroidUtilities.setLightStatusBar(bottomSheet.getWindow(), isLight);
        }
        return isLight;
    }

    private void setProgressToLightStatusBar(float value) {
        if (progressToLightStatusBar != value) {
            progressToLightStatusBar = value;
            int color = ColorUtils.blendARGB(Color.BLACK, Color.WHITE, progressToLightStatusBar);
            int selectorColor = ColorUtils.setAlphaComponent(color, 60);
            overlayActionBar.setItemsColor(color, false);
            setPhotoItem.setBackground(Theme.createSelectorDrawable(selectorColor, Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE));
        }
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void onPreviewClick() {
        if (isLandscapeMode) {
            return;
        }
        if (keyboardVisibleProgress > 0) {
            if (keyboardVisibilityAnimator != null) {
                progressToExpand = 1f;
                expandWithKeyboard = true;
            }
            AndroidUtilities.hideKeyboard(fragmentView);
            return;
        }
        setExpanded(!previewView.expanded, true, false);
    }

    private class ContainerLayout extends SizeNotifierFrameLayout implements NestedScrollingParent {

        private NestedScrollingParentHelper nestedScrollingParentHelper;

        public ContainerLayout(Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
            if (keyboardVisibleProgress > 0 || isLandscapeMode) {
                return false;
            }
            return true;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
            cancelExpandAnimator();
        }

        @Override
        public void onStopNestedScroll(View target) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
            setExpanded(progressToExpand > 0.5f, false, false);
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
            if (keyboardVisibleProgress > 0 || isLandscapeMode) {
                return;
            }
            if (dyUnconsumed != 0) {
                cancelExpandAnimator();
                float progressToExpand = AvatarConstructorFragment.this.progressToExpand - dyUnconsumed / (float) expandedHeight;
                progressToExpand = Utilities.clamp(progressToExpand, 1f, 0f);
                setProgressToExpand(progressToExpand, true);
            }
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
            if (keyboardVisibleProgress > 0 || isLandscapeMode) {
                return;
            }
            if (dy > 0 && AvatarConstructorFragment.this.progressToExpand > 0) {
                cancelExpandAnimator();
                float progressToExpand = AvatarConstructorFragment.this.progressToExpand - dy / (float) expandedHeight;
                progressToExpand = Utilities.clamp(progressToExpand, 1f, 0f);
                setProgressToExpand(progressToExpand, true);
                consumed[1] = dy;
            }
        }

        @Override
        public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
            return false;
        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            return false;
        }

        @Override
        public int getNestedScrollAxes() {
            return nestedScrollingParentHelper.getNestedScrollAxes();
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onBackPressed() {
        discardEditor();
        return false;
    }

    public interface Delegate {
        void onDone(BackgroundGradient backgroundGradient, long documentId, TLRPC.Document document, PreviewView previewView);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), getClassGuid());
    }
}

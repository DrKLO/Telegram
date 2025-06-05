package org.telegram.ui.Components.FloatingDebug;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BlurSettingsBottomSheet;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FloatingDebugView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private FrameLayout floatingButtonContainer;
    private Drawable floatingButtonBackground;

    private SpringAnimation fabXSpring, fabYSpring;
    private SharedPreferences mPrefs;

    private boolean isScrolling;
    private boolean isScrollDisallowed;
    private boolean isFromFling;

    private boolean inLongPress;
    private Runnable onLongPress = () -> {
        inLongPress = true;
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignored) {}
    };

    private boolean isBigMenuShown;
    private int wasStatusBar;
    private LinearLayout bigLayout;
    private TextView titleView;
    private RecyclerListView listView;

    private List<FloatingDebugController.DebugItem> debugItems = new ArrayList<>();

    private int touchSlop;
    private GestureDetector.OnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
        private float startX, startY;

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (inLongPress) {
                return false;
            }
            if (!isBigMenuShown) {
                showBigMenu(true);
                return true;
            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (isScrolling && !inLongPress) {
                fabXSpring.getSpring().setFinalPosition(fabXSpring.getSpring().getFinalPosition() + velocityX / 7f >= getWidth() / 2f ? clampX(getResources().getDisplayMetrics(), Integer.MAX_VALUE) : clampX(getResources().getDisplayMetrics(), Integer.MIN_VALUE));
                fabYSpring.getSpring().setFinalPosition(clampY(getResources().getDisplayMetrics(), fabYSpring.getSpring().getFinalPosition() + velocityY / 10f));
                fabXSpring.start();
                fabYSpring.start();
                return isFromFling = true;
            }
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!inLongPress) {
                AndroidUtilities.cancelRunOnUIThread(onLongPress);
            }
            if (!isScrolling && !isScrollDisallowed) {
                if (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) {
                    startX = fabXSpring.getSpring().getFinalPosition();
                    startY = fabYSpring.getSpring().getFinalPosition();
                    isScrolling = true;
                } else {
                    isScrollDisallowed = false;
                }
            }
            if (isScrolling) {
                if (inLongPress) {
                    // TODO: Show additional actions
                } else {
                    fabXSpring.getSpring().setFinalPosition(startX + e2.getRawX() - e1.getRawX());
                    fabYSpring.getSpring().setFinalPosition(startY + e2.getRawY() - e1.getRawY());
                    fabXSpring.start();
                    fabYSpring.start();
                }
            }

            return isScrolling;
        }
    };

    public FloatingDebugView(@NonNull Context context) {
        super(context);

        mPrefs = context.getSharedPreferences("floating_debug", 0);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        GestureDetectorCompat gestureDetector = new GestureDetectorCompat(context, gestureListener);
        gestureDetector.setIsLongpressEnabled(false);
        floatingButtonContainer = new FrameLayout(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                FloatingDebugView.this.invalidate();
            }

            @Override
            public void setTranslationX(float translationX) {
                super.setTranslationX(translationX);
                FloatingDebugView.this.invalidate();
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                FloatingDebugView.this.invalidate();
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                boolean detector = gestureDetector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    AndroidUtilities.runOnUIThread(onLongPress, 200);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPress);

                    if (!isFromFling) {
                        fabXSpring.getSpring().setFinalPosition(fabXSpring.getSpring().getFinalPosition() >= getWidth() / 2f ? clampX(getResources().getDisplayMetrics(), Integer.MAX_VALUE) : clampX(getResources().getDisplayMetrics(), Integer.MIN_VALUE));
                        fabYSpring.getSpring().setFinalPosition(clampY(getResources().getDisplayMetrics(), fabYSpring.getSpring().getFinalPosition()));
                        fabXSpring.start();
                        fabYSpring.start();
                    }
                    inLongPress = false;
                    isScrolling = false;
                    isScrollDisallowed = false;
                    isFromFling = false;
                }
                return detector;
            }
        };
        ImageView actionIcon = new ImageView(context);
        actionIcon.setImageResource(R.drawable.device_phone_android);
        actionIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
        floatingButtonContainer.addView(actionIcon);
        floatingButtonContainer.setVisibility(GONE);
        addView(floatingButtonContainer, LayoutHelper.createFrame(56, 56));

        bigLayout = new LinearLayout(context);
        bigLayout.setOrientation(LinearLayout.VERTICAL);
        bigLayout.setVisibility(GONE);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setText(LocaleController.getString(R.string.DebugMenu));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(19), AndroidUtilities.dp(24), AndroidUtilities.dp(19));
        bigLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                FloatingDebugController.DebugItemType type = FloatingDebugController.DebugItemType.values()[holder.getItemViewType()];

                return type == FloatingDebugController.DebugItemType.SIMPLE;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v;
                switch (FloatingDebugController.DebugItemType.values()[viewType]) {
                    default:
                    case SIMPLE:
                        v = new AlertDialog.AlertDialogCell(context, null);
                        break;
                    case HEADER:
                        v = new HeaderCell(context);
                        break;
                    case SEEKBAR:
                        v = new SeekBarCell(context);
                        break;
                }
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                FloatingDebugController.DebugItem item = debugItems.get(position);
                switch (item.type) {
                    case SIMPLE: {
                        AlertDialog.AlertDialogCell cell = (AlertDialog.AlertDialogCell) holder.itemView;
                        cell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        cell.setTextAndIcon(item.title, 0);
                        break;
                    }
                    case HEADER: {
                        HeaderCell cell = (HeaderCell) holder.itemView;
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
                        cell.setText(item.title);
                        break;
                    }
                    case SEEKBAR: {
                        SeekBarCell cell = (SeekBarCell) holder.itemView;
                        cell.title = item.title.toString();
                        cell.value = (float) item.floatProperty.get(null);
                        cell.min = item.from;
                        cell.max = item.to;
                        cell.callback = item.floatProperty;
                        cell.invalidate();
                        break;
                    }
                }
            }

            @Override
            public int getItemViewType(int position) {
                return debugItems.get(position).type.ordinal();
            }

            @Override
            public int getItemCount() {
                return debugItems.size();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            FloatingDebugController.DebugItem item = debugItems.get(position);
            if (item.action != null) {
                item.action.run();
                showBigMenu(false);
            }
        });
        bigLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        addView(bigLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.NO_GRAVITY, 8, 8, 8, 8));

        updateDrawables();

        setFitsSystemWindows(true);
        setWillNotDraw(false);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == bigLayout) {
            canvas.drawColor(Color.argb((int)(0x7A * bigLayout.getAlpha()), 0, 0, 0));
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public boolean onBackPressed() {
        if (isBigMenuShown) {
            showBigMenu(false);
            return true;
        }
        return false;
    }

    @SuppressLint("ApplySharedPref")
    public void saveConfig() {
        mPrefs.edit()
                .putFloat("x", fabXSpring.getSpring().getFinalPosition())
                .putFloat("y", fabYSpring.getSpring().getFinalPosition())
                .commit();
    }

    private void updateDrawables() {
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        Drawable shadowDrawable = getResources().getDrawable(R.drawable.floating_shadow).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
        combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
        drawable = combinedDrawable;
        floatingButtonBackground = drawable;

        drawable = getResources().getDrawable(R.drawable.popup_fixed_alert3);
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        bigLayout.setBackground(drawable);

        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));

        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return isBigMenuShown;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            updateDrawables();
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        float savedX = mPrefs.getFloat("x", -1), savedY = mPrefs.getFloat("y", -1);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        floatingButtonContainer.setTranslationX(savedX == -1 || savedX >= displayMetrics.widthPixels / 2f ? clampX(displayMetrics, Integer.MAX_VALUE) : clampX(displayMetrics, Integer.MIN_VALUE));
        floatingButtonContainer.setTranslationY(savedY == -1 ? clampY(displayMetrics, Integer.MAX_VALUE) : clampY(displayMetrics, savedY));

        fabXSpring = new SpringAnimation(floatingButtonContainer, SpringAnimation.TRANSLATION_X, floatingButtonContainer.getTranslationX())
                .setSpring(new SpringForce(floatingButtonContainer.getTranslationX())
                        .setStiffness(650f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        fabYSpring = new SpringAnimation(floatingButtonContainer, SpringAnimation.TRANSLATION_Y, floatingButtonContainer.getTranslationY())
                .setSpring(new SpringForce(floatingButtonContainer.getTranslationY())
                        .setStiffness(650f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        fabXSpring.cancel();
        fabYSpring.cancel();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void showBigMenu(boolean show) {
        if (isBigMenuShown == show) {
            return;
        }
        isBigMenuShown = show;

        if (show) {
            bigLayout.setVisibility(VISIBLE);

            debugItems.clear();
            if (getContext() instanceof LaunchActivity) {
                INavigationLayout layout = ((LaunchActivity) getContext()).getActionBarLayout();
                if (layout instanceof FloatingDebugProvider) {
                    debugItems.addAll(((FloatingDebugProvider) layout).onGetDebugItems());
                }
                layout = ((LaunchActivity) getContext()).getRightActionBarLayout();
                if (layout instanceof FloatingDebugProvider) {
                    debugItems.addAll(((FloatingDebugProvider) layout).onGetDebugItems());
                }
                layout = ((LaunchActivity) getContext()).getLayersActionBarLayout();
                if (layout instanceof FloatingDebugProvider) {
                    debugItems.addAll(((FloatingDebugProvider) layout).onGetDebugItems());
                }
            }
            debugItems.addAll(getBuiltInDebugItems());
            listView.getAdapter().notifyDataSetChanged();
        }

        Window window = ((Activity) getContext()).getWindow();
        if (show && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wasStatusBar = window.getStatusBarColor();
        }
        float startX = floatingButtonContainer.getTranslationX();
        float startY = floatingButtonContainer.getTranslationY();
        new SpringAnimation(new FloatValueHolder(show ? 0f : 1000f))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(900)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                        .setFinalPosition(show ? 1000f : 0f))
                .addUpdateListener((animation, value, velocity) -> {
                    value /= 1000f;

                    bigLayout.setAlpha(value);
                    bigLayout.setTranslationX(AndroidUtilities.lerp(startX - AndroidUtilities.dp(8), 0, value));
                    bigLayout.setTranslationY(AndroidUtilities.lerp(startY - AndroidUtilities.dp(8), 0, value));

                    bigLayout.setPivotX(floatingButtonContainer.getTranslationX() + AndroidUtilities.dp(28));
                    bigLayout.setPivotY(floatingButtonContainer.getTranslationY() + AndroidUtilities.dp(28));
                    if (bigLayout.getWidth() != 0) {
                        bigLayout.setScaleX(AndroidUtilities.lerp(floatingButtonContainer.getWidth() / (float) bigLayout.getWidth(), 1f, value));
                    }
                    if (bigLayout.getHeight() != 0) {
                        bigLayout.setScaleY(AndroidUtilities.lerp(floatingButtonContainer.getHeight() / (float) bigLayout.getHeight(), 1f, value));
                    }

                    floatingButtonContainer.setTranslationX(AndroidUtilities.lerp(startX, getWidth() / 2f - AndroidUtilities.dp(28), value));
                    floatingButtonContainer.setTranslationY(AndroidUtilities.lerp(startY, getHeight() / 2f - AndroidUtilities.dp(28), value));
                    floatingButtonContainer.setAlpha(1f - value);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        window.setStatusBarColor(ColorUtils.blendARGB(wasStatusBar, 0x7A000000, value));
                    }

                    invalidate();
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    floatingButtonContainer.setTranslationX(startX);
                    floatingButtonContainer.setTranslationY(startY);

                    if (!show) {
                        bigLayout.setVisibility(GONE);
                    }
                })
                .start();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        fabXSpring.cancel();
        fabYSpring.cancel();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        floatingButtonContainer.setTranslationX(floatingButtonContainer.getTranslationX() >= metrics.widthPixels / 2f ? clampX(metrics, Integer.MAX_VALUE) : clampX(metrics, Integer.MIN_VALUE));
        floatingButtonContainer.setTranslationY(clampY(metrics, floatingButtonContainer.getTranslationY()));
        fabXSpring.getSpring().setFinalPosition(floatingButtonContainer.getTranslationX());
        fabYSpring.getSpring().setFinalPosition(floatingButtonContainer.getTranslationY());
    }

    private List<FloatingDebugController.DebugItem> getBuiltInDebugItems() {
        List<FloatingDebugController.DebugItem> items = new ArrayList<>();

        items.add(new FloatingDebugController.DebugItem("Theme"));
        items.add(new FloatingDebugController.DebugItem("Draw action bar shadow", () -> {
            SharedConfig.drawActionBarShadow = !SharedConfig.drawActionBarShadow;
            SharedConfig.saveDebugConfig();
            AndroidUtilities.forEachViews(LaunchActivity.instance.drawerLayoutContainer.getRootView(), View::invalidate);
        }));
        items.add(new FloatingDebugController.DebugItem("Show blur settings", () -> {
            BlurSettingsBottomSheet.show(LaunchActivity.getLastFragment());
            showBigMenu(false);
        }));

        items.add(new FloatingDebugController.DebugItem(LocaleController.getString(R.string.DebugGeneral)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            items.add(new FloatingDebugController.DebugItem(LocaleController.getString(SharedConfig.debugWebView ? R.string.DebugMenuDisableWebViewDebug : R.string.DebugMenuEnableWebViewDebug), ()->{
                SharedConfig.toggleDebugWebView();
                Toast.makeText(getContext(), LocaleController.getString(SharedConfig.debugWebView ? R.string.DebugMenuWebViewDebugEnabled : R.string.DebugMenuWebViewDebugDisabled), Toast.LENGTH_SHORT).show();
            }));
        }
        items.add(new FloatingDebugController.DebugItem(Theme.isCurrentThemeDark() ? "Switch to day theme" : "Switch to dark theme", () -> {
            boolean toDark;

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                dayThemeName = "Blue";
            }
            String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                nightThemeName = "Dark Blue";
            }
            Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }

            if (!Theme.isCurrentThemeDark()) {
                themeInfo = Theme.getTheme(nightThemeName);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
            }
            Theme.ThemeInfo finalThemeInfo = themeInfo;
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, finalThemeInfo, true, null, -1);
            }, 200);
        }));
        items.add(new FloatingDebugController.DebugItem(LocaleController.getString(R.string.DebugSendLogs), () -> ProfileActivity.sendLogs((Activity) getContext(), false)));
        return items;
    }

    private float clampX(DisplayMetrics displayMetrics, float x) {
        return MathUtils.clamp(x, AndroidUtilities.dp(16), displayMetrics.widthPixels - AndroidUtilities.dp(56 + 16));
    }

    private float clampY(DisplayMetrics displayMetrics, float y) {
        return MathUtils.clamp(y, AndroidUtilities.dp(16), displayMetrics.heightPixels - AndroidUtilities.dp(56 + 16));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.translate(floatingButtonContainer.getTranslationX(), floatingButtonContainer.getTranslationY());
        canvas.scale(floatingButtonContainer.getScaleX(), floatingButtonContainer.getScaleY(), floatingButtonContainer.getPivotX(), floatingButtonContainer.getPivotY());
        floatingButtonBackground.setAlpha((int) (floatingButtonContainer.getAlpha() * 0xFF));
        floatingButtonBackground.setBounds(floatingButtonContainer.getLeft(), floatingButtonContainer.getTop(),
                floatingButtonContainer.getRight(), floatingButtonContainer.getBottom());
        floatingButtonBackground.draw(canvas);
        canvas.restore();
    }

    public void showFab() {
        floatingButtonContainer.setVisibility(VISIBLE);

        new SpringAnimation(new FloatValueHolder(0))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(750f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY))
                .addUpdateListener((animation, value, velocity) -> {
                    value /= 1000;

                    floatingButtonContainer.setPivotX(AndroidUtilities.dp(28));
                    floatingButtonContainer.setPivotY(AndroidUtilities.dp(28));
                    floatingButtonContainer.setScaleX(value);
                    floatingButtonContainer.setScaleY(value);
                    floatingButtonContainer.setAlpha(MathUtils.clamp(value, 0, 1));
                    invalidate();
                })
                .start();
    }

    public void dismiss(Runnable callback) {
        callback.run();
    }

    @SuppressWarnings("unchecked")
    private class SeekBarCell extends FrameLayout {

        private SeekBarView seekBar;
        private float min;
        private float max;
        private float value;
        private AnimationProperties.FloatProperty callback;
        private String title;

        private TextPaint textPaint;
        private int lastWidth;

        public SeekBarCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    value = min + (max - min) * progress;
                    if (stop) {
                        callback.set(null, value);
                    }
                    invalidate();
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {}

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(Math.round(min + (max - min) * seekBar.getProgress()));
                }
            });
            seekBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.BOTTOM, 5, 5 + 24, 47, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.drawText(title, AndroidUtilities.dp(24), AndroidUtilities.dp(24), textPaint);

            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            String str = String.format(Locale.ROOT, "%.2f", value);
            canvas.drawText(str, getMeasuredWidth() - AndroidUtilities.dp(8) - textPaint.measureText(str), AndroidUtilities.dp(28 - 5) + seekBar.getY(), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != width) {
                seekBar.setProgress(((float) callback.get(null) - min) / (float) (max - min));
                lastWidth = width;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            seekBar.invalidate();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            seekBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || seekBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
        }
    }
}

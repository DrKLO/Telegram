package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

public class UpdateLayout extends IUpdateLayout {

    private FrameLayout updateLayout;
    private RadialProgress2 updateLayoutIcon;
    private SimpleTextView updateTextView;
    private TextView updateSizeTextView;

    private Activity activity;
    private ViewGroup sideMenu;
    private ViewGroup sideMenuContainer;

    public UpdateLayout(Activity activity, ViewGroup sideMenu, ViewGroup sideMenuContainer) {
        super(activity, sideMenu, sideMenuContainer);
        this.activity = activity;
        this.sideMenu = sideMenu;
        this.sideMenuContainer = sideMenuContainer;
    }

    public void updateFileProgress(Object[] args) {
        if (updateTextView != null && SharedConfig.isAppUpdateAvailable()) {
            String location = (String) args[0];
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            if (fileName != null && fileName.equals(location)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float loadProgress = loadedSize / (float) totalSize;
                updateLayoutIcon.setProgress(loadProgress, true);
                updateTextView.setText(LocaleController.formatString("AppUpdateDownloading", R.string.AppUpdateDownloading, (int) (loadProgress * 100)));
            }
        }
    }

    public void createUpdateUI(int currentAccount) {
        if (sideMenuContainer == null) {
            return;
        }
        updateLayout = new FrameLayout(activity) {

            private Paint paint = new Paint();
            private Matrix matrix = new Matrix();
            private LinearGradient updateGradient;
            private int lastGradientWidth;

            @Override
            public void draw(Canvas canvas) {
                if (updateGradient != null) {
                    paint.setColor(0xffffffff);
                    paint.setShader(updateGradient);
                    updateGradient.setLocalMatrix(matrix);
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
                    updateLayoutIcon.setBackgroundGradientDrawable(updateGradient);
                    updateLayoutIcon.draw(canvas);
                }
                super.draw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (lastGradientWidth != width) {
                    updateGradient = new LinearGradient(0, 0, width, 0, new int[]{0xff69BF72, 0xff53B3AD}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
                    lastGradientWidth = width;
                }
            }
        };
        updateLayout.setWillNotDraw(false);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(AndroidUtilities.dp(44));
        if (Build.VERSION.SDK_INT >= 21) {
            updateLayout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        }
        sideMenuContainer.addView(updateLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        updateLayout.setOnClickListener(v -> {
            if (!SharedConfig.isAppUpdateAvailable()) {
                return;
            }
            if (updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_DOWNLOAD) {
                FileLoader.getInstance(currentAccount).loadFile(SharedConfig.pendingAppUpdate.document, "update", FileLoader.PRIORITY_NORMAL, 1);
                updateAppUpdateViews(currentAccount,  true);
            } else if (updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_CANCEL) {
                FileLoader.getInstance(currentAccount).cancelLoadFile(SharedConfig.pendingAppUpdate.document);
                updateAppUpdateViews(currentAccount, true);
            } else {
                AndroidUtilities.openForView(SharedConfig.pendingAppUpdate.document, true, activity);
            }
        });
        updateLayoutIcon = new RadialProgress2(updateLayout);
        updateLayoutIcon.setColors(0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff);
        updateLayoutIcon.setProgressRect(AndroidUtilities.dp(22), AndroidUtilities.dp(11), AndroidUtilities.dp(22 + 22), AndroidUtilities.dp(11 + 22));
        updateLayoutIcon.setCircleRadius(AndroidUtilities.dp(11));
        updateLayoutIcon.setAsMini();

        updateTextView = new SimpleTextView(activity);
        updateTextView.setTextSize(15);
        updateTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        updateTextView.setText(LocaleController.getString("AppUpdate", R.string.AppUpdate));
        updateTextView.setTextColor(0xffffffff);
        updateTextView.setGravity(Gravity.LEFT);
        updateLayout.addView(updateTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 74, 0, 0, 0));

        updateSizeTextView = new TextView(activity);
        updateSizeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        updateSizeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        updateSizeTextView.setGravity(Gravity.RIGHT);
        updateSizeTextView.setTextColor(0xffffffff);
        updateLayout.addView(updateSizeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 17, 0));
    }

    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (sideMenuContainer == null) {
            return;
        }
        if (SharedConfig.isAppUpdateAvailable()) {
            View prevUpdateLayout = updateLayout;
            createUpdateUI(currentAccount);
            updateSizeTextView.setText(AndroidUtilities.formatFileSize(SharedConfig.pendingAppUpdate.document.size));
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            File path = FileLoader.getInstance(currentAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            boolean showSize;
            if (path.exists()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, false);
                updateTextView.setText(LocaleController.getString("AppUpdateNow", R.string.AppUpdateNow));
                showSize = false;
            } else {
                if (FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, false);
                    updateLayoutIcon.setProgress(0, false);
                    Float p = ImageLoader.getInstance().getFileProgress(fileName);
                    updateTextView.setText(LocaleController.formatString("AppUpdateDownloading", R.string.AppUpdateDownloading, (int) ((p != null ? p : 0.0f) * 100)));
                    showSize = false;
                } else {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, false);
                    updateTextView.setText(LocaleController.getString("AppUpdate", R.string.AppUpdate));
                    showSize = true;
                }
            }
            if (showSize) {
                if (updateSizeTextView.getTag() != null) {
                    if (animated) {
                        updateSizeTextView.setTag(null);
                        updateSizeTextView.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(180).start();
                    } else {
                        updateSizeTextView.setAlpha(1.0f);
                        updateSizeTextView.setScaleX(1.0f);
                        updateSizeTextView.setScaleY(1.0f);
                    }
                }
            } else {
                if (updateSizeTextView.getTag() == null) {
                    if (animated) {
                        updateSizeTextView.setTag(1);
                        updateSizeTextView.animate().alpha(0.0f).scaleX(0.0f).scaleY(0.0f).setDuration(180).start();
                    } else {
                        updateSizeTextView.setAlpha(0.0f);
                        updateSizeTextView.setScaleX(0.0f);
                        updateSizeTextView.setScaleY(0.0f);
                    }
                }
            }
            if (updateLayout.getTag() != null) {
                return;
            }
            updateLayout.setVisibility(View.VISIBLE);
            updateLayout.setTag(1);
            if (animated) {
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).withEndAction(() -> {
                    if (prevUpdateLayout != null) {
                        ViewGroup parent = (ViewGroup) prevUpdateLayout.getParent();
                        if (parent != null) {
                            parent.removeView(prevUpdateLayout);
                        }
                    }
                }).start();
            } else {
                updateLayout.setTranslationY(0);
                if (prevUpdateLayout != null) {
                    ViewGroup parent = (ViewGroup) prevUpdateLayout.getParent();
                    if (parent != null) {
                        parent.removeView(prevUpdateLayout);
                    }
                }
            }
            sideMenu.setPadding(0, 0, 0, AndroidUtilities.dp(44));
        } else {
            if (updateLayout == null || updateLayout.getTag() == null) {
                return;
            }
            updateLayout.setTag(null);
            if (animated) {
                updateLayout.animate().translationY(AndroidUtilities.dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateLayout.getTag() == null) {
                            updateLayout.setVisibility(View.INVISIBLE);
                        }
                    }
                }).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(AndroidUtilities.dp(44));
                updateLayout.setVisibility(View.INVISIBLE);
            }
            sideMenu.setPadding(0, 0, 0, 0);
        }
    }
}


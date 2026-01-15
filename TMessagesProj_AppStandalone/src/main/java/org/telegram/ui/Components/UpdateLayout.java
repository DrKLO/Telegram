package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.IUpdateLayout;

import java.io.File;
import java.util.ArrayList;

public class UpdateLayout extends IUpdateLayout {

    private LinearLayout updateLayout;
    private RadialProgress2 updateLayoutIcon;
    private TextView[] updateTextViews;
    private TextView updateSizeTextView;
    private AnimatorSet updateTextAnimator;

    private final Activity activity;
    private final ViewGroup sideMenuContainer;

    public UpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        super(activity, sideMenuContainer);
        this.activity = activity;
        this.sideMenuContainer = sideMenuContainer;
    }

    public void updateFileProgress(Object[] args) {
        if (updateTextViews == null || args == null) return;
        if (updateTextViews[0] != null && SharedConfig.isAppUpdateAvailable()) {
            String location = (String) args[0];
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            if (fileName != null && fileName.equals(location)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float loadProgress = loadedSize / (float) totalSize;
                updateLayoutIcon.setProgress(loadProgress, true);
                updateTextViews[0].setText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) (loadProgress * 100)));
            }
        }
    }

    public void createUpdateUI(int currentAccount) {
        if (sideMenuContainer == null || updateLayout != null) {
            return;
        }
        updateLayout = new LinearLayout(activity);
        updateLayout.setOrientation(LinearLayout.HORIZONTAL);
        updateLayout.setWillNotDraw(false);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(dp(44));
        updateLayout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
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

        View v = new View(activity) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                super.onDraw(canvas);
                updateLayoutIcon.draw(canvas);
            }
        };
        updateLayoutIcon = new RadialProgress2(v);
        updateLayoutIcon.setColors(0xffffffff, 0xffffffff, Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButton));
        updateLayoutIcon.setProgressRect(0, 0, dp(22), dp(22));
        updateLayoutIcon.setCircleRadius(dp(11));
        updateLayoutIcon.setAsMini();
        updateLayout.addView(v, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));


        FrameLayout updateTextViewsContainer = new FrameLayout(activity);
        updateTextViews = new TextView[2];
        for (int i = 0; i < 2; ++i) {
            updateTextViews[i] = new TextView(activity);
            updateTextViews[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            updateTextViews[i].setTypeface(AndroidUtilities.bold());
            updateTextViews[i].setTextColor(0xffffffff);
            updateTextViews[i].setGravity(Gravity.LEFT);
            updateTextViewsContainer.addView(updateTextViews[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        updateLayout.addView(updateTextViewsContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        updateTextViews[0].setText(LocaleController.getString(R.string.AppUpdate));
        updateTextViews[1].setAlpha(0f);
        updateTextViews[1].setVisibility(View.GONE);

        updateSizeTextView = new TextView(activity);
        updateSizeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        updateSizeTextView.setTypeface(AndroidUtilities.bold());
        updateSizeTextView.setGravity(Gravity.RIGHT);
        updateSizeTextView.setTextColor(0xffffffff);
        updateLayout.addView(updateSizeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 12, 0, 0, 0));
    }

    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (sideMenuContainer == null) {
            return;
        }
        if (SharedConfig.isAppUpdateAvailable()) {
            createUpdateUI(currentAccount);
            updateSizeTextView.setText(AndroidUtilities.formatFileSize(SharedConfig.pendingAppUpdate.document.size));
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            File path = FileLoader.getInstance(currentAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            boolean showSize;
            if (path.exists()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                setUpdateText(LocaleController.getString(R.string.AppUpdateNow), animated);
                showSize = false;
            } else {
                if (FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                    updateLayoutIcon.setProgress(0, false);
                    Float p = ImageLoader.getInstance().getFileProgress(fileName);
                    setUpdateText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) ((p != null ? p : 0.0f) * 100)), animated);
                    showSize = false;
                } else {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                    setUpdateText(LocaleController.getString(R.string.AppUpdate), animated);
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
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(0);
            }
        } else {
            if (updateLayout == null || updateLayout.getTag() == null) {
                return;
            }
            updateLayout.setTag(null);
            if (animated) {
                updateLayout.animate().translationY(dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateLayout.getTag() == null) {
                            updateLayout.setVisibility(View.INVISIBLE);
                        }
                    }
                }).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(dp(44));
                updateLayout.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setUpdateText(String text, boolean animate) {
        if (TextUtils.equals(updateTextViews[0].getText(), text)) {
            return;
        }
        if (updateTextAnimator != null) {
            updateTextAnimator.cancel();
            updateTextAnimator = null;
        }

        if (animate) {
            updateTextViews[1].setText(updateTextViews[0].getText());
            updateTextViews[0].setText(text);

            updateTextViews[0].setAlpha(0);
            updateTextViews[1].setAlpha(1);
            updateTextViews[0].setVisibility(View.VISIBLE);
            updateTextViews[1].setVisibility(View.VISIBLE);

            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(updateTextViews[1], View.ALPHA, 0));
            arrayList.add(ObjectAnimator.ofFloat(updateTextViews[0], View.ALPHA, 1));

            updateTextAnimator = new AnimatorSet();
            updateTextAnimator.playTogether(arrayList);
            updateTextAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (updateTextAnimator == animation) {
                        updateTextViews[1].setVisibility(View.GONE);
                        updateTextAnimator = null;
                    }
                }
            });
            updateTextAnimator.setDuration(320);
            updateTextAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            updateTextAnimator.start();
        } else {
            updateTextViews[0].setText(text);
            updateTextViews[0].setAlpha(1);
            updateTextViews[0].setVisibility(View.VISIBLE);
            updateTextViews[1].setVisibility(View.GONE);
        }
    }
}

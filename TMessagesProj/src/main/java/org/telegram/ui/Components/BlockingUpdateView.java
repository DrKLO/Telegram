package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.io.File;
import java.util.Locale;

public class BlockingUpdateView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private TextView textView;
    private TextView acceptTextView;
    private FrameLayout radialProgressView;
    private FrameLayout acceptButton;
    private RadialProgress radialProgress;
    private ScrollView scrollView;

    private AnimatorSet progressAnimation;

    private TLRPC.TL_help_appUpdate appUpdate;
    private String fileName;
    private int accountNum;
    private int pressCount;

    public BlockingUpdateView(final Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int top = Build.VERSION.SDK_INT >= 21 ? (int) (AndroidUtilities.statusBarHeight / AndroidUtilities.density) : 0;

        FrameLayout view = new FrameLayout(context);
        addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(176) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.qr_code_logo, 108, 108);
        imageView.playAnimation();
        imageView.getAnimatedDrawable().setAutoRepeat(1);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setPadding(0, 0, 0, AndroidUtilities.dp(14));
        view.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, top, 0, 0));
        imageView.setOnClickListener(v -> {
            pressCount++;
            if (pressCount >= 10) {
                setVisibility(GONE);
                SharedConfig.pendingAppUpdate = null;
                SharedConfig.saveConfig();
            }
        });

        FrameLayout container = new FrameLayout(context);
        scrollView = new ScrollView(context);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        scrollView.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
        scrollView.setClipToPadding(false);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 27, 178 + top, 27, 130));


        scrollView.addView(container);

        TextView titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setText(LocaleController.getString("UpdateTelegram", R.string.UpdateTelegram));
        container.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        textView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        container.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 44, 0, 0));


        acceptButton = new FrameLayout(context) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (cellFlickerDrawable == null) {
                    cellFlickerDrawable = new CellFlickerDrawable();
                    cellFlickerDrawable.drawFrame = false;
                    cellFlickerDrawable.repeatProgress = 2f;
                }
                cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4), null);
                invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                if (size > AndroidUtilities.dp(260)) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320), MeasureSpec.EXACTLY), heightMeasureSpec);
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };
        acceptButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        acceptButton.setBackgroundDrawable(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
        acceptButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        addView(acceptButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 46, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 45));
        acceptButton.setOnClickListener(view1 -> {
            if (!checkApkInstallPermissions(getContext())) {
                return;
            }
            if (appUpdate.document instanceof TLRPC.TL_document) {
                if (!openApkInstall((Activity) getContext(), appUpdate.document)) {
                    FileLoader.getInstance(accountNum).loadFile(appUpdate.document, "update", FileLoader.PRIORITY_HIGH, 1);
                    showProgress(true);
                }
            } else if (appUpdate.url != null) {
                Browser.openUrl(getContext(), appUpdate.url);
            }
        });

        acceptTextView = new TextView(context);
        acceptTextView.setGravity(Gravity.CENTER);
        acceptTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        acceptTextView.setTextColor(0xffffffff);
        acceptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        acceptButton.addView(acceptTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        radialProgressView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int width = right - left;
                int height = bottom - top;
                int w = AndroidUtilities.dp(36);
                int l = (width - w) / 2;
                int t = (height - w) / 2;
                radialProgress.setProgressRect(l, t, l + w, t + w);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                radialProgress.draw(canvas);
            }
        };
        radialProgressView.setWillNotDraw(false);
        radialProgressView.setAlpha(0.0f);
        radialProgressView.setScaleX(0.1f);
        radialProgressView.setScaleY(0.1f);
        radialProgressView.setVisibility(View.INVISIBLE);
        radialProgress = new RadialProgress(radialProgressView);
        radialProgress.setBackground(null, true, false);
        radialProgress.setProgressColor(0xffffffff);
        acceptButton.addView(radialProgressView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == GONE) {
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.fileLoadFailed);
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
                openApkInstall((Activity) getContext(), appUpdate.document);
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float progress = Math.min(1f, loadedSize / (float) totalSize);
                radialProgress.setProgress(progress, true);
            }
        }
    }

    public static boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }

    public static boolean openApkInstall(Activity activity, TLRPC.Document document) {
        boolean exists = false;
        try {
            String fileName = FileLoader.getAttachFileName(document);
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
            if (exists = f.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (Build.VERSION.SDK_INT >= 24) {
                    intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", f), "application/vnd.android.package-archive");
                } else {
                    intent.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
                }
                try {
                    activity.startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return exists;
    }

    private void showProgress(final boolean show) {
        if (progressAnimation != null) {
            progressAnimation.cancel();
        }
        progressAnimation = new AnimatorSet();
        if (show) {
            radialProgressView.setVisibility(View.VISIBLE);
            acceptButton.setEnabled(false);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(acceptTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, View.ALPHA, 1.0f));
        } else {
            acceptTextView.setVisibility(View.VISIBLE);
            acceptButton.setEnabled(true);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(acceptTextView, View.ALPHA, 1.0f));

        }
        progressAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (progressAnimation != null && progressAnimation.equals(animation)) {
                    if (!show) {
                        radialProgressView.setVisibility(View.INVISIBLE);
                    } else {
                        acceptTextView.setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (progressAnimation != null && progressAnimation.equals(animation)) {
                    progressAnimation = null;
                }
            }
        });
        progressAnimation.setDuration(150);
        progressAnimation.start();
    }

    public void show(int account, TLRPC.TL_help_appUpdate update, boolean check) {
        pressCount = 0;
        appUpdate = update;
        accountNum = account;
        if (update.document instanceof TLRPC.TL_document) {
            fileName = FileLoader.getAttachFileName(update.document);
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(update.text);
        MessageObject.addEntitiesToText(builder, update.entities, false, false, false, false);
        textView.setText(builder);
        if (update.document instanceof TLRPC.TL_document) {
            acceptTextView.setText(LocaleController.getString("Update", R.string.Update) + String.format(Locale.US, " (%1$s)", AndroidUtilities.formatFileSize(update.document.size)));
        } else {
            acceptTextView.setText(LocaleController.getString("Update", R.string.Update));
        }
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        if (check) {
            TLRPC.TL_help_getAppUpdate req = new TLRPC.TL_help_getAppUpdate();
            try {
                req.source = ApplicationLoader.applicationContext.getPackageManager().getInstallerPackageName(ApplicationLoader.applicationContext.getPackageName());
            } catch (Exception ignore) {

            }
            if (req.source == null) {
                req.source = "";
            }
            ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_help_appUpdate) {
                    final TLRPC.TL_help_appUpdate res = (TLRPC.TL_help_appUpdate) response;
                    if (!res.can_not_skip) {
                        setVisibility(GONE);
                        SharedConfig.pendingAppUpdate = null;
                        SharedConfig.saveConfig();
                    }
                }
            }));
        }
    }

    Drawable gradientDrawableTop = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {Theme.getColor(Theme.key_windowBackgroundWhite), Color.TRANSPARENT });
    Drawable gradientDrawableBottom = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {Theme.getColor(Theme.key_windowBackgroundWhite), Color.TRANSPARENT });

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        gradientDrawableTop.setBounds(scrollView.getLeft(), scrollView.getTop(), scrollView.getRight(), scrollView.getTop() + AndroidUtilities.dp(16));
        gradientDrawableTop.draw(canvas);

        gradientDrawableBottom.setBounds(scrollView.getLeft(), scrollView.getBottom() - AndroidUtilities.dp(18), scrollView.getRight(), scrollView.getBottom());
        gradientDrawableBottom.draw(canvas);
    }

}

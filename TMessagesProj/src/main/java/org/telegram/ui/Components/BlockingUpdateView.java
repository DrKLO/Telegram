package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.Locale;

public class BlockingUpdateView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private TextView textView;
    private TextView acceptTextView;
    private FrameLayout radialProgressView;
    private FrameLayout acceptButton;
    private RadialProgress radialProgress;

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
        view.setBackgroundColor(0xff4fa9e6);
        addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(176) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.intro_tg_plane);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setPadding(0, 0, 0, AndroidUtilities.dp(14));
        view.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, top, 0, 0));
        imageView.setOnClickListener(v -> {
            pressCount++;
            if (pressCount >= 10) {
                setVisibility(GONE);
                UserConfig.getInstance(0).pendingAppUpdate = null;
                UserConfig.getInstance(0).saveConfig(false);
            }
        });

        ScrollView scrollView = new ScrollView(context);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 27, 206 + top, 27, 130));

        FrameLayout container = new FrameLayout(context);
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

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

        acceptButton = new FrameLayout(context);
        acceptButton.setBackgroundResource(R.drawable.regbtn_states);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(acceptButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(acceptButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            acceptButton.setStateListAnimator(animator);
        }
        acceptButton.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        addView(acceptButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 45));
        acceptButton.setOnClickListener(view1 -> {
            if (!checkApkInstallPermissions(getContext())) {
                return;
            }
            if (appUpdate.document instanceof TLRPC.TL_document) {
                if (!openApkInstall((Activity) getContext(), appUpdate.document)) {
                    FileLoader.getInstance(accountNum).loadFile(appUpdate.document, true, 1);
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
        acceptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

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
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.FileDidLoaded);
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.FileDidFailedLoad);
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.FileLoadProgressChanged);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
                openApkInstall((Activity) getContext(), appUpdate.document);
            }
        } else if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                Float loadProgress = (Float) args[1];
                radialProgress.setProgress(loadProgress, true);
            }
        }
    }

    public static boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= 26 && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.getString("ApkRestricted", R.string.ApkRestricted));
            builder.setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                try {
                    context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName())));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
            return false;
        }
        return true;
    }

    public static boolean openApkInstall(Activity activity, TLRPC.Document document) {
        boolean exists = false;
        try {
            String fileName = FileLoader.getAttachFileName(document);
            File f = FileLoader.getPathToAttach(document, true);
            if (exists = f.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (Build.VERSION.SDK_INT >= 24) {
                    intent.setDataAndType(FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", f), "application/vnd.android.package-archive");
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
                    ObjectAnimator.ofFloat(acceptTextView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(acceptTextView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(acceptTextView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(radialProgressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, "alpha", 1.0f));
        } else {
            acceptTextView.setVisibility(View.VISIBLE);
            acceptButton.setEnabled(true);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(radialProgressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(acceptTextView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(acceptTextView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(acceptTextView, "alpha", 1.0f));

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

    public void show(int account, TLRPC.TL_help_appUpdate update) {
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
        MessageObject.addEntitiesToText(builder, update.entities, false, 0, false, false, false);
        textView.setText(builder);
        if (update.document instanceof TLRPC.TL_document) {
            acceptTextView.setText(LocaleController.getString("Update", R.string.Update).toUpperCase() + String.format(Locale.US, " (%1$s)", AndroidUtilities.formatFileSize(update.document.size)));
        } else {
            acceptTextView.setText(LocaleController.getString("Update", R.string.Update).toUpperCase());
        }
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.FileLoadProgressChanged);
    }
}

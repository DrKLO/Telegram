/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.util.Log;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SlideView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("HardwareIds")
public class LoginActivity extends BaseFragment {

    public final static int AUTH_TYPE_SMS = 2;
    public final static int AUTH_TYPE_FLASH_CALL = 3;
    public final static int AUTH_TYPE_CALL = 4;
    public final static int AUTH_TYPE_MISSED_CALL = 11;

    private int currentViewNum;
    private SlideView[] views = new SlideView[12];

    private boolean restoringState;

    private Dialog permissionsDialog;
    private Dialog permissionsShowDialog;
    private ArrayList<String> permissionsItems = new ArrayList<>();
    private ArrayList<String> permissionsShowItems = new ArrayList<>();
    private boolean checkPermissions = true;
    private boolean checkShowPermissions = true;
    private boolean newAccount;
    private boolean syncContacts = true;
    private boolean testBackend = false;

    private int scrollHeight;

    private int currentDoneType;
    private AnimatorSet[] showDoneAnimation = new AnimatorSet[2];
    private ActionBarMenuItem doneItem;
    private AnimatorSet doneItemAnimation;
    private ContextProgressView doneProgressView;
    private ImageView floatingButtonIcon;
    private FrameLayout floatingButtonContainer;
    private RadialProgressView floatingProgressView;
    private int progressRequestId;
    private boolean[] doneButtonVisible = new boolean[] {true, false};

    private static final int DONE_TYPE_FLOATING = 0;
    private static final int DONE_TYPE_ACTION = 1;

    private final static int done_button = 1;

    private boolean needRequestPermissions;

    private static class ProgressView extends View {

        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final RectF boundsRect = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private long startTime;
        private long duration;
        private boolean animating;

        private float radius;

        public ProgressView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_login_progressInner));
            paint2.setColor(Theme.getColor(Theme.key_login_progressOuter));
        }

        public void startProgressAnimation(long duration) {
            this.animating = true;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            invalidate();
        }

        public void resetProgressAnimation() {
            duration = 0;
            startTime = 0;
            animating = false;
            invalidate();
        }

        public boolean isProgressAnimationRunning() {
            return animating;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            path.rewind();
            radius = h / 2f;
            boundsRect.set(0, 0, w, h);
            rect.set(boundsRect);
            path.addRoundRect(boundsRect, radius, radius, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float progress;
            if (duration > 0) {
                progress = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
            } else {
                progress = 0f;
            }

            canvas.clipPath(path);
            canvas.drawRoundRect(boundsRect, radius, radius, paint);
            rect.right = boundsRect.right * progress;
            canvas.drawRoundRect(rect, radius, radius, paint2);

            if (animating &= duration > 0 && progress < 1f) {
                postInvalidateOnAnimation();
            }
        }
    }

    public LoginActivity() {
        super();
    }

    public LoginActivity(int account) {
        super();
        currentAccount = account;
        newAccount = true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (int a = 0; a < views.length; a++) {
            if (views[a] != null) {
                views[a].onDestroyActivity();
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    onDoneButtonPressed();
                } else if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                }
            }
        });

        currentDoneType = DONE_TYPE_FLOATING;
        doneButtonVisible[DONE_TYPE_FLOATING] = true;
        doneButtonVisible[DONE_TYPE_ACTION] = false;

        ActionBarMenu menu = actionBar.createMenu();
        actionBar.setAllowOverlayTitle(true);
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        doneProgressView = new ContextProgressView(context, 1);
        doneProgressView.setAlpha(0.0f);
        doneProgressView.setScaleX(0.1f);
        doneProgressView.setScaleY(0.1f);
        doneProgressView.setVisibility(View.INVISIBLE);
        doneItem.setAlpha(0.0f);
        doneItem.setScaleX(0.1f);
        doneItem.setScaleY(0.1f);
        doneItem.addView(doneProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        doneItem.setContentDescription(LocaleController.getString("Done", R.string.Done));
        doneItem.setVisibility(doneButtonVisible[DONE_TYPE_ACTION] ? View.VISIBLE : View.GONE);

        FrameLayout container = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                MarginLayoutParams marginLayoutParams = (MarginLayoutParams) floatingButtonContainer.getLayoutParams();
                if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().isShowing()) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    marginLayoutParams.bottomMargin = AndroidUtilities.dp(14) + Bulletin.getVisibleBulletin().getLayout().getMeasuredHeight() - AndroidUtilities.dp(10);
                } else {
                    marginLayoutParams.bottomMargin = AndroidUtilities.dp(14);
                }

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        fragmentView = container;

        ScrollView scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (currentViewNum == 1 || currentViewNum == 2 || currentViewNum == 4) {
                    rectangle.bottom += AndroidUtilities.dp(40);
                }
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                scrollHeight = MeasureSpec.getSize(heightMeasureSpec) - AndroidUtilities.dp(30);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scrollView.setFillViewport(true);
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        views[0] = new PhoneView(context);
        views[1] = new LoginActivitySmsView(context, 1);
        views[2] = new LoginActivitySmsView(context, 2);
        views[3] = new LoginActivitySmsView(context, 3);
        views[4] = new LoginActivitySmsView(context, 4);
        views[5] = new LoginActivityRegisterView(context);
        views[6] = new LoginActivityPasswordView(context);
        views[7] = new LoginActivityRecoverView(context);
        views[8] = new LoginActivityResetWaitView(context);
        views[9] = new LoginActivityNewPasswordView(context, 0);
        views[10] = new LoginActivityNewPasswordView(context, 1);
        views[11] = new LoginActivitySmsView(context, 11);

        for (int a = 0; a < views.length; a++) {
            views[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
            frameLayout.addView(views[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 26 : 18, 30, AndroidUtilities.isTablet() ? 26 : 18, 0));
        }

        Bundle savedInstanceState = loadCurrentState();
        if (savedInstanceState != null) {
            currentViewNum = savedInstanceState.getInt("currentViewNum", 0);
            syncContacts = savedInstanceState.getInt("syncContacts", 1) == 1;
            if (currentViewNum >= 1 && currentViewNum <= 4) {
                int time = savedInstanceState.getInt("open");
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    currentViewNum = 0;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            } else if (currentViewNum == 6) {
                LoginActivityPasswordView view = (LoginActivityPasswordView) views[6];
                if (view.currentPassword == null) {
                    currentViewNum = 0;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            } else if (currentViewNum == 7) {
                LoginActivityRecoverView view = (LoginActivityRecoverView) views[7];
                if (view.passwordString == null) {
                    currentViewNum = 0;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            }
        }

        floatingButtonContainer = new FrameLayout(context);
        floatingButtonContainer.setVisibility(doneButtonVisible[DONE_TYPE_FLOATING] ? View.VISIBLE : View.GONE);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButtonContainer.setBackgroundDrawable(drawable);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
            floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        VerticalPositionAutoAnimator.attach(floatingButtonContainer);
        container.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 14, 14));
        floatingButtonContainer.setOnClickListener(view -> onDoneButtonPressed());

        floatingButtonIcon = new ImageView(context);
        floatingButtonIcon.setScaleType(ImageView.ScaleType.CENTER);
        floatingButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButtonIcon.setImageResource(R.drawable.actionbtn_next);
        floatingButtonContainer.setContentDescription(LocaleController.getString("Done", R.string.Done));
        floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

        floatingProgressView = new RadialProgressView(context);
        floatingProgressView.setSize(AndroidUtilities.dp(22));
        floatingProgressView.setProgressColor(Theme.getColor(Theme.key_chats_actionIcon));
        floatingProgressView.setAlpha(0.0f);
        floatingProgressView.setScaleX(0.1f);
        floatingProgressView.setScaleY(0.1f);
        floatingProgressView.setVisibility(View.INVISIBLE);
        floatingButtonContainer.addView(floatingProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (savedInstanceState != null) {
            restoringState = true;
        }
        for (int a = 0; a < views.length; a++) {
            if (savedInstanceState != null) {
                if (a >= 1 && a <= 4) {
                    if (a == currentViewNum) {
                        views[a].restoreStateParams(savedInstanceState);
                    }
                } else {
                    views[a].restoreStateParams(savedInstanceState);
                }
            }
            if (currentViewNum == a) {
                actionBar.setBackButtonImage(views[a].needBackButton() || newAccount ? R.drawable.ic_ab_back : 0);
                views[a].setVisibility(View.VISIBLE);
                views[a].onShow();
                currentDoneType = DONE_TYPE_FLOATING;
                if (a == 1 || a == 2 || a == 3 || a == 4 || a == 8) {
                    showDoneButton(false, false);
                } else {
                    showDoneButton(true, false);
                }
                if (a == 1 || a == 2 || a == 3 || a == 4) {
                    currentDoneType = DONE_TYPE_ACTION;
                }
            } else {
                views[a].setVisibility(View.GONE);
            }
        }
        restoringState = false;

        actionBar.setTitle(views[currentViewNum].getHeaderName());

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (newAccount) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (newAccount) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        fragmentView.requestLayout();
        try {
            if (currentViewNum >= 1 && currentViewNum <= 4 && views[currentViewNum] instanceof LoginActivitySmsView) {
                int time = ((LoginActivitySmsView) views[currentViewNum]).openTime;
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    views[currentViewNum].onBackPressed(true);
                    setPage(0, false, null, true);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (currentViewNum == 0 && !needRequestPermissions) {
            SlideView view = views[currentViewNum];
            if (view != null) {
                view.onShow();
            }
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 6) {
            checkPermissions = false;
            if (currentViewNum == 0) {
                views[currentViewNum].onNextPressed(null);
            }
        } else if (requestCode == 7) {
            checkShowPermissions = false;
            if (currentViewNum == 0) {
                ((PhoneView) views[currentViewNum]).fillNumber();
            }
        }
    }

    private Bundle loadCurrentState() {
        if (newAccount) {
            return null;
        }
        try {
            Bundle bundle = new Bundle();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            Map<String, ?> params = preferences.getAll();
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String[] args = key.split("_\\|_");
                if (args.length == 1) {
                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    }
                } else if (args.length == 2) {
                    Bundle inner = bundle.getBundle(args[0]);
                    if (inner == null) {
                        inner = new Bundle();
                        bundle.putBundle(args[0], inner);
                    }
                    if (value instanceof String) {
                        inner.putString(args[1], (String) value);
                    } else if (value instanceof Integer) {
                        inner.putInt(args[1], (Integer) value);
                    }
                }
            }
            return bundle;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void clearCurrentState() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object obj = bundle.get(key);
            if (obj instanceof String) {
                if (prefix != null) {
                    editor.putString(prefix + "_|_" + key, (String) obj);
                } else {
                    editor.putString(key, (String) obj);
                }
            } else if (obj instanceof Integer) {
                if (prefix != null) {
                    editor.putInt(prefix + "_|_" + key, (Integer) obj);
                } else {
                    editor.putInt(key, (Integer) obj);
                }
            } else if (obj instanceof Bundle) {
                putBundleToEditor((Bundle) obj, editor, key);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (dialog == permissionsDialog && !permissionsItems.isEmpty() && getParentActivity() != null) {
                try {
                    getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                } catch (Exception ignore) {

                }
            } else if (dialog == permissionsShowDialog && !permissionsShowItems.isEmpty() && getParentActivity() != null) {
                AndroidUtilities.runOnUIThread(() -> needRequestPermissions = false, 200);
                try {
                    getParentActivity().requestPermissions(permissionsShowItems.toArray(new String[0]), 7);
                } catch (Exception ignore) {

                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (currentViewNum == 0) {
            for (int a = 0; a < views.length; a++) {
                if (views[a] != null) {
                    views[a].onDestroyActivity();
                }
            }
            clearCurrentState();
            return true;
        } else if (currentViewNum == 6) {
            views[currentViewNum].onBackPressed(true);
            setPage(0, true, null, true);
        } else if (currentViewNum == 7 || currentViewNum == 8) {
            views[currentViewNum].onBackPressed(true);
            setPage(6, true, null, true);
        } else if ((currentViewNum >= 1 && currentViewNum <= 4) || currentViewNum == AUTH_TYPE_MISSED_CALL) {
            if (views[currentViewNum].onBackPressed(false)) {
                setPage(0, true, null, true);
            }
        } else if (currentViewNum == 5) {
            ((LoginActivityRegisterView) views[currentViewNum]).wrongNumber.callOnClick();
        } else if (currentViewNum == 9) {
            views[currentViewNum].onBackPressed(true);
            setPage(7, true, null, true);
        } else if (currentViewNum == 10) {
            views[currentViewNum].onBackPressed(true);
            setPage(9, true, null, true);
        }
        return false;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        LoginActivityRegisterView registerView = (LoginActivityRegisterView) views[5];
        if (registerView != null) {
            registerView.imageUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void needShowAlert(String title, String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    private void onFieldError(View view) {
        try {
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
        } catch (Throwable ignore) {

        }
        AndroidUtilities.shakeView(view, 2, 0);
    }

    public static void needShowInvalidAlert(BaseFragment fragment, final String phoneNumber, final boolean banned) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        if (banned) {
            builder.setMessage(LocaleController.getString("BannedPhoneNumber", R.string.BannedPhoneNumber));
        } else {
            builder.setMessage(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
        }
        builder.setNeutralButton(LocaleController.getString("BotHelp", R.string.BotHelp), (dialog, which) -> {
            try {
                PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                Intent mailer = new Intent(Intent.ACTION_SENDTO);
                mailer.setData(Uri.parse("mailto:"));
                mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{banned ? "recover@telegram.org" : "login@stel.com"});
                if (banned) {
                    mailer.putExtra(Intent.EXTRA_SUBJECT, "Banned phone number: " + phoneNumber);
                    mailer.putExtra(Intent.EXTRA_TEXT, "I'm trying to use my mobile phone number: " + phoneNumber + "\nBut Telegram says it's banned. Please help.\n\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault());
                } else {
                    mailer.putExtra(Intent.EXTRA_SUBJECT, "Invalid phone number: " + phoneNumber);
                    mailer.putExtra(Intent.EXTRA_TEXT, "I'm trying to use my mobile phone number: " + phoneNumber + "\nBut Telegram says it's invalid. Please help.\n\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault());
                }
                fragment.getParentActivity().startActivity(Intent.createChooser(mailer, "Send email..."));
            } catch (Exception e) {
                AlertDialog.Builder builder2 = new AlertDialog.Builder(fragment.getParentActivity());
                builder2.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder2.setMessage(LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                builder2.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                fragment.showDialog(builder2.create());
            }
        });
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create());
    }

    private void showDoneButton(boolean show, boolean animated) {
        final boolean floating = currentDoneType == 0;
        if (doneButtonVisible[currentDoneType] == show) {
            return;
        }
        if (showDoneAnimation[currentDoneType] != null) {
            if (animated) {
                showDoneAnimation[currentDoneType].removeAllListeners();
            }
            showDoneAnimation[currentDoneType].cancel();
        }
        doneButtonVisible[currentDoneType] = show;
        if (animated) {
            showDoneAnimation[currentDoneType] = new AnimatorSet();
            if (show) {
                if (floating) {
                    if (floatingButtonContainer.getVisibility() != View.VISIBLE) {
                        floatingButtonContainer.setTranslationY(AndroidUtilities.dpf2(70f));
                        floatingButtonContainer.setVisibility(View.VISIBLE);
                    }
                    showDoneAnimation[currentDoneType].play(ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y, 0f));
                } else {
                    doneItem.setVisibility(View.VISIBLE);
                    showDoneAnimation[currentDoneType].playTogether(
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(doneItem, View.ALPHA, 1.0f));
                }
            } else {
                if (floating) {
                    showDoneAnimation[currentDoneType].play(ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y, AndroidUtilities.dpf2(70f)));
                } else {
                    showDoneAnimation[currentDoneType].playTogether(
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(doneItem, View.ALPHA, 0.0f));
                }
            }
            showDoneAnimation[currentDoneType].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (showDoneAnimation[floating ? 0 : 1] != null && showDoneAnimation[floating ? 0 : 1].equals(animation)) {
                        if (!show) {
                            if (floating) {
                                floatingButtonContainer.setVisibility(View.GONE);
                            } else {
                                doneItem.setVisibility(View.GONE);
                            }
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (showDoneAnimation[floating ? 0 : 1] != null && showDoneAnimation[floating ? 0 : 1].equals(animation)) {
                        showDoneAnimation[floating ? 0 : 1] = null;
                    }
                }
            });
            final int duration;
            final Interpolator interpolator;
            if (floating) {
                if (show) {
                    duration = 200;
                    interpolator = AndroidUtilities.decelerateInterpolator;
                } else {
                    duration = 150;
                    interpolator = AndroidUtilities.accelerateInterpolator;
                }
            } else {
                duration = 150;
                interpolator = null;
            }
            showDoneAnimation[currentDoneType].setDuration(duration);
            showDoneAnimation[currentDoneType].setInterpolator(interpolator);
            showDoneAnimation[currentDoneType].start();
        } else {
            if (show) {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setTranslationY(0f);
                } else {
                    doneItem.setVisibility(View.VISIBLE);
                    doneItem.setScaleX(1.0f);
                    doneItem.setScaleY(1.0f);
                    doneItem.setAlpha(1.0f);
                }
            } else {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.GONE);
                    floatingButtonContainer.setTranslationY(AndroidUtilities.dpf2(70f));
                } else {
                    doneItem.setVisibility(View.GONE);
                    doneItem.setScaleX(0.1f);
                    doneItem.setScaleY(0.1f);
                    doneItem.setAlpha(0.0f);
                }
            }
        }
    }

    private void onDoneButtonPressed() {
        if (!doneButtonVisible[currentDoneType]) {
            return;
        }
        if (doneProgressView.getTag() != null) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.getString("StopLoading", R.string.StopLoading));
            builder.setPositiveButton(LocaleController.getString("WaitMore", R.string.WaitMore), null);
            builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                views[currentViewNum].onCancelPressed();
                needHideProgress(true);
            });
            showDialog(builder.create());
        } else {
            views[currentViewNum].onNextPressed(null);
        }
    }

    private void showEditDoneProgress(final boolean show, boolean animated) {
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        final boolean floating = currentDoneType == 0;
        if (animated) {
            doneItemAnimation = new AnimatorSet();
            if (show) {
                doneProgressView.setTag(1);
                if (floating) {
                    floatingProgressView.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(false);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.ALPHA, 1.0f));
                } else {
                    doneProgressView.setVisibility(View.VISIBLE);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(doneProgressView, View.ALPHA, 1.0f));
                }
            } else {
                doneProgressView.setTag(null);
                if (floating) {
                    floatingButtonIcon.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(true);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.ALPHA, 1.0f));
                } else {
                    doneItem.getContentView().setVisibility(View.VISIBLE);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(doneProgressView, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 1.0f));
                }
            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (floating) {
                            if (!show) {
                                floatingProgressView.setVisibility(View.INVISIBLE);
                            } else {
                                floatingButtonIcon.setVisibility(View.INVISIBLE);
                            }
                        } else {
                            if (!show) {
                                doneProgressView.setVisibility(View.INVISIBLE);
                            } else {
                                doneItem.getContentView().setVisibility(View.INVISIBLE);
                            }
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        } else {
            if (show) {
                if (floating) {
                    floatingProgressView.setVisibility(View.VISIBLE);
                    floatingButtonIcon.setVisibility(View.INVISIBLE);
                    floatingButtonContainer.setEnabled(false);
                    floatingButtonIcon.setScaleX(0.1f);
                    floatingButtonIcon.setScaleY(0.1f);
                    floatingButtonIcon.setAlpha(0.0f);
                    floatingProgressView.setScaleX(1.0f);
                    floatingProgressView.setScaleY(1.0f);
                    floatingProgressView.setAlpha(1.0f);
                } else {
                    doneProgressView.setVisibility(View.VISIBLE);
                    doneItem.getContentView().setVisibility(View.INVISIBLE);
                    doneItem.getContentView().setScaleX(0.1f);
                    doneItem.getContentView().setScaleY(0.1f);
                    doneItem.getContentView().setAlpha(0.0f);
                    doneProgressView.setScaleX(1.0f);
                    doneProgressView.setScaleY(1.0f);
                    doneProgressView.setAlpha(1.0f);
                }
            } else {
                doneProgressView.setTag(null);
                if (floating) {
                    floatingProgressView.setVisibility(View.INVISIBLE);
                    floatingButtonIcon.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(true);
                    floatingProgressView.setScaleX(0.1f);
                    floatingProgressView.setScaleY(0.1f);
                    floatingProgressView.setAlpha(0.0f);
                    floatingButtonIcon.setScaleX(1.0f);
                    floatingButtonIcon.setScaleY(1.0f);
                    floatingButtonIcon.setAlpha(1.0f);
                } else {
                    doneItem.getContentView().setVisibility(View.VISIBLE);
                    doneProgressView.setVisibility(View.INVISIBLE);
                    doneProgressView.setScaleX(0.1f);
                    doneProgressView.setScaleY(0.1f);
                    doneProgressView.setAlpha(0.0f);
                    doneItem.getContentView().setScaleX(1.0f);
                    doneItem.getContentView().setScaleY(1.0f);
                    doneItem.getContentView().setAlpha(1.0f);
                }
            }
        }
    }

    private void needShowProgress(final int reqiestId) {
        needShowProgress(reqiestId, true);
    }

    private void needShowProgress(final int reqiestId, boolean animated) {
        progressRequestId = reqiestId;
        showEditDoneProgress(true, animated);
    }

    private void needHideProgress(boolean cancel) {
        needHideProgress(cancel, true);
    }

    private void needHideProgress(boolean cancel, boolean animated) {
        if (progressRequestId != 0) {
            if (cancel) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(progressRequestId, true);
            }
            progressRequestId = 0;
        }
        showEditDoneProgress(false, animated);
    }

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        final boolean needFloatingButton = page == 0 || page == 5 || page == 6 || page == 7 || page == 9 || page == 10 || page == 11;
        if (needFloatingButton) {
            if (page == 0) {
                checkPermissions = true;
                checkShowPermissions = true;
            }
            currentDoneType = DONE_TYPE_ACTION;
            showDoneButton(false, animated);
            currentDoneType = DONE_TYPE_FLOATING;
            showEditDoneProgress(false, false);
            if (!animated) {
                showDoneButton(true, false);
            }
        } else {
            currentDoneType = DONE_TYPE_FLOATING;
            showDoneButton(false, animated);
            if (page != 8) {
                currentDoneType = DONE_TYPE_ACTION;
            }
        }
        if (animated) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;
            actionBar.setBackButtonImage(newView.needBackButton() || newAccount ? R.drawable.ic_ab_back : 0);

            newView.setParams(params, false);
            actionBar.setTitle(newView.getHeaderName());
            setParentActivityTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);
            newView.setVisibility(View.VISIBLE);

            AnimatorSet pagesAnimation = new AnimatorSet();
            pagesAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentDoneType == DONE_TYPE_FLOATING && needFloatingButton) {
                        showDoneButton(true, true);
                    }
                    outView.setVisibility(View.GONE);
                    outView.setX(0);
                }
            });
            pagesAnimation.playTogether(
                    ObjectAnimator.ofFloat(outView, View.TRANSLATION_X, back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x),
                    ObjectAnimator.ofFloat(newView, View.TRANSLATION_X, 0));
            pagesAnimation.setDuration(300);
            pagesAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            pagesAnimation.start();
        } else {
            actionBar.setBackButtonImage(views[page].needBackButton() || newAccount ? R.drawable.ic_ab_back : 0);
            views[currentViewNum].setVisibility(View.GONE);
            currentViewNum = page;
            views[page].setParams(params, false);
            views[page].setVisibility(View.VISIBLE);
            actionBar.setTitle(views[page].getHeaderName());
            setParentActivityTitle(views[page].getHeaderName());
            views[page].onShow();
        }
    }

    @Override
    public void saveSelfArgs(Bundle outState) {
        try {
            Bundle bundle = new Bundle();
            bundle.putInt("currentViewNum", currentViewNum);
            bundle.putInt("syncContacts", syncContacts ? 1 : 0);
            for (int a = 0; a <= currentViewNum; a++) {
                SlideView v = views[a];
                if (v != null) {
                    v.saveStateParams(bundle);
                }
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            putBundleToEditor(bundle, editor, null);
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void needFinishActivity(boolean afterSignup, boolean showSetPasswordConfirm, int otherwiseRelogin) {
        clearCurrentState();
        if (getParentActivity() instanceof LaunchActivity) {
            if (newAccount) {
                newAccount = false;
                ((LaunchActivity) getParentActivity()).switchToAccount(currentAccount, false);
                finishFragment();
            } else {

                if (afterSignup && showSetPasswordConfirm) {
                    TwoStepVerificationSetupActivity twoStepVerification = new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null);
                    twoStepVerification.setBlockingAlert(otherwiseRelogin);
                    presentFragment(twoStepVerification, true);
                } else {
                    final Bundle args = new Bundle();
                    args.putBoolean("afterSignup", afterSignup);
                    DialogsActivity dialogsActivity = new DialogsActivity(args);
                    presentFragment(dialogsActivity, true);
                }

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            }
        } else if (getParentActivity() instanceof ExternalActionActivity) {
            ((ExternalActionActivity) getParentActivity()).onFinishLogin();
        }
    }

    private void onAuthSuccess(TLRPC.TL_auth_authorization res) {
        onAuthSuccess(res, false);
    }

    private void onAuthSuccess(TLRPC.TL_auth_authorization res, boolean afterSignup) {
        ConnectionsManager.getInstance(currentAccount).setUserId(res.user.id);
        UserConfig.getInstance(currentAccount).clearConfig();
        MessagesController.getInstance(currentAccount).cleanup();
        UserConfig.getInstance(currentAccount).syncContacts = syncContacts;
        UserConfig.getInstance(currentAccount).setCurrentUser(res.user);
        UserConfig.getInstance(currentAccount).saveConfig(true);
        MessagesStorage.getInstance(currentAccount).cleanup(true);
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(res.user);
        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true);
        MessagesController.getInstance(currentAccount).putUser(res.user, false);
        ContactsController.getInstance(currentAccount).checkAppAccount();
        MessagesController.getInstance(currentAccount).checkPromoInfo(true);
        ConnectionsManager.getInstance(currentAccount).updateDcSettings();
        needFinishActivity(afterSignup, res.setup_password_required, res.otherwise_relogin_days);
    }

    private void fillNextCodeParams(Bundle params, TLRPC.TL_auth_sentCode res) {
        params.putString("phoneHash", res.phone_code_hash);
        if (res.next_type instanceof TLRPC.TL_auth_codeTypeCall) {
            params.putInt("nextType", AUTH_TYPE_CALL);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeFlashCall) {
            params.putInt("nextType", AUTH_TYPE_FLASH_CALL);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeSms) {
            params.putInt("nextType", AUTH_TYPE_SMS);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeMissedCall) {
            params.putInt("nextType", AUTH_TYPE_MISSED_CALL);
        }
        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeApp) {
            params.putInt("type", 1);
            params.putInt("length", res.type.length);
            setPage(1, true, params, false);
        } else {
            if (res.timeout == 0) {
                res.timeout = 60;
            }
            params.putInt("timeout", res.timeout * 1000);
            if (res.type instanceof TLRPC.TL_auth_sentCodeTypeCall) {
                params.putInt("type", 4);
                params.putInt("length", res.type.length);
                setPage(4, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFlashCall) {
                params.putInt("type", 3);
                params.putString("pattern", res.type.pattern);
                setPage(3, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSms) {
                params.putInt("type", 2);
                params.putInt("length", res.type.length);
                setPage(2, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeMissedCall) {
                params.putInt("type", AUTH_TYPE_MISSED_CALL);
                params.putInt("length", res.type.length);
                params.putString("prefix", res.type.prefix);
                setPage(AUTH_TYPE_MISSED_CALL, true, params, false);
            }
        }
    }

    private TLRPC.TL_help_termsOfService currentTermsOfService;

    public class PhoneView extends SlideView implements AdapterView.OnItemSelectedListener {

        private EditTextBoldCursor codeField;
        private HintEditText phoneField;
        private TextView countryButton;
        private View view;
        private TextView textView;
        private TextView textView2;
        private CheckBoxCell checkBoxCell;
        private CheckBoxCell testBackendCheckBox;

        private int countryState = 0;

        private ArrayList<CountrySelectActivity.Country> countriesArray = new ArrayList<>();
        private HashMap<String, CountrySelectActivity.Country> codesMap = new HashMap<>();
        private HashMap<String, String> phoneFormatMap = new HashMap<>();

        private boolean ignoreSelection = false;
        private boolean ignoreOnTextChange = false;
        private boolean ignoreOnPhoneChange = false;
        private boolean nextPressed = false;

        public PhoneView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            countryButton = new TextView(context);
            countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            countryButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            countryButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            countryButton.setMaxLines(1);
            countryButton.setSingleLine(true);
            countryButton.setEllipsize(TextUtils.TruncateAt.END);
            countryButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            countryButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7));
            addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 0, 14));
            countryButton.setOnClickListener(view -> {
                CountrySelectActivity fragment = new CountrySelectActivity(true, countriesArray);
                fragment.setCountrySelectActivityDelegate((country) -> {
                    selectCountry(country);
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(phoneField), 300);
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                });
                presentFragment(fragment);
            });

            view = new View(context);
            view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayLine));
            addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 4, -17.5f, 4, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

            textView = new TextView(context);
            textView.setText("+");
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            codeField = new EditTextBoldCursor(context);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(5);
            codeField.setFilters(inputFilters);
            linearLayout.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 16, 0));
            codeField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (ignoreOnTextChange) {
                        return;
                    }
                    ignoreOnTextChange = true;
                    String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
                    codeField.setText(text);
                    if (text.length() == 0) {
                        countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                        phoneField.setHintText(null);
                        countryState = 1;
                    } else {
                        CountrySelectActivity.Country country;
                        boolean ok = false;
                        String textToSet = null;
                        if (text.length() > 4) {
                            for (int a = 4; a >= 1; a--) {
                                String sub = text.substring(0, a);
                                country = codesMap.get(sub);
                                if (country != null) {
                                    ok = true;
                                    textToSet = text.substring(a) + phoneField.getText().toString();
                                    codeField.setText(text = sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                textToSet = text.substring(1) + phoneField.getText().toString();
                                codeField.setText(text = text.substring(0, 1));
                            }
                        }
                        country = codesMap.get(text);
                        if (country != null) {
                            ignoreSelection = true;
                            countryButton.setText(country.name);
                            String hint = phoneFormatMap.get(text);
                            phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
                            countryState = 0;
                        } else {
                            countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                            phoneField.setHintText(null);
                            countryState = 2;
                        }
                        if (!ok) {
                            codeField.setSelection(codeField.getText().length());
                        }
                        if (textToSet != null) {
                            phoneField.requestFocus();
                            phoneField.setText(textToSet);
                            phoneField.setSelection(phoneField.length());
                        }
                    }
                    ignoreOnTextChange = false;
                }
            });
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                    return true;
                }
                return false;
            });

            phoneField = new HintEditText(context) {

                @Override
                public boolean onKeyDown(int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && phoneField.length() == 0) {
                        codeField.requestFocus();
                        codeField.setSelection(codeField.length());
                        codeField.dispatchKeyEvent(event);
                    }
                    return super.onKeyDown(keyCode, event);
                }


                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!AndroidUtilities.showKeyboard(this)) {
                            clearFocus();
                            requestFocus();
                        }
                    }
                    return super.onTouchEvent(event);
                }
            };
            phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
            phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            phoneField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            phoneField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            phoneField.setPadding(0, 0, 0, 0);
            phoneField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            phoneField.setCursorSize(AndroidUtilities.dp(20));
            phoneField.setCursorWidth(1.5f);
            phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            phoneField.setMaxLines(1);
            phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            linearLayout.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
            phoneField.addTextChangedListener(new TextWatcher() {

                private int characterAction = -1;
                private int actionPosition;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (count == 0 && after == 1) {
                        characterAction = 1;
                    } else if (count == 1 && after == 0) {
                        if (s.charAt(start) == ' ' && start > 0) {
                            characterAction = 3;
                            actionPosition = start - 1;
                        } else {
                            characterAction = 2;
                        }
                    } else {
                        characterAction = -1;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreOnPhoneChange) {
                        return;
                    }
                    int start = phoneField.getSelectionStart();
                    String phoneChars = "0123456789";
                    String str = phoneField.getText().toString();
                    if (characterAction == 3) {
                        str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
                        start--;
                    }
                    StringBuilder builder = new StringBuilder(str.length());
                    for (int a = 0; a < str.length(); a++) {
                        String ch = str.substring(a, a + 1);
                        if (phoneChars.contains(ch)) {
                            builder.append(ch);
                        }
                    }
                    ignoreOnPhoneChange = true;
                    String hint = phoneField.getHintText();
                    if (hint != null) {
                        for (int a = 0; a < builder.length(); a++) {
                            if (a < hint.length()) {
                                if (hint.charAt(a) == ' ') {
                                    builder.insert(a, ' ');
                                    a++;
                                    if (start == a && characterAction != 2 && characterAction != 3) {
                                        start++;
                                    }
                                }
                            } else {
                                builder.insert(a, ' ');
                                if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                    start++;
                                }
                                break;
                            }
                        }
                    }
                    s.replace(0, s.length(), builder);
                    if (start >= 0) {
                        phoneField.setSelection(Math.min(start, phoneField.length()));
                    }
                    phoneField.onTextChange();
                    ignoreOnPhoneChange = false;
                }
            });
            phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            textView2 = new TextView(context);
            textView2.setText(LocaleController.getString("StartText", R.string.StartText));
            textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView2.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView2.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 28, 0, 10));

            TextView textViewProxy = new TextView(context);
            textViewProxy.setText("You may need to set up a proxy before you login, in case your country or ISP blocks Telegram");
            textViewProxy.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            textViewProxy.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textViewProxy.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textViewProxy.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textViewProxy, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 28, 0, 10));
            TextView addProxyButton = new TextView(context);
            addProxyButton.setText("SET A PROXY");
            addProxyButton.setGravity(Gravity.CENTER);
            addProxyButton.setTextColor(0xffffffff);
            addProxyButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addProxyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addProxyButton.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), 0xff50a8eb, 0xff439bde));
            addProxyButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            addView(addProxyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 10, 0, 10, 10));
            addProxyButton.setOnClickListener(view -> presentFragment(new ProxyListActivity()));

            if (true) {
                checkBoxCell = new CheckBoxCell(context, 2);
                checkBoxCell.setText(LocaleController.getString("SyncContacts", R.string.SyncContacts), "", syncContacts, false);
                addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                checkBoxCell.setOnClickListener(new OnClickListener() {

                    private Toast visibleToast;

                    @Override
                    public void onClick(View v) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        CheckBoxCell cell = (CheckBoxCell) v;
                        syncContacts = !syncContacts;
                        cell.setChecked(syncContacts, true);
                        try {
                            if (visibleToast != null) {
                                visibleToast.cancel();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (syncContacts) {
                            BulletinFactory.of((FrameLayout) fragmentView, null).createSimpleBulletin(R.raw.contacts_sync_on, LocaleController.getString("SyncContactsOn", R.string.SyncContactsOn)).show();
                        } else {
                            BulletinFactory.of((FrameLayout) fragmentView, null).createSimpleBulletin(R.raw.contacts_sync_off, LocaleController.getString("SyncContactsOff", R.string.SyncContactsOff)).show();
                        }
                    }
                });
            }

            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                testBackendCheckBox = new CheckBoxCell(context, 2);
                testBackendCheckBox.setText("Test Backend", "", testBackend, false);
                addView(testBackendCheckBox, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                testBackendCheckBox.setOnClickListener(v -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    CheckBoxCell cell = (CheckBoxCell) v;
                    testBackend = !testBackend;
                    cell.setChecked(testBackend, true);
                });
            }

            HashMap<String, String> languageMap = new HashMap<>();

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().getAssets().open("countries.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split(";");
                    CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                    countryWithCode.name = args[2];
                    countryWithCode.code = args[0];
                    countryWithCode.shortname = args[1];
                    countriesArray.add(0, countryWithCode);
                    codesMap.put(args[0], countryWithCode);
                    if (args.length > 3) {
                        phoneFormatMap.put(args[0], args[3]);
                    }
                    languageMap.put(args[1], args[2]);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e(e);
            }

            Collections.sort(countriesArray, Comparator.comparing(o -> o.name));

            String country = null;

            try {
                TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    country = null;//telephonyManager.getSimCountryIso().toUpperCase();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (country != null) {
                setCountry(languageMap, country.toUpperCase());
            } else {
                TLRPC.TL_help_getNearestDc req = new TLRPC.TL_help_getNearestDc();
                getAccountInstance().getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response == null) {
                        return;
                    }
                    TLRPC.TL_nearestDc res = (TLRPC.TL_nearestDc) response;
                    if (codeField.length() == 0) {
                        setCountry(languageMap, res.country.toUpperCase());
                    }
                }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
            }
            if (codeField.length() == 0) {
                countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                phoneField.setHintText(null);
                countryState = 1;
            }

            if (codeField.length() != 0) {
                phoneField.requestFocus();
                phoneField.setSelection(phoneField.length());
            } else {
                codeField.requestFocus();
            }

            TLRPC.TL_help_getCountriesList req = new TLRPC.TL_help_getCountriesList();
            req.lang_code = "";
            getConnectionsManager().sendRequest(req, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        countriesArray.clear();
                        codesMap.clear();
                        phoneFormatMap.clear();
                        TLRPC.TL_help_countriesList help_countriesList = (TLRPC.TL_help_countriesList) response;
                        for (int i = 0; i < help_countriesList.countries.size(); i++) {
                            TLRPC.TL_help_country c = help_countriesList.countries.get(i);
                            for (int k = 0; k < c.country_codes.size(); k++) {
                                CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                                countryWithCode.name = c.default_name;
                                countryWithCode.code = c.country_codes.get(k).country_code;

                                countriesArray.add(countryWithCode);
                                codesMap.put(c.country_codes.get(k).country_code, countryWithCode);
                                if (c.country_codes.get(k).patterns.size() > 0) {
                                    phoneFormatMap.put(c.country_codes.get(k).country_code, c.country_codes.get(k).patterns.get(0));
                                }
                            }
                        }
                    }
                });
            }, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        public void selectCountry(CountrySelectActivity.Country country) {
            ignoreOnTextChange = true;
            String code = country.code;
            codeField.setText(code);
            countryButton.setText(country.name);
            String hint = phoneFormatMap.get(code);
            phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
            countryState = 0;
            ignoreOnTextChange = false;
        }

        private void setCountry(HashMap<String, String> languageMap, String country) {
            String name = languageMap.get(country);
            if (name != null && countriesArray != null) {
                CountrySelectActivity.Country countryWithCode = null;
                for (int i = 0; i < countriesArray.size(); i++) {
                    if (countriesArray.get(i) != null && countriesArray.get(i).name.equals(country)) {
                        countryWithCode = countriesArray.get(i);
                        break;
                    }
                }
                if (countryWithCode != null) {
                    codeField.setText(countryWithCode.code);
                    countryState = 0;
                }
            }
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (ignoreSelection) {
                ignoreSelection = false;
                return;
            }
            ignoreOnTextChange = true;
            CountrySelectActivity.Country countryWithCode = countriesArray.get(i);
            codeField.setText(countryWithCode.code);
            ignoreOnTextChange = false;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed(String code) {
            if (getParentActivity() == null || nextPressed) {
                return;
            }
            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("sim status = " + tm.getSimState());
            }
            int state = tm.getSimState();
            boolean simcardAvailable = state != TelephonyManager.SIM_STATE_ABSENT && state != TelephonyManager.SIM_STATE_UNKNOWN && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE && !AndroidUtilities.isAirplaneModeOn();
            boolean allowCall = true;
            boolean allowCancelCall = true;
            boolean allowReadCallLog = true;
            boolean allowReadPhoneNumbers = true;
            if (Build.VERSION.SDK_INT >= 23 && simcardAvailable) {
                allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                allowCancelCall = getParentActivity().checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
                allowReadCallLog = Build.VERSION.SDK_INT < 28 || getParentActivity().checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    allowReadPhoneNumbers = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
                }
                if (checkPermissions) {
                    permissionsItems.clear();
                    if (!allowCall) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                    }
                    if (!allowCancelCall) {
                        permissionsItems.add(Manifest.permission.CALL_PHONE);
                    }
                    if (!allowReadCallLog) {
                        permissionsItems.add(Manifest.permission.READ_CALL_LOG);
                    }
                    if (!allowReadPhoneNumbers) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_NUMBERS);
                    }
                    boolean ok = true;
                    if (!permissionsItems.isEmpty()) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        if (preferences.getBoolean("firstlogin", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                            preferences.edit().putBoolean("firstlogin", false).commit();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                            builder.setPositiveButton(LocaleController.getString("Contin", R.string.Continue), null);
                            int resId;
                            if (!allowCall && (!allowCancelCall || !allowReadCallLog)) {
                                builder.setMessage(LocaleController.getString("AllowReadCallAndLog", R.string.AllowReadCallAndLog));
                                resId = R.raw.calls_log;
                            } else if (!allowCancelCall || !allowReadCallLog) {
                                builder.setMessage(LocaleController.getString("AllowReadCallLog", R.string.AllowReadCallLog));
                                resId = R.raw.calls_log;
                            } else {
                                builder.setMessage(LocaleController.getString("AllowReadCall", R.string.AllowReadCall));
                                resId = R.raw.incoming_calls;
                            }
                            builder.setTopAnimation(resId, 46, false, Theme.getColor(Theme.key_dialogTopBackground));
                            permissionsDialog = showDialog(builder.create());
                        } else {
                            try {
                                getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                            } catch (Exception ignore) {
                                ok = false;
                            }
                        }
                        if (ok) {
                            return;
                        }
                    }
                }
            }

            if (countryState == 1) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                return;
            } else if (countryState == 2 && !BuildVars.DEBUG_VERSION) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("WrongCountry", R.string.WrongCountry));
                return;
            }
            if (codeField.length() == 0) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                return;
            }
            if (phoneField.length() == 0) {
                onFieldError(phoneField);
                return;
            }
            String phone = PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
            boolean isTestBakcend = BuildVars.DEBUG_PRIVATE_VERSION && getConnectionsManager().isTestBackend();
            if (isTestBakcend != testBackend) {
                getConnectionsManager().switchBackend(false);
                isTestBakcend = testBackend;
            }
            if (getParentActivity() instanceof LaunchActivity) {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    UserConfig userConfig = UserConfig.getInstance(a);
                    if (!userConfig.isClientActivated()) {
                        continue;
                    }
                    String userPhone = userConfig.getCurrentUser().phone;
                    if (PhoneNumberUtils.compare(phone, userPhone) && ConnectionsManager.getInstance(a).isTestBackend() == isTestBakcend) {
                        final int num = a;
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("AccountAlreadyLoggedIn", R.string.AccountAlreadyLoggedIn));
                        builder.setPositiveButton(LocaleController.getString("AccountSwitch", R.string.AccountSwitch), (dialog, which) -> {
                            if (UserConfig.selectedAccount != num) {
                                ((LaunchActivity) getParentActivity()).switchToAccount(num, false);
                            }
                            finishFragment();
                        });
                        builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                }
            }

            ConnectionsManager.getInstance(currentAccount).cleanup(false);
            final TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
            req.api_hash = BuildVars.APP_HASH;
            req.api_id = BuildVars.APP_ID;
            req.phone_number = phone;
            req.settings = new TLRPC.TL_codeSettings();
            req.settings.allow_flashcall = simcardAvailable && allowCall && allowCancelCall && allowReadCallLog;
            req.settings.allow_missed_call = simcardAvailable && allowCall;
            req.settings.allow_app_hash = ApplicationLoader.hasPlayServices;
            ArrayList<TLRPC.TL_auth_loggedOut> tokens = MessagesController.getSavedLogOutTokens();
            if (tokens != null) {
                for (int i = 0; i < tokens.size(); i++) {
                    if (req.settings.logout_tokens == null) {
                        req.settings.logout_tokens = new ArrayList<>();
                    }
                    req.settings.logout_tokens.add(tokens.get(i).future_auth_token);
                }
                MessagesController.saveLogOutTokens(tokens);
            }
            if (req.settings.logout_tokens != null) {
                req.settings.flags |= 64;
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            if (req.settings.allow_app_hash) {
                preferences.edit().putString("sms_hash", BuildVars.SMS_HASH).commit();
            } else {
                preferences.edit().remove("sms_hash").commit();
            }
            if (req.settings.allow_flashcall) {
                try {
                    String number = tm.getLine1Number();
                    if (!TextUtils.isEmpty(number)) {
                        req.settings.current_number = PhoneNumberUtils.compare(phone, number);
                        if (!req.settings.current_number) {
                            req.settings.allow_flashcall = false;
                        }
                    } else {
                        if (UserConfig.getActivatedAccountsCount() > 0) {
                            req.settings.allow_flashcall = false;
                        } else {
                            req.settings.current_number = false;
                        }
                    }
                } catch (Exception e) {
                    req.settings.allow_flashcall = false;
                    FileLog.e(e);
                }
            }
            final Bundle params = new Bundle();
            params.putString("phone", "+" + codeField.getText() + " " + phoneField.getText());
            try {
                params.putString("ephone", "+" + PhoneFormat.stripExceptNumbers(codeField.getText().toString()) + " " + PhoneFormat.stripExceptNumbers(phoneField.getText().toString()));
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("ephone", "+" + phone);
            }
            params.putString("phoneFormated", phone);
            nextPressed = true;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    if (error.text != null) {
                        if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                            TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                nextPressed = false;
                                showDoneButton(false, true);
                                if (error1 == null) {
                                    TLRPC.TL_account_password password = (TLRPC.TL_account_password) response1;
                                    if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                        AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                        return;
                                    }
                                    Bundle bundle = new Bundle();
                                    SerializedData data = new SerializedData(password.getObjectSize());
                                    password.serializeToStream(data);
                                    bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                    setPage(6, true, bundle, false);
                                } else {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), error1.text);
                                }
                            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        } else if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowInvalidAlert(LoginActivity.this, req.phone_number, false);
                        } else if (error.text.contains("PHONE_PASSWORD_FLOOD")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                        } else if (error.text.contains("PHONE_NUMBER_FLOOD")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("PhoneNumberFlood", R.string.PhoneNumberFlood));
                        } else if (error.text.contains("PHONE_NUMBER_BANNED")) {
                            needShowInvalidAlert(LoginActivity.this, req.phone_number, true);
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                        } else if (error.code != -1000) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                }
                needHideProgress(false);
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagTryDifferentDc | ConnectionsManager.RequestFlagEnableUnauthorized);
            needShowProgress(reqId);
        }

        private boolean numberFilled;
        public void fillNumber() {
            if (numberFilled) {
                return;
            }
            try {
                TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE) {
                    boolean allowCall = true;
                    boolean allowReadPhoneNumbers = true;
                    if (Build.VERSION.SDK_INT >= 23) {
                        allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            allowReadPhoneNumbers = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
                        }
                        if (checkShowPermissions && (!allowCall || !allowReadPhoneNumbers)) {
                            permissionsShowItems.clear();
                            if (!allowCall) {
                                permissionsShowItems.add(Manifest.permission.READ_PHONE_STATE);
                            }
                            if (!allowReadPhoneNumbers) {
                                permissionsShowItems.add(Manifest.permission.READ_PHONE_NUMBERS);
                            }
                            if (!permissionsShowItems.isEmpty()) {
                                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                if (preferences.getBoolean("firstloginshow", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                                    preferences.edit().putBoolean("firstloginshow", false).commit();
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                                    builder.setTopAnimation(R.raw.incoming_calls, 46, false, Theme.getColor(Theme.key_dialogTopBackground));
                                    builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), null);
                                    builder.setMessage(LocaleController.getString("AllowFillNumber", R.string.AllowFillNumber));
                                    permissionsShowDialog = showDialog(builder.create());
                                    needRequestPermissions = true;
                                } else {
                                    getParentActivity().requestPermissions(permissionsShowItems.toArray(new String[0]), 7);
                                }
                            }
                            return;
                        }
                    }
                    numberFilled = true;
                    if (!newAccount && allowCall && allowReadPhoneNumbers) {
                        String number = PhoneFormat.stripExceptNumbers(tm.getLine1Number());
                        String textToSet = null;
                        boolean ok = false;
                        if (!TextUtils.isEmpty(number)) {
                            if (number.length() > 4) {
                                for (int a = 4; a >= 1; a--) {
                                    String sub = number.substring(0, a);
                                    CountrySelectActivity.Country country = codesMap.get(sub);
                                    if (country != null) {
                                        ok = true;
                                        textToSet = number.substring(a);
                                        codeField.setText(sub);
                                        break;
                                    }
                                }
                                if (!ok) {
                                    textToSet = number.substring(1);
                                    codeField.setText(number.substring(0, 1));
                                }
                            }
                            if (textToSet != null) {
                                phoneField.requestFocus();
                                phoneField.setText(textToSet);
                                phoneField.setSelection(phoneField.length());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onShow() {
            super.onShow();
            fillNumber();
            if (checkBoxCell != null) {
                checkBoxCell.setChecked(syncContacts, false);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (phoneField != null) {
                    if (needRequestPermissions) {
                        codeField.clearFocus();
                        phoneField.clearFocus();
                    } else {
                        if (codeField.length() != 0) {
                            phoneField.requestFocus();
                            phoneField.setSelection(phoneField.length());
                            AndroidUtilities.showKeyboard(phoneField);
                        } else {
                            codeField.requestFocus();
                            AndroidUtilities.showKeyboard(codeField);
                        }
                    }
                }
            }, 100);
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("YourPhone", R.string.YourPhone);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("phoneview_code", code);
            }
            String phone = phoneField.getText().toString();
            if (phone.length() != 0) {
                bundle.putString("phoneview_phone", phone);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            String code = bundle.getString("phoneview_code");
            if (code != null) {
                codeField.setText(code);
            }
            String phone = bundle.getString("phoneview_phone");
            if (phone != null) {
                phoneField.setText(phone);
            }
        }
    }

    public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {

        private String phone;
        private String phoneHash;
        private String requestPhone;
        private String emailPhone;
        private CodeFieldContainer codeFieldContainer;
        private TextView confirmTextView;
        private TextView titleTextView;
        private ImageView blackImageView;
        private RLottieImageView blueImageView;
        private TextView timeText;
        private TextView problemText;
        private Bundle currentParams;
        private ProgressView progressView;
        private TextView prefixTextView;

        RLottieDrawable hintDrawable;

        private Timer timeTimer;
        private Timer codeTimer;
        private int openTime;
        private final Object timerSync = new Object();
        private int time = 60000;
        private int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForEvent;
        private boolean nextPressed;
        private String lastError = "";
        private int currentType;
        private int nextType;
        private String pattern = "*";
        private String prefix = "";
        private String catchedPhone;
        private int length;

        public LoginActivitySmsView(Context context, final int type) {
            super(context);

            currentType = type;
            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

            if (currentType == AUTH_TYPE_MISSED_CALL) {
                titleTextView.setText(LocaleController.getString("MissedCallDescriptionTitle", R.string.MissedCallDescriptionTitle));

                FrameLayout frameLayout = new FrameLayout(context);
                ImageView iconView1 = new ImageView(context);
                ImageView iconView2 = new ImageView(context);
                frameLayout.addView(iconView1);
                frameLayout.addView(iconView2);

                iconView1.setImageResource(R.drawable.login_arrow1);
                iconView1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), PorterDuff.Mode.SRC_IN));
                iconView2.setImageResource(R.drawable.login_phone1);
                iconView2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));

                addView(frameLayout, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
                addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));

                TextView subtitleTextView2 = new TextView(context);
                subtitleTextView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                subtitleTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                subtitleTextView2.setGravity(Gravity.CENTER_HORIZONTAL);
                subtitleTextView2.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                subtitleTextView2.setText(AndroidUtilities.replaceTags(LocaleController.getString("MissedCallDescriptionSubtitle", R.string.MissedCallDescriptionSubtitle)));

                addView(subtitleTextView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 36, 16, 36, 0));

                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        onNextPressed(null);
                    }
                };

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                prefixTextView = new TextView(context);
                prefixTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                prefixTextView.setMaxLines(1);
                prefixTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                prefixTextView.setPadding(0, 0, 0, 0);
                prefixTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                prefixTextView.setGravity(Gravity.CENTER_VERTICAL);

                linearLayout.addView(prefixTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
                linearLayout.addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

                addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 34, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

                subtitleTextView2 = new TextView(context);
                subtitleTextView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                subtitleTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                subtitleTextView2.setGravity(Gravity.CENTER_HORIZONTAL);
                subtitleTextView2.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                subtitleTextView2.setText(AndroidUtilities.replaceTags(LocaleController.getString("MissedCallDescriptionSubtitle2", R.string.MissedCallDescriptionSubtitle2)));

                addView(subtitleTextView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 36, 28, 36, 12));


            } else if (currentType == 3) {
                confirmTextView.setGravity(Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.phone_activate);
                if (LocaleController.isRTL) {
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.LEFT | Gravity.CENTER_VERTICAL, 2, 2, 0, 0));
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 64 + 18, 0, 0, 0));
                } else {
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 64 + 18, 0));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 2, 0, 2));
                }
            } else {
                confirmTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

                if (currentType == 1) {
                    blackImageView = new ImageView(context);
                    blackImageView.setImageResource(R.drawable.sms_devices);
                    blackImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blackImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    blueImageView = new RLottieImageView(context);
                    blueImageView.setImageResource(R.drawable.sms_bubble);
                    blueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionBackground), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    titleTextView.setText(LocaleController.getString("SentAppCodeTitle", R.string.SentAppCodeTitle));
                } else {
                    blueImageView = new RLottieImageView(context);
                    hintDrawable = new RLottieDrawable(R.raw.sms_incoming_info, "" + R.raw.sms_incoming_info, AndroidUtilities.dp(64), AndroidUtilities.dp(64), true, null);
                    hintDrawable.setLayerColor("Bubble.**", Theme.getColor(Theme.key_chats_actionBackground));
                    hintDrawable.setLayerColor("Phone.**", Theme.getColor(Theme.key_chats_actionBackground));
                    blueImageView.setAnimation(hintDrawable);
                    frameLayout.addView(blueImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                    titleTextView.setText(LocaleController.getString("SentSmsCodeTitle", R.string.SentSmsCodeTitle));
                }
                addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));
                addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 17, 0, 0));
            }
            if (currentType != AUTH_TYPE_MISSED_CALL) {
                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        onNextPressed(null);
                    }
                };

                addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL));
            }
            if (currentType == 3) {
                codeFieldContainer.setVisibility(GONE);
            }

            timeText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            if (currentType == 3) {
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                progressView = new ProgressView(context);
                timeText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3, 0, 12, 0, 0));
            } else {
                timeText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                timeText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            }

            problemText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            problemText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            problemText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
            problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            problemText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            if (currentType == 1) {
                if (nextType == AUTH_TYPE_FLASH_CALL || nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                    problemText.setText(LocaleController.getString("DidNotGetTheCodPhone", R.string.DidNotGetTheCodePhone));
                } else {
                    problemText.setText(LocaleController.getString("DidNotGetTheCodeSms", R.string.DidNotGetTheCodeSms));
                }
            } else {
                problemText.setText(LocaleController.getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
            }
            addView(problemText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            problemText.setOnClickListener(v -> {
                if (nextPressed) {
                    return;
                }
                boolean email = nextType == 4 && currentType == 2 || nextType == 0;
                if (!email) {
                    if (doneProgressView.getTag() != null) {
                        return;
                    }
                    resendCode();
                } else {
                    try {
                        PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                        Intent mailer = new Intent(Intent.ACTION_SENDTO);
                        mailer.setData(Uri.parse("mailto:"));
                        mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"sms@telegram.org"});
                        mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + emailPhone);
                        mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + requestPhone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                        getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                    } catch (Exception e) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                    }
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (currentType != 3 && blueImageView != null) {
                int innerHeight = blueImageView.getMeasuredHeight() + titleTextView.getMeasuredHeight() + confirmTextView.getMeasuredHeight() + AndroidUtilities.dp(18 + 17);
                int requiredHeight = AndroidUtilities.dp(80);
                int maxHeight = AndroidUtilities.dp(291);
                if (scrollHeight - innerHeight < requiredHeight) {
                    setMeasuredDimension(getMeasuredWidth(), innerHeight + requiredHeight);
                } else {
                    setMeasuredDimension(getMeasuredWidth(), Math.min(scrollHeight, maxHeight));
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (currentType != 3 && blueImageView != null) {
                int bottom = confirmTextView.getBottom();
                int height = getMeasuredHeight() - bottom;

                int h;
                if (problemText.getVisibility() == VISIBLE) {
                    h = problemText.getMeasuredHeight();
                    t = bottom + height - h;
                    problemText.layout(problemText.getLeft(), t, problemText.getRight(), t + h);
                } else if (timeText.getVisibility() == VISIBLE) {
                    h = timeText.getMeasuredHeight();
                    t = bottom + height - h;
                    timeText.layout(timeText.getLeft(), t, timeText.getRight(), t + h);
                } else {
                    t = bottom + height;
                }

                height = t - bottom;
                h = codeFieldContainer.getMeasuredHeight();
                t = (height - h) / 2 + bottom;
                codeFieldContainer.layout(codeFieldContainer.getLeft(), t, codeFieldContainer.getRight(), t + h);
            }
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        private void resendCode() {
            final Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);

            nextPressed = true;

            TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    if (error.text != null) {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            onBackPressed(true);
                            setPage(0, true, null, true);
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                        } else if (error.code != -1000) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                        }
                    }
                }
                needHideProgress(false);
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            needShowProgress(reqId);
        }

        @Override
        public String getHeaderName() {
            if (currentType == 1 || currentType == AUTH_TYPE_MISSED_CALL) {
                return phone;
            } else {
                return LocaleController.getString("YourCode", R.string.YourCode);
            }
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            waitingForEvent = true;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveCall);
            }

            currentParams = params;
            phone = params.getString("phone");
            emailPhone = params.getString("ephone");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            time = params.getInt("timeout");
            openTime = (int) (System.currentTimeMillis() / 1000);
            nextType = params.getInt("nextType");
            pattern = params.getString("pattern");
            prefix = params.getString("prefix");
            length = params.getInt("length");
            if (length == 0) {
                length = 5;
            }

            codeFieldContainer.setNumbersCount(length, currentType);

            if (progressView != null) {
                progressView.setVisibility(nextType != 0 ? VISIBLE : GONE);
            }

            if (phone == null) {
                return;
            }

            String number = PhoneFormat.getInstance().format(phone);
            CharSequence str = "";
            if (currentType == 1) {
                str = AndroidUtilities.replaceTags(LocaleController.getString("SentAppCode", R.string.SentAppCode));
            } else if (currentType == 2) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentSmsCode", R.string.SentSmsCode, LocaleController.addNbsp(number)));
            } else if (currentType == 3) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallCode", R.string.SentCallCode, LocaleController.addNbsp(number)));
            } else if (currentType == 4) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallOnly", R.string.SentCallOnly, LocaleController.addNbsp(number)));
            }
            confirmTextView.setText(str);

            if (currentType != 3) {
                AndroidUtilities.showKeyboard(codeFieldContainer.codeField[0]);
                codeFieldContainer.codeField[0].requestFocus();
            } else {
                AndroidUtilities.hideKeyboard(codeFieldContainer.codeField[0]);
            }

            destroyTimer();
            destroyCodeTimer();

            lastCurrentTime = System.currentTimeMillis();
            if (currentType == 1) {
                problemText.setVisibility(VISIBLE);
                timeText.setVisibility(GONE);
            } else if (currentType == 3 && (nextType == 4 || nextType == 2)) {
                problemText.setVisibility(GONE);
                timeText.setVisibility(VISIBLE);
                if (nextType == 4) {
                    timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
                } else if (nextType == 2) {
                    timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 1, 0));
                }
                String callLogNumber = restore ? AndroidUtilities.obtainLoginPhoneCall(pattern) : null;
                if (callLogNumber != null) {
                    onNextPressed(callLogNumber);
                } else if (catchedPhone != null) {
                    onNextPressed(catchedPhone);
                } else {
                    createTimer();
                }
            } else if (currentType == 2 && (nextType == 4 || nextType == 3)) {
                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                String hash = preferences.getString("sms_hash", null);
                String savedCode = null;
                if (!TextUtils.isEmpty(hash)) {
                    savedCode = preferences.getString("sms_hash_code", null);
                    if (savedCode != null && savedCode.contains(hash + "|")) {
                        savedCode = savedCode.substring(savedCode.indexOf('|') + 1);
                    } else {
                        savedCode = null;
                    }
                }
                if (savedCode != null) {
                    codeFieldContainer.setCode(savedCode);
                    onNextPressed(null);
                } else {
                    createTimer();
                }
            } else if (currentType == 4 && nextType == 2) {
                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                createTimer();
            } else {
                timeText.setVisibility(GONE);
                problemText.setVisibility(GONE);
                createCodeTimer();
            }

            if (currentType == AUTH_TYPE_MISSED_CALL) {
                String pref = prefix;
                for  (int i = 0; i < length; i++) {
                    pref += "0";
                }
                pref = PhoneFormat.getInstance().format("+" + pref);
                for  (int i = 0; i < length; i++) {
                   int index = pref.lastIndexOf("0");
                   if (index >= 0) {
                       pref = pref.substring(0,  index);
                   }
                }
                pref = pref.replaceAll("\\)", "");
                pref = pref.replaceAll("\\(", "");
                prefixTextView.setText(pref);
            }
        }

        private void createCodeTimer() {
            if (codeTimer != null) {
                return;
            }
            codeTime = 15000;
            codeTimer = new Timer();
            lastCodeTime = System.currentTimeMillis();
            codeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCodeTime;
                        lastCodeTime = currentTime;
                        codeTime -= diff;
                        if (codeTime <= 1000) {
                            problemText.setVisibility(VISIBLE);
                            timeText.setVisibility(GONE);
                            destroyCodeTimer();
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyCodeTimer() {
            try {
                synchronized (timerSync) {
                    if (codeTimer != null) {
                        codeTimer.cancel();
                        codeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void createTimer() {
            if (timeTimer != null) {
                return;
            }
            if (progressView != null) {
                progressView.resetProgressAnimation();
            }
            timeTimer = new Timer();
            timeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (timeTimer == null) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        final double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCurrentTime;
                        lastCurrentTime = currentTime;
                        time -= diff;
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            if (nextType == 4 || nextType == 3) {
                                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, minutes, seconds));
                            } else if (nextType == 2) {
                                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, minutes, seconds));
                            }
                            if (progressView != null && !progressView.isProgressAnimationRunning()) {
                                progressView.startProgressAnimation(time - 1000L);
                            }
                        } else {
                            destroyTimer();
                            if (currentType == 3) {
                                AndroidUtilities.setWaitingForCall(false);
                                NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                                waitingForEvent = false;
                                destroyCodeTimer();
                                resendCode();
                            } else if (currentType == 2 || currentType == 4) {
                                if (nextType == 4 || nextType == 2) {
                                    if (nextType == 4) {
                                        timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                                    } else {
                                        timeText.setText(LocaleController.getString("SendingSms", R.string.SendingSms));
                                    }
                                    createCodeTimer();
                                    TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                                    req.phone_number = requestPhone;
                                    req.phone_code_hash = phoneHash;
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                        if (error != null && error.text != null) {
                                            AndroidUtilities.runOnUIThread(() -> lastError = error.text);
                                        }
                                    }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                                } else if (nextType == 3) {
                                    AndroidUtilities.setWaitingForSms(false);
                                    NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                                    waitingForEvent = false;
                                    destroyCodeTimer();
                                    resendCode();
                                }
                            }
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyTimer() {
            try {
                synchronized (timerSync) {
                    if (timeTimer != null) {
                        timeTimer.cancel();
                        timeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }


        @Override
        public void onNextPressed(String code) {
            if (currentViewNum == AUTH_TYPE_MISSED_CALL) {
                if (nextPressed) {
                    return;
                }
            } else {
                if (nextPressed || currentViewNum < 1 || currentViewNum > 4) {
                    return;
                }
            }

            if (code == null) {
                code = codeFieldContainer.getCode();
            }
            if (TextUtils.isEmpty(code)) {
                onFieldError(codeFieldContainer);
                return;
            }
            nextPressed = true;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
            req.phone_number = requestPhone;
            req.phone_code = code;
            req.phone_code_hash = phoneHash;
            destroyTimer();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                boolean ok = false;

                if (error == null) {
                    nextPressed = false;
                    ok = true;
                    showDoneButton(false, true);
                    destroyTimer();
                    destroyCodeTimer();
                    if (response instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                        TLRPC.TL_auth_authorizationSignUpRequired authorization = (TLRPC.TL_auth_authorizationSignUpRequired) response;
                        if (authorization.terms_of_service != null) {
                            currentTermsOfService = authorization.terms_of_service;
                        }
                        Bundle params = new Bundle();
                        params.putString("phoneFormated", requestPhone);
                        params.putString("phoneHash", phoneHash);
                        params.putString("code", req.phone_code);
                        setPage(5, true, params, false);
                    } else {
                        onAuthSuccess((TLRPC.TL_auth_authorization) response);
                    }
                } else {
                    lastError = error.text;
                    if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                        ok = true;
                        TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            nextPressed = false;
                            showDoneButton(false, true);
                            if (error1 == null) {
                                TLRPC.TL_account_password password = (TLRPC.TL_account_password) response1;
                                if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                    return;
                                }
                                Bundle bundle = new Bundle();
                                SerializedData data = new SerializedData(password.getObjectSize());
                                password.serializeToStream(data);
                                bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                bundle.putString("phoneFormated", requestPhone);
                                bundle.putString("phoneHash", phoneHash);
                                bundle.putString("code", req.phone_code);
                                setPage(6, true, bundle, false);
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error1.text);
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        destroyTimer();
                        destroyCodeTimer();
                    } else {
                        nextPressed = false;
                        showDoneButton(false, true);
                        if (currentType == 3 && (nextType == 4 || nextType == 2) || currentType == 2 && (nextType == 4 || nextType == 3) || currentType == 4 && nextType == 2) {
                            createTimer();
                        }
                        if (currentType == 2) {
                            AndroidUtilities.setWaitingForSms(true);
                            NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                        } else if (currentType == 3) {
                            AndroidUtilities.setWaitingForCall(true);
                            NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                        }
                        waitingForEvent = true;
                        if (currentType != 3) {
                            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                                    codeFieldContainer.codeField[a].setText("");
                                }
                                codeFieldContainer.codeField[0].requestFocus();
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                onBackPressed(true);
                                setPage(0, true, null, true);
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                            }
                        }
                    }
                }
                if (ok) {
                    if (currentType == 3) {
                        AndroidUtilities.endIncomingCall();
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            needShowProgress(reqId, false);
            showDoneButton(true, true);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("StopVerification", R.string.StopVerification));
                builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), null);
                builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    onBackPressed(true);
                    setPage(0, true, null, true);
                });
                showDialog(builder.create());
                return false;
            }
            nextPressed = false;
            needHideProgress(true);
            TLRPC.TL_auth_cancelCode req = new TLRPC.TL_auth_cancelCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);

            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            return true;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            destroyTimer();
            destroyCodeTimer();
        }

        @Override
        public void onShow() {
            super.onShow();
            if (currentType == 3) {
                return;
            }
            if (hintDrawable != null) {
                hintDrawable.setCurrentFrame(0);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (codeFieldContainer.codeField != null) {
                    for (int a = codeFieldContainer.codeField.length - 1; a >= 0; a--) {
                        if (a == 0 || codeFieldContainer.codeField[a].length() != 0) {
                            codeFieldContainer.codeField[a].requestFocus();
                            codeFieldContainer.codeField[a].setSelection(codeFieldContainer.codeField[a].length());
                            AndroidUtilities.showKeyboard(codeFieldContainer.codeField[a]);
                            break;
                        }
                    }
                }
                if (hintDrawable != null) {
                    hintDrawable.start();
                }
            }, 100);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (!waitingForEvent || codeFieldContainer.codeField == null) {
                return;
            }
            if (id == NotificationCenter.didReceiveSmsCode) {
                codeFieldContainer.setText("" + args[0]);
                onNextPressed(null);
            } else if (id == NotificationCenter.didReceiveCall) {
                String num = "" + args[0];
                if (!AndroidUtilities.checkPhonePattern(pattern, num)) {
                    return;
                }
                if (!pattern.equals("*")) {
                    catchedPhone = num;
                    AndroidUtilities.endIncomingCall();
                }
                onNextPressed(num);
            }
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeFieldContainer.getCode();
            if (code.length() != 0) {
                bundle.putString("smsview_code_" + currentType, code);
            }
            if (catchedPhone != null) {
                bundle.putString("catchedPhone", catchedPhone);
            }
            if (currentParams != null) {
                bundle.putBundle("smsview_params_" + currentType, currentParams);
            }
            if (time != 0) {
                bundle.putInt("time", time);
            }
            if (openTime != 0) {
                bundle.putInt("open", openTime);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("smsview_params_" + currentType);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String catched = bundle.getString("catchedPhone");
            if (catched != null) {
                catchedPhone = catched;
            }
            String code = bundle.getString("smsview_code_" + currentType);
            if (code != null && codeFieldContainer.codeField != null) {
                codeFieldContainer.setText(code);
            }
            int t = bundle.getInt("time");
            if (t != 0) {
                time = t;
            }
            int t2 = bundle.getInt("open");
            if (t2 != 0) {
                openTime = t2;
            }
        }
    }

    public class LoginActivityPasswordView extends SlideView {

        private EditTextBoldCursor codeField;
        private TextView confirmTextView;
        private TextView resetAccountButton;
        private TextView resetAccountText;
        private TextView cancelButton;

        private Bundle currentParams;
        private boolean nextPressed;
        private TLRPC.TL_account_password currentPassword;
        private String passwordString;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;

        public LoginActivityPasswordView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(LocaleController.getString("LoginPasswordText", R.string.LoginPasswordText));
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            codeField = new EditTextBoldCursor(context);
            codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            codeField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setTypeface(Typeface.DEFAULT);
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            cancelButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
            cancelButton.setOnClickListener(view -> {
                if (doneProgressView.getTag() != null) {
                    return;
                }
                if (currentPassword.has_recovery) {
                    needShowProgress(0);
                    TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        needHideProgress(false);
                        if (error == null) {
                            final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, res.email_pattern));
                            builder.setTitle(LocaleController.getString("RestoreEmailSentTitle", R.string.RestoreEmailSentTitle));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                                Bundle bundle = new Bundle();
                                bundle.putString("email_unconfirmed_pattern", res.email_pattern);
                                bundle.putString("password", passwordString);
                                setPage(7, true, bundle, false);
                            });
                            Dialog dialog = showDialog(builder.create());
                            if (dialog != null) {
                                dialog.setCanceledOnTouchOutside(false);
                                dialog.setCancelable(false);
                            }
                        } else {
                            if (error.text.startsWith("FLOOD_WAIT")) {
                                int time = Utilities.parseInt(error.text);
                                String timeString;
                                if (time < 60) {
                                    timeString = LocaleController.formatPluralString("Seconds", time);
                                } else {
                                    timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                }
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                            }
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    resetAccountText.setVisibility(VISIBLE);
                    resetAccountButton.setVisibility(VISIBLE);
                    AndroidUtilities.hideKeyboard(codeField);
                    needShowAlert(LocaleController.getString("RestorePasswordNoEitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                }
            });

            resetAccountButton = new TextView(context);
            resetAccountButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText6));
            resetAccountButton.setVisibility(GONE);
            resetAccountButton.setText(LocaleController.getString("ResetMyAccount", R.string.ResetMyAccount));
            resetAccountButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            resetAccountButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(resetAccountButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 34, 0, 0));
            resetAccountButton.setOnClickListener(view -> {
                if (doneProgressView.getTag() != null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
                builder.setTitle(LocaleController.getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning));
                builder.setPositiveButton(LocaleController.getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), (dialogInterface, i) -> {
                    needShowProgress(0);
                    TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                    req.reason = "Forgot password";
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        needHideProgress(false);
                        if (error == null) {
                            Bundle params = new Bundle();
                            params.putString("phoneFormated", requestPhone);
                            params.putString("phoneHash", phoneHash);
                            params.putString("code", phoneCode);
                            setPage(5, true, params, false);
                        } else {
                            if (error.text.equals("2FA_RECENT_CONFIRM")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ResetAccountCancelledAlert", R.string.ResetAccountCancelledAlert));
                            } else if (error.text.startsWith("2FA_CONFIRM_WAIT_")) {
                                Bundle params = new Bundle();
                                params.putString("phoneFormated", requestPhone);
                                params.putString("phoneHash", phoneHash);
                                params.putString("code", phoneCode);
                                params.putInt("startTime", ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                                params.putInt("waitTime", Utilities.parseInt(error.text.replace("2FA_CONFIRM_WAIT_", "")));
                                setPage(8, true, params, false);
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                            }
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            });

            resetAccountText = new TextView(context);
            resetAccountText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountText.setVisibility(GONE);
            resetAccountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            resetAccountText.setText(LocaleController.getString("ResetMyAccountText", R.string.ResetMyAccountText));
            resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 7, 0, 14));
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            if (params.isEmpty()) {
                resetAccountButton.setVisibility(VISIBLE);
                resetAccountText.setVisibility(VISIBLE);
                AndroidUtilities.hideKeyboard(codeField);
                return;
            }
            resetAccountButton.setVisibility(GONE);
            resetAccountText.setVisibility(GONE);
            codeField.setText("");
            currentParams = params;
            passwordString = currentParams.getString("password");
            if (passwordString != null) {
                SerializedData data = new SerializedData(Utilities.hexToBytes(passwordString));
                currentPassword = TLRPC.TL_account_password.TLdeserialize(data, data.readInt32(false), false);
            }

            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");

            if (currentPassword != null && !TextUtils.isEmpty(currentPassword.hint)) {
                codeField.setHint(currentPassword.hint);
            } else {
                codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
            }
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            if (clear) {
                codeField.setText("");
            }
            onFieldError(confirmTextView);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            String oldPassword = codeField.getText().toString();
            if (oldPassword.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);

            Utilities.globalQueue.postRunnable(() -> {
                final byte[] x_bytes;

                TLRPC.PasswordKdfAlgo current_algo = currentPassword.current_algo;
                if (current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    byte[] passwordBytes = AndroidUtilities.getStringBytes(oldPassword);
                    TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) current_algo;
                    x_bytes = SRPHelper.getX(passwordBytes, algo);
                } else {
                    x_bytes = null;
                }


                final TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();

                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    nextPressed = false;
                    if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.TL_account_password) response2;
                                onNextPressed(null);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }

                    if (response instanceof TLRPC.TL_auth_authorization) {
                        showDoneButton(false, true);
                        postDelayed(() -> {
                            needHideProgress(false, false);
                            AndroidUtilities.hideKeyboard(codeField);
                            onAuthSuccess((TLRPC.TL_auth_authorization) response);
                        }, 150);
                    } else {
                        needHideProgress(false);
                        if (error.text.equals("PASSWORD_HASH_INVALID")) {
                            onPasscodeError(true);
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                });

                if (current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    req.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) current_algo);
                    if (req.password == null) {
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.text = "PASSWORD_HASH_INVALID";
                        requestDelegate.run(null, error);
                        return;
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public boolean onBackPressed(boolean force) {
            nextPressed = false;
            needHideProgress(true);
            currentParams = null;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    AndroidUtilities.showKeyboard(codeField);
                }
            }, 100);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("passview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("passview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("passview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("passview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityResetWaitView extends SlideView {

        private TextView confirmTextView;
        private TextView resetAccountButton;
        private TextView resetAccountTime;
        private TextView resetAccountText;
        private Runnable timeRunnable;

        private Bundle currentParams;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;
        private int startTime;
        private int waitTime;

        public LoginActivityResetWaitView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            resetAccountText = new TextView(context);
            resetAccountText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            resetAccountText.setText(LocaleController.getString("ResetAccountStatus", R.string.ResetAccountStatus));
            resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 24, 0, 0));

            resetAccountTime = new TextView(context);
            resetAccountTime.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountTime.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            resetAccountTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountTime.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountTime, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 2, 0, 0));

            resetAccountButton = new TextView(context);
            resetAccountButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountButton.setText(LocaleController.getString("ResetAccountButton", R.string.ResetAccountButton));
            resetAccountButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            resetAccountButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(resetAccountButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 7, 0, 0));
            resetAccountButton.setOnClickListener(view -> {
                if (doneProgressView.getTag() != null) {
                    return;
                }
                if (Math.abs(ConnectionsManager.getInstance(currentAccount).getCurrentTime() - startTime) < waitTime) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
                builder.setTitle(LocaleController.getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning));
                builder.setPositiveButton(LocaleController.getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), (dialogInterface, i) -> {
                    needShowProgress(0);
                    TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                    req.reason = "Forgot password";
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        needHideProgress(false);
                        if (error == null) {
                            Bundle params = new Bundle();
                            params.putString("phoneFormated", requestPhone);
                            params.putString("phoneHash", phoneHash);
                            params.putString("code", phoneCode);
                            setPage(5, true, params, false);
                        } else {
                            if (error.text.equals("2FA_RECENT_CONFIRM")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ResetAccountCancelledAlert", R.string.ResetAccountCancelledAlert));
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                            }
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            });
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("ResetAccount", R.string.ResetAccount);
        }

        private void updateTimeText() {
            int timeLeft = Math.max(0, waitTime - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - startTime));
            int days = timeLeft / 86400;
            int hours = (timeLeft - days * 86400) / 3600;
            int minutes = (timeLeft - days * 86400 - hours * 3600) / 60;
            int seconds = timeLeft % 60;
            if (days != 0) {
                resetAccountTime.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("DaysBold", days) + " " + LocaleController.formatPluralString("HoursBold", hours) + " " + LocaleController.formatPluralString("MinutesBold", minutes)));
            } else {
                resetAccountTime.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("HoursBold", hours) + " " + LocaleController.formatPluralString("MinutesBold", minutes) + " " + LocaleController.formatPluralString("SecondsBold", seconds)));
            }
            if (timeLeft > 0) {
                resetAccountButton.setTag(Theme.key_windowBackgroundWhiteGrayText6);
                resetAccountButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            } else {
                resetAccountButton.setTag(Theme.key_windowBackgroundWhiteRedText6);
                resetAccountButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText6));
            }
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            currentParams = params;
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");
            startTime = params.getInt("startTime");
            waitTime = params.getInt("waitTime");
            confirmTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ResetAccountInfo", R.string.ResetAccountInfo, LocaleController.addNbsp(PhoneFormat.getInstance().format("+" + requestPhone)))));
            updateTimeText();
            timeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (timeRunnable != this) {
                        return;
                    }
                    updateTimeText();
                    AndroidUtilities.runOnUIThread(timeRunnable, 1000);
                }
            };
            AndroidUtilities.runOnUIThread(timeRunnable, 1000);
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            AndroidUtilities.cancelRunOnUIThread(timeRunnable);
            timeRunnable = null;
            currentParams = null;
            return true;
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("resetview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("resetview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }
    }

    public class LoginActivityRecoverView extends SlideView {

        private EditTextBoldCursor codeField;
        private TextView confirmTextView;
        private TextView cancelButton;

        private Bundle currentParams;
        private String passwordString;
        private boolean nextPressed;

        public LoginActivityRecoverView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            codeField = new EditTextBoldCursor(context);
            codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            codeField.setHint(LocaleController.getString("PasswordCode", R.string.PasswordCode));
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setTypeface(Typeface.DEFAULT);
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 0, 0, 14));
            cancelButton.setOnClickListener(view -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
                builder.setTitle(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> setPage(6, true, new Bundle(), true));
                Dialog dialog = showDialog(builder.create());
                if (dialog != null) {
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            codeField.setText("");
            currentParams = params;
            passwordString = currentParams.getString("password");
            String email_unconfirmed_pattern = currentParams.getString("email_unconfirmed_pattern");
            cancelButton.setText(LocaleController.formatString("RestoreEmailTrouble", R.string.RestoreEmailTrouble, email_unconfirmed_pattern));

            AndroidUtilities.showKeyboard(codeField);
            codeField.requestFocus();
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            if (clear) {
                codeField.setText("");
            }
            AndroidUtilities.shakeView(confirmTextView, 2, 0);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            code = codeField.getText().toString();
            if (code.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);
            TLRPC.TL_auth_checkRecoveryPassword req = new TLRPC.TL_auth_checkRecoveryPassword();
            req.code = code;
            String finalCode = code;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false);
                nextPressed = false;
                if (response instanceof TLRPC.TL_boolTrue) {
                    Bundle params = new Bundle();
                    params.putString("emailCode", finalCode);
                    params.putString("password", passwordString);
                    setPage(9, true, params, false);
                } else {
                    if (error == null || error.text.startsWith("CODE_INVALID")) {
                        onPasscodeError(true);
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        int time = Utilities.parseInt(error.text);
                        String timeString;
                        if (time < 60) {
                            timeString = LocaleController.formatPluralString("Seconds", time);
                        } else {
                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                        }
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                    } else {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                }
            }, 100);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code != null && code.length() != 0) {
                bundle.putString("recoveryview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("recoveryview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityNewPasswordView extends SlideView {

        private EditTextBoldCursor[] codeField;
        private TextView confirmTextView;
        private TextView cancelButton;

        private String emailCode;
        private String newPassword;
        private String passwordString;
        private TLRPC.TL_account_password currentPassword;
        private Bundle currentParams;
        private boolean nextPressed;
        private int currentStage;

        public LoginActivityNewPasswordView(Context context, int stage) {
            super(context);
            currentStage = stage;

            setOrientation(VERTICAL);

            codeField = new EditTextBoldCursor[stage == 1 ? 1 : 2];

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            for (int a = 0; a < codeField.length; a++) {
                codeField[a] = new EditTextBoldCursor(context);
                codeField[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                codeField[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                codeField[a].setCursorSize(AndroidUtilities.dp(20));
                codeField[a].setCursorWidth(1.5f);
                codeField[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                codeField[a].setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
                codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                codeField[a].setMaxLines(1);
                codeField[a].setPadding(0, 0, 0, 0);
                if (stage == 0) {
                    codeField[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                codeField[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                codeField[a].setTypeface(Typeface.DEFAULT);
                codeField[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(codeField[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, a == 0 ? 20 : 30, 0, 0));
                int num = a;
                codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (num == 0 && codeField.length == 2) {
                        codeField[1].requestFocus();
                        return true;
                    } else if (i == EditorInfo.IME_ACTION_NEXT) {
                        onNextPressed(null);
                        return true;
                    }
                    return false;
                });

                if (stage == 0) {
                    if (a == 0) {
                        codeField[a].setHint(LocaleController.getString("PleaseEnterNewFirstPasswordHint", R.string.PleaseEnterNewFirstPasswordHint));
                    } else {
                        codeField[a].setHint(LocaleController.getString("PleaseEnterNewSecondPasswordHint", R.string.PleaseEnterNewSecondPasswordHint));
                    }
                } else {
                    codeField[a].setHint(LocaleController.getString("PasswordHintPlaceholder", R.string.PasswordHintPlaceholder));
                }
            }

            if (stage == 0) {
                confirmTextView.setText(LocaleController.getString("PleaseEnterNewFirstPasswordLogin", R.string.PleaseEnterNewFirstPasswordLogin));
            } else {
                confirmTextView.setText(LocaleController.getString("PasswordHintTextLogin", R.string.PasswordHintTextLogin));
            }

            cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            cancelButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 6, 0, 14));
            cancelButton.setOnClickListener(view -> {
                if (currentStage == 0) {
                    recoverPassword(null, null);
                } else {
                    recoverPassword(newPassword, null);
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("NewPassword", R.string.NewPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            for (int a = 0; a < codeField.length; a++) {
                codeField[a].setText("");
            }
            currentParams = params;
            emailCode = currentParams.getString("emailCode");
            passwordString = currentParams.getString("password");
            if (passwordString != null) {
                SerializedData data = new SerializedData(Utilities.hexToBytes(passwordString));
                currentPassword = TLRPC.TL_account_password.TLdeserialize(data, data.readInt32(false), false);
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
            }
            newPassword = currentParams.getString("new_password");

            AndroidUtilities.showKeyboard(codeField[0]);
            codeField[0].requestFocus();
        }

        private void onPasscodeError(boolean clear, int num) {
            if (getParentActivity() == null) {
                return;
            }
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(codeField[num], 2, 0);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            code = codeField[0].getText().toString();
            if (code.length() == 0) {
                onPasscodeError(false, 0);
                return;
            }
            if (currentStage == 0) {
                if (!code.equals(codeField[1].getText().toString())) {
                    onPasscodeError(false, 1);
                    return;
                }
                Bundle params = new Bundle();
                params.putString("emailCode", emailCode);
                params.putString("new_password", code);
                params.putString("password", passwordString);
                setPage(10, true, params, false);
            } else {
                nextPressed = true;
                needShowProgress(0);
                recoverPassword(newPassword, code);
            }
        }

        private void recoverPassword(String password, String hint) {
            TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
            req.code = emailCode;
            if (!TextUtils.isEmpty(password)) {
                req.flags |= 1;
                req.new_settings = new TLRPC.TL_account_passwordInputSettings();
                req.new_settings.flags |= 1;
                req.new_settings.hint = hint != null ? hint : "";
                req.new_settings.new_algo = currentPassword.new_algo;
            }
            Utilities.globalQueue.postRunnable(() -> {
                byte[] newPasswordBytes;
                if (password != null) {
                    newPasswordBytes = AndroidUtilities.getStringBytes(password);
                } else {
                    newPasswordBytes = null;
                }

                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error != null && ("SRP_ID_INVALID".equals(error.text) || "NEW_SALT_INVALID".equals(error.text))) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.TL_account_password) response2;
                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                recoverPassword(password, hint);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }
                    needHideProgress(false);
                    if (response instanceof TLRPC.auth_Authorization) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> onAuthSuccess((TLRPC.TL_auth_authorization) response));
                        if (TextUtils.isEmpty(password)) {
                            builder.setMessage(LocaleController.getString("PasswordReset", R.string.PasswordReset));
                        } else {
                            builder.setMessage(LocaleController.getString("YourPasswordChangedSuccessText", R.string.YourPasswordChangedSuccessText));
                        }
                        builder.setTitle(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle));
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
                        }
                    } else if (error != null) {
                        nextPressed = false;
                        if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                });

                if (currentPassword.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    if (password != null) {
                        TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.new_algo;
                        req.new_settings.new_password_hash = SRPHelper.getVBytes(newPasswordBytes, algo);
                        if (req.new_settings.new_password_hash == null) {
                            TLRPC.TL_error error = new TLRPC.TL_error();
                            error.text = "ALGO_INVALID";
                            requestDelegate.run(null, error);
                        }
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "PASSWORD_HASH_INVALID";
                    requestDelegate.run(null, error);
                }
            });
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField[0].requestFocus();
                    codeField[0].setSelection(codeField[0].length());
                }
            }, 100);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params" + currentStage, currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params" + currentStage);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }
    }

    public class LoginActivityRegisterView extends SlideView implements ImageUpdater.ImageUpdaterDelegate {

        private EditTextBoldCursor firstNameField;
        private EditTextBoldCursor lastNameField;
        private BackupImageView avatarImage;
        private AvatarDrawable avatarDrawable;
        private View avatarOverlay;
        private RLottieImageView avatarEditor;
        private RadialProgressView avatarProgressView;
        private AnimatorSet avatarAnimation;
        private TextView textView;
        private TextView wrongNumber;
        private TextView privacyView;
        private String requestPhone;
        private String phoneHash;
        private Bundle currentParams;
        private boolean nextPressed = false;

        private RLottieDrawable cameraDrawable;

        private ImageUpdater imageUpdater;

        private TLRPC.FileLocation avatar;
        private TLRPC.FileLocation avatarBig;

        private boolean createAfterUpload;

        public class LinkSpan extends ClickableSpan {
            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }

            @Override
            public void onClick(View widget) {
                showTermsOfService(false);
            }
        }

        private void showTermsOfService(boolean needAccept) {
            if (currentTermsOfService == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("TermsOfService", R.string.TermsOfService));

            if (needAccept) {
                builder.setPositiveButton(LocaleController.getString("Accept", R.string.Accept), (dialog, which) -> {
                    currentTermsOfService.popup = false;
                    onNextPressed(null);
                });
                builder.setNegativeButton(LocaleController.getString("Decline", R.string.Decline), (dialog, which) -> {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(LocaleController.getString("TermsOfService", R.string.TermsOfService));
                    builder1.setMessage(LocaleController.getString("TosDecline", R.string.TosDecline));
                    builder1.setPositiveButton(LocaleController.getString("SignUp", R.string.SignUp), (dialog1, which1) -> {
                        currentTermsOfService.popup = false;
                        onNextPressed(null);
                    });
                    builder1.setNegativeButton(LocaleController.getString("Decline", R.string.Decline), (dialog12, which12) -> {
                        onBackPressed(true);
                        setPage(0, true, null, true);
                    });
                    showDialog(builder1.create());
                });
            } else {
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            }

            SpannableStringBuilder text = new SpannableStringBuilder(currentTermsOfService.text);
            MessageObject.addEntitiesToText(text, currentTermsOfService.entities, false, false, false, false);
            builder.setMessage(text);

            showDialog(builder.create());
        }

        public LoginActivityRegisterView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            imageUpdater = new ImageUpdater(false);
            imageUpdater.setOpenWithFrontfaceCamera(true);
            imageUpdater.setSearchAvailable(false);
            imageUpdater.setUploadAfterSelect(false);
            imageUpdater.parentFragment = LoginActivity.this;
            imageUpdater.setDelegate(this);

            textView = new TextView(context);
            textView.setText(LocaleController.getString("RegisterText2", R.string.RegisterText2));
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 0, 0));

            FrameLayout editTextContainer = new FrameLayout(context);
            addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 0, 0));

            avatarDrawable = new AvatarDrawable();

            avatarImage = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            avatarImage.setRoundRadius(AndroidUtilities.dp(32));
            avatarDrawable.setInfo(5, null, null);
            avatarImage.setImageDrawable(avatarDrawable);
            editTextContainer.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 16, 0, 0));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0x55000000);

            avatarOverlay = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (avatarImage != null && avatarProgressView.getVisibility() == VISIBLE) {
                        paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha() * avatarProgressView.getAlpha()));
                        canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                    }
                }
            };
            editTextContainer.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 16, 0, 0));
            avatarOverlay.setOnClickListener(view -> {
                imageUpdater.openMenu(avatar != null, () -> {
                    avatar = null;
                    avatarBig = null;
                    showAvatarProgress(false, true);
                    avatarImage.setImage(null, null, avatarDrawable, null);
                    avatarEditor.setImageResource(R.drawable.actions_setphoto);
                    avatarEditor.setAnimation(cameraDrawable);
                    cameraDrawable.setCurrentFrame(0);
                }, dialog -> {
                    if (!imageUpdater.isUploadingImage()) {
                        cameraDrawable.setCustomEndFrame(86);
                        avatarEditor.playAnimation();
                    } else {
                        cameraDrawable.setCurrentFrame(0, false);
                    }
                });
                cameraDrawable.setCurrentFrame(0);
                cameraDrawable.setCustomEndFrame(43);
                avatarEditor.playAnimation();
            });

            cameraDrawable = new RLottieDrawable(R.raw.camera, "" + R.raw.camera, AndroidUtilities.dp(60), AndroidUtilities.dp(60), false, null);

            avatarEditor = new RLottieImageView(context) {
                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    avatarOverlay.invalidate();
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    avatarOverlay.invalidate();
                }
            };
            avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
            avatarEditor.setAnimation(cameraDrawable);
            avatarEditor.setEnabled(false);
            avatarEditor.setClickable(false);
            avatarEditor.setPadding(AndroidUtilities.dp(2), 0, 0, AndroidUtilities.dp(1));
            editTextContainer.addView(avatarEditor, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 16, 0, 0));

            avatarProgressView = new RadialProgressView(context) {
                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    avatarOverlay.invalidate();
                }
            };
            avatarProgressView.setSize(AndroidUtilities.dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            editTextContainer.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 16, 0, 0));

            showAvatarProgress(false, false);

            firstNameField = new EditTextBoldCursor(context);
            firstNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            firstNameField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            firstNameField.setCursorSize(AndroidUtilities.dp(20));
            firstNameField.setCursorWidth(1.5f);
            firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
            firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            firstNameField.setMaxLines(1);
            firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            editTextContainer.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 85, 0, LocaleController.isRTL ? 85 : 0, 0));
            firstNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    return true;
                }
                return false;
            });

            lastNameField = new EditTextBoldCursor(context);
            lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
            lastNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            lastNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            lastNameField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            lastNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            lastNameField.setCursorSize(AndroidUtilities.dp(20));
            lastNameField.setCursorWidth(1.5f);
            lastNameField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            lastNameField.setMaxLines(1);
            lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            editTextContainer.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 85, 51, LocaleController.isRTL ? 85 : 0, 0));
            lastNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            wrongNumber = new TextView(context);
            wrongNumber.setText(LocaleController.getString("CancelRegistration", R.string.CancelRegistration));
            wrongNumber.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            wrongNumber.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            wrongNumber.setVisibility(GONE);
            addView(wrongNumber, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 20, 0, 0));
            wrongNumber.setOnClickListener(view -> {
                if (doneProgressView.getTag() != null) {
                    return;
                }
                onBackPressed(false);
            });

            final FrameLayout privacyLayout = new FrameLayout(context);
            privacyLayout.setClipToPadding(false);
            privacyLayout.setPadding(0, AndroidUtilities.dp(28f), AndroidUtilities.dp(100f), AndroidUtilities.dp(16f));
            addView(privacyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM));

            privacyView = new TextView(context);
            privacyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            privacyView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
            privacyView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            privacyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            privacyView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            VerticalPositionAutoAnimator.attach(privacyView);
            privacyLayout.addView(privacyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

            String str = LocaleController.getString("TermsOfServiceLogin", R.string.TermsOfServiceLogin);
            SpannableStringBuilder text = new SpannableStringBuilder(str);
            int index1 = str.indexOf('*');
            int index2 = str.lastIndexOf('*');
            if (index1 != -1 && index2 != -1 && index1 != index2) {
                text.replace(index2, index2 + 1, "");
                text.replace(index1, index1 + 1, "");
                text.setSpan(new LinkSpan(), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            privacyView.setText(text);
        }

        @Override
        public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize) {
            AndroidUtilities.runOnUIThread(() -> {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
            });
        }

        private void showAvatarProgress(boolean show, boolean animated) {
            if (avatarEditor == null) {
                return;
            }
            if (avatarAnimation != null) {
                avatarAnimation.cancel();
                avatarAnimation = null;
            }
            if (animated) {
                avatarAnimation = new AnimatorSet();
                if (show) {
                    avatarProgressView.setVisibility(View.VISIBLE);

                    avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
                } else {
                    avatarEditor.setVisibility(View.VISIBLE);

                    avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                            ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
                }
                avatarAnimation.setDuration(180);
                avatarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (avatarAnimation == null || avatarEditor == null) {
                            return;
                        }
                        if (show) {
                            avatarEditor.setVisibility(View.INVISIBLE);
                        } else {
                            avatarProgressView.setVisibility(View.INVISIBLE);
                        }
                        avatarAnimation = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        avatarAnimation = null;
                    }
                });
                avatarAnimation.start();
            } else {
                if (show) {
                    avatarEditor.setAlpha(1.0f);
                    avatarEditor.setVisibility(View.INVISIBLE);
                    avatarProgressView.setAlpha(1.0f);
                    avatarProgressView.setVisibility(View.VISIBLE);
                } else {
                    avatarEditor.setAlpha(1.0f);
                    avatarEditor.setVisibility(View.VISIBLE);
                    avatarProgressView.setAlpha(0.0f);
                    avatarProgressView.setVisibility(View.INVISIBLE);
                }
            }
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("AreYouSureRegistration", R.string.AreYouSureRegistration));
                builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    onBackPressed(true);
                    setPage(0, true, null, true);
                    hidePrivacyView();
                });
                builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), null);
                showDialog(builder.create());
                return false;
            }
            needHideProgress(true);
            nextPressed = false;
            currentParams = null;
            return true;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("YourName", R.string.YourName);
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            if (privacyView != null) {
                if (restoringState) {
                    privacyView.setAlpha(1f);
                } else {
                    privacyView.setAlpha(0f);
                    privacyView.animate().alpha(1f).setDuration(200).setStartDelay(300).setInterpolator(AndroidUtilities.decelerateInterpolator).start();
                }
            }
            if (firstNameField != null) {
                firstNameField.requestFocus();
                firstNameField.setSelection(firstNameField.length());
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (firstNameField != null) {
                    firstNameField.requestFocus();
                    firstNameField.setSelection(firstNameField.length());
                }
            }, 100);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            firstNameField.setText("");
            lastNameField.setText("");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            currentParams = params;
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }
            if (currentTermsOfService != null && currentTermsOfService.popup) {
                showTermsOfService(true);
                return;
            }
            if (firstNameField.length() == 0) {
                onFieldError(firstNameField);
                return;
            }
            nextPressed = true;
            TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
            req.phone_code_hash = phoneHash;
            req.phone_number = requestPhone;
            req.first_name = firstNameField.getText().toString();
            req.last_name = lastNameField.getText().toString();
            needShowProgress(0);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (response instanceof TLRPC.TL_auth_authorization) {
                    hidePrivacyView();
                    showDoneButton(false, true);
                    postDelayed(() -> {
                        needHideProgress(false, false);
                        AndroidUtilities.hideKeyboard(fragmentView.findFocus());
                        onAuthSuccess((TLRPC.TL_auth_authorization) response, true);
                        if (avatarBig != null) {
                            MessagesController.getInstance(currentAccount).uploadAndApplyUserAvatar(avatarBig);
                        }
                    }, 150);
                } else {
                    needHideProgress(false);
                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                    } else if (error.text.contains("FIRSTNAME_INVALID")) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidFirstName", R.string.InvalidFirstName));
                    } else if (error.text.contains("LASTNAME_INVALID")) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidLastName", R.string.InvalidLastName));
                    } else {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String first = firstNameField.getText().toString();
            if (first.length() != 0) {
                bundle.putString("registerview_first", first);
            }
            String last = lastNameField.getText().toString();
            if (last.length() != 0) {
                bundle.putString("registerview_last", last);
            }
            if (currentTermsOfService != null) {
                SerializedData data = new SerializedData(currentTermsOfService.getObjectSize());
                currentTermsOfService.serializeToStream(data);
                String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                bundle.putString("terms", str);
                data.cleanup();
            }
            if (currentParams != null) {
                bundle.putBundle("registerview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("registerview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }

            try {
                String terms = bundle.getString("terms");
                if (terms != null) {
                    byte[] arr = Base64.decode(terms, Base64.DEFAULT);
                    if (arr != null) {
                        SerializedData data = new SerializedData(arr);
                        currentTermsOfService = TLRPC.TL_help_termsOfService.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            String first = bundle.getString("registerview_first");
            if (first != null) {
                firstNameField.setText(first);
            }
            String last = bundle.getString("registerview_last");
            if (last != null) {
                lastNameField.setText(last);
            }
        }

        private void hidePrivacyView() {
            privacyView.animate().alpha(0f).setDuration(150).setStartDelay(0).setInterpolator(AndroidUtilities.accelerateInterpolator).start();
        }
    }


    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        for (int a = 0;a < views.length; a++) {
            if (views[a] == null) {
                return new ArrayList<>();
            }
        }
        PhoneView phoneView = (PhoneView) views[0];
        LoginActivitySmsView smsView1 = (LoginActivitySmsView) views[1];
        LoginActivitySmsView smsView2 = (LoginActivitySmsView) views[2];
        LoginActivitySmsView smsView3 = (LoginActivitySmsView) views[3];
        LoginActivitySmsView smsView4 = (LoginActivitySmsView) views[4];
        LoginActivityRegisterView registerView = (LoginActivityRegisterView) views[5];
        LoginActivityPasswordView passwordView = (LoginActivityPasswordView) views[6];
        LoginActivityRecoverView recoverView = (LoginActivityRecoverView) views[7];
        LoginActivityResetWaitView waitView = (LoginActivityResetWaitView) views[8];

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = () -> {
            for (int i = 0; i < views.length; i++) {
                if (views[i] instanceof LoginActivitySmsView) {
                    LoginActivitySmsView smsView = (LoginActivitySmsView) views[i];
                    if (smsView.hintDrawable != null) {
                        smsView.hintDrawable.setLayerColor("Bubble.**", Theme.getColor(Theme.key_chats_actionBackground));
                        smsView.hintDrawable.setLayerColor("Phone.**", Theme.getColor(Theme.key_chats_actionBackground));
                    }
                }
            }
        };

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(floatingButtonIcon, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
        arrayList.add(new ThemeDescription(floatingButtonIcon, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
        arrayList.add(new ThemeDescription(floatingButtonIcon, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));
        arrayList.add(new ThemeDescription(floatingProgressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_chats_actionIcon));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        arrayList.add(new ThemeDescription(phoneView.countryButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.countryButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(phoneView.view, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhiteGrayLine));
        arrayList.add(new ThemeDescription(phoneView.textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.codeField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(phoneView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(phoneView.textView2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));

        arrayList.add(new ThemeDescription(passwordView.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(passwordView.codeField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(passwordView.codeField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        arrayList.add(new ThemeDescription(passwordView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(passwordView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(passwordView.cancelButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(passwordView.resetAccountButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteRedText6));
        arrayList.add(new ThemeDescription(passwordView.resetAccountText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));

        arrayList.add(new ThemeDescription(registerView.textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(registerView.firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        arrayList.add(new ThemeDescription(registerView.firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(registerView.firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(registerView.firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(registerView.lastNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        arrayList.add(new ThemeDescription(registerView.lastNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(registerView.lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(registerView.lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(registerView.wrongNumber, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(registerView.privacyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(registerView.privacyView, ThemeDescription.FLAG_LINKCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        arrayList.add(new ThemeDescription(recoverView.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(recoverView.codeField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(recoverView.codeField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        arrayList.add(new ThemeDescription(recoverView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(recoverView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(recoverView.cancelButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        arrayList.add(new ThemeDescription(waitView.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(waitView.resetAccountText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(waitView.resetAccountTime, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(waitView.resetAccountButton, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(waitView.resetAccountButton, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteRedText6));

        arrayList.add(new ThemeDescription(smsView1.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView1.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView1.codeFieldContainer.codeField != null) {
            for (int a = 0; a < smsView1.codeFieldContainer.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView1.codeFieldContainer.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView1.codeFieldContainer.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView1.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView1.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView1.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView1.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView1.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView1.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView2.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView2.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView2.codeFieldContainer.codeField != null) {
            for (int a = 0; a < smsView2.codeFieldContainer.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView2.codeFieldContainer.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView2.codeFieldContainer.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView2.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView2.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView2.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView2.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView2.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView2.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView3.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView3.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView3.codeFieldContainer.codeField != null) {
            for (int a = 0; a < smsView3.codeFieldContainer.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView3.codeFieldContainer.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView3.codeFieldContainer.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView3.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView3.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView3.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView3.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView3.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView3.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView4.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView4.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView4.codeFieldContainer.codeField != null) {
            for (int a = 0; a < smsView4.codeFieldContainer.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView4.codeFieldContainer.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView4.codeFieldContainer.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView4.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView4.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView4.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView4.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView4.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView4.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView4.blueImageView, 0, null, null, null, descriptionDelegate, Theme.key_chats_actionBackground));

        for (int a = 0; a < 2; a++) {
            if (views[9 + a] == null) {
                continue;
            }
            LoginActivityNewPasswordView view = (LoginActivityNewPasswordView) views[9 + a];
            arrayList.add(new ThemeDescription(view.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
            for (int b = 0; b < view.codeField.length; b++) {
                arrayList.add(new ThemeDescription(view.codeField[b], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(view.codeField[b], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
                arrayList.add(new ThemeDescription(view.codeField[b], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
                arrayList.add(new ThemeDescription(view.codeField[b], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
            arrayList.add(new ThemeDescription(view.cancelButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        }

        return arrayList;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Looper;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.EmuDetector;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.Intro;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class IntroActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static int ICON_WIDTH_DP = 200, ICON_HEIGHT_DP = 150;

    private final Object pagerHeaderTag = new Object(),
            pagerMessageTag = new Object();

    private int currentAccount = UserConfig.selectedAccount;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView switchLanguageTextView;
    private TextView startMessagingButton;
    private FrameLayout frameLayout2;
    private FrameLayout frameContainerView;

    private RLottieDrawable darkThemeDrawable;

    private int lastPage = 0;
    private boolean justCreated = false;
    private boolean startPressed = false;
    private String[] titles;
    private String[] messages;
    private int currentViewPagerPage;
    private EGLThread eglThread;
    private long currentDate;
    private boolean justEndDragging;
    private boolean dragging;
    private int startDragX;

    private LocaleController.LocaleInfo localeInfo;

    private boolean destroyed;

    private boolean isOnLogout;

    @Override
    public boolean onFragmentCreate() {
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", System.currentTimeMillis()).apply();

        titles = new String[]{
                LocaleController.getString("Page1Title", R.string.Page1Title),
                LocaleController.getString("Page2Title", R.string.Page2Title),
                LocaleController.getString("Page3Title", R.string.Page3Title),
                LocaleController.getString("Page5Title", R.string.Page5Title),
                LocaleController.getString("Page4Title", R.string.Page4Title),
                LocaleController.getString("Page6Title", R.string.Page6Title)
        };
        messages = new String[]{
                LocaleController.getString("Page1Message", R.string.Page1Message),
                LocaleController.getString("Page2Message", R.string.Page2Message),
                LocaleController.getString("Page3Message", R.string.Page3Message),
                LocaleController.getString("Page5Message", R.string.Page5Message),
                LocaleController.getString("Page4Message", R.string.Page4Message),
                LocaleController.getString("Page6Message", R.string.Page6Message)
        };
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        RLottieImageView themeIconView = new RLottieImageView(context);
        FrameLayout themeFrameLayout = new FrameLayout(context);
        themeFrameLayout.addView(themeIconView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));

        int themeMargin = 4;
        frameContainerView = new FrameLayout(context) {

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                int oneFourth = (bottom - top) / 4;

                int y = (oneFourth * 3 - AndroidUtilities.dp(275)) / 2;
                frameLayout2.layout(0, y, frameLayout2.getMeasuredWidth(), y + frameLayout2.getMeasuredHeight());
                y += AndroidUtilities.dp(ICON_HEIGHT_DP);
                y += AndroidUtilities.dp(122);
                int x = (getMeasuredWidth() - bottomPages.getMeasuredWidth()) / 2;
                bottomPages.layout(x, y, x + bottomPages.getMeasuredWidth(), y + bottomPages.getMeasuredHeight());
                viewPager.layout(0, 0, viewPager.getMeasuredWidth(), viewPager.getMeasuredHeight());

                y = oneFourth * 3 + (oneFourth - startMessagingButton.getMeasuredHeight()) / 2;
                x = (getMeasuredWidth() - startMessagingButton.getMeasuredWidth()) / 2;
                startMessagingButton.layout(x, y, x + startMessagingButton.getMeasuredWidth(), y + startMessagingButton.getMeasuredHeight());
                y -= AndroidUtilities.dp(30);
                x = (getMeasuredWidth() - switchLanguageTextView.getMeasuredWidth()) / 2;
                switchLanguageTextView.layout(x, y - switchLanguageTextView.getMeasuredHeight(), x + switchLanguageTextView.getMeasuredWidth(), y);

                MarginLayoutParams marginLayoutParams = (MarginLayoutParams) themeFrameLayout.getLayoutParams();
                int newTopMargin = AndroidUtilities.dp(themeMargin) + (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight);
                if (marginLayoutParams.topMargin != newTopMargin) {
                    marginLayoutParams.topMargin = newTopMargin;
                    themeFrameLayout.requestLayout();
                }
            }
        };
        scrollView.addView(frameContainerView, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        darkThemeDrawable = new RLottieDrawable(R.raw.sun, String.valueOf(R.raw.sun), AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
        darkThemeDrawable.beginApplyLayerColors();
        darkThemeDrawable.commitApplyLayerColors();

        darkThemeDrawable.setCustomEndFrame(Theme.getCurrentTheme().isDark() ? darkThemeDrawable.getFramesCount() - 1 : 0);
        darkThemeDrawable.setCurrentFrame(Theme.getCurrentTheme().isDark() ? darkThemeDrawable.getFramesCount() - 1 : 0, false);
        themeIconView.setContentDescription(LocaleController.getString(Theme.getCurrentTheme().isDark() ? R.string.AccDescrSwitchToDayTheme : R.string.AccDescrSwitchToNightTheme));

        themeIconView.setAnimation(darkThemeDrawable);
        themeFrameLayout.setOnClickListener(v -> {
            if (DrawerProfileCell.switchingTheme) return;
            DrawerProfileCell.switchingTheme = true;

            // TODO: Generify this part, currently it's a clone of another theme switch toggle
            String dayThemeName = "Blue";
            String nightThemeName = "Night";

            Theme.ThemeInfo themeInfo;
            boolean toDark;
            if (toDark = !Theme.isCurrentThemeDark()) {
                themeInfo = Theme.getTheme(nightThemeName);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
            }

            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
            Theme.saveAutoNightThemeConfig();
            Theme.cancelAutoNightThemeCallbacks();

            darkThemeDrawable.setCustomEndFrame(toDark ? darkThemeDrawable.getFramesCount() - 1 : 0);
            themeIconView.playAnimation();

            int[] pos = new int[2];
            themeIconView.getLocationInWindow(pos);
            pos[0] += themeIconView.getMeasuredWidth() / 2;
            pos[1] += themeIconView.getMeasuredHeight() / 2;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, themeIconView);
            themeIconView.setContentDescription(LocaleController.getString(toDark ? R.string.AccDescrSwitchToDayTheme : R.string.AccDescrSwitchToNightTheme));
        });

        frameLayout2 = new FrameLayout(context);
        frameContainerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 78, 0, 0));

        TextureView textureView = new TextureView(context);
        frameLayout2.addView(textureView, LayoutHelper.createFrame(ICON_WIDTH_DP, ICON_HEIGHT_DP, Gravity.CENTER));
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (eglThread == null && surface != null) {
                    eglThread = new EGLThread(surface);
                    eglThread.setSurfaceTextureSize(width, height);
                    eglThread.postRunnable(()->{
                        float time = (System.currentTimeMillis() - currentDate) / 1000.0f;
                        Intro.setPage(currentViewPagerPage);
                        Intro.setDate(time);
                        Intro.onDrawFrame(0);
                        if (eglThread != null && eglThread.isAlive() && eglThread.eglDisplay != null && eglThread.eglSurface != null) {
                            try {
                                eglThread.egl10.eglSwapBuffers(eglThread.eglDisplay, eglThread.eglSurface);
                            } catch (Exception ignored) {} // If display or surface already destroyed
                        }
                    });
                    eglThread.postRunnable(eglThread.drawRunnable);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
                if (eglThread != null) {
                    eglThread.setSurfaceTextureSize(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (eglThread != null) {
                    eglThread.shutdown();
                    eglThread = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        viewPager = new ViewPager(context);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        frameContainerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                bottomPages.setPageOffset(position, positionOffset);

                float width = viewPager.getMeasuredWidth();
                if (width == 0) {
                    return;
                }
                float offset = (position * width + positionOffsetPixels - currentViewPagerPage * width) / width;
                Intro.setScrollOffset(offset);
            }

            @Override
            public void onPageSelected(int i) {
                currentViewPagerPage = i;
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if (i == ViewPager.SCROLL_STATE_DRAGGING) {
                    dragging = true;
                    startDragX = viewPager.getCurrentItem() * viewPager.getMeasuredWidth();
                } else if (i == ViewPager.SCROLL_STATE_IDLE || i == ViewPager.SCROLL_STATE_SETTLING) {
                    if (dragging) {
                        justEndDragging = true;
                        dragging = false;
                    }
                    if (lastPage != viewPager.getCurrentItem()) {
                        lastPage = viewPager.getCurrentItem();
                    }
                }
            }
        });

        startMessagingButton = new TextView(context) {
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
        startMessagingButton.setText(LocaleController.getString("StartMessaging", R.string.StartMessaging));
        startMessagingButton.setGravity(Gravity.CENTER);
        startMessagingButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        frameContainerView.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 76));
        startMessagingButton.setOnClickListener(view -> {
            if (startPressed) {
                return;
            }
            startPressed = true;

            presentFragment(new LoginActivity().setIntroView(frameContainerView, startMessagingButton), true);
            destroyed = true;
        });

        bottomPages = new BottomPagesView(context, viewPager, 6);
        frameContainerView.addView(bottomPages, LayoutHelper.createFrame(66, 5, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, ICON_HEIGHT_DP + 200, 0, 0));

        switchLanguageTextView = new TextView(context);
        switchLanguageTextView.setGravity(Gravity.CENTER);
        switchLanguageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        frameContainerView.addView(switchLanguageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        switchLanguageTextView.setOnClickListener(v -> {
            if (startPressed || localeInfo == null) {
                return;
            }
            startPressed = true;

            AlertDialog loaderDialog = new AlertDialog(v.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            loaderDialog.setCanCancel(false);
            loaderDialog.showDelayed(1000);

            NotificationCenter.getGlobalInstance().addObserver(new NotificationCenter.NotificationCenterDelegate() {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.reloadInterface) {
                        loaderDialog.dismiss();

                        NotificationCenter.getGlobalInstance().removeObserver(this, id);
                        AndroidUtilities.runOnUIThread(()->{
                            presentFragment(new LoginActivity().setIntroView(frameContainerView, startMessagingButton), true);
                            destroyed = true;
                        }, 100);
                    }
                }
            }, NotificationCenter.reloadInterface);
            LocaleController.getInstance().applyLanguage(localeInfo, true, false, currentAccount);
        });

        frameContainerView.addView(themeFrameLayout, LayoutHelper.createFrame(64, 64, Gravity.TOP | Gravity.RIGHT, 0, themeMargin, themeMargin, 0));

        fragmentView = scrollView;

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.configLoaded);
        ConnectionsManager.getInstance(currentAccount).updateDcSettings();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount);
        checkContinueText();
        justCreated = true;

        updateColors(false);

        return fragmentView;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (justCreated) {
            if (LocaleController.isRTL) {
                viewPager.setCurrentItem(6);
                lastPage = 6;
            } else {
                viewPager.setCurrentItem(0);
                lastPage = 0;
            }
            justCreated = false;
        }
        if (!AndroidUtilities.isTablet()) {
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!AndroidUtilities.isTablet()) {
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        destroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.configLoaded);
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", 0).apply();
    }

    private void checkContinueText() {
        LocaleController.LocaleInfo englishInfo = null;
        LocaleController.LocaleInfo systemInfo = null;
        LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        String systemLang = MessagesController.getInstance(currentAccount).suggestedLangCode;
        if (systemLang == null || systemLang.equals("en") && LocaleController.getInstance().getSystemDefaultLocale().getLanguage() != null && !LocaleController.getInstance().getSystemDefaultLocale().getLanguage().equals("en")) {
            systemLang = LocaleController.getInstance().getSystemDefaultLocale().getLanguage();
            if (systemLang == null) {
                systemLang = "en";
            }
        }

        String arg = systemLang.contains("-") ? systemLang.split("-")[0] : systemLang;
        String alias = LocaleController.getLocaleAlias(arg);
        for (int a = 0; a < LocaleController.getInstance().languages.size(); a++) {
            LocaleController.LocaleInfo info = LocaleController.getInstance().languages.get(a);
            if (info.shortName.equals("en")) {
                englishInfo = info;
            }
            if (info.shortName.replace("_", "-").equals(systemLang) || info.shortName.equals(arg) || info.shortName.equals(alias)) {
                systemInfo = info;
            }
            if (englishInfo != null && systemInfo != null) {
                break;
            }
        }
        if (englishInfo == null || systemInfo == null || englishInfo == systemInfo) {
            return;
        }
        TLRPC.TL_langpack_getStrings req = new TLRPC.TL_langpack_getStrings();
        if (systemInfo != currentLocaleInfo) {
            req.lang_code = systemInfo.getLangCode();
            localeInfo = systemInfo;
        } else {
            req.lang_code = englishInfo.getLangCode();
            localeInfo = englishInfo;
        }
        req.keys.add("ContinueOnThisLanguage");
        String finalSystemLang = systemLang;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                if (vector.objects.isEmpty()) {
                    return;
                }
                final TLRPC.LangPackString string = (TLRPC.LangPackString) vector.objects.get(0);
                if (string instanceof TLRPC.TL_langPackString) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!destroyed) {
                            switchLanguageTextView.setText(string.value);
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            preferences.edit().putString("language_showed2", finalSystemLang.toLowerCase()).apply();
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack || id == NotificationCenter.configLoaded) {
            checkContinueText();
        }
    }

    public IntroActivity setOnLogout() {
        isOnLogout = true;
        return this;
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (isOnLogout) {
            AnimatorSet set = new AnimatorSet().setDuration(50);
            set.playTogether(ValueAnimator.ofFloat());
            return set;
        }
        return null;
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return titles.length;
        }

        @NonNull
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TextView headerTextView = new TextView(container.getContext());
            headerTextView.setTag(pagerHeaderTag);
            TextView messageTextView = new TextView(container.getContext());
            messageTextView.setTag(pagerMessageTag);

            FrameLayout frameLayout = new FrameLayout(container.getContext()) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int oneFourth = (bottom - top) / 4;
                    int y = (oneFourth * 3 - AndroidUtilities.dp(275)) / 2;
                    y += AndroidUtilities.dp(ICON_HEIGHT_DP);
                    y += AndroidUtilities.dp(16);
                    int x = AndroidUtilities.dp(18);
                    headerTextView.layout(x, y, x + headerTextView.getMeasuredWidth(), y + headerTextView.getMeasuredHeight());

                    y += headerTextView.getTextSize();
                    y += AndroidUtilities.dp(16);
                    x = AndroidUtilities.dp(16);
                    messageTextView.layout(x, y, x + messageTextView.getMeasuredWidth(), y + messageTextView.getMeasuredHeight());
                }
            };

            headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            headerTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 18, 244, 18, 0));

            messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            messageTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 286, 16, 0));

            container.addView(frameLayout, 0);

            headerTextView.setText(titles[position]);
            messageTextView.setText(AndroidUtilities.replaceTags(messages[position]));

            return frameLayout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            bottomPages.setCurrentPage(position);
            currentViewPagerPage = position;
        }

        @Override
        public boolean isViewFromObject(View view, @NonNull Object object) {
            return view.equals(object);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    public class EGLThread extends DispatchQueue {

        private final static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private final static int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLConfig eglConfig;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private boolean initied;
        private int[] textures = new int[24];

        private float maxRefreshRate;
        private long lastDrawFrame;

        private GenericProvider<Void, Bitmap> telegramMaskProvider = v -> {
            int size = AndroidUtilities.dp(ICON_HEIGHT_DP);
            Bitmap bm = Bitmap.createBitmap(AndroidUtilities.dp(ICON_WIDTH_DP), size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            c.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            c.drawCircle(bm.getWidth() / 2f, bm.getHeight() / 2f, size / 2f, paint);
            return bm;
        };

        public EGLThread(SurfaceTexture surface) {
            super("EGLThread");
            surfaceTexture = surface;
        }

        private boolean initGL() {
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec;
            if (EmuDetector.with(getParentActivity()).detect()) {
                configSpec = new int[] {
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_DEPTH_SIZE, 24,
                        EGL10.EGL_NONE
                };
            } else {
                configSpec = new int[] {
                        EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_DEPTH_SIZE, 24,
                        EGL10.EGL_STENCIL_SIZE, 0,
                        EGL10.EGL_SAMPLE_BUFFERS, 1,
                        EGL10.EGL_SAMPLES, 2,
                        EGL10.EGL_NONE
                };
            }
            if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglConfig not initialized");
                }
                finish();
                return false;
            }

            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            if (eglContext == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            if (surfaceTexture instanceof SurfaceTexture) {
                eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            } else {
                finish();
                return false;
            }

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            GLES20.glGenTextures(23, textures, 0);
            loadTexture(R.drawable.intro_fast_arrow_shadow, 0);
            loadTexture(R.drawable.intro_fast_arrow, 1);
            loadTexture(R.drawable.intro_fast_body, 2);
            loadTexture(R.drawable.intro_fast_spiral, 3);
            loadTexture(R.drawable.intro_ic_bubble_dot, 4);
            loadTexture(R.drawable.intro_ic_bubble, 5);
            loadTexture(R.drawable.intro_ic_cam_lens, 6);
            loadTexture(R.drawable.intro_ic_cam, 7);
            loadTexture(R.drawable.intro_ic_pencil, 8);
            loadTexture(R.drawable.intro_ic_pin, 9);
            loadTexture(R.drawable.intro_ic_smile_eye, 10);
            loadTexture(R.drawable.intro_ic_smile, 11);
            loadTexture(R.drawable.intro_ic_videocam, 12);
            loadTexture(R.drawable.intro_knot_down, 13);
            loadTexture(R.drawable.intro_knot_up, 14);
            loadTexture(R.drawable.intro_powerful_infinity_white, 15);
            loadTexture(R.drawable.intro_powerful_infinity, 16);
            loadTexture(R.drawable.intro_powerful_mask, 17, Theme.getColor(Theme.key_windowBackgroundWhite), false);
            loadTexture(R.drawable.intro_powerful_star, 18);
            loadTexture(R.drawable.intro_private_door, 19);
            loadTexture(R.drawable.intro_private_screw, 20);
            loadTexture(R.drawable.intro_tg_plane, 21);
            loadTexture(v -> {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(0xFF2CA5E0); // It's logo color, it should not be colored by the theme
                int size = AndroidUtilities.dp(ICON_HEIGHT_DP);
                Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm);
                c.drawCircle(size / 2f, size / 2f, size / 2f, paint);
                return bm;
            }, 22);
            loadTexture(telegramMaskProvider, 23);

            updateTelegramTextures();
            updatePowerfulTextures();
            Intro.setPrivateTextures(textures[19], textures[20]);
            Intro.setFreeTextures(textures[14], textures[13]);
            Intro.setFastTextures(textures[2], textures[3], textures[1], textures[0]);
            Intro.setIcTextures(textures[4], textures[5], textures[6], textures[7], textures[8], textures[9], textures[10], textures[11], textures[12]);
            Intro.onSurfaceCreated();
            currentDate = System.currentTimeMillis() - 1000;

            return true;
        }

        public void updateTelegramTextures() {
            Intro.setTelegramTextures(textures[22], textures[21], textures[23]);
        }

        public void updatePowerfulTextures() {
            Intro.setPowerfulTextures(textures[17], textures[18], textures[16], textures[15]);
        }

        public void finish() {
            if (eglSurface != null) {
                egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }
            if (eglContext != null) {
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
        }

        private Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!initied) {
                    return;
                }

                long current = System.currentTimeMillis();
                if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                        }
                        return;
                    }
                }
                int deltaDrawMs = (int) Math.min(current - lastDrawFrame, 16);
                float time = (current - currentDate) / 1000.0f;
                Intro.setPage(currentViewPagerPage);
                Intro.setDate(time);
                Intro.onDrawFrame(deltaDrawMs);
                egl10.eglSwapBuffers(eglDisplay, eglSurface);
                lastDrawFrame = current;

                if (maxRefreshRate == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                        Display display = wm.getDefaultDisplay();
                        float[] rates = display.getSupportedRefreshRates();
                        float maxRate = 0;
                        for (float rate : rates) {
                            if (rate > maxRate) {
                                maxRate = rate;
                            }
                        }
                        maxRefreshRate = maxRate;
                    } else maxRefreshRate = 60;
                }

                long drawMs = System.currentTimeMillis() - current;
                postRunnable(drawRunnable, Math.max((long) (1000 / maxRefreshRate) - drawMs, 0));
            }
        };

        private void loadTexture(GenericProvider<Void, Bitmap> bitmapProvider, int index) {
            loadTexture(bitmapProvider, index, false);
        }

        private void loadTexture(GenericProvider<Void, Bitmap> bitmapProvider, int index, boolean rebind) {
            if (rebind) {
                GLES20.glDeleteTextures(1, textures, index);
                GLES20.glGenTextures(1, textures, index);
            }
            Bitmap bm = bitmapProvider.provide(null);
            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[index]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bm, 0);
            bm.recycle();
        }

        private void loadTexture(int resId, int index) {
            loadTexture(resId, index, 0, false);
        }

        private void loadTexture(int resId, int index, int tintColor, boolean rebind) {
            Drawable drawable = getParentActivity().getResources().getDrawable(resId);
            if (drawable instanceof BitmapDrawable) {
                if (rebind) {
                    GLES20.glDeleteTextures(1, textures, index);
                    GLES20.glGenTextures(1, textures, index);
                }

                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[index]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

                if (tintColor != 0) {
                    Bitmap tempBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(tempBitmap);
                    Paint tempPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                    tempPaint.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
                    canvas.drawBitmap(bitmap, 0, 0, tempPaint);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, tempBitmap, 0);
                    tempBitmap.recycle();
                } else {
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
                }
            }
        }

        public void shutdown() {
            postRunnable(() -> {
                finish();
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            });
        }

        public void setSurfaceTextureSize(int width, int height) {
            Intro.onSurfaceChanged(width, height, Math.min(width / 150.0f, height / 150.0f), 0);
        }

        @Override
        public void run() {
            initied = initGL();
            super.run();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(() -> updateColors(true), Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlueText4, Theme.key_chats_actionBackground, Theme.key_chats_actionPressedBackground,
                Theme.key_featuredStickers_buttonText, Theme.key_windowBackgroundWhiteBlackText, Theme.key_windowBackgroundWhiteGrayText3
        );
    }

    private void updateColors(boolean fromTheme) {
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        switchLanguageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        startMessagingButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        startMessagingButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_changephoneinfo_image2), Theme.getColor(Theme.key_chats_actionPressedBackground)));
        darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_changephoneinfo_image2), PorterDuff.Mode.SRC_IN));
        bottomPages.invalidate();
        if (fromTheme) {
            if (eglThread != null) {
                eglThread.postRunnable(()->{
                    eglThread.loadTexture(R.drawable.intro_powerful_mask, 17, Theme.getColor(Theme.key_windowBackgroundWhite), true);
                    eglThread.updatePowerfulTextures();

                    eglThread.loadTexture(eglThread.telegramMaskProvider, 23, true);
                    eglThread.updateTelegramTextures();

                    Intro.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                });
            }
            for (int i = 0; i < viewPager.getChildCount(); i++) {
                View ch = viewPager.getChildAt(i);
                TextView headerTextView = ch.findViewWithTag(pagerHeaderTag);
                headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                TextView messageTextView = ch.findViewWithTag(pagerMessageTag);
                messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            }
        } else Intro.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }
}

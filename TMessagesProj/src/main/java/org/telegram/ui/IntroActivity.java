/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Intro;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class IntroActivity extends AppCompatActivity implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount = UserConfig.selectedAccount;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView textView;
    private TextView startMessagingButton;
    private FrameLayout frameLayout2;

    private int lastPage = 0;
    private boolean justCreated = false;
    private boolean startPressed = false;
    private String[] titles;
    private String[] messages;
    private int currentViewPagerPage;
    private boolean dragging;
    private boolean destroyed;

    private LocaleController.LocaleInfo localeInfo;

    private int getBackgroundColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    private int getTextColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    private int getSecondaryTextColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    private void initCG() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int color = Theme.getColor(Theme.key_chat_messagePanelBackground, null, true);
            getWindow().setStatusBarColor(color);
            AndroidUtilities.setLightStatusBar(getWindow(), color == Color.WHITE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final Window window = getWindow();
                if (window.getNavigationBarColor() != color) {
                    window.setNavigationBarColor(color);
                    final float brightness = AndroidUtilities.computePerceivedBrightness(color);
                    AndroidUtilities.setLightNavigationBar(getWindow(), brightness >= 0.721f);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        initCG();

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putLong("intro_crashed_time", System.currentTimeMillis()).commit();

        titles = new String[]{
                LocaleController.getString("CG_AppName", R.string.CG_AppName),
                LocaleController.getString("Page2Title", R.string.Page2Title),
                LocaleController.getString("Page3Title", R.string.Page3Title),
                LocaleController.getString("Page5Title", R.string.Page5Title),
                LocaleController.getString("Page4Title", R.string.Page4Title),
                LocaleController.getString("Page6Title", R.string.Page6Title)
        };
        messages = new String[]{
                LocaleController.getString("CG_IntroMessage", R.string.CG_IntroMessage),
                LocaleController.getString("Page2Message", R.string.Page2Message),
                LocaleController.getString("Page3Message", R.string.Page3Message),
                LocaleController.getString("Page5Message", R.string.Page5Message),
                LocaleController.getString("Page4Message", R.string.Page4Message),
                LocaleController.getString("Page6Message", R.string.Page6Message)
        };

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        FrameLayout frameLayout = new FrameLayout(this) {

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                int oneFourth = (bottom - top) / 4;

                int y = (oneFourth * 3 - AndroidUtilities.dp(275)) / 2;
                frameLayout2.layout(0, y, frameLayout2.getMeasuredWidth(), y + frameLayout2.getMeasuredHeight());
                y += AndroidUtilities.dp(272);
                int x = (getMeasuredWidth() - bottomPages.getMeasuredWidth()) / 2;
                bottomPages.layout(x, y, x + bottomPages.getMeasuredWidth(), y + bottomPages.getMeasuredHeight());
                viewPager.layout(0, 0, viewPager.getMeasuredWidth(), viewPager.getMeasuredHeight());

                y = oneFourth * 3 + (oneFourth - startMessagingButton.getMeasuredHeight()) / 2;
                x = (getMeasuredWidth() - startMessagingButton.getMeasuredWidth()) / 2;
                startMessagingButton.layout(x, y, x + startMessagingButton.getMeasuredWidth(), y + startMessagingButton.getMeasuredHeight());
                y -= AndroidUtilities.dp(30);
                x = (getMeasuredWidth() - textView.getMeasuredWidth()) / 2;
                textView.layout(x, y - textView.getMeasuredHeight(), x + textView.getMeasuredWidth(), y);
            }
        };

        frameLayout.setBackgroundColor(getBackgroundColor());
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        frameLayout2 = new FrameLayout(this);
        frameLayout2.setBackgroundColor(getBackgroundColor());
        frameLayout.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 78, 0, 0));

        AppCompatImageView textureView = new AppCompatImageView(this);
        frameLayout2.addView(textureView, LayoutHelper.createFrame(200, 150, Gravity.CENTER));
        textureView.setImageResource(R.mipmap.cg_launcher_alt_blue);

        viewPager = new ViewPager(this);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
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
                } else if (i == ViewPager.SCROLL_STATE_IDLE || i == ViewPager.SCROLL_STATE_SETTLING) {
                    if (dragging) {
                        dragging = false;
                    }
                    if (lastPage != viewPager.getCurrentItem()) {
                        lastPage = viewPager.getCurrentItem();
                    }
                }
            }
        });

        startMessagingButton = new androidx.appcompat.widget.AppCompatTextView(this) {
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
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4));
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
        startMessagingButton.setTextColor(0xffffffff);
        startMessagingButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), 0xff50a8eb, 0xff439bde));
        startMessagingButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        frameLayout.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 36, 0, 36, 76));
        startMessagingButton.setOnClickListener(view -> {
            if (startPressed) {
                return;
            }
            startPressed = true;
            Intent intent2 = new Intent(IntroActivity.this, LaunchActivity.class);
            intent2.putExtra("fromIntro", true);
            startActivity(intent2);
            destroyed = true;
            finish();
        });

        bottomPages = new BottomPagesView(this, viewPager, 6);
        frameLayout.addView(bottomPages, LayoutHelper.createFrame(66, 5, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 350, 0, 0));

        textView = new TextView(this);
        textView.setTextColor(0xff1393d2);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        textView.setOnClickListener(v -> {
            if (startPressed || localeInfo == null) {
                return;
            }
            LocaleController.getInstance().applyLanguage(localeInfo, true, false, currentAccount);
            startPressed = true;
            Intent intent2 = new Intent(IntroActivity.this, LaunchActivity.class);
            intent2.putExtra("fromIntro", true);
            startActivity(intent2);
            destroyed = true;
            finish();
        });

        if (AndroidUtilities.isTablet()) {
            FrameLayout frameLayout3 = new FrameLayout(this);
            setContentView(frameLayout3);

            SizeNotifierFrameLayout backgroundTablet = new SizeNotifierFrameLayout(this) {
                @Override
                protected boolean isActionBarVisible() {
                    return false;
                }
            };
            backgroundTablet.setOccupyStatusBar(false);
            backgroundTablet.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            frameLayout3.addView(backgroundTablet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            FrameLayout frameLayout4 = new FrameLayout(this);
            frameLayout4.setBackgroundResource(R.drawable.btnshadow);
            frameLayout4.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            frameLayout3.addView(frameLayout4, LayoutHelper.createFrame(498, 528, Gravity.CENTER));
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setContentView(scrollView);
        }

        LocaleController.getInstance().loadRemoteLanguages(currentAccount);
        checkContinueText();
        justCreated = true;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);

        AndroidUtilities.handleProxyIntent(this, getIntent());
        AndroidUtilities.startAppCenter(this);
    }

    @Override
    protected void onResume() {
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
        ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putLong("intro_crashed_time", 0).commit();
    }

    private void checkContinueText() {
        LocaleController.LocaleInfo englishInfo = null;
        LocaleController.LocaleInfo systemInfo = null;
        LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        final String systemLang = MessagesController.getInstance(currentAccount).suggestedLangCode;
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
                            textView.setText(string.value);
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            preferences.edit().putString("language_showed2", systemLang.toLowerCase()).commit();
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack) {
            checkContinueText();
        }
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return titles.length;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TextView headerTextView = new TextView(container.getContext());
            TextView messageTextView = new TextView(container.getContext());

            FrameLayout frameLayout = new FrameLayout(container.getContext()) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int oneFourth = (bottom - top) / 4;
                    int y = (oneFourth * 3 - AndroidUtilities.dp(275)) / 2;
                    y += AndroidUtilities.dp(166);
                    int x = AndroidUtilities.dp(18);
                    headerTextView.layout(x, y, x + headerTextView.getMeasuredWidth(), y + headerTextView.getMeasuredHeight());

                    y += AndroidUtilities.dp(42);
                    x = AndroidUtilities.dp(16);
                    messageTextView.layout(x, y, x + messageTextView.getMeasuredWidth(), y + messageTextView.getMeasuredHeight());
                }
            };

            headerTextView.setTextColor(getTextColor());
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            headerTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 18, 244, 18, 0));

            messageTextView.setTextColor(getSecondaryTextColor());
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            messageTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 286, 16, 0));

            container.addView(frameLayout, 0);

            headerTextView.setText(titles[position]);
            messageTextView.setText(AndroidUtilities.replaceTags(messages[position]));

            return frameLayout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            bottomPages.setCurrentPage(position);
            currentViewPagerPage = position;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
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
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }
}

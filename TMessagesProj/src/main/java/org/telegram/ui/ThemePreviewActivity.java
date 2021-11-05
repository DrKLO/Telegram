/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.PatternCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.WallpaperCheckBoxView;
import org.telegram.ui.Components.WallpaperParallaxEffect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ThemePreviewActivity extends BaseFragment implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate {

    public static final int SCREEN_TYPE_PREVIEW = 0;
    public static final int SCREEN_TYPE_ACCENT_COLOR = 1;
    public static final int SCREEN_TYPE_CHANGE_BACKGROUND = 2;

    private final int screenType;
    public boolean useDefaultThemeForButtons = true;

    private ActionBarMenuItem dropDownContainer;
    private ActionBarMenuItem saveItem;
    private TextView dropDown;
    private int colorType = 1;

    private Drawable sheetDrawable;

    private Theme.ThemeAccent accent;
    private boolean removeBackgroundOverride;
    private int backupAccentColor;
    private int backupAccentColor2;
    private int backupMyMessagesAccentColor;
    private int backupMyMessagesGradientAccentColor1;
    private int backupMyMessagesGradientAccentColor2;
    private int backupMyMessagesGradientAccentColor3;
    private boolean backupMyMessagesAnimated;
    private long backupBackgroundOverrideColor;
    private long backupBackgroundGradientOverrideColor1;
    private long backupBackgroundGradientOverrideColor2;
    private long backupBackgroundGradientOverrideColor3;
    private float backupIntensity;
    private String backupSlug;
    private int backupBackgroundRotation;

    private long watchForKeyboardEndTime;
    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;

    Theme.MessageDrawable msgOutDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false);
    Theme.MessageDrawable msgOutDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, true);
    Theme.MessageDrawable msgOutMediaDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, false);
    Theme.MessageDrawable msgOutMediaDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, true);

    private ColorPicker colorPicker;
    private int lastPickedColor;
    private int lastPickedColorNum = -1;
    private Runnable applyColorAction = () -> {
        applyColorScheduled = false;
        applyColor(lastPickedColor, lastPickedColorNum);
        lastPickedColorNum = -1;
    };
    private boolean applyColorScheduled;

    private View dotsContainer;
    private FrameLayout saveButtonsContainer;
    private TextView doneButton;
    private TextView cancelButton;

    private Theme.ThemeInfo applyingTheme;
    private boolean nightTheme;
    private boolean editingTheme;
    private boolean deleteOnCancel;
    private List<ThemeDescription> themeDescriptions;

    private ViewPager viewPager;

    private FrameLayout frameLayout;

    private UndoView undoView;

    private FrameLayout page1;
    private RecyclerListView listView;
    private DialogsAdapter dialogsAdapter;
    private ImageView floatingButton;

    private boolean wasScroll;

    private ActionBar actionBar2;
    private FrameLayout page2;
    private RecyclerListView listView2;
    private MessagesAdapter messagesAdapter;
    private BackupImageView backgroundImage;
    private FrameLayout backgroundButtonsContainer;
    private FrameLayout messagesButtonsContainer;
    private HintView animationHint;
    private AnimatorSet motionAnimation;
    private RadialProgress2 radialProgress;
    private FrameLayout bottomOverlayChat;
    private FrameLayout backgroundPlayAnimationView;
    private FrameLayout messagesPlayAnimationView;
    private ImageView backgroundPlayAnimationImageView;
    private ImageView messagesPlayAnimationImageView;
    private AnimatorSet backgroundPlayViewAnimator;
    private AnimatorSet messagesPlayViewAnimator;
    private WallpaperCheckBoxView[] backgroundCheckBoxView;
    private WallpaperCheckBoxView[] messagesCheckBoxView;
    private FrameLayout[] patternLayout = new FrameLayout[2];
    private TextView[] patternsCancelButton = new TextView[2];
    private TextView[] patternsSaveButton = new TextView[2];
    private FrameLayout[] patternsButtonsContainer = new FrameLayout[2];
    private RecyclerListView patternsListView;
    private PatternsAdapter patternsAdapter;
    private LinearLayoutManager patternsLayoutManager;
    private HeaderCell intensityCell;
    private SeekBarView intensitySeekBar;
    private ArrayList<Object> patterns;
    private HashMap<Long, Object> patternsDict = new HashMap<>();
    private TLRPC.TL_wallPaper selectedPattern;
    private TLRPC.TL_wallPaper previousSelectedPattern;
    private TLRPC.TL_wallPaper lastSelectedPattern;
    private int backgroundColor;
    private int previousBackgroundColor;
    private int backgroundGradientColor1;
    private int backgroundGradientColor2;
    private int backgroundGradientColor3;
    private int previousBackgroundGradientColor1;
    private int previousBackgroundGradientColor2;
    private int previousBackgroundGradientColor3;
    private int backgroundRotation;
    private int previousBackgroundRotation;
    private int patternColor;
    private int checkColor;
    private float currentIntensity = 0.5f;
    private float previousIntensity;

    private AnimatorSet patternViewAnimation;

    private final PorterDuff.Mode blendMode = PorterDuff.Mode.SRC_IN;

    private int TAG;

    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private WallpaperParallaxEffect parallaxEffect;
    private Bitmap blurredBitmap;
    private Bitmap originalBitmap;
    private float parallaxScale = 1.0f;

    private TextView bottomOverlayChatText;

    private String loadingFile = null;
    private File loadingFileObject = null;
    private TLRPC.PhotoSize loadingSize = null;

    private Object currentWallpaper;
    private Bitmap currentWallpaperBitmap;
    private boolean rotatePreview;

    private boolean isMotion;
    private boolean isBlurred;

    private boolean showColor;

    private boolean progressVisible;

    private String imageFilter = "640_360";
    private int maxWallpaperSize = 1920;

    private WallpaperActivityDelegate delegate;

    public interface WallpaperActivityDelegate {
        void didSetNewBackground();
    }

    public ThemePreviewActivity(Object wallPaper, Bitmap bitmap) {
        this(wallPaper, bitmap, false, false);
    }

    public ThemePreviewActivity(Object wallPaper, Bitmap bitmap, boolean rotate, boolean openColor) {
        super();
        screenType = SCREEN_TYPE_CHANGE_BACKGROUND;
        showColor = openColor;
        currentWallpaper = wallPaper;
        currentWallpaperBitmap = bitmap;
        rotatePreview = rotate;
        if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            WallpapersListActivity.ColorWallpaper object = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
            isMotion = object.motion;
            selectedPattern = object.pattern;
            if (selectedPattern != null) {
                currentIntensity = object.intensity;
                if (currentIntensity < 0 && !Theme.getActiveTheme().isDark()) {
                    currentIntensity *= -1;
                }
            }
        }
        msgOutDrawable.themePreview = true;
        msgOutMediaDrawable.themePreview = true;
        msgOutDrawableSelected.themePreview = true;
        msgOutMediaDrawableSelected.themePreview = true;
    }

    public ThemePreviewActivity(Theme.ThemeInfo themeInfo) {
        this(themeInfo, false, SCREEN_TYPE_PREVIEW, false, false);
    }

    public ThemePreviewActivity(Theme.ThemeInfo themeInfo, boolean deleteFile, int screenType, boolean edit, boolean night) {
        super();
        this.screenType = screenType;
        nightTheme = night;
        applyingTheme = themeInfo;
        deleteOnCancel = deleteFile;
        editingTheme = edit;

        if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
            accent = applyingTheme.getAccent(!edit);
            useDefaultThemeForButtons = false;
            backupAccentColor = accent.accentColor;
            backupAccentColor2 = accent.accentColor2;
            backupMyMessagesAccentColor = accent.myMessagesAccentColor;
            backupMyMessagesGradientAccentColor1 = accent.myMessagesGradientAccentColor1;
            backupMyMessagesGradientAccentColor2 = accent.myMessagesGradientAccentColor2;
            backupMyMessagesGradientAccentColor3 = accent.myMessagesGradientAccentColor3;
            backupMyMessagesAnimated = accent.myMessagesAnimated;
            backupBackgroundOverrideColor = accent.backgroundOverrideColor;
            backupBackgroundGradientOverrideColor1 = accent.backgroundGradientOverrideColor1;
            backupBackgroundGradientOverrideColor2 = accent.backgroundGradientOverrideColor2;
            backupBackgroundGradientOverrideColor3 = accent.backgroundGradientOverrideColor3;
            backupIntensity = accent.patternIntensity;
            backupSlug = accent.patternSlug;
            backupBackgroundRotation = accent.backgroundRotation;
        } else {
            if (screenType == SCREEN_TYPE_PREVIEW) {
                useDefaultThemeForButtons = false;
            }
            accent = applyingTheme.getAccent(false);
            if (accent != null) {
                selectedPattern = accent.pattern;
            }
        }
        if (accent != null) {
            isMotion = accent.patternMotion;
            if (!TextUtils.isEmpty(accent.patternSlug)) {
                currentIntensity = accent.patternIntensity;
            }
            Theme.applyThemeTemporary(applyingTheme, true);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.goingToPreviewTheme);
        msgOutDrawable.themePreview = true;
        msgOutMediaDrawable.themePreview = true;
        msgOutDrawableSelected.themePreview = true;
        msgOutMediaDrawableSelected.themePreview = true;
    }

    public void setInitialModes(boolean blur, boolean motion) {
        isBlurred = blur;
        isMotion = motion;
    }

    @Override
    public int getNavigationBarColor() {
        return super.getNavigationBarColor();
    }

    @SuppressLint("Recycle")
    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        page1 = new FrameLayout(context);
        ActionBarMenu menu = actionBar.createMenu();
        final ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {

            }

            @Override
            public boolean canCollapseSearch() {
                return true;
            }

            @Override
            public void onSearchCollapse() {

            }

            @Override
            public void onTextChanged(EditText editText) {

            }
        });
        item.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));

        actionBar.setBackButtonDrawable(new MenuDrawable());
        actionBar.setAddToContainer(false);
        actionBar.setTitle(LocaleController.getString("ThemePreview", R.string.ThemePreview));

        page1 = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                if (actionBar.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                listView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));

                measureChildWithMargins(floatingButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() : 0);
                }
                return result;
            }
        };
        page1.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        page1.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(true);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(screenType != SCREEN_TYPE_PREVIEW ? 12 : 0));
        listView.setOnItemClickListener((view, position) -> {

        });
        page1.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButton.setBackgroundDrawable(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButton.setImageResource(R.drawable.floating_pencil);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButton.setStateListAnimator(animator);
            floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        page1.addView(floatingButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));

        dialogsAdapter = new DialogsAdapter(context);
        listView.setAdapter(dialogsAdapter);

        page2 = new FrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                if (dropDownContainer != null) {
                    ignoreLayout = true;
                    if (!AndroidUtilities.isTablet()) {
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
                        layoutParams.topMargin = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                        dropDownContainer.setLayoutParams(layoutParams);
                    }
                    if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        dropDown.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    } else {
                        dropDown.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    }
                    ignoreLayout = false;
                }

                measureChildWithMargins(actionBar2, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar2.getMeasuredHeight();
                if (actionBar2.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView2.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                listView2.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - layoutParams.bottomMargin, MeasureSpec.EXACTLY));

                layoutParams = (FrameLayout.LayoutParams) backgroundImage.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                backgroundImage.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));

                if (bottomOverlayChat != null) {
                    measureChildWithMargins(bottomOverlayChat, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
                for (int a = 0; a < patternLayout.length; a++) {
                    if (patternLayout[a] != null) {
                        measureChildWithMargins(patternLayout[a], widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar2 && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar2.getVisibility() == VISIBLE ? (int) (actionBar2.getMeasuredHeight() + actionBar2.getTranslationY()) : 0);
                }
                return result;
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };

        messagesAdapter = new MessagesAdapter(context);

        actionBar2 = createActionBar(context);
        if (AndroidUtilities.isTablet()) {
            actionBar2.setOccupyStatusBar(false);
        }
        actionBar2.setBackButtonDrawable(new BackDrawable(false));
        actionBar2.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        cancelThemeApply(false);
                    }
                } else if (id >= 1 && id <= 3) {
                    selectColorType(id);
                } else if (id == 4) {
                    if (removeBackgroundOverride) {
                        Theme.resetCustomWallpaper(false);
                    }
                    File path = accent.getPathToWallpaper();
                    if (path != null) {
                        path.delete();
                    }
                    accent.patternSlug = selectedPattern != null ? selectedPattern.slug : "";
                    accent.patternIntensity = currentIntensity;
                    accent.patternMotion = isMotion;
                    if ((int) accent.backgroundOverrideColor == 0) {
                        accent.backgroundOverrideColor = 0x100000000L;
                    }
                    if ((int) accent.backgroundGradientOverrideColor1 == 0) {
                        accent.backgroundGradientOverrideColor1 = 0x100000000L;
                    }
                    if ((int) accent.backgroundGradientOverrideColor2 == 0) {
                        accent.backgroundGradientOverrideColor2 = 0x100000000L;
                    }
                    if ((int) accent.backgroundGradientOverrideColor3 == 0) {
                        accent.backgroundGradientOverrideColor3 = 0x100000000L;
                    }
                    saveAccentWallpaper();
                    NotificationCenter.getGlobalInstance().removeObserver(ThemePreviewActivity.this, NotificationCenter.wallpapersDidLoad);
                    Theme.saveThemeAccents(applyingTheme, true, false, false, true);
                    Theme.applyPreviousTheme();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, applyingTheme, nightTheme, null, -1);
                    finishFragment();
                } else if (id == 5) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    String link;
                    StringBuilder modes = new StringBuilder();
                    if (isBlurred) {
                        modes.append("blur");
                    }
                    if (isMotion) {
                        if (modes.length() > 0) {
                            modes.append("+");
                        }
                        modes.append("motion");
                    }
                    if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                        link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/bg/" + wallPaper.slug;
                        if (modes.length() > 0) {
                            link += "?mode=" + modes.toString();
                        }
                    } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        WallpapersListActivity.ColorWallpaper colorWallpaper = new WallpapersListActivity.ColorWallpaper(selectedPattern != null ? selectedPattern.slug : Theme.COLOR_BACKGROUND_SLUG, backgroundColor, backgroundGradientColor1, backgroundGradientColor2, backgroundGradientColor3, backgroundRotation, currentIntensity, isMotion, null);
                        colorWallpaper.pattern = selectedPattern;
                        link = colorWallpaper.getUrl();
                    } else {
                        if (BuildVars.DEBUG_PRIVATE_VERSION) {
                            Theme.ThemeAccent accent = Theme.getActiveTheme().getAccent(false);
                            if (accent != null) {
                                WallpapersListActivity.ColorWallpaper colorWallpaper = new WallpapersListActivity.ColorWallpaper(accent.patternSlug, (int) accent.backgroundOverrideColor, (int) accent.backgroundGradientOverrideColor1, (int) accent.backgroundGradientOverrideColor2, (int) accent.backgroundGradientOverrideColor3, accent.backgroundRotation, accent.patternIntensity, accent.patternMotion, null);
                                for (int a = 0, N = patterns.size(); a < N; a++) {
                                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) patterns.get(a);
                                    if (wallPaper.pattern) {
                                        if (accent.patternSlug.equals(wallPaper.slug)) {
                                            colorWallpaper.pattern = wallPaper;
                                            break;
                                        }
                                    }
                                }
                                link = colorWallpaper.getUrl();
                            } else {
                                return;
                            }
                        } else {
                            return;
                        }
                    }
                    showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false) {
                        @Override
                        protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count) {
                            if (dids.size() == 1) {
                                undoView.showWithAction(dids.valueAt(0).id, UndoView.ACTION_SHARE_BACKGROUND, count);
                            } else {
                                undoView.showWithAction(0, UndoView.ACTION_SHARE_BACKGROUND, count, dids.size(), null, null);
                            }
                        }
                    });
                }
            }
        });

        backgroundImage = new BackupImageView(context) {

            private Drawable background;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                parallaxScale = parallaxEffect.getScale(getMeasuredWidth(), getMeasuredHeight());
                if (isMotion) {
                    setScaleX(parallaxScale);
                    setScaleY(parallaxScale);
                }
                if (radialProgress != null) {
                    int size = AndroidUtilities.dp(44);
                    int x = (getMeasuredWidth() - size) / 2;
                    int y = (getMeasuredHeight() - size) / 2;
                    radialProgress.setProgressRect(x, y, x + size, y + size);
                }

                progressVisible = screenType == SCREEN_TYPE_CHANGE_BACKGROUND && getMeasuredWidth() <= getMeasuredHeight();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (background instanceof ColorDrawable || background instanceof GradientDrawable || background instanceof MotionBackgroundDrawable) {
                    background.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    background.draw(canvas);
                } else if (background instanceof BitmapDrawable) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) background;
                    if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                        canvas.save();
                        float scale = 2.0f / AndroidUtilities.density;
                        canvas.scale(scale, scale);
                        background.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                        background.draw(canvas);
                        canvas.restore();
                    } else {
                        int viewHeight = getMeasuredHeight();
                        float scaleX = (float) getMeasuredWidth() / (float) background.getIntrinsicWidth();
                        float scaleY = (float) (viewHeight) / (float) background.getIntrinsicHeight();
                        float scale = Math.max(scaleX, scaleY);
                        int width = (int) Math.ceil(background.getIntrinsicWidth() * scale * parallaxScale);
                        int height = (int) Math.ceil(background.getIntrinsicHeight() * scale * parallaxScale);
                        int x = (getMeasuredWidth() - width) / 2;
                        int y = (viewHeight - height) / 2;
                        background.setBounds(x, y, x + width, y + height);
                        background.draw(canvas);
                    }
                }
                super.onDraw(canvas);
                if (progressVisible && radialProgress != null) {
                    radialProgress.draw(canvas);
                }
            }

            @Override
            public Drawable getBackground() {
                return background;
            }

            @Override
            public void setBackground(Drawable drawable) {
                background = drawable;
            }

            @Override
            public void setAlpha(float alpha) {
                if (radialProgress != null) {
                    radialProgress.setOverrideAlpha(alpha);
                }
            }
        };

        page2.addView(backgroundImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));
        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            backgroundImage.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
                if (!(currentWallpaper instanceof WallpapersListActivity.ColorWallpaper)) {
                    Drawable dr = imageReceiver.getDrawable();
                    if (set && dr != null) {
                        if (!Theme.hasThemeKey(Theme.key_chat_serviceBackground) || backgroundImage.getBackground() instanceof MotionBackgroundDrawable) {
                            Theme.applyChatServiceMessageColor(AndroidUtilities.calcDrawableColor(dr), dr);
                        }
                        listView2.invalidateViews();
                        if (backgroundButtonsContainer != null) {
                            for (int a = 0, N = backgroundButtonsContainer.getChildCount(); a < N; a++) {
                                backgroundButtonsContainer.getChildAt(a).invalidate();
                            }
                        }
                        if (messagesButtonsContainer != null) {
                            for (int a = 0, N = messagesButtonsContainer.getChildCount(); a < N; a++) {
                                messagesButtonsContainer.getChildAt(a).invalidate();
                            }
                        }
                        if (radialProgress != null) {
                            radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);
                        }
                        if (!thumb && isBlurred && blurredBitmap == null) {
                            backgroundImage.getImageReceiver().setCrossfadeWithOldImage(false);
                            updateBlurred();
                            backgroundImage.getImageReceiver().setCrossfadeWithOldImage(true);
                        }
                    }
                }
            });
        }

        if (messagesAdapter.showSecretMessages) {
            actionBar2.setTitle("Telegram Beta Chat");
            actionBar2.setSubtitle(LocaleController.formatPluralString("Members", 505));
        } else {
            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                actionBar2.setTitle(LocaleController.getString("BackgroundPreview", R.string.BackgroundPreview));
                if (BuildVars.DEBUG_PRIVATE_VERSION && Theme.getActiveTheme().getAccent(false) != null || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper && !Theme.DEFAULT_BACKGROUND_SLUG.equals(((WallpapersListActivity.ColorWallpaper) currentWallpaper).slug) || currentWallpaper instanceof TLRPC.TL_wallPaper) {
                    ActionBarMenu menu2 = actionBar2.createMenu();
                    menu2.addItem(5, R.drawable.msg_share_filled);
                }
            } else if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                ActionBarMenu menu2 = actionBar2.createMenu();
                saveItem = menu2.addItem(4, LocaleController.getString("Save", R.string.Save).toUpperCase());

                dropDownContainer = new ActionBarMenuItem(context, menu2, 0, 0) {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(info);
                        info.setText(dropDown.getText());
                    }
                };
                dropDownContainer.setSubMenuOpenSide(1);
                dropDownContainer.addSubItem(2, LocaleController.getString("ColorPickerBackground", R.string.ColorPickerBackground));
                dropDownContainer.addSubItem(1, LocaleController.getString("ColorPickerMainColor", R.string.ColorPickerMainColor));
                dropDownContainer.addSubItem(3, LocaleController.getString("ColorPickerMyMessages", R.string.ColorPickerMyMessages));
                dropDownContainer.setAllowCloseAnimation(false);
                dropDownContainer.setForceSmoothKeyboard(true);
                actionBar2.addView(dropDownContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
                dropDownContainer.setOnClickListener(view -> dropDownContainer.toggleSubMenu());

                dropDown = new TextView(context);
                dropDown.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                dropDown.setGravity(Gravity.LEFT);
                dropDown.setSingleLine(true);
                dropDown.setLines(1);
                dropDown.setMaxLines(1);
                dropDown.setEllipsize(TextUtils.TruncateAt.END);
                dropDown.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
                dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                dropDown.setText(LocaleController.getString("ColorPickerMainColor", R.string.ColorPickerMainColor));
                Drawable dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
                dropDownDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultTitle), PorterDuff.Mode.MULTIPLY));
                dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null);
                dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
                dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 1));
            } else {
                String name = applyingTheme.info != null ? applyingTheme.info.title : applyingTheme.getName();
                int index = name.lastIndexOf(".attheme");
                if (index >= 0) {
                    name = name.substring(0, index);
                }
                actionBar2.setTitle(name);
                if (applyingTheme.info != null && applyingTheme.info.installs_count > 0) {
                    actionBar2.setSubtitle(LocaleController.formatPluralString("ThemeInstallCount", applyingTheme.info.installs_count));
                } else {
                    actionBar2.setSubtitle(LocaleController.formatDateOnline(System.currentTimeMillis() / 1000 - 60 * 60));
                }
            }
        }

        listView2 = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) child;
                    MessageObject message = chatMessageCell.getMessageObject();
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
                        int top = child.getTop();
                        if (chatMessageCell.isPinnedBottom()) {
                            ViewHolder holder = listView2.getChildViewHolder(child);
                            if (holder != null) {
                                int p = holder.getAdapterPosition();
                                int nextPosition;
                                nextPosition = p - 1;
                                holder = listView2.findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    imageReceiver.setImageY(-AndroidUtilities.dp(1000));
                                    imageReceiver.draw(canvas);
                                    return result;
                                }
                            }
                        }
                        float tx = chatMessageCell.getTranslationX();
                        int y = child.getTop() + chatMessageCell.getLayoutHeight();
                        int maxY = listView2.getMeasuredHeight() - listView2.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }
                        if (chatMessageCell.isPinnedTop()) {
                            ViewHolder holder = listView2.getChildViewHolder(child);
                            if (holder != null) {
                                int tries = 0;
                                while (true) {
                                    if (tries >= 20) {
                                        break;
                                    }
                                    tries++;
                                    int p = holder.getAdapterPosition();
                                    int prevPosition = p + 1;
                                    holder = listView2.findViewHolderForAdapterPosition(prevPosition);
                                    if (holder != null) {
                                        top = holder.itemView.getTop();
                                        if (y - AndroidUtilities.dp(48) < holder.itemView.getBottom()) {
                                            tx = Math.min(holder.itemView.getTranslationX(), tx);
                                        }
                                        if (holder.itemView instanceof ChatMessageCell) {
                                            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                                            if (!cell.isPinnedTop()) {
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                        if (y - AndroidUtilities.dp(48) < top) {
                            y = top + AndroidUtilities.dp(48);
                        }
                        if (tx != 0) {
                            canvas.save();
                            canvas.translate(tx, 0);
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(44));
                        imageReceiver.draw(canvas);
                        if (tx != 0) {
                            canvas.restore();
                        }
                    }
                }
                return result;
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (backgroundCheckBoxView != null) {
                    for (int a = 0; a < backgroundCheckBoxView.length; a++) {
                        backgroundCheckBoxView[a].invalidate();
                    }
                }
                if (messagesCheckBoxView != null) {
                    for (int a = 0; a < messagesCheckBoxView.length; a++) {
                        messagesCheckBoxView[a].invalidate();
                    }
                }
                if (backgroundPlayAnimationView != null) {
                    backgroundPlayAnimationView.invalidate();
                }
                if (messagesPlayAnimationView != null) {
                    messagesPlayAnimationView.invalidate();
                }
            }

            @Override
            protected void onChildPressed(View child, float x, float y, boolean pressed) {
                if (pressed && child instanceof ChatMessageCell) {
                    ChatMessageCell messageCell = (ChatMessageCell) child;
                    if (!messageCell.isInsideBackground(x, y)) {
                        return;
                    }
                }
                super.onChildPressed(child, x, y, pressed);
            }

            @Override
            protected boolean allowSelectChildAtPosition(View child) {
                RecyclerView.ViewHolder holder = listView2.findContainingViewHolder(child);
                if (holder != null && holder.getItemViewType() == 2) {
                    return false;
                }
                return super.allowSelectChildAtPosition(child);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                checkMotionEvent(e);
                return super.onTouchEvent(e);
            }

            private void checkMotionEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (!wasScroll && currentWallpaper instanceof WallpapersListActivity.ColorWallpaper && patternLayout[0].getVisibility() == View.VISIBLE) {
                        showPatternsView(0, false, true);
                    }
                    wasScroll = false;
                }
            }
        };
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                listView2.invalidateViews();
            }
        };
        itemAnimator.setDelayAnimations(false);
        listView2.setItemAnimator(itemAnimator);
        listView2.setVerticalScrollBarEnabled(true);
        listView2.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            listView2.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4 + 48));
        } else if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
            listView2.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(16));
        } else {
            listView2.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        }
        listView2.setClipToPadding(false);
        listView2.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
        listView2.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
            page2.addView(listView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 273));
            listView2.setOnItemClickListener((view, position, x, y) -> {
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    if (cell.isInsideBackground(x, y)) {
                        if (cell.getMessageObject().isOutOwner()) {
                            selectColorType(3);
                        } else {
                            selectColorType(1);
                        }
                    } else {
                        selectColorType(2);
                    }
                }
            });
        } else {
            page2.addView(listView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        }
        listView2.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                listView2.invalidateViews();
                wasScroll = true;
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    wasScroll = false;
                }
            }
        });

        page2.addView(actionBar2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        parallaxEffect = new WallpaperParallaxEffect(context);
        parallaxEffect.setCallback((offsetX, offsetY, angle) -> {
            if (!isMotion) {
                return;
            }
            Drawable background = backgroundImage.getBackground();
            float progress;
            if (motionAnimation != null) {
                progress = (backgroundImage.getScaleX() - 1.0f) / (parallaxScale - 1.0f);
            } else {
                progress = 1.0f;
            }
            backgroundImage.setTranslationX(offsetX * progress);
            backgroundImage.setTranslationY(offsetY * progress);
        });

        if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            radialProgress = new RadialProgress2(backgroundImage);
            radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);

            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                bottomOverlayChat = new FrameLayout(context) {
                    @Override
                    public void onDraw(Canvas canvas) {
                        int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                        Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                        Theme.chat_composeShadowDrawable.draw(canvas);
                        canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
                    }
                };
                bottomOverlayChat.setWillNotDraw(false);
                bottomOverlayChat.setPadding(0, AndroidUtilities.dp(3), 0, 0);
                page2.addView(bottomOverlayChat, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
                bottomOverlayChat.setOnClickListener(view -> {
                    boolean done;
                    boolean sameFile = false;
                    Theme.ThemeInfo theme = Theme.getActiveTheme();
                    String originalFileName = theme.generateWallpaperName(null, isBlurred);
                    String fileName = isBlurred ? theme.generateWallpaperName(null, false) : originalFileName;
                    File toFile = new File(ApplicationLoader.getFilesDirFixed(), originalFileName);
                    if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                        if (originalBitmap != null) {
                            try {
                                FileOutputStream stream = new FileOutputStream(toFile);
                                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                                stream.close();
                                done = true;
                            } catch (Exception e) {
                                done = false;
                                FileLog.e(e);
                            }
                        } else {
                            ImageReceiver imageReceiver = backgroundImage.getImageReceiver();
                            if (imageReceiver.hasNotThumb() || imageReceiver.hasStaticThumb()) {
                                Bitmap bitmap = imageReceiver.getBitmap();
                                try {
                                    FileOutputStream stream = new FileOutputStream(toFile);
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                                    stream.close();
                                    done = true;
                                } catch (Exception e) {
                                    done = false;
                                    FileLog.e(e);
                                }
                            } else {
                                done = false;
                            }
                        }

                        if (!done) {
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                            File f = FileLoader.getPathToAttach(wallPaper.document, true);
                            try {
                                done = AndroidUtilities.copyFile(f, toFile);
                            } catch (Exception e) {
                                done = false;
                                FileLog.e(e);
                            }
                        }
                    } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        if (selectedPattern != null) {
                            try {
                                WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                                Bitmap bitmap = backgroundImage.getImageReceiver().getBitmap();
                                @SuppressLint("DrawAllocation")
                                Bitmap dst = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(dst);
                                if (backgroundGradientColor2 != 0) {

                                } else if (backgroundGradientColor1 != 0) {
                                    GradientDrawable gradientDrawable = new GradientDrawable(BackgroundGradientDrawable.getGradientOrientation(backgroundRotation), new int[]{backgroundColor, backgroundGradientColor1});
                                    gradientDrawable.setBounds(0, 0, dst.getWidth(), dst.getHeight());
                                    gradientDrawable.draw(canvas);
                                } else {
                                    canvas.drawColor(backgroundColor);
                                }
                                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                                paint.setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
                                paint.setAlpha((int) (255 * Math.abs(currentIntensity)));
                                canvas.drawBitmap(bitmap, 0, 0, paint);

                                FileOutputStream stream = new FileOutputStream(toFile);
                                if (backgroundGradientColor2 != 0) {
                                    dst.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                } else {
                                    dst.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                                }
                                stream.close();
                                done = true;
                            } catch (Throwable e) {
                                FileLog.e(e);
                                done = false;
                            }
                        } else {
                            done = true;
                        }
                    } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                        WallpapersListActivity.FileWallpaper wallpaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                        if (wallpaper.resId != 0 || Theme.THEME_BACKGROUND_SLUG.equals(wallpaper.slug)) {
                            done = true;
                        } else {
                            try {
                                File fromFile = wallpaper.originalPath != null ? wallpaper.originalPath : wallpaper.path;
                                if (sameFile = fromFile.equals(toFile)) {
                                    done = true;
                                } else {
                                    done = AndroidUtilities.copyFile(fromFile, toFile);
                                }
                            } catch (Exception e) {
                                done = false;
                                FileLog.e(e);
                            }
                        }
                    } else if (currentWallpaper instanceof MediaController.SearchImage) {
                        MediaController.SearchImage wallpaper = (MediaController.SearchImage) currentWallpaper;
                        File f;
                        if (wallpaper.photo != null) {
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallpaper.photo.sizes, maxWallpaperSize, true);
                            f = FileLoader.getPathToAttach(image, true);
                        } else {
                            f = ImageLoader.getHttpFilePath(wallpaper.imageUrl, "jpg");
                        }
                        try {
                            done = AndroidUtilities.copyFile(f, toFile);
                        } catch (Exception e) {
                            done = false;
                            FileLog.e(e);
                        }
                    } else {
                        done = false;
                    }
                    if (isBlurred) {
                        try {
                            File blurredFile = new File(ApplicationLoader.getFilesDirFixed(), fileName);
                            FileOutputStream stream = new FileOutputStream(blurredFile);
                            blurredBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                            stream.close();
                            done = true;
                        } catch (Throwable e) {
                            FileLog.e(e);
                            done = false;
                        }
                    }
                    String slug;
                    int rotation = 45;
                    int color = 0;
                    int gradientColor1 = 0;
                    int gradientColor2 = 0;
                    int gradientColor3 = 0;
                    File path = null;

                    if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                        slug = wallPaper.slug;
                    } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                        if (Theme.DEFAULT_BACKGROUND_SLUG.equals(wallPaper.slug)) {
                            slug = Theme.DEFAULT_BACKGROUND_SLUG;
                            color = 0;
                        } else {
                            if (selectedPattern != null) {
                                slug = selectedPattern.slug;
                            } else {
                                slug = Theme.COLOR_BACKGROUND_SLUG;
                            }
                            color = backgroundColor;
                            gradientColor1 = backgroundGradientColor1;
                            gradientColor2 = backgroundGradientColor2;
                            gradientColor3 = backgroundGradientColor3;
                            rotation = backgroundRotation;
                        }
                    } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                        WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                        slug = wallPaper.slug;
                        path = wallPaper.path;
                    } else if (currentWallpaper instanceof MediaController.SearchImage) {
                        MediaController.SearchImage wallPaper = (MediaController.SearchImage) currentWallpaper;
                        if (wallPaper.photo != null) {
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                            path = FileLoader.getPathToAttach(image, true);
                        } else {
                            path = ImageLoader.getHttpFilePath(wallPaper.imageUrl, "jpg");
                        }
                        slug = "";
                    } else {
                        color = 0;
                        slug = Theme.DEFAULT_BACKGROUND_SLUG;
                    }

                    Theme.OverrideWallpaperInfo wallpaperInfo = new Theme.OverrideWallpaperInfo();
                    wallpaperInfo.fileName = fileName;
                    wallpaperInfo.originalFileName = originalFileName;
                    wallpaperInfo.slug = slug;
                    wallpaperInfo.isBlurred = isBlurred;
                    wallpaperInfo.isMotion = isMotion;
                    wallpaperInfo.color = color;
                    wallpaperInfo.gradientColor1 = gradientColor1;
                    wallpaperInfo.gradientColor2 = gradientColor2;
                    wallpaperInfo.gradientColor3 = gradientColor3;
                    wallpaperInfo.rotation = rotation;
                    wallpaperInfo.intensity = currentIntensity;
                    if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        WallpapersListActivity.ColorWallpaper colorWallpaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                        String slugStr;
                        if (!Theme.COLOR_BACKGROUND_SLUG.equals(slug) && !Theme.THEME_BACKGROUND_SLUG.equals(slug) && !Theme.DEFAULT_BACKGROUND_SLUG.equals(slug)) {
                            slugStr = slug;
                        } else {
                            slugStr = null;
                        }
                        float intensity = colorWallpaper.intensity;
                        if (intensity < 0 && !Theme.getActiveTheme().isDark()) {
                            intensity *= -1;
                        }
                        if (colorWallpaper.parentWallpaper != null && colorWallpaper.color == color &&
                                colorWallpaper.gradientColor1 == gradientColor1 && colorWallpaper.gradientColor2 == gradientColor2 && colorWallpaper.gradientColor3 == gradientColor3 && TextUtils.equals(colorWallpaper.slug, slugStr) &&
                                colorWallpaper.gradientRotation == rotation && (selectedPattern == null || Math.abs(intensity - currentIntensity) < 0.001f)) {
                            wallpaperInfo.wallpaperId = colorWallpaper.parentWallpaper.id;
                            wallpaperInfo.accessHash = colorWallpaper.parentWallpaper.access_hash;
                        }
                    }
                    MessagesController.getInstance(currentAccount).saveWallpaperToServer(path, wallpaperInfo, slug != null, 0);

                    if (done) {
                        Theme.serviceMessageColorBackup = Theme.getColor(Theme.key_chat_serviceBackground);
                        if (Theme.THEME_BACKGROUND_SLUG.equals(wallpaperInfo.slug)) {
                            wallpaperInfo = null;
                        }
                        Theme.getActiveTheme().setOverrideWallpaper(wallpaperInfo);
                        Theme.reloadWallpaper();
                        if (!sameFile) {
                            ImageLoader.getInstance().removeImage(ImageLoader.getHttpFileName(toFile.getAbsolutePath()) + "@100_100");
                        }
                    }
                    if (delegate != null) {
                        delegate.didSetNewBackground();
                    }
                    finishFragment();
                });

                bottomOverlayChatText = new TextView(context);
                bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                bottomOverlayChatText.setText(LocaleController.getString("SetBackground", R.string.SetBackground));
                bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            }

            Rect paddings = new Rect();
            sheetDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
            sheetDrawable.getPadding(paddings);
            sheetDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.MULTIPLY));

            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(14));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            {
                int textsCount;
                if (screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                    textsCount = 3;
                    if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper && Theme.DEFAULT_BACKGROUND_SLUG.equals(((WallpapersListActivity.ColorWallpaper) currentWallpaper).slug)) {
                        textsCount = 0;
                    }
                } else {
                    textsCount = 2;
                    if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                        WallpapersListActivity.FileWallpaper fileWallpaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                        if (Theme.THEME_BACKGROUND_SLUG.equals(fileWallpaper.slug)) {
                            textsCount = 0;
                        }
                    }
                }

                String[] texts = new String[textsCount];
                int[] textSizes = new int[textsCount];
                backgroundCheckBoxView = new WallpaperCheckBoxView[textsCount];
                int maxTextSize = 0;
                if (textsCount != 0) {
                    backgroundButtonsContainer = new FrameLayout(context);
                    if (screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        texts[0] = LocaleController.getString("BackgroundColors", R.string.BackgroundColors);
                        texts[1] = LocaleController.getString("BackgroundPattern", R.string.BackgroundPattern);
                        texts[2] = LocaleController.getString("BackgroundMotion", R.string.BackgroundMotion);
                    } else {
                        texts[0] = LocaleController.getString("BackgroundBlurred", R.string.BackgroundBlurred);
                        texts[1] = LocaleController.getString("BackgroundMotion", R.string.BackgroundMotion);
                    }
                    for (int a = 0; a < texts.length; a++) {
                        textSizes[a] = (int) Math.ceil(textPaint.measureText(texts[a]));
                        maxTextSize = Math.max(maxTextSize, textSizes[a]);
                    }

                    backgroundPlayAnimationView = new FrameLayout(context) {

                        private RectF rect = new RectF();

                        @Override
                        protected void onDraw(Canvas canvas) {
                            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                            Theme.applyServiceShaderMatrixForView(backgroundPlayAnimationView, backgroundImage);
                            canvas.drawRoundRect(rect, getMeasuredHeight() / 2, getMeasuredHeight() / 2, Theme.chat_actionBackgroundPaint);
                            if (Theme.hasGradientService()) {
                                canvas.drawRoundRect(rect, getMeasuredHeight() / 2, getMeasuredHeight() / 2, Theme.chat_actionBackgroundGradientDarkenPaint);
                            }
                        }
                    };
                    backgroundPlayAnimationView.setWillNotDraw(false);
                    backgroundPlayAnimationView.setVisibility(backgroundGradientColor1 != 0 ? View.VISIBLE : View.INVISIBLE);
                    backgroundPlayAnimationView.setScaleX(backgroundGradientColor1 != 0 ? 1.0f : 0.1f);
                    backgroundPlayAnimationView.setScaleY(backgroundGradientColor1 != 0 ? 1.0f : 0.1f);
                    backgroundPlayAnimationView.setAlpha(backgroundGradientColor1 != 0 ? 1.0f : 0.0f);
                    backgroundPlayAnimationView.setTag(backgroundGradientColor1 != 0 ? 1 : null);
                    backgroundButtonsContainer.addView(backgroundPlayAnimationView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
                    backgroundPlayAnimationView.setOnClickListener(new View.OnClickListener() {

                        int rotation = 0;

                        @Override
                        public void onClick(View v) {
                            Drawable background = backgroundImage.getBackground();
                            backgroundPlayAnimationImageView.setRotation(rotation);
                            rotation -= 45;
                            backgroundPlayAnimationImageView.animate().rotationBy(-45).setDuration(300).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                            if (background instanceof MotionBackgroundDrawable) {
                                MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) background;
                                motionBackgroundDrawable.switchToNextPosition();
                            } else {
                                onColorsRotate();
                            }
                        }
                    });

                    backgroundPlayAnimationImageView = new ImageView(context);
                    backgroundPlayAnimationImageView.setScaleType(ImageView.ScaleType.CENTER);
                    backgroundPlayAnimationImageView.setImageResource(R.drawable.bg_rotate_large);
                    backgroundPlayAnimationView.addView(backgroundPlayAnimationImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                }

                for (int a = 0; a < textsCount; a++) {
                    final int num = a;
                    backgroundCheckBoxView[a] = new WallpaperCheckBoxView(context, screenType != SCREEN_TYPE_ACCENT_COLOR && !(currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) || a != 0, backgroundImage);
                    backgroundCheckBoxView[a].setBackgroundColor(backgroundColor);
                    backgroundCheckBoxView[a].setText(texts[a], textSizes[a], maxTextSize);

                    if (screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        if (a == 1) {
                            backgroundCheckBoxView[a].setChecked(selectedPattern != null || accent != null && !TextUtils.isEmpty(accent.patternSlug), false);
                        } else if (a == 2) {
                            backgroundCheckBoxView[a].setChecked(isMotion, false);
                        }
                    } else {
                        backgroundCheckBoxView[a].setChecked(a == 0 ? isBlurred : isMotion, false);
                    }
                    int width = maxTextSize + AndroidUtilities.dp(14 * 2 + 28);
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                    layoutParams.gravity = Gravity.CENTER;
                    if (textsCount == 3) {
                        if (a == 0 || a == 2) {
                            layoutParams.leftMargin = width / 2 + AndroidUtilities.dp(10);
                        } else {
                            layoutParams.rightMargin = width / 2 + AndroidUtilities.dp(10);
                        }
                    } else {
                        if (a == 1) {
                            layoutParams.leftMargin = width / 2 + AndroidUtilities.dp(10);
                        } else {
                            layoutParams.rightMargin = width / 2 + AndroidUtilities.dp(10);
                        }
                    }
                    backgroundButtonsContainer.addView(backgroundCheckBoxView[a], layoutParams);
                    WallpaperCheckBoxView view = backgroundCheckBoxView[a];
                    backgroundCheckBoxView[a].setOnClickListener(v -> {
                        if (backgroundButtonsContainer.getAlpha() != 1.0f || patternViewAnimation != null) {
                            return;
                        }
                        if ((screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) && num == 2) {
                            view.setChecked(!view.isChecked(), true);
                            isMotion = view.isChecked();
                            parallaxEffect.setEnabled(isMotion);
                            animateMotionChange();
                        } else if (num == 1 && (screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper)) {
                            if (backgroundCheckBoxView[1].isChecked()) {
                                lastSelectedPattern = selectedPattern;
                                backgroundImage.setImageDrawable(null);
                                selectedPattern = null;
                                isMotion = false;
                                updateButtonState(false, true);
                                animateMotionChange();
                                if (patternLayout[1].getVisibility() == View.VISIBLE) {
                                    if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                                        showPatternsView(0, true, true);
                                    } else {
                                        showPatternsView(num, patternLayout[num].getVisibility() != View.VISIBLE, true);
                                    }
                                }
                            } else {
                                selectPattern(lastSelectedPattern != null ? -1 : 0);
                                if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                                    showPatternsView(1, true, true);
                                } else {
                                    showPatternsView(num, patternLayout[num].getVisibility() != View.VISIBLE, true);
                                }
                            }
                            backgroundCheckBoxView[1].setChecked(selectedPattern != null, true);
                            updateSelectedPattern(true);
                            patternsListView.invalidateViews();
                            updateMotionButton();
                        } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                            showPatternsView(num, patternLayout[num].getVisibility() != View.VISIBLE, true);
                        } else if (screenType != SCREEN_TYPE_ACCENT_COLOR) {
                            view.setChecked(!view.isChecked(), true);
                            if (num == 0) {
                                isBlurred = view.isChecked();
                                if (isBlurred) {
                                    backgroundImage.getImageReceiver().setForceCrossfade(true);
                                }
                                updateBlurred();
                            } else {
                                isMotion = view.isChecked();
                                parallaxEffect.setEnabled(isMotion);
                                animateMotionChange();
                            }
                        }
                    });
                    if (a == 2) {
                        backgroundCheckBoxView[a].setAlpha(0.0f);
                        backgroundCheckBoxView[a].setVisibility(View.INVISIBLE);
                    }
                }
            }

            if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                String[] texts = new String[2];
                int[] textSizes = new int[2];
                messagesCheckBoxView = new WallpaperCheckBoxView[2];
                int maxTextSize = 0;

                messagesButtonsContainer = new FrameLayout(context);

                texts[0] = LocaleController.getString("BackgroundAnimate", R.string.BackgroundAnimate);
                texts[1] = LocaleController.getString("BackgroundColors", R.string.BackgroundColors);

                for (int a = 0; a < texts.length; a++) {
                    textSizes[a] = (int) Math.ceil(textPaint.measureText(texts[a]));
                    maxTextSize = Math.max(maxTextSize, textSizes[a]);
                }

                messagesPlayAnimationView = new FrameLayout(context) {

                    private RectF rect = new RectF();

                    @Override
                    protected void onDraw(Canvas canvas) {
                        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        Theme.applyServiceShaderMatrixForView(messagesPlayAnimationView, backgroundImage);
                        canvas.drawRoundRect(rect, getMeasuredHeight() / 2, getMeasuredHeight() / 2, Theme.chat_actionBackgroundPaint);
                        if (Theme.hasGradientService()) {
                            canvas.drawRoundRect(rect, getMeasuredHeight() / 2, getMeasuredHeight() / 2, Theme.chat_actionBackgroundGradientDarkenPaint);
                        }
                    }
                };
                messagesPlayAnimationView.setWillNotDraw(false);
                messagesPlayAnimationView.setVisibility(accent.myMessagesGradientAccentColor1 != 0 ? View.VISIBLE : View.INVISIBLE);
                messagesPlayAnimationView.setScaleX(accent.myMessagesGradientAccentColor1 != 0 ? 1.0f : 0.1f);
                messagesPlayAnimationView.setScaleY(accent.myMessagesGradientAccentColor1 != 0 ? 1.0f : 0.1f);
                messagesPlayAnimationView.setAlpha(accent.myMessagesGradientAccentColor1 != 0 ? 1.0f : 0.0f);
                messagesButtonsContainer.addView(messagesPlayAnimationView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
                messagesPlayAnimationView.setOnClickListener(new View.OnClickListener() {

                    int rotation = 0;

                    @Override
                    public void onClick(View v) {
                        messagesPlayAnimationImageView.setRotation(rotation);
                        rotation -= 45;
                        messagesPlayAnimationImageView.animate().rotationBy(-45).setDuration(300).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                        if (accent.myMessagesAnimated) {
                            if (msgOutDrawable.getMotionBackgroundDrawable() != null) {
                                msgOutDrawable.getMotionBackgroundDrawable().switchToNextPosition();
                            }
                        } else {
                            int temp;
                            if (accent.myMessagesGradientAccentColor3 != 0) {
                                temp = accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accent.accentColor;
                                accent.myMessagesAccentColor = accent.myMessagesGradientAccentColor1;
                                accent.myMessagesGradientAccentColor1 = accent.myMessagesGradientAccentColor2;
                                accent.myMessagesGradientAccentColor2 = accent.myMessagesGradientAccentColor3;
                                accent.myMessagesGradientAccentColor3 = temp;
                            } else {
                                temp = accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accent.accentColor;
                                accent.myMessagesAccentColor = accent.myMessagesGradientAccentColor1;
                                accent.myMessagesGradientAccentColor1 = accent.myMessagesGradientAccentColor2;
                                accent.myMessagesGradientAccentColor2 = temp;
                            }
                            colorPicker.setColor(accent.myMessagesGradientAccentColor3, 3);
                            colorPicker.setColor(accent.myMessagesGradientAccentColor2, 2);
                            colorPicker.setColor(accent.myMessagesGradientAccentColor1, 1);
                            colorPicker.setColor(accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accent.accentColor, 0);
                            messagesCheckBoxView[1].setColor(0, accent.myMessagesAccentColor);
                            messagesCheckBoxView[1].setColor(1, accent.myMessagesGradientAccentColor1);
                            messagesCheckBoxView[1].setColor(2, accent.myMessagesGradientAccentColor2);
                            messagesCheckBoxView[1].setColor(3, accent.myMessagesGradientAccentColor3);
                            Theme.refreshThemeColors(true, true);
                            listView2.invalidateViews();
                        }
                    }
                });

                messagesPlayAnimationImageView = new ImageView(context);
                messagesPlayAnimationImageView.setScaleType(ImageView.ScaleType.CENTER);
                messagesPlayAnimationImageView.setImageResource(R.drawable.bg_rotate_large);
                messagesPlayAnimationView.addView(messagesPlayAnimationImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                for (int a = 0; a < 2; a++) {
                    final int num = a;
                    messagesCheckBoxView[a] = new WallpaperCheckBoxView(context, a == 0, backgroundImage);
                    messagesCheckBoxView[a].setText(texts[a], textSizes[a], maxTextSize);

                    if (a == 0) {
                        messagesCheckBoxView[a].setChecked(accent.myMessagesAnimated, false);
                    }
                    int width = maxTextSize + AndroidUtilities.dp(14 * 2 + 28);
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                    layoutParams.gravity = Gravity.CENTER;
                    if (a == 1) {
                        layoutParams.leftMargin = width / 2 + AndroidUtilities.dp(10);
                    } else {
                        layoutParams.rightMargin = width / 2 + AndroidUtilities.dp(10);
                    }
                    messagesButtonsContainer.addView(messagesCheckBoxView[a], layoutParams);
                    WallpaperCheckBoxView view = messagesCheckBoxView[a];
                    messagesCheckBoxView[a].setOnClickListener(v -> {
                        if (messagesButtonsContainer.getAlpha() != 1.0f) {
                            return;
                        }
                        if (num == 0) {
                            view.setChecked(!view.isChecked(), true);
                            accent.myMessagesAnimated = view.isChecked();
                            Theme.refreshThemeColors(true, true);
                            listView2.invalidateViews();
                        }
                    });
                }
            }

            if (screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                isBlurred = false;

                for (int a = 0; a < 2; a++) {
                    final int num = a;

                    patternLayout[a] = new FrameLayout(context) {
                        @Override
                        public void onDraw(Canvas canvas) {
                            if (num == 0) {
                                sheetDrawable.setBounds(colorPicker.getLeft() - paddings.left, 0, colorPicker.getRight() + paddings.right, getMeasuredHeight());
                            } else {
                                sheetDrawable.setBounds(-paddings.left, 0, getMeasuredWidth() + paddings.right, getMeasuredHeight());
                            }
                            sheetDrawable.draw(canvas);
                        }
                    };
                    if (a == 1 || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        patternLayout[a].setVisibility(View.INVISIBLE);
                    }
                    patternLayout[a].setWillNotDraw(false);
                    FrameLayout.LayoutParams layoutParams;
                    if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, a == 0 ? 321 : 316, Gravity.LEFT | Gravity.BOTTOM);
                    } else {
                        layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, a == 0 ? 273 : 316, Gravity.LEFT | Gravity.BOTTOM);
                    }
                    if (a == 0) {
                        layoutParams.height += AndroidUtilities.dp(12) + paddings.top;
                        patternLayout[a].setPadding(0, AndroidUtilities.dp(12) + paddings.top, 0, 0);
                    }
                    page2.addView(patternLayout[a], layoutParams);

                    if (a == 1 || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        patternsButtonsContainer[a] = new FrameLayout(context) {
                            @Override
                            public void onDraw(Canvas canvas) {
                                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                                Theme.chat_composeShadowDrawable.draw(canvas);
                                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
                            }
                        };
                        patternsButtonsContainer[a].setWillNotDraw(false);
                        patternsButtonsContainer[a].setPadding(0, AndroidUtilities.dp(3), 0, 0);
                        patternsButtonsContainer[a].setClickable(true);
                        patternLayout[a].addView(patternsButtonsContainer[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

                        patternsCancelButton[a] = new TextView(context);
                        patternsCancelButton[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                        patternsCancelButton[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                        patternsCancelButton[a].setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                        patternsCancelButton[a].setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
                        patternsCancelButton[a].setGravity(Gravity.CENTER);
                        patternsCancelButton[a].setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
                        patternsCancelButton[a].setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
                        patternsButtonsContainer[a].addView(patternsCancelButton[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
                        patternsCancelButton[a].setOnClickListener(v -> {
                            if (patternViewAnimation != null) {
                                return;
                            }
                            if (num == 0) {
                                backgroundRotation = previousBackgroundRotation;
                                setBackgroundColor(previousBackgroundGradientColor3, 3, true, true);
                                setBackgroundColor(previousBackgroundGradientColor2, 2, true, true);
                                setBackgroundColor(previousBackgroundGradientColor1, 1, true, true);
                                setBackgroundColor(previousBackgroundColor, 0, true, true);
                            } else {
                                selectedPattern = previousSelectedPattern;
                                if (selectedPattern == null) {
                                    backgroundImage.setImageDrawable(null);
                                } else {
                                    backgroundImage.setImage(ImageLocation.getForDocument(selectedPattern.document), imageFilter, null, null, "jpg", selectedPattern.document.size, 1, selectedPattern);
                                }
                                backgroundCheckBoxView[1].setChecked(selectedPattern != null, false);

                                currentIntensity = previousIntensity;
                                intensitySeekBar.setProgress(currentIntensity);
                                backgroundImage.getImageReceiver().setAlpha(currentIntensity);
                                updateButtonState(false, true);
                                updateSelectedPattern(true);
                            }
                            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                                showPatternsView(num, false, true);
                            } else {
                                if (selectedPattern == null) {
                                    if (isMotion) {
                                        isMotion = false;
                                        backgroundCheckBoxView[0].setChecked(false, true);
                                        animateMotionChange();
                                    }
                                    updateMotionButton();
                                }
                                showPatternsView(0, true, true);
                            }
                        });

                        patternsSaveButton[a] = new TextView(context);
                        patternsSaveButton[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                        patternsSaveButton[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                        patternsSaveButton[a].setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                        patternsSaveButton[a].setText(LocaleController.getString("ApplyTheme", R.string.ApplyTheme).toUpperCase());
                        patternsSaveButton[a].setGravity(Gravity.CENTER);
                        patternsSaveButton[a].setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
                        patternsSaveButton[a].setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
                        patternsButtonsContainer[a].addView(patternsSaveButton[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
                        patternsSaveButton[a].setOnClickListener(v -> {
                            if (patternViewAnimation != null) {
                                return;
                            }
                            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                                showPatternsView(num, false, true);
                            } else {
                                showPatternsView(0, true, true);
                            }
                        });
                    }

                    if (a == 1) {
                        TextView titleView = new TextView(context);
                        titleView.setLines(1);
                        titleView.setSingleLine(true);
                        titleView.setText(LocaleController.getString("BackgroundChoosePattern", R.string.BackgroundChoosePattern));

                        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                        titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));

                        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                        titleView.setGravity(Gravity.CENTER_VERTICAL);
                        patternLayout[a].addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, 0, 21, 0, 0));

                        patternsListView = new RecyclerListView(context) {
                            @Override
                            public boolean onTouchEvent(MotionEvent event) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                return super.onTouchEvent(event);
                            }
                        };
                        patternsListView.setLayoutManager(patternsLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                        patternsListView.setAdapter(patternsAdapter = new PatternsAdapter(context));
                        patternsListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                            @Override
                            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                                int position = parent.getChildAdapterPosition(view);
                                outRect.left = AndroidUtilities.dp(12);
                                outRect.bottom = outRect.top = 0;
                                if (position == state.getItemCount() - 1) {
                                    outRect.right = AndroidUtilities.dp(12);
                                }
                            }
                        });
                        patternLayout[a].addView(patternsListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.LEFT | Gravity.TOP, 0, 76, 0, 0));
                        patternsListView.setOnItemClickListener((view, position) -> {
                            boolean previousMotion = selectedPattern != null;
                            selectPattern(position);
                            if (previousMotion == (selectedPattern == null)) {
                                animateMotionChange();
                                updateMotionButton();
                            }
                            updateSelectedPattern(true);
                            backgroundCheckBoxView[1].setChecked(selectedPattern != null, true);
                            patternsListView.invalidateViews();

                            int left = view.getLeft();
                            int right = view.getRight();
                            int extra = AndroidUtilities.dp(52);
                            if (left - extra < 0) {
                                patternsListView.smoothScrollBy(left - extra, 0);
                            } else if (right + extra > patternsListView.getMeasuredWidth()) {
                                patternsListView.smoothScrollBy(right + extra - patternsListView.getMeasuredWidth(), 0);
                            }
                        });

                        intensityCell = new HeaderCell(context);
                        intensityCell.setText(LocaleController.getString("BackgroundIntensity", R.string.BackgroundIntensity));
                        patternLayout[a].addView(intensityCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 175, 0, 0));

                        intensitySeekBar = new SeekBarView(context) {
                            @Override
                            public boolean onTouchEvent(MotionEvent event) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                return super.onTouchEvent(event);
                            }
                        };
                        intensitySeekBar.setProgress(currentIntensity);
                        intensitySeekBar.setReportChanges(true);
                        intensitySeekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                            @Override
                            public void onSeekBarDrag(boolean stop, float progress) {
                                currentIntensity = progress;
                                backgroundImage.getImageReceiver().setAlpha(Math.abs(currentIntensity));
                                backgroundImage.invalidate();
                                patternsListView.invalidateViews();
                                if (currentIntensity >= 0) {
                                    if (Build.VERSION.SDK_INT >= 29 && backgroundImage.getBackground() instanceof MotionBackgroundDrawable) {
                                        backgroundImage.getImageReceiver().setBlendMode(BlendMode.SOFT_LIGHT);
                                    }
                                    backgroundImage.getImageReceiver().setGradientBitmap(null);
                                } else {
                                    if (Build.VERSION.SDK_INT >= 29) {
                                        backgroundImage.getImageReceiver().setBlendMode(null);
                                    }
                                    if (backgroundImage.getBackground() instanceof MotionBackgroundDrawable) {
                                        MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundImage.getBackground();
                                        backgroundImage.getImageReceiver().setGradientBitmap(motionBackgroundDrawable.getBitmap());
                                    }
                                }
                            }

                            @Override
                            public void onSeekBarPressed(boolean pressed) {

                            }
                        });
                        patternLayout[a].addView(intensitySeekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 5, 211, 5, 0));
                    } else {
                        colorPicker = new ColorPicker(context, editingTheme, new ColorPicker.ColorPickerDelegate() {
                            @Override
                            public void setColor(int color, int num, boolean applyNow) {
                                if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                                    setBackgroundColor(color, num, applyNow, true);
                                } else {
                                    scheduleApplyColor(color, num, applyNow);
                                }
                            }

                            @Override
                            public void openThemeCreate(boolean share) {
                                if (share) {
                                    if (accent.info == null) {
                                        finishFragment();
                                        MessagesController.getInstance(currentAccount).saveThemeToServer(accent.parentTheme, accent);
                                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needShareTheme, accent.parentTheme, accent);
                                    } else {
                                        String link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addtheme/" + accent.info.slug;
                                        showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                                    }
                                } else {
                                    AlertsCreator.createThemeCreateDialog(ThemePreviewActivity.this, 1, null, null);
                                }
                            }

                            @Override
                            public void deleteTheme() {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                                builder1.setTitle(LocaleController.getString("DeleteThemeTitle", R.string.DeleteThemeTitle));
                                builder1.setMessage(LocaleController.getString("DeleteThemeAlert", R.string.DeleteThemeAlert));
                                builder1.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                                    Theme.deleteThemeAccent(applyingTheme, accent, true);
                                    Theme.applyPreviousTheme();
                                    Theme.refreshThemeColors();
                                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, applyingTheme, nightTheme, null, -1);
                                    finishFragment();
                                });
                                builder1.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                AlertDialog alertDialog = builder1.create();
                                showDialog(alertDialog);
                                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                                if (button != null) {
                                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                                }
                            }

                            @Override
                            public void rotateColors() {
                                onColorsRotate();
                            }

                            @Override
                            public int getDefaultColor(int num) {
                                if (colorType == 3 && applyingTheme.firstAccentIsDefault && num == 0) {
                                    Theme.ThemeAccent accent = applyingTheme.themeAccentsMap.get(Theme.DEFALT_THEME_ACCENT_ID);
                                    return accent != null ? accent.myMessagesAccentColor : 0;
                                }
                                return 0;
                            }

                            @Override
                            public boolean hasChanges() {
                                return ThemePreviewActivity.this.hasChanges(colorType);
                            }
                        });
                        if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                            patternLayout[a].addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
                            if (applyingTheme.isDark()) {
                                colorPicker.setMinBrightness(0.2f);
                            } else {
                                colorPicker.setMinBrightness(0.05f);
                                colorPicker.setMaxBrightness(0.8f);
                            }
                            int colorsCount = accent.accentColor2 != 0 ? 2 : 1;
                            colorPicker.setType(1, hasChanges(1), 2, colorsCount, false, 0, false);
                            colorPicker.setColor(accent.accentColor, 0);
                            if (accent.accentColor2 != 0) {
                                colorPicker.setColor(accent.accentColor2, 1);
                            }
                        } else {
                            patternLayout[a].addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 48));
                        }
                    }
                }
            }

            updateButtonState(false, false);
            if (!backgroundImage.getImageReceiver().hasBitmapImage()) {
                page2.setBackgroundColor(0xff000000);
            }

            if (screenType != SCREEN_TYPE_ACCENT_COLOR && !(currentWallpaper instanceof WallpapersListActivity.ColorWallpaper)) {
                backgroundImage.getImageReceiver().setCrossfadeWithOldImage(true);
            }
        }

        listView2.setAdapter(messagesAdapter);

        frameLayout = new FrameLayout(context) {

            private int[] loc = new int[2];

            @Override
            public void invalidate() {
                super.invalidate();
                if (page2 != null) {
                    page2.invalidate();
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (!AndroidUtilities.usingHardwareInput) {
                    getLocationInWindow(loc);
                    if (Build.VERSION.SDK_INT < 21 && !AndroidUtilities.isTablet()) {
                        loc[1] -= AndroidUtilities.statusBarHeight;
                    }
                    if (actionBar2.getTranslationY() != loc[1]) {
                        actionBar2.setTranslationY(-loc[1]);
                        page2.invalidate();
                    }
                    if (SystemClock.elapsedRealtime() < watchForKeyboardEndTime) {
                        invalidate();
                    }
                }
            }
        };
        frameLayout.setWillNotDraw(false);
        fragmentView = frameLayout;
        frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener = () -> {
            watchForKeyboardEndTime = SystemClock.elapsedRealtime() + 1500;
            frameLayout.invalidate();
        });

        viewPager = new ViewPager(context);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                dotsContainer.invalidate();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        viewPager.setAdapter(new PagerAdapter() {

            @Override
            public int getCount() {
                return screenType != SCREEN_TYPE_PREVIEW ? 1 : 2;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return object == view;
            }

            @Override
            public int getItemPosition(Object object) {
                return POSITION_UNCHANGED;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View view = position == 0 ? page2 : page1;
                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }

            @Override
            public void unregisterDataSetObserver(DataSetObserver observer) {
                if (observer != null) {
                    super.unregisterDataSetObserver(observer);
                }
            }
        });
        AndroidUtilities.setViewPagerEdgeEffectColor(viewPager, Theme.getColor(Theme.key_actionBarDefault));
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, screenType == SCREEN_TYPE_PREVIEW ? 48 : 0));

        undoView = new UndoView(context, this);
        undoView.setAdditionalTranslationY(AndroidUtilities.dp(51));
        frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        if (screenType == SCREEN_TYPE_PREVIEW) {
            View shadow = new View(context);
            shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM);
            layoutParams.bottomMargin = AndroidUtilities.dp(48);
            frameLayout.addView(shadow, layoutParams);

            saveButtonsContainer = new FrameLayout(context);
            saveButtonsContainer.setBackgroundColor(getButtonsColor(Theme.key_windowBackgroundWhite));
            frameLayout.addView(saveButtonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

            dotsContainer = new View(context) {

                private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas canvas) {
                    int selected = viewPager.getCurrentItem();
                    paint.setColor(getButtonsColor(Theme.key_chat_fieldOverlayText));
                    for (int a = 0; a < 2; a++) {
                        paint.setAlpha(a == selected ? 255 : 127);
                        canvas.drawCircle(AndroidUtilities.dp(3 + 15 * a), AndroidUtilities.dp(4), AndroidUtilities.dp(3), paint);
                    }
                }
            };
            saveButtonsContainer.addView(dotsContainer, LayoutHelper.createFrame(22, 8, Gravity.CENTER));

            cancelButton = new TextView(context);
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setTextColor(getButtonsColor(Theme.key_chat_fieldOverlayText));
            cancelButton.setGravity(Gravity.CENTER);
            cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x0f000000, 0));
            cancelButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            saveButtonsContainer.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            cancelButton.setOnClickListener(v -> cancelThemeApply(false));

            doneButton = new TextView(context);
            doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            doneButton.setTextColor(getButtonsColor(Theme.key_chat_fieldOverlayText));
            doneButton.setGravity(Gravity.CENTER);
            doneButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x0f000000, 0));
            doneButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
            doneButton.setText(LocaleController.getString("ApplyTheme", R.string.ApplyTheme).toUpperCase());
            doneButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            saveButtonsContainer.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
            doneButton.setOnClickListener(v -> {
                Theme.ThemeInfo previousTheme = Theme.getPreviousTheme();
                if (previousTheme == null) {
                    return;
                }
                Theme.ThemeAccent previousAccent;
                if (previousTheme != null && previousTheme.prevAccentId >= 0) {
                    previousAccent = previousTheme.themeAccentsMap.get(previousTheme.prevAccentId);
                } else {
                    previousAccent = previousTheme.getAccent(false);
                }
                if (accent != null) {
                    saveAccentWallpaper();
                    Theme.saveThemeAccents(applyingTheme, true, false, false, false);
                    Theme.clearPreviousTheme();
                    Theme.applyTheme(applyingTheme, nightTheme);
                    parentLayout.rebuildAllFragmentViews(false, false);
                } else {
                    parentLayout.rebuildAllFragmentViews(false, false);
                    Theme.applyThemeFile(new File(applyingTheme.pathToFile), applyingTheme.name, applyingTheme.info, false);
                    MessagesController.getInstance(applyingTheme.account).saveTheme(applyingTheme, null, false, false);
                    SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit();
                    editor.putString("lastDayTheme", applyingTheme.getKey());
                    editor.commit();
                }
                finishFragment();
                if (screenType == SCREEN_TYPE_PREVIEW) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didApplyNewTheme, previousTheme, previousAccent, deleteOnCancel);
                }
            });
        }

        if (screenType == SCREEN_TYPE_ACCENT_COLOR && !Theme.hasCustomWallpaper() && accent.backgroundOverrideColor != 0x100000000L) {
            selectColorType(2);
        }

        themeDescriptions = getThemeDescriptionsInternal();
        setCurrentImage(true);
        updatePlayAnimationView(false);

        if (showColor) {
            showPatternsView(0, true, false);
        }

        return fragmentView;
    }

    private void onColorsRotate() {
        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            backgroundRotation += 45;
            while (backgroundRotation >= 360) {
                backgroundRotation -= 360;
            }
            setBackgroundColor(backgroundColor, 0, true, true);
        } else {
            accent.backgroundRotation += 45;
            while (accent.backgroundRotation >= 360) {
                accent.backgroundRotation -= 360;
            }
            Theme.refreshThemeColors();
        }
    }

    private void selectColorType(int id) {
        selectColorType(id, true);
    }

    private void selectColorType(int id, boolean ask) {
        if (getParentActivity() == null || colorType == id || patternViewAnimation != null) {
            return;
        }
        if (ask && id == 2 && (Theme.hasCustomWallpaper() || accent.backgroundOverrideColor == 0x100000000L)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("ChangeChatBackground", R.string.ChangeChatBackground));
            if (!Theme.hasCustomWallpaper() || Theme.isCustomWallpaperColor()) {
                builder.setMessage(LocaleController.getString("ChangeColorToColor", R.string.ChangeColorToColor));
                builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialog, which) -> {
                    if (accent.backgroundOverrideColor == 0x100000000L) {
                        accent.backgroundOverrideColor = 0;
                        accent.backgroundGradientOverrideColor1 = 0;
                        accent.backgroundGradientOverrideColor2 = 0;
                        accent.backgroundGradientOverrideColor3 = 0;
                        updatePlayAnimationView(false);
                        Theme.refreshThemeColors();
                    }
                    removeBackgroundOverride = true;
                    Theme.resetCustomWallpaper(true);
                    selectColorType(2, false);
                });
                builder.setNegativeButton(LocaleController.getString("Continue", R.string.Continue), (dialog, which) -> {
                    if (Theme.isCustomWallpaperColor()) {
                        accent.backgroundOverrideColor = accent.overrideWallpaper.color;
                        accent.backgroundGradientOverrideColor1 = accent.overrideWallpaper.gradientColor1;
                        accent.backgroundGradientOverrideColor2 = accent.overrideWallpaper.gradientColor2;
                        accent.backgroundGradientOverrideColor3 = accent.overrideWallpaper.gradientColor3;
                        accent.backgroundRotation = accent.overrideWallpaper.rotation;
                        accent.patternSlug = accent.overrideWallpaper.slug;
                        currentIntensity = accent.patternIntensity = accent.overrideWallpaper.intensity;
                        if (accent.patternSlug != null && !Theme.COLOR_BACKGROUND_SLUG.equals(accent.patternSlug)) {
                            for (int a = 0, N = patterns.size(); a < N; a++) {
                                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) patterns.get(a);
                                if (wallPaper.pattern) {
                                    if (accent.patternSlug.equals(wallPaper.slug)) {
                                        selectedPattern = wallPaper;
                                        break;
                                    }
                                }
                            }
                        } else {
                            selectedPattern = null;
                        }
                        removeBackgroundOverride = true;
                        backgroundCheckBoxView[1].setChecked(selectedPattern != null, true);
                        updatePlayAnimationView(false);
                        Theme.refreshThemeColors();
                    }
                    Drawable background = backgroundImage.getBackground();
                    if (background instanceof MotionBackgroundDrawable) {
                        MotionBackgroundDrawable drawable = (MotionBackgroundDrawable) background;
                        drawable.setPatternBitmap(100, null);
                        if (Theme.getActiveTheme().isDark()) {
                            if (currentIntensity < 0) {
                                backgroundImage.getImageReceiver().setGradientBitmap(drawable.getBitmap());
                            }
                            if (intensitySeekBar != null) {
                                intensitySeekBar.setTwoSided(true);
                            }
                        } else if (currentIntensity < 0) {
                            currentIntensity = -currentIntensity;
                        }
                    }
                    if (intensitySeekBar != null) {
                        intensitySeekBar.setProgress(currentIntensity);
                    }
                    Theme.resetCustomWallpaper(true);
                    selectColorType(2, false);
                });
            } else {
                builder.setMessage(LocaleController.getString("ChangeWallpaperToColor", R.string.ChangeWallpaperToColor));
                builder.setPositiveButton(LocaleController.getString("Change", R.string.Change), (dialog, which) -> {
                    if (accent.backgroundOverrideColor == 0x100000000L) {
                        accent.backgroundOverrideColor = 0;
                        accent.backgroundGradientOverrideColor1 = 0;
                        accent.backgroundGradientOverrideColor2 = 0;
                        accent.backgroundGradientOverrideColor3 = 0;
                        updatePlayAnimationView(false);
                        Theme.refreshThemeColors();
                    }
                    removeBackgroundOverride = true;
                    Theme.resetCustomWallpaper(true);
                    selectColorType(2, false);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            }
            showDialog(builder.create());
            return;
        }
        int prevType = colorType;
        colorType = id;
        switch (id) {
            case 1:
                dropDown.setText(LocaleController.getString("ColorPickerMainColor", R.string.ColorPickerMainColor));
                int colorsCount = accent.accentColor2 != 0 ? 2 : 1;
                colorPicker.setType(1, hasChanges(1), 2, colorsCount, false, 0, false);
                colorPicker.setColor(accent.accentColor, 0);
                if (accent.accentColor2 != 0) {
                    colorPicker.setColor(accent.accentColor2, 1);
                }
                if (prevType == 2 || prevType == 3 && accent.myMessagesGradientAccentColor2 != 0) {
                    messagesAdapter.notifyItemRemoved(0);
                }
                break;
            case 2: {
                dropDown.setText(LocaleController.getString("ColorPickerBackground", R.string.ColorPickerBackground));

                int defaultBackground = Theme.getColor(Theme.key_chat_wallpaper);
                int defaultGradient1 = Theme.hasThemeKey(Theme.key_chat_wallpaper_gradient_to1) ? Theme.getColor(Theme.key_chat_wallpaper_gradient_to1) : 0;
                int defaultGradient2 = Theme.hasThemeKey(Theme.key_chat_wallpaper_gradient_to2) ? Theme.getColor(Theme.key_chat_wallpaper_gradient_to2) : 0;
                int defaultGradient3 = Theme.hasThemeKey(Theme.key_chat_wallpaper_gradient_to3) ? Theme.getColor(Theme.key_chat_wallpaper_gradient_to3) : 0;

                int backgroundGradientOverrideColor1 = (int) accent.backgroundGradientOverrideColor1;
                if (backgroundGradientOverrideColor1 == 0 && accent.backgroundGradientOverrideColor1 != 0) {
                    defaultGradient1 = 0;
                }
                int backgroundGradientOverrideColor2 = (int) accent.backgroundGradientOverrideColor2;
                if (backgroundGradientOverrideColor2 == 0 && accent.backgroundGradientOverrideColor2 != 0) {
                    defaultGradient2 = 0;
                }
                int backgroundGradientOverrideColor3 = (int) accent.backgroundGradientOverrideColor3;
                if (backgroundGradientOverrideColor3 == 0 && accent.backgroundGradientOverrideColor3 != 0) {
                    defaultGradient3 = 0;
                }
                int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
                int count;
                if (backgroundGradientOverrideColor1 != 0 || defaultGradient1 != 0) {
                    if (backgroundGradientOverrideColor3 != 0 || defaultGradient3 != 0) {
                        count = 4;
                    } else if (backgroundGradientOverrideColor2 != 0 || defaultGradient2 != 0) {
                        count = 3;
                    } else {
                        count = 2;
                    }
                } else {
                    count = 1;
                }
                colorPicker.setType(2, hasChanges(2), 4, count, false, accent.backgroundRotation, false);
                colorPicker.setColor(backgroundGradientOverrideColor3 != 0 ? backgroundGradientOverrideColor3 : defaultGradient3, 3);
                colorPicker.setColor(backgroundGradientOverrideColor2 != 0 ? backgroundGradientOverrideColor2 : defaultGradient2, 2);
                colorPicker.setColor(backgroundGradientOverrideColor1 != 0 ? backgroundGradientOverrideColor1 : defaultGradient1, 1);
                colorPicker.setColor(backgroundOverrideColor != 0 ? backgroundOverrideColor : defaultBackground, 0);
                if (prevType == 1 || accent.myMessagesGradientAccentColor2 == 0) {
                    messagesAdapter.notifyItemInserted(0);
                } else {
                    messagesAdapter.notifyItemChanged(0);
                }
                listView2.smoothScrollBy(0, AndroidUtilities.dp(60));
                break;
            }
            case 3: {
                dropDown.setText(LocaleController.getString("ColorPickerMyMessages", R.string.ColorPickerMyMessages));
                int count;
                if (accent.myMessagesGradientAccentColor1 != 0) {
                    if (accent.myMessagesGradientAccentColor3 != 0) {
                        count = 4;
                    } else if (accent.myMessagesGradientAccentColor2 != 0) {
                        count = 3;
                    } else {
                        count = 2;
                    }
                } else {
                    count = 1;
                }
                colorPicker.setType(2, hasChanges(3), 4, count, true, 0, false);
                colorPicker.setColor(accent.myMessagesGradientAccentColor3, 3);
                colorPicker.setColor(accent.myMessagesGradientAccentColor2, 2);
                colorPicker.setColor(accent.myMessagesGradientAccentColor1, 1);
                colorPicker.setColor(accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accent.accentColor, 0);
                messagesCheckBoxView[1].setColor(0, accent.myMessagesAccentColor);
                messagesCheckBoxView[1].setColor(1, accent.myMessagesGradientAccentColor1);
                messagesCheckBoxView[1].setColor(2, accent.myMessagesGradientAccentColor2);
                messagesCheckBoxView[1].setColor(3, accent.myMessagesGradientAccentColor3);
                if (accent.myMessagesGradientAccentColor2 != 0) {
                    if (prevType == 1) {
                        messagesAdapter.notifyItemInserted(0);
                    } else {
                        messagesAdapter.notifyItemChanged(0);
                    }
                } else if (prevType == 2) {
                    messagesAdapter.notifyItemRemoved(0);
                }
                listView2.smoothScrollBy(0, AndroidUtilities.dp(60));
                showAnimationHint();
                break;
            }
        }
        if (id == 1 || id == 3) {
            if (prevType == 2) {
                if (patternLayout[1].getVisibility() == View.VISIBLE) {
                    showPatternsView(0, true, true);
                }
            }
            if (id == 1) {
                if (applyingTheme.isDark()) {
                    colorPicker.setMinBrightness(0.2f);
                } else {
                    colorPicker.setMinBrightness(0.05f);
                    colorPicker.setMaxBrightness(0.8f);
                }
            } else {
                colorPicker.setMinBrightness(0f);
                colorPicker.setMaxBrightness(1f);
            }
        } else {
            colorPicker.setMinBrightness(0f);
            colorPicker.setMaxBrightness(1f);
        }
    }

    private void selectPattern(int position) {
        TLRPC.TL_wallPaper wallPaper;
        if (position >= 0 && position < patterns.size()) {
            wallPaper = (TLRPC.TL_wallPaper) patterns.get(position);
        } else {
            wallPaper = lastSelectedPattern;
        }
        if (wallPaper == null) {
            return;
        }
        backgroundImage.setImage(ImageLocation.getForDocument(wallPaper.document), imageFilter, null, null, "jpg", wallPaper.document.size, 1, wallPaper);
        selectedPattern = wallPaper;
        isMotion = backgroundCheckBoxView[2].isChecked();
        updateButtonState(false, true);
    }

    private void saveAccentWallpaper() {
        if (accent == null || TextUtils.isEmpty(accent.patternSlug)) {
            return;
        }
        try {
            File toFile = accent.getPathToWallpaper();

            Drawable background = backgroundImage.getBackground();
            Bitmap bitmap = backgroundImage.getImageReceiver().getBitmap();

            FileOutputStream stream = new FileOutputStream(toFile);
            bitmap.compress(background instanceof MotionBackgroundDrawable ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 87, stream);
            stream.close();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private boolean hasChanges(int type) {
        if (editingTheme) {
            return false;
        }
        if (type == 1 || type == 2) {
            if (backupBackgroundOverrideColor != 0) {
                if (backupBackgroundOverrideColor != accent.backgroundOverrideColor) {
                    return true;
                }
            } else {
                int defaultBackground = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper);
                int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
                int currentBackground = backgroundOverrideColor == 0 ? defaultBackground : backgroundOverrideColor;
                if (currentBackground != defaultBackground) {
                    return true;
                }
            }
            if (backupBackgroundGradientOverrideColor1 != 0 || backupBackgroundGradientOverrideColor2 != 0 || backupBackgroundGradientOverrideColor3 != 0) {
                if (backupBackgroundGradientOverrideColor1 != accent.backgroundGradientOverrideColor1 || backupBackgroundGradientOverrideColor2 != accent.backgroundGradientOverrideColor2 || backupBackgroundGradientOverrideColor3 != accent.backgroundGradientOverrideColor3) {
                    return true;
                }
            } else {
                for (int a = 0; a < 3; a++) {
                    int defaultBackgroundGradient;
                    long backgroundGradientOverrideColorFull;
                    if (a == 0) {
                        defaultBackgroundGradient = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to1);
                        backgroundGradientOverrideColorFull = accent.backgroundGradientOverrideColor1;
                    } else if (a == 1) {
                        defaultBackgroundGradient = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
                        backgroundGradientOverrideColorFull = accent.backgroundGradientOverrideColor2;
                    } else {
                        defaultBackgroundGradient = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to3);
                        backgroundGradientOverrideColorFull = accent.backgroundGradientOverrideColor3;
                    }
                    int backgroundGradientOverrideColor = (int) backgroundGradientOverrideColorFull;
                    int currentGradient;
                    if (backgroundGradientOverrideColor == 0 && backgroundGradientOverrideColorFull != 0) {
                        currentGradient = 0;
                    } else {
                        currentGradient = backgroundGradientOverrideColor == 0 ? defaultBackgroundGradient : backgroundGradientOverrideColor;
                    }
                    if (currentGradient != defaultBackgroundGradient) {
                        return true;
                    }
                }
            }
            if (accent.backgroundRotation != backupBackgroundRotation) {
                return true;
            }
        }
        if (type == 1 || type == 3) {
            if (backupAccentColor != accent.accentColor2) {
                return true;
            }
            if (backupMyMessagesAccentColor != 0) {
                if (backupMyMessagesAccentColor != accent.myMessagesAccentColor) {
                    return true;
                }
            } else {
                if (accent.myMessagesAccentColor != 0 && accent.myMessagesAccentColor != accent.accentColor) {
                    return true;
                }
            }
            if (backupMyMessagesGradientAccentColor1 != 0) {
                if (backupMyMessagesGradientAccentColor1 != accent.myMessagesGradientAccentColor1) {
                    return true;
                }
            } else {
                if (accent.myMessagesGradientAccentColor1 != 0) {
                    return true;
                }
            }
            if (backupMyMessagesGradientAccentColor2 != 0) {
                if (backupMyMessagesGradientAccentColor2 != accent.myMessagesGradientAccentColor2) {
                    return true;
                }
            } else {
                if (accent.myMessagesGradientAccentColor2 != 0) {
                    return true;
                }
            }
            if (backupMyMessagesGradientAccentColor3 != 0) {
                if (backupMyMessagesGradientAccentColor3 != accent.myMessagesGradientAccentColor3) {
                    return true;
                }
            } else {
                if (accent.myMessagesGradientAccentColor3 != 0) {
                    return true;
                }
            }
            if (backupMyMessagesAnimated != accent.myMessagesAnimated) {
                return true;
            }
        }
        return false;
    }

    private boolean checkDiscard() {
        if (screenType == SCREEN_TYPE_ACCENT_COLOR && (
                accent.accentColor != backupAccentColor ||
                accent.accentColor2 != backupAccentColor2 ||
                accent.myMessagesAccentColor != backupMyMessagesAccentColor ||
                accent.myMessagesGradientAccentColor1 != backupMyMessagesGradientAccentColor1 ||
                accent.myMessagesGradientAccentColor2 != backupMyMessagesGradientAccentColor2 ||
                accent.myMessagesGradientAccentColor3 != backupMyMessagesGradientAccentColor3 ||
                accent.myMessagesAnimated != backupMyMessagesAnimated ||
                accent.backgroundOverrideColor != backupBackgroundOverrideColor ||
                accent.backgroundGradientOverrideColor1 != backupBackgroundGradientOverrideColor1 ||
                accent.backgroundGradientOverrideColor2 != backupBackgroundGradientOverrideColor2 ||
                accent.backgroundGradientOverrideColor3 != backupBackgroundGradientOverrideColor3 ||
                Math.abs(accent.patternIntensity - backupIntensity) > 0.001f ||
                accent.backgroundRotation != backupBackgroundRotation ||
                !accent.patternSlug.equals(selectedPattern != null ? selectedPattern.slug : "") ||
                selectedPattern != null && accent.patternMotion != isMotion ||
                selectedPattern != null && accent.patternIntensity != currentIntensity
        )) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("SaveChangesAlertTitle", R.string.SaveChangesAlertTitle));
            builder.setMessage(LocaleController.getString("SaveChangesAlertText", R.string.SaveChangesAlertText));
            builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialogInterface, i) -> actionBar2.getActionBarMenuOnItemClick().onItemClick(4));
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> cancelThemeApply(false));
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.invalidateMotionBackground);
        if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_PREVIEW) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        }
        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND || screenType == SCREEN_TYPE_ACCENT_COLOR) {
            Theme.setChangingWallpaper(true);
        }
        if (screenType != SCREEN_TYPE_PREVIEW || accent != null) {
            if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
                int w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                int h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                imageFilter = (int) (w / AndroidUtilities.density) + "_" + (int) (h / AndroidUtilities.density) + "_f";
            } else {
                imageFilter = (int) (1080 / AndroidUtilities.density) + "_" + (int) (1920 / AndroidUtilities.density) + "_f";
            }
            maxWallpaperSize = Math.min(1920, Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));

            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersNeedReload);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersDidLoad);
            TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

            if (patterns == null) {
                patterns = new ArrayList<>();
                MessagesStorage.getInstance(currentAccount).getWallpapers();
            }
        } else {
            isMotion = Theme.isWallpaperMotion();
        }

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.invalidateMotionBackground);
        if (frameLayout != null && onGlobalLayoutListener != null) {
            frameLayout.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
        }
        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND || screenType == SCREEN_TYPE_ACCENT_COLOR) {
            AndroidUtilities.runOnUIThread(() -> Theme.setChangingWallpaper(false));
        }

        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            if (blurredBitmap != null) {
                blurredBitmap.recycle();
                blurredBitmap = null;
            }
            Theme.applyChatServiceMessageColor();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
        } else if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_PREVIEW) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        }
        if (screenType != SCREEN_TYPE_PREVIEW || accent != null) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersNeedReload);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersDidLoad);
        }

        super.onFragmentDestroy();
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if (!isOpen) {
            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                Theme.applyChatServiceMessageColor();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dialogsAdapter != null) {
            dialogsAdapter.notifyDataSetChanged();
        }
        if (messagesAdapter != null) {
            messagesAdapter.notifyDataSetChanged();
        }
        if (isMotion) {
            parallaxEffect.setEnabled(true);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isMotion) {
            parallaxEffect.setEnabled(false);
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        updateButtonState( true, canceled);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        if (radialProgress != null) {
            radialProgress.setProgress(1, progressVisible);
        }
        updateButtonState(false, true);
    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        if (radialProgress != null) {
            radialProgress.setProgress(Math.min(1f, downloadedSize / (float) totalSize), progressVisible);
            if (radialProgress.getIcon() != MediaActionDrawable.ICON_EMPTY) {
                updateButtonState(false, true);
            }
        }
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    private void updateBlurred() {
        if (isBlurred && blurredBitmap == null) {
            if (currentWallpaperBitmap != null) {
                originalBitmap = currentWallpaperBitmap;
                blurredBitmap = Utilities.blurWallpaper(currentWallpaperBitmap);
            } else {
                ImageReceiver imageReceiver = backgroundImage.getImageReceiver();
                if (imageReceiver.hasNotThumb() || imageReceiver.hasStaticThumb()) {
                    originalBitmap = imageReceiver.getBitmap();
                    blurredBitmap = Utilities.blurWallpaper(imageReceiver.getBitmap());
                }
            }
        }
        if (isBlurred) {
            if (blurredBitmap != null) {
                backgroundImage.setImageBitmap(blurredBitmap);
            }
        } else {
            setCurrentImage(false);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (!checkDiscard()) {
            return false;
        }
        cancelThemeApply(true);
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (listView == null) {
                return;
            }
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof DialogCell) {
                    DialogCell cell = (DialogCell) child;
                    cell.update(0);
                }
            }
        } else if (id == NotificationCenter.invalidateMotionBackground) {
            if (listView2 != null) {
                listView2.invalidateViews();
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (page2 != null) {
                setCurrentImage(true);
            }
        } else if (id == NotificationCenter.wallpapersNeedReload) {
            if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                WallpapersListActivity.FileWallpaper fileWallpaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                if (fileWallpaper.slug == null) {
                    fileWallpaper.slug = (String) args[0];
                }
            }
        } else if (id == NotificationCenter.wallpapersDidLoad) {
            ArrayList<TLRPC.WallPaper> arrayList = (ArrayList<TLRPC.WallPaper>) args[0];
            patterns.clear();
            patternsDict.clear();

            boolean added = false;
            for (int a = 0, N = arrayList.size(); a < N; a++) {
                TLRPC.WallPaper wallPaper = arrayList.get(a);
                if (wallPaper instanceof TLRPC.TL_wallPaper && wallPaper.pattern) {
                    if (wallPaper.document != null && !patternsDict.containsKey(wallPaper.document.id)) {
                        patterns.add(wallPaper);
                        patternsDict.put(wallPaper.document.id, wallPaper);
                    }
                    if (accent != null && accent.patternSlug.equals(wallPaper.slug)) {
                        selectedPattern = (TLRPC.TL_wallPaper) wallPaper;
                        added = true;
                        setCurrentImage(false);
                        updateButtonState(false, false);
                    } else if (accent == null && selectedPattern != null && selectedPattern.slug.equals(wallPaper.slug)) {
                        added = true;
                    }
                }
            }
            if (!added && selectedPattern != null) {
                patterns.add(0, selectedPattern);
            }
            if (patternsAdapter != null) {
                patternsAdapter.notifyDataSetChanged();
            }
            long acc = 0;
            for (int a = 0, N = arrayList.size(); a < N; a++) {
                TLRPC.WallPaper wallPaper = arrayList.get(a);
                if (wallPaper instanceof TLRPC.TL_wallPaper) {
                    acc = MediaDataController.calcHash(acc, wallPaper.id);
                }
            }
            TLRPC.TL_account_getWallPapers req = new TLRPC.TL_account_getWallPapers();
            req.hash = acc;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_account_wallPapers) {
                    TLRPC.TL_account_wallPapers res = (TLRPC.TL_account_wallPapers) response;
                    patterns.clear();
                    patternsDict.clear();
                    boolean added2 = false;
                    for (int a = 0, N = res.wallpapers.size(); a < N; a++) {
                        if (!(res.wallpapers.get(a) instanceof TLRPC.TL_wallPaper)) {
                            continue;
                        }
                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) res.wallpapers.get(a);
                        if (wallPaper.pattern) {
                            if (wallPaper.document != null && !patternsDict.containsKey(wallPaper.document.id)) {
                                patterns.add(wallPaper);
                                patternsDict.put(wallPaper.document.id, wallPaper);
                            }
                            if (accent != null && accent.patternSlug.equals(wallPaper.slug)) {
                                selectedPattern = wallPaper;
                                added2 = true;
                                setCurrentImage(false);
                                updateButtonState(false, false);
                            } else if (accent == null && selectedPattern != null && selectedPattern.slug.equals(wallPaper.slug)) {
                                added2 = true;
                            }
                        }
                    }
                    if (!added2 && selectedPattern != null) {
                        patterns.add(0, selectedPattern);
                    }
                    if (patternsAdapter != null) {
                        patternsAdapter.notifyDataSetChanged();
                    }
                    MessagesStorage.getInstance(currentAccount).putWallpapers(res.wallpapers, 1);
                }
                if (selectedPattern == null && accent != null && !TextUtils.isEmpty(accent.patternSlug)) {
                    TLRPC.TL_account_getWallPaper req2 = new TLRPC.TL_account_getWallPaper();
                    TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                    inputWallPaperSlug.slug = accent.patternSlug;
                    req2.wallpaper = inputWallPaperSlug;
                    int reqId2 = getConnectionsManager().sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response1 instanceof TLRPC.TL_wallPaper) {
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) response1;
                            if (wallPaper.pattern) {
                                selectedPattern = wallPaper;
                                setCurrentImage(false);
                                updateButtonState(false, false);
                                patterns.add(0, selectedPattern);
                                if (patternsAdapter != null) {
                                    patternsAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                    }));
                    ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId2, classGuid);
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    private void cancelThemeApply(boolean back) {
        if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            if (!back) {
                finishFragment();
            }
            return;
        }
        Theme.applyPreviousTheme();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
            if (editingTheme) {
                accent.accentColor = backupAccentColor;
                accent.accentColor2 = backupAccentColor2;
                accent.myMessagesAccentColor = backupMyMessagesAccentColor;
                accent.myMessagesGradientAccentColor1 = backupMyMessagesGradientAccentColor1;
                accent.myMessagesGradientAccentColor2 = backupMyMessagesGradientAccentColor2;
                accent.myMessagesGradientAccentColor3 = backupMyMessagesGradientAccentColor3;
                accent.myMessagesAnimated = backupMyMessagesAnimated;
                accent.backgroundOverrideColor = backupBackgroundOverrideColor;
                accent.backgroundGradientOverrideColor1 = backupBackgroundGradientOverrideColor1;
                accent.backgroundGradientOverrideColor2 = backupBackgroundGradientOverrideColor2;
                accent.backgroundGradientOverrideColor3 = backupBackgroundGradientOverrideColor3;
                accent.backgroundRotation = backupBackgroundRotation;
                accent.patternSlug = backupSlug;
                accent.patternIntensity = backupIntensity;
            }
            Theme.saveThemeAccents(applyingTheme, false, true, false, false);
        } else {
            if (accent != null) {
                Theme.saveThemeAccents(applyingTheme, false, deleteOnCancel, false, false);
            }
            parentLayout.rebuildAllFragmentViews(false, false);
            if (deleteOnCancel && applyingTheme.pathToFile != null && !Theme.isThemeInstalled(applyingTheme)) {
                new File(applyingTheme.pathToFile).delete();
            }
        }
        if (!back) {
            finishFragment();
        }
    }

    private int getButtonsColor(String key) {
        return useDefaultThemeForButtons ? Theme.getDefaultColor(key) : Theme.getColor(key);
    }

    private void scheduleApplyColor(int color, int num, boolean applyNow) {
        if (num == -1) {
            if (colorType == 1 || colorType == 2) {
                if (backupBackgroundOverrideColor != 0) {
                    accent.backgroundOverrideColor = backupBackgroundOverrideColor;
                } else {
                    accent.backgroundOverrideColor = 0;
                }
                if (backupBackgroundGradientOverrideColor1 != 0) {
                    accent.backgroundGradientOverrideColor1 = backupBackgroundGradientOverrideColor1;
                } else {
                    accent.backgroundGradientOverrideColor1 = 0;
                }
                if (backupBackgroundGradientOverrideColor2 != 0) {
                    accent.backgroundGradientOverrideColor2 = backupBackgroundGradientOverrideColor2;
                } else {
                    accent.backgroundGradientOverrideColor2 = 0;
                }
                if (backupBackgroundGradientOverrideColor3 != 0) {
                    accent.backgroundGradientOverrideColor3 = backupBackgroundGradientOverrideColor3;
                } else {
                    accent.backgroundGradientOverrideColor3 = 0;
                }
                accent.backgroundRotation = backupBackgroundRotation;
                if (colorType == 2) {
                    int defaultBackground = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper);
                    int defaultBackgroundGradient1 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to1);
                    int defaultBackgroundGradient2 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
                    int defaultBackgroundGradient3 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to3);
                    int backgroundGradientOverrideColor1 = (int) accent.backgroundGradientOverrideColor1;
                    int backgroundGradientOverrideColor2 = (int) accent.backgroundGradientOverrideColor2;
                    int backgroundGradientOverrideColor3 = (int) accent.backgroundGradientOverrideColor3;
                    int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
                    colorPicker.setColor(backgroundGradientOverrideColor3 != 0 ? backgroundGradientOverrideColor3 : defaultBackgroundGradient3, 3);
                    colorPicker.setColor(backgroundGradientOverrideColor2 != 0 ? backgroundGradientOverrideColor2 : defaultBackgroundGradient2, 2);
                    colorPicker.setColor(backgroundGradientOverrideColor1 != 0 ? backgroundGradientOverrideColor1 : defaultBackgroundGradient1, 1);
                    colorPicker.setColor(backgroundOverrideColor != 0 ? backgroundOverrideColor : defaultBackground, 0);
                }
            }
            if (colorType == 1 || colorType == 3) {
                if (backupMyMessagesAccentColor != 0) {
                    accent.myMessagesAccentColor = backupMyMessagesAccentColor;
                } else {
                    accent.myMessagesAccentColor = 0;
                }
                if (backupMyMessagesGradientAccentColor1 != 0) {
                    accent.myMessagesGradientAccentColor1 = backupMyMessagesGradientAccentColor1;
                } else {
                    accent.myMessagesGradientAccentColor1 = 0;
                }
                if (backupMyMessagesGradientAccentColor2 != 0) {
                    accent.myMessagesGradientAccentColor2 = backupMyMessagesGradientAccentColor2;
                } else {
                    accent.myMessagesGradientAccentColor2 = 0;
                }
                if (backupMyMessagesGradientAccentColor3 != 0) {
                    accent.myMessagesGradientAccentColor3 = backupMyMessagesGradientAccentColor3;
                } else {
                    accent.myMessagesGradientAccentColor3 = 0;
                }
                if (colorType == 3) {
                    colorPicker.setColor(accent.myMessagesGradientAccentColor3, 3);
                    colorPicker.setColor(accent.myMessagesGradientAccentColor2, 2);
                    colorPicker.setColor(accent.myMessagesGradientAccentColor1, 1);
                    colorPicker.setColor(accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accent.accentColor, 0);
                }
            }
            Theme.refreshThemeColors();
            listView2.invalidateViews();
            return;
        }
        if (lastPickedColorNum != -1 && lastPickedColorNum != num) {
            applyColorAction.run();
        }
        lastPickedColor = color;
        lastPickedColorNum = num;
        if (applyNow) {
            applyColorAction.run();
        } else {
            if (!applyColorScheduled) {
                applyColorScheduled = true;
                fragmentView.postDelayed(applyColorAction, 16L);
            }
        }
    }

    private void applyColor(int color, int num) {
        if (colorType == 1) {
            if (num == 0) {
                accent.accentColor = color;
                Theme.refreshThemeColors();
            } else if (num == 1) {
                accent.accentColor2 = color;
                Theme.refreshThemeColors(true, true);
                listView2.invalidateViews();
                colorPicker.setHasChanges(hasChanges(colorType));
                updatePlayAnimationView(true);
            }
        } else if (colorType == 2) {
            if (lastPickedColorNum == 0) {
                accent.backgroundOverrideColor = color;
            } else {
                if (num == 1) {
                    int defaultGradientColor = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to1);
                    if (color == 0 && defaultGradientColor != 0) {
                        accent.backgroundGradientOverrideColor1 = (1L << 32);
                    } else {
                        accent.backgroundGradientOverrideColor1 = color;
                    }
                } else if (num == 2) {
                    int defaultGradientColor = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
                    if (color == 0 && defaultGradientColor != 0) {
                        accent.backgroundGradientOverrideColor2 = (1L << 32);
                    } else {
                        accent.backgroundGradientOverrideColor2 = color;
                    }
                } else if (num == 3) {
                    int defaultGradientColor = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to3);
                    if (color == 0 && defaultGradientColor != 0) {
                        accent.backgroundGradientOverrideColor3 = (1L << 32);
                    } else {
                        accent.backgroundGradientOverrideColor3 = color;
                    }
                }
            }
            Theme.refreshThemeColors(true, false);
            colorPicker.setHasChanges(hasChanges(colorType));
            updatePlayAnimationView(true);
        } else if (colorType == 3) {
            if (lastPickedColorNum == 0) {
                accent.myMessagesAccentColor = color;
            } else if (lastPickedColorNum == 1) {
                accent.myMessagesGradientAccentColor1 = color;
            } else if (lastPickedColorNum == 2) {
                int prevColor = accent.myMessagesGradientAccentColor2;
                accent.myMessagesGradientAccentColor2 = color;
                if (prevColor != 0 && color == 0) {
                    messagesAdapter.notifyItemRemoved(0);
                } else if (prevColor == 0 && color != 0) {
                    messagesAdapter.notifyItemInserted(0);
                    showAnimationHint();
                }
            } else {
                accent.myMessagesGradientAccentColor3 = color;
            }
            if (lastPickedColorNum >= 0) {
                messagesCheckBoxView[1].setColor(lastPickedColorNum, color);
            }
            Theme.refreshThemeColors(true, true);
            listView2.invalidateViews();
            colorPicker.setHasChanges(hasChanges(colorType));
            updatePlayAnimationView(true);
        }

        for (int i = 0, size = themeDescriptions.size(); i < size; i++) {
            ThemeDescription description = themeDescriptions.get(i);
            description.setColor(Theme.getColor(description.getCurrentKey()), false, false);
        }

        listView.invalidateViews();
        listView2.invalidateViews();
        if (dotsContainer != null) {
            dotsContainer.invalidate();
        }
    }

    private void updateButtonState(boolean ifSame, boolean animated) {
        Object object;
        if (selectedPattern != null) {
            object = selectedPattern;
        } else {
            object = currentWallpaper;
        }
        if (object instanceof TLRPC.TL_wallPaper || object instanceof MediaController.SearchImage) {
            if (animated && !progressVisible) {
                animated = false;
            }
            boolean fileExists;
            File path;
            int size;
            String fileName;
            if (object instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                fileName = FileLoader.getAttachFileName(wallPaper.document);
                if (TextUtils.isEmpty(fileName)) {
                    return;
                }
                path = FileLoader.getPathToAttach(wallPaper.document, true);
                size = wallPaper.document.size;
            } else {
                MediaController.SearchImage wallPaper = (MediaController.SearchImage) object;
                if (wallPaper.photo != null) {
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                    path = FileLoader.getPathToAttach(photoSize, true);
                    fileName = FileLoader.getAttachFileName(photoSize);
                    size = photoSize.size;
                } else {
                    path = ImageLoader.getHttpFilePath(wallPaper.imageUrl, "jpg");
                    fileName = path.getName();
                    size = wallPaper.size;
                }
                if (TextUtils.isEmpty(fileName)) {
                    return;
                }
            }
            if (fileExists = path.exists()) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                if (radialProgress != null) {
                    radialProgress.setProgress(1, animated);
                    radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animated);
                }
                backgroundImage.invalidate();
                if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                    if (size != 0) {
                        actionBar2.setSubtitle(AndroidUtilities.formatFileSize(size));
                    } else {
                        actionBar2.setSubtitle(null);
                    }
                }
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                if (radialProgress != null) {
                    boolean isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                    radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, ifSame, animated);
                }
                if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                    actionBar2.setSubtitle(LocaleController.getString("LoadingFullImage", R.string.LoadingFullImage));
                }
                backgroundImage.invalidate();
            }
            if (selectedPattern == null && backgroundButtonsContainer != null) {
                backgroundButtonsContainer.setAlpha(fileExists ? 1.0f : 0.5f);
            }
            if (screenType == SCREEN_TYPE_PREVIEW) {
                doneButton.setEnabled(fileExists);
                doneButton.setAlpha(fileExists ? 1.0f : 0.5f);
            } else if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                bottomOverlayChat.setEnabled(fileExists);
                bottomOverlayChatText.setAlpha(fileExists ? 1.0f : 0.5f);
            } else {
                saveItem.setEnabled(fileExists);
                saveItem.setAlpha(fileExists ? 1.0f : 0.5f);
            }
        } else {
            if (radialProgress != null) {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animated);
            }
        }
    }

    public void setDelegate(WallpaperActivityDelegate wallpaperActivityDelegate) {
        delegate = wallpaperActivityDelegate;
    }

    public void setPatterns(ArrayList<Object> arrayList) {
        patterns = arrayList;
        if (screenType == SCREEN_TYPE_ACCENT_COLOR || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
            if (wallPaper.patternId != 0) {
                for (int a = 0, N = patterns.size(); a < N; a++) {
                    TLRPC.TL_wallPaper pattern = (TLRPC.TL_wallPaper) patterns.get(a);
                    if (pattern.id == wallPaper.patternId) {
                        selectedPattern = pattern;
                        break;
                    }
                }
                currentIntensity = wallPaper.intensity;
            }
        }
    }

    private void showAnimationHint() {
        if (page2 == null || messagesCheckBoxView == null || accent.myMessagesGradientAccentColor2 == 0) {
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (preferences.getBoolean("bganimationhint", false)) {
            return;
        }
        if (animationHint == null) {
            animationHint = new HintView(getParentActivity(), 8);
            animationHint.setShowingDuration(5000);
            animationHint.setAlpha(0);
            animationHint.setVisibility(View.INVISIBLE);
            animationHint.setText(LocaleController.getString("BackgroundAnimateInfo", R.string.BackgroundAnimateInfo));
            animationHint.setExtraTranslationY(AndroidUtilities.dp(6));
            frameLayout.addView(animationHint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (colorType != 3) {
                return;
            }
            preferences.edit().putBoolean("bganimationhint", true).commit();
            animationHint.showForView(messagesCheckBoxView[0], true);
        }, 500);
    }

    private void updateSelectedPattern(boolean animated) {
        int count = patternsListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = patternsListView.getChildAt(a);
            if (child instanceof PatternCell) {
                ((PatternCell) child).updateSelected(animated);
            }
        }
    }

    private void updateMotionButton() {
        if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            if (selectedPattern == null && currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                backgroundCheckBoxView[2].setChecked(false, true);
            }
            backgroundCheckBoxView[selectedPattern != null ? 2 : 0].setVisibility(View.VISIBLE);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(backgroundCheckBoxView[2], View.ALPHA, selectedPattern != null ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.ALPHA, selectedPattern != null ? 0.0f : 1.0f));
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    backgroundCheckBoxView[selectedPattern != null ? 0 : 2].setVisibility(View.INVISIBLE);
                }
            });
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animatorSet.setDuration(200);
            animatorSet.start();
        } else {
            if (backgroundCheckBoxView[0].isEnabled() == (selectedPattern != null)) {
                return;
            }
            if (selectedPattern == null) {
                backgroundCheckBoxView[0].setChecked(false, true);
            }
            backgroundCheckBoxView[0].setEnabled(selectedPattern != null);

            if (selectedPattern != null) {
                backgroundCheckBoxView[0].setVisibility(View.VISIBLE);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) backgroundCheckBoxView[1].getLayoutParams();
            AnimatorSet animatorSet = new AnimatorSet();
            int offset = (layoutParams.width + AndroidUtilities.dp(9)) / 2;
            animatorSet.playTogether(ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.ALPHA, selectedPattern != null ? 1.0f : 0.0f));
            animatorSet.playTogether(ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.TRANSLATION_X, selectedPattern != null ? 0.0f : offset));
            animatorSet.playTogether(ObjectAnimator.ofFloat(backgroundCheckBoxView[1], View.TRANSLATION_X, selectedPattern != null ? 0.0f : -offset));
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (selectedPattern == null) {
                        backgroundCheckBoxView[0].setVisibility(View.INVISIBLE);
                    }
                }
            });
            animatorSet.start();
        }
    }

    private void showPatternsView(int num, boolean show, boolean animated) {
        boolean showMotion = show && num == 1 && selectedPattern != null;
        if (show) {
            if (num == 0) {
                if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                    previousBackgroundColor = backgroundColor;
                    previousBackgroundGradientColor1 = backgroundGradientColor1;
                    previousBackgroundGradientColor2 = backgroundGradientColor2;
                    previousBackgroundGradientColor3 = backgroundGradientColor3;
                    previousBackgroundRotation = backupBackgroundRotation;
                    int count;
                    if (previousBackgroundGradientColor3 != 0) {
                        count = 4;
                    } else if (previousBackgroundGradientColor2 != 0) {
                        count = 3;
                    } else if (previousBackgroundGradientColor1 != 0) {
                        count = 2;
                    } else {
                        count = 1;
                    }
                    colorPicker.setType(0, false, 4, count, false, previousBackgroundRotation, false);
                    colorPicker.setColor(backgroundGradientColor3, 3);
                    colorPicker.setColor(backgroundGradientColor2, 2);
                    colorPicker.setColor(backgroundGradientColor1, 1);
                    colorPicker.setColor(backgroundColor, 0);
                }
            } else {
                previousSelectedPattern = selectedPattern;
                previousIntensity = currentIntensity;
                patternsAdapter.notifyDataSetChanged();
                if (patterns != null) {
                    int index;
                    if (selectedPattern == null) {
                        index = 0;
                    } else {
                        index = patterns.indexOf(selectedPattern) + (screenType == SCREEN_TYPE_CHANGE_BACKGROUND ? 1 : 0);
                    }
                    patternsLayoutManager.scrollToPositionWithOffset(index, (patternsListView.getMeasuredWidth() - AndroidUtilities.dp(100 + 24)) / 2);
                }
            }
        }
        if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            backgroundCheckBoxView[showMotion ? 2 : 0].setVisibility(View.VISIBLE);
        }
        if (num == 1 && !intensitySeekBar.isTwoSided() && currentIntensity < 0) {
            currentIntensity = -currentIntensity;
            intensitySeekBar.setProgress(currentIntensity);
        }
        if (animated) {
            patternViewAnimation = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            int otherNum = num == 0 ? 1 : 0;
            if (show) {
                patternLayout[num].setVisibility(View.VISIBLE);
                if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                    animators.add(ObjectAnimator.ofFloat(listView2, View.TRANSLATION_Y, num == 1 ? -AndroidUtilities.dp(21) : 0));
                    animators.add(ObjectAnimator.ofFloat(backgroundCheckBoxView[2], View.ALPHA, showMotion ? 1.0f : 0.0f));
                    animators.add(ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.ALPHA, showMotion ? 0.0f : 1.0f));
                    if (num == 1) {
                        animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.ALPHA, 0.0f, 1.0f));
                    } else {
                        patternLayout[num].setAlpha(1.0f);
                        animators.add(ObjectAnimator.ofFloat(patternLayout[otherNum], View.ALPHA, 0.0f));
                    }
                    colorPicker.hideKeyboard();
                } else if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                    animators.add(ObjectAnimator.ofFloat(listView2, View.TRANSLATION_Y, -patternLayout[num].getMeasuredHeight() + AndroidUtilities.dp(48)));
                    animators.add(ObjectAnimator.ofFloat(backgroundCheckBoxView[2], View.ALPHA, showMotion ? 1.0f : 0.0f));
                    animators.add(ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.ALPHA, showMotion ? 0.0f : 1.0f));
                    animators.add(ObjectAnimator.ofFloat(backgroundImage, View.ALPHA, 0.0f));
                    if (patternLayout[otherNum].getVisibility() == View.VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(patternLayout[otherNum], View.ALPHA, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.ALPHA, 0.0f, 1.0f));
                        patternLayout[num].setTranslationY(0);
                    } else {
                        animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.TRANSLATION_Y, patternLayout[num].getMeasuredHeight(), 0));
                    }
                } else {
                    if (num == 1) {
                        animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.ALPHA, 0.0f, 1.0f));
                    } else {
                        patternLayout[num].setAlpha(1.0f);
                        animators.add(ObjectAnimator.ofFloat(patternLayout[otherNum], View.ALPHA, 0.0f));
                    }
                    colorPicker.hideKeyboard();
                }
            } else {
                animators.add(ObjectAnimator.ofFloat(listView2, View.TRANSLATION_Y, 0));
                animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.TRANSLATION_Y, patternLayout[num].getMeasuredHeight()));
                animators.add(ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.ALPHA, 1.0f));
                animators.add(ObjectAnimator.ofFloat(backgroundCheckBoxView[2], View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(backgroundImage, View.ALPHA, 1.0f));
            }
            patternViewAnimation.playTogether(animators);
            patternViewAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    patternViewAnimation = null;
                    if (show && patternLayout[otherNum].getVisibility() == View.VISIBLE) {
                        patternLayout[otherNum].setAlpha(1.0f);
                        patternLayout[otherNum].setVisibility(View.INVISIBLE);
                    } else if (!show) {
                        patternLayout[num].setVisibility(View.INVISIBLE);
                    }
                    if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        backgroundCheckBoxView[showMotion ? 0 : 2].setVisibility(View.INVISIBLE);
                    } else {
                        if (num == 1) {
                            patternLayout[otherNum].setAlpha(0.0f);
                        }
                    }
                }
            });
            patternViewAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            patternViewAnimation.setDuration(200);
            patternViewAnimation.start();
        } else {
            int otherNum = num == 0 ? 1 : 0;
            if (show) {
                patternLayout[num].setVisibility(View.VISIBLE);
                if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                    listView2.setTranslationY(num == 1 ? -AndroidUtilities.dp(21) : 0);
                    backgroundCheckBoxView[2].setAlpha(showMotion ? 1.0f : 0.0f);
                    backgroundCheckBoxView[0].setAlpha(showMotion ? 0.0f : 1.0f);
                    if (num == 1) {
                        patternLayout[num].setAlpha(1.0f);
                    } else {
                        patternLayout[num].setAlpha(1.0f);
                        patternLayout[otherNum].setAlpha(0.0f);
                    }
                    colorPicker.hideKeyboard();
                } else if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                    listView2.setTranslationY(-AndroidUtilities.dp(num == 0 ? 343 : 316) + AndroidUtilities.dp(48));
                    backgroundCheckBoxView[2].setAlpha(showMotion ? 1.0f : 0.0f);
                    backgroundCheckBoxView[0].setAlpha(showMotion ? 0.0f : 1.0f);
                    backgroundImage.setAlpha(0.0f);
                    if (patternLayout[otherNum].getVisibility() == View.VISIBLE) {
                        patternLayout[otherNum].setAlpha(0.0f);
                        patternLayout[num].setAlpha(1.0f);
                        patternLayout[num].setTranslationY(0);
                    } else {
                        patternLayout[num].setTranslationY(0);
                    }
                } else {
                    if (num == 1) {
                        patternLayout[num].setAlpha(1.0f);
                    } else {
                        patternLayout[num].setAlpha(1.0f);
                        patternLayout[otherNum].setAlpha(0.0f);
                    }
                    colorPicker.hideKeyboard();
                }
            } else {
                listView2.setTranslationY(0);
                patternLayout[num].setTranslationY(patternLayout[num].getMeasuredHeight());
                backgroundCheckBoxView[0].setAlpha(1.0f);
                backgroundCheckBoxView[2].setAlpha(1.0f);
                backgroundImage.setAlpha(1.0f);
            }

            if (show && patternLayout[otherNum].getVisibility() == View.VISIBLE) {
                patternLayout[otherNum].setAlpha(1.0f);
                patternLayout[otherNum].setVisibility(View.INVISIBLE);
            } else if (!show) {
                patternLayout[num].setVisibility(View.INVISIBLE);
            }
            if (screenType == SCREEN_TYPE_ACCENT_COLOR || screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                backgroundCheckBoxView[showMotion ? 0 : 2].setVisibility(View.INVISIBLE);
            } else {
                if (num == 1) {
                    patternLayout[otherNum].setAlpha(0.0f);
                }
            }
        }
    }

    private void animateMotionChange() {
        if (motionAnimation != null) {
            motionAnimation.cancel();
        }
        motionAnimation = new AnimatorSet();
        if (isMotion) {
            motionAnimation.playTogether(
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_X, parallaxScale),
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_Y, parallaxScale));
        } else {
            motionAnimation.playTogether(
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(backgroundImage, View.TRANSLATION_X, 0.0f),
                    ObjectAnimator.ofFloat(backgroundImage, View.TRANSLATION_Y, 0.0f));
        }
        motionAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        motionAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                motionAnimation = null;
            }
        });
        motionAnimation.start();
    }

    private void updatePlayAnimationView(boolean animated) {
        if (Build.VERSION.SDK_INT >= 29) {
            int color2 = 0;
            float intensity = 0;
            if (screenType == SCREEN_TYPE_PREVIEW) {
                if (accent != null) {
                    color2 = (int) accent.backgroundGradientOverrideColor2;
                } else {
                    color2 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to2);
                }
            } else if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                int defaultBackgroundGradient2 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
                int backgroundGradientOverrideColor2 = (int) accent.backgroundGradientOverrideColor2;
                if (backgroundGradientOverrideColor2 == 0 && accent.backgroundGradientOverrideColor2 != 0) {
                    color2 = 0;
                } else {
                    color2 = backgroundGradientOverrideColor2 != 0 ? backgroundGradientOverrideColor2 : defaultBackgroundGradient2;
                }
            } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                WallpapersListActivity.ColorWallpaper colorWallpaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                color2 = backgroundGradientColor2;
            }
            if (color2 != 0 && currentIntensity >= 0) {
                backgroundImage.getImageReceiver().setBlendMode(BlendMode.SOFT_LIGHT);
            } else {
                backgroundImage.getImageReceiver().setBlendMode(null);
            }
        }

        if (backgroundPlayAnimationView != null) {
            boolean visible;
            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                visible = backgroundGradientColor1 != 0;
            } else if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                int defaultBackgroundGradient1 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to1);
                int backgroundGradientOverrideColor1 = (int) accent.backgroundGradientOverrideColor1;
                int color1;
                if (backgroundGradientOverrideColor1 == 0 && accent.backgroundGradientOverrideColor1 != 0) {
                    color1 = 0;
                } else {
                    color1 = backgroundGradientOverrideColor1 != 0 ? backgroundGradientOverrideColor1 : defaultBackgroundGradient1;
                }
                visible = color1 != 0;
            } else {
                visible = false;
            }
            boolean wasVisible = backgroundPlayAnimationView.getTag() != null;
            backgroundPlayAnimationView.setTag(visible ? 1 : null);
            if (wasVisible != visible) {
                if (visible) {
                    backgroundPlayAnimationView.setVisibility(View.VISIBLE);
                }
                if (backgroundPlayViewAnimator != null) {
                    backgroundPlayViewAnimator.cancel();
                }
                if (animated) {
                    backgroundPlayViewAnimator = new AnimatorSet();
                    backgroundPlayViewAnimator.playTogether(
                            ObjectAnimator.ofFloat(backgroundPlayAnimationView, View.ALPHA, visible ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(backgroundPlayAnimationView, View.SCALE_X, visible ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(backgroundPlayAnimationView, View.SCALE_Y, visible ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(backgroundCheckBoxView[0], View.TRANSLATION_X, visible ? AndroidUtilities.dp(34) : 0.0f),
                            ObjectAnimator.ofFloat(backgroundCheckBoxView[1], View.TRANSLATION_X, visible ? -AndroidUtilities.dp(34) : 0.0f),
                            ObjectAnimator.ofFloat(backgroundCheckBoxView[2], View.TRANSLATION_X, visible ? AndroidUtilities.dp(34) : 0.0f));
                    backgroundPlayViewAnimator.setDuration(180);
                    backgroundPlayViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (backgroundPlayAnimationView.getTag() == null) {
                                backgroundPlayAnimationView.setVisibility(View.INVISIBLE);
                            }
                            backgroundPlayViewAnimator = null;
                        }
                    });
                    backgroundPlayViewAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    backgroundPlayViewAnimator.start();
                } else {
                    backgroundPlayAnimationView.setAlpha(visible ? 1.0f : 0.0f);
                    backgroundPlayAnimationView.setScaleX(visible ? 1.0f : 0.0f);
                    backgroundPlayAnimationView.setScaleY(visible ? 1.0f : 0.0f);
                    backgroundCheckBoxView[0].setTranslationX(visible ? AndroidUtilities.dp(34) : 0.0f);
                    backgroundCheckBoxView[1].setTranslationX(visible ? -AndroidUtilities.dp(34) : 0.0f);
                    backgroundCheckBoxView[2].setTranslationX(visible ? AndroidUtilities.dp(34) : 0.0f);
                }
            }
        }
        if (messagesPlayAnimationView != null) {
            boolean visible = true;//accent.myMessagesGradientAccentColor1 != 0;
            boolean wasVisible = messagesPlayAnimationView.getTag() != null;
            messagesPlayAnimationView.setTag(visible ? 1 : null);
            if (wasVisible != visible) {
                if (visible) {
                    messagesPlayAnimationView.setVisibility(View.VISIBLE);
                }
                if (messagesPlayViewAnimator != null) {
                    messagesPlayViewAnimator.cancel();
                }
                if (animated) {
                    messagesPlayViewAnimator = new AnimatorSet();
                    messagesPlayViewAnimator.playTogether(
                            ObjectAnimator.ofFloat(messagesPlayAnimationView, View.ALPHA, visible ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(messagesPlayAnimationView, View.SCALE_X, visible ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(messagesPlayAnimationView, View.SCALE_Y, visible ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(messagesCheckBoxView[0], View.TRANSLATION_X, visible ? -AndroidUtilities.dp(34) : 0.0f),
                            ObjectAnimator.ofFloat(messagesCheckBoxView[1], View.TRANSLATION_X, visible ? AndroidUtilities.dp(34) : 0.0f));
                            messagesPlayViewAnimator.setDuration(180);
                    messagesPlayViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (messagesPlayAnimationView.getTag() == null) {
                                messagesPlayAnimationView.setVisibility(View.INVISIBLE);
                            }
                            messagesPlayViewAnimator = null;
                        }
                    });
                    messagesPlayViewAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    messagesPlayViewAnimator.start();
                } else {
                    messagesPlayAnimationView.setAlpha(visible ? 1.0f : 0.0f);
                    messagesPlayAnimationView.setScaleX(visible ? 1.0f : 0.0f);
                    messagesPlayAnimationView.setScaleY(visible ? 1.0f : 0.0f);
                    messagesCheckBoxView[0].setTranslationX(visible ? -AndroidUtilities.dp(34) : 0.0f);
                    messagesCheckBoxView[1].setTranslationX(visible ? AndroidUtilities.dp(34) : 0.0f);
                }
            }
        }
    }

    private void setBackgroundColor(int color, int num, boolean applyNow, boolean animated) {
        if (num == 0) {
            backgroundColor = color;
        } else if (num == 1) {
            backgroundGradientColor1 = color;
        } else if (num == 2) {
            backgroundGradientColor2 = color;
        } else if (num == 3) {
            backgroundGradientColor3 = color;
        }
        updatePlayAnimationView(animated);
        if (backgroundCheckBoxView != null) {
            for (int a = 0; a < backgroundCheckBoxView.length; a++) {
                if (backgroundCheckBoxView[a] != null) {
                    backgroundCheckBoxView[a].setColor(num, color);
                }
            }
        }
        if (backgroundGradientColor2 != 0) {
            if (intensitySeekBar != null && Theme.getActiveTheme().isDark()) {
                intensitySeekBar.setTwoSided(true);
            }
            Drawable currentBackground = backgroundImage.getBackground();
            MotionBackgroundDrawable motionBackgroundDrawable;
            if (currentBackground instanceof MotionBackgroundDrawable) {
                motionBackgroundDrawable = (MotionBackgroundDrawable) currentBackground;
            } else {
                motionBackgroundDrawable = new MotionBackgroundDrawable();
                motionBackgroundDrawable.setParentView(backgroundImage);
                if (rotatePreview) {
                    motionBackgroundDrawable.rotatePreview(false);
                }
            }
            motionBackgroundDrawable.setColors(backgroundColor, backgroundGradientColor1, backgroundGradientColor2, backgroundGradientColor3);
            backgroundImage.setBackground(motionBackgroundDrawable);
            patternColor = motionBackgroundDrawable.getPatternColor();
            checkColor = 0x2D000000;
        } else if (backgroundGradientColor1 != 0) {
            GradientDrawable gradientDrawable = new GradientDrawable(BackgroundGradientDrawable.getGradientOrientation(backgroundRotation), new int[]{backgroundColor, backgroundGradientColor1});
            backgroundImage.setBackground(gradientDrawable);
            patternColor = checkColor = AndroidUtilities.getPatternColor(AndroidUtilities.getAverageColor(backgroundColor, backgroundGradientColor1));
        } else {
            backgroundImage.setBackgroundColor(backgroundColor);
            patternColor = checkColor = AndroidUtilities.getPatternColor(backgroundColor);
        }
        if (!Theme.hasThemeKey(Theme.key_chat_serviceBackground) || backgroundImage.getBackground() instanceof MotionBackgroundDrawable) {
            Theme.applyChatServiceMessageColor(new int[]{checkColor, checkColor, checkColor, checkColor}, backgroundImage.getBackground());
        } else if (Theme.getCachedWallpaper() instanceof MotionBackgroundDrawable) {
            int c = Theme.getColor(Theme.key_chat_serviceBackground);
            Theme.applyChatServiceMessageColor(new int[]{c, c, c, c}, backgroundImage.getBackground());
        }
        if (backgroundPlayAnimationImageView != null) {
            backgroundPlayAnimationImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
        }
        if (messagesPlayAnimationImageView != null) {
            messagesPlayAnimationImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
        }
        if (backgroundImage != null) {
            backgroundImage.getImageReceiver().setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
            backgroundImage.getImageReceiver().setAlpha(Math.abs(currentIntensity));
            backgroundImage.invalidate();
            if (Theme.getActiveTheme().isDark() && backgroundImage.getBackground() instanceof MotionBackgroundDrawable) {
                if (intensitySeekBar != null) {
                    intensitySeekBar.setTwoSided(true);
                }
                if (currentIntensity < 0) {
                    backgroundImage.getImageReceiver().setGradientBitmap(((MotionBackgroundDrawable) backgroundImage.getBackground()).getBitmap());
                }
            } else {
                backgroundImage.getImageReceiver().setGradientBitmap(null);
                if (intensitySeekBar != null) {
                    intensitySeekBar.setTwoSided(false);
                }
            }
            if (intensitySeekBar != null) {
                intensitySeekBar.setProgress(currentIntensity);
            }
        }
        if (listView2 != null) {
            listView2.invalidateViews();
        }
        if (backgroundButtonsContainer != null) {
            for (int a = 0, N = backgroundButtonsContainer.getChildCount(); a < N; a++) {
                backgroundButtonsContainer.getChildAt(a).invalidate();
            }
        }
        if (messagesButtonsContainer != null) {
            for (int a = 0, N = messagesButtonsContainer.getChildCount(); a < N; a++) {
                messagesButtonsContainer.getChildAt(a).invalidate();
            }
        }
        if (radialProgress != null) {
            radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);
        }
    }

    private void setCurrentImage(boolean setThumb) {
        if (screenType == SCREEN_TYPE_PREVIEW && accent == null) {
            backgroundImage.setBackground(Theme.getCachedWallpaper());
        } else if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
            if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                TLRPC.PhotoSize thumb = setThumb ? FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 100) : null;
                backgroundImage.setImage(ImageLocation.getForDocument(wallPaper.document), imageFilter, ImageLocation.getForDocument(thumb, wallPaper.document), "100_100_b", "jpg", wallPaper.document.size, 1, wallPaper);
            } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                backgroundRotation = wallPaper.gradientRotation;
                setBackgroundColor(wallPaper.color, 0, true, false);
                if (wallPaper.gradientColor1 != 0) {
                    setBackgroundColor(wallPaper.gradientColor1, 1, true, false);
                }
                setBackgroundColor(wallPaper.gradientColor2, 2, true, false);
                setBackgroundColor(wallPaper.gradientColor3, 3, true, false);
                if (selectedPattern != null) {
                    backgroundImage.setImage(ImageLocation.getForDocument(selectedPattern.document), imageFilter, null, null, "jpg", selectedPattern.document.size, 1, selectedPattern);
                } else if (Theme.DEFAULT_BACKGROUND_SLUG.equals(wallPaper.slug)) {
                    int w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                    int h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                    int patternColor;
                    if (Build.VERSION.SDK_INT >= 29) {
                        patternColor = 0x57000000;
                    } else {
                        patternColor = MotionBackgroundDrawable.getPatternColor(wallPaper.color, wallPaper.gradientColor1, wallPaper.gradientColor2, wallPaper.gradientColor3);
                    }
                    backgroundImage.setImageBitmap(SvgHelper.getBitmap(R.raw.default_pattern, w, h, patternColor));
                }
            } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                if (currentWallpaperBitmap != null) {
                    backgroundImage.setImageBitmap(currentWallpaperBitmap);
                } else {
                    WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                    if (wallPaper.originalPath != null) {
                        backgroundImage.setImage(wallPaper.originalPath.getAbsolutePath(), imageFilter, null);
                    } else if (wallPaper.path != null) {
                        backgroundImage.setImage(wallPaper.path.getAbsolutePath(), imageFilter, null);
                    } else if (Theme.THEME_BACKGROUND_SLUG.equals(wallPaper.slug)) {
                        backgroundImage.setImageDrawable(Theme.getThemedWallpaper(false, backgroundImage));
                    } else if (wallPaper.resId != 0) {
                        backgroundImage.setImageResource(wallPaper.resId);
                    }
                }
            } else if (currentWallpaper instanceof MediaController.SearchImage) {
                MediaController.SearchImage wallPaper = (MediaController.SearchImage) currentWallpaper;
                if (wallPaper.photo != null) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, 100);
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                    if (image == thumb) {
                        image = null;
                    }
                    int size = image != null ? image.size : 0;
                    backgroundImage.setImage(ImageLocation.getForPhoto(image, wallPaper.photo), imageFilter, ImageLocation.getForPhoto(thumb, wallPaper.photo), "100_100_b", "jpg", size, 1, wallPaper);
                } else {
                    backgroundImage.setImage(wallPaper.imageUrl, imageFilter, wallPaper.thumbUrl, "100_100_b");
                }
            }
        } else {
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            int defaultBackground = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper);
            int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
            int backgroundColor = backgroundOverrideColor != 0 ? backgroundOverrideColor : defaultBackground;

            int defaultBackgroundGradient1 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to1);
            int backgroundGradientOverrideColor1 = (int) accent.backgroundGradientOverrideColor1;
            int color1;
            if (backgroundGradientOverrideColor1 == 0 && accent.backgroundGradientOverrideColor1 != 0) {
                color1 = 0;
            } else {
                color1 = backgroundGradientOverrideColor1 != 0 ? backgroundGradientOverrideColor1 : defaultBackgroundGradient1;
            }
            int defaultBackgroundGradient2 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
            int backgroundGradientOverrideColor2 = (int) accent.backgroundGradientOverrideColor2;
            int color2;
            if (backgroundGradientOverrideColor2 == 0 && accent.backgroundGradientOverrideColor2 != 0) {
                color2 = 0;
            } else {
                color2 = backgroundGradientOverrideColor2 != 0 ? backgroundGradientOverrideColor2 : defaultBackgroundGradient2;
            }
            int defaultBackgroundGradient3 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to3);
            int backgroundGradientOverrideColor3 = (int) accent.backgroundGradientOverrideColor3;
            int color3;
            if (backgroundGradientOverrideColor3 == 0 && accent.backgroundGradientOverrideColor3 != 0) {
                color3 = 0;
            } else {
                color3 = backgroundGradientOverrideColor3 != 0 ? backgroundGradientOverrideColor3 : defaultBackgroundGradient3;
            }
            if (!TextUtils.isEmpty(accent.patternSlug) && !Theme.hasCustomWallpaper()) {
                Drawable backgroundDrawable;
                if (color2 != 0) {
                    Drawable currentBackground = backgroundImage.getBackground();
                    MotionBackgroundDrawable motionBackgroundDrawable;
                    if (currentBackground instanceof MotionBackgroundDrawable) {
                        motionBackgroundDrawable = (MotionBackgroundDrawable) currentBackground;
                    } else {
                        motionBackgroundDrawable = new MotionBackgroundDrawable();
                        motionBackgroundDrawable.setParentView(backgroundImage);
                        if (rotatePreview) {
                            motionBackgroundDrawable.rotatePreview(false);
                        }
                    }
                    motionBackgroundDrawable.setColors(backgroundColor, color1, color2, color3);
                    backgroundDrawable = motionBackgroundDrawable;
                } else if (color1 != 0) {
                    final BackgroundGradientDrawable.Orientation orientation = BackgroundGradientDrawable.getGradientOrientation(accent.backgroundRotation);
                    final BackgroundGradientDrawable backgroundGradientDrawable = new BackgroundGradientDrawable(orientation, new int[]{backgroundColor, color1});
                    final BackgroundGradientDrawable.Listener listener = new BackgroundGradientDrawable.ListenerAdapter() {
                        @Override
                        public void onSizeReady(int width, int height) {
                            final boolean isOrientationPortrait = AndroidUtilities.displaySize.x <= AndroidUtilities.displaySize.y;
                            final boolean isGradientPortrait = width <= height;
                            if (isOrientationPortrait == isGradientPortrait) {
                                backgroundImage.invalidate();
                            }
                        }
                    };
                    backgroundGradientDisposable = backgroundGradientDrawable.startDithering(BackgroundGradientDrawable.Sizes.ofDeviceScreen(), listener, 100);
                    backgroundDrawable = backgroundGradientDrawable;
                } else {
                    backgroundDrawable = new ColorDrawable(backgroundColor);
                }
                backgroundImage.setBackground(backgroundDrawable);
                if (selectedPattern != null) {
                    backgroundImage.setImage(ImageLocation.getForDocument(selectedPattern.document), imageFilter, null, null, "jpg", selectedPattern.document.size, 1, selectedPattern);
                }
            } else {
                Drawable backgroundDrawable = Theme.getCachedWallpaper();
                if (backgroundDrawable != null) {
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        ((MotionBackgroundDrawable) backgroundDrawable).setParentView(backgroundImage);
                    }
                    backgroundImage.setBackground(backgroundDrawable);
                }
            }
            if (color1 == 0) {
                patternColor = checkColor = AndroidUtilities.getPatternColor(backgroundColor);
            } else {
                if (color2 != 0) {
                    patternColor = MotionBackgroundDrawable.getPatternColor(backgroundColor, color1, color2, color3);
                    checkColor = 0x2D000000;
                } else {
                    patternColor = checkColor = AndroidUtilities.getPatternColor(AndroidUtilities.getAverageColor(backgroundColor, color1));
                }
            }
            if (backgroundImage != null) {
                backgroundImage.getImageReceiver().setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
                backgroundImage.getImageReceiver().setAlpha(Math.abs(currentIntensity));
                backgroundImage.invalidate();
                if (Theme.getActiveTheme().isDark() && backgroundImage.getBackground() instanceof MotionBackgroundDrawable) {
                    if (intensitySeekBar != null) {
                        intensitySeekBar.setTwoSided(true);
                    }
                    if (currentIntensity < 0) {
                        backgroundImage.getImageReceiver().setGradientBitmap(((MotionBackgroundDrawable) backgroundImage.getBackground()).getBitmap());
                    }
                } else {
                    backgroundImage.getImageReceiver().setGradientBitmap(null);
                    if (intensitySeekBar != null) {
                        intensitySeekBar.setTwoSided(false);
                    }
                }
                if (intensitySeekBar != null) {
                    intensitySeekBar.setProgress(currentIntensity);
                }
            }
            if (backgroundCheckBoxView != null) {
                for (int a = 0; a < backgroundCheckBoxView.length; a++) {
                    backgroundCheckBoxView[a].setColor(0, backgroundColor);
                    backgroundCheckBoxView[a].setColor(1, color1);
                    backgroundCheckBoxView[a].setColor(2, color2);
                    backgroundCheckBoxView[a].setColor(3, color3);
                }
            }
            if (backgroundPlayAnimationImageView != null) {
                backgroundPlayAnimationImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
            }
            if (messagesPlayAnimationImageView != null) {
                messagesPlayAnimationImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
            }
            if (backgroundButtonsContainer != null) {
                for (int a = 0, N = backgroundButtonsContainer.getChildCount(); a < N; a++) {
                    backgroundButtonsContainer.getChildAt(a).invalidate();
                }
            }
            if (messagesButtonsContainer != null) {
                for (int a = 0, N = messagesButtonsContainer.getChildCount(); a < N; a++) {
                    messagesButtonsContainer.getChildAt(a).invalidate();
                }
            }
        }
        rotatePreview = false;
    }

    public static class DialogsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private ArrayList<DialogCell.CustomDialog> dialogs;

        public DialogsAdapter(Context context) {
            mContext = context;
            dialogs = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000);
            DialogCell.CustomDialog customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog1", R.string.ThemePreviewDialog1);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage1", R.string.ThemePreviewDialogMessage1);
            customDialog.id = 0;
            customDialog.unread_count = 0;
            customDialog.pinned = true;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = true;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog2", R.string.ThemePreviewDialog2);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage2", R.string.ThemePreviewDialogMessage2);
            customDialog.id = 1;
            customDialog.unread_count = 2;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog3", R.string.ThemePreviewDialog3);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage3", R.string.ThemePreviewDialogMessage3);
            customDialog.id = 2;
            customDialog.unread_count = 3;
            customDialog.pinned = false;
            customDialog.muted = true;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 2;
            customDialog.verified = false;
            customDialog.isMedia = true;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog4", R.string.ThemePreviewDialog4);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage4", R.string.ThemePreviewDialogMessage4);
            customDialog.id = 3;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 2;
            customDialog.date = date - 60 * 60 * 3;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog5", R.string.ThemePreviewDialog5);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage5", R.string.ThemePreviewDialogMessage5);
            customDialog.id = 4;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 1;
            customDialog.date = date - 60 * 60 * 4;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = true;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog6", R.string.ThemePreviewDialog6);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage6", R.string.ThemePreviewDialogMessage6);
            customDialog.id = 5;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 5;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog7", R.string.ThemePreviewDialog7);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage7", R.string.ThemePreviewDialogMessage7);
            customDialog.id = 6;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 6;
            customDialog.verified = true;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog8", R.string.ThemePreviewDialog8);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage8", R.string.ThemePreviewDialogMessage8);
            customDialog.id = 0;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 7;
            customDialog.verified = true;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view;
            if (viewType == 0) {
                view = new DialogCell(null, mContext, false, false);
            } else {
                view = new LoadingCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            if (viewHolder.getItemViewType() == 0) {
                DialogCell cell = (DialogCell) viewHolder.itemView;
                cell.useSeparator = (i != getItemCount() - 1);
                cell.setDialog(dialogs.get(i));
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == dialogs.size()) {
                return 1;
            }
            return 0;
        }
    }

    public class MessagesAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private ArrayList<MessageObject> messages;
        private boolean showSecretMessages = screenType == SCREEN_TYPE_PREVIEW && Utilities.random.nextInt(100) <= 1;

        public MessagesAdapter(Context context) {
            mContext = context;
            messages = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

            TLRPC.Message message;
            MessageObject messageObject;
            if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                message = new TLRPC.TL_message();
                if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                    message.message = LocaleController.getString("BackgroundColorSinglePreviewLine2", R.string.BackgroundColorSinglePreviewLine2);
                } else {
                    message.message = LocaleController.getString("BackgroundPreviewLine2", R.string.BackgroundPreviewLine2);
                }
                message.date = date + 60;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = true;
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = 0;
                messageObject = new MessageObject(currentAccount, message, true, false);
                messageObject.eventId = 1;
                messageObject.resetLayout();
                messages.add(messageObject);

                message = new TLRPC.TL_message();
                if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                    message.message = LocaleController.getString("BackgroundColorSinglePreviewLine1", R.string.BackgroundColorSinglePreviewLine1);
                } else {
                    message.message = LocaleController.getString("BackgroundPreviewLine1", R.string.BackgroundPreviewLine1);
                }
                message.date = date + 60;
                message.dialog_id = 1;
                message.flags = 257 + 8;
                message.from_id = new TLRPC.TL_peerUser();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = false;
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                messageObject = new MessageObject(currentAccount, message, true, false);
                messageObject.eventId = 1;
                messageObject.resetLayout();
                messages.add(messageObject);
            } else if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                message = new TLRPC.TL_message();
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.document = new TLRPC.TL_document();
                message.media.document.mime_type = "audio/mp3";
                message.media.document.file_reference = new byte[0];
                message.media.document.id = Integer.MIN_VALUE;
                message.media.document.size = (int) (1024 * 1024 * 2.5f);
                message.media.document.dc_id = Integer.MIN_VALUE;
                TLRPC.TL_documentAttributeFilename attributeFilename = new TLRPC.TL_documentAttributeFilename();
                attributeFilename.file_name = LocaleController.getString("NewThemePreviewReply2", R.string.NewThemePreviewReply2) + ".mp3";
                message.media.document.attributes.add(attributeFilename);
                message.date = date + 60;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                message.id = 1;
                message.out = true;
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = 0;
                MessageObject replyMessageObject = new MessageObject(UserConfig.selectedAccount, message, true, false);

                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    message = new TLRPC.TL_message();
                    message.message = "this is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text\nthis is very very long text";
                    message.date = date + 960;
                    message.dialog_id = 1;
                    message.flags = 259;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = true;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = 0;
                    MessageObject message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
                    message1.resetLayout();
                    message1.eventId = 1;
                    messages.add(message1);
                }

                message = new TLRPC.TL_message();
                String text = LocaleController.getString("NewThemePreviewLine3", R.string.NewThemePreviewLine3);
                StringBuilder builder = new StringBuilder(text);
                int index1 = text.indexOf('*');
                int index2 = text.lastIndexOf('*');
                if (index1 != -1 && index2 != -1) {
                    builder.replace(index2, index2 + 1, "");
                    builder.replace(index1, index1 + 1, "");
                    TLRPC.TL_messageEntityTextUrl entityUrl = new TLRPC.TL_messageEntityTextUrl();
                    entityUrl.offset = index1;
                    entityUrl.length = index2 - index1 - 1;
                    entityUrl.url = "https://telegram.org";
                    message.entities.add(entityUrl);
                }
                message.message = builder.toString();
                message.date = date + 960;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = true;
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = 0;


                MessageObject message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
                message1.resetLayout();
                message1.eventId = 1;
                messages.add(message1);

                message = new TLRPC.TL_message();
                message.message = LocaleController.getString("NewThemePreviewLine1", R.string.NewThemePreviewLine1);
                message.date = date + 60;
                message.dialog_id = 1;
                message.flags = 257 + 8;
                message.from_id = new TLRPC.TL_peerUser();
                message.id = 1;
                message.reply_to = new TLRPC.TL_messageReplyHeader();
                message.reply_to.reply_to_msg_id = 5;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = false;
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                MessageObject message2 = new MessageObject(UserConfig.selectedAccount, message, true, false);
                message2.customReplyName = LocaleController.getString("NewThemePreviewName", R.string.NewThemePreviewName);
                message1.customReplyName = "Test User";
                message2.eventId = 1;
                message2.resetLayout();
                message2.replyMessageObject = replyMessageObject;
                message1.replyMessageObject = message2;
                messages.add(message2);

                messages.add(replyMessageObject);

                message = new TLRPC.TL_message();
                message.date = date + 120;
                message.dialog_id = 1;
                message.flags = 259;
                message.out = false;
                message.from_id = new TLRPC.TL_peerUser();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.flags |= 3;
                message.media.document = new TLRPC.TL_document();
                message.media.document.mime_type = "audio/ogg";
                message.media.document.file_reference = new byte[0];
                TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                audio.flags = 1028;
                audio.duration = 3;
                audio.voice = true;
                audio.waveform = new byte[]{0, 4, 17, -50, -93, 86, -103, -45, -12, -26, 63, -25, -3, 109, -114, -54, -4, -1,
                        -1, -1, -1, -29, -1, -1, -25, -1, -1, -97, -43, 57, -57, -108, 1, -91, -4, -47, 21, 99, 10, 97, 43,
                        45, 115, -112, -77, 51, -63, 66, 40, 34, -122, -116, 48, -124, 16, 66, -120, 16, 68, 16, 33, 4, 1};
                message.media.document.attributes.add(audio);
                message.out = true;
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = 0;
                messageObject = new MessageObject(currentAccount, message, true, false);
                messageObject.audioProgressSec = 1;
                messageObject.audioProgress = 0.3f;
                messageObject.useCustomPhoto = true;
                messages.add(messageObject);
            } else {
                if (showSecretMessages) {
                    TLRPC.TL_user user1 = new TLRPC.TL_user();
                    user1.id = Integer.MAX_VALUE;
                    user1.first_name = "Me";

                    TLRPC.TL_user user2 = new TLRPC.TL_user();
                    user2.id = Integer.MAX_VALUE - 1;
                    user2.first_name = "Serj";

                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user1);
                    users.add(user2);
                    MessagesController.getInstance(currentAccount).putUsers(users, true);

                    message = new TLRPC.TL_message();
                    message.message = "Guess why Half-Life 3 was never released.";
                    message.date = date + 960;
                    message.dialog_id = -1;
                    message.flags = 259;
                    message.id = Integer.MAX_VALUE - 1;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = false;
                    message.peer_id = new TLRPC.TL_peerChat();
                    message.peer_id.chat_id = 1;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = user2.id;
                    messages.add(new MessageObject(currentAccount, message, true, false));

                    message = new TLRPC.TL_message();
                    message.message = "No.\n" +
                            "And every unnecessary ping of the dev delays the release for 10 days.\n" +
                            "Every request for ETA delays the release for 2 weeks.";
                    message.date = date + 960;
                    message.dialog_id = -1;
                    message.flags = 259;
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = false;
                    message.peer_id = new TLRPC.TL_peerChat();
                    message.peer_id.chat_id = 1;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = user2.id;
                    messages.add(new MessageObject(currentAccount, message, true, false));

                    message = new TLRPC.TL_message();
                    message.message = "Is source code for Android coming anytime soon?";
                    message.date = date + 600;
                    message.dialog_id = -1;
                    message.flags = 259;
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = false;
                    message.peer_id = new TLRPC.TL_peerChat();
                    message.peer_id.chat_id = 1;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = user1.id;
                    messages.add(new MessageObject(currentAccount, message, true, false));
                } else {
                    message = new TLRPC.TL_message();
                    message.message = LocaleController.getString("ThemePreviewLine1", R.string.ThemePreviewLine1);
                    message.date = date + 60;
                    message.dialog_id = 1;
                    message.flags = 259;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = true;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = 0;
                    MessageObject replyMessageObject = new MessageObject(currentAccount, message, true, false);

                    message = new TLRPC.TL_message();
                    message.message = LocaleController.getString("ThemePreviewLine2", R.string.ThemePreviewLine2);
                    message.date = date + 960;
                    message.dialog_id = 1;
                    message.flags = 259;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = true;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = 0;
                    messages.add(new MessageObject(currentAccount, message, true, false));

                    message = new TLRPC.TL_message();
                    message.date = date + 130;
                    message.dialog_id = 1;
                    message.flags = 259;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.id = 5;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = new TLRPC.TL_document();
                    message.media.document.mime_type = "audio/mp4";
                    message.media.document.file_reference = new byte[0];
                    TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                    audio.duration = 243;
                    audio.performer = LocaleController.getString("ThemePreviewSongPerformer", R.string.ThemePreviewSongPerformer);
                    audio.title = LocaleController.getString("ThemePreviewSongTitle", R.string.ThemePreviewSongTitle);
                    message.media.document.attributes.add(audio);
                    message.out = false;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    messages.add(new MessageObject(currentAccount, message, true, false));

                    message = new TLRPC.TL_message();
                    message.message = LocaleController.getString("ThemePreviewLine3", R.string.ThemePreviewLine3);
                    message.date = date + 60;
                    message.dialog_id = 1;
                    message.flags = 257 + 8;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.id = 1;
                    message.reply_to = new TLRPC.TL_messageReplyHeader();
                    message.reply_to.reply_to_msg_id = 5;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.out = false;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    messageObject = new MessageObject(currentAccount, message, true, false);
                    messageObject.customReplyName = LocaleController.getString("ThemePreviewLine3Reply", R.string.ThemePreviewLine3Reply);
                    messageObject.replyMessageObject = replyMessageObject;
                    messages.add(messageObject);

                    message = new TLRPC.TL_message();
                    message.date = date + 120;
                    message.dialog_id = 1;
                    message.flags = 259;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = new TLRPC.TL_document();
                    message.media.document.mime_type = "audio/ogg";
                    message.media.document.file_reference = new byte[0];
                    audio = new TLRPC.TL_documentAttributeAudio();
                    audio.flags = 1028;
                    audio.duration = 3;
                    audio.voice = true;
                    audio.waveform = new byte[]{0, 4, 17, -50, -93, 86, -103, -45, -12, -26, 63, -25, -3, 109, -114, -54, -4, -1,
                            -1, -1, -1, -29, -1, -1, -25, -1, -1, -97, -43, 57, -57, -108, 1, -91, -4, -47, 21, 99, 10, 97, 43,
                            45, 115, -112, -77, 51, -63, 66, 40, 34, -122, -116, 48, -124, 16, 66, -120, 16, 68, 16, 33, 4, 1};
                    message.media.document.attributes.add(audio);
                    message.out = true;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = 0;
                    messageObject = new MessageObject(currentAccount, message, true, false);
                    messageObject.audioProgressSec = 1;
                    messageObject.audioProgress = 0.3f;
                    messageObject.useCustomPhoto = true;
                    messages.add(messageObject);

                    messages.add(replyMessageObject);

                    message = new TLRPC.TL_message();
                    message.date = date + 10;
                    message.dialog_id = 1;
                    message.flags = 257;
                    message.from_id = new TLRPC.TL_peerUser();
                    message.id = 1;
                    message.media = new TLRPC.TL_messageMediaPhoto();
                    message.media.flags |= 3;
                    message.media.photo = new TLRPC.TL_photo();
                    message.media.photo.file_reference = new byte[0];
                    message.media.photo.has_stickers = false;
                    message.media.photo.id = 1;
                    message.media.photo.access_hash = 0;
                    message.media.photo.date = date;
                    TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                    photoSize.size = 0;
                    photoSize.w = 500;
                    photoSize.h = 302;
                    photoSize.type = "s";
                    photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                    message.media.photo.sizes.add(photoSize);
                    message.message = LocaleController.getString("ThemePreviewLine4", R.string.ThemePreviewLine4);
                    message.out = false;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    messageObject = new MessageObject(currentAccount, message, true, false);
                    messageObject.useCustomPhoto = true;
                    messages.add(messageObject);
                }
            }
        }

        private boolean hasButtons() {
            return messagesButtonsContainer != null && screenType == SCREEN_TYPE_ACCENT_COLOR && colorType == 3 && accent.myMessagesGradientAccentColor2 != 0 ||
                backgroundButtonsContainer != null && (screenType == SCREEN_TYPE_CHANGE_BACKGROUND || screenType == SCREEN_TYPE_ACCENT_COLOR && colorType == 2);
        }

        @Override
        public int getItemCount() {
            int count = messages.size();
            if (hasButtons()) {
                count++;
            }
            return count;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view;
            if (viewType == 0) {
                view = new ChatMessageCell(mContext, false, new Theme.ResourcesProvider() {
                    @Override
                    public Integer getColor(String key) {
                        return Theme.getColor(key);
                    }

                    @Override
                    public Drawable getDrawable(String drawableKey) {
                        if (drawableKey.equals(Theme.key_drawable_msgOut)) {
                            return msgOutDrawable;
                        }
                        if (drawableKey.equals(Theme.key_drawable_msgOutSelected)) {
                            return msgOutDrawableSelected;
                        }
                        if (drawableKey.equals(Theme.key_drawable_msgOutMedia)) {
                            return msgOutMediaDrawable;
                        }
                        if (drawableKey.equals(Theme.key_drawable_msgOutMediaSelected)) {
                            return msgOutMediaDrawableSelected;
                        }
                        return Theme.getThemeDrawable(drawableKey);
                    }
                });
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                });
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {

                });
            } else if (viewType == 2) {
                if (backgroundButtonsContainer.getParent() != null) {
                    ((ViewGroup) backgroundButtonsContainer.getParent()).removeView(backgroundButtonsContainer);
                }
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
                    }
                };
                frameLayout.addView(backgroundButtonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 76, Gravity.CENTER));
                view = frameLayout;
            } else {
                if (messagesButtonsContainer.getParent() != null) {
                    ((ViewGroup) messagesButtonsContainer.getParent()).removeView(messagesButtonsContainer);
                }
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
                    }
                };
                frameLayout.addView(messagesButtonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 76, Gravity.CENTER));
                view = frameLayout;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            if (type != 2 && type != 3) {
                if (hasButtons()) {
                    position--;
                }
                MessageObject message = messages.get(position);
                View view = holder.itemView;

                if (view instanceof ChatMessageCell) {
                    ChatMessageCell messageCell = (ChatMessageCell) view;
                    messageCell.isChat = false;
                    int nextType = getItemViewType(position - 1);
                    int prevType = getItemViewType(position + 1);
                    boolean pinnedBotton;
                    boolean pinnedTop;
                    if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                        MessageObject nextMessage = messages.get(position - 1);
                        pinnedBotton = nextMessage.isOutOwner() == message.isOutOwner() && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedBotton = false;
                    }
                    if (prevType == holder.getItemViewType() && position + 1 < messages.size()) {
                        MessageObject prevMessage = messages.get(position + 1);
                        pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedTop = false;
                    }
                    messageCell.isChat = showSecretMessages;
                    messageCell.setFullyDraw(true);
                    messageCell.setMessageObject(message, null, pinnedBotton, pinnedTop);
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell actionCell = (ChatActionCell) view;
                    actionCell.setMessageObject(message);
                    actionCell.setAlpha(1.0f);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (hasButtons()) {
                if (position == 0) {
                    if (colorType == 3) {
                        return 3;
                    } else {
                        return 2;
                    }
                }
                position--;
            }
            if (position >= 0 && position < messages.size()) {
                return messages.get(position).contentType;
            }
            return 4;
        }
    }

    private class PatternsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public PatternsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getItemCount() {
            return patterns != null ? patterns.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PatternCell view = new PatternCell(mContext, maxWallpaperSize, new PatternCell.PatternCellDelegate() {
                @Override
                public TLRPC.TL_wallPaper getSelectedPattern() {
                    return selectedPattern;
                }

                @Override
                public int getCheckColor() {
                    return checkColor;
                }

                @Override
                public int getBackgroundColor() {
                    if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        return backgroundColor;
                    }
                    int defaultBackground = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper);
                    int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
                    return backgroundOverrideColor != 0 ? backgroundOverrideColor : defaultBackground;
                }

                @Override
                public int getBackgroundGradientColor1() {
                    if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        return backgroundGradientColor1;
                    }
                    int defaultBackgroundGradient = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to1);
                    int backgroundGradientOverrideColor = (int) accent.backgroundGradientOverrideColor1;
                    return backgroundGradientOverrideColor != 0 ? backgroundGradientOverrideColor : defaultBackgroundGradient;
                }

                @Override
                public int getBackgroundGradientColor2() {
                    if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        return backgroundGradientColor2;
                    }
                    int defaultBackgroundGradient = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
                    int backgroundGradientOverrideColor = (int) accent.backgroundGradientOverrideColor2;
                    return backgroundGradientOverrideColor != 0 ? backgroundGradientOverrideColor : defaultBackgroundGradient;
                }

                @Override
                public int getBackgroundGradientColor3() {
                    if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        return backgroundGradientColor3;
                    }
                    int defaultBackgroundGradient = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to3);
                    int backgroundGradientOverrideColor = (int) accent.backgroundGradientOverrideColor3;
                    return backgroundGradientOverrideColor != 0 ? backgroundGradientOverrideColor : defaultBackgroundGradient;
                }

                @Override
                public int getBackgroundGradientAngle() {
                    if (screenType == SCREEN_TYPE_CHANGE_BACKGROUND) {
                        return backgroundRotation;
                    }
                    return accent.backgroundRotation;
                }

                @Override
                public float getIntensity() {
                    return currentIntensity;
                }

                @Override
                public int getPatternColor() {
                    return patternColor;
                }
            });
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PatternCell view = (PatternCell) holder.itemView;
            view.setPattern((TLRPC.TL_wallPaper) patterns.get(position));
            view.getImageReceiver().setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
            if (Build.VERSION.SDK_INT >= 29) {
                int color2 = 0;
                if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                    int defaultBackgroundGradient2 = Theme.getDefaultAccentColor(Theme.key_chat_wallpaper_gradient_to2);
                    int backgroundGradientOverrideColor2 = (int) accent.backgroundGradientOverrideColor2;
                    if (backgroundGradientOverrideColor2 == 0 && accent.backgroundGradientOverrideColor2 != 0) {
                        color2 = 0;
                    } else {
                        color2 = backgroundGradientOverrideColor2 != 0 ? backgroundGradientOverrideColor2 : defaultBackgroundGradient2;
                    }
                } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                    color2 = backgroundGradientColor2;
                }
                if (color2 != 0 && currentIntensity >= 0) {
                    backgroundImage.getImageReceiver().setBlendMode(BlendMode.SOFT_LIGHT);
                } else {
                    view.getImageReceiver().setBlendMode(null);
                }
            }
        }
    }

    private List<ThemeDescription> getThemeDescriptionsInternal() {
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = () -> {
            if (dropDownContainer != null) {
                dropDownContainer.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
                dropDownContainer.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
            }
            if (sheetDrawable != null) {
                sheetDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.MULTIPLY));
            }
        };

        List<ThemeDescription> items = new ArrayList<>();
        items.add(new ThemeDescription(page1, ThemeDescription.FLAG_BACKGROUND, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite));
        items.add(new ThemeDescription(viewPager, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));

        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, descriptionDelegate, Theme.key_actionBarDefaultSubmenuBackground));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, descriptionDelegate, Theme.key_actionBarDefaultSubmenuItem));

        items.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        items.add(new ThemeDescription(listView2, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));

        items.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
        items.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
        items.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));

        if (!useDefaultThemeForButtons) {
            items.add(new ThemeDescription(saveButtonsContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            items.add(new ThemeDescription(cancelButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
            items.add(new ThemeDescription(doneButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
        }

        if (colorPicker != null) {
            colorPicker.provideThemeDescriptions(items);
        }

        if (patternLayout != null) {
            for (int a = 0; a < patternLayout.length; a++) {
                items.add(new ThemeDescription(patternLayout[a], 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
                items.add(new ThemeDescription(patternLayout[a], 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
            }

            for (int a = 0; a < patternsButtonsContainer.length; a++) {
                items.add(new ThemeDescription(patternsButtonsContainer[a], 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
                items.add(new ThemeDescription(patternsButtonsContainer[a], 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
            }

            items.add(new ThemeDescription(bottomOverlayChat, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
            items.add(new ThemeDescription(bottomOverlayChat, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
            items.add(new ThemeDescription(bottomOverlayChatText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));

            for (int a = 0; a < patternsSaveButton.length; a++) {
                items.add(new ThemeDescription(patternsSaveButton[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
            }
            for (int a = 0; a < patternsCancelButton.length; a++) {
                items.add(new ThemeDescription(patternsCancelButton[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
            }

            items.add(new ThemeDescription(intensitySeekBar, 0, new Class[]{SeekBarView.class}, new String[]{"innerPaint1"}, null, null, null, Theme.key_player_progressBackground));
            items.add(new ThemeDescription(intensitySeekBar, 0, new Class[]{SeekBarView.class}, new String[]{"outerPaint1"}, null, null, null, Theme.key_player_progress));

            items.add(new ThemeDescription(intensityCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgInDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgInMediaDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{msgOutDrawable, msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{msgOutDrawable, msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient1));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{msgOutDrawable, msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient2));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{msgOutDrawable, msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient3));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgOutDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgOutMediaDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextIn));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextOut));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyLine));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyLine));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyNameText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyNameText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMessageText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMessageText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeSelectedText));
            items.add(new ThemeDescription(listView2, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeSelectedText));
        }

        return items;
    }
}

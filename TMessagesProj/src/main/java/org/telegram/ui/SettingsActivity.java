package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceSingleTag;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.PremiumPreviewFragment.applyNewSpan;
import static org.telegram.ui.PremiumPreviewFragment.applyNewSpanTimed;
import static org.telegram.ui.Stars.StarGiftSheet.replaceUnderstood;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AuthTokensHelper;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SharedPrefsHelper;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Wallet.GramWallet;
import org.telegram.ui.Wallet.WalletActivity;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.HintsController;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.ProfileGooeyView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.TON.TONIntroActivity;
import org.telegram.ui.bots.BotBiometry;
import org.telegram.ui.bots.BotDownloads;
import org.telegram.ui.QrShare.QrShareActivity;
import org.telegram.ui.bots.BotLocation;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.bots.SetupEmojiStatusSheet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import java.util.HashSet;

public class SettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate, MainTabsActivity.TabFragmentDelegate, FactorAnimator.Target {

    private static final int ANIMATOR_ID_SEARCH_PAGE_VISIBLE = 0;

    private final BoolAnimator animatorSearchPageVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_PAGE_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    private SizeNotifierFrameLayout contentView;
    private UniversalRecyclerView listView;

    private ActionBarMenuItem searchItem, otherItem;
    private String query;
    private ProfileActivity.SearchAdapter search;

    private ImageUpdater imageUpdater;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private TLRPC.FileLocation avatar;
    private TLRPC.FileLocation avatarBig;
    private ImageLocation uploadingImageLocation;

    private FrameLayout topView;
    private FrameLayout avatarContainer;
    private FrameLayout avatarTouchView;
    private ProfileGooeyView avatarGooey;
    private AvatarDrawable avatarDrawable;
    private BackupImageView avatarView;
    private FrameLayout cameraButton;
    private FrameLayout cameraBackground;
    private ImageView cameraImageView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView versionView;
    public boolean hasMainTabs;

    private View navigationBar;
    private View actionBarFadeView;

    private int versionViewPressCount = 0;

    public SettingsActivity() {
        this(null);
    }

    public SettingsActivity(Bundle args) {
        super(args);
    }

    private NotificationCenter.ObserversGroup observersGroup;

    @Override
    public boolean onFragmentCreate() {
        if (observersGroup != null) {
            observersGroup.removeAllObservers();
            observersGroup = null;
        }

        observersGroup = NotificationCenter.getGlobalInstance()
            .createObserversGroup(this)
            .add(NotificationCenter.updateInterfaces)
            .add(NotificationCenter.starBalanceUpdated)
            .add(NotificationCenter.newSuggestionsAvailable);

        if (arguments != null) {
            hasMainTabs = arguments.getBoolean("hasMainTabs", false);
        }

        additionNavigationBarHeight = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        return super.onFragmentCreate();
    }

    private boolean ignoreClearViews;
    @Override
    public void clearViews() {
        if (ignoreClearViews) return;
        super.clearViews();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                // the place of the avatar in the list also changes without scroll: item animations, adapter updates
                updateAvatarHide();
                super.dispatchDraw(canvas);
                glassEngine.invalidate();
                if (!hasMainTabs) {
                    AndroidUtilities.drawNavigationBarProtection(canvas, this, getThemedColor(Theme.key_windowBackgroundWhite), navigationBarHeight);
                }
            }

            @Override
            public void updateColors() {
                try {
                    super.updateColors();
                    SettingsActivity.this.updateColors();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        };

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        leftButtonIsBack = true; // the view may be rebuilt (theme, language): checkUi_menuItems swaps it to QR again
        simpleActionBar.setTitle(getString(R.string.Settings));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (hasMainTabs) {
                        // no back on the main tab: the left button opens the QR code (with the search open, the action bar closes it instead)
                        final Bundle args = new Bundle();
                        args.putLong("user_id", getUserConfig().getClientUserId());
                        args.putBoolean(QrShareActivity.ARG_IMMEDIATE, true);
                        presentFragment(new QrShareActivity(args));
                    } else {
                        finishFragment();
                    }
                } else if (id == 2) {
                    presentSettingFragment(new LogoutActivity());
                }
            }
        });
        simpleActionBar.setBackground(null);

        final SimpleActionBarMenu menu = simpleActionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.outline_header_search, resourceProvider).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

            @Override
            public void onSearchCollapse() {
                animatorSearchPageVisible.setValue(false, true);
                updateActionBarVisible();
                listView.adapter.update(false);
            }

            @Override
            public void onSearchExpand() {
                animatorSearchPageVisible.setValue(true, true);
                search.search(query = "");
                updateActionBarVisible();
                listView.adapter.update(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                search.search(query = editText.getText().toString());
            }
        });
        searchItem.setSearchFieldHint(getString(R.string.Search));

        otherItem = menu.addItem(1, R.drawable.ic_ab_other);
        otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        otherItem.addSubItem(2, R.drawable.msg_leave, getString(R.string.LogOut));

        search = new ProfileActivity.SearchAdapter(this, context) {
            @Override
            public void notifyDataSetChanged() {
                listView.adapter.update(true);
            }
        };
        search.loadFaqWebPage();

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        BaseFragmentSimpleGlass.setListViewParams(listView, 0, 0, dp(12), additionNavigationBarHeight);
        listView.setClipToPadding(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateActionBarVisible();
                updateAvatarHide();
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    snapAvatarHide();
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarFadeView = new View(context);
        actionBarFadeView.setBackground(getBaseSimpleGlass().createActionBarFadeDrawable(actionBarFadeView));
        contentView.addView(actionBarFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        contentView.addView(simpleActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
        imageUpdater.setOpenWithFrontfaceCamera(true);
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);

        topView = new FrameLayout(context);

        // the avatar is drawn above the list, where it can hide into the camera cutout as in the profile;
        // this view in the list takes its touches, its place and its press scale
        avatarTouchView = new FrameLayout(context) {
            @Override
            public void setScaleX(float scaleX) {
                super.setScaleX(scaleX);
                updateAvatarHide();
            }
        };
        topView.addView(avatarTouchView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 23 - 12, 0, 0));
        avatarTouchView.setOnClickListener(v -> {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            if (user == null) {
                user = UserConfig.getInstance(currentAccount).getCurrentUser();
            }
            if (user == null) {
                return;
            }
            imageUpdater.openMenu(user.photo != null && user.photo.photo_big != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty), () -> {
                MessagesController.getInstance(currentAccount).deleteUserPhoto(null);
            }, dialog -> {

            }, 0);
        });
        ScaleStateListAnimator.apply(avatarTouchView);
        topView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateAvatarHide());

        avatarGooey = new ProfileGooeyView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }
        };
        avatarContainer = new FrameLayout(context);
        avatarContainer.setPivotX(0);
        avatarContainer.setPivotY(0);
        avatarGooey.addView(avatarContainer, LayoutHelper.createFrame(90, 90, Gravity.TOP | Gravity.LEFT));
        contentView.addView(avatarGooey, contentView.indexOfChild(listView) + 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        avatarDrawable = new AvatarDrawable();
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(90));
        avatarContainer.addView(avatarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        avatarProgressView = new RadialProgressView(context) {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                paint.setColor(0x55000000);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarView != null && avatarView.getImageReceiver().hasNotThumb()) {
                    paint.setAlpha((int) (0x55 * avatarView.getImageReceiver().getCurrentAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                }
                super.onDraw(canvas);
            }
        };
        avatarProgressView.setSize(AndroidUtilities.dp(26));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarProgressView.setNoProgress(false);
        avatarContainer.addView(avatarProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        showAvatarProgress(false, false);

        cameraButton = new FrameLayout(context);
        ThemeBind.fillBackground(cameraButton, Theme.createCircleDrawable(dp(32), getThemedColor(Theme.key_windowBackgroundGray)), ThemeBind.of(Theme.key_windowBackgroundGray), getResourceProvider());
        cameraButton.setPadding(dp(2), dp(2), dp(2), dp(2));
        cameraBackground = new FrameLayout(context);
        ThemeBind.fillBackground(cameraBackground, Theme.createCircleDrawable(dp(30), getThemedColor(Theme.key_featuredStickers_addButton)), ThemeBind.of(Theme.key_featuredStickers_addButton), getResourceProvider());
        cameraImageView = new ImageView(context);
        cameraImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cameraImageView.setImageResource(R.drawable.filled_premium_camera);
        cameraBackground.addView(cameraImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        cameraButton.addView(cameraBackground, LayoutHelper.createFrame(30, 30));
        // over the avatar, so it scales and hides with it; its touches go to the place of the avatar in the list
        // over the avatar layer, not in it: the goo is made of the avatar only. It follows the avatar
        // (at its bottom right, past its edge), its touches go to the place of the avatar in the list
        cameraButton.setPivotX(0);
        cameraButton.setPivotY(0);
        contentView.addView(cameraButton, contentView.indexOfChild(avatarGooey) + 1, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.TOP));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        topView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 16, 138.333f - 12, 16, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        topView.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 168 - 12, 0, 0));

        versionView = new TextView(context);
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        ThemeBind.textColor(versionView, Theme.key_windowBackgroundWhiteGrayText4, getResourceProvider());
        versionView.setPadding(dp(21), dp(10), dp(21), dp(10));
        versionView.setGravity(Gravity.CENTER);
        ThemeBind.selectorBackground(versionView, Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL), null, ThemeBind.of(Theme.key_listSelector), getResourceProvider());
        versionView.setOnClickListener(v -> {
            versionViewPressCount++;
            if (versionViewPressCount < 2 && !BuildVars.DEBUG_PRIVATE_VERSION) {
                try {
                    Toast.makeText(getParentActivity(), getString(R.string.DebugMenuLongPress), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return;
            }
            presentFragment(new DebugSettingsActivity());
        });

        navigationBar = new View(context);
//        fragmentView.addView(navigationBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        updateActionBarVisible(true, false);
        listView.adapter.update(false);
        setInfo();
        updateColors();
        checkUi_menuItems();

        simpleActionBar.setBackground(null);
        updateActionBarFadeHeight(mSystemInsets.top);
        updateActionBarVisible(true, false);

        ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onApplyWindowInsets);
        return fragmentView = contentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (observersGroup != null) {
            observersGroup.removeAllObservers();
            observersGroup = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starBalanceUpdated) {
            setInfo();
            if (listView != null) {
                listView.adapter.update(true);
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            setInfo();
        } else if (id == NotificationCenter.newSuggestionsAvailable) {
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    public void setInfo() {
        setInfo(getUserConfig().getCurrentUser());
    }

    public void setInfo(TLRPC.User user) {
        if (avatarView == null) return;
        if (avatarUploadingRequest != -1) return;

        avatarDrawable.setInfo(user);
        avatarView.setForUserOrChat(user, avatarDrawable);
        titleView.setText(UserObject.getUserName(user));
        final StringBuilder sb = new StringBuilder();
        if (user != null) {
            sb.append(PhoneFormat.getInstance().format("+" + user.phone));
        }
        final String username = UserObject.getPublicUsername(user);
        if (username != null) {
            sb.append(" • @").append(username);
        }
        subtitleView.setText(sb);

        versionView.setText(getVersionName());
    }


    private int lastNavigationBarColor;

    public void updateColors() {
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        searchItem.updateColor();

        final int navigationBarColor = getThemedColor(Theme.key_windowBackgroundWhite);
        if (navigationBar.getBackground() == null || navigationBarColor != lastNavigationBarColor) {
            lastNavigationBarColor = navigationBarColor;
            navigationBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { Theme.multAlpha(navigationBarColor, 0.0f), navigationBarColor }));
        }

        listView.invalidate();
    }

    private boolean actionBarVisible;
    private ValueAnimator actionBarVisibleAnimator;
    private float top() {
        if (listView.getChildCount() > 0) {
            final View firstChild = listView.getChildAt(0);
            if (listView.getChildAdapterPosition(firstChild) > 0)
                return 0;
            return Math.max(0, firstChild.getY() + firstChild.getHeight());
        }
        return 0;
    }
    private void updateActionBarVisible() {
        updateActionBarVisible(false, true);
    }
    private void updateActionBarVisible(boolean force, boolean animated) {
        final boolean visible;
        if (searchItem.isSearchFieldVisible2()) {
            visible = true;
        } else if (listView.getChildCount() > 0) {
            final View firstChild = listView.getChildAt(0);
            visible = (
                listView.getChildAdapterPosition(firstChild) > 0 ||
                firstChild.getY() + firstChild.getHeight() < simpleActionBar.getHeight()
            );
        } else {
            visible = false;
        }
        if (actionBarVisible == visible && !force) return;

        actionBarVisible = visible;
        if (actionBarVisibleAnimator != null) {
            actionBarVisibleAnimator.cancel();
            actionBarVisibleAnimator = null;
        }
        if (!animated) {
            setActionBarVisibilityFactor(visible ? 1f : 0f);
        } else {
            actionBarVisibleAnimator = ValueAnimator.ofFloat(simpleActionBar.getTitlesAlpha(), visible ? 1.0f : 0.0f);
            actionBarVisibleAnimator.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                setActionBarVisibilityFactor(t);
            });
            actionBarVisibleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            actionBarVisibleAnimator.setDuration(420);
            actionBarVisibleAnimator.start();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private void setActionBarVisibilityFactor(float factor) {
        simpleActionBar.setTitlesAlpha(factor);
        if (actionBarFadeView != null) {
            actionBarFadeView.setAlpha(factor);
        }
    }

    /**
     * Moves the avatar after its place in the list and hides it into the camera cutout while the list
     * scrolls it up, as the profile does with its collapsing header.
     */
    private void updateAvatarHide() {
        if (avatarGooey == null || avatarContainer == null) {
            return;
        }
        float y = avatarTouchView.getHeight() / 2f;
        float alpha = 1f;
        View view = avatarTouchView;
        while (view != null && view != fragmentView && view.getParent() instanceof View) {
            y += view.getY();
            alpha *= view.getAlpha();
            view = (View) view.getParent();
        }
        if (view != fragmentView || !avatarTouchView.isAttachedToWindow() || avatarTouchView.getHeight() == 0) {
            if (avatarHideShown != 0) {
                avatarHideShown = 0;
                avatarGooey.setHideProgress(1f);
                cameraButton.setAlpha(0f);
            }
            return;
        }
        // the center of the avatar: with the list at its top, and in the cutout
        final float shownY = y - getListItemTop(topView) + listView.getPaddingTop();
        final float hiddenY = avatarGooey.getHiddenAvatarCenterY(-dp(16));
        // the avatar goes with the list, it must not run into the name below it
        final float shown = MathUtils.clamp((y - hiddenY) / Math.max(1, shownY - hiddenY), 0f, 1f);
        final float centerY = Math.max(y, hiddenY);
        avatarListY = y;
        avatarShownY = shownY;
        avatarHiddenY = hiddenY;

        final float size = dp(90);
        final float scale = AndroidUtilities.lerp(dp(avatarGooey.getHiddenAvatarSizeDp()), size, shown) / size * avatarTouchView.getScaleX();
        final float translationX = avatarGooey.getWidth() / 2f - size * scale / 2f;
        if (centerY == avatarHideCenterY && scale == avatarHideScale && shown == avatarHideShown && alpha == avatarGooey.getAlpha() && translationX == avatarContainer.getTranslationX()) {
            return; // called on each draw: no invalidation without changes
        }
        avatarHideCenterY = centerY;
        avatarHideScale = scale;
        avatarHideShown = shown;
        avatarGooey.setAlpha(alpha);
        avatarContainer.setScaleX(scale);
        avatarContainer.setScaleY(scale);
        avatarContainer.setTranslationX(translationX);
        avatarContainer.setTranslationY(centerY - size * scale / 2f);
        cameraButton.setAlpha(alpha * MathUtils.clamp(AndroidUtilities.ilerp(shown, 0.75f, 1f), 0f, 1f));
        cameraButton.setScaleX(scale);
        cameraButton.setScaleY(scale);
        cameraButton.setTranslationX(translationX + dp(60) * scale);
        cameraButton.setTranslationY(centerY - size * scale / 2f + dp(60) * scale);
        avatarGooey.setHideProgress(1f - shown);
    }

    private float avatarHideCenterY, avatarHideScale, avatarHideShown = -1;
    /** The center of the avatar in the list, at the top of the list and in the cutout, from the last update. */
    private float avatarListY, avatarShownY, avatarHiddenY;

    /** The list does not stop with the avatar half hidden: it scrolls on to the shown or the hidden avatar. */
    private void snapAvatarHide() {
        if (avatarGooey == null || searchItem != null && searchItem.isSearchFieldVisible2() || avatarHideShown <= 0 || avatarHideShown >= 1) {
            return;
        }
        if (avatarHideShown >= 0.5f) {
            listView.smoothScrollBy(0, (int) -(avatarShownY - avatarListY), CubicBezierInterpolator.EASE_OUT_QUINT);
        } else {
            listView.smoothScrollBy(0, (int) Math.ceil(avatarListY - avatarHiddenY), CubicBezierInterpolator.EASE_OUT_QUINT);
        }
    }

    /** Top of the list item holding {@code view}, in the list. */
    private float getListItemTop(View view) {
        float top = 0;
        while (view != null && view != listView && view.getParent() instanceof View) {
            top += view.getY();
            view = (View) view.getParent();
        }
        return top;
    }

    private void updateActionBarFadeHeight(int insetTop) {
        if (actionBarFadeView == null) return;
        int height = insetTop + ActionBar.getCurrentActionBarHeight();
        ViewGroup.LayoutParams params = actionBarFadeView.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            actionBarFadeView.setLayoutParams(params);
        }
    }

    private ArrayList<Integer> accountNumbers = new ArrayList<>();
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (searchItem.isSearchFieldVisible2()) {
            items.add(UItem.asSpace(ActionBar.getCurrentActionBarHeight()));
            search.fillItems(items);
            return;
        }

        items.add(UItem.asCustomShadow(topView, 200 - 12));

        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && currentAccount != a) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        final Set<String> suggestions = getMessagesController().pendingSuggestions;
        if (suggestions.contains("PREMIUM_GRACE")) {
            items.add(SuggestionCell.Factory.of(
                getString(R.string.GraceSuggestionTitle),
                getString(R.string.GraceSuggestionMessage),
                null, null,
                getString(R.string.GraceSuggestionButton), v -> {
                    Browser.openUrl(getContext(), getMessagesController().premiumManageSubscriptionUrl);
                    getMessagesController().removeSuggestion(0, "PREMIUM_GRACE");
                }
            ));
            items.add(UItem.asShadow(null));
        } else if (suggestions.contains("VALIDATE_PHONE_NUMBER") && getUserConfig().getCurrentUser() != null) {
            items.add(SuggestionCell.Factory.of(
                formatString(R.string.CheckPhoneNumber, PhoneFormat.getInstance().format("+" + getUserConfig().getCurrentUser().phone)),
                replaceSingleTag(getString(R.string.CheckPhoneNumberInfo), () -> {
                    Browser.openUrl(getContext(), getString(R.string.CheckPhoneNumberLearnMoreUrl));
                }),
                getString(R.string.CheckPhoneNumberNo), v -> {
                    presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));
                },
                replaceUnderstood(getString(R.string.CheckPhoneNumberYes2)), v -> {
                    getMessagesController().removeSuggestion(0, "VALIDATE_PHONE_NUMBER");
                }
            ));
            items.add(UItem.asShadow(null));
        } else if (suggestions.contains("VALIDATE_PASSWORD")) {
            items.add(SuggestionCell.Factory.of(
                getString(R.string.YourPasswordHeader),
                getString(R.string.YourPasswordRemember),
                getString(R.string.YourPasswordRememberNo), v -> {
                    presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_VERIFY, null));
                },
                getString(R.string.YourPasswordRememberYes), v -> {
                    getMessagesController().removeSuggestion(0, "VALIDATE_PASSWORD");
                }
            ));
            items.add(UItem.asShadow(null));
        }

        if (accountNumbers.size() > 0) {
            items.add(UItem.asHeader(getString(R.string.SettingsAccounts)));
            for (int i = 0; i < accountNumbers.size(); ++i) {
                items.add(AccountCell.Factory.of(i, accountNumbers.get(i)));
            }
            items.add(UItem.asShadow(null));
        }

        final boolean walletAbove = getConnectionsManager().getCurrentTime() - 1791538462 < 4 * 7 * 24 * 60 * 60;
        if (walletAbove && getMessagesController().config.walletAvailable.get()) {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            items.add(SettingCell.Factory.of(25, 0, 0, Theme.isCurrentThemeDark() ? R.drawable.settings_wallet_dark : R.drawable.settings_wallet, TextCell.applyNewSpanTimed(getString(R.string.WalletAttachMoney), 1790679020), null, wallet.getBalance() > 0 ? GramWallet.formatGrams(wallet.getBalance()) : null));
            items.add(UItem.asShadow(null));
        }

        items.add(SettingCell.Factory.of(1, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.settings_account, getString(R.string.SettingsAccount), getString(R.string.SettingsAccountInfo)));
        items.add(SettingCell.Factory.of(2, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.settings_chat, getString(R.string.SettingsChat), getString(R.string.SettingsChatInfo)));
        items.add(SettingCell.Factory.of(3, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.settings_privacy, getString(R.string.SettingsPrivacySecurity), getString(R.string.SettingsPrivacySecurityInfo)));
        items.add(SettingCell.Factory.of(5, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.settings_sounds, getString(R.string.SettingsNotifications), getString(R.string.SettingsNotificationsInfo)));
        items.add(SettingCell.Factory.of(6, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.settings_data, getString(R.string.SettingsData), getString(R.string.SettingsDataInfo)));
        items.add(SettingCell.Factory.of(7, IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom, R.drawable.settings_folders, getString(R.string.SettingsFolders), getString(R.string.SettingsFoldersInfo)));
        items.add(SettingCell.Factory.of(8, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.settings_devices, getString(R.string.SettingsDevices), getString(R.string.SettingsDevicesInfo)));
        items.add(SettingCell.Factory.of(9, IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom, R.drawable.settings_power, getString(R.string.SettingsPowerSaving), getString(R.string.SettingsPowerSavingInfo)));
        items.add(SettingCell.Factory.of(10, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_language, getString(R.string.SettingsLanguage), LocaleController.getCurrentLanguageName()));

        items.add(UItem.asShadow(null));
        if (!walletAbove && getMessagesController().config.walletAvailable.get()) {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            items.add(SettingCell.Factory.of(25, 0, 0, Theme.isCurrentThemeDark() ? R.drawable.settings_wallet_dark : R.drawable.settings_wallet, TextCell.applyNewSpanTimed(getString(R.string.WalletAttachMoney), 1790679020), null, wallet.getBalance() > 0 ? GramWallet.formatGrams(wallet.getBalance()) : null));
            items.add(UItem.asShadow(null));
        }

        if (!getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(11, 0xFFB659FF, 0xFF617CFF, R.drawable.settings_premium, getString(R.string.TelegramPremium)));
        }
        if (getMessagesController().starsPurchaseAvailable()) {
            StarsController c = StarsController.getInstance(currentAccount);
            long balance = c.getBalance().amount;
            items.add(SettingCell.Factory.of(12, 0xFFEFA612, 0xFFE77512, R.drawable.settings_stars, getString(R.string.TelegramStars), null, c.balanceAvailable() && balance > 0 ? StarsIntroActivity.formatStarsAmount(c.getBalance(), 0.85f, ' ') : ""));
        }
        StarsController.getInstance(currentAccount, true).getBalance();
        if (ApplicationLoader.isBetaBuild() || ApplicationLoader.isStandaloneBuild() || ApplicationLoader.isHuaweiStoreBuild() || (StarsController.getInstance(currentAccount, true).balanceAvailable() && (StarsController.getInstance(currentAccount, true).hasTransactions() || StarsController.getInstance(currentAccount, true).getBalance().positive()))) {
            final StarsController c = StarsController.getTonInstance(currentAccount);
            final long balance = c.getBalance().amount;
            items.add(SettingCell.Factory.of(13, 0xFF1BA4ED, 0xFF1488E1, R.drawable.filled_gram_earnings, getString(R.string.GramEarnings), null, c.balanceAvailable() && balance > 0 ? StarsIntroActivity.formatStarsAmount(c.getBalance(), 0.85f, ' ') : ""));
        }

        if (!getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(15, 0xFFF45255, 0xFFDF3955, R.drawable.settings_business, getString(R.string.TelegramBusiness)));
        }
        if (!getMessagesController().premiumPurchaseBlocked()) {
            items.add(SettingCell.Factory.of(16, 0xFFF38B31, 0xFFE26314, R.drawable.settings_gift, getString(R.string.SendAGift)));
        }
        if (items.get(items.size() - 1).viewType != UniversalAdapter.VIEW_TYPE_SHADOW)
            items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SettingsHelp)));
        items.add(SettingCell.Factory.of(17, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.settings_ask, getString(R.string.AskAQuestion)));
        items.add(SettingCell.Factory.of(18, IconBackgroundColors.BLUE_LIGHT.top, IconBackgroundColors.BLUE_LIGHT.bottom, R.drawable.settings_faq, getString(R.string.TelegramFAQ)));
        items.add(SettingCell.Factory.of(23, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_features, getString(R.string.TelegramFeatures)));
        items.add(SettingCell.Factory.of(19, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.settings_policy, getString(R.string.PrivacyPolicy)));

        if (SharedSettings.experimentalSettingsAllowed.get()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.SettingsExperimental)));
            items.add(SettingCell.Factory.of(24, 0xFFF45255, 0xFFDF3955, 0, getString(R.string.RoundVideoSettings)));
            items.add(SettingCell.Factory.of(26, 0xFF55CA47, 0xFF27B434, 0, getString(R.string.AudioRecorderSettings)));
            items.add(SettingCell.Factory.of(27, 0xFF3D8BEB, 0xFF2A6FD9, 0, getString(R.string.HeaderSettings)));
        }

        if (BuildVars.LOGS_ENABLED || BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.SettingsDebug)));
            items.add(SettingCell.Factory.of(20, 0xFF55CA47, 0xFF27B434, 0, getString(R.string.DebugSendLogs)));
            items.add(SettingCell.Factory.of(21, 0xFF55CA47, 0xFF27B434, 0, getString(R.string.DebugSendLastLogs)));
            items.add(SettingCell.Factory.of(22, 0xFFF45255, 0xFFDF3955, 0, getString(R.string.DebugClearLogs)));
        }

        items.add(UItem.asCustomShadow(versionView));
    }

    private void presentSettingFragment(BaseFragment fragment) {
        if (AndroidUtilities.isTablet() && LaunchActivity.instance != null && LaunchActivity.instance.getRightActionBarLayout() != null) {
            final INavigationLayout layout = LaunchActivity.instance.getRightActionBarLayout();
            if (!layout.getFragmentStack().isEmpty()) {
                for (int a = 0; a < layout.getFragmentStack().size() - 1; a++) {
                    layout.removeFragmentFromStack(layout.getFragmentStack().get(0));
                    a--;
                }
                layout.closeLastFragment(false);
            }
            layout.presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true).forceRightLayout());
        } else {
            presentFragment(fragment);
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.TL_attachMenuBot) {
            TLRPC.TL_attachMenuBot attachMenuBot = (TLRPC.TL_attachMenuBot) item.object;
            if (attachMenuBot.inactive || attachMenuBot.side_menu_disclaimer_needed) {
                WebAppDisclaimerAlert.show(getContext(), (allowSendMessage) -> {
                    TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                    botRequest.bot = MessagesController.getInstance(currentAccount).getInputUser(attachMenuBot.bot_id);
                    botRequest.enabled = true;
                    botRequest.write_allowed = true;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        attachMenuBot.inactive = attachMenuBot.side_menu_disclaimer_needed = false;
                        LaunchActivity.showAttachMenuBot(LaunchActivity.instance, currentAccount, attachMenuBot, null, true);
                        MediaDataController.getInstance(currentAccount).updateAttachMenuBotsInCache();
                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                }, null, null);
            } else {
                LaunchActivity.showAttachMenuBot(LaunchActivity.instance, currentAccount, attachMenuBot, null, true);
            }
            return;
        }
        if (item.instanceOf(AccountCell.Factory.class)) {
            final int account = item.intValue;
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.switchToAccount(account, true);
            }
            return;
        } else if (item.instanceOf(SettingsSearchCell.Factory.class)) {
            if (item.object instanceof ProfileActivity.SearchAdapter.SearchResult) {
                final ProfileActivity.SearchAdapter.SearchResult r = (ProfileActivity.SearchAdapter.SearchResult) item.object;
                r.open(getParentLayout());
            } else if (item.object instanceof MessagesController.FaqSearchResult) {
                final MessagesController.FaqSearchResult r = (MessagesController.FaqSearchResult) item.object;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.openArticle, search.faqWebPage, r.url);
            }
            if (item.object != null) {
                search.addRecent(item.object);
            }
            return;
        }
        switch (item.id) {
            case 1:
                presentSettingFragment(new UserInfoActivity());
                break;
            case 2:
                presentSettingFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            case 3:
                presentSettingFragment(new PrivacySettingsActivity());
                break;
            case 5:
                presentSettingFragment(new NotificationsSettingsActivity());
                break;
            case 6:
                presentSettingFragment(new DataSettingsActivity());
                break;
            case 7:
                presentSettingFragment(new FiltersSetupActivity());
                break;
            case 8:
                presentSettingFragment(new SessionsActivity(0));
                break;
            case 9:
                presentSettingFragment(new LiteModeSettingsActivity());
                break;
            case 10:
                presentSettingFragment(new LanguageSelectActivity());
                break;

            case 11:
                presentSettingFragment(new PremiumPreviewFragment("settings"));
                break;
            case 12:
                presentSettingFragment(new StarsIntroActivity());
                break;
            case 13:
                presentSettingFragment(new TONIntroActivity());
                break;
            case 25:
                presentSettingFragment(new WalletActivity());
                break;
            case 15:
                presentSettingFragment(new PremiumPreviewFragment(PremiumPreviewFragment.FEATURES_BUSINESS, "settings"));
                break;
            case 16:
                UserSelectorBottomSheet.open(0, BirthdayController.getInstance(UserConfig.selectedAccount).getState());
                break;

            case 17:
                showDialog(AlertsCreator.createSupportAlert(this, resourceProvider));
                break;
            case 18:
                Browser.openUrl(getParentActivity(), LocaleController.getString(R.string.TelegramFaqUrl));
                break;
            case 19:
                Browser.openUrl(getParentActivity(), LocaleController.getString(R.string.PrivacyPolicyUrl));
                break;

            case 20:
                ProfileActivity.sendLogs(getParentActivity(), false);
                break;
            case 21:
                ProfileActivity.sendLogs(getParentActivity(), true);
                break;
            case 22:
                FileLog.cleanupLogs();
                break;
            case 23: {
                if (MessagesController.getInstance(currentAccount).isFrozen()) {
                    AccountFrozenAlert.show(currentAccount);
                } else {
                    Browser.openUrl(getContext(), LocaleController.getString(R.string.TelegramFeaturesUrl));
                }
                break;
            }
            case 24: {
                presentFragment(new RoundVideoSettingsActivity());
                break;
            }
            case 26: {
                presentFragment(new AudioRecorderSettingsActivity());
                break;
            }
            case 27: {
                presentFragment(new HeaderSettingsActivity());
                break;
            }
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.TL_attachMenuBot) {
            TLRPC.TL_attachMenuBot attachMenuBot = (TLRPC.TL_attachMenuBot) item.object;
            BotWebViewSheet.deleteBot(currentAccount, attachMenuBot.bot_id, () -> listView.adapter.update(true));
            return true;
        }

        if (item.instanceOf(SettingsSearchCell.Factory.class)) {
            String link = null;
            if (item.object instanceof ProfileActivity.SearchAdapter.SearchResult) {
                final ProfileActivity.SearchAdapter.SearchResult r = (ProfileActivity.SearchAdapter.SearchResult) item.object;
                link = r.link;
            } else if (item.object instanceof MessagesController.FaqSearchResult) {
                final MessagesController.FaqSearchResult r = (MessagesController.FaqSearchResult) item.object;
                link = r.url;
            }
            if (!TextUtils.isEmpty(link)) {
                final String finalLink = link;
                ItemOptions.makeOptions(this, view)
                    .add(R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
                        AndroidUtilities.addToClipboard(finalLink);
                        BulletinFactory.of(this).createCopyLinkBulletin().show();
                    })
                    .setScrimViewBackground(listView.getClipBackground(view))
                    .show();
                return true;
            }
        }
        return false;
    }

    public String getVersionName() {
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            int code = pInfo.versionCode / 10;
            String abi = "";
            switch (pInfo.versionCode % 10) {
                case 1:
                case 2:
                    abi = "store bundled " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                    break;
                default:
                case 9:
                    if (ApplicationLoader.isStandaloneBuild()) {
                        abi = "direct " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                    } else {
                        abi = "universal " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                    }
                    break;
            }
            return formatString(R.string.TelegramVersion, String.format(Locale.US, "v%s (%d)\n%s", pInfo.versionName, code, abi));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    private int navigationBarHeight;
    private int additionNavigationBarHeight;

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        navigationBarHeight = systemInsets.bottom;
        updateActionBarFadeHeight(systemInsets.top);
        BaseFragmentSimpleGlass.setListViewParams(listView, systemInsets.top, systemInsets.bottom, dp(12), additionNavigationBarHeight);
        return WindowInsetsCompat.CONSUMED;
    }

    public static class AccountCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private AvatarDrawable avatarDrawable;
        private BackupImageView avatarView;
        private SimpleTextView textView;
        private TextView counterView;
        private ImageView arrowView;

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botDrawable;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatusDrawable;

        public AccountCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setOrientation(HORIZONTAL);

            avatarDrawable = new AvatarDrawable();
            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(14));

            textView = new SimpleTextView(context);
            textView.setTextSize(15);
            textView.setTypeface(AndroidUtilities.bold());
            ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);

            botDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(textView, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS);
            emojiStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(textView, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS);
            textView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    botDrawable.attach();
                    emojiStatusDrawable.attach();
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    botDrawable.detach();
                    emojiStatusDrawable.detach();
                }
            });

            counterView = new TextView(context);
            counterView.setPadding(dp(6.66f), 0, dp(6.66f), 0);
            counterView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            counterView.setTypeface(AndroidUtilities.bold());
            counterView.setGravity(Gravity.CENTER);
            ThemeBind.textColor(counterView, Theme.key_featuredStickers_buttonText, resourcesProvider);
            ThemeBind.fillBackground(counterView, Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)), ThemeBind.of(Theme.key_featuredStickers_addButton), resourcesProvider);

            arrowView = new ImageView(context);
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            ThemeBind.colorFilter(arrowView, Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider, PorterDuff.Mode.SRC_IN);

            if (LocaleController.isRTL) {
                textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                arrowView.setScaleX(-1);

                addView(arrowView, LayoutHelper.createLinear(24, 24, 0, Gravity.CENTER_VERTICAL | Gravity.LEFT, 12, 0, 0, 0));
                addView(counterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, 0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
                addView(textView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, Gravity.FILL, 18, 0, 0, 0));
                addView(avatarView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 18, 0, 18, 0));

            } else {
                textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);

                addView(avatarView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 18, 0));
                addView(textView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, Gravity.FILL, 0, 0, 18, 0));
                addView(counterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, 0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
                addView(arrowView, LayoutHelper.createLinear(24, 24, 0, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 12, 0));
            }
        }

        private int lastArrowColor;

        @Override
        public void updateColors() {
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            Theme.setDrawableColor(counterView.getBackground(), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            counterView.invalidate();
            final int arrowColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
            if (arrowColor != lastArrowColor || arrowView.getColorFilter() == null) {
                lastArrowColor = arrowColor;
                arrowView.setColorFilter(new PorterDuffColorFilter(arrowColor, PorterDuff.Mode.SRC_IN));
            }
            emojiStatusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            updateBotDrawableColor();
        }

        private void updateBotDrawableColor() {
            try {
                if (botDrawable != null) {
                    botDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
                    if (textView != null) {
                        textView.invalidate();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_profile_verifiedBackground);
        }

        public void set(int account) {
            final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();

            avatarDrawable.setInfo(account, user);
            avatarView.getImageReceiver().setCurrentAccount(account);
            avatarView.setForUserOrChat(user, avatarDrawable);
            textView.setText(UserObject.getUserName(user));

            botDrawable.setCurrentAccount(account);
            emojiStatusDrawable.setCurrentAccount(account);

            botDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            if (user != null && user.bot_verification_icon != 0) {
                botDrawable.set(user.bot_verification_icon, false);
            } else {
                botDrawable.set((Drawable) null, false);
            }
            final Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
            emojiStatusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            if (emojiStatusId != null) {
                emojiStatusDrawable.set(emojiStatusId, false);
            } else if (user != null && user.premium) {
                emojiStatusDrawable.set(getContext().getResources().getDrawable(R.drawable.msg_premium_liststar).mutate(), false);
            } else {
                emojiStatusDrawable.set((Drawable) null, false);
            }
            textView.setLeftDrawable(!botDrawable.isEmpty() ? botDrawable : null);
            textView.setRightDrawable(!emojiStatusDrawable.isEmpty() ? emojiStatusDrawable : null);

            int counter = MessagesStorage.getInstance(account).getMainUnreadCount();
            counterView.setVisibility(counter > 0 ? View.VISIBLE : View.GONE);
            counterView.setText(LocaleController.formatNumber(counter, ','));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<AccountCell> {
            static { setup(new Factory()); }

            @Override
            public AccountCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new AccountCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((AccountCell) view).set(item.intValue);
            }

            public static UItem of(int id, int account) {
                final UItem item = UItem.ofFactory(AccountCell.Factory.class);
                item.id = id;
                item.intValue = account;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.intValue == b.intValue;
            }
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (simpleActionBar.isSearchFieldVisible()) {
            if (invoked) simpleActionBar.closeSearchField();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    public static class SettingCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Background iconBackground;
        private final FrameLayout iconLayout;
        private final ImageView iconView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView valueView;
        private final boolean mini;
        private boolean hasBackground = true;

        public SettingCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, resourcesProvider, false);
        }

        public SettingCell(Context context, Theme.ResourcesProvider resourcesProvider, boolean mini) {
            super(context);

            this.resourcesProvider = resourcesProvider;
            this.mini = mini;
            setOrientation(HORIZONTAL);

            iconLayout = new FrameLayout(context);
            iconLayout.setBackground(iconBackground = new Background());

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconLayout.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            if (LocaleController.isRTL) {
                addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 20, 0, 0, 0));
                addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, mini ? 12 : 18, 0));
                addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, mini ? 9 : 18, 0));
            } else {
                addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, mini ? 9 : 18, 0, 0, 0));
                addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL,  mini ? 12 : 18, 0, 20, 0));
                addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));
            }
            updateColors();
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            iconBackground.setDrawBorder(hasBackground && (resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark()));
        }
        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText);
        }

        private boolean twoLines;

        public void set(
            int iconColorTop, int iconColorBottom, int icon,
            CharSequence title,
            CharSequence subtitle,
            CharSequence value
        ) {
            hasBackground = iconColorTop != 0 || iconColorBottom != 0;
            iconLayout.setVisibility(icon != 0 ? View.VISIBLE : View.GONE);
            titleView.setTranslationX(icon == 0 ? dp(2) : 0);
            subtitleView.setTranslationX(icon == 0 ? dp(2) : 0);

            iconBackground.setColor(iconColorTop, iconColorBottom);
            iconView.setImageResource(icon);
            titleView.setText(title);
            subtitleView.setVisibility((twoLines = !TextUtils.isEmpty(subtitle)) ? View.VISIBLE : View.GONE);
            subtitleView.setText(subtitle);
            setValue(value);
            updateColors();
        }

        public void setValue(CharSequence value) {
            valueView.setVisibility(!TextUtils.isEmpty(value) ? View.VISIBLE : View.GONE);
            valueView.setText(value);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(mini ? 44 : twoLines ? 60 : 50), MeasureSpec.EXACTLY)
            );
        }

        public static class Background extends Drawable {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private LinearGradient gradient, strokeGradient;
            private final Matrix matrix = new Matrix();

            public Background() {
                strokePaint.setStyle(Paint.Style.STROKE);
                strokeGradient = new LinearGradient(0, 0, 0, dp(28), new int[] { 0x4dffffff, 0, 0x1affffff }, new float[] { 0, 0.5f, 1 }, Shader.TileMode.CLAMP);
                strokePaint.setShader(strokeGradient);
            }

            public void setColor(int topColor, int bottomColor) {
                gradient = new LinearGradient(0, 0, 0, dp(28), new int[] { topColor, bottomColor }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
                paint.setShader(gradient);
            }

            private boolean border;
            public void setDrawBorder(boolean drawBorder) {
                this.border = drawBorder;
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                final float r = dp(10);
                AndroidUtilities.rectTmp.set(getBounds());
                matrix.reset();
                matrix.postTranslate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);

                if (border) {
                    final float sw = dp(1);
                    strokePaint.setStrokeWidth(sw);
                    matrix.reset();
                    matrix.postTranslate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                    AndroidUtilities.rectTmp.inset(sw / 2.0f, sw / 2.0f);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, strokePaint);
                }
            }

            @Override
            public void setAlpha(int alpha) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        }

        public static class Factory extends UItem.UItemFactory<SettingCell> {
            static { setup(new Factory()); }

            @Override
            public SettingCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SettingCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                int iconColorTop    = (int) item.longValue;
                int iconColorBottom = (int) (item.longValue >>> 32);
                ((SettingCell) view).set(
                    iconColorTop, iconColorBottom, item.iconResId,
                    item.text,
                    item.subtext,
                    item.textValue
                );
            }

            public static UItem of(int id, int iconColorTop, int iconColorBottom, int icon, CharSequence title) {
                return of(id, iconColorTop, iconColorBottom, icon, title, null, null);
            }
            public static UItem of(int id, int iconColorTop, int iconColorBottom, int icon, CharSequence title, CharSequence subtitle) {
                return of(id, iconColorTop, iconColorBottom, icon, title, subtitle, null);
            }
            public static UItem of(int id, int iconColorTop, int iconColorBottom, int icon, CharSequence title, CharSequence subtitle, CharSequence value) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.iconResId = icon;
                item.text = title;
                item.subtext = subtitle;
                item.textValue = value;
                item.longValue = ((long) iconColorBottom << 32) | (iconColorTop & 0xFFFFFFFFL);
                return item;
            }

            public static UItem ofBot(TLRPC.TL_attachMenuBot attachMenuBot, int iconColorTop, int iconColorBottom, int icon) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.id = Long.hashCode(attachMenuBot.bot_id);
                item.object = attachMenuBot;
                item.iconResId = icon;
                item.text = attachMenuBot.short_name;
                item.longValue = ((long) iconColorBottom << 32) | (iconColorTop & 0xFFFFFFFFL);
                return item;
            }
        }
    }

    public static class SuggestionCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private LinkSpanDrawable.LinksTextView titleView;
        private LinkSpanDrawable.LinksTextView textView;
        private ButtonWithCounterView no, yes;

        public SuggestionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setOrientation(VERTICAL);

            titleView = TextHelper.makeLinkTextView(context, 15, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
            titleView.setGravity(Gravity.CENTER);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 32, 20, 32, 0));

            textView = TextHelper.makeLinkTextView(context, 13, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 32, 9.33f, 32, 0));

            final LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(HORIZONTAL);

            no = new ButtonWithCounterView(context, resourcesProvider).setRound();
            yes = new ButtonWithCounterView(context, resourcesProvider).setRound();
            buttons.addView(no, LayoutHelper.createLinear(0, 42, 1f, Gravity.FILL_VERTICAL, 0, 0, 12, 0));
            buttons.addView(yes, LayoutHelper.createLinear(0, 42, 1f, Gravity.FILL_VERTICAL, 0, 0, 0, 0));

            addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 24, 18, 24, 16));
        }

        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        }
        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
        }

        public void set(
            CharSequence title,
            CharSequence text,
            CharSequence noText, View.OnClickListener noListener,
            CharSequence yesText, View.OnClickListener yesListener
        ) {
            titleView.setText(Emoji.replaceEmoji(title, titleView.getPaint().getFontMetricsInt(), false));
            textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
            no.setVisibility(TextUtils.isEmpty(noText) ? View.GONE : View.VISIBLE);
            no.setText(noText);
            no.setOnClickListener(noListener);
            yes.setText(yesText);
            yes.setOnClickListener(yesListener);
        }

        public static class Factory extends UItem.UItemFactory<SuggestionCell> {
            static { setup(new Factory()); }

            @Override
            public SuggestionCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SuggestionCell(context, resourcesProvider);
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((SuggestionCell) view).set(
                    item.text, item.subtext,
                    item.textValue, item.clickCallback,
                    item.animatedText, item.clickCallback2
                );
            }

            public static UItem of(
                CharSequence title,
                CharSequence text,
                CharSequence noText, View.OnClickListener noListener,
                CharSequence yesText, View.OnClickListener yesListener
            ) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.text = title;
                item.subtext = text;
                item.textValue = noText;
                item.clickCallback = noListener;
                item.animatedText = yesText;
                item.clickCallback2 = yesListener;
                return item;
            }
        }
    }

    // avatar image updater

    int avatarUploadingRequest = -1;

    @Override
    public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            if (photo != null || video != null || emojiMarkup != null) {
                if (avatar == null) {
                    return;
                }
                final TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                if (photo != null) {
                    req.file = photo;
                    req.flags |= 1;
                }
                if (video != null) {
                    req.video = video;
                    req.flags |= 2;
                    req.video_start_ts = videoStartTimestamp;
                    req.flags |= 4;
                }
                if (emojiMarkup != null) {
                    req.video_emoji_markup = emojiMarkup;
                    req.flags |= 16;
                }
                avatarUploadingRequest = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    avatarUploadingRequest = -1;
                    if (error == null) {
                        TLRPC.User user = getMessagesController().getUser(getUserConfig().getClientUserId());
                        if (user == null) {
                            user = getUserConfig().getCurrentUser();
                            if (user == null) {
                                return;
                            }
                            getMessagesController().putUser(user, false);
                        } else {
                            getUserConfig().setCurrentUser(user);
                        }

                        final TLRPC.TL_photos_photo photos_photo = (TLRPC.TL_photos_photo) response;
                        final ArrayList<TLRPC.PhotoSize> sizes = photos_photo.photo.sizes;
                        final TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(sizes, 150);
                        final TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(sizes, 800);
                        final TLRPC.VideoSize videoSize = photos_photo.photo.video_sizes.isEmpty() ? null : FileLoader.getClosestVideoSizeWithSize(photos_photo.photo.video_sizes, 1000);
                        user.photo = new TLRPC.TL_userProfilePhoto();
                        user.photo.photo_id = photos_photo.photo.id;
                        if (small != null) {
                            user.photo.photo_small = small.location;
                        }
                        if (big != null) {
                            user.photo.photo_big = big.location;
                        }

                        if (small != null && avatar != null) {
                            final File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(small, true);
                            final File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatar, true);
                            src.renameTo(destFile);
                            final String oldKey = avatar.volume_id + "_" + avatar.local_id + "@90_90";
                            final String newKey = small.location.volume_id + "_" + small.location.local_id + "@90_90";
                            ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUserOrChat(currentAccount, user, ImageLocation.TYPE_SMALL), false);
                        }

                        if (videoSize != null && videoPath != null) {
                            final File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(videoSize, "mp4", true);
                            final File src = new File(videoPath);
                            src.renameTo(destFile);
                        } else if (big != null && avatarBig != null) {
                            final File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(big, true);
                            final File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatarBig, true);
                            src.renameTo(destFile);
                        }
                        getMessagesController().getDialogPhotos(user.id).addPhotoAtStart(((TLRPC.TL_photos_photo) response).photo);
                        final ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        getMessagesStorage().putUsersAndChats(users, null, false, true);
                        final TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
                        if (userFull != null) {
                            userFull.profile_photo = photos_photo.photo;
                            getMessagesStorage().updateUserInfo(userFull, false);
                        }

                        setInfo(user);
                    }

                    avatar = null;
                    avatarBig = null;
                    showAvatarProgress(false, true);
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                    getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                    getUserConfig().saveConfig(true);
                }));
            } else {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarView.setImage(ImageLocation.getForLocal(avatar), "90_90", avatarDrawable, null);
                showAvatarProgress(true, false);
            }
            simpleActionBar.createMenu().requestLayout();
        });
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarProgressView == null) {
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
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
            } else {
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarProgressView == null) {
                        return;
                    }
                    if (!show) {
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
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
            } else {
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onUploadProgressChanged(float progress) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(progress);
//        avatarsViewPager.setUploadProgress(uploadingImageLocation, progress);
    }

    @Override
    public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {
        if (avatarProgressView == null) {
            return;
        }
        avatarProgressView.setProgress(0.0f);
    }



    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SEARCH_PAGE_VISIBLE) {
            checkUi_menuItems();
        }
    }

    private boolean leftButtonIsBack = true;

    private void checkUi_menuItems() {
        if (!hasMainTabs) {
            return;
        }
        // the main tab has no back: the left button is the QR code, turning into back while the search is open
        // (the bar swaps the icons inside the glass circle, so the title does not move)
        final boolean back = simpleActionBar.isSearchFieldVisible();
        if (back != leftButtonIsBack) {
            leftButtonIsBack = back;
            simpleActionBar.setBackButtonImageAnimated(back ? R.drawable.ic_ab_back : R.drawable.outline_header_qr_24);
            simpleActionBar.setBackButtonContentDescription(getString(back ? R.string.AccDescrGoBack : R.string.QrCode));
        }
    }


    /* Blur */

    @Override
    public GlassSourceRenderNode getGlassSource() {
        return getBaseSimpleGlass().getGlassSource();
    }

    @Override
    public GlassSourceRenderNode getFrostedGlassSource() {
        return getBaseSimpleGlass().getFrostedGlassSource();
    }

    @Override
    public void onParentScrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !animatorSearchPageVisible.getValue();
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return isSwipeBackEnabled(ev);
    }

    @Override
    protected boolean useFadeDrawableForActionBar() {
        return false;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private interface ChoiceHandler {
        void onChoice(int index);
    }

    private void showChoice(String title, CharSequence[] choices, ChoiceHandler handler) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setItems(choices, (dialog, index) -> {
            handler.onChoice(index);
            listView.adapter.update(true);
        });
        showDialog(builder.create());
    }
}

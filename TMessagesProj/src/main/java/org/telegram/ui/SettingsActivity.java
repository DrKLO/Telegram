package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.replaceSingleTag;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
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
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
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
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.TON.TONIntroActivity;
import org.telegram.ui.bots.BotBiometry;
import org.telegram.ui.bots.BotDownloads;
import org.telegram.ui.bots.BotLocation;
import org.telegram.ui.bots.SetupEmojiStatusSheet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class SettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate, MainTabsActivity.TabFragmentDelegate, FactorAnimator.Target {

    private static final int ANIMATOR_ID_SEARCH_PAGE_VISIBLE = 0;

    private final BoolAnimator animatorSearchPageVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_PAGE_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    private SizeNotifierFrameLayout contentView;
    private UniversalRecyclerView listView;
    private View actionBarBackground;

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
    private AvatarDrawable avatarDrawable;
    private BackupImageView avatarView;
    private FrameLayout cameraButton;
    private FrameLayout cameraBackground;
    private ImageView cameraImageView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView versionView;
    private boolean hasMainTabs;

    private View navigationBar;

    private int versionViewPressCount = 0;

    public SettingsActivity() {
        this(null);
    }

    public SettingsActivity(Bundle args) {
        super(args);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            iBlur3SourceGlassFrosted = new BlurredBackgroundSourceRenderNode(null);
            iBlur3SourceGlass = new BlurredBackgroundSourceRenderNode(null);
        } else {
            scrollableViewNoiseSuppressor = null;
            iBlur3SourceGlassFrosted = null;
            iBlur3SourceGlass = null;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.starBalanceUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.newSuggestionsAvailable);

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
    public View createView(Context context) {
        contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (Build.VERSION.SDK_INT >= 31 && scrollableViewNoiseSuppressor != null) {
                    blur3_InvalidateBlur();

                    final int width = getMeasuredWidth();
                    final int height = getMeasuredHeight();
                    if (iBlur3SourceGlassFrosted != null && !iBlur3SourceGlassFrosted.inRecording()) {
                        //if (iBlur3SourceGlassFrosted.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
                        final Canvas c = iBlur3SourceGlassFrosted.beginRecording(width, height);
                        c.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        if (SharedConfig.chatBlurEnabled()) {
                            scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
                        }
                        iBlur3SourceGlassFrosted.endRecording();
                        //}
                    }
                    if (iBlur3SourceGlass != null && !iBlur3SourceGlass.inRecording()) {
                        //if (iBlur3SourceGlass.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
                        final Canvas c = iBlur3SourceGlass.beginRecording(width, height);
                        c.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        if (SharedConfig.chatBlurEnabled()) {
                            scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                        }
                        iBlur3SourceGlass.endRecording();
                        //}
                    }
                    iBlur3Invalidated = false;
                }
                super.dispatchDraw(canvas);
                if (!hasMainTabs) {
                    AndroidUtilities.drawNavigationBarProtection(canvas, this, getThemedColor(Theme.key_windowBackgroundWhite), navigationBarHeight);
                }
            }

            @Override
            public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || iBlur3SourceGlassFrosted == null) {
                    canvas.drawRect(rectTmp, blurScrimPaint);
                    return;
                }

                canvas.save();
                canvas.translate(0, -y);
                iBlur3SourceGlassFrosted.draw(canvas, rectTmp.left, rectTmp.top + y, rectTmp.right, rectTmp.bottom + y);
                canvas.restore();

                final int oldScrimAlpha = blurScrimPaint.getAlpha();
                blurScrimPaint.setAlpha(ChatActivity.ACTION_BAR_BLUR_ALPHA);
                canvas.drawRect(rectTmp, blurScrimPaint);
                blurScrimPaint.setAlpha(oldScrimAlpha);
            }

            @Override
            public void updateColors() {
                super.updateColors();
                SettingsActivity.this.updateColors();
            }
        };

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setUseContainerForTitles();
        actionBar.setTitle(getString(R.string.Settings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 2) {
                    presentFragment(new LogoutActivity());
                }
            }
        });
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackground(null);

        final ActionBarMenu menu = actionBar.createMenu();
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
        otherItem.addSubItem(2, R.drawable.msg_leave, getString(R.string.LogOut));

        search = new ProfileActivity.SearchAdapter(this, context) {
            @Override
            public void notifyDataSetChanged() {
                listView.adapter.update(true);
            }
        };
        search.loadFaqWebPage();

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setPadding(0, AndroidUtilities.statusBarHeight + dp(12), 0, AndroidUtilities.navigationBarHeight + additionNavigationBarHeight);
        listView.setClipToPadding(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateActionBarVisible();
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                    scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                    blur3_InvalidateBlur();
                }
            }
        });
        iBlur3Capture = new ViewGroupPartRenderer(listView, contentView, listView::drawChild);
        listView.addEdgeEffectListener(() -> listView.postOnAnimation(this::blur3_InvalidateBlur));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarBackground = new View(context) {
            private final Paint blurScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                int top = actionBar.getHeight();
                AndroidUtilities.rectTmp2.set(0, 0, getMeasuredWidth(), top);
                blurScrimPaint.setColor(Theme.getColor(Theme.key_actionBarDefault, resourceProvider));
                contentView.drawBlurRect(canvas, 0, AndroidUtilities.rectTmp2, blurScrimPaint, true);
                if (getParentLayout() != null) {
                    getParentLayout().drawHeaderShadow(canvas, top);
                }
            }
        };
        contentView.addView(actionBarBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP));
        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
        imageUpdater.setOpenWithFrontfaceCamera(true);
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);

        topView = new FrameLayout(context);

        avatarContainer = new FrameLayout(context);
        topView.addView(avatarContainer, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 23 - 12, 0, 0));
        avatarContainer.setOnClickListener(v -> {
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
        ScaleStateListAnimator.apply(avatarContainer);

        avatarDrawable = new AvatarDrawable();
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(90));
        avatarContainer.addView(avatarView, LayoutHelper.createFrame(90, 90, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 15, 0, 0));

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
        avatarContainer.addView(avatarProgressView, LayoutHelper.createFrame(90, 90, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 15, 0, 0));
        showAvatarProgress(false, false);

        cameraButton = new FrameLayout(context);
        cameraButton.setBackground(Theme.createCircleDrawable(dp(32), getThemedColor(Theme.key_windowBackgroundGray)));
        cameraButton.setPadding(dp(2), dp(2), dp(2), dp(2));
        cameraBackground = new FrameLayout(context);
        cameraBackground.setBackground(Theme.createCircleDrawable(dp(30), getThemedColor(Theme.key_featuredStickers_addButton)));
        cameraImageView = new ImageView(context);
        cameraImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cameraImageView.setImageResource(R.drawable.filled_premium_camera);
        cameraBackground.addView(cameraImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        cameraButton.addView(cameraBackground, LayoutHelper.createFrame(30, 30));
        avatarContainer.addView(cameraButton, LayoutHelper.createFrame(34, 34, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 75, 0, 0));
        ScaleStateListAnimator.apply(cameraButton);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        topView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 138.333f - 12, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        topView.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 168 - 12, 0, 0));

        versionView = new TextView(context);
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        versionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText4));
        versionView.setPadding(dp(21), dp(10), dp(21), dp(10));
        versionView.setGravity(Gravity.CENTER);
        versionView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
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
            openDebugMenu();
        });

        navigationBar = new View(context);
//        fragmentView.addView(navigationBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        updateActionBarVisible(true, false);
        listView.adapter.update(false);
        setInfo();
        updateColors();
        checkUi_menuItems();

        ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onApplyWindowInsets);
        return fragmentView = contentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.starBalanceUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.newSuggestionsAvailable);
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


    public void updateColors() {
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        searchItem.updateColor();

        final int navigationBarColor = getThemedColor(Theme.key_windowBackgroundWhite);
        navigationBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { Theme.multAlpha(navigationBarColor, 0.0f), navigationBarColor }));

        actionBarBackground.invalidate();
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
                firstChild.getY() + firstChild.getHeight() < actionBar.getHeight()
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
            actionBar.getTitlesContainer().setAlpha(visible ? 1.0f : 0.0f);
            actionBarBackground.setAlpha(visible ? 1.0f : 0.0f);
        } else {
            actionBarVisibleAnimator = ValueAnimator.ofFloat(actionBar.getTitlesContainer().getAlpha(), visible ? 1.0f : 0.0f);
            actionBarVisibleAnimator.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                actionBar.getTitlesContainer().setAlpha(t);
                actionBarBackground.setAlpha(t);
            });
            actionBarVisibleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            actionBarVisibleAnimator.setDuration(420);
            actionBarVisibleAnimator.start();
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

        items.add(SettingCell.Factory.of(1, 0xFF1CA5ED, 0xFF1488E1, R.drawable.settings_account, getString(R.string.SettingsAccount), getString(R.string.SettingsAccountInfo)));
        items.add(SettingCell.Factory.of(2, 0xFFF09F1B, 0xFFE18A11, R.drawable.settings_chat, getString(R.string.SettingsChat), getString(R.string.SettingsChatInfo)));
        items.add(SettingCell.Factory.of(3, 0xFF55CA47, 0xFF27B434, R.drawable.settings_privacy, getString(R.string.SettingsPrivacySecurity), getString(R.string.SettingsPrivacySecurityInfo)));
        items.add(SettingCell.Factory.of(5, 0xFFF45255, 0xFFDF3955, R.drawable.settings_sounds, getString(R.string.SettingsNotifications), getString(R.string.SettingsNotificationsInfo)));
        items.add(SettingCell.Factory.of(6, 0xFF4F85F6, 0xFF3568E8, R.drawable.settings_data, getString(R.string.SettingsData), getString(R.string.SettingsDataInfo)));
        items.add(SettingCell.Factory.of(7, 0xFF1CA5ED, 0xFF1387E1, R.drawable.settings_folders, getString(R.string.SettingsFolders), getString(R.string.SettingsFoldersInfo)));
        items.add(SettingCell.Factory.of(8, 0xFF32C0CE, 0xFF1D9CC6, R.drawable.settings_devices, getString(R.string.SettingsDevices), getString(R.string.SettingsDevicesInfo)));
        items.add(SettingCell.Factory.of(9, 0xFFF28B31, 0xFFE26314, R.drawable.settings_power, getString(R.string.SettingsPowerSaving), getString(R.string.SettingsPowerSavingInfo)));
        items.add(SettingCell.Factory.of(10, 0xFFC46EF4, 0xFF9F55DF, R.drawable.settings_language, getString(R.string.SettingsLanguage), LocaleController.getCurrentLanguageName()));

        items.add(UItem.asShadow(null));

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
            StarsController c = StarsController.getTonInstance(currentAccount);
            long balance = c.getBalance().amount;
            items.add(SettingCell.Factory.of(13, 0xFF1BA4ED, 0xFF1488E1, R.drawable.settings_ton, getString(R.string.MyTON), null, c.balanceAvailable() && balance > 0 ? StarsIntroActivity.formatStarsAmount(c.getBalance(), 0.85f, ' ') : ""));
        }
//        items.add(SettingCell.Factory.of(14, 0, "Wallet"));
        if (!getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(15, 0xFFF45255, 0xFFDF3955, R.drawable.settings_business, getString(R.string.TelegramBusiness)));
        }
        if (!getMessagesController().premiumPurchaseBlocked()) {
            items.add(SettingCell.Factory.of(16, 0xFFF38B31, 0xFFE26314, R.drawable.settings_gift, getString(R.string.SendAGift)));
        }
        if (items.get(items.size() - 1).viewType != UniversalAdapter.VIEW_TYPE_SHADOW)
            items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SettingsHelp)));
        items.add(SettingCell.Factory.of(17, 0xFFF09F1B, 0xFFE18A11, R.drawable.settings_ask, getString(R.string.AskAQuestion)));
        items.add(SettingCell.Factory.of(18, 0xFF1BA4ED, 0xFF1488E1, R.drawable.settings_faq, getString(R.string.TelegramFAQ)));
        items.add(SettingCell.Factory.of(23, 0xFFC46EF4, 0xFF9F55DF, R.drawable.settings_features, getString(R.string.TelegramFeatures)));
        items.add(SettingCell.Factory.of(19, 0xFF55CA47, 0xFF27B434, R.drawable.settings_policy, getString(R.string.PrivacyPolicy)));

        if (BuildVars.LOGS_ENABLED || BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.SettingsDebug)));
            items.add(SettingCell.Factory.of(20, 0xFF55CA47, 0xFF27B434, 0, getString(R.string.DebugSendLogs)));
            items.add(SettingCell.Factory.of(21, 0xFF55CA47, 0xFF27B434, 0, getString(R.string.DebugSendLastLogs)));
            items.add(SettingCell.Factory.of(22, 0xFFF45255, 0xFFDF3955, 0, getString(R.string.DebugClearLogs)));
        }

        items.add(UItem.asCustomShadow(versionView));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
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
                presentFragment(new UserInfoActivity());
                break;
            case 2:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            case 3:
                presentFragment(new PrivacySettingsActivity());
                break;
            case 5:
                presentFragment(new NotificationsSettingsActivity());
                break;
            case 6:
                presentFragment(new DataSettingsActivity());
                break;
            case 7:
                presentFragment(new FiltersSetupActivity());
                break;
            case 8:
                presentFragment(new SessionsActivity(0));
                break;
            case 9:
                presentFragment(new LiteModeSettingsActivity());
                break;
            case 10:
                presentFragment(new LanguageSelectActivity());
                break;

            case 11:
                presentFragment(new PremiumPreviewFragment("settings"));
                break;
            case 12:
                presentFragment(new StarsIntroActivity());
                break;
            case 13:
                presentFragment(new TONIntroActivity());
                break;
            case 15:
                presentFragment(new PremiumPreviewFragment(PremiumPreviewFragment.FEATURES_BUSINESS, "settings"));
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
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
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
        final int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
        navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        listView.setPadding(0, statusBarHeight + dp(12), 0, navigationBarHeight + additionNavigationBarHeight);
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
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));

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
            counterView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            counterView.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));

            arrowView = new ImageView(context);
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));

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

        @Override
        public void updateColors() {
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            counterView.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
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
        if (actionBar.isSearchFieldVisible()) {
            if (invoked) actionBar.closeSearchField();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    public static class SettingCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Background iconBackground;
        private final ImageView iconView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView valueView;

        public SettingCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            iconView.setBackground(iconBackground = new Background());

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
                addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 18, 0));
                addView(iconView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 18, 0));
            } else {
                addView(iconView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 0, 0));
                addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 18, 0, 20, 0));
                addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));
            }
            updateColors();
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            iconBackground.setDrawBorder(resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark());
        }

        private boolean twoLines;

        public void set(
            int iconColorTop, int iconColorBottom, int icon,
            CharSequence title,
            CharSequence subtitle,
            CharSequence value
        ) {
            iconView.setVisibility(icon != 0 ? View.VISIBLE : View.GONE);
            titleView.setTranslationX(icon == 0 ? dp(2) : 0);
            subtitleView.setTranslationX(icon == 0 ? dp(2) : 0);

            iconBackground.setColor(iconColorTop, iconColorBottom);
            iconView.setImageResource(icon);
            titleView.setText(title);
            subtitleView.setVisibility((twoLines = !TextUtils.isEmpty(subtitle)) ? View.VISIBLE : View.GONE);
            subtitleView.setText(subtitle);
            valueView.setVisibility(!TextUtils.isEmpty(value) ? View.VISIBLE : View.GONE);
            valueView.setText(value);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(twoLines ? 60 : 50), MeasureSpec.EXACTLY)
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

    public void openDebugMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(getString(R.string.DebugMenu));
        CharSequence[] items;
        items = new CharSequence[]{
                getString(R.string.DebugMenuImportContacts),
                getString(R.string.DebugMenuReloadContacts),
                getString(R.string.DebugMenuResetContacts),
                getString(R.string.DebugMenuResetDialogs),
                BuildVars.DEBUG_VERSION ? null : BuildVars.LOGS_ENABLED ? getString("DebugMenuDisableLogs", R.string.DebugMenuDisableLogs) : getString("DebugMenuEnableLogs", R.string.DebugMenuEnableLogs),
                SharedConfig.inappCamera ? getString("DebugMenuDisableCamera", R.string.DebugMenuDisableCamera) : getString("DebugMenuEnableCamera", R.string.DebugMenuEnableCamera),
                getString("DebugMenuClearMediaCache", R.string.DebugMenuClearMediaCache),
                getString(R.string.DebugMenuCallSettings),
                null,
                BuildVars.DEBUG_PRIVATE_VERSION || ApplicationLoader.isStandaloneBuild() || ApplicationLoader.isBetaBuild() ? getString("DebugMenuCheckAppUpdate", R.string.DebugMenuCheckAppUpdate) : null,
                getString("DebugMenuReadAllDialogs", R.string.DebugMenuReadAllDialogs),
                BuildVars.DEBUG_PRIVATE_VERSION ? SharedConfig.disableVoiceAudioEffects ? "Enable voip audio effects" : "Disable voip audio effects" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? "Clean app update" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? "Reset suggestions" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? getString(R.string.DebugMenuClearWebViewCache) : null,
                getString(R.string.DebugMenuClearWebViewCookies),
                getString(SharedConfig.debugWebView ? R.string.DebugMenuDisableWebViewDebug : R.string.DebugMenuEnableWebViewDebug),
                AndroidUtilities.isTabletInternal() && BuildVars.DEBUG_PRIVATE_VERSION ? SharedConfig.forceDisableTabletMode ? "Enable tablet mode" : "Disable tablet mode" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? getString(SharedConfig.isFloatingDebugActive ? R.string.FloatingDebugDisable : R.string.FloatingDebugEnable) : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? "Force remove premium suggestions" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? "Share device info" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? "Force performance class" : null,
                BuildVars.DEBUG_PRIVATE_VERSION && !InstantCameraView.allowBigSizeCameraDebug() ? !SharedConfig.bigCameraForRound ? "Force big camera for round" : "Disable big camera for round" : null,
                getString(DualCameraView.dualAvailableStatic(getContext()) ? "DebugMenuDualOff" : "DebugMenuDualOn"),
                BuildVars.DEBUG_VERSION ? SharedConfig.useSurfaceInStories ? "back to TextureView in stories" : "use SurfaceView in stories" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? SharedConfig.photoViewerBlur ? "do not blur in photoviewer" : "blur in photoviewer" : null,
                !SharedConfig.payByInvoice ? "Enable Invoice Payment" : "Disable Invoice Payment",
                BuildVars.DEBUG_PRIVATE_VERSION ? "Update Attach Bots" : null,
                !SharedConfig.isUsingCamera2(currentAccount) ? "Use Camera 2 API" : "Use old Camera 1 API",
                BuildVars.DEBUG_VERSION ? "Clear Mini Apps Permissions and Files" : null,
                BuildVars.DEBUG_PRIVATE_VERSION ? "Clear all login tokens" : null,
                SharedConfig.canBlurChat() && Build.VERSION.SDK_INT >= 31 ? SharedConfig.useNewBlur ? "back to cpu blur" : "use new gpu blur" : null,
                SharedConfig.adaptableColorInBrowser ? "Disabled adaptive browser colors" : "Enable adaptive browser colors",
                SharedConfig.debugVideoQualities ? "Disable video qualities debug" : "Enable video qualities debug",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? getString(SharedConfig.useSystemBoldFont ? R.string.DebugMenuDontUseSystemBoldFont : R.string.DebugMenuUseSystemBoldFont) : null,
                "Reload app config",
                !SharedConfig.forceForumTabs ? "Force Forum Tabs" : "Do Not Force Forum Tabs",
                "Make Memory Dump",
                BuildVars.DEBUG_PRIVATE_VERSION ? (SharedConfig.fastWallpaperDisabled ? "enable wallpaper shader" : "disable wallpaper shader") : null,
                (SharedConfig.frameMetricsEnabled ? "hide frame metrics" : "show frame metrics"),
                BuildVars.DEBUG_PRIVATE_VERSION ? (SharedConfig.shadowsInSections ? "disable shadows in settings" : "enable shadows in settings") : null
        };

        builder.setItems(items, (dialog, which) -> {
            if (which == 0) { // Import Contacts
                getUserConfig().syncContacts = true;
                getUserConfig().saveConfig(false);
                getContactsController().forceImportContacts();
            } else if (which == 1) { // Reload Contacts
                getContactsController().loadContacts(false, 0);
            } else if (which == 2) { // Reset Imported Contacts
                getContactsController().resetImportedContacts();
            } else if (which == 3) { // Reset Dialogs
                getMessagesController().forceResetDialogs();
            } else if (which == 4) { // Logs
                BuildVars.LOGS_ENABLED = !BuildVars.LOGS_ENABLED;
                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
                sharedPreferences.edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED).commit();
                listView.adapter.update(true);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("app start time = " + ApplicationLoader.startTime);
                    try {
                        FileLog.d("buildVersion = " + ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionCode);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } else if (which == 5) { // In-app camera
                SharedConfig.toggleInappCamera();
            } else if (which == 6) { // Clear sent media cache
                getMessagesStorage().clearSentMedia();
                SharedConfig.setNoSoundHintShowed(false);
                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.remove("archivehint").remove("proximityhint").remove("archivehint_l").remove("searchpostsnew").remove("speedhint").remove("gifhint").remove("reminderhint").remove("soundHint").remove("themehint").remove("bganimationhint").remove("filterhint").remove("n_0").remove("storyprvhint").remove("storyhint").remove("storyhint2").remove("storydualhint").remove("storysvddualhint").remove("stories_camera").remove("dualcam").remove("dualmatrix").remove("dual_available").remove("archivehint").remove("askNotificationsAfter").remove("askNotificationsDuration").remove("viewoncehint").remove("voicepausehint").remove("taptostorysoundhint").remove("nothanos").remove("voiceoncehint").remove("savedhint").remove("savedsearchhint").remove("savedsearchtaghint").remove("groupEmojiPackHintShown").remove("newppsms").remove("monetizationadshint").remove("seekSpeedHintShowed").remove("unsupport_video/av01").remove("channelgifthint").remove("statusgiftpage").remove("multistorieshint").remove("channelsuggesthint").remove("trimvoicehint").remove("taptostoryhighlighthint").remove("proxycheckstatusip").remove("callmiconstart").apply();
                MessagesController.getEmojiSettings(currentAccount).edit().remove("featured_hidden").remove("emoji_featured_hidden").commit();
                SharedConfig.textSelectionHintShows = 0;
                SharedConfig.lockRecordAudioVideoHint = 0;
                SharedConfig.stickersReorderingHintUsed = false;
                SharedConfig.forwardingOptionsHintShown = false;
                SharedConfig.replyingOptionsHintShown = false;
                SharedConfig.messageSeenHintCount = 3;
                SharedConfig.emojiInteractionsHintCount = 3;
                SharedConfig.dayNightThemeSwitchHintCount = 3;
                SharedConfig.fastScrollHintCount = 3;
                SharedConfig.stealthModeSendMessageConfirm = 2;
                SharedConfig.updateStealthModeSendMessageConfirm(2);
                SharedConfig.setStoriesReactionsLongPressHintUsed(false);
                SharedConfig.setStoriesIntroShown(false);
                SharedConfig.setMultipleReactionsPromoShowed(false);
                ChatThemeController.getInstance(currentAccount).clearCache();
                getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
                RestrictedLanguagesSelectActivity.cleanup();
                PersistColorPalette.getInstance(currentAccount).cleanup();
                SharedPreferences prefs = getMessagesController().getMainSettings();
                editor = prefs.edit();
                editor.remove("peerColors").remove("profilePeerColors").remove("boostingappearance").remove("bizbothint").remove("movecaptionhint");
                for (String key : prefs.getAll().keySet()) {
                    if (key.contains("show_gift_for_") || key.contains("bdayhint_") || key.contains("bdayanim_") || key.startsWith("ask_paid_message_") || key.startsWith("topicssidetabs")) {
                        editor.remove(key);
                    }
                }
                editor.apply();
                editor = MessagesController.getNotificationsSettings(currentAccount).edit();
                for (String key : MessagesController.getNotificationsSettings(currentAccount).getAll().keySet()) {
                    if (key.startsWith("dialog_bar_botver")) {
                        editor.remove(key);
                    }
                }
                editor.apply();
            } else if (which == 7) { // Call settings
                VoIPHelper.showCallDebugSettings(getParentActivity());
            } else if (which == 8) { // ?
                SharedConfig.toggleRoundCamera16to9();
            } else if (which == 9) { // Check app update
                ((LaunchActivity) getParentActivity()).checkAppUpdate(true, null);
            } else if (which == 10) { // Read all chats
                getMessagesStorage().readAllDialogs(-1);
            } else if (which == 11) { // Voip audio effects
                SharedConfig.toggleDisableVoiceAudioEffects();
            } else if (which == 12) { // Clean app update
                SharedConfig.pendingAppUpdate = null;
                SharedConfig.saveConfig();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            } else if (which == 13) { // Reset suggestions
                Set<String> suggestions = getMessagesController().pendingSuggestions;
                suggestions.add("VALIDATE_PHONE_NUMBER");
                suggestions.add("VALIDATE_PASSWORD");
                getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
            } else if (which == 14) { // WebView Cache
                ApplicationLoader.applicationContext.deleteDatabase("webview.db");
                ApplicationLoader.applicationContext.deleteDatabase("webviewCache.db");
                WebStorage.getInstance().deleteAllData();
                try {
                    WebView webView = new WebView(ApplicationLoader.applicationContext);
                    webView.clearHistory();
                    webView.destroy();
                } catch (Exception e) {
                }
            } else if (which == 15) {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookies(null);
                cookieManager.flush();
            } else if (which == 16) { // WebView debug
                SharedConfig.toggleDebugWebView();
                Toast.makeText(getParentActivity(), getString(SharedConfig.debugWebView ? R.string.DebugMenuWebViewDebugEnabled : R.string.DebugMenuWebViewDebugDisabled), Toast.LENGTH_SHORT).show();
            } else if (which == 17) { // Tablet mode
                SharedConfig.toggleForceDisableTabletMode();
                Activity activity = getParentActivity();
                if (activity != null) {
                    final PackageManager pm = activity.getPackageManager();
                    final Intent intent = pm.getLaunchIntentForPackage(activity.getPackageName());
                    activity.finishAffinity(); // Finishes all activities.
                    activity.startActivity(intent); // Start the launch activity
                }
                System.exit(0);
            } else if (which == 18) {
                FloatingDebugController.setActive((LaunchActivity) getParentActivity(), !FloatingDebugController.isActive());
            } else if (which == 19) {
                getMessagesController().loadAppConfig();
                TLRPC.TL_help_dismissSuggestion req = new TLRPC.TL_help_dismissSuggestion();
                req.suggestion = "VALIDATE_PHONE_NUMBER";
                req.peer = new TLRPC.TL_inputPeerEmpty();
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    TLRPC.TL_help_dismissSuggestion req2 = new TLRPC.TL_help_dismissSuggestion();
                    req2.suggestion = "VALIDATE_PASSWORD";
                    req2.peer = new TLRPC.TL_inputPeerEmpty();
                    getConnectionsManager().sendRequest(req2, (res2, err2) -> {
                        getMessagesController().loadAppConfig();
                    });
                });
            } else if (which == 20) {
                int cpuCount = ConnectionsManager.CPU_COUNT;
                int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
                long minFreqSum = 0, minFreqCount = 0;
                long maxFreqSum = 0, maxFreqCount = 0;
                long curFreqSum = 0, curFreqCount = 0;
                long capacitySum = 0, capacityCount = 0;
                StringBuilder cpusInfo = new StringBuilder();
                for (int i = 0; i < cpuCount; i++) {
                    Long minFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_min_freq");
                    Long curFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_cur_freq");
                    Long maxFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
                    Long capacity = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpu_capacity");
                    cpusInfo.append("#").append(i).append(" ");
                    if (minFreq != null) {
                        cpusInfo.append("min=").append(minFreq / 1000L).append(" ");
                        minFreqSum += (minFreq / 1000L);
                        minFreqCount++;
                    }
                    if (curFreq != null) {
                        cpusInfo.append("cur=").append(curFreq / 1000L).append(" ");
                        curFreqSum += (curFreq / 1000L);
                        curFreqCount++;
                    }
                    if (maxFreq != null) {
                        cpusInfo.append("max=").append(maxFreq / 1000L).append(" ");
                        maxFreqSum += (maxFreq / 1000L);
                        maxFreqCount++;
                    }
                    if (capacity != null) {
                        cpusInfo.append("cpc=").append(capacity).append(" ");
                        capacitySum += capacity;
                        capacityCount++;
                    }
                    cpusInfo.append("\n");
                }
                StringBuilder info = new StringBuilder();
                info.append(Build.MANUFACTURER).append(", ").append(Build.MODEL).append(" (").append(Build.PRODUCT).append(", ").append(Build.DEVICE).append(") ").append(" (android ").append(Build.VERSION.SDK_INT).append(")\n");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    info.append("SoC: ").append(Build.SOC_MANUFACTURER).append(", ").append(Build.SOC_MODEL).append("\n");
                }
                String gpuModel = AndroidUtilities.getSysInfoString("/sys/kernel/gpu/gpu_model");
                if (gpuModel != null) {
                    info.append("GPU: ").append(gpuModel);
                    Long minClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_min_clock");
                    Long mminClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_mm_min_clock");
                    Long maxClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_max_clock");
                    if (minClock != null) {
                        info.append(", min=").append(minClock / 1000L);
                    }
                    if (mminClock != null) {
                        info.append(", mmin=").append(mminClock / 1000L);
                    }
                    if (maxClock != null) {
                        info.append(", max=").append(maxClock / 1000L);
                    }
                    info.append("\n");
                }
                ConfigurationInfo configurationInfo = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getDeviceConfigurationInfo();
                info.append("GLES Version: ").append(configurationInfo.getGlEsVersion()).append("\n");
                info.append("Memory: class=").append(AndroidUtilities.formatFileSize(memoryClass * 1024L * 1024L));
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
                info.append(", total=").append(AndroidUtilities.formatFileSize(memoryInfo.totalMem));
                info.append(", avail=").append(AndroidUtilities.formatFileSize(memoryInfo.availMem));
                info.append(", low?=").append(memoryInfo.lowMemory);
                info.append(" (threshold=").append(AndroidUtilities.formatFileSize(memoryInfo.threshold)).append(")");
                info.append("\n");
                info.append("Current class: ").append(SharedConfig.performanceClassName(SharedConfig.getDevicePerformanceClass())).append(", measured: ").append(SharedConfig.performanceClassName(SharedConfig.measureDevicePerformanceClass()));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    info.append(", suggest=").append(Build.VERSION.MEDIA_PERFORMANCE_CLASS);
                }
                info.append("\n");
                info.append(cpuCount).append(" CPUs");
                if (minFreqCount > 0) {
                    info.append(", avgMinFreq=").append(minFreqSum / minFreqCount);
                }
                if (curFreqCount > 0) {
                    info.append(", avgCurFreq=").append(curFreqSum / curFreqCount);
                }
                if (maxFreqCount > 0) {
                    info.append(", avgMaxFreq=").append(maxFreqSum / maxFreqCount);
                }
                if (capacityCount > 0) {
                    info.append(", avgCapacity=").append(capacitySum / capacityCount);
                }
                info.append("\n").append(cpusInfo);

                listCodecs("video/avc", info);
                listCodecs("video/hevc", info);
                listCodecs("video/x-vnd.on2.vp8", info);
                listCodecs("video/x-vnd.on2.vp9", info);

                showDialog(new ShareAlert(getParentActivity(), null, info.toString(), false, null, false) {
                    @Override
                    protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                        if (!showToast) return;
                        AndroidUtilities.runOnUIThread(() -> {
                            BulletinFactory.createInviteSentBulletin(getParentActivity(), contentView, dids.size(), dids.size() == 1 ? dids.valueAt(0).id : 0, count, getThemedColor(Theme.key_undo_background), getThemedColor(Theme.key_undo_infoColor)).show();
                        }, 250);
                    }
                });
            } else if (which == 21) {
                AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity(), resourceProvider);
                builder2.setTitle("Force performance class");
                int currentClass = SharedConfig.getDevicePerformanceClass();
                int trueClass = SharedConfig.measureDevicePerformanceClass();
                builder2.setItems(new CharSequence[]{
                        AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? "**HIGH**" : "HIGH") + (trueClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? " (measured)" : "")),
                        AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? "**AVERAGE**" : "AVERAGE") + (trueClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? " (measured)" : "")),
                        AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_LOW ? "**LOW**" : "LOW") + (trueClass == SharedConfig.PERFORMANCE_CLASS_LOW ? " (measured)" : ""))
                }, (dialog2, which2) -> {
                    int newClass = 2 - which2;
                    if (newClass == trueClass) {
                        SharedConfig.overrideDevicePerformanceClass(-1);
                    } else {
                        SharedConfig.overrideDevicePerformanceClass(newClass);
                    }
                });
                builder2.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                builder2.show();
            } else if (which == 22) {
                SharedConfig.toggleRoundCamera();
            } else if (which == 23) {
                boolean enabled = DualCameraView.dualAvailableStatic(getContext());
                MessagesController.getGlobalMainSettings().edit().putBoolean("dual_available", !enabled).apply();
                try {
                    Toast.makeText(getParentActivity(), getString(!enabled ? R.string.DebugMenuDualOnToast : R.string.DebugMenuDualOffToast), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                }
            } else if (which == 24) {
                SharedConfig.toggleSurfaceInStories();
                for (int i = 0; i < getParentLayout().getFragmentStack().size(); i++) {
                    getParentLayout().getFragmentStack().get(i).clearSheets();
                }
            } else if (which == 25) {
                SharedConfig.togglePhotoViewerBlur();
            } else if (which == 26) {
                SharedConfig.togglePaymentByInvoice();
            } else if (which == 27) {
                getMediaDataController().loadAttachMenuBots(false, true);
            } else if (which == 28) {
                SharedConfig.toggleUseCamera2(currentAccount);
            } else if (which == 29) {
                BotBiometry.clear();
                BotLocation.clear();
                BotDownloads.clear();
                SetupEmojiStatusSheet.clear();
            } else if (which == 30) {
                AuthTokensHelper.clearLogInTokens();
            } else if (which == 31) {
                SharedConfig.toggleUseNewBlur();
            } else if (which == 32) {
                SharedConfig.toggleBrowserAdaptableColors();
            } else if (which == 33) {
                SharedConfig.toggleDebugVideoQualities();
            } else if (which == 34) {
                SharedConfig.toggleUseSystemBoldFont();
            } else if (which == 35) {
                MessagesController.getInstance(currentAccount).loadAppConfig(true);
            } else if (which == 36) {
                SharedConfig.toggleForceForumTabs();
            } else if (which == 37) {
                FileLog.getInstance().dumpMemory(true);
            } else if (which == 38) {
                SharedConfig.toggleFastWallpaperDisabled();
            } else if (which == 39) {
                SharedConfig.toggleFrameMetricsEnabled();
                if (LaunchActivity.instance != null) {
                    LaunchActivity.instance.checkFrameMetrics();
                }
            } else if (which == 40) {
                final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                prefs.edit().putBoolean("shadowsInSections", SharedConfig.shadowsInSections = !SharedConfig.shadowsInSections).apply();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void listCodecs(String type, StringBuilder info) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return;
        }
        try {
            final int allCodecsCount = MediaCodecList.getCodecCount();
            final ArrayList<Integer> decoderIndexes = new ArrayList<>();
            final ArrayList<Integer> encoderIndexes = new ArrayList<>();
            boolean first = true;
            for (int i = 0; i < allCodecsCount; ++i) {
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(i);
                if (codec == null) {
                    continue;
                }
                String[] types = codec.getSupportedTypes();
                if (types == null) {
                    continue;
                }
                boolean found = false;
                for (int j = 0; j < types.length; ++j) {
                    if (types[j].equals(type)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    (codec.isEncoder() ? encoderIndexes : decoderIndexes).add(i);
                }
            }
            if (decoderIndexes.isEmpty() && encoderIndexes.isEmpty()) {
                return;
            }
            info.append("\n").append(decoderIndexes.size()).append("+").append(encoderIndexes.size()).append(" ").append(type.substring(6)).append(" codecs:\n");
            for (int a = 0; a < decoderIndexes.size(); ++a) {
                if (a > 0) {
                    info.append("\n");
                }
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(decoderIndexes.get(a));
                info.append("{d} ").append(codec.getName()).append(" (");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (codec.isHardwareAccelerated()) {
                        info.append("gpu"); // as Gpu
                    }
                    if (codec.isSoftwareOnly()) {
                        info.append("cpu"); // as Cpu
                    }
                    if (codec.isVendor()) {
                        info.append(", v"); // as Vendor
                    }
                }
                MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(type);
                info.append("; mi=").append(capabilities.getMaxSupportedInstances()).append(")");
            }
            for (int a = 0; a < encoderIndexes.size(); ++a) {
                if (a > 0 || !decoderIndexes.isEmpty()) {
                    info.append("\n");
                }
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(encoderIndexes.get(a));
                info.append("{e} ").append(codec.getName()).append(" (");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (codec.isHardwareAccelerated()) {
                        info.append("gpu"); // as Gpu
                    }
                    if (codec.isSoftwareOnly()) {
                        info.append("cpu"); // as Cpu
                    }
                    if (codec.isVendor()) {
                        info.append(", v"); // as Vendor
                    }
                }
                MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(type);
                info.append("; mi=").append(capabilities.getMaxSupportedInstances()).append(")");
            }
            info.append("\n");
        } catch (Exception ignore) {
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
                            ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), false);
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
            actionBar.createMenu().requestLayout();
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

    private void checkUi_menuItems() {
        FragmentFloatingButton.setAnimatedVisibility(otherItem, 1f - animatorSearchPageVisible.getFloatValue());
        FragmentFloatingButton.setAnimatedVisibility(actionBar.getBackButton(), lerp(hasMainTabs ? 0f : 1f, 1f, animatorSearchPageVisible.getFloatValue()));
    }


    /* Blur */

    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlassFrosted;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlass;

    private IBlur3Capture iBlur3Capture;
    private boolean iBlur3Invalidated;

    private final ArrayList<RectF> iBlur3Positions = new ArrayList<>();
    private final RectF iBlur3PositionActionBar = new RectF();
    private final RectF iBlur3PositionMainTabs = new RectF(); {
        iBlur3Positions.add(iBlur3PositionActionBar);
        iBlur3Positions.add(iBlur3PositionMainTabs);
    }

    private void blur3_InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
            return;
        }

        final int additionalList = dp(48);
        final int mainTabBottom = fragmentView.getMeasuredHeight() - navigationBarHeight - dp(DialogsActivity.MAIN_TABS_MARGIN);
        final int mainTabTop = mainTabBottom - dp(DialogsActivity.MAIN_TABS_HEIGHT);

        iBlur3PositionActionBar.set(0, -additionalList, fragmentView.getMeasuredWidth(), actionBar.getMeasuredHeight() + additionalList);
        iBlur3PositionMainTabs.set(0, mainTabTop, fragmentView.getMeasuredWidth(), mainTabBottom);
        iBlur3PositionMainTabs.inset(0, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : -dp(48));

        scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, hasMainTabs ? 2 : 1);
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return iBlur3SourceGlass;
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
}

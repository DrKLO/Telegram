/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_ACCOUNTS;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_BOOSTS_FOR_USERS;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.os.StatFs;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.core.app.ActivityCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.api.Status;
import com.google.common.primitives.Longs;
import com.google.firebase.appindexing.Action;
import com.google.firebase.appindexing.FirebaseUserActions;
import com.google.firebase.appindexing.builders.AssistActionBuilder;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AutoDeleteMediaTask;
import org.telegram.messenger.BackupAgent;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChannelBoostsController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ContactsLoadingObserver;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.FlagSecureReason;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.OpenAttachedMenuBotReceiver;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SharedPrefsHelper;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPPendingCall;
import org.telegram.messenger.voip.VoIPPreNotificationService;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_chatlists;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AppIconBulletinLayout;
import org.telegram.ui.Components.AttachBotIntroTopView;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.BatteryDrawable;
import org.telegram.ui.Components.BlockingUpdateView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.FolderBottomSheet;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.PasscodeViewDialog;
import org.telegram.ui.Components.PhonebookShareAlert;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.boosts.BoostPagerBottomSheet;
import org.telegram.ui.Components.Premium.boosts.GiftInfoBottomSheet;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchTagsList;
import org.telegram.ui.Components.SharingLocationsAlert;
import org.telegram.ui.Components.SideMenultItemAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerSetBulletinLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.TermsOfServiceView;
import org.telegram.ui.Components.ThemeEditorView;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stars.ISuperRipple;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.SuperRipple;
import org.telegram.ui.Stars.SuperRippleFallback;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.StoryViewer;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.bots.BotWebViewAttachedSheet;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.bots.WebViewRequestProps;
import org.webrtc.voiceengine.WebRtcAudioTrack;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LaunchActivity extends BasePermissionsActivity implements INavigationLayout.INavigationLayoutDelegate, NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate {
    public final static String EXTRA_FORCE_NOT_INTERNAL_APPS = "force_not_internal_apps";
    public final static String EXTRA_FORCE_REQUEST = "force_request";
    public final static Pattern PREFIX_T_ME_PATTERN = Pattern.compile("^(?:http(?:s|)://|)([A-z0-9-]+?)\\.t\\.me");

    public static boolean isActive;
    public static boolean isResumed;
    public static Runnable onResumeStaticCallback;

    private static final String EXTRA_ACTION_TOKEN = "actions.fulfillment.extra.ACTION_TOKEN";
    public ArrayList<INavigationLayout> sheetFragmentsStack = new ArrayList<>();

    private boolean finished;
    private String videoPath;
    private String voicePath;
    private String sendingText;
    private ArrayList<SendMessagesHelper.SendingMediaInfo> photoPathsArray;
    private ArrayList<String> documentsPathsArray;
    private ArrayList<Uri> documentsUrisArray;
    private Uri exportingChatUri;
    private String documentsMimeType;
    private ArrayList<String> documentsOriginalPathsArray;
    private ArrayList<TLRPC.User> contactsToSend;
    private Uri contactsToSendUri;
    private int currentConnectionState;
    private final static ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<>();
    private final static ArrayList<BaseFragment> layerFragmentsStack = new ArrayList<>();
    private final static ArrayList<BaseFragment> rightFragmentsStack = new ArrayList<>();
    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;
    private ArrayList<Parcelable> importingStickers;
    private ArrayList<String> importingStickersEmoji;
    private String importingStickersSoftware;

    private ActionMode visibleActionMode;

    private boolean wasMutedByAdminRaisedHand;

    private ImageView themeSwitchImageView;
    private View themeSwitchSunView;
    private RLottieDrawable themeSwitchSunDrawable;
    private ActionBarLayout actionBarLayout;
    private ActionBarLayout layersActionBarLayout;
    private ActionBarLayout rightActionBarLayout;
    private RelativeLayout launchLayout;
    private FrameLayout shadowTablet;
    private FrameLayout shadowTabletSide;
    private SizeNotifierFrameLayout backgroundTablet;
    public FrameLayout frameLayout;
    private FireworksOverlay fireworksOverlay;
    private BottomSheetTabsOverlay bottomSheetTabsOverlay;
    public DrawerLayoutContainer drawerLayoutContainer;
    private DrawerLayoutAdapter drawerLayoutAdapter;
    private PasscodeViewDialog passcodeDialog;
    private List<PasscodeView> overlayPasscodeViews = new ArrayList<>();
    private TermsOfServiceView termsOfServiceView;
    private BlockingUpdateView blockingUpdateView;
    public final ArrayList<Dialog> visibleDialogs = new ArrayList<>();
    private Dialog proxyErrorDialog;
    private RecyclerListView sideMenu;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
    private SideMenultItemAnimator itemAnimator;
    private FrameLayout sideMenuContainer;
    private View rippleAbove;
    private IUpdateLayout updateLayout;
    public Dialog getVisibleDialog() {
        for (int i = visibleDialogs.size() - 1; i >= 0; --i) {
            Dialog dialog = visibleDialogs.get(i);
            if (dialog.isShowing()) {
                return dialog;
            }
        }
        return null;
    }

    public FrameLayout getFrameLayout() {
        return frameLayout;
    }

    private Dialog localeDialog;
    private boolean loadingLocaleDialog;
    private HashMap<String, String> systemLocaleStrings;
    private HashMap<String, String> englishLocaleStrings;

    private Intent passcodeSaveIntent;
    private boolean passcodeSaveIntentIsNew;
    private boolean passcodeSaveIntentIsRestore;

    private boolean tabletFullSize;

    private String loadingThemeFileName;
    private String loadingThemeWallpaperName;
    private TLRPC.TL_wallPaper loadingThemeWallpaper;
    private Theme.ThemeInfo loadingThemeInfo;
    private TLRPC.TL_theme loadingTheme;
    private boolean loadingThemeAccent;
    private AlertDialog loadingThemeProgressDialog;

    private boolean isNavigationBarColorFrozen = false;

    private boolean navigateToPremiumBot;
    private Runnable navigateToPremiumGiftCallback;

    private Runnable lockRunnable;

    private List<Runnable> onUserLeaveHintListeners = new ArrayList<>();

    private static final int PLAY_SERVICES_REQUEST_CHECK_SETTINGS = 140;
    public static final int SCREEN_CAPTURE_REQUEST_CODE = 520;
    public static final int WEBVIEW_SHARE_API_REQUEST_CODE = 521;

    public static final int BLUETOOTH_CONNECT_TYPE = 0;
    private SparseIntArray requestedPermissions = new SparseIntArray();
    private int requsetPermissionsPointer = 5934;
    public static boolean systemBlurEnabled;
    private Consumer<Boolean> blurListener = new Consumer<Boolean>() {
        @Override
        public void accept(Boolean aBoolean) {
            systemBlurEnabled = aBoolean;
        }
    };

    private FlagSecureReason flagSecureReason;

    public static LaunchActivity instance;
    private View customNavigationBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isActive = true;
        if (BuildVars.DEBUG_VERSION) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());
        }
        instance = this;
        ApplicationLoader.postInitApplication();
        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());
        currentAccount = UserConfig.selectedAccount;
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            Intent intent = getIntent();
            boolean isProxy = false;
            if (intent != null && intent.getAction() != null) {
                if (Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                    super.onCreate(savedInstanceState);
                    finish();
                    return;
                } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        String url = uri.toString().toLowerCase();
                        isProxy = url.startsWith("tg:proxy") || url.startsWith("tg://proxy") || url.startsWith("tg:socks") || url.startsWith("tg://socks");
                    }
                }
            }
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                setTaskDescription(new ActivityManager.TaskDescription(null, null, Theme.getColor(Theme.key_actionBarDefault) | 0xff000000));
            } catch (Throwable ignore) {

            }
            try {
                getWindow().setNavigationBarColor(0xff000000);
            } catch (Throwable ignore) {

            }
        }
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        flagSecureReason = new FlagSecureReason(getWindow(), () -> SharedConfig.passcodeHash.length() > 0 && !SharedConfig.allowScreenCapture);
        flagSecureReason.attach();

        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 24) {
            AndroidUtilities.isInMultiwindow = isInMultiWindowMode();
        }
        Theme.createCommonChatResources();
        Theme.createDialogsResources(this);
        if (SharedConfig.passcodeHash.length() != 0 && SharedConfig.appLocked) {
            SharedConfig.lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000);
        }
        AndroidUtilities.fillStatusBarHeight(this, false);
        actionBarLayout = new ActionBarLayout(this, true);

        frameLayout = new FrameLayout(this) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                drawRippleAbove(canvas, this);
            }
        };
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        setContentView(frameLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 21) {
            themeSwitchImageView = new ImageView(this);
            themeSwitchImageView.setVisibility(View.GONE);
        }

        drawerLayoutContainer = new DrawerLayoutContainer(this) {
            private boolean wasPortrait;
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                setDrawerPosition(getDrawerPosition());

                boolean portrait = (b - t) > (r - l);
                if (portrait != wasPortrait) {
                    post(() -> {
                        if (selectAnimatedEmojiDialog != null) {
                            selectAnimatedEmojiDialog.dismiss();
                            selectAnimatedEmojiDialog = null;
                        }
                    });
                    wasPortrait = portrait;
                }
            }

            @Override
            public void closeDrawer() {
                super.closeDrawer();
                if (selectAnimatedEmojiDialog != null) {
                    selectAnimatedEmojiDialog.dismiss();
                    selectAnimatedEmojiDialog = null;
                }
            }

            @Override
            public void closeDrawer(boolean fast) {
                super.closeDrawer(fast);
                if (selectAnimatedEmojiDialog != null) {
                    selectAnimatedEmojiDialog.dismiss();
                    selectAnimatedEmojiDialog = null;
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (actionBarLayout.getParent() == this) {
                    actionBarLayout.parentDraw(this, canvas);
                }
                super.dispatchDraw(canvas);
            }
        };
        drawerLayoutContainer.setClipChildren(false);
        drawerLayoutContainer.setClipToPadding(false);
        drawerLayoutContainer.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        frameLayout.addView(drawerLayoutContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (Build.VERSION.SDK_INT >= 21) {
            themeSwitchSunView = new View(this) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (themeSwitchSunDrawable != null) {
                        themeSwitchSunDrawable.draw(canvas);
                        invalidate();
                    }
                }
            };
            frameLayout.addView(themeSwitchSunView, LayoutHelper.createFrame(48, 48));
            themeSwitchSunView.setVisibility(View.GONE);
        }
        frameLayout.addView(bottomSheetTabsOverlay = new BottomSheetTabsOverlay(this));
        frameLayout.addView(fireworksOverlay = new FireworksOverlay(this) {
            {
                setVisibility(GONE);
            }

            @Override
            public void start(boolean withStars) {
                setVisibility(VISIBLE);
                super.start(withStars);
            }

            @Override
            protected void onStop() {
                super.onStop();
                setVisibility(GONE);
            }
        });
        setupActionBarLayout();
        sideMenuContainer = new FrameLayout(this);
        sideMenu = new RecyclerListView(this) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                int restore = -1;
                if (itemAnimator != null && itemAnimator.isRunning() && itemAnimator.isAnimatingChild(child)) {
                    restore = canvas.save();
                    canvas.clipRect(0, itemAnimator.getAnimationClipTop(), getMeasuredWidth(), getMeasuredHeight());
                }
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (restore >= 0) {
                    canvas.restoreToCount(restore);
                    invalidate();
                    invalidateViews();
                }
                return result;
            }
        };
        itemAnimator = new SideMenultItemAnimator(sideMenu);
        sideMenu.setItemAnimator(itemAnimator);
        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenuContainer.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        sideMenu.setAllowItemsInteractionDuringAnimation(false);
        sideMenu.setAdapter(drawerLayoutAdapter = new DrawerLayoutAdapter(this, itemAnimator, drawerLayoutContainer));
        drawerLayoutAdapter.setOnPremiumDrawableClick(e -> showSelectStatusDialog());
        sideMenuContainer.addView(sideMenu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        drawerLayoutContainer.setDrawerLayout(sideMenuContainer, sideMenu);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sideMenuContainer.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet() ? AndroidUtilities.dp(320) : Math.min(AndroidUtilities.dp(320), Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56));
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        sideMenuContainer.setLayoutParams(layoutParams);
        sideMenu.setOnItemClickListener((view, position, x, y) -> {
            if (drawerLayoutAdapter.click(view, position)) {
                drawerLayoutContainer.closeDrawer(false);
                return;
            }
            if (position == 0) {
                DrawerProfileCell profileCell = (DrawerProfileCell) view;
                if (profileCell.isInAvatar(x, y)) {
                    openSettings(profileCell.hasAvatar());
                } else {
                    drawerLayoutAdapter.setAccountsShown(!drawerLayoutAdapter.isAccountsShown(), true);
                }
            } else if (view instanceof DrawerUserCell) {
                switchToAccount(((DrawerUserCell) view).getAccountNumber(), true);
                drawerLayoutContainer.closeDrawer(false);
            } else if (view instanceof DrawerAddCell) {
                int freeAccounts = 0;
                Integer availableAccount = null;
                for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
                    if (!UserConfig.getInstance(a).isClientActivated()) {
                        freeAccounts++;
                        if (availableAccount == null) {
                            availableAccount = a;
                        }
                    }
                }
                if (!UserConfig.hasPremiumOnAccounts()) {
                    freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
                }
                if (freeAccounts > 0 && availableAccount != null) {
                    presentFragment(new LoginActivity(availableAccount));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (!UserConfig.hasPremiumOnAccounts()) {
                    if (actionBarLayout.getFragmentStack().size() > 0) {
                        BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(fragment, this, TYPE_ACCOUNTS, currentAccount, null);
                        fragment.showDialog(limitReachedBottomSheet);
                        limitReachedBottomSheet.onShowPremiumScreenRunnable = () -> drawerLayoutContainer.closeDrawer(false);
                    }
                }
            } else {
                int id = drawerLayoutAdapter.getId(position);
                TLRPC.TL_attachMenuBot attachMenuBot = drawerLayoutAdapter.getAttachMenuBot(position);
                if (attachMenuBot != null) {
                    if (attachMenuBot.inactive || attachMenuBot.side_menu_disclaimer_needed) {
                        WebAppDisclaimerAlert.show(this, (allowSendMessage) -> {
                            TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                            botRequest.bot = MessagesController.getInstance(currentAccount).getInputUser(attachMenuBot.bot_id);
                            botRequest.enabled = true;
                            botRequest.write_allowed = true;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                attachMenuBot.inactive = attachMenuBot.side_menu_disclaimer_needed = false;
                                showAttachMenuBot(attachMenuBot, null, true);
                                MediaDataController.getInstance(currentAccount).updateAttachMenuBotsInCache();
                            }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                        }, null, null);
                    } else {
                        showAttachMenuBot(attachMenuBot, null, true);
                    }
                    return;
                }
                if (id == 2) {
                    Bundle args = new Bundle();
                    presentFragment(new GroupCreateActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 3) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlyUsers", true);
                    args.putBoolean("destroyAfterSelect", true);
                    args.putBoolean("createSecretChat", true);
                    args.putBoolean("allowBots", false);
                    args.putBoolean("allowSelf", false);
                    presentFragment(new ContactsActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 4) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                        Bundle args = new Bundle();
                        args.putInt("step", 0);
                        presentFragment(new ChannelCreateActivity(args));
                    } else {
                        presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                        preferences.edit().putBoolean("channel_intro", true).commit();
                    }
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 6) {
                    Bundle args = new Bundle();
                    args.putBoolean("needFinishFragment", false);
                    presentFragment(new ContactsActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 7) {
                    presentFragment(new InviteContactsActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 8) {
                    openSettings(false);
                } else if (id == 9) {
                    Browser.openUrl(LaunchActivity.this, LocaleController.getString(R.string.TelegramFaqUrl));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 10) {
                    presentFragment(new CallLogActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 11) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    presentFragment(new ChatActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 13) {
                    Browser.openUrl(LaunchActivity.this, LocaleController.getString(R.string.TelegramFeaturesUrl));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 15) {
                    showSelectStatusDialog();
                } else if (id == 16) {
                    drawerLayoutContainer.closeDrawer(true);
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    args.putBoolean("my_profile", true);
                    presentFragment(new ProfileActivity(args, null));
                } else if (id == 17) {
                    drawerLayoutContainer.closeDrawer(true);
                    Bundle args = new Bundle();
                    args.putLong("dialog_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    args.putInt("type", MediaActivity.TYPE_STORIES);
                    presentFragment(new MediaActivity(args, null));
                }
            }
        });
        final ItemTouchHelper sideMenuTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            private RecyclerView.ViewHolder selectedViewHolder;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (viewHolder.getItemViewType() != target.getItemViewType()) {
                    return false;
                }
                drawerLayoutAdapter.swapElements(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                clearSelectedViewHolder();
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                    selectedViewHolder = viewHolder;
                    final View view = viewHolder.itemView;
                    sideMenu.cancelClickRunnables(false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
                    if (Build.VERSION.SDK_INT >= 21) {
                        ObjectAnimator.ofFloat(view, "elevation", AndroidUtilities.dp(1)).setDuration(150).start();
                    }
                }
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                clearSelectedViewHolder();
            }

            private void clearSelectedViewHolder() {
                if (selectedViewHolder != null) {
                    final View view = selectedViewHolder.itemView;
                    selectedViewHolder = null;
                    view.setTranslationX(0f);
                    view.setTranslationY(0f);
                    if (Build.VERSION.SDK_INT >= 21) {
                        final ObjectAnimator animator = ObjectAnimator.ofFloat(view, "elevation", 0f);
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                view.setBackground(null);
                            }
                        });
                        animator.setDuration(150).start();
                    }
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                final View view = viewHolder.itemView;
                if (drawerLayoutAdapter.isAccountsShown()) {
                    RecyclerView.ViewHolder topViewHolder = recyclerView.findViewHolderForAdapterPosition(drawerLayoutAdapter.getFirstAccountPosition() - 1);
                    RecyclerView.ViewHolder bottomViewHolder = recyclerView.findViewHolderForAdapterPosition(drawerLayoutAdapter.getLastAccountPosition() + 1);
                    if (topViewHolder != null && topViewHolder.itemView != null && topViewHolder.itemView.getBottom() == view.getTop() && dY < 0f) {
                        dY = 0f;
                    } else if (bottomViewHolder != null && bottomViewHolder.itemView != null && bottomViewHolder.itemView.getTop() == view.getBottom() && dY > 0f) {
                        dY = 0f;
                    }
                }
                view.setTranslationX(dX);
                view.setTranslationY(dY);
            }
        });
        sideMenuTouchHelper.attachToRecyclerView(sideMenu);
        sideMenu.setOnItemLongClickListener((view, position) -> {
            if (view instanceof DrawerUserCell) {
                final int accountNumber = ((DrawerUserCell) view).getAccountNumber();
                if (accountNumber == currentAccount || AndroidUtilities.isTablet()) {
                    sideMenuTouchHelper.startDrag(sideMenu.getChildViewHolder(view));
                } else {
                    final BaseFragment fragment = new DialogsActivity(null) {
                        @Override
                        public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                            super.onTransitionAnimationEnd(isOpen, backward);
                            if (!isOpen && backward) { // closed
                                drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(false);
                                actionBarLayout.getView().invalidate();
                            }
                        }

                        @Override
                        public void onPreviewOpenAnimationEnd() {
                            super.onPreviewOpenAnimationEnd();
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                            drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(false);
                            switchToAccount(accountNumber, true);
                            actionBarLayout.getView().invalidate();
                        }
                    };
                    fragment.setCurrentAccount(accountNumber);
                    actionBarLayout.presentFragmentAsPreview(fragment);
                    drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(true);
                    return true;
                }
            }
            if (view instanceof DrawerActionCell) {
                int id = drawerLayoutAdapter.getId(position);
                TLRPC.TL_attachMenuBot attachMenuBot = drawerLayoutAdapter.getAttachMenuBot(position);
                if (attachMenuBot != null) {
                    BotWebViewSheet.deleteBot(currentAccount, attachMenuBot.bot_id, null);
                    return true;
                }
            }
            return false;
        });
        updateLayout = ApplicationLoader.applicationLoaderInstance.takeUpdateLayout(this, sideMenu, sideMenuContainer);
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
        actionBarLayout.setFragmentStack(mainFragmentsStack);
        actionBarLayout.setFragmentStackChangedListener(() -> {
            checkSystemBarColors(true, false);
            if (getLastFragment() != null && getLastFragment().getLastStoryViewer() != null) {
                getLastFragment().getLastStoryViewer().updatePlayingMode();
            }
        });
        actionBarLayout.setDelegate(this);
        Theme.loadWallpaper(true);

        checkCurrentAccount();
        updateCurrentConnectionState(currentAccount);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);

        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needCheckSystemBarColors);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.screenStateChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.showBulletin);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.requestPermissions);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.billingConfirmPurchaseError);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        LiteMode.addOnPowerSaverAppliedListener(this::onPowerSaver);
        if (actionBarLayout.getFragmentStack().isEmpty() && (layersActionBarLayout == null || layersActionBarLayout.getFragmentStack().isEmpty())) {
            if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                actionBarLayout.addFragmentToStack(getClientNotActivatedFragment());
                drawerLayoutContainer.setAllowOpenDrawer(false, false);
            } else {
                DialogsActivity dialogsActivity = new DialogsActivity(null);
                dialogsActivity.setSideMenu(sideMenu);
                actionBarLayout.addFragmentToStack(dialogsActivity);
                drawerLayoutContainer.setAllowOpenDrawer(true, false);
            }

            try {
                if (savedInstanceState != null) {
                    String fragmentName = savedInstanceState.getString("fragment");
                    if (fragmentName != null) {
                        Bundle args = savedInstanceState.getBundle("args");
                        switch (fragmentName) {
                            case "chat":
                                if (args != null) {
                                    ChatActivity chat = new ChatActivity(args);
                                    if (actionBarLayout.addFragmentToStack(chat)) {
                                        chat.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                                break;
                            case "settings": {
                                args.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId);
                                ProfileActivity settings = new ProfileActivity(args);
                                actionBarLayout.addFragmentToStack(settings);
                                settings.restoreSelfArgs(savedInstanceState);
                                break;
                            }
                            case "group":
                                if (args != null) {
                                    GroupCreateFinalActivity group = new GroupCreateFinalActivity(args);
                                    if (actionBarLayout.addFragmentToStack(group)) {
                                        group.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                                break;
                            case "channel":
                                if (args != null) {
                                    ChannelCreateActivity channel = new ChannelCreateActivity(args);
                                    if (actionBarLayout.addFragmentToStack(channel)) {
                                        channel.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                                break;
                            case "chat_profile":
                                if (args != null) {
                                    ProfileActivity profile = new ProfileActivity(args);
                                    if (actionBarLayout.addFragmentToStack(profile)) {
                                        profile.restoreSelfArgs(savedInstanceState);
                                    }
                                }
                                break;
                            case "wallpapers": {
                                WallpapersListActivity settings = new WallpapersListActivity(WallpapersListActivity.TYPE_ALL);
                                actionBarLayout.addFragmentToStack(settings);
                                settings.restoreSelfArgs(savedInstanceState);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            BaseFragment fragment = actionBarLayout.getFragmentStack().size() > 0 ? actionBarLayout.getFragmentStack().get(0) : layersActionBarLayout.getFragmentStack().get(0);
            if (fragment instanceof DialogsActivity) {
                ((DialogsActivity) fragment).setSideMenu(sideMenu);
            }
            boolean allowOpen = true;
            if (AndroidUtilities.isTablet()) {
                allowOpen = actionBarLayout.getFragmentStack().size() <= 1 && layersActionBarLayout.getFragmentStack().isEmpty();
                if (layersActionBarLayout.getFragmentStack().size() == 1 && (layersActionBarLayout.getFragmentStack().get(0) instanceof LoginActivity || layersActionBarLayout.getFragmentStack().get(0) instanceof IntroActivity)) {
                    allowOpen = false;
                }
            }
            if (actionBarLayout.getFragmentStack().size() == 1 && (actionBarLayout.getFragmentStack().get(0) instanceof LoginActivity || actionBarLayout.getFragmentStack().get(0) instanceof IntroActivity)) {
                allowOpen = false;
            }
            drawerLayoutContainer.setAllowOpenDrawer(allowOpen, false);
        }
        checkLayout();
        checkSystemBarColors();
        handleIntent(getIntent(), false, savedInstanceState != null, false, null, true, true);
        try {
            String os1 = Build.DISPLAY;
            String os2 = Build.USER;
            if (os1 != null) {
                os1 = os1.toLowerCase();
            } else {
                os1 = "";
            }
            if (os2 != null) {
                os2 = os1.toLowerCase();
            } else {
                os2 = "";
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("OS name " + os1 + " " + os2);
            }
            if ((os1.contains("flyme") || os2.contains("flyme")) && Build.VERSION.SDK_INT <= 24) {
                AndroidUtilities.incorrectDisplaySizeFix = true;
                final View view = getWindow().getDecorView().getRootView();
                view.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener = () -> {
                    int height = view.getMeasuredHeight();
                    FileLog.d("height = " + height + " displayHeight = " + AndroidUtilities.displaySize.y);
                    if (Build.VERSION.SDK_INT >= 21) {
                        height -= AndroidUtilities.statusBarHeight;
                    }
                    if (height > AndroidUtilities.dp(100) && height < AndroidUtilities.displaySize.y && height + AndroidUtilities.dp(100) > AndroidUtilities.displaySize.y) {
                        AndroidUtilities.displaySize.y = height;
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("fix display size y to " + AndroidUtilities.displaySize.y);
                        }
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        MediaController.getInstance().setBaseActivity(this, true);
        ApplicationLoader.startAppCenter(this);
        if (updateLayout != null) {
            updateLayout.updateAppUpdateViews(currentAccount, false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintController.checkKeyReady();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am.isBackgroundRestricted() && System.currentTimeMillis() - SharedConfig.BackgroundActivityPrefs.getLastCheckedBackgroundActivity() >= 86400000L && SharedConfig.BackgroundActivityPrefs.getDismissedCount() < 3) {
                AlertsCreator.createBackgroundActivityDialog(this).show();
                SharedConfig.BackgroundActivityPrefs.setLastCheckedBackgroundActivity(System.currentTimeMillis());
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            getWindowManager().addCrossWindowBlurEnabledListener(blurListener);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            getWindowManager().removeCrossWindowBlurEnabledListener(blurListener);
                        }
                    });
        }
        BackupAgent.requestBackup(this);

        RestrictedLanguagesSelectActivity.checkRestrictedLanguages(false);
    }

    private void showAttachMenuBot(TLRPC.TL_attachMenuBot attachMenuBot, String startApp, boolean sidemenu) {
        drawerLayoutContainer.closeDrawer();
        BaseFragment lastFragment = getLastFragment();
        if (lastFragment == null) return;
        WebViewRequestProps props = WebViewRequestProps.of(currentAccount, attachMenuBot.bot_id, attachMenuBot.bot_id, attachMenuBot.short_name, null, BotWebViewAttachedSheet.TYPE_SIMPLE_WEB_VIEW_BUTTON, 0, false, null, false, startApp, null, BotWebViewSheet.FLAG_FROM_SIDE_MENU, false, false);
        if (getBottomSheetTabs() != null && getBottomSheetTabs().tryReopenTab(props) != null) {
            return;
        }
//        if (AndroidUtilities.isTablet() || true) {
            BotWebViewSheet webViewSheet = new BotWebViewSheet(this, lastFragment.getResourceProvider());
            webViewSheet.setNeedsContext(false);
            webViewSheet.setDefaultFullsize(sidemenu);
            webViewSheet.setParentActivity(this);
            webViewSheet.requestWebView(lastFragment, props);
            webViewSheet.show();
//        } else {
//            BaseFragment fragment = lastFragment;
//            if (fragment.getParentLayout() instanceof ActionBarLayout) {
//                fragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment();
//            }
//            BotWebViewAttachedSheet webViewSheet = fragment.createBotViewer();
//            webViewSheet.setNeedsContext(false);
//            webViewSheet.setDefaultFullsize(sidemenu);
//            webViewSheet.setParentActivity(this);
//            webViewSheet.requestWebView(lastFragment, props);
//            webViewSheet.show();
//        }
    }

    @Override
    public void onThemeProgress(float progress) {
        if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().updateThemeColors(progress);
        }
        drawerLayoutContainer.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (PhotoViewer.hasInstance()) {
            PhotoViewer.getInstance().updateColors();
        }
    }

    private void setupActionBarLayout() {
        int i = drawerLayoutContainer.indexOfChild(launchLayout) != -1 ? drawerLayoutContainer.indexOfChild(launchLayout) : drawerLayoutContainer.indexOfChild(actionBarLayout.getView());
        if (i != -1) {
            drawerLayoutContainer.removeViewAt(i);
        }
        if (AndroidUtilities.isTablet()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            launchLayout = new RelativeLayout(this) {
                private Path path = new Path();
                private boolean inLayout;

                @Override
                public void requestLayout() {
                    if (inLayout) {
                        return;
                    }
                    super.requestLayout();
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    inLayout = true;
                    int width = MeasureSpec.getSize(widthMeasureSpec);
                    int height = MeasureSpec.getSize(heightMeasureSpec);
                    setMeasuredDimension(width, height);

                    if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)) {
                        tabletFullSize = false;
                        int leftWidth = width / 100 * 35;
                        if (leftWidth < AndroidUtilities.dp(320)) {
                            leftWidth = AndroidUtilities.dp(320);
                        }
                        actionBarLayout.getView().measure(MeasureSpec.makeMeasureSpec(leftWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        shadowTabletSide.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        rightActionBarLayout.getView().measure(MeasureSpec.makeMeasureSpec(width - leftWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    } else {
                        tabletFullSize = true;
                        actionBarLayout.getView().measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                    backgroundTablet.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    shadowTablet.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    layersActionBarLayout.getView().measure(MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(530), width - AndroidUtilities.dp(16)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(16), MeasureSpec.EXACTLY));

                    inLayout = false;
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    int width = r - l;
                    int height = b - t;

                    if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)) {
                        int leftWidth = width / 100 * 35;
                        if (leftWidth < AndroidUtilities.dp(320)) {
                            leftWidth = AndroidUtilities.dp(320);
                        }
                        shadowTabletSide.layout(leftWidth, 0, leftWidth + shadowTabletSide.getMeasuredWidth(), shadowTabletSide.getMeasuredHeight());
                        actionBarLayout.getView().layout(0, 0, actionBarLayout.getView().getMeasuredWidth(), actionBarLayout.getView().getMeasuredHeight());
                        rightActionBarLayout.getView().layout(leftWidth, 0, leftWidth + rightActionBarLayout.getView().getMeasuredWidth(), rightActionBarLayout.getView().getMeasuredHeight());
                    } else {
                        actionBarLayout.getView().layout(0, 0, actionBarLayout.getView().getMeasuredWidth(), actionBarLayout.getView().getMeasuredHeight());
                    }
                    int x = (width - layersActionBarLayout.getView().getMeasuredWidth()) / 2;
                    int y = (height - layersActionBarLayout.getView().getMeasuredHeight() + AndroidUtilities.statusBarHeight) / 2;
                    layersActionBarLayout.getView().layout(x, y, x + layersActionBarLayout.getView().getMeasuredWidth(), y + layersActionBarLayout.getView().getMeasuredHeight());
                    backgroundTablet.layout(0, 0, backgroundTablet.getMeasuredWidth(), backgroundTablet.getMeasuredHeight());
                    shadowTablet.layout(0, 0, shadowTablet.getMeasuredWidth(), shadowTablet.getMeasuredHeight());
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (layersActionBarLayout != null) {
                        layersActionBarLayout.parentDraw(this, canvas);
                    }
                    super.dispatchDraw(canvas);
                }
            };
            if (i != -1) {
                drawerLayoutContainer.addView(launchLayout, i, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            } else {
                drawerLayoutContainer.addView(launchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }

            backgroundTablet = new SizeNotifierFrameLayout(this) {
                @Override
                protected boolean isActionBarVisible() {
                    return false;
                }
            };
            backgroundTablet.setOccupyStatusBar(false);
            backgroundTablet.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            launchLayout.addView(backgroundTablet, LayoutHelper.createRelative(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            ViewGroup parent = (ViewGroup) actionBarLayout.getView().getParent();
            if (parent != null) {
                parent.removeView(actionBarLayout.getView());
            }
            launchLayout.addView(actionBarLayout.getView());

            rightActionBarLayout = new ActionBarLayout(this, false);
            rightActionBarLayout.setFragmentStack(rightFragmentsStack);
            rightActionBarLayout.setDelegate(this);
            launchLayout.addView(rightActionBarLayout.getView());

            shadowTabletSide = new FrameLayout(this);
            shadowTabletSide.setBackgroundColor(0x40295274);
            launchLayout.addView(shadowTabletSide);

            shadowTablet = new FrameLayout(this);
            shadowTablet.setVisibility(layerFragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
            shadowTablet.setBackgroundColor(0x7f000000);
            launchLayout.addView(shadowTablet);
            shadowTablet.setOnTouchListener((v, event) -> {
                if (!actionBarLayout.getFragmentStack().isEmpty() && event.getAction() == MotionEvent.ACTION_UP) {
                    float x = event.getX();
                    float y = event.getY();
                    int[] location = new int[2];
                    layersActionBarLayout.getView().getLocationOnScreen(location);
                    int viewX = location[0];
                    int viewY = location[1];

                    if (layersActionBarLayout.checkTransitionAnimation() || x > viewX && x < viewX + layersActionBarLayout.getView().getWidth() && y > viewY && y < viewY + layersActionBarLayout.getView().getHeight()) {
                        return false;
                    } else {
                        if (!layersActionBarLayout.getFragmentStack().isEmpty()) {
                            for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                                layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                                a--;
                            }
                            layersActionBarLayout.closeLastFragment(true);
                        }
                        return true;
                    }
                }
                return false;
            });

            shadowTablet.setOnClickListener(v -> {

            });

            layersActionBarLayout = new ActionBarLayout(this, false);
            layersActionBarLayout.setRemoveActionBarExtraHeight(true);
            layersActionBarLayout.setBackgroundView(shadowTablet);
            layersActionBarLayout.setUseAlphaAnimations(true);
            layersActionBarLayout.setFragmentStack(layerFragmentsStack);
            layersActionBarLayout.setDelegate(this);
            layersActionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);

            View layersView = layersActionBarLayout.getView();
            layersView.setBackgroundResource(R.drawable.popup_fixed_alert3);
            layersView.setVisibility(layerFragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
            launchLayout.addView(layersView);
        } else {
            ViewGroup parent = (ViewGroup) actionBarLayout.getView().getParent();
            if (parent != null) {
                parent.removeView(actionBarLayout.getView());
            }

            actionBarLayout.setFragmentStack(mainFragmentsStack);
            if (i != -1) {
                drawerLayoutContainer.addView(actionBarLayout.getView(), i, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                drawerLayoutContainer.addView(actionBarLayout.getView(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
        FloatingDebugController.setActive(this, SharedConfig.isFloatingDebugActive, false);
    }

    public void addOnUserLeaveHintListener(Runnable callback) {
        onUserLeaveHintListeners.add(callback);
    }

    public void removeOnUserLeaveHintListener(Runnable callback) {
        onUserLeaveHintListeners.remove(callback);
    }

    private BaseFragment getClientNotActivatedFragment() {
        if (LoginActivity.loadCurrentState(false, currentAccount).getInt("currentViewNum", 0) != 0) {
            return new LoginActivity();
        }
        return new IntroActivity();
    }

    public void showSelectStatusDialog() {
        if (selectAnimatedEmojiDialog != null || SharedConfig.appLocked) {
            return;
        }
        BaseFragment fragment = actionBarLayout.getLastFragment();
        if (fragment == null) {
            return;
        }
        final View profileCell = sideMenu.getChildAt(0);
        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
        int xoff = 0, yoff = 0;
        boolean hasEmoji = false;
        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
        View scrimDrawableParent = null;
        if (profileCell instanceof DrawerProfileCell) {
            scrimDrawable = ((DrawerProfileCell) profileCell).getEmojiStatusDrawable();
            if (scrimDrawable != null) {
                scrimDrawable.play();
            }
            scrimDrawableParent = ((DrawerProfileCell) profileCell).getEmojiStatusDrawableParent();
            hasEmoji = scrimDrawable != null && scrimDrawable.getDrawable() instanceof AnimatedEmojiDrawable;
            ((DrawerProfileCell) profileCell).getEmojiStatusLocation(AndroidUtilities.rectTmp2);
            yoff = -(profileCell.getHeight() - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16);
            xoff = AndroidUtilities.rectTmp2.centerX();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    getWindow() != null &&
                    getWindow().getDecorView() != null &&
                    getWindow().getDecorView().getRootWindowInsets() != null
            ) {
                xoff -= getWindow().getDecorView().getRootWindowInsets().getStableInsetLeft();
            }
        }
        SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(fragment, this, true, xoff, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, null) {
            @Override
            public void onSettings() {
                if (drawerLayoutContainer != null) {
                    drawerLayoutContainer.closeDrawer();
                }
            }

            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, Integer until) {
                TLRPC.EmojiStatus emojiStatus;
                if (documentId == null) {
                    emojiStatus = new TLRPC.TL_emojiStatusEmpty();
                } else if (until != null) {
                    emojiStatus = new TLRPC.TL_emojiStatusUntil();
                    ((TLRPC.TL_emojiStatusUntil) emojiStatus).document_id = documentId;
                    ((TLRPC.TL_emojiStatusUntil) emojiStatus).until = until;
                } else {
                    emojiStatus = new TLRPC.TL_emojiStatus();
                    ((TLRPC.TL_emojiStatus) emojiStatus).document_id = documentId;
                }
                MessagesController.getInstance(currentAccount).updateEmojiStatus(emojiStatus);
                TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                if (user != null) {
                    for (int i = 0; i < sideMenu.getChildCount(); ++i) {
                        View child = sideMenu.getChildAt(i);
                        if (child instanceof DrawerUserCell) {
                            ((DrawerUserCell) child).setAccount(((DrawerUserCell) child).getAccountNumber());
                        } else if (child instanceof DrawerProfileCell) {
                            if (documentId != null) {
                                ((DrawerProfileCell) child).animateStateChange(documentId);
                            }
                            ((DrawerProfileCell) child).setUser(user, drawerLayoutAdapter.isAccountsShown());
                        } else if (child instanceof DrawerActionCell && drawerLayoutAdapter.getId(sideMenu.getChildAdapterPosition(child)) == 15) {
                            boolean hasStatus = user != null && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0;
                            ((DrawerActionCell) child).updateTextAndIcon(
                                getString(hasStatus ? R.string.ChangeEmojiStatus : R.string.SetEmojiStatus),
                                hasStatus ?
                                    R.drawable.msg_status_edit :
                                    R.drawable.msg_status_set
                            );
                        }
                    }
                }
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };
        if (user != null) {
            popupLayout.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        }
        popupLayout.setSelected(scrimDrawable != null && scrimDrawable.getDrawable() instanceof AnimatedEmojiDrawable ? ((AnimatedEmojiDrawable) scrimDrawable.getDrawable()).getDocumentId() : null);
        popupLayout.setSaveState(2);
        popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent);
        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        popup[0].showAsDropDown(sideMenu.getChildAt(0), 0, yoff, Gravity.TOP);
        popup[0].dimBehind();
    }

    public FireworksOverlay getFireworksOverlay() {
        return fireworksOverlay;
    }

    public BottomSheetTabsOverlay getBottomSheetTabsOverlay() {
        return bottomSheetTabsOverlay;
    }

    private void openSettings(boolean expanded) {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId);
        if (expanded) {
            args.putBoolean("expandPhoto", true);
        }
        ProfileActivity fragment = new ProfileActivity(args);
        presentFragment(fragment);
        drawerLayoutContainer.closeDrawer(false);
    }

    private void checkSystemBarColors() {
        checkSystemBarColors(false, true, !isNavigationBarColorFrozen, true);
    }

    public void checkSystemBarColors(boolean useCurrentFragment) {
        checkSystemBarColors(useCurrentFragment, true, !isNavigationBarColorFrozen, true);
    }

    private void checkSystemBarColors(boolean checkStatusBar, boolean checkNavigationBar) {
        checkSystemBarColors(false, checkStatusBar, checkNavigationBar, true);
    }

    public void checkSystemBarColors(boolean useCurrentFragment, boolean checkStatusBar, boolean checkNavigationBar, boolean checkButtons) {
        BaseFragment currentFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
        if (currentFragment != null && (currentFragment.isRemovingFromStack() || currentFragment.isInPreviewMode())) {
            currentFragment = mainFragmentsStack.size() > 1 ? mainFragmentsStack.get(mainFragmentsStack.size() - 2) : null;
        }
        boolean forceLightStatusBar = currentFragment != null && currentFragment.hasForceLightStatusBar();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkStatusBar) {
                boolean enable;
                if (currentFragment != null) {
                    enable = currentFragment.isLightStatusBar();
                    if (currentFragment.getParentLayout() instanceof ActionBarLayout) {
                        ActionBarLayout actionBarLayout1 = (ActionBarLayout) currentFragment.getParentLayout();
                        if (actionBarLayout1.getSheetFragment(false) != null && actionBarLayout1.getSheetFragment(false).getLastSheet() != null) {
//                            BaseFragment sheetFragment = actionBarLayout1.getSheetFragment(false);
                            BaseFragment.AttachedSheet sheet = actionBarLayout1.getSheetFragment(false).getLastSheet();
                            if (sheet.isShown()) {
                                enable = sheet.isAttachedLightStatusBar();
                            }
                        } else if (currentFragment.sheetsStack != null && !currentFragment.sheetsStack.isEmpty()) {
                            BaseFragment.AttachedSheet sheet = currentFragment.sheetsStack.get(currentFragment.sheetsStack.size() - 1);
                            if (sheet.isShown()) {
                                enable = sheet.isAttachedLightStatusBar();
                            }
                        }
                    }
                } else {
                    int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
                    enable = ColorUtils.calculateLuminance(color) > 0.7f;
                }
                AndroidUtilities.setLightStatusBar(getWindow(), enable, forceLightStatusBar);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && checkNavigationBar && (!useCurrentFragment || currentFragment == null || !currentFragment.isInPreviewMode())) {
                int color = currentFragment != null && useCurrentFragment ? currentFragment.getNavigationBarColor() : Theme.getColor(Theme.key_windowBackgroundGray, null, true);
                if (actionBarLayout.getSheetFragment(false) != null) {
                    BaseFragment sheetFragment = actionBarLayout.getSheetFragment(false);
                    if (sheetFragment.sheetsStack != null) {
                        for (int i = 0; i < sheetFragment.sheetsStack.size(); ++i) {
                            BaseFragment.AttachedSheet sheet = sheetFragment.sheetsStack.get(i);
                            if (sheet.attachedToParent()) {
                                color = sheet.getNavigationBarColor(color);
                            }
                        }
                    }
                }
                for (BotWebViewSheet sheet : BotWebViewSheet.activeSheets) {
                    color = sheet.getNavigationBarColor(color);
                }
                setNavigationBarColor(color, checkButtons);
                setLightNavigationBar(AndroidUtilities.computePerceivedBrightness(color) >= .721f);
            }
        }
        if ((SharedConfig.noStatusBar || forceLightStatusBar) && Build.VERSION.SDK_INT >= 21 && checkStatusBar) {
            getWindow().setStatusBarColor(0);
        }
    }

    public FrameLayout getMainContainerFrameLayout() {
        return frameLayout;
    }

    private boolean switchingAccount;
    public void switchToAccount(int account, boolean removeAll) {
        switchToAccount(account, removeAll, obj -> new DialogsActivity(null));
    }

    public void switchToAccount(int account, boolean removeAll, GenericProvider<Void, DialogsActivity> dialogsActivityProvider) {
        if (account == UserConfig.selectedAccount || !UserConfig.isValidAccount(account)) {
            return;
        }
        switchingAccount = true;

        ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        UserConfig.selectedAccount = account;
        UserConfig.getInstance(0).saveConfig(false);

        checkCurrentAccount();
        if (AndroidUtilities.isTablet()) {
            layersActionBarLayout.removeAllFragments();
            rightActionBarLayout.removeAllFragments();
            if (!tabletFullSize) {
                shadowTabletSide.setVisibility(View.VISIBLE);
                if (rightActionBarLayout.getFragmentStack().isEmpty()) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                }
                rightActionBarLayout.getView().setVisibility(View.GONE);
            }
            layersActionBarLayout.getView().setVisibility(View.GONE);
        }
        if (removeAll) {
            actionBarLayout.removeAllFragments();
        } else {
            actionBarLayout.removeFragmentFromStack(0);
        }
        DialogsActivity dialogsActivity = dialogsActivityProvider.provide(null);
        dialogsActivity.setSideMenu(sideMenu);
        actionBarLayout.addFragmentToStack(dialogsActivity, INavigationLayout.FORCE_ATTACH_VIEW_AS_FIRST);
        drawerLayoutContainer.setAllowOpenDrawer(true, false);
        actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        if (AndroidUtilities.isTablet()) {
            layersActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        }
        if (!ApplicationLoader.mainInterfacePaused) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        }
        if (UserConfig.getInstance(account).unacceptedTermsOfService != null) {
            showTosActivity(account, UserConfig.getInstance(account).unacceptedTermsOfService);
        }
        updateCurrentConnectionState(currentAccount);

        switchingAccount = false;
    }

    private void switchToAvailableAccountOrLogout() {
        int account = -1;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                account = a;
                break;
            }
        }
        if (termsOfServiceView != null) {
            termsOfServiceView.setVisibility(View.GONE);
        }
        if (account != -1) {
            switchToAccount(account, true);
        } else {
            if (drawerLayoutAdapter != null) {
                drawerLayoutAdapter.notifyDataSetChanged();
            }
            RestrictedLanguagesSelectActivity.checkRestrictedLanguages(true);
            clearFragments();
            actionBarLayout.rebuildLogout();
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.rebuildLogout();
                rightActionBarLayout.rebuildLogout();
            }
            presentFragment(new IntroActivity().setOnLogout());
        }
    }

    public static void clearFragments() {
        for (BaseFragment fragment : mainFragmentsStack) {
            fragment.onFragmentDestroy();
        }
        mainFragmentsStack.clear();
        if (AndroidUtilities.isTablet()) {
            for (BaseFragment fragment : layerFragmentsStack) {
                fragment.onFragmentDestroy();
            }
            layerFragmentsStack.clear();
            for (BaseFragment fragment : rightFragmentsStack) {
                fragment.onFragmentDestroy();
            }
            rightFragmentsStack.clear();
        }
    }

    public int getMainFragmentsCount() {
        return mainFragmentsStack.size();
    }

    private void checkCurrentAccount() {
        if (currentAccount != UserConfig.selectedAccount) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openBoostForUsersDialog);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openArticle);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.hasNewContactsToImport);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowPlayServicesAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.historyImportProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupCallUpdated);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersImportComplete);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newSuggestionsAvailable);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatSwithcedToForum);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesEnabledUpdate);
        }
        currentAccount = UserConfig.selectedAccount;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.openBoostForUsersDialog);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.attachMenuBotsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.openArticle);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.hasNewContactsToImport);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.needShowPlayServicesAlert);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.historyImportProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersImportComplete);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSuggestionsAvailable);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserShowLimitReachedDialog);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatSwithcedToForum);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesEnabledUpdate);
    }

    private void checkLayout() {
        if (!AndroidUtilities.isTablet() || rightActionBarLayout == null || AndroidUtilities.getWasTablet() != null && AndroidUtilities.getWasTablet() != AndroidUtilities.isTabletForce()) {
            return;
        }

        if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            tabletFullSize = false;
            List<BaseFragment> fragmentStack = actionBarLayout.getFragmentStack();
            if (fragmentStack.size() >= 2) {
                for (int a = 1; a < fragmentStack.size(); a++) {
                    BaseFragment chatFragment = fragmentStack.get(a);
                    if (chatFragment instanceof ChatActivity) {
                        ((ChatActivity) chatFragment).setIgnoreAttachOnPause(true);
                    }
                    chatFragment.onPause();
                    chatFragment.onFragmentDestroy();
                    chatFragment.setParentLayout(null);
                    fragmentStack.remove(chatFragment);
                    rightActionBarLayout.addFragmentToStack(chatFragment);
                    a--;
                }
                if (passcodeDialog == null || passcodeDialog.passcodeView.getVisibility() != View.VISIBLE) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                }
            }
            rightActionBarLayout.getView().setVisibility(rightActionBarLayout.getFragmentStack().isEmpty() ? View.GONE : View.VISIBLE);
            backgroundTablet.setVisibility(rightActionBarLayout.getFragmentStack().isEmpty() ? View.VISIBLE : View.GONE);
            shadowTabletSide.setVisibility(!actionBarLayout.getFragmentStack().isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            tabletFullSize = true;
            List<BaseFragment> fragmentStack = rightActionBarLayout.getFragmentStack();
            if (!fragmentStack.isEmpty()) {
                for (int a = 0; a < fragmentStack.size(); a++) {
                    BaseFragment chatFragment = fragmentStack.get(a);
                    if (chatFragment instanceof ChatActivity) {
                        ((ChatActivity) chatFragment).setIgnoreAttachOnPause(true);
                    }
                    chatFragment.onPause();
                    chatFragment.onFragmentDestroy();
                    chatFragment.setParentLayout(null);
                    fragmentStack.remove(chatFragment);
                    actionBarLayout.addFragmentToStack(chatFragment);
                    a--;
                }
                if (passcodeDialog == null || passcodeDialog.passcodeView.getVisibility() != View.VISIBLE) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                }
            }
            shadowTabletSide.setVisibility(View.GONE);
            rightActionBarLayout.getView().setVisibility(View.GONE);
            backgroundTablet.setVisibility(!actionBarLayout.getFragmentStack().isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void showUpdateActivity(int account, TLRPC.TL_help_appUpdate update, boolean check) {
        if (blockingUpdateView == null) {
            blockingUpdateView = new BlockingUpdateView(LaunchActivity.this) {
                @Override
                public void setVisibility(int visibility) {
                    super.setVisibility(visibility);
                    if (visibility == View.GONE) {
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                }
            };
            drawerLayoutContainer.addView(blockingUpdateView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        blockingUpdateView.show(account, update, check);
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
    }

    private void showTosActivity(int account, TLRPC.TL_help_termsOfService tos) {
        if (termsOfServiceView == null) {
            termsOfServiceView = new TermsOfServiceView(this);
            termsOfServiceView.setAlpha(0f);
            drawerLayoutContainer.addView(termsOfServiceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            termsOfServiceView.setDelegate(new TermsOfServiceView.TermsOfServiceViewDelegate() {
                @Override
                public void onAcceptTerms(int account) {
                    UserConfig.getInstance(account).unacceptedTermsOfService = null;
                    UserConfig.getInstance(account).saveConfig(false);
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    if (mainFragmentsStack.size() > 0) {
                        mainFragmentsStack.get(mainFragmentsStack.size() - 1).onResume();
                    }
                    termsOfServiceView.animate()
                            .alpha(0f)
                            .setDuration(150)
                            .setInterpolator(AndroidUtilities.accelerateInterpolator)
                            .withEndAction(() -> termsOfServiceView.setVisibility(View.GONE))
                            .start();
                }

                @Override
                public void onDeclineTerms(int account) {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    termsOfServiceView.setVisibility(View.GONE);
                }
            });
        }
        TLRPC.TL_help_termsOfService currentTos = UserConfig.getInstance(account).unacceptedTermsOfService;
        if (currentTos != tos && (currentTos == null || !currentTos.id.data.equals(tos.id.data))) {
            UserConfig.getInstance(account).unacceptedTermsOfService = tos;
            UserConfig.getInstance(account).saveConfig(false);
        }
        termsOfServiceView.show(account, tos);
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        termsOfServiceView.animate().alpha(1f).setDuration(150).setInterpolator(AndroidUtilities.decelerateInterpolator).setListener(null).start();
    }

    public void showPasscodeActivity(boolean fingerprint, boolean animated, int x, int y, Runnable onShow, Runnable onStart) {
        if (drawerLayoutContainer == null) {
            return;
        }
        if (passcodeDialog == null) {
            passcodeDialog = new PasscodeViewDialog(this);
        }
        if (selectAnimatedEmojiDialog != null) {
            selectAnimatedEmojiDialog.dismiss();
            selectAnimatedEmojiDialog = null;
        }
        SharedConfig.appLocked = true;
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(false, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        StoryRecorder.destroyInstance();
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null && messageObject.isRoundVideo()) {
            MediaController.getInstance().cleanupPlayer(true, true);
        }
        passcodeDialog.show();
        passcodeDialog.passcodeView.onShow(overlayPasscodeViews.isEmpty() && fingerprint, animated, x, y, () -> {
            actionBarLayout.getView().setVisibility(View.INVISIBLE);
            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout != null && layersActionBarLayout.getView() != null && layersActionBarLayout.getView().getVisibility() == View.VISIBLE) {
                    layersActionBarLayout.getView().setVisibility(View.INVISIBLE);
                }
                if (rightActionBarLayout != null && rightActionBarLayout.getView() != null) {
                    rightActionBarLayout.getView().setVisibility(View.INVISIBLE);
                }
            }
            if (onShow != null) {
                onShow.run();
            }
        }, onStart);
        for (int i = 0; i < overlayPasscodeViews.size(); i++) {
            PasscodeView overlay = overlayPasscodeViews.get(i);
            overlay.onShow(fingerprint && i == overlayPasscodeViews.size() - 1, animated, x, y, null, null);
        }
        SharedConfig.isWaitingForPasscodeEnter = true;
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        PasscodeView.PasscodeViewDelegate delegate = view -> {
            SharedConfig.isWaitingForPasscodeEnter = false;
            if (passcodeSaveIntent != null) {
                handleIntent(passcodeSaveIntent, passcodeSaveIntentIsNew, passcodeSaveIntentIsRestore, true, null, false, true);
                passcodeSaveIntent = null;
            }
            drawerLayoutContainer.setAllowOpenDrawer(true, false);
            actionBarLayout.getView().setVisibility(View.VISIBLE);
            actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            actionBarLayout.updateTitleOverlay();
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                if (layersActionBarLayout.getView().getVisibility() == View.INVISIBLE) {
                    layersActionBarLayout.getView().setVisibility(View.VISIBLE);
                }
                rightActionBarLayout.getView().setVisibility(View.VISIBLE);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.passcodeDismissed, view);
            try {
                NotificationsController.getInstance(UserConfig.selectedAccount).showNotifications();
            } catch (Exception e) {
                FileLog.e(e);
            }
        };
        passcodeDialog.passcodeView.setDelegate(delegate);
        for (PasscodeView overlay : overlayPasscodeViews) {
            overlay.setDelegate(delegate);
        }
        try {
            NotificationsController.getInstance(UserConfig.selectedAccount).showNotifications();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean allowShowFingerprintDialog(PasscodeView passcodeView) {
        return overlayPasscodeViews.isEmpty() && this.passcodeDialog != null ? passcodeView == this.passcodeDialog.passcodeView : overlayPasscodeViews.get(overlayPasscodeViews.size() - 1) == passcodeView;
    }

    private boolean handleIntent(Intent intent, boolean isNew, boolean restore, boolean fromPassword) {
        return handleIntent(intent, isNew, restore, fromPassword, null, true, false);
    }

    @SuppressLint("Range")
    private boolean handleIntent(Intent intent, boolean isNew, boolean restore, boolean fromPassword, Browser.Progress progress, boolean rebuildFragments, boolean openedTelegram) {
        if (GiftInfoBottomSheet.handleIntent(intent, progress)) {
            return true;
        }
        if (UserSelectorBottomSheet.handleIntent(intent, progress)) {
            return true;
        }
        if (AndroidUtilities.handleProxyIntent(this, intent)) {
            return true;
        }
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) {
            if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                PhotoViewer.getInstance().closePhoto(false, true);
            }
            StoryRecorder.destroyInstance();
//            dismissAllWeb();
        }
        if (webviewShareAPIDoneListener != null) {
            webviewShareAPIDoneListener.run(true);
            webviewShareAPIDoneListener = null;
        }
        int flags = intent.getFlags();
        String action = intent.getAction();
        final int[] intentAccount = new int[]{intent.getIntExtra("currentAccount", UserConfig.selectedAccount)};
        switchToAccount(intentAccount[0], true);
        boolean isVoipIntent = action != null && action.equals("voip");
        boolean isVoipAnswerIntent = action != null && action.equals("voip_answer");
        if (!fromPassword && (AndroidUtilities.needShowPasscode(true) || SharedConfig.isWaitingForPasscodeEnter)) {
            showPasscodeActivity(true, false, -1, -1, null, null);
            UserConfig.getInstance(currentAccount).saveConfig(false);
            if (!isVoipIntent && !isVoipAnswerIntent) {
                passcodeSaveIntent = intent;
                passcodeSaveIntentIsNew = isNew;
                passcodeSaveIntentIsRestore = restore;
                return false;
            }
        }
        boolean pushOpened = false;
        long push_user_id = 0;
        long push_chat_id = 0;
        long[] push_story_dids = null;
        int push_story_id = -1;
        long push_topic_id = 0;
        int push_enc_id = 0;
        int push_msg_id = 0;
        int open_settings = 0;
        int open_widget_edit = -1;
        int open_widget_edit_type = -1;
        int open_new_dialog = 0;
        long dialogId = 0;
        boolean showDialogsList = false;
        boolean showPlayer = false;
        boolean showLocations = false;
        boolean showGroupVoip = false;
        boolean showCallLog = false;
        boolean audioCallUser = false;
        boolean videoCallUser = false;
        boolean needCallAlert = false;
        boolean newContact = false;
        boolean newContactAlert = false;
        boolean scanQr = false;
        boolean openBot = false;
        long botId = 0;
        long botType = -1;
        String searchQuery = null;
        String callSearchQuery = null;
        String newContactName = null;
        String newContactPhone = null;
        boolean forceNotInternalForApps = intent.getBooleanExtra(EXTRA_FORCE_NOT_INTERNAL_APPS, false);
        boolean forceRequest = intent.getBooleanExtra(EXTRA_FORCE_REQUEST, false);

        photoPathsArray = null;
        videoPath = null;
        voicePath = null;
        sendingText = null;
        documentsPathsArray = null;
        documentsOriginalPathsArray = null;
        documentsMimeType = null;
        documentsUrisArray = null;
        exportingChatUri = null;
        contactsToSend = null;
        contactsToSendUri = null;
        importingStickers = null;
        importingStickersEmoji = null;
        importingStickersSoftware = null;

        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            if (intent != null && intent.getAction() != null && !restore) {
                if (Intent.ACTION_SEND.equals(intent.getAction())) {
                    if (SharedConfig.directShare && intent != null && intent.getExtras() != null) {
                        dialogId = intent.getExtras().getLong("dialogId", 0);
                        String hash = null;
                        if (dialogId == 0) {
                            try {
                                String id = intent.getExtras().getString(ShortcutManagerCompat.EXTRA_SHORTCUT_ID);
                                if (id != null) {
                                    List<ShortcutInfoCompat> list = ShortcutManagerCompat.getDynamicShortcuts(ApplicationLoader.applicationContext);
                                    for (int a = 0, N = list.size(); a < N; a++) {
                                        ShortcutInfoCompat info = list.get(a);
                                        if (id.equals(info.getId())) {
                                            Bundle extras = info.getIntent().getExtras();
                                            dialogId = extras.getLong("dialogId", 0);
                                            hash = extras.getString("hash", null);
                                            break;
                                        }
                                    }
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        } else {
                            hash = intent.getExtras().getString("hash", null);
                        }
                        if (SharedConfig.directShareHash == null || !SharedConfig.directShareHash.equals(hash)) {
                            dialogId = 0;
                        }
                    }

                    boolean error = false;
                    String type = intent.getType();
                    if (type != null && type.equals(ContactsContract.Contacts.CONTENT_VCARD_TYPE)) {
                        try {
                            Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                            if (uri != null) {
                                contactsToSend = AndroidUtilities.loadVCardFromStream(uri, currentAccount, false, null, null);
                                if (contactsToSend.size() > 5) {
                                    contactsToSend = null;
                                    documentsUrisArray = new ArrayList<>();
                                    documentsUrisArray.add(uri);
                                    documentsMimeType = type;
                                } else {
                                    contactsToSendUri = uri;
                                }
                            } else {
                                error = true;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                            error = true;
                        }
                    } else {
                        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (text == null) {
                            CharSequence textSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                            if (textSequence != null) {
                                text = textSequence.toString();
                            }
                        }
                        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

                        if (!TextUtils.isEmpty(text)) {
                            if ((text.startsWith("http://") || text.startsWith("https://")) && !TextUtils.isEmpty(subject)) {
                                text = subject + "\n" + text;
                            }
                            sendingText = text;
                        } else if (!TextUtils.isEmpty(subject)) {
                            sendingText = subject;
                        }

                        Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        if (parcelable != null) {
                            String path;
                            if (!(parcelable instanceof Uri)) {
                                parcelable = Uri.parse(parcelable.toString());
                            }
                            Uri uri = (Uri) parcelable;
                            if (uri != null) {
                                if (AndroidUtilities.isInternalUri(uri)) {
                                    error = true;
                                }
                            }
                            if (!error && uri != null) {
                                if (type != null && type.startsWith("image/") || uri.toString().toLowerCase().endsWith(".jpg")) {
                                    if (photoPathsArray == null) {
                                        photoPathsArray = new ArrayList<>();
                                    }
                                    SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                    info.uri = uri;
                                    photoPathsArray.add(info);
                                } else {
                                    String originalPath = uri.toString();
                                    if (dialogId == 0 && originalPath != null) {
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.d("export path = " + originalPath);
                                        }
                                        Set<String> exportUris = MessagesController.getInstance(intentAccount[0]).exportUri;
                                        String fileName = FileLoader.fixFileName(MediaController.getFileName(uri));
                                        for (String u : exportUris) {
                                            try {
                                                Pattern pattern = Pattern.compile(u);
                                                if (pattern.matcher(originalPath).find() || pattern.matcher(fileName).find()) {
                                                    exportingChatUri = uri;
                                                    break;
                                                }
                                            } catch (Exception e) {
                                                FileLog.e(e);
                                            }
                                        }
                                        if (exportingChatUri == null) {
                                            if (originalPath.startsWith("content://com.kakao.talk") && originalPath.endsWith("KakaoTalkChats.txt")) {
                                                exportingChatUri = uri;
                                            }
                                        }
                                    }
                                    if (exportingChatUri == null) {
                                        path = AndroidUtilities.getPath(uri);
                                        if (!BuildVars.NO_SCOPED_STORAGE) {
                                            path = MediaController.copyFileToCache(uri, "file");
                                        }
                                        if (path != null) {
                                            if (path.startsWith("file:")) {
                                                path = path.replace("file://", "");
                                            }
                                            if (type != null && type.startsWith("video/")) {
                                                videoPath = path;
                                            } else if (type != null && type.startsWith("audio/ogg") && type.contains("codecs=opus") && MediaController.isOpusFile(path) == 1) {
                                                voicePath = path;
                                            } else {
                                                if (documentsPathsArray == null) {
                                                    documentsPathsArray = new ArrayList<>();
                                                    documentsOriginalPathsArray = new ArrayList<>();
                                                }
                                                documentsPathsArray.add(path);
                                                documentsOriginalPathsArray.add(uri.toString());
                                            }
                                        } else {
                                            if (documentsUrisArray == null) {
                                                documentsUrisArray = new ArrayList<>();
                                            }
                                            documentsUrisArray.add(uri);
                                            documentsMimeType = type;
                                        }
                                    }
                                }
                            }
                        } else if (sendingText == null) {
                            error = true;
                        }
                    }
                    if (error) {
                        Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
                    }
                } else if ("org.telegram.messenger.CREATE_STICKER_PACK".equals(intent.getAction())) {
                    try {
                        importingStickers = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                        importingStickersEmoji = intent.getStringArrayListExtra("STICKER_EMOJIS");
                        importingStickersSoftware = intent.getStringExtra("IMPORTER");
                    } catch (Throwable e) {
                        FileLog.e(e);
                        importingStickers = null;
                        importingStickersEmoji = null;
                        importingStickersSoftware = null;
                    }
                } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                    boolean error = false;
                    try {
                        ArrayList<Parcelable> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                        String type = intent.getType();
                        if (uris != null) {
                            for (int a = 0; a < uris.size(); a++) {
                                Parcelable parcelable = uris.get(a);
                                if (!(parcelable instanceof Uri)) {
                                    parcelable = Uri.parse(parcelable.toString());
                                }
                                Uri uri = (Uri) parcelable;
                                if (uri != null) {
                                    if (AndroidUtilities.isInternalUri(uri)) {
                                        uris.remove(a);
                                        a--;
                                    }
                                }
                            }
                            if (uris.isEmpty()) {
                                uris = null;
                            }
                        }
                        if (uris != null) {
                            if (type != null && type.startsWith("image/")) {
                                for (int a = 0; a < uris.size(); a++) {
                                    Parcelable parcelable = uris.get(a);
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    Uri uri = (Uri) parcelable;
                                    if (photoPathsArray == null) {
                                        photoPathsArray = new ArrayList<>();
                                    }
                                    SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                    info.uri = uri;
                                    photoPathsArray.add(info);
                                }
                            } else {
                                Set<String> exportUris = MessagesController.getInstance(intentAccount[0]).exportUri;
                                for (int a = 0; a < uris.size(); a++) {
                                    Parcelable parcelable = uris.get(a);
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    Uri uri = (Uri) parcelable;
                                    String path = AndroidUtilities.getPath(uri);
                                    String originalPath = parcelable.toString();
                                    if (originalPath == null) {
                                        originalPath = path;
                                    }

                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("export path = " + originalPath);
                                    }
                                    if (dialogId == 0 && originalPath != null && exportingChatUri == null) {
                                        boolean ok = false;
                                        String fileName = FileLoader.fixFileName(MediaController.getFileName(uri));
                                        for (String u : exportUris) {
                                            try {
                                                Pattern pattern = Pattern.compile(u);
                                                if (pattern.matcher(originalPath).find() || pattern.matcher(fileName).find()) {
                                                    exportingChatUri = uri;
                                                    ok = true;
                                                    break;
                                                }
                                            } catch (Exception e) {
                                                FileLog.e(e);
                                            }
                                        }
                                        if (ok) {
                                            continue;
                                        } else if (originalPath.startsWith("content://com.kakao.talk") && originalPath.endsWith("KakaoTalkChats.txt")) {
                                            exportingChatUri = uri;
                                            continue;
                                        }
                                    }
                                    if (path != null) {
                                        if (path.startsWith("file:")) {
                                            path = path.replace("file://", "");
                                        }
                                        if (documentsPathsArray == null) {
                                            documentsPathsArray = new ArrayList<>();
                                            documentsOriginalPathsArray = new ArrayList<>();
                                        }
                                        documentsPathsArray.add(path);
                                        documentsOriginalPathsArray.add(originalPath);
                                    } else {
                                        if (documentsUrisArray == null) {
                                            documentsUrisArray = new ArrayList<>();
                                        }
                                        documentsUrisArray.add(uri);
                                        documentsMimeType = type;
                                    }
                                }
                            }
                        } else {
                            error = true;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        error = true;
                    }
                    if (error) {
                        Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
                    }
                } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                    Uri data = intent.getData();
                    if (data != null) {
                        String username = null;
                        String referrer = null;
                        String login = null;
                        String group = null;
                        String sticker = null;
                        String emoji = null;
                        HashMap<String, String> auth = null;
                        String unsupportedUrl = null;
                        String botAppMaybe = null;
                        String startApp = null;
                        String botUser = null;
                        String botChat = null;
                        String botChannel = null;
                        String botChatAdminParams = null;
                        String message = null;
                        String phone = null;
                        String game = null;
                        String voicechat = null;
                        boolean videochat = false;
                        String livestream = null;
                        String phoneHash = null;
                        String lang = null;
                        String theme = null;
                        String code = null;
                        String contactToken = null;
                        String folderSlug = null;
                        String chatLinkSlug = null;
                        TLRPC.TL_wallPaper wallPaper = null;
                        String inputInvoiceSlug = null;
                        Integer messageId = null;
                        Long channelId = null;
                        Long threadId = null;
                        String text = null;
                        boolean isBoost = false;
                        Integer commentId = null;
                        int videoTimestamp = -1;
                        boolean hasUrl = false;
                        String setAsAttachBot = null;
                        String attachMenuBotToOpen = null;
                        String attachMenuBotChoose = null;
                        boolean botCompact = false;
                        boolean botFullscreen = false;
                        boolean openProfile = false;
                        int storyId = 0;
                        final String scheme = data.getScheme();
                        if (scheme != null) {
                            switch (scheme) {
                                case "tonsite":
                                    Browser.openUrl(this, data);
                                    intent.setAction(null);
                                    if (progress != null) {
                                        progress.end();
                                    }
                                    return false;
                                case "http":
                                case "https": {
                                    String host = data.getHost().toLowerCase();
                                    Matcher prefixMatcher = PREFIX_T_ME_PATTERN.matcher(host);
                                    boolean isPrefix = prefixMatcher.find();
                                    if (host.equals("telegram.me") || host.equals("t.me") || host.equals("telegram.dog") || isPrefix) {
                                        if (isPrefix) {
                                            data = Uri.parse("https://t.me/" + prefixMatcher.group(1) + (TextUtils.isEmpty(data.getPath()) ? "" : data.getPath()) + (TextUtils.isEmpty(data.getQuery()) ? "" : "?" + data.getQuery()));
                                        }
                                        String path = data.getPath();
                                        if (path != null && path.length() > 1) {
                                            path = path.substring(1);
                                            if (path.startsWith("$")) {
                                                inputInvoiceSlug = path.substring(1);
                                            } else if (path.startsWith("invoice/")) {
                                                inputInvoiceSlug = path.substring(path.indexOf('/') + 1);
                                            } else if (path.startsWith("bg/")) {
                                                wallPaper = new TLRPC.TL_wallPaper();
                                                wallPaper.settings = new TLRPC.TL_wallPaperSettings();
                                                wallPaper.slug = path.replace("bg/", "");
                                                boolean ok = false;
                                                if (wallPaper.slug != null && wallPaper.slug.length() == 6) {
                                                    try {
                                                        wallPaper.settings.background_color = Integer.parseInt(wallPaper.slug, 16) | 0xff000000;
                                                        wallPaper.slug = null;
                                                        ok = true;
                                                    } catch (Exception ignore) {

                                                    }
                                                } else if (wallPaper.slug != null && wallPaper.slug.length() >= 13 && AndroidUtilities.isValidWallChar(wallPaper.slug.charAt(6))) {
                                                    try {
                                                        wallPaper.settings.background_color = Integer.parseInt(wallPaper.slug.substring(0, 6), 16) | 0xff000000;
                                                        wallPaper.settings.second_background_color = Integer.parseInt(wallPaper.slug.substring(7, 13), 16) | 0xff000000;
                                                        if (wallPaper.slug.length() >= 20 && AndroidUtilities.isValidWallChar(wallPaper.slug.charAt(13))) {
                                                            wallPaper.settings.third_background_color = Integer.parseInt(wallPaper.slug.substring(14, 20), 16) | 0xff000000;
                                                        }
                                                        if (wallPaper.slug.length() == 27 && AndroidUtilities.isValidWallChar(wallPaper.slug.charAt(20))) {
                                                            wallPaper.settings.fourth_background_color = Integer.parseInt(wallPaper.slug.substring(21), 16) | 0xff000000;
                                                        }
                                                        try {
                                                            String rotation = data.getQueryParameter("rotation");
                                                            if (!TextUtils.isEmpty(rotation)) {
                                                                wallPaper.settings.rotation = Utilities.parseInt(rotation);
                                                            }
                                                        } catch (Exception ignore) {

                                                        }
                                                        wallPaper.slug = null;
                                                        ok = true;
                                                    } catch (Exception ignore) {

                                                    }
                                                }
                                                if (!ok) {
                                                    String mode = data.getQueryParameter("mode");
                                                    if (mode != null) {
                                                        mode = mode.toLowerCase();
                                                        String[] modes = mode.split(" ");
                                                        if (modes != null && modes.length > 0) {
                                                            for (int a = 0; a < modes.length; a++) {
                                                                if ("blur".equals(modes[a])) {
                                                                    wallPaper.settings.blur = true;
                                                                } else if ("motion".equals(modes[a])) {
                                                                    wallPaper.settings.motion = true;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    String intensity = data.getQueryParameter("intensity");
                                                    if (!TextUtils.isEmpty(intensity)) {
                                                        wallPaper.settings.intensity = Utilities.parseInt(intensity);
                                                    } else {
                                                        wallPaper.settings.intensity = 50;
                                                    }
                                                    try {
                                                        String bgColor = data.getQueryParameter("bg_color");
                                                        if (!TextUtils.isEmpty(bgColor)) {
                                                            wallPaper.settings.background_color = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                                            if (bgColor.length() >= 13) {
                                                                wallPaper.settings.second_background_color = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                                                if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                                                    wallPaper.settings.third_background_color = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                                                }
                                                                if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                                                    wallPaper.settings.fourth_background_color = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                                                }
                                                            }
                                                        } else {
                                                            wallPaper.settings.background_color = 0xffffffff;
                                                        }
                                                    } catch (Exception ignore) {

                                                    }
                                                    try {
                                                        String rotation = data.getQueryParameter("rotation");
                                                        if (!TextUtils.isEmpty(rotation)) {
                                                            wallPaper.settings.rotation = Utilities.parseInt(rotation);
                                                        }
                                                    } catch (Exception ignore) {

                                                    }
                                                }
                                            } else if (path.startsWith("login/")) {
                                                int intCode = Utilities.parseInt(path.replace("login/", ""));
                                                if (intCode != 0) {
                                                    code = "" + intCode;
                                                }
                                            } else if (path.startsWith("joinchat/")) {
                                                group = path.replace("joinchat/", "");
                                            } else if (path.startsWith("+")) {
                                                group = path.replace("+", "");
                                                if (AndroidUtilities.isNumeric(group)) {
                                                    username = group;
                                                    group = null;
                                                }
                                                text = data.getQueryParameter("text");
                                            } else if (path.startsWith("addstickers/")) {
                                                sticker = path.replace("addstickers/", "");
                                            } else if (path.startsWith("addemoji/")) {
                                                emoji = path.replace("addemoji/", "");
                                            } else if (path.startsWith("msg/") || path.startsWith("share/")) {
                                                message = data.getQueryParameter("url");
                                                if (message == null) {
                                                    message = "";
                                                }
                                                if (data.getQueryParameter("text") != null) {
                                                    if (message.length() > 0) {
                                                        hasUrl = true;
                                                        message += "\n";
                                                    }
                                                    message += data.getQueryParameter("text");
                                                }
                                                if (message.length() > 4096 * 4) {
                                                    message = message.substring(0, 4096 * 4);
                                                }
                                                while (message.endsWith("\n")) {
                                                    message = message.substring(0, message.length() - 1);
                                                }
                                            } else if (path.startsWith("confirmphone")) {
                                                phone = data.getQueryParameter("phone");
                                                phoneHash = data.getQueryParameter("hash");
                                            } else if (path.startsWith("setlanguage/")) {
                                                lang = path.substring(12);
                                            } else if (path.startsWith("addtheme/")) {
                                                theme = path.substring(9);
                                            } else if (path.equalsIgnoreCase("boost") || path.startsWith("boost/")) {
                                                isBoost = true;
                                                String c = data.getQueryParameter("c");
                                                List<String> segments = data.getPathSegments();
                                                if (segments.size() >= 2) {
                                                    username = segments.get(1);
                                                } else if (!TextUtils.isEmpty(c)) {
                                                    channelId = Utilities.parseLong(c);
                                                }
                                            } else if (path.startsWith("c/")) {
                                                List<String> segments = data.getPathSegments();
                                                if (segments.size() >= 3) {
                                                    channelId = Utilities.parseLong(segments.get(1));
                                                    messageId = Utilities.parseInt(segments.get(2));
                                                    if (messageId == 0 || channelId == 0) {
                                                        messageId = null;
                                                        channelId = null;
                                                    }
                                                    threadId = Utilities.parseLong(data.getQueryParameter("thread"));
                                                    if (threadId == 0) {
                                                        threadId = null;
                                                    }
                                                    if (threadId == null) {
                                                        threadId = Utilities.parseLong(data.getQueryParameter("topic"));
                                                        if (threadId == 0) {
                                                            threadId = null;
                                                        }
                                                    }
                                                    if (threadId == null && messageId != null && segments.size() >= 4) {
                                                        threadId = (long) (int) messageId;
                                                        messageId = Utilities.parseInt(segments.get(3));
                                                    }
                                                }
                                                if (data.getQuery() != null && segments.size() == 2) {
                                                    isBoost = data.getQuery().equals("boost");
                                                    channelId = Utilities.parseLong(segments.get(1));
                                                }
                                            } else if (path.startsWith("contact/")) {
                                                contactToken = path.substring(8);
                                            } else if (path.startsWith("folder/")) {
                                                folderSlug = path.substring(7);
                                            } else if (path.startsWith("addlist/")) {
                                                folderSlug = path.substring(8);
                                            } else if (path.startsWith("m/")) {
                                                chatLinkSlug = path.substring(2);
                                            } else if (path.length() >= 1) {
                                                botAppMaybe = null;
                                                ArrayList<String> segments = new ArrayList<>(data.getPathSegments());
                                                if (segments.size() > 0 && segments.get(0).equals("s")) {
                                                    segments.remove(0);
                                                }
                                                if (segments.size() > 0) {
                                                    username = segments.get(0);
                                                    if (segments.size() >= 3 && "s".equals(segments.get(1))) {
                                                        try {
                                                            storyId = Integer.parseInt(segments.get(2));
                                                        } catch (Exception ignore) {}
                                                    } else if (segments.size() > 1) {
                                                        botAppMaybe = segments.get(1);
                                                        startApp = data.getQueryParameter("startapp");
                                                        try {
                                                            messageId = Utilities.parseInt(segments.get(1));
                                                            if (messageId == 0) {
                                                                messageId = null;
                                                            }
                                                        } catch (NumberFormatException ignored) {
                                                            messageId = null;
                                                        }
                                                    } else if (segments.size() == 1) {
                                                        startApp = data.getQueryParameter("startapp");
                                                    }
                                                }
                                                if (messageId != null) {
                                                    videoTimestamp = getTimestampFromLink(data);
                                                }
                                                botUser = data.getQueryParameter("start");
                                                botChat = data.getQueryParameter("startgroup");
                                                if (!TextUtils.isEmpty(username)) {
                                                    referrer = data.getQueryParameter("ref");
                                                    if (TextUtils.isEmpty(referrer) && !TextUtils.isEmpty(botUser)) {
                                                        for (String prefix : MessagesController.getInstance(intentAccount[0]).starrefStartParamPrefixes) {
                                                            if (botUser.startsWith(prefix)) {
                                                                referrer = botUser.substring(prefix.length());
                                                                break;
                                                            }
                                                        }
                                                    } else if (TextUtils.isEmpty(referrer) && !TextUtils.isEmpty(startApp)) {
                                                        for (String prefix : MessagesController.getInstance(intentAccount[0]).starrefStartParamPrefixes) {
                                                            if (startApp.startsWith(prefix)) {
                                                                referrer = startApp.substring(prefix.length());
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                                botChannel = data.getQueryParameter("startchannel");
                                                botChatAdminParams = data.getQueryParameter("admin");
                                                game = data.getQueryParameter("game");
                                                voicechat = data.getQueryParameter("voicechat");
                                                videochat = data.getBooleanQueryParameter("videochat", false);
                                                livestream = data.getQueryParameter("livestream");
                                                setAsAttachBot = data.getQueryParameter("startattach");
                                                attachMenuBotChoose = data.getQueryParameter("choose");
                                                attachMenuBotToOpen = data.getQueryParameter("attach");
                                                botCompact = TextUtils.equals(data.getQueryParameter("mode"), "compact");
                                                botFullscreen = TextUtils.equals(data.getQueryParameter("mode"), "fullscreen");
                                                openProfile = data.getBooleanQueryParameter("profile", false);
                                                threadId = Utilities.parseLong(data.getQueryParameter("thread"));
                                                text = data.getQueryParameter("text");
                                                if (data.getQuery() != null) {
                                                    isBoost = data.getQuery().equals("boost");
                                                }
//                                                storyId = Utilities.parseInt(data.getQueryParameter("story"));
                                                if (threadId == 0) {
                                                    threadId = null;
                                                }
                                                if (threadId == null) {
                                                    threadId = Utilities.parseLong(data.getQueryParameter("topic"));
                                                    if (threadId == 0) {
                                                        threadId = null;
                                                    }
                                                }
                                                if (threadId == null && messageId != null && segments.size() >= 3) {
                                                    threadId = (long) (int) messageId;
                                                    messageId = Utilities.parseInt(segments.get(2));
                                                }
                                                commentId = Utilities.parseInt(data.getQueryParameter("comment"));
                                                if (commentId == 0) {
                                                    commentId = null;
                                                }
                                            }
                                        }
                                    }
                                    break;
                                }
                                case "tg": {
                                    String url = data.toString();
                                    if (url.startsWith("tg:premium_offer") || url.startsWith("tg://premium_offer")) {
                                        String finalUrl = url;
                                        AndroidUtilities.runOnUIThread(() -> {
                                        if (!actionBarLayout.getFragmentStack().isEmpty()) {
                                            BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                                            Uri uri = Uri.parse(finalUrl);
                                            fragment.presentFragment(new PremiumPreviewFragment(uri.getQueryParameter("ref")));
                                        }});
                                    } else if (url.startsWith("tg:resolve") || url.startsWith("tg://resolve")) {
                                        url = url.replace("tg:resolve", "tg://telegram.org").replace("tg://resolve", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        username = data.getQueryParameter("domain");
                                        if (username == null) {
                                            username = data.getQueryParameter("phone");
                                            if (username != null && username.startsWith("+")) {
                                                username = username.substring(1);
                                            }
                                        }
                                        botAppMaybe = data.getQueryParameter("appname");
                                        startApp = data.getQueryParameter("startapp");
                                        openProfile = data.getBooleanQueryParameter("profile", false);
                                        if ("telegrampassport".equals(username)) {
                                            username = null;
                                            auth = new HashMap<>();
                                            String scope = data.getQueryParameter("scope");
                                            if (!TextUtils.isEmpty(scope) && scope.startsWith("{") && scope.endsWith("}")) {
                                                auth.put("nonce", data.getQueryParameter("nonce"));
                                            } else {
                                                auth.put("payload", data.getQueryParameter("payload"));
                                            }
                                            auth.put("bot_id", data.getQueryParameter("bot_id"));
                                            auth.put("scope", scope);
                                            auth.put("public_key", data.getQueryParameter("public_key"));
                                            auth.put("callback_url", data.getQueryParameter("callback_url"));
                                        } else {
                                            botUser = data.getQueryParameter("start");
                                            botChat = data.getQueryParameter("startgroup");
                                            botChannel = data.getQueryParameter("startchannel");
                                            botChatAdminParams = data.getQueryParameter("admin");
                                            game = data.getQueryParameter("game");
                                            voicechat = data.getQueryParameter("voicechat");
                                            videochat = data.getBooleanQueryParameter("videochat", false);
                                            livestream = data.getQueryParameter("livestream");
                                            setAsAttachBot = data.getQueryParameter("startattach");
                                            attachMenuBotChoose = data.getQueryParameter("choose");
                                            attachMenuBotToOpen = data.getQueryParameter("attach");
                                            messageId = Utilities.parseInt(data.getQueryParameter("post"));
                                            storyId = Utilities.parseInt(data.getQueryParameter("story"));
                                            if (messageId == 0) {
                                                messageId = null;
                                            }
                                            threadId = Utilities.parseLong(data.getQueryParameter("thread"));
                                            if (threadId == 0) {
                                                threadId = null;
                                            }
                                            if (threadId == null) {
                                                threadId = Utilities.parseLong(data.getQueryParameter("topic"));
                                                if (threadId == 0) {
                                                    threadId = null;
                                                }
                                            }
                                            text = data.getQueryParameter("text");
                                            commentId = Utilities.parseInt(data.getQueryParameter("comment"));
                                            if (commentId == 0) {
                                                commentId = null;
                                            }
                                        }
                                        if (!TextUtils.isEmpty(username)) {
                                            referrer = data.getQueryParameter("ref");
                                            if (TextUtils.isEmpty(referrer) && !TextUtils.isEmpty(botUser)) {
                                                for (String prefix : MessagesController.getInstance(intentAccount[0]).starrefStartParamPrefixes) {
                                                    if (botUser.startsWith(prefix)) {
                                                        referrer = botUser.substring(prefix.length());
                                                        break;
                                                    }
                                                }
                                            } else if (TextUtils.isEmpty(referrer) && !TextUtils.isEmpty(startApp)) {
                                                for (String prefix : MessagesController.getInstance(intentAccount[0]).starrefStartParamPrefixes) {
                                                    if (startApp.startsWith(prefix)) {
                                                        referrer = startApp.substring(prefix.length());
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    } else if (url.startsWith("tg:invoice") || url.startsWith("tg://invoice")) {
                                        url = url.replace("tg:invoice", "tg://invoice");
                                        data = Uri.parse(url);
                                        inputInvoiceSlug = data.getQueryParameter("slug");
                                    } else if (url.startsWith("tg:contact") || url.startsWith("tg://contact")) {
                                        url = url.replace("tg:contact", "tg://contact");
                                        data = Uri.parse(url);
                                        contactToken = data.getQueryParameter("token");
                                    } else if (url.startsWith("tg:privatepost") || url.startsWith("tg://privatepost")) {
                                        url = url.replace("tg:privatepost", "tg://telegram.org").replace("tg://privatepost", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        messageId = Utilities.parseInt(data.getQueryParameter("post"));
                                        channelId = Utilities.parseLong(data.getQueryParameter("channel"));
                                        if (messageId == 0 || channelId == 0) {
                                            messageId = null;
                                            channelId = null;
                                        }
                                        threadId = Utilities.parseLong(data.getQueryParameter("thread"));
                                        if (threadId == 0) {
                                            threadId = null;
                                        }
                                        if (threadId == null) {
                                            threadId = Utilities.parseLong(data.getQueryParameter("topic"));
                                            if (threadId == 0) {
                                                threadId = null;
                                            }
                                        }
                                        commentId = Utilities.parseInt(data.getQueryParameter("comment"));
                                        if (commentId == 0) {
                                            commentId = null;
                                        }
                                    } else if (url.startsWith("tg:bg") || url.startsWith("tg://bg")) {
                                        url = url.replace("tg:bg", "tg://telegram.org").replace("tg://bg", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        wallPaper = new TLRPC.TL_wallPaper();
                                        wallPaper.settings = new TLRPC.TL_wallPaperSettings();
                                        wallPaper.slug = data.getQueryParameter("slug");
                                        if (wallPaper.slug == null) {
                                            wallPaper.slug = data.getQueryParameter("color");
                                        }
                                        boolean ok = false;
                                        if (wallPaper.slug != null && wallPaper.slug.length() == 6) {
                                            try {
                                                wallPaper.settings.background_color = Integer.parseInt(wallPaper.slug, 16) | 0xff000000;
                                                wallPaper.slug = null;
                                                ok = true;
                                            } catch (Exception ignore) {

                                            }
                                        } else if (wallPaper.slug != null && wallPaper.slug.length() >= 13 && AndroidUtilities.isValidWallChar(wallPaper.slug.charAt(6))) {
                                            try {
                                                wallPaper.settings.background_color = Integer.parseInt(wallPaper.slug.substring(0, 6), 16) | 0xff000000;
                                                wallPaper.settings.second_background_color = Integer.parseInt(wallPaper.slug.substring(7, 13), 16) | 0xff000000;
                                                if (wallPaper.slug.length() >= 20 && AndroidUtilities.isValidWallChar(wallPaper.slug.charAt(13))) {
                                                    wallPaper.settings.third_background_color = Integer.parseInt(wallPaper.slug.substring(14, 20), 16) | 0xff000000;
                                                }
                                                if (wallPaper.slug.length() == 27 && AndroidUtilities.isValidWallChar(wallPaper.slug.charAt(20))) {
                                                    wallPaper.settings.fourth_background_color = Integer.parseInt(wallPaper.slug.substring(21), 16) | 0xff000000;
                                                }
                                                try {
                                                    String rotation = data.getQueryParameter("rotation");
                                                    if (!TextUtils.isEmpty(rotation)) {
                                                        wallPaper.settings.rotation = Utilities.parseInt(rotation);
                                                    }
                                                } catch (Exception ignore) {

                                                }
                                                wallPaper.slug = null;
                                                ok = true;
                                            } catch (Exception ignore) {

                                            }
                                        }
                                        if (!ok) {
                                            String mode = data.getQueryParameter("mode");
                                            if (mode != null) {
                                                mode = mode.toLowerCase();
                                                String[] modes = mode.split(" ");
                                                if (modes != null && modes.length > 0) {
                                                    for (int a = 0; a < modes.length; a++) {
                                                        if ("blur".equals(modes[a])) {
                                                            wallPaper.settings.blur = true;
                                                        } else if ("motion".equals(modes[a])) {
                                                            wallPaper.settings.motion = true;
                                                        }
                                                    }
                                                }
                                            }
                                            wallPaper.settings.intensity = Utilities.parseInt(data.getQueryParameter("intensity"));
                                            try {
                                                String bgColor = data.getQueryParameter("bg_color");
                                                if (!TextUtils.isEmpty(bgColor)) {
                                                    wallPaper.settings.background_color = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                                    if (bgColor.length() >= 13) {
                                                        wallPaper.settings.second_background_color = Integer.parseInt(bgColor.substring(8, 13), 16) | 0xff000000;
                                                        if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                                            wallPaper.settings.third_background_color = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                                        }
                                                        if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                                            wallPaper.settings.fourth_background_color = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                                        }
                                                    }
                                                }
                                            } catch (Exception ignore) {

                                            }
                                            try {
                                                String rotation = data.getQueryParameter("rotation");
                                                if (!TextUtils.isEmpty(rotation)) {
                                                    wallPaper.settings.rotation = Utilities.parseInt(rotation);
                                                }
                                            } catch (Exception ignore) {

                                            }
                                        }
                                    } else if (url.startsWith("tg:join") || url.startsWith("tg://join")) {
                                        url = url.replace("tg:join", "tg://telegram.org").replace("tg://join", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        group = data.getQueryParameter("invite");
                                    } else if (url.startsWith("tg:addstickers") || url.startsWith("tg://addstickers")) {
                                        url = url.replace("tg:addstickers", "tg://telegram.org").replace("tg://addstickers", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        sticker = data.getQueryParameter("set");
                                    } else if (url.startsWith("tg:addemoji") || url.startsWith("tg://addemoji")) {
                                        url = url.replace("tg:addemoji", "tg://telegram.org").replace("tg://addemoji", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        emoji = data.getQueryParameter("set");
                                    } else if (url.startsWith("tg:msg") || url.startsWith("tg://msg") || url.startsWith("tg://share") || url.startsWith("tg:share")) {
                                        url = url.replace("tg:msg", "tg://telegram.org").replace("tg://msg", "tg://telegram.org").replace("tg://share", "tg://telegram.org").replace("tg:share", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        message = data.getQueryParameter("url");
                                        if (message == null) {
                                            message = "";
                                        }
                                        if (data.getQueryParameter("text") != null) {
                                            if (message.length() > 0) {
                                                hasUrl = true;
                                                message += "\n";
                                            }
                                            message += data.getQueryParameter("text");
                                        }
                                        if (message.length() > 4096 * 4) {
                                            message = message.substring(0, 4096 * 4);
                                        }
                                        while (message.endsWith("\n")) {
                                            message = message.substring(0, message.length() - 1);
                                        }
                                    } else if (url.startsWith("tg:confirmphone") || url.startsWith("tg://confirmphone")) {
                                        url = url.replace("tg:confirmphone", "tg://telegram.org").replace("tg://confirmphone", "tg://telegram.org");
                                        data = Uri.parse(url);

                                        phone = data.getQueryParameter("phone");
                                        phoneHash = data.getQueryParameter("hash");
                                    } else if (url.startsWith("tg:login") || url.startsWith("tg://login")) {
                                        url = url.replace("tg:login", "tg://telegram.org").replace("tg://login", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        login = data.getQueryParameter("token");
                                        int intCode = Utilities.parseInt(data.getQueryParameter("code"));
                                        if (intCode != 0) {
                                            code = "" + intCode;
                                        }
                                    } else if (url.startsWith("tg:openmessage") || url.startsWith("tg://openmessage")) {
                                        url = url.replace("tg:openmessage", "tg://telegram.org").replace("tg://openmessage", "tg://telegram.org");
                                        data = Uri.parse(url);

                                        String userID = data.getQueryParameter("user_id");
                                        String chatID = data.getQueryParameter("chat_id");
                                        String msgID = data.getQueryParameter("message_id");
                                        if (userID != null) {
                                            try {
                                                push_user_id = Long.parseLong(userID);
                                            } catch (NumberFormatException ignore) {
                                            }
                                        } else if (chatID != null) {
                                            try {
                                                push_chat_id = Long.parseLong(chatID);
                                            } catch (NumberFormatException ignore) {
                                            }
                                        }
                                        if (msgID != null) {
                                            try {
                                                push_msg_id = Integer.parseInt(msgID);
                                            } catch (NumberFormatException ignore) {
                                            }
                                        }
                                    } else if (url.startsWith("tg:passport") || url.startsWith("tg://passport") || url.startsWith("tg:secureid")) {
                                        url = url.replace("tg:passport", "tg://telegram.org").replace("tg://passport", "tg://telegram.org").replace("tg:secureid", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        auth = new HashMap<>();
                                        String scope = data.getQueryParameter("scope");
                                        if (!TextUtils.isEmpty(scope) && scope.startsWith("{") && scope.endsWith("}")) {
                                            auth.put("nonce", data.getQueryParameter("nonce"));
                                        } else {
                                            auth.put("payload", data.getQueryParameter("payload"));
                                        }
                                        auth.put("bot_id", data.getQueryParameter("bot_id"));
                                        auth.put("scope", scope);
                                        auth.put("public_key", data.getQueryParameter("public_key"));
                                        auth.put("callback_url", data.getQueryParameter("callback_url"));
                                    } else if (url.startsWith("tg:setlanguage") || url.startsWith("tg://setlanguage")) {
                                        url = url.replace("tg:setlanguage", "tg://telegram.org").replace("tg://setlanguage", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        lang = data.getQueryParameter("lang");
                                    } else if (url.startsWith("tg:addtheme") || url.startsWith("tg://addtheme")) {
                                        url = url.replace("tg:addtheme", "tg://telegram.org").replace("tg://addtheme", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        theme = data.getQueryParameter("slug");
                                    } else if (url.startsWith("tg:settings") || url.startsWith("tg://settings")) {
                                        if (url.contains("themes") || url.contains("theme")) {
                                            open_settings = 2;
                                        } else if (url.contains("devices")) {
                                            open_settings = 3;
                                        } else if (url.contains("folders")) {
                                            open_settings = 4;
                                        } else if (url.contains("change_number")) {
                                            open_settings = 5;
                                        } else if (url.contains("language")) {
                                            open_settings = 10;
                                        } else if (url.contains("auto_delete")) {
                                            open_settings = 11;
                                        } else if (url.contains("privacy")) {
                                            open_settings = 12;
                                        } else if (url.contains("?enablelogs")) {
                                            open_settings = 7;
                                        } else if (url.contains("?sendlogs")) {
                                            open_settings = 8;
                                        } else if (url.contains("?disablelogs")) {
                                            open_settings = 9;
                                        } else if (url.contains("premium_sms")) {
                                            open_settings = 13;
                                        } else {
                                            open_settings = 1;
                                        }
                                    } else if ((url.startsWith("tg:search") || url.startsWith("tg://search"))) {
                                        url = url.replace("tg:search", "tg://telegram.org").replace("tg://search", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        searchQuery = data.getQueryParameter("query");
                                        if (searchQuery != null) {
                                            searchQuery = searchQuery.trim();
                                        } else {
                                            searchQuery = "";
                                        }
                                    } else if ((url.startsWith("tg:calllog") || url.startsWith("tg://calllog"))) {
                                        showCallLog = true;
                                    } else if ((url.startsWith("tg:call") || url.startsWith("tg://call"))) {
                                        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
                                            final String extraForceCall = "extra_force_call";
                                            if (ContactsController.getInstance(currentAccount).contactsLoaded || intent.hasExtra(extraForceCall)) {
                                                final String callFormat = data.getQueryParameter("format");
                                                final String callUserName = data.getQueryParameter("name");
                                                final String callPhone = data.getQueryParameter("phone");
                                                final List<TLRPC.TL_contact> contacts = findContacts(callUserName, callPhone, false);

                                                if (contacts.isEmpty() && callPhone != null) {
                                                    newContactName = callUserName;
                                                    newContactPhone = callPhone;
                                                    newContactAlert = true;
                                                } else {
                                                    if (contacts.size() == 1) {
                                                        push_user_id = contacts.get(0).user_id;
                                                    }

                                                    if (push_user_id == 0) {
                                                        callSearchQuery = callUserName != null ? callUserName : "";
                                                    }

                                                    if ("video".equalsIgnoreCase(callFormat)) {
                                                        videoCallUser = true;
                                                    } else {
                                                        audioCallUser = true;
                                                    }

                                                    needCallAlert = true;
                                                }
                                            } else {
                                                final Intent copyIntent = new Intent(intent);
                                                copyIntent.removeExtra(EXTRA_ACTION_TOKEN);
                                                copyIntent.putExtra(extraForceCall, true);
                                                ContactsLoadingObserver.observe((contactsLoaded) -> handleIntent(copyIntent, true, false, false), 1000);
                                            }
                                        }
                                    } else if ((url.startsWith("tg:scanqr") || url.startsWith("tg://scanqr"))) {
                                        scanQr = true;
                                    } else if ((url.startsWith("tg:addcontact") || url.startsWith("tg://addcontact"))) {
                                        url = url.replace("tg:addcontact", "tg://telegram.org").replace("tg://addcontact", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        newContactName = data.getQueryParameter("name");

                                        // use getQueryParameters to keep the "+" sign
                                        List<String> phoneParams = data.getQueryParameters("phone");
                                        if (phoneParams != null && phoneParams.size() > 0) {
                                            newContactPhone = phoneParams.get(0);
                                        }
                                        newContact = true;
                                    } else if (url.startsWith("tg:addlist") || url.startsWith("tg://addlist")) {
                                        url = url.replace("tg:addlist", "tg://telegram.org").replace("tg://addlist", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        folderSlug = data.getQueryParameter("slug");
                                    } else if (url.startsWith("tg:message") || url.startsWith("tg://message")) {
                                        url = url.replace("tg:message", "tg://telegram.org").replace("tg://message", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        chatLinkSlug = data.getQueryParameter("slug");
                                    } else if (url.startsWith("tg:stars_topup") || url.startsWith("tg://stars_topup")) {
                                        url = url.replace("tg:stars_topup", "tg://telegram.org").replace("tg://stars_topup", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        long balance = 0;
                                        try {
                                            balance = (int) Long.parseLong(data.getQueryParameter("balance"));
                                            if (balance < 0 || balance >= Integer.MAX_VALUE) balance = 0;
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                        String purpose = data.getQueryParameter("purpose");
                                        StarsController.getInstance(intentAccount[0]).showStarsTopup(this, balance, purpose);
                                    } else {
                                        unsupportedUrl = url.replace("tg://", "").replace("tg:", "");
                                        int index;
                                        if ((index = unsupportedUrl.indexOf('?')) >= 0) {
                                            unsupportedUrl = unsupportedUrl.substring(0, index);
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        if (intent.hasExtra(EXTRA_ACTION_TOKEN)) {
                            final boolean success = UserConfig.getInstance(currentAccount).isClientActivated() && "tg".equals(scheme) && unsupportedUrl == null;
                            final Action assistAction = new AssistActionBuilder()
                                    .setActionToken(intent.getStringExtra(EXTRA_ACTION_TOKEN))
                                    .setActionStatus(success ? Action.Builder.STATUS_TYPE_COMPLETED : Action.Builder.STATUS_TYPE_FAILED)
                                    .build();
                            FirebaseUserActions.getInstance(this).end(assistAction);
                            intent.removeExtra(EXTRA_ACTION_TOKEN);
                        }
                        if (code != null || UserConfig.getInstance(currentAccount).isClientActivated()) {
                            if (phone != null || phoneHash != null) {
                                AlertDialog cancelDeleteProgressDialog = new AlertDialog(LaunchActivity.this, AlertDialog.ALERT_TYPE_SPINNER);
                                cancelDeleteProgressDialog.setCanCancel(false);
                                cancelDeleteProgressDialog.show();

                                TLRPC.TL_account_sendConfirmPhoneCode req = new TLRPC.TL_account_sendConfirmPhoneCode();
                                req.hash = phoneHash;
                                req.settings = new TLRPC.TL_codeSettings();
                                req.settings.allow_flashcall = false;
                                req.settings.allow_app_hash = req.settings.allow_firebase = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                if (req.settings.allow_app_hash) {
                                    preferences.edit().putString("sms_hash", BuildVars.getSmsHash()).apply();
                                } else {
                                    preferences.edit().remove("sms_hash").apply();
                                }

                                Bundle params = new Bundle();
                                params.putString("phone", phone);

                                String finalPhone = phone;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                    cancelDeleteProgressDialog.dismiss();
                                    if (error == null) {
                                        presentFragment(new LoginActivity().cancelAccountDeletion(finalPhone, params, (TLRPC.TL_auth_sentCode) response));
                                    } else {
                                        AlertsCreator.processError(currentAccount, error, getActionBarLayout().getLastFragment(), req);
                                    }
                                }), ConnectionsManager.RequestFlagFailOnServerErrors);
                            } else if (username != null || group != null || sticker != null || emoji != null || contactToken != null || folderSlug != null || message != null || game != null || voicechat != null || videochat || auth != null || unsupportedUrl != null || lang != null || code != null || wallPaper != null || inputInvoiceSlug != null || channelId != null || theme != null || login != null || chatLinkSlug != null) {
                                if (message != null && message.startsWith("@")) {
                                    message = " " + message;
                                }
                                runLinkRequest(intentAccount[0], username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, login, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, 0, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, startApp, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
                            } else {
                                try (Cursor cursor = getContentResolver().query(intent.getData(), null, null, null, null)) {
                                    if (cursor != null) {
                                        if (cursor.moveToFirst()) {
                                            long userId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data.DATA4));
                                            int accountId = Utilities.parseInt(cursor.getString(cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)));
                                            for (int a = -1; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                                int i = a == -1 ? intentAccount[0] : a;
                                                if ((a == -1 && MessagesStorage.getInstance(i).containsLocalDialog(userId)) || UserConfig.getInstance(i).getClientUserId() == accountId) {
                                                    intentAccount[0] = i;
                                                    switchToAccount(intentAccount[0], true);
                                                    break;
                                                }
                                            }
                                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                                            push_user_id = userId;
                                            String mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));
                                            if (TextUtils.equals(mimeType, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call")) {
                                                audioCallUser = true;
                                            } else if (TextUtils.equals(mimeType, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call.video")) {
                                                videoCallUser = true;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }
                    }
                } else if (intent.getAction().equals("org.telegram.messenger.OPEN_ACCOUNT")) {
                    open_settings = 1;
                } else if (intent.getAction().equals("new_dialog")) {
                    open_new_dialog = 1;
                } else if (intent.getAction().startsWith("com.tmessages.openchat")) {
//                    Integer chatIdInt = intent.getIntExtra("chatId", 0);
                    long chatId = intent.getLongExtra("chatId", 0);
//                    Integer userIdInt = intent.getIntExtra("userId", 0);
                    long[] storyDialogIds = intent.getLongArrayExtra("storyDialogIds");
                    int storyId = intent.getIntExtra("storyId", -1);
                    long userId = intent.getLongExtra("userId", 0);
                    int encId = intent.getIntExtra("encId", 0);
                    int widgetId = intent.getIntExtra("appWidgetId", 0);
                    long topicId = intent.getLongExtra("topicId", 0);
                    if (widgetId != 0) {
                        open_settings = 6;
                        open_widget_edit = widgetId;
                        open_widget_edit_type = intent.getIntExtra("appWidgetType", 0);
                    } else {
                        if (push_msg_id == 0) {
                            push_msg_id = intent.getIntExtra("message_id", 0);
                        }
                        if (storyId != -1) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_story_id = storyId;
                        } else if (storyDialogIds != null) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_story_dids = storyDialogIds;
//                            push_story_id = intent.getIntExtra("storyId", 0);
                            showDialogsList = true;
                        } else if (chatId != 0) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_chat_id = chatId;
                            push_topic_id = topicId;
                        } else if (userId != 0) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_user_id = userId;
                        } else if (encId != 0) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_enc_id = encId;
                        } else {
                            showDialogsList = true;
                        }
                    }
                } else if (intent.getAction().startsWith(OpenAttachedMenuBotReceiver.ACTION)) {
                    botId = intent.getLongExtra("botId", 0);
                    if (botId != 0) {
                        openBot = true;
                    }
                } else if (intent.getAction().equals("com.tmessages.openplayer")) {
                    showPlayer = true;
                } else if (intent.getAction().equals("org.tmessages.openlocations")) {
                    showLocations = true;
                } else if (action.equals("voip_chat")) {
                    showGroupVoip = true;
                }
            }
        }
        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            if (searchQuery != null) {
                final BaseFragment lastFragment = actionBarLayout.getLastFragment();
                if (lastFragment instanceof DialogsActivity) {
                    final DialogsActivity dialogsActivity = (DialogsActivity) lastFragment;
                    if (dialogsActivity.isMainDialogList()) {
                        if (dialogsActivity.getFragmentView() != null && isNew) {
                            dialogsActivity.search(searchQuery, true);
                        } else {
                            dialogsActivity.setInitialSearchString(searchQuery);
                        }
                    }
                } else {
                    showDialogsList = true;
                }
            }

            if (push_story_id > 0) {
                NotificationsController.getInstance(intentAccount[0]).processSeenStoryReactions(UserConfig.getInstance(intentAccount[0]).getClientUserId(), push_story_id);
                openMyStory(push_story_id, true);
            } else if (push_story_dids != null) {
                NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                openStories(push_story_dids, true);
            } else if (push_user_id != 0) {
                if (audioCallUser || videoCallUser) {
                    if (needCallAlert) {
                        final BaseFragment lastFragment = actionBarLayout.getLastFragment();
                        if (lastFragment != null) {
                            AlertsCreator.createCallDialogAlert(lastFragment, lastFragment.getMessagesController().getUser(push_user_id), videoCallUser);
                        }
                    } else {
                        VoIPPendingCall.startOrSchedule(this, push_user_id, videoCallUser, AccountInstance.getInstance(intentAccount[0]));
                    }
                } else {
                    Bundle args = new Bundle();
                    args.putLong("user_id", push_user_id);
                    if (push_msg_id != 0) {
                        args.putInt("message_id", push_msg_id);
                    }
                    if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount[0]).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                        ChatActivity fragment = new ChatActivity(args);
                        if (getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true))) {
                            pushOpened = true;
                            LaunchActivity.dismissAllWeb();
                            drawerLayoutContainer.closeDrawer();
                        }
                    }
                }
            } else if (push_chat_id != 0) {
                Bundle args = new Bundle();
                args.putLong("chat_id", push_chat_id);
                if (push_msg_id != 0) {
                    args.putInt("message_id", push_msg_id);
                }
                if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount[0]).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                    ChatActivity fragment = new ChatActivity(args);

                    if (push_topic_id > 0) {
                        TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(push_chat_id, push_topic_id);
                        FileLog.d("LaunchActivity openForum " + push_chat_id + " " + push_topic_id + " TL_forumTopic " + topic);
                        if (topic != null) {
                            ForumUtilities.applyTopic(fragment, MessagesStorage.TopicKey.of(-push_chat_id, push_topic_id));
                        } else {
                            boolean finalIsNew = isNew;
                            long finalPush_chat_id = push_chat_id;
                            long finalPush_topic_id = push_topic_id;
                            MessagesController.getInstance(currentAccount).getTopicsController().loadTopic(push_chat_id, push_topic_id, () -> {
                                TLRPC.TL_forumTopic loadedTopic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(finalPush_chat_id, finalPush_topic_id);
                                FileLog.d("LaunchActivity openForum after load " + finalPush_chat_id + " " + finalPush_topic_id + " TL_forumTopic " + loadedTopic);
                                if (actionBarLayout != null) {
                                    ForumUtilities.applyTopic(fragment, MessagesStorage.TopicKey.of(-finalPush_chat_id, finalPush_topic_id));
                                    getActionBarLayout().presentFragment(fragment);
                                }
                            });
                            return true;
                        }
                    }
                    if (getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true))) {
                        pushOpened = true;
                        LaunchActivity.dismissAllWeb();
                        drawerLayoutContainer.closeDrawer();
                    }
                }
            } else if (push_enc_id != 0) {
                Bundle args = new Bundle();
                args.putInt("enc_id", push_enc_id);
                ChatActivity fragment = new ChatActivity(args);
                if (getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true))) {
                    pushOpened = true;
                    LaunchActivity.dismissAllWeb();
                    drawerLayoutContainer.closeDrawer();
                }
            } else if (showDialogsList) {
                if (!AndroidUtilities.isTablet()) {
                    actionBarLayout.removeAllFragments();
                } else {
                    if (layersActionBarLayout != null && !layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(false);
                    }
                }
                pushOpened = false;
                isNew = false;
            } else if (showPlayer) {
                if (!actionBarLayout.getFragmentStack().isEmpty()) {
                    BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                    fragment.showDialog(new AudioPlayerAlert(this, null));
                }
                pushOpened = false;
            } else if (showLocations) {
                if (!actionBarLayout.getFragmentStack().isEmpty()) {
                    BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                    fragment.showDialog(new SharingLocationsAlert(this, info -> {
                        intentAccount[0] = info.messageObject.currentAccount;
                        switchToAccount(intentAccount[0], true);

                        LocationActivity locationActivity = new LocationActivity(2);
                        locationActivity.setMessageObject(info.messageObject);
                        final long dialog_id = info.messageObject.getDialogId();
                        locationActivity.setDelegate((location, live, notify, scheduleDate) -> SendMessagesHelper.getInstance(intentAccount[0]).sendMessage(SendMessagesHelper.SendMessageParams.of(location, dialog_id, null, null, null, null, notify, scheduleDate)));
                        presentFragment(locationActivity);
                    }, null));
                }
                pushOpened = false;
            } else if (exportingChatUri != null) {
                runImportRequest(exportingChatUri, documentsUrisArray);
            } else if (importingStickers != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!actionBarLayout.getFragmentStack().isEmpty()) {
                        BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                        fragment.showDialog(new StickersAlert(this, importingStickersSoftware, importingStickers, importingStickersEmoji, null));
                    }
                });
                pushOpened = false;
            } else if (videoPath != null || voicePath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null || documentsUrisArray != null) {
                if (!AndroidUtilities.isTablet()) {
                    NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                }
                if (dialogId == 0) {
                    openDialogsToSend(false);
                    pushOpened = true;
                } else {
                    ArrayList<MessagesStorage.TopicKey> dids = new ArrayList<>();
                    dids.add(MessagesStorage.TopicKey.of(dialogId, 0));
                    didSelectDialogs(null, dids, null, false, true, 0, null);
                }
            } else if (open_settings == 7 || open_settings == 8 || open_settings == 9) {
                CharSequence bulletinText = null;
                boolean can = BuildVars.DEBUG_PRIVATE_VERSION; // TODO: check source
                if (!can) {
                    bulletinText = "Locked in release.";
                } else if (open_settings == 7) {
                    bulletinText = "Logs enabled.";
                    ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE).edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED = true).commit();
                    Thread.setDefaultUncaughtExceptionHandler(BuildVars.LOGS_ENABLED ? (thread, exception) -> {
                        if (thread == Looper.getMainLooper().getThread()) {
                            FileLog.fatal(exception, true);
                        }
                    } : null);
                } else if (open_settings == 8) {
                    ProfileActivity.sendLogs(LaunchActivity.this, false);
                } else if (open_settings == 9) {
                    bulletinText = "Logs disabled.";
                    ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE).edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED = false).commit();
                    Thread.setDefaultUncaughtExceptionHandler(BuildVars.LOGS_ENABLED ? (thread, exception) -> {
                        if (thread == Looper.getMainLooper().getThread()) {
                            FileLog.fatal(exception, true);
                        }
                    } : null);
                }

                if (bulletinText != null) {
                    BaseFragment fragment = actionBarLayout.getLastFragment();
                    if (fragment != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, bulletinText).show();
                    }
                }
            } else if (open_settings != 0) {
                BaseFragment fragment;
                boolean closePrevious = false;
                if (open_settings == 1) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).clientUserId);
                    fragment = new ProfileActivity(args);
                } else if (open_settings == 2) {
                    fragment = new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC);
                } else if (open_settings == 3) {
                    fragment = new SessionsActivity(0);
                } else if (open_settings == 4) {
                    fragment = new FiltersSetupActivity();
                } else if (open_settings == 5) {
                    fragment = new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER);
                    closePrevious = true;
                } else if (open_settings == 6) {
                    fragment = new EditWidgetActivity(open_widget_edit_type, open_widget_edit);
                } else if (open_settings == 10) {
                    fragment = new LanguageSelectActivity();
                } else if (open_settings == 11) {
                    fragment = new AutoDeleteMessagesActivity();
                } else if (open_settings == 12) {
                    fragment = new PrivacySettingsActivity();
                } else if (ApplicationLoader.applicationLoaderInstance != null) {
                    fragment = ApplicationLoader.applicationLoaderInstance.openSettings(open_settings);
                } else {
                    fragment = null;
                }
                boolean closePreviousFinal = closePrevious;
                if (open_settings == 6) {
                    getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true));
                } else {
                    AndroidUtilities.runOnUIThread(() -> presentFragment(fragment, closePreviousFinal, false));
                }
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            } else if (open_new_dialog != 0) {
                Bundle args = new Bundle();
                args.putBoolean("destroyAfterSelect", true);
                getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(new ContactsActivity(args)).setNoAnimation(true));
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            } else if (callSearchQuery != null) {
                final Bundle args = new Bundle();
                args.putBoolean("destroyAfterSelect", true);
                args.putBoolean("returnAsResult", true);
                args.putBoolean("onlyUsers", true);
                args.putBoolean("allowSelf", false);
                final ContactsActivity contactsFragment = new ContactsActivity(args);
                contactsFragment.setInitialSearchString(callSearchQuery);
                final boolean videoCall = videoCallUser;
                contactsFragment.setDelegate((user, param, activity) -> {
                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
                    VoIPHelper.startCall(user, videoCall, userFull != null && userFull.video_calls_available, LaunchActivity.this, userFull, AccountInstance.getInstance(intentAccount[0]));
                });
                getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(contactsFragment).setRemoveLast(actionBarLayout.getLastFragment() instanceof ContactsActivity));
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            } else if (scanQr) {
                ActionIntroActivity fragment = new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_QR_LOGIN);
                fragment.setQrLoginDelegate(code -> {
                    AlertDialog progressDialog = new AlertDialog(LaunchActivity.this, AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.setCanCancel(false);
                    progressDialog.show();
                    byte[] token = Base64.decode(code.substring("tg://login?token=".length()), Base64.URL_SAFE);
                    TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
                    req.token = token;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception ignore) {
                        }
                        if (!(response instanceof TLRPC.TL_authorization)) {
                            AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSimpleAlert(fragment, LocaleController.getString(R.string.AuthAnotherClient), LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text));
                        }
                    }));
                });
                getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true));
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            } else if (newContact) {
                final NewContactBottomSheet fragment = new NewContactBottomSheet(actionBarLayout.getLastFragment(), this);
                if (newContactName != null) {
                    final String[] names = newContactName.split(" ", 2);
                    fragment.setInitialName(names[0], names.length > 1 ? names[1] : null);
                }
                if (newContactPhone != null) {
                    fragment.setInitialPhoneNumber(PhoneFormat.stripExceptNumbers(newContactPhone, true), false);
                }
                fragment.show();
               // getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(fragment).setNoAnimation(true));
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            } else if (showGroupVoip) {
                GroupCallActivity.create(this, AccountInstance.getInstance(currentAccount), null, null, false, null);
                if (GroupCallActivity.groupCallInstance != null) {
                    GroupCallActivity.groupCallUiVisible = true;
                }
            } else if (newContactAlert) {
                final BaseFragment lastFragment = actionBarLayout.getLastFragment();
                if (lastFragment != null && lastFragment.getParentActivity() != null) {
                    final String finalNewContactName = newContactName;
                    final String finalNewContactPhone = NewContactBottomSheet.getPhoneNumber(this, UserConfig.getInstance(currentAccount).getCurrentUser(), newContactPhone, false);
                    final AlertDialog newContactAlertDialog = new AlertDialog.Builder(lastFragment.getParentActivity())
                            .setTitle(LocaleController.getString(R.string.NewContactAlertTitle))
                            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("NewContactAlertMessage", R.string.NewContactAlertMessage, PhoneFormat.getInstance().format(finalNewContactPhone))))
                            .setPositiveButton(LocaleController.getString(R.string.NewContactAlertButton), (d, i) -> {
                                final NewContactBottomSheet fragment = new NewContactBottomSheet(lastFragment, this);
                                fragment.setInitialPhoneNumber(finalNewContactPhone, false);
                                if (finalNewContactName != null) {
                                    final String[] names = finalNewContactName.split(" ", 2);
                                    fragment.setInitialName(names[0], names.length > 1 ? names[1] : null);
                                }
                                fragment.show();
                                //lastFragment.presentFragment(fragment);
                            })
                            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                            .create();
                    lastFragment.showDialog(newContactAlertDialog);
                    pushOpened = true;
                }
            } else if (showCallLog) {
                getActionBarLayout().presentFragment(new INavigationLayout.NavigationParams(new CallLogActivity()).setNoAnimation(true));
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            } else if (openBot) {
                processAttachedMenuBotFromShortcut(botId);
                pushOpened = false;
            }
        }
        if (!pushOpened && !isNew) {
            if (AndroidUtilities.isTablet()) {
                if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                    if (layersActionBarLayout.getFragmentStack().isEmpty()) {
                        layersActionBarLayout.addFragmentToStack(getClientNotActivatedFragment(), INavigationLayout.FORCE_NOT_ATTACH_VIEW);
                        drawerLayoutContainer.setAllowOpenDrawer(false, false);
                    }
                } else {
                    if (actionBarLayout.getFragmentStack().isEmpty()) {
                        DialogsActivity dialogsActivity = new DialogsActivity(null);
                        dialogsActivity.setSideMenu(sideMenu);
                        if (searchQuery != null) {
                            dialogsActivity.setInitialSearchString(searchQuery);
                        }
                        actionBarLayout.addFragmentToStack(dialogsActivity, INavigationLayout.FORCE_NOT_ATTACH_VIEW);
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                }
            } else {
                if (actionBarLayout.getFragmentStack().isEmpty()) {
                    if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                        actionBarLayout.addFragmentToStack(getClientNotActivatedFragment(), INavigationLayout.FORCE_NOT_ATTACH_VIEW);
                        drawerLayoutContainer.setAllowOpenDrawer(false, false);
                    } else {
                        DialogsActivity dialogsActivity = new DialogsActivity(null);
                        dialogsActivity.setSideMenu(sideMenu);
                        if (searchQuery != null) {
                            dialogsActivity.setInitialSearchString(searchQuery);
                        }
                        actionBarLayout.addFragmentToStack(dialogsActivity, INavigationLayout.FORCE_NOT_ATTACH_VIEW);
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                }
            }
            if (rebuildFragments) {
                actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                if (AndroidUtilities.isTablet()) {
                    layersActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                }
            }
        }
        if (isVoipAnswerIntent) {
            VoIPPreNotificationService.answer(this);
        } else if (isVoipIntent) {
            VoIPFragment.show(this, intentAccount[0]);
        }
        if (!showGroupVoip && (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) && GroupCallActivity.groupCallInstance != null) {
            GroupCallActivity.groupCallInstance.dismiss();
        }

        intent.setAction(null);
        return pushOpened;
    }

    public static int getTimestampFromLink(Uri data) {
        List<String> segments = data.getPathSegments();
        String timestampStr = null;
        if (segments.contains("video")) {
            timestampStr = data.getQuery();
        } else if (data.getQueryParameter("t") != null) {
            timestampStr = data.getQueryParameter("t");
        }
        int videoTimestamp = -1;
        if (timestampStr != null) {
            try {
                videoTimestamp = Integer.parseInt(timestampStr);
            } catch (Throwable ignore) {

            }
            if (videoTimestamp == -1) {
                DateFormat dateFormat = new SimpleDateFormat("mm:ss");
                Date reference = null;
                try {
                    reference = dateFormat.parse("00:00");
                    Date date = dateFormat.parse(timestampStr);
                    videoTimestamp = (int) ((date.getTime() - reference.getTime()) / 1000L);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return videoTimestamp;
    }

    private void openDialogsToSend(boolean animated) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("canSelectTopics", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowSwitchAccount", true);
        if (contactsToSend != null) {
            if (contactsToSend.size() != 1) {
                args.putString("selectAlertString", LocaleController.getString(R.string.SendMessagesToText));
                args.putString("selectAlertStringGroup", LocaleController.getString(R.string.SendContactToGroupText));
            }
        } else {
            args.putString("selectAlertString", LocaleController.getString(R.string.SendMessagesToText));
            args.putString("selectAlertStringGroup", LocaleController.getString(R.string.SendMessagesToGroupText));
        }
        DialogsActivity fragment = new DialogsActivity(args) {
            @Override
            public boolean shouldShowNextButton(DialogsActivity dialogsFragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                if (exportingChatUri != null) {
                    return false;
                }
                if (contactsToSend != null && contactsToSend.size() == 1 && !mainFragmentsStack.isEmpty()) {
                    return true;
                }
                if (dids.size() <= 1) {
                    return videoPath != null || photoPathsArray != null && photoPathsArray.size() > 0;
                }
                return false;
            }
        };
        fragment.setDelegate(this);
        boolean removeLast;
        if (AndroidUtilities.isTablet()) {
            removeLast = layersActionBarLayout.getFragmentStack().size() > 0 && layersActionBarLayout.getFragmentStack().get(layersActionBarLayout.getFragmentStack().size() - 1) instanceof DialogsActivity;
        } else {
            removeLast = actionBarLayout.getFragmentStack().size() > 1 && actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1) instanceof DialogsActivity;
        }
        getActionBarLayout().presentFragment(fragment, removeLast, !animated, true, false);
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(false, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        StoryRecorder.destroyInstance();
        if (GroupCallActivity.groupCallInstance != null) {
            GroupCallActivity.groupCallInstance.dismiss();
        }

        if (!animated) {
            drawerLayoutContainer.setAllowOpenDrawer(false, false);
            if (AndroidUtilities.isTablet()) {
                actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            } else {
                drawerLayoutContainer.setAllowOpenDrawer(true, false);
            }
        }
    }

    private int runCommentRequest(int intentAccount, Runnable dismissLoading, Integer messageId, Integer commentId, Long threadId, TLRPC.Chat chat) {
        return runCommentRequest(intentAccount, dismissLoading, messageId, commentId, threadId, chat, null, null, 0, -1);
    }

    private int runCommentRequest(int intentAccount, Runnable dismissLoading, Integer messageId, Integer commentId, Long threadId, TLRPC.Chat chat, Runnable onOpened, String quote, int fromMessageId, int quoteOffset) {
        if (chat == null) {
            return 0;
        }
        TLRPC.TL_messages_getDiscussionMessage req = new TLRPC.TL_messages_getDiscussionMessage();
        req.peer = MessagesController.getInputPeer(chat);
        req.msg_id = commentId != null ? messageId : (int) (long) threadId;
        return ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            boolean chatOpened = false;
            if (response instanceof TLRPC.TL_messages_discussionMessage) {
                TLRPC.TL_messages_discussionMessage res = (TLRPC.TL_messages_discussionMessage) response;
                MessagesController.getInstance(intentAccount).putUsers(res.users, false);
                MessagesController.getInstance(intentAccount).putChats(res.chats, false);
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                for (int a = 0, N = res.messages.size(); a < N; a++) {
                    arrayList.add(new MessageObject(UserConfig.selectedAccount, res.messages.get(a), true, true));
                }
                if (!arrayList.isEmpty() || chat.forum && threadId != null && threadId == 1) {
                    if (chat.forum) {
                        openTopicRequest(intentAccount, (int) (long) threadId, chat, commentId != null ? commentId : messageId, null, onOpened, quote, fromMessageId, arrayList, quoteOffset);
                        chatOpened = true;
                    } else {
                        Bundle args = new Bundle();
                        args.putLong("chat_id", -arrayList.get(0).getDialogId());
                        args.putInt("message_id", Math.max(1, messageId));
                        ChatActivity chatActivity = new ChatActivity(args);
                        chatActivity.setThreadMessages(arrayList, chat, req.msg_id, res.read_inbox_max_id, res.read_outbox_max_id, null);
                        if (commentId != null) {
                            if (quote != null) {
                                chatActivity.setHighlightQuote(commentId, quote, quoteOffset);
                            } else {
                                chatActivity.setHighlightMessageId(commentId);
                            }
                        } else if (threadId != null) {
                            if (quote != null) {
                                chatActivity.setHighlightQuote(messageId, quote, quoteOffset);
                            } else {
                                chatActivity.setHighlightMessageId(messageId);
                            }
                        }
                        presentFragment(chatActivity);
                        chatOpened = true;
                    }
                }
            }
            if (!chatOpened) {
                try {
                    if (!mainFragmentsStack.isEmpty()) {
                        BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.ChannelPostDeleted)).show();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            try {
                if (dismissLoading != null) {
                    dismissLoading.run();
                }
                if (onOpened != null) {
                    onOpened.run();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }));
    }

    private void openTopicRequest(int intentAccount, int topicId, TLRPC.Chat chat, int messageId, TLRPC.TL_forumTopic forumTopic, Runnable whenDone, String quote, int fromMessageId, ArrayList<MessageObject> arrayList, int quoteOffset) {
        if (forumTopic == null) {
            forumTopic = MessagesController.getInstance(intentAccount).getTopicsController().findTopic(chat.id, topicId);
        }
        if (forumTopic == null) {
            TLRPC.TL_channels_getForumTopicsByID getForumTopicsByID = new TLRPC.TL_channels_getForumTopicsByID();
            getForumTopicsByID.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
            getForumTopicsByID.topics.add(topicId);
            ConnectionsManager.getInstance(intentAccount).sendRequest(getForumTopicsByID, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                if (error2 == null) {
                    TLRPC.TL_messages_forumTopics topics = (TLRPC.TL_messages_forumTopics) response2;
                    SparseArray<TLRPC.Message> messagesMap = new SparseArray<>();
                    for (int i = 0; i < topics.messages.size(); i++) {
                        messagesMap.put(topics.messages.get(i).id, topics.messages.get(i));
                    }
                    MessagesController.getInstance(intentAccount).putUsers(topics.users, false);
                    MessagesController.getInstance(intentAccount).putChats(topics.chats, false);

                    MessagesController.getInstance(intentAccount).getTopicsController().processTopics(chat.id, topics.topics, messagesMap, false, TopicsController.LOAD_TYPE_LOAD_UNKNOWN, -1);

                    TLRPC.TL_forumTopic topic = MessagesController.getInstance(intentAccount).getTopicsController().findTopic(chat.id, topicId);
                    openTopicRequest(intentAccount, topicId, chat, messageId, topic, whenDone, quote, fromMessageId, arrayList, quoteOffset);
                };
            }));
            return;
        }
        BaseFragment lastFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
        if (lastFragment instanceof ChatActivity && ((ChatActivity) lastFragment).getDialogId() == -chat.id && ((ChatActivity) lastFragment).isTopic && ((ChatActivity) lastFragment).getTopicId() == forumTopic.id) {
            if (quote != null) {
                ((ChatActivity) lastFragment).setHighlightQuote(messageId, quote, quoteOffset);
            }
            ((ChatActivity) lastFragment).scrollToMessageId(messageId, fromMessageId, true, 0, true, 0, null);
        } else {
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            if (messageId != forumTopic.id) {
                args.putInt("message_id", Math.max(1, messageId));
            }
            ChatActivity chatActivity = new ChatActivity(args);
            if (arrayList.isEmpty()) {
                TLRPC.Message message = new TLRPC.Message();
                message.id = 1;
                message.action = new TLRPC.TL_messageActionChannelMigrateFrom();
                arrayList.add(new MessageObject(intentAccount, message, false, false));
            }
            chatActivity.setThreadMessages(arrayList, chat, messageId, forumTopic.read_inbox_max_id, forumTopic.read_outbox_max_id, forumTopic);
            if (messageId != forumTopic.id) {
                if (quote != null) {
                    chatActivity.setHighlightQuote(messageId, quote, quoteOffset);
                } else {
                    chatActivity.setHighlightMessageId(messageId);
                }
                chatActivity.scrollToMessageId(messageId, fromMessageId, true, 0, true, 0, null);
            }
            presentFragment(chatActivity);
        }
        if (whenDone != null) {
            whenDone.run();
        }
    }

    private String readImport(Uri uri) {
        final String filename = FileLoader.fixFileName(MediaController.getFileName(uri));
        if (filename != null && filename.endsWith(".zip")) {
            String content = null;
            try {
                try (ZipInputStream zis = new ZipInputStream(getContentResolver().openInputStream(uri))) {
                    ZipEntry zipEntry = zis.getNextEntry();
                    while (zipEntry != null) {
                        String name = zipEntry.getName();
                        if (name == null) {
                            zipEntry = zis.getNextEntry();
                            continue;
                        }
                        int idx = name.lastIndexOf("/");
                        if (idx >= 0) {
                            name = name.substring(idx + 1);
                        }
                        if (name.endsWith(".txt")) {
                            try {
                                int linesCount = 0;
                                BufferedReader r = new BufferedReader(new InputStreamReader(zis));
                                StringBuilder total = new StringBuilder();
                                for (String line; (line = r.readLine()) != null && linesCount < 100; ) {
                                    total.append(line).append('\n');
                                    linesCount++;
                                }
                                content = total.toString();
                            } catch (Exception e) {
                                FileLog.e(e);
                                return null;
                            }
                            break;
                        }
                        zipEntry = zis.getNextEntry();
                    }
                    zis.closeEntry();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            return content;
        } else {
            String content;
            InputStream inputStream = null;
            try {
                int linesCount = 0;
                inputStream = getContentResolver().openInputStream(uri);
                BufferedReader r = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder total = new StringBuilder();
                for (String line; (line = r.readLine()) != null && linesCount < 100; ) {
                    total.append(line).append('\n');
                    linesCount++;
                }
                content = total.toString();
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
            return content;
        }
    }

    private void runImportRequest(final Uri importUri,
                                  ArrayList<Uri> documents) {
        final int intentAccount = UserConfig.selectedAccount;
        final AlertDialog progressDialog = new AlertDialog(this, AlertDialog.ALERT_TYPE_SPINNER);
        final int[] requestId = new int[]{0};
        Runnable cancelRunnable = null;

        String content = readImport(importUri);
        if (content == null) return;
        final TLRPC.TL_messages_checkHistoryImport req = new TLRPC.TL_messages_checkHistoryImport();
        req.import_head = content;
        requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!LaunchActivity.this.isFinishing()) {
                if (response != null && actionBarLayout != null) {
                    final TLRPC.TL_messages_historyImportParsed res = (TLRPC.TL_messages_historyImportParsed) response;
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putString("importTitle", res.title);

                    args.putBoolean("allowSwitchAccount", true);
                    if (res.pm) {
                        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_USERS);
                    } else if (res.group) {
                        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS);
                    } else {
                        String uri = importUri.toString();
                        Set<String> uris = MessagesController.getInstance(intentAccount).exportPrivateUri;
                        boolean ok = false;
                        for (String u : uris) {
                            if (uri.contains(u)) {
                                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_USERS);
                                ok = true;
                                break;
                            }
                        }
                        if (!ok) {
                            uris = MessagesController.getInstance(intentAccount).exportGroupUri;
                            for (String u : uris) {
                                if (uri.contains(u)) {
                                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS);
                                    ok = true;
                                    break;
                                }
                            }
                            if (!ok) {
                                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY);
                            }
                        }
                    }

                    if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
                        SecretMediaViewer.getInstance().closePhoto(false, false);
                    } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                        PhotoViewer.getInstance().closePhoto(false, true);
                    } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
                        ArticleViewer.getInstance().close(false, true);
                    }
                    StoryRecorder.destroyInstance();
                    if (GroupCallActivity.groupCallInstance != null) {
                        GroupCallActivity.groupCallInstance.dismiss();
                    }

                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                        rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    } else {
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }

                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate(this);
                    boolean removeLast;
                    if (AndroidUtilities.isTablet()) {
                        removeLast = layersActionBarLayout.getFragmentStack().size() > 0 && layersActionBarLayout.getFragmentStack().get(layersActionBarLayout.getFragmentStack().size() - 1) instanceof DialogsActivity;
                    } else {
                        removeLast = actionBarLayout.getFragmentStack().size() > 1 && actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1) instanceof DialogsActivity;
                    }
                    getActionBarLayout().presentFragment(fragment, removeLast, false, true, false);
                } else {
                    if (documentsUrisArray == null) {
                        documentsUrisArray = new ArrayList<>();
                    }
                    documentsUrisArray.add(0, exportingChatUri);
                    exportingChatUri = null;
                    openDialogsToSend(true);
                }
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors));
        final Runnable cancelRunnableFinal = cancelRunnable;
        progressDialog.setOnCancelListener(dialog -> {
            ConnectionsManager.getInstance(intentAccount).cancelRequest(requestId[0], true);
            if (cancelRunnableFinal != null) {
                cancelRunnableFinal.run();
            }
        });
        try {
            progressDialog.showDelayed(300);
        } catch (Exception ignore) {

        }
    }

    private void openGroupCall(AccountInstance accountInstance, TLRPC.Chat chat, String hash) {
        VoIPHelper.startCall(chat, null, hash, false, this, mainFragmentsStack.get(mainFragmentsStack.size() - 1), accountInstance);
    }

    public void openMessage(long dialogId, int messageId, String quote, final Browser.Progress progress, int fromMessageId, final int quoteOffset) {
        if (dialogId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null && ChatObject.isForum(chat)) {
                if (progress != null) {
                    progress.init();
                }
                openForumFromLink(dialogId, messageId, quote, () -> {
                    if (progress != null) {
                        progress.end();
                    }
                }, fromMessageId, quoteOffset);
                return;
            }
        }
        if (progress != null) {
            progress.init();
        }
        Bundle args = new Bundle();
        if (dialogId >= 0) {
            args.putLong("user_id", dialogId);
        } else {
            TLRPC.Chat chatLocal = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chatLocal != null && chatLocal.forum) {
                openForumFromLink(dialogId, messageId, quote, () -> {
                    if (progress != null) {
                        progress.end();
                    }
                }, fromMessageId, quoteOffset);
                return;
            }
            args.putLong("chat_id", -dialogId);
        }
        args.putInt("message_id", messageId);
        BaseFragment lastFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
        if (lastFragment == null || MessagesController.getInstance(currentAccount).checkCanOpenChat(args, lastFragment)) {
            AndroidUtilities.runOnUIThread(() -> {
                ChatActivity chatActivity = new ChatActivity(args);
                chatActivity.setHighlightQuote(messageId, quote, quoteOffset);
                if (!(AndroidUtilities.isTablet() ? rightActionBarLayout : getActionBarLayout()).presentFragment(chatActivity) && dialogId < 0) {
                    TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
                    TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
                    inputChannel.channel_id = -dialogId;
                    req.id.add(inputChannel);
                    final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (progress != null) {
                            progress.end();
                        }
                        boolean notFound = true;
                        if (response instanceof TLRPC.TL_messages_chats) {
                            TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                            if (!res.chats.isEmpty()) {
                                notFound = false;
                                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                                TLRPC.Chat chat = res.chats.get(0);
                                if (chat != null && chat.forum) {
                                    openForumFromLink(-dialogId, messageId, null);
                                }
                                if (lastFragment == null || MessagesController.getInstance(currentAccount).checkCanOpenChat(args, lastFragment)) {
                                    ChatActivity chatActivity2 = new ChatActivity(args);
                                    chatActivity.setHighlightQuote(messageId, quote, quoteOffset);
                                    getActionBarLayout().presentFragment(chatActivity2);
                                }
                            }
                        }
                        if (notFound) {
                            showAlertDialog(AlertsCreator.createNoAccessAlert(LaunchActivity.this, LocaleController.getString(R.string.DialogNotAvailable), LocaleController.getString(R.string.LinkNotFound), null));
                        }
                    }));
                    if (progress != null) {
                        progress.onCancel(() -> {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                        });
                    }
                } else {
                    if (progress != null) {
                        progress.end();
                    }
                }
            });
        }
    }

    private void runLinkRequest(final int intentAccount,
                                final String username,
                                final String group,
                                final String sticker,
                                final String emoji,
                                final String botUser,
                                final String botChat,
                                final String botChannel,
                                final String botChatAdminParams,
                                final String message,
                                final String contactToken,
                                final String folderSlug,
                                String text, final boolean hasUrl,
                                final Integer messageId,
                                final Long channelId,
                                final Long threadId,
                                final Integer commentId,
                                final String game,
                                final HashMap<String, String> auth,
                                final String lang,
                                final String unsupportedUrl,
                                final String code,
                                final String loginToken,
                                final TLRPC.TL_wallPaper wallPaper,
                                final String inputInvoiceSlug,
                                final String theme,
                                final String voicechat,
                                final boolean videochat,
                                final String livestream,
                                final int state,
                                final int videoTimestamp,
                                final String setAsAttachBot,
                                final String attachMenuBotToOpen,
                                final String attachMenuBotChoose,
                                final String botAppMaybe,
                                final String botAppStartParam,
                                final Browser.Progress progress,
                                final boolean forceNotInternalForApps,
                                final int storyId,
                                final boolean isBoost,
                                final String chatLinkSlug, boolean botCompact, boolean botFullscreen, boolean openedTelegram, boolean openProfile, boolean forceRequest, String referrer) {
        if (state == 0 && ChatActivity.SCROLL_DEBUG_DELAY && progress != null) {
            Runnable runnable = () -> runLinkRequest(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, 1, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, botAppStartParam, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
            progress.init();
            progress.onCancel(() -> AndroidUtilities.cancelRunOnUIThread(runnable));
            AndroidUtilities.runOnUIThread(runnable, 7500);
            return;
        } else if (state == 0 && UserConfig.getActivatedAccountsCount() >= 2 && auth != null) {
            AlertsCreator.createAccountSelectDialog(this, account -> {
                if (account != intentAccount) {
                    switchToAccount(account, true);
                }
                runLinkRequest(account, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, 1, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, botAppStartParam, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
            }).show();
            return;
        } else if (code != null) {
            if (NotificationCenter.getGlobalInstance().hasObservers(NotificationCenter.didReceiveSmsCode)) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReceiveSmsCode, code);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                builder.setTitle(LocaleController.getString(R.string.AppName));
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.OtherLoginCode, code)));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showAlertDialog(builder);
            }
            return;
        } else if (loginToken != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
            builder.setTitle(LocaleController.getString(R.string.AuthAnotherClient));
            builder.setMessage(LocaleController.getString(R.string.AuthAnotherClientUrl));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            showAlertDialog(builder);
            return;
        }
        final AlertDialog progressDialog = new AlertDialog(this, AlertDialog.ALERT_TYPE_SPINNER);
        final Runnable dismissLoading = () -> {
            if (progress != null) {
                progress.end();
            }
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        };
        final int[] requestId = new int[]{0};
        Runnable cancelRunnable = null;

        if (contactToken != null) {
            TLRPC.TL_contacts_importContactToken req = new TLRPC.TL_contacts_importContactToken();
            req.token = contactToken;
            requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) response;
                    MessagesController.getInstance(intentAccount).putUser(user, false);
                    Bundle args = new Bundle();
                    args.putLong("user_id", user.id);
                    presentFragment(new ChatActivity(args));
                } else {
                    FileLog.e("cant import contact token. token=" + contactToken + " err=" + (error == null ? null : error.text));
                    BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.NoUsernameFound)).show();
                }

                try {
                    dismissLoading.run();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }));
        } else if (folderSlug != null) {
            TL_chatlists.TL_chatlists_checkChatlistInvite req = new TL_chatlists.TL_chatlists_checkChatlistInvite();
            req.slug = folderSlug;
            requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (response instanceof TL_chatlists.chatlist_ChatlistInvite) {
                    TL_chatlists.chatlist_ChatlistInvite inv = (TL_chatlists.chatlist_ChatlistInvite) response;
                    ArrayList<TLRPC.Chat> chats = null;
                    ArrayList<TLRPC.User> users = null;
                    if (inv instanceof TL_chatlists.TL_chatlists_chatlistInvite) {
                        chats = ((TL_chatlists.TL_chatlists_chatlistInvite) inv).chats;
                        users = ((TL_chatlists.TL_chatlists_chatlistInvite) inv).users;
                    } else if (inv instanceof TL_chatlists.TL_chatlists_chatlistInviteAlready) {
                        chats = ((TL_chatlists.TL_chatlists_chatlistInviteAlready) inv).chats;
                        users = ((TL_chatlists.TL_chatlists_chatlistInviteAlready) inv).users;
                    }
                    MessagesController.getInstance(intentAccount).putChats(chats, false);
                    MessagesController.getInstance(intentAccount).putUsers(users, false);
                    if (!(inv instanceof TL_chatlists.TL_chatlists_chatlistInvite && ((TL_chatlists.TL_chatlists_chatlistInvite) inv).peers.isEmpty())) {
                        final FolderBottomSheet sheet = new FolderBottomSheet(fragment, folderSlug, inv);
                        if (fragment != null) {
                            fragment.showDialog(sheet);
                        } else {
                            sheet.show();
                        }
                    } else {
                        BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.NoFolderFound)).show();
                    }
                } else {
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.NoFolderFound)).show();
                }

                try {
                    dismissLoading.run();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }));
        } else if (inputInvoiceSlug != null) {
            TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
            TLRPC.TL_inputInvoiceSlug invoiceSlug = new TLRPC.TL_inputInvoiceSlug();
            invoiceSlug.slug = inputInvoiceSlug;
            req.invoice = invoiceSlug;
            requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    if ("SUBSCRIPTION_ALREADY_ACTIVE".equalsIgnoreCase(error.text)) {
                        BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.PaymentInvoiceSubscriptionLinkAlreadyPaid)).show();
                    } else {
                        BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.PaymentInvoiceLinkInvalid)).show();
                    }
                } else if (!LaunchActivity.this.isFinishing()) {
                    PaymentFormActivity paymentFormActivity = null;
                    if (response instanceof TLRPC.TL_payments_paymentFormStars) {
                        Runnable callback = navigateToPremiumGiftCallback;
                        navigateToPremiumGiftCallback = null;
                        StarsController.getInstance(currentAccount).openPaymentForm(null, invoiceSlug, (TLRPC.TL_payments_paymentFormStars) response, () -> {
                            try {
                                dismissLoading.run();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }, status -> {
                            if (callback != null && "paid".equals(status)) {
                                callback.run();
                            }
                        });
                        return;
                    } else if (response instanceof TLRPC.PaymentForm) {
                        TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                        MessagesController.getInstance(intentAccount).putUsers(form.users, false);
                        paymentFormActivity = new PaymentFormActivity(form, inputInvoiceSlug, getActionBarLayout().getLastFragment());
                    } else if (response instanceof TLRPC.PaymentReceipt) {
                        paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                    }

                    if (paymentFormActivity != null) {
                        if (navigateToPremiumGiftCallback != null) {
                            Runnable callback = navigateToPremiumGiftCallback;
                            navigateToPremiumGiftCallback = null;
                            paymentFormActivity.setPaymentFormCallback(status -> {
                                if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                                    callback.run();
                                }
                            });
                        }
                        presentFragment(paymentFormActivity);
                    }
                }

                try {
                    dismissLoading.run();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }));
        } else if (username != null) {
            if (progress != null) {
                progress.init();
            }
            MessagesController.getInstance(intentAccount).getUserNameResolver().resolve(username, referrer, (peerId) -> {
                if (peerId != null && peerId == Long.MAX_VALUE) {
                    try {
                        dismissLoading.run();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    new AlertDialog.Builder(this, null)
                        .setTitle(LocaleController.getString(R.string.AffiliateLinkExpiredTitle))
                        .setMessage(LocaleController.getString(R.string.AffiliateLinkExpiredText))
                        .setNegativeButton(LocaleController.getString(R.string.OK), null)
                        .show();
                    return;
                }
                if (!LaunchActivity.this.isFinishing()) {
                    boolean hideProgressDialog = true;
                    if (storyId != 0 && peerId != null) {
                        hideProgressDialog = false;
                        MessagesController.getInstance(currentAccount).getStoriesController().resolveStoryLink(peerId, storyId, storyItem -> {
                            try {
                                dismissLoading.run();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            BaseFragment baseFragment = getLastFragment();
                            if (storyItem == null) {
                                BulletinFactory factory = BulletinFactory.global();
                                if (factory != null) {
                                    factory.createSimpleBulletin(R.raw.story_bomb2, LocaleController.getString(R.string.StoryNotFound)).show();
                                }
                                return;
                            } else if (storyItem instanceof TL_stories.TL_storyItemDeleted) {
                                BulletinFactory factory = BulletinFactory.global();
                                if (factory != null) {
                                    factory.createSimpleBulletin(R.raw.story_bomb1, LocaleController.getString(R.string.StoryNotFound)).show();
                                }
                                return;
                            }
                            if (baseFragment != null) {
                                storyItem.dialogId = peerId;
                                StoryViewer storyViewer = baseFragment.createOverlayStoryViewer();
                                storyViewer.instantClose();
                                storyViewer.open(this, storyItem, null);
                            }
                        });
                    } else if (peerId != null && actionBarLayout != null && (game == null && voicechat == null || game != null && peerId > 0 || voicechat != null && peerId > 0 || videochat && peerId < 0 || livestream != null && peerId < 0)) {
                        if (!TextUtils.isEmpty(botAppMaybe)) {
                            TLRPC.User user = MessagesController.getInstance(intentAccount).getUser(peerId);
                            if (user != null && user.bot) {
                                if (user.bot_attach_menu && !MediaDataController.getInstance(intentAccount).botInAttachMenu(user.id)) {
                                    TLRPC.TL_messages_getAttachMenuBot getAttachMenuBot = new TLRPC.TL_messages_getAttachMenuBot();
                                    getAttachMenuBot.bot = MessagesController.getInstance(intentAccount).getInputUser(peerId);
                                    ConnectionsManager.getInstance(intentAccount).sendRequest(getAttachMenuBot, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (error1 != null) {
                                            AndroidUtilities.runOnUIThread(() -> runLinkRequest(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, state, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, null, null, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer));
                                        } else if (response1 instanceof TLRPC.TL_attachMenuBotsBot) {
                                            TLRPC.TL_attachMenuBotsBot bot = (TLRPC.TL_attachMenuBotsBot) response1;
                                            TLRPC.TL_attachMenuBot attachBot = bot.bot;
                                            final boolean botAttachable = attachBot != null && (attachBot.show_in_side_menu || attachBot.show_in_attach_menu);
                                            if ((attachBot.inactive || attachBot.side_menu_disclaimer_needed) && botAttachable) {
                                                WebAppDisclaimerAlert.show(this, (allowSendMessage) -> {
                                                    attachBot.inactive = false;
                                                    attachBot.request_write_access = false;

                                                    TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                                                    botRequest.bot = MessagesController.getInstance(intentAccount).getInputUser(peerId);
                                                    botRequest.enabled = true;
                                                    botRequest.write_allowed = true;

                                                    ConnectionsManager.getInstance(intentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                                        if (response2 instanceof TLRPC.TL_boolTrue) {
                                                            MediaDataController.getInstance(intentAccount).loadAttachMenuBots(false, true, null);
                                                        }
                                                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);

                                                    processWebAppBot(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, state, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, botAppStartParam, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, user, dismissLoading, botAttachable, true, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
                                                }, null, progress != null ? progress::end : null);
                                            } else if (attachBot.request_write_access || forceNotInternalForApps) {
                                                AtomicBoolean allowWrite = new AtomicBoolean(true);
                                                AlertsCreator.createBotLaunchAlert(getLastFragment(), allowWrite, user, () -> {
                                                    SharedPrefsHelper.setWebViewConfirmShown(currentAccount, peerId, true);

                                                    attachBot.inactive = false;
                                                    attachBot.request_write_access = !allowWrite.get();

                                                    TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                                                    botRequest.bot = MessagesController.getInstance(intentAccount).getInputUser(peerId);
                                                    botRequest.write_allowed = allowWrite.get();

                                                    ConnectionsManager.getInstance(intentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                                        if (response2 instanceof TLRPC.TL_boolTrue) {
                                                            MediaDataController.getInstance(intentAccount).loadAttachMenuBots(false, true, null);
                                                        }
                                                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);

                                                    processWebAppBot(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, state, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, botAppStartParam, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, user, dismissLoading, false, false, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
                                                });
                                            } else {
                                                processWebAppBot(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, state, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, botAppStartParam, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, user, dismissLoading, false, false, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
                                            }
                                        }
                                    }));
                                } else {
                                    processWebAppBot(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, state, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, botAppMaybe, botAppStartParam, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, user, dismissLoading, false, false, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer);
                                }
                                return;
                            }
                        }

                        if (isBoost) {
                            TLRPC.Chat chat = MessagesController.getInstance(intentAccount).getChat(-peerId);
                            if (ChatObject.isBoostSupported(chat)) {
                                processBoostDialog(peerId, dismissLoading, progress);
                                return;
                            }
                        }

                        if (botAppStartParam != null) {
                            TLRPC.User user = MessagesController.getInstance(intentAccount).getUser(peerId);
                            if (user != null && user.bot) {
                                MessagesController.getInstance(intentAccount).openApp(null, user, botAppStartParam, 0, progress, botCompact, botFullscreen);
                            }
                        } else if (setAsAttachBot != null && attachMenuBotToOpen == null) {
                            TLRPC.User user = MessagesController.getInstance(intentAccount).getUser(peerId);
                            if (user != null && user.bot) {
                                if (user.bot_attach_menu) {
                                    processAttachMenuBot(intentAccount, peerId, attachMenuBotChoose, user, setAsAttachBot, botAppStartParam);
                                } else {
                                    BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.BotCantAddToAttachMenu)).show();
                                }
                            } else {
                                BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.BotSetAttachLinkNotBot)).show();
                            }
                        } else if (messageId != null && (commentId != null || threadId != null) && peerId < 0) {
                            TLRPC.Chat chat = MessagesController.getInstance(intentAccount).getChat(-peerId);
                            requestId[0] = runCommentRequest(intentAccount, dismissLoading, messageId, commentId, threadId, chat);
                            if (requestId[0] != 0) {
                                hideProgressDialog = false;
                            }
                        } else if (game != null) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlySelect", true);
                            args.putBoolean("cantSendToChannels", true);
                            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_BOT_SHARE);
                            args.putString("selectAlertString", LocaleController.getString(R.string.SendGameToText));
                            args.putString("selectAlertStringGroup", LocaleController.getString(R.string.SendGameToGroupText));
                            DialogsActivity fragment = new DialogsActivity(args);
                            TLRPC.User user = MessagesController.getInstance(intentAccount).getUser(peerId);
                            fragment.setDelegate((fragment1, dids, message1, param, notify, scheduleDate, topicsFragment) -> {
                                long did = dids.get(0).dialogId;
                                TLRPC.TL_inputMediaGame inputMediaGame = new TLRPC.TL_inputMediaGame();
                                inputMediaGame.id = new TLRPC.TL_inputGameShortName();
                                inputMediaGame.id.short_name = game;
                                inputMediaGame.id.bot_id = MessagesController.getInstance(intentAccount).getInputUser(user);
                                SendMessagesHelper.getInstance(intentAccount).sendGame(MessagesController.getInstance(intentAccount).getInputPeer(did), inputMediaGame, 0, 0);

                                Bundle args1 = new Bundle();
                                args1.putBoolean("scrollToTopOnResume", true);
                                if (DialogObject.isEncryptedDialog(did)) {
                                    args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                                } else if (DialogObject.isUserDialog(did)) {
                                    args1.putLong("user_id", did);
                                } else {
                                    args1.putLong("chat_id", -did);
                                }
                                if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args1, fragment1)) {
                                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                    getActionBarLayout().presentFragment(new ChatActivity(args1), true, false, true, false);
                                }
                                return true;
                            });
                            boolean removeLast;
                            if (AndroidUtilities.isTablet()) {
                                removeLast = layersActionBarLayout.getFragmentStack().size() > 0 && layersActionBarLayout.getFragmentStack().get(layersActionBarLayout.getFragmentStack().size() - 1) instanceof DialogsActivity;
                            } else {
                                removeLast = actionBarLayout.getFragmentStack().size() > 1 && actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1) instanceof DialogsActivity;
                            }
                            getActionBarLayout().presentFragment(fragment, removeLast, true, true, false);
                            if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
                                SecretMediaViewer.getInstance().closePhoto(false, false);
                            } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                                PhotoViewer.getInstance().closePhoto(false, true);
                            } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
                                ArticleViewer.getInstance().close(false, true);
                            }
                            StoryRecorder.destroyInstance();
                            if (GroupCallActivity.groupCallInstance != null) {
                                GroupCallActivity.groupCallInstance.dismiss();
                            }
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                            if (AndroidUtilities.isTablet()) {
                                actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                                rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                            } else {
                                drawerLayoutContainer.setAllowOpenDrawer(true, false);
                            }
                        } else if (botChat != null || botChannel != null) {
                            final TLRPC.User user = MessagesController.getInstance(intentAccount).getUser(peerId);
                            if (user == null || user.bot && user.bot_nochats) {
                                try {
                                    if (!mainFragmentsStack.isEmpty()) {
                                        BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.BotCantJoinGroups)).show();
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                return;
                            }
                            Bundle args = new Bundle();
                            args.putBoolean("onlySelect", true);
                            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO);
                            args.putBoolean("resetDelegate", false);
                            args.putBoolean("closeFragment", false);
                            args.putBoolean("allowGroups", botChat != null);
                            args.putBoolean("allowChannels", botChannel != null);
                            final String botHash = TextUtils.isEmpty(botChat) ? (TextUtils.isEmpty(botChannel) ? null : botChannel) : botChat;
//                            args.putString("addToGroupAlertString", LocaleController.formatString("AddToTheGroupAlertText", R.string.AddToTheGroupAlertText, UserObject.getUserName(user), "%1$s"));
                            DialogsActivity fragment = new DialogsActivity(args);
                            fragment.setDelegate((fragment12, dids, message1, param, notify, scheduleDate, topicsFragment) -> {
                                long did = dids.get(0).dialogId;

                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                                if (chat != null && (chat.creator || chat.admin_rights != null && chat.admin_rights.add_admins)) {
                                    MessagesController.getInstance(intentAccount).checkIsInChat(false, chat, user, (isInChatAlready, currentRights, currentRank) -> AndroidUtilities.runOnUIThread(() -> {
                                        TLRPC.TL_chatAdminRights requestingRights = null;
                                        if (botChatAdminParams != null) {
                                            String[] adminParams = botChatAdminParams.split("\\+| ");
                                            requestingRights = new TLRPC.TL_chatAdminRights();
                                            final int count = adminParams.length;
                                            for (int i = 0; i < count; ++i) {
                                                String adminParam = adminParams[i];
                                                switch (adminParam) {
                                                    case "change_info":
                                                        requestingRights.change_info = true;
                                                        break;
                                                    case "post_messages":
                                                        requestingRights.post_messages = true;
                                                        break;
                                                    case "edit_messages":
                                                        requestingRights.edit_messages = true;
                                                        break;
                                                    case "add_admins":
                                                    case "promote_members":
                                                        requestingRights.add_admins = true;
                                                        break;
                                                    case "delete_messages":
                                                        requestingRights.delete_messages = true;
                                                        break;
                                                    case "ban_users":
                                                    case "restrict_members":
                                                        requestingRights.ban_users = true;
                                                        break;
                                                    case "invite_users":
                                                        requestingRights.invite_users = true;
                                                        break;
                                                    case "pin_messages":
                                                        requestingRights.pin_messages = true;
                                                        break;
                                                    case "manage_video_chats":
                                                    case "manage_call":
                                                        requestingRights.manage_call = true;
                                                        break;
                                                    case "manage_chat":
                                                    case "other":
                                                        requestingRights.other = true;
                                                        break;
                                                    case "anonymous":
                                                        requestingRights.anonymous = true;
                                                        break;
                                                }
                                            }
                                        }
                                        TLRPC.TL_chatAdminRights editRights = null;
                                        if (requestingRights != null || currentRights != null) {
                                            if (requestingRights == null) {
                                                editRights = currentRights;
                                            } else if (currentRights == null) {
                                                editRights = requestingRights;
                                            } else {
                                                editRights = currentRights;
                                                editRights.change_info = requestingRights.change_info || editRights.change_info;
                                                editRights.post_messages = requestingRights.post_messages || editRights.post_messages;
                                                editRights.edit_messages = requestingRights.edit_messages || editRights.edit_messages;
                                                editRights.add_admins = requestingRights.add_admins || editRights.add_admins;
                                                editRights.delete_messages = requestingRights.delete_messages || editRights.delete_messages;
                                                editRights.ban_users = requestingRights.ban_users || editRights.ban_users;
                                                editRights.invite_users = requestingRights.invite_users || editRights.invite_users;
                                                editRights.pin_messages = requestingRights.pin_messages || editRights.pin_messages;
                                                editRights.manage_call = requestingRights.manage_call || editRights.manage_call;
                                                editRights.anonymous = requestingRights.anonymous || editRights.anonymous;
                                                editRights.other = requestingRights.other || editRights.other;
                                            }
                                        }
                                        if (isInChatAlready && requestingRights == null && !TextUtils.isEmpty(botHash)) {
                                            Runnable onFinish = () -> {
                                                NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);

                                                Bundle args1 = new Bundle();
                                                args1.putBoolean("scrollToTopOnResume", true);
                                                args1.putLong("chat_id", chat.id);
                                                if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment)) {
                                                    return;
                                                }
                                                ChatActivity chatActivity = new ChatActivity(args1);
                                                presentFragment(chatActivity, true, false);
                                            };
                                            MessagesController.getInstance(currentAccount).addUserToChat(chat.id, user, 0, botHash, fragment, true, onFinish, null);
                                        } else {
                                            ChatRightsEditActivity editRightsActivity = new ChatRightsEditActivity(user.id, -did, editRights, null, null, currentRank, ChatRightsEditActivity.TYPE_ADD_BOT, true, !isInChatAlready, botHash);
                                            editRightsActivity.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                                                @Override
                                                public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                                                    fragment.removeSelfFromStack();
                                                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                                }

                                                @Override
                                                public void didChangeOwner(TLRPC.User user) {
                                                }
                                            });
                                            getActionBarLayout().presentFragment(editRightsActivity, false);
                                        }
                                    }));
                                } else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                    builder.setTitle(LocaleController.getString(R.string.AddBot));
                                    String chatName = chat == null ? "" : chat.title;
                                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, UserObject.getUserName(user), chatName)));
                                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                                    builder.setPositiveButton(LocaleController.getString(R.string.AddBot), (di, i) -> {
                                        Bundle args12 = new Bundle();
                                        args12.putBoolean("scrollToTopOnResume", true);
                                        args12.putLong("chat_id", -did);

                                        ChatActivity chatActivity = new ChatActivity(args12);
                                        NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                        MessagesController.getInstance(intentAccount).addUserToChat(-did, user, 0, botHash, chatActivity, null);
                                        getActionBarLayout().presentFragment(chatActivity, true, false, true, false);
                                    });
                                    builder.show();
                                }
                                return true;
                            });
                            presentFragment(fragment);
                        } else {
                            long dialog_id;
                            boolean isBot = false;
                            Bundle args = new Bundle();
                            TLRPC.User user = MessagesController.getInstance(intentAccount).getUser(peerId);
                            if (peerId < 0) {
                                args.putLong("chat_id", -peerId);
                                dialog_id = peerId;
                            } else {
                                args.putLong("user_id", peerId);
                                dialog_id = peerId;
                                if (text != null) {
                                    String textToSet = text;
                                    if (textToSet.startsWith("@")) {
                                        textToSet = " " + textToSet;
                                    }
                                    args.putString("start_text", textToSet);
                                }
                            }
                            if (botUser != null && user != null && user.bot) {
                                args.putString("botUser", botUser);
                                isBot = true;
                            }
                            if (navigateToPremiumBot) {
                                navigateToPremiumBot = false;
                                args.putBoolean("premium_bot", true);
                            }
                            if (messageId != null) {
                                args.putInt("message_id", messageId);
                            }
                            if (voicechat != null) {
                                args.putString("voicechat", voicechat);
                            }
                            if (videochat) {
                                args.putBoolean("videochat", true);
                            }
                            if (livestream != null) {
                                args.putString("livestream", livestream);
                            }
                            if (videoTimestamp >= 0) {
                                args.putInt("video_timestamp", videoTimestamp);
                            }
                            if (attachMenuBotToOpen != null) {
                                args.putString("attach_bot", attachMenuBotToOpen);
                            }
                            if (setAsAttachBot != null) {
                                args.putString("attach_bot_start_command", setAsAttachBot);
                            }
                            BaseFragment lastFragment = !mainFragmentsStack.isEmpty() && voicechat == null ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
                            if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
                                final boolean sameDialogId = lastFragment instanceof ChatActivity && ((ChatActivity) lastFragment).getDialogId() == dialog_id;
                                if (isBot && sameDialogId) {
                                    ((ChatActivity) lastFragment).setBotUser(botUser);
                                } else if (attachMenuBotToOpen != null && sameDialogId) {
                                    ((ChatActivity) lastFragment).openAttachBotLayout(attachMenuBotToOpen);
                                } else {
                                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog_id);
                                    if (openProfile) {
                                        try {
                                            dismissLoading.run();
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                        if (LaunchActivity.this.isFinishing()) return;
                                        Bundle profile_args = new Bundle();
                                        if (peerId < 0) {
                                            profile_args.putLong("chat_id", -peerId);
                                        } else {
                                            profile_args.putLong("user_id", peerId);
                                        }
                                        getActionBarLayout().presentFragment(new ProfileActivity(profile_args));
                                    } else if (chat != null && chat.forum) {
                                        Long topicId = threadId;
                                        if (topicId == null && messageId != null) {
                                            topicId = (long) (int) messageId;
                                        }
                                        if (topicId != null && topicId != 0) {
                                            openForumFromLink(dialog_id, messageId, () -> {
                                                try {
                                                    dismissLoading.run();
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                            });
                                        } else {
                                            Bundle bundle = new Bundle();
                                            bundle.putLong("chat_id", -dialog_id);
                                            if (voicechat != null) {
                                                bundle.putString("voicechat", voicechat);
                                            }
                                            if (videochat) {
                                                bundle.putBoolean("videochat", true);
                                            }
                                            presentFragment(TopicsFragment.getTopicsOrChat(this, bundle));
                                            try {
                                                dismissLoading.run();
                                            } catch (Exception e) {
                                                FileLog.e(e);
                                            }
                                        }
                                    } else {
                                        MessagesController.getInstance(intentAccount).ensureMessagesLoaded(dialog_id, messageId == null ? 0 : messageId, new MessagesController.MessagesLoadedCallback() {
                                            @Override
                                            public void onMessagesLoaded(boolean fromCache) {
                                                try {
                                                    dismissLoading.run();
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                                if (!LaunchActivity.this.isFinishing()) {
                                                    BaseFragment voipLastFragment;
                                                    if (livestream == null || !(lastFragment instanceof ChatActivity) || ((ChatActivity) lastFragment).getDialogId() != dialog_id) {
                                                        if (lastFragment instanceof ChatActivity && ((ChatActivity) lastFragment).getDialogId() == dialog_id && messageId == null) {
                                                            ChatActivity chatActivity = (ChatActivity) lastFragment;
                                                            ViewGroup v = chatActivity.getChatListView();
                                                            AndroidUtilities.shakeViewSpring(v, 5);
                                                            BotWebViewVibrationEffect.APP_ERROR.vibrate();

                                                            v = chatActivity.getChatActivityEnterView();
                                                            for (int i = 0; i < v.getChildCount(); i++) {
                                                                AndroidUtilities.shakeViewSpring(v.getChildAt(i), 5);
                                                            }
                                                            v = chatActivity.getActionBar();
                                                            for (int i = 0; i < v.getChildCount(); i++) {
                                                                AndroidUtilities.shakeViewSpring(v.getChildAt(i), 5);
                                                            }
                                                            voipLastFragment = lastFragment;
                                                        } else {
                                                            ChatActivity fragment = new ChatActivity(args);
                                                            getActionBarLayout().presentFragment(fragment);
                                                            voipLastFragment = fragment;
                                                        }
                                                    } else {
                                                        voipLastFragment = lastFragment;
                                                    }

                                                    AndroidUtilities.runOnUIThread(() -> {
                                                        if (livestream != null) {
                                                            AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
                                                            ChatObject.Call cachedCall = accountInstance.getMessagesController().getGroupCall(-dialog_id, false);
                                                            if (cachedCall != null) {
                                                                VoIPHelper.startCall(accountInstance.getMessagesController().getChat(-dialog_id), accountInstance.getMessagesController().getInputPeer(dialog_id), null, false, cachedCall == null || !cachedCall.call.rtmp_stream, LaunchActivity.this, voipLastFragment, accountInstance);
                                                            } else {
                                                                TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(-dialog_id);
                                                                if (chatFull != null) {
                                                                    if (chatFull.call == null) {
                                                                        if (voipLastFragment.getParentActivity() != null) {
                                                                            BulletinFactory.of(voipLastFragment).createSimpleBulletin(R.raw.linkbroken, LocaleController.getString(R.string.InviteExpired)).show();
                                                                        }
                                                                    } else {
                                                                        accountInstance.getMessagesController().getGroupCall(-dialog_id, true, () -> AndroidUtilities.runOnUIThread(() -> {
                                                                            ChatObject.Call call = accountInstance.getMessagesController().getGroupCall(-dialog_id, false);
                                                                            VoIPHelper.startCall(accountInstance.getMessagesController().getChat(-dialog_id), accountInstance.getMessagesController().getInputPeer(dialog_id), null, false, call == null || !call.call.rtmp_stream, LaunchActivity.this, voipLastFragment, accountInstance);
                                                                        }));
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }, 150);
                                                }
                                            }

                                            @Override
                                            public void onError() {
                                                if (!LaunchActivity.this.isFinishing()) {
                                                    BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                                                    AlertsCreator.showSimpleAlert(fragment, LocaleController.getString(R.string.JoinToGroupErrorNotExist));
                                                }
                                                try {
                                                    dismissLoading.run();
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                            }
                                        });
                                    }
                                    hideProgressDialog = false;
                                }
                            }
                        }
                    } else {
                        try {
                            BaseFragment lastFragment = LaunchActivity.getLastFragment();
                            if (lastFragment != null) {
                                if (lastFragment instanceof ChatActivity) {
                                    ((ChatActivity) lastFragment).shakeContent();
                                }
                                if (AndroidUtilities.isNumeric(username)) {
                                    BulletinFactory.of(lastFragment).createErrorBulletin(LocaleController.getString(R.string.NoPhoneFound)).show();
                                } else {
                                    BulletinFactory.of(lastFragment).createErrorBulletin(LocaleController.getString(R.string.NoUsernameFound)).show();
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (hideProgressDialog) {
                        try {
                            dismissLoading.run();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            });
        } else if (group != null) {
            if (state == 0) {
                final TLRPC.TL_messages_checkChatInvite req = new TLRPC.TL_messages_checkChatInvite();
                req.hash = group;
                requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!LaunchActivity.this.isFinishing()) {
                        boolean hideProgressDialog = true;
                        if (error == null && actionBarLayout != null) {
                            TLRPC.ChatInvite invite = (TLRPC.ChatInvite) response;
                            if (invite.chat != null && (!ChatObject.isLeftFromChat(invite.chat) || !invite.chat.kicked && (ChatObject.isPublic(invite.chat) || invite instanceof TLRPC.TL_chatInvitePeek || invite.chat.has_geo))) {
                                MessagesController.getInstance(intentAccount).putChat(invite.chat, false);
                                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                                chats.add(invite.chat);
                                MessagesStorage.getInstance(intentAccount).putUsersAndChats(null, chats, false, true);
                                Bundle args = new Bundle();
                                args.putLong("chat_id", invite.chat.id);
                                if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                    boolean[] canceled = new boolean[1];
                                    progressDialog.setOnCancelListener(dialog -> canceled[0] = true);
                                    if (invite.chat.forum) {
                                        Bundle bundle = new Bundle();
                                        bundle.putLong("chat_id", invite.chat.id);
                                        presentFragment(TopicsFragment.getTopicsOrChat(this, bundle));
                                    } else {
                                        MessagesController.getInstance(intentAccount).ensureMessagesLoaded(-invite.chat.id, 0, new MessagesController.MessagesLoadedCallback() {
                                            @Override
                                            public void onMessagesLoaded(boolean fromCache) {
                                                try {
                                                    dismissLoading.run();
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                                if (canceled[0]) {
                                                    return;
                                                }
                                                ChatActivity fragment = new ChatActivity(args);
                                                if (invite instanceof TLRPC.TL_chatInvitePeek) {
                                                    fragment.setChatInvite(invite);
                                                }
                                                getActionBarLayout().presentFragment(fragment);
                                            }

                                            @Override
                                            public void onError() {
                                                if (!LaunchActivity.this.isFinishing()) {
                                                    BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                                                    AlertsCreator.showSimpleAlert(fragment, LocaleController.getString(R.string.JoinToGroupErrorNotExist));
                                                }
                                                try {
                                                    dismissLoading.run();
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                            }
                                        });
                                        hideProgressDialog = false;
                                    }

                                }
                            } else if (invite.subscription_pricing != null && !invite.can_refulfill_subscription) {
                                final long stars = invite.subscription_pricing.amount;
                                MessagesController.getInstance(intentAccount).putChat(invite.chat, false);
                                StarsController.getInstance(currentAccount).subscribeTo(group, invite, (status, dialogId) -> {
                                    if ("paid".equals(status) && dialogId != 0) {
                                        AndroidUtilities.runOnUIThread(() -> {
                                            BaseFragment lastFragment = getSafeLastFragment();
                                            if (lastFragment == null) return;
                                            BaseFragment chatActivity = ChatActivity.of(dialogId);
                                            lastFragment.presentFragment(chatActivity);

                                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                                            if (chat != null) {
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.stars_send, LocaleController.getString(R.string.StarsSubscriptionCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsSubscriptionCompletedText", (int) stars, chat.title))).show(true);
                                                }, 250);
                                            }
                                        });
                                    }
                                });
                            } else {
                                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                                fragment.showDialog(new JoinGroupAlert(LaunchActivity.this, invite, group, fragment, (fragment instanceof ChatActivity ? ((ChatActivity) fragment).themeDelegate : null)));
                            }
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                            builder.setTitle(LocaleController.getString(R.string.AppName));
                            if (error.text.startsWith("FLOOD_WAIT")) {
                                builder.setMessage(LocaleController.getString(R.string.FloodWait));
                            } else if (error.text.startsWith("INVITE_HASH_EXPIRED")) {
                                builder.setTitle(LocaleController.getString(R.string.ExpiredLink));
                                builder.setMessage(LocaleController.getString(R.string.InviteExpired));
                            } else {
                                builder.setMessage(LocaleController.getString(R.string.JoinToGroupErrorNotExist));
                            }
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            showAlertDialog(builder);
                        }

                        try {
                            if (hideProgressDialog) {
                                dismissLoading.run();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            } else if (state == 1) {
                TLRPC.TL_messages_importChatInvite req = new TLRPC.TL_messages_importChatInvite();
                req.hash = group;
                ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.Updates updates = (TLRPC.Updates) response;
                        MessagesController.getInstance(intentAccount).processUpdates(updates, false);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!LaunchActivity.this.isFinishing()) {
                            try {
                                dismissLoading.run();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                if (actionBarLayout != null) {
                                    TLRPC.Updates updates = (TLRPC.Updates) response;
                                    if (!updates.chats.isEmpty()) {
                                        TLRPC.Chat chat = updates.chats.get(0);
                                        chat.left = false;
                                        chat.kicked = false;
                                        MessagesController.getInstance(intentAccount).putUsers(updates.users, false);
                                        MessagesController.getInstance(intentAccount).putChats(updates.chats, false);
                                        Bundle args = new Bundle();
                                        args.putLong("chat_id", chat.id);
                                        if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                            ChatActivity fragment = new ChatActivity(args);
                                            NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                            getActionBarLayout().presentFragment(fragment, false, true, true, false);
                                        }
                                    }
                                }
                            } else {
                                AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                                builder.setTitle(LocaleController.getString(R.string.AppName));
                                if (error.text.startsWith("FLOOD_WAIT")) {
                                    builder.setMessage(LocaleController.getString(R.string.FloodWait));
                                } else if (error.text.equals("USERS_TOO_MUCH")) {
                                    builder.setMessage(LocaleController.getString(R.string.JoinToGroupErrorFull));
                                } else {
                                    builder.setMessage(LocaleController.getString(R.string.JoinToGroupErrorNotExist));
                                }
                                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                                showAlertDialog(builder);
                            }
                        }
                    });
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        } else if (sticker != null) {
            if (!mainFragmentsStack.isEmpty()) {
                TLRPC.TL_inputStickerSetShortName stickerset = new TLRPC.TL_inputStickerSetShortName();
                stickerset.short_name = sticker != null ? sticker : emoji;
                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                StickersAlert alert;
                if (fragment instanceof ChatActivity) {
                    ChatActivity chatActivity = (ChatActivity) fragment;
                    alert = new StickersAlert(LaunchActivity.this, fragment, stickerset, null, chatActivity.getChatActivityEnterViewForStickers(), chatActivity.getResourceProvider(), false);
                    alert.setCalcMandatoryInsets(chatActivity.isKeyboardVisible());
                } else {
                    alert = new StickersAlert(LaunchActivity.this, fragment, stickerset, null, null, false);
                }
                alert.probablyEmojis = emoji != null;
                fragment.showDialog(alert);
            }
            return;
        } else if (emoji != null) {
            if (!mainFragmentsStack.isEmpty()) {
                TLRPC.TL_inputStickerSetShortName stickerset = new TLRPC.TL_inputStickerSetShortName();
                stickerset.short_name = sticker != null ? sticker : emoji;
                ArrayList<TLRPC.InputStickerSet> sets = new ArrayList<>(1);
                sets.add(stickerset);
                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                EmojiPacksAlert alert;
                if (fragment instanceof ChatActivity) {
                    ChatActivity chatActivity = (ChatActivity) fragment;
                    alert = new EmojiPacksAlert(fragment, LaunchActivity.this, chatActivity.getResourceProvider(), sets);
                    alert.setCalcMandatoryInsets(chatActivity.isKeyboardVisible());
                } else {
                    alert = new EmojiPacksAlert(fragment, LaunchActivity.this, null, sets);
                }
                fragment.showDialog(alert);
            }
            return;
        } else if (message != null) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment13, dids, m, param, notify, scheduleDate, topicsFragment) -> {
                long did = dids.get(0).dialogId;
                Bundle args13 = new Bundle();
                args13.putBoolean("scrollToTopOnResume", true);
                args13.putBoolean("hasUrl", hasUrl);
                if (DialogObject.isEncryptedDialog(did)) {
                    args13.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                } else if (DialogObject.isUserDialog(did)) {
                    args13.putLong("user_id", did);
                } else {
                    args13.putLong("chat_id", -did);
                }
                if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args13, fragment13)) {
                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                    MediaDataController.getInstance(intentAccount).saveDraft(did, 0, message, null, null, false, 0);
                    getActionBarLayout().presentFragment(new ChatActivity(args13), true, false, true, false);
                }
                return true;
            });
            presentFragment(fragment, false, true);
        } else if (auth != null) {
            final long bot_id = Utilities.parseLong(auth.get("bot_id"));
            if (bot_id == 0) {
                return;
            }
            final String payload = auth.get("payload");
            final String nonce = auth.get("nonce");
            final String callbackUrl = auth.get("callback_url");
            final TLRPC.TL_account_getAuthorizationForm req = new TLRPC.TL_account_getAuthorizationForm();
            req.bot_id = bot_id;
            req.scope = auth.get("scope");
            req.public_key = auth.get("public_key");
            requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> {
                final TLRPC.TL_account_authorizationForm authorizationForm = (TLRPC.TL_account_authorizationForm) response;
                if (authorizationForm != null) {
                    TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                    requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            dismissLoading.run();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (response1 != null) {
                            TLRPC.account_Password accountPassword = (TLRPC.account_Password) response1;
                            MessagesController.getInstance(intentAccount).putUsers(authorizationForm.users, false);
                            presentFragment(new PassportActivity(PassportActivity.TYPE_PASSWORD, req.bot_id, req.scope, req.public_key, payload, nonce, callbackUrl, authorizationForm, accountPassword));
                        }
                    }));
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            dismissLoading.run();
                            if ("APP_VERSION_OUTDATED".equals(error.text)) {
                                AlertsCreator.showUpdateAppAlert(LaunchActivity.this, LocaleController.getString(R.string.UpdateAppAlert), true);
                            } else {
                                showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text));
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }
            });
        } else if (unsupportedUrl != null) {
            TLRPC.TL_help_getDeepLinkInfo req = new TLRPC.TL_help_getDeepLinkInfo();
            req.path = unsupportedUrl;
            requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    dismissLoading.run();
                } catch (Exception e) {
                    FileLog.e(e);
                }

                if (response instanceof TLRPC.TL_help_deepLinkInfo) {
                    TLRPC.TL_help_deepLinkInfo res = (TLRPC.TL_help_deepLinkInfo) response;
                    AlertsCreator.showUpdateAppAlert(LaunchActivity.this, res.message, res.update_app);
                }
            }));
        } else if (lang != null) {
            TLRPC.TL_langpack_getLanguage req = new TLRPC.TL_langpack_getLanguage();
            req.lang_code = lang;
            req.lang_pack = "android";
            requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    dismissLoading.run();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response instanceof TLRPC.TL_langPackLanguage) {
                    TLRPC.TL_langPackLanguage res = (TLRPC.TL_langPackLanguage) response;
                    showAlertDialog(AlertsCreator.createLanguageAlert(LaunchActivity.this, res));
                } else if (error != null) {
                    if ("LANG_CODE_NOT_SUPPORTED".equals(error.text)) {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.LanguageUnsupportedError)));
                    } else {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text));
                    }
                }
            }));
        } else if (wallPaper != null) {
            boolean ok = false;
            if (TextUtils.isEmpty(wallPaper.slug)) {
                try {
                    WallpapersListActivity.ColorWallpaper colorWallpaper;
                    if (wallPaper.settings.third_background_color != 0) {
                        colorWallpaper = new WallpapersListActivity.ColorWallpaper(Theme.COLOR_BACKGROUND_SLUG, wallPaper.settings.background_color, wallPaper.settings.second_background_color, wallPaper.settings.third_background_color, wallPaper.settings.fourth_background_color);
                    } else {
                        colorWallpaper = new WallpapersListActivity.ColorWallpaper(Theme.COLOR_BACKGROUND_SLUG, wallPaper.settings.background_color, wallPaper.settings.second_background_color, AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false));
                    }
                    ThemePreviewActivity wallpaperActivity = new ThemePreviewActivity(colorWallpaper, null, true, false);
                    AndroidUtilities.runOnUIThread(() -> presentFragment(wallpaperActivity));
                    ok = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (!ok) {
                TLRPC.TL_account_getWallPaper req = new TLRPC.TL_account_getWallPaper();
                TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                inputWallPaperSlug.slug = wallPaper.slug;
                req.wallpaper = inputWallPaperSlug;
                requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    try {
                        dismissLoading.run();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (response instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_wallPaper res = (TLRPC.TL_wallPaper) response;
                        Object object;
                        if (res.pattern) {
                            WallpapersListActivity.ColorWallpaper colorWallpaper = new WallpapersListActivity.ColorWallpaper(res.slug, wallPaper.settings.background_color, wallPaper.settings.second_background_color, wallPaper.settings.third_background_color, wallPaper.settings.fourth_background_color, AndroidUtilities.getWallpaperRotation(wallPaper.settings.rotation, false), wallPaper.settings.intensity / 100.0f, wallPaper.settings.motion, null);
                            colorWallpaper.pattern = res;
                            object = colorWallpaper;
                        } else {
                            object = res;
                        }
                        ThemePreviewActivity wallpaperActivity = new ThemePreviewActivity(object, null, true, false);
                        wallpaperActivity.setInitialModes(wallPaper.settings.blur, wallPaper.settings.motion, wallPaper.settings.intensity);
                        presentFragment(wallpaperActivity);
                    } else {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text));
                    }
                }));
            }
        } else if (theme != null) {
            cancelRunnable = () -> {
                loadingThemeFileName = null;
                loadingThemeWallpaperName = null;
                loadingThemeWallpaper = null;
                loadingThemeInfo = null;
                loadingThemeProgressDialog = null;
                loadingTheme = null;

                if (progress != null) {
                    progress.end();
                }
            };
            TLRPC.TL_account_getTheme req = new TLRPC.TL_account_getTheme();
            req.format = "android";
            TLRPC.TL_inputThemeSlug inputThemeSlug = new TLRPC.TL_inputThemeSlug();
            inputThemeSlug.slug = theme;
            req.theme = inputThemeSlug;
            requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                int notFound = 2;
                if (response instanceof TLRPC.TL_theme) {
                    TLRPC.TL_theme t = (TLRPC.TL_theme) response;
                    TLRPC.ThemeSettings settings = null;
                    if (t.settings.size() > 0) {
                        settings = t.settings.get(0);
                    }
                    if (settings != null) {
                        String key = Theme.getBaseThemeKey(settings);
                        Theme.ThemeInfo info = Theme.getTheme(key);
                        if (info != null) {
                            TLRPC.TL_wallPaper object;
                            if (settings.wallpaper instanceof TLRPC.TL_wallPaper) {
                                object = (TLRPC.TL_wallPaper) settings.wallpaper;
                                File path = FileLoader.getInstance(currentAccount).getPathToAttach(object.document, true);
                                if (!path.exists()) {
                                    loadingThemeProgressDialog = progressDialog;
                                    loadingThemeAccent = true;
                                    loadingThemeInfo = info;
                                    loadingTheme = t;
                                    loadingThemeWallpaper = object;
                                    loadingThemeWallpaperName = FileLoader.getAttachFileName(object.document);
                                    FileLoader.getInstance(currentAccount).loadFile(object.document, object, FileLoader.PRIORITY_NORMAL, 1);
                                    return;
                                }
                            } else {
                                object = null;
                            }
                            try {
                                dismissLoading.run();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            notFound = 0;
                            openThemeAccentPreview(t, object, info);
                        } else {
                            notFound = 1;
                        }
                    } else if (t.document != null) {
                        loadingThemeAccent = false;
                        loadingTheme = t;
                        loadingThemeFileName = FileLoader.getAttachFileName(loadingTheme.document);
                        loadingThemeProgressDialog = progressDialog;
                        FileLoader.getInstance(currentAccount).loadFile(loadingTheme.document, t, FileLoader.PRIORITY_NORMAL, 1);
                        notFound = 0;
                    } else {
                        notFound = 1;
                    }
                } else if (error != null && "THEME_FORMAT_INVALID".equals(error.text)) {
                    notFound = 1;
                }
                if (notFound != 0) {
                    try {
                        dismissLoading.run();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (notFound == 1) {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.Theme), LocaleController.getString(R.string.ThemeNotSupported)));
                    } else {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.Theme), LocaleController.getString(R.string.ThemeNotFound)));
                    }
                }
            }));
        } else if (channelId != null && (messageId != null || isBoost)) {
            if (threadId != null) {
                TLRPC.Chat chat = MessagesController.getInstance(intentAccount).getChat(channelId);
                if (chat != null) {
                    requestId[0] = runCommentRequest(intentAccount, dismissLoading, messageId, commentId, threadId, chat);
                } else {
                    TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
                    TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
                    inputChannel.channel_id = channelId;
                    req.id.add(inputChannel);
                    requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        boolean notFound = true;
                        if (response instanceof TLRPC.TL_messages_chats) {
                            TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                            if (!res.chats.isEmpty()) {
                                notFound = false;
                                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                                requestId[0] = runCommentRequest(intentAccount, dismissLoading, messageId, commentId, threadId, res.chats.get(0));
                            }
                        }
                        if (notFound) {
                            try {
                                dismissLoading.run();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            showAlertDialog(AlertsCreator.createNoAccessAlert(LaunchActivity.this, LocaleController.getString(R.string.DialogNotAvailable), LocaleController.getString(R.string.LinkNotFound), null));
                        }
                    }));
                }
            } else {
                Bundle args = new Bundle();
                args.putLong("chat_id", channelId);
                if (messageId != null) {
                    args.putInt("message_id", messageId);
                }
                TLRPC.Chat chatLocal = MessagesController.getInstance(currentAccount).getChat(channelId);
                if (chatLocal != null && ChatObject.isBoostSupported(chatLocal) && isBoost) {
                    processBoostDialog(-channelId, dismissLoading, progress);
                } else if (chatLocal != null && chatLocal.forum) {
                    openForumFromLink(-channelId, messageId,  () -> {
                        try {
                            dismissLoading.run();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                } else {
                    BaseFragment lastFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
                    if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (!getActionBarLayout().presentFragment(new ChatActivity(args))) {
                                TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
                                TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
                                inputChannel.channel_id = channelId;
                                req.id.add(inputChannel);
                                requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                    try {
                                        dismissLoading.run();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    boolean notFound = true;
                                    if (response instanceof TLRPC.TL_messages_chats) {
                                        TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                                        if (!res.chats.isEmpty()) {
                                            notFound = false;
                                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                                            TLRPC.Chat chat = res.chats.get(0);
                                            if (chat != null && isBoost && ChatObject.isBoostSupported(chat)) {
                                                processBoostDialog(-channelId, null, progress);
                                            } else if (chat != null && chat.forum) {
                                                if (threadId != null) {
                                                    openForumFromLink(-channelId, messageId, null);
                                                } else {
                                                    openForumFromLink(-channelId, null, null);
                                                }
                                            }
                                            if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
                                                getActionBarLayout().presentFragment(new ChatActivity(args));
                                            }
                                        }
                                    }
                                    if (notFound) {
                                        showAlertDialog(AlertsCreator.createNoAccessAlert(LaunchActivity.this, LocaleController.getString(R.string.DialogNotAvailable), LocaleController.getString(R.string.LinkNotFound), null));
                                    }
                                }));
                            }
                        });
                    }
                }
            }
        } else if (chatLinkSlug != null) {
            TLRPC.TL_account_resolveBusinessChatLink req = new TLRPC.TL_account_resolveBusinessChatLink();
            req.slug = chatLinkSlug;

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_account_resolvedBusinessChatLinks) {
                    TLRPC.TL_account_resolvedBusinessChatLinks resolvedLink = (TLRPC.TL_account_resolvedBusinessChatLinks) res;

                    MessagesController.getInstance(currentAccount).putUsers(resolvedLink.users, false);
                    MessagesController.getInstance(currentAccount).putChats(resolvedLink.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(resolvedLink.users, resolvedLink.chats, true, true);

                    Bundle args = new Bundle();
                    if (resolvedLink.peer instanceof TLRPC.TL_peerUser) {
                        args.putLong("user_id", resolvedLink.peer.user_id);
                    } else if (resolvedLink.peer instanceof TLRPC.TL_peerChat || resolvedLink.peer instanceof TLRPC.TL_peerChannel) {
                        args.putLong("chat_id", resolvedLink.peer.channel_id);
                    }
                    ChatActivity chatActivity = new ChatActivity(args);
                    chatActivity.setResolvedChatLink(resolvedLink);
                    presentFragment(chatActivity, false, true);
                } else {
                    showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString(R.string.BusinessLink), LocaleController.getString(R.string.BusinessLinkInvalid)));
                }
            }));
        }

        if (requestId[0] != 0) {
            final Runnable cancelRunnableFinal = cancelRunnable;
            progressDialog.setOnCancelListener(dialog -> {
                ConnectionsManager.getInstance(intentAccount).cancelRequest(requestId[0], true);
                if (cancelRunnableFinal != null) {
                    cancelRunnableFinal.run();
                }
            });
            if (progress != null) {
                progress.onCancel(() -> {
                    ConnectionsManager.getInstance(intentAccount).cancelRequest(requestId[0], true);
                    if (cancelRunnableFinal != null) {
                        cancelRunnableFinal.run();
                    }
                });
            }
            try {
                if (progress != null) {
                    progress.init();
                } else {
                    progressDialog.showDelayed(300);
                }
            } catch (Exception ignore) {}
        }
    }

    private void processWebAppBot(final int intentAccount,
                                  final String username,
                                  final String group,
                                  final String sticker,
                                  final String emoji,
                                  final String botUser,
                                  final String botChat,
                                  final String botChannel,
                                  final String botChatAdminParams,
                                  final String message,
                                  final String contactToken,
                                  final String folderSlug,
                                  final String text,
                                  final boolean hasUrl,
                                  final Integer messageId,
                                  final Long channelId,
                                  final Long threadId,
                                  final Integer commentId,
                                  final String game,
                                  final HashMap<String, String> auth,
                                  final String lang,
                                  final String unsupportedUrl,
                                  final String code,
                                  final String loginToken,
                                  final TLRPC.TL_wallPaper wallPaper,
                                  final String inputInvoiceSlug,
                                  final String theme,
                                  final String voicechat,
                                  final boolean videochat,
                                  final String livestream,
                                  final int state,
                                  final int videoTimestamp,
                                  final String setAsAttachBot,
                                  final String attachMenuBotToOpen,
                                  final String attachMenuBotChoose,
                                  final String botAppMaybe,
                                  final String botAppStartParam,
                                  final Browser.Progress progress,
                                  final boolean forceNotInternalForApps,
                                  final int storyId,
                                  final boolean isBoost,
                                  final String chatLinkSlug,
                                  TLRPC.User user,
                                  Runnable dismissLoading, boolean botAttachable, boolean ignoreInactive, boolean botCompact, boolean botFullscreen, boolean openedTelegram, boolean openProfile, boolean forceRequest, String referrer) {

        TLRPC.TL_messages_getBotApp getBotApp = new TLRPC.TL_messages_getBotApp();
        TLRPC.TL_inputBotAppShortName app = new TLRPC.TL_inputBotAppShortName();
        app.bot_id = MessagesController.getInstance(intentAccount).getInputUser(user);
        app.short_name = botAppMaybe;
        getBotApp.app = app;
        ConnectionsManager.getInstance(intentAccount).sendRequest(getBotApp, (response1, error1) -> {
            if (progress != null) {
                progress.end();
            }
            if (error1 != null) {
                AndroidUtilities.runOnUIThread(() -> runLinkRequest(intentAccount, username, group, sticker, emoji, botUser, botChat, botChannel, botChatAdminParams, message, contactToken, folderSlug, text, hasUrl, messageId, channelId, threadId, commentId, game, auth, lang, unsupportedUrl, code, loginToken, wallPaper, inputInvoiceSlug, theme, voicechat, videochat, livestream, state, videoTimestamp, setAsAttachBot, attachMenuBotToOpen, attachMenuBotChoose, null, null, progress, forceNotInternalForApps, storyId, isBoost, chatLinkSlug, botCompact, botFullscreen, openedTelegram, openProfile, forceRequest, referrer));
            } else {
                TLRPC.TL_messages_botApp botApp = (TLRPC.TL_messages_botApp) response1;
                AndroidUtilities.runOnUIThread(() -> {
                    dismissLoading.run();

                    AtomicBoolean allowWrite = new AtomicBoolean();
                    BaseFragment lastFragment = mainFragmentsStack == null || mainFragmentsStack.isEmpty() ? null : mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                    Runnable loadBotSheet = () -> {
                        if (lastFragment == null || !isActive || isFinishing() || isDestroyed()) return;
                        WebViewRequestProps props = WebViewRequestProps.of(intentAccount, user.id, user.id, null, null, BotWebViewAttachedSheet.TYPE_WEB_VIEW_BOT_APP, 0, false, botApp.app, allowWrite.get(), botAppStartParam, user, 0, botCompact, botFullscreen);
                        if (getBottomSheetTabs() != null && getBottomSheetTabs().tryReopenTab(props) != null) {
                            return;
                        }
                        SharedPrefsHelper.setWebViewConfirmShown(currentAccount, user.id, true);
//                        if (AndroidUtilities.isTablet() || true) {
                            BotWebViewSheet sheet = new BotWebViewSheet(LaunchActivity.this, lastFragment != null ? lastFragment.getResourceProvider() : null);
                            sheet.setWasOpenedByLinkIntent(openedTelegram);
                            sheet.setDefaultFullsize(!botCompact);
                            if (botFullscreen) {
                                sheet.setFullscreen(true, false);
                            }
                            sheet.setNeedsContext(false);
                            sheet.setParentActivity(LaunchActivity.this);
                            sheet.requestWebView(lastFragment, props);
                            sheet.show();
                            if (botApp.inactive || forceNotInternalForApps) {
                                sheet.showJustAddedBulletin();
                            }
//                        } else {
//                            BaseFragment fragment = lastFragment;
//                            if (fragment.getParentLayout() instanceof ActionBarLayout) {
//                                fragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment();
//                            }
//                            BotWebViewAttachedSheet sheet = fragment.createBotViewer();
//                            sheet.setWasOpenedByLinkIntent(openedTelegram);
//                            sheet.setDefaultFullsize(!botCompact);
//                            sheet.setNeedsContext(false);
//                            sheet.setParentActivity(LaunchActivity.this);
//                            sheet.requestWebView(fragment, props);
//                            sheet.show();
//                            if (botApp.inactive || forceNotInternalForApps) {
//                                sheet.showJustAddedBulletin();
//                            }
//                        }
                    };

                    if (ignoreInactive) {
                        loadBotSheet.run();
                    } else if (botApp.inactive && botAttachable) {
                        WebAppDisclaimerAlert.show(this, (allowSendMessage) -> {
                            loadBotSheet.run();
                        }, null, progress != null ? progress::end : null);
                    } else if (botApp.request_write_access || forceNotInternalForApps) {
                        AlertsCreator.createBotLaunchAlert(lastFragment, allowWrite, user, loadBotSheet);
                    } else {
                        loadBotSheet.run();
                    }
                });
            }
        });

    }

    private void processAttachedMenuBotFromShortcut(long botId) {
        for (int i = 0; i < visibleDialogs.size(); i++) {
            if (visibleDialogs.get(i) instanceof BotWebViewSheet) {
                BotWebViewSheet addedDialog = (BotWebViewSheet) visibleDialogs.get(i);
                if (addedDialog.isShowing() && addedDialog.getBotId() == botId) {
                    return;
                }
            }
        }
        BaseFragment fragment = getSafeLastFragment();
        if (fragment != null && fragment.sheetsStack != null) {
            for (int i = 0; i < fragment.sheetsStack.size(); ++i) {
                if (fragment.sheetsStack.get(i).isShown() && fragment.sheetsStack.get(i) instanceof BotWebViewAttachedSheet && ((BotWebViewAttachedSheet) fragment.sheetsStack.get(i)).getBotId() == botId) {
                    return;
                }
            }
        }
        fragment = actionBarLayout.getSheetFragment(false);
        if (fragment != null && fragment.sheetsStack != null) {
            for (int i = 0; i < fragment.sheetsStack.size(); ++i) {
                if (fragment.sheetsStack.get(i).isShown() && fragment.sheetsStack.get(i) instanceof BotWebViewAttachedSheet && ((BotWebViewAttachedSheet) fragment.sheetsStack.get(i)).getBotId() == botId) {
                    return;
                }
            }
        }
        Utilities.Callback<TLRPC.User> open = user -> {
            MessagesController.getInstance(currentAccount).openApp(user, 0);
        };
        TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
        if (bot != null) {
            open.run(bot);
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            TLRPC.User user = MessagesStorage.getInstance(currentAccount).getUser(botId);
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getInstance(currentAccount).putUser(user, true);
                open.run(user);
            });
        });
    }

    private void processBoostDialog(Long peerId, Runnable dismissLoading, Browser.Progress progress) {
        processBoostDialog(peerId, dismissLoading, progress, null);
    }

    private void processBoostDialog(Long peerId, Runnable dismissLoading, Browser.Progress progress, ChatMessageCell chatMessageCell) {
        ChannelBoostsController boostsController = MessagesController.getInstance(currentAccount).getBoostsController();
        if (progress != null) {
            progress.init();
        }
        boostsController.getBoostsStats(peerId, boostsStatus -> {
            if (boostsStatus == null) {
                if (progress != null) {
                    progress.end();
                }
                if (dismissLoading != null) {
                    dismissLoading.run();
                }
                return;
            }
            boostsController.userCanBoostChannel(peerId, boostsStatus, canApplyBoost -> {
                if (progress != null) {
                    progress.end();
                }
                BaseFragment lastFragment = getLastFragment();
                if (lastFragment == null) {
                    return;
                }
                Theme.ResourcesProvider resourcesProvider = lastFragment.getResourceProvider();
                if (lastFragment.getLastStoryViewer() != null && lastFragment.getLastStoryViewer().isFullyVisible()) {
                    resourcesProvider = lastFragment.getLastStoryViewer().getResourceProvider();
                }
                LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(lastFragment, this, TYPE_BOOSTS_FOR_USERS, currentAccount, resourcesProvider);
                limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);

                boolean isCurrentChat = false;
                if (lastFragment instanceof ChatActivity) {
                    isCurrentChat = ((ChatActivity) lastFragment).getDialogId() == peerId;
                } else if (lastFragment instanceof DialogsActivity) {
                    DialogsActivity dialogsActivity = ((DialogsActivity) lastFragment);
                    isCurrentChat = dialogsActivity.rightSlidingDialogContainer != null && dialogsActivity.rightSlidingDialogContainer.getCurrentFragmetDialogId() == peerId;
                }
                limitReachedBottomSheet.setBoostsStats(boostsStatus, isCurrentChat);
                limitReachedBottomSheet.setDialogId(peerId);
                limitReachedBottomSheet.setChatMessageCell(chatMessageCell);

                lastFragment.showDialog(limitReachedBottomSheet);
                try {
                    if (dismissLoading != null) {
                        dismissLoading.run();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        });
    }

    private void processAttachMenuBot(int intentAccount, long peerId, String attachMenuBotChoose, TLRPC.User user,  String setAsAttachBot, String startAppParam) {
        TLRPC.TL_messages_getAttachMenuBot getAttachMenuBot = new TLRPC.TL_messages_getAttachMenuBot();
        getAttachMenuBot.bot = MessagesController.getInstance(intentAccount).getInputUser(peerId);
        ConnectionsManager.getInstance(intentAccount).sendRequest(getAttachMenuBot, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
            if (response1 instanceof TLRPC.TL_attachMenuBotsBot) {
                TLRPC.TL_attachMenuBotsBot attachMenuBotsBot = (TLRPC.TL_attachMenuBotsBot) response1;
                MessagesController.getInstance(intentAccount).putUsers(attachMenuBotsBot.users, false);
                TLRPC.TL_attachMenuBot attachMenuBot = attachMenuBotsBot.bot;
                if (startAppParam != null) {
                    showAttachMenuBot(attachMenuBot, startAppParam, false);
                    return;
                }
                BaseFragment lastFragment_ = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (AndroidUtilities.isTablet() && !(lastFragment_ instanceof ChatActivity) && !rightFragmentsStack.isEmpty()) {
                    lastFragment_ = rightFragmentsStack.get(rightFragmentsStack.size() - 1);
                }
                final BaseFragment lastFragment = lastFragment_;

                List<String> chooserTargets = new ArrayList<>();
                if (!TextUtils.isEmpty(attachMenuBotChoose)) {
                    for (String target : attachMenuBotChoose.split(" ")) {
                        if (MediaDataController.canShowAttachMenuBotForTarget(attachMenuBot, target)) {
                            chooserTargets.add(target);
                        }
                    }
                }
                DialogsActivity dialogsActivity;

                if (!chooserTargets.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT);
                    args.putBoolean("onlySelect", true);

                    args.putBoolean("allowGroups", chooserTargets.contains("groups"));
                    args.putBoolean("allowMegagroups", chooserTargets.contains("groups"));
                    args.putBoolean("allowLegacyGroups", chooserTargets.contains("groups"));
                    args.putBoolean("allowUsers", chooserTargets.contains("users"));
                    args.putBoolean("allowChannels", chooserTargets.contains("channels"));
                    args.putBoolean("allowBots", chooserTargets.contains("bots"));

                    dialogsActivity = new DialogsActivity(args);
                    dialogsActivity.setDelegate((fragment, dids, message1, param, notify, scheduleDate, topicsFragment) -> {
                        long did = dids.get(0).dialogId;

                        Bundle args1 = new Bundle();
                        args1.putBoolean("scrollToTopOnResume", true);
                        if (DialogObject.isEncryptedDialog(did)) {
                            args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                        } else if (DialogObject.isUserDialog(did)) {
                            args1.putLong("user_id", did);
                        } else {
                            args1.putLong("chat_id", -did);
                        }
                        args1.putString("attach_bot", UserObject.getPublicUsername(user));
                        if (setAsAttachBot != null) {
                            args1.putString("attach_bot_start_command", setAsAttachBot);
                        }
                        if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args1, fragment)) {
                            NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                            getActionBarLayout().presentFragment(new ChatActivity(args1), true, false, true, false);
                        }
                        return true;
                    });
                } else {
                    dialogsActivity = null;
                }

                if (!attachMenuBot.inactive) {
                    if (dialogsActivity != null) {
                        if (lastFragment != null) {
                            lastFragment.dismissCurrentDialog();
                        }
                        for (int i = 0; i < visibleDialogs.size(); ++i) {
                            Dialog dialog = visibleDialogs.get(i);
                            if (dialog.isShowing()) {
                                visibleDialogs.get(i).dismiss();
                            }
                        }
                        visibleDialogs.clear();
                        presentFragment(dialogsActivity);
                    } else if (lastFragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) lastFragment;
                        if (!MediaDataController.canShowAttachMenuBot(attachMenuBot, chatActivity.getCurrentUser() != null ? chatActivity.getCurrentUser() : chatActivity.getCurrentChat())) {
                            BulletinFactory.of(lastFragment).createErrorBulletin(LocaleController.getString(R.string.BotAlreadyAddedToAttachMenu)).show();
                            return;
                        }
                        chatActivity.openAttachBotLayout(user.id, setAsAttachBot, false);
                    } else {
                        BulletinFactory.of(lastFragment).createErrorBulletin(LocaleController.getString(R.string.BotAlreadyAddedToAttachMenu)).show();
                    }
                } else {
                    AttachBotIntroTopView introTopView = new AttachBotIntroTopView(LaunchActivity.this);
                    introTopView.setColor(Theme.getColor(Theme.key_chat_attachIcon));
                    introTopView.setBackgroundColor(Theme.getColor(Theme.key_dialogTopBackground));
                    introTopView.setAttachBot(attachMenuBot);

                    WebAppDisclaimerAlert.show(this, (allowSendMessage) -> {
                        TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                        botRequest.bot = MessagesController.getInstance(intentAccount).getInputUser(peerId);
                        botRequest.enabled = true;
                        botRequest.write_allowed = true;

                        ConnectionsManager.getInstance(intentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response2 instanceof TLRPC.TL_boolTrue) {
                                MediaDataController.getInstance(intentAccount).loadAttachMenuBots(false, true, () -> {
                                    if (dialogsActivity != null) {
                                        if (lastFragment != null) {
                                            lastFragment.dismissCurrentDialog();
                                        }
                                        for (int i = 0; i < visibleDialogs.size(); ++i) {
                                            Dialog dialog = visibleDialogs.get(i);
                                            if (dialog.isShowing()) {
                                                visibleDialogs.get(i).dismiss();
                                            }
                                        }
                                        visibleDialogs.clear();
                                        presentFragment(dialogsActivity);
                                    } else if (lastFragment instanceof ChatActivity) {
                                        ((ChatActivity) lastFragment).openAttachBotLayout(user.id, setAsAttachBot, true);
                                    }
                                });
                            }
                        }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                    }, attachMenuBot.request_write_access ? user : null, null);
                }
            } else {
                BulletinFactory.of(mainFragmentsStack.get(mainFragmentsStack.size() - 1)).createErrorBulletin(LocaleController.getString(R.string.BotCantAddToAttachMenu)).show();
            }
        }));
    }

    private void openForumFromLink(long dialogId, Integer messageId, Runnable onOpened) {
        openForumFromLink(dialogId, messageId, null, onOpened, 0, -1);
    }

    private void openForumFromLink(long dialogId, Integer messageId, String quote, Runnable onOpened, int fromMessageId, int quoteOffset) {
        if (messageId == null) {
            Bundle bundle = new Bundle();
            bundle.putLong("chat_id", -dialogId);
            presentFragment(TopicsFragment.getTopicsOrChat(this, bundle));

            if (onOpened != null) {
                onOpened.run();
            }
            return;
        }
        TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
        req.id.add(messageId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            AndroidUtilities.runOnUIThread(() -> {
                TLRPC.Message message = null;
                if (res instanceof TLRPC.messages_Messages) {
                    ArrayList<TLRPC.Message> messages = ((TLRPC.messages_Messages) res).messages;
                    for (int i = 0; i < messages.size(); ++i) {
                        if (messages.get(i) != null && messages.get(i).id == messageId) {
                            message = messages.get(i);
                            break;
                        }
                    }
                }

                if (message != null) {
                    runCommentRequest(currentAccount, null, message.id, null, MessageObject.getTopicId(currentAccount, message, MessagesController.getInstance(currentAccount).isForum(message)), MessagesController.getInstance(currentAccount).getChat(-dialogId), onOpened, quote, fromMessageId, quoteOffset);
                    return;
                }

                Bundle bundle = new Bundle();
                bundle.putLong("chat_id", -dialogId);
                presentFragment(TopicsFragment.getTopicsOrChat(this, bundle));

                if (onOpened != null) {
                    onOpened.run();
                }
            });
        });
    }

    private List<TLRPC.TL_contact> findContacts(String userName, String userPhone, boolean allowSelf) {
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final ContactsController contactsController = ContactsController.getInstance(currentAccount);
        final List<TLRPC.TL_contact> contacts = new ArrayList<>(contactsController.contacts);
        final List<TLRPC.TL_contact> foundContacts = new ArrayList<>();

        if (userPhone != null) {
            userPhone = PhoneFormat.stripExceptNumbers(userPhone);
            TLRPC.TL_contact contact = contactsController.contactsByPhone.get(userPhone);
            if (contact == null) {
                String shortUserPhone = userPhone.substring(Math.max(0, userPhone.length() - 7));
                contact = contactsController.contactsByShortPhone.get(shortUserPhone);
            }
            if (contact != null) {
                final TLRPC.User user = messagesController.getUser(contact.user_id);
                if (user != null && (!user.self || allowSelf)) {
                    foundContacts.add(contact);
                } else {
                    // disable search by name
                    userName = null;
                }
            }
        }

        if (foundContacts.isEmpty() && userName != null) {
            final String query1 = userName.trim().toLowerCase();
            if (!TextUtils.isEmpty(query1)) {
                String query2 = LocaleController.getInstance().getTranslitString(query1);
                if (query1.equals(query2) || query2.length() == 0) {
                    query2 = null;
                }
                final String[] queries = new String[]{query1, query2};
                for (int i = 0, size = contacts.size(); i < size; i++) {
                    final TLRPC.TL_contact contact = contacts.get(i);
                    if (contact != null) {
                        final TLRPC.User user = messagesController.getUser(contact.user_id);
                        if (user != null) {
                            if (user.self && !allowSelf) {
                                continue;
                            }

                            final String[] names = new String[3];
                            names[0] = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                            names[1] = LocaleController.getInstance().getTranslitString(names[0]);
                            if (names[0].equals(names[1])) {
                                names[1] = null;
                            }
                            if (UserObject.isReplyUser(user)) {
                                names[2] = LocaleController.getString(R.string.RepliesTitle).toLowerCase();
                            } else if (user.self) {
                                names[2] = LocaleController.getString(R.string.SavedMessages).toLowerCase();
                            }

                            boolean found = false;
                            for (String q : queries) {
                                if (q == null) {
                                    continue;
                                }
                                for (int j = 0; j < names.length; j++) {
                                    final String name = names[j];
                                    if (name != null && (name.startsWith(q) || name.contains(" " + q))) {
                                        found = true;
                                        break;
                                    }
                                }
                                String username = UserObject.getPublicUsername(user);
                                if (!found && username != null && username.startsWith(q)) {
                                    found = true;
                                }
                                if (found) {
                                    foundContacts.add(contact);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        return foundContacts;
    }

    public void checkAppUpdate(boolean force, Browser.Progress progress) {
        if (!force && BuildVars.DEBUG_VERSION || !force && !BuildVars.CHECK_UPDATES) {
            return;
        }
        if (!force && Math.abs(System.currentTimeMillis() - SharedConfig.lastUpdateCheckTime) < MessagesController.getInstance(0).updateCheckDelay * 1000) {
            return;
        }
        TLRPC.TL_help_getAppUpdate req = new TLRPC.TL_help_getAppUpdate();
        try {
            req.source = ApplicationLoader.applicationContext.getPackageManager().getInstallerPackageName(ApplicationLoader.applicationContext.getPackageName());
        } catch (Exception ignore) {

        }
        if (req.source == null) {
            req.source = "";
        }
        final int accountNum = currentAccount;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            SharedConfig.lastUpdateCheckTime = System.currentTimeMillis();
            SharedConfig.saveConfig();
            if (response instanceof TLRPC.TL_help_appUpdate) {
                final TLRPC.TL_help_appUpdate res = (TLRPC.TL_help_appUpdate) response;
                AndroidUtilities.runOnUIThread(() -> {
                    if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.version.equals(res.version)) {
                        return;
                    }
                    final boolean newVersionAvailable = SharedConfig.setNewAppVersionAvailable(res);
                    if (newVersionAvailable) {
                        if (res.can_not_skip) {
                            showUpdateActivity(accountNum, res, false);
                        } else if (ApplicationLoader.isStandaloneBuild() || BuildVars.DEBUG_VERSION) {
                            drawerLayoutAdapter.notifyDataSetChanged();
                            ApplicationLoader.applicationLoaderInstance.showUpdateAppPopup(LaunchActivity.this, res, accountNum);
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                    }
                    if (progress != null) {
                        progress.end();
                        if (!newVersionAvailable) {
                            BaseFragment fragment = getLastFragment();
                            if (fragment != null) {
                                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.YourVersionIsLatest)).show();
                            }
                        }
                    }
                });
            } else if (response instanceof TLRPC.TL_help_noAppUpdate) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (progress != null) {
                        progress.end();
                        BaseFragment fragment = getLastFragment();
                        if (fragment != null) {
                            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.YourVersionIsLatest)).show();
                        }
                    }
                });
            } else if (error != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (progress != null) {
                        progress.end();
                        BaseFragment fragment = getLastFragment();
                        if (fragment != null) {
                            BulletinFactory.of(fragment).showForError(error);
                        }
                    }
                });
            }
        });
        if (progress != null) {
            progress.init();
            progress.onCancel(() -> ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true));
        }
    }

    public Dialog showAlertDialog(AlertDialog.Builder builder) {
        try {
            AlertDialog dialog = builder.show();
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(d -> {
                if (dialog != null) {
                    if (dialog == localeDialog) {
                        BaseFragment fragment = actionBarLayout == null ? null : actionBarLayout.getLastFragment();
                        try {
                            String shorname = LocaleController.getInstance().getCurrentLocaleInfo().shortName;
                            if (fragment != null) {
                                BulletinFactory.of(fragment).createSimpleBulletin(
                                    R.raw.msg_translate,
                                    getStringForLanguageAlert(shorname.equals("en") ? englishLocaleStrings : systemLocaleStrings, "ChangeLanguageLater", R.string.ChangeLanguageLater)
                                ).setDuration(Bulletin.DURATION_PROLONG).show();
                            } else {
                                BulletinFactory.of(Bulletin.BulletinWindow.make(LaunchActivity.this), null).createSimpleBulletin(
                                    R.raw.msg_translate,
                                    getStringForLanguageAlert(shorname.equals("en") ? englishLocaleStrings : systemLocaleStrings, "ChangeLanguageLater", R.string.ChangeLanguageLater)
                                ).setDuration(Bulletin.DURATION_PROLONG).show();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        localeDialog = null;
                    } else if (dialog == proxyErrorDialog) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                        editor.putBoolean("proxy_enabled", false);
                        editor.putBoolean("proxy_enabled_calls", false);
                        editor.commit();
                        ConnectionsManager.setProxySettings(false, "", 1080, "", "", "");
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                        proxyErrorDialog = null;
                    }
                }
                visibleDialogs.remove(dialog);
            });
            visibleDialogs.add(dialog);
            return dialog;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public void showBulletin(Function<BulletinFactory, Bulletin> createBulletin) {
        BaseFragment topFragment = null;
        if (!layerFragmentsStack.isEmpty()) {
            topFragment = layerFragmentsStack.get(layerFragmentsStack.size() - 1);
        } else if (!rightFragmentsStack.isEmpty()) {
            topFragment = rightFragmentsStack.get(rightFragmentsStack.size() - 1);
        } else if (!mainFragmentsStack.isEmpty()) {
            topFragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
        }
        if (BulletinFactory.canShowBulletin(topFragment)) {
            createBulletin.apply(BulletinFactory.of(topFragment)).show();
        }
    }

    public void setNavigateToPremiumBot(boolean val) {
        navigateToPremiumBot = val;
    }

    public void setNavigateToPremiumGiftCallback(Runnable val) {
        navigateToPremiumGiftCallback = val;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false, false, null, true, true);
    }

    public void onNewIntent(Intent intent, Browser.Progress progress) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false, false, progress, true, false);
    }

    @Override
    public boolean didSelectDialogs(DialogsActivity dialogsFragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean _notify, int _scheduleDate, TopicsFragment topicsFragment) {
        final int account = dialogsFragment != null ? dialogsFragment.getCurrentAccount() : currentAccount;

        if (exportingChatUri != null) {
            Uri uri = exportingChatUri;
            ArrayList<Uri> documentsUris = documentsUrisArray != null ? new ArrayList<>(documentsUrisArray) : null;
            final AlertDialog progressDialog = new AlertDialog(this, AlertDialog.ALERT_TYPE_SPINNER);
            SendMessagesHelper.getInstance(account).prepareImportHistory(dids.get(0).dialogId, exportingChatUri, documentsUrisArray, (result) -> {
                if (result != 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("scrollToTopOnResume", true);
                    if (!AndroidUtilities.isTablet()) {
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.closeChats);
                    }
                    if (DialogObject.isUserDialog(result)) {
                        args.putLong("user_id", result);
                    } else {
                        args.putLong("chat_id", -result);
                    }
                    ChatActivity fragment = new ChatActivity(args);
                    fragment.setOpenImport();
                    getActionBarLayout().presentFragment(fragment, dialogsFragment != null || param, dialogsFragment == null, true, false);
                } else {
                    documentsUrisArray = documentsUris;
                    if (documentsUrisArray == null) {
                        documentsUrisArray = new ArrayList<>();
                    }
                    documentsUrisArray.add(0, uri);
                    openDialogsToSend(true);
                }
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            try {
                progressDialog.showDelayed(300);
            } catch (Exception ignore) {

            }
        } else {
            boolean notify = dialogsFragment == null || dialogsFragment.notify || _notify;
            int scheduleDate = _scheduleDate != 0 ? _scheduleDate : dialogsFragment == null ? 0 : dialogsFragment.scheduleDate;
            final ChatActivity fragment;
            if (dids.size() <= 1) {
                final long did = dids.get(0).dialogId;

                Bundle args = new Bundle();
                args.putBoolean("scrollToTopOnResume", true);
                if (!AndroidUtilities.isTablet()) {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.closeChats);
                }
                if (DialogObject.isEncryptedDialog(did)) {
                    args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                } else if (DialogObject.isUserDialog(did)) {
                    args.putLong("user_id", did);
                } else {
                    args.putLong("chat_id", -did);
                }
                if (!MessagesController.getInstance(account).checkCanOpenChat(args, dialogsFragment)) {
                    return false;
                }
                fragment = new ChatActivity(args);
                ForumUtilities.applyTopic(fragment, dids.get(0));
            } else {
                fragment = null;
            }

            int attachesCount = 0;
            if (contactsToSend != null) {
                attachesCount += contactsToSend.size();
            }
            if (videoPath != null) {
                attachesCount++;
            }
            if (voicePath != null) {
                attachesCount++;
            }
            if (photoPathsArray != null) {
                attachesCount += photoPathsArray.size();
            }
            if (documentsPathsArray != null) {
                attachesCount += documentsPathsArray.size();
            }
            if (documentsUrisArray != null) {
                attachesCount += documentsUrisArray.size();
            }
            if (videoPath == null && voicePath == null && photoPathsArray == null && documentsPathsArray == null && documentsUrisArray == null && sendingText != null) {
                attachesCount++;
            }

            for (int i = 0; i < dids.size(); i++) {
                final long did = dids.get(i).dialogId;
                if (AlertsCreator.checkSlowMode(this, currentAccount, did, attachesCount > 1)) {
                    return false;
                }
            }

            if (topicsFragment != null) {
                topicsFragment.removeSelfFromStack();
            }
            boolean presentedFragmentWithRemoveLast = false;
            if (contactsToSend != null && contactsToSend.size() == 1 && !mainFragmentsStack.isEmpty()) {
                presentedFragmentWithRemoveLast = true;
                PhonebookShareAlert alert = new PhonebookShareAlert(mainFragmentsStack.get(mainFragmentsStack.size() - 1), null, null, contactsToSendUri, null, null, null);
                alert.setDelegate((user, notify2, scheduleDate2, effectId, invertMedia) -> {
                    if (fragment != null) {
                        getActionBarLayout().presentFragment(fragment, true, false, true, false);
                    }
                    AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);
                    for (int i = 0; i < dids.size(); i++) {
                        long did = dids.get(i).dialogId;
                        long topicId = dids.get(i).topicId;
                        MessageObject replyToMsg = null;
                        if (topicId != 0) {
                            TLRPC.TL_forumTopic topic = accountInstance.getMessagesController().getTopicsController().findTopic(-did, topicId);
                            if (topic != null && topic.topicStartMessage != null) {
                                replyToMsg = new MessageObject(accountInstance.getCurrentAccount(), topic.topicStartMessage, false, false);
                                replyToMsg.isTopicMainMessage = true;
                            }
                        }

                        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(user, did, replyToMsg, replyToMsg, null, null, notify2, scheduleDate2 != 0 ? scheduleDate2 : scheduleDate);
                        if (TextUtils.isEmpty(message)) {
                            params.effect_id = effectId;
                        }
                        params.invert_media = invertMedia;
                        SendMessagesHelper.getInstance(account).sendMessage(params);
                        if (!TextUtils.isEmpty(message)) {
                            SendMessagesHelper.prepareSendingText(accountInstance, message.toString(), did, notify, scheduleDate2 != 0 ? scheduleDate2 : scheduleDate, effectId);
                        }
                    }
                });
                mainFragmentsStack.get(mainFragmentsStack.size() - 1).showDialog(alert);
            } else {
                String captionToSend = null;
                for (int i = 0; i < dids.size(); i++) {
                    final long did = dids.get(i).dialogId;
                    final long topicId = dids.get(i).topicId;

                    AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);
                    MessageObject replyToMsg = null;
                    if (topicId != 0) {
                        TLRPC.TL_forumTopic topic = accountInstance.getMessagesController().getTopicsController().findTopic(-did, topicId);
                        if (topic != null && topic.topicStartMessage != null) {
                            replyToMsg = new MessageObject(accountInstance.getCurrentAccount(), topic.topicStartMessage, false, false);
                            replyToMsg.isTopicMainMessage = true;
                        }
                    }
                    boolean photosEditorOpened = false, videoEditorOpened = false;
                    if (fragment != null) {
                        boolean withoutAnimation = dialogsFragment == null || videoPath != null || (photoPathsArray != null && photoPathsArray.size() > 0);
                        getActionBarLayout().presentFragment(fragment, dialogsFragment != null, withoutAnimation, true, false);
                        presentedFragmentWithRemoveLast = dialogsFragment != null;
                        if (videoPath != null && topicId == 0) {
                            fragment.openVideoEditor(videoPath, sendingText);
                            videoEditorOpened = true;
                            sendingText = null;
                        } else if (photoPathsArray != null && photoPathsArray.size() > 0 && topicId == 0) {
                            photosEditorOpened = fragment.openPhotosEditor(photoPathsArray, message == null || message.length() == 0 ? sendingText : message);
                            if (photosEditorOpened) {
                                sendingText = null;
                            }
                        } else if (videoPath != null) {
                            if (sendingText != null && sendingText.length() <= 1024) {
                                captionToSend = sendingText;
                                sendingText = null;
                            }
                            ArrayList<String> arrayList = new ArrayList<>();
                            arrayList.add(videoPath);
                            SendMessagesHelper.prepareSendingDocuments(accountInstance, arrayList, arrayList, null, captionToSend, null, did, replyToMsg, replyToMsg, null, null, null, notify, scheduleDate, null, null, 0, 0, false);
                        } else if (photoPathsArray != null && photoPathsArray.size() > 0 && !photosEditorOpened) {
                            if (sendingText != null && sendingText.length() <= 1024 && photoPathsArray.size() == 1) {
                                photoPathsArray.get(0).caption = sendingText;
                                sendingText = null;
                            }
                            SendMessagesHelper.prepareSendingMedia(accountInstance, photoPathsArray, did, replyToMsg, replyToMsg, null, null, false, false, null, notify, scheduleDate, 0, false, null, null, 0, 0, false);
                        }
                    } else {
                        if (videoPath != null) {
                            if (sendingText != null && sendingText.length() <= 1024) {
                                captionToSend = sendingText;
                                sendingText = null;
                            }
                            ArrayList<String> arrayList = new ArrayList<>();
                            arrayList.add(videoPath);
                            SendMessagesHelper.prepareSendingDocuments(accountInstance, arrayList, arrayList, null, captionToSend, null, did, replyToMsg, replyToMsg, null, null, null, notify, scheduleDate, null, null, 0, 0, false);
                        }
                        if (photoPathsArray != null && !photosEditorOpened) {
                            if (sendingText != null && sendingText.length() <= 1024 && photoPathsArray.size() == 1) {
                                photoPathsArray.get(0).caption = sendingText;
                                sendingText = null;
                            }
                            SendMessagesHelper.prepareSendingMedia(accountInstance, photoPathsArray, did, replyToMsg, replyToMsg, null, null, false, false, null, notify, scheduleDate, 0, false, null, null, 0, 0, false);
                        }
                    }
                    if (documentsPathsArray != null || documentsUrisArray != null) {
                        if (sendingText != null && sendingText.length() <= 1024 && ((documentsPathsArray != null ? documentsPathsArray.size() : 0) + (documentsUrisArray != null ? documentsUrisArray.size() : 0)) == 1) {
                            captionToSend = sendingText;
                            sendingText = null;
                        }
                        SendMessagesHelper.prepareSendingDocuments(accountInstance, documentsPathsArray, documentsOriginalPathsArray, documentsUrisArray, captionToSend, documentsMimeType, did, replyToMsg, replyToMsg, null, null, null, notify, scheduleDate, null, null, 0, 0, false);
                    }
                    if (voicePath != null) {
                        File file = new File(voicePath);

                        if (file.exists()) {
                            TLRPC.TL_document document = new TLRPC.TL_document();
                            document.file_reference = new byte[0];
                            document.dc_id = Integer.MIN_VALUE;
                            document.id = SharedConfig.getLastLocalId();
                            document.user_id = accountInstance.getUserConfig().getClientUserId();
                            document.mime_type = "audio/ogg";
                            document.date = accountInstance.getConnectionsManager().getCurrentTime();
                            document.size = (int) file.length();
                            TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                            attributeAudio.voice = true;
                            attributeAudio.waveform = MediaController.getWaveform(file.getAbsolutePath());
                            if (attributeAudio.waveform != null) {
                                attributeAudio.flags |= 4;
                            }
                            document.attributes.add(attributeAudio);

                            accountInstance.getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, file.getAbsolutePath(), did, replyToMsg, replyToMsg, sendingText, null, null, null, notify, scheduleDate, 0, null, null, false));
                            if (sendingText != null) {
                                sendingText = null;
                            }
                        }
                    }
                    if (sendingText != null) {
                        SendMessagesHelper.prepareSendingText(accountInstance, sendingText, did, topicId, notify, scheduleDate, 0);
                    }
                    if (contactsToSend != null && !contactsToSend.isEmpty()) {
                        for (int a = 0; a < contactsToSend.size(); a++) {
                            TLRPC.User user = contactsToSend.get(a);
                            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(user, did, replyToMsg, replyToMsg, null, null, notify, scheduleDate));
                        }
                    }
                    if (!TextUtils.isEmpty(message) && !videoEditorOpened && !photosEditorOpened) {
                        SendMessagesHelper.prepareSendingText(accountInstance, message.toString(), did, topicId, notify, scheduleDate, 0);
                    }
                }
            }
            if (dialogsFragment != null && fragment == null) {
                if (!presentedFragmentWithRemoveLast) {
                    dialogsFragment.finishFragment();
                }
            }
        }

        photoPathsArray = null;
        videoPath = null;
        voicePath = null;
        sendingText = null;
        documentsPathsArray = null;
        documentsOriginalPathsArray = null;
        contactsToSend = null;
        contactsToSendUri = null;
        exportingChatUri = null;
        return true;
    }

    private void onFinish() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (finished) {
            return;
        }
        finished = true;
        if (currentAccount != -1) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openBoostForUsersDialog);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openArticle);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.hasNewContactsToImport);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowPlayServicesAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.historyImportProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupCallUpdated);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersImportComplete);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newSuggestionsAvailable);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserShowLimitReachedDialog);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needCheckSystemBarColors);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.screenStateChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.showBulletin);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.requestPermissions);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.billingConfirmPurchaseError);

        LiteMode.removeOnPowerSaverAppliedListener(this::onPowerSaver);
    }

    private void onPowerSaver(boolean applied) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || actionBarLayout == null || !applied || LiteMode.getPowerSaverLevel() >= 100) {
            return;
        }
        BaseFragment lastFragment = actionBarLayout.getLastFragment();
        if (lastFragment == null || lastFragment instanceof LiteModeSettingsActivity) {
            return;
        }
        int percent = LiteMode.getBatteryLevel();
        BulletinFactory.of(lastFragment).createSimpleBulletin(
            new BatteryDrawable(percent / 100F, Color.WHITE, lastFragment.getThemedColor(Theme.key_dialogSwipeRemove), 1.3f),
            LocaleController.getString(R.string.LowPowerEnabledTitle),
            LocaleController.formatString("LowPowerEnabledSubtitle", R.string.LowPowerEnabledSubtitle, String.format("%d%%", percent)),
            LocaleController.getString(R.string.Disable),
            () -> presentFragment(new LiteModeSettingsActivity())
        ).setDuration(Bulletin.DURATION_PROLONG).show();
    }

    public void presentFragment(INavigationLayout.NavigationParams params) {
        getActionBarLayout().presentFragment(params);
    }

    public void presentFragment(BaseFragment fragment) {
        getActionBarLayout().presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return getActionBarLayout().presentFragment(fragment, removeLast, forceWithoutAnimation, true, false);
    }

    public INavigationLayout getActionBarLayout() {
        INavigationLayout currentLayout = actionBarLayout;
        if (!sheetFragmentsStack.isEmpty()) {
            currentLayout = sheetFragmentsStack.get(sheetFragmentsStack.size() - 1);
        }
        return currentLayout;
    }

    public INavigationLayout getLayersActionBarLayout() {
        return layersActionBarLayout;
    }

    public INavigationLayout getRightActionBarLayout() {
        return rightActionBarLayout;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (SharedConfig.passcodeHash.length() != 0 && SharedConfig.lastPauseTime != 0) {
            SharedConfig.lastPauseTime = 0;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("reset lastPauseTime onActivityResult");
            }
            UserConfig.getInstance(currentAccount).saveConfig(false);
        }
        if (requestCode == 105) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ApplicationLoader.canDrawOverlays = Settings.canDrawOverlays(this)) {
                    if (GroupCallActivity.groupCallInstance != null) {
                        GroupCallActivity.groupCallInstance.dismissInternal();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        GroupCallPip.clearForce();
                        GroupCallPip.updateVisibility(LaunchActivity.this);
                    }, 200);
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                VoIPService service = VoIPService.getSharedInstance();
                if (service != null) {
                    VideoCapturerDevice.mediaProjectionPermissionResultData = data;
                    service.createCaptureDevice(true);
                }
            }
        } else if (requestCode == PLAY_SERVICES_REQUEST_CHECK_SETTINGS) {
            LocationController.getInstance(currentAccount).startFusedLocationRequest(resultCode == Activity.RESULT_OK);
        } else if (requestCode == WEBVIEW_SHARE_API_REQUEST_CODE) {
            if (webviewShareAPIDoneListener != null) {
                webviewShareAPIDoneListener.run(resultCode == RESULT_OK);
                webviewShareAPIDoneListener = null;
            }
        } else {
            ThemeEditorView editorView = ThemeEditorView.getInstance();
            if (editorView != null) {
                editorView.onActivityResult(requestCode, resultCode, data);
            }
            if (actionBarLayout.getFragmentStack().size() != 0) {
                BaseFragment fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
                fragment.onActivityResultFragment(requestCode, resultCode, data);
                if (fragment.getLastStoryViewer() != null) {
                    fragment.getLastStoryViewer().onActivityResult(requestCode, resultCode, data);
                }
            }
            if (AndroidUtilities.isTablet()) {
                //TODO stories
                // check on tablets
                if (rightActionBarLayout.getFragmentStack().size() != 0) {
                    BaseFragment fragment = rightActionBarLayout.getFragmentStack().get(rightActionBarLayout.getFragmentStack().size() - 1);
                    fragment.onActivityResultFragment(requestCode, resultCode, data);
                }
                if (layersActionBarLayout.getFragmentStack().size() != 0) {
                    BaseFragment fragment = layersActionBarLayout.getFragmentStack().get(layersActionBarLayout.getFragmentStack().size() - 1);
                    fragment.onActivityResultFragment(requestCode, resultCode, data);
                }
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.onActivityResultReceived, requestCode, resultCode, data);
        }
    }

    private Utilities.Callback<Boolean> webviewShareAPIDoneListener;
    public void whenWebviewShareAPIDone(Utilities.Callback<Boolean> listener) {
        webviewShareAPIDoneListener = listener;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!checkPermissionsResult(requestCode, permissions, grantResults)) return;
        if (ApplicationLoader.applicationLoaderInstance != null && ApplicationLoader.applicationLoaderInstance.checkRequestPermissionResult(requestCode, permissions, grantResults)) return;

        if (actionBarLayout.getFragmentStack().size() != 0) {
            BaseFragment fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
            fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
        if (AndroidUtilities.isTablet()) {
            if (rightActionBarLayout.getFragmentStack().size() != 0) {
                BaseFragment fragment = rightActionBarLayout.getFragmentStack().get(rightActionBarLayout.getFragmentStack().size() - 1);
                fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
            }
            if (layersActionBarLayout.getFragmentStack().size() != 0) {
                BaseFragment fragment = layersActionBarLayout.getFragmentStack().get(layersActionBarLayout.getFragmentStack().size() - 1);
                fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
            }
        }

        VoIPFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
        StoryRecorder.onRequestPermissionsResult(requestCode, permissions, grantResults);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.onRequestPermissionResultReceived, requestCode, permissions, grantResults);

        if (requestedPermissions.get(requestCode, -1) >= 0) {
            int type = requestedPermissions.get(requestCode, -1);
            requestedPermissions.delete(requestCode);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.permissionsGranted, type);
        }

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.activityPermissionsGranted, requestCode, permissions, grantResults);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4096);
        ApplicationLoader.mainInterfacePaused = true;
        int account = currentAccount;
        Utilities.stageQueue.postRunnable(() -> {
            ApplicationLoader.mainInterfacePausedStageQueue = true;
            ApplicationLoader.mainInterfacePausedStageQueueTime = 0;
            if (VoIPService.getSharedInstance() == null) {
                MessagesController.getInstance(account).ignoreSetOnline = false;
            }
        });
        onPasscodePause();
        actionBarLayout.onPause();
        if (AndroidUtilities.isTablet()) {
            if (rightActionBarLayout != null) {
                rightActionBarLayout.onPause();
            }
            if (layersActionBarLayout != null) {
                layersActionBarLayout.onPause();
            }
        }
        if (passcodeDialog != null) {
            passcodeDialog.passcodeView.onPause();
        }
        for (PasscodeView overlay : overlayPasscodeViews) {
            overlay.onPause();
        }
        boolean doNotPause = false;
        if (ApplicationLoader.applicationLoaderInstance != null) {
            doNotPause = ApplicationLoader.applicationLoaderInstance.onPause();
        }
        ConnectionsManager.getInstance(currentAccount).setAppPaused(!doNotPause, false);
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onPause();
        }
        StoryRecorder.onPause();

        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.onPause();
        }
        SpoilerEffect2.pause(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Browser.bindCustomTabsService(this);
        ApplicationLoader.mainInterfaceStopped = false;
        GroupCallPip.updateVisibility(this);
        if (GroupCallActivity.groupCallInstance != null) {
            GroupCallActivity.groupCallInstance.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Browser.unbindCustomTabsService(this);
        ApplicationLoader.mainInterfaceStopped = true;
        GroupCallPip.updateVisibility(this);
        if (GroupCallActivity.groupCallInstance != null) {
            GroupCallActivity.groupCallInstance.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        isActive = false;
        if (PhotoViewer.getPipInstance() != null) {
            PhotoViewer.getPipInstance().destroyPhotoViewer();
        }
        if (PhotoViewer.hasInstance()) {
            PhotoViewer.getInstance().destroyPhotoViewer();
        }
        if (SecretMediaViewer.hasInstance()) {
            SecretMediaViewer.getInstance().destroyPhotoViewer();
        }
        if (ArticleViewer.hasInstance()) {
            ArticleViewer.getInstance().destroyArticleViewer();
        }
        if (ContentPreviewViewer.hasInstance()) {
            ContentPreviewViewer.getInstance().destroy();
        }
        if (GroupCallActivity.groupCallInstance != null) {
            GroupCallActivity.groupCallInstance.dismissInternal();
        }
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        MediaController.getInstance().setBaseActivity(this, false);
        MediaController.getInstance().setFeedbackView(feedbackView, false);
        if (pipRoundVideoView != null) {
            pipRoundVideoView.close(false);
        }
        Theme.destroyResources();
        EmbedBottomSheet embedBottomSheet = EmbedBottomSheet.getInstance();
        if (embedBottomSheet != null) {
            embedBottomSheet.destroy();
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.destroy();
        }
        try {
            for (int i = 0; i < visibleDialogs.size(); ++i) {
                Dialog dialog = visibleDialogs.get(i);
                if (dialog.isShowing()) {
                    visibleDialogs.get(i).dismiss();
                }
            }
            visibleDialogs.clear();
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (onGlobalLayoutListener != null) {
                final View view = getWindow().getDecorView().getRootView();
                view.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        super.onDestroy();
        onFinish();
        FloatingDebugController.onDestroy();
        if (flagSecureReason != null) {
            flagSecureReason.detach();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        for (Runnable callback : onUserLeaveHintListeners) {
            callback.run();
        }
        if (actionBarLayout != null) {
            actionBarLayout.onUserLeaveHint();
        }
    }

    View feedbackView;

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        if (onResumeStaticCallback != null) {
            onResumeStaticCallback.run();
            onResumeStaticCallback = null;
        }
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions();
        }
        checkWasMutedByAdmin(true);
        //FileLog.d("UI resume time = " + (SystemClock.elapsedRealtime() - ApplicationLoader.startTime));
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4096);
        MediaController.getInstance().setFeedbackView(feedbackView = actionBarLayout.getView(), true);
        ApplicationLoader.mainInterfacePaused = false;
        MessagesController.getInstance(currentAccount).sortDialogs(null);
        showLanguageAlert(false);
        Utilities.stageQueue.postRunnable(() -> {
            ApplicationLoader.mainInterfacePausedStageQueue = false;
            ApplicationLoader.mainInterfacePausedStageQueueTime = System.currentTimeMillis();
        });
        checkFreeDiscSpace(0);
        MediaController.checkGallery();
        onPasscodeResume();
        if (passcodeDialog == null || passcodeDialog.passcodeView.getVisibility() != View.VISIBLE) {
            actionBarLayout.onResume();
            if (AndroidUtilities.isTablet()) {
                if (rightActionBarLayout != null) {
                    rightActionBarLayout.onResume();
                }
                if (layersActionBarLayout != null) {
                    layersActionBarLayout.onResume();
                }
            }
        } else {
            actionBarLayout.dismissDialogs();
            if (AndroidUtilities.isTablet()) {
                if (rightActionBarLayout != null) {
                    rightActionBarLayout.dismissDialogs();
                }
                if (layersActionBarLayout != null) {
                    layersActionBarLayout.dismissDialogs();
                }
            }
            passcodeDialog.passcodeView.onResume();

            for (PasscodeView overlay : overlayPasscodeViews) {
                overlay.onResume();
            }
        }
        ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        updateCurrentConnectionState(currentAccount);
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onResume();
        }
        StoryRecorder.onResume();
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null && MediaController.getInstance().isMessagePaused()) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                MediaController.getInstance().seekToProgress(messageObject, messageObject.audioProgress);
            }
        }
        if (UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService != null) {
            showTosActivity(UserConfig.selectedAccount, UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService);
        } else if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.can_not_skip) {
            showUpdateActivity(UserConfig.selectedAccount, SharedConfig.pendingAppUpdate, true);
        }
        checkAppUpdate(false, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ApplicationLoader.canDrawOverlays = Settings.canDrawOverlays(this);
        }
        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.onResume();
        }
        invalidateTabletMode();
        SpoilerEffect2.pause(false);

        if (ApplicationLoader.applicationLoaderInstance != null) {
            ApplicationLoader.applicationLoaderInstance.onResume();
        }
        if (whenResumed != null) {
            whenResumed.run();
            whenResumed = null;
        }
    }

    public static Runnable whenResumed;

    private void invalidateTabletMode() {
        Boolean wasTablet = AndroidUtilities.getWasTablet();
        if (wasTablet == null) {
            return;
        }
        AndroidUtilities.resetWasTabletFlag();
        if (wasTablet != AndroidUtilities.isTablet()) {
            long dialogId = 0;
            long topicId = 0;
            if (wasTablet) {
                mainFragmentsStack.addAll(rightFragmentsStack);
                mainFragmentsStack.addAll(layerFragmentsStack);
                rightFragmentsStack.clear();
                layerFragmentsStack.clear();
            } else {
                List<BaseFragment> fragments = new ArrayList<>(mainFragmentsStack);
                mainFragmentsStack.clear();
                rightFragmentsStack.clear();
                layerFragmentsStack.clear();
                for (BaseFragment fragment : fragments) {
                    if (fragment instanceof DialogsActivity && ((DialogsActivity) fragment).isMainDialogList() && !((DialogsActivity) fragment).isArchive()) {
                        mainFragmentsStack.add(fragment);
                    } else if (fragment instanceof ChatActivity && !((ChatActivity) fragment).isInScheduleMode()) {
                        rightFragmentsStack.add(fragment);
                        if (dialogId == 0) {
                            dialogId = ((ChatActivity) fragment).getDialogId();
                            topicId = ((ChatActivity) fragment).getTopicId();
                        }
                    } else {
                        layerFragmentsStack.add(fragment);
                    }
                }
            }

            setupActionBarLayout();
            actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            if (AndroidUtilities.isTablet()) {
                rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                layersActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);

                for (BaseFragment fragment : mainFragmentsStack) {
                    if (fragment instanceof DialogsActivity && ((DialogsActivity) fragment).isMainDialogList()) {
                        ((DialogsActivity) fragment).setOpenedDialogId(dialogId, topicId);
                    }
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        AndroidUtilities.checkDisplaySize(this, newConfig);
        AndroidUtilities.setPreferredMaxRefreshRate(getWindow());
        super.onConfigurationChanged(newConfig);
        checkLayout();
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null) {
            pipRoundVideoView.onConfigurationChanged();
        }
        EmbedBottomSheet embedBottomSheet = EmbedBottomSheet.getInstance();
        if (embedBottomSheet != null) {
            embedBottomSheet.onConfigurationChanged(newConfig);
        }
        BoostPagerBottomSheet boostPagerBottomSheet = BoostPagerBottomSheet.getInstance();
        if (boostPagerBottomSheet != null) {
            boostPagerBottomSheet.onConfigurationChanged(newConfig);
        }
        PhotoViewer photoViewer = PhotoViewer.getPipInstance();
        if (photoViewer != null) {
            photoViewer.onConfigurationChanged(newConfig);
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.onConfigurationChanged();
        }
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        AndroidUtilities.isInMultiwindow = isInMultiWindowMode;
        checkLayout();
        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, final int account, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            switchToAvailableAccountOrLogout();
        } else if (id == NotificationCenter.openBoostForUsersDialog) {
            long dialogId = (long) args[0];
            ChatMessageCell chatMessageCell = null;
            if (args.length > 1) {
                chatMessageCell = (ChatMessageCell) args[1];
            }
            processBoostDialog(dialogId, null, null, chatMessageCell);
        } else if (id == NotificationCenter.closeOtherAppActivities) {
            if (args[0] != this) {
                onFinish();
                finish();
            }
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (currentConnectionState != state) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("switch to state " + state);
                }
                currentConnectionState = state;
                updateCurrentConnectionState(account);
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            drawerLayoutAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.attachMenuBotsDidLoad) {
            drawerLayoutAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.needShowAlert) {
            final Integer reason = (Integer) args[0];
            if (reason == 6 || reason == 3 && proxyErrorDialog != null) {
                return;
            } else if (reason == 4) {
                showTosActivity(account, (TLRPC.TL_help_termsOfService) args[1]);
                return;
            }
            BaseFragment fragment = null;
            if (!mainFragmentsStack.isEmpty()) {
                fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString(R.string.AppName));
            if (fragment != null) {
                Map<String, Integer> colorsReplacement = new HashMap<>();
                colorsReplacement.put("info1.**", fragment.getThemedColor(Theme.key_dialogTopBackground));
                colorsReplacement.put("info2.**", fragment.getThemedColor(Theme.key_dialogTopBackground));
                builder.setTopAnimation(R.raw.not_available, AlertsCreator.NEW_DENY_DIALOG_TOP_ICON_SIZE, false, fragment.getThemedColor(Theme.key_dialogTopBackground), colorsReplacement);
                builder.setTopAnimationIsNew(true);
            }
            if (reason != 2 && reason != 3) {
                builder.setNegativeButton(LocaleController.getString(R.string.MoreInfo), (dialogInterface, i) -> {
                    if (!mainFragmentsStack.isEmpty()) {
                        MessagesController.getInstance(account).openByUserName("spambot", mainFragmentsStack.get(mainFragmentsStack.size() - 1), 1);
                    }
                });
            }
            if (reason == 5) {
                builder.setMessage(LocaleController.getString(R.string.NobodyLikesSpam3));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            } else if (reason == 0) {
                builder.setMessage(LocaleController.getString(R.string.NobodyLikesSpam1));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            } else if (reason == 1) {
                builder.setMessage(LocaleController.getString(R.string.NobodyLikesSpam2));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            } else if (reason == 2) {
                SpannableStringBuilder span = SpannableStringBuilder.valueOf((String) args[1]);
                String type = (String) args[2];
                if (type.startsWith("PREMIUM_GIFT_SELF_REQUIRED_")) {
                    String msg = (String) args[1];
                    int start = msg.indexOf('*'), end = msg.indexOf('*', start + 1);
                    if (start != -1 && end != -1 && start != end) {
                        span.replace(start, end + 1, msg.substring(start + 1, end));
                        span.setSpan(new ClickableSpan() {
                            @Override
                            public void onClick(@NonNull View widget) {
                                getActionBarLayout().presentFragment(new PremiumPreviewFragment("gift"));
                            }

                            @Override
                            public void updateDrawState(@NonNull TextPaint ds) {
                                super.updateDrawState(ds);
                                ds.setUnderlineText(false);
                            }
                        }, start, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                builder.setMessage(span);
                if (type.startsWith("AUTH_KEY_DROP_")) {
                    builder.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
                    builder.setNegativeButton(LocaleController.getString(R.string.LogOut), (dialog, which) -> MessagesController.getInstance(currentAccount).performLogout(2));
                } else if (type.startsWith("PREMIUM_")) {
                    builder.setTitle(LocaleController.getString(R.string.TelegramPremium));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                } else {
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                }
            } else if (reason == 3) {
                builder.setTitle(LocaleController.getString(R.string.Proxy));
                builder.setMessage(LocaleController.getString(R.string.UseProxyTelegramError));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                proxyErrorDialog = showAlertDialog(builder);
                return;
            }
            if (!mainFragmentsStack.isEmpty()) {
                mainFragmentsStack.get(mainFragmentsStack.size() - 1).showDialog(builder.create());
            }
        } else if (id == NotificationCenter.wasUnableToFindCurrentLocation) {
            final HashMap<String, MessageObject> waitingForLocation = (HashMap<String, MessageObject>) args[0];
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString(R.string.AppName));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.setNegativeButton(LocaleController.getString(R.string.ShareYouLocationUnableManually), (dialogInterface, i) -> {
                if (mainFragmentsStack.isEmpty()) {
                    return;
                }
                BaseFragment lastFragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (!AndroidUtilities.isMapsInstalled(lastFragment)) {
                    return;
                }
                LocationActivity fragment = new LocationActivity(0);
                fragment.setDelegate((location, live, notify, scheduleDate) -> {
                    for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
                        MessageObject messageObject = entry.getValue();
                        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(location, messageObject.getDialogId(), messageObject, null, null, null, notify, scheduleDate));
                    }
                });
                presentFragment(fragment);
            });
            builder.setMessage(LocaleController.getString(R.string.ShareYouLocationUnable));
            if (!mainFragmentsStack.isEmpty()) {
                mainFragmentsStack.get(mainFragmentsStack.size() - 1).showDialog(builder.create());
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (sideMenu != null) {
                View child = sideMenu.getChildAt(0);
                if (child != null) {
                    child.invalidate();
                }
            }
            if (backgroundTablet != null) {
                backgroundTablet.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            }
        } else if (id == NotificationCenter.didSetPasscode) {
            flagSecureReason.invalidate();
        } else if (id == NotificationCenter.reloadInterface) {
            boolean last = mainFragmentsStack.size() > 1 && mainFragmentsStack.get(mainFragmentsStack.size() - 1) instanceof ProfileActivity;
            if (last) {
                ProfileActivity profileActivity = (ProfileActivity) mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (!profileActivity.isSettings()) {
                    last = false;
                }
            }
            rebuildAllFragments(last);
        } else if (id == NotificationCenter.suggestedLangpack) {
            showLanguageAlert(false);
        } else if (id == NotificationCenter.openArticle) {
            if (mainFragmentsStack.isEmpty()) {
                return;
            }
            if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab((TLRPC.TL_webPage) args[0]) != null) {
                return;
            }
            BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
            fragment.createArticleViewer(false).open((TLRPC.TL_webPage) args[0], (String) args[1]);
        } else if (id == NotificationCenter.hasNewContactsToImport) {
            if (actionBarLayout == null || actionBarLayout.getFragmentStack().isEmpty()) {
                return;
            }
            final int type = (Integer) args[0];
            final HashMap<String, ContactsController.Contact> contactHashMap = (HashMap<String, ContactsController.Contact>) args[1];
            final boolean first = (Boolean) args[2];
            final boolean schedule = (Boolean) args[3];
            BaseFragment fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);

            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
            builder.setTopAnimation(R.raw.permission_request_contacts, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground));
            builder.setTitle(LocaleController.getString(R.string.UpdateContactsTitle));
            builder.setMessage(LocaleController.getString(R.string.UpdateContactsMessage));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> ContactsController.getInstance(account).syncPhoneBookByAlert(contactHashMap, first, schedule, false));
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> ContactsController.getInstance(account).syncPhoneBookByAlert(contactHashMap, first, schedule, true));
            builder.setOnBackButtonListener((dialogInterface, i) -> ContactsController.getInstance(account).syncPhoneBookByAlert(contactHashMap, first, schedule, true));
            AlertDialog dialog = builder.create();
            fragment.showDialog(dialog);
            dialog.setCanceledOnTouchOutside(false);
        } else if (id == NotificationCenter.didSetNewTheme) {
            Boolean nightTheme = (Boolean) args[0];
            if (!nightTheme) {
                if (sideMenu != null) {
                    if (sideMenuContainer != null) {
                        sideMenuContainer.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
                    }
                    sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
                    sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
                    sideMenu.setListSelectorColor(Theme.getColor(Theme.key_listSelector));
                    sideMenu.getAdapter().notifyDataSetChanged();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        setTaskDescription(new ActivityManager.TaskDescription(null, null, Theme.getColor(Theme.key_actionBarDefault) | 0xff000000));
                    } catch (Exception ignore) {

                    }
                }
            }
            drawerLayoutContainer.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            boolean checkNavigationBarColor = true;
            if (args.length > 1) {
                checkNavigationBarColor = (boolean) args[1];
            }
            checkSystemBarColors(args.length > 2 && (boolean) args[2], true, checkNavigationBarColor && !isNavigationBarColorFrozen && !actionBarLayout.isTransitionAnimationInProgress(), true);
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            boolean instant = false;
            if (Build.VERSION.SDK_INT >= 21 && args[2] != null) {
                if (themeSwitchImageView.getVisibility() == View.VISIBLE) {
                    return;
                }
                try {
                    int[] pos = (int[]) args[2];
                    boolean toDark = (Boolean) args[4];
                    RLottieImageView darkThemeView = (RLottieImageView) args[5];
                    int w = drawerLayoutContainer.getMeasuredWidth();
                    int h = drawerLayoutContainer.getMeasuredHeight();
                    if (!toDark) {
                        darkThemeView.setVisibility(View.INVISIBLE);
                    }
                    rippleAbove = null;
                    if (args.length > 6) {
                        rippleAbove = (View) args[6];
                    }

                    isNavigationBarColorFrozen = true;

                    invalidateCachedViews(drawerLayoutContainer);
                    if (rippleAbove != null && rippleAbove.getBackground() != null) {
                        rippleAbove.getBackground().setAlpha(0);
                    }
                    Bitmap bitmap = AndroidUtilities.snapshotView(drawerLayoutContainer);
                    if (rippleAbove != null && rippleAbove.getBackground() != null) {
                        rippleAbove.getBackground().setAlpha(255);
                    }
                    frameLayout.removeView(themeSwitchImageView);
                    themeSwitchImageView = new ImageView(this);
                    if (toDark) {
                        frameLayout.addView(themeSwitchImageView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        themeSwitchSunView.setVisibility(View.GONE);
                    } else {
                        frameLayout.addView(themeSwitchImageView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        themeSwitchSunView.setTranslationX(pos[0] - AndroidUtilities.dp(14));
                        themeSwitchSunView.setTranslationY(pos[1] - AndroidUtilities.dp(14));
                        themeSwitchSunView.setVisibility(View.VISIBLE);
                        themeSwitchSunView.invalidate();
                    }
                    if (sideMenu != null && sideMenu.getChildCount() > 0) {
                        View firstChild = sideMenu.getChildAt(0);
                        if (firstChild instanceof DrawerProfileCell) {
                            ((DrawerProfileCell) firstChild).updateSunDrawable(toDark);
                        }
                    }
                    themeSwitchImageView.setImageBitmap(bitmap);
                    themeSwitchImageView.setVisibility(View.VISIBLE);
                    themeSwitchSunDrawable = darkThemeView.getAnimatedDrawable();
                    float finalRadius = (float) Math.max(Math.sqrt((w - pos[0]) * (w - pos[0]) + (h - pos[1]) * (h - pos[1])), Math.sqrt(pos[0] * pos[0] + (h - pos[1]) * (h - pos[1])));
                    float finalRadius2 = (float) Math.max(Math.sqrt((w - pos[0]) * (w - pos[0]) + pos[1] * pos[1]), Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1]));
                    finalRadius = Math.max(finalRadius, finalRadius2);
                    Animator anim = ViewAnimationUtils.createCircularReveal(toDark ? drawerLayoutContainer : themeSwitchImageView, pos[0], pos[1], toDark ? 0 : finalRadius, toDark ? finalRadius : 0);
                    anim.setDuration(400);
                    anim.setInterpolator(Easings.easeInOutQuad);
                    anim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rippleAbove = null;
                            drawerLayoutContainer.invalidate();
                            themeSwitchImageView.invalidate();
                            themeSwitchImageView.setImageDrawable(null);
                            themeSwitchImageView.setVisibility(View.GONE);
                            themeSwitchSunView.setVisibility(View.GONE);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeAccentListUpdated);
                            if (!toDark) {
                                darkThemeView.setVisibility(View.VISIBLE);
                            }
                            DrawerProfileCell.switchingTheme = false;
                        }
                    });
                    if (rippleAbove != null) {
                        ValueAnimator invalidateAnimator = ValueAnimator.ofFloat(0, 1);
                        invalidateAnimator.addUpdateListener(a -> frameLayout.invalidate());
                        invalidateAnimator.setDuration(anim.getDuration());
                        invalidateAnimator.start();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (isNavigationBarColorFrozen) {
                            isNavigationBarColorFrozen = false;
                            checkSystemBarColors(false, true);
                        }
                    }, toDark ? (h - pos[1]) / AndroidUtilities.dp(2.25f) : 50);
                    anim.start();
                    instant = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    try {
                        themeSwitchImageView.setImageDrawable(null);
                        frameLayout.removeView(themeSwitchImageView);
                        DrawerProfileCell.switchingTheme = false;
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                }
            } else {
                DrawerProfileCell.switchingTheme = false;
            }
            Theme.ThemeInfo theme = (Theme.ThemeInfo) args[0];
            boolean nightTheme = (Boolean) args[1];
            int accentId = (Integer) args[3];
            Runnable calcInBackgroundEnd = args.length > 7 ? (Runnable) args[7] : null;
            if (actionBarLayout == null) {
                return;
            }
            actionBarLayout.animateThemedValues(theme, accentId, nightTheme, instant, calcInBackgroundEnd);
            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout != null) {
                    layersActionBarLayout.animateThemedValues(theme, accentId, nightTheme, instant);
                }
                if (rightActionBarLayout != null) {
                    rightActionBarLayout.animateThemedValues(theme, accentId, nightTheme, instant);
                }
            }
        } else if (id == NotificationCenter.notificationsCountUpdated) {
            if (sideMenu != null) {
                Integer accountNum = (Integer) args[0];
                int count = sideMenu.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = sideMenu.getChildAt(a);
                    if (child instanceof DrawerUserCell) {
                        if (((DrawerUserCell) child).getAccountNumber() == accountNum) {
                            child.invalidate();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.needShowPlayServicesAlert) {
            try {
                final Status status = (Status) args[0];
                status.startResolutionForResult(this, PLAY_SERVICES_REQUEST_CHECK_SETTINGS);
            } catch (Throwable ignore) {

            }
        } else if (id == NotificationCenter.fileLoaded) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
            if (loadingThemeFileName != null) {
                if (loadingThemeFileName.equals(path)) {
                    loadingThemeFileName = null;
                    File locFile = new File(ApplicationLoader.getFilesDirFixed(), "remote" + loadingTheme.id + ".attheme");
                    Theme.ThemeInfo themeInfo = Theme.fillThemeValues(locFile, loadingTheme.title, loadingTheme);
                    if (themeInfo != null) {
                        if (themeInfo.pathToWallpaper != null) {
                            File file = new File(themeInfo.pathToWallpaper);
                            if (!file.exists()) {
                                TLRPC.TL_account_getWallPaper req = new TLRPC.TL_account_getWallPaper();
                                TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                                inputWallPaperSlug.slug = themeInfo.slug;
                                req.wallpaper = inputWallPaperSlug;
                                ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (response instanceof TLRPC.TL_wallPaper) {
                                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) response;
                                        loadingThemeInfo = themeInfo;
                                        loadingThemeWallpaperName = FileLoader.getAttachFileName(wallPaper.document);
                                        loadingThemeWallpaper = wallPaper;
                                        FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, FileLoader.PRIORITY_NORMAL, 1);
                                    } else {
                                        onThemeLoadFinish();
                                    }
                                }));
                                return;
                            }
                        }
                        Theme.ThemeInfo finalThemeInfo = Theme.applyThemeFile(locFile, loadingTheme.title, loadingTheme, true);
                        if (finalThemeInfo != null) {
                            presentFragment(new ThemePreviewActivity(finalThemeInfo, true, ThemePreviewActivity.SCREEN_TYPE_PREVIEW, false, false));
                        }
                    }
                    onThemeLoadFinish();
                }
            } else if (loadingThemeWallpaperName != null) {
                if (loadingThemeWallpaperName.equals(path)) {
                    loadingThemeWallpaperName = null;
                    File file = (File) args[1];
                    if (loadingThemeAccent) {
                        openThemeAccentPreview(loadingTheme, loadingThemeWallpaper, loadingThemeInfo);
                        onThemeLoadFinish();
                    } else {
                        Theme.ThemeInfo info = loadingThemeInfo;
                        Utilities.globalQueue.postRunnable(() -> {
                            info.createBackground(file, info.pathToWallpaper);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (loadingTheme == null) {
                                    return;
                                }
                                File locFile = new File(ApplicationLoader.getFilesDirFixed(), "remote" + loadingTheme.id + ".attheme");
                                Theme.ThemeInfo finalThemeInfo = Theme.applyThemeFile(locFile, loadingTheme.title, loadingTheme, true);
                                if (finalThemeInfo != null) {
                                    presentFragment(new ThemePreviewActivity(finalThemeInfo, true, ThemePreviewActivity.SCREEN_TYPE_PREVIEW, false, false));
                                }
                                onThemeLoadFinish();
                            });
                        });
                    }
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String path = (String) args[0];
            if (path.equals(loadingThemeFileName) || path.equals(loadingThemeWallpaperName)) {
                onThemeLoadFinish();
            }
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.screenStateChanged) {
            if (ApplicationLoader.mainInterfacePaused) {
                return;
            }
            if (ApplicationLoader.isScreenOn) {
                onPasscodeResume();
            } else {
                onPasscodePause();
            }
        } else if (id == NotificationCenter.needCheckSystemBarColors) {
            boolean useCurrentFragment = args.length > 0 && (boolean) args[0];
            checkSystemBarColors(useCurrentFragment);
        } else if (id == NotificationCenter.historyImportProgressChanged) {
            if (args.length > 1 && !mainFragmentsStack.isEmpty()) {
                AlertsCreator.processError(currentAccount, (TLRPC.TL_error) args[2], mainFragmentsStack.get(mainFragmentsStack.size() - 1), (TLObject) args[1]);
            }
        } else if (id == NotificationCenter.billingConfirmPurchaseError) {
            AlertsCreator.processError(currentAccount, (TLRPC.TL_error) args[1], mainFragmentsStack.get(mainFragmentsStack.size() - 1), (TLObject) args[0]);
        } else if (id == NotificationCenter.stickersImportComplete) {
            MediaDataController.getInstance(account).toggleStickerSet(this, (TLObject) args[0], 2, !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null, false, true);
        } else if (id == NotificationCenter.newSuggestionsAvailable) {
            sideMenu.invalidateViews();
        } else if (id == NotificationCenter.showBulletin) {
            if (!mainFragmentsStack.isEmpty()) {
                int type = (int) args[0];

                FrameLayout container = null;
                BaseFragment fragment = null;
                if (GroupCallActivity.groupCallUiVisible && GroupCallActivity.groupCallInstance != null) {
                    container = GroupCallActivity.groupCallInstance.getContainer();
                }

                if (container == null) {
                    fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                }

                switch (type) {
                    case Bulletin.TYPE_NAME_CHANGED: {
                        long peerId = (long) args[1];
                        String text = peerId > 0 ? LocaleController.getString(R.string.YourNameChanged) : LocaleController.getString(R.string.ChannelTitleChanged);
                        (container != null ? BulletinFactory.of(container, null) : BulletinFactory.of(fragment)).createErrorBulletin(text).show();
                        break;
                    }
                    case Bulletin.TYPE_BIO_CHANGED: {
                        long peerId = (long) args[1];
                        String text = peerId > 0 ? LocaleController.getString(R.string.YourBioChanged) : LocaleController.getString(R.string.ChannelDescriptionChanged);
                        (container != null ? BulletinFactory.of(container, null) : BulletinFactory.of(fragment)).createErrorBulletin(text).show();
                        break;
                    }
                    case Bulletin.TYPE_STICKER: {
                        TLRPC.Document sticker = (TLRPC.Document) args[1];
                        int bulletinType = (int) args[2];
                        StickerSetBulletinLayout layout = new StickerSetBulletinLayout(this, null, bulletinType, sticker, null);
                        int duration = Bulletin.DURATION_SHORT;
                        if (bulletinType == StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES || bulletinType == StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES_GIFS) {
                            duration = 3500;
                        }
                        if (fragment != null) {
                            Bulletin.make(fragment, layout, duration).show();
                        } else {
                            Bulletin.make(container, layout, duration).show();
                        }
                        break;
                    }
                    case Bulletin.TYPE_ERROR:
                        if (fragment != null) {
                            BulletinFactory.of(fragment).createErrorBulletin((String) args[1]).show();
                        } else {
                            BulletinFactory.of(container, null).createErrorBulletin((String) args[1]).show();
                        }
                        break;
                    case Bulletin.TYPE_SUCCESS:
                        if (fragment != null) {
                            BulletinFactory.of(fragment).createSuccessBulletin((String) args[1]).show();
                        } else {
                            BulletinFactory.of(container, null).createSuccessBulletin((String) args[1]).show();
                        }
                        break;
                    case Bulletin.TYPE_ERROR_SUBTITLE:
                        if (fragment != null) {
                            BulletinFactory.of(fragment).createErrorBulletinSubtitle((String) args[1], (String) args[2], fragment.getResourceProvider()).show();
                        } else {
                            BulletinFactory.of(container, null).createErrorBulletinSubtitle((String) args[1], (String) args[2], null).show();
                        }
                        break;
                    case Bulletin.TYPE_APP_ICON: {
                        LauncherIconController.LauncherIcon icon = (LauncherIconController.LauncherIcon) args[1];
                        AppIconBulletinLayout layout = new AppIconBulletinLayout(this, icon, null);
                        int duration = Bulletin.DURATION_SHORT;
                        if (fragment != null) {
                            Bulletin.make(fragment, layout, duration).show();
                        } else {
                            Bulletin.make(container, layout, duration).show();
                        }
                        break;
                    }
                }
            }
        } else if (id == NotificationCenter.groupCallUpdated) {
            checkWasMutedByAdmin(false);
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(args);
            }
        } else if (id == NotificationCenter.appUpdateAvailable) {
            if (updateLayout != null) {
                updateLayout.updateAppUpdateViews(currentAccount, mainFragmentsStack.size() == 1);
            }
        } else if (id == NotificationCenter.currentUserShowLimitReachedDialog) {
            if (!mainFragmentsStack.isEmpty()) {
                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (fragment.getParentActivity() != null) {
                    fragment.showDialog(new LimitReachedBottomSheet(fragment, fragment.getParentActivity(), (int) args[0], currentAccount, null));
                }
            }
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            if (drawerLayoutAdapter != null) {
                drawerLayoutAdapter.notifyDataSetChanged();
            }
            MessagesController.getMainSettings(currentAccount).edit().remove("transcribeButtonPressed").apply();
        } else if (id == NotificationCenter.requestPermissions) {
            int type = (int) args[0];
            String[] permissions = null;
            if (type == BLUETOOTH_CONNECT_TYPE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissions = new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    };
                }
            }
            if (permissions != null) {
                requsetPermissionsPointer++;
                requestedPermissions.put(requsetPermissionsPointer, type);
                ActivityCompat.requestPermissions(
                        this,
                        permissions,
                        requsetPermissionsPointer
                );
            }
        } else if (id == NotificationCenter.chatSwithcedToForum) {
            long chatId = (long) args[0];
            ForumUtilities.switchAllFragmentsInStackToForum(chatId, actionBarLayout);
        } else if (id == NotificationCenter.storiesEnabledUpdate) {
            if (drawerLayoutAdapter != null) {
                drawerLayoutAdapter.notifyDataSetChanged();
            }
        }
    }

    private void invalidateCachedViews(View parent) {
        int layerType = parent.getLayerType();
        if (layerType != View.LAYER_TYPE_NONE) {
            parent.invalidate();
        }
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                invalidateCachedViews(viewGroup.getChildAt(i));
            }
        }
    }

    private void checkWasMutedByAdmin(boolean checkOnly) {
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService != null && voIPService.groupCall != null) {
            boolean wasMuted = wasMutedByAdminRaisedHand;
            ChatObject.Call call = voIPService.groupCall;
            TLRPC.InputPeer peer = voIPService.getGroupCallPeer();
            long did;
            if (peer != null) {
                if (peer.user_id != 0) {
                    did = peer.user_id;
                } else if (peer.chat_id != 0) {
                    did = -peer.chat_id;
                } else {
                    did = -peer.channel_id;
                }
            } else {
                did = UserConfig.getInstance(currentAccount).clientUserId;
            }
            TLRPC.TL_groupCallParticipant participant = call.participants.get(did);
            boolean mutedByAdmin = participant != null && !participant.can_self_unmute && participant.muted;
            wasMutedByAdminRaisedHand = mutedByAdmin && participant.raise_hand_rating != 0;

            if (!checkOnly && wasMuted && !wasMutedByAdminRaisedHand && !mutedByAdmin && GroupCallActivity.groupCallInstance == null) {
                showVoiceChatTooltip(UndoView.ACTION_VOIP_CAN_NOW_SPEAK);
            }
        } else {
            wasMutedByAdminRaisedHand = false;
        }
    }

    private void showVoiceChatTooltip(int action) {
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService == null || mainFragmentsStack.isEmpty() || voIPService.groupCall == null) {
            return;
        }
        TLRPC.Chat chat = voIPService.getChat();
        BaseFragment fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
        UndoView undoView = null;
        if (fragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) fragment;
            if (chatActivity.getDialogId() == -chat.id) {
                chat = null;
            }
            undoView = chatActivity.getUndoView();
        } else if (fragment instanceof DialogsActivity) {
            DialogsActivity dialogsActivity = (DialogsActivity) fragment;
            undoView = dialogsActivity.getUndoView();
        } else if (fragment instanceof ProfileActivity) {
            ProfileActivity profileActivity = (ProfileActivity) fragment;
            undoView = profileActivity.getUndoView();
        }
        if (undoView != null) {
            undoView.showWithAction(0, action, chat);
        }

        if (action == UndoView.ACTION_VOIP_CAN_NOW_SPEAK && VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().playAllowTalkSound();
        }
    }

    private String getStringForLanguageAlert(HashMap<String, String> map, String key, int intKey) {
        String value = map.get(key);
        if (value == null) {
            return LocaleController.getString(key, intKey);
        }
        return value;
    }

    private void openThemeAccentPreview(TLRPC.TL_theme t, TLRPC.TL_wallPaper wallPaper, Theme.ThemeInfo info) {
        int lastId = info.lastAccentId;
        Theme.ThemeAccent accent = info.createNewAccent(t, currentAccount);
        info.prevAccentId = info.currentAccentId;
        info.setCurrentAccentId(accent.id);
        accent.pattern = wallPaper;
        presentFragment(new ThemePreviewActivity(info, lastId != info.lastAccentId, ThemePreviewActivity.SCREEN_TYPE_PREVIEW, false, false));
    }

    private void onThemeLoadFinish() {
        if (loadingThemeProgressDialog != null) {
            try {
                loadingThemeProgressDialog.dismiss();
            } finally {
                loadingThemeProgressDialog = null;
            }
        }
        loadingThemeWallpaperName = null;
        loadingThemeWallpaper = null;
        loadingThemeInfo = null;
        loadingThemeFileName = null;
        loadingTheme = null;
    }

    private boolean checkFreeDiscSpaceShown;
    private long alreadyShownFreeDiscSpaceAlertForced;
    private long lastSpaceAlert;
    private static LaunchActivity staticInstanceForAlerts;
    private void checkFreeDiscSpace(final int force) {
        staticInstanceForAlerts = this;
        AutoDeleteMediaTask.run();
        SharedConfig.checkLogsToDelete();
        if (Build.VERSION.SDK_INT >= 26 && force == 0 || checkFreeDiscSpaceShown) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                return;
            }
            try {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if ((force == 2 || force == 1) && Math.abs(alreadyShownFreeDiscSpaceAlertForced - System.currentTimeMillis()) > 1000 * 60 * 4 || Math.abs(preferences.getLong("last_space_check", 0) - System.currentTimeMillis()) >= 3 * 24 * 3600 * 1000) {
                    File path = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                    if (path == null) {
                        return;
                    }
                    long freeSpace;
                    StatFs statFs = new StatFs(path.getAbsolutePath());
                    if (Build.VERSION.SDK_INT < 18) {
                        freeSpace = Math.abs(statFs.getAvailableBlocks() * statFs.getBlockSize());
                    } else {
                        freeSpace = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
                    }
                    if (force > 0 || freeSpace < 1024 * 1024 * 50) {
                        if (force > 0) {
                            alreadyShownFreeDiscSpaceAlertForced = System.currentTimeMillis();
                        }
                        preferences.edit().putLong("last_space_check", System.currentTimeMillis()).commit();
                        AndroidUtilities.runOnUIThread(() -> {
                            if (checkFreeDiscSpaceShown) {
                                return;
                            }
                            try {
                                Dialog dialog = AlertsCreator.createFreeSpaceDialog(LaunchActivity.this);
                                dialog.setOnDismissListener(di -> {
                                    checkFreeDiscSpaceShown = false;
                                });
                                checkFreeDiscSpaceShown = true;
                                dialog.show();
                            } catch (Throwable ignore) {

                            }
                        });
                    }
                }
            } catch (Throwable ignore) {

            }
        }, 2000);
    }
    public static void checkFreeDiscSpaceStatic(final int force) {
        if (staticInstanceForAlerts != null) {
            staticInstanceForAlerts.checkFreeDiscSpace(force);
        }
    }

    private void showLanguageAlertInternal(LocaleController.LocaleInfo systemInfo, LocaleController.LocaleInfo englishInfo, String systemLang) {
        try {
            loadingLocaleDialog = false;
            boolean firstSystem = systemInfo.builtIn || LocaleController.getInstance().isCurrentLocalLocale();
            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
            builder.setTitle(getStringForLanguageAlert(systemLocaleStrings, "ChooseYourLanguage", R.string.ChooseYourLanguage));
            builder.setSubtitle(getStringForLanguageAlert(englishLocaleStrings, "ChooseYourLanguage", R.string.ChooseYourLanguage));
            LinearLayout linearLayout = new LinearLayout(LaunchActivity.this);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            final LanguageCell[] cells = new LanguageCell[2];
            final LocaleController.LocaleInfo[] selectedLanguage = new LocaleController.LocaleInfo[1];
            final LocaleController.LocaleInfo[] locales = new LocaleController.LocaleInfo[2];
            final String englishName = getStringForLanguageAlert(systemLocaleStrings, "English", R.string.English);
            locales[0] = firstSystem ? systemInfo : englishInfo;
            locales[1] = firstSystem ? englishInfo : systemInfo;
            selectedLanguage[0] = firstSystem ? systemInfo : englishInfo;

            for (int a = 0; a < 2; a++) {
                cells[a] = new LanguageCell(LaunchActivity.this);
                cells[a].setLanguage(locales[a], locales[a] == englishInfo ? englishName : null, true);
                cells[a].setTag(a);
                cells[a].setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
                cells[a].setLanguageSelected(a == 0, false);
                linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                cells[a].setOnClickListener(v -> {
                    Integer tag = (Integer) v.getTag();
                    selectedLanguage[0] = ((LanguageCell) v).getCurrentLocale();
                    for (int a1 = 0; a1 < cells.length; a1++) {
                        cells[a1].setLanguageSelected(a1 == tag, true);
                    }
                });
            }
            LanguageCell cell = new LanguageCell(LaunchActivity.this);
            cell.setValue(getStringForLanguageAlert(systemLocaleStrings, "ChooseYourLanguageOther", R.string.ChooseYourLanguageOther), getStringForLanguageAlert(englishLocaleStrings, "ChooseYourLanguageOther", R.string.ChooseYourLanguageOther));
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
            cell.setOnClickListener(v -> {
                localeDialog = null;
                drawerLayoutContainer.closeDrawer(true);
                presentFragment(new LanguageSelectActivity());
                for (int i = 0; i < visibleDialogs.size(); ++i) {
                    Dialog dialog = visibleDialogs.get(i);
                    if (dialog.isShowing()) {
                        visibleDialogs.get(i).dismiss();
                    }
                }
                visibleDialogs.clear();
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            builder.setView(linearLayout);
            builder.setNegativeButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                LocaleController.getInstance().applyLanguage(selectedLanguage[0], true, false, currentAccount);
                rebuildAllFragments(true);
            });
            localeDialog = showAlertDialog(builder);
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            preferences.edit().putString("language_showed2", systemLang).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private int[] tempLocation;
    private void drawRippleAbove(Canvas canvas, View parent) {
        if (parent == null || rippleAbove == null || rippleAbove.getBackground() == null) {
            return;
        }
        if (tempLocation == null) {
            tempLocation = new int[2];
        }
        rippleAbove.getLocationInWindow(tempLocation);
        int x = tempLocation[0], y = tempLocation[1];
        parent.getLocationInWindow(tempLocation);
        x -= tempLocation[0];
        y -= tempLocation[1];
        canvas.save();
        canvas.translate(x, y);
        rippleAbove.getBackground().draw(canvas);
        canvas.restore();
    }

    private void showLanguageAlert(boolean force) {
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            return;
        }
        try {
            if (loadingLocaleDialog || ApplicationLoader.mainInterfacePaused) {
                return;
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            String showedLang = preferences.getString("language_showed2", "");
            final String systemLang = MessagesController.getInstance(currentAccount).suggestedLangCode;
            if (!force && showedLang.equals(systemLang)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("alert already showed for " + showedLang);
                }
                return;
            }

            final LocaleController.LocaleInfo[] infos = new LocaleController.LocaleInfo[2];
            String arg = systemLang.contains("-") ? systemLang.split("-")[0] : systemLang;
            String alias;
            if ("in".equals(arg)) {
                alias = "id";
            } else if ("iw".equals(arg)) {
                alias = "he";
            } else if ("jw".equals(arg)) {
                alias = "jv";
            } else {
                alias = null;
            }
            for (int a = 0; a < LocaleController.getInstance().languages.size(); a++) {
                LocaleController.LocaleInfo info = LocaleController.getInstance().languages.get(a);
                if (info.shortName.equals("en")) {
                    infos[0] = info;
                }
                if (info.shortName.replace("_", "-").equals(systemLang) || info.shortName.equals(arg) || info.shortName.equals(alias)) {
                    infos[1] = info;
                }
                if (infos[0] != null && infos[1] != null) {
                    break;
                }
            }
            if (infos[0] == null || infos[1] == null || infos[0] == infos[1]) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("show lang alert for " + infos[0].getKey() + " and " + infos[1].getKey());
            }

            systemLocaleStrings = null;
            englishLocaleStrings = null;
            loadingLocaleDialog = true;

            TLRPC.TL_langpack_getStrings req = new TLRPC.TL_langpack_getStrings();
            req.lang_code = infos[1].getLangCode();
            req.keys.add("English");
            req.keys.add("ChooseYourLanguage");
            req.keys.add("ChooseYourLanguageOther");
            req.keys.add("ChangeLanguageLater");
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                final HashMap<String, String> keys = new HashMap<>();
                if (response != null) {
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    for (int a = 0; a < vector.objects.size(); a++) {
                        final TLRPC.LangPackString string = (TLRPC.LangPackString) vector.objects.get(a);
                        keys.put(string.key, string.value);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    systemLocaleStrings = keys;
                    if (englishLocaleStrings != null && systemLocaleStrings != null) {
                        showLanguageAlertInternal(infos[1], infos[0], systemLang);
                    }
                });
            }, ConnectionsManager.RequestFlagWithoutLogin);

            req = new TLRPC.TL_langpack_getStrings();
            req.lang_code = infos[0].getLangCode();
            req.keys.add("English");
            req.keys.add("ChooseYourLanguage");
            req.keys.add("ChooseYourLanguageOther");
            req.keys.add("ChangeLanguageLater");
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                final HashMap<String, String> keys = new HashMap<>();
                if (response != null) {
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    for (int a = 0; a < vector.objects.size(); a++) {
                        final TLRPC.LangPackString string = (TLRPC.LangPackString) vector.objects.get(a);
                        keys.put(string.key, string.value);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    englishLocaleStrings = keys;
                    if (englishLocaleStrings != null && systemLocaleStrings != null) {
                        showLanguageAlertInternal(infos[1], infos[0], systemLang);
                    }
                });
            }, ConnectionsManager.RequestFlagWithoutLogin);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void onPasscodePause() {
        if (lockRunnable != null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("cancel lockRunnable onPasscodePause");
            }
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (SharedConfig.passcodeHash.length() != 0) {
            SharedConfig.lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000);
            lockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (lockRunnable == this) {
                        if (AndroidUtilities.needShowPasscode(true)) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("lock app");
                            }
                            showPasscodeActivity(true, false, -1, -1, null, null);
                            try {
                                NotificationsController.getInstance(UserConfig.selectedAccount).showNotifications();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("didn't pass lock check");
                            }
                        }
                        lockRunnable = null;
                    }
                }
            };
            if (SharedConfig.appLocked) {
                AndroidUtilities.runOnUIThread(lockRunnable, 1000);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("schedule app lock in " + 1000);
                }
            } else if (SharedConfig.autoLockIn != 0) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("schedule app lock in " + (((long) SharedConfig.autoLockIn) * 1000 + 1000));
                }
                AndroidUtilities.runOnUIThread(lockRunnable, ((long) SharedConfig.autoLockIn) * 1000 + 1000);
            }
        } else {
            SharedConfig.lastPauseTime = 0;
        }
        SharedConfig.saveConfig();
    }

    public void addOverlayPasscodeView(PasscodeView overlay) {
        overlayPasscodeViews.add(overlay);
    }

    public void removeOverlayPasscodeView(PasscodeView overlay) {
        overlayPasscodeViews.remove(overlay);
    }

    private void onPasscodeResume() {
        if (lockRunnable != null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("cancel lockRunnable onPasscodeResume");
            }
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (AndroidUtilities.needShowPasscode(true)) {
            showPasscodeActivity(true, false, -1, -1, null, null);
        }
        if (SharedConfig.lastPauseTime != 0) {
            SharedConfig.lastPauseTime = 0;
            SharedConfig.saveConfig();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("reset lastPauseTime onPasscodeResume");
            }
        }
    }

    private void updateCurrentConnectionState(int account) {
        if (actionBarLayout == null) {
            return;
        }
        String title = null;
        int titleId = 0;
        Runnable action = null;
        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            title = "WaitingForNetwork";
            titleId = R.string.WaitingForNetwork;
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            title = "Updating";
            titleId = R.string.Updating;
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            title = "ConnectingToProxy";
            titleId = R.string.ConnectingToProxy;
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            title = "Connecting";
            titleId = R.string.Connecting;
        }
        if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting || currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            action = () -> {
                BaseFragment lastFragment = null;
                if (AndroidUtilities.isTablet()) {
                    if (!layerFragmentsStack.isEmpty()) {
                        lastFragment = layerFragmentsStack.get(layerFragmentsStack.size() - 1);
                    }
                } else {
                    if (!mainFragmentsStack.isEmpty()) {
                        lastFragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                    }
                }
                if (lastFragment instanceof ProxyListActivity || lastFragment instanceof ProxySettingsActivity) {
                    return;
                }
                presentFragment(new ProxyListActivity());
            };
        }
        actionBarLayout.setTitleOverlayText(title, titleId, action);
    }

    public void hideVisibleActionMode() {
        if (visibleActionMode == null) {
            return;
        }
        visibleActionMode.finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            BaseFragment lastFragment = null;
            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout != null && !layersActionBarLayout.getFragmentStack().isEmpty()) {
                    lastFragment = layersActionBarLayout.getFragmentStack().get(layersActionBarLayout.getFragmentStack().size() - 1);
                } else if (rightActionBarLayout != null && !rightActionBarLayout.getFragmentStack().isEmpty()) {
                    lastFragment = rightActionBarLayout.getFragmentStack().get(rightActionBarLayout.getFragmentStack().size() - 1);
                } else if (!actionBarLayout.getFragmentStack().isEmpty()) {
                    lastFragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
                }
            } else {
                if (!actionBarLayout.getFragmentStack().isEmpty()) {
                    lastFragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
                }
            }

            if (lastFragment != null) {
                Bundle args = lastFragment.getArguments();
                if (lastFragment instanceof ChatActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat");
                } else if (lastFragment instanceof GroupCreateFinalActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "group");
                } else if (lastFragment instanceof WallpapersListActivity) {
                    outState.putString("fragment", "wallpapers");
                } else if (lastFragment instanceof ProfileActivity) {
                    ProfileActivity profileActivity = (ProfileActivity) lastFragment;
                    if (profileActivity.isSettings()) {
                        outState.putString("fragment", "settings");
                    } else if (profileActivity.isChat() && args != null) {
                        outState.putBundle("args", args);
                        outState.putString("fragment", "chat_profile");
                    }
                } else if (lastFragment instanceof ChannelCreateActivity && args != null && args.getInt("step") == 0) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "channel");
                }
                lastFragment.saveSelfArgs(outState);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (FloatingDebugController.onBackPressed()) {
            return;
        }
        if (passcodeDialog != null && passcodeDialog.passcodeView.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }
        if (bottomSheetTabsOverlay != null && bottomSheetTabsOverlay.onBackPressed()) {
            return;
        }
        if (SearchTagsList.onBackPressedRenameTagAlert()) {
            return;
        } else if (ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible()) {
            ContentPreviewViewer.getInstance().closeWithMenu();
        } else if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(true, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(true, false);
        } else if (drawerLayoutContainer.isDrawerOpened()) {
            drawerLayoutContainer.closeDrawer(false);
        } else if (AndroidUtilities.isTablet()) {
            if (layersActionBarLayout.getView().getVisibility() == View.VISIBLE) {
                layersActionBarLayout.onBackPressed();
            } else {
                if (rightActionBarLayout.getView().getVisibility() == View.VISIBLE && !rightActionBarLayout.getFragmentStack().isEmpty()) {
                    BaseFragment lastFragment = rightActionBarLayout.getFragmentStack().get(rightActionBarLayout.getFragmentStack().size() - 1);
                    if (lastFragment.onBackPressed()) {
                        lastFragment.finishFragment();
                    }
                } else {
                    actionBarLayout.onBackPressed();
                }
            }
        } else {
            actionBarLayout.onBackPressed();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (actionBarLayout != null) {
            actionBarLayout.onLowMemory();
            if (AndroidUtilities.isTablet()) {
                if (rightActionBarLayout != null) {
                    rightActionBarLayout.onLowMemory();
                }
                if (layersActionBarLayout != null) {
                    layersActionBarLayout.onLowMemory();
                }
            }
        }
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        visibleActionMode = mode;
        try {
            Menu menu = mode.getMenu();
            if (menu != null) {
                boolean extended = actionBarLayout.extendActionMode(menu);
                if (!extended && AndroidUtilities.isTablet()) {
                    extended = rightActionBarLayout.extendActionMode(menu);
                    if (!extended) {
                        layersActionBarLayout.extendActionMode(menu);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (Build.VERSION.SDK_INT >= 23 && mode.getType() == ActionMode.TYPE_FLOATING) {
            return;
        }
        actionBarLayout.onActionModeStarted(mode);
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onActionModeStarted(mode);
            layersActionBarLayout.onActionModeStarted(mode);
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        if (visibleActionMode == mode) {
            visibleActionMode = null;
        }
        if (Build.VERSION.SDK_INT >= 23 && mode.getType() == ActionMode.TYPE_FLOATING) {
            return;
        }
        actionBarLayout.onActionModeFinished(mode);
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onActionModeFinished(mode);
            layersActionBarLayout.onActionModeFinished(mode);
        }
    }

    @Override
    public boolean onPreIme() {
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(true, false);
            return true;
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
            return true;
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(true, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            BaseFragment baseFragment = getLastFragment();
            if (baseFragment != null && baseFragment.getLastStoryViewer() != null) {
                baseFragment.getLastStoryViewer().dispatchKeyEvent(event);
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (VoIPService.getSharedInstance() != null) {
                if (Build.VERSION.SDK_INT >= 32) {
                    boolean oldValue = WebRtcAudioTrack.isSpeakerMuted();
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    int minVolume = am.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL);
                    boolean mute = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == minVolume && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN;
                    WebRtcAudioTrack.setSpeakerMute(mute);
                    if (oldValue != WebRtcAudioTrack.isSpeakerMuted()) {
                        showVoiceChatTooltip(mute ? UndoView.ACTION_VOIP_SOUND_MUTED : UndoView.ACTION_VOIP_SOUND_UNMUTED);
                    }
                }
            } else if (!mainFragmentsStack.isEmpty() && (!PhotoViewer.hasInstance() || !PhotoViewer.getInstance().isVisible()) && event.getRepeatCount() == 0) {
                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (fragment instanceof ChatActivity && !BaseFragment.hasSheets(fragment)) {
                    if (((ChatActivity) fragment).maybePlayVisibleVideo()) {
                        return true;
                    }
                }
                if (AndroidUtilities.isTablet() && !rightFragmentsStack.isEmpty()) {
                    fragment = rightFragmentsStack.get(rightFragmentsStack.size() - 1);
                    if (fragment instanceof ChatActivity && !BaseFragment.hasSheets(fragment)) {
                        if (((ChatActivity) fragment).maybePlayVisibleVideo()) {
                            return true;
                        }
                    }
                }
            }
        }
        try {
            return super.dispatchKeyEvent(event);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !SharedConfig.isWaitingForPasscodeEnter) {
            if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                return super.onKeyUp(keyCode, event);
            } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
                return super.onKeyUp(keyCode, event);
            }
            if (AndroidUtilities.isTablet()) {
                if (layersActionBarLayout.getView().getVisibility() == View.VISIBLE && !layersActionBarLayout.getFragmentStack().isEmpty()) {
                    layersActionBarLayout.getView().onKeyUp(keyCode, event);
                } else if (rightActionBarLayout.getView().getVisibility() == View.VISIBLE && !rightActionBarLayout.getFragmentStack().isEmpty()) {
                    rightActionBarLayout.getView().onKeyUp(keyCode, event);
                } else {
                    actionBarLayout.getView().onKeyUp(keyCode, event);
                }
            } else {
                if (actionBarLayout.getFragmentStack().size() == 1) {
                    if (!drawerLayoutContainer.isDrawerOpened()) {
                        if (getCurrentFocus() != null) {
                            AndroidUtilities.hideKeyboard(getCurrentFocus());
                        }
                        drawerLayoutContainer.openDrawer(false);
                    } else {
                        drawerLayoutContainer.closeDrawer(false);
                    }
                } else {
                    actionBarLayout.getView().onKeyUp(keyCode, event);
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean needPresentFragment(INavigationLayout layout, INavigationLayout.NavigationParams params) {
        BaseFragment fragment = params.fragment;
        boolean removeLast = params.removeLast;
        boolean forceWithoutAnimation = params.noAnimation;

        if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        if (AndroidUtilities.isTablet()) {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof IntroActivity || fragment instanceof CountrySelectActivity) && (layersActionBarLayout == null || layersActionBarLayout.getView().getVisibility() != View.VISIBLE), true);
            if (fragment instanceof DialogsActivity) {
                DialogsActivity dialogsActivity = (DialogsActivity) fragment;
                if (dialogsActivity.isMainDialogList() && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    getActionBarLayout().presentFragment(params.setRemoveLast(removeLast).setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(false));
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.getView().setVisibility(View.GONE);
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.getFragmentStack().isEmpty()) {
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            }
            if (fragment instanceof ChatActivity && !((ChatActivity) fragment).isInScheduleMode()) {
                if (!tabletFullSize && layout == rightActionBarLayout || tabletFullSize && layout == actionBarLayout) {
                    boolean result = !(tabletFullSize && layout == actionBarLayout && actionBarLayout.getFragmentStack().size() == 1);
                    if (!layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    if (!result) {
                        getActionBarLayout().presentFragment(params.setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(false));
                    }
                    return result;
                } else if (!tabletFullSize && layout != rightActionBarLayout && rightActionBarLayout != null) {
                    if (rightActionBarLayout.getView() != null) {
                        rightActionBarLayout.getView().setVisibility(View.VISIBLE);
                    }
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.presentFragment(params.setNoAnimation(true).setRemoveLast(removeLast).setCheckPresentFromDelegate(false));
                    if (!layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    getActionBarLayout().presentFragment(params.setRemoveLast(actionBarLayout.getFragmentStack().size() > 1).setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(false));
                    if (!layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else {
                    if (layersActionBarLayout != null && layersActionBarLayout.getFragmentStack() != null && !layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    getActionBarLayout().presentFragment(params.setRemoveLast(actionBarLayout.getFragmentStack().size() > 1).setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(false));
                    return false;
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.getView().setVisibility(View.VISIBLE);
                drawerLayoutContainer.setAllowOpenDrawer(false, true);

                int account = -1;
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        account = a;
                        break;
                    }
                }

                if (fragment instanceof LoginActivity && account == -1) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                    shadowTabletSide.setVisibility(View.GONE);
                    shadowTablet.setBackgroundColor(0x00000000);
                } else {
                    shadowTablet.setBackgroundColor(0x7f000000);
                }
                layersActionBarLayout.presentFragment(params.setRemoveLast(removeLast).setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(false));
                return false;
            }
        } else {
            boolean allow = true; // TODO: Make it a flag inside fragment itself, maybe BaseFragment#isDrawerOpenAllowed()?
            if (fragment instanceof LoginActivity || fragment instanceof IntroActivity || fragment instanceof ProxyListActivity || fragment instanceof ProxySettingsActivity) {
                if (mainFragmentsStack.size() == 0 || mainFragmentsStack.get(0) instanceof IntroActivity || mainFragmentsStack.get(0) instanceof LoginActivity) {
                    allow = false;
                }
            } else if (fragment instanceof CountrySelectActivity) {
                if (mainFragmentsStack.size() == 1) {
                    allow = false;
                }
            }
            drawerLayoutContainer.setAllowOpenDrawer(allow, false);
        }
        return true;
    }

    @Override
    public boolean needAddFragmentToStack(BaseFragment fragment, INavigationLayout layout) {
        if (AndroidUtilities.isTablet()) {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof IntroActivity || fragment instanceof CountrySelectActivity || fragment instanceof ProxyListActivity || fragment instanceof ProxySettingsActivity) && layersActionBarLayout.getView().getVisibility() != View.VISIBLE, true);
            if (fragment instanceof DialogsActivity) {
                DialogsActivity dialogsActivity = (DialogsActivity) fragment;
                if (dialogsActivity.isMainDialogList() && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.addFragmentToStack(fragment);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.getView().setVisibility(View.GONE);
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.getFragmentStack().isEmpty()) {
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            } else if (fragment instanceof ChatActivity && !((ChatActivity) fragment).isInScheduleMode()) {
                if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.getView().setVisibility(View.VISIBLE);
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.addFragmentToStack(fragment);
                    if (!layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(true);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    actionBarLayout.addFragmentToStack(fragment);
                    if (!layersActionBarLayout.getFragmentStack().isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.getFragmentStack().size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.getFragmentStack().get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(true);
                    }
                    return false;
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.getView().setVisibility(View.VISIBLE);
                drawerLayoutContainer.setAllowOpenDrawer(false, true);

                int account = -1;
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        account = a;
                        break;
                    }
                }

                if (fragment instanceof LoginActivity && account == -1) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                    shadowTabletSide.setVisibility(View.GONE);
                    shadowTablet.setBackgroundColor(0x00000000);
                } else {
                    shadowTablet.setBackgroundColor(0x7f000000);
                }
                layersActionBarLayout.addFragmentToStack(fragment);
                return false;
            }
        } else {
            boolean allow = true;
            if (fragment instanceof LoginActivity || fragment instanceof IntroActivity || fragment instanceof ProxyListActivity || fragment instanceof ProxySettingsActivity) {
                if (mainFragmentsStack.size() == 0 || mainFragmentsStack.get(0) instanceof IntroActivity) {
                    allow = false;
                }
            } else if (fragment instanceof CountrySelectActivity) {
                if (mainFragmentsStack.size() == 1) {
                    allow = false;
                }
            }
            drawerLayoutContainer.setAllowOpenDrawer(allow, false);
        }
        return true;
    }

    @Override
    public boolean needCloseLastFragment(INavigationLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (layout == actionBarLayout && layout.getFragmentStack().size() <= 1 && !switchingAccount) {
                onFinish();
                finish();
                return false;
            } else if (layout == rightActionBarLayout) {
                if (!tabletFullSize) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                }
            } else if (layout == layersActionBarLayout && actionBarLayout.getFragmentStack().isEmpty() && layersActionBarLayout.getFragmentStack().size() == 1) {
                onFinish();
                finish();
                return false;
            }
        } else {
            if (layout.getFragmentStack().size() <= 1) {
                onFinish();
                finish();
                return false;
            }
            if (layout.getFragmentStack().size() >= 2 && !(layout.getFragmentStack().get(0) instanceof LoginActivity)) {
                drawerLayoutContainer.setAllowOpenDrawer(true, false);
            }
        }
        return true;
    }

    public void rebuildAllFragments(boolean last) {
        if (layersActionBarLayout != null) {
            layersActionBarLayout.rebuildAllFragmentViews(last, last);
        } else {
            actionBarLayout.rebuildAllFragmentViews(last, last);
        }
    }

    @Override
    public void onRebuildAllFragments(INavigationLayout layout, boolean last) {
        if (AndroidUtilities.isTablet()) {
            if (layout == layersActionBarLayout) {
                rightActionBarLayout.rebuildAllFragmentViews(last, last);
                actionBarLayout.rebuildAllFragmentViews(last, last);
            }
        }
        drawerLayoutAdapter.notifyDataSetChanged();
    }

    public static BaseFragment getLastFragment() {
        if (instance != null && !instance.sheetFragmentsStack.isEmpty()) {
            return instance.sheetFragmentsStack.get(instance.sheetFragmentsStack.size() - 1).getLastFragment();
        }
        if (instance != null && instance.getActionBarLayout() != null) {
            return instance.getActionBarLayout().getLastFragment();
        }
        return null;
    }

    // last fragment that is not finishing itself
    public static BaseFragment getSafeLastFragment() {
        if (instance != null && !instance.sheetFragmentsStack.isEmpty()) {
            return instance.sheetFragmentsStack.get(instance.sheetFragmentsStack.size() - 1).getSafeLastFragment();
        }
        if (instance != null && instance.getActionBarLayout() != null) {
            return instance.getActionBarLayout().getSafeLastFragment();
        }
        return null;
    }

    //work faster that window.setNavigationBarColor
    public void requestCustomNavigationBar() {
        if (customNavigationBar == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            customNavigationBar = drawerLayoutContainer.createNavigationBar();
            FrameLayout decorView = (FrameLayout) getWindow().getDecorView();
            decorView.addView(customNavigationBar);
        }
        if (customNavigationBar != null) {
            if (customNavigationBar.getLayoutParams().height != AndroidUtilities.navigationBarHeight || ((FrameLayout.LayoutParams)customNavigationBar.getLayoutParams()).topMargin != customNavigationBar.getHeight()) {
                customNavigationBar.getLayoutParams().height = AndroidUtilities.navigationBarHeight;
                ((FrameLayout.LayoutParams)customNavigationBar.getLayoutParams()).topMargin = drawerLayoutContainer.getMeasuredHeight();
                customNavigationBar.requestLayout();
            }
        }
    }

    public int getNavigationBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Window window = getWindow();
            if (customNavigationBar != null) {
                return drawerLayoutContainer.getNavigationBarColor();
            } else {
                return window.getNavigationBarColor();
            }
        }
        return 0;
    }

    public void setNavigationBarColor(int color, boolean checkButtons) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Window window = getWindow();
            if (customNavigationBar != null) {
                if (drawerLayoutContainer.getNavigationBarColor() != color) {
                    drawerLayoutContainer.setNavigationBarColor(color);
                    if (checkButtons) {
                        final float brightness = AndroidUtilities.computePerceivedBrightness(color);
                        AndroidUtilities.setLightNavigationBar(window, brightness >= 0.721f);
                    }
                }
            } else {
                if (window.getNavigationBarColor() != color) {
                    window.setNavigationBarColor(color);
                    if (checkButtons) {
                        final float brightness = AndroidUtilities.computePerceivedBrightness(color);
                        AndroidUtilities.setLightNavigationBar(window, brightness >= 0.721f);
                    }
                }
            }
        }
        BottomSheetTabs bottomSheetTabs = getBottomSheetTabs();
        if (bottomSheetTabs != null) {
            bottomSheetTabs.setNavigationBarColor(color);
        }
    }

    public BottomSheetTabs getBottomSheetTabs() {
        if (rightActionBarLayout != null && rightActionBarLayout.getBottomSheetTabs() != null) {
            return rightActionBarLayout.getBottomSheetTabs();
        }
        if (actionBarLayout != null && actionBarLayout.getBottomSheetTabs() != null) {
            return actionBarLayout.getBottomSheetTabs();
        }
        return null;
    }

    private ValueAnimator navBarAnimator;
    public void animateNavigationBarColor(int toColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        if (navBarAnimator != null) {
            navBarAnimator.cancel();
            navBarAnimator = null;
        }
        navBarAnimator = ValueAnimator.ofArgb(getNavigationBarColor(), toColor);
        navBarAnimator.addUpdateListener(anm -> setNavigationBarColor((int) anm.getAnimatedValue(), false));
        navBarAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setNavigationBarColor(toColor, false);
            }
        });
        navBarAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        navBarAnimator.setDuration(320);
        navBarAnimator.start();
    }

    public void setLightNavigationBar(boolean lightNavigationBar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Window window = getWindow();
            AndroidUtilities.setLightNavigationBar(window, lightNavigationBar);
        }
    }

    public boolean isLightNavigationBar() {
        return AndroidUtilities.getLightNavigationBar(getWindow());
    }

    private void openMyStory(final int storyId, boolean openViews) {
        final long dialogId = UserConfig.getInstance(currentAccount).getClientUserId();
        StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
        TL_stories.PeerStories peerStories = storiesController.getStories(dialogId);
        TL_stories.StoryItem storyItem = null;
        if (peerStories != null) {
            for (int i = 0; i < peerStories.stories.size(); ++i) {
                if (peerStories.stories.get(i).id == storyId) {
                    storyItem = peerStories.stories.get(i);
                    break;
                }
            }
            if (storyItem != null) {
                BaseFragment lastFragment = getLastFragment();
                if (lastFragment == null) {
                    return;
                }
                StoryViewer.PlaceProvider placeProvider = null;
                if (lastFragment instanceof DialogsActivity) {
                    try {
                        placeProvider = StoriesListPlaceProvider.of(((DialogsActivity) lastFragment).dialogStoriesCell.recyclerListView);
                    } catch (Exception ignore) {}
                }
                lastFragment.getOrCreateStoryViewer().instantClose();
                ArrayList<Long> dialogIds = new ArrayList<>();
                dialogIds.add(storyItem.dialogId);
                if (openViews) {
                    lastFragment.getOrCreateStoryViewer().showViewsAfterOpening();
                }
                lastFragment.getOrCreateStoryViewer().open(this, storyItem, dialogIds, 0, null, peerStories, placeProvider, false);
                return;
            }
        }
        if (storyItem == null) {
            StoriesController.StoriesList list = null;
            StoriesController.StoriesList profileList = storiesController.getStoriesList(dialogId, StoriesController.StoriesList.TYPE_PINNED);
            if (profileList != null) {
                MessageObject msg = profileList.findMessageObject(storyId);
                if (msg != null) {
                    storyItem = msg.storyItem;
                    list = profileList;
                }
            }
            if (storyItem == null) {
                StoriesController.StoriesList archiveList = storiesController.getStoriesList(dialogId, StoriesController.StoriesList.TYPE_ARCHIVE);
                if (archiveList != null) {
                    MessageObject msg = archiveList.findMessageObject(storyId);
                    if (msg != null) {
                        storyItem = msg.storyItem;
                        list = archiveList;
                    }
                }
            }
            if (storyItem != null && list != null) {
                BaseFragment lastFragment = getLastFragment();
                if (lastFragment == null) {
                    return;
                }
                StoryViewer.PlaceProvider placeProvider = null;
                if (lastFragment instanceof DialogsActivity) {
                    try {
                        placeProvider = StoriesListPlaceProvider.of(((DialogsActivity) lastFragment).dialogStoriesCell.recyclerListView);
                    } catch (Exception ignore) {}
                }
                lastFragment.getOrCreateStoryViewer().instantClose();
                ArrayList<Long> dialogIds = new ArrayList<>();
                dialogIds.add(storyItem.dialogId);
                if (openViews) {
                    lastFragment.getOrCreateStoryViewer().showViewsAfterOpening();
                }
                lastFragment.getOrCreateStoryViewer().open(this, storyItem, dialogIds, 0, list, null, placeProvider, false);
                return;
            }
        }
        TL_stories.TL_stories_getStoriesByID req = new TL_stories.TL_stories_getStoriesByID();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.id.add(storyId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_stories.TL_stories_stories) {
                TL_stories.TL_stories_stories response = (TL_stories.TL_stories_stories) res;
                TL_stories.StoryItem storyItem1 = null;
                for (int i = 0; i < response.stories.size(); ++i) {
                    if (response.stories.get(i).id == storyId) {
                        storyItem1 = response.stories.get(i);
                        break;
                    }
                }
                if (storyItem1 != null) {
                    storyItem1.dialogId = dialogId;
                    BaseFragment lastFragment = getLastFragment();
                    if (lastFragment == null) {
                        return;
                    }
                    StoryViewer.PlaceProvider placeProvider = null;
                    if (lastFragment instanceof DialogsActivity) {
                        try {
                            placeProvider = StoriesListPlaceProvider.of(((DialogsActivity) lastFragment).dialogStoriesCell.recyclerListView);
                        } catch (Exception ignore) {}
                    }
                    lastFragment.getOrCreateStoryViewer().instantClose();
                    ArrayList<Long> dialogIds = new ArrayList<>();
                    dialogIds.add(dialogId);
                    if (openViews) {
                        lastFragment.getOrCreateStoryViewer().showViewsAfterOpening();
                    }
                    lastFragment.getOrCreateStoryViewer().open(this, storyItem1, dialogIds, 0, null, null, placeProvider, false);
                    return;
                }
            }
            BulletinFactory.global().createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.StoryNotFound)).show(false);
        }));
    }

    private void openStories(long[] dialogIds, boolean requestWhenNeeded) {
        boolean onlyArchived = true;
        for (int i = 0; i < dialogIds.length; ++i) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogIds[i]);
            if (user != null && !user.stories_hidden) {
                onlyArchived = false;
                break;
            }
        }
//        NotificationsController.getInstance(currentAccount).processIgnoreStories();
//        List<BaseFragment> fragments = actionBarLayout.getFragmentStack();
//        DialogsActivity dialogsActivity = null;
//        for (int i = fragments.size() - 1; i >= 0; --i) {
//            BaseFragment fragment = fragments.get(i);
//            if (fragment instanceof DialogsActivity && (!((DialogsActivity) fragment).isArchive() || onlyArchived) && ((DialogsActivity) fragment).getType() == DialogsActivity.DIALOGS_TYPE_DEFAULT) {
//                dialogsActivity = (DialogsActivity) fragment;
//                break;
//            } else {
//                fragment.removeSelfFromStack(true);
//            }
//        }
//        if (dialogsActivity != null) {
//            if (drawerLayoutContainer != null) {
//                drawerLayoutContainer.closeDrawer(true);
//            }
//            if (onlyArchived) {
//                MessagesController.getInstance(dialogsActivity.getCurrentAccount()).getStoriesController().loadHiddenStories();
//            } else {
//                MessagesController.getInstance(dialogsActivity.getCurrentAccount()).getStoriesController().loadStories();
//            }
//            if (dialogsActivity.rightSlidingDialogContainer.hasFragment()) {
//                dialogsActivity.rightSlidingDialogContainer.finishPreview();
//            }
//            if (onlyArchived && !dialogsActivity.isArchive()) {
//                Bundle args = new Bundle();
//                args.putInt("folderId", 1);
//                presentFragment(dialogsActivity = new DialogsActivity(args));
//            }
//            final DialogsActivity dialogsActivity1 = dialogsActivity;
//            dialogsActivity1.scrollToTop(false, false);
//            AndroidUtilities.runOnUIThread(() -> {
//                dialogsActivity1.scrollToTop(true, true);
//            }, 500);
//            return;
//        }

        BaseFragment lastFragment = getLastFragment();
        if (lastFragment == null) {
            return;
        }
        StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
        ArrayList<TL_stories.PeerStories> stories = new ArrayList<>(onlyArchived ? storiesController.getHiddenList() : storiesController.getDialogListStories());
        ArrayList<Long> peerIds = new ArrayList<>();
        ArrayList<Long> toLoadPeerIds = new ArrayList<>();
        final long[] finalDialogIds;
        if (!onlyArchived) {
            ArrayList<Long> dids = new ArrayList<>();
            for (int i = 0; i < dialogIds.length; ++i) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogIds[i]);
                if (user == null || !user.stories_hidden) {
                    dids.add(dialogIds[i]);
                }
            }
            finalDialogIds = Longs.toArray(dids);
        } else {
            finalDialogIds = dialogIds;
        }
        if (requestWhenNeeded) {
            for (int i = 0; i < finalDialogIds.length; ++i) {
                toLoadPeerIds.add(finalDialogIds[i]);
            }
        } else {
            for (int i = 0; i < finalDialogIds.length; ++i) {
                peerIds.add(finalDialogIds[i]);
            }
        }
        if (!toLoadPeerIds.isEmpty() && requestWhenNeeded) {
            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final int[] loaded = new int[] { toLoadPeerIds.size() };
            final Runnable whenDone = () -> {
                loaded[0]--;
                if (loaded[0] == 0) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesUpdated);
                    openStories(finalDialogIds, false);
                }
            };
            for (int i = 0; i < toLoadPeerIds.size(); ++i) {
                long did = toLoadPeerIds.get(i);
                TL_stories.TL_stories_getPeerStories req = new TL_stories.TL_stories_getPeerStories();
                req.peer = messagesController.getInputPeer(did);
                if (req.peer instanceof TLRPC.TL_inputPeerEmpty) {
                    loaded[0]--;
                    continue;
                }
                if (req.peer == null) {
                    loaded[0]--;
                    continue;
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TL_stories.TL_stories_peerStories) {
                        TL_stories.TL_stories_peerStories r = (TL_stories.TL_stories_peerStories) res;
                        messagesController.putUsers(r.users, false);
                        messagesController.getStoriesController().putStories(did, r.stories);
                        whenDone.run();
                    } else {
                        whenDone.run();
                    }
                }));
            }
        } else {
            long me = UserConfig.getInstance(currentAccount).getClientUserId();
            for (int i = 0; i < stories.size(); ++i) {
                TL_stories.PeerStories userStories = stories.get(i);
                long dialogId = DialogObject.getPeerDialogId(userStories.peer);
                if (dialogId != me && !peerIds.contains(dialogId) && storiesController.hasUnreadStories(dialogId)) {
                    peerIds.add(dialogId);
                }
            }
            if (!peerIds.isEmpty()) {
                StoryViewer.PlaceProvider placeProvider = null;
                if (lastFragment instanceof DialogsActivity) {
                    try {
                        placeProvider = StoriesListPlaceProvider.of(((DialogsActivity) lastFragment).dialogStoriesCell.recyclerListView);
                    } catch (Exception ignore) {}
                }
                lastFragment.getOrCreateStoryViewer().instantClose();
                lastFragment.getOrCreateStoryViewer().open(this, null, peerIds, 0, null, null, placeProvider, false);
            }
        }
    }

    public static void dismissAllWeb() {
        BaseFragment lastFragment = getSafeLastFragment();
        if (lastFragment == null) return;

        BaseFragment sheetFragment =
            lastFragment.getParentLayout() instanceof ActionBarLayout ?
                ((ActionBarLayout) lastFragment.getParentLayout()).getSheetFragment(false) :
                null;

        if (sheetFragment != null && sheetFragment.sheetsStack != null) {
            for (int i = sheetFragment.sheetsStack.size() - 1; i >= 0; --i) {
                BaseFragment.AttachedSheet sheet = sheetFragment.sheetsStack.get(i);
                sheet.dismiss(true);
            }
        }
        if (lastFragment != null && lastFragment.sheetsStack != null) {
            for (int i = lastFragment.sheetsStack.size() - 1; i >= 0; --i) {
                BaseFragment.AttachedSheet sheet = lastFragment.sheetsStack.get(i);
                sheet.dismiss(true);
            }
        }

        final ArrayList<BotWebViewSheet> botSheets = new ArrayList<>();
        for (BotWebViewSheet sheet : BotWebViewSheet.activeSheets)
            botSheets.add(sheet);
        for (BotWebViewSheet sheet : botSheets)
            sheet.dismiss(true);
    }

    public static void makeRipple(float x, float y, float intensity) {
        if (instance == null) return;
        instance.makeRippleInternal(x, y, intensity);
    }

    private ISuperRipple currentRipple;
    private void makeRippleInternal(float x, float y, float intensity) {
        View parent = getWindow().getDecorView();
        if (parent == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (currentRipple == null || currentRipple.view != parent) {
                currentRipple = new SuperRipple(parent);
            }
        } else if (Build.VERSION.SDK_INT >= 26) {
            if (currentRipple == null || currentRipple.view != parent) {
                currentRipple = new SuperRippleFallback(parent);
            }
        }
        if (currentRipple != null) {
            currentRipple.animate(x, y, intensity);
        }
    }
}

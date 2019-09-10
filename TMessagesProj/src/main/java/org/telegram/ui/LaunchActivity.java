/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.StatFs;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.camera.CameraController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BlockingUpdateView;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharingLocationsAlert;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TermsOfServiceView;
import org.telegram.ui.Components.ThemeEditorView;
import org.telegram.ui.Components.UpdateAppAlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

public class LaunchActivity extends Activity implements ActionBarLayout.ActionBarLayoutDelegate, NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate {

    private boolean finished;
    private String videoPath;
    private String sendingText;
    private ArrayList<SendMessagesHelper.SendingMediaInfo> photoPathsArray;
    private ArrayList<String> documentsPathsArray;
    private ArrayList<Uri> documentsUrisArray;
    private String documentsMimeType;
    private ArrayList<String> documentsOriginalPathsArray;
    private ArrayList<TLRPC.User> contactsToSend;
    private Uri contactsToSendUri;
    private int currentConnectionState;
    private static ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<>();
    private static ArrayList<BaseFragment> layerFragmentsStack = new ArrayList<>();
    private static ArrayList<BaseFragment> rightFragmentsStack = new ArrayList<>();
    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;

    private ActionMode visibleActionMode;

    private ActionBarLayout actionBarLayout;
    private ActionBarLayout layersActionBarLayout;
    private ActionBarLayout rightActionBarLayout;
    private FrameLayout shadowTablet;
    private FrameLayout shadowTabletSide;
    private View backgroundTablet;
    protected DrawerLayoutContainer drawerLayoutContainer;
    private DrawerLayoutAdapter drawerLayoutAdapter;
    private PasscodeView passcodeView;
    private TermsOfServiceView termsOfServiceView;
    private BlockingUpdateView blockingUpdateView;
    private AlertDialog visibleDialog;
    private AlertDialog proxyErrorDialog;
    private RecyclerListView sideMenu;

    private AlertDialog localeDialog;
    private boolean loadingLocaleDialog;
    private HashMap<String, String> systemLocaleStrings;
    private HashMap<String, String> englishLocaleStrings;

    private int currentAccount;

    private Intent passcodeSaveIntent;
    private boolean passcodeSaveIntentIsNew;
    private boolean passcodeSaveIntentIsRestore;

    private boolean tabletFullSize;

    private String loadingThemeFileName;
    private String loadingThemeWallpaperName;
    private Theme.ThemeInfo loadingThemeInfo;
    private TLRPC.TL_theme loadingTheme;
    private AlertDialog loadingThemeProgressDialog;

    private Runnable lockRunnable;

    private static final int PLAY_SERVICES_REQUEST_CHECK_SETTINGS = 140;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            long crashed_time = preferences.getLong("intro_crashed_time", 0);
            boolean fromIntro = intent.getBooleanExtra("fromIntro", false);
            if (fromIntro) {
                preferences.edit().putLong("intro_crashed_time", 0).commit();
            }
            if (!isProxy && Math.abs(crashed_time - System.currentTimeMillis()) >= 60 * 2 * 1000 && intent != null && !fromIntro) {
                preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", MODE_PRIVATE);
                Map<String, ?> state = preferences.getAll();
                if (state.isEmpty()) {
                    Intent intent2 = new Intent(this, IntroActivity.class);
                    intent2.setData(intent.getData());
                    startActivity(intent2);
                    super.onCreate(savedInstanceState);
                    finish();
                    return;
                }
            }
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                setTaskDescription(new ActivityManager.TaskDescription(null, null, Theme.getColor(Theme.key_actionBarDefault) | 0xff000000));
            } catch (Exception ignore) {

            }
            try {
                getWindow().setNavigationBarColor(0xff000000);
            } catch (Exception ignore) {

            }
        }

        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        if (SharedConfig.passcodeHash.length() > 0 && !SharedConfig.allowScreenCapture) {
            try {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 24) {
            AndroidUtilities.isInMultiwindow = isInMultiWindowMode();
        }
        Theme.createChatResources(this, false);
        if (SharedConfig.passcodeHash.length() != 0 && SharedConfig.appLocked) {
            SharedConfig.lastPauseTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        }

        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            AndroidUtilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        actionBarLayout = new ActionBarLayout(this);

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setContentView(drawerLayoutContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (AndroidUtilities.isTablet()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            RelativeLayout launchLayout = new RelativeLayout(this) {

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
                        actionBarLayout.measure(MeasureSpec.makeMeasureSpec(leftWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        shadowTabletSide.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        rightActionBarLayout.measure(MeasureSpec.makeMeasureSpec(width - leftWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    } else {
                        tabletFullSize = true;
                        actionBarLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                    backgroundTablet.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    shadowTablet.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    layersActionBarLayout.measure(MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(530), width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(528), height), MeasureSpec.EXACTLY));

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
                        actionBarLayout.layout(0, 0, actionBarLayout.getMeasuredWidth(), actionBarLayout.getMeasuredHeight());
                        rightActionBarLayout.layout(leftWidth, 0, leftWidth + rightActionBarLayout.getMeasuredWidth(), rightActionBarLayout.getMeasuredHeight());
                    } else {
                        actionBarLayout.layout(0, 0, actionBarLayout.getMeasuredWidth(), actionBarLayout.getMeasuredHeight());
                    }
                    int x = (width - layersActionBarLayout.getMeasuredWidth()) / 2;
                    int y = (height - layersActionBarLayout.getMeasuredHeight()) / 2;
                    layersActionBarLayout.layout(x, y, x + layersActionBarLayout.getMeasuredWidth(), y + layersActionBarLayout.getMeasuredHeight());
                    backgroundTablet.layout(0, 0, backgroundTablet.getMeasuredWidth(), backgroundTablet.getMeasuredHeight());
                    shadowTablet.layout(0, 0, shadowTablet.getMeasuredWidth(), shadowTablet.getMeasuredHeight());
                }
            };
            drawerLayoutContainer.addView(launchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            backgroundTablet = new View(this);
            BitmapDrawable drawable = (BitmapDrawable) getResources().getDrawable(R.drawable.catstile);
            drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            backgroundTablet.setBackgroundDrawable(drawable);
            launchLayout.addView(backgroundTablet, LayoutHelper.createRelative(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            launchLayout.addView(actionBarLayout);

            rightActionBarLayout = new ActionBarLayout(this);
            rightActionBarLayout.init(rightFragmentsStack);
            rightActionBarLayout.setDelegate(this);
            launchLayout.addView(rightActionBarLayout);

            shadowTabletSide = new FrameLayout(this);
            shadowTabletSide.setBackgroundColor(0x40295274);
            launchLayout.addView(shadowTabletSide);

            shadowTablet = new FrameLayout(this);
            shadowTablet.setVisibility(layerFragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
            shadowTablet.setBackgroundColor(0x7f000000);
            launchLayout.addView(shadowTablet);
            shadowTablet.setOnTouchListener((v, event) -> {
                if (!actionBarLayout.fragmentsStack.isEmpty() && event.getAction() == MotionEvent.ACTION_UP) {
                    float x = event.getX();
                    float y = event.getY();
                    int[] location = new int[2];
                    layersActionBarLayout.getLocationOnScreen(location);
                    int viewX = location[0];
                    int viewY = location[1];

                    if (layersActionBarLayout.checkTransitionAnimation() || x > viewX && x < viewX + layersActionBarLayout.getWidth() && y > viewY && y < viewY + layersActionBarLayout.getHeight()) {
                        return false;
                    } else {
                        if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                            for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                                layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
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

            layersActionBarLayout = new ActionBarLayout(this);
            layersActionBarLayout.setRemoveActionBarExtraHeight(true);
            layersActionBarLayout.setBackgroundView(shadowTablet);
            layersActionBarLayout.setUseAlphaAnimations(true);
            layersActionBarLayout.setBackgroundResource(R.drawable.boxshadow);
            layersActionBarLayout.init(layerFragmentsStack);
            layersActionBarLayout.setDelegate(this);
            layersActionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
            layersActionBarLayout.setVisibility(layerFragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
            launchLayout.addView(layersActionBarLayout);
        } else {
            drawerLayoutContainer.addView(actionBarLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        sideMenu = new RecyclerListView(this);
        ((DefaultItemAnimator) sideMenu.getItemAnimator()).setDelayAnimations(false);
        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        sideMenu.setAdapter(drawerLayoutAdapter = new DrawerLayoutAdapter(this));
        drawerLayoutContainer.setDrawerLayout(sideMenu);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sideMenu.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet() ? AndroidUtilities.dp(320) : Math.min(AndroidUtilities.dp(320), Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56));
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        sideMenu.setLayoutParams(layoutParams);
        sideMenu.setOnItemClickListener((view, position) -> {
            if (position == 0) {
                drawerLayoutAdapter.setAccountsShowed(!drawerLayoutAdapter.isAccountsShowed(), true);
            } else if (view instanceof DrawerUserCell) {
                switchToAccount(((DrawerUserCell) view).getAccountNumber(), true);
                drawerLayoutContainer.closeDrawer(false);
            } else if (view instanceof DrawerAddCell) {
                int freeAccount = -1;
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (!UserConfig.getInstance(a).isClientActivated()) {
                        freeAccount = a;
                        break;
                    }
                }
                if (freeAccount >= 0) {
                    presentFragment(new LoginActivity(freeAccount));
                }
                drawerLayoutContainer.closeDrawer(false);
            } else {
                int id = drawerLayoutAdapter.getId(position);
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
                    presentFragment(new ContactsActivity(null));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 7) {
                    presentFragment(new InviteContactsActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 8) {
                    presentFragment(new SettingsActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 9) {
                    Browser.openUrl(LaunchActivity.this, LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 10) {
                    presentFragment(new CallLogActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 11) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    presentFragment(new ChatActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                }
            }
        });

        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
        actionBarLayout.init(mainFragmentsStack);
        actionBarLayout.setDelegate(this);

        Theme.loadWallpaper();

        passcodeView = new PasscodeView(this);
        drawerLayoutContainer.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        checkCurrentAccount();

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);

        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.notificationsCountUpdated);

        if (actionBarLayout.fragmentsStack.isEmpty()) {
            if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                actionBarLayout.addFragmentToStack(new LoginActivity());
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
                                SettingsActivity settings = new SettingsActivity();
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
            BaseFragment fragment = actionBarLayout.fragmentsStack.get(0);
            if (fragment instanceof DialogsActivity) {
                ((DialogsActivity) fragment).setSideMenu(sideMenu);
            }
            boolean allowOpen = true;
            if (AndroidUtilities.isTablet()) {
                allowOpen = actionBarLayout.fragmentsStack.size() <= 1 && layersActionBarLayout.fragmentsStack.isEmpty();
                if (layersActionBarLayout.fragmentsStack.size() == 1 && layersActionBarLayout.fragmentsStack.get(0) instanceof LoginActivity) {
                    allowOpen = false;
                }
            }
            if (actionBarLayout.fragmentsStack.size() == 1 && actionBarLayout.fragmentsStack.get(0) instanceof LoginActivity) {
                allowOpen = false;
            }
            drawerLayoutContainer.setAllowOpenDrawer(allowOpen, false);
        }
        checkLayout();

        handleIntent(getIntent(), false, savedInstanceState != null, false);

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
            if (os1.contains("flyme") || os2.contains("flyme")) {
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
    }

    public void switchToAccount(int account, boolean removeAll) {
        if (account == UserConfig.selectedAccount) {
            return;
        }

        ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        UserConfig.selectedAccount = account;
        UserConfig.getInstance(0).saveConfig(false);

        checkCurrentAccount();
        if (AndroidUtilities.isTablet()) {
            layersActionBarLayout.removeAllFragments();
            rightActionBarLayout.removeAllFragments();
            if (!tabletFullSize) {
                shadowTabletSide.setVisibility(View.VISIBLE);
                if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                }
                rightActionBarLayout.setVisibility(View.GONE);
            }
            layersActionBarLayout.setVisibility(View.GONE);
        }
        if (removeAll) {
            actionBarLayout.removeAllFragments();
        } else {
            actionBarLayout.removeFragmentFromStack(0);
        }
        DialogsActivity dialogsActivity = new DialogsActivity(null);
        dialogsActivity.setSideMenu(sideMenu);
        actionBarLayout.addFragmentToStack(dialogsActivity, 0);
        drawerLayoutContainer.setAllowOpenDrawer(true, false);
        actionBarLayout.showLastFragment();
        if (AndroidUtilities.isTablet()) {
            layersActionBarLayout.showLastFragment();
            rightActionBarLayout.showLastFragment();
        }
        if (!ApplicationLoader.mainInterfacePaused) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        }
        if (UserConfig.getInstance(account).unacceptedTermsOfService != null) {
            showTosActivity(account, UserConfig.getInstance(account).unacceptedTermsOfService);
        }
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
            for (BaseFragment fragment : actionBarLayout.fragmentsStack) {
                fragment.onFragmentDestroy();
            }
            actionBarLayout.fragmentsStack.clear();
            if (AndroidUtilities.isTablet()) {
                for (BaseFragment fragment : layersActionBarLayout.fragmentsStack) {
                    fragment.onFragmentDestroy();
                }
                layersActionBarLayout.fragmentsStack.clear();
                for (BaseFragment fragment : rightActionBarLayout.fragmentsStack) {
                    fragment.onFragmentDestroy();
                }
                rightActionBarLayout.fragmentsStack.clear();
            }
            Intent intent2 = new Intent(this, IntroActivity.class);
            startActivity(intent2);
            onFinish();
            finish();
        }
    }

    public int getMainFragmentsCount() {
        return mainFragmentsStack.size();
    }

    private void checkCurrentAccount() {
        if (currentAccount != UserConfig.selectedAccount) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openArticle);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.hasNewContactsToImport);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowPlayServicesAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidFailToLoad);
        }
        currentAccount = UserConfig.selectedAccount;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.openArticle);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.hasNewContactsToImport);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.needShowPlayServicesAlert);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidFailToLoad);
        updateCurrentConnectionState(currentAccount);
    }

    private void checkLayout() {
        if (!AndroidUtilities.isTablet() || rightActionBarLayout == null) {
            return;
        }

        if (!AndroidUtilities.isInMultiwindow && (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            tabletFullSize = false;
            if (actionBarLayout.fragmentsStack.size() >= 2) {
                for (int a = 1; a < actionBarLayout.fragmentsStack.size(); a++) {
                    BaseFragment chatFragment = actionBarLayout.fragmentsStack.get(a);
                    if (chatFragment instanceof ChatActivity) {
                        ((ChatActivity) chatFragment).setIgnoreAttachOnPause(true);
                    }
                    chatFragment.onPause();
                    actionBarLayout.fragmentsStack.remove(a);
                    rightActionBarLayout.fragmentsStack.add(chatFragment);
                    a--;
                }
                if (passcodeView.getVisibility() != View.VISIBLE) {
                    actionBarLayout.showLastFragment();
                    rightActionBarLayout.showLastFragment();
                }
            }
            rightActionBarLayout.setVisibility(rightActionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
            backgroundTablet.setVisibility(rightActionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
            shadowTabletSide.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            tabletFullSize = true;
            if (!rightActionBarLayout.fragmentsStack.isEmpty()) {
                for (int a = 0; a < rightActionBarLayout.fragmentsStack.size(); a++) {
                    BaseFragment chatFragment = rightActionBarLayout.fragmentsStack.get(a);
                    if (chatFragment instanceof ChatActivity) {
                        ((ChatActivity) chatFragment).setIgnoreAttachOnPause(true);
                    }
                    chatFragment.onPause();
                    rightActionBarLayout.fragmentsStack.remove(a);
                    actionBarLayout.fragmentsStack.add(chatFragment);
                    a--;
                }
                if (passcodeView.getVisibility() != View.VISIBLE) {
                    actionBarLayout.showLastFragment();
                }
            }
            shadowTabletSide.setVisibility(View.GONE);
            rightActionBarLayout.setVisibility(View.GONE);
            backgroundTablet.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
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
            drawerLayoutContainer.addView(termsOfServiceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            termsOfServiceView.setDelegate(new TermsOfServiceView.TermsOfServiceViewDelegate() {
                @Override
                public void onAcceptTerms(int account) {
                    UserConfig.getInstance(account).unacceptedTermsOfService = null;
                    UserConfig.getInstance(account).saveConfig(false);
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    termsOfServiceView.setVisibility(View.GONE);
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
    }

    private void showPasscodeActivity() {
        if (passcodeView == null) {
            return;
        }
        SharedConfig.appLocked = true;
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(false, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        passcodeView.onShow();
        SharedConfig.isWaitingForPasscodeEnter = true;
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        passcodeView.setDelegate(() -> {
            SharedConfig.isWaitingForPasscodeEnter = false;
            if (passcodeSaveIntent != null) {
                handleIntent(passcodeSaveIntent, passcodeSaveIntentIsNew, passcodeSaveIntentIsRestore, true);
                passcodeSaveIntent = null;
            }
            drawerLayoutContainer.setAllowOpenDrawer(true, false);
            actionBarLayout.setVisibility(View.VISIBLE);
            actionBarLayout.showLastFragment();
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.showLastFragment();
                rightActionBarLayout.showLastFragment();
                if (layersActionBarLayout.getVisibility() == View.INVISIBLE) {
                    layersActionBarLayout.setVisibility(View.VISIBLE);
                }
                rightActionBarLayout.setVisibility(View.VISIBLE);
            }
        });
        actionBarLayout.setVisibility(View.INVISIBLE);
        if (AndroidUtilities.isTablet()) {
            if (layersActionBarLayout.getVisibility() == View.VISIBLE) {
                layersActionBarLayout.setVisibility(View.INVISIBLE);
            }
            rightActionBarLayout.setVisibility(View.INVISIBLE);
        }
    }

    private boolean handleIntent(Intent intent, boolean isNew, boolean restore, boolean fromPassword) {
        if (AndroidUtilities.handleProxyIntent(this, intent)) {
            actionBarLayout.showLastFragment();
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.showLastFragment();
                rightActionBarLayout.showLastFragment();
            }
            return true;
        }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) {
                PhotoViewer.getInstance().closePhoto(false, true);
            }
        }
        int flags = intent.getFlags();
        final int[] intentAccount = new int[]{intent.getIntExtra("currentAccount", UserConfig.selectedAccount)};
        switchToAccount(intentAccount[0], true);
        if (!fromPassword && (AndroidUtilities.needShowPasscode(true) || SharedConfig.isWaitingForPasscodeEnter)) {
            showPasscodeActivity();
            passcodeSaveIntent = intent;
            passcodeSaveIntentIsNew = isNew;
            passcodeSaveIntentIsRestore = restore;
            UserConfig.getInstance(currentAccount).saveConfig(false);
        } else {
            boolean pushOpened = false;

            int push_user_id = 0;
            int push_chat_id = 0;
            int push_enc_id = 0;
            int push_msg_id = 0;
            int open_settings = 0;
            int open_new_dialog = 0;
            long dialogId = 0;
            if (SharedConfig.directShare && intent != null && intent.getExtras() != null) {
                dialogId = intent.getExtras().getLong("dialogId", 0);
                long hash = intent.getExtras().getLong("hash", 0);
                if (hash != SharedConfig.directShareHash) {
                    dialogId = 0;
                }
            }
            boolean showDialogsList = false;
            boolean showPlayer = false;
            boolean showLocations = false;

            photoPathsArray = null;
            videoPath = null;
            sendingText = null;
            documentsPathsArray = null;
            documentsOriginalPathsArray = null;
            documentsMimeType = null;
            documentsUrisArray = null;
            contactsToSend = null;
            contactsToSendUri = null;

            if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
                if (intent != null && intent.getAction() != null && !restore) {
                    if (Intent.ACTION_SEND.equals(intent.getAction())) {
                        boolean error = false;
                        String type = intent.getType();
                        if (type != null && type.equals(ContactsContract.Contacts.CONTENT_VCARD_TYPE)) {
                            try {
                                Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                                if (uri != null) {
                                    contactsToSend = AndroidUtilities.loadVCardFromStream(uri, currentAccount, false, null, null);
                                    contactsToSendUri = uri;
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
                                if (!error) {
                                    if (uri != null && (type != null && type.startsWith("image/") || uri.toString().toLowerCase().endsWith(".jpg"))) {
                                        if (photoPathsArray == null) {
                                            photoPathsArray = new ArrayList<>();
                                        }
                                        SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                        info.uri = uri;
                                        photoPathsArray.add(info);
                                    } else {
                                        path = AndroidUtilities.getPath(uri);
                                        if (path != null) {
                                            if (path.startsWith("file:")) {
                                                path = path.replace("file://", "");
                                            }
                                            if (type != null && type.startsWith("video/")) {
                                                videoPath = path;
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
                            } else if (sendingText == null) {
                                error = true;
                            }
                        }
                        if (error) {
                            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
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
                            String group = null;
                            String sticker = null;
                            HashMap<String, String> auth = null;
                            String unsupportedUrl = null;
                            String botUser = null;
                            String botChat = null;
                            String message = null;
                            String phone = null;
                            String game = null;
                            String phoneHash = null;
                            String lang = null;
                            String theme = null;
                            String code = null;
                            TLRPC.TL_wallPaper wallPaper = null;
                            Integer messageId = null;
                            Integer channelId = null;
                            boolean hasUrl = false;
                            String scheme = data.getScheme();
                            if (scheme != null) {
                                if ((scheme.equals("http") || scheme.equals("https"))) {
                                    String host = data.getHost().toLowerCase();
                                    if (host.equals("telegram.me") || host.equals("t.me") || host.equals("telegram.dog")) {
                                        String path = data.getPath();
                                        if (path != null && path.length() > 1) {
                                            path = path.substring(1);
                                            if (path.startsWith("bg/")) {
                                                wallPaper = new TLRPC.TL_wallPaper();
                                                wallPaper.settings = new TLRPC.TL_wallPaperSettings();
                                                wallPaper.slug = path.replace("bg/", "");
                                                if (wallPaper.slug != null && wallPaper.slug.length() == 6) {
                                                    try {
                                                        wallPaper.settings.background_color = Integer.parseInt(wallPaper.slug, 16) | 0xff000000;
                                                    } catch (Exception ignore) {

                                                    }
                                                    wallPaper.slug = null;
                                                } else {
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
                                                            wallPaper.settings.background_color = Integer.parseInt(bgColor, 16) | 0xff000000;
                                                        } else {
                                                            wallPaper.settings.background_color = 0xffffffff;
                                                        }
                                                    } catch (Exception ignore) {

                                                    }
                                                }
                                            } else if (path.startsWith("login/")) {
                                                code = path.replace("login/", "");
                                            } else if (path.startsWith("joinchat/")) {
                                                group = path.replace("joinchat/", "");
                                            } else if (path.startsWith("addstickers/")) {
                                                sticker = path.replace("addstickers/", "");
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
                                            } else if (path.startsWith("c/")) {
                                                List<String> segments = data.getPathSegments();
                                                if (segments.size() == 3) {
                                                    channelId = Utilities.parseInt(segments.get(1));
                                                    messageId = Utilities.parseInt(segments.get(2));
                                                    if (messageId == 0 || channelId == 0) {
                                                        messageId = null;
                                                        channelId = null;
                                                    }
                                                }
                                            } else if (path.length() >= 1) {
                                                ArrayList<String> segments = new ArrayList<>(data.getPathSegments());
                                                if (segments.size() > 0 && segments.get(0).equals("s")) {
                                                    segments.remove(0);
                                                }
                                                if (segments.size() > 0) {
                                                    username = segments.get(0);
                                                    if (segments.size() > 1) {
                                                        messageId = Utilities.parseInt(segments.get(1));
                                                        if (messageId == 0) {
                                                            messageId = null;
                                                        }
                                                    }
                                                }
                                                botUser = data.getQueryParameter("start");
                                                botChat = data.getQueryParameter("startgroup");
                                                game = data.getQueryParameter("game");
                                            }
                                        }
                                    }
                                } else if (scheme.equals("tg")) {
                                    String url = data.toString();
                                    if (url.startsWith("tg:resolve") || url.startsWith("tg://resolve")) {
                                        url = url.replace("tg:resolve", "tg://telegram.org").replace("tg://resolve", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        username = data.getQueryParameter("domain");
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
                                            game = data.getQueryParameter("game");
                                            messageId = Utilities.parseInt(data.getQueryParameter("post"));
                                            if (messageId == 0) {
                                                messageId = null;
                                            }
                                        }
                                    } else if (url.startsWith("tg:privatepost") || url.startsWith("tg://privatepost")) {
                                        url = url.replace("tg:privatepost", "tg://telegram.org").replace("tg://privatepost", "tg://telegram.org");
                                        data = Uri.parse(url);
                                        messageId = Utilities.parseInt(data.getQueryParameter("post"));
                                        channelId = Utilities.parseInt(data.getQueryParameter("channel"));
                                        if (messageId == 0 || channelId == 0) {
                                            messageId = null;
                                            channelId = null;
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
                                        if (wallPaper.slug != null && wallPaper.slug.length() == 6) {
                                            try {
                                                wallPaper.settings.background_color = Integer.parseInt(wallPaper.slug, 16) | 0xff000000;
                                            } catch (Exception ignore) {

                                            }
                                            wallPaper.slug = null;
                                        } else {
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
                                                    wallPaper.settings.background_color = Integer.parseInt(bgColor, 16) | 0xff000000;
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

                                        code = data.getQueryParameter("code");
                                    } else if (url.startsWith("tg:openmessage") || url.startsWith("tg://openmessage")) {
                                        url = url.replace("tg:openmessage", "tg://telegram.org").replace("tg://openmessage", "tg://telegram.org");
                                        data = Uri.parse(url);

                                        String userID = data.getQueryParameter("user_id");
                                        String chatID = data.getQueryParameter("chat_id");
                                        String msgID = data.getQueryParameter("message_id");
                                        if (userID != null) {
                                            try {
                                                push_user_id = Integer.parseInt(userID);
                                            } catch (NumberFormatException ignore) {
                                            }
                                        } else if (chatID != null) {
                                            try {
                                                push_chat_id = Integer.parseInt(chatID);
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
                                    } else {
                                        unsupportedUrl = url.replace("tg://", "").replace("tg:", "");
                                        int index;
                                        if ((index = unsupportedUrl.indexOf('?')) >= 0) {
                                            unsupportedUrl = unsupportedUrl.substring(0, index);
                                        }
                                    }
                                }
                            }
                            if (code != null || UserConfig.getInstance(currentAccount).isClientActivated()) {
                                if (phone != null || phoneHash != null) {
                                    final Bundle args = new Bundle();
                                    args.putString("phone", phone);
                                    args.putString("hash", phoneHash);
                                    AndroidUtilities.runOnUIThread(() -> presentFragment(new CancelAccountDeletionActivity(args)));
                                } else if (username != null || group != null || sticker != null || message != null || game != null || auth != null || unsupportedUrl != null || lang != null || code != null || wallPaper != null || channelId != null || theme != null) {
                                    if (message != null && message.startsWith("@")) {
                                        message = " " + message;
                                    }
                                    runLinkRequest(intentAccount[0], username, group, sticker, botUser, botChat, message, hasUrl, messageId, channelId, game, auth, lang, unsupportedUrl, code, wallPaper, theme, 0);
                                } else {
                                    try (Cursor cursor = getContentResolver().query(intent.getData(), null, null, null, null)) {
                                        if (cursor != null) {
                                            if (cursor.moveToFirst()) {
                                                int accountId = Utilities.parseInt(cursor.getString(cursor.getColumnIndex("account_name")));
                                                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                                    if (UserConfig.getInstance(a).getClientUserId() == accountId) {
                                                        intentAccount[0] = a;
                                                        switchToAccount(intentAccount[0], true);
                                                        break;
                                                    }
                                                }
                                                int userId = cursor.getInt(cursor.getColumnIndex("DATA4"));
                                                NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                                                push_user_id = userId;
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
                        int chatId = intent.getIntExtra("chatId", 0);
                        int userId = intent.getIntExtra("userId", 0);
                        int encId = intent.getIntExtra("encId", 0);
                        if (chatId != 0) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_chat_id = chatId;
                        } else if (userId != 0) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_user_id = userId;
                        } else if (encId != 0) {
                            NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                            push_enc_id = encId;
                        } else {
                            showDialogsList = true;
                        }
                    } else if (intent.getAction().equals("com.tmessages.openplayer")) {
                        showPlayer = true;
                    } else if (intent.getAction().equals("org.tmessages.openlocations")) {
                        showLocations = true;
                    }
                }
            }

            if (UserConfig.getInstance(currentAccount).isClientActivated()) {
                if (push_user_id != 0) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", push_user_id);
                    if (push_msg_id != 0)
                        args.putInt("message_id", push_msg_id);
                    if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount[0]).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                        ChatActivity fragment = new ChatActivity(args);
                        if (actionBarLayout.presentFragment(fragment, false, true, true, false)) {
                            pushOpened = true;
                        }
                    }
                } else if (push_chat_id != 0) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", push_chat_id);
                    if (push_msg_id != 0)
                        args.putInt("message_id", push_msg_id);
                    if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount[0]).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                        ChatActivity fragment = new ChatActivity(args);
                        if (actionBarLayout.presentFragment(fragment, false, true, true, false)) {
                            pushOpened = true;
                        }
                    }
                } else if (push_enc_id != 0) {
                    Bundle args = new Bundle();
                    args.putInt("enc_id", push_enc_id);
                    ChatActivity fragment = new ChatActivity(args);
                    if (actionBarLayout.presentFragment(fragment, false, true, true, false)) {
                        pushOpened = true;
                    }
                } else if (showDialogsList) {
                    if (!AndroidUtilities.isTablet()) {
                        actionBarLayout.removeAllFragments();
                    } else {
                        if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                            for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                                layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                                a--;
                            }
                            layersActionBarLayout.closeLastFragment(false);
                        }
                    }
                    pushOpened = false;
                    isNew = false;
                } else if (showPlayer) {
                    if (!actionBarLayout.fragmentsStack.isEmpty()) {
                        BaseFragment fragment = actionBarLayout.fragmentsStack.get(0);
                        fragment.showDialog(new AudioPlayerAlert(this));
                    }
                    pushOpened = false;
                } else if (showLocations) {
                    if (!actionBarLayout.fragmentsStack.isEmpty()) {
                        BaseFragment fragment = actionBarLayout.fragmentsStack.get(0);
                        fragment.showDialog(new SharingLocationsAlert(this, info -> {
                            intentAccount[0] = info.messageObject.currentAccount;
                            switchToAccount(intentAccount[0], true);

                            LocationActivity locationActivity = new LocationActivity(2);
                            locationActivity.setMessageObject(info.messageObject);
                            final long dialog_id = info.messageObject.getDialogId();
                            locationActivity.setDelegate((location, live, notify, scheduleDate) -> SendMessagesHelper.getInstance(intentAccount[0]).sendMessage(location, dialog_id, null, null, null, notify, scheduleDate));
                            presentFragment(locationActivity);
                        }));
                    }
                    pushOpened = false;
                } else if (videoPath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null || documentsUrisArray != null) {
                    if (!AndroidUtilities.isTablet()) {
                        NotificationCenter.getInstance(intentAccount[0]).postNotificationName(NotificationCenter.closeChats);
                    }
                    if (dialogId == 0) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putInt("dialogsType", 3);
                        args.putBoolean("allowSwitchAccount", true);
                        if (contactsToSend != null) {
                            if (contactsToSend.size() != 1) {
                                args.putString("selectAlertString", LocaleController.getString("SendContactTo", R.string.SendMessagesTo));
                                args.putString("selectAlertStringGroup", LocaleController.getString("SendContactToGroup", R.string.SendContactToGroup));
                            }
                        } else {
                            args.putString("selectAlertString", LocaleController.getString("SendMessagesTo", R.string.SendMessagesTo));
                            args.putString("selectAlertStringGroup", LocaleController.getString("SendMessagesToGroup", R.string.SendMessagesToGroup));
                        }
                        DialogsActivity fragment = new DialogsActivity(args);
                        fragment.setDelegate(this);
                        boolean removeLast;
                        if (AndroidUtilities.isTablet()) {
                            removeLast = layersActionBarLayout.fragmentsStack.size() > 0 && layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1) instanceof DialogsActivity;
                        } else {
                            removeLast = actionBarLayout.fragmentsStack.size() > 1 && actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1) instanceof DialogsActivity;
                        }
                        actionBarLayout.presentFragment(fragment, removeLast, true, true, false);
                        pushOpened = true;
                        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
                            SecretMediaViewer.getInstance().closePhoto(false, false);
                        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                            PhotoViewer.getInstance().closePhoto(false, true);
                        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
                            ArticleViewer.getInstance().close(false, true);
                        }

                        drawerLayoutContainer.setAllowOpenDrawer(false, false);
                        if (AndroidUtilities.isTablet()) {
                            actionBarLayout.showLastFragment();
                            rightActionBarLayout.showLastFragment();
                        } else {
                            drawerLayoutContainer.setAllowOpenDrawer(true, false);
                        }
                    } else {
                        ArrayList<Long> dids = new ArrayList<>();
                        dids.add(dialogId);
                        didSelectDialogs(null, dids, null, false);
                    }
                } else if (open_settings != 0) {
                    actionBarLayout.presentFragment(new SettingsActivity(), false, true, true, false);
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.showLastFragment();
                        rightActionBarLayout.showLastFragment();
                        drawerLayoutContainer.setAllowOpenDrawer(false, false);
                    } else {
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                    pushOpened = true;
                } else if (open_new_dialog != 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("destroyAfterSelect", true);
                    actionBarLayout.presentFragment(new ContactsActivity(args), false, true, true, false);
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.showLastFragment();
                        rightActionBarLayout.showLastFragment();
                        drawerLayoutContainer.setAllowOpenDrawer(false, false);
                    } else {
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                    pushOpened = true;
                }
            }

            if (!pushOpened && !isNew) {
                if (AndroidUtilities.isTablet()) {
                    if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                        if (layersActionBarLayout.fragmentsStack.isEmpty()) {
                            layersActionBarLayout.addFragmentToStack(new LoginActivity());
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                        }
                    } else {
                        if (actionBarLayout.fragmentsStack.isEmpty()) {
                            DialogsActivity dialogsActivity = new DialogsActivity(null);
                            dialogsActivity.setSideMenu(sideMenu);
                            actionBarLayout.addFragmentToStack(dialogsActivity);
                            drawerLayoutContainer.setAllowOpenDrawer(true, false);
                        }
                    }
                } else {
                    if (actionBarLayout.fragmentsStack.isEmpty()) {
                        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                            actionBarLayout.addFragmentToStack(new LoginActivity());
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                        } else {
                            DialogsActivity dialogsActivity = new DialogsActivity(null);
                            dialogsActivity.setSideMenu(sideMenu);
                            actionBarLayout.addFragmentToStack(dialogsActivity);
                            drawerLayoutContainer.setAllowOpenDrawer(true, false);
                        }
                    }
                }
                actionBarLayout.showLastFragment();
                if (AndroidUtilities.isTablet()) {
                    layersActionBarLayout.showLastFragment();
                    rightActionBarLayout.showLastFragment();
                }
            }

            intent.setAction(null);
            return pushOpened;
        }
        return false;
    }

    private void runLinkRequest(final int intentAccount,
                                final String username,
                                final String group,
                                final String sticker,
                                final String botUser,
                                final String botChat,
                                final String message,
                                final boolean hasUrl,
                                final Integer messageId,
                                final Integer channelId,
                                final String game,
                                final HashMap<String, String> auth,
                                final String lang,
                                final String unsupportedUrl,
                                final String code,
                                final TLRPC.TL_wallPaper wallPaper,
                                final String theme,
                                final int state) {
        if (state == 0 && UserConfig.getActivatedAccountsCount() >= 2 && auth != null) {
            AlertsCreator.createAccountSelectDialog(this, account -> {
                if (account != intentAccount) {
                    switchToAccount(account, true);
                }
                runLinkRequest(account, username, group, sticker, botUser, botChat, message, hasUrl, messageId, channelId, game, auth, lang, unsupportedUrl, code, wallPaper, theme, 1);
            }).show();
            return;
        } else if (code != null) {
            if (NotificationCenter.getGlobalInstance().hasObservers(NotificationCenter.didReceiveSmsCode)) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReceiveSmsCode, code);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("OtherLoginCode", R.string.OtherLoginCode, code)));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                showAlertDialog(builder);
            }
            return;
        }
        final AlertDialog progressDialog = new AlertDialog(this, 3);
        final int[] requestId = new int[]{0};
        Runnable cancelRunnable = null;

        if (username != null) {
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!LaunchActivity.this.isFinishing()) {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    final TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                    if (error == null && actionBarLayout != null && (game == null || game != null && !res.users.isEmpty())) {
                        MessagesController.getInstance(intentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(intentAccount).putChats(res.chats, false);
                        MessagesStorage.getInstance(intentAccount).putUsersAndChats(res.users, res.chats, false, true);
                        if (game != null) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlySelect", true);
                            args.putBoolean("cantSendToChannels", true);
                            args.putInt("dialogsType", 1);
                            args.putString("selectAlertString", LocaleController.getString("SendGameTo", R.string.SendGameTo));
                            args.putString("selectAlertStringGroup", LocaleController.getString("SendGameToGroup", R.string.SendGameToGroup));
                            DialogsActivity fragment = new DialogsActivity(args);
                            fragment.setDelegate((fragment1, dids, message1, param) -> {
                                long did = dids.get(0);
                                TLRPC.TL_inputMediaGame inputMediaGame = new TLRPC.TL_inputMediaGame();
                                inputMediaGame.id = new TLRPC.TL_inputGameShortName();
                                inputMediaGame.id.short_name = game;
                                inputMediaGame.id.bot_id = MessagesController.getInstance(intentAccount).getInputUser(res.users.get(0));
                                SendMessagesHelper.getInstance(intentAccount).sendGame(MessagesController.getInstance(intentAccount).getInputPeer((int) did), inputMediaGame, 0, 0);

                                Bundle args1 = new Bundle();
                                args1.putBoolean("scrollToTopOnResume", true);
                                int lower_part = (int) did;
                                int high_id = (int) (did >> 32);
                                if (lower_part != 0) {
                                    if (lower_part > 0) {
                                        args1.putInt("user_id", lower_part);
                                    } else if (lower_part < 0) {
                                        args1.putInt("chat_id", -lower_part);
                                    }
                                } else {
                                    args1.putInt("enc_id", high_id);
                                }
                                if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args1, fragment1)) {
                                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                    actionBarLayout.presentFragment(new ChatActivity(args1), true, false, true, false);
                                }
                            });
                            boolean removeLast;
                            if (AndroidUtilities.isTablet()) {
                                removeLast = layersActionBarLayout.fragmentsStack.size() > 0 && layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1) instanceof DialogsActivity;
                            } else {
                                removeLast = actionBarLayout.fragmentsStack.size() > 1 && actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1) instanceof DialogsActivity;
                            }
                            actionBarLayout.presentFragment(fragment, removeLast, true, true, false);
                            if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
                                SecretMediaViewer.getInstance().closePhoto(false, false);
                            } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                                PhotoViewer.getInstance().closePhoto(false, true);
                            } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
                                ArticleViewer.getInstance().close(false, true);
                            }
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                            if (AndroidUtilities.isTablet()) {
                                actionBarLayout.showLastFragment();
                                rightActionBarLayout.showLastFragment();
                            } else {
                                drawerLayoutContainer.setAllowOpenDrawer(true, false);
                            }
                        } else if (botChat != null) {
                            final TLRPC.User user = !res.users.isEmpty() ? res.users.get(0) : null;
                            if (user == null || user.bot && user.bot_nochats) {
                                try {
                                    Toast.makeText(LaunchActivity.this, LocaleController.getString("BotCantJoinGroups", R.string.BotCantJoinGroups), Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                return;
                            }
                            Bundle args = new Bundle();
                            args.putBoolean("onlySelect", true);
                            args.putInt("dialogsType", 2);
                            args.putString("addToGroupAlertString", LocaleController.formatString("AddToTheGroupTitle", R.string.AddToTheGroupTitle, UserObject.getUserName(user), "%1$s"));
                            DialogsActivity fragment = new DialogsActivity(args);
                            fragment.setDelegate((fragment12, dids, message1, param) -> {
                                long did = dids.get(0);
                                Bundle args12 = new Bundle();
                                args12.putBoolean("scrollToTopOnResume", true);
                                args12.putInt("chat_id", -(int) did);
                                if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args12, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                    MessagesController.getInstance(intentAccount).addUserToChat(-(int) did, user, null, 0, botChat, null, null);
                                    actionBarLayout.presentFragment(new ChatActivity(args12), true, false, true, false);
                                }
                            });
                            presentFragment(fragment);
                        } else {
                            long dialog_id;
                            boolean isBot = false;
                            Bundle args = new Bundle();
                            if (!res.chats.isEmpty()) {
                                args.putInt("chat_id", res.chats.get(0).id);
                                dialog_id = -res.chats.get(0).id;
                            } else {
                                args.putInt("user_id", res.users.get(0).id);
                                dialog_id = res.users.get(0).id;
                            }
                            if (botUser != null && res.users.size() > 0 && res.users.get(0).bot) {
                                args.putString("botUser", botUser);
                                isBot = true;
                            }
                            if (messageId != null) {
                                args.putInt("message_id", messageId);
                            }
                            BaseFragment lastFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
                            if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
                                if (isBot && lastFragment instanceof ChatActivity && ((ChatActivity) lastFragment).getDialogId() == dialog_id) {
                                    ((ChatActivity) lastFragment).setBotUser(botUser);
                                } else {
                                    ChatActivity fragment = new ChatActivity(args);
                                    actionBarLayout.presentFragment(fragment);
                                }
                            }
                        }
                    } else {
                        try {
                            Toast.makeText(LaunchActivity.this, LocaleController.getString("NoUsernameFound", R.string.NoUsernameFound), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }));
        } else if (group != null) {
            if (state == 0) {
                final TLRPC.TL_messages_checkChatInvite req = new TLRPC.TL_messages_checkChatInvite();
                req.hash = group;
                requestId[0] = ConnectionsManager.getInstance(intentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!LaunchActivity.this.isFinishing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (error == null && actionBarLayout != null) {
                            TLRPC.ChatInvite invite = (TLRPC.ChatInvite) response;
                            if (invite.chat != null && (!ChatObject.isLeftFromChat(invite.chat) || !invite.chat.kicked && !TextUtils.isEmpty(invite.chat.username))) {
                                MessagesController.getInstance(intentAccount).putChat(invite.chat, false);
                                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                                chats.add(invite.chat);
                                MessagesStorage.getInstance(intentAccount).putUsersAndChats(null, chats, false, true);
                                Bundle args = new Bundle();
                                args.putInt("chat_id", invite.chat.id);
                                if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                    ChatActivity fragment = new ChatActivity(args);
                                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                    actionBarLayout.presentFragment(fragment, false, true, true, false);
                                }
                            } else {
                                if ((invite.chat == null && (!invite.channel || invite.megagroup) || invite.chat != null && (!ChatObject.isChannel(invite.chat) || invite.chat.megagroup)) && !mainFragmentsStack.isEmpty()) {
                                    BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                                    fragment.showDialog(new JoinGroupAlert(LaunchActivity.this, invite, group, fragment));
                                } else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setMessage(LocaleController.formatString("ChannelJoinTo", R.string.ChannelJoinTo, invite.chat != null ? invite.chat.title : invite.title));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> runLinkRequest(intentAccount, username, group, sticker, botUser, botChat, message, hasUrl, messageId, channelId, game, auth, lang, unsupportedUrl, code, wallPaper, theme, 1));
                                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                    showAlertDialog(builder);
                                }
                            }
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            if (error.text.startsWith("FLOOD_WAIT")) {
                                builder.setMessage(LocaleController.getString("FloodWait", R.string.FloodWait));
                            } else {
                                builder.setMessage(LocaleController.getString("JoinToGroupErrorNotExist", R.string.JoinToGroupErrorNotExist));
                            }
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            showAlertDialog(builder);
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
                                progressDialog.dismiss();
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
                                        args.putInt("chat_id", chat.id);
                                        if (mainFragmentsStack.isEmpty() || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                            ChatActivity fragment = new ChatActivity(args);
                                            NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                                            actionBarLayout.presentFragment(fragment, false, true, true, false);
                                        }
                                    }
                                }
                            } else {
                                AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                if (error.text.startsWith("FLOOD_WAIT")) {
                                    builder.setMessage(LocaleController.getString("FloodWait", R.string.FloodWait));
                                } else if (error.text.equals("USERS_TOO_MUCH")) {
                                    builder.setMessage(LocaleController.getString("JoinToGroupErrorFull", R.string.JoinToGroupErrorFull));
                                } else {
                                    builder.setMessage(LocaleController.getString("JoinToGroupErrorNotExist", R.string.JoinToGroupErrorNotExist));
                                }
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                showAlertDialog(builder);
                            }
                        }
                    });
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        } else if (sticker != null) {
            if (!mainFragmentsStack.isEmpty()) {
                TLRPC.TL_inputStickerSetShortName stickerset = new TLRPC.TL_inputStickerSetShortName();
                stickerset.short_name = sticker;
                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                fragment.showDialog(new StickersAlert(LaunchActivity.this, fragment, stickerset, null, null));
            }
            return;
        } else if (message != null) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment13, dids, m, param) -> {
                long did = dids.get(0);
                Bundle args13 = new Bundle();
                args13.putBoolean("scrollToTopOnResume", true);
                args13.putBoolean("hasUrl", hasUrl);
                int lower_part = (int) did;
                int high_id = (int) (did >> 32);
                if (lower_part != 0) {
                    if (lower_part > 0) {
                        args13.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args13.putInt("chat_id", -lower_part);
                    }
                } else {
                    args13.putInt("enc_id", high_id);
                }
                if (MessagesController.getInstance(intentAccount).checkCanOpenChat(args13, fragment13)) {
                    NotificationCenter.getInstance(intentAccount).postNotificationName(NotificationCenter.closeChats);
                    MediaDataController.getInstance(intentAccount).saveDraft(did, message, null, null, false);
                    actionBarLayout.presentFragment(new ChatActivity(args13), true, false, true, false);
                }
            });
            presentFragment(fragment, false, true);
        } else if (auth != null) {
            final int bot_id = Utilities.parseInt(auth.get("bot_id"));
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
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (response1 != null) {
                            TLRPC.TL_account_password accountPassword = (TLRPC.TL_account_password) response1;
                            MessagesController.getInstance(intentAccount).putUsers(authorizationForm.users, false);
                            presentFragment(new PassportActivity(PassportActivity.TYPE_PASSWORD, req.bot_id, req.scope, req.public_key, payload, nonce, callbackUrl, authorizationForm, accountPassword));
                        }
                    }));
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                            if ("APP_VERSION_OUTDATED".equals(error.text)) {
                                AlertsCreator.showUpdateAppAlert(LaunchActivity.this, LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                            } else {
                                showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text));
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
                    progressDialog.dismiss();
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
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response instanceof TLRPC.TL_langPackLanguage) {
                    TLRPC.TL_langPackLanguage res = (TLRPC.TL_langPackLanguage) response;
                    showAlertDialog(AlertsCreator.createLanguageAlert(LaunchActivity.this, res));
                } else if (error != null) {
                    if ("LANG_CODE_NOT_SUPPORTED".equals(error.text)) {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("LanguageUnsupportedError", R.string.LanguageUnsupportedError)));
                    } else {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text));
                    }
                }
            }));
        } else if (wallPaper != null) {
            boolean ok = false;
            if (TextUtils.isEmpty(wallPaper.slug)) {
                try {
                    WallpapersListActivity.ColorWallpaper colorWallpaper = new WallpapersListActivity.ColorWallpaper(-100, wallPaper.settings.background_color);
                    WallpaperActivity wallpaperActivity = new WallpaperActivity(colorWallpaper, null);
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
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (response instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_wallPaper res = (TLRPC.TL_wallPaper) response;
                        Object object;
                        if (res.pattern) {
                            WallpapersListActivity.ColorWallpaper colorWallpaper = new WallpapersListActivity.ColorWallpaper(-1, wallPaper.settings.background_color, res.id, wallPaper.settings.intensity / 100.0f, wallPaper.settings.motion, null);
                            colorWallpaper.pattern = res;
                            object = colorWallpaper;
                        } else {
                            object = res;
                        }
                        WallpaperActivity wallpaperActivity = new WallpaperActivity(object, null);
                        wallpaperActivity.setInitialModes(wallPaper.settings.blur, wallPaper.settings.motion);
                        presentFragment(wallpaperActivity);
                    } else {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text));
                    }
                }));
            }
        } else if (theme != null) {
            cancelRunnable = () -> {
                loadingThemeFileName = null;
                loadingThemeWallpaperName = null;
                loadingThemeInfo = null;
                loadingThemeProgressDialog = null;
                loadingTheme = null;
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
                    if (t.document != null) {
                        loadingTheme = t;
                        loadingThemeFileName = FileLoader.getAttachFileName(loadingTheme.document);
                        loadingThemeProgressDialog = progressDialog;
                        FileLoader.getInstance(currentAccount).loadFile(loadingTheme.document, t, 1, 1);
                        notFound = 0;
                    } else {
                        notFound = 1;
                    }
                } else if (error != null && "THEME_FORMAT_INVALID".equals(error.text)) {
                    notFound = 1;
                }
                if (notFound != 0) {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (notFound == 1) {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("Theme", R.string.Theme), LocaleController.getString("ThemeNotSupported", R.string.ThemeNotSupported)));
                    } else {
                        showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("Theme", R.string.Theme), LocaleController.getString("ThemeNotFound", R.string.ThemeNotFound)));
                    }
                }
            }));
        } else if (channelId != null && messageId != null) {
            Bundle args = new Bundle();
            args.putInt("chat_id", channelId);
            args.putInt("message_id", messageId);
            BaseFragment lastFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
            if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!actionBarLayout.presentFragment(new ChatActivity(args))) {
                        TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
                        TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
                        inputChannel.channel_id = channelId;
                        req.id.add(inputChannel);
                        requestId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
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
                                    if (lastFragment == null || MessagesController.getInstance(intentAccount).checkCanOpenChat(args, lastFragment)) {
                                        actionBarLayout.presentFragment(new ChatActivity(args));
                                    }
                                }
                            }
                            if (notFound) {
                                showAlertDialog(AlertsCreator.createSimpleAlert(LaunchActivity.this, LocaleController.getString("LinkNotFound", R.string.LinkNotFound)));
                            }
                        }));
                    }
                });
            }
        }

        if (requestId[0] != 0) {
            final Runnable cancelRunnableFinal = cancelRunnable;
            progressDialog.setOnCancelListener(dialog -> {
                ConnectionsManager.getInstance(intentAccount).cancelRequest(requestId[0], true);
                if (cancelRunnableFinal != null) {
                    cancelRunnableFinal.run();
                }
            });
            try {
                progressDialog.show();
            } catch (Exception ignore) {

            }
        }
    }

    public void checkAppUpdate(boolean force) {
        if (!force && BuildVars.DEBUG_VERSION || !force && !BuildVars.CHECK_UPDATES) {
            return;
        }
        if (!force && Math.abs(System.currentTimeMillis() - UserConfig.getInstance(0).lastUpdateCheckTime) < 24 * 60 * 60 * 1000) {
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            UserConfig.getInstance(0).lastUpdateCheckTime = System.currentTimeMillis();
            UserConfig.getInstance(0).saveConfig(false);
            if (response instanceof TLRPC.TL_help_appUpdate) {
                final TLRPC.TL_help_appUpdate res = (TLRPC.TL_help_appUpdate) response;
                AndroidUtilities.runOnUIThread(() -> {
                    if (res.can_not_skip) {
                        UserConfig.getInstance(0).pendingAppUpdate = res;
                        UserConfig.getInstance(0).pendingAppUpdateBuildVersion = BuildVars.BUILD_VERSION;
                        try {
                            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                            UserConfig.getInstance(0).pendingAppUpdateInstallTime = Math.max(packageInfo.lastUpdateTime, packageInfo.firstInstallTime);
                        } catch (Exception e) {
                            FileLog.e(e);
                            UserConfig.getInstance(0).pendingAppUpdateInstallTime = 0;
                        }
                        UserConfig.getInstance(0).saveConfig(false);
                        showUpdateActivity(accountNum, res, false);
                    } else {
                        (new UpdateAppAlertDialog(LaunchActivity.this, res, accountNum)).show();
                    }
                });
            }
        });
    }

    public AlertDialog showAlertDialog(AlertDialog.Builder builder) {
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            visibleDialog = builder.show();
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (visibleDialog != null) {
                        if (visibleDialog == localeDialog) {
                            try {
                                String shorname = LocaleController.getInstance().getCurrentLocaleInfo().shortName;
                                Toast.makeText(LaunchActivity.this, getStringForLanguageAlert(shorname.equals("en") ? englishLocaleStrings : systemLocaleStrings, "ChangeLanguageLater", R.string.ChangeLanguageLater), Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            localeDialog = null;
                        } else if (visibleDialog == proxyErrorDialog) {
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                            editor.putBoolean("proxy_enabled", false);
                            editor.putBoolean("proxy_enabled_calls", false);
                            editor.commit();
                            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
                            ConnectionsManager.setProxySettings(false, "", 1080, "", "", "");
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                            proxyErrorDialog = null;
                        }
                    }
                    visibleDialog = null;
                }
            });
            return visibleDialog;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false, false);
    }

    @Override
    public void didSelectDialogs(DialogsActivity dialogsFragment, ArrayList<Long> dids, CharSequence message, boolean param) {
        final long did = dids.get(0);
        int lower_part = (int) did;
        int high_id = (int) (did >> 32);

        int attachesCount = 0;
        if (contactsToSend != null) {
            attachesCount += contactsToSend.size();
        }
        if (videoPath != null) {
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
        if (videoPath == null && photoPathsArray == null && documentsPathsArray == null && documentsUrisArray == null && sendingText != null) {
            attachesCount++;
        }
        if (AlertsCreator.checkSlowMode(this, currentAccount, did, attachesCount > 1)) {
            return;
        }

        Bundle args = new Bundle();
        final int account = dialogsFragment != null ? dialogsFragment.getCurrentAccount() : currentAccount;
        args.putBoolean("scrollToTopOnResume", true);
        if (!AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.closeChats);
        }
        if (lower_part != 0) {
            if (lower_part > 0) {
                args.putInt("user_id", lower_part);
            } else if (lower_part < 0) {
                args.putInt("chat_id", -lower_part);
            }
        } else {
            args.putInt("enc_id", high_id);
        }
        if (!MessagesController.getInstance(account).checkCanOpenChat(args, dialogsFragment)) {
            return;
        }
        final ChatActivity fragment = new ChatActivity(args);
        if (contactsToSend != null && contactsToSend.size() == 1) {
            if (contactsToSend.size() == 1) {
                PhonebookShareActivity contactFragment = new PhonebookShareActivity(null, contactsToSendUri, null, null);
                contactFragment.setDelegate((user, notify, scheduleDate) -> {
                    actionBarLayout.presentFragment(fragment, true, false, true, false);
                    SendMessagesHelper.getInstance(account).sendMessage(user, did, null, null, null, notify, scheduleDate);
                });
                actionBarLayout.presentFragment(contactFragment, dialogsFragment != null, dialogsFragment == null, true, false);
            }
        } else {
            AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);
            actionBarLayout.presentFragment(fragment, dialogsFragment != null, dialogsFragment == null, true, false);
            if (videoPath != null) {
                fragment.openVideoEditor(videoPath, sendingText);
                sendingText = null;
            }
            if (photoPathsArray != null) {
                if (sendingText != null && sendingText.length() <= 1024 && photoPathsArray.size() == 1) {
                    photoPathsArray.get(0).caption = sendingText;
                    sendingText = null;
                }
                SendMessagesHelper.prepareSendingMedia(accountInstance, photoPathsArray, did, null, null, false, false, null, true, 0);
            }
            if (documentsPathsArray != null || documentsUrisArray != null) {
                String caption = null;
                if (sendingText != null && sendingText.length() <= 1024 && ((documentsPathsArray != null ? documentsPathsArray.size() : 0) + (documentsUrisArray != null ? documentsUrisArray.size() : 0)) == 1) {
                    caption = sendingText;
                    sendingText = null;
                }
                SendMessagesHelper.prepareSendingDocuments(accountInstance, documentsPathsArray, documentsOriginalPathsArray, documentsUrisArray, caption, documentsMimeType, did, null, null, null, true, 0);
            }
            if (sendingText != null) {
                SendMessagesHelper.prepareSendingText(accountInstance, sendingText, did, true, 0);
            }
            if (contactsToSend != null && !contactsToSend.isEmpty()) {
                for (int a = 0; a < contactsToSend.size(); a++) {
                    TLRPC.User user = contactsToSend.get(a);
                    SendMessagesHelper.getInstance(account).sendMessage(user, did, null, null, null, true, 0);
                }
            }
        }

        photoPathsArray = null;
        videoPath = null;
        sendingText = null;
        documentsPathsArray = null;
        documentsOriginalPathsArray = null;
        contactsToSend = null;
        contactsToSendUri = null;
    }

    private void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (currentAccount != -1) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needShowAlert);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openArticle);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.hasNewContactsToImport);
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.notificationsCountUpdated);
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false);
    }

    public ActionBarLayout getActionBarLayout() {
        return actionBarLayout;
    }

    public ActionBarLayout getLayersActionBarLayout() {
        return layersActionBarLayout;
    }

    public ActionBarLayout getRightActionBarLayout() {
        return rightActionBarLayout;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (SharedConfig.passcodeHash.length() != 0 && SharedConfig.lastPauseTime != 0) {
            SharedConfig.lastPauseTime = 0;
            UserConfig.getInstance(currentAccount).saveConfig(false);
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLAY_SERVICES_REQUEST_CHECK_SETTINGS) {
            LocationController.getInstance(currentAccount).startFusedLocationRequest(resultCode == Activity.RESULT_OK);
        } else {
            ThemeEditorView editorView = ThemeEditorView.getInstance();
            if (editorView != null) {
                editorView.onActivityResult(requestCode, resultCode, data);
            }
            if (actionBarLayout.fragmentsStack.size() != 0) {
                BaseFragment fragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                fragment.onActivityResultFragment(requestCode, resultCode, data);
            }
            if (AndroidUtilities.isTablet()) {
                if (rightActionBarLayout.fragmentsStack.size() != 0) {
                    BaseFragment fragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                    fragment.onActivityResultFragment(requestCode, resultCode, data);
                }
                if (layersActionBarLayout.fragmentsStack.size() != 0) {
                    BaseFragment fragment = layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1);
                    fragment.onActivityResultFragment(requestCode, resultCode, data);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == 4) {
            if (!granted) {
                showPermissionErrorAlert(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            } else {
                ImageLoader.getInstance().checkMediaPaths();
            }
        } else if (requestCode == 5) {
            if (!granted) {
                ContactsController.getInstance(currentAccount).forceImportContacts();
            } else {
                showPermissionErrorAlert(LocaleController.getString("PermissionContacts", R.string.PermissionContacts));
                return;
            }
        } else if (requestCode == 3) {
            boolean audioGranted = true;
            boolean cameraGranted = true;
            for (int i = 0, size = permissions.length; i < size; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                    audioGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                } else if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
            if (!audioGranted) {
                showPermissionErrorAlert(LocaleController.getString("PermissionNoAudio", R.string.PermissionNoAudio));
            } else if (!cameraGranted) {
                showPermissionErrorAlert(LocaleController.getString("PermissionNoCamera", R.string.PermissionNoCamera));
            } else {
                if (SharedConfig.inappCamera) {
                    CameraController.getInstance().initCamera(null);
                }
                return;
            }
        } else if (requestCode == 18 || requestCode == 19 || requestCode == 20 || requestCode == 22) {
            if (!granted) {
                showPermissionErrorAlert(LocaleController.getString("PermissionNoCamera", R.string.PermissionNoCamera));
            }
        } else if (requestCode == 2) {
            if (granted) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.locationPermissionGranted);
            }
        }
        if (actionBarLayout.fragmentsStack.size() != 0) {
            BaseFragment fragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
            fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
        if (AndroidUtilities.isTablet()) {
            if (rightActionBarLayout.fragmentsStack.size() != 0) {
                BaseFragment fragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
            }
            if (layersActionBarLayout.fragmentsStack.size() != 0) {
                BaseFragment fragment = layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1);
                fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
            }
        }
    }

    private void showPermissionErrorAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(message);
        builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4096);
        SharedConfig.lastAppPauseTime = System.currentTimeMillis();
        ApplicationLoader.mainInterfacePaused = true;
        Utilities.stageQueue.postRunnable(() -> {
            ApplicationLoader.mainInterfacePausedStageQueue = true;
            ApplicationLoader.mainInterfacePausedStageQueueTime = 0;
        });
        onPasscodePause();
        actionBarLayout.onPause();
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onPause();
            layersActionBarLayout.onPause();
        }
        if (passcodeView != null) {
            passcodeView.onPause();
        }
        ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        AndroidUtilities.unregisterUpdates();
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onPause();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Browser.bindCustomTabsService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Browser.unbindCustomTabsService(this);
    }

    @Override
    protected void onDestroy() {
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
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        MediaController.getInstance().setBaseActivity(this, false);
        MediaController.getInstance().setFeedbackView(actionBarLayout, false);
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
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4096);
        MediaController.getInstance().setFeedbackView(actionBarLayout, true);
        ApplicationLoader.mainInterfacePaused = false;
        showLanguageAlert(false);
        Utilities.stageQueue.postRunnable(() -> {
            ApplicationLoader.mainInterfacePausedStageQueue = false;
            ApplicationLoader.mainInterfacePausedStageQueueTime = System.currentTimeMillis();
        });
        checkFreeDiscSpace();
        MediaController.checkGallery();
        onPasscodeResume();
        if (passcodeView.getVisibility() != View.VISIBLE) {
            actionBarLayout.onResume();
            if (AndroidUtilities.isTablet()) {
                rightActionBarLayout.onResume();
                layersActionBarLayout.onResume();
            }
        } else {
            actionBarLayout.dismissDialogs();
            if (AndroidUtilities.isTablet()) {
                rightActionBarLayout.dismissDialogs();
                layersActionBarLayout.dismissDialogs();
            }
            passcodeView.onResume();
        }
        AndroidUtilities.checkForCrashes(this);
        AndroidUtilities.checkForUpdates(this);
        ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        updateCurrentConnectionState(currentAccount);
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onResume();
        }
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null && MediaController.getInstance().isMessagePaused()) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                MediaController.getInstance().seekToProgress(messageObject, messageObject.audioProgress);
            }
        }
        if (UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService != null) {
            showTosActivity(UserConfig.selectedAccount, UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService);
        } else if (UserConfig.getInstance(0).pendingAppUpdate != null) {
            showUpdateActivity(UserConfig.selectedAccount, UserConfig.getInstance(0).pendingAppUpdate, true);
        }
        checkAppUpdate(false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        AndroidUtilities.checkDisplaySize(this, newConfig);
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
        PhotoViewer photoViewer = PhotoViewer.getPipInstance();
        if (photoViewer != null) {
            photoViewer.onConfigurationChanged(newConfig);
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.onConfigurationChanged();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        AndroidUtilities.isInMultiwindow = isInMultiWindowMode;
        checkLayout();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, final int account, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            switchToAvailableAccountOrLogout();
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
        } else if (id == NotificationCenter.needShowAlert) {
            final Integer reason = (Integer) args[0];
            if (reason == 3 && proxyErrorDialog != null) {
                return;
            } else if (reason == 4) {
                showTosActivity(account, (TLRPC.TL_help_termsOfService) args[1]);
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            if (reason != 2 && reason != 3) {
                builder.setNegativeButton(LocaleController.getString("MoreInfo", R.string.MoreInfo), (dialogInterface, i) -> {
                    if (!mainFragmentsStack.isEmpty()) {
                        MessagesController.getInstance(account).openByUserName("spambot", mainFragmentsStack.get(mainFragmentsStack.size() - 1), 1);
                    }
                });
            }
            if (reason == 5) {
                builder.setMessage(LocaleController.getString("NobodyLikesSpam3", R.string.NobodyLikesSpam3));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            } else if (reason == 0) {
                builder.setMessage(LocaleController.getString("NobodyLikesSpam1", R.string.NobodyLikesSpam1));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            } else if (reason == 1) {
                builder.setMessage(LocaleController.getString("NobodyLikesSpam2", R.string.NobodyLikesSpam2));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            } else if (reason == 2) {
                builder.setMessage((String) args[1]);
                String type = (String) args[2];
                if (type.startsWith("AUTH_KEY_DROP_")) {
                    builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setNegativeButton(LocaleController.getString("LogOut", R.string.LogOut), (dialog, which) -> MessagesController.getInstance(currentAccount).performLogout(2));
                } else {
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                }
            } else if (reason == 3) {
                builder.setMessage(LocaleController.getString("UseProxyTelegramError", R.string.UseProxyTelegramError));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                proxyErrorDialog = showAlertDialog(builder);
                return;
            }
            if (!mainFragmentsStack.isEmpty()) {
                mainFragmentsStack.get(mainFragmentsStack.size() - 1).showDialog(builder.create());
            }
        } else if (id == NotificationCenter.wasUnableToFindCurrentLocation) {
            final HashMap<String, MessageObject> waitingForLocation = (HashMap<String, MessageObject>) args[0];
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            builder.setNegativeButton(LocaleController.getString("ShareYouLocationUnableManually", R.string.ShareYouLocationUnableManually), (dialogInterface, i) -> {
                if (mainFragmentsStack.isEmpty()) {
                    return;
                }
                BaseFragment lastFragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                if (!AndroidUtilities.isGoogleMapsInstalled(lastFragment)) {
                    return;
                }
                LocationActivity fragment = new LocationActivity(0);
                fragment.setDelegate((location, live, notify, scheduleDate) -> {
                    for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
                        MessageObject messageObject = entry.getValue();
                        SendMessagesHelper.getInstance(account).sendMessage(location, messageObject.getDialogId(), messageObject, null, null, notify, scheduleDate);
                    }
                });
                presentFragment(fragment);
            });
            builder.setMessage(LocaleController.getString("ShareYouLocationUnable", R.string.ShareYouLocationUnable));
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
        } else if (id == NotificationCenter.didSetPasscode) {
            if (SharedConfig.passcodeHash.length() > 0 && !SharedConfig.allowScreenCapture) {
                try {
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (!MediaController.getInstance().hasFlagSecureFragment()) {
                try {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.reloadInterface) {
            boolean last = mainFragmentsStack.size() > 1 && mainFragmentsStack.get(mainFragmentsStack.size() - 1) instanceof SettingsActivity;
            rebuildAllFragments(last);
        } else if (id == NotificationCenter.suggestedLangpack) {
            showLanguageAlert(false);
        } else if (id == NotificationCenter.openArticle) {
            if (mainFragmentsStack.isEmpty()) {
                return;
            }
            ArticleViewer.getInstance().setParentActivity(this, mainFragmentsStack.get(mainFragmentsStack.size() - 1));
            ArticleViewer.getInstance().open((TLRPC.TL_webPage) args[0], (String) args[1]);
        } else if (id == NotificationCenter.hasNewContactsToImport) {
            if (actionBarLayout == null || actionBarLayout.fragmentsStack.isEmpty()) {
                return;
            }
            final int type = (Integer) args[0];
            final HashMap<String, ContactsController.Contact> contactHashMap = (HashMap<String, ContactsController.Contact>) args[1];
            final boolean first = (Boolean) args[2];
            final boolean schedule = (Boolean) args[3];
            BaseFragment fragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);

            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
            builder.setTitle(LocaleController.getString("UpdateContactsTitle", R.string.UpdateContactsTitle));
            builder.setMessage(LocaleController.getString("UpdateContactsMessage", R.string.UpdateContactsMessage));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> ContactsController.getInstance(account).syncPhoneBookByAlert(contactHashMap, first, schedule, false));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> ContactsController.getInstance(account).syncPhoneBookByAlert(contactHashMap, first, schedule, true));
            builder.setOnBackButtonListener((dialogInterface, i) -> ContactsController.getInstance(account).syncPhoneBookByAlert(contactHashMap, first, schedule, true));
            AlertDialog dialog = builder.create();
            fragment.showDialog(dialog);
            dialog.setCanceledOnTouchOutside(false);
        } else if (id == NotificationCenter.didSetNewTheme) {
            Boolean nightTheme = (Boolean) args[0];
            if (!nightTheme) {
                if (sideMenu != null) {
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
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            Theme.ThemeInfo theme = (Theme.ThemeInfo) args[0];
            boolean nigthTheme = (Boolean) args[1];
            actionBarLayout.animateThemedValues(theme, nigthTheme);
            if (AndroidUtilities.isTablet()) {
                layersActionBarLayout.animateThemedValues(theme, nigthTheme);
                rightActionBarLayout.animateThemedValues(theme, nigthTheme);
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
        } else if (id == NotificationCenter.fileDidLoad) {
            if (loadingThemeFileName != null) {
                String path = (String) args[0];
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
                                        FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, 1, 1);
                                    } else {
                                        onThemeLoadFinish();
                                    }
                                }));
                                return;
                            }
                        }
                        Theme.ThemeInfo finalThemeInfo = Theme.applyThemeFile(locFile, loadingTheme.title, loadingTheme, true);
                        if (finalThemeInfo != null) {
                            presentFragment(new ThemePreviewActivity(finalThemeInfo, true, ThemePreviewActivity.SCREEN_TYPE_PREVIEW, false));
                        }
                    }
                    onThemeLoadFinish();
                }
            } else if (loadingThemeWallpaperName != null) {
                String path = (String) args[0];
                if (loadingThemeWallpaperName.equals(path)) {
                    loadingThemeWallpaperName = null;
                    File file = (File) args[1];
                    Utilities.globalQueue.postRunnable(() -> {
                        try {
                            Bitmap bitmap = ThemesHorizontalListCell.getScaledBitmap(AndroidUtilities.dp(640), AndroidUtilities.dp(360), file.getAbsolutePath(), null, 0);
                            if (loadingThemeInfo.isBlured) {
                                bitmap = Utilities.blurWallpaper(bitmap);
                            }
                            FileOutputStream stream = new FileOutputStream(loadingThemeInfo.pathToWallpaper);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                            stream.close();
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            File locFile = new File(ApplicationLoader.getFilesDirFixed(), "remote" + loadingTheme.id + ".attheme");
                            Theme.ThemeInfo finalThemeInfo = Theme.applyThemeFile(locFile, loadingTheme.title, loadingTheme, true);
                            if (finalThemeInfo != null) {
                                presentFragment(new ThemePreviewActivity(finalThemeInfo, true, ThemePreviewActivity.SCREEN_TYPE_PREVIEW, false));
                            }
                            onThemeLoadFinish();
                        });
                    });
                }
            }
        } else if (id == NotificationCenter.fileDidFailToLoad) {
            String path = (String) args[0];
            if (path.equals(loadingThemeFileName) || path.equals(loadingThemeWallpaperName)) {
                onThemeLoadFinish();
            }
        }
    }

    private String getStringForLanguageAlert(HashMap<String, String> map, String key, int intKey) {
        String value = map.get(key);
        if (value == null) {
            return LocaleController.getString(key, intKey);
        }
        return value;
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
        loadingThemeInfo = null;
        loadingThemeFileName = null;
        loadingTheme = null;
    }

    private void checkFreeDiscSpace() {
        SharedConfig.checkKeepMedia();
        if (Build.VERSION.SDK_INT >= 26) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                return;
            }
            try {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if (Math.abs(preferences.getLong("last_space_check", 0) - System.currentTimeMillis()) >= 3 * 24 * 3600 * 1000) {
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
                    if (freeSpace < 1024 * 1024 * 100) {
                        preferences.edit().putLong("last_space_check", System.currentTimeMillis()).commit();
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                AlertsCreator.createFreeSpaceDialog(LaunchActivity.this).show();
                            } catch (Throwable ignore) {

                            }
                        });
                    }
                }
            } catch (Throwable ignore) {

            }
        }, 2000);
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
                cells[a] = new LanguageCell(LaunchActivity.this, true);
                cells[a].setLanguage(locales[a], locales[a] == englishInfo ? englishName : null, true);
                cells[a].setTag(a);
                cells[a].setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
                cells[a].setLanguageSelected(a == 0);
                linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                cells[a].setOnClickListener(v -> {
                    Integer tag = (Integer) v.getTag();
                    selectedLanguage[0] = ((LanguageCell) v).getCurrentLocale();
                    for (int a1 = 0; a1 < cells.length; a1++) {
                        cells[a1].setLanguageSelected(a1 == tag);
                    }
                });
            }
            LanguageCell cell = new LanguageCell(LaunchActivity.this, true);
            cell.setValue(getStringForLanguageAlert(systemLocaleStrings, "ChooseYourLanguageOther", R.string.ChooseYourLanguageOther), getStringForLanguageAlert(englishLocaleStrings, "ChooseYourLanguageOther", R.string.ChooseYourLanguageOther));
            cell.setOnClickListener(v -> {
                localeDialog = null;
                drawerLayoutContainer.closeDrawer(true);
                presentFragment(new LanguageSelectActivity());
                if (visibleDialog != null) {
                    visibleDialog.dismiss();
                    visibleDialog = null;
                }
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            builder.setView(linearLayout);
            builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
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

    private void showLanguageAlert(boolean force) {
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
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (SharedConfig.passcodeHash.length() != 0) {
            SharedConfig.lastPauseTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            lockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (lockRunnable == this) {
                        if (AndroidUtilities.needShowPasscode(true)) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("lock app");
                            }
                            showPasscodeActivity();
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
            } else if (SharedConfig.autoLockIn != 0) {
                AndroidUtilities.runOnUIThread(lockRunnable, (long) SharedConfig.autoLockIn * 1000 + 1000);
            }
        } else {
            SharedConfig.lastPauseTime = 0;
        }
        SharedConfig.saveConfig();
    }

    private void onPasscodeResume() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (AndroidUtilities.needShowPasscode(true)) {
            showPasscodeActivity();
        }
        if (SharedConfig.lastPauseTime != 0) {
            SharedConfig.lastPauseTime = 0;
            SharedConfig.saveConfig();
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
                if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1);
                } else if (!rightActionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                } else if (!actionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                }
            } else {
                if (!actionBarLayout.fragmentsStack.isEmpty()) {
                    lastFragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                }
            }

            if (lastFragment != null) {
                Bundle args = lastFragment.getArguments();
                if (lastFragment instanceof ChatActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat");
                } else if (lastFragment instanceof SettingsActivity) {
                    outState.putString("fragment", "settings");
                } else if (lastFragment instanceof GroupCreateFinalActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "group");
                } else if (lastFragment instanceof WallpapersListActivity) {
                    outState.putString("fragment", "wallpapers");
                } else if (lastFragment instanceof ProfileActivity && ((ProfileActivity) lastFragment).isChat() && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat_profile");
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
        if (passcodeView.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(true, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(true, false);
        } else if (drawerLayoutContainer.isDrawerOpened()) {
            drawerLayoutContainer.closeDrawer(false);
        } else if (AndroidUtilities.isTablet()) {
            if (layersActionBarLayout.getVisibility() == View.VISIBLE) {
                layersActionBarLayout.onBackPressed();
            } else {
                boolean cancel = false;
                if (rightActionBarLayout.getVisibility() == View.VISIBLE && !rightActionBarLayout.fragmentsStack.isEmpty()) {
                    BaseFragment lastFragment = rightActionBarLayout.fragmentsStack.get(rightActionBarLayout.fragmentsStack.size() - 1);
                    cancel = !lastFragment.onBackPressed();
                }
                if (!cancel) {
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
        actionBarLayout.onLowMemory();
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onLowMemory();
            layersActionBarLayout.onLowMemory();
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
        if (!mainFragmentsStack.isEmpty() && (!PhotoViewer.hasInstance() || !PhotoViewer.getInstance().isVisible()) && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
            if (fragment instanceof ChatActivity) {
                if (((ChatActivity) fragment).maybePlayVisibleVideo()) {
                    return true;
                }
            }
            if (AndroidUtilities.isTablet() && !rightFragmentsStack.isEmpty()) {
                fragment = rightFragmentsStack.get(rightFragmentsStack.size() - 1);
                if (fragment instanceof ChatActivity) {
                    if (((ChatActivity) fragment).maybePlayVisibleVideo()) {
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
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
                if (layersActionBarLayout.getVisibility() == View.VISIBLE && !layersActionBarLayout.fragmentsStack.isEmpty()) {
                    layersActionBarLayout.onKeyUp(keyCode, event);
                } else if (rightActionBarLayout.getVisibility() == View.VISIBLE && !rightActionBarLayout.fragmentsStack.isEmpty()) {
                    rightActionBarLayout.onKeyUp(keyCode, event);
                } else {
                    actionBarLayout.onKeyUp(keyCode, event);
                }
            } else {
                if (actionBarLayout.fragmentsStack.size() == 1) {
                    if (!drawerLayoutContainer.isDrawerOpened()) {
                        if (getCurrentFocus() != null) {
                            AndroidUtilities.hideKeyboard(getCurrentFocus());
                        }
                        drawerLayoutContainer.openDrawer(false);
                    } else {
                        drawerLayoutContainer.closeDrawer(false);
                    }
                } else {
                    actionBarLayout.onKeyUp(keyCode, event);
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout) {
        if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        if (AndroidUtilities.isTablet()) {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity) && layersActionBarLayout.getVisibility() != View.VISIBLE, true);
            if (fragment instanceof DialogsActivity) {
                DialogsActivity dialogsActivity = (DialogsActivity) fragment;
                if (dialogsActivity.isMainDialogList() && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false, false);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.setVisibility(View.GONE);
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            }
            if (fragment instanceof ChatActivity && !((ChatActivity) fragment).isInScheduleMode()) {
                if (!tabletFullSize && layout == rightActionBarLayout || tabletFullSize && layout == actionBarLayout) {
                    boolean result = !(tabletFullSize && layout == actionBarLayout && actionBarLayout.fragmentsStack.size() == 1);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    if (!result) {
                        actionBarLayout.presentFragment(fragment, false, forceWithoutAnimation, false, false);
                    }
                    return result;
                } else if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.presentFragment(fragment, removeLast, true, false, false);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false, false);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else {
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false, false);
                    return false;
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.setVisibility(View.VISIBLE);
                drawerLayoutContainer.setAllowOpenDrawer(false, true);
                if (fragment instanceof LoginActivity) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                    shadowTabletSide.setVisibility(View.GONE);
                    shadowTablet.setBackgroundColor(0x00000000);
                } else {
                    shadowTablet.setBackgroundColor(0x7f000000);
                }
                layersActionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false, false);
                return false;
            }
            return true;
        } else {
            boolean allow = true;
            if (fragment instanceof LoginActivity) {
                if (mainFragmentsStack.size() == 0) {
                    allow = false;
                }
            } else if (fragment instanceof CountrySelectActivity) {
                if (mainFragmentsStack.size() == 1) {
                    allow = false;
                }
            }
            drawerLayoutContainer.setAllowOpenDrawer(allow, false);
            return true;
        }
    }

    @Override
    public boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity) && layersActionBarLayout.getVisibility() != View.VISIBLE, true);
            if (fragment instanceof DialogsActivity) {
                DialogsActivity dialogsActivity = (DialogsActivity) fragment;
                if (dialogsActivity.isMainDialogList() && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.addFragmentToStack(fragment);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.setVisibility(View.GONE);
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            } else if (fragment instanceof ChatActivity && !((ChatActivity) fragment).isInScheduleMode()) {
                if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.addFragmentToStack(fragment);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(true);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    actionBarLayout.addFragmentToStack(fragment);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(true);
                    }
                    return false;
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.setVisibility(View.VISIBLE);
                drawerLayoutContainer.setAllowOpenDrawer(false, true);
                if (fragment instanceof LoginActivity) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                    shadowTabletSide.setVisibility(View.GONE);
                    shadowTablet.setBackgroundColor(0x00000000);
                } else {
                    shadowTablet.setBackgroundColor(0x7f000000);
                }
                layersActionBarLayout.addFragmentToStack(fragment);
                return false;
            }
            return true;
        } else {
            boolean allow = true;
            if (fragment instanceof LoginActivity) {
                if (mainFragmentsStack.size() == 0) {
                    allow = false;
                }
            } else if (fragment instanceof CountrySelectActivity) {
                if (mainFragmentsStack.size() == 1) {
                    allow = false;
                }
            }
            drawerLayoutContainer.setAllowOpenDrawer(allow, false);
            return true;
        }
    }

    @Override
    public boolean needCloseLastFragment(ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (layout == actionBarLayout && layout.fragmentsStack.size() <= 1) {
                onFinish();
                finish();
                return false;
            } else if (layout == rightActionBarLayout) {
                if (!tabletFullSize) {
                    backgroundTablet.setVisibility(View.VISIBLE);
                }
            } else if (layout == layersActionBarLayout && actionBarLayout.fragmentsStack.isEmpty() && layersActionBarLayout.fragmentsStack.size() == 1) {
                onFinish();
                finish();
                return false;
            }
        } else {
            if (layout.fragmentsStack.size() <= 1) {
                onFinish();
                finish();
                return false;
            }
            if (layout.fragmentsStack.size() >= 2 && !(layout.fragmentsStack.get(0) instanceof LoginActivity)) {
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
    public void onRebuildAllFragments(ActionBarLayout layout, boolean last) {
        if (AndroidUtilities.isTablet()) {
            if (layout == layersActionBarLayout) {
                rightActionBarLayout.rebuildAllFragmentViews(last, last);
                actionBarLayout.rebuildAllFragmentViews(last, last);
            }
        }
        drawerLayoutAdapter.notifyDataSetChanged();
    }
}

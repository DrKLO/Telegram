/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NativeCrashManager;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.query.DraftQuery;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharingLocationsAlert;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ThemeEditorView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private int currentConnectionState;
    private static ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<>();
    private static ArrayList<BaseFragment> layerFragmentsStack = new ArrayList<>();
    private static ArrayList<BaseFragment> rightFragmentsStack = new ArrayList<>();
    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;

    private ActionBarLayout actionBarLayout;
    private ActionBarLayout layersActionBarLayout;
    private ActionBarLayout rightActionBarLayout;
    private FrameLayout shadowTablet;
    private FrameLayout shadowTabletSide;
    private View backgroundTablet;
    protected DrawerLayoutContainer drawerLayoutContainer;
    private DrawerLayoutAdapter drawerLayoutAdapter;
    private PasscodeView passcodeView;
    private AlertDialog visibleDialog;
    private RecyclerListView sideMenu;

    private AlertDialog localeDialog;
    private boolean loadingLocaleDialog;
    private HashMap<String, String> systemLocaleStrings;
    private HashMap<String, String> englishLocaleStrings;

    private Intent passcodeSaveIntent;
    private boolean passcodeSaveIntentIsNew;
    private boolean passcodeSaveIntentIsRestore;

    private boolean tabletFullSize;

    private Runnable lockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();
        NativeCrashManager.handleDumpFiles(this);
        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());

        if (!UserConfig.isClientActivated()) {
            Intent intent = getIntent();
            if (intent != null && intent.getAction() != null && (Intent.ACTION_SEND.equals(intent.getAction()) || intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))) {
                super.onCreate(savedInstanceState);
                finish();
                return;
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            long crashed_time = preferences.getLong("intro_crashed_time", 0);
            boolean fromIntro = intent.getBooleanExtra("fromIntro", false);
            if (fromIntro) {
                preferences.edit().putLong("intro_crashed_time", 0).commit();
            }
            if (Math.abs(crashed_time - System.currentTimeMillis()) >= 60 * 2 * 1000 && intent != null && !fromIntro) {
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
            } catch (Exception e) {
                //
            }
        }

        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        if (UserConfig.passcodeHash.length() > 0 && !UserConfig.allowScreenCapture) {
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
        if (UserConfig.passcodeHash.length() != 0 && UserConfig.appLocked) {
            UserConfig.lastPauseTime = ConnectionsManager.getInstance().getCurrentTime();
        }

        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            AndroidUtilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        actionBarLayout = new ActionBarLayout(this);

        drawerLayoutContainer = new DrawerLayoutContainer(this);
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
            shadowTablet.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (!actionBarLayout.fragmentsStack.isEmpty() && event.getAction() == MotionEvent.ACTION_UP) {
                        float x = event.getX();
                        float y = event.getY();
                        int location[] = new int[2];
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
                }
            });

            shadowTablet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
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
        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        sideMenu.setAdapter(drawerLayoutAdapter = new DrawerLayoutAdapter(this));
        drawerLayoutContainer.setDrawerLayout(sideMenu);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sideMenu.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet() ? AndroidUtilities.dp(320) : Math.min(AndroidUtilities.dp(320), Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56));
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        sideMenu.setLayoutParams(layoutParams);
        sideMenu.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(final View view, int position) {
                int id = drawerLayoutAdapter.getId(position);
                if (position == 0) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", UserConfig.getClientUserId());
                    presentFragment(new ChatActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 2) {
                    if (!MessagesController.isFeatureEnabled("chat_create", actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1))) {
                        return;
                    }
                    presentFragment(new GroupCreateActivity());
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
                    if (!MessagesController.isFeatureEnabled("broadcast_create", actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1))) {
                        return;
                    }
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                        Bundle args = new Bundle();
                        args.putInt("step", 0);
                        presentFragment(new ChannelCreateActivity(args));
                    } else {
                        presentFragment(new ChannelIntroActivity());
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
                    args.putInt("user_id", UserConfig.getClientUserId());
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

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);
        currentConnectionState = ConnectionsManager.getInstance().getConnectionState();

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didUpdatedConnectionState);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.openArticle);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hasNewContactsToImport);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetNewTheme);

        if (actionBarLayout.fragmentsStack.isEmpty()) {
            if (!UserConfig.isClientActivated()) {
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
                            case "edit":
                                if (args != null) {
                                    ChannelEditActivity channel = new ChannelEditActivity(args);
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
                                WallpapersActivity settings = new WallpapersActivity();
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
            if (os1.contains("flyme") || os2.contains("flyme")) {
                AndroidUtilities.incorrectDisplaySizeFix = true;
                final View view = getWindow().getDecorView().getRootView();
                view.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int height = view.getMeasuredHeight();
                        if (Build.VERSION.SDK_INT >= 21) {
                            height -= AndroidUtilities.statusBarHeight;
                        }
                        if (height > AndroidUtilities.dp(100) && height < AndroidUtilities.displaySize.y && height + AndroidUtilities.dp(100) > AndroidUtilities.displaySize.y) {
                            AndroidUtilities.displaySize.y = height;
                            FileLog.e("fix display size y to " + AndroidUtilities.displaySize.y);
                        }
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        MediaController.getInstance().setBaseActivity(this, true);
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

    private void showPasscodeActivity() {
        if (passcodeView == null) {
            return;
        }
        UserConfig.appLocked = true;
        if (SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(false, false);
        } else if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
        } else if (ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        passcodeView.onShow();
        UserConfig.isWaitingForPasscodeEnter = true;
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        passcodeView.setDelegate(new PasscodeView.PasscodeViewDelegate() {
            @Override
            public void didAcceptedPassword() {
                UserConfig.isWaitingForPasscodeEnter = false;
                if (passcodeSaveIntent != null) {
                    handleIntent(passcodeSaveIntent, passcodeSaveIntentIsNew, passcodeSaveIntentIsRestore, true);
                    passcodeSaveIntent = null;
                }
                drawerLayoutContainer.setAllowOpenDrawer(true, false);
                actionBarLayout.showLastFragment();
                if (AndroidUtilities.isTablet()) {
                    layersActionBarLayout.showLastFragment();
                    rightActionBarLayout.showLastFragment();
                }
            }
        });
    }

    private class VcardData {
        String name;
        ArrayList<String> phones = new ArrayList<>();
    }

    private boolean handleIntent(Intent intent, boolean isNew, boolean restore, boolean fromPassword) {
        if (AndroidUtilities.handleProxyIntent(this, intent)) {
            return true;
        }
        int flags = intent.getFlags();
        if (!fromPassword && (AndroidUtilities.needShowPasscode(true) || UserConfig.isWaitingForPasscodeEnter)) {
            showPasscodeActivity();
            passcodeSaveIntent = intent;
            passcodeSaveIntentIsNew = isNew;
            passcodeSaveIntentIsRestore = restore;
            UserConfig.saveConfig(false);
        } else {
            boolean pushOpened = false;

            Integer push_user_id = 0;
            Integer push_chat_id = 0;
            Integer push_enc_id = 0;
            Integer push_msg_id = 0;
            Integer open_settings = 0;
            Integer open_new_dialog = 0;
            long dialogId = intent != null && intent.getExtras() != null ? intent.getExtras().getLong("dialogId", 0) : 0;
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

            if (UserConfig.isClientActivated() && (flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
                if (intent != null && intent.getAction() != null && !restore) {
                    if (Intent.ACTION_SEND.equals(intent.getAction())) {
                        boolean error = false;
                        String type = intent.getType();
                        if (type != null && type.equals(ContactsContract.Contacts.CONTENT_VCARD_TYPE)) {
                            try {
                                Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                                if (uri != null) {
                                    ContentResolver cr = getContentResolver();
                                    InputStream stream = cr.openInputStream(uri);
                                    ArrayList<VcardData> vcardDatas = new ArrayList<>();
                                    VcardData currentData = null;

                                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                                    String line;
                                    while ((line = bufferedReader.readLine()) != null) {
                                        FileLog.e(line);
                                        String[] args = line.split(":");
                                        if (args.length != 2) {
                                            continue;
                                        }
                                        if (args[0].equals("BEGIN") && args[1].equals("VCARD")) {
                                            vcardDatas.add(currentData = new VcardData());
                                        } else if (args[0].equals("END") && args[1].equals("VCARD")) {
                                            currentData = null;
                                        }
                                        if (currentData == null) {
                                            continue;
                                        }
                                        if (args[0].startsWith("FN") || args[0].startsWith("ORG") && TextUtils.isEmpty(currentData.name)) {
                                            String nameEncoding = null;
                                            String nameCharset = null;
                                            String[] params = args[0].split(";");
                                            for (String param : params) {
                                                String[] args2 = param.split("=");
                                                if (args2.length != 2) {
                                                    continue;
                                                }
                                                if (args2[0].equals("CHARSET")) {
                                                    nameCharset = args2[1];
                                                } else if (args2[0].equals("ENCODING")) {
                                                    nameEncoding = args2[1];
                                                }
                                            }
                                            currentData.name = args[1];
                                            if (nameEncoding != null && nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                                                while (currentData.name.endsWith("=") && nameEncoding != null) {
                                                    currentData.name = currentData.name.substring(0, currentData.name.length() - 1);
                                                    line = bufferedReader.readLine();
                                                    if (line == null) {
                                                        break;
                                                    }
                                                    currentData.name += line;
                                                }
                                                byte[] bytes = AndroidUtilities.decodeQuotedPrintable(currentData.name.getBytes());
                                                if (bytes != null && bytes.length != 0) {
                                                    String decodedName = new String(bytes, nameCharset);
                                                    if (decodedName != null) {
                                                        currentData.name = decodedName;
                                                    }
                                                }
                                            }
                                        } else if (args[0].startsWith("TEL")) {
                                            String phone = PhoneFormat.stripExceptNumbers(args[1], true);
                                            if (phone.length() > 0) {
                                                currentData.phones.add(phone);
                                            }
                                        }
                                    }
                                    try {
                                        bufferedReader.close();
                                        stream.close();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    for (int a = 0; a < vcardDatas.size(); a++) {
                                        VcardData vcardData = vcardDatas.get(a);
                                        if (vcardData.name != null && !vcardData.phones.isEmpty()) {
                                            if (contactsToSend == null) {
                                                contactsToSend = new ArrayList<>();
                                            }

                                            for (int b = 0; b < vcardData.phones.size(); b++) {
                                                String phone = vcardData.phones.get(b);
                                                TLRPC.User user = new TLRPC.TL_userContact_old2();
                                                user.phone = phone;
                                                user.first_name = vcardData.name;
                                                user.last_name = "";
                                                user.id = 0;
                                                contactsToSend.add(user);
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
                        } else {
                            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                            if (text == null) {
                                CharSequence textSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                                if (textSequence != null) {
                                    text = textSequence.toString();
                                }
                            }
                            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

                            if (text != null && text.length() != 0) {
                                if ((text.startsWith("http://") || text.startsWith("https://")) && subject != null && subject.length() != 0) {
                                    text = subject + "\n" + text;
                                }
                                sendingText = text;
                            } else if (subject != null && subject.length() > 0) {
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
                    } else if (intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
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
                            String instantView[] = null;
                            String botUser = null;
                            String botChat = null;
                            String message = null;
                            String phone = null;
                            String game = null;
                            String phoneHash = null;
                            Integer messageId = null;
                            boolean hasUrl = false;
                            String scheme = data.getScheme();
                            if (scheme != null) {
                                if ((scheme.equals("http") || scheme.equals("https"))) {
                                    String host = data.getHost().toLowerCase();
                                    if (host.equals("telegram.me") || host.equals("t.me") || host.equals("telegram.dog") || host.equals("telesco.pe")) {
                                        String path = data.getPath();
                                        if (path != null && path.length() > 1) {
                                            path = path.substring(1);
                                            if (path.startsWith("joinchat/")) {
                                                group = path.replace("joinchat/", "");
                                            } else if (path.startsWith("addstickers/")) {
                                                sticker = path.replace("addstickers/", "");
                                            } else if (path.startsWith("iv/")) {
                                                instantView[0] = data.getQueryParameter("url");
                                                instantView[1] = data.getQueryParameter("rhash");
                                                if (TextUtils.isEmpty(instantView[0]) || TextUtils.isEmpty(instantView[1])) {
                                                    instantView = null;
                                                }
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
                                            } else if (path.length() >= 1) {
                                                List<String> segments = data.getPathSegments();
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
                                        botUser = data.getQueryParameter("start");
                                        botChat = data.getQueryParameter("startgroup");
                                        game = data.getQueryParameter("game");
                                        messageId = Utilities.parseInt(data.getQueryParameter("post"));
                                        if (messageId == 0) {
                                            messageId = null;
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
                                        phone = data.getQueryParameter("phone");
                                        phoneHash = data.getQueryParameter("hash");
                                    } else if (url.startsWith("tg:openmessage") || url.startsWith("tg://openmessage")) {
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
                                    }
                                }
                            }
                            if (message != null && message.startsWith("@")) {
                                message = " " + message;
                            }
                            if (phone != null || phoneHash != null) {
                                final Bundle args = new Bundle();
                                args.putString("phone", phone);
                                args.putString("hash", phoneHash);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        presentFragment(new CancelAccountDeletionActivity(args));
                                    }
                                });
                            } else if (username != null || group != null || sticker != null || message != null || game != null || instantView != null) {
                                runLinkRequest(username, group, sticker, botUser, botChat, message, hasUrl, messageId, game, instantView, 0);
                            } else {
                                try {
                                    Cursor cursor = getContentResolver().query(intent.getData(), null, null, null, null);
                                    if (cursor != null) {
                                        if (cursor.moveToFirst()) {
                                            int userId = cursor.getInt(cursor.getColumnIndex("DATA4"));
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                            push_user_id = userId;
                                        }
                                        cursor.close();
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
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
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                            push_chat_id = chatId;
                        } else if (userId != 0) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                            push_user_id = userId;
                        } else if (encId != 0) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
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

            if (push_user_id != 0) {
                Bundle args = new Bundle();
                args.putInt("user_id", push_user_id);
                if (push_msg_id != 0)
                    args.putInt("message_id", push_msg_id);
                if (mainFragmentsStack.isEmpty() || MessagesController.checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                    ChatActivity fragment = new ChatActivity(args);
                    if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                        pushOpened = true;
                    }
                }
            } else if (push_chat_id != 0) {
                Bundle args = new Bundle();
                args.putInt("chat_id", push_chat_id);
                if (push_msg_id != 0)
                    args.putInt("message_id", push_msg_id);
                if (mainFragmentsStack.isEmpty() || MessagesController.checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                    ChatActivity fragment = new ChatActivity(args);
                    if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                        pushOpened = true;
                    }
                }
            } else if (push_enc_id != 0) {
                Bundle args = new Bundle();
                args.putInt("enc_id", push_enc_id);
                ChatActivity fragment = new ChatActivity(args);
                if (actionBarLayout.presentFragment(fragment, false, true, true)) {
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
                    fragment.showDialog(new SharingLocationsAlert(this, new SharingLocationsAlert.SharingLocationsAlertDelegate() {
                        @Override
                        public void didSelectLocation(LocationController.SharingLocationInfo info) {
                            LocationActivity locationActivity = new LocationActivity(2);
                            locationActivity.setMessageObject(info.messageObject);
                            final long dialog_id = info.messageObject.getDialogId();
                            locationActivity.setDelegate(new LocationActivity.LocationActivityDelegate() {
                                @Override
                                public void didSelectLocation(TLRPC.MessageMedia location, int live) {
                                    SendMessagesHelper.getInstance().sendMessage(location, dialog_id, null, null, null);
                                }
                            });
                            presentFragment(locationActivity);
                        }
                    }));
                }
                pushOpened = false;
            } else if (videoPath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null || documentsUrisArray != null) {
                if (!AndroidUtilities.isTablet()) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                }
                if (dialogId == 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    if (contactsToSend != null) {
                        args.putString("selectAlertString", LocaleController.getString("SendContactTo", R.string.SendMessagesTo));
                        args.putString("selectAlertStringGroup", LocaleController.getString("SendContactToGroup", R.string.SendContactToGroup));
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
                    actionBarLayout.presentFragment(fragment, removeLast, true, true);
                    pushOpened = true;
                    if (SecretMediaViewer.getInstance().isVisible()) {
                        SecretMediaViewer.getInstance().closePhoto(false, false);
                    } else if (PhotoViewer.getInstance().isVisible()) {
                        PhotoViewer.getInstance().closePhoto(false, true);
                    } else if (ArticleViewer.getInstance().isVisible()) {
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
                actionBarLayout.presentFragment(new SettingsActivity(), false, true, true);
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
                actionBarLayout.presentFragment(new ContactsActivity(args), false, true, true);
                if (AndroidUtilities.isTablet()) {
                    actionBarLayout.showLastFragment();
                    rightActionBarLayout.showLastFragment();
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                pushOpened = true;
            }

            if (!pushOpened && !isNew) {
                if (AndroidUtilities.isTablet()) {
                    if (!UserConfig.isClientActivated()) {
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
                        if (!UserConfig.isClientActivated()) {
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

    private void runLinkRequest(final String username, final String group, final String sticker, final String botUser, final String botChat, final String message, final boolean hasUrl, final Integer messageId, final String game, final String[] instantView, final int state) {
        final AlertDialog progressDialog = new AlertDialog(this, 1);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        int requestId = 0;

        if (username != null) {
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            requestId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!LaunchActivity.this.isFinishing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                final TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                if (error == null && actionBarLayout != null && (game == null || game != null && !res.users.isEmpty())) {
                                    MessagesController.getInstance().putUsers(res.users, false);
                                    MessagesController.getInstance().putChats(res.chats, false);
                                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, true);
                                    if (game != null) {
                                        Bundle args = new Bundle();
                                        args.putBoolean("onlySelect", true);
                                        args.putBoolean("cantSendToChannels", true);
                                        args.putInt("dialogsType", 1);
                                        args.putString("selectAlertString", LocaleController.getString("SendGameTo", R.string.SendGameTo));
                                        args.putString("selectAlertStringGroup", LocaleController.getString("SendGameToGroup", R.string.SendGameToGroup));
                                        DialogsActivity fragment = new DialogsActivity(args);
                                        fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                                            @Override
                                            public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                                                long did = dids.get(0);
                                                TLRPC.TL_inputMediaGame inputMediaGame = new TLRPC.TL_inputMediaGame();
                                                inputMediaGame.id = new TLRPC.TL_inputGameShortName();
                                                inputMediaGame.id.short_name = game;
                                                inputMediaGame.id.bot_id = MessagesController.getInputUser(res.users.get(0));
                                                SendMessagesHelper.getInstance().sendGame(MessagesController.getInputPeer((int) did), inputMediaGame, 0, 0);

                                                Bundle args = new Bundle();
                                                args.putBoolean("scrollToTopOnResume", true);
                                                int lower_part = (int) did;
                                                int high_id = (int) (did >> 32);
                                                if (lower_part != 0) {
                                                    if (high_id == 1) {
                                                        args.putInt("chat_id", lower_part);
                                                    } else {
                                                        if (lower_part > 0) {
                                                            args.putInt("user_id", lower_part);
                                                        } else if (lower_part < 0) {
                                                            args.putInt("chat_id", -lower_part);
                                                        }
                                                    }
                                                } else {
                                                    args.putInt("enc_id", high_id);
                                                }
                                                if (MessagesController.checkCanOpenChat(args, fragment)) {
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                                    actionBarLayout.presentFragment(new ChatActivity(args), true, false, true);
                                                }
                                            }
                                        });
                                        boolean removeLast;
                                        if (AndroidUtilities.isTablet()) {
                                            removeLast = layersActionBarLayout.fragmentsStack.size() > 0 && layersActionBarLayout.fragmentsStack.get(layersActionBarLayout.fragmentsStack.size() - 1) instanceof DialogsActivity;
                                        } else {
                                            removeLast = actionBarLayout.fragmentsStack.size() > 1 && actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1) instanceof DialogsActivity;
                                        }
                                        actionBarLayout.presentFragment(fragment, removeLast, true, true);
                                        if (SecretMediaViewer.getInstance().isVisible()) {
                                            SecretMediaViewer.getInstance().closePhoto(false, false);
                                        } else if (PhotoViewer.getInstance().isVisible()) {
                                            PhotoViewer.getInstance().closePhoto(false, true);
                                        } else if (ArticleViewer.getInstance().isVisible()) {
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
                                        fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                                            @Override
                                            public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                                                long did = dids.get(0);
                                                Bundle args = new Bundle();
                                                args.putBoolean("scrollToTopOnResume", true);
                                                args.putInt("chat_id", -(int) did);
                                                if (mainFragmentsStack.isEmpty() || MessagesController.checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                                    MessagesController.getInstance().addUserToChat(-(int) did, user, null, 0, botChat, null);
                                                    actionBarLayout.presentFragment(new ChatActivity(args), true, false, true);
                                                }
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
                                        if (lastFragment == null || MessagesController.checkCanOpenChat(args, lastFragment)) {
                                            if (isBot && lastFragment != null && lastFragment instanceof ChatActivity && ((ChatActivity) lastFragment).getDialogId() == dialog_id) {
                                                ((ChatActivity) lastFragment).setBotUser(botUser);
                                            } else {
                                                ChatActivity fragment = new ChatActivity(args);
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                                actionBarLayout.presentFragment(fragment, false, true, true);
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
                        }
                    });
                }
            });
        } else if (group != null) {
            if (state == 0) {
                final TLRPC.TL_messages_checkChatInvite req = new TLRPC.TL_messages_checkChatInvite();
                req.hash = group;
                requestId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!LaunchActivity.this.isFinishing()) {
                                    try {
                                        progressDialog.dismiss();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    if (error == null && actionBarLayout != null) {
                                        TLRPC.ChatInvite invite = (TLRPC.ChatInvite) response;
                                        if (invite.chat != null && !ChatObject.isLeftFromChat(invite.chat)) {
                                            MessagesController.getInstance().putChat(invite.chat, false);
                                            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                                            chats.add(invite.chat);
                                            MessagesStorage.getInstance().putUsersAndChats(null, chats, false, true);
                                            Bundle args = new Bundle();
                                            args.putInt("chat_id", invite.chat.id);
                                            if (mainFragmentsStack.isEmpty() || MessagesController.checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                                ChatActivity fragment = new ChatActivity(args);
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                                actionBarLayout.presentFragment(fragment, false, true, true);
                                            }
                                        } else {
                                            if ((invite.chat == null && (!invite.channel || invite.megagroup) || invite.chat != null && (!ChatObject.isChannel(invite.chat) || invite.chat.megagroup)) && !mainFragmentsStack.isEmpty()) {
                                                BaseFragment fragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                                                fragment.showDialog(new JoinGroupAlert(LaunchActivity.this, invite, group, fragment));
                                            } else {
                                                AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                                builder.setMessage(LocaleController.formatString("ChannelJoinTo", R.string.ChannelJoinTo, invite.chat != null ? invite.chat.title : invite.title));
                                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialogInterface, int i) {
                                                        runLinkRequest(username, group, sticker, botUser, botChat, message, hasUrl, messageId, game, instantView, 1);
                                                    }
                                                });
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
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            } else if (state == 1) {
                TLRPC.TL_messages_importChatInvite req = new TLRPC.TL_messages_importChatInvite();
                req.hash = group;
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        if (error == null) {
                            TLRPC.Updates updates = (TLRPC.Updates) response;
                            MessagesController.getInstance().processUpdates(updates, false);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
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
                                                MessagesController.getInstance().putUsers(updates.users, false);
                                                MessagesController.getInstance().putChats(updates.chats, false);
                                                Bundle args = new Bundle();
                                                args.putInt("chat_id", chat.id);
                                                if (mainFragmentsStack.isEmpty() || MessagesController.checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                                                    ChatActivity fragment = new ChatActivity(args);
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                                    actionBarLayout.presentFragment(fragment, false, true, true);
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
                            }
                        });
                    }
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
            fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                @Override
                public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence m, boolean param) {
                    long did = dids.get(0);
                    Bundle args = new Bundle();
                    args.putBoolean("scrollToTopOnResume", true);
                    args.putBoolean("hasUrl", hasUrl);
                    int lower_part = (int) did;
                    int high_id = (int) (did >> 32);
                    if (lower_part != 0) {
                        if (high_id == 1) {
                            args.putInt("chat_id", lower_part);
                        } else {
                            if (lower_part > 0) {
                                args.putInt("user_id", lower_part);
                            } else if (lower_part < 0) {
                                args.putInt("chat_id", -lower_part);
                            }
                        }
                    } else {
                        args.putInt("enc_id", high_id);
                    }
                    if (MessagesController.checkCanOpenChat(args, fragment)) {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                        DraftQuery.saveDraft(did, message, null, null, true);
                        actionBarLayout.presentFragment(new ChatActivity(args), true, false, true);
                    }
                }
            });
            presentFragment(fragment, false, true);
        } else if (instantView != null) {

        }

        if (requestId != 0) {
            final int reqId = requestId;
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ConnectionsManager.getInstance().cancelRequest(reqId, true);
                    try {
                        dialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
            try {
                progressDialog.show();
            } catch (Exception ignore) {

            }
        }
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
                    if (visibleDialog != null && visibleDialog == localeDialog) {
                        try {
                            String shorname = LocaleController.getInstance().getCurrentLocaleInfo().shortName;
                            Toast.makeText(LaunchActivity.this, getStringForLanguageAlert(shorname.equals("en") ? englishLocaleStrings : systemLocaleStrings, "ChangeLanguageLater", R.string.ChangeLanguageLater), Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        localeDialog = null;
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
        long did = dids.get(0);
        int lower_part = (int) did;
        int high_id = (int) (did >> 32);

        Bundle args = new Bundle();
        args.putBoolean("scrollToTopOnResume", true);
        if (!AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
        }
        if (lower_part != 0) {
            if (high_id == 1) {
                args.putInt("chat_id", lower_part);
            } else {
                if (lower_part > 0) {
                    args.putInt("user_id", lower_part);
                } else if (lower_part < 0) {
                    args.putInt("chat_id", -lower_part);
                }
            }
        } else {
            args.putInt("enc_id", high_id);
        }
        if (!MessagesController.checkCanOpenChat(args, dialogsFragment)) {
            return;
        }
        ChatActivity fragment = new ChatActivity(args);

        actionBarLayout.presentFragment(fragment, dialogsFragment != null, dialogsFragment == null, true);
        if (videoPath != null) {
            fragment.openVideoEditor(videoPath, sendingText);
            sendingText = null;
        }

        if (photoPathsArray != null) {
            if (sendingText != null && sendingText.length() <= 200 && photoPathsArray.size() == 1) {
                photoPathsArray.get(0).caption = sendingText;
            }
            SendMessagesHelper.prepareSendingMedia(photoPathsArray, did, null, null, false, false);
        }

        if (sendingText != null) {
            SendMessagesHelper.prepareSendingText(sendingText, did);
        }

        if (documentsPathsArray != null || documentsUrisArray != null) {
            SendMessagesHelper.prepareSendingDocuments(documentsPathsArray, documentsOriginalPathsArray, documentsUrisArray, documentsMimeType, did, null, null);
        }
        if (contactsToSend != null && !contactsToSend.isEmpty()) {
            for (TLRPC.User user : contactsToSend) {
                SendMessagesHelper.getInstance().sendMessage(user, did, null, null, null);
            }
        }

        photoPathsArray = null;
        videoPath = null;
        sendingText = null;
        documentsPathsArray = null;
        documentsOriginalPathsArray = null;
        contactsToSend = null;
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didUpdatedConnectionState);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.openArticle);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.hasNewContactsToImport);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
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
        if (UserConfig.passcodeHash.length() != 0 && UserConfig.lastPauseTime != 0) {
            UserConfig.lastPauseTime = 0;
            UserConfig.saveConfig(false);
        }
        super.onActivityResult(requestCode, resultCode, data);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3 || requestCode == 4 || requestCode == 5 || requestCode == 19 || requestCode == 20) {
            boolean showAlert = true;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == 4) {
                    ImageLoader.getInstance().checkMediaPaths();
                    return;
                } else if (requestCode == 5) {
                    ContactsController.getInstance().forceImportContacts();
                    return;
                } else if (requestCode == 3) {
                    if (MediaController.getInstance().canInAppCamera()) {
                        CameraController.getInstance().initCamera();
                    }
                    return;
                } else if (requestCode == 19 || requestCode == 20) {
                    showAlert = false;
                }
            }
            if (showAlert) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                if (requestCode == 3) {
                    builder.setMessage(LocaleController.getString("PermissionNoAudio", R.string.PermissionNoAudio));
                } else if (requestCode == 4) {
                    builder.setMessage(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
                } else if (requestCode == 5) {
                    builder.setMessage(LocaleController.getString("PermissionContacts", R.string.PermissionContacts));
                } else if (requestCode == 19 || requestCode == 20) {
                    builder.setMessage(LocaleController.getString("PermissionNoCamera", R.string.PermissionNoCamera));
                }
                builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
                return;
            }
        } else if (requestCode == 2) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.locationPermissionGranted);
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

    @Override
    protected void onPause() {
        super.onPause();
        UserConfig.lastAppPauseTime = System.currentTimeMillis();
        ApplicationLoader.mainInterfacePaused = true;
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                ApplicationLoader.mainInterfacePausedStageQueue = true;
                ApplicationLoader.mainInterfacePausedStageQueueTime = 0;
            }
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
        ConnectionsManager.getInstance().setAppPaused(true, false);
        AndroidUtilities.unregisterUpdates();
        if (PhotoViewer.getInstance().isVisible()) {
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
        PhotoViewer.getInstance().destroyPhotoViewer();
        SecretMediaViewer.getInstance().destroyPhotoViewer();
        ArticleViewer.getInstance().destroyArticleViewer();
        StickerPreviewViewer.getInstance().destroy();
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        MediaController.getInstance().setBaseActivity(this, false);
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
        showLanguageAlert(false);
        ApplicationLoader.mainInterfacePaused = false;
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                ApplicationLoader.mainInterfacePausedStageQueue = false;
                ApplicationLoader.mainInterfacePausedStageQueueTime = System.currentTimeMillis();
            }
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
        ConnectionsManager.getInstance().setAppPaused(false, false);
        updateCurrentConnectionState();
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onResume();
        }
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null && MediaController.getInstance().isMessagePaused()) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                MediaController.getInstance().seekToProgress(messageObject, messageObject.audioProgress);
            }
        }
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
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
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
        } else if (id == NotificationCenter.closeOtherAppActivities) {
            if (args[0] != this) {
                onFinish();
                finish();
            }
        } else if (id == NotificationCenter.didUpdatedConnectionState) {
            int state = ConnectionsManager.getInstance().getConnectionState();
            if (currentConnectionState != state) {
                FileLog.d("switch to state " + state);
                currentConnectionState = state;
                updateCurrentConnectionState();
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            drawerLayoutAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.needShowAlert) {
            final Integer reason = (Integer) args[0];
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            if (reason != 2) {
                builder.setNegativeButton(LocaleController.getString("MoreInfo", R.string.MoreInfo), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (!mainFragmentsStack.isEmpty()) {
                            MessagesController.openByUserName("spambot", mainFragmentsStack.get(mainFragmentsStack.size() - 1), 1);
                        }
                    }
                });
            }
            if (reason == 0) {
                builder.setMessage(LocaleController.getString("NobodyLikesSpam1", R.string.NobodyLikesSpam1));
            } else if (reason == 1) {
                builder.setMessage(LocaleController.getString("NobodyLikesSpam2", R.string.NobodyLikesSpam2));
            } else if (reason == 2) {
                builder.setMessage((String) args[1]);
            }
            if (!mainFragmentsStack.isEmpty()) {
                mainFragmentsStack.get(mainFragmentsStack.size() - 1).showDialog(builder.create());
            }
        } else if (id == NotificationCenter.wasUnableToFindCurrentLocation) {
            final HashMap<String, MessageObject> waitingForLocation = (HashMap<String, MessageObject>) args[0];
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            builder.setNegativeButton(LocaleController.getString("ShareYouLocationUnableManually", R.string.ShareYouLocationUnableManually), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (mainFragmentsStack.isEmpty()) {
                        return;
                    }
                    BaseFragment lastFragment = mainFragmentsStack.get(mainFragmentsStack.size() - 1);
                    if (!AndroidUtilities.isGoogleMapsInstalled(lastFragment)) {
                        return;
                    }
                    LocationActivity fragment = new LocationActivity(0);
                    fragment.setDelegate(new LocationActivity.LocationActivityDelegate() {
                        @Override
                        public void didSelectLocation(TLRPC.MessageMedia location, int live) {
                            for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
                                MessageObject messageObject = entry.getValue();
                                SendMessagesHelper.getInstance().sendMessage(location, messageObject.getDialogId(), messageObject, null, null);
                            }
                        }
                    });
                    presentFragment(fragment);
                }
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
            if (UserConfig.passcodeHash.length() > 0 && !UserConfig.allowScreenCapture) {
                try {
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                try {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.reloadInterface) {
            rebuildAllFragments(true);
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
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    ContactsController.getInstance().syncPhoneBookByAlert(contactHashMap, first, schedule, false);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ContactsController.getInstance().syncPhoneBookByAlert(contactHashMap, first, schedule, true);
                }
            });
            builder.setOnBackButtonListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    ContactsController.getInstance().syncPhoneBookByAlert(contactHashMap, first, schedule, true);
                }
            });
            AlertDialog dialog = builder.create();
            fragment.showDialog(dialog);
            dialog.setCanceledOnTouchOutside(false);
        } else if (id == NotificationCenter.didSetNewTheme) {
            if (sideMenu != null) {
                sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
                sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
                sideMenu.getAdapter().notifyDataSetChanged();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    setTaskDescription(new ActivityManager.TaskDescription(null, null, Theme.getColor(Theme.key_actionBarDefault) | 0xff000000));
                } catch (Exception e) {
                    //
                }
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

    private void checkFreeDiscSpace() {
        if (Build.VERSION.SDK_INT >= 26) {
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!UserConfig.isClientActivated()) {
                    return;
                }
                try {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    if (Math.abs(preferences.getLong("last_space_check", 0) - System.currentTimeMillis()) >= 3 * 24 * 3600 * 1000) {
                        File path = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE);
                        if (path == null) {
                            return;
                        }
                        long freeSpace;
                        StatFs statFs = new StatFs(path.getAbsolutePath());
                        if (android.os.Build.VERSION.SDK_INT < 18) {
                            freeSpace = Math.abs(statFs.getAvailableBlocks() * statFs.getBlockSize());
                        } else {
                            freeSpace = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
                        }
                        preferences.edit().putLong("last_space_check", System.currentTimeMillis()).commit();
                        if (freeSpace < 1024 * 1024 * 100) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        AlertsCreator.createFreeSpaceDialog(LaunchActivity.this).show();
                                    } catch (Throwable ignore) {

                                    }
                                }
                            });
                        }
                    }
                } catch (Throwable ignore) {

                }
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
                linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                cells[a].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Integer tag = (Integer) v.getTag();
                        selectedLanguage[0] = ((LanguageCell) v).getCurrentLocale();
                        for (int a = 0; a < cells.length; a++) {
                            cells[a].setLanguageSelected(a == tag);
                        }
                    }
                });
            }
            LanguageCell cell = new LanguageCell(LaunchActivity.this, true);
            cell.setValue(getStringForLanguageAlert(systemLocaleStrings, "ChooseYourLanguageOther", R.string.ChooseYourLanguageOther), getStringForLanguageAlert(englishLocaleStrings, "ChooseYourLanguageOther", R.string.ChooseYourLanguageOther));
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    localeDialog = null;
                    drawerLayoutContainer.closeDrawer(true);
                    presentFragment(new LanguageSelectActivity());
                    if (visibleDialog != null) {
                        visibleDialog.dismiss();
                        visibleDialog = null;
                    }
                }
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            builder.setView(linearLayout);
            builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    LocaleController.getInstance().applyLanguage(selectedLanguage[0], true, false);
                    rebuildAllFragments(true);
                }
            });
            localeDialog = showAlertDialog(builder);
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            preferences.edit().putString("language_showed2", systemLang).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void showLanguageAlert(boolean force) {
        try {
            if (loadingLocaleDialog) {
                return;
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            String showedLang = preferences.getString("language_showed2", "");
            final String systemLang = LocaleController.getSystemLocaleStringIso639().toLowerCase();
            if (!force && showedLang.equals(systemLang)) {
                FileLog.d("alert already showed for " + showedLang);
                return;
            }

            final LocaleController.LocaleInfo infos[] = new LocaleController.LocaleInfo[2];
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
                if (info.shortName.replace("_", "-").equals(systemLang) || info.shortName.equals(arg) || alias != null && info.shortName.equals(alias)) {
                    infos[1] = info;
                }
                if (infos[0] != null && infos[1] != null) {
                    break;
                }
            }
            if (infos[0] == null || infos[1] == null || infos[0] == infos[1]) {
                return;
            }
            FileLog.d("show lang alert for " + infos[0].getKey() + " and " + infos[1].getKey());

            systemLocaleStrings = null;
            englishLocaleStrings = null;
            loadingLocaleDialog = true;

            TLRPC.TL_langpack_getStrings req = new TLRPC.TL_langpack_getStrings();
            req.lang_code = infos[1].shortName.replace("_", "-");
            req.keys.add("English");
            req.keys.add("ChooseYourLanguage");
            req.keys.add("ChooseYourLanguageOther");
            req.keys.add("ChangeLanguageLater");
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    final HashMap<String, String> keys = new HashMap<>();
                    if (response != null) {
                        TLRPC.Vector vector = (TLRPC.Vector) response;
                        for (int a = 0; a < vector.objects.size(); a++) {
                            final TLRPC.LangPackString string = (TLRPC.LangPackString) vector.objects.get(a);
                            keys.put(string.key, string.value);
                        }
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            systemLocaleStrings = keys;
                            if (englishLocaleStrings != null && systemLocaleStrings != null) {
                                showLanguageAlertInternal(infos[1], infos[0], systemLang);
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagWithoutLogin);

            req = new TLRPC.TL_langpack_getStrings();
            req.lang_code = infos[0].shortName.replace("_", "-");
            req.keys.add("English");
            req.keys.add("ChooseYourLanguage");
            req.keys.add("ChooseYourLanguageOther");
            req.keys.add("ChangeLanguageLater");
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    final HashMap<String, String> keys = new HashMap<>();
                    if (response != null) {
                        TLRPC.Vector vector = (TLRPC.Vector) response;
                        for (int a = 0; a < vector.objects.size(); a++) {
                            final TLRPC.LangPackString string = (TLRPC.LangPackString) vector.objects.get(a);
                            keys.put(string.key, string.value);
                        }
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            englishLocaleStrings = keys;
                            if (englishLocaleStrings != null && systemLocaleStrings != null) {
                                showLanguageAlertInternal(infos[1], infos[0], systemLang);
                            }
                        }
                    });
                }
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
        if (UserConfig.passcodeHash.length() != 0) {
            UserConfig.lastPauseTime = ConnectionsManager.getInstance().getCurrentTime();
            lockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (lockRunnable == this) {
                        if (AndroidUtilities.needShowPasscode(true)) {
                            FileLog.e("lock app");
                            showPasscodeActivity();
                        } else {
                            FileLog.e("didn't pass lock check");
                        }
                        lockRunnable = null;
                    }
                }
            };
            if (UserConfig.appLocked) {
                AndroidUtilities.runOnUIThread(lockRunnable, 1000);
            } else if (UserConfig.autoLockIn != 0) {
                AndroidUtilities.runOnUIThread(lockRunnable, (long) UserConfig.autoLockIn * 1000 + 1000);
            }
        } else {
            UserConfig.lastPauseTime = 0;
        }
        UserConfig.saveConfig(false);
    }

    private void onPasscodeResume() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (AndroidUtilities.needShowPasscode(true)) {
            showPasscodeActivity();
        }
        if (UserConfig.lastPauseTime != 0) {
            UserConfig.lastPauseTime = 0;
            UserConfig.saveConfig(false);
        }
    }

    private void updateCurrentConnectionState() {
        String title = null;
        String subtitle = null;
        Runnable action = null;
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            title = LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            title = LocaleController.getString("Connecting", R.string.Connecting);
            action = new Runnable() {
                @Override
                public void run() {
                    if (AndroidUtilities.isTablet()) {
                        if (!layerFragmentsStack.isEmpty() && layerFragmentsStack.get(layerFragmentsStack.size() - 1) instanceof ProxySettingsActivity) {
                            return;
                        }
                    } else {
                        if (!mainFragmentsStack.isEmpty() && mainFragmentsStack.get(mainFragmentsStack.size() - 1) instanceof ProxySettingsActivity) {
                            return;
                        }
                    }
                    presentFragment(new ProxySettingsActivity());
                }
            };
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            title = LocaleController.getString("Updating", R.string.Updating);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            title = LocaleController.getString("ConnectingToProxy", R.string.ConnectingToProxy);
            subtitle = LocaleController.getString("ConnectingToProxyTapToDisable", R.string.ConnectingToProxyTapToDisable);
            action = new Runnable() {
                @Override
                public void run() {
                    if (actionBarLayout == null || actionBarLayout.fragmentsStack.isEmpty()) {
                        return;
                    }
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    BaseFragment fragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                    AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                    builder.setTitle(LocaleController.getString("Proxy", R.string.Proxy));
                    builder.setMessage(LocaleController.formatString("ConnectingToProxyDisableAlert", R.string.ConnectingToProxyDisableAlert, preferences.getString("proxy_ip", "")));
                    builder.setPositiveButton(LocaleController.getString("ConnectingToProxyDisable", R.string.ConnectingToProxyDisable), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit();
                            editor.putBoolean("proxy_enabled", false);
                            editor.commit();
                            ConnectionsManager.native_setProxySettings("", 0, "", "");
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    fragment.showDialog(builder.create());
                }
            };
        }
        actionBarLayout.setTitleOverlayText(title, subtitle, action);
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
                } else if (lastFragment instanceof WallpapersActivity) {
                    outState.putString("fragment", "wallpapers");
                } else if (lastFragment instanceof ProfileActivity && ((ProfileActivity) lastFragment).isChat() && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat_profile");
                } else if (lastFragment instanceof ChannelCreateActivity && args != null && args.getInt("step") == 0) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "channel");
                } else if (lastFragment instanceof ChannelEditActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "edit");
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
        if (SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(true, false);
        } else if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
        } else if (ArticleViewer.getInstance().isVisible()) {
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
        if (SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(true, false);
            return true;
        } else if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
            return true;
        } else if (ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(true, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !UserConfig.isWaitingForPasscodeEnter) {
            if (PhotoViewer.getInstance().isVisible()) {
                return super.onKeyUp(keyCode, event);
            } else if (ArticleViewer.getInstance().isVisible()) {
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
        if (ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        if (AndroidUtilities.isTablet()) {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity) && layersActionBarLayout.getVisibility() != View.VISIBLE, true);
            if (fragment instanceof DialogsActivity) {
                DialogsActivity dialogsActivity = (DialogsActivity) fragment;
                if (dialogsActivity.isMainDialogList() && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false);
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
            if (fragment instanceof ChatActivity) {
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
                        actionBarLayout.presentFragment(fragment, false, forceWithoutAnimation, false);
                    }
                    return result;
                } else if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    backgroundTablet.setVisibility(View.GONE);
                    rightActionBarLayout.removeAllFragments();
                    rightActionBarLayout.presentFragment(fragment, removeLast, true, false);
                    if (!layersActionBarLayout.fragmentsStack.isEmpty()) {
                        for (int a = 0; a < layersActionBarLayout.fragmentsStack.size() - 1; a++) {
                            layersActionBarLayout.removeFragmentFromStack(layersActionBarLayout.fragmentsStack.get(0));
                            a--;
                        }
                        layersActionBarLayout.closeLastFragment(!forceWithoutAnimation);
                    }
                    return false;
                } else if (tabletFullSize && layout != actionBarLayout) {
                    actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false);
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
                    actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false);
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
                layersActionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false);
                return false;
            }
            return true;
        } else {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity), false);
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
            } else if (fragment instanceof ChatActivity) {
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
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity), false);
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
            layersActionBarLayout.rebuildAllFragmentViews(last, true);
        } else {
            actionBarLayout.rebuildAllFragmentViews(last, true);
        }
    }

    @Override
    public void onRebuildAllFragments(ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (layout == layersActionBarLayout) {
                rightActionBarLayout.rebuildAllFragmentViews(true, true);
                actionBarLayout.rebuildAllFragmentViews(true, true);
            }
        }
        drawerLayoutAdapter.notifyDataSetChanged();
    }
}

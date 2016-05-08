/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLoader;
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
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
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
    private ArrayList<Uri> photoPathsArray;
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
    private ImageView backgroundTablet;
    protected DrawerLayoutContainer drawerLayoutContainer;
    private DrawerLayoutAdapter drawerLayoutAdapter;
    private PasscodeView passcodeView;
    private AlertDialog visibleDialog;

    private Intent passcodeSaveIntent;
    private boolean passcodeSaveIntentIsNew;
    private boolean passcodeSaveIntentIsRestore;

    private boolean tabletFullSize;

    private Runnable lockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();
        NativeCrashManager.handleDumpFiles(this);

        if (!UserConfig.isClientActivated()) {
            Intent intent = getIntent();
            if (intent != null && intent.getAction() != null && (Intent.ACTION_SEND.equals(intent.getAction()) || intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))) {
                super.onCreate(savedInstanceState);
                finish();
                return;
            }
            if (intent != null && !intent.getBooleanExtra("fromIntro", false)) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", MODE_PRIVATE);
                Map<String, ?> state = preferences.getAll();
                if (state.isEmpty()) {
                    Intent intent2 = new Intent(this, IntroActivity.class);
                    startActivity(intent2);
                    super.onCreate(savedInstanceState);
                    finish();
                    return;
                }
            }
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);

        super.onCreate(savedInstanceState);
        Theme.loadRecources(this);

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

            RelativeLayout launchLayout = new RelativeLayout(this);
            drawerLayoutContainer.addView(launchLayout);
            FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) launchLayout.getLayoutParams();
            layoutParams1.width = LayoutHelper.MATCH_PARENT;
            layoutParams1.height = LayoutHelper.MATCH_PARENT;
            launchLayout.setLayoutParams(layoutParams1);

            backgroundTablet = new ImageView(this);
            backgroundTablet.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backgroundTablet.setImageResource(R.drawable.cats);
            launchLayout.addView(backgroundTablet);
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) backgroundTablet.getLayoutParams();
            relativeLayoutParams.width = LayoutHelper.MATCH_PARENT;
            relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
            backgroundTablet.setLayoutParams(relativeLayoutParams);

            launchLayout.addView(actionBarLayout);
            relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
            relativeLayoutParams.width = LayoutHelper.MATCH_PARENT;
            relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
            actionBarLayout.setLayoutParams(relativeLayoutParams);

            rightActionBarLayout = new ActionBarLayout(this);
            launchLayout.addView(rightActionBarLayout);
            relativeLayoutParams = (RelativeLayout.LayoutParams)rightActionBarLayout.getLayoutParams();
            relativeLayoutParams.width = AndroidUtilities.dp(320);
            relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
            rightActionBarLayout.setLayoutParams(relativeLayoutParams);
            rightActionBarLayout.init(rightFragmentsStack);
            rightActionBarLayout.setDelegate(this);

            shadowTabletSide = new FrameLayout(this);
            shadowTabletSide.setBackgroundColor(0x40295274);
            launchLayout.addView(shadowTabletSide);
            relativeLayoutParams = (RelativeLayout.LayoutParams) shadowTabletSide.getLayoutParams();
            relativeLayoutParams.width = AndroidUtilities.dp(1);
            relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
            shadowTabletSide.setLayoutParams(relativeLayoutParams);

            shadowTablet = new FrameLayout(this);
            shadowTablet.setVisibility(View.GONE);
            shadowTablet.setBackgroundColor(0x7F000000);
            launchLayout.addView(shadowTablet);
            relativeLayoutParams = (RelativeLayout.LayoutParams) shadowTablet.getLayoutParams();
            relativeLayoutParams.width = LayoutHelper.MATCH_PARENT;
            relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
            shadowTablet.setLayoutParams(relativeLayoutParams);
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
            launchLayout.addView(layersActionBarLayout);
            relativeLayoutParams = (RelativeLayout.LayoutParams)layersActionBarLayout.getLayoutParams();
            relativeLayoutParams.width = AndroidUtilities.dp(530);
            relativeLayoutParams.height = AndroidUtilities.dp(528);
            layersActionBarLayout.setLayoutParams(relativeLayoutParams);
            layersActionBarLayout.init(layerFragmentsStack);
            layersActionBarLayout.setDelegate(this);
            layersActionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
            layersActionBarLayout.setVisibility(View.GONE);
        } else {
            drawerLayoutContainer.addView(actionBarLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        ListView listView = new ListView(this) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        listView.setBackgroundColor(0xffffffff);
        listView.setAdapter(drawerLayoutAdapter = new DrawerLayoutAdapter(this));
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        drawerLayoutContainer.setDrawerLayout(listView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet() ? AndroidUtilities.dp(320) : Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56);
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        listView.setLayoutParams(layoutParams);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 2) {
                    if (!MessagesController.isFeatureEnabled("chat_create", actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1))) {
                        return;
                    }
                    presentFragment(new GroupCreateActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (position == 3) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlyUsers", true);
                    args.putBoolean("destroyAfterSelect", true);
                    args.putBoolean("createSecretChat", true);
                    args.putBoolean("allowBots", false);
                    presentFragment(new ContactsActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (position == 4) {
                    if (!MessagesController.isFeatureEnabled("broadcast_create", actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1))) {
                        return;
                    }
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    if (preferences.getBoolean("channel_intro", false)) {
                        Bundle args = new Bundle();
                        args.putInt("step", 0);
                        presentFragment(new ChannelCreateActivity(args));
                    } else {
                        presentFragment(new ChannelIntroActivity());
                        preferences.edit().putBoolean("channel_intro", true).commit();
                    }
                    drawerLayoutContainer.closeDrawer(false);
                } else if (position == 6) {
                    presentFragment(new ContactsActivity(null));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (position == 7) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, ContactsController.getInstance().getInviteText());
                        startActivityForResult(Intent.createChooser(intent, LocaleController.getString("InviteFriends", R.string.InviteFriends)), 500);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    drawerLayoutContainer.closeDrawer(false);
                } else if (position == 8) {
                    presentFragment(new SettingsActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (position == 9) {
                    Browser.openUrl(LaunchActivity.this, LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl));
                    drawerLayoutContainer.closeDrawer(false);
                }
            }
        });

        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
        actionBarLayout.init(mainFragmentsStack);
        actionBarLayout.setDelegate(this);

        ApplicationLoader.loadWallpaper();

        passcodeView = new PasscodeView(this);
        drawerLayoutContainer.addView(passcodeView);
        FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) passcodeView.getLayoutParams();
        layoutParams1.width = LayoutHelper.MATCH_PARENT;
        layoutParams1.height = LayoutHelper.MATCH_PARENT;
        passcodeView.setLayoutParams(layoutParams1);

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);
        currentConnectionState = ConnectionsManager.getInstance().getConnectionState();

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didUpdatedConnectionState);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.needShowAlert);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.wasUnableToFindCurrentLocation);
        if (Build.VERSION.SDK_INT < 14) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.screenStateChanged);
        }

        if (actionBarLayout.fragmentsStack.isEmpty()) {
            if (!UserConfig.isClientActivated()) {
                actionBarLayout.addFragmentToStack(new LoginActivity());
                drawerLayoutContainer.setAllowOpenDrawer(false, false);
            } else {
                actionBarLayout.addFragmentToStack(new DialogsActivity(null));
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
                FileLog.e("tmessages", e);
            }
        } else {
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

        handleIntent(getIntent(), false, savedInstanceState != null, false);
        needLayout();

        final View view = getWindow().getDecorView().getRootView();
        view.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int height = view.getMeasuredHeight();
                if (height > AndroidUtilities.dp(100) && height < AndroidUtilities.displaySize.y && height + AndroidUtilities.dp(100) > AndroidUtilities.displaySize.y) {
                    AndroidUtilities.displaySize.y = height;
                    FileLog.e("tmessages", "fix display size y to " + AndroidUtilities.displaySize.y);
                }
            }
        });
    }

    private void showPasscodeActivity() {
        if (passcodeView == null) {
            return;
        }
        UserConfig.appLocked = true;
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
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

    private boolean handleIntent(Intent intent, boolean isNew, boolean restore, boolean fromPassword) {
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
            Integer open_settings = 0;
            long dialogId = intent != null && intent.getExtras() != null ? intent.getExtras().getLong("dialogId", 0) : 0;
            boolean showDialogsList = false;
            boolean showPlayer = false;

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

                                    String name = null;
                                    String nameEncoding = null;
                                    String nameCharset = null;
                                    ArrayList<String> phones = new ArrayList<>();
                                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                                    String line;
                                    while ((line = bufferedReader.readLine()) != null) {
                                        String[] args = line.split(":");
                                        if (args.length != 2) {
                                            continue;
                                        }
                                        if (args[0].startsWith("FN")) {
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
                                            name = args[1];
                                            if (nameEncoding != null && nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                                                while (name.endsWith("=") && nameEncoding != null) {
                                                    name = name.substring(0, name.length() - 1);
                                                    line = bufferedReader.readLine();
                                                    if (line == null) {
                                                        break;
                                                    }
                                                    name += line;
                                                }
                                                byte[] bytes = AndroidUtilities.decodeQuotedPrintable(name.getBytes());
                                                if (bytes != null && bytes.length != 0) {
                                                    String decodedName = new String(bytes, nameCharset);
                                                    if (decodedName != null) {
                                                        name = decodedName;
                                                    }
                                                }
                                            }
                                        } else if (args[0].startsWith("TEL")) {
                                            String phone = PhoneFormat.stripExceptNumbers(args[1], true);
                                            if (phone.length() > 0) {
                                                phones.add(phone);
                                            }
                                        }
                                    }
                                    try {
                                        bufferedReader.close();
                                        stream.close();
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                    if (name != null && !phones.isEmpty()) {
                                        contactsToSend = new ArrayList<>();
                                        for (String phone : phones) {
                                            TLRPC.User user = new TLRPC.TL_userContact_old2();
                                            user.phone = phone;
                                            user.first_name = name;
                                            user.last_name = "";
                                            user.id = 0;
                                            contactsToSend.add(user);
                                        }
                                    }
                                } else {
                                    error = true;
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
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
                                    if (isInternalUri(uri)) {
                                        error = true;
                                    }
                                }
                                if (!error) {
                                    if (uri != null && (type != null && type.startsWith("image/") || uri.toString().toLowerCase().endsWith(".jpg"))) {
                                        if (photoPathsArray == null) {
                                            photoPathsArray = new ArrayList<>();
                                        }
                                        photoPathsArray.add(uri);
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
                                    if (sendingText != null) {
                                        if (sendingText.contains("WhatsApp")) { //remove unnecessary caption 'sent from WhatsApp' from photos forwarded from WhatsApp
                                            sendingText = null;
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
                                        if (isInternalUri(uri)) {
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
                                        photoPathsArray.add(uri);
                                    }
                                } else {
                                    for (int a = 0; a < uris.size(); a++) {
                                        Parcelable parcelable = uris.get(a);
                                        if (!(parcelable instanceof Uri)) {
                                            parcelable = Uri.parse(parcelable.toString());
                                        }
                                        String path = AndroidUtilities.getPath((Uri) parcelable);
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
                                        }
                                    }
                                }
                            } else {
                                error = true;
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
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
                            String botUser = null;
                            String botChat = null;
                            String message = null;
                            Integer messageId = null;
                            boolean hasUrl = false;
                            String scheme = data.getScheme();
                            if (scheme != null) {
                                if ((scheme.equals("http") || scheme.equals("https"))) {
                                    String host = data.getHost().toLowerCase();
                                    if (host.equals("telegram.me") || host.equals("telegram.dog")) {
                                        String path = data.getPath();
                                        if (path != null && path.length() > 1) {
                                            path = path.substring(1);
                                            if (path.startsWith("joinchat/")) {
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
                                    }
                                }
                            }
                            if (username != null || group != null || sticker != null || message != null) {
                                runLinkRequest(username, group, sticker, botUser, botChat, message, hasUrl, messageId, 0);
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
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    } else if (intent.getAction().equals("org.telegram.messenger.OPEN_ACCOUNT")) {
                        open_settings = 1;
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
                    }
                }
            }

            if (push_user_id != 0) {
                Bundle args = new Bundle();
                args.putInt("user_id", push_user_id);
                if (mainFragmentsStack.isEmpty() || MessagesController.checkCanOpenChat(args, mainFragmentsStack.get(mainFragmentsStack.size() - 1))) {
                    ChatActivity fragment = new ChatActivity(args);
                    if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                        pushOpened = true;
                    }
                }
            } else if (push_chat_id != 0) {
                Bundle args = new Bundle();
                args.putInt("chat_id", push_chat_id);
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
                if (AndroidUtilities.isTablet()) {
                    for (int a = 0; a < layersActionBarLayout.fragmentsStack.size(); a++) {
                        BaseFragment fragment = layersActionBarLayout.fragmentsStack.get(a);
                        if (fragment instanceof AudioPlayerActivity) {
                            layersActionBarLayout.removeFragmentFromStack(fragment);
                            break;
                        }
                    }
                    actionBarLayout.showLastFragment();
                    rightActionBarLayout.showLastFragment();
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    for (int a = 0; a < actionBarLayout.fragmentsStack.size(); a++) {
                        BaseFragment fragment = actionBarLayout.fragmentsStack.get(a);
                        if (fragment instanceof AudioPlayerActivity) {
                            actionBarLayout.removeFragmentFromStack(fragment);
                            break;
                        }
                    }
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
                actionBarLayout.presentFragment(new AudioPlayerActivity(), false, true, true);
                pushOpened = true;
            } else if (videoPath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null || documentsUrisArray != null) {
                if (!AndroidUtilities.isTablet()) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                }
                if (dialogId == 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
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
                    if (PhotoViewer.getInstance().isVisible()) {
                        PhotoViewer.getInstance().closePhoto(false, true);
                    }

                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.showLastFragment();
                        rightActionBarLayout.showLastFragment();
                    } else {
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                } else {
                    didSelectDialog(null, dialogId, false);
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
                            actionBarLayout.addFragmentToStack(new DialogsActivity(null));
                            drawerLayoutContainer.setAllowOpenDrawer(true, false);
                        }
                    }
                } else {
                    if (actionBarLayout.fragmentsStack.isEmpty()) {
                        if (!UserConfig.isClientActivated()) {
                            actionBarLayout.addFragmentToStack(new LoginActivity());
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                        } else {
                            actionBarLayout.addFragmentToStack(new DialogsActivity(null));
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

    private boolean isInternalUri(Uri uri) {
        String pathString = uri.getPath();
        if (pathString == null) {
            return false;
        }
        while (true) {
            String newPath = Utilities.readlink(pathString);
            if (newPath == null || newPath.equals(pathString)) {
                break;
            }
            pathString = newPath;
        }
        return pathString != null && pathString.toLowerCase().contains("/data/data/" + getPackageName() + "/files");
    }

    private void runLinkRequest(final String username, final String group, final String sticker, final String botUser, final String botChat, final String message, final boolean hasUrl, final Integer messageId, final int state) {
        final ProgressDialog progressDialog = new ProgressDialog(this);
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
                                    FileLog.e("tmessages", e);
                                }
                                if (error == null && actionBarLayout != null) {
                                    final TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                    MessagesController.getInstance().putUsers(res.users, false);
                                    MessagesController.getInstance().putChats(res.chats, false);
                                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, true);

                                    if (botChat != null) {
                                        final TLRPC.User user = !res.users.isEmpty() ? res.users.get(0) : null;
                                        if (user == null || user.bot && user.bot_nochats) {
                                            try {
                                                Toast.makeText(LaunchActivity.this, LocaleController.getString("BotCantJoinGroups", R.string.BotCantJoinGroups), Toast.LENGTH_SHORT).show();
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
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
                                            public void didSelectDialog(DialogsActivity fragment, long did, boolean param) {
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
                                        FileLog.e("tmessages", e);
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
                                        FileLog.e("tmessages", e);
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
                                            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.this);
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            if (!invite.megagroup && invite.channel || ChatObject.isChannel(invite.chat) && !invite.chat.megagroup) {
                                                builder.setMessage(LocaleController.formatString("ChannelJoinTo", R.string.ChannelJoinTo, invite.chat != null ? invite.chat.title : invite.title));
                                            } else {
                                                builder.setMessage(LocaleController.formatString("JoinToGroup", R.string.JoinToGroup, invite.chat != null ? invite.chat.title : invite.title));
                                            }
                                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    runLinkRequest(username, group, sticker, botUser, botChat, message, hasUrl, messageId, 1);
                                                }
                                            });
                                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                            showAlertDialog(builder);
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
                                        FileLog.e("tmessages", e);
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
                mainFragmentsStack.get(mainFragmentsStack.size() - 1).showDialog(new StickersAlert(LaunchActivity.this, stickerset, null, null));
            }
            return;
        } else if (message != null) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                @Override
                public void didSelectDialog(DialogsActivity fragment, long did, boolean param) {
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
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("dialog_" + did, message);
                        editor.commit();
                        actionBarLayout.presentFragment(new ChatActivity(args), true, false, true);
                    }
                }
            });
            presentFragment(fragment, false, true);
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
                        FileLog.e("tmessages", e);
                    }
                }
            });
            progressDialog.show();
        }
    }

    public AlertDialog showAlertDialog(AlertDialog.Builder builder) {
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            visibleDialog = builder.show();
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    visibleDialog = null;
                }
            });
            return visibleDialog;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false, false);
    }

    @Override
    public void didSelectDialog(DialogsActivity dialogsFragment, long dialog_id, boolean param) {
        if (dialog_id != 0) {
            int lower_part = (int)dialog_id;
            int high_id = (int)(dialog_id >> 32);

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

            if (videoPath != null) {
                if(android.os.Build.VERSION.SDK_INT >= 16) {
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.presentFragment(fragment, false, true, true);
                    } else {
                        actionBarLayout.addFragmentToStack(fragment, actionBarLayout.fragmentsStack.size() - 1);
                    }

                    if (!fragment.openVideoEditor(videoPath, dialogsFragment != null, false) && dialogsFragment != null) {
                        if (!AndroidUtilities.isTablet()) {
                            dialogsFragment.finishFragment(true);
                        }
                    }
                } else {
                    actionBarLayout.presentFragment(fragment, dialogsFragment != null, dialogsFragment == null, true);
                    SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id, null, true);
                }
            } else {
                actionBarLayout.presentFragment(fragment, dialogsFragment != null, dialogsFragment == null, true);

                if (sendingText != null) {
                    SendMessagesHelper.prepareSendingText(sendingText, dialog_id, true);
                }

                if (photoPathsArray != null) {
                    SendMessagesHelper.prepareSendingPhotos(null, photoPathsArray, dialog_id, null, null, true);
                }
                if (documentsPathsArray != null || documentsUrisArray != null) {
                    SendMessagesHelper.prepareSendingDocuments(documentsPathsArray, documentsOriginalPathsArray, documentsUrisArray, documentsMimeType, dialog_id, null, true);
                }
                if (contactsToSend != null && !contactsToSend.isEmpty()) {
                    for (TLRPC.User user : contactsToSend) {
                        SendMessagesHelper.getInstance().sendMessage(user, dialog_id, null, true, null, null);
                    }
                }
            }

            photoPathsArray = null;
            videoPath = null;
            sendingText = null;
            documentsPathsArray = null;
            documentsOriginalPathsArray = null;
            contactsToSend = null;
        }
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
        if (Build.VERSION.SDK_INT < 14) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.screenStateChanged);
        }
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
    }

    public void needLayout() {
        if (AndroidUtilities.isTablet()) {
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) layersActionBarLayout.getLayoutParams();
            relativeLayoutParams.leftMargin = (AndroidUtilities.displaySize.x - relativeLayoutParams.width) / 2;
            int y = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            relativeLayoutParams.topMargin = y + (AndroidUtilities.displaySize.y - relativeLayoutParams.height - y) / 2;
            layersActionBarLayout.setLayoutParams(relativeLayoutParams);


            if (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                tabletFullSize = false;
                int leftWidth = AndroidUtilities.displaySize.x / 100 * 35;
                if (leftWidth < AndroidUtilities.dp(320)) {
                    leftWidth = AndroidUtilities.dp(320);
                }

                relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = leftWidth;
                relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) shadowTabletSide.getLayoutParams();
                relativeLayoutParams.leftMargin = leftWidth;
                shadowTabletSide.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) rightActionBarLayout.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.displaySize.x - leftWidth;
                relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
                relativeLayoutParams.leftMargin = leftWidth;
                rightActionBarLayout.setLayoutParams(relativeLayoutParams);

                if (AndroidUtilities.isSmallTablet() && actionBarLayout.fragmentsStack.size() >= 2) {
                    for (int a = 1; a < actionBarLayout.fragmentsStack.size(); a++) {
                        BaseFragment chatFragment = actionBarLayout.fragmentsStack.get(a);
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

                relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = LayoutHelper.MATCH_PARENT;
                relativeLayoutParams.height = LayoutHelper.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                shadowTabletSide.setVisibility(View.GONE);
                rightActionBarLayout.setVisibility(View.GONE);
                backgroundTablet.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);

                if (!rightActionBarLayout.fragmentsStack.isEmpty()) {
                    for (int a = 0; a < rightActionBarLayout.fragmentsStack.size(); a++) {
                        BaseFragment chatFragment = rightActionBarLayout.fragmentsStack.get(a);
                        chatFragment.onPause();
                        rightActionBarLayout.fragmentsStack.remove(a);
                        actionBarLayout.fragmentsStack.add(chatFragment);
                        a--;
                    }
                    if (passcodeView.getVisibility() != View.VISIBLE) {
                        actionBarLayout.showLastFragment();
                    }
                }
            }
        }
    }

    public void fixLayout() {
        if (!AndroidUtilities.isTablet()) {
            return;
        }
        if (actionBarLayout == null) {
            return;
        }
        actionBarLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        needLayout();
                    }
                });
                if (actionBarLayout != null) {
                    if (Build.VERSION.SDK_INT < 16) {
                        actionBarLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        actionBarLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (UserConfig.passcodeHash.length() != 0 && UserConfig.lastPauseTime != 0) {
            UserConfig.lastPauseTime = 0;
            UserConfig.saveConfig(false);
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        if (requestCode == 3 || requestCode == 4 || requestCode == 5) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == 4) {
                    ImageLoader.getInstance().checkMediaPaths();
                } else if (requestCode == 5) {
                    ContactsController.getInstance().readContacts();
                }
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            if (requestCode == 3) {
                builder.setMessage(LocaleController.getString("PermissionNoAudio", R.string.PermissionNoAudio));
            } else if (requestCode == 4) {
                builder.setMessage(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            } else if (requestCode == 5) {
                builder.setMessage(LocaleController.getString("PermissionContacts", R.string.PermissionContacts));
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
                        FileLog.e("tmessages", e);
                    }
                }
            });
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            builder.show();
            return;
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
        ApplicationLoader.mainInterfacePaused = true;
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
        SecretPhotoViewer.getInstance().destroyPhotoViewer();
        StickerPreviewViewer.getInstance().destroy();
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            if (onGlobalLayoutListener != null) {
                final View view = getWindow().getDecorView().getRootView();
                if (Build.VERSION.SDK_INT < 16) {
                    view.getViewTreeObserver().removeGlobalOnLayoutListener(onGlobalLayoutListener);
                } else {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        super.onDestroy();
        onFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApplicationLoader.mainInterfacePaused = false;
        onPasscodeResume();
        if (passcodeView.getVisibility() != View.VISIBLE) {
            actionBarLayout.onResume();
            if (AndroidUtilities.isTablet()) {
                rightActionBarLayout.onResume();
                layersActionBarLayout.onResume();
            }
        } else {
            passcodeView.onResume();
        }
        AndroidUtilities.checkForCrashes(this);
        AndroidUtilities.checkForUpdates(this);
        ConnectionsManager.getInstance().setAppPaused(false, false);
        updateCurrentConnectionState();
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onResume();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        AndroidUtilities.checkDisplaySize();
        super.onConfigurationChanged(newConfig);
        fixLayout();
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
                FileLog.d("tmessages", "switch to state " + state);
                currentConnectionState = state;
                updateCurrentConnectionState();
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            drawerLayoutAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.screenStateChanged) {
            if (!ApplicationLoader.mainInterfacePaused) {
                if (!ApplicationLoader.isScreenOn) {
                    onPasscodePause();
                } else {
                    onPasscodeResume();
                }
            }
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
                    LocationActivity fragment = new LocationActivity();
                    fragment.setDelegate(new LocationActivity.LocationActivityDelegate() {
                        @Override
                        public void didSelectLocation(TLRPC.MessageMedia location) {
                            for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
                                MessageObject messageObject = entry.getValue();
                                SendMessagesHelper.getInstance().sendMessage(location, messageObject.getDialogId(), messageObject, false, null, null);
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
                            FileLog.e("tmessages", "lock app");
                            showPasscodeActivity();
                        } else {
                            FileLog.e("tmessages", "didn't pass lock check");
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
        String text = null;
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            text = LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            text = LocaleController.getString("Connecting", R.string.Connecting);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            text = LocaleController.getString("Updating", R.string.Updating);
        }
        actionBarLayout.setTitleOverlayText(text);
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
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (passcodeView.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
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
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !UserConfig.isWaitingForPasscodeEnter) {
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
        if (AndroidUtilities.isTablet()) {
            drawerLayoutContainer.setAllowOpenDrawer(!(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity) && layersActionBarLayout.getVisibility() != View.VISIBLE, true);
            if (fragment instanceof DialogsActivity) {
                DialogsActivity dialogsActivity = (DialogsActivity)fragment;
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
                    shadowTablet.setBackgroundColor(0x7F000000);
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
                DialogsActivity dialogsActivity = (DialogsActivity)fragment;
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
                    shadowTablet.setBackgroundColor(0x7F000000);
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
        }
        return true;
    }

    @Override
    public void onRebuildAllFragments(ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (layout == layersActionBarLayout) {
                rightActionBarLayout.rebuildAllFragmentViews(true);
                rightActionBarLayout.showLastFragment();
                actionBarLayout.rebuildAllFragmentViews(true);
                actionBarLayout.showLastFragment();
            }
        }
        drawerLayoutAdapter.notifyDataSetChanged();
    }
}

/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.ActionBar.ActionBarLayout;
import org.telegram.ui.Views.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

public class LaunchActivity extends Activity implements ActionBarLayout.ActionBarLayoutDelegate, NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate {
    private boolean finished = false;
    private String videoPath = null;
    private String sendingText = null;
    private ArrayList<Uri> photoPathsArray = null;
    private ArrayList<String> documentsPathsArray = null;
    private ArrayList<String> documentsOriginalPathsArray = null;
    private ArrayList<TLRPC.User> contactsToSend = null;
    private int currentConnectionState;
    private static ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<BaseFragment>();
    private static ArrayList<BaseFragment> layerFragmentsStack = new ArrayList<BaseFragment>();
    private static ArrayList<BaseFragment> rightFragmentsStack = new ArrayList<BaseFragment>();

    private ActionBarLayout actionBarLayout = null;
    private ActionBarLayout layersActionBarLayout = null;
    private ActionBarLayout rightActionBarLayout = null;
    private FrameLayout shadowTablet = null;
    private LinearLayout buttonLayoutTablet = null;
    private FrameLayout shadowTabletSide = null;
    private ImageView backgroundTablet = null;
    private boolean tabletFullSize = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();

        if (!UserConfig.isClientActivated()) {
            Intent intent = getIntent();
            if (intent != null && intent.getAction() != null && (Intent.ACTION_SEND.equals(intent.getAction()) || intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))) {
                super.onCreate(savedInstanceState);
                finish();
                return;
            }
            if (intent != null && !intent.getBooleanExtra("fromIntro", false)) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", MODE_PRIVATE);
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

        actionBarLayout = new ActionBarLayout(this);
        if (AndroidUtilities.isTablet()) {
            setContentView(R.layout.launch_layout_tablet);
            shadowTablet = (FrameLayout)findViewById(R.id.shadow_tablet);
            buttonLayoutTablet = (LinearLayout)findViewById(R.id.launch_button_layout);
            shadowTabletSide = (FrameLayout)findViewById(R.id.shadow_tablet_side);
            backgroundTablet = (ImageView)findViewById(R.id.launch_background);

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

                        if (x > viewX && x < viewX + layersActionBarLayout.getWidth() && y > viewY && y < viewY + layersActionBarLayout.getHeight()) {
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

            RelativeLayout launchLayout = (RelativeLayout)findViewById(R.id.launch_layout);

            layersActionBarLayout = new ActionBarLayout(this);
            layersActionBarLayout.setBackgroundView(shadowTablet);
            layersActionBarLayout.setUseAlphaAnimations(true);
            layersActionBarLayout.setBackgroundResource(R.drawable.boxshadow);
            launchLayout.addView(layersActionBarLayout);
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams)layersActionBarLayout.getLayoutParams();
            relativeLayoutParams.width = AndroidUtilities.dp(498);
            relativeLayoutParams.height = AndroidUtilities.dp(528);
            relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            layersActionBarLayout.setLayoutParams(relativeLayoutParams);
            layersActionBarLayout.init(layerFragmentsStack);
            layersActionBarLayout.setDelegate(this);
            layersActionBarLayout.setVisibility(View.GONE);

            launchLayout.addView(actionBarLayout, 2);
            relativeLayoutParams = (RelativeLayout.LayoutParams)actionBarLayout.getLayoutParams();
            relativeLayoutParams.width = AndroidUtilities.dp(320);
            relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            actionBarLayout.setLayoutParams(relativeLayoutParams);

            rightActionBarLayout = new ActionBarLayout(this);
            launchLayout.addView(rightActionBarLayout, 3);
            relativeLayoutParams = (RelativeLayout.LayoutParams)rightActionBarLayout.getLayoutParams();
            relativeLayoutParams.width = AndroidUtilities.dp(320);
            relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            rightActionBarLayout.setLayoutParams(relativeLayoutParams);
            rightActionBarLayout.init(rightFragmentsStack);
            rightActionBarLayout.setDelegate(this);

            TextView button = (TextView)findViewById(R.id.new_group_button);
            button.setText(LocaleController.getString("NewGroup", R.string.NewGroup));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presentFragment(new GroupCreateActivity());
                }
            });

            button = (TextView)findViewById(R.id.new_secret_button);
            button.setText(LocaleController.getString("NewSecretChat", R.string.NewSecretChat));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlyUsers", true);
                    args.putBoolean("destroyAfterSelect", true);
                    args.putBoolean("usersAsSections", true);
                    args.putBoolean("createSecretChat", true);
                    presentFragment(new ContactsActivity(args));
                }
            });

            button = (TextView)findViewById(R.id.new_broadcast_button);
            button.setText(LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle args = new Bundle();
                    args.putBoolean("broadcast", true);
                    presentFragment(new GroupCreateActivity(args));
                }
            });

            button = (TextView)findViewById(R.id.contacts_button);
            button.setText(LocaleController.getString("Contacts", R.string.Contacts));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presentFragment(new ContactsActivity(null));
                }
            });

            button = (TextView)findViewById(R.id.settings_button);
            button.setText(LocaleController.getString("Settings", R.string.Settings));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presentFragment(new SettingsActivity());
                }
            });
        } else {
            setContentView(actionBarLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        actionBarLayout.init(mainFragmentsStack);
        actionBarLayout.setDelegate(this);

        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            AndroidUtilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);
        currentConnectionState = ConnectionsManager.getInstance().getConnectionState();

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didUpdatedConnectionState);

        if (actionBarLayout.fragmentsStack.isEmpty()) {
            if (!UserConfig.isClientActivated()) {
                actionBarLayout.addFragmentToStack(new LoginActivity());
            } else {
                actionBarLayout.addFragmentToStack(new MessagesActivity(null));
            }

            try {
                if (savedInstanceState != null) {
                    String fragmentName = savedInstanceState.getString("fragment");
                    if (fragmentName != null) {
                        Bundle args = savedInstanceState.getBundle("args");
                        if (fragmentName.equals("chat")) {
                            if (args != null) {
                                ChatActivity chat = new ChatActivity(args);
                                if (actionBarLayout.addFragmentToStack(chat)) {
                                    chat.restoreSelfArgs(savedInstanceState);
                                }
                            }
                        } else if (fragmentName.equals("settings")) {
                            SettingsActivity settings = new SettingsActivity();
                            actionBarLayout.addFragmentToStack(settings);
                            settings.restoreSelfArgs(savedInstanceState);
                        } else if (fragmentName.equals("group")) {
                            if (args != null) {
                                GroupCreateFinalActivity group = new GroupCreateFinalActivity(args);
                                if (actionBarLayout.addFragmentToStack(group)) {
                                    group.restoreSelfArgs(savedInstanceState);
                                }
                            }
                        } else if (fragmentName.equals("chat_profile")) {
                            if (args != null) {
                                ChatProfileActivity profile = new ChatProfileActivity(args);
                                if (actionBarLayout.addFragmentToStack(profile)) {
                                    profile.restoreSelfArgs(savedInstanceState);
                                }
                            }
                        } else if (fragmentName.equals("wallpapers")) {
                            SettingsWallpapersActivity settings = new SettingsWallpapersActivity();
                            actionBarLayout.addFragmentToStack(settings);
                            settings.restoreSelfArgs(savedInstanceState);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        handleIntent(getIntent(), false, savedInstanceState != null);
        needLayout();
    }

    private void handleIntent(Intent intent, boolean isNew, boolean restore) {
        boolean pushOpened = false;

        Integer push_user_id = 0;
        Integer push_chat_id = 0;
        Integer push_enc_id = 0;
        Integer open_settings = 0;
        boolean showDialogsList = false;

        photoPathsArray = null;
        videoPath = null;
        sendingText = null;
        documentsPathsArray = null;
        documentsOriginalPathsArray = null;
        contactsToSend = null;

        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            if (intent != null && intent.getAction() != null && !restore) {
                if (Intent.ACTION_SEND.equals(intent.getAction())) {
                    boolean error = false;
                    String type = intent.getType();
                    if (type != null && type.equals("text/plain")) {
                        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

                        if (text != null && text.length() != 0) {
                            if ((text.startsWith("http://") || text.startsWith("https://")) && subject != null && subject.length() != 0) {
                                text = subject + "\n" + text;
                            }
                            sendingText = text;
                        } else {
                            error = true;
                        }
                    } else if (type != null && type.equals(ContactsContract.Contacts.CONTENT_VCARD_TYPE)) {
                        try {
                            Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                            if (uri != null) {
                                ContentResolver cr = getContentResolver();
                                InputStream stream = cr.openInputStream(uri);

                                String name = null;
                                String nameEncoding = null;
                                String nameCharset = null;
                                ArrayList<String> phones = new ArrayList<String>();
                                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                                String line = null;
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
                                            byte[] bytes = Utilities.decodeQuotedPrintable(name.getBytes());
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
                                if (name != null && !phones.isEmpty()) {
                                    contactsToSend = new ArrayList<TLRPC.User>();
                                    for (String phone : phones) {
                                        TLRPC.User user = new TLRPC.TL_userContact();
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
                        Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        if (parcelable == null) {
                            return;
                        }
                        String path = null;
                        if (!(parcelable instanceof Uri)) {
                            parcelable = Uri.parse(parcelable.toString());
                        }
                        Uri uri = (Uri) parcelable;
                        if (uri != null && type != null && type.startsWith("image/")) {
                            String tempPath = Utilities.getPath(uri);
                            if (photoPathsArray == null) {
                                photoPathsArray = new ArrayList<Uri>();
                            }
                            photoPathsArray.add(uri);
                        } else {
                            path = Utilities.getPath(uri);
                            if (path != null) {
                                if (path.startsWith("file:")) {
                                    path = path.replace("file://", "");
                                }
                                if (type != null && type.startsWith("video/")) {
                                    videoPath = path;
                                } else {
                                    if (documentsPathsArray == null) {
                                        documentsPathsArray = new ArrayList<String>();
                                        documentsOriginalPathsArray = new ArrayList<String>();
                                    }
                                    documentsPathsArray.add(path);
                                    documentsOriginalPathsArray.add(uri.toString());
                                }
                            } else {
                                error = true;
                            }
                        }
                        if (error) {
                            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if (intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
                    boolean error = false;
                    try {
                        ArrayList<Parcelable> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                        String type = intent.getType();
                        if (uris != null) {
                            if (type != null && type.startsWith("image/")) {
                                for (Parcelable parcelable : uris) {
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    Uri uri = (Uri) parcelable;
                                    if (photoPathsArray == null) {
                                        photoPathsArray = new ArrayList<Uri>();
                                    }
                                    photoPathsArray.add(uri);
                                }
                            } else {
                                for (Parcelable parcelable : uris) {
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    String path = Utilities.getPath((Uri) parcelable);
                                    String originalPath = parcelable.toString();
                                    if (originalPath == null) {
                                        originalPath = path;
                                    }
                                    if (path != null) {
                                        if (path.startsWith("file:")) {
                                            path = path.replace("file://", "");
                                        }
                                        if (documentsPathsArray == null) {
                                            documentsPathsArray = new ArrayList<String>();
                                            documentsOriginalPathsArray = new ArrayList<String>();
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
                } else if (intent.getAction().equals("org.telegram.messenger.OPEN_ACCOUNT")) {
                    open_settings = 1;
                }
            }

            if (intent.getAction() != null && intent.getAction().startsWith("com.tmessages.openchat") && !restore) {
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
            }
        }

        if (push_user_id != 0) {
            if (push_user_id == UserConfig.getClientUserId()) {
                open_settings = 1;
            } else {
                Bundle args = new Bundle();
                args.putInt("user_id", push_user_id);
                ChatActivity fragment = new ChatActivity(args);
                if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                    pushOpened = true;
                }
            }
        } else if (push_chat_id != 0) {
            Bundle args = new Bundle();
            args.putInt("chat_id", push_chat_id);
            ChatActivity fragment = new ChatActivity(args);
            if (actionBarLayout.presentFragment(fragment, false, true, true)) {
                pushOpened = true;
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
            }
            pushOpened = false;
            isNew = false;
        }
        if (videoPath != null || photoPathsArray != null || sendingText != null || documentsPathsArray != null || contactsToSend != null) {
            if (!AndroidUtilities.isTablet()) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            }
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putString("selectAlertString", LocaleController.getString("SendMessagesTo", R.string.SendMessagesTo));
            args.putString("selectAlertStringGroup", LocaleController.getString("SendMessagesToGroup", R.string.SendMessagesToGroup));
            MessagesActivity fragment = new MessagesActivity(args);
            fragment.setDelegate(this);
            actionBarLayout.presentFragment(fragment, false, true, true);
            pushOpened = true;
            if (PhotoViewer.getInstance().isVisible()) {
                PhotoViewer.getInstance().closePhoto(false);
            }

            if (AndroidUtilities.isTablet()) {
                actionBarLayout.showLastFragment();
                rightActionBarLayout.showLastFragment();
            }
        }
        if (open_settings != 0) {
            actionBarLayout.presentFragment(new SettingsActivity(), false, true, true);
            pushOpened = true;
        }
        if (!pushOpened && !isNew) {
            if (AndroidUtilities.isTablet()) {
                if (UserConfig.isClientActivated()) {
                    if (actionBarLayout.fragmentsStack.isEmpty()) {
                        actionBarLayout.addFragmentToStack(new MessagesActivity(null));
                    }
                } else {
                    if (layersActionBarLayout.fragmentsStack.isEmpty()) {
                        layersActionBarLayout.addFragmentToStack(new LoginActivity());
                    }
                }
            } else {
                if (actionBarLayout.fragmentsStack.isEmpty()) {
                    if (!UserConfig.isClientActivated()) {
                        actionBarLayout.addFragmentToStack(new LoginActivity());
                    } else {
                        actionBarLayout.addFragmentToStack(new MessagesActivity(null));
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false);
    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id, boolean param) {
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
            ChatActivity fragment = new ChatActivity(args);

            if (videoPath != null) {
                if(android.os.Build.VERSION.SDK_INT >= 16) {
                    if (AndroidUtilities.isTablet()) {
                        actionBarLayout.presentFragment(fragment, false, true, true);
                    }

                    Bundle args2 = new Bundle();
                    args2.putString("videoPath", videoPath);
                    VideoEditorActivity fragment2 = new VideoEditorActivity(args2);
                    fragment2.setDelegate(fragment);
                    presentFragment(fragment2, true, true);
                    if (!AndroidUtilities.isTablet()) {
                        actionBarLayout.addFragmentToStack(fragment, actionBarLayout.fragmentsStack.size() - 1);
                    }
                } else {
                    actionBarLayout.presentFragment(fragment, true);
                    fragment.processSendingVideo(videoPath, 0, 0, 0, 0, null);
                }
            } else {
                actionBarLayout.presentFragment(fragment, true);
                if (sendingText != null) {
                    fragment.processSendingText(sendingText);
                }
                if (photoPathsArray != null) {
                    fragment.processSendingPhotos(null, photoPathsArray);
                }
                if (documentsPathsArray != null) {
                    fragment.processSendingDocuments(documentsPathsArray, documentsOriginalPathsArray);
                }
                if (contactsToSend != null && !contactsToSend.isEmpty()) {
                    for (TLRPC.User user : contactsToSend) {
                        SendMessagesHelper.getInstance().sendMessage(user, dialog_id);
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didUpdatedConnectionState);
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
    }

    public void needLayout() {
        if (AndroidUtilities.isTablet()) {
            if (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                tabletFullSize = false;
                int leftWidth = AndroidUtilities.displaySize.x / 100 * 35;
                if (leftWidth < AndroidUtilities.dp(320)) {
                    leftWidth = AndroidUtilities.dp(320);
                }

                RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = leftWidth;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) shadowTabletSide.getLayoutParams();
                relativeLayoutParams.leftMargin = leftWidth;
                shadowTabletSide.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) rightActionBarLayout.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.displaySize.x - leftWidth;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                relativeLayoutParams.leftMargin = leftWidth;
                rightActionBarLayout.setLayoutParams(relativeLayoutParams);

                relativeLayoutParams = (RelativeLayout.LayoutParams) buttonLayoutTablet.getLayoutParams();
                relativeLayoutParams.width = AndroidUtilities.displaySize.x - leftWidth;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                relativeLayoutParams.leftMargin = leftWidth;
                buttonLayoutTablet.setLayoutParams(relativeLayoutParams);

                if (AndroidUtilities.isSmallTablet() && actionBarLayout.fragmentsStack.size() == 2) {
                    BaseFragment chatFragment = actionBarLayout.fragmentsStack.get(1);
                    chatFragment.onPause();
                    actionBarLayout.fragmentsStack.remove(1);
                    actionBarLayout.showLastFragment();
                    rightActionBarLayout.fragmentsStack.add(chatFragment);
                    rightActionBarLayout.showLastFragment();
                }

                rightActionBarLayout.setVisibility(rightActionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
                buttonLayoutTablet.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() && rightActionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
                backgroundTablet.setVisibility(rightActionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
                shadowTabletSide.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                tabletFullSize = true;

                RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) actionBarLayout.getLayoutParams();
                relativeLayoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                relativeLayoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                actionBarLayout.setLayoutParams(relativeLayoutParams);

                shadowTabletSide.setVisibility(View.GONE);
                rightActionBarLayout.setVisibility(View.GONE);
                backgroundTablet.setVisibility(!actionBarLayout.fragmentsStack.isEmpty() ? View.GONE : View.VISIBLE);
                buttonLayoutTablet.setVisibility(View.GONE);

                if (rightActionBarLayout.fragmentsStack.size() == 1) {
                    BaseFragment chatFragment = rightActionBarLayout.fragmentsStack.get(0);
                    chatFragment.onPause();
                    rightActionBarLayout.fragmentsStack.remove(0);
                    actionBarLayout.presentFragment(chatFragment, false, true, false);
                }
            }
        }
    }

    public void fixLayout() {
        if (AndroidUtilities.isTablet()) {
            actionBarLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    needLayout();
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
    protected void onPause() {
        super.onPause();
        actionBarLayout.onPause();
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onPause();
            layersActionBarLayout.onPause();
        }
        ApplicationLoader.mainInterfacePaused = true;
        ConnectionsManager.getInstance().setAppPaused(true, false);
    }

    @Override
    protected void onDestroy() {
        PhotoViewer.getInstance().destroyPhotoViewer();
        super.onDestroy();
        onFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        actionBarLayout.onResume();
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onResume();
            layersActionBarLayout.onResume();
        }
        Utilities.checkForCrashes(this);
        Utilities.checkForUpdates(this);
        ApplicationLoader.mainInterfacePaused = false;
        ConnectionsManager.getInstance().setAppPaused(false, false);
        actionBarLayout.getActionBar().setBackOverlayVisible(currentConnectionState != 0);
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
            }
        } else if (id == NotificationCenter.didUpdatedConnectionState) {
            int state = (Integer)args[0];
            if (currentConnectionState != state) {
                FileLog.e("tmessages", "switch to state " + state);
                currentConnectionState = state;
                actionBarLayout.getActionBar().setBackOverlayVisible(currentConnectionState != 0);
            }
        }
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
                } else if (lastFragment instanceof SettingsWallpapersActivity) {
                    outState.putString("fragment", "wallpapers");
                } else if (lastFragment instanceof ChatProfileActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat_profile");
                }
                lastFragment.saveSelfArgs(outState);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true);
        } else {
            if (AndroidUtilities.isTablet()) {
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
        actionBarLayout.onActionModeStarted(mode);
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onActionModeStarted(mode);
            layersActionBarLayout.onActionModeStarted(mode);
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        actionBarLayout.onActionModeFinished(mode);
        if (AndroidUtilities.isTablet()) {
            rightActionBarLayout.onActionModeFinished(mode);
            layersActionBarLayout.onActionModeFinished(mode);
        }
    }

    @Override
    public boolean onPreIme() {
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true);
            return true;
        }
        return false;
    }

    @Override
    public void onOverlayShow(View view, BaseFragment fragment) {
        if (view == null || fragment == null || actionBarLayout.fragmentsStack.isEmpty()) {
            return;
        }
        View backStatusButton = view.findViewById(R.id.back_button);
        TextView statusText = (TextView)view.findViewById(R.id.status_text);
        backStatusButton.setVisibility(actionBarLayout.fragmentsStack.get(0) == fragment ? View.GONE : View.VISIBLE);
        view.setEnabled(actionBarLayout.fragmentsStack.get(0) != fragment);
        if (currentConnectionState == 1) {
            statusText.setText(LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork));
        } else if (currentConnectionState == 2) {
            statusText.setText(LocaleController.getString("Connecting", R.string.Connecting));
        } else if (currentConnectionState == 3) {
            statusText.setText(LocaleController.getString("Updating", R.string.Updating));
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (AndroidUtilities.isTablet()) {
            if (layersActionBarLayout.getVisibility() == View.VISIBLE && !layersActionBarLayout.fragmentsStack.isEmpty()) {
                layersActionBarLayout.onKeyUp(keyCode, event);
            } else if (rightActionBarLayout.getVisibility() == View.VISIBLE && !rightActionBarLayout.fragmentsStack.isEmpty()) {
                rightActionBarLayout.onKeyUp(keyCode, event);
            } else {
                actionBarLayout.onKeyUp(keyCode, event);
            }
        } else {
            actionBarLayout.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (fragment instanceof MessagesActivity) {
                MessagesActivity messagesActivity = (MessagesActivity)fragment;
                if (messagesActivity.getDelegate() == null && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, false);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.setVisibility(View.GONE);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                            buttonLayoutTablet.setVisibility(View.VISIBLE);
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            }
            if (fragment instanceof ChatActivity) {
                if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    buttonLayoutTablet.setVisibility(View.GONE);
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
                    if (actionBarLayout.fragmentsStack.size() > 1) {
                        actionBarLayout.presentFragment(fragment, actionBarLayout.fragmentsStack.size() > 1, forceWithoutAnimation, false);
                        return false;
                    }
                }
            } else if (layout != layersActionBarLayout) {
                layersActionBarLayout.setVisibility(View.VISIBLE);
                if (fragment instanceof LoginActivity) {
                    buttonLayoutTablet.setVisibility(View.GONE);
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
            return true;
        }
    }

    @Override
    public boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout) {
        if (AndroidUtilities.isTablet()) {
            if (fragment instanceof MessagesActivity) {
                MessagesActivity messagesActivity = (MessagesActivity)fragment;
                if (messagesActivity.getDelegate() == null && layout != actionBarLayout) {
                    actionBarLayout.removeAllFragments();
                    actionBarLayout.addFragmentToStack(fragment);
                    layersActionBarLayout.removeAllFragments();
                    layersActionBarLayout.setVisibility(View.GONE);
                    if (!tabletFullSize) {
                        shadowTabletSide.setVisibility(View.VISIBLE);
                        if (rightActionBarLayout.fragmentsStack.isEmpty()) {
                            buttonLayoutTablet.setVisibility(View.VISIBLE);
                            backgroundTablet.setVisibility(View.VISIBLE);
                        }
                    }
                    return false;
                }
            } else if (fragment instanceof ChatActivity) {
                if (!tabletFullSize && layout != rightActionBarLayout) {
                    rightActionBarLayout.setVisibility(View.VISIBLE);
                    buttonLayoutTablet.setVisibility(View.GONE);
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
                if (fragment instanceof LoginActivity) {
                    buttonLayoutTablet.setVisibility(View.GONE);
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
                    buttonLayoutTablet.setVisibility(View.VISIBLE);
                    backgroundTablet.setVisibility(View.VISIBLE);
                }
            } else if (layout == layersActionBarLayout && actionBarLayout.fragmentsStack.isEmpty()) {
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

                TextView button = (TextView)findViewById(R.id.new_group_button);
                button.setText(LocaleController.getString("NewGroup", R.string.NewGroup));
                button = (TextView)findViewById(R.id.new_secret_button);
                button.setText(LocaleController.getString("NewSecretChat", R.string.NewSecretChat));
                button = (TextView)findViewById(R.id.new_broadcast_button);
                button.setText(LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
                button = (TextView)findViewById(R.id.contacts_button);
                button.setText(LocaleController.getString("Contacts", R.string.Contacts));
                button = (TextView)findViewById(R.id.settings_button);
                button.setText(LocaleController.getString("Settings", R.string.Settings));
            }
        }
    }
}

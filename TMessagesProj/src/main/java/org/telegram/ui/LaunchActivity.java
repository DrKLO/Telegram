/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.ui.Views.ActionBar.ActionBarActivity;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.NotificationView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

public class LaunchActivity extends ActionBarActivity implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate {
    private boolean finished = false;
    private NotificationView notificationView;
    private Uri photoPath = null;
    private String videoPath = null;
    private String sendingText = null;
    private String documentPath = null;
    private ArrayList<Uri> imagesPathArray = null;
    private ArrayList<String> documentsPathArray = null;
    private ArrayList<TLRPC.User> contactsToSend = null;
    private int currentConnectionState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();

        if (!UserConfig.clientActivated) {
            Intent intent = getIntent();
            if (intent != null && intent.getAction() != null && (Intent.ACTION_SEND.equals(intent.getAction()) || intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))) {
                super.onCreateFinish(savedInstanceState);
                finish();
                return;
            }
            if (intent != null && !intent.getBooleanExtra("fromIntro", false)) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", MODE_PRIVATE);
                Map<String, ?> state = preferences.getAll();
                if (state.isEmpty()) {
                    Intent intent2 = new Intent(this, IntroActivity.class);
                    startActivity(intent2);
                    super.onCreateFinish(savedInstanceState);
                    finish();
                    return;
                }
            }
        }

        super.onCreate(savedInstanceState);

        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            Utilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        NotificationCenter.getInstance().postNotificationName(702, this);
        currentConnectionState = ConnectionsManager.getInstance().connectionState;

        NotificationCenter.getInstance().addObserver(this, 1234);
        NotificationCenter.getInstance().addObserver(this, 658);
        NotificationCenter.getInstance().addObserver(this, 701);
        NotificationCenter.getInstance().addObserver(this, 702);
        NotificationCenter.getInstance().addObserver(this, 703);

        if (fragmentsStack.isEmpty()) {
            if (!UserConfig.clientActivated) {
                addFragmentToStack(new LoginActivity());
            } else {
                addFragmentToStack(new MessagesActivity(null));
            }

            try {
                if (savedInstanceState != null) {
                    String fragmentName = savedInstanceState.getString("fragment");
                    if (fragmentName != null) {
                        Bundle args = savedInstanceState.getBundle("args");
                        if (fragmentName.equals("chat")) {
                            if (args != null) {
                                ChatActivity chat = new ChatActivity(args);
                                if (addFragmentToStack(chat)) {
                                    chat.restoreSelfArgs(savedInstanceState);
                                }
                            }
                        } else if (fragmentName.equals("settings")) {
                            SettingsActivity settings = new SettingsActivity();
                            addFragmentToStack(settings);
                            settings.restoreSelfArgs(savedInstanceState);
                        } else if (fragmentName.equals("group")) {
                            if (args != null) {
                                GroupCreateFinalActivity group = new GroupCreateFinalActivity(args);
                                if (addFragmentToStack(group)) {
                                    group.restoreSelfArgs(savedInstanceState);
                                }
                            }
                        } else if (fragmentName.equals("chat_profile")) {
                            if (args != null) {
                                ChatProfileActivity profile = new ChatProfileActivity(args);
                                if (addFragmentToStack(profile)) {
                                    profile.restoreSelfArgs(savedInstanceState);
                                }
                            }
                        } else if (fragmentName.equals("wallpapers")) {
                            SettingsWallpapersActivity settings = new SettingsWallpapersActivity();
                            addFragmentToStack(settings);
                            settings.restoreSelfArgs(savedInstanceState);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        handleIntent(getIntent(), false, savedInstanceState != null);

        PhotoViewer.getInstance().setParentActivity(this);
    }

    private void handleIntent(Intent intent, boolean isNew, boolean restore) {
        boolean pushOpened = false;

        Integer push_user_id = 0;
        Integer push_chat_id = 0;
        Integer push_enc_id = 0;
        Integer open_settings = 0;

        photoPath = null;
        videoPath = null;
        sendingText = null;
        documentPath = null;
        imagesPathArray = null;
        documentsPathArray = null;

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
                            boolean isGif = false;
                            if (tempPath != null && tempPath.endsWith(".gif")) {
                                isGif = true;
                                documentPath = tempPath;
                            } else if (tempPath == null) {
                                isGif = MediaController.isGif(uri);
                                if (isGif) {
                                    documentPath = MediaController.copyDocumentToCache(uri);
                                }
                            }
                            if (!isGif || documentPath == null) {
                                photoPath = uri;
                            }
                        } else {
                            path = Utilities.getPath(uri);
                            if (path != null) {
                                if (path.startsWith("file:")) {
                                    path = path.replace("file://", "");
                                }
                                if (type != null && type.startsWith("video/")) {
                                    videoPath = path;
                                } else {
                                    documentPath = path;
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
                                    String tempPath = Utilities.getPath(uri);

                                    boolean isGif = false;
                                    if (tempPath != null && tempPath.endsWith(".gif")) {
                                        isGif = true;
                                    } else if (tempPath == null) {
                                        isGif = MediaController.isGif(uri);
                                        if (isGif) {
                                            tempPath = MediaController.copyDocumentToCache(uri);
                                        }
                                    }
                                    if (isGif && tempPath != null) {
                                        if (documentsPathArray == null) {
                                            documentsPathArray = new ArrayList<String>();
                                        }
                                        try {
                                            documentsPathArray.add(tempPath);
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                    } else {
                                        if (imagesPathArray == null) {
                                            imagesPathArray = new ArrayList<Uri>();
                                        }
                                        imagesPathArray.add(uri);
                                    }
                                }
                            } else {
                                for (Parcelable parcelable : uris) {
                                    if (!(parcelable instanceof Uri)) {
                                        parcelable = Uri.parse(parcelable.toString());
                                    }
                                    String path = Utilities.getPath((Uri) parcelable);
                                    if (path != null) {
                                        if (path.startsWith("file:")) {
                                            path = path.replace("file://", "");
                                        }
                                        if (documentsPathArray == null) {
                                            documentsPathArray = new ArrayList<String>();
                                        }
                                        documentsPathArray.add(path);
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
                                NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
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
                    TLRPC.Chat chat = MessagesController.getInstance().chats.get(chatId);
                    if (chat != null) {
                        NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                        push_chat_id = chatId;
                    }
                } else if (userId != 0) {
                    TLRPC.User user = MessagesController.getInstance().users.get(userId);
                    if (user != null) {
                        NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                        push_user_id = userId;
                    }
                } else if (encId != 0) {
                    TLRPC.EncryptedChat chat = MessagesController.getInstance().encryptedChats.get(encId);
                    if (chat != null) {
                        NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                        push_enc_id = encId;
                    }
                }
            }
        }

        if (push_user_id != 0) {
            if (push_user_id == UserConfig.clientUserId) {
                open_settings = 1;
            } else {
                Bundle args = new Bundle();
                args.putInt("user_id", push_user_id);
                ChatActivity fragment = new ChatActivity(args);
                if (presentFragment(fragment, false, true)) {
                    pushOpened = true;
                }
            }
        } else if (push_chat_id != 0) {
            Bundle args = new Bundle();
            args.putInt("chat_id", push_chat_id);
            ChatActivity fragment = new ChatActivity(args);
            if (presentFragment(fragment, false, true)) {
                pushOpened = true;
            }
        } else if (push_enc_id != 0) {
            Bundle args = new Bundle();
            args.putInt("enc_id", push_enc_id);
            ChatActivity fragment = new ChatActivity(args);
            if (presentFragment(fragment, false, true)) {
                pushOpened = true;
            }
        }
        if (videoPath != null || photoPath != null || sendingText != null || documentPath != null || documentsPathArray != null || imagesPathArray != null || contactsToSend != null) {
            NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
            MessagesActivity fragment = new MessagesActivity(args);
            fragment.setDelegate(this);
            presentFragment(fragment, false, true);
            pushOpened = true;
        }
        if (open_settings != 0) {
            presentFragment(new SettingsActivity(), false, true);
            pushOpened = true;
        }
        if (!pushOpened && !isNew) {
            showLastFragment();
        }

        intent.setAction(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true, false);
    }

    @Override
    public void didSelectDialog(MessagesActivity messageFragment, long dialog_id) {
        if (dialog_id != 0) {
            int lower_part = (int)dialog_id;

            Bundle args = new Bundle();
            args.putBoolean("scrollToTopOnResume", true);
            NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
            if (lower_part != 0) {
                if (lower_part > 0) {
                    args.putInt("user_id", lower_part);
                } else if (lower_part < 0) {
                    args.putInt("chat_id", -lower_part);
                }
            } else {
                args.putInt("enc_id", (int)(dialog_id >> 32));
            }
            ChatActivity fragment = new ChatActivity(args);
            presentFragment(fragment, true);
            if (photoPath != null) {
                fragment.processSendingPhoto(null, photoPath);
            }
            if (videoPath != null) {
                fragment.processSendingVideo(videoPath);
            }
            if (sendingText != null) {
                fragment.processSendingText(sendingText);
            }
            if (documentPath != null) {
                fragment.processSendingDocument(documentPath);
            }
            if (imagesPathArray != null) {
                for (Uri path : imagesPathArray) {
                    fragment.processSendingPhoto(null, path);
                }
            }
            if (documentsPathArray != null) {
                for (String path : documentsPathArray) {
                    fragment.processSendingDocument(path);
                }
            }
            if (contactsToSend != null && !contactsToSend.isEmpty()) {
                for (TLRPC.User user : contactsToSend) {
                    MessagesController.getInstance().sendMessage(user, dialog_id);
                }
            }
            photoPath = null;
            videoPath = null;
            sendingText = null;
            documentPath = null;
            imagesPathArray = null;
            documentsPathArray = null;
            contactsToSend = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (fragmentsStack.size() != 0) {
            BaseFragment fragment = fragmentsStack.get(fragmentsStack.size() - 1);
            fragment.onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ApplicationLoader.lastPauseTime = System.currentTimeMillis();
        if (notificationView != null) {
            notificationView.hide(false);
        }
        View focusView = getCurrentFocus();
        if (focusView instanceof EditText) {
            focusView.clearFocus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notificationView == null && getLayoutInflater() != null) {
            notificationView = (NotificationView) getLayoutInflater().inflate(R.layout.notification_layout, null);
        }
        Utilities.checkForCrashes(this);
        Utilities.checkForUpdates(this);
        ApplicationLoader.resetLastPauseTime();
        actionBar.setBackOverlayVisible(currentConnectionState != 0);
        try {
            NotificationManager mNotificationManager = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(1);
            MessagesController.getInstance().currentPushMessage = null;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    protected void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        NotificationCenter.getInstance().removeObserver(this, 1234);
        NotificationCenter.getInstance().removeObserver(this, 658);
        NotificationCenter.getInstance().removeObserver(this, 701);
        NotificationCenter.getInstance().removeObserver(this, 702);
        NotificationCenter.getInstance().removeObserver(this, 703);
        if (notificationView != null) {
            notificationView.hide(false);
            notificationView.destroy();
            notificationView = null;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Utilities.checkDisplaySize();
    }

    @Override
    public void needLayout() {
        super.needLayout();
        if (notificationView != null) {
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();

            int height = Utilities.dp(48);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                height = Utilities.dp(40);
            }
            notificationView.applyOrientationPaddings(rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90, height);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == 1234) {
            for (BaseFragment fragment : fragmentsStack) {
                fragment.onFragmentDestroy();
            }
            fragmentsStack.clear();
            Intent intent2 = new Intent(this, IntroActivity.class);
            startActivity(intent2);
            onFinish();
            finish();
        } else if (id == 658) {
            if (PhotoViewer.getInstance().isVisible()) {
                PhotoViewer.getInstance().closePhoto(false);
            }
            Integer push_chat_id = (Integer)args[0];
            Integer push_user_id = (Integer)args[1];
            Integer push_enc_id = (Integer)args[2];

            if (push_user_id != 0) {
                NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                Bundle args2 = new Bundle();
                args2.putInt("user_id", push_user_id);
                presentFragment(new ChatActivity(args2), false, true);
            } else if (push_chat_id != 0) {
                NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                Bundle args2 = new Bundle();
                args2.putInt("chat_id", push_chat_id);
                presentFragment(new ChatActivity(args2), false, true);
            } else if (push_enc_id != 0) {
                NotificationCenter.getInstance().postNotificationName(MessagesController.closeChats);
                Bundle args2 = new Bundle();
                args2.putInt("enc_id", push_enc_id);
                presentFragment(new ChatActivity(args2), false, true);
            }
        } else if (id == 701) {
            if (notificationView != null) {
                MessageObject message = (MessageObject)args[0];
                notificationView.show(message);
            }
        } else if (id == 702) {
            if (args[0] != this) {
                onFinish();
            }
        } else if (id == 703) {
            int state = (Integer)args[0];
            if (currentConnectionState != state) {
                FileLog.e("tmessages", "switch to state " + state);
                currentConnectionState = state;
                actionBar.setBackOverlayVisible(currentConnectionState != 0);
            }
        }
    }

    @Override
    public void onOverlayShow(View view, BaseFragment fragment) {
        if (view == null || fragment == null || fragmentsStack.isEmpty()) {
            return;
        }
        View backStatusButton = view.findViewById(R.id.back_button);
        TextView statusText = (TextView)view.findViewById(R.id.status_text);
        backStatusButton.setVisibility(fragmentsStack.get(0) == fragment ? View.GONE : View.VISIBLE);
        view.setEnabled(fragmentsStack.get(0) != fragment);
        if (currentConnectionState == 1) {
            statusText.setText(LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork));
        } else if (currentConnectionState == 2) {
            statusText.setText(LocaleController.getString("Connecting", R.string.Connecting));
        } else if (currentConnectionState == 3) {
            statusText.setText(LocaleController.getString("Updating", R.string.Updating));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            if (!fragmentsStack.isEmpty()) {
                BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
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
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPreIme() {
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true);
            return true;
        }
        return super.onPreIme();
    }
}

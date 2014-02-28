/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.PausableActivity;

import java.util.ArrayList;

public class LaunchActivity extends PausableActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return;
        }
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        getSupportActionBar().hide();
        if (!UserConfig.clientActivated) {
            Intent intent = getIntent();
            if (Intent.ACTION_SEND.equals(intent.getAction())) {
                finish();
                return;
            }
            Intent intent2 = new Intent(this, IntroActivity.class);
            startActivity(intent2);
            finish();
        } else {
            Intent intent = getIntent();
            if (intent != null && intent.getAction() != null) {
                if (Intent.ACTION_SEND.equals(intent.getAction())) {
                    boolean error = false;
                    String type = intent.getType();
                    if (type != null && type.equals("text/plain")) {
                        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (text != null && text.length() != 0) {
                            NotificationCenter.Instance.addToMemCache(535, text);
                        } else {
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
                        path = Utilities.getPath((Uri)parcelable);
                        if (path != null) {
                            if (path.startsWith("file:")) {
                                path = path.replace("file://", "");
                            }
                            if (type != null && type.startsWith("image/")) {
                                NotificationCenter.Instance.addToMemCache(533, path);
                            } else if (type != null && type.startsWith("video/")) {
                                NotificationCenter.Instance.addToMemCache(534, path);
                            } else {
                                NotificationCenter.Instance.addToMemCache(536, path);
                            }
                        } else {
                            error = true;
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
                            String[] uris2 = new String[uris.size()];
                            for (int i = 0; i < uris2.length; i++) {
                                Parcelable parcelable = uris.get(i);
                                if (!(parcelable instanceof Uri)) {
                                    parcelable = Uri.parse(parcelable.toString());
                                }
                                String path = Utilities.getPath((Uri)parcelable);
                                if (path != null) {
                                    if (path.startsWith("file:")) {
                                        path = path.replace("file://", "");
                                    }
                                    uris2[i] = path;
                                }
                            }
                            if (type != null && type.startsWith("image/")) {
                                NotificationCenter.Instance.addToMemCache(537, uris2);
                            } else {
                                NotificationCenter.Instance.addToMemCache(538, uris2);
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
                                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                                NotificationCenter.Instance.addToMemCache("push_user_id", userId);
                            }
                            cursor.close();
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (intent.getAction().equals("org.telegram.messenger.OPEN_ACCOUNT")) {
                    NotificationCenter.Instance.addToMemCache("open_settings", 1);
                }
            }
            openNotificationChat();
            Intent intent2 = new Intent(this, ApplicationActivity.class);
            startActivity(intent2);
            finish();
        }
        getIntent().setAction(null);
        try {
            NotificationManager mNotificationManager = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(1);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void openNotificationChat() {
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        int chatId = getIntent().getIntExtra("chatId", 0);
        int userId = getIntent().getIntExtra("userId", 0);
        int encId = getIntent().getIntExtra("encId", 0);
        if (chatId != 0) {
            TLRPC.Chat chat = MessagesController.Instance.chats.get(chatId);
            if (chat != null) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                NotificationCenter.Instance.addToMemCache("push_chat_id", chatId);
            }
        } else if (userId != 0) {
            TLRPC.User user = MessagesController.Instance.users.get(userId);
            if (user != null) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                NotificationCenter.Instance.addToMemCache("push_user_id", userId);
            }
        } else if (encId != 0) {
            TLRPC.EncryptedChat chat = MessagesController.Instance.encryptedChats.get(encId);
            if (chat != null) {
                NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                NotificationCenter.Instance.addToMemCache("push_enc_id", encId);
            }
        }
    }
}

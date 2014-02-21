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

import org.telegram.TL.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.PausableActivity;

import java.util.Enumeration;

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
                    if (intent.getType() != null) {
                        if (intent.getType().startsWith("image/")) {
                            Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                            if (parcelable == null) {
                                return;
                            }
                            String path = null;
                            if (parcelable instanceof Uri) {
                                path = Utilities.getPath((Uri)parcelable);
                            } else {
                                path = intent.getParcelableExtra(Intent.EXTRA_STREAM).toString();
                                if (path.startsWith("content:")) {
                                    Cursor cursor = getContentResolver().query(Uri.parse(path), new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                                    if (cursor != null) {
                                        cursor.moveToFirst();
                                        path = cursor.getString(0);
                                        cursor.close();
                                    }
                                }
                            }
                            if (path != null) {
                                if (path.startsWith("file:")) {
                                    path = path.replace("file://", "");
                                }
                                NotificationCenter.Instance.addToMemCache(533, path);
                            }
                        } else if (intent.getType().startsWith("video/")) {
                            Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                            if (parcelable == null) {
                                return;
                            }
                            String path = null;
                            if (parcelable instanceof Uri) {
                                path = Utilities.getPath((Uri)parcelable);
                            } else {
                                path = parcelable.toString();
                                if (path.startsWith("content:")) {
                                    Cursor cursor = getContentResolver().query(Uri.parse(path), new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                                    if (cursor != null) {
                                        cursor.moveToFirst();
                                        path = cursor.getString(0);
                                        cursor.close();
                                    }
                                }
                            }
                            if (path != null) {
                                if (path.startsWith("file:")) {
                                    path = path.replace("file://", "");
                                }
                                NotificationCenter.Instance.addToMemCache(534, path);
                            }
                        } else if (intent.getType().equals("text/plain")) {
                            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                            if (text != null && text.length() != 0) {
                                NotificationCenter.Instance.addToMemCache(535, text);
                            }
                        }
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
                } else if (Intent.ACTION_SENDTO.equals(intent.getAction())) {
                    String phone = intent.getData().toString();
                    if(phone.startsWith("smsto:")) {
                        phone = phone.replace("smsto:","");
                    } else if (phone.startsWith("sms:")) {
                        phone = phone.replace("sms:","");
                    }
                    if( phone.startsWith("%2B")) { // IF STARTSWITH +
                        phone = phone.replace("%2B","");
                    } else {
                        TLRPC.User user = MessagesController.Instance.users.get(UserConfig.clientUserId);
                        if (user == null) {
                            user = UserConfig.currentUser;
                        }
                        if(user != null) {
                            phone = user.phone.substring(0,2) + phone;
                        }
                    }

                    int user_id = 0;
                    Enumeration<TLRPC.User> users = MessagesController.Instance.users.elements();
                    while (users.hasMoreElements()) {
                        TLRPC.User u = users.nextElement();
                        if(u.phone.equals(phone)) {
                            user_id = u.id;
                        }
                    }
                    if(user_id == 0) {
                        NotificationCenter.Instance.addToMemCache("phone_ntg",phone);
                    } else {
                        NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                        NotificationCenter.Instance.addToMemCache("push_user_id", user_id);
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

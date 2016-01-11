/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefRecord;
import android.os.Build;

public class UriAction {
    public enum UriActionType {
        JOINCHAT, ADDSTICKERS, MSG, RESOLVE
    }
    public String username;
    public String group;
    public String sticker;
    public String botUser;
    public String botChat;
    public String message;
    public boolean hasUrl;

    public UriActionType actionType;

    private void parse(Uri data) {
        username = null;
        group = null;
        sticker = null;
        botUser = null;
        botChat = null;
        message = null;
        hasUrl = false;
        actionType = null;
        if (data == null)
            return;
        String scheme = data.getScheme();
        if (scheme == null)
            return;
            /* Parse url. */
        switch (scheme) {
            case "http":
            case "https":
                String host = data.getHost().toLowerCase();
                if (!host.equals("telegram.me"))
                    return;
                String path = data.getPath();
                if (path == null || path.length() <= 1)
                    return;
                path = path.substring(1);
                if (path.startsWith("joinchat/")) {
                    actionType = UriActionType.JOINCHAT;
                    group = path.replace("joinchat/", "");
                    return;
                } else if (path.startsWith("addstickers/")) {
                    actionType = UriActionType.ADDSTICKERS;
                    sticker = path.replace("addstickers/", "");
                    return;
                } else if (path.startsWith("msg/")) {
                    actionType = UriActionType.MSG;
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
                    return;
                } else if (path.length() >= 5) {
                    username = data.getLastPathSegment();
                    botUser = data.getQueryParameter("start");
                    botChat = data.getQueryParameter("startgroup");
                    actionType = UriActionType.RESOLVE;
                    return;
                }
                break;
            case "tg":
            case "telegram":
                    /* We need to handle shortened tags */
                String url = data.toString();
                if (url.startsWith("tg:resolve") || url.startsWith("tg://resolve")) {
                    url = url.replace("tg:resolve", "tg://telegram.org").replace("tg://resolve", "tg://telegram.org");
                    data = Uri.parse(url);
                    username = data.getQueryParameter("domain");
                    botUser = data.getQueryParameter("start");
                    botChat = data.getQueryParameter("startgroup");
                    actionType = UriActionType.RESOLVE;
                    return;
                } else if (url.startsWith("tg:join") || url.startsWith("tg://join")) {
                    url = url.replace("tg:join", "tg://telegram.org").replace("tg://join", "tg://telegram.org");
                    data = Uri.parse(url);
                    group = data.getQueryParameter("invite");
                    actionType = UriActionType.JOINCHAT;
                    return;
                } else if (url.startsWith("tg:addstickers") || url.startsWith("tg://addstickers")) {
                    url = url.replace("tg:addstickers", "tg://telegram.org").replace("tg://addstickers", "tg://telegram.org");
                    data = Uri.parse(url);
                    sticker = data.getQueryParameter("set");
                    actionType = UriActionType.ADDSTICKERS;
                    return;
                } else if (url.startsWith("tg:msg") || url.startsWith("tg://msg")) {
                    url = url.replace("tg:msg", "tg://telegram.org").replace("tg://msg", "tg://telegram.org");
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
                    actionType = UriActionType.MSG;
                    return;
                }
        }
    }

    public UriAction(NdefRecord record) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= 16) {
            uri = record.toUri();
            parse(uri);
        }
    }
    public UriAction(String string) {
        Uri uri = Uri.parse(string);
        parse(uri);
    }

    public UriAction(Uri uri) {
        parse(uri);
    }

    public UriAction(Intent intent) {
        Uri data = intent.getData();
        parse(data);
    }

    public UriAction(String username, String group, String sticker, String botUser, String botChat,
                     String message, boolean hasUrl, UriActionType actionType) {
        this.username = username;
        this.group = group;
        this.sticker = sticker;
        this.botUser = botUser;
        this.botChat = botChat;
        this.message = message;
        this.hasUrl = hasUrl;
        this.actionType = actionType;
    }
}
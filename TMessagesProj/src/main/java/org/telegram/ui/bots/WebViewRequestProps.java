package org.telegram.ui.bots;


import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class WebViewRequestProps {

    public int currentAccount;
    public long peerId;
    public long botId;
    public String buttonText;
    public String buttonUrl;
    public @BotWebViewAttachedSheet.WebViewType int type;
    public int replyToMsgId;
    public long monoforumTopicId;
    public boolean silent;
    public TLRPC.BotApp app;
    public boolean allowWrite;
    public String startParam;
    public TLRPC.User botUser;
    public int flags;
    public boolean compact;
    public boolean fullscreen;

    public TLObject response;
    public long responseTime;


    public static WebViewRequestProps of(
        int currentAccount,
        long peerId,
        long botId,
        String buttonText,
        String buttonUrl,
        @BotWebViewAttachedSheet.WebViewType int type,
        int replyToMsgId,
        long monoforumTopicId,
        boolean silent,
        TLRPC.BotApp app,
        boolean allowWrite,
        String startParam,
        TLRPC.User botUser,
        int flags,
        boolean compact,
        boolean fullscreen
    ) {
        WebViewRequestProps p = new WebViewRequestProps();
        p.currentAccount = currentAccount;
        p.peerId = peerId;
        p.botId = botId;
        p.buttonText = buttonText;
        p.buttonUrl = buttonUrl;
        p.type = type;
        p.replyToMsgId = replyToMsgId;
        p.monoforumTopicId = monoforumTopicId;
        p.silent = silent;
        p.app = app;
        p.allowWrite = allowWrite;
        p.startParam = startParam;
        p.botUser = botUser;
        p.flags = flags;
        p.compact = compact;
        p.fullscreen = fullscreen;
        if (!compact && !fullscreen && !TextUtils.isEmpty(buttonUrl)) {
            try {
                Uri uri = Uri.parse(buttonUrl);
                p.compact = TextUtils.equals(uri.getQueryParameter("mode"), "compact");
                p.fullscreen = TextUtils.equals(uri.getQueryParameter("mode"), "fullscreen");
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return p;
    }

    public void applyResponse(TLObject response) {
        this.response = response;
        this.responseTime = System.currentTimeMillis();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof WebViewRequestProps))
            return false;
        final WebViewRequestProps p = (WebViewRequestProps) obj;
        return (
            currentAccount == p.currentAccount &&
            peerId == p.peerId &&
            botId == p.botId &&
            TextUtils.equals(buttonUrl, p.buttonUrl) &&
            type == p.type &&
            replyToMsgId == p.replyToMsgId &&
            silent == p.silent &&
            (app == null ? 0 : app.id) == (p.app == null ? 0 : p.app.id) &&
            allowWrite == p.allowWrite &&
            TextUtils.equals(startParam, p.startParam) &&
            (botUser == null ? 0 : botUser.id) == (p.botUser == null ? 0 : p.botUser.id) &&
            flags == p.flags
        );
    }
}

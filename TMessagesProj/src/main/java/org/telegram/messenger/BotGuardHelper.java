package org.Tajgram.messenger;

import org.Tajgram.messenger.support.LongSparseLongArray;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.ui.ActionBar.BaseFragment;
import org.Tajgram.ui.ActionBar.BottomSheetTabs;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Components.AlertsCreator;
import org.Tajgram.ui.LaunchActivity;
import org.Tajgram.ui.bots.BotWebViewAttachedSheet;
import org.Tajgram.ui.bots.BotWebViewSheet;
import org.Tajgram.ui.bots.WebViewRequestProps;

public class BotGuardHelper extends BaseController {
    private BotGuardHelper(int num) {
        super(num);
    }

    private final LongSparseLongArray queryIdToBotId = new LongSparseLongArray();

    public void openGuardBotWebApp(long dialogId, long guardBotId, TLRPC.TL_webViewResultUrl webview) {
        openGuardBotWebApp(dialogId, guardBotId, webview, false);
    }

    private void openGuardBotWebApp(long dialogId, long guardBotId, TLRPC.TL_webViewResultUrl webview, boolean confirmed) {
        if (LaunchActivity.instance == null) {
            return;
        }
        final BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment == null) {
            return;
        }

        final TLRPC.User botUser = getMessagesController().getUser(guardBotId);

        if (!confirmed) {
            if (SharedPrefsHelper.isWebViewConfirmShown(currentAccount, guardBotId) || getMessagesController().whitelistedBots.contains(guardBotId)) {
                openGuardBotWebApp(dialogId, guardBotId, webview, true);
            } else {
                AlertsCreator.createBotLaunchAlert(lastFragment, botUser, () -> {
                    openGuardBotWebApp(dialogId, guardBotId, webview, true);
                    SharedPrefsHelper.setWebViewConfirmShown(currentAccount, guardBotId, true);
                }, () -> {});
            }
            return;
        }

        queryIdToBotId.put(webview.query_id, guardBotId);

        final BaseFragment parentFragment = LaunchActivity.getLastFragment();
        final Theme.ResourcesProvider resourcesProvider = null;

        final WebViewRequestProps props = WebViewRequestProps.of(currentAccount, dialogId, guardBotId,
            null, null, BotWebViewAttachedSheet.TYPE_WEB_VIEW_GUARD, 0, 0,
            false, null, false, null, null, 0, false, false);

        props.applyResponse(webview);
        BotWebViewSheet webViewSheet = new BotWebViewSheet(LaunchActivity.instance, resourcesProvider);
        webViewSheet.setDefaultFullsize(false);
        webViewSheet.setNeedsContext(true);
        webViewSheet.setParentActivity(LaunchActivity.instance);
        webViewSheet.requestWebView(parentFragment, props);
        webViewSheet.show();
    }

    public void closeGuardBotWebApp(long dialogId, long queryId, TLRPC.JoinChatBotResult result) {
        final long guardBotId = queryIdToBotId.get(queryId, 0);

        getNotificationCenter().postNotificationName(NotificationCenter.guardBotDecisionResult, new GuardBotDecisionResultNotification(dialogId, guardBotId, queryId, result));
        if (BotWebViewSheet.activeSheets != null) {
            for (BotWebViewSheet sheet : BotWebViewSheet.activeSheets) {
                if (sheet.isGuardBotTab(dialogId, queryId)) {
                    sheet.dismiss();
                    break;
                }
            }
        }

        /*final ArrayList<BottomSheetTabs.WebTabData> tabs = bottomSheetTabs.getTabs(currentAccount);
        for (BottomSheetTabs.WebTabData tab : tabs) {
            if (tab.props != null && tab.props.type == BotWebViewAttachedSheet.TYPE_WEB_VIEW_GUARD && tab.props.peerId == dialogId) {
                if (tab.props.response instanceof TLRPC.TL_webViewResultUrl) {
                    final long bQueryId = ((TLRPC.TL_webViewResultUrl) tab.props.response).query_id;
                }
            }
        }*/
    }

    public static class GuardBotDecisionResultNotification {
        public final long dialogId;
        public final long guardBotId;
        public final long queryId;
        public final TLRPC.JoinChatBotResult result;

        public GuardBotDecisionResultNotification(long dialogId, long guardBotId, long queryId, TLRPC.JoinChatBotResult result) {
            this.dialogId = dialogId;
            this.guardBotId = guardBotId;
            this.queryId = queryId;
            this.result = result;
        }
    }



    private static volatile BotGuardHelper[] Instance = new BotGuardHelper[UserConfig.MAX_ACCOUNT_COUNT];
    public static BotGuardHelper getInstance(final int num) {
        BotGuardHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (BotForumHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new BotGuardHelper(num);
                }
            }
        }
        return localInstance;
    }
}

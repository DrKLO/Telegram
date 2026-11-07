package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AuthTokensHelper;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SharedPrefsHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.HintsController;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.bots.BotBiometry;
import org.telegram.ui.bots.BotDownloads;
import org.telegram.ui.bots.BotLocation;
import org.telegram.ui.bots.SetupEmojiStatusSheet;
import org.telegram.utils.settings.SharedSettings;

import java.util.ArrayList;
import java.util.Set;

/** The debug menu, opened by pressing the version in settings. */
public class DebugSettingsActivity extends UniversalFragment {
    private static final int ID_IMPORT_CONTACTS = 1;
    private static final int ID_RELOAD_CONTACTS = 2;
    private static final int ID_RESET_CONTACTS = 3;
    private static final int ID_RESET_DIALOGS = 4;
    private static final int ID_READ_ALL_DIALOGS = 5;
    private static final int ID_LOGS = 6;
    private static final int ID_INAPP_CAMERA = 7;
    private static final int ID_CLEAR_MEDIA_CACHE = 8;
    private static final int ID_CALL_SETTINGS = 9;
    private static final int ID_CHECK_APP_UPDATE = 10;
    private static final int ID_VOIP_AUDIO_EFFECTS = 11;
    private static final int ID_CLEAN_APP_UPDATE = 12;
    private static final int ID_RESET_SUGGESTIONS = 13;
    private static final int ID_CLEAR_WEBVIEW_CACHE = 14;
    private static final int ID_CLEAR_WEBVIEW_COOKIES = 15;
    private static final int ID_WEBVIEW_DEBUG = 16;
    private static final int ID_TABLET_MODE = 17;
    private static final int ID_FLOATING_DEBUG = 18;
    private static final int ID_REMOVE_PREMIUM_SUGGESTIONS = 19;
    private static final int ID_SHARE_DEVICE_INFO = 20;
    private static final int ID_PERFORMANCE_CLASS = 21;
    private static final int ID_BIG_ROUND_CAMERA = 22;
    private static final int ID_DUAL_CAMERA = 23;
    private static final int ID_SURFACE_IN_STORIES = 24;
    private static final int ID_PHOTOVIEWER_BLUR = 25;
    private static final int ID_INVOICE_PAYMENT = 26;
    private static final int ID_UPDATE_ATTACH_BOTS = 27;
    private static final int ID_CAMERA2 = 28;
    private static final int ID_CLEAR_MINIAPPS = 29;
    private static final int ID_CLEAR_LOGIN_TOKENS = 30;
    private static final int ID_NEW_BLUR = 31;
    private static final int ID_BROWSER_COLORS = 32;
    private static final int ID_VIDEO_QUALITIES = 33;
    private static final int ID_SYSTEM_BOLD_FONT = 34;
    private static final int ID_RELOAD_APP_CONFIG = 35;
    private static final int ID_FORCE_FORUM_TABS = 36;
    private static final int ID_MEMORY_DUMP = 37;
    private static final int ID_WALLPAPER_SHADER = 38;
    private static final int ID_FRAME_METRICS = 39;
    private static final int ID_VIEW_METRICS = 41;
    private static final int ID_EXPERIMENTAL = 42;
    private static final int ID_CLEAR_WIDGET_HEIGHTS = 43;
    private static final int ID_WIDGET_STATIC_LAYERS = 44;
    private static final int ID_FORGET_HINTS = 45;
    private static final int ID_COMPACT_MODE = 46;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.DebugMenu);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Contacts and chats"));
        items.add(UItem.asButton(ID_IMPORT_CONTACTS, getString(R.string.DebugMenuImportContacts)));
        items.add(UItem.asButton(ID_RELOAD_CONTACTS, getString(R.string.DebugMenuReloadContacts)));
        items.add(UItem.asButton(ID_RESET_CONTACTS, getString(R.string.DebugMenuResetContacts)));
        items.add(UItem.asButton(ID_RESET_DIALOGS, getString(R.string.DebugMenuResetDialogs)));
        items.add(UItem.asButton(ID_READ_ALL_DIALOGS, getString(R.string.DebugMenuReadAllDialogs)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Data"));
        items.add(UItem.asButton(ID_CLEAR_MEDIA_CACHE, getString(R.string.DebugMenuClearMediaCache)));
        items.add(UItem.asButton(ID_FORGET_HINTS, "Forget all hints"));
        items.add(UItem.asButton(ID_RELOAD_APP_CONFIG, "Reload app config"));
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asButton(ID_RESET_SUGGESTIONS, "Reset suggestions"));
            items.add(UItem.asButton(ID_REMOVE_PREMIUM_SUGGESTIONS, "Force remove premium suggestions"));
            items.add(UItem.asButton(ID_UPDATE_ATTACH_BOTS, "Update Attach Bots"));
        }
        if (BuildVars.DEBUG_VERSION) {
            items.add(UItem.asButton(ID_CLEAR_MINIAPPS, "Clear Mini Apps Permissions and Files"));
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asButton(ID_CLEAR_LOGIN_TOKENS, "Clear all login tokens"));
            items.add(UItem.asButton(ID_CLEAR_WIDGET_HEIGHTS, "Clear widget heights"));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("App"));
        if (!BuildVars.DEBUG_VERSION) {
            items.add(UItem.asCheck(ID_LOGS, getString(R.string.DebugMenuEnableLogs)).setChecked(BuildVars.LOGS_ENABLED));
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION || ApplicationLoader.isStandaloneBuild() || ApplicationLoader.isBetaBuild()) {
            items.add(UItem.asButton(ID_CHECK_APP_UPDATE, getString(R.string.DebugMenuCheckAppUpdate)));
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asButton(ID_CLEAN_APP_UPDATE, "Clean app update"));
            items.add(UItem.asButton(ID_SHARE_DEVICE_INFO, "Share device info"));
            items.add(UItem.asButton(ID_PERFORMANCE_CLASS, "Force performance class"));
        }
        items.add(UItem.asButton(ID_MEMORY_DUMP, "Make Memory Dump"));
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asCheck(ID_FLOATING_DEBUG, getString(R.string.FloatingDebugEnable)).setChecked(SharedConfig.isFloatingDebugActive));
        }
        items.add(UItem.asCheck(ID_EXPERIMENTAL, "Experimental settings").setChecked(SharedSettings.experimentalSettingsAllowed.get()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Interface"));
        if (AndroidUtilities.isTabletInternal() && BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asCheck(ID_TABLET_MODE, "Tablet mode").setChecked(!SharedConfig.forceDisableTabletMode));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            items.add(UItem.asCheck(ID_SYSTEM_BOLD_FONT, getString(R.string.DebugMenuUseSystemBoldFont)).setChecked(SharedConfig.useSystemBoldFont));
        }
        if (SharedConfig.canBlurChat() && Build.VERSION.SDK_INT >= 31) {
            items.add(UItem.asCheck(ID_NEW_BLUR, "New GPU blur").setChecked(SharedConfig.useNewBlur));
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asCheck(ID_PHOTOVIEWER_BLUR, "Blur in photoviewer").setChecked(SharedConfig.photoViewerBlur));
            items.add(UItem.asCheck(ID_WALLPAPER_SHADER, "Wallpaper shader").setChecked(!SharedConfig.fastWallpaperDisabled));
        }
        items.add(UItem.asCheck(ID_FORCE_FORUM_TABS, "Force Forum Tabs").setChecked(SharedConfig.forceForumTabs));
        items.add(UItem.asCheck(ID_FRAME_METRICS, "Frame metrics").setChecked(SharedConfig.frameMetricsEnabled));
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asCheck(ID_VIEW_METRICS, "Debug view metrics").setChecked(SharedConfig.debugViewMetrics));
            items.add(UItem.asCheck(ID_WIDGET_STATIC_LAYERS, "Widget static layers").setChecked(SharedConfig.widgetStaticLayers));
        }
        items.add(UItem.asCheck(ID_COMPACT_MODE, getString(R.string.CompactMode)).setChecked(SharedConfig.compactMode));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Camera and media"));
        items.add(UItem.asCheck(ID_INAPP_CAMERA, getString(R.string.DebugMenuEnableCamera)).setChecked(SharedConfig.inappCamera));
        items.add(UItem.asCheck(ID_CAMERA2, "Use Camera 2 API").setChecked(SharedConfig.isUsingCamera2(currentAccount)));
        items.add(UItem.asCheck(ID_DUAL_CAMERA, getString(R.string.DebugMenuDualOn)).setChecked(DualCameraView.dualAvailableStatic(getContext())));
        if (BuildVars.DEBUG_PRIVATE_VERSION && !InstantCameraView.allowBigSizeCameraDebug()) {
            items.add(UItem.asCheck(ID_BIG_ROUND_CAMERA, "Big camera for round").setChecked(SharedConfig.bigCameraForRound));
        }
        if (BuildVars.DEBUG_VERSION) {
            items.add(UItem.asCheck(ID_SURFACE_IN_STORIES, "SurfaceView in stories").setChecked(SharedConfig.useSurfaceInStories));
        }
        items.add(UItem.asCheck(ID_VIDEO_QUALITIES, "Video qualities debug").setChecked(SharedConfig.debugVideoQualities));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Calls"));
        items.add(UItem.asButton(ID_CALL_SETTINGS, getString(R.string.DebugMenuCallSettings)));
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asCheck(ID_VOIP_AUDIO_EFFECTS, "VoIP audio effects").setChecked(!SharedConfig.disableVoiceAudioEffects));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("WebView"));
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            items.add(UItem.asButton(ID_CLEAR_WEBVIEW_CACHE, getString(R.string.DebugMenuClearWebViewCache)));
        }
        items.add(UItem.asButton(ID_CLEAR_WEBVIEW_COOKIES, getString(R.string.DebugMenuClearWebViewCookies)));
        items.add(UItem.asCheck(ID_WEBVIEW_DEBUG, getString(R.string.DebugMenuEnableWebViewDebug)).setChecked(SharedConfig.debugWebView));
        items.add(UItem.asCheck(ID_BROWSER_COLORS, "Adaptive browser colors").setChecked(SharedConfig.adaptableColorInBrowser));
        items.add(UItem.asCheck(ID_INVOICE_PAYMENT, "Invoice Payment").setChecked(SharedConfig.payByInvoice));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_IMPORT_CONTACTS:
                getUserConfig().syncContacts = true;
                getUserConfig().saveConfig(false);
                getContactsController().forceImportContacts();
                break;
            case ID_RELOAD_CONTACTS:
                getContactsController().loadContacts(false, 0);
                break;
            case ID_RESET_CONTACTS:
                getContactsController().resetImportedContacts();
                break;
            case ID_RESET_DIALOGS:
                getMessagesController().forceResetDialogs();
                break;
            case ID_LOGS: {
                BuildVars.LOGS_ENABLED = !BuildVars.LOGS_ENABLED;
                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
                sharedPreferences.edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED).commit();
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("app start time = " + ApplicationLoader.startTime);
                    try {
                        FileLog.d("buildVersion = " + ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionCode);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                break;
            }
            case ID_INAPP_CAMERA:
                SharedConfig.toggleInappCamera();
                break;
            case ID_CLEAR_MEDIA_CACHE:
                clearSentMediaCache();
                break;
            case ID_COMPACT_MODE:
                SharedConfig.toggleCompactMode();
                Theme.applyChatTheme(false, false);
                break;
            case ID_FORGET_HINTS:
                forgetAllHints();
                break;
            case ID_CALL_SETTINGS:
                VoIPHelper.showCallDebugSettings(getParentActivity());
                break;
            case ID_CHECK_APP_UPDATE:
                ((LaunchActivity) getParentActivity()).checkAppUpdate(true, null);
                break;
            case ID_READ_ALL_DIALOGS:
                getMessagesStorage().readAllDialogs(-1);
                break;
            case ID_VOIP_AUDIO_EFFECTS:
                SharedConfig.toggleDisableVoiceAudioEffects();
                break;
            case ID_CLEAN_APP_UPDATE:
                SharedConfig.pendingAppUpdate = null;
                SharedConfig.saveConfig();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                break;
            case ID_RESET_SUGGESTIONS: {
                Set<String> suggestions = getMessagesController().pendingSuggestions;
                suggestions.add("VALIDATE_PHONE_NUMBER");
                suggestions.add("VALIDATE_PASSWORD");
                getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
                break;
            }
            case ID_CLEAR_WEBVIEW_CACHE:
                ApplicationLoader.applicationContext.deleteDatabase("webview.db");
                ApplicationLoader.applicationContext.deleteDatabase("webviewCache.db");
                WebStorage.getInstance().deleteAllData();
                try {
                    WebView webView = new WebView(ApplicationLoader.applicationContext);
                    webView.clearHistory();
                    webView.destroy();
                } catch (Exception e) {
                }
                break;
            case ID_CLEAR_WEBVIEW_COOKIES: {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookies(null);
                cookieManager.flush();
                break;
            }
            case ID_WEBVIEW_DEBUG:
                SharedConfig.toggleDebugWebView();
                Toast.makeText(getParentActivity(), getString(SharedConfig.debugWebView ? R.string.DebugMenuWebViewDebugEnabled : R.string.DebugMenuWebViewDebugDisabled), Toast.LENGTH_SHORT).show();
                break;
            case ID_TABLET_MODE: {
                SharedConfig.toggleForceDisableTabletMode();
                Activity activity = getParentActivity();
                if (activity != null) {
                    final PackageManager pm = activity.getPackageManager();
                    final Intent intent = pm.getLaunchIntentForPackage(activity.getPackageName());
                    activity.finishAffinity(); // Finishes all activities.
                    activity.startActivity(intent); // Start the launch activity
                }
                System.exit(0);
                break;
            }
            case ID_FLOATING_DEBUG:
                FloatingDebugController.setActive((LaunchActivity) getParentActivity(), !FloatingDebugController.isActive());
                break;
            case ID_REMOVE_PREMIUM_SUGGESTIONS: {
                getMessagesController().loadAppConfig();
                TLRPC.TL_help_dismissSuggestion req = new TLRPC.TL_help_dismissSuggestion();
                req.suggestion = "VALIDATE_PHONE_NUMBER";
                req.peer = new TLRPC.TL_inputPeerEmpty();
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    TLRPC.TL_help_dismissSuggestion req2 = new TLRPC.TL_help_dismissSuggestion();
                    req2.suggestion = "VALIDATE_PASSWORD";
                    req2.peer = new TLRPC.TL_inputPeerEmpty();
                    getConnectionsManager().sendRequest(req2, (res2, err2) -> {
                        getMessagesController().loadAppConfig();
                    });
                });
                break;
            }
            case ID_SHARE_DEVICE_INFO:
                shareDeviceInfo();
                break;
            case ID_PERFORMANCE_CLASS:
                showPerformanceClassDialog();
                break;
            case ID_BIG_ROUND_CAMERA:
                SharedConfig.toggleRoundCamera();
                break;
            case ID_DUAL_CAMERA: {
                boolean enabled = DualCameraView.dualAvailableStatic(getContext());
                MessagesController.getGlobalMainSettings().edit().putBoolean("dual_available", !enabled).apply();
                try {
                    Toast.makeText(getParentActivity(), getString(!enabled ? R.string.DebugMenuDualOnToast : R.string.DebugMenuDualOffToast), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                }
                break;
            }
            case ID_SURFACE_IN_STORIES:
                SharedConfig.toggleSurfaceInStories();
                for (int i = 0; i < getParentLayout().getFragmentStack().size(); i++) {
                    getParentLayout().getFragmentStack().get(i).clearSheets();
                }
                break;
            case ID_PHOTOVIEWER_BLUR:
                SharedConfig.togglePhotoViewerBlur();
                break;
            case ID_INVOICE_PAYMENT:
                SharedConfig.togglePaymentByInvoice();
                break;
            case ID_UPDATE_ATTACH_BOTS:
                getMediaDataController().loadAttachMenuBots(false, true);
                break;
            case ID_CAMERA2:
                SharedConfig.toggleUseCamera2(currentAccount);
                break;
            case ID_CLEAR_MINIAPPS:
                BotBiometry.clear();
                BotLocation.clear();
                BotDownloads.clear();
                SetupEmojiStatusSheet.clear();
                break;
            case ID_CLEAR_LOGIN_TOKENS:
                AuthTokensHelper.clearLogInTokens();
                break;
            case ID_NEW_BLUR:
                SharedConfig.toggleUseNewBlur();
                break;
            case ID_BROWSER_COLORS:
                SharedConfig.toggleBrowserAdaptableColors();
                break;
            case ID_VIDEO_QUALITIES:
                SharedConfig.toggleDebugVideoQualities();
                break;
            case ID_SYSTEM_BOLD_FONT:
                SharedConfig.toggleUseSystemBoldFont();
                break;
            case ID_RELOAD_APP_CONFIG:
                MessagesController.getInstance(currentAccount).loadAppConfig(true);
                break;
            case ID_FORCE_FORUM_TABS:
                SharedConfig.toggleForceForumTabs();
                break;
            case ID_MEMORY_DUMP:
                FileLog.getInstance().dumpMemory(true);
                break;
            case ID_WALLPAPER_SHADER:
                SharedConfig.toggleFastWallpaperDisabled();
                break;
            case ID_FRAME_METRICS:
                SharedConfig.toggleFrameMetricsEnabled();
                if (LaunchActivity.instance != null) {
                    LaunchActivity.instance.checkFrameMetrics();
                }
                break;
            case ID_VIEW_METRICS: {
                final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                prefs.edit().putBoolean("debugViewMetrics", SharedConfig.debugViewMetrics = !SharedConfig.debugViewMetrics).apply();
                break;
            }
            case ID_EXPERIMENTAL:
                SharedSettings.experimentalSettingsAllowed.toggle();
                break;
            case ID_CLEAR_WIDGET_HEIGHTS:
                // Chats opened after this lay their widgets out as on first sight.
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) MessagesStorage.getInstance(a).clearWidgetHeights();
                }
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Widget heights cleared").show();
                break;
            case ID_WIDGET_STATIC_LAYERS:
                // Frames drawn from now on follow it; one already in a layer keeps it until the next frame.
                SharedConfig.toggleWidgetStaticLayers();
                break;
            default:
                return;
        }
        if (item.viewType == UniversalAdapter.VIEW_TYPE_CHECK) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void clearSentMediaCache() {
        getMessagesStorage().clearSentMedia();
        ChatThemeController.getInstance(currentAccount).clearCache();
        getMessagesController().getMainSettings().edit().remove("peerColors").remove("profilePeerColors").apply();
    }

    private void forgetAllHints() {
        SharedConfig.setNoSoundHintShowed(false);
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.remove("archivehint").remove("proximityhint").remove("archivehint_l").remove("searchpostsnew").remove("speedhint").remove("gifhint").remove("reminderhint").remove("soundHint").remove("themehint").remove("bganimationhint").remove("filterhint").remove("n_0").remove("storyprvhint").remove("storyhint").remove("storyhint2").remove("storydualhint").remove("storysvddualhint").remove("stories_camera").remove("dualcam").remove("dualmatrix").remove("dual_available").remove("archivehint").remove("askNotificationsAfter").remove("askNotificationsDuration").remove("viewoncehint").remove("voicepausehint").remove("taptostorysoundhint").remove("nothanos").remove("voiceoncehint").remove("savedhint").remove("savedsearchhint").remove("savedsearchtaghint").remove("newppsms").remove("monetizationadshint").remove("seekSpeedHintShowed").remove("unsupport_video/av01").remove("statusgiftpage").remove("multistorieshint").remove("trimvoicehint").remove("taptostoryhighlighthint").remove("proxycheckstatusip").remove("callmiconstart").remove("showchattagsinfo").remove("language_showed2").remove("aihintshown").remove("savedmsgschatshint").remove("walletcardstickerhint").apply();
        HintsController.resetAll();
        SharedPrefsHelper.cleanupAccount(currentAccount);
        MessagesController.getEmojiSettings(currentAccount).edit().remove("featured_hidden").remove("emoji_featured_hidden").commit();
        MessagesController.getGlobalNotificationsSettings().edit().remove("disable_sharing_learn").remove("askedAboutFSILockscreen").apply();
        SharedConfig.textSelectionHintShows = 0;
        SharedConfig.lockRecordAudioVideoHint = 0;
        SharedConfig.stickersReorderingHintUsed = false;
        SharedConfig.forwardingOptionsHintShown = false;
        SharedConfig.replyingOptionsHintShown = false;
        SharedConfig.messageSeenHintCount = 3;
        SharedConfig.emojiInteractionsHintCount = 3;
        SharedConfig.dayNightThemeSwitchHintCount = 3;
        SharedConfig.fastScrollHintCount = 3;
        SharedConfig.stealthModeSendMessageConfirm = 2;
        SharedConfig.updateStealthModeSendMessageConfirm(2);
        SharedConfig.setStoriesReactionsLongPressHintUsed(false);
        SharedConfig.setStoriesIntroShown(false);
        SharedConfig.setMultipleReactionsPromoShowed(false);
        getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
        RestrictedLanguagesSelectActivity.cleanup();
        PersistColorPalette.getInstance(currentAccount).cleanup();
        SharedPreferences prefs = getMessagesController().getMainSettings();
        editor = prefs.edit();
        editor.remove("boostingappearance").remove("bizbothint").remove("movecaptionhint");
        for (String key : prefs.getAll().keySet()) {
            if (key.contains("show_gift_for_") || key.contains("bdayhint_") || key.contains("bdayanim_") || key.startsWith("ask_paid_message_") || key.startsWith("topicssidetabs")) {
                editor.remove(key);
            }
        }
        editor.apply();
        editor = MessagesController.getNotificationsSettings(currentAccount).edit();
        for (String key : MessagesController.getNotificationsSettings(currentAccount).getAll().keySet()) {
            if (key.startsWith("dialog_bar_botver")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private void showPerformanceClassDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle("Force performance class");
        int currentClass = SharedConfig.getDevicePerformanceClass();
        int trueClass = SharedConfig.measureDevicePerformanceClass();
        builder.setItems(new CharSequence[]{
                AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? "**HIGH**" : "HIGH") + (trueClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? " (measured)" : "")),
                AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? "**AVERAGE**" : "AVERAGE") + (trueClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? " (measured)" : "")),
                AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_LOW ? "**LOW**" : "LOW") + (trueClass == SharedConfig.PERFORMANCE_CLASS_LOW ? " (measured)" : ""))
        }, (dialog, which) -> {
            int newClass = 2 - which;
            if (newClass == trueClass) {
                SharedConfig.overrideDevicePerformanceClass(-1);
            } else {
                SharedConfig.overrideDevicePerformanceClass(newClass);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void shareDeviceInfo() {
        int cpuCount = ConnectionsManager.CPU_COUNT;
        int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        long minFreqSum = 0, minFreqCount = 0;
        long maxFreqSum = 0, maxFreqCount = 0;
        long curFreqSum = 0, curFreqCount = 0;
        long capacitySum = 0, capacityCount = 0;
        StringBuilder cpusInfo = new StringBuilder();
        for (int i = 0; i < cpuCount; i++) {
            Long minFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_min_freq");
            Long curFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_cur_freq");
            Long maxFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            Long capacity = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpu_capacity");
            cpusInfo.append("#").append(i).append(" ");
            if (minFreq != null) {
                cpusInfo.append("min=").append(minFreq / 1000L).append(" ");
                minFreqSum += (minFreq / 1000L);
                minFreqCount++;
            }
            if (curFreq != null) {
                cpusInfo.append("cur=").append(curFreq / 1000L).append(" ");
                curFreqSum += (curFreq / 1000L);
                curFreqCount++;
            }
            if (maxFreq != null) {
                cpusInfo.append("max=").append(maxFreq / 1000L).append(" ");
                maxFreqSum += (maxFreq / 1000L);
                maxFreqCount++;
            }
            if (capacity != null) {
                cpusInfo.append("cpc=").append(capacity).append(" ");
                capacitySum += capacity;
                capacityCount++;
            }
            cpusInfo.append("\n");
        }
        StringBuilder info = new StringBuilder();
        info.append(Build.MANUFACTURER).append(", ").append(Build.MODEL).append(" (").append(Build.PRODUCT).append(", ").append(Build.DEVICE).append(") ").append(" (android ").append(Build.VERSION.SDK_INT).append(")\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.append("SoC: ").append(Build.SOC_MANUFACTURER).append(", ").append(Build.SOC_MODEL).append("\n");
        }
        String gpuModel = AndroidUtilities.getSysInfoString("/sys/kernel/gpu/gpu_model");
        if (gpuModel != null) {
            info.append("GPU: ").append(gpuModel);
            Long minClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_min_clock");
            Long mminClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_mm_min_clock");
            Long maxClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_max_clock");
            if (minClock != null) {
                info.append(", min=").append(minClock / 1000L);
            }
            if (mminClock != null) {
                info.append(", mmin=").append(mminClock / 1000L);
            }
            if (maxClock != null) {
                info.append(", max=").append(maxClock / 1000L);
            }
            info.append("\n");
        }
        ConfigurationInfo configurationInfo = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getDeviceConfigurationInfo();
        info.append("GLES Version: ").append(configurationInfo.getGlEsVersion()).append("\n");
        info.append("Memory: class=").append(AndroidUtilities.formatFileSize(memoryClass * 1024L * 1024L));
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        info.append(", total=").append(AndroidUtilities.formatFileSize(memoryInfo.totalMem));
        info.append(", avail=").append(AndroidUtilities.formatFileSize(memoryInfo.availMem));
        info.append(", low?=").append(memoryInfo.lowMemory);
        info.append(" (threshold=").append(AndroidUtilities.formatFileSize(memoryInfo.threshold)).append(")");
        info.append("\n");
        info.append("Current class: ").append(SharedConfig.performanceClassName(SharedConfig.getDevicePerformanceClass())).append(", measured: ").append(SharedConfig.performanceClassName(SharedConfig.measureDevicePerformanceClass()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.append(", suggest=").append(Build.VERSION.MEDIA_PERFORMANCE_CLASS);
        }
        info.append("\n");
        info.append(cpuCount).append(" CPUs");
        if (minFreqCount > 0) {
            info.append(", avgMinFreq=").append(minFreqSum / minFreqCount);
        }
        if (curFreqCount > 0) {
            info.append(", avgCurFreq=").append(curFreqSum / curFreqCount);
        }
        if (maxFreqCount > 0) {
            info.append(", avgMaxFreq=").append(maxFreqSum / maxFreqCount);
        }
        if (capacityCount > 0) {
            info.append(", avgCapacity=").append(capacitySum / capacityCount);
        }
        info.append("\n").append(cpusInfo);

        listCodecs("video/avc", info);
        listCodecs("video/hevc", info);
        listCodecs("video/x-vnd.on2.vp8", info);
        listCodecs("video/x-vnd.on2.vp9", info);

        showDialog(new ShareAlert(getParentActivity(), null, info.toString(), false, null, false) {
            @Override
            protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                if (!showToast) return;
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.createInviteSentBulletin(getParentActivity(), contentView, dids.size(), dids.size() == 1 ? dids.valueAt(0).id : 0, count, getThemedColor(Theme.key_undo_background), getThemedColor(Theme.key_undo_infoColor)).show();
                }, 250);
            }
        });
    }

    private void listCodecs(String type, StringBuilder info) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return;
        }
        try {
            final int allCodecsCount = MediaCodecList.getCodecCount();
            final ArrayList<Integer> decoderIndexes = new ArrayList<>();
            final ArrayList<Integer> encoderIndexes = new ArrayList<>();
            boolean first = true;
            for (int i = 0; i < allCodecsCount; ++i) {
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(i);
                if (codec == null) {
                    continue;
                }
                String[] types = codec.getSupportedTypes();
                if (types == null) {
                    continue;
                }
                boolean found = false;
                for (int j = 0; j < types.length; ++j) {
                    if (types[j].equals(type)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    (codec.isEncoder() ? encoderIndexes : decoderIndexes).add(i);
                }
            }
            if (decoderIndexes.isEmpty() && encoderIndexes.isEmpty()) {
                return;
            }
            info.append("\n").append(decoderIndexes.size()).append("+").append(encoderIndexes.size()).append(" ").append(type.substring(6)).append(" codecs:\n");
            for (int a = 0; a < decoderIndexes.size(); ++a) {
                if (a > 0) {
                    info.append("\n");
                }
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(decoderIndexes.get(a));
                info.append("{d} ").append(codec.getName()).append(" (");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (codec.isHardwareAccelerated()) {
                        info.append("gpu"); // as Gpu
                    }
                    if (codec.isSoftwareOnly()) {
                        info.append("cpu"); // as Cpu
                    }
                    if (codec.isVendor()) {
                        info.append(", v"); // as Vendor
                    }
                }
                MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(type);
                info.append("; mi=").append(capabilities.getMaxSupportedInstances()).append(")");
            }
            for (int a = 0; a < encoderIndexes.size(); ++a) {
                if (a > 0 || !decoderIndexes.isEmpty()) {
                    info.append("\n");
                }
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(encoderIndexes.get(a));
                info.append("{e} ").append(codec.getName()).append(" (");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (codec.isHardwareAccelerated()) {
                        info.append("gpu"); // as Gpu
                    }
                    if (codec.isSoftwareOnly()) {
                        info.append("cpu"); // as Cpu
                    }
                    if (codec.isVendor()) {
                        info.append(", v"); // as Vendor
                    }
                }
                MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(type);
                info.append("; mi=").append(capabilities.getMaxSupportedInstances()).append(")");
            }
            info.append("\n");
        } catch (Exception ignore) {
        }
    }
}

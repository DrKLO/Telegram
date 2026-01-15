package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MessagesController.findUpdatesAndRemove;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Keep;

import com.google.firebase.appindexing.Action;
import com.google.firebase.appindexing.FirebaseUserActions;
import com.google.firebase.appindexing.builders.AssistActionBuilder;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ContactsLoadingObserver;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SaveToGallerySettingsHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stars.BotStarsActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.TON.TONIntroActivity;
import org.telegram.ui.bots.ChannelAffiliateProgramsFragment;
import org.telegram.ui.web.WebBrowserSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;

public class LinkManager {

    private final LaunchActivity activity;
    private final int currentAccount;
    private final Browser.Progress progress;

    private AlertDialog progressDialog;

    private boolean inited, done;
    private int currentRequestId = -1;

    public LinkManager(LaunchActivity activity, int account, Browser.Progress progress) {
        this.activity = activity;
        this.currentAccount = account;
        this.progress = progress;
    }

    public boolean handle(Uri uri) {
        if (uri == null) return false;

        final String scheme = uri.getScheme();

        if ("tonsite".equalsIgnoreCase(scheme))
            return handleTonsite(uri);

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
            return handleHttp(uri);

        if ("tg".equalsIgnoreCase(scheme))
            return handleTg(uri);

        return false;
    }

    private boolean handleTonsite(Uri uri) {
        Browser.openUrl(activity, uri);
        return true;
    }

    private boolean handleHttp(Uri uri) {
        final String host = uri.getHost();
        if (host == null) return false;
        final Matcher prefixMatcher = LaunchActivity.PREFIX_T_ME_PATTERN.matcher(host.toLowerCase());
        final boolean isPrefix = prefixMatcher.find();
        if (!"telegram.me".equalsIgnoreCase(host) && !"t.me".equalsIgnoreCase(host) && !"telegram.dog".equalsIgnoreCase(host) && !isPrefix)
            return false;

        if (isPrefix) {
            uri = Uri.parse("https://t.me/" + prefixMatcher.group(1) + (TextUtils.isEmpty(uri.getPath()) ? "" : uri.getPath()) + (TextUtils.isEmpty(uri.getQuery()) ? "" : "?" + uri.getQuery()));
        }

        String path = uri.getPath();
        if (path == null || path.length() <= 1) return false;
        path = path.substring(1);

        final List<String> segments = uri.getPathSegments();
        if (segments == null || segments.isEmpty()) return false;

        final String first = segments.get(0);
        final String second = segments.size() > 1 ? segments.get(1) : null;

        if ("$".equalsIgnoreCase(first))
            return handleInvoiceSlug(path.substring(1));
        if ("invoice".equalsIgnoreCase(first))
            return handleInvoiceSlug(second);

        return false;
    }

    private Uri normalizeTgUri(Uri uri) {
        if (uri == null || !uri.isOpaque()) return uri;

        final String scheme = uri.getScheme();
        if (scheme == null) return uri;

        if (uri.getAuthority() != null) return uri;

        final String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) return uri;

        return Uri.parse(scheme + "://" + ssp);
    }

    private boolean handleTg(Uri uri) {
        uri = normalizeTgUri(uri);

        final List<String> _segments = uri.getPathSegments();
        if (_segments == null) return false;
        final ArrayList<String> segments = new ArrayList<>(_segments);
        final String authority = uri.getAuthority();
        if (!TextUtils.isEmpty(authority))
            segments.add(0, authority);
        if (segments.isEmpty()) return false;

        final String first = segments.get(0);
        final String second = segments.size() > 1 ? segments.get(1) : null;

        if ("invoice".equalsIgnoreCase(first))
            return handleInvoiceSlug(uri.getQueryParameter("slug"));

        if ("settings".equalsIgnoreCase(first))
            return handleSettings(segments.subList(1, segments.size()));

        if ("chats".equalsIgnoreCase(first)) {
            if ("search".equalsIgnoreCase(second)) {
                // TODO
            }
            if ("edit".equalsIgnoreCase(second)) {
                // TODO
            }
            if ("emoji-status".equalsIgnoreCase(second)) {
                // TODO
            }
        }

        if ("new".equalsIgnoreCase(first)) {
            if ("group".equalsIgnoreCase(second)) {
                presentFragment(new GroupCreateActivity(new Bundle()), false);
                return true;
            }
            if ("contact".equalsIgnoreCase(second)) {
                new NewContactBottomSheet(getLastFragment(), activity).show();
                return true;
            }
            if ("channel".equalsIgnoreCase(second)) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                    Bundle args = new Bundle();
                    args.putInt("step", 0);
                    presentFragment(new ChannelCreateActivity(args));
                } else {
                    presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                    preferences.edit().putBoolean("channel_intro", true).commit();
                }
                return true;
            }

            final Bundle args = new Bundle();
            args.putBoolean("destroyAfterSelect", true);
            presentFragment(new ContactsActivity(args));
            return true;
        }

        if ("post".equalsIgnoreCase(first)) {
            int mode = StoryRecorder.MODE_PHOTO;
            if ("video".equalsIgnoreCase(second))
                mode = StoryRecorder.MODE_VIDEO;
            if ("live".equalsIgnoreCase(second))
                mode = StoryRecorder.MODE_LIVE;
            StoryRecorder.getInstance(activity, currentAccount)
                .setMode(mode)
                .open(null);
            return true;
        }

        if ("contacts".equalsIgnoreCase(first)) {
            if ("new".equalsIgnoreCase(second)) {
                new NewContactBottomSheet(getLastFragment(), activity).show();
                return true;
            }

            final Bundle args = new Bundle();
            args.putBoolean("needPhonebook", true);
            args.putBoolean("needFinishFragment", true);
            presentFragment(new ContactsActivity(args));

            if ("search".equalsIgnoreCase(second)) {
                // TODO
            }
            if ("sort".equalsIgnoreCase(second)) {
                // TODO
            }
            if ("invite".equalsIgnoreCase(second)) {
                scrollTo("phonebookRow");
            }
            return true;
        }

        return false;
    }

    // tg://settings/*
    private boolean handleSettings(final List<String> segments) {
        if (segments == null) return false;
        if (segments.isEmpty()) {
            presentFragment(new SettingsActivity());
            return true;
        }

        final String first  = segments.get(0);
        final String second = segments.size() > 1 ? segments.get(1) : null;
        final String third  = segments.size() > 2 ? segments.get(2) : null;
        final String fourth = segments.size() > 3 ? segments.get(3) : null;
        final String fifth  = segments.size() > 4 ? segments.get(4) : null;

        // legacy paths:
        if ("theme".equalsIgnoreCase(first) || "themes".equalsIgnoreCase(first)) { // open_settings = 2;
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            return true;
        }
        if ("devices".equalsIgnoreCase(first)) { // open_settings = 3;
            presentFragment(new SessionsActivity(0));
            if ("terminate-sessions".equalsIgnoreCase(second))
                scrollTo("terminateAllSessionsRow");
            if ("auto-terminate".equalsIgnoreCase(second))
                scrollTo("ttlRow");
            return true;
        }
        if ("folders".equalsIgnoreCase(first)) { // open_settings = 4;

            final FiltersSetupActivity f = new FiltersSetupActivity();
            presentFragment(new FiltersSetupActivity());

            if ("create".equalsIgnoreCase(second)) {
                AndroidUtilities.runOnUIThread(() -> f.createFolder(getParentLayout()), 300);
            }
            if ("show-tags".equalsIgnoreCase(second))
                scrollTo("showTagsRow");

            return true;
        }
        if ("change_number".equalsIgnoreCase(first)) { // open_settings = 5;
            presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER), true);
            return true;
        }
        if ("language".equalsIgnoreCase(first)) { // open_settings = 10;
            if ("do-not-translate".equalsIgnoreCase(second)) {
                presentFragment(new RestrictedLanguagesSelectActivity());
                return true;
            }
            presentFragment(new LanguageSelectActivity());
            if ("show-button".equalsIgnoreCase(second)) {
                scrollTo("manualTranslationPosition");
            }
            if ("translate-chats".equalsIgnoreCase(second)) {
                scrollTo("autoTranslationPosition");
            }
            return true;
        }
        if ("auto_delete".equalsIgnoreCase(first)) { // open_settings = 11;
            presentFragment(new AutoDeleteMessagesActivity());
            return true;
        }
        if ("phone_privacy".equalsIgnoreCase(first)) { // open_settings = 14;
            presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE));
            return true;
        }
        if ("premium_sms".equalsIgnoreCase(first)) { // open_settings = 13;
            if (ApplicationLoader.applicationLoaderInstance != null) {
                final BaseFragment fragment = ApplicationLoader.applicationLoaderInstance.openSettings(13);
                if (fragment != null) {
                    presentFragment(fragment);
                    return true;
                }
            }
        }
        if ("login_email".equalsIgnoreCase(first)) { // open_settings = 15;
            init();
            setRequestId(getConnectionsManager().sendRequest(new TL_account.getPassword(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                done();
                if (response != null) {
                    final TL_account.Password password = (TL_account.Password) response;
                    activity.openEmailSettings(password);
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin));
            return true;
        }

        if ("chats".equalsIgnoreCase(first)) {
            final INavigationLayout layout = getParentLayout();
            MainTabsActivity mainFragment = null;
            for (int i = layout.getFragmentStack().size() - 1; i >= 0; ++i) {
                if (layout.getFragmentStack().get(i) instanceof MainTabsActivity) {
                    mainFragment = (MainTabsActivity) layout.getFragmentStack().get(i);
                    break;
                }
                if (i > 0) {
                    layout.removeFragmentFromStack(i);
                }
            }

            if (mainFragment != null) {
                if ("search".equalsIgnoreCase(second)) {
                    mainFragment.viewPager.scrollToPosition(2);
                    return true;
                }
            }
        }

        if ("saved-messages".equalsIgnoreCase(first)) {
            presentFragment(ChatActivity.of(getUserConfig().getClientUserId()));
            return true;
        }

        if ("calls".equalsIgnoreCase(first)) {
            if ("start-call".equalsIgnoreCase(second)) {
                Bundle args = new Bundle();
                args.putBoolean("isCall", true);
                final GroupCreateActivity fragment = new GroupCreateActivity(args) {
                    @Override
                    protected void onCallUsersSelected(HashSet<Long> users, boolean video) {
                        if (users.size() == 1) {
                            final TLRPC.User user = getMessagesController().getUser(users.iterator().next());
                            TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
                            if (userFull == null) {
                                final TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
                                req.id = getMessagesController().getInputUser(user.id);
                                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                    TLRPC.UserFull newUserFull = null;
                                    if (res instanceof TLRPC.TL_users_userFull) {
                                        final TLRPC.TL_users_userFull r = (TLRPC.TL_users_userFull) res;
                                        getMessagesController().putUsers(r.users, false);
                                        getMessagesController().putChats(r.chats, false);
                                        newUserFull = r.full_user;
                                    }
                                    VoIPHelper.startCall(user, video, newUserFull != null && newUserFull.video_calls_available, getParentActivity(), newUserFull, getAccountInstance());
                                }));
                                return;
                            }
                            VoIPHelper.startCall(user, video, userFull != null && userFull.video_calls_available, getParentActivity(), userFull, getAccountInstance());
                        } else {
                            final TL_phone.createConferenceCall req = new TL_phone.createConferenceCall();
                            req.random_id = Utilities.random.nextInt();
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                if (res instanceof TLRPC.Updates) {
                                    final TLRPC.Updates updates = (TLRPC.Updates) res;
                                    MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
                                    MessagesController.getInstance(currentAccount).putChats(updates.chats, false);

                                    TLRPC.GroupCall groupCall = null;
                                    for (TLRPC.TL_updateGroupCall u : findUpdatesAndRemove(updates, TLRPC.TL_updateGroupCall.class)) {
                                        groupCall = u.call;
                                    }

                                    if (LaunchActivity.instance == null) {
                                        return;
                                    }
                                    if (groupCall != null) {
                                        final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
                                        inputGroupCall.id = groupCall.id;
                                        inputGroupCall.access_hash = groupCall.access_hash;
                                        VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCall, video, groupCall, users);
                                    }
                                } else if (res instanceof TL_phone.groupCall) {
                                    final TL_phone.groupCall r = (TL_phone.groupCall) res;
                                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                                    MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                                    if (LaunchActivity.instance == null) {
                                        return;
                                    }
                                    final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
                                    inputGroupCall.id = r.call.id;
                                    inputGroupCall.access_hash = r.call.access_hash;
                                    VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCall, video, r.call, users);
                                } else if (err != null) {
                                    getBulletinFactory().showForError(err);
                                }
                            }));
                        }
                        finishFragment();
                    }
                };
                presentFragment(fragment);
                return true;
            }
            presentFragment(new CallLogActivity());
            return true;
        }

        if ("qr-code".equalsIgnoreCase(first)) {
//            if ("share".equalsIgnoreCase(second)) {
//
//            }
            if ("scan".equalsIgnoreCase(second)) {
                BaseFragment lastFragment = getLastFragment();
                if (lastFragment != null) {
                    QrActivity.openCameraScanActivity(lastFragment);
                    return true;
                }
            }

            final Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            presentFragment(new QrActivity(args));
            return true;
        }

        if ("chat".equalsIgnoreCase(first) && "browser".equalsIgnoreCase(second)) {
            if (TextUtils.isEmpty(third)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                scrollTo("browserRow");
                return true;
            }

            presentFragment(new WebBrowserSettings(null));

            if ("enable-browser".equalsIgnoreCase(third))
                scrollTo("enableRow");
            if ("clear-cookies".equalsIgnoreCase(third))
                scrollTo("clearCookiesRow");
            if ("clear-cache".equalsIgnoreCase(third))
                scrollTo("clearCacheRow");
            if ("history".equalsIgnoreCase(third))
                scrollTo("historyRow");
            if ("clear-history".equalsIgnoreCase(third))
                scrollTo("clearHistoryRow");
            if ("never-open".equalsIgnoreCase(third))
                scrollTo("neverOpenRow");
            if ("clear-list".equalsIgnoreCase(third))
                scrollTo("clearListRow");
            if ("search".equalsIgnoreCase(third))
                scrollTo("searchRow");
            return true;
        }

        if ("edit".equalsIgnoreCase(first)) {
            presentFragment(new UserInfoActivity());
            if ("first-name".equalsIgnoreCase(second))
                scrollTo("firstNameRow");
            if ("last-name".equalsIgnoreCase(second))
                scrollTo("lastNameRow");
            if ("bio".equalsIgnoreCase(second))
                scrollTo("bioRow");
            if ("birthday".equalsIgnoreCase(second))
                scrollTo("birthdayRow");
            if ("change-number".equalsIgnoreCase(second))
                scrollTo("numberRow");
            if ("username".equalsIgnoreCase(second))
                scrollTo("usernameRow");
            if ("channel".equalsIgnoreCase(second))
                scrollTo("channelRow");
            if ("add-account".equalsIgnoreCase(second))
                scrollTo("addAccountRow");
            if ("log-out".equalsIgnoreCase(second))
                scrollTo("logoutRow");
            return true;
        }

        if ("my-profile".equalsIgnoreCase(first)) {
            if ("edit".equalsIgnoreCase(second)) {
                presentFragment(new UserInfoActivity());
                return true;
            }

            final Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            args.putBoolean("my_profile", true);
            if ("gifts".equalsIgnoreCase(second)) {
                args.putBoolean("open_gifts", true);
            }
            final ProfileActivity f = new ProfileActivity(args);
            if ("gifts".equalsIgnoreCase(second)) {
                f.whenFullyVisible(() -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (f.sharedMediaLayout != null) {
                            f.sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_GIFTS);
                            f.scrollToSharedMedia();
                        }
                    }, 200);
                });
            }
            if ("posts".equalsIgnoreCase(second)) {
                f.whenFullyVisible(() -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (f.sharedMediaLayout != null) {
                            f.sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_GIFTS);
                            f.scrollToSharedMedia();
                        }
                    }, 200);
                });
            }
            presentFragment(f);

            return true;
        }

        if ("notifications".equalsIgnoreCase(first)) {
            if (!TextUtils.isEmpty(third) && (
                "private-chats".equalsIgnoreCase(second) ||
                "groups".equalsIgnoreCase(second) ||
                "channels".equalsIgnoreCase(second) ||
                "stories".equalsIgnoreCase(second) ||
                "reactions".equalsIgnoreCase(second)
            )) {
                int _type = 0;
                if      ("private-chats".equalsIgnoreCase(second)) _type = NotificationsController.TYPE_PRIVATE;
                else if ("groups".equalsIgnoreCase(second))        _type = NotificationsController.TYPE_GROUP;
                else if ("channels".equalsIgnoreCase(second))      _type = NotificationsController.TYPE_CHANNEL;
                else if ("stories".equalsIgnoreCase(second))       _type = NotificationsController.TYPE_STORIES;
                else if ("reactions".equalsIgnoreCase(second))     _type = NotificationsController.TYPE_REACTIONS_MESSAGES;
                final int type = _type;

                final NotificationsSettingsActivity b = new NotificationsSettingsActivity();
                init();
                b.loadExceptions(() -> {
                    done();

                    final NotificationsCustomSettingsActivity f = b.makeNotificationsCustomSettingsActivity(type);
                    f.expanded = true;
                    f.updateRows(false);
                    presentFragment(f);

                    if ("show".equalsIgnoreCase(third))
                        scrollTo("showRow");
                    if ("new".equalsIgnoreCase(third))
                        scrollTo("newRow");
                    if ("important".equalsIgnoreCase(third))
                        scrollTo("importantRow");
                    if ("messages".equalsIgnoreCase(third))
                        scrollTo("messagesRow");
                    if ("stories".equalsIgnoreCase(third))
                        scrollTo("storiesRow");
                    if ("preview".equalsIgnoreCase(third))
                        scrollTo("previewRow");
                    if ("show-sender".equalsIgnoreCase(third))
                        scrollTo("showSenderRow");
                    if ("sound".equalsIgnoreCase(third))
                        scrollTo("soundRow");
                    if ("add-exception".equalsIgnoreCase(third))
                        scrollTo("addExceptionRow");
                    if ("delete-exceptions".equalsIgnoreCase(third))
                        scrollTo("deleteExceptionsRow");
                    if ("light-color".equalsIgnoreCase(third))
                        scrollTo("lightColorRow");
                    if ("vibrate".equalsIgnoreCase(third))
                        scrollTo("vibrateRow");
                    if ("popup".equalsIgnoreCase(third))
                        scrollTo("popupRow");
                    if ("priority".equalsIgnoreCase(third))
                        scrollTo("priorityRow");
                });
                return true;
            }

            presentFragment(new NotificationsSettingsActivity());

            if ("accounts".equalsIgnoreCase(second))
                scrollTo("accountsAllRow");
            if ("private-chats".equalsIgnoreCase(second))
                scrollTo("privateRow");
            if ("groups".equalsIgnoreCase(second))
                scrollTo("groupRow");
            if ("channels".equalsIgnoreCase(second))
                scrollTo("channelsRow");
            if ("stories".equalsIgnoreCase(second))
                scrollTo("storiesRow");
            if ("reactions".equalsIgnoreCase(second))
                scrollTo("reactionsRow");

            if ("in-app-sounds".equalsIgnoreCase(second))
                scrollTo("inappSoundRow");
            if ("in-app-vibrate".equalsIgnoreCase(second))
                scrollTo("inappVibrateRow");
            if ("in-app-preview".equalsIgnoreCase(second))
                scrollTo("inappPreviewRow");
            if ("in-chat-sounds".equalsIgnoreCase(second))
                scrollTo("inchatSoundRow");
            if ("in-app-popup".equalsIgnoreCase(second))
                scrollTo("inappPriorityRow");
            if ("show-badge-icon".equalsIgnoreCase(second))
                scrollTo("badgeNumberShowRow");
            if ("include-muted-chats".equalsIgnoreCase(second))
                scrollTo("badgeNumberMutedRow");
            if ("count-unread-messages".equalsIgnoreCase(second))
                scrollTo("badgeNumberMessagesRow");
            if ("new-contacts".equalsIgnoreCase(second))
                scrollTo("contactJoinedRow");
            if ("pinned-messages".equalsIgnoreCase(second))
                scrollTo("pinnedMessageRow");
            if ("reset".equalsIgnoreCase(second))
                scrollTo("resetNotificationsRow");

            return true;
        }

        if ("privacy".equalsIgnoreCase(first)) { // open_settings = 12;
            if ("data-settings".equalsIgnoreCase(second) && "delete-cloud-drafts".equalsIgnoreCase(third)) {
                presentFragment(new DataSettingsActivity());
                scrollTo("clearDraftsRow");
                return true;
            }

            if (!TextUtils.isEmpty(third) && "blocked".equalsIgnoreCase(second)) {
                presentFragment(new PrivacyUsersActivity());
                return true;
            }
            if (!TextUtils.isEmpty(third) && "active-websites".equalsIgnoreCase(second)) {
                presentFragment(new SessionsActivity(SessionsActivity.TYPE_WEB_SESSIONS));

                if ("disconnect-all".equalsIgnoreCase(third))
                    scrollTo("terminateAllSessionsRow");
                return true;
            }
            if (!TextUtils.isEmpty(third) && "passcode".equalsIgnoreCase(second)) {
                final Runnable opened = () -> {
                    if ("disable".equalsIgnoreCase(third))
                        scrollTo("disablePasscodeRow");
                    if ("change".equalsIgnoreCase(third))
                        scrollTo("changePasscodeRow");
                    if ("auto-lock".equalsIgnoreCase(third))
                        scrollTo("autoLockRow");
                    if ("fingerprint".equalsIgnoreCase(third))
                        scrollTo("fingerprintRow");
                };

                final BaseFragment f = PasscodeActivity.determineOpenFragment();
                presentFragment(f);

                if (f instanceof ActionIntroActivity) {
                    ((ActionIntroActivity) f).setOnOpenedSettings(opened);
                } else if (f instanceof PasscodeActivity) {
                    ((PasscodeActivity) f).setOnOpenedSettings(opened);
                }

                return true;
            }

            if (!TextUtils.isEmpty(third) && "2sv".equalsIgnoreCase(second)) {
                init();
                setRequestId(getConnectionsManager().sendRequest(new TL_account.getPassword(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    done();
                    if (response == null) return;
                    final TL_account.Password password = (TL_account.Password) response;

                    if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, false)) {
                        AlertsCreator.showUpdateAppAlert(activity, getString(R.string.UpdateAppAlert), true);
                    }
                    final Runnable opened = () -> {
                        if ("disable".equalsIgnoreCase(third))
                            scrollTo("turnPasswordOffRow");
                        if ("change".equalsIgnoreCase(third))
                            scrollTo("changePasswordRow");
                        if ("change-email".equalsIgnoreCase(third))
                            scrollTo("emailRow");
                    };
                    if (password.has_password) {
                        final TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                        fragment.setPassword(password);
                        presentFragment(fragment);
                        opened.run();
                    } else {
                        int type;
                        if (TextUtils.isEmpty(password.email_unconfirmed_pattern)) {
                            type = TwoStepVerificationSetupActivity.TYPE_INTRO;
                        } else {
                            type = TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM;
                        }
                        final TwoStepVerificationSetupActivity f = new TwoStepVerificationSetupActivity(type, password);
                        f.setOnOpenedSettings(opened);
                        presentFragment(f);
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin));
                return true;
            }
            if (!TextUtils.isEmpty(third) && "passkey".equalsIgnoreCase(second) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                init();
                setRequestId(getConnectionsManager().sendRequestTyped(new TL_account.getPasskeys(), AndroidUtilities::runOnUIThread, (passkeys, error) -> {
                    done();
                    if (passkeys == null) return;

                    presentFragment(new PasskeysActivity(passkeys.passkeys));

                    if ("create".equalsIgnoreCase(third))
                        scrollTo("addPasskeyRow");
                }));
                return true;
            }
            if (!TextUtils.isEmpty(third) && "auto-delete".equalsIgnoreCase(second) && getUserConfig().getGlobalTTl() >= 0) {
                presentFragment(new AutoDeleteMessagesActivity());
//                // TODO
//                if ("set-custom".equalsIgnoreCase(third))
//                    scrollTo("");
                return true;
            }
            if (!TextUtils.isEmpty(third) && (
                "phone-number".equalsIgnoreCase(second) ||
                "last-seen".equalsIgnoreCase(second) ||
                "profile-photos".equalsIgnoreCase(second) ||
                "bio".equalsIgnoreCase(second) ||
                "gifts".equalsIgnoreCase(second) ||
                "birthday".equalsIgnoreCase(second) ||
                "saved-music".equalsIgnoreCase(second) ||
                "forwards".equalsIgnoreCase(second) ||
                "calls".equalsIgnoreCase(second) ||
                "voice".equalsIgnoreCase(second) ||
                "messages".equalsIgnoreCase(second) ||
                "invites".equalsIgnoreCase(second)
            )) {
                int type = 0;
                if      ("phone-number".equalsIgnoreCase(second))   type = ContactsController.PRIVACY_RULES_TYPE_PHONE;
                else if ("last-seen".equalsIgnoreCase(second))      type = ContactsController.PRIVACY_RULES_TYPE_LASTSEEN;
                else if ("profile-photos".equalsIgnoreCase(second)) type = ContactsController.PRIVACY_RULES_TYPE_PHOTO;
                else if ("bio".equalsIgnoreCase(second))            type = ContactsController.PRIVACY_RULES_TYPE_BIO;
                else if ("gifts".equalsIgnoreCase(second))          type = ContactsController.PRIVACY_RULES_TYPE_GIFTS;
                else if ("birthday".equalsIgnoreCase(second))       type = ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY;
                else if ("saved-music".equalsIgnoreCase(second))    type = ContactsController.PRIVACY_RULES_TYPE_MUSIC;
                else if ("forwards".equalsIgnoreCase(second))       type = ContactsController.PRIVACY_RULES_TYPE_FORWARDS;
                else if ("calls".equalsIgnoreCase(second))
                    if ("p2p".equalsIgnoreCase(third))             type = ContactsController.PRIVACY_RULES_TYPE_P2P;
                    else                                            type = ContactsController.PRIVACY_RULES_TYPE_CALLS;
                else if ("voice".equalsIgnoreCase(second))          type = ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES;
                else if ("messages".equalsIgnoreCase(second))       type = ContactsController.PRIVACY_RULES_TYPE_MESSAGES;
                else if ("invites".equalsIgnoreCase(second))        type = ContactsController.PRIVACY_RULES_TYPE_INVITE;

                presentFragment(new PrivacyControlActivity(type));

                if ("birthday".equalsIgnoreCase(second) && "add".equalsIgnoreCase(third))
                    scrollTo("setBirthdayRow");
                if ("always-share".equalsIgnoreCase(third) || "always-share".equalsIgnoreCase(fourth) ||
                    "always".equalsIgnoreCase(third) || "always".equalsIgnoreCase(fourth))
                    scrollTo("everybodyRow");
                if ("never-share".equalsIgnoreCase(third) || "never-share".equalsIgnoreCase(fourth) ||
                    "never".equalsIgnoreCase(third) || "never".equalsIgnoreCase(fourth))
                    scrollTo("nobodyRow");
                if ("gifts".equalsIgnoreCase(second) && "show-icon".equalsIgnoreCase(third))
                    scrollTo("showGiftIconRow");
                if ("gifts".equalsIgnoreCase(second) && "accepted-types".equalsIgnoreCase(third))
                    scrollTo("giftTypesHeaderRow");
                if ("messages".equalsIgnoreCase(second) && "set-price".equalsIgnoreCase(third))
                    scrollTo("priceRow");
                if ("messages".equalsIgnoreCase(second) && "remove-fee".equalsIgnoreCase(third))
                    scrollTo("alwaysShareRow");
                if ("last-seen".equalsIgnoreCase(second) && "hide-read-time".equalsIgnoreCase(third))
                    scrollTo("readRow");
                if ("profile-photos".equalsIgnoreCase(second)) {
                    if ("set-public".equalsIgnoreCase(third))
                        scrollTo("photoForRestRow");
                    if ("update-public".equalsIgnoreCase(third))
                        scrollTo("photoForRestRow");
                    if ("remove-public".equalsIgnoreCase(third))
                        scrollTo("currentPhotoForRestRow");
                }

                return true;
            }


            presentFragment(new PrivacySettingsActivity());

            if ("blocked".equalsIgnoreCase(second))
                scrollTo("blockedRow");
            if ("active-websites".equalsIgnoreCase(second))
                scrollTo("webSessionsRow");
            if ("passcode".equalsIgnoreCase(second))
                scrollTo("passcodeRow");
            if ("2sv".equalsIgnoreCase(second))
                scrollTo("passwordRow");
            if ("passkey".equalsIgnoreCase(second))
                scrollTo("passkeysRow");
            if ("auto-delete".equalsIgnoreCase(second))
                scrollTo("autoDeleteMesages");
            if ("login-email".equalsIgnoreCase(second))
                scrollTo("emailLoginRow");
            if ("phone-number".equalsIgnoreCase(second))
                scrollTo("phoneNumberRow");
            if ("last-seen".equalsIgnoreCase(second))
                scrollTo("lastSeenRow");
            if ("profile-photos".equalsIgnoreCase(second))
                scrollTo("profilePhotoRow");
            if ("bio".equalsIgnoreCase(second))
                scrollTo("bioRow");
            if ("gifts".equalsIgnoreCase(second))
                scrollTo("giftsRow");
            if ("birthday".equalsIgnoreCase(second))
                scrollTo("birthdayRow");
            if ("saved-music".equalsIgnoreCase(second))
                scrollTo("musicRow");
            if ("forwards".equalsIgnoreCase(second))
                scrollTo("forwardsRow");
            if ("calls".equalsIgnoreCase(second))
                scrollTo("callsRow");
            if ("voice".equalsIgnoreCase(second))
                scrollTo("voicesRow");
            if ("messages".equalsIgnoreCase(second))
                scrollTo("noncontactsRow");
            if ("invites".equalsIgnoreCase(second))
                scrollTo("groupsRow");
            if ("self-destruct".equalsIgnoreCase(second))
                scrollTo("deleteAccountRow");
            if ("archive-and-mute".equalsIgnoreCase(second))
                scrollTo("newChatsRow");
            if ("data-settings".equalsIgnoreCase(second)) {
                if ("sync-contacts".equalsIgnoreCase(third))
                    scrollTo("contactsSyncRow");
                if ("delete-synced".equalsIgnoreCase(third))
                    scrollTo("contactsDeleteRow");
                if ("suggest-contacts".equalsIgnoreCase(third))
                    scrollTo("contactsSuggestRow");
                if ("clear-payment-info".equalsIgnoreCase(third))
                    scrollTo("paymentsClearRow");
                if ("link-previews".equalsIgnoreCase(third))
                    scrollTo("secretWebpageRow");
                if ("map-provider".equalsIgnoreCase(third))
                    scrollTo("secretMapRow");
            }

            return true;
        }

        if ("data".equalsIgnoreCase(first)) {
            if ("storage".equalsIgnoreCase(second)) {
                if ("clear-cache".equalsIgnoreCase(third)) {

                }

                presentFragment(new CacheControlActivity());
                return true;
            }

            if ("usage".equalsIgnoreCase(second)) {
                final DataUsage2Activity f = new DataUsage2Activity();
                presentFragment(f);
                if ("mobile".equalsIgnoreCase(third))
                    f.selectTab(DataUsage2Activity.TYPE_MOBILE);
                if ("wifi".equalsIgnoreCase(third))
                    f.selectTab(DataUsage2Activity.TYPE_WIFI);
                if ("roaming".equalsIgnoreCase(third))
                    f.selectTab(DataUsage2Activity.TYPE_ROAMING);
                if ("reset".equalsIgnoreCase(third))
                    f.scrollToReset();
                return true;
            }

            if ("auto-download".equalsIgnoreCase(second)) {
                if (
                    "mobile".equalsIgnoreCase(third) ||
                    "wifi".equalsIgnoreCase(third) ||
                    "roaming".equalsIgnoreCase(third)
                ) {
                    int type = 0;
                    if      ("mobile".equalsIgnoreCase(third))  type = 0;
                    else if ("wifi".equalsIgnoreCase(third))    type = 1;
                    else if ("roaming".equalsIgnoreCase(third)) type = 2;
                    presentFragment(new DataAutoDownloadActivity(type));

                    if ("enable".equalsIgnoreCase(fourth))
                        scrollTo("autoDownloadRow");
                    if ("usage".equalsIgnoreCase(fourth))
                        scrollTo("usageProgressRow");
                    if ("photos".equalsIgnoreCase(fourth))
                        scrollTo("photosRow");
                    if ("stories".equalsIgnoreCase(fourth))
                        scrollTo("storiesRow");
                    if ("videos".equalsIgnoreCase(fourth))
                        scrollTo("videosRow");
                    if ("files".equalsIgnoreCase(fourth))
                        scrollTo("filesRow");
                    return true;
                }

                if ("reset".equalsIgnoreCase(third)) {
                    presentFragment(new DataSettingsActivity());
                    scrollTo("resetDownloadRow");
                    return true;
                }
            }

            if (!TextUtils.isEmpty(fourth) && "save-to-photos".equalsIgnoreCase(second)) {
                int flag;
                if      ("groups".equalsIgnoreCase(third))   flag = SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP;
                else if ("channels".equalsIgnoreCase(third)) flag = SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS;
                else                                         flag = SharedConfig.SAVE_TO_GALLERY_FLAG_PEER;

                final Bundle bundle = new Bundle();
                bundle.putInt("type", flag);
                presentFragment(new SaveToGallerySettingsActivity(bundle));

                if ("max-video-size".equalsIgnoreCase(fourth))
                    scrollTo("maxVideoSizeRow");
                if ("add-exception".equalsIgnoreCase(fourth))
                    scrollTo("addExceptionRow");
                if ("delete-all".equalsIgnoreCase(fourth))
                    scrollTo("deleteAllExceptionsRow");

                return true;
            }

            if (!TextUtils.isEmpty(third) && "proxy".equalsIgnoreCase(second)) {
                presentFragment(new ProxyListActivity());

                if ("use-proxy".equalsIgnoreCase(third))
                    scrollTo("useProxyRow");
                if ("add-proxy".equalsIgnoreCase(third))
                    scrollTo("proxyAddRow");
                if ("use-for-calls".equalsIgnoreCase(third))
                    scrollTo("callsRow");

                return true;
            }

            if ("pause-music".equalsIgnoreCase(second)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                scrollTo("pauseOnMediaRow");
                return true;
            }
            if ("pause-music-on-record".equalsIgnoreCase(second)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                scrollTo("pauseOnRecordRow");
                return true;
            }
            if ("raise-to-listen".equalsIgnoreCase(second)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                scrollTo("raiseToListenRow");
                return true;
            }
            if ("raise-to-speak".equalsIgnoreCase(second)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                scrollTo("raiseToSpeakRow");
                return true;
            }
            if ("show-18-contnet".equalsIgnoreCase(second)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                scrollTo("sensitiveContentRow");
                return true;
            }

            presentFragment(new DataSettingsActivity());

            if ("save-to-photos".equalsIgnoreCase(second)) {
                if ("chats".equalsIgnoreCase(third))
                    scrollTo("saveToGalleryPeerRow");
                if ("groups".equalsIgnoreCase(third))
                    scrollTo("saveToGalleryGroupsRow");
                if ("channels".equalsIgnoreCase(third))
                    scrollTo("saveToGalleryChannelsRow");
            }

            if ("use-less-data".equalsIgnoreCase(second))
                scrollTo("useLessDataForCallsRow");

            if ("proxy".equalsIgnoreCase(second))
                scrollTo("proxyRow");

            return true;
        }

        if ("appearance".equalsIgnoreCase(first)) {
            if ("themes".equalsIgnoreCase(second) || "theme".equalsIgnoreCase(second)) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_THEMES_BROWSER));
                if ("create".equalsIgnoreCase(third))
                    scrollTo("createNewThemeRow");
                return true;
            }
            if (!TextUtils.isEmpty(third) && ("wallpaper".equalsIgnoreCase(second) || "wallpapers".equalsIgnoreCase(second))) {
                presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
                if ("set".equalsIgnoreCase(third) || "choose-photo".equalsIgnoreCase(third))
                    scrollTo("uploadImageRow");
                return true;
            }
            if (!TextUtils.isEmpty(third) && ("your-color".equalsIgnoreCase(second) || "color".equalsIgnoreCase(second))) {
                final PeerColorActivity f = new PeerColorActivity(0);
                // TODO
                presentFragment(f);
                return true;
            }
            if (!TextUtils.isEmpty(third) && "stickers-and-emoji".equalsIgnoreCase(second)) {
                if (!TextUtils.isEmpty(fourth) && "archived".equalsIgnoreCase(third)) {
                    presentFragment(new ArchivedStickersActivity(MediaDataController.TYPE_IMAGE));
                    return true;
                }
                if (
                    "emoji".equalsIgnoreCase(third) &&
                    !TextUtils.isEmpty(fourth) &&
                    !"large".equalsIgnoreCase(fourth) &&
                    !"dynamic-order".equalsIgnoreCase(fourth)
                ) {
                    if (!TextUtils.isEmpty(fifth) && "archived".equalsIgnoreCase(fourth)) {
                        presentFragment(new ArchivedStickersActivity(MediaDataController.TYPE_EMOJIPACKS));
                        return true;
                    }

                    presentFragment(new StickersActivity(MediaDataController.TYPE_EMOJIPACKS, null));
                    if ("suggest".equalsIgnoreCase(fourth))
                        scrollTo("suggestRow");

                    return true;
                }

                presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE, null));

                if ("trending".equalsIgnoreCase(third))
                    scrollTo("featuredRow");
                if ("archived".equalsIgnoreCase(third))
                    scrollTo("archivedRow");
                if ("emoji".equalsIgnoreCase(third) && "large".equalsIgnoreCase(fourth))
                    scrollTo("largeEmojiRow");
                else if ("emoji".equalsIgnoreCase(third) && "dynamic-order".equalsIgnoreCase(fourth))
                    scrollTo("dynamicPackOrder");
                else if ("emoji".equalsIgnoreCase(third))
                    scrollTo("emojiPacksRow");

                return true;
            }

            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            if ("wallpaper".equalsIgnoreCase(second) || "wallpapers".equalsIgnoreCase(second)) {
                scrollTo("backgroundRow");
            }
            if ("your-color".equalsIgnoreCase(second) || "color".equalsIgnoreCase(second)) {
                scrollTo("changeUserColor");
            }
//            if ("night-mode".equalsIgnoreCase(second) || "dark-mode".equalsIgnoreCase(second) || "dark".equalsIgnoreCase(second) || "night".equalsIgnoreCase(second))
//                scrollTo("nightmode?") // TODO
            if ("auto-night-mode".equalsIgnoreCase(second))
                scrollTo("nightThemeRow");
            if ("text-size".equalsIgnoreCase(second))
                scrollTo("textSizeRow");
            if ("message-corners".equalsIgnoreCase(second))
                scrollTo("bubbleRadiusRow");
            if ("animations".equalsIgnoreCase(second))
                scrollTo("liteModeRow");
            if ("stickers-and-emoji".equalsIgnoreCase(second))
                scrollTo("stickersRow");
            if ("app-icon".equalsIgnoreCase(second))
                scrollTo("appIconSelectorRow");
            if ("tap-for-next-media".equalsIgnoreCase(second))
                scrollTo("nextMediaTapRow");

            return true;
        }

        if ("power-saving".equalsIgnoreCase(first)) {
            final LiteModeSettingsActivity f = new LiteModeSettingsActivity();
            presentFragment(f);
            if ("videos".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAG_AUTOPLAY_VIDEOS);
            if ("gifs".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAG_AUTOPLAY_GIFS);
            if ("stickers".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAGS_ANIMATED_STICKERS);
            if ("emoji".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAGS_ANIMATED_EMOJI);
            if ("effects".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAGS_CHAT);
            if ("call-animations".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAG_CALLS_ANIMATIONS);
            if ("particles".equalsIgnoreCase(second))
                f.scrollToFlags(LiteMode.FLAG_PARTICLES);
            if ("transitions".equalsIgnoreCase(second))
                f.scrollToType(LiteModeSettingsActivity.SWITCH_TYPE_SMOOTH_TRANSITIONS);
            return true;
        }

        if ("stars".equalsIgnoreCase(first)) {
            if ("top-up".equalsIgnoreCase(second)) {
                new StarsIntroActivity.StarsOptionsSheet(activity, null).show();
                return true;
            }
            if ("stats".equalsIgnoreCase(second)) {
                presentFragment(new BotStarsActivity(BotStarsActivity.TYPE_STARS, getUserConfig().getClientUserId()));
                return true;
            }
            if ("gift".equalsIgnoreCase(second)) {
                StarsController.getInstance(currentAccount).getGiftOptions();
                UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STARS, 0, BirthdayController.getInstance(currentAccount).getState());
                return true;
            }
            if ("earn".equalsIgnoreCase(second)) {
                presentFragment(new ChannelAffiliateProgramsFragment(getUserConfig().getClientUserId()));
                return true;
            }
            presentFragment(new StarsIntroActivity());
            return true;
        }

        if ("premium".equalsIgnoreCase(first)) {
            presentFragment(new PremiumPreviewFragment("link"));
            return true;
        }

        if ("business".equalsIgnoreCase(first)) {
            presentFragment(new PremiumPreviewFragment(PremiumPreviewFragment.FEATURES_BUSINESS, "link"));
            if ("do-not-hide-ads".equalsIgnoreCase(second)) {
                scrollTo("showAdsRow");
            }
            return true;
        }

        if ("ton".equalsIgnoreCase(first)) {
            presentFragment(new TONIntroActivity());
            return true;
        }

        if ("send-gift".equalsIgnoreCase(first)) {
            if ("self".equalsIgnoreCase(second)) {
                new GiftSheet(activity, currentAccount, getUserConfig().getClientUserId(), null, null).show();
                return true;
            }

            UserSelectorBottomSheet.open(0, BirthdayController.getInstance(currentAccount).getState());
            return true;
        }

        if ("ask-question".equalsIgnoreCase(first)) {
            AlertsCreator.createSupportAlert(getLastFragment(), null).show();
            return true;
        }
        if ("faq".equalsIgnoreCase(first)) {
            Browser.openUrl(activity, LocaleController.getString(R.string.TelegramFaqUrl));
            return true;
        }
        if ("features".equalsIgnoreCase(first)) {
            Browser.openUrl(activity, LocaleController.getString(R.string.TelegramFeaturesUrl));
            return true;
        }
        if ("privacy-policy".equalsIgnoreCase(first)) {
            Browser.openUrl(activity, LocaleController.getString(R.string.PrivacyPolicyUrl));
            return true;
        }

        presentFragment(new SettingsActivity());
        return true;
    }

    private boolean handleInvoiceSlug(String slug) {
        if (TextUtils.isEmpty(slug)) return false;

        init();

        final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final TLRPC.TL_inputInvoiceSlug invoiceSlug = new TLRPC.TL_inputInvoiceSlug();
        invoiceSlug.slug = slug;
        req.invoice = invoiceSlug;
        final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if ("SUBSCRIPTION_ALREADY_ACTIVE".equalsIgnoreCase(error.text)) {
                    getBulletinFactory().createErrorBulletin(LocaleController.getString(R.string.PaymentInvoiceSubscriptionLinkAlreadyPaid)).show();
                } else {
                    getBulletinFactory().createErrorBulletin(LocaleController.getString(R.string.PaymentInvoiceLinkInvalid)).show();
                }
            } else if (!activity.isFinishing()) {
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.TL_payments_paymentFormStars) {
                    final Runnable callback = activity.navigateToPremiumGiftCallback;
                    activity.navigateToPremiumGiftCallback = null;
                    StarsController.getInstance(currentAccount).openPaymentForm(null, invoiceSlug, (TLRPC.TL_payments_paymentFormStars) response, () -> {
                        done();
                    }, status -> {
                        if (callback != null && "paid".equals(status)) {
                            callback.run();
                        }
                    });
                    return;
                } else if (response instanceof TLRPC.PaymentForm) {
                    final TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                    paymentFormActivity = new PaymentFormActivity(form, slug, getLastFragment());
                } else if (response instanceof TLRPC.PaymentReceipt) {
                    paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                }

                if (paymentFormActivity != null) {
                    if (activity.navigateToPremiumGiftCallback != null) {
                        Runnable callback = activity.navigateToPremiumGiftCallback;
                        activity.navigateToPremiumGiftCallback = null;
                        paymentFormActivity.setPaymentFormCallback(status -> {
                            if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                                callback.run();
                            }
                        });
                    }
                    presentFragment(paymentFormActivity);
                }
            }

            done();
        }));
        setRequestId(reqId);

        return true;
    }

    private void setRequestId(int requestId) {
        currentRequestId = requestId;
    }

    private void presentFragment(BaseFragment fragment) {
        presentFragment(fragment, false);
    }
    private void presentFragment(BaseFragment fragment, boolean removeLast) {
        activity.presentFragment(fragment, removeLast, false);

        if (AndroidUtilities.isTablet()) {
            activity.actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            activity.rightActionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        }
    }
    private INavigationLayout getParentLayout() {
        return activity.getActionBarLayout();
    }

    private void scrollTo(String rowName) {
        AndroidUtilities.scrollToFragmentRow(getParentLayout(), rowName);
    }

    private BaseFragment getLastFragment() {
        return LaunchActivity.getSafeLastFragment();
    }

    private BulletinFactory getBulletinFactory() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return BulletinFactory.global();
        return BulletinFactory.of(fragment);
    }

    public UserConfig getUserConfig() {
        return UserConfig.getInstance(currentAccount);
    }

    public MessagesController getMessagesController() {
        return MessagesController.getInstance(currentAccount);
    }

    public ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(currentAccount);
    }

    private void init() {
        if (inited || done) return;

        if (progress == null) {
            if (progressDialog == null) {
                progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
            }
            progressDialog.setOnCancelListener(di -> cancel());
            progressDialog.showDelayed(300);
        } else {
            progress.onCancel(this::cancel);
            progress.init();
        }

        inited = true;
    }

    private void cancel() {
        if (currentRequestId >= 0) {
            getConnectionsManager().cancelRequest(currentRequestId, true);
            currentRequestId = -1;
        }
    }

    private void done() {
        if (done) return;

        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        if (progress != null) {
            progress.end();
        }

        done = true;
    }

}

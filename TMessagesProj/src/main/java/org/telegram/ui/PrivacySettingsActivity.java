/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.bots.BotBiometry;
import org.telegram.ui.bots.BotBiometrySettings;

import java.util.ArrayList;

public class PrivacySettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private AlertDialog progressDialog;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private TL_account.Password currentPassword;

    private int privacySectionRow;
    private int blockedRow;
    private int phoneNumberRow;
    private int lastSeenRow;
    private int profilePhotoRow;
    private int bioRow;
    private int giftsRow;
    private int birthdayRow;
    private int forwardsRow;
    private int callsRow;
    private int voicesRow;
    private int noncontactsRow;
    private int emailLoginRow;
    private int privacyShadowRow;
    private int groupsRow;
    private int groupsDetailRow;
    private int securitySectionRow;
    private int passwordRow;
    private int sessionsRow;
    private int passcodeRow;
    private int autoDeleteMesages;
    private int sessionsDetailRow;
    private int newChatsHeaderRow;
    private int newChatsRow;
    private int newChatsSectionRow;
    private int advancedSectionRow;
    private int deleteAccountRow;
    private int deleteAccountDetailRow;
    private int botsSectionRow;
    private int passportRow;
    private int paymentsClearRow;
    private int webSessionsRow;
    private int botsBiometryRow;
    private int botsDetailRow;
    private int botsAndWebsitesShadowRow;
    private int contactsSectionRow;
    private int contactsDeleteRow;
    private int contactsSuggestRow;
    private int contactsSyncRow;
    private int contactsDetailRow;
    private int secretSectionRow;
    private int secretMapRow;
    private int secretWebpageRow;
    private int secretDetailRow;
    private int rowCount;

    private final ArrayList<BotBiometry.Bot> biometryBots = new ArrayList<>();

    private boolean deleteAccountUpdate;
    private boolean secretMapUpdate;
    private boolean currentSync;
    private boolean newSync;
    private boolean currentSuggest;
    private boolean newSuggest;
    private boolean archiveChats;
    private boolean noncontactsValue;
    private boolean feeValue;

    private boolean[] clear = new boolean[2];
    private SessionsActivity devicesActivityPreload;
    private SessionsActivity webSessionsActivityPreload;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        getContactsController().loadPrivacySettings();
        getMessagesController().getBlockedPeers(true);
        currentSync = newSync = getUserConfig().syncContacts;
        currentSuggest = newSuggest = getUserConfig().suggestContacts;
        TLRPC.GlobalPrivacySettings privacySettings = getContactsController().getGlobalPrivacySettings();
        if (privacySettings != null) {
            archiveChats = privacySettings.archive_and_mute_new_noncontact_peers;
            noncontactsValue = privacySettings.new_noncontact_peers_require_premium;
            feeValue = (privacySettings.flags & 32) != 0;
        }

        updateRows();
        loadPasswordSettings();

        getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.blockedUsersDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.didSetOrRemoveTwoStepPassword);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateGlobalAutoDeleteTimer);

        getUserConfig().loadGlobalTTl();

        devicesActivityPreload = new SessionsActivity(SessionsActivity.TYPE_DEVICES);
        devicesActivityPreload.setDelegate(() -> {
            if (listAdapter != null && sessionsRow >= 0) {
                listAdapter.notifyItemChanged(sessionsRow);
            }
        });
        devicesActivityPreload.loadSessions(false);

        webSessionsActivityPreload = new SessionsActivity(SessionsActivity.TYPE_WEB_SESSIONS);
        webSessionsActivityPreload.setDelegate(() -> {
            if (listAdapter != null) {
                int webSessionsCount = webSessionsActivityPreload.getSessionsCount();
                if (webSessionsRow < 0 && webSessionsCount > 0) {
                    updateRows();
                }
            }
        });
        webSessionsActivityPreload.loadSessions(false);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.blockedUsersDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.didSetOrRemoveTwoStepPassword);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateGlobalAutoDeleteTimer);
        boolean save = false;
        if (currentSync != newSync) {
            getUserConfig().syncContacts = newSync;
            save = true;
            if (newSync) {
                getContactsController().forceImportContacts();
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), getString("SyncContactsAdded", R.string.SyncContactsAdded), Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (newSuggest != currentSuggest) {
            if (!newSuggest) {
                getMediaDataController().clearTopPeers();
            }
            getUserConfig().suggestContacts = newSuggest;
            save = true;
            TLRPC.TL_contacts_toggleTopPeers req = new TLRPC.TL_contacts_toggleTopPeers();
            req.enabled = newSuggest;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
        TLRPC.GlobalPrivacySettings globalPrivacySettings = getContactsController().getGlobalPrivacySettings();
        if (globalPrivacySettings != null && globalPrivacySettings.archive_and_mute_new_noncontact_peers != archiveChats) {
            globalPrivacySettings.archive_and_mute_new_noncontact_peers = archiveChats;
            save = true;
            TL_account.setGlobalPrivacySettings req = new TL_account.setGlobalPrivacySettings();
            req.settings = getContactsController().getGlobalPrivacySettings();
            if (req.settings == null) {
                req.settings = new TLRPC.TL_globalPrivacySettings();
            }
            req.settings.archive_and_mute_new_noncontact_peers = archiveChats;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
        if (save) {
            getUserConfig().saveConfig(false);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.PrivacySettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutAnimation(null);
        listView.setItemAnimator(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (!view.isEnabled()) {
                return;
            }
            if (position == autoDeleteMesages) {
                if (getUserConfig().getGlobalTTl() >= 0) {
                    presentFragment(new AutoDeleteMessagesActivity());
                }
            } if (position == blockedRow) {
                presentFragment(new PrivacyUsersActivity());
            } else if (position == sessionsRow) {
                devicesActivityPreload.resetFragment();
                presentFragment(devicesActivityPreload);
            } else if (position == webSessionsRow) {
                webSessionsActivityPreload.resetFragment();
                presentFragment(webSessionsActivityPreload);
            } else if (position == deleteAccountRow) {
                if (getParentActivity() == null) {
                    return;
                }
                int ttl = getContactsController().getDeleteAccountTTL();
                int selected;
                if (ttl <= 31) {
                    selected = 0;
                } else if (ttl <= 93) {
                    selected = 1;
                } else if (ttl <= 182) {
                    selected = 2;
                } else if (ttl == 548) {
                    selected = 4;
                } else if (ttl == 730) {
                    selected = 5;
                } else {
                    selected = 3;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString("DeleteAccountTitle", R.string.DeleteAccountTitle));
                String[] items = new String[]{
                    formatPluralString("Months", 1),
                    formatPluralString("Months", 3),
                    formatPluralString("Months", 6),
                    formatPluralString("Months", 12),
                    formatPluralString("Months", 18),
                    formatPluralString("Months", 24)
                };
                final LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                for (int a = 0; a < items.length; a++) {
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(dp(4), 0, dp(4), 0);
                    cell.setTag(a);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[a], selected == a);
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        builder.getDismissRunnable().run();
                        Integer which = (Integer) v.getTag();

                        int value = 0;
                        if (which == 0) {
                            value = 30;
                        } else if (which == 1) {
                            value = 90;
                        } else if (which == 2) {
                            value = 182;
                        } else if (which == 3) {
                            value = 365;
                        } else if (which == 4) {
                            value = 548;
                        } else if (which == 5) {
                            value = 730;
                        }
                        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.setCanCancel(false);
                        progressDialog.show();

                        final TL_account.setAccountTTL req = new TL_account.setAccountTTL();
                        req.ttl = new TLRPC.TL_accountDaysTTL();
                        req.ttl.days = value;
                        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (response instanceof TLRPC.TL_boolTrue) {
                                deleteAccountUpdate = true;
                                getContactsController().setDeleteAccountTTL(req.ttl.days);
                                listAdapter.notifyDataSetChanged();
                            }
                        }));
                    });
                }
                builder.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == lastSeenRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN));
            } else if (position == phoneNumberRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE));
            } else if (position == groupsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE));
            } else if (position == callsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_CALLS));
            } else if (position == profilePhotoRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHOTO));
            } else if (position == bioRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_BIO));
            } else if (position == birthdayRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY));
            } else if (position == giftsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_GIFTS));
            } else if (position == forwardsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_FORWARDS));
            } else if (position == voicesRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES));
            } else if (position == noncontactsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_MESSAGES));
            } else if (position == emailLoginRow) {
                if (currentPassword == null || currentPassword.login_email_pattern == null) {
                    return;
                }

                SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(currentPassword.login_email_pattern);
                int startIndex = currentPassword.login_email_pattern.indexOf('*');
                int endIndex = currentPassword.login_email_pattern.lastIndexOf('*');
                if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                    run.start = startIndex;
                    run.end = endIndex + 1;
                    spannable.setSpan(new TextStyleSpan(run), startIndex, endIndex + 1, 0);
                }

                new AlertDialog.Builder(context)
                        .setTitle(spannable)
                        .setMessage(getString(R.string.EmailLoginChangeMessage))
                        .setPositiveButton(getString(R.string.ChangeEmail), (dialog, which) -> presentFragment(new LoginActivity().changeEmail(() -> {
                            Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), null);
                            layout.setAnimation(R.raw.email_check_inbox);
                            layout.textView.setText(getString(R.string.YourLoginEmailChangedSuccess));
                            int duration = Bulletin.DURATION_SHORT;
                            Bulletin.make(PrivacySettingsActivity.this, layout, duration).show();

                            try {
                                fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            } catch (Exception ignored) {}

                            loadPasswordSettings();
                        })))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
            } else if (position == passwordRow) {
                if (currentPassword == null) {
                    return;
                }
                if (!TwoStepVerificationActivity.canHandleCurrentPassword(currentPassword, false)) {
                    AlertsCreator.showUpdateAppAlert(getParentActivity(), getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                }
                if (currentPassword.has_password) {
                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                    fragment.setPassword(currentPassword);
                    presentFragment(fragment);
                } else {
                    int type;
                    if (TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern)) {
                        type = TwoStepVerificationSetupActivity.TYPE_INTRO;
                    } else {
                        type = TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM;
                    }
                    presentFragment(new TwoStepVerificationSetupActivity(type, currentPassword));
                }
            } else if (position == passcodeRow) {
                presentFragment(PasscodeActivity.determineOpenFragment());
            } else if (position == secretWebpageRow) {
                if (getMessagesController().secretWebpagePreview == 1) {
                    getMessagesController().secretWebpagePreview = 0;
                } else {
                    getMessagesController().secretWebpagePreview = 1;
                }
                MessagesController.getGlobalMainSettings().edit().putInt("secretWebpage2", getMessagesController().secretWebpagePreview).commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(getMessagesController().secretWebpagePreview == 1);
                }
            } else if (position == contactsDeleteRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString("SyncContactsDeleteTitle", R.string.SyncContactsDeleteTitle));
                builder.setMessage(AndroidUtilities.replaceTags(getString("SyncContactsDeleteText", R.string.SyncContactsDeleteText)));
                builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                builder.setPositiveButton(getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                    AlertDialog.Builder builder12 = new AlertDialog.Builder(getParentActivity(), 3, null);
                    progressDialog = builder12.show();
                    progressDialog.setCanCancel(false);

                    if (currentSync != newSync) {
                        currentSync = getUserConfig().syncContacts = newSync;
                        getUserConfig().saveConfig(false);
                    }
                    getContactsController().deleteAllContacts(() -> progressDialog.dismiss());
                });
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            } else if (position == contactsSuggestRow) {
                final TextCheckCell cell = (TextCheckCell) view;
                if (newSuggest) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(getString("SuggestContactsTitle", R.string.SuggestContactsTitle));
                    builder.setMessage(getString("SuggestContactsAlert", R.string.SuggestContactsAlert));
                    builder.setPositiveButton(getString("MuteDisable", R.string.MuteDisable), (dialogInterface, i) -> {
                        TLRPC.TL_payments_clearSavedInfo req = new TLRPC.TL_payments_clearSavedInfo();
                        req.credentials = clear[1];
                        req.info = clear[0];
                        getUserConfig().tmpPassword = null;
                        getUserConfig().saveConfig(false);
                        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            newSuggest = !newSuggest;
                            cell.setChecked(newSuggest);
                        }));
                    });
                    builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                } else {
                    cell.setChecked(newSuggest = true);
                }
            } else if (position == newChatsRow) {
                final TextCheckCell cell = (TextCheckCell) view;
                archiveChats = !archiveChats;
                cell.setChecked(archiveChats);
            } else if (position == contactsSyncRow) {
                newSync = !newSync;
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newSync);
                }
            } else if (position == secretMapRow) {
                AlertsCreator.showSecretLocationAlert(getParentActivity(), currentAccount, () -> {
                    listAdapter.notifyDataSetChanged();
                    secretMapUpdate = true;
                }, false, null);
            } else if (position == paymentsClearRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString("PrivacyPaymentsClearAlertTitle", R.string.PrivacyPaymentsClearAlertTitle));
                builder.setMessage(getString("PrivacyPaymentsClearAlertText", R.string.PrivacyPaymentsClearAlertText));

                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                for (int a = 0; a < 2; a++) {
                    String name;
                    if (a == 0) {
                        name = getString("PrivacyClearShipping", R.string.PrivacyClearShipping);
                    } else {
                        name = getString("PrivacyClearPayment", R.string.PrivacyClearPayment);
                    }
                    clear[a] = true;
                    CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), 1, 21, null);
                    checkBoxCell.setTag(a);
                    checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    checkBoxCell.setPadding(dp(4), 0, dp(4), 0);
                    linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    checkBoxCell.setText(name, null, true, false);
                    checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    checkBoxCell.setOnClickListener(v -> {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        int num = (Integer) cell.getTag();
                        clear[num] = !clear[num];
                        cell.setChecked(clear[num], true);
                    });
                }
                builder.setPositiveButton(getString("ClearButton", R.string.ClearButton), (dialogInterface, i) -> {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(getString("PrivacyPaymentsClearAlertTitle", R.string.PrivacyPaymentsClearAlertTitle));
                    builder1.setMessage(getString("PrivacyPaymentsClearAlert", R.string.PrivacyPaymentsClearAlert));
                    builder1.setPositiveButton(getString("ClearButton", R.string.ClearButton), (dialogInterface2, i2) -> {
                        TLRPC.TL_payments_clearSavedInfo req = new TLRPC.TL_payments_clearSavedInfo();
                        req.credentials = clear[1];
                        req.info = clear[0];
                        getUserConfig().tmpPassword = null;
                        getUserConfig().saveConfig(false);
                        getConnectionsManager().sendRequest(req, (response, error) -> {

                        });
                        String text;
                        if (clear[0] && clear[1]) {
                            text = getString("PrivacyPaymentsPaymentShippingCleared", R.string.PrivacyPaymentsPaymentShippingCleared);
                        } else if (clear[0]) {
                            text = getString("PrivacyPaymentsShippingInfoCleared", R.string.PrivacyPaymentsShippingInfoCleared);
                        } else if (clear[1]) {
                            text = getString("PrivacyPaymentsPaymentInfoCleared", R.string.PrivacyPaymentsPaymentInfoCleared);
                        } else {
                            return;
                        }
                        BulletinFactory.of(PrivacySettingsActivity.this).createSimpleBulletin(R.raw.chats_infotip, text).show();
                    });
                    builder1.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                    showDialog(builder1.create());
                    AlertDialog alertDialog = builder1.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                });
                builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);

                showDialog(builder.create());
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            } else if (position == passportRow) {
                presentFragment(new PassportActivity(PassportActivity.TYPE_PASSWORD, 0, "", "", null, null, null, null, null));
            } else if (position == botsBiometryRow) {
                presentFragment(new BotBiometrySettings());
            }
        });

        BotBiometry.getBots(getContext(), currentAccount, bots -> {
            biometryBots.clear();
            biometryBots.addAll(bots);
            updateRows(true);
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            TLRPC.GlobalPrivacySettings privacySettings = getContactsController().getGlobalPrivacySettings();
            if (privacySettings != null) {
                archiveChats = privacySettings.archive_and_mute_new_noncontact_peers;
                noncontactsValue = privacySettings.new_noncontact_peers_require_premium;
                feeValue = (privacySettings.flags & 32) != 0;
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            listAdapter.notifyItemChanged(blockedRow);
        } else if (id == NotificationCenter.didSetOrRemoveTwoStepPassword) {
            if (args.length > 0) {
                currentPassword = (TL_account.Password) args[0];
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(passwordRow);
                }
            } else {
                currentPassword = null;
                loadPasswordSettings();
                updateRows();
            }
        } if (id == NotificationCenter.didUpdateGlobalAutoDeleteTimer) {
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(autoDeleteMesages);
            }
        }
    }

    private void updateRows() {
        updateRows(true);
    }

    private void updateRows(boolean notify) {
        rowCount = 0;

        securitySectionRow = rowCount++;
        passwordRow = rowCount++;
        autoDeleteMesages = rowCount++;
        passcodeRow = rowCount++;
        if (currentPassword != null ? currentPassword.login_email_pattern != null : SharedConfig.hasEmailLogin) {
            emailLoginRow = rowCount++;
        } else {
            emailLoginRow = -1;
        }
        blockedRow = rowCount++;
        if (currentPassword != null) {
            boolean hasEmail = currentPassword.login_email_pattern != null;
            if (SharedConfig.hasEmailLogin != hasEmail) {
                SharedConfig.hasEmailLogin = hasEmail;
                SharedConfig.saveConfig();
            }
        }
        sessionsRow = rowCount++;
        sessionsDetailRow = rowCount++;

        privacySectionRow = rowCount++;
        phoneNumberRow = rowCount++;
        lastSeenRow = rowCount++;
        profilePhotoRow = rowCount++;
        forwardsRow = rowCount++;
        callsRow = rowCount++;
        groupsDetailRow = -1;
        if (!getMessagesController().premiumFeaturesBlocked() || getUserConfig().isPremium()) {
            voicesRow = rowCount++;
            noncontactsRow = rowCount++;
        } else {
            voicesRow = -1;
            noncontactsRow = -1;
        }
        birthdayRow = rowCount++;
        giftsRow = rowCount++;
        bioRow = rowCount++;
        groupsRow = rowCount++;
        privacyShadowRow = rowCount++;

        if (getMessagesController().autoarchiveAvailable || getUserConfig().isPremium()) {
            newChatsHeaderRow = rowCount++;
            newChatsRow = rowCount++;
            newChatsSectionRow = rowCount++;
        } else {
            newChatsHeaderRow = -1;
            newChatsRow = -1;
            newChatsSectionRow = -1;
        }
        advancedSectionRow = rowCount++;
        deleteAccountRow = rowCount++;
        deleteAccountDetailRow = rowCount++;
        botsSectionRow = rowCount++;
        if (getUserConfig().hasSecureData) {
            passportRow = rowCount++;
        } else {
            passportRow = -1;
        }
        paymentsClearRow = rowCount++;
        if (!biometryBots.isEmpty()) {
            botsBiometryRow = rowCount++;
        } else {
            botsBiometryRow = -1;
        }
        if (webSessionsActivityPreload != null && webSessionsActivityPreload.getSessionsCount() > 0) {
            webSessionsRow = rowCount++;
            botsDetailRow = rowCount++;
            botsAndWebsitesShadowRow = -1;
        } else {
            webSessionsRow = -1;
            botsDetailRow = -1;
            botsAndWebsitesShadowRow = rowCount++;
        }
        contactsSectionRow = rowCount++;
        contactsDeleteRow = rowCount++;
        contactsSyncRow = rowCount++;
        contactsSuggestRow = rowCount++;
        contactsDetailRow = rowCount++;
        secretSectionRow = rowCount++;
        secretMapRow = rowCount++;
        secretWebpageRow = rowCount++;
        secretDetailRow = rowCount++;
        if (listAdapter != null && notify) {
            listAdapter.notifyDataSetChanged();
        }
    }

    public PrivacySettingsActivity setCurrentPassword(TL_account.Password currentPassword) {
        this.currentPassword = currentPassword;
        if (currentPassword != null) {
            initPassword();
        }
        return this;
    }

    private void initPassword() {
        TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
        if (!getUserConfig().hasSecureData && currentPassword.has_secure_values) {
            getUserConfig().hasSecureData = true;
            getUserConfig().saveConfig(false);
            updateRows();
        } else {
            if (currentPassword != null) {
                int wasEmailRow = emailLoginRow;
                boolean appear = currentPassword.login_email_pattern != null && emailLoginRow == -1;
                boolean disappear = currentPassword.login_email_pattern == null && emailLoginRow != -1;

                if (appear || disappear) {
                    updateRows(false);

                    if (listAdapter != null) {
                        if (appear) {
                            listAdapter.notifyItemInserted(emailLoginRow);
                        } else {
                            listAdapter.notifyItemRemoved(wasEmailRow);
                        }
                    }
                }
            }

            if (listAdapter != null) {
                listAdapter.notifyItemChanged(passwordRow);
            }
        }
    }

    private void loadPasswordSettings() {
        TL_account.getPassword req = new TL_account.getPassword();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TL_account.Password password = (TL_account.Password) response;
                AndroidUtilities.runOnUIThread(() -> {
                    currentPassword = password;
                    initPassword();
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    public static String formatRulesString(AccountInstance accountInstance, int rulesType) {
        final ArrayList<TLRPC.PrivacyRule> privacyRules = accountInstance.getContactsController().getPrivacyRules(rulesType);
        final TLRPC.GlobalPrivacySettings global = accountInstance.getContactsController().getGlobalPrivacySettings();
        if (privacyRules == null || privacyRules.size() == 0) {
            if (rulesType == ContactsController.PRIVACY_RULES_TYPE_P2P) {
                return getString(R.string.P2PNobody);
            } else {
                return getString(R.string.LastSeenNobody);
            }
        }
        int type = -1;
        int plus = 0;
        int minus = 0;
        boolean premium = false;
        Boolean miniapps = null;
        for (int a = 0; a < privacyRules.size(); a++) {
            TLRPC.PrivacyRule rule = privacyRules.get(a);
            if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                TLRPC.TL_privacyValueAllowChatParticipants participants = (TLRPC.TL_privacyValueAllowChatParticipants) rule;
                for (int b = 0, N = participants.chats.size(); b < N; b++) {
                    TLRPC.Chat chat = accountInstance.getMessagesController().getChat(participants.chats.get(b));
                    if (chat == null) {
                        continue;
                    }
                    plus += chat.participants_count;
                }
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                TLRPC.TL_privacyValueDisallowChatParticipants participants = (TLRPC.TL_privacyValueDisallowChatParticipants) rule;
                for (int b = 0, N = participants.chats.size(); b < N; b++) {
                    TLRPC.Chat chat = accountInstance.getMessagesController().getChat(participants.chats.get(b));
                    if (chat == null) {
                        continue;
                    }
                    minus += chat.participants_count;
                }
            } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                TLRPC.TL_privacyValueAllowUsers privacyValueAllowUsers = (TLRPC.TL_privacyValueAllowUsers) rule;
                plus += privacyValueAllowUsers.users.size();
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                TLRPC.TL_privacyValueDisallowUsers privacyValueDisallowUsers = (TLRPC.TL_privacyValueDisallowUsers) rule;
                minus += privacyValueDisallowUsers.users.size();
            } else if (rule instanceof TLRPC.TL_privacyValueAllowPremium) {
                premium = true;
            } else if (rule instanceof TLRPC.TL_privacyValueAllowBots) {
                miniapps = true;
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowBots) {
                miniapps = false;
            } else if (type == -1) {
                if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    type = 0;
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                    type = 1;
                } else {
                    type = 2;
                }
            }
        }
        if (rulesType == ContactsController.PRIVACY_RULES_TYPE_GIFTS && global != null && global.disallowed_stargifts != null && (global.disallowed_stargifts.disallow_unique_stargifts && global.disallowed_stargifts.disallow_unlimited_stargifts && global.disallowed_stargifts.disallow_limited_stargifts && !global.disallowed_stargifts.disallow_premium_gifts)) {
            return getString(R.string.PrivacyValueGiftsOnlyPremium);
        } else if (rulesType == ContactsController.PRIVACY_RULES_TYPE_GIFTS && global != null && global.disallowed_stargifts != null && (global.disallowed_stargifts.disallow_unique_stargifts && global.disallowed_stargifts.disallow_unlimited_stargifts && global.disallowed_stargifts.disallow_limited_stargifts && global.disallowed_stargifts.disallow_premium_gifts)) {
            return getString(R.string.PrivacyValueGiftsNone);
        } else if (type == 0 || type == -1 && minus > 0) {
            if (rulesType == ContactsController.PRIVACY_RULES_TYPE_P2P) {
                if (minus == 0) {
                    return getString(R.string.P2PEverybody);
                } else {
                    return LocaleController.formatString(R.string.P2PEverybodyMinus, minus);
                }
            } else if (rulesType == ContactsController.PRIVACY_RULES_TYPE_GIFTS) {
                if (minus == 0) {
                    return getString(miniapps != null && !miniapps ? R.string.PrivacyValueEveryoneExceptBots : R.string.LastSeenEverybody);
                } else {
                    return LocaleController.formatString(miniapps != null && !miniapps ? R.string.PrivacyValueEveryoneExceptBotsMinus : R.string.LastSeenEverybodyMinus, minus);
                }
            } else {
                if (minus == 0) {
                    return getString(R.string.LastSeenEverybody);
                } else {
                    return LocaleController.formatString(R.string.LastSeenEverybodyMinus, minus);
                }
            }
        } else if (type == 2 || type == -1 && minus > 0 && plus > 0) {
            if (rulesType == PrivacyControlActivity.PRIVACY_RULES_TYPE_P2P) {
                if (plus == 0 && minus == 0) {
                    return getString("P2PContacts", R.string.P2PContacts);
                } else {
                    if (plus != 0 && minus != 0) {
                        return LocaleController.formatString(R.string.P2PContactsMinusPlus, minus, plus);
                    } else if (minus != 0) {
                        return LocaleController.formatString(R.string.P2PContactsMinus, minus);
                    } else {
                        return LocaleController.formatString(R.string.P2PContactsPlus, plus);
                    }
                }
            } else {
                if (plus == 0 && minus == 0) {
                    if (premium) {
                        return getString(R.string.LastSeenContactsPremium);
                    } else if (miniapps != null && miniapps) {
                        return LocaleController.getString(R.string.PrivacyContactsAndBotUsers);
                    }
                    return getString(R.string.LastSeenContacts);
                } else {
                    if (plus != 0 && minus != 0) {
                        return LocaleController.formatString(miniapps != null && miniapps ? R.string.PrivacyContactsAndBotUsersMinusPlus : premium ? R.string.LastSeenContactsPremiumMinusPlus : R.string.LastSeenContactsMinusPlus, minus, plus);
                    } else if (minus != 0) {
                        return LocaleController.formatString(miniapps != null && miniapps ? R.string.PrivacyContactsAndBotUsersMinus :premium ? R.string.LastSeenContactsPremiumMinus : R.string.LastSeenContactsMinus, minus);
                    } else {
                        return LocaleController.formatString(miniapps != null && miniapps ? R.string.PrivacyContactsAndBotUsersPlus :premium ? R.string.LastSeenContactsPremiumPlus : R.string.LastSeenContactsPlus, plus);
                    }
                }
            }
        } else if (type == 1 || plus > 0) {
            if (rulesType == PrivacyControlActivity.PRIVACY_RULES_TYPE_P2P) {
                if (plus == 0) {
                    return getString(R.string.P2PNobody);
                } else {
                    return LocaleController.formatString(R.string.P2PNobodyPlus, plus);
                }
            } else {
                if (plus == 0) {
                    if (premium) {
                        return getString(R.string.LastSeenNobodyPremium);
                    }
                    if (miniapps != null && miniapps) {
                        return LocaleController.getString(R.string.PrivacyValueOnlyBots);
                    }
                    return getString(R.string.LastSeenNobody);
                } else {
                    return LocaleController.formatString(premium ? R.string.LastSeenNobodyPremiumPlus : R.string.LastSeenNobodyPlus, plus);
                }
            }
        } else if (miniapps != null) {
            if (miniapps) {
                return LocaleController.getString(R.string.PrivacyValueOnlyBots);
            }
        }
        return "unknown";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == passcodeRow || position == passwordRow || position == blockedRow || position == sessionsRow || position == secretWebpageRow || position == webSessionsRow ||
                    position == groupsRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_INVITE) ||
                    position == lastSeenRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN) ||
                    position == callsRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS) ||
                    position == profilePhotoRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHOTO) ||
                    position == bioRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_BIO) ||
                    position == birthdayRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY) ||
                    position == giftsRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_GIFTS) ||
                    position == forwardsRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_FORWARDS) ||
                    position == phoneNumberRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHONE) ||
                    position == voicesRow && !getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES) ||
                    position == noncontactsRow ||
                    position == deleteAccountRow && !getContactsController().getLoadingDeleteInfo() ||
                    position == newChatsRow && !getContactsController().getLoadingGlobalSettings() ||
                    position == emailLoginRow || position == paymentsClearRow || position == secretMapRow ||
                    position == contactsSyncRow || position == passportRow || position == contactsDeleteRow ||
                    position == contactsSuggestRow || position == autoDeleteMesages || position == botsBiometryRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    boolean showLoading = false;
                    String value = null;
                    int loadingLen = 16;
                    boolean animated = holder.itemView.getTag() != null && ((Integer) holder.itemView.getTag()) == position;
                    holder.itemView.setTag(position);
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setBetterLayout(true);
                    if (position == webSessionsRow) {
                        textCell.setText(getString("WebSessionsTitle", R.string.WebSessionsTitle), false);
                    } else if (position == phoneNumberRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHONE)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_PHONE);
                        }
                        textCell.setTextAndValue(getString("PrivacyPhone", R.string.PrivacyPhone), value, true);
                    } else if (position == lastSeenRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                        }
                        textCell.setTextAndValue(getString("PrivacyLastSeen", R.string.PrivacyLastSeen), value, true);
                    } else if (position == groupsRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_INVITE)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_INVITE);
                        }
                        textCell.setTextAndValue(getString(R.string.PrivacyInvites), value, false);
                    } else if (position == callsRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_CALLS);
                        }
                        textCell.setTextAndValue(getString("Calls", R.string.Calls), value, true);
                    } else if (position == profilePhotoRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHOTO)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_PHOTO);
                        }
                        textCell.setTextAndValue(getString("PrivacyProfilePhoto", R.string.PrivacyProfilePhoto), value, true);
                    } else if (position == bioRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_BIO)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_BIO);
                        }
                        textCell.setTextAndValue(getString("PrivacyBio", R.string.PrivacyBio), value, true);
                    } else if (position == birthdayRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_BIRTHDAY);
                        }
                        textCell.setTextAndValue(getString(R.string.PrivacyBirthday), value, true);
                    } else if (position == giftsRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_GIFTS)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_GIFTS);
                        }
                        textCell.setTextAndValue(getString(R.string.PrivacyGifts), value, true);
                    } else if (position == forwardsRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_FORWARDS)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_FORWARDS);
                        }
                        textCell.setTextAndValue(getString("PrivacyForwards", R.string.PrivacyForwards), value, true);
                    } else if (position == voicesRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES)) {
                            showLoading = true;
                            loadingLen = 30;
                        } else if (!getUserConfig().isPremium()) {
                            value = getString(R.string.P2PEverybody);
                        } else {
                            value = formatRulesString(getAccountInstance(), ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES);
                        }
                        textCell.setTextAndValue(addPremiumStar(getString(R.string.PrivacyVoiceMessages)), value, noncontactsRow != -1);
                        ImageView imageView = textCell.getValueImageView();
                        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
                    } else if (position == noncontactsRow) {
                        value = getString(feeValue ? R.string.ContactsAndFee : noncontactsValue ? R.string.ContactsAndPremium : R.string.P2PEverybody);
                        textCell.setTextAndValue(getMessagesController().newNoncontactPeersRequirePremiumWithoutOwnpremium && !getMessagesController().starsPaidMessagesAvailable ? getString(R.string.PrivacyMessages) : addPremiumStar(getString(R.string.PrivacyMessages)), value, bioRow != -1);
                    } else if (position == passportRow) {
                        textCell.setText(getString("TelegramPassport", R.string.TelegramPassport), true);
                    } else if (position == deleteAccountRow) {
                        if (getContactsController().getLoadingDeleteInfo()) {
                            showLoading = true;
                        } else {
                            int ttl = getContactsController().getDeleteAccountTTL();
                            if (ttl <= 182) {
                                value = formatPluralString("Months", ttl / 30);
                            } else if (ttl == 365) {
                                value = formatPluralString("Months", 12);
                            } else if (ttl == 548) {
                                value = formatPluralString("Months", 18);
                            } else if (ttl == 730) {
                                value = formatPluralString("Months", 24);
                            } else if (ttl > 30) {
                                value = formatPluralString("Months", (int) Math.round(ttl / 30.0));
                            } else {
                                value = formatPluralString("Days", ttl);
                            }
                        }
                        textCell.setTextAndValue(getString("DeleteAccountIfAwayFor3", R.string.DeleteAccountIfAwayFor3), value, deleteAccountUpdate, false);
                        deleteAccountUpdate = false;
                    } else if (position == paymentsClearRow) {
                        textCell.setText(getString("PrivacyPaymentsClear", R.string.PrivacyPaymentsClear), true);
                    } else if (position == botsBiometryRow) {
                        textCell.setText(getString(R.string.PrivacyBiometryBotsButton), true);
                    } else if (position == secretMapRow) {
                        switch (SharedConfig.mapPreviewType) {
                            case 0:
                                value = getString("MapPreviewProviderTelegram", R.string.MapPreviewProviderTelegram);
                                break;
                            case 1:
                                value = getString("MapPreviewProviderGoogle", R.string.MapPreviewProviderGoogle);
                                break;
                            case 2:
                                value = getString("MapPreviewProviderNobody", R.string.MapPreviewProviderNobody);
                                break;
                            case 3:
                            default:
                                value = getString("MapPreviewProviderYandex", R.string.MapPreviewProviderYandex);
                                break;
                        }
                        textCell.setTextAndValue(getString("MapPreviewProvider", R.string.MapPreviewProvider), value, secretMapUpdate, true);
                        secretMapUpdate = false;
                    } else if (position == contactsDeleteRow) {
                        textCell.setText(getString("SyncContactsDelete", R.string.SyncContactsDelete), true);
                    }
                    textCell.setDrawLoading(showLoading, loadingLen, animated);
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    boolean last = position == getItemCount() - 1;
                    privacyCell.setBackground(Theme.getThemedDrawableByKey(mContext, last ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == deleteAccountDetailRow) {
                        privacyCell.setText(getString("DeleteAccountHelp", R.string.DeleteAccountHelp));
                    } else if (position == groupsDetailRow) {
                        privacyCell.setText(getString("GroupsAndChannelsHelp", R.string.GroupsAndChannelsHelp));
                    } else if (position == sessionsDetailRow) {
                        privacyCell.setText(getString("SessionsSettingsInfo", R.string.SessionsSettingsInfo));
                    } else if (position == secretDetailRow) {
                        privacyCell.setText(getString("SecretWebPageInfo", R.string.SecretWebPageInfo));
                    } else if (position == botsDetailRow) {
                        privacyCell.setText(getString("PrivacyBotsInfo", R.string.PrivacyBotsInfo));
                    } else if (position == privacyShadowRow) {
                        privacyCell.setText(getString(R.string.PrivacyInvitesInfo));
                    } else if (position == contactsDetailRow) {
                        /*if (newSync) {
                            privacyCell.setText(LocaleController.getString(R.string.SyncContactsInfoOn));
                        } else {
                            privacyCell.setText(LocaleController.getString(R.string.SyncContactsInfoOff));
                        }*/
                        privacyCell.setText(getString("SuggestContactsInfo", R.string.SuggestContactsInfo));
                    } else if (position == newChatsSectionRow) {
                        privacyCell.setText(getString("ArchiveAndMuteInfo", R.string.ArchiveAndMuteInfo));
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == privacySectionRow) {
                        headerCell.setText(getString("PrivacyTitle", R.string.PrivacyTitle));
                    } else if (position == securitySectionRow) {
                        headerCell.setText(getString("SecurityTitle", R.string.SecurityTitle));
                    } else if (position == advancedSectionRow) {
                        headerCell.setText(getString("DeleteMyAccount", R.string.DeleteMyAccount));
                    } else if (position == secretSectionRow) {
                        headerCell.setText(getString("SecretChat", R.string.SecretChat));
                    } else if (position == botsSectionRow) {
                        headerCell.setText(getString("PrivacyBots", R.string.PrivacyBots));
                    } else if (position == contactsSectionRow) {
                        headerCell.setText(getString("Contacts", R.string.Contacts));
                    } else if (position == newChatsHeaderRow) {
                        headerCell.setText(getString("NewChatsFromNonContacts", R.string.NewChatsFromNonContacts));
                    }
                    break;
                case 3:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == secretWebpageRow) {
                        textCheckCell.setTextAndCheck(getString("SecretWebPage", R.string.SecretWebPage), getMessagesController().secretWebpagePreview == 1, false);
                    } else if (position == contactsSyncRow) {
                        textCheckCell.setTextAndCheck(getString("SyncContacts", R.string.SyncContacts), newSync, true);
                    } else if (position == contactsSuggestRow) {
                        textCheckCell.setTextAndCheck(getString("SuggestContacts", R.string.SuggestContacts), newSuggest, false);
                    } else if (position == newChatsRow) {
                        textCheckCell.setTextAndCheck(getString("ArchiveAndMute", R.string.ArchiveAndMute), archiveChats, false);
                    }
                    break;
                case 5:
                    TextCell textCell2 = (TextCell) holder.itemView;
                    animated = holder.itemView.getTag() != null && ((Integer) holder.itemView.getTag()) == position;
                    holder.itemView.setTag(position);
                    showLoading = false;
                    loadingLen = 16;
                    value = null;
                    textCell2.setPrioritizeTitleOverValue(false);
                    if (position == autoDeleteMesages) {
                        int ttl = getUserConfig().getGlobalTTl();
                        if (ttl == -1) {
                            showLoading = true;
                        } else if (ttl > 0) {
                            value = LocaleController.formatTTLString(ttl * 60);
                        } else {
                            value = getString("PasswordOff", R.string.PasswordOff);
                        }
                        textCell2.setTextAndValueAndIcon(getString("AutoDeleteMessages", R.string.AutoDeleteMessages), value, true, R.drawable.msg2_autodelete, true);
                    } else if (position == sessionsRow) {
                        String count = "";
                        if (devicesActivityPreload.getSessionsCount() == 0) {
                            if (getMessagesController().lastKnownSessionsCount == 0) {
                                showLoading = true;
                            } else {
                                count = String.format(LocaleController.getInstance().getCurrentLocale(), "%d", getMessagesController().lastKnownSessionsCount);
                            }
                        } else {
                            count = String.format(LocaleController.getInstance().getCurrentLocale(), "%d", devicesActivityPreload.getSessionsCount());
                        }
                        getMessagesController().lastKnownSessionsCount = devicesActivityPreload.getSessionsCount();
                        textCell2.setTextAndValueAndIcon(getString("SessionsTitle", R.string.SessionsTitle), count, true, R.drawable.msg2_devices, false);
                    } else if (position == emailLoginRow) {
                        CharSequence val = "";
                        if (currentPassword == null) {
                            showLoading = true;
                        } else {
                            SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(currentPassword.login_email_pattern);
                            int startIndex = currentPassword.login_email_pattern.indexOf('*');
                            int endIndex = currentPassword.login_email_pattern.lastIndexOf('*');
                            if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                                run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                                run.start = startIndex;
                                run.end = endIndex + 1;
                                spannable.setSpan(new TextStyleSpan(run), startIndex, endIndex + 1, 0);
                            }
                            val = spannable;
                        }
                        textCell2.setPrioritizeTitleOverValue(true);
                        textCell2.setTextAndSpoilersValueAndIcon(getString(R.string.EmailLogin), val, R.drawable.msg2_email, true);
                    } else if (position == passwordRow) {
                        value = "";
                        if (currentPassword == null) {
                            showLoading = true;
                        } else if (currentPassword.has_password) {
                            value = getString("PasswordOn", R.string.PasswordOn);
                        } else {
                            value = getString("PasswordOff", R.string.PasswordOff);
                        }
                        textCell2.setTextAndValueAndIcon(getString("TwoStepVerification", R.string.TwoStepVerification), value, true, R.drawable.msg2_permissions, true);
                    } else if (position == passcodeRow) {
                        int icon;
                        if (SharedConfig.passcodeHash.length() != 0) {
                            value = getString("PasswordOn", R.string.PasswordOn);
                            icon = R.drawable.msg2_secret;
                        } else {
                            value = getString("PasswordOff", R.string.PasswordOff);
                            icon = R.drawable.msg2_secret;
                        }
                        textCell2.setTextAndValueAndIcon(getString("Passcode", R.string.Passcode), value, true, icon, true);
                    } else if (position == blockedRow) {
                        int totalCount = getMessagesController().totalBlockedCount;
                        if (totalCount == 0) {
                            value = getString("BlockedEmpty", R.string.BlockedEmpty);
                        } else if (totalCount > 0) {
                            value = String.format(LocaleController.getInstance().getCurrentLocale(), "%d", totalCount);
                        } else {
                            showLoading = true;
                            value = "";
                        }
                        textCell2.setTextAndValueAndIcon(getString("BlockedUsers", R.string.BlockedUsers), value, true, R.drawable.msg2_block2, true);
                    }
                    textCell2.setDrawLoading(showLoading, loadingLen, animated);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == passportRow || position == lastSeenRow || position == phoneNumberRow ||
                    position == deleteAccountRow || position == webSessionsRow || position == groupsRow || position == paymentsClearRow ||
                    position == secretMapRow || position == contactsDeleteRow || position == botsBiometryRow) {
                return 0;
            } else if (position == privacyShadowRow || position == deleteAccountDetailRow || position == groupsDetailRow || position == sessionsDetailRow || position == secretDetailRow || position == botsDetailRow || position == contactsDetailRow || position == newChatsSectionRow) {
                return 1;
            } else if (position == securitySectionRow || position == advancedSectionRow || position == privacySectionRow || position == secretSectionRow || position == botsSectionRow || position == contactsSectionRow || position == newChatsHeaderRow) {
                return 2;
            } else if (position == secretWebpageRow || position == contactsSyncRow || position == contactsSuggestRow || position == newChatsRow) {
                return 3;
            } else if (position == botsAndWebsitesShadowRow) {
                return 4;
            } else if (position == autoDeleteMesages || position == sessionsRow || position == emailLoginRow || position == passwordRow || position == passcodeRow || position == blockedRow) {
                return 5;
            }
            return 0;
        }
    }

    private SpannableString premiumStar;
    private CharSequence addPremiumStar(String text) {
//        if (getUserConfig().isPremium()) {
//            return text;
//        }
        if (premiumStar == null) {
            premiumStar = new SpannableString("★");
            Drawable drawable = new AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, dp(18), dp(18));
            drawable.setBounds(0, 0, dp(18), dp(18));
            premiumStar.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER), 0, premiumStar.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return new SpannableStringBuilder(text).append("  ").append(premiumStar);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        return themeDescriptions;
    }
}

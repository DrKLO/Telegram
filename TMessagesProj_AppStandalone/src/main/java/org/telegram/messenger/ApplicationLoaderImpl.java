package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.isInAirplaneMode;
import static org.telegram.ui.PremiumPreviewFragment.applyNewSpan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.view.ViewGroup;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.web.BuildConfig;
import org.telegram.messenger.web.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TL_smsjobs;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.Components.UpdateButton;
import org.telegram.ui.Components.UpdateLayout;
import org.telegram.ui.IUpdateButton;
import org.telegram.ui.IUpdateLayout;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.SMSStatsActivity;
import org.telegram.ui.SMSSubscribeSheet;

import java.io.File;
import java.util.ArrayList;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    protected boolean isStandalone() {
        return true;
    }

    @Override
    protected void startAppCenterInternal(Activity context) {

    }

    @Override
    protected void checkForUpdatesInternal() {

    }

    protected void appCenterLogInternal(Throwable e) {

    }

    protected void logDualCameraInternal(boolean success, boolean vendor) {

    }

    @Override
    public boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }

    @Override
    public boolean openApkInstall(Activity activity, TLRPC.Document document) {
        boolean exists = false;
        try {
            String fileName = FileLoader.getAttachFileName(document);
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
            if (exists = f.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (Build.VERSION.SDK_INT >= 24) {
                    intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", f), "application/vnd.android.package-archive");
                } else {
                    intent.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
                }
                try {
                    activity.startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return exists;
    }

    @Override
    public boolean showUpdateAppPopup(Context context, TLRPC.TL_help_appUpdate update, int account) {
        try {
            (new UpdateAppAlertDialog(context, update, account)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenu, ViewGroup sideMenuContainer) {
        return new UpdateLayout(activity, sideMenu, sideMenuContainer);
    }

    @Override
    public IUpdateButton takeUpdateButton(Context context) {
        return new UpdateButton(context);
    }

    @Override
    public TLRPC.Update parseTLUpdate(int constructor) {
        if (constructor == TL_smsjobs.TL_updateSmsJob.constructor) {
            return new TL_smsjobs.TL_updateSmsJob();
        }
        return super.parseTLUpdate(constructor);
    }

    @Override
    public void processUpdate(int currentAccount, TLRPC.Update update) {
        if (update instanceof TL_smsjobs.TL_updateSmsJob) {
            SMSJobController.getInstance(currentAccount).processJobUpdate(((TL_smsjobs.TL_updateSmsJob) update).job_id);
        }
    }

    @Override
    public boolean extendDrawer(ArrayList<DrawerLayoutAdapter.Item> items) {
        if (SMSJobController.getInstance(UserConfig.selectedAccount).isAvailable()) {
            CharSequence text = LocaleController.getString(R.string.SmsJobsMenu);
            if (MessagesController.getGlobalMainSettings().getBoolean("newppsms", true)) {
                text = applyNewSpan(text.toString());
            }
            DrawerLayoutAdapter.Item item = new DrawerLayoutAdapter.Item(93, text, R.drawable.left_sms).onClick(v -> {
                MessagesController.getGlobalMainSettings().edit().putBoolean("newppsms", false).apply();
                SMSJobController controller = (SMSJobController) SMSJobController.getInstance(UserConfig.selectedAccount);
                final int state = controller.currentState;
                if (state == SMSJobController.STATE_NONE) {
                    SMSSubscribeSheet.show(LaunchActivity.instance, SMSJobController.getInstance(UserConfig.selectedAccount).isEligible, null, null);
                    return;
                } else if (state == SMSJobController.STATE_NO_SIM) {
                    controller.checkSelectedSIMCard();
                    if (controller.getSelectedSIM() == null) {
                        new AlertDialog.Builder(LaunchActivity.instance)
                                .setTitle(LocaleController.getString(R.string.SmsNoSimTitle))
                                .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.SmsNoSimMessage)))
                                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                                .show();
                        return;
                    }
                } else if (state == SMSJobController.STATE_ASKING_PERMISSION) {
                    SMSSubscribeSheet.requestSMSPermissions(LaunchActivity.instance, () -> {
                        controller.checkSelectedSIMCard();
                        if (controller.getSelectedSIM() == null) {
                            controller.setState(SMSJobController.STATE_NO_SIM);
                            new AlertDialog.Builder(LaunchActivity.instance)
                                    .setTitle(LocaleController.getString(R.string.SmsNoSimTitle))
                                    .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.SmsNoSimMessage)))
                                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                                    .show();
                            return;
                        }
                        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(new TL_smsjobs.TL_smsjobs_join(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            if (err != null) {
                                BulletinFactory.showError(err);
                            } else if (res instanceof TLRPC.TL_boolFalse) {
                                BulletinFactory.global().createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                            } else {
                                controller.setState(SMSJobController.STATE_JOINED);
                                controller.loadStatus(true);
                                SMSSubscribeSheet.showSubscribed(LaunchActivity.instance, null);
                                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                                if (lastFragment != null) {
                                    lastFragment.presentFragment(new SMSStatsActivity());
                                }
                            }
                        }));
                    }, false);
                    return;
                }
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment != null) {
                    lastFragment.presentFragment(new SMSStatsActivity());
                }
            });
            if (isInAirplaneMode(LaunchActivity.instance) || SMSJobController.getInstance(UserConfig.selectedAccount).hasError()) {
                item.withError();
            }
            items.add(item);
        }
        return true;
    }

    @Override
    public boolean checkRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (SMSSubscribeSheet.checkSMSPermissions(requestCode, permissions, grantResults)) {
            return true;
        }
        return super.checkRequestPermissionResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onSuggestionFill(String suggestion, CharSequence[] output, boolean[] closeable) {
        if (suggestion == null && SMSJobController.getInstance(UserConfig.selectedAccount).hasError()) {
            output[0] = new SpannableStringBuilder().append(SMSStatsActivity.error(17)).append("  ").append(LocaleController.getString(R.string.SmsJobsErrorHintTitle));
            output[1] = LocaleController.getString(R.string.SmsJobsErrorHintMessage);
            closeable[0] = false;
            return true;
        }
        if ("PREMIUM_SMSJOBS".equals(suggestion) && SMSJobController.getInstance(UserConfig.selectedAccount).currentState != SMSJobController.STATE_JOINED) {
            output[0] = LocaleController.getString(R.string.SmsJobsPremiumHintTitle);
            output[1] = LocaleController.getString(R.string.SmsJobsPremiumHintMessage);
            closeable[0] = true;
            return true;
        }
        return super.onSuggestionFill(suggestion, output, closeable);
    }

    @Override
    public boolean onSuggestionClick(String suggestion) {
        if (suggestion == null) {
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null) {
                SMSJobController.getInstance(UserConfig.selectedAccount).seenError();
                SMSStatsActivity fragment = new SMSStatsActivity();
                lastFragment.presentFragment(fragment);
                AndroidUtilities.runOnUIThread(() -> {
                    fragment.showDialog(new SMSStatsActivity.SMSHistorySheet(fragment));
                }, 800);
            }
            return true;
        } else if ("PREMIUM_SMSJOBS".equals(suggestion)) {
            SMSJobController controller = SMSJobController.getInstance(UserConfig.selectedAccount);
            if (controller.isEligible != null) {
                SMSSubscribeSheet.show(LaunchActivity.instance, controller.isEligible, null, null);
            } else {
                controller.checkIsEligible(true, isEligible -> {
                    if (isEligible == null) {
                        MessagesController.getInstance(UserConfig.selectedAccount).removeSuggestion(0, "PREMIUM_SMSJOBS");
                        return;
                    }
                    SMSSubscribeSheet.show(LaunchActivity.instance, isEligible, null, null);
                });
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean consumePush(int account, JSONObject json) {
        try {
            if (json != null && "SMSJOB".equals(json.getString("loc_key"))) {
                JSONObject custom = json.getJSONObject("custom");
                String job_id = custom.getString("job_id");
                SMSJobController.getInstance(UserConfig.selectedAccount).processJobUpdate(job_id);
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    @Override
    public boolean onPause() {
        super.onPause();
        return SMSJobsNotification.check();
    }

    @Override
    public void onResume() {
        super.onResume();
        SMSJobsNotification.check();
    }

    @Override
    public BaseFragment openSettings(int n) {
        if (n == 13) {
            if (SMSJobController.getInstance(UserConfig.selectedAccount).getState() == SMSJobController.STATE_JOINED) {
                return new SMSStatsActivity();
            }
        }
        return null;
    }
}

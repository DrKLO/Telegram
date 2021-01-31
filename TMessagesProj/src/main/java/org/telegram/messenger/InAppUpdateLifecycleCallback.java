package org.telegram.messenger;

import android.app.Activity;
import android.app.Application;
import android.content.IntentSender;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.telegram.ui.Components.AlertsCreator;

public class InAppUpdateLifecycleCallback implements Application.ActivityLifecycleCallbacks {

    private static final int UPDATE_REQUEST_CODE = 6708;

    private final AppUpdateManager appUpdateManager;
    private final InstallStateUpdatedListener installStateUpdatedListener = state -> {
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            showDownloadedDialog();
        }
    };

    private Activity currentActivity;

    public InAppUpdateLifecycleCallback(Application application) {
        appUpdateManager = AppUpdateManagerFactory.create(application);
        checkForUpdates();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        currentActivity = activity;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        unsubscribe();
    }

    private void checkForUpdates() {
        appUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(result -> {
                    if (result.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        if (result.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                            startUpdateFlow(result);
                        }
                    } else if (result.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        if (result.installStatus() == InstallStatus.DOWNLOADED) {
                            showDownloadedDialog();
                        } else {
                            appUpdateManager.registerListener(installStateUpdatedListener);
                        }
                    }
                });
    }

    private void showDownloadedDialog() {
        AlertsCreator.showRestartAppForUpdateAlert(currentActivity, "An update has just been downloaded", this::completeUpdate);
    }

    private void startUpdateFlow(AppUpdateInfo updateInfo) {
        appUpdateManager.registerListener(installStateUpdatedListener);
        try {
            appUpdateManager.startUpdateFlowForResult(
                    updateInfo,
                    AppUpdateType.FLEXIBLE,
                    currentActivity,
                    UPDATE_REQUEST_CODE
            );
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
        }
    }

    private void unsubscribe() {
        appUpdateManager.unregisterListener(installStateUpdatedListener);
    }

    private void completeUpdate() {
        unsubscribe();
        appUpdateManager.completeUpdate();
    }
}


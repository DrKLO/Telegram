package org.telegram.ui.Stars;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

/** Native admin controls backed by the proposed private-channel policy API. */
public final class StarsChannelPrivacyActivity extends StarsPrivacySettingsActivity {
    private final long dialogId;
    private AnimatorSet controlsAnimation;

    public StarsChannelPrivacyActivity(int account, long dialogId) {
        currentAccount = account;
        this.dialogId = dialogId;
    }

    @Override public View createView(Context context) {
        createSettingsView(context, R.string.StarsPrivacyReactionsHeader, true);
        addHeader(R.string.StarsPrivacyChannelHeader);
        TextCheckCell privacy = addToggle("stars_channel_private", R.string.StarsChannelPrivate,
            StarsPrivacyMockApi.isChannelPrivate(currentAccount, dialogId));
        addInfo(LocaleController.getString(R.string.StarsChannelPrivateInfo));

        TextCheckCell percentile = addToggle("stars_channel_percentile", R.string.StarsChannelPercentile,
            StarsPrivacyMockApi.showsPercentile(currentAccount, dialogId));
        percentile.setEnabled(StarsPrivacyMockApi.isChannelPrivate(currentAccount, dialogId), null);
        addInfo(LocaleController.getString(R.string.StarsChannelPercentileInfo) + "\n\n"
            + LocaleController.getString(R.string.StarsChannelMockInfo));
        privacy.setOnClickListener(v -> {
            boolean enabled = !StarsPrivacyMockApi.isChannelPrivate(currentAccount, dialogId);
            StarsPrivacyMockApi.setChannelPrivate(currentAccount, dialogId, enabled);
            privacy.setChecked(enabled);
            if (controlsAnimation != null) controlsAnimation.cancel();
            ArrayList<Animator> animators = new ArrayList<>();
            percentile.setEnabled(enabled, animators);
            controlsAnimation = new AnimatorSet();
            controlsAnimation.playTogether(animators);
            controlsAnimation.setDuration(200);
            controlsAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            controlsAnimation.start();
            showFeedback(R.raw.passcode_lock_close,
                enabled ? R.string.StarsPrivacyEnabledNotice : R.string.StarsPrivacyDisabledNotice);
            percentile.setChecked(StarsPrivacyMockApi.showsPercentile(currentAccount, dialogId));
        });
        percentile.setOnClickListener(v -> {
            // Also guard accessibility/programmatic clicks while the parent option is off.
            if (!StarsPrivacyMockApi.isChannelPrivate(currentAccount, dialogId)) return;
            boolean enabled = !StarsPrivacyMockApi.showsPercentile(currentAccount, dialogId);
            StarsPrivacyMockApi.setShowsPercentile(currentAccount, dialogId, enabled);
            percentile.setChecked(enabled);
        });
        updateColors();
        return fragmentView;
    }

    @Override public void onFragmentDestroy() {
        if (controlsAnimation != null) controlsAnimation.cancel();
        super.onFragmentDestroy();
    }
}

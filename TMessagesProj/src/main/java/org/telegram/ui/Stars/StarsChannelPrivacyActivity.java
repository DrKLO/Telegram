package org.telegram.ui.Stars;

import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;

/** Native admin controls backed by the proposed private-channel policy API. */
public final class StarsChannelPrivacyActivity extends BaseFragment {
    private final long dialogId;
    private AnimatorSet controlsAnimation;

    public StarsChannelPrivacyActivity(int account, long dialogId) {
        currentAccount = account;
        this.dialogId = dialogId;
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.StarsChannelPrivacy));
        actionBar.setSubtitle(LocaleController.getString(R.string.StarsPrivacyPreviewLabel));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout root = new FrameLayout(context);
        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, LayoutHelper.createFrame(-1, -1));
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        HeaderCell header = new HeaderCell(context);
        header.setText(LocaleController.getString(R.string.StarsChannelPrivacy));
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(header, LayoutHelper.createLinear(-1, -2));
        TextCheckCell privacy = new TextCheckCell(context);
        privacy.setTag("stars_channel_private");
        privacy.setBackground(Theme.getSelectorDrawable(true));
        privacy.setTextAndCheck(LocaleController.getString(R.string.StarsChannelPrivate), StarsPrivacyMockApi.isChannelPrivate(currentAccount, dialogId), false);
        content.addView(privacy, LayoutHelper.createLinear(-1, -2));
        info(context, content, R.string.StarsChannelPrivateInfo);
        TextCheckCell percentile = new TextCheckCell(context);
        percentile.setTag("stars_channel_percentile");
        percentile.setBackground(Theme.getSelectorDrawable(true));
        percentile.setTextAndCheck(LocaleController.getString(R.string.StarsChannelPercentile), StarsPrivacyMockApi.showsPercentile(currentAccount, dialogId), false);
        percentile.setEnabled(StarsPrivacyMockApi.isChannelPrivate(currentAccount, dialogId), null);
        content.addView(percentile, LayoutHelper.createLinear(-1, -2));
        info(context, content, R.string.StarsChannelPercentileInfo);
        info(context, content, R.string.StarsChannelMockInfo);
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
            BulletinFactory.of(root, getResourceProvider()).createSimpleBulletin(R.raw.passcode_lock_close,
                LocaleController.getString(enabled ? R.string.StarsPrivacyEnabledNotice : R.string.StarsPrivacyDisabledNotice), 3, 5000).show();
            percentile.setChecked(StarsPrivacyMockApi.showsPercentile(currentAccount, dialogId));
        });
        percentile.setOnClickListener(v -> {
            boolean enabled = !StarsPrivacyMockApi.showsPercentile(currentAccount, dialogId);
            StarsPrivacyMockApi.setShowsPercentile(currentAccount, dialogId, enabled);
            percentile.setChecked(enabled);
        });
        fragmentView = root;
        return root;
    }

    @Override public void onFragmentDestroy() {
        if (controlsAnimation != null) controlsAnimation.cancel();
        super.onFragmentDestroy();
    }

    private void info(Context context, LinearLayout content, int text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
        cell.setText(LocaleController.getString(text));
        content.addView(cell, LayoutHelper.createLinear(-1, -2));
    }
}

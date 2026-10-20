package org.telegram.ui.Wallet;

import org.telegram.ui.Wallet.GramWallet.SecretPhrase;
import org.telegram.messenger.LocaleController;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getAccountInProduction;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;

public class WalletSecretPhraseFragment extends BaseFragment {

    private final ArrayList<String> words = new ArrayList<>();
    private ScrollView scrollView;
    private ImageView closeButton;
    private LinearLayout columns;
    private ButtonWithCounterView continueButton;
    private Runnable onTested;
    private boolean current;

    public WalletSecretPhraseFragment onTested(Runnable onTested) {
        this.onTested = onTested;
        return this;
    }

    public WalletSecretPhraseFragment setPhrase(SecretPhrase secretPhrase) {
        words.clear();
        if (secretPhrase != null && !secretPhrase.isEmpty()) {
            words.addAll(secretPhrase.toWordsForDisplay());
        }
        updateWords();
        return this;
    }

    public WalletSecretPhraseFragment setCurrent() {
        this.current = true;
        return this;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(false);

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        fragmentView = container;

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, dp(96), 0, 0);
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), 0, dp(36), dp(24));
        root.setClipToPadding(false);
        scrollView.addView(root, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), PorterDuff.Mode.SRC_IN));
        closeButton.setImageResource(R.drawable.ic_ab_close);
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 3));
        closeButton.setOnClickListener(v -> finishFragment());
        closeButton.setContentDescription(LocaleController.getString(R.string.Close));
        container.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 4, 12, 0, 0));

        BackupImageView stickerView = new BackupImageView(context);
        stickerView.setAspectFit(true);
        stickerView.getImageReceiver().setCurrentAccount(getAccountInProduction());
        MediaDataController.getInstance(getAccountInProduction()).setPlaceholderImage(stickerView, "RestrictedEmoji", "\uD83D\uDCDD", "100_100");
        stickerView.getImageReceiver().setAutoRepeatCount(1);
        root.addView(stickerView, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        titleView.setText(current ? LocaleController.getString(R.string.WalletCurrentSecretPhrase) : LocaleController.getString(R.string.WalletNewSecretPhrase));
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 10));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        subtitleView.setText(current ? LocaleController.getString(R.string.WalletCurrentSecretPhraseInfo) : LocaleController.getString(R.string.WalletNewSecretPhraseInfo));
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 8));

        columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(columns, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 24, 4, 0));

        root.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        continueButton = new ButtonWithCounterView(context, resourceProvider);
        continueButton.setRound();
        continueButton.setText(LocaleController.getString(R.string.WalletContinue));
        continueButton.setOnClickListener(v -> onContinuePressed());
        root.addView(continueButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        updateWords();
        return fragmentView;
    }

    private void updateWords() {
        if (columns == null) {
            return;
        }
        columns.removeAllViews();
        Context context = columns.getContext();
        int leftCount = (words.size() + 1) / 2;
        columns.addView(WalletKeysBackupFragment.makeWordColumn(context, resourceProvider, words, 0, leftCount), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.TOP));
        columns.addView(new View(context), LayoutHelper.createLinear(10, 0));
        columns.addView(WalletKeysBackupFragment.makeWordColumn(context, resourceProvider, words, leftCount, words.size()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.TOP));
        if (continueButton != null) {
            continueButton.setEnabled(words.size() == 12 || words.size() == 24);
        }
    }

    protected void onContinuePressed() {
        if (words.size() != 12 && words.size() != 24) {
            return;
        }
        WalletTestPhraseFragment testFragment = new WalletTestPhraseFragment(words, () -> {
            removeSelfFromStack();
            if (onTested != null) {
                onTested.run();
            }
        });
        testFragment.setCurrentAccount(currentAccount);
        presentFragment(testFragment);
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (scrollView != null) {
            scrollView.setPadding(left, top + dp(96), right, bottom);
        }
        if (closeButton != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) closeButton.getLayoutParams();
            lp.leftMargin = left + dp(4);
            lp.topMargin = top + dp(12);
            closeButton.setLayoutParams(lp);
        }
    }
}

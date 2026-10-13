package org.telegram.ui.Wallet;

import org.telegram.messenger.LocaleController;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getAccountInProduction;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class WalletTestPhraseFragment extends BaseFragment {

    private final String[] words;
    private final int[] indices = new int[3];
    private final Runnable onTested;
    private final ArrayList<WalletWordCell> cells = new ArrayList<>();
    private ScrollView scrollView;
    private ImageView closeButton;
    private ButtonWithCounterView continueButton;
    private int keyboardInset;
    private int lastLeftInset;
    private int lastTopInset;
    private int lastRightInset;
    private int lastBottomInset;
    private boolean tested;

    public WalletTestPhraseFragment(java.util.List<String> secretPhrase, Runnable onTested) {
        words = secretPhrase.toArray(new String[0]);
        if (words.length != 12 && words.length != 24) {
            throw new IllegalArgumentException("Expected a 12- or 24-word recovery phrase");
        }
        this.onTested = onTested;
        ArrayList<Integer> positions = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions);
        for (int i = 0; i < indices.length; i++) {
            indices[i] = positions.get(i);
        }
        Arrays.sort(indices);
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(false);

        SizeNotifierFrameLayout container = new SizeNotifierFrameLayout(context);
        container.setDelegate((keyboardHeight, isWidthGreater) -> {
            keyboardInset = keyboardHeight > dp(20) ? keyboardHeight : 0;
            onInsets(lastLeftInset, lastTopInset, lastRightInset, lastBottomInset);
        });
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
        MediaDataController.getInstance(getAccountInProduction()).setPlaceholderImage(stickerView, "RestrictedEmoji", "\uD83D\uDC68\u200D\uD83C\uDFEB", "100_100");
        stickerView.getImageReceiver().setAutoRepeatCount(1);
        root.addView(stickerView, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        titleView.setText(LocaleController.getString(R.string.WalletTestPhrase));
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 10));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.WalletTestPhraseInfo, indices[0] + 1, indices[1] + 1, indices[2] + 1)));
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 8));

        LinearLayout cellsContainer = new LinearLayout(context);
        cellsContainer.setOrientation(LinearLayout.VERTICAL);
        cellsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        cellsContainer.setClipChildren(false);
        root.addView(cellsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        ArrayList<String> enteredWords = new ArrayList<>();
        for (WalletWordCell cell : cells) {
            enteredWords.add(cell.getWord());
        }
        cells.clear();
        for (int i = 0; i < indices.length; i++) {
            final int position = i;
            WalletWordCell cell = new WalletWordCell(context, indices[i], false, resourceProvider);
            if (i < enteredWords.size()) {
                cell.setText(enteredWords.get(i));
            }
            cell.setOnTextChangedListener(this::updateContinueButton);
            cell.setOnNextListener(() -> {
                if (position + 1 < cells.size()) {
                    cells.get(position + 1).requestFieldFocus();
                } else {
                    AndroidUtilities.hideKeyboard(cell);
                }
            });
            cellsContainer.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL, 0, i == 0 ? 0 : 12, 0, 0));
            cells.add(cell);
        }

        root.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        continueButton = new ButtonWithCounterView(context, resourceProvider);
        continueButton.setRound();
        continueButton.setText(LocaleController.getString(R.string.WalletContinue));
        continueButton.setOnClickListener(v -> onContinuePressed());
        root.addView(continueButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        updateContinueButton();
        return fragmentView;
    }

    private void updateContinueButton() {
        if (continueButton == null) {
            return;
        }
        boolean allFilled = cells.size() == indices.length;
        for (WalletWordCell cell : cells) {
            allFilled &= !cell.isEmpty();
        }
        continueButton.setVisibility(allFilled ? View.VISIBLE : View.INVISIBLE);
        continueButton.setEnabled(allFilled && !tested);
    }

    private void onContinuePressed() {
        if (tested || cells.size() != indices.length) {
            return;
        }
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            for (WalletWordCell cell : cells) {
                if (cell.isEmpty()) {
                    return;
                }
            }
            boolean allCorrect = true;
            for (int i = 0; i < indices.length; i++) {
                WalletWordCell cell = cells.get(i);
                boolean correct = words[indices[i]].equalsIgnoreCase(cell.getWord());
                if (!correct && cell.hasError()) {
                    AndroidUtilities.shakeViewSpring(cell);
                }
                cell.setError(!correct);
                allCorrect &= correct;
            }
            if (!allCorrect) {
                return;
            }
        }
        tested = true;
        updateContinueButton();
        AndroidUtilities.hideKeyboard(fragmentView);
        if (onTested != null) {
            onTested.run();
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        lastLeftInset = left;
        lastTopInset = top;
        lastRightInset = right;
        lastBottomInset = bottom;
        if (scrollView != null) {
            scrollView.setPadding(left, top + dp(96), right, bottom + keyboardInset);
        }
        if (closeButton != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) closeButton.getLayoutParams();
            lp.leftMargin = left + dp(4);
            lp.topMargin = top + dp(12);
            closeButton.setLayoutParams(lp);
        }
    }
}

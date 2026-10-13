package org.telegram.ui.Wallet;

import org.telegram.ui.Wallet.GramWallet.SecretPhrase;
import org.telegram.messenger.LocaleController;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getAccountInProduction;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.BackupImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Wallet.WalletActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WalletImportFragment extends BaseFragment {

    private int currentCount = 12;

    private WalletActivity.TabsView tabs;
    private LinearLayout cellsContainer;
    private ButtonWithCounterView importButton;
    private ImageView closeButton;
    private LinearLayout rootView;
    private ScrollView scrollView;
    private LinkSpanDrawable.LinksTextView subtitleView;
    private int keyboardInset;
    private boolean wipe;
    private byte[] matchPublicKey;

    public WalletImportFragment setWipe(boolean wipe) {
        this.wipe = wipe;
        return this;
    }

    public WalletImportFragment setMatchPublicKey(byte[] publicKey) {
        this.matchPublicKey = publicKey;
        return this;
    }

    private final ArrayList<WalletWordCell> cells = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(false);

        fragmentView = new SizeNotifierFrameLayout(context);
        ((SizeNotifierFrameLayout) fragmentView).setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, dp(96), 0, 0);
        this.scrollView = scrollView;
        scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top < oldBottom - oldTop) {
                for (WalletWordCell cell : cells) {
                    if (cell.hasFocus()) {
                        // Recheck focus after the keyboard has reduced the actual viewport.
                        cell.requestRectangleOnScreen(new Rect(0, 0, cell.getWidth(), cell.getHeight()), true);
                        break;
                    }
                }
            }
        });
        ((FrameLayout) fragmentView).addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), 0, dp(36), dp(24));
        root.setClipToPadding(false);
        rootView = root;
        scrollView.addView(root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), android.graphics.PorterDuff.Mode.SRC_IN));
        closeButton.setImageResource(R.drawable.ic_ab_close);
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 3));
        closeButton.setOnClickListener(v -> finishFragment());
        ((FrameLayout) fragmentView).addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 4, 12, 0, 0));

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
        titleView.setText(matchPublicKey != null ? LocaleController.getString(R.string.WalletSecretPhrase) : LocaleController.getString(R.string.WalletImport));
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 10));

        subtitleView = new LinkSpanDrawable.LinksTextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourceProvider));
        if (matchPublicKey != null) {
            subtitleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.WalletEnterSecretPhrase), () -> {
                new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setMessage(LocaleController.getString(R.string.WalletEnterSecretPhraseInfo))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
            }));
        } else {
            subtitleView.setText(LocaleController.formatSpannable(R.string.WalletImportPhraseInfo, 12));
        }
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 8));

        tabs = new WalletActivity.TabsView(context, new CharSequence[] { LocaleController.formatPluralString("WalletPhraseWords", 12), LocaleController.formatPluralString("WalletPhraseWords", 24) }, tab -> {
            setCount(tab == 0 ? 12 : 24);
        }, resourceProvider);
        tabs.shadowed = true;
        tabs.updateColors();
        tabs.setPadding(dp(8), dp(8), dp(8), dp(8));
        tabs.setClipToPadding(false);
        root.addView(tabs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

        cellsContainer = new LinearLayout(context);
        cellsContainer.setOrientation(LinearLayout.VERTICAL);
        cellsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        cellsContainer.setClipChildren(false);
        root.addView(cellsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        importButton = new ButtonWithCounterView(context, resourceProvider);
        importButton.setRound();
        importButton.setText(LocaleController.getString(R.string.WalletImportButton));
        importButton.setEnabled(false);
        importButton.setOnClickListener(v -> onImportPressed());
        root.addView(importButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        buildCells(context);
        updateImportButton();

        return fragmentView;
    }

    private void setCount(int count) {
        if (currentCount == count) {
            return;
        }
        currentCount = count;
        buildCells(getContext());
        updateImportButton();

        subtitleView.setText(matchPublicKey != null ? LocaleController.formatSpannable(R.string.WalletImportCurrentPhraseInfo, count) : LocaleController.formatSpannable(R.string.WalletImportPhraseInfo, count));
    }

    private void rebuildCells(Context context) {
        cellsContainer.removeAllViews();
        cells.clear();
        for (int i = 0; i < currentCount; i++) {
            final int index = i;
            WalletWordCell cell = new WalletWordCell(context, i, i == 0, resourceProvider);
            cell.setOnTextChangedListener(this::updateImportButton);
            cell.setOnPasteListener(() -> fillRemainingFromClipboard(index));
            cell.setOnNextListener(() -> {
                if (index + 1 < cells.size()) {
                    cells.get(index + 1).requestFieldFocus();
                } else {
                    AndroidUtilities.hideKeyboard(cell);
                }
            });
            cellsContainer.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL, 0, i == 0 ? 0 : 12, 0, 0));
            cells.add(cell);
        }
    }

    private void buildCells(Context context) {
        ArrayList<String> existing = new ArrayList<>();
        for (WalletWordCell cell : cells) {
            existing.add(cell.getWord());
        }
        rebuildCells(context);
        for (int i = 0; i < existing.size() && i < cells.size(); i++) {
            if (!existing.get(i).isEmpty()) {
                cells.get(i).setText(existing.get(i));
            }
        }
    }

    private void fillRemainingFromClipboard(int startIndex) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || clipboard.getPrimaryClip() == null || clipboard.getPrimaryClip().getItemCount() == 0) {
                return;
            }
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (text == null) return;
            String[] parts = text.toString().trim().toLowerCase().split("\\s+");
            int p = 0;
            for (int c = startIndex; c < cells.size() && p < parts.length; c++, p++) {
                cells.get(c).setText(parts[p]);
            }
            updateImportButton();
        } catch (Exception e) {
            // ignore
        }
    }

    private void updateImportButton() {
        boolean allFilled = true;
        for (WalletWordCell cell : cells) {
            if (cell.isEmpty()) {
                allFilled = false;
                break;
            }
        }
        importButton.setEnabled(allFilled);
    }

    private void onImportPressed() {
        if (importButton.isLoading()) return;

        ArrayList<String> words = new ArrayList<>();
        for (WalletWordCell cell : cells) {
            words.add(cell.getWord().toLowerCase().trim());
        }
        try (SecretPhrase phrase = SecretPhrase.fromWords(words)) {

            importButton.setLoading(true);

            if (matchPublicKey != null) {
                byte[] publicKey = null;
                try {
                    publicKey = WalletEngine2.secretPhraseToPublicKey(phrase);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (publicKey == null) {
                    importButton.setLoading(false);
                    new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(LocaleController.getString(R.string.WalletInvalidRecoveryPhrase))
                        .setMessage(LocaleController.getString(R.string.WalletInvalidRecoveryPhraseInfo))
                        .setPositiveButton(LocaleController.getString(R.string.WalletOK), null)
                        .show();
                    return;
                } else if (!Arrays.equals(publicKey, matchPublicKey)) {
                    String address = null;
                    try {
                        address = WalletEngine2.secretPhraseToAddress(phrase);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    FrameLayout addressView = null;
                    if (!TextUtils.isEmpty(address)) {
                        addressView = new FrameLayout(getContext());
                        final TextView addressText = new TextView(getContext());
                        addressText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        addressText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
                        addressText.setText(formatAddressTwoLines(address));
                        addressText.setPadding(dp(10), dp(12), dp(10), dp(12));
                        addressText.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
                        addressText.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider)));
                        addressText.setGravity(Gravity.CENTER);
                        addressView.addView(addressText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 16, 0, 16, 0));
                    }
                    importButton.setLoading(false);
                    new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(LocaleController.getString(R.string.WalletWrongSecretPhrase))
                        .setMessage(LocaleController.getString(R.string.WalletWrongSecretPhraseInfo))
                        .setView(addressView)
                        .setPositiveButton(getString(R.string.OK), null)
                        .show();
                    return;
                } else {
                    final GramWallet wallet = GramWallet.getInstance(currentAccount);
                    wallet.storage.init(UserConfig.getInstance(currentAccount).getClientUserId(), publicKey, phrase, error -> {
                        importButton.setLoading(false);
                        if (error != null) {
                            BulletinFactory.of(this).showForError(error);
                            return;
                        }
                        finishFragment();
                        wallet.notifyUpdate();
                    });
                    return;
                }
            }

            importButton.setLoading(true);

            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            final String pastAddress = wallet.getAddress();
            final BaseFragment previousFragment = getParentLayout() != null && getParentLayout().getFragmentStack().size() > 1
                    ? getParentLayout().getFragmentStack().get(getParentLayout().getFragmentStack().size() - 2)
                    : null;
            wallet.importWallet(phrase, error -> {
                importButton.setLoading(false);
                if ("WRONG_CONTRACT".equalsIgnoreCase(error) || "INVALID_PHRASE".equalsIgnoreCase(error)) {
                    new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(LocaleController.getString(R.string.WalletInvalidRecoveryPhrase))
                            .setMessage(LocaleController.getString(R.string.WalletInvalidRecoveryPhraseInfo))
                            .setPositiveButton(LocaleController.getString(R.string.WalletOK), null)
                            .show();
                    return;
                } else if (error != null) {
                    BulletinFactory.of(this).showForError(error);
                    return;
                }
                if (wipe && !TextUtils.isEmpty(pastAddress) && !TextUtils.equals(pastAddress, wallet.getAddress())) {
                    new GramWalletStorage(getContext(), currentAccount, pastAddress).wipe();
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (previousFragment instanceof WalletActivity && BulletinFactory.canShowBulletin(previousFragment)) {
                        BulletinFactory.of(previousFragment)
                                .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.WalletImported), LocaleController.getString(R.string.WalletImportedInfo))
                                .show();
                    }
                    finishFragment();
                }, 300);
            });
        }
    }

    public static CharSequence formatAddressTwoLines(String address) {
        if (address == null)
            return null;
        final SpannableStringBuilder formatted = new SpannableStringBuilder();
        int from = 0;
        for (int i = 0, j = 0; i < address.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(i % (6*4) == 0 ? '\n' : ' ');
                if (i % (6*4) == 0) j++;
                from = formatted.length();
            }
            formatted.append(address.charAt(i));
            if (i % 4 == 3) {
                if (j++ % 2 == 1) {
                    formatted.setSpan(new EllipsizeSpanAnimator.TextAlphaSpan(0.75f), from, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                from = formatted.length();
            }
        }
        return formatted;
    }

    private void showError(String text) {
        if (getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
            .createErrorBulletin(text, resourceProvider)
            .show();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        keyboardInset = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        return super.onInsetsInternal(view, windowInsets);
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (scrollView != null) {
            scrollView.setPadding(left, top + dp(96), right, 0);
            // Padding alone leaves covered fields inside ScrollView's focus viewport.
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) scrollView.getLayoutParams();
            int bottomMargin = Math.max(bottom, keyboardInset);
            if (lp.bottomMargin != bottomMargin) {
                lp.bottomMargin = bottomMargin;
                scrollView.setLayoutParams(lp);
            }
        }
        if (closeButton != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) closeButton.getLayoutParams();
            if (lp != null) {
                lp.topMargin = top + dp(12);
                closeButton.setLayoutParams(lp);
            }
        }
    }
}

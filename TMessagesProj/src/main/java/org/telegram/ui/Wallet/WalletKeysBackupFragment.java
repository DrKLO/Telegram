package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getAccountInProduction;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.ui.Wallet.GramWallet.SecretPhrase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_toncenter;
import org.telegram.ui.ActionBar.AlertDialog;
 import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TopViewCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.ActionBar.Theme;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class WalletKeysBackupFragment extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private final ArrayList<GramWallet.PreparedDisableBackup> preparedBackups = new ArrayList<>();
    private boolean destroyed;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.WalletKeysAndBackup);
    }

    @Override
    public View createView(Context context) {
        fragmentView = super.createView(context);

        actionBar.setAdaptiveBackground(listView);

        listView.setSections();
        listView.setClipToPadding(false);

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.walletUpdate);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.walletUpdate);
        destroyed = true;
        for (GramWallet.PreparedDisableBackup prepared : preparedBackups) prepared.close();
        preparedBackups.clear();
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.walletUpdate) {
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    private final int BTN_RECOVERY_PHRASE = 1;
    private final int BTN_ENABLE_BACKUP = 2;
    private final int BTN_DISABLE_BACKUP = 3;
    private final int BTN_DELETE = 4;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);

        if (wallet.canExportPhrase() || wallet.cantAccessWallet()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.WalletRecoveryPhraseLearnTitle)));
            items.add(UItem.asButton(BTN_RECOVERY_PHRASE, LocaleController.getString(R.string.WalletRecoveryPhraseLearnButton)).accent());
            items.add(UItem.asShadow(LocaleController.getString(R.string.WalletRecoveryPhraseTransferInfo)));
        }

        if (wallet.isBackupEnabled() || wallet.canEnableBackup()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.WalletEncryptedBackup)));
            if (wallet.isBackupEnabled()) {
                items.add(UItem.asButton(BTN_DISABLE_BACKUP, LocaleController.getString(R.string.WalletDisableBackup)).red());
                items.add(UItem.asShadow(LocaleController.getString(R.string.WalletBackupEnabledDescription)));
            } else {
                items.add(UItem.asButton(BTN_ENABLE_BACKUP, LocaleController.getString(R.string.WalletEnableBackup)).accent());
                items.add(UItem.asShadow(LocaleController.getString(R.string.WalletBackupDisabledDescription)));
            }
        }

        items.add(UItem.asButton(BTN_DELETE, LocaleController.getString(R.string.WalletDeleteWallet)).red());

        final List<GramWallet.PreviousWallet> localWallets = wallet.getPreviousWallets();
        if (!localWallets.isEmpty()) {
            boolean first = true;
            for (GramWallet.PreviousWallet localWallet : localWallets) {
                if (localWallet.outdated && !BuildVars.DEBUG_PRIVATE_VERSION) {
                    continue;
                }
                if (first) {
                    items.add(UItem.asShadow(null));
                    items.add(UItem.asHeader(LocaleController.getString(R.string.WalletPreviousWallets)));
                    first = false;
                }
                items.add(LocalWalletCell.Factory.of(localWallet.address, localWallet.balance, localWallet.lastUsage, localWallet.outdated));
            }
            if (!first) {
                items.add(UItem.asShadow(LocaleController.getString(R.string.WalletPreviousWalletsInfo)));
            }
        }
    }

    public static void showRecoveryPhraseLearnAlert(Context context, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<Utilities.Callback<String>> onShow) {
        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, 0, 0, dp(8));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(36), 0, dp(36), 0);

        TopViewCell topViewCell = new TopViewCell(context, resourcesProvider);
        topViewCell.setPadding(dp(0), dp(24), dp(0), dp(4));
        topViewCell.setEmojiSize(100);
        topViewCell.imageView.getImageReceiver().setCurrentAccount(getAccountInProduction());
        MediaDataController.getInstance(getAccountInProduction()).setPlaceholderImage(topViewCell.imageView, "RestrictedEmoji", "\uD83D\uDCDD", "100_100");
        contentLayout.addView(topViewCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL));

        topViewCell.setText(
                Emoji.replaceEmoji(LocaleController.getString(R.string.WalletRecoveryPhraseLearnTitle), topViewCell.titleView.getPaint().getFontMetricsInt(), false),
                Emoji.replaceEmoji(LocaleController.getString(R.string.WalletRecoveryPhraseLearnInfo), topViewCell.textView.getPaint().getFontMetricsInt(), false));
        topViewCell.titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        topViewCell.textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        NotificationCenter.listenEmojiLoading(topViewCell.titleView);
        NotificationCenter.listenEmojiLoading(topViewCell.textView);

        contentLayout.addView(makeLearnBullet(context, resourcesProvider, R.drawable.wallet_learn_hidden,
                LocaleController.getString(R.string.WalletRecoveryPhraseNeverShare)));
        contentLayout.addView(makeLearnBullet(context, resourcesProvider, R.drawable.wallet_learn_warn,
                LocaleController.getString(R.string.WalletRecoveryPhraseFundsWarning)));
        contentLayout.addView(makeLearnBullet(context, resourcesProvider, R.drawable.wallet_learn_protected,
                LocaleController.getString(R.string.WalletRecoveryPhraseSupportWarning)));

        linearLayout.addView(contentLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(LocaleController.getString(R.string.WalletRecoveryPhraseLearnButton), false);
        linearLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 14, 14, 14, 0));

        b.setCustomView(linearLayout);
        BottomSheet sheet = b.create();
        sheet.fixNavigationBar();

        button.setOnClickListener(v -> {
            button.setLoading(true);
            onShow.run(error -> {
                button.setLoading(false);
                if (error != null) {
                    BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(error);
                } else {
                    sheet.dismiss();
                }
            });
        });

        sheet.show();
    }

    public static void showRecoveryPhraseAlert(Context context, ArrayList<String> words, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<Utilities.Callback<String>> onImport, Runnable onDelete) {
        if (words == null) return;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.setPadding(0, dp(24), 0, 0);

        BackupImageView stickerView = new BackupImageView(context);
        stickerView.setAspectFit(true);
        stickerView.getImageReceiver().setCurrentAccount(getAccountInProduction());
        MediaDataController.getInstance(getAccountInProduction()).setPlaceholderImage(stickerView, "RestrictedEmoji", "\uD83D\uDCDD", "100_100");
        contentLayout.addView(stickerView, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setText(LocaleController.getString(R.string.WalletYourRecoveryPhrase));
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setText(LocaleController.getString(R.string.WalletRecoveryPhraseInfo));
        subtitleView.setMaxWidth(dp(330));
        subtitleView.setPadding(dp(12), dp(4), dp(12), 0);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        LinearLayout listWrap = new LinearLayout(context);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.CENTER_HORIZONTAL);

        int leftCount = (words.size() + 1) / 2;
        LinearLayout leftColumn = makeWordColumn(context, resourcesProvider, words, 0, leftCount);
        LinearLayout rightColumn = makeWordColumn(context, resourcesProvider, words, leftCount, words.size());
        columns.addView(leftColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.TOP));
        View gap = new View(context);
        columns.addView(gap, LayoutHelper.createLinear(10, 0));
        columns.addView(rightColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.TOP));

        listWrap.addView(columns, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        contentLayout.addView(listWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 40, 32, 40, 28));

        linearLayout.addView(contentLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ButtonWithCounterView button2;
        if (onImport != null) {
            button2 = new ButtonWithCounterView(context, resourcesProvider).setRound();
            linearLayout.addView(button2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 14, 14, 14, 4));
        } else {
            button2 = null;
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        linearLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 14, button2 != null ? 4 : 14, 14, 14));

        b.setCustomView(linearLayout);
        BottomSheet sheet = b.create();
        sheet.fixNavigationBar();

        if (button2 != null && onImport != null) {
            button.setFilled(false);
            button2.setText(LocaleController.getString(R.string.WalletImportThisWallet));
            button2.setOnClickListener(v -> {
                if (button2.isLoading()) return;
                button2.setLoading(true);
                onImport.run(err -> {
                    if (err != null) {
                        BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(err);
                        return;
                    }
                    button2.setLoading(false);
                    sheet.dismiss();
                });
            });
        }

        if (onDelete != null) {
            button.setColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
            button.setText(LocaleController.getString(R.string.WalletDelete));
            button.setOnClickListener(v -> {
                new AlertDialog.Builder(context, resourcesProvider)
                    .setTitle(LocaleController.getString(R.string.WalletDeleteWalletTitle))
                    .setMessage(LocaleController.getString(R.string.WalletDeleteWalletInfo))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                        onDelete.run();
                        sheet.dismiss();
                    })
                    .makeRed(AlertDialog.BUTTON_POSITIVE)
                    .show();
            });
        } else {
            button.setText(LocaleController.getString(R.string.WalletDone));
            button.setOnClickListener(v -> sheet.dismiss());
        }

        sheet.show();
    }

    static LinearLayout makeWordColumn(Context context, Theme.ResourcesProvider resourcesProvider, ArrayList<String> words, int from, int to) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        for (int i = from; i < to; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView numberView = new TextView(context);
            numberView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            numberView.setGravity(Gravity.END);
            numberView.setIncludeFontPadding(false);
            numberView.setLineSpacing(0, 1.0f);
            numberView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            numberView.setText((i + 1) + ".");
            row.addView(numberView, LayoutHelper.createLinear(26, LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 0, 6, 0));


            TextView wordView = new TextView(context);
            wordView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            wordView.setTypeface(AndroidUtilities.bold());
            wordView.setIncludeFontPadding(false);
            wordView.setLineSpacing(0, 1.0f);
            wordView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            wordView.setText(words.get(i));
            row.addView(wordView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                row.setScreenReaderFocusable(true);
            }

            column.addView(row, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 0, i == from ? 0 : 10, 0, 0));
        }
        return column;
    }

    private static LinearLayout makeLearnBullet(Context context, Theme.ResourcesProvider resourcesProvider, int iconResId, String text) {
        LinearLayout bullet = new LinearLayout(context);
        bullet.setOrientation(LinearLayout.HORIZONTAL);
        bullet.setPadding(0, dp(8), 0, dp(8));

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        imageView.setImageResource(iconResId);
        bullet.addView(imageView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setText(AndroidUtilities.replaceTags(text));
        bullet.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        return bullet;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BTN_RECOVERY_PHRASE) {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            if (wallet.cantAccessWallet()) {
                presentFragment(new WalletImportFragment().setMatchPublicKey(wallet.getPublicKey()));
                return;
            }
            showRecoveryPhraseLearnAlert(getContext(), getResourceProvider(), done -> {
                GramWallet.getInstance(currentAccount).getSecretPhrase(true, false, (words, error) -> {
                    if (error != null) {
                        done.run(error);
                        return;
                    }
                    if (words == null) {
                        done.run("WORDS_NULL");
                        return;
                    }
                    done.run(null);
                    showRecoveryPhraseAlert(getContext(), words.toWordsForDisplay(), resourceProvider, null, null);
                });
            });
            return;
        } else if (item.id == BTN_DISABLE_BACKUP) {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);

            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(500);
            wallet.emulateDisableBackup((fee, error) -> {
                progressDialog.dismiss();
                if (fee == null) {
                    BulletinFactory.of(this).showForError(error == null ? "NULL_ERROR" : error);
                    return;
                }
                final Context context = getContext();
                if (context == null) {
                    return;
                }

                final CheckBoxCell updatePhraseCell = new CheckBoxCell(context, 1, getResourceProvider());
                updatePhraseCell.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(12)));
                updatePhraseCell.setText(LocaleController.getString(R.string.WalletUpdateSecretPhrase), "", false, false);
                updatePhraseCell.setMultiline(true);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) updatePhraseCell.getCheckBoxView().getLayoutParams();
                lp.topMargin = 0;
                lp.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL;
                updatePhraseCell.getCheckBoxView().setLayoutParams(lp);
                updatePhraseCell.setPadding(LocaleController.isRTL ? dp(4) : 0, dp(12), LocaleController.isRTL ? 0 : dp(4), dp(12));

                final TextView updatePhraseInfo = new TextView(context);
                updatePhraseInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                updatePhraseInfo.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                updatePhraseInfo.setGravity(Gravity.CENTER);
                updatePhraseInfo.setBackground(Theme.createRoundRectDrawable(dp(10), getThemedColor(Theme.key_windowBackgroundGray)));
                updatePhraseInfo.setPadding(dp(16), dp(12), dp(16), dp(12));
                updatePhraseInfo.setText(fee > 0
                    ? LocaleController.formatSpannable(R.string.WalletUpdateSecretPhraseInfoWithFee, GramWallet.formatGramCapital(fee), wallet.formatCurrency(fee, true))
                    : LocaleController.getString(R.string.WalletUpdateSecretPhraseInfo));
                updatePhraseInfo.setVisibility(View.GONE);
                updatePhraseCell.setOnClickListener(v -> {
                    updatePhraseCell.setChecked(!updatePhraseCell.isChecked(), true);
                    updatePhraseInfo.setVisibility(updatePhraseCell.isChecked() ? View.VISIBLE : View.GONE);
                });

                final LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                container.addView(updatePhraseCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 0));
                container.addView(updatePhraseInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 16));

                final AlertDialog dialog = new AlertDialog.Builder(context, getResourceProvider())
                    .setTitle(LocaleController.getString(R.string.WalletDisableBackupTitle))
                    .setMessage(LocaleController.getString(R.string.WalletDisableBackupInfo))
                    .setCustomViewOffset(6)
                    .setView(container)
                    .setWidth(dp(312))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(LocaleController.getString(R.string.WalletDisable), (di, w) -> {
                        if (!updatePhraseCell.isEnabled()) return;
                        final boolean updatePhrase = updatePhraseCell.isChecked();
                        updatePhraseCell.setEnabled(false);
                        final Browser.Progress progress = di.makeButtonLoading(w);
                        progress.init();
                        if (!updatePhrase) {
                            wallet.getSecretPhrase((phrase, error2) -> {
                                progress.end();
                                if (phrase == null) {
                                    BulletinFactory.of(this).showForError(error2 == null ? "NULL_ERROR" : error2);
                                    return;
                                }
                                presentFragment(
                                    new WalletSecretPhraseFragment()
                                        .setCurrent()
                                        .setPhrase(phrase)

                                        .onTested(() -> {
                                            new AlertDialog.Builder(getContext(), getResourceProvider())
                                                .setTitle(LocaleController.getString(R.string.WalletDisableBackupTitle))
                                                .setMessage(LocaleController.getString(R.string.WalletDisableBackupExistingPhraseInfo))
                                                .setNegativeButton(getString(R.string.Cancel), null)
                                                .setPositiveButton(LocaleController.getString(R.string.WalletDisable), (di3, w3) -> {
                                                    final Browser.Progress progress3 = di3.makeButtonLoading(w3);
                                                    progress3.init();
                                                    wallet.disableBackupWithoutUpdatingPhrase(error3 -> {
                                                        progress3.end();
                                                        returnToWalletKeys();
                                                        if (error3 != null) {
                                                            BulletinFactory.of(this).showForError(error3 == null ? "NULL_ERROR" : error3);
                                                        } else {
                                                            BulletinFactory.of(this)
                                                                .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.WalletBackupDisabled), LocaleController.getString(R.string.WalletBackupDisabledInfo))
                                                                .setDuration(Bulletin.DURATION_PROLONG)
                                                                .show();
                                                        }
                                                    });
                                                })
                                                .makeRed(AlertDialog.BUTTON_POSITIVE)
                                                .show();
                                        })
                                );
                            });
                        } else {
                            if (wallet.getBalance() < fee) {
                                new AlertDialog.Builder(getContext(), getResourceProvider())
                                    .setTitle(LocaleController.getString(R.string.WalletNotEnoughGram))
                                    .setMessage(LocaleController.formatSpannable(R.string.WalletUpdateSecretPhraseInsufficientFunds, GramWallet.formatGramCapital(fee), wallet.formatCurrency(fee, true)))
                                    .setNegativeButton(LocaleController.getString(R.string.WalletNotNow), (di3, w3) -> {

                                    })
                                    .setPositiveButton(LocaleController.getString(R.string.WalletTopUp), (di3, w3) -> {
                                        final Browser.Progress progress3 = di3.makeButtonLoading(w3);
                                        progress3.init();
                                        WalletActivity.openOnramp(currentAccount, 0, error3 -> {
                                            progress3.end();
                                            if (!TextUtils.isEmpty(error3)) {
                                                BulletinFactory.of(this).showForError(error3);
                                            }
                                        });
                                    })
                                    .show();
                                return;
                            }
                            wallet.prepareDisableBackup((prepared, error2) -> {
                                progress.end();
                                if (prepared == null) {
                                    BulletinFactory.of(this).showForError(error2 == null ? "NULL_ERROR" : error2);
                                    return;
                                }
                                if (destroyed) { prepared.close(); return; }
                                preparedBackups.add(prepared);
                                presentFragment(
                                    new WalletSecretPhraseFragment()
                                        .setPhrase(prepared.newSecretPhrase)
                                        .onTested(() -> {
                                            new AlertDialog.Builder(getContext(), getResourceProvider())
                                                .setTitle(LocaleController.getString(R.string.WalletDisableBackupTitle))
                                                .setMessage(LocaleController.getString(R.string.WalletDisableBackupConfirmInfo))
                                                .setNegativeButton(getString(R.string.Cancel), null)
                                                .setPositiveButton(LocaleController.getString(R.string.WalletDisable), (di3, w3) -> {
                                                    final Browser.Progress progress3 = di3.makeButtonLoading(w3);
                                                    progress3.init();
                                                    wallet.disableBackup(prepared, (error3) -> {
                                                        progress3.end();
                                                        if (error3 != null) {
                                                            BulletinFactory.of(this).showForError(error3);
                                                            return;
                                                        }
                                                        returnToWallet();
                                                    });
                                                })
                                                .makeRed(AlertDialog.BUTTON_POSITIVE)
                                                .show();
                                        })
                                );
                            });
                        }
                    })
                    .create();
                showDialog(dialog);
                dialog.makeRed(AlertDialog.BUTTON_POSITIVE);
            });
        } else if (item.id == BTN_ENABLE_BACKUP) {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            wallet.enableBackup(error -> {
                if (error != null) {
                    BulletinFactory.of(this).showForError(error);
                    return;
                } else {
                    BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.WalletBackupEnabled), LocaleController.getString(R.string.WalletBackupEnabledInfo))
                        .setDuration(Bulletin.DURATION_PROLONG)
                        .show();
                    listView.adapter.update(true);
                }
            });
        } else if (item.id == BTN_DELETE) {
            AlertsCreator.showAlertWithCheckbox(
                getContext(),
                LocaleController.getString(R.string.WalletDeleteWalletTitle),
                LocaleController.getString(R.string.WalletDeleteWalletInfo),
                BuildVars.DEBUG_PRIVATE_VERSION ? LocaleController.getString(R.string.WalletSaveOnDevice) : null,
                LocaleController.getString(R.string.WalletDeleteAnyway),
                saveLocally -> {
                    final GramWallet wallet = GramWallet.getInstance(currentAccount);
                    final Runnable replaceWallet = () -> {
                        new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(LocaleController.getString(R.string.WalletReplaceWalletTitle))
                            .setPositiveButton(LocaleController.getString(R.string.WalletCreateNew), (di2, w2) -> {
                                if (!saveLocally) {
                                    wallet.storage.wipe();
                                }
                                BaseFragment previousFragment = getParentLayout() != null && getParentLayout().getFragmentStack().size() > 1
                                        ? getParentLayout().getFragmentStack().get(getParentLayout().getFragmentStack().size() - 2)
                                        : null;
                                wallet.createNewWallet(error -> {
                                    if (error != null) {
                                        BulletinFactory.of(WalletKeysBackupFragment.this).showForError(error);
                                        return;
                                    }
                                    finishFragment();
                                    if (previousFragment instanceof WalletActivity) {
                                        final BaseFragment walletFragment = previousFragment;
                                        AndroidUtilities.runOnUIThread(() -> {
                                            if (BulletinFactory.canShowBulletin(walletFragment)) {
                                                BulletinFactory.of(walletFragment)
                                                    .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.WalletCreated), LocaleController.getString(R.string.WalletCreatedInfo))
                                                    .show();
                                            }
                                        }, 300);
                                    }
                                });
                            })
                            .setNegativeButton(LocaleController.getString(R.string.WalletImportExisting), (di2, w2) -> {
                                di2.dismiss();
                                presentFragment(new WalletImportFragment().setWipe(!saveLocally));
                            })
                            .show();
                    };
                    if (saveLocally && BuildVars.DEBUG_PRIVATE_VERSION) {
                        wallet.makeSureSecretPhraseBackedUpLocally(error -> {
                            if (error != null) BulletinFactory.of(WalletKeysBackupFragment.this).showForError(error);
                            else replaceWallet.run();
                        });
                    } else {
                        replaceWallet.run();
                    }
                },
                getResourceProvider()
            ).makeRed(AlertDialog.BUTTON_POSITIVE);
        } else if (item.instanceOf(LocalWalletCell.Factory.class)) {
            final String address = item.text.toString();
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            final GramWalletStorage storage = new GramWalletStorage(getContext(), currentAccount, address);
            showRecoveryPhraseLearnAlert(getContext(), getResourceProvider(), done -> {
                wallet.getAddressBalanceAndPublicKey(address, info -> {
                    if (info == null) {
                        done.run("ADDRESS_NO_INFO");
                        return;
                    }
                    byte[] publicKey = info.publicKey;
                    if (publicKey == null) {
                        final byte[][] keys = storage.getPublicKeys();
                        if (keys.length <= 0) {
                            done.run("STORAGE_NO_PUBLIC_KEY");
                            return;
                        }
                        publicKey = keys[0];
                    }
                    if (!storage.containsSecretPhrase(publicKey)) {
                        done.run("STORAGE_OLD_PUBLIC_KEY");
                        return;
                    }
                    storage.getSecretPhrase(publicKey, phrase -> {
                        if (phrase == null) {
                            done.run("STORAGE_FAILED");
                            return;
                        }
                        done.run(null);
                        final ArrayList<String> displayedWords = phrase.toWordsForDisplay();
                        showRecoveryPhraseAlert(getContext(), displayedWords, resourceProvider, BuildVars.DEBUG_PRIVATE_VERSION && wallet.canSend() ? done2 -> {
                            wallet.makeSureSecretPhraseBackedUpLocally(true, false, error -> {
                                if (error != null) {
                                    done2.run(error);
                                    return;
                                }
                                try (SecretPhrase imported = SecretPhrase.fromWords(displayedWords)) {
                                    wallet.importWallet(true, false, imported, error2 -> {
                                        listView.adapter.update(true);
                                        done2.run(error2);
                                    });
                                }
                            });
                        } : null, () -> {
                            storage.wipe();
                            listView.adapter.update(true);
                        });
                    }, false);
                });
            });
        }
    }

    private void returnToWallet() {
        final INavigationLayout layout = getParentLayout();
        if (layout == null) {
            finishFragment();
            return;
        }
        final List<BaseFragment> stack = new ArrayList<>(layout.getFragmentStack());
        for (BaseFragment fragment : stack) {
            if (fragment instanceof WalletSendSelectSender || fragment instanceof WalletSecretPhraseFragment || fragment instanceof WalletTestPhraseFragment || fragment instanceof WalletKeysBackupFragment) {
                layout.removeFragmentFromStack(fragment);
            }
        }
        finishFragment();
    }

    private void returnToWalletKeys() {
        final INavigationLayout layout = getParentLayout();
        if (layout == null) return;
        final List<BaseFragment> stack = new ArrayList<>(layout.getFragmentStack());
        for (BaseFragment fragment : stack) {
            if (fragment instanceof WalletSendSelectSender || fragment instanceof WalletSecretPhraseFragment || fragment instanceof WalletTestPhraseFragment) {
                layout.removeFragmentFromStack(fragment);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }

    public static class LocalWalletCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;

        private final LinearLayout layout;
        private final TextView addressView;
        private final TextView statusView;
        private final ImageView arrowView;

        public LocalWalletCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            this.resourcesProvider = resourcesProvider;
            setPadding(dp(20), 0, dp(13), 0);

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, dp(10), 0, dp(10));
            addView(layout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            addressView = new TextView(context);
            addressView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addressView.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            layout.addView(addressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 2));

            statusView = new TextView(context);
            statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            layout.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

            arrowView = new ImageView(context);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            arrowView.setImageResource(R.drawable.attach_arrow_right);
            addView(arrowView, LayoutHelper.createLinear(21, 21, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 9, 0, 0, 0));

            updateColors();
        }

        public void set(String address, long balance, int lastUsedDate, boolean outdated) {
            addressView.setText(WalletImportFragment.formatAddressTwoLines(address));
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (balance < 0) {
                ssb.append(getString(R.string.Loading));
                ssb.setSpan(new LoadingSpan(statusView, dp(35)), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.append(GramWallet.formatGramsWithUnit(balance));
            }
            if (lastUsedDate > 0) {
                ssb = new SpannableStringBuilder(LocaleController.formatSpannable(R.string.WalletPreviousWalletLastUsed, ssb, LocaleController.formatShortDateTime(lastUsedDate)));
            }
            statusView.setText(ssb);

            addressView.setAlpha(outdated ? 0.5f : 1.0f);
            statusView.setAlpha(outdated ? 0.5f : 1.0f);
            arrowView.setAlpha(outdated ? 0.5f : 1.0f);
        }

        @Override
        public void updateColors() {
            addressView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        public static class Factory extends UItem.UItemFactory<LocalWalletCell> {
            static { setup(new Factory()); }

            @Override
            public LocalWalletCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new LocalWalletCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((LocalWalletCell) view).set(item.text.toString(), item.longValue, item.intValue, item.accent);
            }

            public static UItem of(String address, long balance, int lastUsedDate, boolean outdated) {
                UItem item = UItem.ofFactory(LocalWalletCell.Factory.class);
                item.text = address;
                item.longValue = balance;
                item.intValue = lastUsedDate;
                item.accent = outdated;
                return item;
            }
        }
    }
}

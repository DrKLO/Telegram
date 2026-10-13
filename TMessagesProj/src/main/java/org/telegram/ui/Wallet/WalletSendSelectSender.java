package org.telegram.ui.Wallet;

import org.telegram.messenger.LocaleController;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.ChatAttachAlertAudioLayout;
import org.telegram.ui.Gifts.ResaleGiftsFragment;
import org.telegram.ui.GradientClip;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class WalletSendSelectSender extends UniversalFragment {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 44;

    private TL_wallet.nftItem collectible;
    private String sourceAddress;
    private boolean selectingRecipient;
    private Runnable cancelEmulation;
    private boolean destroyed;

    public WalletSendSelectSender setCollectible(TL_wallet.nftItem collectible) {
        this.collectible = collectible;
        sourceAddress = GramWallet.getInstance(currentAccount).getAddress();
        return this;
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        if (clipboardManager != null) clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        if (pasteButton != null) pasteButton.animate().cancel();
        if (scanButton != null) scanButton.animate().cancel();
        if (dnsRequestId != 0) getConnectionsManager().cancelRequest(dnsRequestId, true);
        if (buttonContainer != null) buttonContainer.animate().cancel();
        getNotificationCenter().removeObserver(hintsObserver, NotificationCenter.reloadHints);
        if (searchRunnable != null) AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        if (cancelEmulation != null) cancelEmulation.run();
        super.onFragmentDestroy();
    }

    private void selectRecipient(TLRPC.User user, String address) {
        if (collectible == null) {
            presentFragment(user != null ? new WalletSendEnterAmount(user) : new WalletSendEnterAmount(address).setRecipientDomain(TextUtils.equals(address, validAddress) ? recipientDomain : null));
            return;
        }
        if (selectingRecipient) return;
        selectingRecipient = true;
        continueButton.setLoading(true);
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        final Utilities.Callback<String> emulate = recipient -> {
            if (destroyed) return;
            if (TextUtils.isEmpty(recipient) || !GramWallet.addressEquals(sourceAddress, wallet.getAddress())) {
                selectingRecipient = false;
                continueButton.setLoading(false);
                BulletinFactory.of(this).showForError(TextUtils.isEmpty(recipient) ? LocaleController.getString(R.string.WalletRecipientUnavailable) : "WALLET_CHANGED");
                return;
            }
            cancelEmulation = wallet.emulateSendNFT(recipient, collectible, (transaction, error) -> {
                cancelEmulation = null;
                if (destroyed) return;
                continueButton.setLoading(false);
                if (transaction == null || !GramWallet.addressEquals(sourceAddress, wallet.getAddress())) {
                    selectingRecipient = false;
                    BulletinFactory.of(this).showForError(error != null ? error : LocaleController.getString(R.string.WalletCollectibleTransferPrepareFailed));
                    return;
                }
                if (user != null) {
                    final TL_wallet.walletTransactionPeerUser peer = new TL_wallet.walletTransactionPeerUser();
                    peer.user_id = user.id;
                    peer.address = recipient;
                    transaction.peer = peer;
                }
                WalletActivity.openTransactionSheet(getContext(), currentAccount, transaction, (comment, encrypted, done) -> {
                    if (!GramWallet.addressEquals(sourceAddress, wallet.getAddress())) {
                        done.run("WALLET_CHANGED");
                        return;
                    }
                    wallet.sendNFT(user, recipient, collectible, comment, sendError -> {
                        done.run(sendError);
                        if (sendError == null && !destroyed) {
                            if (getParentLayout() != null) {
                                for (BaseFragment fragment : getParentLayout().getFragmentStack()) {
                                    if (fragment instanceof WalletActivity) {
                                        ((WalletActivity) fragment).showTransactions();
                                    }
                                }
                            }
                            finishFragment();
                        }
                    });
                }, (comment, encrypted) -> selectingRecipient = false, getResourceProvider());
            });
        };
        if (user != null) {
            wallet.resolveWallet(user, true, (resolved, error) -> {
                if (error != null) {
                    continueButton.setLoading(false);
                    return;
                }
                emulate.run(resolved == null ? null : resolved.address);
            });
        } else {
            emulate.run(address);
        }
    }

    private final String initialRecipient;
    private final ArrayList<TLRPC.User> recentUsers = new ArrayList<>();
    private final ArrayList<TLRPC.User> searchUsers = new ArrayList<>();
    private SearchAdapterHelper searchAdapterHelper;
    private String validAddress;
    private String recipientDomain;
    private int dnsRequestId;
    private TextView addressResultTitle;
    private FrameLayout addressResult;
    private TextView addressResultSubtitle;
    private final NotificationCenter.NotificationCenterDelegate hintsObserver = (id, account, args) -> {
        if (!destroyed && listView != null) listView.adapter.update(true);
    };
    private int searchId;
    private Runnable searchRunnable;
    private boolean searchLoading;

    private FrameLayout recipientRow;
    private EditTextBoldCursor recipientEdit;
    private TextView pasteButton;
    private ImageView scanButton;
    private boolean pasteButtonVisible = true;
    private boolean scanButtonVisible = true;
    private boolean clipboardHasData = true;
    private ClipboardManager clipboardManager;
    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener = () -> refreshClipboard(true);
    private FrameLayout buttonContainer;
    private ButtonWithCounterView continueButton;

    private int bottomInset;
    private int keyboardInset;
    private int lastBottom;

    public WalletSendSelectSender() {
        this(null);
    }

    public WalletSendSelectSender(String initialRecipient) {
        this.initialRecipient = initialRecipient;
    }

    @Override
    protected CharSequence getTitle() {
        return collectible == null ? LocaleController.getString(R.string.WalletSendGrams) : LocaleController.getString(R.string.WalletTransferCollectible);
    }

    @Override
    public View createView(Context context) {
        fragmentView = super.createView(context);

        actionBar.setAdaptiveBackground(listView);

        listView.setSections();
        listView.setPadding(0, 0, 0, dp(68));
        listView.setClipToPadding(false);
        listView.adapter.setApplyBackground(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(recipientEdit);
                }
            }
        });

        recipientRow = createRecipientRow(context);

        searchAdapterHelper = new SearchAdapterHelper(true);
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged(int id) {
                if (destroyed || id != searchId) {
                    return;
                }
                rebuildSearchUsers(recipientEdit.getText().toString().trim());
            }
        });
        getNotificationCenter().addObserver(hintsObserver, NotificationCenter.reloadHints);
        MediaDataController.getInstance(currentAccount).loadHints(true);
        loadRecentUsers();

        if (!TextUtils.isEmpty(initialRecipient)) {
            recipientEdit.setText(initialRecipient);
            recipientEdit.setSelection(recipientEdit.length());
        }

        buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(dp(14), dp(10), dp(14), dp(10));
        final int background = getThemedColor(Theme.key_windowBackgroundGray);
        buttonContainer.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Theme.multAlpha(background, 0.0f),
                        background,
                        background
                }
        ));

        continueButton = new ButtonWithCounterView(context, getResourceProvider()).setRound();
        continueButton.setText(LocaleController.getString(R.string.WalletContinue));
        continueButton.setEnabled(false);
        continueButton.setOnClickListener(v -> {
            if (validAddress != null) {
                AndroidUtilities.hideKeyboard(recipientEdit);
                selectRecipient(null, validAddress);
            }
        });
        updateContinueButton();
        buttonContainer.addView(continueButton, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                48,
                Gravity.FILL
        ));
        ((FrameLayout) fragmentView).addView(buttonContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                68,
                Gravity.BOTTOM | Gravity.FILL_HORIZONTAL
        ));
        updateBottomLayout();

        listView.adapter.update(false);

        return fragmentView;
    }

    private FrameLayout createRecipientRow(Context context) {
        final FrameLayout row = new FrameLayout(context) {
            @Override
            public void onWindowFocusChanged(boolean hasWindowFocus) {
                super.onWindowFocusChanged(hasWindowFocus);
                if (hasWindowFocus) refreshClipboard(true);
            }
        };
        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        recipientEdit = new EditTextBoldCursor(context) {
            private final GradientClip gradientClip = new GradientClip();

            @Override
            protected void onDraw(Canvas canvas) {
                if (length() == 0) {
                    super.onDraw(canvas);
                    return;
                }

                canvas.saveLayerAlpha(getScrollX(), 0, getScrollX() + getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                super.onDraw(canvas);
                AndroidUtilities.rectTmp.set(getScrollX(), 0, getScrollX() + dp(12), getHeight());
                gradientClip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);
                AndroidUtilities.rectTmp.set(getScrollX() + getWidth() - dp(12), 0, getScrollX() + getWidth(), getHeight());
                gradientClip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);
                canvas.restore();
            }
        };
        recipientEdit.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        recipientEdit.setHintTextColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteHintText), 0.72f));
        recipientEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        recipientEdit.setHint(LocaleController.getString(R.string.WalletAddressOrName));
        recipientEdit.setSingleLine(true);
        recipientEdit.setBackground(null);
        recipientEdit.setPadding(dp(12), 0, dp(12), 0);
        recipientEdit.setClipToPadding(true);
        recipientEdit.setCursorWidth(1.5f);
        recipientEdit.setGravity(Gravity.CENTER_VERTICAL);
        recipientEdit.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        recipientEdit.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_NEXT);
        row.addView(recipientEdit, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL,
                20 - 12, 0, 32 + 20, 0
        ));

        pasteButton = new TextView(context);
        pasteButton.setText(LocaleController.getString(R.string.WalletPaste));
        pasteButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        pasteButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pasteButton.setTypeface(AndroidUtilities.bold());
        pasteButton.setGravity(Gravity.CENTER);
        final int pasteColor = Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), 0.10f);
        pasteButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(16),
                pasteColor,
                Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), 0.18f)
        ));
        pasteButton.setOnClickListener(v -> pasteRecipient());
        ScaleStateListAnimator.apply(pasteButton, 0.04f, 1.2f);
        row.addView(pasteButton, LayoutHelper.createFrame(
                58,
                32,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL,
                0, 0, 62, 0
        ));

        recipientEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchUsers.clear();
                scheduleSearch(s.toString().trim());
                updateRecipientButtons(true);
            }
        });

        scanButton = new ImageView(context);
        scanButton.setScaleType(ImageView.ScaleType.CENTER);
        scanButton.setImageResource(R.drawable.scan_qr);
        scanButton.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_windowBackgroundWhiteBlueText),
                PorterDuff.Mode.SRC_IN
        ));
        scanButton.setBackground(Theme.createSelectorDrawable(
                Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueText), 0.12f)
        ));
        scanButton.setContentDescription(LocaleController.getString(R.string.WalletScanQRCode));
        scanButton.setOnClickListener(v -> openWalletQrScanner());
        row.addView(scanButton, LayoutHelper.createFrame(
                32,
                40,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL,
                0, 0, 20, 0
        ));

        refreshClipboard(false);
        return row;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        }
        refreshClipboard(true);
    }

    @Override
    public void onPause() {
        if (clipboardManager != null) clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        super.onPause();
    }

    private void refreshClipboard(boolean animated) {
        if (destroyed || recipientEdit == null || scanButton == null) return;
        // Checking availability does not read clipboard contents or trigger a paste notification.
        if (clipboardManager != null) clipboardHasData = clipboardManager.hasPrimaryClip();
        else clipboardHasData = false;
        updateRecipientButtons(animated);
    }

    private void setPasteButtonVisible(boolean visible, boolean animated) {
        if (pasteButtonVisible == visible && animated) return;
        pasteButtonVisible = visible;
        animateRecipientButton(pasteButton, visible, animated);
    }

    private void updateRecipientButtons(boolean animated) {
        if (scanButton == null) return;
        final boolean empty = recipientEdit.length() == 0;
        setPasteButtonVisible(empty && clipboardHasData, animated);
        if (scanButtonVisible != empty || !animated) {
            scanButtonVisible = empty;
            animateRecipientButton(scanButton, empty, animated);
        }
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) recipientEdit.getLayoutParams();
        final int rightMargin = dp(pasteButtonVisible ? 128 : scanButtonVisible ? 60 : 8);
        if (params.rightMargin != rightMargin) {
            params.rightMargin = rightMargin;
            recipientEdit.setLayoutParams(params);
        }
    }

    private void animateRecipientButton(View button, boolean visible, boolean animated) {
        button.animate().cancel();
        button.setEnabled(visible);
        final float alpha = visible ? 1.0f : 0.0f;
        final float scale = visible ? 1.0f : 0.5f;
        if (!animated) {
            button.setAlpha(alpha);
            button.setScaleX(scale);
            button.setScaleY(scale);
            button.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.animate().alpha(alpha).scaleX(scale).scaleY(scale)
                .setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> button.setVisibility(visible ? View.VISIBLE : View.GONE)).start();
    }

    private void openWalletQrScanner() {
        if (getParentActivity() == null) {
            return;
        }
        AndroidUtilities.hideKeyboard(recipientEdit);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
            return;
        }
        CameraScanActivity.showAsSheet(this, true, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                if (isFinished || recipientEdit == null) {
                    return;
                }
                final String address;
                try {
                    final Uri uri = Uri.parse(text == null ? "" : text.trim());
                    if (!"ton".equalsIgnoreCase(uri.getScheme())
                            || !"transfer".equalsIgnoreCase(uri.getAuthority())
                            || uri.getPathSegments().size() != 1) {
                        showInvalidWalletQr();
                        return;
                    }
                    address = uri.getPathSegments().get(0);
                    if (!isValidAddress(address)) {
                        showInvalidWalletQr();
                        return;
                    }
                } catch (Throwable e) {
                    showInvalidWalletQr();
                    return;
                }

                recipientEdit.setText(address);
                recipientEdit.setSelection(recipientEdit.length());
                AndroidUtilities.hideKeyboard(recipientEdit);
                selectRecipient(null, address);
            }
        });
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE || getParentActivity() == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openWalletQrScanner();
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setMessage(AndroidUtilities.replaceTags(getString(R.string.QRCodePermissionNoCameraWithHint)))
                .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialog, which) -> {
                    try {
                        final Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                .setTopAnimation(R.raw.permission_request_camera, 72, false, Theme.getColor(Theme.key_dialogTopBackground))
                .show();
    }

    private void showInvalidWalletQr() {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(
                getString(R.string.ScanQrCode),
                getString(R.string.ErrorOccurred)
        ).show());
    }

    private void loadRecentUsers() {
        recentUsers.clear();
        DialogsSearchAdapter.loadRecentSearch(currentAccount, org.telegram.ui.DialogsActivity.DIALOGS_TYPE_DEFAULT, (objects, byId) -> {
            if (destroyed) return;
            recentUsers.clear();
            for (DialogsSearchAdapter.RecentSearchObject object : objects) {
                if (object.object instanceof TLRPC.User && isSelectableUser((TLRPC.User) object.object)) {
                    recentUsers.add((TLRPC.User) object.object);
                }
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
    }

    private boolean isSelectableUser(TLRPC.User user) {
        return user != null && !user.bot && !user.self && !UserObject.isDeleted(user) && !UserObject.isService(user.id);
    }

    private void scheduleSearch(String query) {
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            searchRunnable = null;
        }
        if (dnsRequestId != 0) {
            getConnectionsManager().cancelRequest(dnsRequestId, true);
            dnsRequestId = 0;
        }
        validAddress = isValidAddress(query) ? query : null;
        recipientDomain = validAddress == null ? GramWalletDns.normalizeDomain(query) : null;
        final int id = ++searchId;
        searchLoading = !TextUtils.isEmpty(query) && validAddress == null;
        searchAdapterHelper.queryServerSearch(null, true, false, false, false, false, 0, false, 0, id);
        updateContinueButton();
        if (TextUtils.isEmpty(query) || validAddress != null || recipientDomain != null) {
            searchUsers.clear();
            listView.adapter.update(true);
            if (recipientDomain != null) {
                final String domain = recipientDomain;
                searchRunnable = () -> {
                    searchRunnable = null;
                    dnsRequestId = GramWalletDns.resolve(currentAccount, domain, address -> {
                        if (destroyed || id != searchId) return;
                        dnsRequestId = 0;
                        searchLoading = false;
                        validAddress = isValidAddress(address) ? WalletEngine2.toUserFriendlyAddress(address) : null;
                        updateContinueButton();
                        listView.adapter.update(true);
                    });
                };
                AndroidUtilities.runOnUIThread(searchRunnable, 300);
            }
            return;
        }
        rebuildSearchUsers(query);
        searchRunnable = () -> {
            searchRunnable = null;
            searchAdapterHelper.queryServerSearch(query, true, false, false, false, false, 0, false, 0, id, 0, () -> {
                if (destroyed || id != searchId) return;
                searchLoading = false;
                listView.adapter.update(true);
            });
        };
        AndroidUtilities.runOnUIThread(searchRunnable, 300);
    }

    private void rebuildSearchUsers(String query) {
        searchUsers.clear();
        if (TextUtils.isEmpty(query) || validAddress != null || recipientDomain != null) {
            if (listView != null) {
                listView.adapter.update(true);
            }
            return;
        }
        final String lowerQuery = query.toLowerCase(Locale.ROOT);
        final HashSet<Long> added = new HashSet<>();
        for (TLRPC.Dialog dialog : getMessagesController().dialogsUsersOnly) {
            if (dialog == null || dialog.id <= 0) {
                continue;
            }
            TLRPC.User user = getMessagesController().getUser(dialog.id);
            if (isSelectableUser(user) && matches(user, lowerQuery) && added.add(user.id)) {
                searchUsers.add(user);
            }
        }
        for (TLObject object : searchAdapterHelper.getGlobalSearch()) {
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                if (isSelectableUser(user) && added.add(user.id)) {
                    searchUsers.add(user);
                }
            }
        }
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private boolean matches(TLRPC.User user, String query) {
        final String name = UserObject.getUserName(user);
        if (name != null && name.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        final String username = UserObject.getPublicUsername(user);
        return username != null && username.toLowerCase(Locale.ROOT).contains(query);
    }

    private void pasteRecipient() {
        final ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            clipboardHasData = false;
            updateRecipientButtons(true);
            return;
        }
        final ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            clipboardHasData = false;
            updateRecipientButtons(true);
            return;
        }
        final CharSequence text = clip.getItemAt(0).coerceToText(getContext());
        if (!TextUtils.isEmpty(text)) {
            recipientEdit.setText(text.toString().trim());
            recipientEdit.setSelection(recipientEdit.length());
        } else {
            clipboardHasData = false;
            updateRecipientButtons(true);
        }
    }

    private void updateContinueButton() {
        if (continueButton != null) {
            continueButton.setEnabled(validAddress != null);
        }
    }

    private boolean isValidAddress(String value) {
        return WalletEngine2.isValidRecipientAddress(value);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (recipientRow == null) {
            return;
        }
        items.add(UItem.asSpace(dp(4)));
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(LocaleController.getString(R.string.WalletRecipient)));
        items.add(UItem.asCustom(recipientRow, 50));
        adapter.whiteSectionEnd();

        final String rawQuery = recipientEdit.getText().toString().trim();
        final String query = rawQuery.toLowerCase(Locale.ROOT);
        if (validAddress != null) {
            if (addressResult == null) {
                addressResult = createAddressResult();
            }
            addressResultTitle.setText(recipientDomain != null ? recipientDomain : getString(R.string.WalletGramWalletAddress));
            addressResultSubtitle.setText(validAddress);
            items.add(UItem.asSpace(dp(12)));
            adapter.whiteSectionStart();
            items.add(UItem.asCustom(addressResult, 60));
            adapter.whiteSectionEnd();
            return;
        }
        final ArrayList<TLRPC.User> users;
        if (TextUtils.isEmpty(query)) {
            users = new ArrayList<>();
            final HashSet<Long> added = new HashSet<>();
            for (TLRPC.TL_topPeer peer : MediaDataController.getInstance(currentAccount).hints) {
                final TLRPC.User user = getMessagesController().getUser(peer.peer.user_id);
                if (isSelectableUser(user) && added.add(user.id)) users.add(user);
            }
            for (TLRPC.User user : recentUsers) {
                if (added.add(user.id)) users.add(user);
            }
        } else {
            users = searchUsers;
        }
        int count = 0;
        for (TLRPC.User user : users) {
            if (count == 0) {
                items.add(UItem.asSpace(dp(12)));
                adapter.whiteSectionStart();
                if (TextUtils.isEmpty(query)) {
                    items.add(UItem.asHeader(getString(R.string.Recent)));
                }
            }
            UItem item = UItem.asProfileCell(user);
            if (!TextUtils.isEmpty(query)) {
                item.text = AndroidUtilities.generateSearchName(user.first_name, user.last_name, rawQuery);
                String username = UserObject.getPublicUsername(user);
                if (!TextUtils.isEmpty(username)) {
                    item.subtext = AndroidUtilities.generateSearchName("@" + username, null, "@" + rawQuery);
                }
                final CharSequence highlightedName = item.text;
                final CharSequence highlightedUsername = item.subtext;
                item.bind = view -> ((ProfileSearchCell) view).setData(user, null, highlightedName, highlightedUsername, false, false);
            }
            items.add(item);
            count++;
        }
        if (searchLoading) {
            if (count == 0) {
                items.add(UItem.asSpace(dp(12)));
                adapter.whiteSectionStart();
            }
            final int loadingRows = recipientDomain != null ? 1 : 3;
            for (int i = 0; i < loadingRows; i++) {
                items.add(UItem.asFlicker(-1 - i, FlickerLoadingView.CONTACT_TYPE));
            }
        }
        if (count > 0 || searchLoading) {
            adapter.whiteSectionEnd();
        }
        if (!searchLoading && count <= 0 && !TextUtils.isEmpty(query)) {
            items.add(ChatAttachAlertAudioLayout.EmptyView.Factory.as(LocaleController.getString(R.string.SearchEmptyViewTitle), LocaleController.formatString(R.string.WalletSearchNoResults, query)));
        }
    }

    private FrameLayout createAddressResult() {
        final FrameLayout row = new FrameLayout(getContext());
        row.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        final boolean rtl = LocaleController.isRTL;
        final int start = rtl ? Gravity.RIGHT : Gravity.LEFT;
        final ImageView avatar = new ImageView(getContext());
        avatar.setImageDrawable(new CombinedDrawable(
                Theme.createCircleDrawable(dp(46), getThemedColor(Theme.key_featuredStickers_addButton)),
                getContext().getResources().getDrawable(R.drawable.menu_gram_24).mutate()
        ));
        row.addView(avatar, LayoutHelper.createFrame(46, 46, start | Gravity.CENTER_VERTICAL, rtl ? 0 : 11, 0, rtl ? 11 : 0, 0));
        final TextView title = addressResultTitle = new TextView(getContext());
        title.setText(getString(R.string.WalletGramWalletAddress));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine();
        title.setGravity(start);
        row.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.TOP, rtl ? 16 : 72, 8, rtl ? 72 : 16, 0));
        addressResultSubtitle = new TextView(getContext());
        addressResultSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addressResultSubtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        addressResultSubtitle.setSingleLine();
        addressResultSubtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        addressResultSubtitle.setGravity(start);
        row.addView(addressResultSubtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 22, Gravity.TOP, rtl ? 16 : 72, 32, rtl ? 72 : 16, 0));
        row.setOnClickListener(v -> {
            if (validAddress == null) return;
            AndroidUtilities.hideKeyboard(recipientEdit);
            selectRecipient(null, validAddress);
        });
        return row;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.User) {
            final TLRPC.User user = (TLRPC.User) item.object;
            DialogsSearchAdapter.putRecentSearch(currentAccount, user.id);
            AndroidUtilities.hideKeyboard(recipientEdit);
            selectRecipient(user, null);
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
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        keyboardInset = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        return super.onInsetsInternal(view, windowInsets);
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        updateBottomLayout();
    }

    private void updateBottomLayout() {
        final int bottom = Math.max(0, keyboardInset - bottomInset);
        if (listView != null) {
            listView.setPadding(0, 0, 0, dp(68) + bottomInset + bottom);
            listView.setClipToPadding(false);
        }
        if (buttonContainer != null) {
            buttonContainer.setPadding(dp(14), dp(10), dp(14), dp(10) + bottomInset);
            final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
            layoutParams.height = dp(68) + bottomInset;
            buttonContainer.setLayoutParams(layoutParams);
            if (lastBottom != bottom) {
                buttonContainer.animate().cancel();
                buttonContainer.animate().translationY(-bottom).setDuration(320)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                lastBottom = bottom;
            }
        }
    }
}

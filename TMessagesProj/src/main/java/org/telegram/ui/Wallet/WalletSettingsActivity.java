/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TonController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BiometricPromtHelper;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import javax.crypto.Cipher;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class WalletSettingsActivity extends BaseFragment {

    public static final int SEND_ACTIVITY_RESULT_CODE = 34;

    public static final int TYPE_SETTINGS = 0;
    public static final int TYPE_SERVER = 1;

    private RecyclerListView listView;
    private Adapter adapter;
    private BaseFragment parentFragment;
    private BiometricPromtHelper biometricPromtHelper;

    private String blockchainName;
    private String blockchainUrl;
    private String blockchainJson;
    private String blockchainConfigFromUrl;
    private int configType;

    private int currentType;

    private int typeHeaderRow;
    private int urlTypeRow;
    private int jsonTypeRow;
    private int typeSectionRow;
    private int fieldHeaderRow;
    private int fieldRow;
    private int fieldSectionRow;
    private int blockchainNameHeaderRow;
    private int blockchainNameRow;
    private int blockchainNameSectionRow;
    private int headerRow;
    private int exportRow;
    private int serverSettingsRow;
    private int changePasscodeRow;
    private int walletSectionRow;
    private int deleteRow;
    private int deleteSectionRow;
    private int rowCount;

    private static final int done_button = 1;

    public class TypeCell extends FrameLayout {

        private TextView textView;
        private ImageView checkImage;
        private boolean needDivider;

        public TypeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 + 48 : 21, 0, LocaleController.isRTL ? 21 : 23, 0));

            checkImage = new ImageView(context);
            checkImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
            checkImage.setImageResource(R.drawable.sticker_added);
            addView(checkImage, LayoutHelper.createFrame(19, 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        public void setValue(String name, boolean checked, boolean divider) {
            textView.setText(name);
            checkImage.setVisibility(checked ? VISIBLE : INVISIBLE);
            needDivider = divider;
        }

        public void setTypeChecked(boolean value) {
            checkImage.setVisibility(value ? VISIBLE : INVISIBLE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public WalletSettingsActivity(int type, BaseFragment parent) {
        super();

        parentFragment = parent;
        currentType = type;

        rowCount = 0;

        typeHeaderRow = -1;
        fieldHeaderRow = -1;
        urlTypeRow = -1;
        jsonTypeRow = -1;
        typeSectionRow = -1;
        fieldRow = -1;
        fieldSectionRow = -1;
        headerRow = -1;
        exportRow = -1;
        changePasscodeRow = -1;
        walletSectionRow = -1;
        deleteRow = -1;
        deleteSectionRow = -1;
        serverSettingsRow = -1;
        blockchainNameHeaderRow = -1;
        blockchainNameRow = -1;
        blockchainNameSectionRow = -1;

        if (currentType == TYPE_SETTINGS) {
            headerRow = rowCount++;
            exportRow = rowCount++;
            if (BuildVars.TON_WALLET_STANDALONE) {
                serverSettingsRow = rowCount++;
            }
            if (getUserConfig().tonPasscodeType != -1) {
                changePasscodeRow = rowCount++;
            }
            walletSectionRow = rowCount++;
            deleteRow = rowCount++;
            deleteSectionRow = rowCount++;
        } else if (currentType == TYPE_SERVER) {
            UserConfig userConfig = getUserConfig();
            blockchainName = userConfig.walletBlockchainName;
            blockchainJson = userConfig.walletConfig;
            blockchainUrl = userConfig.walletConfigUrl;
            configType = userConfig.walletConfigType;

            typeHeaderRow = rowCount++;
            urlTypeRow = rowCount++;
            jsonTypeRow = rowCount++;
            typeSectionRow = rowCount++;
            fieldHeaderRow = rowCount++;
            fieldRow = rowCount++;
            fieldSectionRow = rowCount++;
            blockchainNameHeaderRow = rowCount++;
            blockchainNameRow = rowCount++;
            blockchainNameSectionRow = rowCount++;
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackground));
        actionBar.setTitleColor(Theme.getColor(Theme.key_wallet_whiteText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_wallet_whiteText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackgroundSelector), false);
        if (currentType == TYPE_SETTINGS) {
            actionBar.setTitle(LocaleController.getString("WalletSettings", R.string.WalletSettings));
        } else if (currentType == TYPE_SERVER) {
            actionBar.setTitle(LocaleController.getString("WalletServerSettings", R.string.WalletServerSettings));
            ActionBarMenuItem doneItem = actionBar.createMenu().addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
            doneItem.setContentDescription(LocaleController.getString("Done", R.string.Done));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (!TextUtils.equals(getUserConfig().walletBlockchainName, blockchainName)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("Wallet", R.string.Wallet));
                        builder.setMessage(LocaleController.getString("WalletBlockchainNameWarning", R.string.WalletBlockchainNameWarning));
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.setPositiveButton(LocaleController.getString("WalletContinue", R.string.WalletContinue), (dialog, which) -> saveConfig(true));
                        AlertDialog dialog = builder.create();
                        showDialog(dialog);
                        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                        }
                        return;
                    }
                    saveConfig(true);
                }
            }
        });
        return actionBar;
    }

    private void saveConfig(boolean verify) {
        UserConfig userConfig = getUserConfig();
        boolean needApply = false;
        boolean blockchainNameChanged = !TextUtils.equals(userConfig.walletBlockchainName, blockchainName);
        if (configType != userConfig.walletConfigType || blockchainNameChanged) {
            needApply = true;
        } else if (configType == TonController.CONFIG_TYPE_URL) {
            needApply = !TextUtils.equals(userConfig.walletConfigUrl, blockchainUrl);
        } else if (configType == TonController.CONFIG_TYPE_JSON) {
            needApply = !TextUtils.equals(userConfig.walletBlockchainName, blockchainJson);
        }
        if (needApply) {
            if (configType == TonController.CONFIG_TYPE_JSON) {
                if (TextUtils.isEmpty(blockchainJson)) {
                    return;
                }
                try {
                    JSONObject jsonObject = new JSONObject(blockchainJson);
                } catch (Throwable e) {
                    FileLog.e(e);
                    AlertsCreator.showSimpleAlert(this, LocaleController.getString("WalletError", R.string.WalletError), LocaleController.getString("WalletBlockchainConfigInvalid", R.string.WalletBlockchainConfigInvalid));
                    return;
                }
            } else if (verify && configType == TonController.CONFIG_TYPE_URL) {
                AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                progressDialog.setCanCacnel(false);
                progressDialog.show();
                WalletConfigLoader.loadConfig(blockchainUrl, result -> {
                    progressDialog.dismiss();
                    if (TextUtils.isEmpty(result)) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("WalletError", R.string.WalletError), LocaleController.getString("WalletBlockchainConfigLoadError", R.string.WalletBlockchainConfigLoadError));
                        return;
                    }
                    blockchainConfigFromUrl = result;
                    saveConfig(false);
                });
                return;
            }
        }
        if (needApply) {
            String oldWalletBlockchainName = userConfig.walletBlockchainName;
            String oldWalletConfig = userConfig.walletConfig;
            String oldWalletConfigUrl = userConfig.walletConfigUrl;
            int oldWalletConfigType = userConfig.walletConfigType;
            String oldWalletConfigFromUrl = blockchainConfigFromUrl;

            userConfig.walletBlockchainName = blockchainName;
            userConfig.walletConfig = blockchainJson;
            userConfig.walletConfigUrl = blockchainUrl;
            userConfig.walletConfigType = configType;
            userConfig.walletConfigFromUrl = blockchainConfigFromUrl;

            if (!getTonController().onTonConfigUpdated()) {
                userConfig.walletBlockchainName = oldWalletBlockchainName;
                userConfig.walletConfig = oldWalletConfig;
                userConfig.walletConfigUrl = oldWalletConfigUrl;
                userConfig.walletConfigType = oldWalletConfigType;
                userConfig.walletConfigFromUrl = oldWalletConfigFromUrl;
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("WalletError", R.string.WalletError), LocaleController.getString("WalletBlockchainConfigInvalid", R.string.WalletBlockchainConfigInvalid));
                return;
            }

            userConfig.saveConfig(false);
        }
        if (blockchainNameChanged) {
            doLogout();
            if (parentFragment != null) {
                parentFragment.removeSelfFromStack();
            }
            presentFragment(new WalletCreateActivity(WalletCreateActivity.TYPE_CREATE), true);
        } else {
            finishFragment();
        }
    }

    @Override
    public View createView(Context context) {
        biometricPromtHelper = new BiometricPromtHelper(this);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;
        fragmentView.setFocusableInTouchMode(true);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new Adapter(context));
        listView.setGlowColor(Theme.getColor(Theme.key_wallet_blackBackground));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position == exportRow) {
                switch (getTonController().getKeyProtectionType()) {
                    case TonController.KEY_PROTECTION_TYPE_LOCKSCREEN: {
                        if (Build.VERSION.SDK_INT >= 23) {
                            KeyguardManager keyguardManager = (KeyguardManager) ApplicationLoader.applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
                            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletExportConfirmCredentials", R.string.WalletExportConfirmCredentials));
                            getParentActivity().startActivityForResult(intent, SEND_ACTIVITY_RESULT_CODE);
                        }
                        break;
                    }
                    case TonController.KEY_PROTECTION_TYPE_BIOMETRIC: {
                        biometricPromtHelper.promtWithCipher(getTonController().getCipherForDecrypt(), LocaleController.getString("WalletExportConfirmCredentials", R.string.WalletExportConfirmCredentials), this::doExport);
                        break;
                    }
                    case TonController.KEY_PROTECTION_TYPE_NONE: {
                        presentFragment(new WalletPasscodeActivity(WalletPasscodeActivity.TYPE_PASSCODE_EXPORT));
                        break;
                    }
                }
            } else if (position == deleteRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("WalletDeleteTitle", R.string.WalletDeleteTitle));
                builder.setMessage(LocaleController.getString("WalletDeleteInfo", R.string.WalletDeleteInfo));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
                    doLogout();
                    if (parentFragment != null) {
                        parentFragment.removeSelfFromStack();
                    }
                    if (BuildVars.TON_WALLET_STANDALONE) {
                        presentFragment(new WalletCreateActivity(WalletCreateActivity.TYPE_CREATE), true);
                    } else {
                        finishFragment();
                    }
                });
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            } else if (position == changePasscodeRow) {
                presentFragment(new WalletPasscodeActivity(WalletPasscodeActivity.TYPE_PASSCODE_CHANGE));
            } else if (position == urlTypeRow || position == jsonTypeRow) {
                configType = position == urlTypeRow ? TonController.CONFIG_TYPE_URL : TonController.CONFIG_TYPE_JSON;
                adapter.notifyDataSetChanged();
            } else if (position == serverSettingsRow) {
                presentFragment(new WalletSettingsActivity(TYPE_SERVER, WalletSettingsActivity.this));
            }
        });

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (biometricPromtHelper != null) {
            biometricPromtHelper.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void removeSelfFromStack() {
        super.removeSelfFromStack();
        if (parentFragment != null) {
            parentFragment.removeSelfFromStack();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == SEND_ACTIVITY_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                doExport(null);
            }
        }
    }

    private void doLogout() {
        getTonController().cleanup();
        UserConfig userConfig = getUserConfig();
        userConfig.clearTonConfig();
        userConfig.saveConfig(false);
    }

    private void doExport(Cipher cipher) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
        getTonController().getSecretWords(null, cipher, (words) -> {
            progressDialog.dismiss();
            WalletCreateActivity fragment = new WalletCreateActivity(WalletCreateActivity.TYPE_24_WORDS);
            fragment.setSecretWords(words);
            presentFragment(fragment);
        }, (text, error) -> {
            progressDialog.dismiss();
            AlertsCreator.showSimpleAlert(this, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + (error != null ? error.message : text));
        });
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public Adapter(Context c) {
            context = c;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 1: {
                    view = new TextSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 2: {
                    view = new ShadowSectionCell(context);
                    break;
                }
                case 3: {
                    view = new TextInfoPrivacyCell(context);
                    break;
                }
                case 4: {
                    view = new TypeCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 5:
                default: {
                    PollEditTextCell cell = new PollEditTextCell(context, null);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    EditTextBoldCursor editText = cell.getTextView();
                    editText.setPadding(0, AndroidUtilities.dp(14), AndroidUtilities.dp(37), AndroidUtilities.dp(14));
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            Integer tag = (Integer) cell.getTag();
                            if (tag == null) {
                                return;
                            }
                            if (tag == fieldRow) {
                                if (configType == TonController.CONFIG_TYPE_URL) {
                                    blockchainUrl = s.toString();
                                } else {
                                    blockchainJson = s.toString();
                                }
                            } else if (tag == blockchainNameRow) {
                                blockchainName = s.toString();
                            }
                        }
                    });
                    view = cell;
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        cell.setText(LocaleController.getString("Wallet", R.string.Wallet));
                    } else if (position == blockchainNameHeaderRow) {
                        cell.setText(LocaleController.getString("WalletBlockchainName", R.string.WalletBlockchainName));
                    } else if (position == typeHeaderRow) {
                        cell.setText(LocaleController.getString("WalletConfigType", R.string.WalletConfigType));
                    } else if (position == fieldHeaderRow) {
                        if (configType == TonController.CONFIG_TYPE_URL) {
                            cell.setText(LocaleController.getString("WalletConfigTypeUrlHeader", R.string.WalletConfigTypeUrlHeader));
                        } else {
                            cell.setText(LocaleController.getString("WalletConfigTypeJsonHeader", R.string.WalletConfigTypeJsonHeader));
                        }
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == exportRow) {
                        cell.setText(LocaleController.getString("WalletExport", R.string.WalletExport), changePasscodeRow != -1 || serverSettingsRow != -1);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    } else if (position == changePasscodeRow) {
                        cell.setText(LocaleController.getString("WalletChangePasscode", R.string.WalletChangePasscode), false);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    } else if (position == deleteRow) {
                        cell.setText(LocaleController.getString("WalletDelete", R.string.WalletDelete), false);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                        cell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                    } else if (position == serverSettingsRow) {
                        cell.setText(LocaleController.getString("WalletServerSettings", R.string.WalletServerSettings), changePasscodeRow != -1);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    }
                    break;
                }
                case 2: {
                    if (position == walletSectionRow || position == fieldSectionRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == deleteSectionRow) {
                        cell.setText(LocaleController.getString("WalletDeleteInfo", R.string.WalletDeleteInfo));
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == typeSectionRow) {
                        cell.setText(LocaleController.getString("WalletConfigTypeInfo", R.string.WalletConfigTypeInfo));
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == blockchainNameSectionRow) {
                        cell.setText(LocaleController.getString("WalletBlockchainNameInfo", R.string.WalletBlockchainNameInfo));
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 4: {
                    TypeCell cell = (TypeCell) holder.itemView;
                    if (position == urlTypeRow) {
                        cell.setValue(LocaleController.getString("WalletConfigTypeUrl", R.string.WalletConfigTypeUrl), configType == TonController.CONFIG_TYPE_URL, true);
                    } else if (position == jsonTypeRow) {
                        cell.setValue(LocaleController.getString("WalletConfigTypeJson", R.string.WalletConfigTypeJson), configType == TonController.CONFIG_TYPE_JSON, false);
                    }
                    break;
                }
                case 5: {
                    PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                    textCell.setTag(null);
                    if (position == blockchainNameRow) {
                        textCell.setTextAndHint(blockchainName, LocaleController.getString("WalletBlockchainNameHint", R.string.WalletBlockchainNameHint), false);
                    } else if (position == fieldRow) {
                        if (configType == TonController.CONFIG_TYPE_URL) {
                            textCell.setTextAndHint(blockchainUrl, LocaleController.getString("WalletConfigTypeUrlHint", R.string.WalletConfigTypeUrlHint), false);
                        } else {
                            textCell.setTextAndHint(blockchainJson, LocaleController.getString("WalletConfigTypeJsonHint", R.string.WalletConfigTypeJsonHint), false);
                        }
                    }
                    textCell.setTag(position);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == blockchainNameHeaderRow || position == typeHeaderRow || position == fieldHeaderRow) {
                return 0;
            } else if (position == exportRow || position == changePasscodeRow || position == deleteRow || position == serverSettingsRow) {
                return 1;
            } else if (position == walletSectionRow || position == fieldSectionRow) {
                return 2;
            } else if (position == jsonTypeRow || position == urlTypeRow) {
                return 4;
            } else if (position == fieldRow || position == blockchainNameRow) {
                return 5;
            } else {
                return 3;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_wallet_blackBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_wallet_blackBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_wallet_whiteText),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_wallet_whiteText),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_wallet_blackBackgroundSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(listView, 0, new Class[]{TypeCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TypeCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon)
        };
    }
}

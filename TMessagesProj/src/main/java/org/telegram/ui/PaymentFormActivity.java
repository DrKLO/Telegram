/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.FullWallet;
import com.google.android.gms.wallet.FullWalletRequest;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentMethodTokenizationType;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.fragment.WalletFragment;
import com.google.android.gms.wallet.fragment.WalletFragmentInitParams;
import com.google.android.gms.wallet.fragment.WalletFragmentMode;
import com.google.android.gms.wallet.fragment.WalletFragmentOptions;
import com.google.android.gms.wallet.fragment.WalletFragmentStyle;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.exception.APIConnectionException;
import com.stripe.android.exception.APIException;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.net.StripeApiHandler;
import com.stripe.android.net.TokenParser;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PaymentInfoCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextPriceCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class PaymentFormActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final static int FIELD_CARD = 0;
    private final static int FIELD_EXPIRE_DATE = 1;
    private final static int FIELD_CARDNAME = 2;
    private final static int FIELD_CVV = 3;
    private final static int FIELD_CARD_COUNTRY = 4;
    private final static int FIELD_CARD_POSTCODE = 5;
    private final static int FIELDS_COUNT_CARD = 6;

    private final static int FIELD_STREET1 = 0;
    private final static int FIELD_STREET2 = 1;
    private final static int FIELD_CITY = 2;
    private final static int FIELD_STATE = 3;
    private final static int FIELD_COUNTRY = 4;
    private final static int FIELD_POSTCODE = 5;
    private final static int FIELD_NAME = 6;
    private final static int FIELD_EMAIL = 7;
    private final static int FIELD_PHONECODE = 8;
    private final static int FIELD_PHONE = 9;
    private final static int FIELDS_COUNT_ADDRESS = 10;

    private final static int FIELD_SAVEDCARD = 0;
    private final static int FIELD_SAVEDPASSWORD = 1;
    private final static int FIELDS_COUNT_SAVEDCARD = 2;

    private final static int FIELD_ENTERPASSWORD = 0;
    private final static int FIELD_REENTERPASSWORD = 1;
    private final static int FIELD_ENTERPASSWORDEMAIL = 2;
    private final static int FIELDS_COUNT_PASSWORD = 3;

    private ArrayList<String> countriesArray = new ArrayList<>();
    private HashMap<String, String> countriesMap = new HashMap<>();
    private HashMap<String, String> codesMap = new HashMap<>();
    private HashMap<String, String> phoneFormatMap = new HashMap<>();

    private GoogleApiClient googleApiClient;

    private EditTextBoldCursor[] inputFields;
    private RadioCell[] radioCells;
    private ActionBarMenuItem doneItem;
    private ContextProgressView progressView;
    private ContextProgressView progressViewButton;
    private AnimatorSet doneItemAnimation;
    private WebView webView;
    private ScrollView scrollView;

    private TextView textView;
    private HeaderCell headerCell[] = new HeaderCell[3];
    private ArrayList<View> dividers = new ArrayList<>();
    private ShadowSectionCell sectionCell[] = new ShadowSectionCell[3];
    private TextCheckCell checkCell1;
    private TextInfoPrivacyCell bottomCell[] = new TextInfoPrivacyCell[3];
    private TextSettingsCell settingsCell1;
    private FrameLayout androidPayContainer;
    private LinearLayout linearLayout2;

    private PaymentFormActivityDelegate delegate;

    private TextView payTextView;
    private FrameLayout bottomLayout;
    private PaymentInfoCell paymentInfoCell;
    private TextDetailSettingsCell detailSettingsCell[] = new TextDetailSettingsCell[7];

    private TLRPC.account_Password currentPassword;
    private boolean waitingForEmail;
    private Runnable shortPollRunnable;
    private boolean loadingPasswordInfo;
    private PaymentFormActivity passwordFragment;

    private boolean need_card_country;
    private boolean need_card_postcode;
    private boolean need_card_name;
    private String stripeApiKey;

    private TLRPC.User botUser;

    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;
    private boolean ignoreOnCardChange;

    private String currentBotName;
    private String currentItemName;

    private int currentStep;
    private boolean passwordOk;
    private String paymentJson;
    private String cardName;
    private boolean webviewLoading;
    private String countryName;
    private String totalPriceDecimal;
    private TLRPC.TL_payments_paymentForm paymentForm;
    private TLRPC.TL_payments_validatedRequestedInfo requestedInfo;
    private TLRPC.TL_shippingOption shippingOption;
    private TLRPC.TL_payments_validateRequestedInfo validateRequest;
    private TLRPC.TL_inputPaymentCredentialsAndroidPay androidPayCredentials;
    private String androidPayPublicKey;
    private int androidPayBackgroundColor;
    private boolean androidPayBlackTheme;
    private MessageObject messageObject;
    private boolean donePressed;
    private boolean canceled;

    private boolean isWebView;

    private boolean saveShippingInfo;
    private boolean saveCardInfo;

    private final static int done_button = 1;

    private static final int LOAD_MASKED_WALLET_REQUEST_CODE = 1000;
    private static final int LOAD_FULL_WALLET_REQUEST_CODE = 1001;
    private final static int fragment_container_id = 4000;

    private interface PaymentFormActivityDelegate {
        boolean didSelectNewCard(String tokenJson, String card, boolean saveCard, TLRPC.TL_inputPaymentCredentialsAndroidPay androidPay);
        void onFragmentDestroyed();
        void currentPasswordUpdated(TLRPC.account_Password password);
    }

    private class TelegramWebviewProxy {
        @JavascriptInterface
        public void postEvent(final String eventName, final String eventData) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (eventName.equals("payment_form_submit")) {
                        try {
                            JSONObject jsonObject = new JSONObject(eventData);
                            JSONObject response = jsonObject.getJSONObject("credentials");
                            paymentJson = response.toString();
                            cardName = jsonObject.getString("title");
                        } catch (Throwable e) {
                            paymentJson = eventData;
                            FileLog.e(e);
                        }
                        goToNextStep();
                    }
                }
            });
        }
    }

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result = super.onTouchEvent(widget, buffer, event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    public class LinkSpan extends ClickableSpan {
        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }

        @Override
        public void onClick(View widget) {
            presentFragment(new TwoStepVerificationActivity(0));
        }
    }

    public PaymentFormActivity(MessageObject message, TLRPC.TL_payments_paymentReceipt receipt) {
        currentStep = 5;
        paymentForm = new TLRPC.TL_payments_paymentForm();
        paymentForm.bot_id = receipt.bot_id;
        paymentForm.invoice = receipt.invoice;
        paymentForm.provider_id = receipt.provider_id;
        paymentForm.users = receipt.users;
        shippingOption = receipt.shipping;
        messageObject = message;
        botUser = MessagesController.getInstance().getUser(receipt.bot_id);
        if (botUser != null) {
            currentBotName = botUser.first_name;
        } else {
            currentBotName = "";
        }
        currentItemName = message.messageOwner.media.title;
        if (receipt.info != null) {
            validateRequest = new TLRPC.TL_payments_validateRequestedInfo();
            validateRequest.info = receipt.info;
        }
        cardName = receipt.credentials_title;
    }

    public PaymentFormActivity(TLRPC.TL_payments_paymentForm form, MessageObject message) {
        int step;
        if (form.invoice.shipping_address_requested || form.invoice.email_requested || form.invoice.name_requested || form.invoice.phone_requested) {
            step = 0;
        } else if (form.saved_credentials != null) {
            if (UserConfig.tmpPassword != null) {
                if (UserConfig.tmpPassword.valid_until < ConnectionsManager.getInstance().getCurrentTime() + 60) {
                    UserConfig.tmpPassword = null;
                    UserConfig.saveConfig(false);
                }
            }
            if (UserConfig.tmpPassword != null) {
                step = 4;
            } else {
                step = 3;
            }
        } else {
            step = 2;
        }
        init(form, message, step, null, null, null, null, null, false, null);
    }

    private PaymentFormActivity(TLRPC.TL_payments_paymentForm form, MessageObject message, int step, TLRPC.TL_payments_validatedRequestedInfo validatedRequestedInfo, TLRPC.TL_shippingOption shipping, String tokenJson, String card, TLRPC.TL_payments_validateRequestedInfo request, boolean saveCard, TLRPC.TL_inputPaymentCredentialsAndroidPay androidPay) {
        init(form, message, step, validatedRequestedInfo, shipping, tokenJson, card, request, saveCard, androidPay);
    }

    private void setCurrentPassword(TLRPC.account_Password password) {
        if (password instanceof TLRPC.TL_account_password) {
            if (getParentActivity() == null) {
                return;
            }
            goToNextStep();
        } else {
            currentPassword = password;
            if (currentPassword != null) {
                waitingForEmail = currentPassword.email_unconfirmed_pattern.length() > 0;
            }
            updatePasswordFields();
        }
    }

    private void setDelegate(PaymentFormActivityDelegate paymentFormActivityDelegate) {
        delegate = paymentFormActivityDelegate;
    }

    private void init(TLRPC.TL_payments_paymentForm form, MessageObject message, int step, TLRPC.TL_payments_validatedRequestedInfo validatedRequestedInfo, TLRPC.TL_shippingOption shipping, String tokenJson, String card, TLRPC.TL_payments_validateRequestedInfo request, boolean saveCard, TLRPC.TL_inputPaymentCredentialsAndroidPay androidPay) {
        currentStep = step;
        paymentJson = tokenJson;
        androidPayCredentials = androidPay;
        requestedInfo = validatedRequestedInfo;
        paymentForm = form;
        shippingOption = shipping;
        messageObject = message;
        saveCardInfo = saveCard;
        isWebView = !"stripe".equals(paymentForm.native_provider);
        botUser = MessagesController.getInstance().getUser(form.bot_id);
        if (botUser != null) {
            currentBotName = botUser.first_name;
        } else {
            currentBotName = "";
        }
        currentItemName = message.messageOwner.media.title;
        validateRequest = request;
        saveShippingInfo = true;
        if (saveCard) {
            saveCardInfo = saveCard;
        } else {
            saveCardInfo = paymentForm.saved_credentials != null;
        }
        if (card == null) {
            if (form.saved_credentials != null) {
                cardName = form.saved_credentials.title;
            }
        } else {
            cardName = card;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                if ((currentStep == 2 || currentStep == 6) && !paymentForm.invoice.test) {
                    getParentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                } else if (UserConfig.passcodeHash.length() == 0 || UserConfig.allowScreenCapture) {
                    getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    public void onPause() {
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public View createView(Context context) {
        if (currentStep == 0) {
            actionBar.setTitle(LocaleController.getString("PaymentShippingInfo", R.string.PaymentShippingInfo));
        } else if (currentStep == 1) {
            actionBar.setTitle(LocaleController.getString("PaymentShippingMethod", R.string.PaymentShippingMethod));
        } else if (currentStep == 2) {
            actionBar.setTitle(LocaleController.getString("PaymentCardInfo", R.string.PaymentCardInfo));
        } else if (currentStep == 3) {
            actionBar.setTitle(LocaleController.getString("PaymentCardInfo", R.string.PaymentCardInfo));
        } else if (currentStep == 4) {
            if (paymentForm.invoice.test) {
                actionBar.setTitle("Test " + LocaleController.getString("PaymentCheckout", R.string.PaymentCheckout));
            } else {
                actionBar.setTitle(LocaleController.getString("PaymentCheckout", R.string.PaymentCheckout));
            }
        } else if (currentStep == 5) {
            if (paymentForm.invoice.test) {
                actionBar.setTitle("Test " + LocaleController.getString("PaymentReceipt", R.string.PaymentReceipt));
            } else {
                actionBar.setTitle(LocaleController.getString("PaymentReceipt", R.string.PaymentReceipt));
            }
        } else if (currentStep == 6) {
            actionBar.setTitle(LocaleController.getString("PaymentPassword", R.string.PaymentPassword));
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (donePressed) {
                        return;
                    }
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    if (currentStep != 3) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    if (currentStep == 0) {
                        setDonePressed(true);
                        sendForm();
                    } else if (currentStep == 1) {
                        for (int a = 0; a < radioCells.length; a++) {
                            if (radioCells[a].isChecked()) {
                                shippingOption = requestedInfo.shipping_options.get(a);
                                break;
                            }
                        }
                        goToNextStep();
                    } else if (currentStep == 2) {
                        sendCardData();
                    } else if (currentStep == 3) {
                        checkPassword();
                    } else if (currentStep == 6) {
                        sendSavePassword(false);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        if (currentStep == 0 || currentStep == 1 || currentStep == 2 || currentStep == 3 || currentStep == 4 || currentStep == 6) {
            doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
            progressView = new ContextProgressView(context, 1);
            doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            progressView.setVisibility(View.INVISIBLE);
        }

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, currentStep == 4 ? 48 : 0));

        linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout2, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (currentStep == 0) {
            HashMap<String, String> languageMap = new HashMap<>();
            HashMap<String, String> countryMap = new HashMap<>();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split(";");
                    countriesArray.add(0, args[2]);
                    countriesMap.put(args[2], args[0]);
                    codesMap.put(args[0], args[2]);
                    countryMap.put(args[1], args[2]);
                    if (args.length > 3) {
                        phoneFormatMap.put(args[0], args[3]);
                    }
                    languageMap.put(args[1], args[2]);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e(e);
            }

            Collections.sort(countriesArray, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return lhs.compareTo(rhs);
                }
            });

            inputFields = new EditTextBoldCursor[FIELDS_COUNT_ADDRESS];
            for (int a = 0; a < FIELDS_COUNT_ADDRESS; a++) {
                if (a == FIELD_STREET1) {
                    headerCell[0] = new HeaderCell(context);
                    headerCell[0].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell[0].setText(LocaleController.getString("PaymentShippingAddress", R.string.PaymentShippingAddress));
                    linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else if (a == FIELD_NAME) {
                    sectionCell[0] = new ShadowSectionCell(context);
                    linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    headerCell[1] = new HeaderCell(context);
                    headerCell[1].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell[1].setText(LocaleController.getString("PaymentShippingReceiver", R.string.PaymentShippingReceiver));
                    linearLayout2.addView(headerCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                ViewGroup container;
                if (a == FIELD_PHONECODE) {
                    container = new LinearLayout(context);
                    ((LinearLayout) container).setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                } else if (a == FIELD_PHONE) {
                    container = (ViewGroup) inputFields[FIELD_PHONECODE].getParent();
                } else {
                    container = new FrameLayout(context);
                    linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    boolean allowDivider = a != FIELD_POSTCODE && a != FIELD_PHONE;
                    if (allowDivider) {
                        if (a == FIELD_EMAIL && !paymentForm.invoice.phone_requested) {
                            allowDivider = false;
                        } else if (a == FIELD_NAME && !paymentForm.invoice.phone_requested && !paymentForm.invoice.email_requested) {
                            allowDivider = false;
                        }
                    }
                    if (allowDivider) {
                        View divider = new View(context);
                        dividers.add(divider);
                        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                        container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
                    }
                }

                if (a == FIELD_PHONE) {
                    inputFields[a] = new HintEditText(context);
                } else {
                    inputFields[a] = new EditTextBoldCursor(context);
                }
                inputFields[a].setTag(a);
                inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setBackgroundDrawable(null);
                inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                inputFields[a].setCursorWidth(1.5f);
                if (a == FIELD_COUNTRY) {
                    inputFields[a].setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (getParentActivity() == null) {
                                return false;
                            }
                            if (event.getAction() == MotionEvent.ACTION_UP) {
                                CountrySelectActivity fragment = new CountrySelectActivity(false);
                                fragment.setCountrySelectActivityDelegate(new CountrySelectActivity.CountrySelectActivityDelegate() {
                                    @Override
                                    public void didSelectCountry(String name, String shortName) {
                                        inputFields[FIELD_COUNTRY].setText(name);
                                        countryName = shortName;
                                    }
                                });
                                presentFragment(fragment);
                            }
                            return true;
                        }
                    });
                    inputFields[a].setInputType(0);
                }
                if (a == FIELD_PHONE || a == FIELD_PHONECODE) {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_PHONE);
                } else if (a == FIELD_EMAIL) {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT);
                } else {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                }
                inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                switch (a) {
                    case FIELD_NAME:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingName", R.string.PaymentShippingName));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.name != null) {
                            inputFields[a].setText(paymentForm.saved_info.name);
                        }
                        break;
                    case FIELD_EMAIL:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingEmailPlaceholder", R.string.PaymentShippingEmailPlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.email != null) {
                            inputFields[a].setText(paymentForm.saved_info.email);
                        }
                        break;
                    case FIELD_STREET1:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingAddress1Placeholder", R.string.PaymentShippingAddress1Placeholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.street_line1);
                        }
                        break;
                    case FIELD_STREET2:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingAddress2Placeholder", R.string.PaymentShippingAddress2Placeholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.street_line2);
                        }
                        break;
                    case FIELD_CITY:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingCityPlaceholder", R.string.PaymentShippingCityPlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.city);
                        }
                        break;
                    case FIELD_STATE:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingStatePlaceholder", R.string.PaymentShippingStatePlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.state);
                        }
                        break;
                    case FIELD_COUNTRY:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingCountry", R.string.PaymentShippingCountry));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            String value = countryMap.get(paymentForm.saved_info.shipping_address.country_iso2);
                            countryName = paymentForm.saved_info.shipping_address.country_iso2;
                            inputFields[a].setText(value != null ? value : countryName);
                        }
                        break;
                    case FIELD_POSTCODE:
                        inputFields[a].setHint(LocaleController.getString("PaymentShippingZipPlaceholder", R.string.PaymentShippingZipPlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.post_code);
                        }
                        break;
                }
                inputFields[a].setSelection(inputFields[a].length());

                if (a == FIELD_PHONECODE) {
                    textView = new TextView(context);
                    textView.setText("+");
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    container.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 17, 12, 0, 6));

                    inputFields[a].setPadding(AndroidUtilities.dp(10), 0, 0, 0);
                    inputFields[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    InputFilter[] inputFilters = new InputFilter[1];
                    inputFilters[0] = new InputFilter.LengthFilter(5);
                    inputFields[a].setFilters(inputFilters);
                    container.addView(inputFields[a], LayoutHelper.createLinear(55, LayoutHelper.WRAP_CONTENT, 0, 12, 16, 6));
                    inputFields[a].addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                            if (ignoreOnTextChange) {
                                return;
                            }
                            ignoreOnTextChange = true;
                            String text = PhoneFormat.stripExceptNumbers(inputFields[FIELD_PHONECODE].getText().toString());
                            inputFields[FIELD_PHONECODE].setText(text);
                            HintEditText phoneField = (HintEditText) inputFields[FIELD_PHONE];
                            if (text.length() == 0) {
                                phoneField.setHintText(null);
                                phoneField.setHint(LocaleController.getString("PaymentShippingPhoneNumber", R.string.PaymentShippingPhoneNumber));
                            } else {
                                String country;
                                boolean ok = false;
                                String textToSet = null;
                                if (text.length() > 4) {
                                    for (int a = 4; a >= 1; a--) {
                                        String sub = text.substring(0, a);
                                        country = codesMap.get(sub);
                                        if (country != null) {
                                            ok = true;
                                            textToSet = text.substring(a, text.length()) + inputFields[FIELD_PHONE].getText().toString();
                                            inputFields[FIELD_PHONECODE].setText(text = sub);
                                            break;
                                        }
                                    }
                                    if (!ok) {
                                        textToSet = text.substring(1, text.length()) + inputFields[FIELD_PHONE].getText().toString();
                                        inputFields[FIELD_PHONECODE].setText(text = text.substring(0, 1));
                                    }
                                }
                                country = codesMap.get(text);
                                boolean set = false;
                                if (country != null) {
                                    int index = countriesArray.indexOf(country);
                                    if (index != -1) {
                                        String hint = phoneFormatMap.get(text);
                                        if (hint != null) {
                                            set = true;
                                            phoneField.setHintText(hint.replace('X', '–'));
                                            phoneField.setHint(null);
                                        }
                                    }
                                }
                                if (!set) {
                                    phoneField.setHintText(null);
                                    phoneField.setHint(LocaleController.getString("PaymentShippingPhoneNumber", R.string.PaymentShippingPhoneNumber));
                                }
                                if (!ok) {
                                    inputFields[FIELD_PHONECODE].setSelection(inputFields[FIELD_PHONECODE].getText().length());
                                }
                                if (textToSet != null) {
                                    phoneField.requestFocus();
                                    phoneField.setText(textToSet);
                                    phoneField.setSelection(phoneField.length());
                                }
                            }
                            ignoreOnTextChange = false;
                        }
                    });
                } else if (a == FIELD_PHONE) {
                    inputFields[a].setPadding(0, 0, 0, 0);
                    inputFields[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    container.addView(inputFields[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 17, 6));
                    inputFields[a].addTextChangedListener(new TextWatcher() {
                        private int characterAction = -1;
                        private int actionPosition;

                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            if (count == 0 && after == 1) {
                                characterAction = 1;
                            } else if (count == 1 && after == 0) {
                                if (s.charAt(start) == ' ' && start > 0) {
                                    characterAction = 3;
                                    actionPosition = start - 1;
                                } else {
                                    characterAction = 2;
                                }
                            } else {
                                characterAction = -1;
                            }
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreOnPhoneChange) {
                                return;
                            }
                            HintEditText phoneField = (HintEditText) inputFields[FIELD_PHONE];
                            int start = phoneField.getSelectionStart();
                            String phoneChars = "0123456789";
                            String str = phoneField.getText().toString();
                            if (characterAction == 3) {
                                str = str.substring(0, actionPosition) + str.substring(actionPosition + 1, str.length());
                                start--;
                            }
                            StringBuilder builder = new StringBuilder(str.length());
                            for (int a = 0; a < str.length(); a++) {
                                String ch = str.substring(a, a + 1);
                                if (phoneChars.contains(ch)) {
                                    builder.append(ch);
                                }
                            }
                            ignoreOnPhoneChange = true;
                            String hint = phoneField.getHintText();
                            if (hint != null) {
                                for (int a = 0; a < builder.length(); a++) {
                                    if (a < hint.length()) {
                                        if (hint.charAt(a) == ' ') {
                                            builder.insert(a, ' ');
                                            a++;
                                            if (start == a && characterAction != 2 && characterAction != 3) {
                                                start++;
                                            }
                                        }
                                    } else {
                                        builder.insert(a, ' ');
                                        if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                            start++;
                                        }
                                        break;
                                    }
                                }
                            }
                            phoneField.setText(builder);
                            if (start >= 0) {
                                phoneField.setSelection(start <= phoneField.length() ? start : phoneField.length());
                            }
                            phoneField.onTextChange();
                            ignoreOnPhoneChange = false;
                        }
                    });
                } else {
                    inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                    inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 17, 12, 17, 6));
                }

                inputFields[a].setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        if (i == EditorInfo.IME_ACTION_NEXT) {
                            int num = (Integer) textView.getTag();
                            while (num + 1 < inputFields.length) {
                                num++;
                                if (num != FIELD_COUNTRY && ((View) inputFields[num].getParent()).getVisibility() == View.VISIBLE) {
                                    inputFields[num].requestFocus();
                                    break;
                                }
                            }
                            return true;
                        } else if (i == EditorInfo.IME_ACTION_DONE) {
                            doneItem.performClick();
                            return true;
                        }
                        return false;
                    }
                });
                if (a == FIELD_PHONE) {
                    if (paymentForm.invoice.email_to_provider || paymentForm.invoice.phone_to_provider) {
                        TLRPC.User providerUser = null;
                        for (int b = 0; b < paymentForm.users.size(); b++) {
                            TLRPC.User user = paymentForm.users.get(b);
                            if (user.id == paymentForm.provider_id) {
                                providerUser = user;
                            }
                        }
                        final String providerName;
                        if (providerUser != null) {
                            providerName = ContactsController.formatName(providerUser.first_name, providerUser.last_name);
                        } else {
                            providerName = "";
                        }

                        bottomCell[1] = new TextInfoPrivacyCell(context);
                        bottomCell[1].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        linearLayout2.addView(bottomCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        if (paymentForm.invoice.email_to_provider && paymentForm.invoice.phone_to_provider) {
                            bottomCell[1].setText(LocaleController.formatString("PaymentPhoneEmailToProvider", R.string.PaymentPhoneEmailToProvider, providerName));
                        } else if (paymentForm.invoice.email_to_provider) {
                            bottomCell[1].setText(LocaleController.formatString("PaymentEmailToProvider", R.string.PaymentPhoneEmailToProvider, providerName));
                        } else {
                            bottomCell[1].setText(LocaleController.formatString("PaymentPhoneToProvider", R.string.PaymentPhoneEmailToProvider, providerName));
                        }
                    } else {
                        sectionCell[1] = new ShadowSectionCell(context);
                        linearLayout2.addView(sectionCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    checkCell1 = new TextCheckCell(context);
                    checkCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                    checkCell1.setTextAndCheck(LocaleController.getString("PaymentShippingSave", R.string.PaymentShippingSave), saveShippingInfo, false);
                    linearLayout2.addView(checkCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    checkCell1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            saveShippingInfo = !saveShippingInfo;
                            checkCell1.setChecked(saveShippingInfo);
                        }
                    });

                    bottomCell[0] = new TextInfoPrivacyCell(context);
                    bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    bottomCell[0].setText(LocaleController.getString("PaymentShippingSaveInfo", R.string.PaymentShippingSaveInfo));
                    linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            }

            if (!paymentForm.invoice.name_requested) {
                ((ViewGroup) inputFields[FIELD_NAME].getParent()).setVisibility(View.GONE);
            }
            if (!paymentForm.invoice.phone_requested) {
                ((ViewGroup) inputFields[FIELD_PHONECODE].getParent()).setVisibility(View.GONE);
            }
            if (!paymentForm.invoice.email_requested) {
                ((ViewGroup) inputFields[FIELD_EMAIL].getParent()).setVisibility(View.GONE);
            }

            if (paymentForm.invoice.phone_requested) {
                inputFields[FIELD_PHONE].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            } else if (paymentForm.invoice.email_requested) {
                inputFields[FIELD_EMAIL].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            } else if (paymentForm.invoice.name_requested) {
                inputFields[FIELD_NAME].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            } else {
                inputFields[FIELD_POSTCODE].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            }

            if (sectionCell[1] != null) {
                sectionCell[1].setVisibility(paymentForm.invoice.name_requested || paymentForm.invoice.phone_requested || paymentForm.invoice.email_requested ? View.VISIBLE : View.GONE);
            } else if (bottomCell[1] != null) {
                bottomCell[1].setVisibility(paymentForm.invoice.name_requested || paymentForm.invoice.phone_requested || paymentForm.invoice.email_requested ? View.VISIBLE : View.GONE);
            }
            headerCell[1].setVisibility(paymentForm.invoice.name_requested || paymentForm.invoice.phone_requested || paymentForm.invoice.email_requested ? View.VISIBLE : View.GONE);
            if (!paymentForm.invoice.shipping_address_requested) {
                headerCell[0].setVisibility(View.GONE);
                sectionCell[0].setVisibility(View.GONE);
                ((ViewGroup) inputFields[FIELD_STREET1].getParent()).setVisibility(View.GONE);
                ((ViewGroup) inputFields[FIELD_STREET2].getParent()).setVisibility(View.GONE);
                ((ViewGroup) inputFields[FIELD_CITY].getParent()).setVisibility(View.GONE);
                ((ViewGroup) inputFields[FIELD_STATE].getParent()).setVisibility(View.GONE);
                ((ViewGroup) inputFields[FIELD_COUNTRY].getParent()).setVisibility(View.GONE);
                ((ViewGroup) inputFields[FIELD_POSTCODE].getParent()).setVisibility(View.GONE);
            }

            if (paymentForm.saved_info != null && !TextUtils.isEmpty(paymentForm.saved_info.phone)) {
                fillNumber(paymentForm.saved_info.phone);
            } else {
                fillNumber(null);
            }

            if (inputFields[FIELD_PHONECODE].length() == 0 && (paymentForm.invoice.phone_requested && (paymentForm.saved_info == null || TextUtils.isEmpty(paymentForm.saved_info.phone)))) {
                String country = null;

                try {
                    TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                    if (telephonyManager != null) {
                        country = telephonyManager.getSimCountryIso().toUpperCase();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                if (country != null) {
                    String countryName = languageMap.get(country);
                    if (countryName != null) {
                        int index = countriesArray.indexOf(countryName);
                        if (index != -1) {
                            inputFields[FIELD_PHONECODE].setText(countriesMap.get(countryName));
                        }
                    }
                }
            }
        } else if (currentStep == 2) {
            if (paymentForm.native_params != null) {
                try {
                    JSONObject jsonObject = new JSONObject(paymentForm.native_params.data);
                    try {
                        String androidPayKey = jsonObject.getString("android_pay_public_key");
                        if (!TextUtils.isEmpty(androidPayKey)) {
                            androidPayPublicKey = androidPayKey;
                        }
                    } catch (Exception e) {
                        androidPayPublicKey = null;
                    }
                    try {
                        androidPayBackgroundColor = jsonObject.getInt("android_pay_bgcolor") | 0xff000000;
                    } catch (Exception e) {
                        androidPayBackgroundColor = 0xffffffff;
                    }
                    try {
                        androidPayBlackTheme = jsonObject.getBoolean("android_pay_inverse");
                    } catch (Exception e) {
                        androidPayBlackTheme = false;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (isWebView) {
                if (androidPayPublicKey != null) {
                    initAndroidPay(context);
                }
                androidPayContainer = new FrameLayout(context);
                androidPayContainer.setId(fragment_container_id);
                androidPayContainer.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                androidPayContainer.setVisibility(View.GONE);
                linearLayout2.addView(androidPayContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                webviewLoading = true;
                showEditDoneProgress(true, true);
                progressView.setVisibility(View.VISIBLE);
                doneItem.setEnabled(false);
                doneItem.getImageView().setVisibility(View.INVISIBLE);
                webView = new WebView(context) {
                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return super.onTouchEvent(event);
                    }
                };
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);

                if (Build.VERSION.SDK_INT >= 21) {
                    webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.setAcceptThirdPartyCookies(webView, true);
                }
                webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onLoadResource(WebView view, String url) {
                        super.onLoadResource(view, url);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        webviewLoading = false;
                        showEditDoneProgress(true, false);
                        updateSavePaymentField();
                    }
                });

                linearLayout2.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                sectionCell[2] = new ShadowSectionCell(context);
                linearLayout2.addView(sectionCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                checkCell1 = new TextCheckCell(context);
                checkCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                checkCell1.setTextAndCheck(LocaleController.getString("PaymentCardSavePaymentInformation", R.string.PaymentCardSavePaymentInformation), saveCardInfo, false);
                linearLayout2.addView(checkCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                checkCell1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        saveCardInfo = !saveCardInfo;
                        checkCell1.setChecked(saveCardInfo);
                    }
                });

                bottomCell[0] = new TextInfoPrivacyCell(context);
                bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                updateSavePaymentField();
                linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            } else {
                if (paymentForm.native_params != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(paymentForm.native_params.data);
                        try {
                            need_card_country = jsonObject.getBoolean("need_country");
                        } catch (Exception e) {
                            need_card_country = false;
                        }
                        try {
                            need_card_postcode = jsonObject.getBoolean("need_zip");
                        } catch (Exception e) {
                            need_card_postcode = false;
                        }
                        try {
                            need_card_name = jsonObject.getBoolean("need_cardholder_name");
                        } catch (Exception e) {
                            need_card_name = false;
                        }
                        try {
                            stripeApiKey = jsonObject.getString("publishable_key");
                        } catch (Exception e) {
                            stripeApiKey = "";
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                initAndroidPay(context);

                inputFields = new EditTextBoldCursor[FIELDS_COUNT_CARD];
                for (int a = 0; a < FIELDS_COUNT_CARD; a++) {
                    if (a == FIELD_CARD) {
                        headerCell[0] = new HeaderCell(context);
                        headerCell[0].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        headerCell[0].setText(LocaleController.getString("PaymentCardTitle", R.string.PaymentCardTitle));
                        linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    } else if (a == FIELD_CARD_COUNTRY) {
                        headerCell[1] = new HeaderCell(context);
                        headerCell[1].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        headerCell[1].setText(LocaleController.getString("PaymentBillingAddress", R.string.PaymentBillingAddress));
                        linearLayout2.addView(headerCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    boolean allowDivider = a != FIELD_CVV && a != FIELD_CARD_POSTCODE && !(a == FIELD_CARD_COUNTRY && !need_card_postcode);
                    ViewGroup container = new FrameLayout(context);
                    linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    View.OnTouchListener onTouchListener = null;
                    inputFields[a] = new EditTextBoldCursor(context);
                    inputFields[a].setTag(a);
                    inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                    inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    inputFields[a].setBackgroundDrawable(null);
                    inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                    inputFields[a].setCursorWidth(1.5f);
                    if (a == FIELD_CVV) {
                        InputFilter[] inputFilters = new InputFilter[1];
                        inputFilters[0] = new InputFilter.LengthFilter(3);
                        inputFields[a].setFilters(inputFilters);
                        inputFields[a].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        inputFields[a].setTypeface(Typeface.DEFAULT);
                        inputFields[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                    } else if (a == FIELD_CARD) {
                        inputFields[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                    } else if (a == FIELD_CARD_COUNTRY) {
                        inputFields[a].setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                if (getParentActivity() == null) {
                                    return false;
                                }
                                if (event.getAction() == MotionEvent.ACTION_UP) {
                                    CountrySelectActivity fragment = new CountrySelectActivity(false);
                                    fragment.setCountrySelectActivityDelegate(new CountrySelectActivity.CountrySelectActivityDelegate() {
                                        @Override
                                        public void didSelectCountry(String name, String shortName) {
                                            inputFields[FIELD_CARD_COUNTRY].setText(name);
                                        }
                                    });
                                    presentFragment(fragment);
                                }
                                return true;
                            }
                        });
                        inputFields[a].setInputType(0);
                    } else if (a == FIELD_EXPIRE_DATE) {
                        inputFields[a].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    } else if (a == FIELD_CARDNAME) {
                        inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                    } else {
                        inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    }
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    switch (a) {
                        case FIELD_CARD:
                            inputFields[a].setHint(LocaleController.getString("PaymentCardNumber", R.string.PaymentCardNumber));
                            break;
                        case FIELD_CVV:
                            inputFields[a].setHint(LocaleController.getString("PaymentCardCvv", R.string.PaymentCardCvv));
                            break;
                        case FIELD_EXPIRE_DATE:
                            inputFields[a].setHint(LocaleController.getString("PaymentCardExpireDate", R.string.PaymentCardExpireDate));
                            break;
                        case FIELD_CARDNAME:
                            inputFields[a].setHint(LocaleController.getString("PaymentCardName", R.string.PaymentCardName));
                            break;
                        case FIELD_CARD_POSTCODE:
                            inputFields[a].setHint(LocaleController.getString("PaymentShippingZipPlaceholder", R.string.PaymentShippingZipPlaceholder));
                            break;
                        case FIELD_CARD_COUNTRY:
                            inputFields[a].setHint(LocaleController.getString("PaymentShippingCountry", R.string.PaymentShippingCountry));
                            break;
                    }

                    if (a == FIELD_CARD) {
                        inputFields[a].addTextChangedListener(new TextWatcher() {

                            public final String[] PREFIXES_15 = {"34", "37"};
                            public final String[] PREFIXES_14 = {"300", "301", "302", "303", "304", "305", "309", "36", "38", "39"};
                            public final String[] PREFIXES_16 = {
                                    "2221", "2222", "2223", "2224", "2225", "2226", "2227", "2228", "2229",
                                    "223", "224", "225", "226", "227", "228", "229",
                                    "23", "24", "25", "26",
                                    "270", "271", "2720",
                                    "50", "51", "52", "53", "54", "55",

                                    "4",

                                    "60", "62", "64", "65",

                                    "35"
                            };

                            public static final int MAX_LENGTH_STANDARD = 16;
                            public static final int MAX_LENGTH_AMERICAN_EXPRESS = 15;
                            public static final int MAX_LENGTH_DINERS_CLUB = 14;

                            private int characterAction = -1;
                            private int actionPosition;

                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                if (count == 0 && after == 1) {
                                    characterAction = 1;
                                } else if (count == 1 && after == 0) {
                                    if (s.charAt(start) == ' ' && start > 0) {
                                        characterAction = 3;
                                        actionPosition = start - 1;
                                    } else {
                                        characterAction = 2;
                                    }
                                } else {
                                    characterAction = -1;
                                }
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {

                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if (ignoreOnCardChange) {
                                    return;
                                }
                                EditText phoneField = inputFields[FIELD_CARD];
                                int start = phoneField.getSelectionStart();
                                String phoneChars = "0123456789";
                                String str = phoneField.getText().toString();
                                if (characterAction == 3) {
                                    str = str.substring(0, actionPosition) + str.substring(actionPosition + 1, str.length());
                                    start--;
                                }
                                StringBuilder builder = new StringBuilder(str.length());
                                for (int a = 0; a < str.length(); a++) {
                                    String ch = str.substring(a, a + 1);
                                    if (phoneChars.contains(ch)) {
                                        builder.append(ch);
                                    }
                                }
                                ignoreOnCardChange = true;
                                String hint = null;
                                int maxLength = 100;
                                if (builder.length() > 0) {
                                    String currentString = builder.toString();
                                    for (int a = 0; a < 3; a++) {
                                        String checkArr[];
                                        String resultHint;
                                        int resultMaxLength;
                                        switch (a) {
                                            case 0:
                                                checkArr = PREFIXES_16;
                                                resultMaxLength = 16;
                                                resultHint = "xxxx xxxx xxxx xxxx";
                                                break;
                                            case 1:
                                                checkArr = PREFIXES_15;
                                                resultMaxLength = 15;
                                                resultHint = "xxxx xxxx xxxx xxx";
                                                break;
                                            case 2:
                                            default:
                                                checkArr = PREFIXES_14;
                                                resultMaxLength = 14;
                                                resultHint = "xxxx xxxx xxxx xx";
                                                break;
                                        }
                                        for (int b = 0; b < checkArr.length; b++) {
                                            String prefix = checkArr[b];
                                            if (currentString.length() <= prefix.length()) {
                                                if (prefix.startsWith(currentString)) {
                                                    hint = resultHint;
                                                    maxLength = resultMaxLength;
                                                    break;
                                                }
                                            } else {
                                                if (currentString.startsWith(prefix)) {
                                                    hint = resultHint;
                                                    maxLength = resultMaxLength;
                                                    break;
                                                }
                                            }
                                        }
                                        if (hint != null) {
                                            break;
                                        }
                                    }
                                    if (maxLength != 0) {
                                        if (builder.length() > maxLength) {
                                            builder.setLength(maxLength);
                                        }
                                    }
                                }
                                if (hint != null) {
                                    if (maxLength != 0) {
                                        if (builder.length() == maxLength) {
                                            inputFields[FIELD_EXPIRE_DATE].requestFocus();
                                        }
                                    }
                                    phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                                    for (int a = 0; a < builder.length(); a++) {
                                        if (a < hint.length()) {
                                            if (hint.charAt(a) == ' ') {
                                                builder.insert(a, ' ');
                                                a++;
                                                if (start == a && characterAction != 2 && characterAction != 3) {
                                                    start++;
                                                }
                                            }
                                        } else {
                                            builder.insert(a, ' ');
                                            if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                                start++;
                                            }
                                            break;
                                        }
                                    }
                                } else {
                                    phoneField.setTextColor(builder.length() > 0 ? Theme.getColor(Theme.key_windowBackgroundWhiteRedText4) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                                }
                                phoneField.setText(builder);
                                if (start >= 0) {
                                    phoneField.setSelection(start <= phoneField.length() ? start : phoneField.length());
                                }
                                ignoreOnCardChange = false;
                            }
                        });
                    } else if (a == FIELD_EXPIRE_DATE) {
                        inputFields[a].addTextChangedListener(new TextWatcher() {

                            private int characterAction = -1;
                            private boolean isYear;
                            private int actionPosition;

                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                if (count == 0 && after == 1) {
                                    isYear = TextUtils.indexOf(inputFields[FIELD_EXPIRE_DATE].getText(), '/') != -1;
                                    characterAction = 1;
                                } else if (count == 1 && after == 0) {
                                    if (s.charAt(start) == '/' && start > 0) {
                                        isYear = false;
                                        characterAction = 3;
                                        actionPosition = start - 1;
                                    } else {
                                        characterAction = 2;
                                    }
                                } else {
                                    characterAction = -1;
                                }
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {

                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if (ignoreOnCardChange) {
                                    return;
                                }
                                EditText phoneField = inputFields[FIELD_EXPIRE_DATE];
                                int start = phoneField.getSelectionStart();
                                String phoneChars = "0123456789";
                                String str = phoneField.getText().toString();
                                if (characterAction == 3) {
                                    str = str.substring(0, actionPosition) + str.substring(actionPosition + 1, str.length());
                                    start--;
                                }
                                StringBuilder builder = new StringBuilder(str.length());
                                for (int a = 0; a < str.length(); a++) {
                                    String ch = str.substring(a, a + 1);
                                    if (phoneChars.contains(ch)) {
                                        builder.append(ch);
                                    }
                                }
                                ignoreOnCardChange = true;
                                inputFields[FIELD_EXPIRE_DATE].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                                if (builder.length() > 4) {
                                    builder.setLength(4);
                                }
                                if (builder.length() < 2) {
                                    isYear = false;
                                }
                                boolean isError = false;
                                if (isYear) {
                                    String[] args = new String[builder.length() > 2 ? 2 : 1];
                                    args[0] = builder.substring(0, 2);
                                    if (args.length == 2) {
                                        args[1] = builder.substring(2);
                                    }
                                    if (builder.length() == 4 && args.length == 2) {
                                        int month = Utilities.parseInt(args[0]);
                                        int year = Utilities.parseInt(args[1]) + 2000;
                                        Calendar rightNow = Calendar.getInstance();
                                        int currentYear = rightNow.get(Calendar.YEAR);
                                        int currentMonth = rightNow.get(Calendar.MONTH) + 1;
                                        if (year < currentYear || year == currentYear && month < currentMonth) {
                                            inputFields[FIELD_EXPIRE_DATE].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                                            isError = true;
                                        }
                                    } else {
                                        int value = Utilities.parseInt(args[0]);
                                        if (value > 12 || value == 0) {
                                            inputFields[FIELD_EXPIRE_DATE].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                                            isError = true;
                                        }
                                    }
                                } else {
                                    if (builder.length() == 1) {
                                        int value = Utilities.parseInt(builder.toString());
                                        if (value != 1 && value != 0) {
                                            builder.insert(0, "0");
                                            start++;
                                        }
                                    } else if (builder.length() == 2) {
                                        int value = Utilities.parseInt(builder.toString());
                                        if (value > 12 || value == 0) {
                                            inputFields[FIELD_EXPIRE_DATE].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                                            isError = true;
                                        }
                                        start++;
                                    }
                                }
                                if (!isError && builder.length() == 4) {
                                    inputFields[need_card_name ? FIELD_CARDNAME : FIELD_CVV].requestFocus();
                                }
                                if (builder.length() == 2) {
                                    builder.append('/');
                                    start++;
                                } else if (builder.length() > 2 && builder.charAt(2) != '/') {
                                    builder.insert(2, '/');
                                    start++;
                                }

                                phoneField.setText(builder);
                                if (start >= 0) {
                                    phoneField.setSelection(start <= phoneField.length() ? start : phoneField.length());
                                }
                                ignoreOnCardChange = false;
                            }
                        });
                    }
                    inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                    inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 17, 12, 17, 6));

                    inputFields[a].setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                            if (i == EditorInfo.IME_ACTION_NEXT) {
                                int num = (Integer) textView.getTag();
                                while (num + 1 < inputFields.length) {
                                    num++;
                                    if (num == FIELD_CARD_COUNTRY) {
                                        num++;
                                    }
                                    if (((View) inputFields[num].getParent()).getVisibility() == View.VISIBLE) {
                                        inputFields[num].requestFocus();
                                        break;
                                    }
                                }
                                return true;
                            } else if (i == EditorInfo.IME_ACTION_DONE) {
                                doneItem.performClick();
                                return true;
                            }
                            return false;
                        }
                    });
                    if (a == FIELD_CVV) {
                        sectionCell[0] = new ShadowSectionCell(context);
                        linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    } else if (a == FIELD_CARD_POSTCODE) {
                        sectionCell[2] = new ShadowSectionCell(context);
                        linearLayout2.addView(sectionCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                        checkCell1 = new TextCheckCell(context);
                        checkCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        checkCell1.setTextAndCheck(LocaleController.getString("PaymentCardSavePaymentInformation", R.string.PaymentCardSavePaymentInformation), saveCardInfo, false);
                        linearLayout2.addView(checkCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        checkCell1.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                saveCardInfo = !saveCardInfo;
                                checkCell1.setChecked(saveCardInfo);
                            }
                        });

                        bottomCell[0] = new TextInfoPrivacyCell(context);
                        bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        updateSavePaymentField();
                        linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    } else if (a == FIELD_CARD) {
                        androidPayContainer = new FrameLayout(context);
                        androidPayContainer.setId(fragment_container_id);
                        androidPayContainer.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        androidPayContainer.setVisibility(View.GONE);
                        container.addView(androidPayContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 4, 0));
                    }

                    if (allowDivider) {
                        View divider = new View(context);
                        dividers.add(divider);
                        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                        container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
                    }

                    if (a == FIELD_CARD_COUNTRY && !need_card_country || a == FIELD_CARD_POSTCODE && !need_card_postcode || a == FIELD_CARDNAME && !need_card_name) {
                        container.setVisibility(View.GONE);
                    }
                }
                if (!need_card_country && !need_card_postcode) {
                    headerCell[1].setVisibility(View.GONE);
                    sectionCell[0].setVisibility(View.GONE);
                }
                if (need_card_postcode) {
                    inputFields[FIELD_CARD_POSTCODE].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                } else {
                    inputFields[FIELD_CVV].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                }
            }
        } else if (currentStep == 1) {
            int count = requestedInfo.shipping_options.size();
            radioCells = new RadioCell[count];
            for (int a = 0; a < count; a++) {
                TLRPC.TL_shippingOption shippingOption = requestedInfo.shipping_options.get(a);
                radioCells[a] = new RadioCell(context);
                radioCells[a].setTag(a);
                radioCells[a].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                radioCells[a].setText(String.format("%s - %s", getTotalPriceString(shippingOption.prices), shippingOption.title), a == 0, a != count - 1);
                radioCells[a].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int num = (Integer) v.getTag();
                        for (int a = 0; a < radioCells.length; a++) {
                            radioCells[a].setChecked(num == a, true);
                        }
                    }
                });
                linearLayout2.addView(radioCells[a]);
            }
            bottomCell[0] = new TextInfoPrivacyCell(context);
            bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else if (currentStep == 3) {
            inputFields = new EditTextBoldCursor[FIELDS_COUNT_SAVEDCARD];
            for (int a = 0; a < FIELDS_COUNT_SAVEDCARD; a++) {
                if (a == FIELD_SAVEDCARD) {
                    headerCell[0] = new HeaderCell(context);
                    headerCell[0].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell[0].setText(LocaleController.getString("PaymentCardTitle", R.string.PaymentCardTitle));
                    linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }

                ViewGroup container = new FrameLayout(context);
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                boolean allowDivider = a != FIELD_SAVEDPASSWORD;
                if (allowDivider) {
                    if (a == FIELD_EMAIL && !paymentForm.invoice.phone_requested) {
                        allowDivider = false;
                    } else if (a == FIELD_NAME && !paymentForm.invoice.phone_requested && !paymentForm.invoice.email_requested) {
                        allowDivider = false;
                    }
                }
                if (allowDivider) {
                    View divider = new View(context);
                    dividers.add(divider);
                    divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                    container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
                }

                inputFields[a] = new EditTextBoldCursor(context);
                inputFields[a].setTag(a);
                inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setBackgroundDrawable(null);
                inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                inputFields[a].setCursorWidth(1.5f);
                if (a == FIELD_SAVEDCARD) {
                    inputFields[a].setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            return true;
                        }
                    });
                    inputFields[a].setInputType(0);
                } else {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputFields[a].setTypeface(Typeface.DEFAULT);
                }
                inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                switch (a) {
                    case FIELD_SAVEDCARD:
                        inputFields[a].setText(paymentForm.saved_credentials.title);
                        break;
                    case FIELD_SAVEDPASSWORD:
                        inputFields[a].setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
                        inputFields[a].requestFocus();
                        break;
                }

                inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 17, 12, 17, 6));

                inputFields[a].setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        if (i == EditorInfo.IME_ACTION_DONE) {
                            doneItem.performClick();
                            return true;
                        }
                        return false;
                    }
                });
                if (a == FIELD_SAVEDPASSWORD) {
                    bottomCell[0] = new TextInfoPrivacyCell(context);
                    bottomCell[0].setText(LocaleController.formatString("PaymentConfirmationMessage", R.string.PaymentConfirmationMessage, paymentForm.saved_credentials.title));
                    bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    settingsCell1 = new TextSettingsCell(context);
                    settingsCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                    settingsCell1.setText(LocaleController.getString("PaymentConfirmationNewCard", R.string.PaymentConfirmationNewCard), false);
                    linearLayout2.addView(settingsCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    settingsCell1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            passwordOk = false;
                            goToNextStep();
                        }
                    });

                    bottomCell[1] = new TextInfoPrivacyCell(context);
                    bottomCell[1].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            }
        } else if (currentStep == 4 || currentStep == 5) {
            paymentInfoCell = new PaymentInfoCell(context);
            paymentInfoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            paymentInfoCell.setInvoice((TLRPC.TL_messageMediaInvoice) messageObject.messageOwner.media, currentBotName);
            linearLayout2.addView(paymentInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            sectionCell[0] = new ShadowSectionCell(context);
            linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            ArrayList<TLRPC.TL_labeledPrice> arrayList = new ArrayList<>();
            arrayList.addAll(paymentForm.invoice.prices);
            if (shippingOption != null) {
                arrayList.addAll(shippingOption.prices);
            }
            final String totalPrice = getTotalPriceString(arrayList);

            for (int a = 0; a < arrayList.size(); a++) {
                TLRPC.TL_labeledPrice price = arrayList.get(a);

                TextPriceCell priceCell = new TextPriceCell(context);
                priceCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                priceCell.setTextAndValue(price.label, LocaleController.getInstance().formatCurrencyString(price.amount, paymentForm.invoice.currency), false);
                linearLayout2.addView(priceCell);
            }

            TextPriceCell priceCell = new TextPriceCell(context);
            priceCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            priceCell.setTextAndValue(LocaleController.getString("PaymentTransactionTotal", R.string.PaymentTransactionTotal), totalPrice, true);
            linearLayout2.addView(priceCell);

            View divider = new View(context);
            dividers.add(divider);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            linearLayout2.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));

            detailSettingsCell[0] = new TextDetailSettingsCell(context);
            detailSettingsCell[0].setBackgroundDrawable(Theme.getSelectorDrawable(true));
            detailSettingsCell[0].setTextAndValue(cardName, LocaleController.getString("PaymentCheckoutMethod", R.string.PaymentCheckoutMethod), true);
            linearLayout2.addView(detailSettingsCell[0]);
            if (currentStep == 4) {
                detailSettingsCell[0].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PaymentFormActivity activity = new PaymentFormActivity(paymentForm, messageObject, 2, requestedInfo, shippingOption, null, cardName, validateRequest, saveCardInfo, null);
                        activity.setDelegate(new PaymentFormActivityDelegate() {
                            @Override
                            public boolean didSelectNewCard(String tokenJson, String card, boolean saveCard, TLRPC.TL_inputPaymentCredentialsAndroidPay androidPay) {
                                paymentForm.saved_credentials = null;
                                paymentJson = tokenJson;
                                saveCardInfo = saveCard;
                                cardName = card;
                                androidPayCredentials = androidPay;
                                detailSettingsCell[0].setTextAndValue(cardName, LocaleController.getString("PaymentCheckoutMethod", R.string.PaymentCheckoutMethod), true);
                                return false;
                            }

                            @Override
                            public void onFragmentDestroyed() {

                            }

                            @Override
                            public void currentPasswordUpdated(TLRPC.account_Password password) {

                            }
                        });
                        presentFragment(activity);
                    }
                });
            }

            TLRPC.User providerUser = null;
            for (int a = 0; a < paymentForm.users.size(); a++) {
                TLRPC.User user = paymentForm.users.get(a);
                if (user.id == paymentForm.provider_id) {
                    providerUser = user;
                }
            }
            final String providerName;
            if (providerUser != null) {
                detailSettingsCell[1] = new TextDetailSettingsCell(context);
                detailSettingsCell[1].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                detailSettingsCell[1].setTextAndValue(providerName = ContactsController.formatName(providerUser.first_name, providerUser.last_name), LocaleController.getString("PaymentCheckoutProvider", R.string.PaymentCheckoutProvider), true);
                linearLayout2.addView(detailSettingsCell[1]);
            } else {
                providerName = "";
            }

            if (validateRequest != null) {
                if (validateRequest.info.shipping_address != null) {
                    String address = String.format("%s %s, %s, %s, %s, %s", validateRequest.info.shipping_address.street_line1, validateRequest.info.shipping_address.street_line2, validateRequest.info.shipping_address.city, validateRequest.info.shipping_address.state, validateRequest.info.shipping_address.country_iso2, validateRequest.info.shipping_address.post_code);
                    detailSettingsCell[2] = new TextDetailSettingsCell(context);
                    detailSettingsCell[2].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    detailSettingsCell[2].setTextAndValue(address, LocaleController.getString("PaymentShippingAddress", R.string.PaymentShippingAddress), true);
                    linearLayout2.addView(detailSettingsCell[2]);
                }

                if (validateRequest.info.name != null) {
                    detailSettingsCell[3] = new TextDetailSettingsCell(context);
                    detailSettingsCell[3].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    detailSettingsCell[3].setTextAndValue(validateRequest.info.name, LocaleController.getString("PaymentCheckoutName", R.string.PaymentCheckoutName), true);
                    linearLayout2.addView(detailSettingsCell[3]);
                }

                if (validateRequest.info.phone != null) {
                    detailSettingsCell[4] = new TextDetailSettingsCell(context);
                    detailSettingsCell[4].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    detailSettingsCell[4].setTextAndValue(PhoneFormat.getInstance().format(validateRequest.info.phone), LocaleController.getString("PaymentCheckoutPhoneNumber", R.string.PaymentCheckoutPhoneNumber), true);
                    linearLayout2.addView(detailSettingsCell[4]);
                }

                if (validateRequest.info.email != null) {
                    detailSettingsCell[5] = new TextDetailSettingsCell(context);
                    detailSettingsCell[5].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    detailSettingsCell[5].setTextAndValue(validateRequest.info.email, LocaleController.getString("PaymentCheckoutEmail", R.string.PaymentCheckoutEmail), true);
                    linearLayout2.addView(detailSettingsCell[5]);
                }

                if (shippingOption != null) {
                    detailSettingsCell[6] = new TextDetailSettingsCell(context);
                    detailSettingsCell[6].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    detailSettingsCell[6].setTextAndValue(shippingOption.title, LocaleController.getString("PaymentCheckoutShippingMethod", R.string.PaymentCheckoutShippingMethod), false);
                    linearLayout2.addView(detailSettingsCell[6]);
                }
            }

            if (currentStep == 4) {
                bottomLayout = new FrameLayout(context);
                bottomLayout.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                frameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
                bottomLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (botUser != null && !botUser.verified) {
                            String botKey = "payment_warning_" + botUser.id;
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            if (!preferences.getBoolean(botKey, false)) {
                                preferences.edit().putBoolean(botKey, true).commit();
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle(LocaleController.getString("PaymentWarning", R.string.PaymentWarning));
                                builder.setMessage(LocaleController.formatString("PaymentWarningText", R.string.PaymentWarningText, currentBotName, providerName));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        showPayAlert(totalPrice);
                                    }
                                });
                                showDialog(builder.create());
                            } else {
                                showPayAlert(totalPrice);
                            }
                        } else {
                            showPayAlert(totalPrice);
                        }
                    }
                });
                payTextView = new TextView(context);
                payTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText6));
                payTextView.setText(LocaleController.formatString("PaymentCheckoutPay", R.string.PaymentCheckoutPay, totalPrice));
                payTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                payTextView.setGravity(Gravity.CENTER);
                payTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                bottomLayout.addView(payTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                progressViewButton = new ContextProgressView(context, 0);
                progressViewButton.setVisibility(View.INVISIBLE);
                bottomLayout.addView(progressViewButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                View shadow = new View(context);
                shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
                frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

                doneItem.setEnabled(false);
                doneItem.getImageView().setVisibility(View.INVISIBLE);

                webView = new WebView(context) {
                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return super.onTouchEvent(event);
                    }
                };
                webView.setBackgroundColor(0xffffffff);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);

                if (Build.VERSION.SDK_INT >= 21) {
                    webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.setAcceptThirdPartyCookies(webView, true);
                }

                webView.setWebViewClient(new WebViewClient() {

                    @Override
                    public void onLoadResource(WebView view, String url) {
                        try {
                            Uri uri = Uri.parse(url);
                            if ("t.me".equals(uri.getHost())) {
                                goToNextStep();
                                return;
                            }
                        } catch (Exception ignore) {

                        }
                        super.onLoadResource(view, url);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        webviewLoading = false;
                        showEditDoneProgress(true, false);
                        updateSavePaymentField();
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        try {
                            Uri uri = Uri.parse(url);
                            if ("t.me".equals(uri.getHost())) {
                                goToNextStep();
                                return true;
                            }
                        } catch (Exception ignore) {

                        }
                        return false;
                    }
                });

                frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }

            sectionCell[1] = new ShadowSectionCell(context);
            sectionCell[1].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(sectionCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else if (currentStep == 6) {
            bottomCell[2] = new TextInfoPrivacyCell(context);
            bottomCell[2].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(bottomCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            settingsCell1 = new TextSettingsCell(context);
            settingsCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            settingsCell1.setTag(Theme.key_windowBackgroundWhiteRedText3);
            settingsCell1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            settingsCell1.setText(LocaleController.getString("AbortPassword", R.string.AbortPassword), false);
            linearLayout2.addView(settingsCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            settingsCell1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("TurnPasswordOffQuestion", R.string.TurnPasswordOffQuestion));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            sendSavePassword(true);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            });

            inputFields = new EditTextBoldCursor[FIELDS_COUNT_PASSWORD];
            for (int a = 0; a < FIELDS_COUNT_PASSWORD; a++) {
                if (a == FIELD_ENTERPASSWORD) {
                    headerCell[0] = new HeaderCell(context);
                    headerCell[0].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell[0].setText(LocaleController.getString("PaymentPasswordTitle", R.string.PaymentPasswordTitle));
                    linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else if (a == FIELD_ENTERPASSWORDEMAIL) {
                    headerCell[1] = new HeaderCell(context);
                    headerCell[1].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell[1].setText(LocaleController.getString("PaymentPasswordEmailTitle", R.string.PaymentPasswordEmailTitle));
                    linearLayout2.addView(headerCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }

                ViewGroup container = new FrameLayout(context);
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                if (a == FIELD_ENTERPASSWORD) {
                    View divider = new View(context);
                    dividers.add(divider);
                    divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                    container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
                }

                inputFields[a] = new EditTextBoldCursor(context);
                inputFields[a].setTag(a);
                inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setBackgroundDrawable(null);
                inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                inputFields[a].setCursorWidth(1.5f);

                if (a == FIELD_ENTERPASSWORD || a == FIELD_REENTERPASSWORD) {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputFields[a].setTypeface(Typeface.DEFAULT);
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                } else {
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                }

                switch (a) {
                    case FIELD_ENTERPASSWORD:
                        inputFields[a].setHint(LocaleController.getString("PaymentPasswordEnter", R.string.PaymentPasswordEnter));
                        inputFields[a].requestFocus();
                        break;
                    case FIELD_REENTERPASSWORD:
                        inputFields[a].setHint(LocaleController.getString("PaymentPasswordReEnter", R.string.PaymentPasswordReEnter));
                        break;
                    case FIELD_ENTERPASSWORDEMAIL:
                        inputFields[a].setHint(LocaleController.getString("PaymentPasswordEmail", R.string.PaymentPasswordEmail));
                        break;
                }

                inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 17, 12, 17, 6));

                inputFields[a].setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        if (i == EditorInfo.IME_ACTION_DONE) {
                            doneItem.performClick();
                            return true;
                        } else if (i == EditorInfo.IME_ACTION_NEXT) {
                            int num = (Integer) textView.getTag();
                            if (num == FIELD_ENTERPASSWORD) {
                                inputFields[FIELD_REENTERPASSWORD].requestFocus();
                            } else if (num == FIELD_REENTERPASSWORD) {
                                inputFields[FIELD_ENTERPASSWORDEMAIL].requestFocus();
                            }
                        }
                        return false;
                    }
                });
                if (a == FIELD_REENTERPASSWORD) {
                    bottomCell[0] = new TextInfoPrivacyCell(context);
                    bottomCell[0].setText(LocaleController.getString("PaymentPasswordInfo", R.string.PaymentPasswordInfo));
                    bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else if (a == FIELD_ENTERPASSWORDEMAIL) {
                    bottomCell[1] = new TextInfoPrivacyCell(context);
                    bottomCell[1].setText(LocaleController.getString("PaymentPasswordEmailInfo", R.string.PaymentPasswordEmailInfo));
                    bottomCell[1].setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            }
            updatePasswordFields();
        }
        return fragmentView;
    }

    private void updatePasswordFields() {
        if (currentStep != 6 || bottomCell[2] == null) {
            return;
        }
        if (currentPassword == null) {
            doneItem.setVisibility(View.VISIBLE);
            showEditDoneProgress(true, true);
            bottomCell[2].setVisibility(View.GONE);
            settingsCell1.setVisibility(View.GONE);
            headerCell[0].setVisibility(View.GONE);
            headerCell[1].setVisibility(View.GONE);
            bottomCell[0].setVisibility(View.GONE);
            for (int a = 0; a < FIELDS_COUNT_PASSWORD; a++) {
                ((View) inputFields[a].getParent()).setVisibility(View.GONE);
            }
            for (int a = 0; a < dividers.size(); a++) {
                dividers.get(a).setVisibility(View.GONE);
            }
        } else {
            showEditDoneProgress(true, false);
            if (waitingForEmail) {
                if (getParentActivity() != null) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
                doneItem.setVisibility(View.GONE);
                bottomCell[2].setText(LocaleController.formatString("EmailPasswordConfirmText", R.string.EmailPasswordConfirmText, currentPassword.email_unconfirmed_pattern));
                bottomCell[2].setVisibility(View.VISIBLE);
                settingsCell1.setVisibility(View.VISIBLE);
                bottomCell[1].setText("");

                headerCell[0].setVisibility(View.GONE);
                headerCell[1].setVisibility(View.GONE);
                bottomCell[0].setVisibility(View.GONE);
                for (int a = 0; a < FIELDS_COUNT_PASSWORD; a++) {
                    ((View) inputFields[a].getParent()).setVisibility(View.GONE);
                }
                for (int a = 0; a < dividers.size(); a++) {
                    dividers.get(a).setVisibility(View.GONE);
                }
            } else {
                doneItem.setVisibility(View.VISIBLE);
                bottomCell[2].setVisibility(View.GONE);
                settingsCell1.setVisibility(View.GONE);
                bottomCell[1].setText(LocaleController.getString("PaymentPasswordEmailInfo", R.string.PaymentPasswordEmailInfo));

                headerCell[0].setVisibility(View.VISIBLE);
                headerCell[1].setVisibility(View.VISIBLE);
                bottomCell[0].setVisibility(View.VISIBLE);
                for (int a = 0; a < FIELDS_COUNT_PASSWORD; a++) {
                    ((View) inputFields[a].getParent()).setVisibility(View.VISIBLE);
                }
                for (int a = 0; a < dividers.size(); a++) {
                    dividers.get(a).setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void loadPasswordInfo() {
        if (loadingPasswordInfo) {
            return;
        }
        loadingPasswordInfo = true;
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingPasswordInfo = false;
                        if (error == null) {
                            currentPassword = (TLRPC.account_Password) response;
                            if (paymentForm != null && currentPassword instanceof TLRPC.TL_account_password) {
                                paymentForm.password_missing = false;
                                paymentForm.can_save_credentials = true;
                                updateSavePaymentField();
                            }
                            byte[] salt = new byte[currentPassword.new_salt.length + 8];
                            Utilities.random.nextBytes(salt);
                            System.arraycopy(currentPassword.new_salt, 0, salt, 0, currentPassword.new_salt.length);
                            currentPassword.new_salt = salt;
                            if (passwordFragment != null) {
                                passwordFragment.setCurrentPassword(currentPassword);
                            }
                        }
                        if (response instanceof TLRPC.TL_account_noPassword && shortPollRunnable == null) {
                            shortPollRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (shortPollRunnable == null) {
                                        return;
                                    }
                                    loadPasswordInfo();
                                    shortPollRunnable = null;
                                }
                            };
                            AndroidUtilities.runOnUIThread(shortPollRunnable, 5000);
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void showAlertWithText(String title, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setTitle(title);
        builder.setMessage(text);
        showDialog(builder.create());
    }

    private void showPayAlert(final String totalPrice) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("PaymentTransactionReview", R.string.PaymentTransactionReview));
        builder.setMessage(LocaleController.formatString("PaymentTransactionMessage", R.string.PaymentTransactionMessage, totalPrice, currentBotName, currentItemName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                setDonePressed(true);
                sendData();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void initAndroidPay(Context context) {
        if (Build.VERSION.SDK_INT < 19) {
            return;
        }
        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {

                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {

                    }
                })
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                        .setEnvironment(paymentForm.invoice.test ? WalletConstants.ENVIRONMENT_TEST : WalletConstants.ENVIRONMENT_PRODUCTION)
                        .setTheme(WalletConstants.THEME_LIGHT)
                        .build())
                .build();

        Wallet.Payments.isReadyToPay(googleApiClient).setResultCallback(
                new ResultCallback<BooleanResult>() {
                    @Override
                    public void onResult(BooleanResult booleanResult) {
                        if (booleanResult.getStatus().isSuccess()) {
                            if (booleanResult.getValue()) {
                                showAndroidPay();
                            }
                        } else {

                        }
                    }
                }
        );
        googleApiClient.connect();
    }

    private String getTotalPriceString(ArrayList<TLRPC.TL_labeledPrice> prices) {
        long amount = 0;
        for (int a = 0; a < prices.size(); a++) {
            amount += prices.get(a).amount;
        }
        return LocaleController.getInstance().formatCurrencyString(amount, paymentForm.invoice.currency);
    }

    private String getTotalPriceDecimalString(ArrayList<TLRPC.TL_labeledPrice> prices) {
        long amount = 0;
        for (int a = 0; a < prices.size(); a++) {
            amount += prices.get(a).amount;
        }
        return LocaleController.getInstance().formatCurrencyDecimalString(amount, paymentForm.invoice.currency, false);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetTwoStepPassword);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didRemovedTwoStepPassword);
        if (currentStep != 4) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.paymentFinished);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (delegate != null) {
            delegate.onFragmentDestroyed();
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetTwoStepPassword);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didRemovedTwoStepPassword);
        if (currentStep != 4) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.paymentFinished);
        }
        if (webView != null) {
            try {
                ViewParent parent = webView.getParent();
                if (parent != null) {
                    ((FrameLayout) parent).removeView(webView);
                }
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            if ((currentStep == 2 || currentStep == 6) && Build.VERSION.SDK_INT >= 23 && (UserConfig.passcodeHash.length() == 0 || UserConfig.allowScreenCapture)) {
                getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        super.onFragmentDestroy();
        canceled = true;
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward) {
            if (webView != null) {
                if (currentStep != 4) {
                    webView.loadUrl(paymentForm.url);
                }
            } else if (currentStep == 2) {
                inputFields[FIELD_CARD].requestFocus();
                AndroidUtilities.showKeyboard(inputFields[FIELD_CARD]);
            } else if (currentStep == 3) {
                inputFields[FIELD_SAVEDPASSWORD].requestFocus();
                AndroidUtilities.showKeyboard(inputFields[FIELD_SAVEDPASSWORD]);
            } else if (currentStep == 6) {
                if (!waitingForEmail) {
                    inputFields[FIELD_ENTERPASSWORD].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[FIELD_ENTERPASSWORD]);
                }
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.didSetTwoStepPassword) {
            paymentForm.password_missing = false;
            paymentForm.can_save_credentials = true;
            updateSavePaymentField();
        } else if (id == NotificationCenter.didRemovedTwoStepPassword) {
            paymentForm.password_missing = true;
            paymentForm.can_save_credentials = false;
            updateSavePaymentField();
        } else if (id == NotificationCenter.paymentFinished) {
            removeSelfFromStack();
        }
    }

    private void showAndroidPay() {
        if (getParentActivity() == null || androidPayContainer == null) {
            return;
        }

        WalletFragmentOptions.Builder optionsBuilder = WalletFragmentOptions.newBuilder();
        optionsBuilder.setEnvironment(paymentForm.invoice.test ? WalletConstants.ENVIRONMENT_TEST : WalletConstants.ENVIRONMENT_PRODUCTION);
        optionsBuilder.setMode(WalletFragmentMode.BUY_BUTTON);

        WalletFragmentStyle walletFragmentStyle;
        if (androidPayPublicKey != null) {
            androidPayContainer.setBackgroundColor(androidPayBackgroundColor);
            walletFragmentStyle = new WalletFragmentStyle()
                .setBuyButtonText(WalletFragmentStyle.BuyButtonText.BUY_WITH)
                .setBuyButtonAppearance(androidPayBlackTheme ? WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_LIGHT_WITH_BORDER : WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_DARK)
                .setBuyButtonWidth(WalletFragmentStyle.Dimension.MATCH_PARENT);
        } else {
            walletFragmentStyle = new WalletFragmentStyle()
                    .setBuyButtonText(WalletFragmentStyle.BuyButtonText.LOGO_ONLY)
                    .setBuyButtonAppearance(WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_LIGHT_WITH_BORDER)
                    .setBuyButtonWidth(WalletFragmentStyle.Dimension.WRAP_CONTENT);
        }

        optionsBuilder.setFragmentStyle(walletFragmentStyle);
        WalletFragment walletFragment = WalletFragment.newInstance(optionsBuilder.build());
        FragmentManager fragmentManager = getParentActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(fragment_container_id, walletFragment);
        fragmentTransaction.commit();

        ArrayList<TLRPC.TL_labeledPrice> arrayList = new ArrayList<>();
        arrayList.addAll(paymentForm.invoice.prices);
        if (shippingOption != null) {
            arrayList.addAll(shippingOption.prices);
        }
        totalPriceDecimal = getTotalPriceDecimalString(arrayList);

        PaymentMethodTokenizationParameters parameters;
        if (androidPayPublicKey != null) {
            parameters = PaymentMethodTokenizationParameters.newBuilder()
                    .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.NETWORK_TOKEN)
                    .addParameter("publicKey", androidPayPublicKey)
                    .build();
        } else {
            parameters = PaymentMethodTokenizationParameters.newBuilder()
                    .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.PAYMENT_GATEWAY)
                    .addParameter("gateway", "stripe")
                    .addParameter("stripe:publishableKey", stripeApiKey)
                    .addParameter("stripe:version", StripeApiHandler.VERSION)
                    .build();
        }

        MaskedWalletRequest maskedWalletRequest = MaskedWalletRequest.newBuilder()
                .setPaymentMethodTokenizationParameters(parameters)
                .setEstimatedTotalPrice(totalPriceDecimal)
                .setCurrencyCode(paymentForm.invoice.currency)
                .build();

        WalletFragmentInitParams initParams = WalletFragmentInitParams.newBuilder()
                .setMaskedWalletRequest(maskedWalletRequest)
                .setMaskedWalletRequestCode(LOAD_MASKED_WALLET_REQUEST_CODE)
                .build();

        walletFragment.initialize(initParams);
        androidPayContainer.setVisibility(View.VISIBLE);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(androidPayContainer, "alpha", 0.0f, 1.0f));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == LOAD_MASKED_WALLET_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                showEditDoneProgress(true, true);
                setDonePressed(true);

                MaskedWallet maskedWallet = data.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);

                Cart.Builder cardBuilder = Cart.newBuilder()
                        .setCurrencyCode(paymentForm.invoice.currency)
                        .setTotalPrice(totalPriceDecimal);

                ArrayList<TLRPC.TL_labeledPrice> arrayList = new ArrayList<>();
                arrayList.addAll(paymentForm.invoice.prices);
                if (shippingOption != null) {
                    arrayList.addAll(shippingOption.prices);
                }
                for (int a = 0; a < arrayList.size(); a++) {
                    TLRPC.TL_labeledPrice price = arrayList.get(a);
                    String amount = LocaleController.getInstance().formatCurrencyDecimalString(price.amount, paymentForm.invoice.currency, false);
                    cardBuilder.addLineItem(LineItem.newBuilder()
                            .setCurrencyCode(paymentForm.invoice.currency)
                            .setQuantity("1")
                            .setDescription(price.label)
                            .setTotalPrice(amount)
                            .setUnitPrice(amount).build());
                }
                FullWalletRequest fullWalletRequest = FullWalletRequest.newBuilder()
                        .setCart(cardBuilder.build())
                        .setGoogleTransactionId(maskedWallet.getGoogleTransactionId())
                        .build();
                Wallet.Payments.loadFullWallet(googleApiClient, fullWalletRequest, LOAD_FULL_WALLET_REQUEST_CODE);
            } else {
                showEditDoneProgress(true, false);
                setDonePressed(false);
            }
        } else if (requestCode == LOAD_FULL_WALLET_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                FullWallet fullWallet = data.getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                String tokenJSON = fullWallet.getPaymentMethodToken().getToken();
                try {
                    if (androidPayPublicKey != null) {
                        androidPayCredentials = new TLRPC.TL_inputPaymentCredentialsAndroidPay();
                        androidPayCredentials.payment_token = new TLRPC.TL_dataJSON();
                        androidPayCredentials.payment_token.data = tokenJSON;
                        String[] descriptions = fullWallet.getPaymentDescriptions();
                        if (descriptions.length > 0) {
                            cardName = descriptions[0];
                        } else {
                            cardName = "Android Pay";
                        }
                    } else {
                        Token token = TokenParser.parseToken(tokenJSON);
                        paymentJson = String.format(Locale.US, "{\"type\":\"%1$s\", \"id\":\"%2$s\"}", token.getType(), token.getId());
                        Card card = token.getCard();
                        cardName = card.getType() + " *" + card.getLast4();
                    }
                    goToNextStep();
                    showEditDoneProgress(true, false);
                    setDonePressed(false);
                } catch (JSONException ignore) {
                    showEditDoneProgress(true, false);
                    setDonePressed(false);
                }
            } else {
                showEditDoneProgress(true, false);
                setDonePressed(false);
            }
        }
    }

    private void goToNextStep() {
        if (currentStep == 0) {
            int nextStep;
            if (paymentForm.invoice.flexible) {
                nextStep = 1;
            } else if (paymentForm.saved_credentials != null) {
                if (UserConfig.tmpPassword != null) {
                    if (UserConfig.tmpPassword.valid_until < ConnectionsManager.getInstance().getCurrentTime() + 60) {
                        UserConfig.tmpPassword = null;
                        UserConfig.saveConfig(false);
                    }
                }
                if (UserConfig.tmpPassword != null) {
                    nextStep = 4;
                } else {
                    nextStep = 3;
                }
            } else {
                nextStep = 2;
            }
            presentFragment(new PaymentFormActivity(paymentForm, messageObject, nextStep, requestedInfo, null, null, cardName, validateRequest, saveCardInfo, androidPayCredentials), isWebView);
        } else if (currentStep == 1) {
            int nextStep;
            if (paymentForm.saved_credentials != null) {
                if (UserConfig.tmpPassword != null) {
                    if (UserConfig.tmpPassword.valid_until < ConnectionsManager.getInstance().getCurrentTime() + 60) {
                        UserConfig.tmpPassword = null;
                        UserConfig.saveConfig(false);
                    }
                }
                if (UserConfig.tmpPassword != null) {
                    nextStep = 4;
                } else {
                    nextStep = 3;
                }
            } else {
                nextStep = 2;
            }
            presentFragment(new PaymentFormActivity(paymentForm, messageObject, nextStep, requestedInfo, shippingOption, null, cardName, validateRequest, saveCardInfo, androidPayCredentials), isWebView);
        } else if (currentStep == 2) {
            if (paymentForm.password_missing && saveCardInfo) {
                passwordFragment = new PaymentFormActivity(paymentForm, messageObject, 6, requestedInfo, shippingOption, paymentJson, cardName, validateRequest, saveCardInfo, androidPayCredentials);
                passwordFragment.setCurrentPassword(currentPassword);
                passwordFragment.setDelegate(new PaymentFormActivityDelegate() {
                    @Override
                    public boolean didSelectNewCard(String tokenJson, String card, boolean saveCard, TLRPC.TL_inputPaymentCredentialsAndroidPay androidPay) {
                        if (delegate != null) {
                            delegate.didSelectNewCard(tokenJson, card, saveCard, androidPay);
                        }
                        if (isWebView) {
                            removeSelfFromStack();
                        }
                        return delegate != null;
                    }

                    @Override
                    public void onFragmentDestroyed() {
                        passwordFragment = null;
                    }

                    @Override
                    public void currentPasswordUpdated(TLRPC.account_Password password) {
                        currentPassword = password;
                    }
                });
                presentFragment(passwordFragment, isWebView);
            } else {
                if (delegate != null) {
                    delegate.didSelectNewCard(paymentJson, cardName, saveCardInfo, androidPayCredentials);
                    finishFragment();
                } else {
                    presentFragment(new PaymentFormActivity(paymentForm, messageObject, 4, requestedInfo, shippingOption, paymentJson, cardName, validateRequest, saveCardInfo, androidPayCredentials), isWebView);
                }
            }
        } else if (currentStep == 3) {
            int nextStep;
            if (passwordOk) {
                nextStep = 4;
            } else {
                nextStep = 2;
            }
            presentFragment(new PaymentFormActivity(paymentForm, messageObject, nextStep, requestedInfo, shippingOption, paymentJson, cardName, validateRequest, saveCardInfo, androidPayCredentials), !passwordOk);
        } else if (currentStep == 4) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.paymentFinished);
            finishFragment();
        } else if (currentStep == 6) {
            if (!delegate.didSelectNewCard(paymentJson, cardName, saveCardInfo, androidPayCredentials)) {
                presentFragment(new PaymentFormActivity(paymentForm, messageObject, 4, requestedInfo, shippingOption, paymentJson, cardName, validateRequest, saveCardInfo, androidPayCredentials), true);
            } else {
                finishFragment();
            }
        }
    }

    private void updateSavePaymentField() {
        if (bottomCell[0] == null || sectionCell[2] == null) {
            return;
        }
        if ((paymentForm.password_missing || paymentForm.can_save_credentials) && (webView == null || webView != null && !webviewLoading)) {
            SpannableStringBuilder text = new SpannableStringBuilder(LocaleController.getString("PaymentCardSavePaymentInformationInfoLine1", R.string.PaymentCardSavePaymentInformationInfoLine1));
            if (paymentForm.password_missing) {
                loadPasswordInfo();
                text.append("\n");
                int len = text.length();
                String str2 = LocaleController.getString("PaymentCardSavePaymentInformationInfoLine2", R.string.PaymentCardSavePaymentInformationInfoLine2);
                int index1 = str2.indexOf('*');
                int index2 = str2.lastIndexOf('*');
                text.append(str2);
                if (index1 != -1 && index2 != -1) {
                    index1 += len;
                    index2 += len;
                    bottomCell[0].getTextView().setMovementMethod(new LinkMovementMethodMy());
                    text.replace(index2, index2 + 1, "");
                    text.replace(index1, index1 + 1, "");
                    text.setSpan(new LinkSpan(), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            checkCell1.setEnabled(true);
            bottomCell[0].setText(text);
            checkCell1.setVisibility(View.VISIBLE);
            bottomCell[0].setVisibility(View.VISIBLE);
            sectionCell[2].setBackgroundDrawable(Theme.getThemedDrawable(sectionCell[2].getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        } else {
            checkCell1.setVisibility(View.GONE);
            bottomCell[0].setVisibility(View.GONE);
            sectionCell[2].setBackgroundDrawable(Theme.getThemedDrawable(sectionCell[2].getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        }
    }

    @SuppressLint("HardwareIds")
    public void fillNumber(String number) {
        try {
            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            boolean allowCall = true;
            boolean allowSms = true;
            if (number != null || tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE) {
                if (Build.VERSION.SDK_INT >= 23) {
                    allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                    allowSms = getParentActivity().checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
                }
                if (number != null || allowCall || allowSms) {
                    if (number == null) {
                        number = PhoneFormat.stripExceptNumbers(tm.getLine1Number());
                    }
                    String textToSet = null;
                    boolean ok = false;
                    if (!TextUtils.isEmpty(number)) {
                        if (number.length() > 4) {
                            for (int a = 4; a >= 1; a--) {
                                String sub = number.substring(0, a);
                                String country = codesMap.get(sub);
                                if (country != null) {
                                    ok = true;
                                    textToSet = number.substring(a, number.length());
                                    inputFields[FIELD_PHONECODE].setText(sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                textToSet = number.substring(1, number.length());
                                inputFields[FIELD_PHONECODE].setText(number.substring(0, 1));
                            }
                        }
                        if (textToSet != null) {
                            inputFields[FIELD_PHONE].setText(textToSet);
                            inputFields[FIELD_PHONE].setSelection(inputFields[FIELD_PHONE].length());
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void sendSavePassword(final boolean clear) {
        final TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
        final String email;
        if (clear) {
            doneItem.setVisibility(View.VISIBLE);
            email = null;
            req.new_settings = new TLRPC.TL_account_passwordInputSettings();
            req.new_settings.flags = 2;
            req.new_settings.email = "";
            req.current_password_hash = new byte[0];
        } else {
            final String firstPassword = inputFields[FIELD_ENTERPASSWORD].getText().toString();
            if (TextUtils.isEmpty(firstPassword)) {
                shakeField(FIELD_ENTERPASSWORD);
                return;
            }
            String secondPassword = inputFields[FIELD_REENTERPASSWORD].getText().toString();
            if (!firstPassword.equals(secondPassword)) {
                try {
                    Toast.makeText(getParentActivity(), LocaleController.getString("PasswordDoNotMatch", R.string.PasswordDoNotMatch), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                shakeField(FIELD_REENTERPASSWORD);
                return;
            }
            email = inputFields[FIELD_ENTERPASSWORDEMAIL].getText().toString();
            if (email.length() < 3) {
                shakeField(FIELD_ENTERPASSWORDEMAIL);
                return;
            }
            int dot = email.lastIndexOf('.');
            int dog = email.lastIndexOf('@');
            if (dot < 0 || dog < 0 || dot < dog) {
                shakeField(FIELD_ENTERPASSWORDEMAIL);
                return;
            }

            req.current_password_hash = new byte[0];
            req.new_settings = new TLRPC.TL_account_passwordInputSettings();
            byte[] newPasswordBytes = null;
            try {
                newPasswordBytes = firstPassword.getBytes("UTF-8");
            } catch (Exception e) {
                FileLog.e(e);
            }

            byte[] new_salt = currentPassword.new_salt;
            byte[] hash = new byte[new_salt.length * 2 + newPasswordBytes.length];
            System.arraycopy(new_salt, 0, hash, 0, new_salt.length);
            System.arraycopy(newPasswordBytes, 0, hash, new_salt.length, newPasswordBytes.length);
            System.arraycopy(new_salt, 0, hash, hash.length - new_salt.length, new_salt.length);
            req.new_settings.flags |= 1;
            req.new_settings.hint = "";
            req.new_settings.new_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
            req.new_settings.new_salt = new_salt;

            if (email.length() > 0) {
                req.new_settings.flags |= 2;
                req.new_settings.email = email.trim();
            }
        }
        showEditDoneProgress(true, true);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        showEditDoneProgress(true, false);
                        if (clear) {
                            currentPassword = new TLRPC.TL_account_noPassword();
                            delegate.currentPasswordUpdated(currentPassword);
                            finishFragment();
                        } else {
                            if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                goToNextStep();
                            } else if (error != null) {
                                if (error.text.equals("EMAIL_UNCONFIRMED")) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            waitingForEmail = true;
                                            currentPassword.email_unconfirmed_pattern = email;
                                            updatePasswordFields();
                                        }
                                    });
                                    builder.setMessage(LocaleController.getString("YourEmailAlmostThereText", R.string.YourEmailAlmostThereText));
                                    builder.setTitle(LocaleController.getString("YourEmailAlmostThere", R.string.YourEmailAlmostThere));
                                    Dialog dialog = showDialog(builder.create());
                                    if (dialog != null) {
                                        dialog.setCanceledOnTouchOutside(false);
                                        dialog.setCancelable(false);
                                    }
                                } else {
                                    if (error.text.equals("EMAIL_INVALID")) {
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("PasswordEmailInvalid", R.string.PasswordEmailInvalid));
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        int time = Utilities.parseInt(error.text);
                                        String timeString;
                                        if (time < 60) {
                                            timeString = LocaleController.formatPluralString("Seconds", time);
                                        } else {
                                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                        }
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                    } else {
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private boolean sendCardData() {
        Integer month;
        Integer year;
        String date = inputFields[FIELD_EXPIRE_DATE].getText().toString();
        String args[] = date.split("/");
        if (args.length == 2) {
            month = Utilities.parseInt(args[0]);
            year = Utilities.parseInt(args[1]);
        } else {
            month = null;
            year = null;
        }
        Card card = new Card(
                inputFields[FIELD_CARD].getText().toString(),
                month,
                year,
                inputFields[FIELD_CVV].getText().toString(),
                inputFields[FIELD_CARDNAME].getText().toString(),
                null, null, null, null,
                inputFields[FIELD_CARD_POSTCODE].getText().toString(),
                inputFields[FIELD_CARD_COUNTRY].getText().toString(),
                null);
        cardName = card.getType() + " *" + card.getLast4();
        if (!card.validateNumber()) {
            shakeField(FIELD_CARD);
            return false;
        } else if (!card.validateExpMonth() || !card.validateExpYear() || !card.validateExpiryDate()) {
            shakeField(FIELD_EXPIRE_DATE);
            return false;
        } else if (need_card_name && inputFields[FIELD_CARDNAME].length() == 0) {
            shakeField(FIELD_CARDNAME);
            return false;
        } else if (!card.validateCVC()) {
            shakeField(FIELD_CVV);
            return false;
        } else if (need_card_country && inputFields[FIELD_CARD_COUNTRY].length() == 0) {
            shakeField(FIELD_CARD_COUNTRY);
            return false;
        } else if (need_card_postcode && inputFields[FIELD_CARD_POSTCODE].length() == 0) {
            shakeField(FIELD_CARD_POSTCODE);
            return false;
        }
        showEditDoneProgress(true, true);
        try {
            Stripe stripe = new Stripe(stripeApiKey);
            stripe.createToken(card, new TokenCallback() {
                        public void onSuccess(Token token) {
                            if (canceled) {
                                return;
                            }
                            paymentJson = String.format(Locale.US, "{\"type\":\"%1$s\", \"id\":\"%2$s\"}", token.getType(), token.getId());
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    goToNextStep();
                                    showEditDoneProgress(true, false);
                                    setDonePressed(false);
                                }
                            });
                        }

                        public void onError(Exception error) {
                            if (canceled) {
                                return;
                            }
                            showEditDoneProgress(true, false);
                            setDonePressed(false);
                            if (error instanceof APIConnectionException || error instanceof APIException) {
                                AlertsCreator.showSimpleToast(PaymentFormActivity.this, LocaleController.getString("PaymentConnectionFailed", R.string.PaymentConnectionFailed));
                            } else {
                                AlertsCreator.showSimpleToast(PaymentFormActivity.this, error.getMessage());
                            }
                        }
                    }
            );
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    private void sendForm() {
        if (canceled) {
            return;
        }
        showEditDoneProgress(true, true);
        validateRequest = new TLRPC.TL_payments_validateRequestedInfo();
        validateRequest.save = saveShippingInfo;
        validateRequest.msg_id = messageObject.getId();
        validateRequest.info = new TLRPC.TL_paymentRequestedInfo();
        if (paymentForm.invoice.name_requested) {
            validateRequest.info.name = inputFields[FIELD_NAME].getText().toString();
            validateRequest.info.flags |= 1;
        }
        if (paymentForm.invoice.phone_requested) {
            validateRequest.info.phone = "+" + inputFields[FIELD_PHONECODE].getText().toString() + inputFields[FIELD_PHONE].getText().toString();
            validateRequest.info.flags |= 2;
        }
        if (paymentForm.invoice.email_requested) {
            validateRequest.info.email = inputFields[FIELD_EMAIL].getText().toString().trim();
            validateRequest.info.flags |= 4;
        }
        if (paymentForm.invoice.shipping_address_requested) {
            validateRequest.info.shipping_address = new TLRPC.TL_postAddress();
            validateRequest.info.shipping_address.street_line1 = inputFields[FIELD_STREET1].getText().toString();
            validateRequest.info.shipping_address.street_line2 = inputFields[FIELD_STREET2].getText().toString();
            validateRequest.info.shipping_address.city = inputFields[FIELD_CITY].getText().toString();
            validateRequest.info.shipping_address.state = inputFields[FIELD_STATE].getText().toString();
            validateRequest.info.shipping_address.country_iso2 = countryName != null ? countryName : "";
            validateRequest.info.shipping_address.post_code = inputFields[FIELD_POSTCODE].getText().toString();
            validateRequest.info.flags |= 8;
        }
        final TLObject req = validateRequest;
        ConnectionsManager.getInstance().sendRequest(validateRequest, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                if (response instanceof TLRPC.TL_payments_validatedRequestedInfo) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            requestedInfo = (TLRPC.TL_payments_validatedRequestedInfo) response;
                            if (paymentForm.saved_info != null && !saveShippingInfo) {
                                TLRPC.TL_payments_clearSavedInfo req = new TLRPC.TL_payments_clearSavedInfo();
                                req.info = true;
                                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {

                                    }
                                });
                            }
                            goToNextStep();
                            setDonePressed(false);
                            showEditDoneProgress(true, false);
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            setDonePressed(false);
                            showEditDoneProgress(true, false);
                            if (error != null) {
                                switch (error.text) {
                                    case "REQ_INFO_NAME_INVALID":
                                        shakeField(FIELD_NAME);
                                        break;
                                    case "REQ_INFO_PHONE_INVALID":
                                        shakeField(FIELD_PHONE);
                                        break;
                                    case "REQ_INFO_EMAIL_INVALID":
                                        shakeField(FIELD_EMAIL);
                                        break;
                                    case "ADDRESS_COUNTRY_INVALID":
                                        shakeField(FIELD_COUNTRY);
                                        break;
                                    case "ADDRESS_CITY_INVALID":
                                        shakeField(FIELD_CITY);
                                        break;
                                    case "ADDRESS_POSTCODE_INVALID":
                                        shakeField(FIELD_POSTCODE);
                                        break;
                                    case "ADDRESS_STATE_INVALID":
                                        shakeField(FIELD_STATE);
                                        break;
                                    case "ADDRESS_STREET_LINE1_INVALID":
                                        shakeField(FIELD_STREET1);
                                        break;
                                    case "ADDRESS_STREET_LINE2_INVALID":
                                        shakeField(FIELD_STREET2);
                                        break;
                                    default:
                                        AlertsCreator.processError(error, PaymentFormActivity.this, req);
                                        break;
                                }
                            }
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private TLRPC.TL_paymentRequestedInfo getRequestInfo() {
        TLRPC.TL_paymentRequestedInfo info = new TLRPC.TL_paymentRequestedInfo();
        if (paymentForm.invoice.name_requested) {
            info.name = inputFields[FIELD_NAME].getText().toString();
            info.flags |= 1;
        }
        if (paymentForm.invoice.phone_requested) {
            info.phone = "+" + inputFields[FIELD_PHONECODE].getText().toString() + inputFields[FIELD_PHONE].getText().toString();
            info.flags |= 2;
        }
        if (paymentForm.invoice.email_requested) {
            info.email = inputFields[FIELD_EMAIL].getText().toString().trim();
            info.flags |= 4;
        }
        if (paymentForm.invoice.shipping_address_requested) {
            info.shipping_address = new TLRPC.TL_postAddress();
            info.shipping_address.street_line1 = inputFields[FIELD_STREET1].getText().toString();
            info.shipping_address.street_line2 = inputFields[FIELD_STREET2].getText().toString();
            info.shipping_address.city = inputFields[FIELD_CITY].getText().toString();
            info.shipping_address.state = inputFields[FIELD_STATE].getText().toString();
            info.shipping_address.country_iso2 = countryName != null ? countryName : "";
            info.shipping_address.post_code = inputFields[FIELD_POSTCODE].getText().toString();
            info.flags |= 8;
        }
        return info;
    }

    private void sendData() {
        if (canceled) {
            return;
        }
        showEditDoneProgress(false, true);
        final TLRPC.TL_payments_sendPaymentForm req = new TLRPC.TL_payments_sendPaymentForm();
        req.msg_id = messageObject.getId();
        if (UserConfig.tmpPassword != null && paymentForm.saved_credentials != null) {
            req.credentials = new TLRPC.TL_inputPaymentCredentialsSaved();
            req.credentials.id = paymentForm.saved_credentials.id;
            req.credentials.tmp_password = UserConfig.tmpPassword.tmp_password;
        } else if (androidPayCredentials != null) {
            req.credentials = androidPayCredentials;
        } else {
            req.credentials = new TLRPC.TL_inputPaymentCredentials();
            req.credentials.save = saveCardInfo;
            req.credentials.data = new TLRPC.TL_dataJSON();
            req.credentials.data.data = paymentJson;
        }
        if (requestedInfo != null && requestedInfo.id != null) {
            req.requested_info_id = requestedInfo.id;
            req.flags |= 1;
        }
        if (shippingOption != null) {
            req.shipping_option_id = shippingOption.id;
            req.flags |= 2;
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                if (response != null) {
                    if (response instanceof TLRPC.TL_payments_paymentResult) {
                        MessagesController.getInstance().processUpdates(((TLRPC.TL_payments_paymentResult) response).updates, false);
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                goToNextStep();
                            }
                        });
                    } else if (response instanceof TLRPC.TL_payments_paymentVerficationNeeded) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.paymentFinished);
                                setDonePressed(false);
                                webView.setVisibility(View.VISIBLE);
                                webviewLoading = true;
                                showEditDoneProgress(true, true);
                                progressView.setVisibility(View.VISIBLE);
                                doneItem.setEnabled(false);
                                doneItem.getImageView().setVisibility(View.INVISIBLE);
                                webView.loadUrl(((TLRPC.TL_payments_paymentVerficationNeeded) response).url);
                            }
                        });
                    }
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            AlertsCreator.processError(error, PaymentFormActivity.this, req);
                            setDonePressed(false);
                            showEditDoneProgress(false, false);
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void shakeField(int field) {
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        AndroidUtilities.shakeView(inputFields[field], 2, 0);
    }

    private void setDonePressed(boolean value) {
        donePressed = value;
        swipeBackEnabled = !value;
        actionBar.getBackButton().setEnabled(!donePressed);
        if (detailSettingsCell[0] != null) {
            detailSettingsCell[0].setEnabled(!donePressed);
        }
    }

    private void checkPassword() {
        if (UserConfig.tmpPassword != null) {
            if (UserConfig.tmpPassword.valid_until < ConnectionsManager.getInstance().getCurrentTime() + 60) {
                UserConfig.tmpPassword = null;
                UserConfig.saveConfig(false);
            }
        }
        if (UserConfig.tmpPassword != null) {
            sendData();
            return;
        }
        if (inputFields[FIELD_SAVEDPASSWORD].length() == 0) {
            Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(inputFields[FIELD_SAVEDPASSWORD], 2, 0);
            return;
        }
        final String password = inputFields[FIELD_SAVEDPASSWORD].getText().toString();
        showEditDoneProgress(true, true);
        setDonePressed(true);
        final TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            if (response instanceof TLRPC.TL_account_noPassword) {
                                passwordOk = false;
                                goToNextStep();
                            } else {
                                TLRPC.TL_account_password currentPassword = (TLRPC.TL_account_password) response;
                                byte[] passwordBytes = null;
                                try {
                                    passwordBytes = password.getBytes("UTF-8");
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }

                                byte[] hash = new byte[currentPassword.current_salt.length * 2 + passwordBytes.length];
                                System.arraycopy(currentPassword.current_salt, 0, hash, 0, currentPassword.current_salt.length);
                                System.arraycopy(passwordBytes, 0, hash, currentPassword.current_salt.length, passwordBytes.length);
                                System.arraycopy(currentPassword.current_salt, 0, hash, hash.length - currentPassword.current_salt.length, currentPassword.current_salt.length);

                                final TLRPC.TL_account_getTmpPassword req = new TLRPC.TL_account_getTmpPassword();
                                req.password_hash = Utilities.computeSHA256(hash, 0, hash.length);
                                req.period = 60 * 30;
                                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(final TLObject response, final TLRPC.TL_error error) {
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                showEditDoneProgress(true, false);
                                                setDonePressed(false);
                                                if (response != null) {
                                                    passwordOk = true;
                                                    UserConfig.tmpPassword = (TLRPC.TL_account_tmpPassword) response;
                                                    UserConfig.saveConfig(false);
                                                    goToNextStep();
                                                } else {
                                                    if (error.text.equals("PASSWORD_HASH_INVALID")) {
                                                        Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                                                        if (v != null) {
                                                            v.vibrate(200);
                                                        }
                                                        AndroidUtilities.shakeView(inputFields[FIELD_SAVEDPASSWORD], 2, 0);
                                                        inputFields[FIELD_SAVEDPASSWORD].setText("");
                                                    } else {
                                                        AlertsCreator.processError(error, PaymentFormActivity.this, req);
                                                    }
                                                }
                                            }
                                        });

                                    }
                                }, ConnectionsManager.RequestFlagFailOnServerErrors);
                            }
                        } else {
                            AlertsCreator.processError(error, PaymentFormActivity.this, req);
                            showEditDoneProgress(true, false);
                            setDonePressed(false);
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void showEditDoneProgress(final boolean animateDoneItem, final boolean show) {
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        if (animateDoneItem && doneItem != null) {
            doneItemAnimation = new AnimatorSet();
            if (show) {
                progressView.setVisibility(View.VISIBLE);
                doneItem.setEnabled(false);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(doneItem.getImageView(), "alpha", 0.0f),
                        ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
            } else {
                if (webView != null) {
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                            ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                            ObjectAnimator.ofFloat(progressView, "alpha", 0.0f));
                } else {
                    doneItem.getImageView().setVisibility(View.VISIBLE);
                    doneItem.setEnabled(true);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                            ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                            ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                            ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(doneItem.getImageView(), "scaleY", 1.0f),
                            ObjectAnimator.ofFloat(doneItem.getImageView(), "alpha", 1.0f));
                }

            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (!show) {
                            progressView.setVisibility(View.INVISIBLE);
                        } else {
                            doneItem.getImageView().setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        } else if (payTextView != null) {
            doneItemAnimation = new AnimatorSet();
            if (show) {
                progressViewButton.setVisibility(View.VISIBLE);
                bottomLayout.setEnabled(false);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(payTextView, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(payTextView, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(payTextView, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(progressViewButton, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(progressViewButton, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(progressViewButton, "alpha", 1.0f));
            } else {
                payTextView.setVisibility(View.VISIBLE);
                bottomLayout.setEnabled(true);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(progressViewButton, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(progressViewButton, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(progressViewButton, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(payTextView, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(payTextView, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(payTextView, "alpha", 1.0f));

            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (!show) {
                            progressViewButton.setVisibility(View.INVISIBLE);
                        } else {
                            payTextView.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        }
    }

    @Override
    public boolean onBackPressed() {
        return !donePressed;
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        arrayList.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner2));
        arrayList.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter2));
        arrayList.add(new ThemeDescription(progressViewButton, 0, null, null, null, null, Theme.key_contextProgressInner2));
        arrayList.add(new ThemeDescription(progressViewButton, 0, null, null, null, null, Theme.key_contextProgressOuter2));

        if (inputFields != null) {
            for (int a = 0; a < inputFields.length; a++) {
                arrayList.add(new ThemeDescription((View) inputFields[a].getParent(), ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
            }
        } else {
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        }
        if (radioCells != null) {
            for (int a = 0; a < radioCells.length; a++) {
                arrayList.add(new ThemeDescription(radioCells[a], ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
                arrayList.add(new ThemeDescription(radioCells[a], ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));
                arrayList.add(new ThemeDescription(radioCells[a], 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(radioCells[a], ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
                arrayList.add(new ThemeDescription(radioCells[a], ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
            }
        } else {
            arrayList.add(new ThemeDescription(null, 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        }
        for (int a = 0; a < headerCell.length; a++) {
            arrayList.add(new ThemeDescription(headerCell[a], ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(headerCell[a], 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        }
        for (int a = 0; a < sectionCell.length; a++) {
            arrayList.add(new ThemeDescription(sectionCell[a], ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        }
        for (int a = 0; a < bottomCell.length; a++) {
            arrayList.add(new ThemeDescription(bottomCell[a], ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(bottomCell[a], 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
            arrayList.add(new ThemeDescription(bottomCell[a], ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        }
        for (int a = 0; a < dividers.size(); a++) {
            arrayList.add(new ThemeDescription(dividers.get(a), ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_divider));
        }

        arrayList.add(new ThemeDescription(textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb));
        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked));
        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        arrayList.add(new ThemeDescription(checkCell1, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(checkCell1, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));

        arrayList.add(new ThemeDescription(settingsCell1, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(settingsCell1, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(settingsCell1, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(payTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText6));

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextPriceCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextPriceCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextPriceCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextPriceCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextPriceCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(detailSettingsCell[0], ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(detailSettingsCell[0], ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));

        for (int a = 1; a < detailSettingsCell.length; a++) {
            arrayList.add(new ThemeDescription(detailSettingsCell[a], ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(detailSettingsCell[a], 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(detailSettingsCell[a], 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        }

        arrayList.add(new ThemeDescription(paymentInfoCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(paymentInfoCell, 0, new Class[]{PaymentInfoCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(paymentInfoCell, 0, new Class[]{PaymentInfoCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(paymentInfoCell, 0, new Class[]{PaymentInfoCell.class}, new String[]{"detailExTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(bottomLayout, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(bottomLayout, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));

        return arrayList.toArray(new ThemeDescription[arrayList.size()]);
    }
}

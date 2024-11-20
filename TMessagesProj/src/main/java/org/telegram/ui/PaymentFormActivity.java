/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.exception.APIConnectionException;
import com.stripe.android.exception.APIException;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.net.StripeApiHandler;
import com.stripe.android.net.TokenParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EditTextSettingsCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PaymentInfoCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.RecurrentPaymentsAcceptCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextPriceCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

public class PaymentFormActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static List<String> WEBVIEW_PROTOCOLS = Arrays.asList(
            "http",
            "https"
    );
    private final static List<String> BLACKLISTED_PROTOCOLS = Collections.singletonList(
            "tg"
    );

    private final static int STEP_SHIPPING_INFORMATION = 0,
        STEP_SHIPPING_METHODS = 1,
        STEP_PAYMENT_INFO = 2,
        STEP_CONFIRM_PASSWORD = 3,
        STEP_CHECKOUT = 4,
        STEP_RECEIPT = 5,
        STEP_SET_PASSWORD_EMAIL = 6;

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

    private PaymentsClient paymentsClient;

    private EditTextBoldCursor[] inputFields;
    private RadioCell[] radioCells;
    private ActionBarMenuItem doneItem;
    private ContextProgressView progressView;
    private ContextProgressView progressViewButton;
    private AnimatorSet doneItemAnimation;
    private WebView webView;
    private String webViewUrl;
    private boolean shouldNavigateBack;
    private ScrollView scrollView;

    private boolean recurrentAccepted;
    private boolean swipeBackEnabled = true;

    private TextView textView;
    private HeaderCell[] headerCell = new HeaderCell[3];
    private ArrayList<View> dividers = new ArrayList<>();
    private ShadowSectionCell[] sectionCell = new ShadowSectionCell[3];
    private TextCheckCell checkCell1;
    private TextInfoPrivacyCell[] bottomCell = new TextInfoPrivacyCell[3];
    private TextSettingsCell[] settingsCell = new TextSettingsCell[2];
    private FrameLayout googlePayContainer;
    private FrameLayout googlePayButton;
    private LinearLayout linearLayout2;
    private TextPriceCell totalCell;

    private EditTextSettingsCell codeFieldCell;

    private PaymentFormActivityDelegate delegate;

    private TextView payTextView;
    private RecurrentPaymentsAcceptCell recurrentAcceptCell;
    private BottomFrameLayout bottomLayout;
    private PaymentInfoCell paymentInfoCell;
    private TextDetailSettingsCell[] detailSettingsCell = new TextDetailSettingsCell[7];

    private float shiftDp = -4.5f;

    private TLRPC.account_Password currentPassword;
    private boolean waitingForEmail;
    private int emailCodeLength = 6;
    private Runnable shortPollRunnable;
    private boolean loadingPasswordInfo;
    private PaymentFormActivity passwordFragment;

    private String overrideSmartGlocalConnectionUrl;
    private boolean need_card_country;
    private boolean need_card_postcode;
    private boolean need_card_name;
    private String providerApiKey;
    private boolean initGooglePay;

    private TLRPC.User botUser;

    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;
    private boolean ignoreOnCardChange;

    private String currentBotName;
    private String currentItemName;

    private BaseFragment parentFragment;

    private LinearLayout tipLayout;

    private ArrayList<TLRPC.TL_labeledPrice> prices;

    private int currentStep;
    private boolean passwordOk;
    private String paymentJson;
    private String cardName;
    private TLRPC.TL_paymentSavedCredentialsCard savedCredentialsCard;
    private boolean webviewLoading;
    private CountrySelectActivity.Country country;
    private String countryName;
    private String totalPriceDecimal;
    private TLRPC.PaymentForm paymentForm;
    private TLRPC.PaymentReceipt paymentReceipt;
    private TLRPC.TL_payments_validatedRequestedInfo requestedInfo;
    private TLRPC.TL_paymentFormMethod paymentFormMethod;
    private TLRPC.TL_shippingOption shippingOption;
    private Long tipAmount;
    private TLRPC.TL_payments_validateRequestedInfo validateRequest;
    private TLRPC.TL_inputPaymentCredentialsGooglePay googlePayCredentials;
    private String googlePayPublicKey;
    private String googlePayCountryCode;
    private JSONObject googlePayParameters;
    private MessageObject messageObject;
    private String invoiceSlug;
    private boolean donePressed;
    private boolean canceled;

    private String[] totalPrice;

    private boolean isWebView;

    private boolean saveShippingInfo;
    private boolean saveCardInfo;

    private boolean isCheckoutPreview;
    private boolean needPayAfterTransition;

    private Theme.ResourcesProvider resourcesProvider;
    private PaymentFormCallback paymentFormCallback;
    private InvoiceStatus invoiceStatus;
    private boolean paymentStatusSent;
    private TLRPC.InputInvoice invoiceInput;

    private final static int done_button = 1;

    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;

    public enum InvoiceStatus {
        PAID,
        CANCELLED,
        PENDING,
        FAILED
    }

    public interface PaymentFormCallback {
        void onInvoiceStatusChanged(InvoiceStatus status);
    }

    private interface PaymentFormActivityDelegate {
        default boolean didSelectNewCard(String tokenJson, String card, boolean saveCard, TLRPC.TL_inputPaymentCredentialsGooglePay googlePay, TLRPC.TL_paymentSavedCredentialsCard credentialsCard) {
            return false;
        }

        default void didSelectNewAddress(TLRPC.TL_payments_validateRequestedInfo validateRequested) {

        }

        default void onFragmentDestroyed() {

        }

        default void currentPasswordUpdated(TLRPC.account_Password password) {

        }
    }

    private class TelegramWebviewProxy {
        @Keep
        @JavascriptInterface
        public void postEvent(final String eventName, final String eventData) {
            AndroidUtilities.runOnUIThread(() -> {
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
            });
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
            presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, currentPassword));
        }
    }

    public PaymentFormActivity(TLRPC.PaymentReceipt receipt) {
        currentStep = STEP_RECEIPT;
        paymentForm = new TLRPC.PaymentForm();
        paymentReceipt = receipt;
        paymentForm.bot_id = receipt.bot_id;
        paymentForm.invoice = receipt.invoice;
        paymentForm.provider_id = receipt.provider_id;
        paymentForm.users = receipt.users;
        shippingOption = receipt.shipping;
        if (receipt.tip_amount != 0) {
            tipAmount = receipt.tip_amount;
        }
        botUser = getMessagesController().getUser(receipt.bot_id);
        if (botUser != null) {
            currentBotName = botUser.first_name;
        } else {
            currentBotName = "";
        }
        currentItemName = receipt.title;
        if (receipt.info != null) {
            validateRequest = new TLRPC.TL_payments_validateRequestedInfo();

            if (messageObject != null) {
                TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
                inputInvoice.peer = getMessagesController().getInputPeer(receipt.bot_id);
                validateRequest.invoice = inputInvoice;
            } else {
                TLRPC.TL_inputInvoiceSlug inputInvoice = new TLRPC.TL_inputInvoiceSlug();
                inputInvoice.slug = invoiceSlug;
                validateRequest.invoice = inputInvoice;
            }
            validateRequest.info = receipt.info;
        }
        cardName = receipt.credentials_title;
    }

    public PaymentFormActivity(TLRPC.PaymentForm form, String invoiceSlug, BaseFragment parentFragment) {
        this(form, null, invoiceSlug, parentFragment);
   }

    public PaymentFormActivity(TLRPC.PaymentForm form, TLRPC.InputInvoice premiumGiftCode, BaseFragment parentFragment) {
        this(premiumGiftCode, form, null, null, STEP_CHECKOUT, null, null, null, null, null, null, false, null, parentFragment);
        isCheckoutPreview = true;
    }

    public PaymentFormActivity(TLRPC.PaymentForm form, MessageObject message, BaseFragment parentFragment) {
        this(form, message, null, parentFragment);
    }

    public PaymentFormActivity(TLRPC.PaymentForm form, MessageObject message, String invoiceSlug, BaseFragment parentFragment) {
        isCheckoutPreview = true;
        init(null, form, message, invoiceSlug, STEP_CHECKOUT, null, null, null, null, null, null, false, null, parentFragment);
    }

    private PaymentFormActivity(TLRPC.InputInvoice premiumGiftCode, TLRPC.PaymentForm form, MessageObject message, String invoiceSlug, int step, TLRPC.TL_payments_validatedRequestedInfo validatedRequestedInfo, TLRPC.TL_shippingOption shipping, Long tips, String tokenJson, String card, TLRPC.TL_payments_validateRequestedInfo request, boolean saveCard, TLRPC.TL_inputPaymentCredentialsGooglePay googlePay, BaseFragment parent) {
        init(premiumGiftCode, form, message, invoiceSlug, step, validatedRequestedInfo, shipping, tips, tokenJson, card, request, saveCard, googlePay, parent);
    }

    public void setPaymentFormCallback(PaymentFormCallback callback) {
        paymentFormCallback = callback;
    }

    private void setCurrentPassword(TLRPC.account_Password password) {
        if (password != null && password.has_password) {
            if (getParentActivity() == null) {
                return;
            }
            goToNextStep();
        } else {
            currentPassword = password;
            waitingForEmail = currentPassword != null && !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
            updatePasswordFields();
        }
    }

    private void setDelegate(PaymentFormActivityDelegate paymentFormActivityDelegate) {
        delegate = paymentFormActivityDelegate;
    }
    
    public void setResourcesProvider(Theme.ResourcesProvider provider) {
        resourcesProvider = provider;
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    private void init(TLRPC.InputInvoice premiumGiftCode, TLRPC.PaymentForm form, MessageObject message, String slug, int step, TLRPC.TL_payments_validatedRequestedInfo validatedRequestedInfo, TLRPC.TL_shippingOption shipping, Long tips, String tokenJson, String card, TLRPC.TL_payments_validateRequestedInfo request, boolean saveCard, TLRPC.TL_inputPaymentCredentialsGooglePay googlePay, BaseFragment parent) {
        currentStep = step;
        parentFragment = parent;
        paymentJson = tokenJson;
        googlePayCredentials = googlePay;
        requestedInfo = validatedRequestedInfo;
        paymentForm = form;
        shippingOption = shipping;
        tipAmount = tips;
        messageObject = message;
        invoiceSlug = slug;
        invoiceInput = premiumGiftCode;
        saveCardInfo = saveCard;
        isWebView = !"stripe".equals(paymentForm.native_provider) && !"smartglocal".equals(paymentForm.native_provider);
        botUser = getMessagesController().getUser(form.bot_id);
        if (botUser != null) {
            currentBotName = botUser.first_name;
        } else {
            currentBotName = "";
        }
        currentItemName = form.title;
        validateRequest = request;
        saveShippingInfo = true;
        if (saveCard || currentStep == STEP_CHECKOUT) {
            saveCardInfo = saveCard;
        } else {
            saveCardInfo = !paymentForm.saved_credentials.isEmpty();
        }
        if (card == null) {
            if (!paymentForm.saved_credentials.isEmpty()) {
                savedCredentialsCard = paymentForm.saved_credentials.get(0);
                cardName = savedCredentialsCard.title;
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
                if ((currentStep == STEP_PAYMENT_INFO || currentStep == STEP_SET_PASSWORD_EMAIL) && !paymentForm.invoice.test) {
                    getParentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                } else if (SharedConfig.passcodeHash.length() == 0 || SharedConfig.allowScreenCapture) {
                    getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public View createView(Context context) {
        switch (currentStep) {
            case STEP_SHIPPING_INFORMATION:
                actionBar.setTitle(LocaleController.getString(R.string.PaymentShippingInfo));
                break;
            case STEP_SHIPPING_METHODS:
                actionBar.setTitle(LocaleController.getString(R.string.PaymentShippingMethod));
                break;
            case STEP_PAYMENT_INFO:
            case STEP_CONFIRM_PASSWORD:
                if (paymentFormMethod != null && !TextUtils.isEmpty(paymentFormMethod.title)) {
                    actionBar.setTitle(paymentFormMethod.title);
                } else {
                    actionBar.setTitle(LocaleController.getString(R.string.PaymentCardInfo));
                }
                break;
            case STEP_CHECKOUT:
                if (paymentForm.invoice.test) {
                    actionBar.setTitle("Test " + LocaleController.getString(R.string.PaymentCheckout));
                } else {
                    actionBar.setTitle(LocaleController.getString(R.string.PaymentCheckout));
                }
                break;
            case STEP_RECEIPT:
                if (paymentForm.invoice.test) {
                    actionBar.setTitle("Test " + LocaleController.getString(R.string.PaymentReceipt));
                } else {
                    actionBar.setTitle(LocaleController.getString(R.string.PaymentReceipt));
                }
                break;
            case STEP_SET_PASSWORD_EMAIL:
                actionBar.setTitle(LocaleController.getString(R.string.PaymentPassword));
                break;
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
                    if (currentStep != STEP_CONFIRM_PASSWORD) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    switch (currentStep) {
                        case STEP_SHIPPING_INFORMATION:
                            setDonePressed(true);
                            sendForm();
                            break;
                        case STEP_SHIPPING_METHODS:
                            for (int a = 0; a < radioCells.length; a++) {
                                if (radioCells[a].isChecked()) {
                                    shippingOption = requestedInfo.shipping_options.get(a);
                                    break;
                                }
                            }
                            goToNextStep();
                            break;
                        case STEP_PAYMENT_INFO:
                            sendCardData();
                            break;
                        case STEP_CONFIRM_PASSWORD:
                            checkPassword();
                            break;
                        case STEP_SET_PASSWORD_EMAIL:
                            sendSavePassword(false);
                            break;
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        switch (currentStep) {
            case STEP_SHIPPING_INFORMATION:
            case STEP_SHIPPING_METHODS:
            case STEP_PAYMENT_INFO:
            case STEP_CONFIRM_PASSWORD:
            case STEP_CHECKOUT:
            case STEP_SET_PASSWORD_EMAIL:
                doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
                progressView = new ContextProgressView(context, 1);
                progressView.setAlpha(0.0f);
                progressView.setScaleX(0.1f);
                progressView.setScaleY(0.1f);
                progressView.setVisibility(View.INVISIBLE);
                doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
        }

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, getThemedColor(Theme.key_actionBarDefault));
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, currentStep == STEP_CHECKOUT ? 48 : 0));

        linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setClipChildren(false);
        scrollView.addView(linearLayout2, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (currentStep == STEP_SHIPPING_INFORMATION) {
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

            Collections.sort(countriesArray, String::compareTo);

            inputFields = new EditTextBoldCursor[FIELDS_COUNT_ADDRESS];
            for (int a = 0; a < FIELDS_COUNT_ADDRESS; a++) {
                if (a == FIELD_STREET1) {
                    headerCell[0] = new HeaderCell(context, resourcesProvider);
                    headerCell[0].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    headerCell[0].setText(LocaleController.getString(R.string.PaymentShippingAddress));
                    linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else if (a == FIELD_NAME) {
                    sectionCell[0] = new ShadowSectionCell(context, resourcesProvider);
                    linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    headerCell[1] = new HeaderCell(context, resourcesProvider);
                    headerCell[1].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    headerCell[1].setText(LocaleController.getString(R.string.PaymentShippingReceiver));
                    linearLayout2.addView(headerCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                ViewGroup container;
                if (a == FIELD_PHONECODE) {
                    container = new LinearLayout(context);
                    container.setClipChildren(false);
                    ((LinearLayout) container).setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    container.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                } else if (a == FIELD_PHONE) {
                    container = (ViewGroup) inputFields[FIELD_PHONECODE].getParent();
                } else {
                    container = new FrameLayout(context);
                    container.setClipChildren(false);
                    linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    container.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

                    boolean allowDivider = a != FIELD_POSTCODE;
                    if (allowDivider) {
                        if (a == FIELD_EMAIL && !paymentForm.invoice.phone_requested) {
                            allowDivider = false;
                        } else if (a == FIELD_NAME && !paymentForm.invoice.phone_requested && !paymentForm.invoice.email_requested) {
                            allowDivider = false;
                        }
                    }
                    if (allowDivider) {
                        View divider = new View(context) {
                            @Override
                            protected void onDraw(Canvas canvas) {
                                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
                            }
                        };
                        divider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        dividers.add(divider);
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
                inputFields[a].setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
                inputFields[a].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setBackgroundDrawable(null);
                inputFields[a].setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                inputFields[a].setCursorWidth(1.5f);
                if (a == FIELD_COUNTRY) {
                    inputFields[a].setOnTouchListener((v, event) -> {
                        if (getParentActivity() == null) {
                            return false;
                        }
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            CountrySelectActivity fragment = new CountrySelectActivity(false);
                            fragment.setDisableAnonymousNumbers(true);
                            fragment.setCountrySelectActivityDelegate((country) -> {
                                this.country = country;
                                inputFields[FIELD_COUNTRY].setText(country.name);
                                countryName = country.shortname;
                            });
                            presentFragment(fragment);
                        }
                        return true;
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
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingName));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.name != null) {
                            inputFields[a].setText(paymentForm.saved_info.name);
                        }
                        break;
                    case FIELD_EMAIL:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingEmailPlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.email != null) {
                            inputFields[a].setText(paymentForm.saved_info.email);
                        }
                        break;
                    case FIELD_STREET1:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingAddress1Placeholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.street_line1);
                        }
                        break;
                    case FIELD_STREET2:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingAddress2Placeholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.street_line2);
                        }
                        break;
                    case FIELD_CITY:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingCityPlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.city);
                        }
                        break;
                    case FIELD_STATE:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingStatePlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.state);
                        }
                        break;
                    case FIELD_COUNTRY:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingCountry));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            String value = countryMap.get(paymentForm.saved_info.shipping_address.country_iso2);
                            countryName = paymentForm.saved_info.shipping_address.country_iso2;
                            inputFields[a].setText(value != null ? value : countryName);
                        }
                        break;
                    case FIELD_POSTCODE:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingZipPlaceholder));
                        if (paymentForm.saved_info != null && paymentForm.saved_info.shipping_address != null) {
                            inputFields[a].setText(paymentForm.saved_info.shipping_address.post_code);
                        }
                        break;
                }
                inputFields[a].setSelection(inputFields[a].length());

                if (a == FIELD_PHONECODE) {
                    textView = new TextView(context);
                    textView.setText("+");
                    textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    container.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 21, 12, 0, 6));

                    inputFields[a].setPadding(AndroidUtilities.dp(10), 0, 0, 0);
                    inputFields[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    InputFilter[] inputFilters = new InputFilter[1];
                    inputFilters[0] = new InputFilter.LengthFilter(5);
                    inputFields[a].setFilters(inputFilters);
                    container.addView(inputFields[a], LayoutHelper.createLinear(55, LayoutHelper.WRAP_CONTENT, 0, 12, 21, 6));
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
                                phoneField.setHint(LocaleController.getString(R.string.PaymentShippingPhoneNumber));
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
                                            textToSet = text.substring(a) + inputFields[FIELD_PHONE].getText().toString();
                                            inputFields[FIELD_PHONECODE].setText(text = sub);
                                            break;
                                        }
                                    }
                                    if (!ok) {
                                        textToSet = text.substring(1) + inputFields[FIELD_PHONE].getText().toString();
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
                                    phoneField.setHint(LocaleController.getString(R.string.PaymentShippingPhoneNumber));
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
                    container.addView(inputFields[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 21, 6));
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
                                str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
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
                                phoneField.setSelection(Math.min(start, phoneField.length()));
                            }
                            phoneField.onTextChange();
                            ignoreOnPhoneChange = false;
                        }
                    });
                } else {
                    inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                    inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));
                }

                inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
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

                        bottomCell[1] = new TextInfoPrivacyCell(context, resourcesProvider);
                        bottomCell[1].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        linearLayout2.addView(bottomCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        if (paymentForm.invoice.email_to_provider && paymentForm.invoice.phone_to_provider) {
                            bottomCell[1].setText(LocaleController.formatString("PaymentPhoneEmailToProvider", R.string.PaymentPhoneEmailToProvider, providerName));
                        } else if (paymentForm.invoice.email_to_provider) {
                            bottomCell[1].setText(LocaleController.formatString("PaymentEmailToProvider", R.string.PaymentEmailToProvider, providerName));
                        } else {
                            bottomCell[1].setText(LocaleController.formatString("PaymentPhoneToProvider", R.string.PaymentPhoneToProvider, providerName));
                        }
                    } else {
                        sectionCell[1] = new ShadowSectionCell(context, resourcesProvider);
                        linearLayout2.addView(sectionCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    checkCell1 = new TextCheckCell(context, resourcesProvider);
                    checkCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                    checkCell1.setTextAndCheck(LocaleController.getString(R.string.PaymentShippingSave), saveShippingInfo, false);
                    linearLayout2.addView(checkCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    checkCell1.setOnClickListener(v -> {
                        saveShippingInfo = !saveShippingInfo;
                        checkCell1.setChecked(saveShippingInfo);
                    });

                    bottomCell[0] = new TextInfoPrivacyCell(context, resourcesProvider);
                    bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    bottomCell[0].setText(LocaleController.getString(R.string.PaymentShippingSaveInfo));
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
        } else if (currentStep == STEP_PAYMENT_INFO) {
            if (paymentForm.native_params != null) {
                try {
                    JSONObject jsonObject = new JSONObject(paymentForm.native_params.data);
                    String googlePayKey = jsonObject.optString("google_pay_public_key");
                    if (!TextUtils.isEmpty(googlePayKey)) {
                        googlePayPublicKey = googlePayKey;
                    }
                    googlePayCountryCode = jsonObject.optString("acquirer_bank_country");
                    googlePayParameters = jsonObject.optJSONObject("gpay_parameters");
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (isWebView || paymentFormMethod != null) {
                if (googlePayPublicKey != null || googlePayParameters != null) {
                    initGooglePay(context);
                }
                createGooglePayButton(context);
                linearLayout2.addView(googlePayContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

                webviewLoading = true;
                showEditDoneProgress(true, true);
                progressView.setVisibility(View.VISIBLE);
                doneItem.setEnabled(false);
                doneItem.getContentView().setVisibility(View.INVISIBLE);
                AndroidUtilities.checkAndroidTheme(context, true);
                webView = new WebView(context) {
                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        ((ViewGroup) fragmentView).requestDisallowInterceptTouchEvent(true);
                        return super.onTouchEvent(event);
                    }

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                };
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);

                webView.getSettings().setSupportZoom(true);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setDisplayZoomControls(false);
                webView.getSettings().setUseWideViewPort(true);

                if (Build.VERSION.SDK_INT >= 21) {
                    webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.setAcceptThirdPartyCookies(webView, true);
                }
                if (Build.VERSION.SDK_INT >= 17) {
                    webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");
                }
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        if (!AndroidUtilities.isSafeToShow(getContext())) {
                            return true;
                        }
                        new AlertDialog.Builder(getContext(), resourcesProvider)
                                .setTitle(getString(R.string.ChromeCrashTitle))
                                .setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.ChromeCrashMessage), () -> Browser.openUrl(getContext(), "https://play.google.com/store/apps/details?id=com.google.android.webview")))
                                .setPositiveButton(getString(R.string.OK), null)
                                .show();
                        return true;
                    }

                    @Override
                    public void onLoadResource(WebView view, String url) {
                        super.onLoadResource(view, url);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        shouldNavigateBack = !url.equals(webViewUrl);
                        try {
                            Uri uri = Uri.parse(url);
                            if ("t.me".equals(uri.getHost())) {
                                goToNextStep();
                                return true;
                            }

                            if (BLACKLISTED_PROTOCOLS.contains(uri.getScheme())) {
                                return true;
                            }
                            if (!WEBVIEW_PROTOCOLS.contains(uri.getScheme())) {
                                try {
                                    if (getContext() instanceof Activity) {
                                        ((Activity) getContext()).startActivityForResult(new Intent(Intent.ACTION_VIEW, uri), BasePermissionsActivity.REQUEST_CODE_PAYMENT_FORM);
                                    }
                                } catch (ActivityNotFoundException e) {
                                    new AlertDialog.Builder(context)
                                            .setTitle(currentBotName)
                                            .setMessage(LocaleController.getString(R.string.PaymentAppNotFoundForDeeplink))
                                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                                            .show();
                                }
                                return true;
                            }
                        } catch (Exception ignore) {

                        }
                        return super.shouldOverrideUrlLoading(view, url);
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

                sectionCell[2] = new ShadowSectionCell(context, resourcesProvider);
                linearLayout2.addView(sectionCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                checkCell1 = new TextCheckCell(context, resourcesProvider);
                checkCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                checkCell1.setTextAndCheck(LocaleController.getString(R.string.PaymentCardSavePaymentInformation), saveCardInfo, false);
                linearLayout2.addView(checkCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                checkCell1.setOnClickListener(v -> {
                    saveCardInfo = !saveCardInfo;
                    checkCell1.setChecked(saveCardInfo);
                });

                bottomCell[0] = new TextInfoPrivacyCell(context, resourcesProvider);
                bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
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
                        if (jsonObject.has("public_token")) {
                            providerApiKey = jsonObject.getString("public_token");
                        } else {
                            try {
                                providerApiKey = jsonObject.getString("publishable_key");
                            } catch (Exception e) {
                                providerApiKey = "";
                            }
                        }
                        initGooglePay = !jsonObject.optBoolean("google_pay_hidden", false);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                if (initGooglePay && (!TextUtils.isEmpty(providerApiKey) && "stripe".equals(paymentForm.native_provider) || googlePayParameters != null)) {
                    initGooglePay(context);
                }

                inputFields = new EditTextBoldCursor[FIELDS_COUNT_CARD];
                for (int a = 0; a < FIELDS_COUNT_CARD; a++) {
                    if (a == FIELD_CARD) {
                        headerCell[0] = new HeaderCell(context, resourcesProvider);
                        headerCell[0].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        headerCell[0].setText(LocaleController.getString(R.string.PaymentCardTitle));
                        linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    } else if (a == FIELD_CARD_COUNTRY) {
                        headerCell[1] = new HeaderCell(context, resourcesProvider);
                        headerCell[1].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        headerCell[1].setText(LocaleController.getString(R.string.PaymentBillingAddress));
                        linearLayout2.addView(headerCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    boolean allowDivider = a != FIELD_CVV && a != FIELD_CARD_POSTCODE && !(a == FIELD_CARD_COUNTRY && !need_card_postcode);
                    ViewGroup container = new FrameLayout(context);
                    container.setClipChildren(false);
                    container.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

                    View.OnTouchListener onTouchListener = null;
                    inputFields[a] = new EditTextBoldCursor(context);
                    inputFields[a].setTag(a);
                    inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    inputFields[a].setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
                    inputFields[a].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                    inputFields[a].setBackgroundDrawable(null);
                    inputFields[a].setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
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
                        inputFields[a].setInputType(InputType.TYPE_CLASS_PHONE);
                    } else if (a == FIELD_CARD_COUNTRY) {
                        inputFields[a].setOnTouchListener((v, event) -> {
                            if (getParentActivity() == null) {
                                return false;
                            }
                            if (event.getAction() == MotionEvent.ACTION_UP) {
                                CountrySelectActivity fragment = new CountrySelectActivity(false);
                                fragment.setDisableAnonymousNumbers(true);
                                fragment.setCountrySelectActivityDelegate((country) -> {
                                    this.country = country;
                                    inputFields[FIELD_CARD_COUNTRY].setText(country.name);
                                });
                                presentFragment(fragment);
                            }
                            return true;
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
                            inputFields[a].setHint(LocaleController.getString(R.string.PaymentCardNumber));
                            break;
                        case FIELD_CVV:
                            inputFields[a].setHint(LocaleController.getString(R.string.PaymentCardCvv));
                            break;
                        case FIELD_EXPIRE_DATE:
                            inputFields[a].setHint(LocaleController.getString(R.string.PaymentCardExpireDate));
                            break;
                        case FIELD_CARDNAME:
                            inputFields[a].setHint(LocaleController.getString(R.string.PaymentCardName));
                            break;
                        case FIELD_CARD_POSTCODE:
                            inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingZipPlaceholder));
                            break;
                        case FIELD_CARD_COUNTRY:
                            inputFields[a].setHint(LocaleController.getString(R.string.PaymentShippingCountry));
                            break;
                    }

                    if (a == FIELD_CARD) {
                        inputFields[a].addTextChangedListener(new TextWatcher() {

                            public final String[] PREFIXES_15 = {"34", "37"};
                            public final String[] PREFIXES_14 = {"300", "301", "302", "303", "304", "305", "309", "36", "38", "39"};
                            public final String[] PREFIXES_16 = {
                                    "2221", "2222", "2223", "2224", "2225", "2226", "2227", "2228", "2229",
                                    "2200", "2201", "2202", "2203", "2204",
                                    "8600", "9860",
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
                            public void afterTextChanged(Editable editable) {
                                if (ignoreOnCardChange) {
                                    return;
                                }
                                EditText phoneField = inputFields[FIELD_CARD];
                                int start = phoneField.getSelectionStart();
                                String phoneChars = "0123456789";
                                String str = phoneField.getText().toString();
                                if (characterAction == 3) {
                                    str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
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
                                        String[] checkArr;
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
                                    if (builder.length() > maxLength) {
                                        builder.setLength(maxLength);
                                    }
                                }
                                if (hint != null) {
                                    if (builder.length() == maxLength) {
                                        inputFields[FIELD_EXPIRE_DATE].requestFocus();
                                    }
                                    phoneField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
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
//                                    phoneField.setTextColor(builder.length() > 0 ? getThemedColor(Theme.key_text_RedRegular) : getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                                }
                                if (!builder.toString().equals(editable.toString())) {
                                    editable.replace(0, editable.length(), builder);
                                }
                                if (start >= 0) {
                                    phoneField.setSelection(Math.min(start, phoneField.length()));
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
                                    str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
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
                                inputFields[FIELD_EXPIRE_DATE].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
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
                                        // Only check for >= 01/22 then
                                        boolean bypass = UserConfig.getInstance(currentAccount).getClientPhone().startsWith("7") || country != null && country.code.equals("7");
                                        int currentYear = bypass ? 2022 : rightNow.get(Calendar.YEAR);
                                        int currentMonth = bypass ? 1 : rightNow.get(Calendar.MONTH) + 1;
                                        if (year < currentYear || year == currentYear && month < currentMonth) {
                                            inputFields[FIELD_EXPIRE_DATE].setTextColor(getThemedColor(Theme.key_text_RedRegular));
                                            isError = true;
                                        }
                                    } else {
                                        int value = Utilities.parseInt(args[0]);
                                        if (value > 12 || value == 0) {
                                            inputFields[FIELD_EXPIRE_DATE].setTextColor(getThemedColor(Theme.key_text_RedRegular));
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
                                            inputFields[FIELD_EXPIRE_DATE].setTextColor(getThemedColor(Theme.key_text_RedRegular));
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
                                    phoneField.setSelection(Math.min(start, phoneField.length()));
                                }
                                ignoreOnCardChange = false;
                            }
                        });
                    }
                    inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                    inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));

                    inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
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
                    });
                    if (a == FIELD_CVV) {
                        sectionCell[0] = new ShadowSectionCell(context, resourcesProvider);
                        linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    } else if (a == FIELD_CARD_POSTCODE) {
                        sectionCell[2] = new ShadowSectionCell(context, resourcesProvider);
                        linearLayout2.addView(sectionCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                        checkCell1 = new TextCheckCell(context, resourcesProvider);
                        checkCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        checkCell1.setTextAndCheck(LocaleController.getString(R.string.PaymentCardSavePaymentInformation), saveCardInfo, false);
                        linearLayout2.addView(checkCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        checkCell1.setOnClickListener(v -> {
                            saveCardInfo = !saveCardInfo;
                            checkCell1.setChecked(saveCardInfo);
                        });

                        bottomCell[0] = new TextInfoPrivacyCell(context, resourcesProvider);
                        bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        updateSavePaymentField();
                        linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    } else if (a == FIELD_CARD) {
                        createGooglePayButton(context);
                        container.addView(googlePayContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 0, 0, 4, 0));
                    }

                    if (allowDivider) {
                        View divider = new View(context) {
                            @Override
                            protected void onDraw(Canvas canvas) {
                                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
                            }
                        };
                        divider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        dividers.add(divider);
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
        } else if (currentStep == STEP_SHIPPING_METHODS) {
            int count = requestedInfo.shipping_options == null ? 0 : requestedInfo.shipping_options.size();
            radioCells = new RadioCell[count];
            for (int a = 0; a < count; a++) {
                TLRPC.TL_shippingOption shippingOption = requestedInfo.shipping_options.get(a);
                radioCells[a] = new RadioCell(context);
                radioCells[a].setTag(a);
                radioCells[a].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                radioCells[a].setText(String.format("%s - %s", getTotalPriceString(shippingOption.prices), shippingOption.title), a == 0, a != count - 1);
                radioCells[a].setOnClickListener(v -> {
                    int num = (Integer) v.getTag();
                    for (int a1 = 0; a1 < radioCells.length; a1++) {
                        radioCells[a1].setChecked(num == a1, true);
                    }
                });
                linearLayout2.addView(radioCells[a]);
            }
            bottomCell[0] = new TextInfoPrivacyCell(context, resourcesProvider);
            bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else if (currentStep == STEP_CONFIRM_PASSWORD) {
            inputFields = new EditTextBoldCursor[FIELDS_COUNT_SAVEDCARD];
            for (int a = 0; a < FIELDS_COUNT_SAVEDCARD; a++) {
                if (a == FIELD_SAVEDCARD) {
                    headerCell[0] = new HeaderCell(context, resourcesProvider);
                    headerCell[0].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    headerCell[0].setText(LocaleController.getString(R.string.PaymentCardTitle));
                    linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }

                ViewGroup container = new FrameLayout(context);
                container.setClipChildren(false);
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                container.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

                boolean allowDivider = a != FIELD_SAVEDPASSWORD;
                if (allowDivider) {
                    if (a == FIELD_EMAIL && !paymentForm.invoice.phone_requested) {
                        allowDivider = false;
                    } else if (a == FIELD_NAME && !paymentForm.invoice.phone_requested && !paymentForm.invoice.email_requested) {
                        allowDivider = false;
                    }
                }
                if (allowDivider) {
                    View divider = new View(context) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
                        }
                    };
                    divider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    dividers.add(divider);
                    container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
                }

                inputFields[a] = new EditTextBoldCursor(context);
                inputFields[a].setTag(a);
                inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                inputFields[a].setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
                inputFields[a].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setBackgroundDrawable(null);
                inputFields[a].setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                inputFields[a].setCursorWidth(1.5f);
                if (a == FIELD_SAVEDCARD) {
                    inputFields[a].setOnTouchListener((v, event) -> true);
                    inputFields[a].setInputType(0);
                } else {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputFields[a].setTypeface(Typeface.DEFAULT);
                }
                inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                switch (a) {
                    case FIELD_SAVEDCARD:
                        inputFields[a].setText(savedCredentialsCard == null ? "" : savedCredentialsCard.title);
                        break;
                    case FIELD_SAVEDPASSWORD:
                        inputFields[a].setHint(LocaleController.getString(R.string.LoginPassword));
                        inputFields[a].requestFocus();
                        break;
                }

                inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));

                inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        doneItem.performClick();
                        return true;
                    }
                    return false;
                });
                if (a == FIELD_SAVEDPASSWORD) {
                    bottomCell[0] = new TextInfoPrivacyCell(context, resourcesProvider);
                    bottomCell[0].setText(LocaleController.formatString("PaymentConfirmationMessage", R.string.PaymentConfirmationMessage, savedCredentialsCard == null ? "" : savedCredentialsCard.title));
                    bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    settingsCell[0] = new TextSettingsCell(context, resourcesProvider);
                    settingsCell[0].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                    settingsCell[0].setText(LocaleController.getString(R.string.PaymentConfirmationNewCard), false);
                    linearLayout2.addView(settingsCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    settingsCell[0].setOnClickListener(v -> {
                        passwordOk = false;
                        goToNextStep();
                    });

                    bottomCell[1] = new TextInfoPrivacyCell(context, resourcesProvider);
                    bottomCell[1].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            }
        } else if (currentStep == STEP_CHECKOUT || currentStep == STEP_RECEIPT) {
            paymentInfoCell = new PaymentInfoCell(context);
            paymentInfoCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            if (messageObject != null) {
                paymentInfoCell.setInvoice((TLRPC.TL_messageMediaInvoice) messageObject.messageOwner.media, currentBotName);
            } else if (paymentReceipt != null) {
                paymentInfoCell.setReceipt(paymentReceipt, currentBotName);
            } else if (invoiceSlug != null || invoiceInput != null) {
                paymentInfoCell.setInfo(paymentForm.title, paymentForm.description, paymentForm.photo, currentBotName, paymentForm);
            }
            linearLayout2.addView(paymentInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            sectionCell[0] = new ShadowSectionCell(context, resourcesProvider);
            linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            prices = new ArrayList<>(paymentForm.invoice.prices);
            if (shippingOption != null) {
                prices.addAll(shippingOption.prices);
            }
            totalPrice = new String[1];

            for (int a = 0; a < prices.size(); a++) {
                TLRPC.TL_labeledPrice price = prices.get(a);

                TextPriceCell priceCell = new TextPriceCell(context);
                priceCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                priceCell.setTextAndValue(price.label, LocaleController.getInstance().formatCurrencyString(price.amount, paymentForm.invoice.currency), false);
                linearLayout2.addView(priceCell);
            }

            if (currentStep == STEP_RECEIPT && tipAmount != null) {
                TextPriceCell priceCell = new TextPriceCell(context);
                priceCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                priceCell.setTextAndValue(LocaleController.getString(R.string.PaymentTip), LocaleController.getInstance().formatCurrencyString(tipAmount, paymentForm.invoice.currency), false);
                linearLayout2.addView(priceCell);
            }

            totalCell = new TextPriceCell(context);
            totalCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            totalPrice[0] = getTotalPriceString(prices);
            totalCell.setTextAndValue(LocaleController.getString(R.string.PaymentTransactionTotal), totalPrice[0], true);

            if (currentStep == STEP_CHECKOUT && (paymentForm.invoice.flags & 256) != 0) {
                ViewGroup container = new FrameLayout(context);
                container.setClipChildren(false);
                container.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, paymentForm.invoice.suggested_tip_amounts.isEmpty() ? 40 : 78));
                container.setOnClickListener(v -> {
                    inputFields[0].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[0]);
                });

                TextPriceCell cell = new TextPriceCell(context);
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                cell.setTextAndValue(LocaleController.getString(R.string.PaymentTipOptional), "", false);
                container.addView(cell);

                inputFields = new EditTextBoldCursor[1];
                inputFields[0] = new EditTextBoldCursor(context);
                inputFields[0].setTag(0);
                inputFields[0].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                inputFields[0].setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                inputFields[0].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                inputFields[0].setBackgroundDrawable(null);
                inputFields[0].setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[0].setCursorSize(AndroidUtilities.dp(20));
                inputFields[0].setCursorWidth(1.5f);
                inputFields[0].setInputType(InputType.TYPE_CLASS_PHONE);
                inputFields[0].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                inputFields[0].setHint(LocaleController.getInstance().formatCurrencyString(0, paymentForm.invoice.currency));
                inputFields[0].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                inputFields[0].setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                container.addView(inputFields[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 9, 21, 1));
                inputFields[0].addTextChangedListener(new TextWatcher() {

                    private boolean anyBefore;
                    private String overrideText;
                    private boolean isDeletedChar;
                    private int beforeTextLength;
                    private int enteredCharacterStart;
                    private boolean lastDotEntered;

                    char[] commas = new char[]{',', '.', '٫', '、', '\u2E41', '︐', '︑', '﹐', '﹑', '，', '､', 'ʻ'};

                    private int indexOfComma(String text) {
                        for (int a = 0; a < commas.length; a++) {
                            int idx = text.indexOf(commas[a]);
                            if (idx >= 0) {
                                return idx;
                            }
                        }
                        return -1;
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        anyBefore = !TextUtils.isEmpty(s);
                        overrideText = null;
                        beforeTextLength = s == null ? 0 : s.length();
                        enteredCharacterStart = start;
                        if (isDeletedChar = (count == 1 && after == 0)) {
                            String fixed = LocaleController.fixNumbers(s);
                            char actionCh = fixed.charAt(start);
                            int idx = indexOfComma(fixed);
                            String reminderStr = idx >= 0 ? fixed.substring(idx + 1) : "";
                            long reminder = Utilities.parseLong(PhoneFormat.stripExceptNumbers(reminderStr));
                            if ((actionCh < '0' || actionCh > '9') && (reminderStr.length() == 0 || reminder != 0)) {
                                while (--start >= 0) {
                                    actionCh = fixed.charAt(start);
                                    if (actionCh >= '0' && actionCh <= '9') {
                                        overrideText = fixed.substring(0, start) + fixed.substring(start + 1);
                                        break;
                                    }
                                }
                            } else if (idx > 0 && start > idx && reminder == 0) {
                                overrideText = fixed.substring(0, idx - 1);
                            }
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        long oldAmount = tipAmount != null ? tipAmount : 0;

                        String text;
                        if (overrideText != null) {
                            text = overrideText;
                        } else {
                            text = LocaleController.fixNumbers(s.toString());
                        }
                        int idx = indexOfComma(text);
                        boolean dotEntered = idx >= 0;
                        int exp = LocaleController.getCurrencyExpDivider(paymentForm.invoice.currency);
                        String wholeStr = idx >= 0 ? text.substring(0, idx) : text;
                        String reminderStr = idx >= 0 ? text.substring(idx + 1) : "";
                        long whole = Utilities.parseLong(PhoneFormat.stripExceptNumbers(wholeStr)) * exp;
                        long reminder = Utilities.parseLong(PhoneFormat.stripExceptNumbers(reminderStr));
                        reminderStr = "" + reminder;
                        String expStr = "" + (exp - 1);
                        if (idx > 0 && reminderStr.length() > expStr.length()) {
                            if (enteredCharacterStart - idx < reminderStr.length()) {
                                reminderStr = reminderStr.substring(0, expStr.length());
                            } else {
                                reminderStr = reminderStr.substring(reminderStr.length() - expStr.length());
                            }
                            reminder = Utilities.parseLong(reminderStr);
                        }
                        tipAmount = whole + reminder;
                        if (paymentForm.invoice.max_tip_amount != 0 && tipAmount > paymentForm.invoice.max_tip_amount) {
                            tipAmount = paymentForm.invoice.max_tip_amount;
                        }
                        int start = inputFields[0].getSelectionStart();
                        ignoreOnTextChange = true;
                        String newText;
                        if (tipAmount == 0) {
                            inputFields[0].setText(newText = "");
                        } else {
                            inputFields[0].setText(newText = LocaleController.getInstance().formatCurrencyString(tipAmount, false, dotEntered, true, paymentForm.invoice.currency));
                        }
                        if (oldAmount < tipAmount && oldAmount != 0 && anyBefore && start >= 0) {
                            inputFields[0].setSelection(Math.min(start, inputFields[0].length()));
                        } else if (!isDeletedChar || beforeTextLength == inputFields[0].length()) {
                            if (!lastDotEntered && dotEntered && idx >= 0) {
                                idx = indexOfComma(newText);
                                if (idx > 0) {
                                    inputFields[0].setSelection(idx + 1);
                                } else {
                                    inputFields[0].setSelection(inputFields[0].length());
                                }
                            } else {
                                inputFields[0].setSelection(inputFields[0].length());
                            }
                        } else {
                            inputFields[0].setSelection(Math.max(0, Math.min(start, inputFields[0].length())));
                        }
                        lastDotEntered = dotEntered;
                        updateTotalPrice();
                        overrideText = null;
                        ignoreOnTextChange = false;
                    }
                });
                inputFields[0].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        AndroidUtilities.hideKeyboard(textView);
                        return true;
                    }
                    return false;
                });
                inputFields[0].requestFocus();

                if (!paymentForm.invoice.suggested_tip_amounts.isEmpty()) {
                    HorizontalScrollView scrollView = new HorizontalScrollView(context);
                    scrollView.setHorizontalScrollBarEnabled(false);
                    scrollView.setVerticalScrollBarEnabled(false);
                    scrollView.setClipToPadding(false);
                    scrollView.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
                    scrollView.setFillViewport(true);
                    container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT | Gravity.TOP, 0, 44, 0, 0));
                    int[] maxTextWidth = new int[1];
                    int[] textWidths = new int[1];
                    int N = paymentForm.invoice.suggested_tip_amounts.size();

                    tipLayout = new LinearLayout(context) {

                        boolean ignoreLayout;

                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int availableSize = MeasureSpec.getSize(widthMeasureSpec);
                            ignoreLayout = true;
                            int gaps = AndroidUtilities.dp(9) * (N - 1);
                            if (maxTextWidth[0] * N + gaps <= availableSize) {
                                setWeightSum(1.0f);
                                for (int a = 0, N2 = getChildCount(); a < N2; a++) {
                                    getChildAt(a).getLayoutParams().width = 0;
                                    ((LayoutParams) getChildAt(a).getLayoutParams()).weight = 1.0f / N2;
                                }
                            } else if (textWidths[0] + gaps <= availableSize) {
                                setWeightSum(1.0f);
                                availableSize -= gaps;
                                float extraWeight = 1.0f;
                                for (int a = 0, N2 = getChildCount(); a < N2; a++) {
                                    View child = getChildAt(a);
                                    LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
                                    layoutParams.width = 0;
                                    int width = (Integer) child.getTag(R.id.width_tag);
                                    layoutParams.weight = width / (float) availableSize;
                                    extraWeight -= layoutParams.weight;
                                }
                                extraWeight /= (N - 1);
                                if (extraWeight > 0) {
                                    for (int a = 0, N2 = getChildCount(); a < N2; a++) {
                                        View child = getChildAt(a);
                                        LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
                                        int width = (Integer) child.getTag(R.id.width_tag);
                                        if (width != maxTextWidth[0]) {
                                            layoutParams.weight += extraWeight;
                                        }
                                    }
                                }
                            } else {
                                setWeightSum(0.0f);
                                for (int a = 0, N2 = getChildCount(); a < N2; a++) {
                                    getChildAt(a).getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                                    ((LayoutParams) getChildAt(a).getLayoutParams()).weight = 0.0f;
                                }
                            }
                            ignoreLayout = false;
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        }

                        @Override
                        public void requestLayout() {
                            if (ignoreLayout) {
                                return;
                            }
                            super.requestLayout();
                        }
                    };
                    tipLayout.setOrientation(LinearLayout.HORIZONTAL);
                    scrollView.addView(tipLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT | Gravity.TOP));
                    int color = getThemedColor(Theme.key_contacts_inviteBackground);
                    for (int a = 0; a < N; a++) {
                        long amount;
                        if (LocaleController.isRTL) {
                            amount = paymentForm.invoice.suggested_tip_amounts.get(N - a - 1);
                        } else {
                            amount = paymentForm.invoice.suggested_tip_amounts.get(a);
                        }
                        String text = LocaleController.getInstance().formatCurrencyString(amount, paymentForm.invoice.currency);
                        TextView valueTextView = new TextView(context);
                        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        valueTextView.setTypeface(AndroidUtilities.bold());
                        valueTextView.setLines(1);
                        valueTextView.setTag(amount);
                        valueTextView.setMaxLines(1);
                        valueTextView.setText(text);
                        valueTextView.setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), 0);
                        valueTextView.setTextColor(getThemedColor(Theme.key_chats_secretName));
                        valueTextView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(15), color & 0x1fffffff));
                        valueTextView.setSingleLine(true);
                        valueTextView.setGravity(Gravity.CENTER);
                        tipLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, a != N -1 ? 9 : 0, 0));
                        valueTextView.setOnClickListener(v -> {
                            long amoumt = (Long) valueTextView.getTag();
                            if (tipAmount != null && amoumt == tipAmount) {
                                ignoreOnTextChange = true;
                                inputFields[0].setText("");
                                ignoreOnTextChange = false;
                                tipAmount = 0L;
                                updateTotalPrice();
                            } else {
                                inputFields[0].setText(LocaleController.getInstance().formatCurrencyString(amount, false, true, true, paymentForm.invoice.currency));
                            }
                            inputFields[0].setSelection(inputFields[0].length());
                        });
                        int width = (int) Math.ceil(valueTextView.getPaint().measureText(text)) + AndroidUtilities.dp(30);
                        valueTextView.setTag(R.id.width_tag, width);
                        maxTextWidth[0] = Math.max(maxTextWidth[0], width);
                        textWidths[0] += width;
                    }
                }
            }

            linearLayout2.addView(totalCell);

            sectionCell[2] = new ShadowSectionCell(context, resourcesProvider);
            sectionCell[2].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(sectionCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            detailSettingsCell[0] = new TextDetailSettingsCell(context);
            detailSettingsCell[0].setBackgroundDrawable(Theme.getSelectorDrawable(true));
            detailSettingsCell[0].setTextAndValueAndIcon(cardName != null && cardName.length() > 1 ? cardName.substring(0, 1).toUpperCase() + cardName.substring(1) : cardName, LocaleController.getString(R.string.PaymentCheckoutMethod), R.drawable.msg_payment_card,  true);
            int cardInfoVisibility = View.VISIBLE;
            if (isCheckoutPreview) {
                cardInfoVisibility = cardName != null && cardName.length() > 1 ? View.VISIBLE : View.GONE;
            }
            detailSettingsCell[0].setVisibility(cardInfoVisibility);
            linearLayout2.addView(detailSettingsCell[0]);
            if (currentStep == STEP_CHECKOUT) {
                detailSettingsCell[0].setOnClickListener(v -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    showChoosePaymentMethod();
                });
            }

            TLRPC.User providerUser = null;
            for (int a = 0; a < paymentForm.users.size(); a++) {
                TLRPC.User user = paymentForm.users.get(a);
                if (user.id == paymentForm.provider_id) {
                    providerUser = user;
                }
            }
            detailSettingsCell[1] = new TextDetailSettingsCell(context);
            detailSettingsCell[1].setBackground(Theme.getSelectorDrawable(true));
            String providerName;
            if (providerUser != null) {
                detailSettingsCell[1].setTextAndValueAndIcon(providerName = ContactsController.formatName(providerUser.first_name, providerUser.last_name), LocaleController.getString(R.string.PaymentCheckoutProvider), R.drawable.msg_payment_provider, validateRequest != null && (validateRequest.info.shipping_address != null || shippingOption != null) || paymentForm.saved_info != null && (paymentForm.saved_info.shipping_address != null));
                linearLayout2.addView(detailSettingsCell[1]);
            } else {
                providerName = "";
            }
            detailSettingsCell[1].setVisibility(providerUser != null ? cardInfoVisibility : View.GONE);

            if (validateRequest != null || isCheckoutPreview && paymentForm != null && paymentForm.saved_info != null) {
                TLRPC.TL_paymentRequestedInfo info = validateRequest != null ? validateRequest.info : paymentForm.saved_info;

                detailSettingsCell[2] = new TextDetailSettingsCell(context);
                detailSettingsCell[2].setVisibility(View.GONE);
                linearLayout2.addView(detailSettingsCell[2]);
                if (info.shipping_address != null) {
                    detailSettingsCell[2].setVisibility(View.VISIBLE);
                    if (currentStep == STEP_CHECKOUT) {
                        detailSettingsCell[2].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        detailSettingsCell[2].setOnClickListener(v -> {
                            PaymentFormActivity activity = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_SHIPPING_INFORMATION, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment);
                            activity.setDelegate(new PaymentFormActivityDelegate() {
                                @Override
                                public void didSelectNewAddress(TLRPC.TL_payments_validateRequestedInfo validateRequested) {
                                    validateRequest = validateRequested;
                                    setAddressFields(validateRequest.info);
                                }
                            });
                            presentFragment(activity);
                        });
                    } else {
                        detailSettingsCell[2].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    }
                }

                detailSettingsCell[3] = new TextDetailSettingsCell(context);
                detailSettingsCell[3].setVisibility(View.GONE);
                linearLayout2.addView(detailSettingsCell[3]);
                if (info.name != null) {
                    detailSettingsCell[3].setVisibility(View.VISIBLE);
                    if (currentStep == STEP_CHECKOUT) {
                        detailSettingsCell[3].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        detailSettingsCell[3].setOnClickListener(v -> {
                            PaymentFormActivity activity = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_SHIPPING_INFORMATION, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment);
                            activity.setDelegate(new PaymentFormActivityDelegate() {
                                @Override
                                public void didSelectNewAddress(TLRPC.TL_payments_validateRequestedInfo validateRequested) {
                                    validateRequest = validateRequested;
                                    setAddressFields(validateRequest.info);
                                }
                            });
                            presentFragment(activity);
                        });
                    } else {
                        detailSettingsCell[3].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    }
                }

                detailSettingsCell[4] = new TextDetailSettingsCell(context);
                detailSettingsCell[4].setVisibility(View.GONE);
                linearLayout2.addView(detailSettingsCell[4]);
                if (info.phone != null) {
                    detailSettingsCell[4].setVisibility(View.VISIBLE);
                    if (currentStep == STEP_CHECKOUT) {
                        detailSettingsCell[4].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        detailSettingsCell[4].setOnClickListener(v -> {
                            PaymentFormActivity activity = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_SHIPPING_INFORMATION, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment);
                            activity.setDelegate(new PaymentFormActivityDelegate() {
                                @Override
                                public void didSelectNewAddress(TLRPC.TL_payments_validateRequestedInfo validateRequested) {
                                    validateRequest = validateRequested;
                                    setAddressFields(validateRequest.info);
                                }
                            });
                            presentFragment(activity);
                        });
                    } else {
                        detailSettingsCell[4].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    }
                }

                detailSettingsCell[5] = new TextDetailSettingsCell(context);
                detailSettingsCell[5].setVisibility(View.GONE);
                linearLayout2.addView(detailSettingsCell[5]);
                if (info.email != null) {
                    detailSettingsCell[5].setVisibility(View.VISIBLE);
                    if (currentStep == STEP_CHECKOUT) {
                        detailSettingsCell[5].setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        detailSettingsCell[5].setOnClickListener(v -> {
                            PaymentFormActivity activity = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_SHIPPING_INFORMATION, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment);
                            activity.setDelegate(new PaymentFormActivityDelegate() {
                                @Override
                                public void didSelectNewAddress(TLRPC.TL_payments_validateRequestedInfo validateRequested) {
                                    validateRequest = validateRequested;
                                    setAddressFields(validateRequest.info);
                                }
                            });
                            presentFragment(activity);
                        });
                    } else {
                        detailSettingsCell[5].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    }
                }

                if (shippingOption != null) {
                    detailSettingsCell[6] = new TextDetailSettingsCell(context);
                    detailSettingsCell[6].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    detailSettingsCell[6].setTextAndValueAndIcon(shippingOption.title, LocaleController.getString(R.string.PaymentCheckoutShippingMethod), R.drawable.msg_payment_delivery, false);
                    linearLayout2.addView(detailSettingsCell[6]);
                }
                setAddressFields(info);
            }

            if (currentStep == STEP_CHECKOUT) {
                recurrentAccepted = !isCheckoutPreview;
                if (invoiceInput instanceof TLRPC.TL_inputInvoiceStars) {
                    recurrentAccepted = true;
                }
                bottomLayout = new BottomFrameLayout(context, paymentForm);
                if (Build.VERSION.SDK_INT >= 21) {
                    View selectorView = new View(context);
                    selectorView.setBackground(Theme.getSelectorDrawable(getThemedColor(Theme.key_listSelector), false));
                    bottomLayout.addView(selectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                }
                frameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
                bottomLayout.setOnClickListener(v -> {
                    if (recurrentAcceptCell != null && !recurrentAccepted) {
                        shiftDp = -shiftDp;
                        AndroidUtilities.shakeViewSpring(recurrentAcceptCell.getTextView(), shiftDp);
                        AndroidUtilities.shakeViewSpring(recurrentAcceptCell.getCheckBox(), shiftDp);
                        try {
                            recurrentAcceptCell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {}
                        return;
                    }

                    if (isCheckoutPreview && paymentForm.saved_info != null && validateRequest == null) {
                        setDonePressed(true);

                        sendSavedForm(()->{
                            setDonePressed(false);
                            v.callOnClick();
                        });
                        return;
                    }

                    if (isCheckoutPreview && (paymentForm.saved_info == null && (paymentForm.invoice.shipping_address_requested || paymentForm.invoice.email_requested || paymentForm.invoice.name_requested || paymentForm.invoice.phone_requested) || savedCredentialsCard == null && paymentJson == null && googlePayCredentials == null || shippingOption == null && paymentForm.invoice.flexible)) {
                        int step;
                        if (paymentForm.saved_info == null && (paymentForm.invoice.shipping_address_requested || paymentForm.invoice.email_requested || paymentForm.invoice.name_requested || paymentForm.invoice.phone_requested)) {
                            step = STEP_SHIPPING_INFORMATION;
                        } else if (savedCredentialsCard == null && paymentJson == null && googlePayCredentials == null) {
                            step = STEP_PAYMENT_INFO;
                        } else {
                            step = STEP_SHIPPING_METHODS;
                        }
                        if (step == STEP_PAYMENT_INFO && !paymentForm.additional_methods.isEmpty()) {
                            showChoosePaymentMethod(v::callOnClick);
                        } else {
                            presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, step, requestedInfo, shippingOption, tipAmount, paymentJson, cardName, validateRequest, saveCardInfo, null, parentFragment));
                        }
                        return;
                    }

                    if (!paymentForm.password_missing && savedCredentialsCard != null) {
                        if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
                            if (UserConfig.getInstance(currentAccount).tmpPassword.valid_until < ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60) {
                                UserConfig.getInstance(currentAccount).tmpPassword = null;
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                            }
                        }

                        if (UserConfig.getInstance(currentAccount).tmpPassword == null) {
                            needPayAfterTransition = true;
                            presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_CONFIRM_PASSWORD, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment));
                            needPayAfterTransition = false;
                            return;
                        } else if (isCheckoutPreview) {
                            isCheckoutPreview = false;
                            NotificationCenter.getInstance(currentAccount).removeObserver(PaymentFormActivity.this, NotificationCenter.paymentFinished);
                        }
                    }

                    if (botUser != null && !botUser.verified) {
                        String botKey = "payment_warning_" + botUser.id;
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        if (!preferences.getBoolean(botKey, false)) {
                            preferences.edit().putBoolean(botKey, true).commit();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString(R.string.PaymentWarning));
                            builder.setMessage(LocaleController.formatString("PaymentWarningText", R.string.PaymentWarningText, currentBotName, providerName));
                            builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialogInterface, i) -> showPayAlert(totalPrice[0]));
                            showDialog(builder.create());
                        } else {
                            showPayAlert(totalPrice[0]);
                        }
                    } else {
                        showPayAlert(totalPrice[0]);
                    }
                });
                payTextView = new TextView(context);
                payTextView.setTextColor(getThemedColor(Theme.key_contacts_inviteText));
                payTextView.setText(LocaleController.formatString("PaymentCheckoutPay", R.string.PaymentCheckoutPay, totalPrice[0]));
                payTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                payTextView.setGravity(Gravity.CENTER);
                payTextView.setTypeface(AndroidUtilities.bold());
                bottomLayout.addView(payTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                progressViewButton = new ContextProgressView(context, 0);
                progressViewButton.setVisibility(View.INVISIBLE);
                int color = getThemedColor(Theme.key_contacts_inviteText);
                progressViewButton.setColors(color & 0x2fffffff, color);
                bottomLayout.addView(progressViewButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                bottomLayout.setChecked(recurrentAccepted, false);
                payTextView.setAlpha(!recurrentAccepted ? 0.8f : 1f);

                doneItem.setEnabled(false);
                doneItem.getContentView().setVisibility(View.INVISIBLE);

                AndroidUtilities.checkAndroidTheme(context, true);
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

                webView.getSettings().setSupportZoom(true);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setDisplayZoomControls(false);
                webView.getSettings().setUseWideViewPort(true);

                if (Build.VERSION.SDK_INT >= 21) {
                    webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.setAcceptThirdPartyCookies(webView, true);
                }

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        if (!AndroidUtilities.isSafeToShow(getContext())) {
                            return true;
                        }
                        new AlertDialog.Builder(getContext(), resourcesProvider)
                                .setTitle(getString(R.string.ChromeCrashTitle))
                                .setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.ChromeCrashMessage), () -> Browser.openUrl(getContext(), "https://play.google.com/store/apps/details?id=com.google.android.webview")))
                                .setPositiveButton(getString(R.string.OK), null)
                                .show();
                        return true;
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

                            if (BLACKLISTED_PROTOCOLS.contains(uri.getScheme())) {
                                return true;
                            }
                            if (!WEBVIEW_PROTOCOLS.contains(uri.getScheme())) {
                                try {
                                    if (getContext() instanceof Activity) {
                                        ((Activity) getContext()).startActivityForResult(new Intent(Intent.ACTION_VIEW, uri), BasePermissionsActivity.REQUEST_CODE_PAYMENT_FORM);
                                    }
                                } catch (ActivityNotFoundException e) {
                                    new AlertDialog.Builder(context)
                                            .setTitle(currentBotName)
                                            .setMessage(LocaleController.getString(R.string.PaymentAppNotFoundForDeeplink))
                                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                                            .show();
                                }
                                return true;
                            }
                        } catch (Exception ignore) {

                        }
                        return false;
                    }
                });

                recurrentAcceptCell = null;
                if (paymentForm.invoice.terms_url != null) {
                    recurrentAcceptCell = new RecurrentPaymentsAcceptCell(context, getResourceProvider());
                    recurrentAcceptCell.setChecked(recurrentAccepted);
                    String str = LocaleController.getString(R.string.PaymentCheckoutAcceptRecurrent);
                    SpannableStringBuilder sb = new SpannableStringBuilder(str);
                    int firstIndex = str.indexOf('*'), lastIndex = str.lastIndexOf('*');
                    if (firstIndex != -1 && lastIndex != -1) {
                        SpannableString acceptTerms = new SpannableString(str.substring(firstIndex + 1, lastIndex));
                        acceptTerms.setSpan(new URLSpanNoUnderline(paymentForm.invoice.terms_url), 0, acceptTerms.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.replace(firstIndex, lastIndex + 1, acceptTerms);
                        str = str.substring(0, firstIndex) + acceptTerms + str.substring(lastIndex + 1);
                    }
                    String format = "%1$s";
                    int botIndex = str.indexOf(format);
                    if (botIndex != -1) {
                        sb.replace(botIndex, botIndex + format.length(), currentBotName);
                        sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), botIndex, botIndex + currentBotName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    recurrentAcceptCell.setText(sb);

                    recurrentAcceptCell.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)));
                    recurrentAcceptCell.setOnClickListener(v -> {
                        if (donePressed) {
                            return;
                        }

                        recurrentAccepted = !recurrentAccepted;
                        recurrentAcceptCell.setChecked(recurrentAccepted);

                        bottomLayout.setChecked(recurrentAccepted, true);
                    });
                    frameLayout.addView(recurrentAcceptCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 48));
                }

                frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }

            sectionCell[1] = new ShadowSectionCell(context, resourcesProvider);
            sectionCell[1].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            if (cardInfoVisibility != View.VISIBLE && currentStep == STEP_CHECKOUT && validateRequest == null && (paymentForm == null || paymentForm.saved_info == null)) {
                sectionCell[1].setVisibility(cardInfoVisibility);
            }
            linearLayout2.addView(sectionCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else if (currentStep == STEP_SET_PASSWORD_EMAIL) {
            codeFieldCell = new EditTextSettingsCell(context);
            codeFieldCell.setTextAndHint("", LocaleController.getString(R.string.PasswordCode), false);
            codeFieldCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            EditTextBoldCursor editText = codeFieldCell.getTextView();
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    sendSavePassword(false);
                    return true;
                }
                return false;
            });
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (emailCodeLength != 0 && s.length() == emailCodeLength) {
                        sendSavePassword(false);
                    }
                }
            });
            linearLayout2.addView(codeFieldCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            bottomCell[2] = new TextInfoPrivacyCell(context, resourcesProvider);
            bottomCell[2].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(bottomCell[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            settingsCell[1] = new TextSettingsCell(context, resourcesProvider);
            settingsCell[1].setBackgroundDrawable(Theme.getSelectorDrawable(true));
            settingsCell[1].setTag(Theme.key_windowBackgroundWhiteBlackText);
            settingsCell[1].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            settingsCell[1].setText(LocaleController.getString(R.string.ResendCode), true);
            linearLayout2.addView(settingsCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            settingsCell[1].setOnClickListener(v -> {
                TLRPC.TL_account_resendPasswordEmail req = new TLRPC.TL_account_resendPasswordEmail();
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                });
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString(R.string.ResendCodeInfo));
                builder.setTitle(LocaleController.getString(R.string.AppName));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showDialog(builder.create());
            });

            settingsCell[0] = new TextSettingsCell(context, resourcesProvider);
            settingsCell[0].setBackgroundDrawable(Theme.getSelectorDrawable(true));
            settingsCell[0].setTag(Theme.key_text_RedRegular);
            settingsCell[0].setTextColor(getThemedColor(Theme.key_text_RedRegular));
            settingsCell[0].setText(LocaleController.getString(R.string.AbortPassword), false);
            linearLayout2.addView(settingsCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            settingsCell[0].setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                String text = LocaleController.getString(R.string.TurnPasswordOffQuestion);
                if (currentPassword.has_secure_values) {
                    text += "\n\n" + LocaleController.getString(R.string.TurnPasswordOffPassport);
                }
                builder.setMessage(text);
                builder.setTitle(LocaleController.getString(R.string.TurnPasswordOffQuestionTitle));
                builder.setPositiveButton(LocaleController.getString(R.string.Disable), (dialogInterface, i) -> sendSavePassword(true));
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(getThemedColor(Theme.key_text_RedBold));
                }
            });

            inputFields = new EditTextBoldCursor[FIELDS_COUNT_PASSWORD];
            for (int a = 0; a < FIELDS_COUNT_PASSWORD; a++) {
                if (a == FIELD_ENTERPASSWORD) {
                    headerCell[0] = new HeaderCell(context, resourcesProvider);
                    headerCell[0].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    headerCell[0].setText(LocaleController.getString(R.string.PaymentPasswordTitle));
                    linearLayout2.addView(headerCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else if (a == FIELD_ENTERPASSWORDEMAIL) {
                    headerCell[1] = new HeaderCell(context, resourcesProvider);
                    headerCell[1].setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    headerCell[1].setText(LocaleController.getString(R.string.PaymentPasswordEmailTitle));
                    linearLayout2.addView(headerCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }

                ViewGroup container = new FrameLayout(context);
                container.setClipChildren(false);
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                container.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

                if (a == FIELD_ENTERPASSWORD) {
                    View divider = new View(context) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
                        }
                    };
                    divider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    dividers.add(divider);
                    container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
                }

                inputFields[a] = new EditTextBoldCursor(context);
                inputFields[a].setTag(a);
                inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                inputFields[a].setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
                inputFields[a].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setBackgroundDrawable(null);
                inputFields[a].setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                inputFields[a].setCursorSize(AndroidUtilities.dp(20));
                inputFields[a].setCursorWidth(1.5f);

                if (a == FIELD_ENTERPASSWORD || a == FIELD_REENTERPASSWORD) {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputFields[a].setTypeface(Typeface.DEFAULT);
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                } else {
                    inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                }

                switch (a) {
                    case FIELD_ENTERPASSWORD:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentPasswordEnter));
                        inputFields[a].requestFocus();
                        break;
                    case FIELD_REENTERPASSWORD:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentPasswordReEnter));
                        break;
                    case FIELD_ENTERPASSWORDEMAIL:
                        inputFields[a].setHint(LocaleController.getString(R.string.PaymentPasswordEmail));
                        break;
                }

                inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));

                inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
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
                });
                if (a == FIELD_REENTERPASSWORD) {
                    bottomCell[0] = new TextInfoPrivacyCell(context, resourcesProvider);
                    bottomCell[0].setText(LocaleController.getString(R.string.PaymentPasswordInfo));
                    bottomCell[0].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else if (a == FIELD_ENTERPASSWORDEMAIL) {
                    bottomCell[1] = new TextInfoPrivacyCell(context, resourcesProvider);
                    bottomCell[1].setText(LocaleController.getString(R.string.PaymentPasswordEmailInfo));
                    bottomCell[1].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    linearLayout2.addView(bottomCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            }
            updatePasswordFields();
        }
        return fragmentView;
    }

    private void showChoosePaymentMethod() {
        showChoosePaymentMethod(null);
    }

    private void showChoosePaymentMethod(Runnable callback) {
        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.PaymentCheckoutMethod), true);
        List<CharSequence> titles = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        if (savedCredentialsCard != null) {
            titles.add(savedCredentialsCard.title);
            icons.add(R.drawable.msg_payment_card);
        } else if (cardName != null) {
            titles.add(cardName);
            icons.add(R.drawable.msg_payment_card);
        }
        List<TLRPC.TL_paymentSavedCredentialsCard> savedCredentialsCards = new ArrayList<>();
        for (TLRPC.TL_paymentSavedCredentialsCard credentialsCard : paymentForm.saved_credentials) {
            if (savedCredentialsCard == null || !Objects.equals(credentialsCard.id, savedCredentialsCard.id)) {
                titles.add(credentialsCard.title);
                icons.add(R.drawable.msg_payment_card);
                savedCredentialsCards.add(credentialsCard);
            }
        }
        for (TLRPC.TL_paymentFormMethod method : paymentForm.additional_methods) {
            titles.add(method.title);
            icons.add(R.drawable.msg_payment_provider);
        }
        titles.add(LocaleController.getString(R.string.PaymentCheckoutMethodNewCard));
        icons.add(R.drawable.msg_addbot);

        int[] iconsIntArr = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) {
            iconsIntArr[i] = icons.get(i);
        }
        builder.setItems(titles.toArray(new CharSequence[0]), iconsIntArr, (dialog, which) -> {
            PaymentFormActivityDelegate delegate = new PaymentFormActivityDelegate() {
                @Override
                public boolean didSelectNewCard(String tokenJson, String card, boolean saveCard, TLRPC.TL_inputPaymentCredentialsGooglePay googlePay, TLRPC.TL_paymentSavedCredentialsCard credentialsCard) {
                    savedCredentialsCard = credentialsCard;
                    paymentJson = tokenJson;
                    saveCardInfo = saveCard;
                    cardName = card;
                    googlePayCredentials = googlePay;
                    if (detailSettingsCell[0] != null) {
                        detailSettingsCell[0].setVisibility(View.VISIBLE);
                        detailSettingsCell[0].setTextAndValueAndIcon(cardName != null && cardName.length() > 1 ? cardName.substring(0, 1).toUpperCase() + cardName.substring(1) : cardName, LocaleController.getString(R.string.PaymentCheckoutMethod), R.drawable.msg_payment_card, true);

                        if (detailSettingsCell[1] != null) {
                            detailSettingsCell[1].setVisibility(View.VISIBLE);
                        }
                    }
                    if (callback != null) {
                        callback.run();
                    }
                    return false;
                }
            };
            int offset = savedCredentialsCard != null || cardName != null ? 1 : 0;
            if ((savedCredentialsCard != null || cardName != null) && which == 0) {
                return;
            }
            if (which >= offset && which < offset + savedCredentialsCards.size()) {
                savedCredentialsCard = savedCredentialsCards.get(which - offset);
                delegate.didSelectNewCard(null, savedCredentialsCard.title, true, null, savedCredentialsCard);
                return;
            }

            if (which < titles.size() - 1) {
                TLRPC.TL_paymentFormMethod method = paymentForm.additional_methods.get(which - savedCredentialsCards.size() - offset);

                PaymentFormActivity activity = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_PAYMENT_INFO, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment);
                activity.setPaymentMethod(method);
                activity.setDelegate(delegate);
                presentFragment(activity);
            } else if (which == titles.size() - 1) {
                PaymentFormActivity activity = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_PAYMENT_INFO, requestedInfo, shippingOption, tipAmount, null, cardName, validateRequest, saveCardInfo, null, parentFragment);
                activity.setDelegate(delegate);
                presentFragment(activity);
            }
        });
        showDialog(builder.create());
    }

    private void setPaymentMethod(TLRPC.TL_paymentFormMethod method) {
        paymentFormMethod = method;
    }

    private void setAddressFields(TLRPC.TL_paymentRequestedInfo info) {
        if (info.shipping_address != null) {
            String address = String.format("%s %s, %s, %s, %s, %s", info.shipping_address.street_line1, info.shipping_address.street_line2, info.shipping_address.city, info.shipping_address.state, info.shipping_address.country_iso2, info.shipping_address.post_code);
            detailSettingsCell[2].setTextAndValueAndIcon(address, LocaleController.getString(R.string.PaymentShippingAddress), R.drawable.msg_payment_address, true);
        }
        detailSettingsCell[2].setVisibility(info.shipping_address != null ? View.VISIBLE : View.GONE);

        if (info.name != null) {
            detailSettingsCell[3].setTextAndValueAndIcon(info.name, LocaleController.getString(R.string.PaymentCheckoutName), R.drawable.msg_contacts, true);
        }
        detailSettingsCell[3].setVisibility(info.name != null ? View.VISIBLE : View.GONE);

        if (info.phone != null) {
            detailSettingsCell[4].setTextAndValueAndIcon(PhoneFormat.getInstance().format(info.phone), LocaleController.getString(R.string.PaymentCheckoutPhoneNumber), R.drawable.msg_calls, info.email != null || shippingOption != null);
        }
        detailSettingsCell[4].setVisibility(info.phone != null ? View.VISIBLE : View.GONE);

        if (info.email != null) {
            detailSettingsCell[5].setTextAndValueAndIcon(info.email, LocaleController.getString(R.string.PaymentCheckoutEmail), R.drawable.msg_mention, shippingOption != null);
        }
        detailSettingsCell[5].setVisibility(info.email != null ? View.VISIBLE : View.GONE);
    }

    private void updateTotalPrice() {
        totalPrice[0] = getTotalPriceString(prices);
        totalCell.setTextAndValue(LocaleController.getString(R.string.PaymentTransactionTotal), totalPrice[0], true);
        if (payTextView != null) {
            payTextView.setText(LocaleController.formatString("PaymentCheckoutPay", R.string.PaymentCheckoutPay, totalPrice[0]));
        }
        if (tipLayout != null) {
            int color = getThemedColor(Theme.key_contacts_inviteBackground);
            for (int b = 0, N2 = tipLayout.getChildCount(); b < N2; b++) {
                TextView child = (TextView) tipLayout.getChildAt(b);
                if (child.getTag().equals(tipAmount)) {
                    Theme.setDrawableColor(child.getBackground(), color);
                    child.setTextColor(getThemedColor(Theme.key_contacts_inviteText));
                } else {
                    Theme.setDrawableColor(child.getBackground(), color & 0x1fffffff);
                    child.setTextColor(getThemedColor(Theme.key_chats_secretName));
                }
                child.invalidate();
            }
        }
    }

    private void createGooglePayButton(Context context) {
        googlePayContainer = new FrameLayout(context);
        googlePayContainer.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        googlePayContainer.setVisibility(View.GONE);

        googlePayButton = new FrameLayout(context);
        googlePayButton.setClickable(true);
        googlePayButton.setFocusable(true);
        googlePayButton.setBackgroundResource(R.drawable.googlepay_button_no_shadow_background);
        if (googlePayPublicKey == null) {
            googlePayButton.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(2), AndroidUtilities.dp(10), AndroidUtilities.dp(2));
        } else {
            googlePayButton.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        }
        googlePayContainer.addView(googlePayButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        googlePayButton.setOnClickListener(v -> {
            googlePayButton.setClickable(false);
            try {
                JSONObject paymentDataRequest = getBaseRequest();

                JSONObject cardPaymentMethod = getBaseCardPaymentMethod();
                if (googlePayPublicKey != null && googlePayParameters == null) {
                    cardPaymentMethod.put("tokenizationSpecification", new JSONObject() {{
                        put("type", "DIRECT");
                        put("parameters", new JSONObject() {{
                            put("protocolVersion", "ECv2");
                            put("publicKey", googlePayPublicKey);
                        }});
                    }});
                } else {
                    cardPaymentMethod.put("tokenizationSpecification", new JSONObject() {{
                        put("type", "PAYMENT_GATEWAY");
                        if (googlePayParameters != null) {
                            put("parameters", googlePayParameters);
                        } else {
                            put("parameters", new JSONObject() {{
                                put("gateway", "stripe");
                                put("stripe:publishableKey", providerApiKey);
                                put("stripe:version", StripeApiHandler.VERSION);
                            }});
                        }
                    }});
                }

                paymentDataRequest.put("allowedPaymentMethods", new JSONArray().put(cardPaymentMethod));

                JSONObject transactionInfo = new JSONObject();
                ArrayList<TLRPC.TL_labeledPrice> arrayList = new ArrayList<>(paymentForm.invoice.prices);
                if (shippingOption != null) {
                    arrayList.addAll(shippingOption.prices);
                }
                transactionInfo.put("totalPrice", totalPriceDecimal = getTotalPriceDecimalString(arrayList));
                transactionInfo.put("totalPriceStatus", "FINAL");
                if (!TextUtils.isEmpty(googlePayCountryCode)) {
                    transactionInfo.put("countryCode", googlePayCountryCode);
                }
                transactionInfo.put("currencyCode", paymentForm.invoice.currency);
                transactionInfo.put("checkoutOption", "COMPLETE_IMMEDIATE_PURCHASE");
                paymentDataRequest.put("transactionInfo", transactionInfo);

                paymentDataRequest.put("merchantInfo", new JSONObject().put("merchantName", currentBotName));

                /*paymentDataRequest.put("shippingAddressRequired", true);

                JSONObject shippingAddressParameters = new JSONObject();
                shippingAddressParameters.put("phoneNumberRequired", false);

                JSONArray allowedCountryCodes = new JSONArray(Constants.SHIPPING_SUPPORTED_COUNTRIES);
                shippingAddressParameters.put("allowedCountryCodes", allowedCountryCodes);
                paymentDataRequest.put("shippingAddressParameters", shippingAddressParameters);*/

                PaymentDataRequest request = PaymentDataRequest.fromJson(paymentDataRequest.toString());
                if (request != null) {
                    AutoResolveHelper.resolveTask(paymentsClient.loadPaymentData(request), getParentActivity(), LOAD_PAYMENT_DATA_REQUEST_CODE);
                }
            } catch (JSONException e) {
                FileLog.e(e);
            }
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setWeightSum(2);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setDuplicateParentStateEnabled(true);
        googlePayButton.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setDuplicateParentStateEnabled(true);
        imageView.setImageResource(R.drawable.buy_with_googlepay_button_content);
        linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setDuplicateParentStateEnabled(true);
        imageView.setImageResource(R.drawable.googlepay_button_overlay);
        googlePayButton.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void updatePasswordFields() {
        if (currentStep != STEP_SET_PASSWORD_EMAIL || bottomCell[2] == null) {
            return;
        }
        doneItem.setVisibility(View.VISIBLE);
        if (currentPassword == null) {
            showEditDoneProgress(true, true);
            bottomCell[2].setVisibility(View.GONE);
            settingsCell[0].setVisibility(View.GONE);
            settingsCell[1].setVisibility(View.GONE);
            codeFieldCell.setVisibility(View.GONE);
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
                bottomCell[2].setText(LocaleController.formatString("EmailPasswordConfirmText2", R.string.EmailPasswordConfirmText2, currentPassword.email_unconfirmed_pattern != null ? currentPassword.email_unconfirmed_pattern : ""));
                bottomCell[2].setVisibility(View.VISIBLE);
                settingsCell[0].setVisibility(View.VISIBLE);
                settingsCell[1].setVisibility(View.VISIBLE);
                codeFieldCell.setVisibility(View.VISIBLE);
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
                bottomCell[2].setVisibility(View.GONE);
                settingsCell[0].setVisibility(View.GONE);
                settingsCell[1].setVisibility(View.GONE);
                bottomCell[1].setText(LocaleController.getString(R.string.PaymentPasswordEmailInfo));
                codeFieldCell.setVisibility(View.GONE);

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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingPasswordInfo = false;
            if (error == null) {
                currentPassword = (TLRPC.account_Password) response;
                if (!TwoStepVerificationActivity.canHandleCurrentPassword(currentPassword, false)) {
                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString(R.string.UpdateAppAlert), true);
                    return;
                }
                if (paymentForm != null && currentPassword.has_password) {
                    paymentForm.password_missing = false;
                    paymentForm.can_save_credentials = true;
                    updateSavePaymentField();
                }
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                if (passwordFragment != null) {
                    passwordFragment.setCurrentPassword(currentPassword);
                }
                if (!currentPassword.has_password && shortPollRunnable == null) {
                    shortPollRunnable = () -> {
                        if (shortPollRunnable == null) {
                            return;
                        }
                        loadPasswordInfo();
                        shortPollRunnable = null;
                    };
                    AndroidUtilities.runOnUIThread(shortPollRunnable, 5000);
                }
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void showAlertWithText(String title, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.setTitle(title);
        builder.setMessage(text);
        showDialog(builder.create());
    }

    private void showPayAlert(final String totalPrice) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.PaymentTransactionReview));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("PaymentTransactionMessage2", R.string.PaymentTransactionMessage2, totalPrice, currentBotName, currentItemName)));
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialogInterface, i) -> {
            setDonePressed(true);
            sendData();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private JSONObject getBaseRequest() throws JSONException {
        return new JSONObject().put("apiVersion", 2).put("apiVersionMinor", 0);
    }

    private JSONObject getBaseCardPaymentMethod() throws JSONException {
        List<String> SUPPORTED_NETWORKS = Arrays.asList(
                "AMEX",
                "DISCOVER",
                "JCB",
                "MASTERCARD",
                "VISA");

        List<String> SUPPORTED_METHODS = Arrays.asList(
                "PAN_ONLY",
                "CRYPTOGRAM_3DS");

        JSONObject cardPaymentMethod = new JSONObject();
        cardPaymentMethod.put("type", "CARD");

        JSONObject parameters = new JSONObject();
        parameters.put("allowedAuthMethods", new JSONArray(SUPPORTED_METHODS));
        parameters.put("allowedCardNetworks", new JSONArray(SUPPORTED_NETWORKS));

        cardPaymentMethod.put("parameters", parameters);

        return cardPaymentMethod;
    }

    public Optional<JSONObject> getIsReadyToPayRequest() {
        try {
            JSONObject isReadyToPayRequest = getBaseRequest();
            isReadyToPayRequest.put(
                    "allowedPaymentMethods", new JSONArray().put(getBaseCardPaymentMethod()));

            return Optional.of(isReadyToPayRequest);
        } catch (JSONException e) {
            return Optional.empty();
        }
    }

    private void initGooglePay(Context context) {
        if (Build.VERSION.SDK_INT < 19 || getParentActivity() == null) {
            return;
        }
        Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder()
                .setEnvironment(paymentForm.invoice.test ? WalletConstants.ENVIRONMENT_TEST : WalletConstants.ENVIRONMENT_PRODUCTION)
                .setTheme(WalletConstants.THEME_LIGHT)
                .build();
        paymentsClient = Wallet.getPaymentsClient(context, walletOptions);

        final Optional<JSONObject> isReadyToPayJson = getIsReadyToPayRequest();
        if (!isReadyToPayJson.isPresent()) {
            return;
        }
        IsReadyToPayRequest request = IsReadyToPayRequest.fromJson(isReadyToPayJson.get().toString());
        if (request == null) {
            return;
        }

        Task<Boolean> task = paymentsClient.isReadyToPay(request);
        task.addOnCompleteListener(getParentActivity(),
                task1 -> {
                    if (task1.isSuccessful()) {
                        if (googlePayContainer != null) {
                            googlePayContainer.setVisibility(View.VISIBLE);
                        }
                    } else {
                        FileLog.e("isReadyToPay failed", task1.getException());
                    }
                });
    }

    private String getTotalPriceString(ArrayList<TLRPC.TL_labeledPrice> prices) {
        long amount = 0;
        for (int a = 0; a < prices.size(); a++) {
            amount += prices.get(a).amount;
        }
        if (tipAmount != null) {
            amount += tipAmount;
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.twoStepPasswordChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didRemoveTwoStepPassword);
        if (currentStep != STEP_CHECKOUT || isCheckoutPreview) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.paymentFinished);
        }
        return super.onFragmentCreate();
    }

    public int getOtherSameFragmentDiff() {
        if (parentLayout == null || parentLayout.getFragmentStack() == null) {
            return 0;
        }
        int cur = parentLayout.getFragmentStack().indexOf(this);
        if (cur == -1) {
            cur = parentLayout.getFragmentStack().size();
        }
        int i = cur;
        for (int a = 0; a < parentLayout.getFragmentStack().size(); a++) {
            BaseFragment fragment = parentLayout.getFragmentStack().get(a);
            if (fragment instanceof PaymentFormActivity) {
                i = a;
                break;
            }
        }
        return i - cur;
    }

    @Override
    public void onFragmentDestroy() {
        if (delegate != null) {
            delegate.onFragmentDestroyed();
        }
        AndroidUtilities.checkAndroidTheme(getContext(), false);
        if (!paymentStatusSent) {
            invoiceStatus = InvoiceStatus.CANCELLED;
            if (paymentFormCallback != null && getOtherSameFragmentDiff() == 0) {
                paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
            }
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.twoStepPasswordChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didRemoveTwoStepPassword);
        if (currentStep != STEP_CHECKOUT || isCheckoutPreview) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.paymentFinished);
        }
        if (webView != null) {
            try {
                ViewParent parent = webView.getParent();
                if (parent != null) {
                    ((ViewGroup) parent).removeView(webView);
                }
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webViewUrl = null;
                webView.destroy();
                webView = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            if ((currentStep == STEP_PAYMENT_INFO || currentStep == STEP_SET_PASSWORD_EMAIL) && Build.VERSION.SDK_INT >= 23 && (SharedConfig.passcodeHash.length() == 0 || SharedConfig.allowScreenCapture)) {
                getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        super.onFragmentDestroy();
        canceled = true;
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();

        if (currentStep == STEP_CHECKOUT) {
            if (needPayAfterTransition) {
                needPayAfterTransition = false;
                bottomLayout.callOnClick();
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward) {
            if (webView != null) {
                if (currentStep != STEP_CHECKOUT) {
                    if (paymentFormMethod != null) {
                        webView.loadUrl(webViewUrl = paymentFormMethod.url);
                    } else {
                        webView.loadUrl(webViewUrl = paymentForm.url);
                    }
                }
            } else if (currentStep == STEP_PAYMENT_INFO) {
                AndroidUtilities.runOnUIThread(() -> {
                    inputFields[FIELD_CARD].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[FIELD_CARD]);
                }, 100);
            } else if (currentStep == STEP_CONFIRM_PASSWORD) {
                inputFields[FIELD_SAVEDPASSWORD].requestFocus();
                AndroidUtilities.showKeyboard(inputFields[FIELD_SAVEDPASSWORD]);
            } else if (currentStep == STEP_CHECKOUT) {
                if (inputFields != null) {
                    inputFields[0].requestFocus();
                }
            } else if (currentStep == STEP_SET_PASSWORD_EMAIL) {
                if (!waitingForEmail) {
                    inputFields[FIELD_ENTERPASSWORD].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[FIELD_ENTERPASSWORD]);
                }
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.twoStepPasswordChanged) {
            paymentForm.password_missing = false;
            paymentForm.can_save_credentials = true;
            updateSavePaymentField();
        } else if (id == NotificationCenter.didRemoveTwoStepPassword) {
            paymentForm.password_missing = true;
            paymentForm.can_save_credentials = false;
            updateSavePaymentField();
        } else if (id == NotificationCenter.paymentFinished) {
            paymentStatusSent = true;
            removeSelfFromStack();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
            AndroidUtilities.runOnUIThread(() -> {
                if (resultCode == Activity.RESULT_OK) {
                    PaymentData paymentData = PaymentData.getFromIntent(data);
                    if (paymentData == null) {
                        return;
                    }
                    final String paymentInfo = paymentData.toJson();
                    if (paymentInfo == null) {
                        return;
                    }
                    try {
                        JSONObject paymentMethodData = new JSONObject(paymentInfo).getJSONObject("paymentMethodData");
                        final JSONObject tokenizationData = paymentMethodData.getJSONObject("tokenizationData");
                        final String tokenizationType = tokenizationData.getString("type");
                        final String token = tokenizationData.getString("token");

                        if (googlePayPublicKey != null || googlePayParameters != null) {
                            googlePayCredentials = new TLRPC.TL_inputPaymentCredentialsGooglePay();
                            googlePayCredentials.payment_token = new TLRPC.TL_dataJSON();
                            googlePayCredentials.payment_token.data = tokenizationData.toString();
                            String descriptions = paymentMethodData.optString("description");
                            if (!TextUtils.isEmpty(descriptions)) {
                                cardName = descriptions;
                            } else {
                                cardName = "Android Pay";
                            }
                        } else {
                            Token t = TokenParser.parseToken(token);
                            paymentJson = String.format(Locale.US, "{\"type\":\"%1$s\", \"id\":\"%2$s\"}", t.getType(), t.getId());
                            Card card = t.getCard();
                            cardName = card.getBrand() + " *" + card.getLast4();
                        }
                        goToNextStep();
                    } catch (JSONException e) {
                        FileLog.e(e);
                    }
                } else {
                    if (resultCode == AutoResolveHelper.RESULT_ERROR) {
                        Status status = AutoResolveHelper.getStatusFromIntent(data);
                        FileLog.e("android pay error " + (status != null ? status.getStatusMessage() : ""));
                    }
                }
                showEditDoneProgress(true, false);
                setDonePressed(false);
                if (googlePayButton != null) {
                    googlePayButton.setClickable(true);
                }
            });
        }
    }

    private void goToNextStep() {
        switch (currentStep) {
            case STEP_SHIPPING_INFORMATION:
                if (delegate != null) {
                    delegate.didSelectNewAddress(validateRequest);
                    finishFragment();
                } else {
                    int nextStep;
                    if (paymentForm.invoice.flexible) {
                        nextStep = STEP_SHIPPING_METHODS;
                    } else if (savedCredentialsCard != null || paymentJson != null) {
                        if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
                            if (UserConfig.getInstance(currentAccount).tmpPassword.valid_until < ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60) {
                                UserConfig.getInstance(currentAccount).tmpPassword = null;
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                            }
                        }
                        if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
                            nextStep = STEP_CHECKOUT;
                        } else {
                            nextStep = STEP_CONFIRM_PASSWORD;
                        }
                    } else {
                        nextStep = STEP_PAYMENT_INFO;
                    }
                    if (nextStep == STEP_PAYMENT_INFO && savedCredentialsCard == null && paymentJson == null && !paymentForm.additional_methods.isEmpty()) {
                        showChoosePaymentMethod(this::goToNextStep);
                    } else {
                        presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, nextStep, requestedInfo, null, null, paymentJson, cardName, validateRequest, saveCardInfo, googlePayCredentials, parentFragment), isWebView);
                    }
                }
                break;
            case STEP_SHIPPING_METHODS: {
                int nextStep;
                if (paymentJson != null || cardName != null) {
                    nextStep = STEP_CHECKOUT;
                } else if (savedCredentialsCard != null) {
                    if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
                        if (UserConfig.getInstance(currentAccount).tmpPassword.valid_until < ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60) {
                            UserConfig.getInstance(currentAccount).tmpPassword = null;
                            UserConfig.getInstance(currentAccount).saveConfig(false);
                        }
                    }
                    if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
                        nextStep = STEP_CHECKOUT;
                    } else {
                        nextStep = STEP_CONFIRM_PASSWORD;
                    }
                } else {
                    nextStep = STEP_PAYMENT_INFO;
                }
                if (nextStep == STEP_PAYMENT_INFO && cardName == null && savedCredentialsCard == null && paymentJson == null && !paymentForm.additional_methods.isEmpty()) {
                    showChoosePaymentMethod(this::goToNextStep);
                } else {
                    presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, nextStep, requestedInfo, shippingOption, tipAmount, paymentJson, cardName, validateRequest, saveCardInfo, googlePayCredentials, parentFragment), isWebView);
                }
                break;
            }
            case STEP_PAYMENT_INFO:
                if (paymentForm.password_missing && saveCardInfo) {
                    passwordFragment = new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_SET_PASSWORD_EMAIL, requestedInfo, shippingOption, tipAmount, paymentJson, cardName, validateRequest, saveCardInfo, googlePayCredentials, parentFragment);
                    passwordFragment.setCurrentPassword(currentPassword);
                    passwordFragment.setDelegate(new PaymentFormActivityDelegate() {
                        @Override
                        public boolean didSelectNewCard(String tokenJson, String card, boolean saveCard, TLRPC.TL_inputPaymentCredentialsGooglePay googlePay, TLRPC.TL_paymentSavedCredentialsCard credentialsCard) {
                            if (delegate != null) {
                                delegate.didSelectNewCard(tokenJson, card, saveCard, googlePay, credentialsCard);
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
                        delegate.didSelectNewCard(paymentJson, cardName, saveCardInfo, googlePayCredentials, null);
                        finishFragment();
                    } else {
                        presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_CHECKOUT, requestedInfo, shippingOption, tipAmount, paymentJson, cardName, validateRequest, saveCardInfo, googlePayCredentials, parentFragment), isWebView);
                    }
                }
                break;
            case STEP_CONFIRM_PASSWORD: {
                int nextStep;
                if (passwordOk) {
                    nextStep = STEP_CHECKOUT;
                } else {
                    nextStep = STEP_PAYMENT_INFO;
                }
                presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, nextStep, requestedInfo, shippingOption, tipAmount, paymentJson, cardName, validateRequest, saveCardInfo, googlePayCredentials, parentFragment), true);
                break;
            }
            case STEP_CHECKOUT:
                if (isCheckoutPreview) {
                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.paymentFinished);
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.paymentFinished);

                if (getMessagesController().newMessageCallback == null) {
                    if (!onCheckoutSuccess(getParentLayout(), getParentActivity()) && !isFinishing()) {
                        finishFragment();
                    }
                } else {
                    AndroidUtilities.runOnUIThread(()-> {
                        getMessagesController().newMessageCallback = null;
                        if (invoiceStatus == InvoiceStatus.PENDING && !isFinishing()) {
                            invoiceStatus = InvoiceStatus.FAILED;
                            if (paymentFormCallback != null) {
                                paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                            }
                            finishFragment();
                        } else if (invoiceStatus == InvoiceStatus.PAID && !isFinishing()) {
                            finishFragment();
                        }
                    }, 500);
                }
                break;
            case STEP_SET_PASSWORD_EMAIL:
                if (!delegate.didSelectNewCard(paymentJson, cardName, saveCardInfo, googlePayCredentials, savedCredentialsCard)) {
                    presentFragment(new PaymentFormActivity(invoiceInput, paymentForm, messageObject, invoiceSlug, STEP_CHECKOUT, requestedInfo, shippingOption, tipAmount, paymentJson, cardName, validateRequest, saveCardInfo, googlePayCredentials, parentFragment), true);
                } else {
                    finishFragment();
                }
                break;
        }
    }

    private boolean onCheckoutSuccess(INavigationLayout parentLayout, Activity parentActivity) {
        if (invoiceInput != null) {
            if (parentLayout != null) {
                for (BaseFragment fragment : new ArrayList<>(parentLayout.getFragmentStack())) {
                    if (fragment instanceof PaymentFormActivity) {
                        fragment.removeSelfFromStack();
                    }
                }
                return true;
            }
            return false;
        }
        if (botUser.username != null && botUser.username.equalsIgnoreCase(getMessagesController().premiumBotUsername) && invoiceSlug == null || invoiceSlug != null && getMessagesController().premiumInvoiceSlug != null && Objects.equals(invoiceSlug, getMessagesController().premiumInvoiceSlug)) {
            if (parentLayout != null) {
                for (BaseFragment fragment : new ArrayList<>(parentLayout.getFragmentStack())) {
                    if (fragment instanceof ChatActivity || fragment instanceof PremiumPreviewFragment) {
                        fragment.removeSelfFromStack();
                    }
                }

                parentLayout.presentFragment(new PremiumPreviewFragment(null).setForcePremium(), !isFinishing());
                if (parentActivity instanceof LaunchActivity) {
                    try {
                        fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {
                    }
                    ((LaunchActivity) parentActivity).getFireworksOverlay().start();
                }
                return true;
            }
        }
        return false;
    }

    private void updateSavePaymentField() {
        if (bottomCell[0] == null || sectionCell[2] == null) {
            return;
        }
        if ((paymentForm.password_missing || paymentForm.can_save_credentials) && (webView == null || !webviewLoading)) {
            SpannableStringBuilder text = new SpannableStringBuilder(LocaleController.getString(R.string.PaymentCardSavePaymentInformationInfoLine1));
            if (paymentForm.password_missing) {
                loadPasswordInfo();
                text.append("\n");
                int len = text.length();
                String str2 = LocaleController.getString(R.string.PaymentCardSavePaymentInformationInfoLine2);
                int index1 = str2.indexOf('*');
                int index2 = str2.lastIndexOf('*');
                text.append(str2);
                if (index1 != -1 && index2 != -1) {
                    index1 += len;
                    index2 += len;
                    bottomCell[0].getTextView().setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                    text.replace(index2, index2 + 1, "");
                    text.replace(index1, index1 + 1, "");
                    text.setSpan(new LinkSpan(), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            checkCell1.setEnabled(true);
            bottomCell[0].setText(text);
            checkCell1.setVisibility(View.VISIBLE);
            bottomCell[0].setVisibility(View.VISIBLE);
            sectionCell[2].setBackgroundDrawable(Theme.getThemedDrawableByKey(sectionCell[2].getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        } else {
            checkCell1.setVisibility(View.GONE);
            bottomCell[0].setVisibility(View.GONE);
            sectionCell[2].setBackgroundDrawable(Theme.getThemedDrawableByKey(sectionCell[2].getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
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
                }
                if (number != null || allowCall) {
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
                                    textToSet = number.substring(a);
                                    inputFields[FIELD_PHONECODE].setText(sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                textToSet = number.substring(1);
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
        if (!clear && codeFieldCell.getVisibility() == View.VISIBLE) {
            String code = codeFieldCell.getText();
            if (code.length() == 0) {
                shakeView(codeFieldCell);
                return;
            }
            showEditDoneProgress(true, true);
            TLRPC.TL_account_confirmPasswordEmail req = new TLRPC.TL_account_confirmPasswordEmail();
            req.code = code;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                showEditDoneProgress(true, false);
                if (error == null) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (shortPollRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
                        shortPollRunnable = null;
                    }
                    goToNextStep();
                } else {
                    if (error.text.startsWith("CODE_INVALID")) {
                        shakeView(codeFieldCell);
                        codeFieldCell.setText("", false);
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        int time = Utilities.parseInt(error.text);
                        String timeString;
                        if (time < 60) {
                            timeString = LocaleController.formatPluralString("Seconds", time);
                        } else {
                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                        }
                        showAlertWithText(LocaleController.getString(R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                    } else {
                        showAlertWithText(LocaleController.getString(R.string.AppName), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        } else {
            final TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
            final String email;
            final String firstPassword;
            if (clear) {
                doneItem.setVisibility(View.VISIBLE);
                email = null;
                firstPassword = null;
                req.new_settings = new TLRPC.TL_account_passwordInputSettings();
                req.new_settings.flags = 2;
                req.new_settings.email = "";
                req.password = new TLRPC.TL_inputCheckPasswordEmpty();
            } else {
                firstPassword = inputFields[FIELD_ENTERPASSWORD].getText().toString();
                if (TextUtils.isEmpty(firstPassword)) {
                    shakeField(FIELD_ENTERPASSWORD);
                    return;
                }
                String secondPassword = inputFields[FIELD_REENTERPASSWORD].getText().toString();
                if (!firstPassword.equals(secondPassword)) {
                    try {
                        Toast.makeText(getParentActivity(), LocaleController.getString(R.string.PasswordDoNotMatch), Toast.LENGTH_SHORT).show();
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
                if (dog < 0 || dot < dog) {
                    shakeField(FIELD_ENTERPASSWORDEMAIL);
                    return;
                }

                req.password = new TLRPC.TL_inputCheckPasswordEmpty();
                req.new_settings = new TLRPC.TL_account_passwordInputSettings();
                req.new_settings.flags |= 1;
                req.new_settings.hint = "";
                req.new_settings.new_algo = currentPassword.new_algo;

                req.new_settings.flags |= 2;
                req.new_settings.email = email.trim();
            }
            showEditDoneProgress(true, true);
            Utilities.globalQueue.postRunnable(() -> {
                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.account_Password) response2;
                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                sendSavePassword(clear);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }
                    showEditDoneProgress(true, false);
                    if (clear) {
                        currentPassword.has_password = false;
                        currentPassword.current_algo = null;
                        delegate.currentPasswordUpdated(currentPassword);
                        finishFragment();
                    } else {
                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                            if (getParentActivity() == null) {
                                return;
                            }
                            goToNextStep();
                        } else if (error != null) {
                            if (error.text.equals("EMAIL_UNCONFIRMED") || error.text.startsWith("EMAIL_UNCONFIRMED_")) {
                                emailCodeLength = Utilities.parseInt(error.text);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
                                    waitingForEmail = true;
                                    currentPassword.email_unconfirmed_pattern = email;
                                    updatePasswordFields();
                                });
                                builder.setMessage(LocaleController.getString(R.string.YourEmailAlmostThereText));
                                builder.setTitle(LocaleController.getString(R.string.YourEmailAlmostThere));
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            } else {
                                if (error.text.equals("EMAIL_INVALID")) {
                                    showAlertWithText(LocaleController.getString(R.string.AppName), LocaleController.getString(R.string.PasswordEmailInvalid));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    int time = Utilities.parseInt(error.text);
                                    String timeString;
                                    if (time < 60) {
                                        timeString = LocaleController.formatPluralString("Seconds", time);
                                    } else {
                                        timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                    }
                                    showAlertWithText(LocaleController.getString(R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    showAlertWithText(LocaleController.getString(R.string.AppName), error.text);
                                }
                            }
                        }
                    }
                });

                if (!clear) {
                    byte[] newPasswordBytes = AndroidUtilities.getStringBytes(firstPassword);
                    if (currentPassword.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                        TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.new_algo;
                        req.new_settings.new_password_hash = SRPHelper.getVBytes(newPasswordBytes, algo);
                        if (req.new_settings.new_password_hash == null) {
                            TLRPC.TL_error error = new TLRPC.TL_error();
                            error.text = "ALGO_INVALID";
                            requestDelegate.run(null, error);
                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.text = "PASSWORD_HASH_INVALID";
                        requestDelegate.run(null, error);
                    }
                } else {
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                }
            });
        }
    }

    private boolean sendCardData() {
        Integer month;
        Integer year;
        String date = inputFields[FIELD_EXPIRE_DATE].getText().toString();
        String[] args = date.split("/");
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
        cardName = card.getBrand() + " *" + card.getLast4();
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
            if ("stripe".equals(paymentForm.native_provider)) {
                Stripe stripe = new Stripe(providerApiKey);
                stripe.createToken(card, new TokenCallback() {
                            public void onSuccess(Token token) {
                                if (canceled) {
                                    return;
                                }
                                paymentJson = String.format(Locale.US, "{\"type\":\"%1$s\", \"id\":\"%2$s\"}", token.getType(), token.getId());
                                AndroidUtilities.runOnUIThread(() -> {
                                    goToNextStep();
                                    showEditDoneProgress(true, false);
                                    setDonePressed(false);
                                });
                            }

                            public void onError(Exception error) {
                                if (canceled) {
                                    return;
                                }
                                showEditDoneProgress(true, false);
                                setDonePressed(false);
                                if (error instanceof APIConnectionException || error instanceof APIException) {
                                    AlertsCreator.showSimpleToast(PaymentFormActivity.this, LocaleController.getString(R.string.PaymentConnectionFailed));
                                } else {
                                    AlertsCreator.showSimpleToast(PaymentFormActivity.this, error.getMessage());
                                }
                            }
                        }
                );
            } else if ("smartglocal".equals(paymentForm.native_provider)) {
                AsyncTask<Object, Object, String> task = new AsyncTask<Object, Object, String>() {
                    @Override
                    protected String doInBackground(Object... objects) {
                        HttpURLConnection conn = null;
                        try {
                            JSONObject jsonObject = new JSONObject();
                            JSONObject cardObject = new JSONObject();
                            cardObject.put("number", card.getNumber());
                            cardObject.put("expiration_month", String.format(Locale.US, "%02d", card.getExpMonth()));
                            cardObject.put("expiration_year", "" + card.getExpYear());
                            cardObject.put("security_code", "" + card.getCVC());
                            jsonObject.put("card", cardObject);

                            String overrideSmartGlocalConnectionUrl = null;
                            if (paymentForm.native_params != null) {
                                try {
                                    JSONObject jsonObject2 = new JSONObject(paymentForm.native_params.data);
                                    overrideSmartGlocalConnectionUrl = jsonObject2.getString("tokenize_url");
                                    if (overrideSmartGlocalConnectionUrl != null && !(
                                        overrideSmartGlocalConnectionUrl.startsWith("https://") &&
                                        overrideSmartGlocalConnectionUrl.endsWith(".smart-glocal.com/cds/v1/tokenize/card")
                                    )) {
                                        overrideSmartGlocalConnectionUrl = null;
                                    }
                                } catch (Exception e) {}
                            }
                            URL connectionUrl;
                            if (overrideSmartGlocalConnectionUrl != null) {
                                connectionUrl = new URL(overrideSmartGlocalConnectionUrl);
                            } else if (paymentForm.invoice.test) {
                                connectionUrl = new URL("https://tgb-playground.smart-glocal.com/cds/v1/tokenize/card");
                            } else {
                                connectionUrl = new URL("https://tgb.smart-glocal.com/cds/v1/tokenize/card");
                            }
                            conn = (HttpURLConnection) connectionUrl.openConnection();
                            conn.setConnectTimeout(30 * 1000);
                            conn.setReadTimeout(80 * 1000);
                            conn.setUseCaches(false);
                            conn.setDoOutput(true);
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setRequestProperty("X-PUBLIC-TOKEN", providerApiKey);

                            try (OutputStream output = conn.getOutputStream()) {
                                output.write(jsonObject.toString().getBytes("UTF-8"));
                            }

                            int code = conn.getResponseCode();
                            if (code >= 200 && code < 300) {
                                JSONObject result = new JSONObject();
                                JSONObject jsonObject1 = new JSONObject(getResponseBody(conn.getInputStream()));
                                String token = jsonObject1.getJSONObject("data").getString("token");
                                result.put("token", token);
                                result.put("type", "card");
                                return result.toString();
                            } else {
                                if (BuildVars.DEBUG_VERSION) {
                                    FileLog.e("" + getResponseBody(conn.getErrorStream()));
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        } finally {
                            if (conn != null) {
                                conn.disconnect();
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        if (canceled) {
                            return;
                        }
                        if (result == null) {
                            AlertsCreator.showSimpleToast(PaymentFormActivity.this, LocaleController.getString(R.string.PaymentConnectionFailed));
                        } else {
                            paymentJson = result;
                            goToNextStep();
                        }
                        showEditDoneProgress(true, false);
                        setDonePressed(false);
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    private static String getResponseBody(InputStream responseStream) throws IOException {
        String rBody = new Scanner(responseStream, "UTF-8")
                .useDelimiter("\\A")
                .next();
        responseStream.close();
        return rBody;
    }

    private void sendSavedForm(Runnable callback) {
        if (canceled) {
            return;
        }
        showEditDoneProgress(true, true);
        validateRequest = new TLRPC.TL_payments_validateRequestedInfo();
        if (invoiceInput != null) {
            validateRequest.invoice = invoiceInput;
        } else if (messageObject != null) {
            TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
            inputInvoice.peer = getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
            inputInvoice.msg_id = messageObject.getId();
            validateRequest.invoice = inputInvoice;
        } else {
            TLRPC.TL_inputInvoiceSlug inputInvoice = new TLRPC.TL_inputInvoiceSlug();
            inputInvoice.slug = invoiceSlug;
            validateRequest.invoice = inputInvoice;
        }
        validateRequest.save = true;
        validateRequest.info = paymentForm.saved_info;

        TLObject req = validateRequest;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_payments_validatedRequestedInfo) {
                AndroidUtilities.runOnUIThread(() -> {
                    requestedInfo = (TLRPC.TL_payments_validatedRequestedInfo) response;
                    callback.run();
                    setDonePressed(false);
                    showEditDoneProgress(true, false);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    setDonePressed(false);
                    showEditDoneProgress(true, false);
                    if (error != null) {
                        AlertsCreator.processError(currentAccount, error, PaymentFormActivity.this, req);
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void sendForm() {
        if (canceled) {
            return;
        }
        showEditDoneProgress(true, true);
        validateRequest = new TLRPC.TL_payments_validateRequestedInfo();
        if (invoiceInput != null) {
            validateRequest.invoice = invoiceInput;
        } else if (messageObject != null) {
            TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
            inputInvoice.peer = getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
            inputInvoice.msg_id = messageObject.getId();
            validateRequest.invoice = inputInvoice;
        } else {
            TLRPC.TL_inputInvoiceSlug inputInvoice = new TLRPC.TL_inputInvoiceSlug();
            inputInvoice.slug = invoiceSlug;
            validateRequest.invoice = inputInvoice;
        }
        validateRequest.save = saveShippingInfo;
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
        TLObject req = validateRequest;
        ConnectionsManager.getInstance(currentAccount).sendRequest(validateRequest, (response, error) -> {
            if (response instanceof TLRPC.TL_payments_validatedRequestedInfo) {
                AndroidUtilities.runOnUIThread(() -> {
                    requestedInfo = (TLRPC.TL_payments_validatedRequestedInfo) response;
                    if (paymentForm.saved_info != null && !saveShippingInfo) {
                        TLRPC.TL_payments_clearSavedInfo req1 = new TLRPC.TL_payments_clearSavedInfo();
                        req1.info = true;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {

                        });
                    }
                    goToNextStep();
                    setDonePressed(false);
                    showEditDoneProgress(true, false);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
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
                                AlertsCreator.processError(currentAccount, error, PaymentFormActivity.this, req);
                                break;
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void sendData() {
        if (canceled) {
            return;
        }
        showEditDoneProgress(false, true);
        TLRPC.TL_payments_sendPaymentForm req = new TLRPC.TL_payments_sendPaymentForm();
        if (invoiceInput != null) {
            req.invoice = invoiceInput;
        } else if (messageObject != null) {
            TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
            inputInvoice.peer = getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
            inputInvoice.msg_id = messageObject.getId();
            req.invoice = inputInvoice;
        } else {
            TLRPC.TL_inputInvoiceSlug inputInvoice = new TLRPC.TL_inputInvoiceSlug();
            inputInvoice.slug = invoiceSlug;
            req.invoice = inputInvoice;
        }

        req.form_id = paymentForm.form_id;
        if (UserConfig.getInstance(currentAccount).tmpPassword != null && savedCredentialsCard != null) {
            req.credentials = new TLRPC.TL_inputPaymentCredentialsSaved();
            req.credentials.id = savedCredentialsCard.id;
            req.credentials.tmp_password = UserConfig.getInstance(currentAccount).tmpPassword.tmp_password;
        } else if (googlePayCredentials != null) {
            req.credentials = googlePayCredentials;
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
        if ((paymentForm.invoice.flags & 256) != 0) {
            req.tip_amount = tipAmount != null ? tipAmount : 0;
            req.flags |= 4;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                if (response instanceof TLRPC.TL_payments_paymentResult) {
                    TLRPC.Updates updates = ((TLRPC.TL_payments_paymentResult) response).updates;
                    TLRPC.Message[] message = new TLRPC.Message[1];
                    for (int a = 0, N = updates.updates.size(); a < N; a++) {
                        TLRPC.Update update = updates.updates.get(a);
                        if (update instanceof TLRPC.TL_updateNewMessage) {
                            message[0] = ((TLRPC.TL_updateNewMessage) update).message;
                            break;
                        } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                            message[0] = ((TLRPC.TL_updateNewChannelMessage) update).message;
                            break;
                        }
                    }
                    getMessagesController().processUpdates(updates, false);
                    AndroidUtilities.runOnUIThread(() -> {
                        Context context = getContext();
                        if (context == null) context = ApplicationLoader.applicationContext;
                        if (context == null) context = LaunchActivity.instance;
                        if (context == null) return;

                        paymentStatusSent = true;
                        invoiceStatus = InvoiceStatus.PAID;
                        final boolean isStars = invoiceInput instanceof TLRPC.TL_inputInvoiceStars;
                        final boolean isStarsGift = isStars && ((TLRPC.TL_inputInvoiceStars) invoiceInput).purpose instanceof TLRPC.TL_inputStorePaymentStarsGift;
                        final boolean isStarsGiveaway = isStars && ((TLRPC.TL_inputInvoiceStars) invoiceInput).purpose instanceof TLRPC.TL_inputStorePaymentStarsGiveaway;
                        if (!isStars && paymentFormCallback != null) {
                            paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                        }

                        goToNextStep();
                        if (isStars && paymentFormCallback != null) {
                            paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                        }
                        final long giftUserId = getStarsGiftUserId();
                        String giftUser = "";
                        if (giftUserId > 0) {
                            giftUser = UserObject.getForcedFirstName(getMessagesController().getUser(giftUserId));
                        } else if (giftUserId < 0) {
                            TLRPC.Chat chat = getMessagesController().getChat(-giftUserId);
                            giftUser = chat != null ? chat.title : "";
                        }
                        long stars = getStars();
                        int icon = isStars ? (isStarsGift || isStarsGiveaway ? R.raw.stars_send : R.raw.stars_topup) : R.raw.payment_success;
                        CharSequence bulletinTitle = !isStars ? null : (isStarsGiveaway ? getString(R.string.StarsGiveawaySentPopup) : isStarsGift ? getString(R.string.StarsGiftSentPopup) : getString(R.string.StarsAcquired));
                        CharSequence bulletinText = AndroidUtilities.replaceTags(
                            isStars ?
                                isStarsGiveaway ? LocaleController.formatPluralStringComma("StarsGiveawaySentPopupInfo", (int) stars) : LocaleController.formatPluralStringComma(isStarsGift ? "StarsGiftSentPopupInfo" : "StarsAcquiredInfo", (int) stars, giftUser) :
                                LocaleController.formatString(R.string.PaymentInfoHint, totalPrice[0], currentItemName)
                        );
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        BulletinFactory factory = BulletinFactory.of(lastFragment);
                        Bulletin bulletin;
                        if (giftUserId != 0 && bulletinTitle != null && !isStarsGiveaway) {
                            bulletin = factory.createSimpleBulletin(icon, bulletinTitle, bulletinText, getString(R.string.ViewInChat), () -> {
                                BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                                if (lastFragment2 != null) {
                                    lastFragment2.presentFragment(ChatActivity.of(giftUserId));
                                }
                            });
                        } else if (bulletinTitle != null) {
                            bulletin = factory.createSimpleBulletin(icon, bulletinTitle, bulletinText);
                        } else {
                            bulletin = factory.createSimpleBulletin(icon, bulletinText);
                        }
                        bulletin.hideAfterBottomSheet = false;
                        bulletin.setDuration(Bulletin.DURATION_PROLONG);
                        if (message[0] != null) {
                            bulletin.setOnClickListener(v -> {
                                bulletin.hide();
                                if (isStarsGift) {
                                    BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                                    if (fragment != null) {
                                        fragment.presentFragment(ChatActivity.of(MessageObject.getDialogId(message[0]), message[0].id));
                                    }
                                } else {
                                    TLRPC.TL_payments_getPaymentReceipt req2 = new TLRPC.TL_payments_getPaymentReceipt();
                                    req2.msg_id = message[0].id;
                                    req2.peer = MessagesController.getInstance(currentAccount).getInputPeer(message[0].peer_id);
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response2 instanceof TLRPC.TL_payments_paymentReceiptStars) {
                                            StarsIntroActivity.showTransactionSheet(getContext(), false, currentAccount, (TLRPC.TL_payments_paymentReceiptStars) response2, getResourceProvider());
                                        } else if (response2 instanceof TLRPC.PaymentReceipt) {
                                            BaseFragment lastFragment3 = LaunchActivity.getLastFragment();
                                            if (lastFragment3 != null) {
                                                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                                                params.transitionFromLeft = true;
                                                params.allowNestedScroll = false;
                                                lastFragment3.showAsSheet(new PaymentFormActivity((TLRPC.PaymentReceipt) response2), params);
                                            }
                                        }
                                    }), ConnectionsManager.RequestFlagFailOnServerErrors);
                                }
                            });
                        }
                        bulletin.show(isStarsGiveaway);
                    });
                } else if (response instanceof TLRPC.TL_payments_paymentVerificationNeeded) {
                    AndroidUtilities.runOnUIThread(() -> {
                        setDonePressed(false);
                        webviewLoading = true;
                        showEditDoneProgress(true, true);
                        if (progressView != null) {
                            progressView.setVisibility(View.VISIBLE);
                        }
                        if (doneItem != null) {
                            doneItem.setEnabled(false);
                            doneItem.getContentView().setVisibility(View.INVISIBLE);
                        }

                        INavigationLayout parentLayout = getParentLayout();
                        Activity parentActivity = getParentActivity();
                        getMessagesController().newMessageCallback = message -> {
                            if (MessageObject.getPeerId(message.peer_id) == botUser.id && message.action instanceof TLRPC.TL_messageActionPaymentSent) {
                                AndroidUtilities.runOnUIThread(() -> {

                                    paymentStatusSent = true;
                                    invoiceStatus = InvoiceStatus.PAID;

                                    onCheckoutSuccess(parentLayout, parentActivity);

                                    final boolean isStars = invoiceInput instanceof TLRPC.TL_inputInvoiceStars;
                                    final boolean isStarsGift = isStars && ((TLRPC.TL_inputInvoiceStars) invoiceInput).purpose instanceof TLRPC.TL_inputStorePaymentStarsGift;
                                    final boolean isStarsGiveaway = isStars && ((TLRPC.TL_inputInvoiceStars) invoiceInput).purpose instanceof TLRPC.TL_inputStorePaymentStarsGiveaway;
                                    if (!isStars && paymentFormCallback != null) {
                                        paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                                    }

                                    goToNextStep();
                                    if (isStars && paymentFormCallback != null) {
                                        paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                                    }
                                    final long giftUserId = getStarsGiftUserId();
                                    String giftUser = "";
                                    if (giftUserId > 0) {
                                        giftUser = UserObject.getForcedFirstName(getMessagesController().getUser(giftUserId));
                                    } else if (giftUserId < 0) {
                                        TLRPC.Chat chat = getMessagesController().getChat(-giftUserId);
                                        giftUser = chat != null ? chat.title : "";
                                    }
                                    long stars = getStars();
                                    int icon = isStars ? (isStarsGift || isStarsGiveaway ? R.raw.stars_send : R.raw.stars_topup) : R.raw.payment_success;
                                    CharSequence bulletinTitle = !isStars ? null : (isStarsGiveaway ? getString(R.string.StarsGiveawaySentPopup) : isStarsGift ? getString(R.string.StarsGiftSentPopup) : getString(R.string.StarsAcquired));
                                    CharSequence bulletinText = AndroidUtilities.replaceTags(
                                        isStars ?
                                            isStarsGiveaway ? LocaleController.formatPluralStringComma("StarsGiveawaySentPopupInfo", (int) stars) : LocaleController.formatPluralStringComma(isStarsGift ? "StarsGiftSentPopupInfo" : "StarsAcquiredInfo", (int) stars, giftUser) :
                                            LocaleController.formatString(R.string.PaymentInfoHint, totalPrice[0], currentItemName)
                                    );
                                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                    if (lastFragment == null) return;
                                    BulletinFactory factory = BulletinFactory.of(lastFragment);
                                    Bulletin bulletin;
                                    if (giftUserId != 0 && bulletinTitle != null && !isStarsGiveaway) {
                                        bulletin = factory.createSimpleBulletin(icon, bulletinTitle, bulletinText, getString(R.string.ViewInChat), () -> {
                                            BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                                            if (lastFragment2 != null) {
                                                lastFragment2.presentFragment(ChatActivity.of(giftUserId));
                                            }
                                        });
                                    } else if (bulletinTitle != null) {
                                        bulletin = factory.createSimpleBulletin(icon, bulletinTitle, bulletinText);
                                    } else {
                                        bulletin = factory.createSimpleBulletin(icon, bulletinText);
                                    }
                                    bulletin.hideAfterBottomSheet = false;
                                    bulletin.setDuration(Bulletin.DURATION_PROLONG);
                                    if (message != null) {
                                        bulletin.setOnClickListener(v -> {
                                            bulletin.hide();
                                            if (isStarsGift) {
                                                BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                                                if (fragment != null) {
                                                    fragment.presentFragment(ChatActivity.of(MessageObject.getDialogId(message), message.id));
                                                }
                                            } else {
                                                TLRPC.TL_payments_getPaymentReceipt req2 = new TLRPC.TL_payments_getPaymentReceipt();
                                                req2.msg_id = message.id;
                                                req2.peer = MessagesController.getInstance(currentAccount).getInputPeer(message.peer_id);
                                                ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                                    if (response2 instanceof TLRPC.TL_payments_paymentReceiptStars) {
                                                        StarsIntroActivity.showTransactionSheet(getContext(), false, currentAccount, (TLRPC.TL_payments_paymentReceiptStars) response2, getResourceProvider());
                                                    } else if (response2 instanceof TLRPC.PaymentReceipt) {
                                                        BaseFragment lastFragment3 = LaunchActivity.getLastFragment();
                                                        if (lastFragment3 != null) {
                                                            BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                                                            params.transitionFromLeft = true;
                                                            params.allowNestedScroll = false;
                                                            lastFragment3.showAsSheet(new PaymentFormActivity((TLRPC.PaymentReceipt) response2), params);
                                                        }
                                                    }
                                                }), ConnectionsManager.RequestFlagFailOnServerErrors);
                                            }
                                        });
                                    }
                                    bulletin.show(isStarsGiveaway);
                                });
                                return true;
                            }
                            return false;
                        };

                        if (webView != null) {
                            webView.setVisibility(View.VISIBLE);
                            webView.loadUrl(webViewUrl = ((TLRPC.TL_payments_paymentVerificationNeeded) response).url);
                        }

                        paymentStatusSent = true;
                        invoiceStatus = InvoiceStatus.PENDING;
                        if (paymentFormCallback != null) {
                            paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                        }
                    });
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    AlertsCreator.processError(currentAccount, error, PaymentFormActivity.this, req);
                    setDonePressed(false);
                    showEditDoneProgress(false, false);

                    paymentStatusSent = true;
                    invoiceStatus = InvoiceStatus.FAILED;
                    if (paymentFormCallback != null) {
                        paymentFormCallback.onInvoiceStatusChanged(invoiceStatus);
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private long getStars() {
        if (invoiceInput instanceof TLRPC.TL_inputInvoiceStars) {
            TLRPC.TL_inputInvoiceStars invoiceInputStars = (TLRPC.TL_inputInvoiceStars) invoiceInput;
            final TLRPC.InputStorePaymentPurpose purpose = invoiceInputStars.purpose;
            if (purpose instanceof TLRPC.TL_inputStorePaymentStarsGift) {
                return ((TLRPC.TL_inputStorePaymentStarsGift) purpose).stars;
            } else if (purpose instanceof TLRPC.TL_inputStorePaymentStarsTopup) {
                return ((TLRPC.TL_inputStorePaymentStarsTopup) purpose).stars;
            } else if (purpose instanceof TLRPC.TL_inputStorePaymentStarsGiveaway) {
                return ((TLRPC.TL_inputStorePaymentStarsGiveaway) purpose).stars;
            }
        }
        return 0;
    }

    private long getStarsGiftUserId() {
        if (invoiceInput instanceof TLRPC.TL_inputInvoiceStars) {
            TLRPC.TL_inputInvoiceStars invoiceInputStars = (TLRPC.TL_inputInvoiceStars) invoiceInput;
            final TLRPC.InputStorePaymentPurpose purpose = invoiceInputStars.purpose;
            if (purpose instanceof TLRPC.TL_inputStorePaymentStarsGift) {
                TLRPC.TL_inputStorePaymentStarsGift p = (TLRPC.TL_inputStorePaymentStarsGift) purpose;
                if (p.user_id != null) return p.user_id.user_id;
            } else if (purpose instanceof TLRPC.TL_inputStorePaymentStarsGiveaway) {
                TLRPC.TL_inputStorePaymentStarsGiveaway p = (TLRPC.TL_inputStorePaymentStarsGiveaway) purpose;
                if (p.boost_peer != null) return DialogObject.getPeerDialogId(p.boost_peer);
            }
        }
        return 0;
    }

    private void shakeField(int field) {
        shakeView(inputFields[field]);
    }

    private void shakeView(View view) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {}
        AndroidUtilities.shakeViewSpring(view, 2.5f);
    }

    private void setDonePressed(boolean value) {
        donePressed = value;
        swipeBackEnabled = !value;
        if (actionBar != null && actionBar.getBackButton() != null) {
            actionBar.getBackButton().setEnabled(!donePressed);
        }
        if (detailSettingsCell[0] != null) {
            detailSettingsCell[0].setEnabled(!donePressed);
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return swipeBackEnabled;
    }

    private void checkPassword() {
        if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
            if (UserConfig.getInstance(currentAccount).tmpPassword.valid_until < ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60) {
                UserConfig.getInstance(currentAccount).tmpPassword = null;
                UserConfig.getInstance(currentAccount).saveConfig(false);
            }
        }
        if (UserConfig.getInstance(currentAccount).tmpPassword != null) {
            sendData();
            return;
        }
        if (inputFields[FIELD_SAVEDPASSWORD].length() == 0) {
            try {
                inputFields[FIELD_SAVEDPASSWORD].performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) {}
            AndroidUtilities.shakeViewSpring(inputFields[FIELD_SAVEDPASSWORD], 2.5f);
            return;
        }
        final String password = inputFields[FIELD_SAVEDPASSWORD].getText().toString();
        showEditDoneProgress(true, true);
        setDonePressed(true);
        final TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.account_Password currentPassword = (TLRPC.account_Password) response;
                if (!TwoStepVerificationActivity.canHandleCurrentPassword(currentPassword, false)) {
                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString(R.string.UpdateAppAlert), true);
                    return;
                }
                if (!currentPassword.has_password) {
                    passwordOk = false;
                    goToNextStep();
                } else {
                    byte[] passwordBytes = AndroidUtilities.getStringBytes(password);

                    Utilities.globalQueue.postRunnable(() -> {
                        final byte[] x_bytes;
                        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                            x_bytes = SRPHelper.getX(passwordBytes, algo);
                        } else {
                            x_bytes = null;
                        }

                        final TLRPC.TL_account_getTmpPassword req1 = new TLRPC.TL_account_getTmpPassword();
                        req1.period = 60 * 30;

                        RequestDelegate requestDelegate = (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            showEditDoneProgress(true, false);
                            setDonePressed(false);
                            if (response1 != null) {
                                passwordOk = true;
                                UserConfig.getInstance(currentAccount).tmpPassword = (TLRPC.TL_account_tmpPassword) response1;
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                                goToNextStep();
                            } else {
                                if (error1.text.equals("PASSWORD_HASH_INVALID")) {
                                    try {
                                        inputFields[FIELD_SAVEDPASSWORD].performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                    } catch (Exception ignored) {}
                                    AndroidUtilities.shakeViewSpring(inputFields[FIELD_SAVEDPASSWORD], 3.25f);
                                    inputFields[FIELD_SAVEDPASSWORD].setText("");
                                } else {
                                    AlertsCreator.processError(currentAccount, error1, PaymentFormActivity.this, req1);
                                }
                            }
                        });

                        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                            req1.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, algo);
                            if (req1.password == null) {
                                TLRPC.TL_error error2 = new TLRPC.TL_error();
                                error2.text = "ALGO_INVALID";
                                requestDelegate.run(null, error2);
                                return;
                            }
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req1, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        } else {
                            TLRPC.TL_error error2 = new TLRPC.TL_error();
                            error2.text = "PASSWORD_HASH_INVALID";
                            requestDelegate.run(null, error2);
                        }
                    });
                }
            } else {
                AlertsCreator.processError(currentAccount, error, PaymentFormActivity.this, req);
                showEditDoneProgress(true, false);
                setDonePressed(false);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
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
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 1.0f));
            } else {
                if (webView != null) {
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f));
                } else {
                    doneItem.getContentView().setVisibility(View.VISIBLE);
                    doneItem.setEnabled(true);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f));

                    if (!isFinishing()) {
                        doneItemAnimation.playTogether(ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 1.0f),
                                ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 1.0f),
                                ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 1.0f));
                    }
                }
            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (!show) {
                            progressView.setVisibility(View.INVISIBLE);
                        } else {
                            doneItem.getContentView().setVisibility(View.INVISIBLE);
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
                        ObjectAnimator.ofFloat(payTextView, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(payTextView, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(payTextView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(progressViewButton, View.ALPHA, 1.0f));
            } else {
                payTextView.setVisibility(View.VISIBLE);
                bottomLayout.setEnabled(true);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(progressViewButton, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(payTextView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(payTextView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(payTextView, View.ALPHA, 1.0f));

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
    public boolean presentFragment(BaseFragment fragment) {
        onPresentFragment(fragment);
        return super.presentFragment(fragment);
    }

    @Override
    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        onPresentFragment(fragment);
        return super.presentFragment(fragment, removeLast);
    }

    private void onPresentFragment(BaseFragment fragment) {
        AndroidUtilities.hideKeyboard(fragmentView);
        if (fragment instanceof PaymentFormActivity) {
            ((PaymentFormActivity) fragment).paymentFormCallback = paymentFormCallback;
            ((PaymentFormActivity) fragment).resourcesProvider = resourcesProvider;
            ((PaymentFormActivity) fragment).needPayAfterTransition = needPayAfterTransition;
            ((PaymentFormActivity) fragment).savedCredentialsCard = savedCredentialsCard;
        }
    }

    @Override
    public boolean onBackPressed() {
        if (shouldNavigateBack) {
            webView.loadUrl(webViewUrl);
            shouldNavigateBack = false;
            return false;
        }
        return !donePressed;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
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
            arrayList.add(new ThemeDescription(dividers.get(a), ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        }

        arrayList.add(new ThemeDescription(codeFieldCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(codeFieldCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(codeFieldCell, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        arrayList.add(new ThemeDescription(textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        arrayList.add(new ThemeDescription(checkCell1, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        arrayList.add(new ThemeDescription(checkCell1, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(checkCell1, ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));

        for (int a = 0; a < settingsCell.length; a++) {
            arrayList.add(new ThemeDescription(settingsCell[a], ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(settingsCell[a], ThemeDescription.FLAG_SELECTORWHITE, null, null, null, null, Theme.key_listSelector));
            arrayList.add(new ThemeDescription(settingsCell[a], 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        }

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

        return arrayList;
    }

    private class BottomFrameLayout extends FrameLayout {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float progress;
        SpringAnimation springAnimation;

        public BottomFrameLayout(@NonNull Context context, TLRPC.PaymentForm paymentForm) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawColor(getThemedColor(Theme.key_switchTrackBlue));
            paint.setColor(getThemedColor(Theme.key_contacts_inviteBackground));
            canvas.drawCircle(LocaleController.isRTL ? getWidth() - AndroidUtilities.dp(28) : AndroidUtilities.dp(28), -AndroidUtilities.dp(28), Math.max(getWidth(), getHeight()) * progress, paint);
        }

        public void setChecked(boolean checked, boolean animated) {
            if (springAnimation != null) {
                springAnimation.cancel();
            }
            float to = checked ? 1f : 0f;
            if (animated) {
                if (progress == to) {
                    return;
                }
                springAnimation = new SpringAnimation(new FloatValueHolder(progress * 100f))
                        .setSpring(new SpringForce(to * 100f)
                                .setStiffness(checked ? 500f : 650f)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
                springAnimation.addUpdateListener((animation, value, velocity) -> {
                    progress = value / 100f;
                    if (payTextView != null) {
                        payTextView.setAlpha(0.8f + 0.2f * progress);
                    }
                    invalidate();
                });
                springAnimation.addEndListener((animation, canceled1, value, velocity) -> {
                    if (animation == springAnimation) {
                        springAnimation = null;
                    }
                });
                springAnimation.start();
            } else {
                progress = to;
                if (payTextView != null) {
                    payTextView.setAlpha(0.8f + 0.2f * progress);
                }
                invalidate();
            }
        }
    }
}

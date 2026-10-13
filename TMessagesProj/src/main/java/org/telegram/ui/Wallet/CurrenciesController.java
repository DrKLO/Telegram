package org.telegram.ui.Wallet;

import org.telegram.messenger.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.util.ULocale;
import android.os.Build;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.BillingUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_wallet;

import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class CurrenciesController {

    public final int currentAccount;
    private final GramWallet wallet;
    private boolean loadingCurrencyRates;
    private TL_wallet.currencyRates currencyRates;
    private String currencyRatesLanguage = "en";
    private String currencySetting;

    public CurrenciesController(GramWallet wallet) {
        this.wallet = wallet;
        currentAccount = wallet.currentAccount;
        loadPrefs();
        if (!TextUtils.equals(getCurrency(), "USD")) {
            getCurrencies();
        }
    }

    private void loadPrefs() {
        try {
            final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("gram_wallet", Context.MODE_PRIVATE);
            currencySetting = prefs.getString("currency", getCurrency());
        } catch (Exception e) {
            FileLog.e("[gram-wallet] failed to load currency prefs", e);
        }
    }

    private void savePrefs() {
        try {
            final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("gram_wallet", Context.MODE_PRIVATE);
            prefs.edit().putString("currency", getCurrency()).apply();
        } catch (Exception e) {
            FileLog.e("[gram-wallet] failed to save currency prefs", e);
        }
    }

    @Nullable
    public TL_wallet.currencyRate getSelectedCurrencyRate() {
        final String currency = getCurrency();
        if (currencyRates == null) {
            return TextUtils.equals(currency, "USD") ? createFallbackUsdRate() : null;
        }
        for (int i = 0; i < currencyRates.rates.size(); ++i) {
            final TL_wallet.currencyRate rate = currencyRates.rates.get(i);
            if (TextUtils.equals(rate.currency, currency)) {
                return rate;
            }
        }
        return null;
    }

    public String getCurrency() {
        return currencySetting == null ? "USD" : currencySetting;
    }

    public String getCurrencyTitle() {
        final String currency = getCurrency();
        final TL_wallet.currencyRates currencyRates = getCurrencies();
        if (currencyRates != null) {
            for (int i = 0; i < currencyRates.rates.size(); ++i) {
                final TL_wallet.currencyRate rate = currencyRates.rates.get(i);
                if (TextUtils.equals(rate.currency, currency)) {
                    return TextUtils.isEmpty(rate.translatedTitle) ? rate.title : rate.translatedTitle;
                }
            }
        }
        return "";
    }

    public void setCurrency(String currency) {
        if (TextUtils.equals(currencySetting, currency)) return;
        currencySetting = currency;
        savePrefs();
        wallet.notifyUpdate();
    }

    public double getCurrencyRate() {
        final String currency = getCurrency();
        if (TextUtils.equals(currency, "USD")) {
            return 1.0;
        }
        if (currencyRates == null) return 0.0;
        for (int i = 0; i < currencyRates.rates.size(); ++i) {
            final TL_wallet.currencyRate rate = currencyRates.rates.get(i);
            if (TextUtils.equals(rate.currency, currency)) {
                return rate.rate;
            }
        }
        return 0;
    }

    @Nullable
    public TL_wallet.currencyRates getCurrencies() {
        if (currencyRates == null) {
            if (loadingCurrencyRates) return null;
            loadingCurrencyRates = true;
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(new TL_wallet.getCurrencyRates(), AndroidUtilities::runOnUIThread, (rates, err) -> {
                if (err != null) {
                    FileLog.d("[gram-wallet] failed to load currency rates: " + err.text);
                    return;
                }
                Utilities.stageQueue.postRunnable(() -> {
                    BillingUtilities.fillTitles(rates);
                    AndroidUtilities.runOnUIThread(() -> {
                        currencyRates = rates;
                        loadingCurrencyRates = false;
                        wallet.notifyUpdate();
                    });
                });
            });
        } else {
            final Locale locale = LocaleController.getInstance().getCurrentLocale();
            final String language = locale == null || TextUtils.isEmpty(locale.getLanguage())
                    ? "en"
                    : locale.getLanguage();
            if (!TextUtils.equals(currencyRatesLanguage, language)) {
                final boolean translate = !TextUtils.equals(language, "en");
                for (int i = 0; i < currencyRates.rates.size(); ++i) {
                    final TL_wallet.currencyRate rate = currencyRates.rates.get(i);
                    if (rate == null) {
                        continue;
                    }
                    rate.translatedTitle = null;
                    if (translate && !TextUtils.isEmpty(rate.currency)) {
                        try {
                            final String translatedTitle = Currency.getInstance(rate.currency).getDisplayName(locale);
                            rate.translatedTitle = capitalizeFirstLetter(translatedTitle, locale);
                        } catch (IllegalArgumentException ignore) {
                        }
                    }
                }
                currencyRatesLanguage = language;
            }
        }
        return currencyRates;
    }

    public static String capitalizeFirstLetter(String value, Locale locale) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        for (int offset = 0; offset < value.length(); ) {
            final int codePoint = value.codePointAt(offset);
            final int nextOffset = offset + Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                return value.substring(0, offset)
                        + value.substring(offset, nextOffset).toUpperCase(locale)
                        + value.substring(nextOffset);
            }
            offset = nextOffset;
        }
        return value;
    }

    public ArrayList<TL_wallet.currencyRate> getHintCurrencies() {
        final ArrayList<TL_wallet.currencyRate> result = new ArrayList<>();
        final TL_wallet.currencyRates rates = getCurrencies();
        TL_wallet.currencyRate usdRate = null, eurRate = null;
        if (rates != null && rates.rates != null) {
            for (TL_wallet.currencyRate rate : rates.rates) {
                if (rate != null && TextUtils.equals(rate.currency, "USD")) {
                    usdRate = rate;
                } else if (rate != null && TextUtils.equals(rate.currency, "EUR")) {
                    eurRate = rate;
                }
            }
        }
        result.add(usdRate == null ? createFallbackUsdRate() : usdRate);
        if (eurRate != null) result.add(eurRate);
        if (rates == null || rates.rates == null) {
            return result;
        }

        final LinkedHashSet<String> currencyCodes = new LinkedHashSet<>();
        currencyCodes.add(getCurrency());
        addKeyboardCurrencyHints(currencyCodes);
        addCurrencyHintForCountry(currencyCodes, MessagesController.getInstance(currentAccount).config.phoneCountryIso2.get());
        addCurrencyHintForLocale(currencyCodes, LocaleController.getInstance().getCurrentLocale());
        addCurrencyHintForLocale(currencyCodes, LocaleController.getInstance().getSystemDefaultLocale());

        for (String currencyCode : currencyCodes) {
            if (TextUtils.equals(currencyCode, "USD") || TextUtils.equals(currencyCode, "EUR")) {
                continue;
            }
            for (TL_wallet.currencyRate rate : rates.rates) {
                if (rate != null && TextUtils.equals(rate.currency, currencyCode)) {
                    result.add(rate);
                    break;
                }
            }
        }
        return result;
    }

    private TL_wallet.currencyRate createFallbackUsdRate() {
        final TL_wallet.currencyRate rate = new TL_wallet.currencyRate();
        rate.currency = "USD";
        rate.rate = 1.0;
        rate.title = LocaleController.getString(R.string.WalletCurrencyUSD);
        rate.symbol = "$";
        rate.thousandsSeparator = ",";
        rate.decimalSeparator = ".";
        rate.symbolLeft = true;
        rate.spaceBetween = false;
        rate.dropZeros = false;
        rate.exp = 2;
        return rate;
    }

    private static void addKeyboardCurrencyHints(LinkedHashSet<String> currencyCodes) {
        try {
            final InputMethodManager inputMethodManager = (InputMethodManager) ApplicationLoader.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager == null) {
                return;
            }
            addCurrencyHintForKeyboardSubtype(currencyCodes, inputMethodManager.getCurrentInputMethodSubtype());
            final List<InputMethodInfo> inputMethods = inputMethodManager.getEnabledInputMethodList();
            for (InputMethodInfo inputMethod : inputMethods) {
                final List<InputMethodSubtype> subtypes = inputMethodManager.getEnabledInputMethodSubtypeList(inputMethod, true);
                for (InputMethodSubtype subtype : subtypes) {
                    addCurrencyHintForKeyboardSubtype(currencyCodes, subtype);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void addCurrencyHintForKeyboardSubtype(LinkedHashSet<String> currencyCodes, InputMethodSubtype subtype) {
        if (subtype == null || !"keyboard".equals(subtype.getMode())) {
            return;
        }
        String languageTag = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            languageTag = subtype.getLanguageTag();
        }
        if (TextUtils.isEmpty(languageTag)) {
            languageTag = subtype.getLocale();
        }
        if (!TextUtils.isEmpty(languageTag)) {
            addCurrencyHintForLocale(currencyCodes, Locale.forLanguageTag(languageTag.replace('_', '-')));
        }
    }

    private static void addCurrencyHintForLocale(LinkedHashSet<String> currencyCodes, Locale locale) {
        if (locale == null) {
            return;
        }
        String country = locale.getCountry();
        if (TextUtils.isEmpty(country) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            country = ULocale.addLikelySubtags(ULocale.forLocale(locale)).getCountry();
        }
        addCurrencyHintForCountry(currencyCodes, country);
    }

    private static void addCurrencyHintForCountry(LinkedHashSet<String> currencyCodes, String country) {
        if (TextUtils.isEmpty(country)) {
            return;
        }
        try {
            final Currency currency = Currency.getInstance(new Locale("", country.toUpperCase(Locale.US)));
            if (currency != null) {
                currencyCodes.add(currency.getCurrencyCode());
            }
        } catch (IllegalArgumentException ignore) {
        }
    }

}

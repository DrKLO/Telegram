package org.telegram.ui.Components.Premium.boosts;

import android.os.Build;
import android.util.Pair;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BoostRepository {

    public static int prepareServerDate(long date) {
        long twoMinutes = 1000L * 60 * 2;
        if (date < System.currentTimeMillis() + twoMinutes) {
            date = System.currentTimeMillis() + twoMinutes;
        }
        return (int) (date / 1000L);
    }

    public static long giveawayAddPeersMax() {
        return MessagesController.getInstance(UserConfig.selectedAccount).giveawayAddPeersMax;
    }

    public static long giveawayPeriodMax() {
        return MessagesController.getInstance(UserConfig.selectedAccount).giveawayPeriodMax;
    }

    public static long giveawayCountriesMax() {
        return MessagesController.getInstance(UserConfig.selectedAccount).giveawayCountriesMax;
    }

    public static int giveawayBoostsPerPremium() {
        return (int) MessagesController.getInstance(UserConfig.selectedAccount).giveawayBoostsPerPremium;
    }

    public static boolean isMultiBoostsAvailable() {
        return MessagesController.getInstance(UserConfig.selectedAccount).boostsPerSentGift > 0;
    }

    public static int boostsPerSentGift() {
        return (int) MessagesController.getInstance(UserConfig.selectedAccount).boostsPerSentGift;
    }

    public static void loadParticipantsCount(Utilities.Callback<HashMap<Long, Integer>> callback) {
        MessagesStorage storage = MessagesStorage.getInstance(UserConfig.selectedAccount);
        storage.getStorageQueue().postRunnable(() -> {
            HashMap<Long, Integer> participantsCountByChat = storage.getSmallGroupsParticipantsCount();
            if (participantsCountByChat == null || participantsCountByChat.isEmpty()) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(participantsCountByChat));
        });
    }

    public static ArrayList<TLRPC.InputPeer> getMyChannels(long currentChatId) {
        ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();
        final MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
        final ArrayList<TLRPC.Dialog> dialogs = messagesController.getAllDialogs();
        for (int i = 0; i < dialogs.size(); ++i) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (DialogObject.isChatDialog(dialog.id)) {
                TLRPC.Chat chat = messagesController.getChat(-dialog.id);
                if (ChatObject.isBoostSupported(chat) && -dialog.id != currentChatId) {
                    peers.add(messagesController.getInputPeer(dialog.id));
                }
            }
        }
        return peers;
    }

    public static void payGiftCode(List<TLObject> users, TLRPC.TL_premiumGiftCodeOption option, TLRPC.Chat chat, BaseFragment baseFragment, Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        if (!isGoogleBillingAvailable()) {
            payGiftCodeByInvoice(users, option, chat, baseFragment, onSuccess, onError);
        } else {
            payGiftCodeByGoogle(users, option, chat, baseFragment, onSuccess, onError);
        }
    }

    public static boolean isGoogleBillingAvailable() {
        if (BuildVars.useInvoiceBilling()) {
            return false;
        }
        return BillingController.getInstance().isReady();
    }

    public static void payGiftCodeByInvoice(List<TLObject> users, TLRPC.TL_premiumGiftCodeOption option, TLRPC.Chat chat, BaseFragment baseFragment, Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        TLRPC.TL_inputInvoicePremiumGiftCode invoice = new TLRPC.TL_inputInvoicePremiumGiftCode();
        TLRPC.TL_inputStorePaymentPremiumGiftCode payload = new TLRPC.TL_inputStorePaymentPremiumGiftCode();

        payload.users = new ArrayList<>();
        for (TLObject user : users) {
            if (user instanceof TLRPC.User) {
                payload.users.add(controller.getInputUser((TLRPC.User) user));
            }
        }

        if (chat != null) {
            payload.flags = 1;
            payload.boost_peer = controller.getInputPeer(-chat.id);
        }

        payload.currency = option.currency;
        payload.amount = option.amount;

        invoice.purpose = payload;
        invoice.option = option;

        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(baseFragment.getResourceProvider());
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = invoice;

        int requestId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                onError.run(error);
                return;
            }
            PaymentFormActivity paymentFormActivity = null;
            if (response instanceof TLRPC.PaymentForm) {
                TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                form.invoice.recurring = true;
                controller.putUsers(form.users, false);
                paymentFormActivity = new PaymentFormActivity(form, invoice, baseFragment);
            } else if (response instanceof TLRPC.PaymentReceipt) {
                paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
            }
            if (paymentFormActivity != null) {
                paymentFormActivity.setPaymentFormCallback(status -> {
                    if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                        onSuccess.run(null);
                    } else if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                        onError.run(null);
                    }
                });
                LaunchActivity.getLastFragment().showAsSheet(paymentFormActivity, new BaseFragment.BottomSheetParams());
            } else {
                onError.run(null);
            }
        }));
    }

    public static void payGiftCodeByGoogle(List<TLObject> users, TLRPC.TL_premiumGiftCodeOption option, TLRPC.Chat chat, BaseFragment baseFragment, Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_inputStorePaymentPremiumGiftCode payload = new TLRPC.TL_inputStorePaymentPremiumGiftCode();

        payload.users = new ArrayList<>();
        for (TLObject user : users) {
            if (user instanceof TLRPC.User) {
                payload.users.add(controller.getInputUser((TLRPC.User) user));
            }
        }
        if (chat != null) {
            payload.flags = 1;
            payload.boost_peer = controller.getInputPeer(-chat.id);
        }

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .setProductId(option.store_product)
                .build();
        BillingController.getInstance().queryProductDetails(Arrays.asList(product), (billingResult, list) -> {
            ProductDetails.OneTimePurchaseOfferDetails offerDetails = list.get(0).getOneTimePurchaseOfferDetails();
            payload.currency = offerDetails.getPriceCurrencyCode();
            payload.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));

            TLRPC.TL_payments_canPurchasePremium req = new TLRPC.TL_payments_canPurchasePremium();
            req.purpose = payload;
            connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    onError.run(error);
                    return;
                }
                if (response != null) {
                    BillingController.getInstance().addResultListener(list.get(0).getProductId(), billingResult1 -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            AndroidUtilities.runOnUIThread(() -> onSuccess.run(null));
                        }
                    });
                    BillingController.getInstance().setOnCanceled(() -> {
                        AndroidUtilities.runOnUIThread(() -> onError.run(null));
                    });
                    BillingController.getInstance().launchBillingFlow(
                            baseFragment.getParentActivity(), AccountInstance.getInstance(UserConfig.selectedAccount), payload,
                            Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(list.get(0))
                                    .build())
                    );
                }
            }));
        });
    }

    public static void launchPreparedGiveaway(TL_stories.PrepaidGiveaway prepaidGiveaway, List<TLObject> chats, List<TLObject> selectedCountries,
                                              TLRPC.Chat chat, int date, boolean onlyNewSubscribers, boolean winnersVisible, boolean withAdditionPrize, int users, String prizeDesc,
                                              Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.InputStorePaymentPurpose finalPurpose;
        if (prepaidGiveaway instanceof TL_stories.TL_prepaidGiveaway) {
            TLRPC.TL_inputStorePaymentPremiumGiveaway purpose = new TLRPC.TL_inputStorePaymentPremiumGiveaway();
            purpose.only_new_subscribers = onlyNewSubscribers;
            purpose.winners_are_visible = winnersVisible;
            purpose.prize_description = prizeDesc;
            purpose.until_date = date;
            purpose.flags |= 2;
            purpose.flags |= 4;
            if (withAdditionPrize) {
                purpose.flags |= 16;
            }
            purpose.random_id = System.currentTimeMillis();
            purpose.additional_peers = new ArrayList<>();
            purpose.boost_peer = controller.getInputPeer(-chat.id);
            purpose.currency = "";

            for (TLObject object : selectedCountries) {
                TLRPC.TL_help_country country = (TLRPC.TL_help_country) object;
                purpose.countries_iso2.add(country.iso2);
            }

            for (TLObject o : chats) {
                if (o instanceof TLRPC.Chat) {
                    purpose.additional_peers.add(controller.getInputPeer(-((TLRPC.Chat) o).id));
                }
            }

            finalPurpose = purpose;
        } else if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
            TLRPC.TL_inputStorePaymentStarsGiveaway purpose = new TLRPC.TL_inputStorePaymentStarsGiveaway();
            purpose.only_new_subscribers = onlyNewSubscribers;
            purpose.winners_are_visible = winnersVisible;
            purpose.prize_description = prizeDesc;
            purpose.until_date = date;
            purpose.flags |= 2;
            purpose.flags |= 4;
            if (withAdditionPrize) {
                purpose.flags |= 16;
            }
            purpose.random_id = System.currentTimeMillis();
            purpose.additional_peers = new ArrayList<>();
            purpose.boost_peer = controller.getInputPeer(-chat.id);
            purpose.currency = "";

            purpose.stars = ((TL_stories.TL_prepaidStarsGiveaway) prepaidGiveaway).stars;
            purpose.users = prepaidGiveaway.quantity;

            for (TLObject object : selectedCountries) {
                TLRPC.TL_help_country country = (TLRPC.TL_help_country) object;
                purpose.countries_iso2.add(country.iso2);
            }

            for (TLObject o : chats) {
                if (o instanceof TLRPC.Chat) {
                    purpose.additional_peers.add(controller.getInputPeer(-((TLRPC.Chat) o).id));
                }
            }

            finalPurpose = purpose;
        } else {
            return;
        }

        TLRPC.TL_payments_launchPrepaidGiveaway req = new TLRPC.TL_payments_launchPrepaidGiveaway();
        req.giveaway_id = prepaidGiveaway.id;
        req.peer = controller.getInputPeer(-chat.id);
        req.purpose = finalPurpose;
        connection.sendRequest(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> onError.run(error));
                return;
            }
            if (response != null) {
                controller.processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> onSuccess.run(null));
            }
        });
    }

    public static void payGiveAway(List<TLObject> chats, List<TLObject> selectedCountries, TLRPC.TL_premiumGiftCodeOption option,
                                   TLRPC.Chat chat, int date, boolean onlyNewSubscribers, BaseFragment baseFragment,
                                   boolean winnersVisible, boolean withAdditionPrize, String prizeDesc,
                                   Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        if (!isGoogleBillingAvailable()) {
            payGiveAwayByInvoice(chats, selectedCountries, option, chat, date, onlyNewSubscribers, baseFragment, winnersVisible, withAdditionPrize, prizeDesc, onSuccess, onError);
        } else {
            payGiveAwayByGoogle(chats, selectedCountries, option, chat, date, onlyNewSubscribers, baseFragment, winnersVisible, withAdditionPrize, prizeDesc, onSuccess, onError);
        }
    }

    public static void payGiveAwayByInvoice(List<TLObject> chats, List<TLObject> selectedCountries, TLRPC.TL_premiumGiftCodeOption option,
                                            TLRPC.Chat chat, int date, boolean onlyNewSubscribers, BaseFragment baseFragment,
                                            boolean winnersVisible, boolean withAdditionPrize, String prizeDesc,
                                            Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        TLRPC.TL_inputInvoicePremiumGiftCode invoice = new TLRPC.TL_inputInvoicePremiumGiftCode();
        TLRPC.TL_inputStorePaymentPremiumGiveaway payload = new TLRPC.TL_inputStorePaymentPremiumGiveaway();

        payload.only_new_subscribers = onlyNewSubscribers;
        payload.winners_are_visible = winnersVisible;
        payload.prize_description = prizeDesc;
        payload.until_date = date;
        payload.flags |= 2;
        payload.flags |= 4;
        if (withAdditionPrize) {
            payload.flags |= 16;
        }
        payload.random_id = System.currentTimeMillis();
        payload.additional_peers = new ArrayList<>();
        for (TLObject o : chats) {
            if (o instanceof TLRPC.Chat) {
                payload.additional_peers.add(controller.getInputPeer(-((TLRPC.Chat) o).id));
            }
        }
        payload.boost_peer = controller.getInputPeer(-chat.id);
        payload.boost_peer = controller.getInputPeer(-chat.id);
        payload.currency = option.currency;
        payload.amount = option.amount;

        for (TLObject object : selectedCountries) {
            TLRPC.TL_help_country country = (TLRPC.TL_help_country) object;
            payload.countries_iso2.add(country.iso2);
        }

        invoice.purpose = payload;
        invoice.option = option;

        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(baseFragment.getResourceProvider());
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = invoice;

        int requestId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                onError.run(error);
                return;
            }
            PaymentFormActivity paymentFormActivity = null;
            if (response instanceof TLRPC.PaymentForm) {
                TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                form.invoice.recurring = true;
                controller.putUsers(form.users, false);
                paymentFormActivity = new PaymentFormActivity(form, invoice, baseFragment);
            } else if (response instanceof TLRPC.PaymentReceipt) {
                paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
            }
            if (paymentFormActivity != null) {
                paymentFormActivity.setPaymentFormCallback(status -> {
                    if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                        onSuccess.run(null);
                    } else if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                        onError.run(null);
                    }
                });
                LaunchActivity.getLastFragment().showAsSheet(paymentFormActivity, new BaseFragment.BottomSheetParams());
            } else {
                onError.run(null);
            }
        }));
    }

    public static void payGiveAwayByGoogle(List<TLObject> chats, List<TLObject> selectedCountries, TLRPC.TL_premiumGiftCodeOption option,
                                           TLRPC.Chat chat, int date, boolean onlyNewSubscribers, BaseFragment baseFragment,
                                           boolean winnersVisible, boolean withAdditionPrize, String prizeDesc,
                                           Utilities.Callback<Void> onSuccess, Utilities.Callback<TLRPC.TL_error> onError) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_inputStorePaymentPremiumGiveaway payload = new TLRPC.TL_inputStorePaymentPremiumGiveaway();

        payload.only_new_subscribers = onlyNewSubscribers;
        payload.winners_are_visible = winnersVisible;
        payload.prize_description = prizeDesc;
        payload.until_date = date;
        payload.flags |= 2;
        payload.flags |= 4;
        if (withAdditionPrize) {
            payload.flags |= 16;
        }
        payload.random_id = System.currentTimeMillis();
        payload.additional_peers = new ArrayList<>();
        for (TLObject o : chats) {
            if (o instanceof TLRPC.Chat) {
                payload.additional_peers.add(controller.getInputPeer(-((TLRPC.Chat) o).id));
            }
        }
        payload.boost_peer = controller.getInputPeer(-chat.id);
        for (TLObject object : selectedCountries) {
            TLRPC.TL_help_country country = (TLRPC.TL_help_country) object;
            payload.countries_iso2.add(country.iso2);
        }

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .setProductId(option.store_product)
                .build();
        BillingController.getInstance().queryProductDetails(Arrays.asList(product), (billingResult, list) -> {
            ProductDetails.OneTimePurchaseOfferDetails offerDetails = list.get(0).getOneTimePurchaseOfferDetails();
            payload.currency = offerDetails.getPriceCurrencyCode();
            payload.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));

            TLRPC.TL_payments_canPurchasePremium req = new TLRPC.TL_payments_canPurchasePremium();
            req.purpose = payload;
            connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    onError.run(error);
                    return;
                }
                if (response != null) {
                    BillingController.getInstance().addResultListener(list.get(0).getProductId(), billingResult1 -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            AndroidUtilities.runOnUIThread(() -> onSuccess.run(null));
                        }
                    });
                    BillingController.getInstance().setOnCanceled(() -> {
                        AndroidUtilities.runOnUIThread(() -> onError.run(null));
                    });
                    BillingController.getInstance().launchBillingFlow(
                            baseFragment.getParentActivity(), AccountInstance.getInstance(UserConfig.selectedAccount), payload,
                            Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(list.get(0))
                                    .build())
                    );
                }
            }));
        });
    }

    public static List<TLRPC.TL_premiumGiftCodeOption> filterGiftOptions(List<TLRPC.TL_premiumGiftCodeOption> list, int selected) {
        List<TLRPC.TL_premiumGiftCodeOption> result = new ArrayList<>();
        for (TLRPC.TL_premiumGiftCodeOption item : list) {
            boolean isAvailableInGoogleStore = item.store_product != null;
            if (item.users == selected) {
                result.add(item);
            }
        }
        if (result.isEmpty()) {
            for (TLRPC.TL_premiumGiftCodeOption item : list) {
                if (item.users == 1) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    public static List<TLRPC.TL_premiumGiftCodeOption> filterGiftOptionsByBilling(List<TLRPC.TL_premiumGiftCodeOption> list) {
        if (BoostRepository.isGoogleBillingAvailable()) {
            List<TLRPC.TL_premiumGiftCodeOption> result = new ArrayList<>();
            for (TLRPC.TL_premiumGiftCodeOption item : list) {
                boolean isAvailableInGoogleStore = item.store_product != null;
                if (isAvailableInGoogleStore) {
                    result.add(item);
                }
            }
            return result;
        } else {
            return list;
        }
    }

    public static void loadCountries(Utilities.Callback<Pair<Map<String, List<TLRPC.TL_help_country>>, List<String>>> onDone) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.TL_help_getCountriesList req = new TLRPC.TL_help_getCountriesList();
        req.lang_code = LocaleController.getInstance().getCurrentLocaleInfo() != null ? LocaleController.getInstance().getCurrentLocaleInfo().getLangCode() : Locale.getDefault().getCountry();
        int reqId = connection.sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_help_countriesList help_countriesList = (TLRPC.TL_help_countriesList) response;
                Map<String, List<TLRPC.TL_help_country>> countriesMap = new HashMap<>();
                List<String> sortedLetters = new ArrayList<>();

                for (int i = 0; i < help_countriesList.countries.size(); i++) {
                    TLRPC.TL_help_country country = help_countriesList.countries.get(i);
                    if (country.name != null) {
                        country.default_name = country.name;
                    }
                    if (country.iso2.equalsIgnoreCase("FT")) {
                        continue;
                    }
                    String letter = country.default_name.substring(0, 1).toUpperCase();
                    List<TLRPC.TL_help_country> arr = countriesMap.get(letter);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        countriesMap.put(letter, arr);
                        sortedLetters.add(letter);
                    }
                    arr.add(country);
                }

                Comparator<String> comparator;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Collator collator = Collator.getInstance(LocaleController.getInstance().getCurrentLocale() != null ? LocaleController.getInstance().getCurrentLocale() : Locale.getDefault());
                    comparator = collator::compare;
                } else {
                    comparator = String::compareTo;
                }
                Collections.sort(sortedLetters, comparator);
                for (List<TLRPC.TL_help_country> arr : countriesMap.values()) {
                    Collections.sort(arr, (country, country2) -> comparator.compare(country.default_name, country2.default_name));
                }
                AndroidUtilities.runOnUIThread(() -> onDone.run(new Pair<>(countriesMap, sortedLetters)));
            }
        });
    }

    public static int loadGiftOptions(TLRPC.Chat chat, Utilities.Callback<List<TLRPC.TL_premiumGiftCodeOption>> onDone) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_payments_getPremiumGiftCodeOptions req = new TLRPC.TL_payments_getPremiumGiftCodeOptions();
        if (chat != null) {
            req.flags = 1;
            req.boost_peer = controller.getInputPeer(-chat.id);
        }

        return connection.sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                List<TLRPC.TL_premiumGiftCodeOption> result = new ArrayList<>();
                List<QueryProductDetailsParams.Product> products = new ArrayList<>();
                for (int i = 0; i < vector.objects.size(); i++) {
                    final TLRPC.TL_premiumGiftCodeOption object = (TLRPC.TL_premiumGiftCodeOption) vector.objects.get(i);
                    result.add(object);
                    if (object.store_product != null) {
                        products.add(QueryProductDetailsParams.Product.newBuilder()
                                .setProductType(BillingClient.ProductType.INAPP)
                                .setProductId(object.store_product)
                                .build());
                    }
                }
                if (products.isEmpty() || !isGoogleBillingAvailable()) {
                    AndroidUtilities.runOnUIThread(() -> onDone.run(result));
                    return;
                }
                BillingController.getInstance().queryProductDetails(products, (billingResult, list) -> {
                    for (ProductDetails productDetails : list) {
                        ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                        for (TLRPC.TL_premiumGiftCodeOption option : result) {
                            if (option.store_product != null && option.store_product.equals(productDetails.getProductId())) {
                                option.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));
                                option.currency = offerDetails.getPriceCurrencyCode();
                                break;
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> onDone.run(result));
                });
            }
        });
    }

    public static int searchContacts(int reqId, String query, Utilities.Callback<List<TLRPC.User>> onDone) {
        final int currentAccount = UserConfig.selectedAccount;
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.TL_contact> contacts = ContactsController.getInstance(currentAccount).contacts;
        if (contacts == null || contacts.isEmpty()) {
            ContactsController.getInstance(currentAccount).loadContacts(false, 0);
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final String q = query.toLowerCase();
        final String qt = AndroidUtilities.translitSafe(q);
        if (contacts != null) {
            for (int i = 0; i < contacts.size(); ++i) {
                final TLRPC.TL_contact contact = contacts.get(i);
                if (contact != null) {
                    final TLRPC.User user = messagesController.getUser(contact.user_id);
                    if (user == null || user.bot || UserObject.isService(user.id) || UserObject.isUserSelf(user)) continue;
                    final String u = UserObject.getUserName(user).toLowerCase();
                    final String ut = AndroidUtilities.translitSafe(u);
                    if (u.startsWith(q) || u.contains(" " + q) || ut.startsWith(qt) || ut.contains(" " + qt)) {
                        users.add(user);
                    } else if (user.usernames != null) {
                        for (int j = 0; j < user.usernames.size(); ++j) {
                            TLRPC.TL_username username = user.usernames.get(j);
                            if (username == null || !username.active) continue;
                            final String us = username.username.toLowerCase();
                            if (us.startsWith(q) || us.contains("_" + q) || us.startsWith(qt) || us.contains(" " + qt)) {
                                users.add(user);
                                break;
                            }
                        }
                    } else if (user.username != null) {
                        final String us = user.username.toLowerCase();
                        if (us.startsWith(q) || us.contains("_" + q) || us.startsWith(qt) || us.contains(" " + qt)) {
                            users.add(user);
                        }
                    }
                }
            }
        }
        onDone.run(users);
        return -1;
//        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
//        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
//        if (reqId != 0) {
//            connection.cancelRequest(reqId, false);
//        }
//        if (query == null || query.isEmpty()) {
//            AndroidUtilities.runOnUIThread(() -> onDone.run(Collections.emptyList()));
//            return 0;
//        }
//        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
//        req.q = query;
//        req.limit = 50;
//        return connection.sendRequest(req, (response, error) -> {
//            if (response instanceof TLRPC.TL_contacts_found) {
//                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
//                controller.putUsers(res.users, false);
//                List<TLRPC.User> result = new ArrayList<>();
//                for (int a = 0; a < res.users.size(); a++) {
//                    TLRPC.User user = res.users.get(a);
//                    if (!user.self && !UserObject.isDeleted(user) && !user.bot && !UserObject.isService(user.id)) {
//                        result.add(user);
//                    }
//                }
//                AndroidUtilities.runOnUIThread(() -> onDone.run(result));
//            }
//        });
    }

    public static void searchChats(long currentChatId, int guid, String query, int count, Utilities.Callback<List<TLRPC.InputPeer>> onDone) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        req.limit = 50;

        int reqId = connection.sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_contacts_found) {
                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                controller.putChats(res.chats, false);
                List<TLRPC.InputPeer> result = new ArrayList<>();
                for (int a = 0; a < res.chats.size(); a++) {
                    TLRPC.Chat chat = res.chats.get(a);
                    TLRPC.InputPeer inputPeer = MessagesController.getInputPeer(chat);
                    if (chat.id != currentChatId && ChatObject.isBoostSupported(chat)) {
                        result.add(inputPeer);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> onDone.run(result));
            }
        });
    }

    public static void loadChatParticipants(long chatId, int guid, String query, int offset, int count, Utilities.Callback<List<TLRPC.InputPeer>> onDone) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = controller.getInputChannel(chatId);
        req.filter = query == null ? new TLRPC.TL_channelParticipantsRecent() : new TLRPC.TL_channelParticipantsSearch();
        req.filter.q = query == null ? "" : query;
        req.offset = offset;
        req.limit = count;

        int reqId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_channels_channelParticipants) {
                TLRPC.TL_channels_channelParticipants res = ((TLRPC.TL_channels_channelParticipants) response);
                controller.putUsers(res.users, false);
                controller.putChats(res.chats, false);
                long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                List<TLRPC.InputPeer> result = new ArrayList<>();
                for (int a = 0; a < res.participants.size(); a++) {
                    TLRPC.Peer peer = res.participants.get(a).peer;
                    if (MessageObject.getPeerId(peer) != selfId) {
                        TLRPC.User user = controller.getUser(peer.user_id);
                        if (user != null && !UserObject.isDeleted(user) && !user.bot) {
                            result.add(controller.getInputPeer(peer));
                        }
                    }
                }
                onDone.run(result);
            }
        }));
    }

    public static void checkGiftCode(String slug, Utilities.Callback<TLRPC.TL_payments_checkedGiftCode> onDone, Utilities.Callback<TLRPC.TL_error> onError) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_payments_checkGiftCode req = new TLRPC.TL_payments_checkGiftCode();
        req.slug = slug;
        int reqId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_payments_checkedGiftCode) {
                TLRPC.TL_payments_checkedGiftCode checkedGiftCode = (TLRPC.TL_payments_checkedGiftCode) response;
                controller.putChats(checkedGiftCode.chats, false);
                controller.putUsers(checkedGiftCode.users, false);
                onDone.run((TLRPC.TL_payments_checkedGiftCode) response);
            }
            onError.run(error);
        }));
    }

    public static void applyGiftCode(String slug, Utilities.Callback<Void> onDone, Utilities.Callback<TLRPC.TL_error> onError) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_payments_applyGiftCode req = new TLRPC.TL_payments_applyGiftCode();
        req.slug = slug;
        int reqId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                onError.run(error);
                return;
            }
            onDone.run(null);
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static void getGiveawayInfo(MessageObject messageObject, Utilities.Callback<TLRPC.payments_GiveawayInfo> onDone, Utilities.Callback<TLRPC.TL_error> onError) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_payments_getGiveawayInfo req = new TLRPC.TL_payments_getGiveawayInfo();
        req.msg_id = messageObject.getId();
        req.peer = controller.getInputPeer(MessageObject.getPeerId(messageObject.messageOwner.peer_id));
        int reqId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                onError.run(error);
                return;
            }
            if (response instanceof TLRPC.payments_GiveawayInfo) {
                onDone.run((TLRPC.payments_GiveawayInfo) response);
            }
        }));
    }

    public static void getMyBoosts(Utilities.Callback<TL_stories.TL_premium_myBoosts> onDone, Utilities.Callback<TLRPC.TL_error> onError) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        TL_stories.TL_premium_getMyBoosts req = new TL_stories.TL_premium_getMyBoosts();
        int reqId = connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                onError.run(error);
                return;
            }
            if (response instanceof TL_stories.TL_premium_myBoosts) {
                TL_stories.TL_premium_myBoosts myBoosts = (TL_stories.TL_premium_myBoosts) response;
                controller.putUsers(myBoosts.users, false);
                controller.putChats(myBoosts.chats, false);
                onDone.run(myBoosts);
            }
        }));
    }

    public static void applyBoost(long dialogId, List<Integer> slots, Utilities.Callback<TL_stories.TL_premium_myBoosts> onDone, Utilities.Callback<TLRPC.TL_error> onError) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        TL_stories.TL_premium_applyBoost req = new TL_stories.TL_premium_applyBoost();
        req.peer = controller.getInputPeer(-dialogId);
        req.flags |= 1;
        req.slots.addAll(slots);
        connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                onError.run(error);
                return;
            }
            if (response instanceof TL_stories.TL_premium_myBoosts) {
                TL_stories.TL_premium_myBoosts myBoosts = (TL_stories.TL_premium_myBoosts) response;
                controller.putUsers(myBoosts.users, false);
                controller.putChats(myBoosts.chats, false);
                onDone.run(myBoosts);
            }
        }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
    }
}

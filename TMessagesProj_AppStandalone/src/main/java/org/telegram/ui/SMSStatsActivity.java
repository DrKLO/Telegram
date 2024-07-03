package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SMSJobController;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.web.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TL_smsjobs;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.LimitPreviewView;
import org.telegram.ui.Components.Premium.boosts.GiftInfoBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class SMSStatsActivity extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private final static int VIEW_TYPE_HEADER = 0;
    private final static int VIEW_TYPE_TABLE = 1;
    private final static int VIEW_TYPE_SHADOW = 2;
    private final static int VIEW_TYPE_BUTTON = 3;
    private final static int VIEW_TYPE_SWITCH = 4;
    private final static int VIEW_TYPE_HEADERCELL = 5;

    private ArrayList<Item> oldItems = new ArrayList<>();
    private ArrayList<Item> items = new ArrayList<>();
    private boolean allowInternationalSet = false, allowInternational = false;
    private boolean askedStatusToLoad = false;

    public static CharSequence error(int sz) {
        SpannableString error = new SpannableString("!");
        Drawable errorDrawable = new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                Theme.chat_docBackPaint.setColor(Theme.getColor(Theme.key_text_RedBold));
                canvas.drawCircle(getIntrinsicWidth() / 2f, getIntrinsicHeight() / 2f, getIntrinsicWidth() / 2f, Theme.chat_docBackPaint);
                int w = Theme.dialogs_errorDrawable.getIntrinsicWidth();
                int h = Theme.dialogs_errorDrawable.getIntrinsicHeight();
                if (sz < 18) {
                    w *= .8f;
                    h *= .8f;
                }
                Theme.dialogs_errorDrawable.setBounds((int) (getIntrinsicWidth() / 2f - w / 2), (int) (getIntrinsicHeight() / 2f - h / 2), (int) (getIntrinsicWidth() / 2f + w / 2), (int) (getIntrinsicHeight() / 2f + h / 2));
                Theme.dialogs_errorDrawable.draw(canvas);
            }
            @Override
            public void setAlpha(int alpha) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
            @Override
            public int getIntrinsicHeight() {
                return dp(sz);
            }
            @Override
            public int getIntrinsicWidth() {
                return dp(sz);
            }
        };
        errorDrawable.setBounds(0, 0, dp(sz), dp(sz));
        error.setSpan(new ImageSpan(errorDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return error;
    }

    private final AdapterWithDiffUtils adapter = new AdapterWithDiffUtils() {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int viewType = holder.getItemViewType();
            return viewType == VIEW_TYPE_BUTTON || viewType == VIEW_TYPE_SWITCH;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = getHeader(getContext());
                    break;
                case VIEW_TYPE_HEADERCELL:
                    view = new HeaderCell(getContext());
                    break;
                case VIEW_TYPE_TABLE:
                    view = table = new TableView(getContext(), currentAccount);
                    break;
                case VIEW_TYPE_BUTTON:
                    view = new TextCell(getContext());
                    break;
                case VIEW_TYPE_SWITCH:
                    view = new TextCell(getContext(), 23, false, true, getResourceProvider());
                    break;
                default:
                case VIEW_TYPE_SHADOW:
                    view = new TextInfoPrivacyCell(getContext());
                    Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), org.telegram.messenger.R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourceProvider));
                    Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }


        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size())
                return VIEW_TYPE_SHADOW;
            return items.get(position).viewType;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size())
                return;
            SMSStatsActivity.Item item = items.get(position);
            final int viewType = holder.getItemViewType();
            final boolean divider = position + 1 < items.size() && items.get(position + 1).viewType == viewType;
            if (viewType == VIEW_TYPE_SHADOW) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                final boolean isLast = position == items.size() - 1;
                if (TextUtils.isEmpty(item.text)) {
                    cell.setFixedSize(isLast ? 350 : 21);
                    cell.setText("");
                } else {
                    cell.setFixedSize(0);
                    cell.setText(item.text);
                }
            } else if (viewType == VIEW_TYPE_BUTTON) {
                TextCell cell = (TextCell) holder.itemView;
                if (item.red) {
                    cell.setColors(Theme.key_text_RedBold, Theme.key_text_RedRegular);
                } else {
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                }
                if (item.id == BUTTON_HISTORY) {
                    cell.setTextAndValueAndIcon(item.text.toString(), item.error ? error(19) : null, item.icon, divider);
                } else if (item.id == BUTTON_SIM) {
                    SMSJobController.SIM sim = SMSJobController.getInstance(currentAccount).getSelectedSIM();
                    String simCard = sim == null ? "" : sim.name;
                    if (item.icon == 0) {
                        cell.setTextAndValue(item.text.toString(), simCard, divider);
                    } else {
                        cell.setTextAndValueAndIcon(item.text.toString(), simCard, item.icon, divider);
                    }
                } else if (item.icon == 0) {
                    cell.setText(item.text, divider);
                } else {
                    cell.setTextAndIcon(item.text, item.icon, divider);
                }
            } else if (viewType == VIEW_TYPE_SWITCH) {
                TextCell cell = (TextCell) holder.itemView;
                boolean checked = false;
                if (item.id == BUTTON_ALLOW_INTERNATIONAL) {
                    checked = allowInternational;
                }
                cell.setTextAndCheck(item.text, checked, divider);
            } else if (viewType == VIEW_TYPE_TABLE) {
                ((TableView) holder.itemView).update(false);
            } else if (viewType == VIEW_TYPE_HEADERCELL) {
                ((HeaderCell) holder.itemView).setText(item.text);
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder.getItemViewType() == VIEW_TYPE_TABLE) {
                ((TableView) holder.itemView).update(false);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    };

    private static class Item extends AdapterWithDiffUtils.Item {

        public int id;
        public int icon;
        public CharSequence text;
        public boolean red;
        public boolean error;
        public SMSJobController.JobEntry entry;

        public Item(int viewType) {
            super(viewType, false);
        }

        public static Item asButton(int id, int icon, CharSequence text) {
            Item item = new Item(VIEW_TYPE_BUTTON);
            item.id = id;
            item.icon = icon;
            item.text = text;
            return item;
        }

        public static Item asSwitch(int id, CharSequence text) {
            Item item = new Item(VIEW_TYPE_SWITCH);
            item.id = id;
            item.text = text;
            return item;
        }

        public static Item asHeader(CharSequence text) {
            Item item = new Item(VIEW_TYPE_HEADERCELL);
            item.text = text;
            return item;
        }

        public static Item asShadow(CharSequence text) {
            Item item = new Item(VIEW_TYPE_SHADOW);
            item.text = text;
            return item;
        }

        public Item makeRed() {
            this.red = true;
            return this;
        }

        public Item setError(boolean error) {
            this.error = error;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return item.id == id && item.viewType == viewType && item.entry == entry && icon == item.icon && red == item.red && Objects.equals(text, item.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(icon, text, red);
        }
    }

    public final static int BUTTON_TERMS = 1;
    public final static int BUTTON_PREMIUM = 2;
    public final static int BUTTON_ALLOW_INTERNATIONAL = 3;
    public final static int BUTTON_SIM = 4;
    public final static int BUTTON_HISTORY = 5;
    public final static int BUTTON_DEACTIVATE = 6;

    private TableView table;
    public void updateItems() {
        final int state = SMSJobController.getInstance(currentAccount).getState();

        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        items.add(new Item(VIEW_TYPE_HEADER));
        items.add(new Item(VIEW_TYPE_TABLE));
        items.add(Item.asShadow(null));
        items.add(Item.asButton(BUTTON_TERMS, R.drawable.menu_intro, LocaleController.getString(R.string.SmsToS)));
        items.add(Item.asButton(BUTTON_PREMIUM, R.drawable.menu_premium_main, LocaleController.getString(R.string.SmsPremiumBenefits)));
        if (state == SMSJobController.STATE_JOINED && !SMSJobController.getInstance(currentAccount).journal.isEmpty()) {
            items.add(Item.asButton(BUTTON_HISTORY, R.drawable.menu_sms_history, LocaleController.getString(R.string.SmsHistory)).setError(SMSJobController.getInstance(currentAccount).hasError()));
        }
        if (state == SMSJobController.STATE_JOINED) {
            final int simsCount = SMSJobController.getInstance(currentAccount).simsCount();
            if (simsCount > 1 || simsCount == 1 && Build.VERSION.SDK_INT < 22) {
                items.add(Item.asButton(BUTTON_SIM, R.drawable.menu_storage_path, LocaleController.getString(R.string.SmsActiveSim)));
            }
        }
        items.add(Item.asShadow(null));
        items.add(Item.asSwitch(BUTTON_ALLOW_INTERNATIONAL, LocaleController.getString(R.string.SmsAllowInternational)));
        items.add(Item.asShadow(LocaleController.getString(R.string.SmsCostsInfo)));
        if (state != SMSJobController.STATE_NONE) {
            items.add(Item.asButton(BUTTON_DEACTIVATE, 0, LocaleController.getString(R.string.SmsDeactivate)).makeRed());
        }
        items.add(Item.asShadow(null));

        if (adapter != null) {
            adapter.setItems(oldItems, items);
        }
    }

    public SMSStatsActivity() {
        super();
        updateItems();
    }

    @Override
    public View createView(Context context) {
        View rootView = super.createView(context);
        updateHeader();
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) return;
            Item item = items.get(position);

            if (item.viewType == VIEW_TYPE_HEADER) {
                if (SMSJobController.getInstance(currentAccount).getState() == SMSJobController.STATE_ASKING_PERMISSION) {
                    SMSSubscribeSheet.requestSMSPermissions(getContext(), () -> {
                        SMSJobController.getInstance(currentAccount).checkSelectedSIMCard();
                        if (SMSJobController.getInstance(currentAccount).getSelectedSIM() == null) {
                            SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_NO_SIM);
                            new AlertDialog.Builder(getContext(), getResourceProvider())
                                    .setTitle(LocaleController.getString(R.string.SmsNoSimTitle))
                                    .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.SmsNoSimMessage)))
                                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                                    .show();
                            return;
                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_join(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            if (err != null) {
                                BulletinFactory.showError(err);
                            } else if (res instanceof TLRPC.TL_boolFalse) {
                                BulletinFactory.global().createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                            } else {
                                SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_JOINED);
                                SMSJobController.getInstance(currentAccount).loadStatus(true);
                                SMSSubscribeSheet.showSubscribed(getContext(), getResourceProvider());
                                update(true);
                            }
                        }));
                    }, false);
                }
            } else if (item.id == BUTTON_TERMS) {
                TL_smsjobs.TL_smsjobs_status status = SMSJobController.getInstance(currentAccount).currentStatus;
                TL_smsjobs.TL_smsjobs_eligibleToJoin isEligible = SMSJobController.getInstance(currentAccount).isEligible;
                if (status != null) {
                    Browser.openUrl(getContext(), status.terms_url);
                } else if (isEligible != null) {
                    Browser.openUrl(getContext(), isEligible.terms_of_use);
                }
            } else if (item.id == BUTTON_ALLOW_INTERNATIONAL) {
                if (SMSJobController.getInstance(currentAccount).currentState != SMSJobController.STATE_JOINED) {
                    return;
                }
                SMSJobController.getInstance(currentAccount).toggleAllowInternational(allowInternational = !allowInternational);
                ((TextCell) view).setChecked(allowInternational);
            } else if (item.id == BUTTON_PREMIUM) {
                presentFragment(new PremiumPreviewFragment("sms"));
            } else if (item.id == BUTTON_DEACTIVATE) {
                AlertDialog d = new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(LocaleController.getString(R.string.SmsDeactivateTitle))
                    .setMessage(LocaleController.getString(R.string.SmsDeactivateMessage))
                    .setPositiveButton(LocaleController.getString(R.string.VoipGroupLeave), (di, w) -> {
                        finishFragment();
                        if (SMSJobController.getInstance(currentAccount).getState() == SMSJobController.STATE_JOINED) {
                            AndroidUtilities.runOnUIThread(() -> {
                                SMSJobController.getInstance(currentAccount).leave();
                            }, 120);
                        } else {
                            SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_NONE);
                        }
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Back), null)
                    .setDimAlpha(0.5f)
                    .create();
                showDialog(d);
                ((TextView) d.getButton(DialogInterface.BUTTON_POSITIVE)).setTextColor(getThemedColor(Theme.key_text_RedBold));
            } else if (item.id == BUTTON_SIM) {
                try {
                    ArrayList<SMSJobController.SIM> finalSims = SMSJobController.getInstance(currentAccount).getSIMs();
                    SMSJobController.SIM finalSelectedSim = SMSJobController.getInstance(currentAccount).getSelectedSIM();
                    if (finalSims == null) return;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.SmsSelectSim));
                    final LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    for (int a = 0, N = finalSims.size(); a < N; a++) {
                        final SMSJobController.SIM sim = finalSims.get(a);
                        if (sim == null) continue;
                        LanguageCell cell = new LanguageCell(context);
                        cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                        cell.setTag(a);
                        String description = "";
                        if (sim.country != null) {
                            description += LocationController.countryCodeToEmoji(sim.country);
                        }
                        if (!TextUtils.isEmpty(description)) {
                            description += " ";
                        }
                        description += sim.name;
                        NotificationCenter.listenEmojiLoading(cell.textView2);
                        cell.setValue(AndroidUtilities.replaceTags("**SIM" + (1 + sim.slot) + "**"), Emoji.replaceEmoji(description, cell.textView2.getPaint().getFontMetricsInt(), false));
                        cell.setLanguageSelected(finalSelectedSim != null && finalSelectedSim.id == sim.id, false);
                        cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
                        linearLayout.addView(cell);
                        cell.setOnClickListener(v -> {
                            SMSJobController.getInstance(currentAccount).setSelectedSIM(sim);
                            ((TextCell) view).setValue(sim.name, !LocaleController.isRTL);
                            builder.getDismissRunnable().run();
                        });
                    }
                    builder.setNegativeButton(LocaleController.getString("Cancel", org.telegram.messenger.R.string.Cancel), null);
                    showDialog(builder.create());
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (item.id == BUTTON_HISTORY) {
                showDialog(new SMSHistorySheet(this));
            }
        });

        AndroidUtilities.cancelRunOnUIThread(checkAirplaneMode);
        AndroidUtilities.runOnUIThread(checkAirplaneMode, 1200);
        return rootView;
    }

    private LimitPreviewView limitPreviewView;
    private View aboveTitleView;
    private FrameLayout underTitleView;
    private TextView errorChipTextView;

    private void updateHeader() {
        limitPreviewView = new LimitPreviewView(getContext(), R.drawable.msg_limit_chats, 0, 0, resourceProvider);
        limitPreviewView.isStatistic = true;
        limitPreviewView.setDarkGradientProvider(this::setDarkGradientLocation);
        aboveTitleView = new FrameLayout(getContext()) {
            {
                addView(limitPreviewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 60, 8, 33));
            }
        };

        underTitleView = new FrameLayout(getContext());
        errorChipTextView = new TextView(getContext());
        errorChipTextView.setBackground(Theme.createRoundRectDrawable(dp(30), Theme.multAlpha(Color.WHITE, .2f)));
        errorChipTextView.setPadding(dp(10), dp(4), dp(10), dp(4));
        errorChipTextView.setTextColor(Color.WHITE);
        errorChipTextView.setText(getString(R.string.SmsAirplaneMode));
        errorChipTextView.setCompoundDrawablePadding(dp(6));
        errorChipTextView.setGravity(Gravity.CENTER);
        Drawable errorDrawable = getContext().getResources().getDrawable(R.drawable.list_warning_sign).mutate();
        errorDrawable.setBounds(0, 0, errorDrawable.getIntrinsicWidth(), errorDrawable.getIntrinsicHeight());
        errorDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        errorChipTextView.setCompoundDrawables(errorDrawable, null, null, null);
        underTitleView.addView(errorChipTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));
        if (isAirplaneMode(getContext())) {
            underTitleView.setPadding(0, dp(12), 0, 0);
            errorChipTextView.setVisibility(View.VISIBLE);
        } else {
            underTitleView.setPadding(0, 0, 0, 0);
            if (errorChipTextView != null) {
                errorChipTextView.setVisibility(View.GONE);
            }
        }

        update(false);
    }

    private boolean lastAirplaneMode;
    public static boolean isAirplaneMode(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    private Runnable checkAirplaneMode = () -> {
        final boolean airplane = isAirplaneMode(getContext());
        if (lastAirplaneMode != airplane) {
            lastAirplaneMode = airplane;
            update(true);
        }
        AndroidUtilities.runOnUIThread(this.checkAirplaneMode, 1200);
    };

    private void update(boolean animated) {
        final int state = SMSJobController.getInstance(currentAccount).getState();

        TL_smsjobs.TL_smsjobs_status status = SMSJobController.getInstance(currentAccount).currentStatus;
        TL_smsjobs.TL_smsjobs_eligibleToJoin isEligible = SMSJobController.getInstance(currentAccount).isEligible;
        if (status == null && !askedStatusToLoad) {
            SMSJobController.getInstance(currentAccount).loadStatus(true);
            askedStatusToLoad = true;
        }

        if (!allowInternational) {
            allowInternationalSet = status != null;
            allowInternational = status != null && status.allow_international;
        }

        if (isAirplaneMode(getContext())) {
            if (errorChipTextView != null) {
                errorChipTextView.setVisibility(View.VISIBLE);
            }
            if (underTitleView != null) {
                underTitleView.setPadding(0, dp(12), 0, 0);
            }
        } else {
            if (errorChipTextView != null) {
                errorChipTextView.setVisibility(View.GONE);
            }
            if (underTitleView != null) {
                underTitleView.setPadding(0, 0, 0, 0);
            }
        }

        final int sentNumber = status == null ? 0 : status.recent_sent;
        final int remainingNumber = status == null ? (isEligible == null ? 0 : isEligible.monthly_sent_sms) : status.recent_remains;

        if (limitPreviewView != null) {
            limitPreviewView.setStatus(sentNumber, sentNumber + remainingNumber, animated);
        }
        if (table != null) {
            table.update(animated);
        }

        if (state == SMSJobController.STATE_NO_SIM) {
            SMSJobController.getInstance(currentAccount).checkSelectedSIMCard();
            configureHeader(
                getString(R.string.SmsStatusNoSim),
                getString(R.string.SmsStatusNoSimSubtitle),
                aboveTitleView,
                underTitleView
            );
        } else if (state == SMSJobController.STATE_ASKING_PERMISSION) {
            if (getParentActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (
                getParentActivity().checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            )) {
                SMSJobController.getInstance(currentAccount).checkSelectedSIMCard();
                if (SMSJobController.getInstance(currentAccount).getSelectedSIM() == null) {
                    SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_NO_SIM);
                    update(true);
                    new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(LocaleController.getString(R.string.SmsNoSimTitle))
                            .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.SmsNoSimMessage)))
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                    return;
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_join(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (err != null) {
                        BulletinFactory.showError(err);
                    } else if (res instanceof TLRPC.TL_boolFalse) {
                        BulletinFactory.global().createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                    } else {
                        SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_JOINED);
                        SMSJobController.getInstance(currentAccount).loadStatus(true);
                        SMSSubscribeSheet.showSubscribed(getContext(), getResourceProvider());
                        update(true);
                    }
                }));
            }

            configureHeader(
                getString(R.string.SmsStatusNoPermission),
                getString(R.string.SmsStatusNoPermissionSubtitle),
                aboveTitleView,
                underTitleView
            );
        } else if (status != null && sentNumber >= sentNumber + remainingNumber) {
            configureHeader(
                formatString(R.string.SmsStatusDone, sentNumber),
                AndroidUtilities.replaceTags(getString(R.string.SmsStatusDoneSubtitle)),
                aboveTitleView,
                underTitleView
            );
        } else {
            if (sentNumber == 0) {
                configureHeader(
                    getString(R.string.SmsStatusFirst),
                    AndroidUtilities.replaceTags(getString(R.string.SmsStatusFirstSubtitle)),
                    aboveTitleView,
                    underTitleView
                );
            } else {
                configureHeader(
                    formatString(R.string.SmsStatusSending, sentNumber, sentNumber + remainingNumber),
                    AndroidUtilities.replaceTags(formatPluralString("SmsStatusSendingSubtitle", remainingNumber)),
                    aboveTitleView,
                    underTitleView
                );
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.smsJobStatusUpdate);
        SMSJobController.getInstance(currentAccount).init();
        SMSJobController.getInstance(currentAccount).atStatisticsPage = true;
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(checkAirplaneMode);
        getNotificationCenter().removeObserver(this, NotificationCenter.smsJobStatusUpdate);
        SMSJobController.getInstance(currentAccount).atStatisticsPage = false;
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.smsJobStatusUpdate) {
            updateItems();
            update(true);
        }
    }

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        return adapter;
    }

    public class TableView extends LinearLayout {

        public final int currentAccount;
        public final AnimatedTextView smsSentTextView;
        public final AnimatedTextView smsRemainingTextView;
        public final TextView sentSinceTitleView;
        public final AnimatedTextView sentSinceDateTextView;
        public final AnimatedTextView giftSinceDateTextView;
        public final LinkSpanDrawable.LinksTextView lastGiftLinkTextView;

        public TableView(Context context, int currentAccount) {
            super(context);
            this.currentAccount = currentAccount;

            setOrientation(LinearLayout.VERTICAL);
            setPadding(dp(22), 0, dp(22), dp(20));

            TextView titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(LocaleController.getString(R.string.SmsOverview));
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            LinearLayout hor = new LinearLayout(context);
            hor.setOrientation(HORIZONTAL);
            addView(hor, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 0, 0));
            LinearLayout left = new LinearLayout(context);
            left.setOrientation(VERTICAL);
            hor.addView(left, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));
            LinearLayout right = new LinearLayout(context);
            right.setOrientation(VERTICAL);
            hor.addView(right, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));

            smsSentTextView = new AnimatedTextView(context, false, true, true);
            smsSentTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            smsSentTextView.setTextSize(dp(17));
            smsSentTextView.setTypeface(AndroidUtilities.bold());
            left.addView(smsSentTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 4, 0, 4, 0));
            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            textView.setText(LocaleController.getString(R.string.SmsTotalSent));
            left.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));

            sentSinceDateTextView = new AnimatedTextView(context, false, true, true);
            sentSinceDateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            sentSinceDateTextView.setTextSize(dp(17));
            sentSinceDateTextView.setTypeface(AndroidUtilities.bold());
            right.addView(sentSinceDateTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 4, 0, 4, 0));
            sentSinceTitleView = new TextView(context);
            sentSinceTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sentSinceTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            sentSinceTitleView.setText(LocaleController.getString(R.string.SmsSentSince));
            right.addView(sentSinceTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));

            hor = new LinearLayout(context);
            hor.setOrientation(HORIZONTAL);
            addView(hor, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 23, 0, 0));
            left = new LinearLayout(context);
            left.setOrientation(VERTICAL);
            hor.addView(left, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));
            right = new LinearLayout(context);
            right.setOrientation(VERTICAL);
            hor.addView(right, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));

            smsRemainingTextView = new AnimatedTextView(context, false, true, true);
            smsRemainingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            smsRemainingTextView.setTextSize(dp(17));
            smsRemainingTextView.setTypeface(AndroidUtilities.bold());
            smsRemainingTextView.setText("0");
            left.addView(smsRemainingTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 4, 0, 4, 0));
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            textView.setText(LocaleController.getString(R.string.SmsRemaining));
            left.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));

            giftSinceDateTextView = new AnimatedTextView(context, false, true, true);
            giftSinceDateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            giftSinceDateTextView.setTextSize(dp(17));
            giftSinceDateTextView.setTypeface(AndroidUtilities.bold());
            right.addView(giftSinceDateTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 4, 0, 4, 0));
            lastGiftLinkTextView = new LinkSpanDrawable.LinksTextView(context);
            lastGiftLinkTextView.setPadding(dp(4), 0, 0, 0);
            lastGiftLinkTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourceProvider));
            lastGiftLinkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            lastGiftLinkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            lastGiftLinkTextView.setText(LocaleController.getString(R.string.SmsLastGiftLink));
            right.addView(lastGiftLinkTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            update(false);
        }

        public void update(boolean animated) {
            TL_smsjobs.TL_smsjobs_status status = SMSJobController.getInstance(currentAccount).currentStatus;
            TL_smsjobs.TL_smsjobs_eligibleToJoin isEligible = SMSJobController.getInstance(currentAccount).isEligible;
            if (status == null && !askedStatusToLoad) {
                SMSJobController.getInstance(currentAccount).loadStatus(true);
                askedStatusToLoad = true;
            }
            if (LocaleController.isRTL) {
                animated = false;
            }

            final int sentNumber = status == null ? 0 : status.recent_sent;
            final int remainingNumber = status == null ? (isEligible == null ? 0 : isEligible.monthly_sent_sms) : status.recent_remains;

            smsSentTextView.setText("" + (status == null ? 0 : status.total_sent), animated);
            smsRemainingTextView.setText("" + remainingNumber, animated);
            if (status == null) {
                sentSinceDateTextView.setText(LocaleController.getString(R.string.None), animated);
            } else {
                String date = LocaleController.formatDateAudio(status.total_since, false);
                if (date.length() > 0) {
                    date = date.substring(0, 1).toUpperCase() + date.substring(1);
                }
                sentSinceDateTextView.setText(date, animated);
            }
            sentSinceTitleView.setText(LocaleController.getString(R.string.SmsStartDate));
            if (status != null && status.last_gift_slug != null) {
                String date = LocaleController.formatDateAudio(status.recent_since, false);
                if (date.length() > 0) {
                    date = date.substring(0, 1).toUpperCase() + date.substring(1);
                }
                giftSinceDateTextView.setText(date, animated);
            } else {
                giftSinceDateTextView.setText(LocaleController.getString(R.string.None), animated);
            }
            SpannableString giftLink = new SpannableString(LocaleController.getString(R.string.SmsLastGiftLink));
            if (status != null && status.last_gift_slug != null) {
                giftLink.setSpan(new URLSpan("") {
                    @Override
                    public void onClick(View widget) {
                        GiftInfoBottomSheet.show(SMSStatsActivity.this, status.last_gift_slug);
                    }
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourceProvider));
                    }
                }, 0, giftLink.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            }
            lastGiftLinkTextView.setText(giftLink);
        }
    }

    public static class SMSHistorySheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public class HeaderCell extends LinearLayout {
            public HeaderCell(Context context) {
                super(context);

                setOrientation(LinearLayout.VERTICAL);

                ImageView imageView = new ImageView(context);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setImageResource(R.drawable.large_sms_code);
                imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton)));
                addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 12));

                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setGravity(Gravity.CENTER);
                textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                textView.setTypeface(AndroidUtilities.bold());
                textView.setText(getString(R.string.SmsHistoryTitle));
                addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 50, 0, 50, 6));

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setGravity(Gravity.CENTER);
                textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                textView.setText(AndroidUtilities.replaceTags(getString(R.string.SmsHistorySubtitle)));
                textView.setLineSpacing(dp(2), 1f);
                addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 50, 0, 50, 20));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        }

        public class TableHeader extends FrameLayout {
            private final LinearLayout container;
            public TableHeader(Context context) {
                super(context);

                container = new LinearLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(350)), MeasureSpec.EXACTLY), heightMeasureSpec);
                    }
                };
                addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 37, Gravity.CENTER_HORIZONTAL, 14, 0, 14, 0));

                TextView dateCountry = new TextView(context);
                dateCountry.setGravity(Gravity.CENTER_VERTICAL);
                dateCountry.setTypeface(AndroidUtilities.bold());
                dateCountry.setText(LocaleController.getString(R.string.SmsHistoryDateCountry));
                dateCountry.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                dateCountry.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                dateCountry.setPadding(dp(13), 0, dp(4), 0);
                container.addView(dateCountry, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 37, 45, Gravity.FILL));

                TextView status = new TextView(context);
                status.setGravity(Gravity.CENTER_VERTICAL);
                status.setTypeface(AndroidUtilities.bold());
                status.setText(LocaleController.getString(R.string.SmsHistoryStatus));
                status.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                status.setPadding(dp(4), 0, dp(13), 0);
                container.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 37, 55, Gravity.FILL));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), dp(37));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                AndroidUtilities.rectTmp.set(container.getX() - dpf2(0.5f), dpf2(0.5f), container.getX() + container.getMeasuredWidth() + dpf2(0.5f), container.getMeasuredHeight() + dp(5));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), backgroundPaint);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), strokePaint);
                canvas.drawLine(container.getX() - dpf2(0.5f), container.getMeasuredHeight() - dpf2(0.5f), container.getX() + container.getMeasuredWidth() + dpf2(0.5f), container.getMeasuredHeight() - dpf2(0.5f), strokePaint);
                super.dispatchDraw(canvas);
            }
        }

        public class TableCell extends FrameLayout {
            private final LinearLayout container;
            private final TextView dateTextView;
            private final TextView countryTextView;
            private final TextView statusTextView;
            private final TextView errorTextView;
            private final TextView errorDescriptionTextView;
            public TableCell(Context context) {
                super(context);

                container = new LinearLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(350)), MeasureSpec.EXACTLY), heightMeasureSpec);
                    }
                };
                container.setOrientation(LinearLayout.VERTICAL);
                addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 14, 0, 14, 0));

                LinearLayout top = new LinearLayout(context);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setMinimumHeight(dp(50));
                container.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL));

                LinearLayout left = new LinearLayout(context);
                left.setOrientation(LinearLayout.VERTICAL);
                left.setGravity(Gravity.CENTER_VERTICAL);

                dateTextView = new TextView(context);
                dateTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                dateTextView.setPadding(dp(13), 0, dp(4), 0);
                left.addView(dateTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2f));

                countryTextView = new TextView(context);
                NotificationCenter.listenEmojiLoading(countryTextView);
                countryTextView.setTextColor(Theme.blendOver(Theme.getColor(Theme.key_dialogBackground), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), .55f)));
                countryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                countryTextView.setPadding(dp(13), 0, dp(4), 0);
                left.addView(countryTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                top.addView(left, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 45, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL));

                LinearLayout right = new LinearLayout(context);
                right.setPadding(0, dp(6), 0, dp(6));
                right.setOrientation(LinearLayout.VERTICAL);
                right.setGravity(Gravity.CENTER_VERTICAL);

                statusTextView = new TextView(context);
                statusTextView.setGravity(Gravity.CENTER_VERTICAL);
                statusTextView.setTypeface(AndroidUtilities.bold());
                statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                statusTextView.setPadding(dp(4), 0, dp(13), 0);
                right.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 55, Gravity.FILL));

                errorTextView = new TextView(context);
                errorTextView.setGravity(Gravity.CENTER_VERTICAL);
                errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                errorTextView.setAlpha(0.7f);
                errorTextView.setPadding(dp(4), 0, dp(13), 0);
                errorTextView.setVisibility(View.GONE);
                errorTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                right.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 55, Gravity.FILL));

                top.addView(right, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 55, Gravity.FILL | Gravity.CENTER_VERTICAL));

                errorDescriptionTextView = new TextView(context);
                errorDescriptionTextView.setVisibility(View.GONE);
                errorDescriptionTextView.setPadding(dp(13), dp(2), dp(13), dp(6));
                errorDescriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
                errorDescriptionTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                errorDescriptionTextView.setGravity(Gravity.CENTER);
                container.addView(errorDescriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
            }

            private boolean isLast;

            public void setEntry(SMSJobController.JobEntry entry, boolean last) {
                if (entry == null) return;
                dateTextView.setText(LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(entry.date * 1000L)) + ", " + LocaleController.getInstance().getFormatterDay().format(new Date(entry.date * 1000L)));
                if (!TextUtils.isEmpty(entry.country)) {
                    countryTextView.setText(Emoji.replaceEmoji(LocationController.countryCodeToEmoji(entry.country) + " " + new Locale("", entry.country).getDisplayCountry(), countryTextView.getPaint().getFontMetricsInt(), false));
                } else {
                    countryTextView.setText("");
                }
                if (entry.state == 1) {
                    statusTextView.setTextColor(Theme.getColor(Theme.key_avatar_nameInMessageOrange));
                    statusTextView.setText(LocaleController.getString(R.string.SmsHistoryStatusPending));
                    errorTextView.setVisibility(View.GONE);
                    errorDescriptionTextView.setVisibility(View.GONE);
                } else if (TextUtils.isEmpty(entry.error)) {
                    statusTextView.setTextColor(Theme.getColor(Theme.key_avatar_nameInMessageGreen));
                    statusTextView.setText(LocaleController.getString(R.string.SmsHistoryStatusSuccess));
                    errorTextView.setVisibility(View.GONE);
                    errorDescriptionTextView.setVisibility(View.GONE);
                } else {
                    statusTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    statusTextView.setText(LocaleController.getString(R.string.SmsHistoryStatusFailure));
                    errorTextView.setVisibility(View.VISIBLE);
                    errorTextView.setText(entry.error);
                    String errorDescription = LocaleController.getString("SmsDescr" + entry.error);
                    if (errorDescription == null || errorDescription.startsWith("LOC_ERR")) {
                        int _idx = entry.error.lastIndexOf("_");
                        if (_idx >= 0) {
                            errorDescription = LocaleController.getString("SmsDescr" + entry.error.substring(0, _idx));
                        }
                        if (errorDescription == null || errorDescription.startsWith("LOC_ERR")) {
                            errorDescriptionTextView.setVisibility(View.GONE);
                        } else {
                            errorDescriptionTextView.setText(errorDescription);
                            errorDescriptionTextView.setVisibility(View.VISIBLE);
                        }
                    } else {
                        errorDescriptionTextView.setText(errorDescription);
                        errorDescriptionTextView.setVisibility(View.VISIBLE);
                    }
                }
                isLast = last;
                invalidate();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (isLast) {
                    AndroidUtilities.rectTmp.set(container.getX() - dpf2(0.5f), -dp(6), container.getX() + container.getMeasuredWidth() + dpf2(0.5f), container.getMeasuredHeight() - dpf2(0.5f));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), strokePaint);
                } else {
                    canvas.drawRect(container.getX() - dpf2(0.5f), -dp(1), container.getX() + container.getMeasuredWidth() + dpf2(0.5f), container.getMeasuredHeight() - dpf2(0.5f), strokePaint);
                }
                super.dispatchDraw(canvas);
            }
        }

        public SMSHistorySheet(BaseFragment fragment) {
            super(fragment, false, false);

            SMSJobController.getInstance(currentAccount).seenError();

            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Theme.getColor(Theme.key_table_border, resourcesProvider));
            backgroundPaint.setColor(Theme.getColor(Theme.key_table_background, resourcesProvider));

            FrameLayout buttonContainer = new FrameLayout(getContext());
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            ButtonWithCounterView button = new ButtonWithCounterView(getContext(), resourcesProvider);
            button.setOnClickListener(v -> {
                dismiss();
            });
            button.setText(LocaleController.getString(R.string.Close), false);
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            View buttonShadow = new View(getContext());
            buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.5f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setDurations(350);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            recyclerListView.setItemAnimator(itemAnimator);
        }

        @Override
        protected CharSequence getTitle() {
            return LocaleController.getString(R.string.SmsHistoryTitle);
        }

        public static final int VIEW_TYPE_HEADER = 0;
        public static final int VIEW_TYPE_TABLE_HEADER = 1;
        public static final int VIEW_TYPE_TABLE_CELL = 2;
        public static final int VIEW_TYPE_BUTTON_PAD = 3;

        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            return new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return false;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view;
                    if (viewType == VIEW_TYPE_HEADER) {
                        view = new HeaderCell(getContext());
                    } else if (viewType == VIEW_TYPE_TABLE_HEADER) {
                        view = new TableHeader(getContext());
                    } else if (viewType == VIEW_TYPE_BUTTON_PAD) {
                        view = new View(getContext()) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(68 + 20), MeasureSpec.EXACTLY));
                            }
                        };
                    } else {
                        view = new TableCell(getContext());
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    position -= 2;
                    ArrayList<SMSJobController.JobEntry> entries = SMSJobController.getInstance(currentAccount).journal;
                    if (position < 0 || entries == null || position >= entries.size()) {
                        return;
                    }
                    ((TableCell) holder.itemView).setEntry(entries.get(position), position + 1 == entries.size());
                }

                @Override
                public int getItemViewType(int position) {
                    if (position == 0) {
                        return VIEW_TYPE_HEADER;
                    } else if (position == 1) {
                        return VIEW_TYPE_TABLE_HEADER;
                    } else if (position == getItemCount() - 1) {
                        return VIEW_TYPE_BUTTON_PAD;
                    } else {
                        return VIEW_TYPE_TABLE_CELL;
                    }
                }

                @Override
                public int getItemCount() {
                    return 3 + SMSJobController.getInstance(currentAccount).journal.size();
                }
            };
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.smsJobStatusUpdate) {
                if (recyclerListView != null && recyclerListView.getAdapter() != null) {
                    recyclerListView.getAdapter().notifyDataSetChanged();
                }
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.smsJobStatusUpdate);
        }

        @Override
        public void dismiss() {
            super.dismiss();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.smsJobStatusUpdate);
        }
    }

}

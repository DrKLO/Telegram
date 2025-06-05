package org.telegram.ui.Components;

import android.content.Context;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

public class TermsOfServiceView extends FrameLayout {

    private TextView textView;
    private TermsOfServiceViewDelegate delegate;
    private TLRPC.TL_help_termsOfService currentTos;
    @SuppressWarnings("FieldCanBeLocal")
    private TextView titleTextView;
    @SuppressWarnings("FieldCanBeLocal")
    private ScrollView scrollView;
    private int currentAccount;

    public interface TermsOfServiceViewDelegate {
        void onAcceptTerms(int account);
        void onDeclineTerms(int account);
    }

    public TermsOfServiceView(final Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        final int top = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0;

        if (top > 0) {
            View view = new View(context);
            view.setBackgroundColor(0xff000000);
            addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, top));
        }

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.logo_middle);
        linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 28, 0, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setText(LocaleController.getString(R.string.PrivacyPolicyAndTerms));
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 20, 0, 0));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 15, 0, 15));

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setPadding(AndroidUtilities.dp(24f), top, AndroidUtilities.dp(24f), AndroidUtilities.dp(75f));
        scrollView.addView(linearLayout, new LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView declineTextView = new TextView(context);
        declineTextView.setText(LocaleController.getString(R.string.Decline).toUpperCase());
        declineTextView.setGravity(Gravity.CENTER);
        declineTextView.setTypeface(AndroidUtilities.bold());
        declineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        declineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        declineTextView.setBackground(Theme.getRoundRectSelectorDrawable(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)));
        declineTextView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        addView(declineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 16, 16));
        declineTextView.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
            builder.setTitle(LocaleController.getString(R.string.TermsOfService));
            builder.setPositiveButton(LocaleController.getString(R.string.DeclineDeactivate), (dialog, which) -> {
                AlertDialog.Builder builder12 = new AlertDialog.Builder(getContext());
                builder12.setMessage(LocaleController.getString(R.string.TosDeclineDeleteAccount));
                builder12.setTitle(LocaleController.getString(R.string.AppName));
                builder12.setPositiveButton(LocaleController.getString(R.string.Deactivate), (dialogInterface, i) -> {
                    final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.setCanCancel(false);

                    TL_account.deleteAccount req = new TL_account.deleteAccount();
                    req.reason = "Decline ToS update";
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (response instanceof TLRPC.TL_boolTrue) {
                            MessagesController.getInstance(currentAccount).performLogout(0);
                        } else if (error == null || error.code != -1000) {
                            String errorText = LocaleController.getString(R.string.ErrorOccurred);
                            if (error != null) {
                                errorText += "\n" + error.text;
                            }
                            AlertDialog.Builder builder1 = new AlertDialog.Builder(getContext());
                            builder1.setTitle(LocaleController.getString(R.string.AppName));
                            builder1.setMessage(errorText);
                            builder1.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            builder1.show();
                        }
                    }));
                    progressDialog.show();
                });
                builder12.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder12.show();
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Back), null);
            builder.setMessage(LocaleController.getString(R.string.TosUpdateDecline));
            builder.show();
        });

        TextView acceptTextView = new TextView(context);
        acceptTextView.setText(LocaleController.getString(R.string.Accept));
        acceptTextView.setGravity(Gravity.CENTER);
        acceptTextView.setTypeface(AndroidUtilities.bold());
        acceptTextView.setTextColor(0xffffffff);
        acceptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        acceptTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), 0xff50a8eb, 0xff439bde));
        acceptTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        addView(acceptTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 42, Gravity.RIGHT | Gravity.BOTTOM, 16, 0, 16, 16));
        acceptTextView.setOnClickListener(view -> {
            if (currentTos.min_age_confirm != 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                builder.setTitle(LocaleController.getString(R.string.TosAgeTitle));
                builder.setPositiveButton(LocaleController.getString(R.string.Agree), (dialog, which) -> accept());
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder.setMessage(LocaleController.formatString("TosAgeText", R.string.TosAgeText, LocaleController.formatPluralString("Years", currentTos.min_age_confirm)));
                builder.show();
            } else {
                accept();
            }
        });

        final View lineView = new View(context);
        lineView.setBackgroundColor(Theme.getColor(Theme.key_divider));
        final LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.bottomMargin = AndroidUtilities.dp(75f);
        params.gravity = Gravity.BOTTOM;
        addView(lineView, params);
    }

    private void accept() {
        delegate.onAcceptTerms(currentAccount);
        TLRPC.TL_help_acceptTermsOfService req = new TLRPC.TL_help_acceptTermsOfService();
        req.id = currentTos.id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    public void show(int account, TLRPC.TL_help_termsOfService tos) {
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(tos.text);
        MessageObject.addEntitiesToText(builder, tos.entities, false, false, false, false);
        addBulletsToText(builder, '-', AndroidUtilities.dp(10f), 0xff50a8eb, AndroidUtilities.dp(4f));
        textView.setText(builder);
        currentTos = tos;
        currentAccount = account;
    }

    public void setDelegate(TermsOfServiceViewDelegate termsOfServiceViewDelegate) {
        delegate = termsOfServiceViewDelegate;
    }

    private static void addBulletsToText(SpannableStringBuilder builder, char bulletChar, int gapWidth, int color, int radius) {
        for (int i = 0, until = builder.length() - 2; i < until; i++) {
            if (builder.charAt(i) == '\n' && builder.charAt(i + 1) == bulletChar && builder.charAt(i + 2) == ' ') {
                final BulletSpan span = new BulletSpan(gapWidth, color, radius);
                builder.replace(i + 1, i + 3, "\0\0");
                builder.setSpan(span, i + 1, i + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}

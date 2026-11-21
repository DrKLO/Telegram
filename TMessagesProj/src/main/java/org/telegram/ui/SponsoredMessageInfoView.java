package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

public class SponsoredMessageInfoView extends FrameLayout {

    LinearLayout linearLayout;

    public SponsoredMessageInfoView(Context context, Runnable close, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TextView textView = new TextView(context);
        textView.setText(LocaleController.getString(R.string.SponsoredMessageInfo));
        textView.setTypeface(AndroidUtilities.bold());
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);

        LinkSpanDrawable.LinksTextView description1 = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        description1.setText(AndroidUtilities.replaceLinks(LocaleController.getString("SponsoredMessageInfo2Description1"), resourcesProvider));
        ThemeBind.linkTextColor(description1, Theme.key_chat_messageLinkIn, resourcesProvider);
        ThemeBind.textColor(description1, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        description1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description1.setLineSpacing(AndroidUtilities.dp(2), 1f);
        description1.setOnLinkPressListener(link -> {
            if (close != null) close.run();
            link.onClick(this);
        });

        LinkSpanDrawable.LinksTextView description2 = new LinkSpanDrawable.LinksTextView(context);
        description2.setText(AndroidUtilities.replaceLinks(LocaleController.getString("SponsoredMessageInfo2Description2"), resourcesProvider));
        ThemeBind.textColor(description2, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        description2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description2.setLineSpacing(AndroidUtilities.dp(2), 1f);
        description2.setOnLinkPressListener(link -> {
            if (close != null) close.run();
            link.onClick(this);
        });

        LinkSpanDrawable.LinksTextView description3 = new LinkSpanDrawable.LinksTextView(context);
        description3.setText(AndroidUtilities.replaceLinks(LocaleController.getString("SponsoredMessageInfo2Description3"), resourcesProvider));
        ThemeBind.textColor(description3, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        description3.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description3.setLineSpacing(AndroidUtilities.dp(2), 1f);
        description3.setOnLinkPressListener(link -> {
            if (close != null) close.run();
            link.onClick(this);
        });

        Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        buttonPaint.setStrokeWidth(AndroidUtilities.dp(1));
        TextView button = new TextView(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                // same key as the (bound) text color: follows theme changes
                buttonPaint.setColor(getCurrentTextColor());
                AndroidUtilities.rectTmp.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), getMeasuredWidth() - AndroidUtilities.dp(1), getMeasuredHeight() - AndroidUtilities.dp(1));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), buttonPaint);
            }
        };
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (close != null) close.run();
                Browser.openUrl(context, LocaleController.getString(R.string.SponsoredMessageAlertLearnMoreUrl));
            }
        });

        button.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        button.setText(LocaleController.getString(R.string.SponsoredMessageAlertLearnMoreUrl));
        ThemeBind.textColor(button, Theme.key_featuredStickers_addButton, resourcesProvider);
        ThemeBind.selectorBackground(button, Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 4), ThemeBind.of(Theme.key_dialogBackground), ThemeBind.adaptiveRipple(Theme.key_dialogBackground), resourcesProvider);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setGravity(Gravity.CENTER_VERTICAL);


        LinkSpanDrawable.LinksTextView description4 = new LinkSpanDrawable.LinksTextView(context);
        description4.setText(AndroidUtilities.replaceLinks(LocaleController.getString("SponsoredMessageInfo2Description4"), resourcesProvider));
        description4.setLineSpacing(AndroidUtilities.dp(2), 1f);
        ThemeBind.textColor(description4, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        description4.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

        textView.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        linearLayout.addView(textView);

        description1.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        linearLayout.addView(description1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 18, 0, 0));

        description2.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        linearLayout.addView(description2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 24, 0, 0));

        description3.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        linearLayout.addView(description3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 24, 0, 0));

        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 34, Gravity.CENTER_HORIZONTAL, 22, 14, 22, 0));

        description4.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        linearLayout.addView(description4, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0, 0));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(linearLayout);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 12, 0, 22));

    }
}

package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class BusinessLinksEmptyView extends LinearLayout {

    private ImageView imageView;
    private TextView descriptionView;
    private TextView linkView;

    public BusinessLinksEmptyView(Context context, BaseFragment fragment, TLRPC.TL_businessChatLink businessLink, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);

        Theme.getColor(Theme.key_chat_serviceText, resourcesProvider);
        Theme.getColor(Theme.key_chat_serviceText);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setImageResource(R.drawable.filled_chatlink_large);
        addView(imageView, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 17, 17, 9));

        descriptionView = new TextView(context);
        descriptionView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, resourcesProvider));
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionView.setMaxWidth(dp(208));
        descriptionView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.BusinessLinksIntro)));
        addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 0, 17, 9));

        linkView = new TextView(context);
        linkView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        linkView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, resourcesProvider));
        linkView.setTypeface(linkView.getTypeface(), Typeface.BOLD);
        linkView.setGravity(Gravity.CENTER_HORIZONTAL);
        linkView.setMaxWidth(dp(208));
        linkView.setText(BusinessLinksController.stripHttps(businessLink.link));
        linkView.setBackground(Theme.createRadSelectorDrawable(0x1e000000, 0x1e000000, 5, 5));
        linkView.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
        linkView.setOnClickListener(v -> {
            AndroidUtilities.addToClipboard(businessLink.link);
            BulletinFactory.of(fragment).createCopyLinkBulletin().show();
        });
        addView(linkView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 0, 17, 17));
    }
}

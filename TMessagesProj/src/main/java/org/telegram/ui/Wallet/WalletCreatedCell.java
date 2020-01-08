/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

@SuppressWarnings("FieldCanBeLocal")
public class WalletCreatedCell extends FrameLayout {

    private RLottieImageView imageView;
    private TextView createdTextView;
    private TextView addressTextView;
    private TextView addressValueTextView;

    public WalletCreatedCell(Context context) {
        super(context);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.wallet_egg, 112, 112);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.playAnimation();
        container.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        createdTextView = new TextView(context);
        createdTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        createdTextView.setText(LocaleController.getString("WalletCreated", R.string.WalletCreated));
        createdTextView.setTextColor(Theme.getColor(Theme.key_wallet_blackText));
        container.addView(createdTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 13, 0, 0));

        addressTextView = new TextView(context);
        addressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        addressTextView.setText(LocaleController.getString("WalletYourAddress", R.string.WalletYourAddress));
        addressTextView.setTextColor(Theme.getColor(Theme.key_wallet_grayText));
        container.addView(addressTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 22, 0, 0));
        addressTextView.setOnLongClickListener(v -> {
            AndroidUtilities.addToClipboard("ton://transfer/" + addressValueTextView.getText().toString().replace("\n", ""));
            Toast.makeText(v.getContext(), LocaleController.getString("WalletTransactionAddressCopied", R.string.WalletTransactionAddressCopied), Toast.LENGTH_SHORT).show();
            return true;
        });

        addressValueTextView = new TextView(context);
        addressValueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        addressValueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
        addressValueTextView.setText("t\nt");
        addressValueTextView.setAlpha(0.0f);
        addressValueTextView.setTextColor(Theme.getColor(Theme.key_wallet_blackText));
        container.addView(addressValueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 13, 0, 0));
    }

    public void setAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }
        StringBuilder stringBuilder = new StringBuilder(address);
        stringBuilder.insert(stringBuilder.length() / 2, '\n');
        addressValueTextView.setText(stringBuilder);
        addressValueTextView.setAlpha(1.0f);
    }
}

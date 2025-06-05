package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;

public class DownloadsInfoBottomSheet extends BottomSheet {

    public static void show(Activity parentActivity, BaseFragment parentFragment) {
        if (parentFragment == null || parentActivity == null) {
            return;
        }
        new DownloadsInfoBottomSheet(parentActivity, parentFragment, false).show();
    }

    public DownloadsInfoBottomSheet(Context context, BaseFragment parentFragment, boolean needFocus) {
        super(context, needFocus);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundWhite));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(linearLayout);

        ImageView closeView = new ImageView(context);
        closeView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        closeView.setColorFilter(getThemedColor(Theme.key_sheet_other));
        closeView.setImageResource(R.drawable.ic_layer_close);
        closeView.setOnClickListener((view) -> dismiss());
        int closeViewPadding = AndroidUtilities.dp(8);
        closeView.setPadding(closeViewPadding, closeViewPadding, closeViewPadding, closeViewPadding);
        frameLayout.addView(closeView, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.END, 6, 8, 8, 0));

        StickerImageView imageView = new StickerImageView(context, currentAccount);
        imageView.setStickerNum(9);
        imageView.getImageReceiver().setAutoRepeat(1);
        linearLayout.addView(imageView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL, 0, 26, 0, 0));

        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setText(LocaleController.getString(R.string.DownloadedFiles));
        linearLayout.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 20, 21, 0));

        TextView description = new TextView(context);
        description.setGravity(Gravity.CENTER_HORIZONTAL);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
        description.setText(LocaleController.formatString("DownloadedFilesMessage", R.string.DownloadedFilesMessage));
        linearLayout.addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 28, 7, 28, 0));

        TextView storageBtn = new TextView(context);
        storageBtn.setGravity(Gravity.CENTER);
        storageBtn.setEllipsize(TextUtils.TruncateAt.END);
        storageBtn.setSingleLine(true);
        storageBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        storageBtn.setTypeface(AndroidUtilities.bold());
        storageBtn.setText(LocaleController.getString(R.string.ManageDeviceStorage));
        storageBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        storageBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), 120)));
        linearLayout.addView(storageBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 14, 28, 14, 6));

        TextView clearBtn = new TextView(context);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setEllipsize(TextUtils.TruncateAt.END);
        clearBtn.setSingleLine(true);
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        clearBtn.setTypeface(AndroidUtilities.bold());
        clearBtn.setText(LocaleController.getString(R.string.ClearDownloadsList));
        clearBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        clearBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_featuredStickers_addButton), 120)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clearBtn.setLetterSpacing(0.025f);
        }
        linearLayout.addView(clearBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 14, 0, 14, 6));

        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.addView(frameLayout);
        setCustomView(scrollView);

        storageBtn.setOnClickListener(view -> {
            dismiss();
            parentFragment.presentFragment(new CacheControlActivity());
        });
        clearBtn.setOnClickListener(view -> {
            dismiss();
            DownloadController.getInstance(currentAccount).clearRecentDownloadedFiles();
        });
    }
}

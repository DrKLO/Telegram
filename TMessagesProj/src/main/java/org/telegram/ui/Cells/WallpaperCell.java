/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class WallpaperCell extends FrameLayout {

    private BackupImageView imageView;
    private View selectionView;
    private ImageView imageView2;

    public WallpaperCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        addView(imageView, LayoutHelper.createFrame(100, 100, Gravity.LEFT | Gravity.BOTTOM));

        imageView2 = new ImageView(context);
        imageView2.setImageResource(R.drawable.ic_gallery_background);
        imageView2.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView2, LayoutHelper.createFrame(100, 100, Gravity.LEFT | Gravity.BOTTOM));

        selectionView = new View(context);
        selectionView.setBackgroundResource(R.drawable.wall_selection);
        addView(selectionView, LayoutHelper.createFrame(100, 102));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(102), MeasureSpec.EXACTLY));
    }

    public void setWallpaper(TLRPC.WallPaper wallpaper, int selectedBackground, Drawable themedWallpaper, boolean themed) {
        if (wallpaper == null) {
            imageView.setVisibility(INVISIBLE);
            imageView2.setVisibility(VISIBLE);
            if (themed) {
                selectionView.setVisibility(selectedBackground == -2 ? View.VISIBLE : INVISIBLE);
                imageView2.setImageDrawable(themedWallpaper);
                imageView2.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                selectionView.setVisibility(selectedBackground == -1 ? View.VISIBLE : INVISIBLE);
                imageView2.setBackgroundColor(selectedBackground == -1 || selectedBackground == 1000001 ? 0x5a475866 : 0x5a000000);
                imageView2.setScaleType(ImageView.ScaleType.CENTER);
            }
        } else {
            imageView.setVisibility(VISIBLE);
            imageView2.setVisibility(INVISIBLE);
            selectionView.setVisibility(selectedBackground == wallpaper.id ? View.VISIBLE : INVISIBLE);

            if (wallpaper instanceof TLRPC.TL_wallPaperSolid) {
                imageView.setImageBitmap(null);
                imageView.setBackgroundColor(0xff000000 | wallpaper.bg_color);
            } else {
                int side = AndroidUtilities.dp(100);
                TLRPC.PhotoSize size = null;
                for (int a = 0; a < wallpaper.sizes.size(); a++) {
                    TLRPC.PhotoSize obj = wallpaper.sizes.get(a);
                    if (obj == null) {
                        continue;
                    }
                    int currentSide = obj.w >= obj.h ? obj.w : obj.h;
                    if (size == null || side > 100 && size.location != null && size.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || currentSide <= side) {
                        size = obj;
                    }
                }
                if (size != null && size.location != null) {
                    imageView.setImage(size.location, "100_100", (Drawable) null);
                }
                imageView.setBackgroundColor(0x5a475866);
            }
        }
    }
}

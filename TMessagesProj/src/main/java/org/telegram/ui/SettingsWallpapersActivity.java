/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.objects.PhotoObject;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.HorizontalListView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class SettingsWallpapersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private HorizontalListView listView;
    private ListAdapter listAdapter;
    private ImageView backgroundImage;
    private ProgressBar progressBar;
    private int selectedBackground;
    private int selectedColor;
    private ArrayList<TLRPC.WallPaper> wallPapers = new ArrayList<TLRPC.WallPaper>();
    private HashMap<Integer, TLRPC.WallPaper> wallpappersByIds = new HashMap<Integer, TLRPC.WallPaper>();
    private View doneButton;
    private String loadingFile = null;
    private File loadingFileObject = null;
    private TLRPC.PhotoSize loadingSize = null;
    private String currentPicturePath;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, MessagesStorage.wallpapersDidLoaded);

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        selectedBackground = preferences.getInt("selectedBackground", 1000001);
        selectedColor = preferences.getInt("selectedColor", 0);
        MessagesStorage.getInstance().getWallpapers();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, MessagesStorage.wallpapersDidLoaded);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.settings_wallpapers_layout, container, false);
            listAdapter = new ListAdapter(parentActivity);

            progressBar = (ProgressBar)fragmentView.findViewById(R.id.action_progress);
            backgroundImage = (ImageView)fragmentView.findViewById(R.id.background_image);
            listView = (HorizontalListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == 0) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

                        CharSequence[] items = new CharSequence[] {LocaleController.getString("FromCamera", R.string.FromCamera), LocaleController.getString("FromGalley", R.string.FromGalley), LocaleController.getString("Cancel", R.string.Cancel)};

                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                    File image = Utilities.generatePicturePath();
                                    if (image != null) {
                                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                                        currentPicturePath = image.getAbsolutePath();
                                    }
                                    startActivityForResult(takePictureIntent, 0);
                                } else if (i == 1) {
                                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                                    photoPickerIntent.setType("image/*");
                                    startActivityForResult(photoPickerIntent, 1);
                                }
                            }
                        });
                        builder.show().setCanceledOnTouchOutside(true);
                    } else {
                        TLRPC.WallPaper wallPaper = wallPapers.get(i - 1);
                        selectedBackground = wallPaper.id;
                        listAdapter.notifyDataSetChanged();
                        processSelectedBackground();
                    }
                }
            });

            TextView textView = (TextView)fragmentView.findViewById(R.id.done_button_text);
            textView.setText(LocaleController.getString("Set", R.string.Set));

            Button cancelButton = (Button)fragmentView.findViewById(R.id.cancel_button);
            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel));
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finishFragment();
                }
            });

            doneButton = fragmentView.findViewById(R.id.done_button);
            doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean done;
                    TLRPC.WallPaper wallPaper = wallpappersByIds.get(selectedBackground);
                    if (wallPaper != null && wallPaper.id != 1000001 && wallPaper instanceof TLRPC.TL_wallPaper) {
                        TLRPC.PhotoSize size = PhotoObject.getClosestPhotoSizeWithSize(wallPaper.sizes, Utilities.dp(320), Utilities.dp(480));
                        String fileName = size.location.volume_id + "_" + size.location.local_id + ".jpg";
                        File f = new File(Utilities.getCacheDir(), fileName);
                        File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                        try {
                            done = Utilities.copyFile(f, toFile);
                        } catch (Exception e) {
                            done = false;
                            FileLog.e("tmessages", e);
                        }
                    } else {
                        done = true;
                    }

                    if (done) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("selectedBackground", selectedBackground);
                        editor.putInt("selectedColor", selectedColor);
                        editor.commit();
                        ApplicationLoader.cachedWallpaper = null;
                    }
                    finishFragment();
                }
            });

            processSelectedBackground();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {
                Utilities.addMediaToGallery(currentPicturePath);
                try {
                    Bitmap bitmap = FileLoader.loadBitmap(currentPicturePath, null, Utilities.dp(320), Utilities.dp(480));
                    File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                    FileOutputStream stream = new FileOutputStream(toFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    selectedBackground = -1;
                    selectedColor = 0;
                    backgroundImage.setImageBitmap(bitmap);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                currentPicturePath = null;
            } else if (requestCode == 1) {
                Uri imageUri = data.getData();
                Cursor cursor = parentActivity.getContentResolver().query(imageUri, new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                if (cursor == null) {
                    return;
                }

                try {
                    String imageFilePath = null;
                    if (cursor.moveToFirst()) {
                        imageFilePath = cursor.getString(0);
                    }
                    cursor.close();

                    Bitmap bitmap = FileLoader.loadBitmap(imageFilePath, null, Utilities.dp(320), Utilities.dp(480));
                    File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                    FileOutputStream stream = new FileOutputStream(toFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    selectedBackground = -1;
                    selectedColor = 0;
                    backgroundImage.setImageBitmap(bitmap);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }

    private void processSelectedBackground() {
        TLRPC.WallPaper wallPaper = wallpappersByIds.get(selectedBackground);
        if (selectedBackground != -1 && selectedBackground != 1000001 && wallPaper != null && wallPaper instanceof TLRPC.TL_wallPaper) {
            TLRPC.PhotoSize size = PhotoObject.getClosestPhotoSizeWithSize(wallPaper.sizes, Utilities.dp(320), Utilities.dp(480));
            String fileName = size.location.volume_id + "_" + size.location.local_id + ".jpg";
            File f = new File(Utilities.getCacheDir(), fileName);
            if (!f.exists()) {
                progressBar.setProgress(0);
                loadingFile = fileName;
                loadingFileObject = f;
                doneButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                loadingSize = size;
                selectedColor = 0;
                FileLoader.getInstance().loadFile(null, size, null, null);
                backgroundImage.setBackgroundColor(0);
            } else {
                if (loadingFile != null) {
                    FileLoader.getInstance().cancelLoadFile(null, loadingSize, null, null);
                }
                loadingFileObject = null;
                loadingFile = null;
                loadingSize = null;
                backgroundImage.setImageURI(Uri.fromFile(f));
                backgroundImage.setBackgroundColor(0);
                selectedColor = 0;
                doneButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
            }
        } else {
            if (loadingFile != null) {
                FileLoader.getInstance().cancelLoadFile(null, loadingSize, null, null);
            }
            if (selectedBackground == 1000001) {
                backgroundImage.setImageResource(R.drawable.background_hd);
                backgroundImage.setBackgroundColor(0);
                selectedColor = 0;
            } else if (selectedBackground == -1) {
                File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                if (toFile.exists()) {
                    backgroundImage.setImageURI(Uri.fromFile(toFile));
                } else {
                    selectedBackground = 1000001;
                    processSelectedBackground();
                }
            } else {
                if (wallPaper == null) {
                    return;
                }
                if (wallPaper instanceof TLRPC.TL_wallPaperSolid) {
                    backgroundImage.setImageBitmap(null);
                    selectedColor = 0xff000000 | wallPaper.bg_color;
                    backgroundImage.setBackgroundColor(selectedColor);
                }
            }
            loadingFileObject = null;
            loadingFile = null;
            loadingSize = null;
            doneButton.setEnabled(true);
            progressBar.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == FileLoader.FileDidFailedLoad) {
            String location = (String)args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                loadingFileObject = null;
                loadingFile = null;
                loadingSize = null;
                progressBar.setVisibility(View.GONE);
                doneButton.setEnabled(false);
            }
        } else if (id == FileLoader.FileDidLoaded) {
            String location = (String)args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                backgroundImage.setImageURI(Uri.fromFile(loadingFileObject));
                progressBar.setVisibility(View.GONE);
                backgroundImage.setBackgroundColor(0);
                doneButton.setEnabled(true);
                loadingFileObject = null;
                loadingFile = null;
                loadingSize = null;
            }
        } else if (id == FileLoader.FileLoadProgressChanged) {
            String location = (String)args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                Float progress = (Float)args[1];
                progressBar.setProgress((int)(progress * 100));
            }
        } else if (id == MessagesStorage.wallpapersDidLoaded) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    wallPapers = (ArrayList<TLRPC.WallPaper>)args[0];
                    wallpappersByIds.clear();
                    for (TLRPC.WallPaper wallPaper : wallPapers) {
                        wallpappersByIds.put(wallPaper.id, wallPaper);
                    }
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (!wallPapers.isEmpty() && backgroundImage != null) {
                        processSelectedBackground();
                    }
                    loadWallpapers();
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void loadWallpapers() {
        TLRPC.TL_account_getWallPapers req = new TLRPC.TL_account_getWallPapers();
        long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        wallPapers.clear();
                        TLRPC.Vector res = (TLRPC.Vector)response;
                        wallpappersByIds.clear();
                        for (Object obj : res.objects) {
                            wallPapers.add((TLRPC.WallPaper)obj);
                            wallpappersByIds.put(((TLRPC.WallPaper)obj).id, (TLRPC.WallPaper)obj);
                        }
                        listAdapter.notifyDataSetChanged();
                        if (backgroundImage != null) {
                            processSelectedBackground();
                        }
                        MessagesStorage.getInstance().putWallpapers(wallPapers);
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
    }

    private void fixLayout() {
        final View view = getView();
        if (view != null) {
            ViewTreeObserver obs = view.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    view.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (listView != null) {
                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                listView.scrollTo(0);
                            }
                        });
                    }
                    return false;
                }
            });
        }
    }

    @Override
    public boolean canApplyUpdateStatus() {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getActivity() == null) {
            return;
        }
        if (!firstStart && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }

        ((LaunchActivity) parentActivity).hideActionBar();

        processSelectedBackground();

        fixLayout();
    }

    private class ListAdapter extends BaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            return 1 + wallPapers.size();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_wallpapers_my_row, viewGroup, false);
                }
                View parentView = view.findViewById(R.id.parent);
                ImageView imageView = (ImageView)view.findViewById(R.id.image);
                View selection = view.findViewById(R.id.selection);
                if (i == 0) {
                    if (selectedBackground == -1 || selectedColor != 0 || selectedBackground == 1000001) {
                        imageView.setBackgroundColor(0x5A475866);
                    } else {
                        imageView.setBackgroundColor(0x5A000000);
                    }
                    imageView.setImageResource(R.drawable.ic_gallery_background);
                    if (selectedBackground == -1) {
                        selection.setVisibility(View.VISIBLE);
                    } else {
                        selection.setVisibility(View.INVISIBLE);
                    }
                } else {
                    imageView.setImageBitmap(null);
                    TLRPC.WallPaper wallPaper = wallPapers.get(i - 1);
                    imageView.setBackgroundColor(0xff000000 | wallPaper.bg_color);
                    if (wallPaper.id == selectedBackground) {
                        selection.setVisibility(View.VISIBLE);
                    } else {
                        selection.setVisibility(View.INVISIBLE);
                    }
                }

            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_wallpapers_other_row, viewGroup, false);
                }
                BackupImageView image = (BackupImageView)view.findViewById(R.id.image);
                View selection = view.findViewById(R.id.selection);
                TLRPC.WallPaper wallPaper = wallPapers.get(i - 1);
                TLRPC.PhotoSize size = PhotoObject.getClosestPhotoSizeWithSize(wallPaper.sizes, Utilities.dp(100), Utilities.dp(100));
                image.setImage(size.location, "100_100", 0);
                if (wallPaper.id == selectedBackground) {
                    selection.setVisibility(View.VISIBLE);
                } else {
                    selection.setVisibility(View.INVISIBLE);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == 0) {
                return 0;
            }
            TLRPC.WallPaper wallPaper = wallPapers.get(i - 1);
            if (wallPaper instanceof TLRPC.TL_wallPaperSolid) {
                return 0;
            }
            return 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}

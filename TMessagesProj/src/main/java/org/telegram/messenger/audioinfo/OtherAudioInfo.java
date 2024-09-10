package org.telegram.messenger.audioinfo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import com.google.android.exoplayer2.MetadataRetriever;

import org.telegram.messenger.FileLog;

import java.io.File;

public class OtherAudioInfo extends AudioInfo {

    private final MediaMetadataRetriever r;
    public boolean failed;

    public OtherAudioInfo(File file) {
        r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getAbsolutePath());

            brand = "OTHER";
            version = "0";

            duration = getLong(MediaMetadataRetriever.METADATA_KEY_DURATION);
            title = getString(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = getString(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            albumArtist = getString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            album = getString(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            year = getShort(MediaMetadataRetriever.METADATA_KEY_YEAR);
            genre = getString(MediaMetadataRetriever.METADATA_KEY_GENRE);
            track = getShort(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            tracks = getShort(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
            disc = getShort(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
            composer = getString(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
            byte[] coverBytes = r.getEmbeddedPicture();
            if (coverBytes != null) {
                cover = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length);
            }
            if (cover != null) {
                float scale = Math.max(cover.getWidth(), cover.getHeight()) / 120.0f;
                if (scale > 0) {
                    smallCover = Bitmap.createScaledBitmap(cover, (int) (cover.getWidth() / scale), (int) (cover.getHeight() / scale), true);
                } else {
                    smallCover = cover;
                }
            }

        } catch (Exception e) {
            failed = true;
            FileLog.e(e);
        }

        try {
            if (r != null) {
                r.close();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private String getString(int key) {
        try {
            return r.extractMetadata(key);
        } catch (Exception ignore) {}
        return null;
    }

    private short getShort(int key) {
        try {
            return Short.parseShort(r.extractMetadata(key));
        } catch (Exception ignore) {}
        return 0;
    }

    private long getLong(int key) {
        try {
            return Long.parseLong(r.extractMetadata(key));
        } catch (Exception ignore) {}
        return 0;
    }

}

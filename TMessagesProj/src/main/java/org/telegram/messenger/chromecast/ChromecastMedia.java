package org.telegram.messenger.chromecast;

import android.net.Uri;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;

public class ChromecastMedia {
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_PNG = "image/png";
    public static final String VIDEO_MP4 = "video/mp4";
    public static final String APPLICATION_X_MPEG_URL = "application/x-mpegURL";

    public final String mimeType;
    public final MediaMetadata mediaMetadata;

    public final Uri internalUri;
    public final String externalPath;

    public final int width;
    public final int height;

    private ChromecastMedia(ChromecastMedia.Builder b) {
        this.mimeType = b.mimeType;
        this.mediaMetadata = b.buildMetadata();
        this.internalUri = b.internalUri;
        this.externalPath = b.externalPath;
        this.width = b.width;
        this.height = b.height;
    }

    public String getExternalUri (String host) {
        return ChromecastFileServer.getUrlToSource(host, externalPath);
    }

    public MediaInfo buildMediaInfo (String host, String options) {
        return new MediaInfo.Builder(getExternalUri(host) + options)
            .setContentType(mimeType)
            .setMetadata(mediaMetadata)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .build();
    }

    /* */

    public static class Builder {
        private final String mimeType;
        private final Uri internalUri;
        private final String externalPath;
        private MediaMetadata baseMetadata;

        private int width;
        private int height;
        private String title;
        private String subtitle;

        private Builder (String mime, Uri internalUri, String externalPath) {
            this.mimeType = mime;
            this.internalUri = internalUri;
            this.externalPath = externalPath;
        }

        public static Builder fromUri (Uri internalUri, String externalPath, String mimeType) {
            return new Builder(mimeType, internalUri, externalPath);
        }

        public Builder setTitle (String title) {
            this.title = title;
            return this;
        }

        public Builder setSubtitle (String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder setSize (int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder setMetadata(MediaMetadata metadata) {
            this.baseMetadata = metadata;
            return this;
        }

        public ChromecastMedia build () {
            return new ChromecastMedia(this);
        }

        private MediaMetadata buildMetadata () {
            final int mediaType;
            switch (mimeType) {
                case IMAGE_JPEG:
                case IMAGE_PNG:
                    mediaType = MediaMetadata.MEDIA_TYPE_PHOTO;
                    break;
                case APPLICATION_X_MPEG_URL:
                case VIDEO_MP4:
                    mediaType = MediaMetadata.MEDIA_TYPE_MOVIE;
                    break;
                default:
                    if (mimeType.startsWith("audio/")) {
                        mediaType = MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
                        break;
                    }
                    return null;
            }


            final MediaMetadata metadata = baseMetadata == null ? new MediaMetadata(mediaType) : baseMetadata;
            final StringBuilder titleBuilder = new StringBuilder();
            final StringBuilder subtitleBuilder = new StringBuilder();

            if (title != null) {
                titleBuilder.append(title);
            }

            if (subtitle != null) {
                subtitleBuilder.append(subtitle);
            }

            if (width != 0 && height != 0) {
                metadata.putInt(MediaMetadata.KEY_WIDTH, width);
                metadata.putInt(MediaMetadata.KEY_HEIGHT, height);

                if (subtitleBuilder.length() > 0) {
                    subtitleBuilder.append(' ');
                }
                subtitleBuilder.append("(").append(width).append("x").append(height).append(")");
            }

            if (titleBuilder.length() > 0) {
                metadata.putString(MediaMetadata.KEY_TITLE, titleBuilder.toString());
            } else {
                metadata.putString(MediaMetadata.KEY_TITLE, "No Title");
            }

            if (subtitleBuilder.length() > 0) {
                metadata.putString(MediaMetadata.KEY_SUBTITLE, subtitleBuilder.toString());
            }

            return metadata;
        }
    }
}

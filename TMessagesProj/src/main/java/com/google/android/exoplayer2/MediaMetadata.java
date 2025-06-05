/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

/**
 * Metadata of a {@link MediaItem}, playlist, or a combination of multiple sources of {@link
 * Metadata}.
 */
public final class MediaMetadata implements Bundleable {

  /** A builder for {@link MediaMetadata} instances. */
  public static final class Builder {

    @Nullable private CharSequence title;
    @Nullable private CharSequence artist;
    @Nullable private CharSequence albumTitle;
    @Nullable private CharSequence albumArtist;
    @Nullable private CharSequence displayTitle;
    @Nullable private CharSequence subtitle;
    @Nullable private CharSequence description;
    @Nullable private Rating userRating;
    @Nullable private Rating overallRating;
    @Nullable private byte[] artworkData;
    @Nullable private @PictureType Integer artworkDataType;
    @Nullable private Uri artworkUri;
    @Nullable private Integer trackNumber;
    @Nullable private Integer totalTrackCount;
    @Nullable private @FolderType Integer folderType;
    @Nullable private Boolean isBrowsable;
    @Nullable private Boolean isPlayable;
    @Nullable private Integer recordingYear;
    @Nullable private Integer recordingMonth;
    @Nullable private Integer recordingDay;
    @Nullable private Integer releaseYear;
    @Nullable private Integer releaseMonth;
    @Nullable private Integer releaseDay;
    @Nullable private CharSequence writer;
    @Nullable private CharSequence composer;
    @Nullable private CharSequence conductor;
    @Nullable private Integer discNumber;
    @Nullable private Integer totalDiscCount;
    @Nullable private CharSequence genre;
    @Nullable private CharSequence compilation;
    @Nullable private CharSequence station;
    @Nullable private @MediaType Integer mediaType;
    @Nullable private Bundle extras;

    public Builder() {}

    private Builder(MediaMetadata mediaMetadata) {
      this.title = mediaMetadata.title;
      this.artist = mediaMetadata.artist;
      this.albumTitle = mediaMetadata.albumTitle;
      this.albumArtist = mediaMetadata.albumArtist;
      this.displayTitle = mediaMetadata.displayTitle;
      this.subtitle = mediaMetadata.subtitle;
      this.description = mediaMetadata.description;
      this.userRating = mediaMetadata.userRating;
      this.overallRating = mediaMetadata.overallRating;
      this.artworkData = mediaMetadata.artworkData;
      this.artworkDataType = mediaMetadata.artworkDataType;
      this.artworkUri = mediaMetadata.artworkUri;
      this.trackNumber = mediaMetadata.trackNumber;
      this.totalTrackCount = mediaMetadata.totalTrackCount;
      this.folderType = mediaMetadata.folderType;
      this.isBrowsable = mediaMetadata.isBrowsable;
      this.isPlayable = mediaMetadata.isPlayable;
      this.recordingYear = mediaMetadata.recordingYear;
      this.recordingMonth = mediaMetadata.recordingMonth;
      this.recordingDay = mediaMetadata.recordingDay;
      this.releaseYear = mediaMetadata.releaseYear;
      this.releaseMonth = mediaMetadata.releaseMonth;
      this.releaseDay = mediaMetadata.releaseDay;
      this.writer = mediaMetadata.writer;
      this.composer = mediaMetadata.composer;
      this.conductor = mediaMetadata.conductor;
      this.discNumber = mediaMetadata.discNumber;
      this.totalDiscCount = mediaMetadata.totalDiscCount;
      this.genre = mediaMetadata.genre;
      this.compilation = mediaMetadata.compilation;
      this.station = mediaMetadata.station;
      this.mediaType = mediaMetadata.mediaType;
      this.extras = mediaMetadata.extras;
    }

    /** Sets the title. */
    @CanIgnoreReturnValue
    public Builder setTitle(@Nullable CharSequence title) {
      this.title = title;
      return this;
    }

    /** Sets the artist. */
    @CanIgnoreReturnValue
    public Builder setArtist(@Nullable CharSequence artist) {
      this.artist = artist;
      return this;
    }

    /** Sets the album title. */
    @CanIgnoreReturnValue
    public Builder setAlbumTitle(@Nullable CharSequence albumTitle) {
      this.albumTitle = albumTitle;
      return this;
    }

    /** Sets the album artist. */
    @CanIgnoreReturnValue
    public Builder setAlbumArtist(@Nullable CharSequence albumArtist) {
      this.albumArtist = albumArtist;
      return this;
    }

    /** Sets the display title. */
    @CanIgnoreReturnValue
    public Builder setDisplayTitle(@Nullable CharSequence displayTitle) {
      this.displayTitle = displayTitle;
      return this;
    }

    /**
     * Sets the subtitle.
     *
     * <p>This is the secondary title of the media, unrelated to closed captions.
     */
    @CanIgnoreReturnValue
    public Builder setSubtitle(@Nullable CharSequence subtitle) {
      this.subtitle = subtitle;
      return this;
    }

    /** Sets the description. */
    @CanIgnoreReturnValue
    public Builder setDescription(@Nullable CharSequence description) {
      this.description = description;
      return this;
    }

    /** Sets the user {@link Rating}. */
    @CanIgnoreReturnValue
    public Builder setUserRating(@Nullable Rating userRating) {
      this.userRating = userRating;
      return this;
    }

    /** Sets the overall {@link Rating}. */
    @CanIgnoreReturnValue
    public Builder setOverallRating(@Nullable Rating overallRating) {
      this.overallRating = overallRating;
      return this;
    }

    /**
     * @deprecated Use {@link #setArtworkData(byte[] data, Integer pictureType)} or {@link
     *     #maybeSetArtworkData(byte[] data, int pictureType)}, providing a {@link PictureType}.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setArtworkData(@Nullable byte[] artworkData) {
      return setArtworkData(artworkData, /* artworkDataType= */ null);
    }

    /**
     * Sets the artwork data as a compressed byte array with an associated {@link PictureType
     * artworkDataType}.
     */
    @CanIgnoreReturnValue
    public Builder setArtworkData(
        @Nullable byte[] artworkData, @Nullable @PictureType Integer artworkDataType) {
      this.artworkData = artworkData == null ? null : artworkData.clone();
      this.artworkDataType = artworkDataType;
      return this;
    }

    /**
     * Sets the artwork data as a compressed byte array in the event that the associated {@link
     * PictureType} is {@link #PICTURE_TYPE_FRONT_COVER}, the existing {@link PictureType} is not
     * {@link #PICTURE_TYPE_FRONT_COVER}, or the current artworkData is not set.
     *
     * <p>Use {@link #setArtworkData(byte[], Integer)} to set the artwork data without checking the
     * {@link PictureType}.
     */
    @CanIgnoreReturnValue
    public Builder maybeSetArtworkData(byte[] artworkData, @PictureType int artworkDataType) {
      if (this.artworkData == null
          || Util.areEqual(artworkDataType, PICTURE_TYPE_FRONT_COVER)
          || !Util.areEqual(this.artworkDataType, PICTURE_TYPE_FRONT_COVER)) {
        this.artworkData = artworkData.clone();
        this.artworkDataType = artworkDataType;
      }
      return this;
    }

    /** Sets the artwork {@link Uri}. */
    @CanIgnoreReturnValue
    public Builder setArtworkUri(@Nullable Uri artworkUri) {
      this.artworkUri = artworkUri;
      return this;
    }

    /** Sets the track number. */
    @CanIgnoreReturnValue
    public Builder setTrackNumber(@Nullable Integer trackNumber) {
      this.trackNumber = trackNumber;
      return this;
    }

    /** Sets the total number of tracks. */
    @CanIgnoreReturnValue
    public Builder setTotalTrackCount(@Nullable Integer totalTrackCount) {
      this.totalTrackCount = totalTrackCount;
      return this;
    }

    /**
     * Sets the {@link FolderType}.
     *
     * <p>This method will be deprecated. Use {@link #setIsBrowsable} to indicate if an item is a
     * browsable folder and use {@link #setMediaType} to indicate the type of the folder.
     */
    @CanIgnoreReturnValue
    public Builder setFolderType(@Nullable @FolderType Integer folderType) {
      this.folderType = folderType;
      return this;
    }

    /** Sets whether the media is a browsable folder. */
    @CanIgnoreReturnValue
    public Builder setIsBrowsable(@Nullable Boolean isBrowsable) {
      this.isBrowsable = isBrowsable;
      return this;
    }

    /** Sets whether the media is playable. */
    @CanIgnoreReturnValue
    public Builder setIsPlayable(@Nullable Boolean isPlayable) {
      this.isPlayable = isPlayable;
      return this;
    }

    /**
     * @deprecated Use {@link #setRecordingYear(Integer)} instead.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setYear(@Nullable Integer year) {
      return setRecordingYear(year);
    }

    /** Sets the year of the recording date. */
    @CanIgnoreReturnValue
    public Builder setRecordingYear(@Nullable Integer recordingYear) {
      this.recordingYear = recordingYear;
      return this;
    }

    /**
     * Sets the month of the recording date.
     *
     * <p>Value should be between 1 and 12.
     */
    @CanIgnoreReturnValue
    public Builder setRecordingMonth(
        @Nullable @IntRange(from = 1, to = 12) Integer recordingMonth) {
      this.recordingMonth = recordingMonth;
      return this;
    }

    /**
     * Sets the day of the recording date.
     *
     * <p>Value should be between 1 and 31.
     */
    @CanIgnoreReturnValue
    public Builder setRecordingDay(@Nullable @IntRange(from = 1, to = 31) Integer recordingDay) {
      this.recordingDay = recordingDay;
      return this;
    }

    /** Sets the year of the release date. */
    @CanIgnoreReturnValue
    public Builder setReleaseYear(@Nullable Integer releaseYear) {
      this.releaseYear = releaseYear;
      return this;
    }

    /**
     * Sets the month of the release date.
     *
     * <p>Value should be between 1 and 12.
     */
    @CanIgnoreReturnValue
    public Builder setReleaseMonth(@Nullable @IntRange(from = 1, to = 12) Integer releaseMonth) {
      this.releaseMonth = releaseMonth;
      return this;
    }

    /**
     * Sets the day of the release date.
     *
     * <p>Value should be between 1 and 31.
     */
    @CanIgnoreReturnValue
    public Builder setReleaseDay(@Nullable @IntRange(from = 1, to = 31) Integer releaseDay) {
      this.releaseDay = releaseDay;
      return this;
    }

    /** Sets the writer. */
    @CanIgnoreReturnValue
    public Builder setWriter(@Nullable CharSequence writer) {
      this.writer = writer;
      return this;
    }

    /** Sets the composer. */
    @CanIgnoreReturnValue
    public Builder setComposer(@Nullable CharSequence composer) {
      this.composer = composer;
      return this;
    }

    /** Sets the conductor. */
    @CanIgnoreReturnValue
    public Builder setConductor(@Nullable CharSequence conductor) {
      this.conductor = conductor;
      return this;
    }

    /** Sets the disc number. */
    @CanIgnoreReturnValue
    public Builder setDiscNumber(@Nullable Integer discNumber) {
      this.discNumber = discNumber;
      return this;
    }

    /** Sets the total number of discs. */
    @CanIgnoreReturnValue
    public Builder setTotalDiscCount(@Nullable Integer totalDiscCount) {
      this.totalDiscCount = totalDiscCount;
      return this;
    }

    /** Sets the genre. */
    @CanIgnoreReturnValue
    public Builder setGenre(@Nullable CharSequence genre) {
      this.genre = genre;
      return this;
    }

    /** Sets the compilation. */
    @CanIgnoreReturnValue
    public Builder setCompilation(@Nullable CharSequence compilation) {
      this.compilation = compilation;
      return this;
    }

    /** Sets the name of the station streaming the media. */
    @CanIgnoreReturnValue
    public Builder setStation(@Nullable CharSequence station) {
      this.station = station;
      return this;
    }

    /** Sets the {@link MediaType}. */
    @CanIgnoreReturnValue
    public Builder setMediaType(@Nullable @MediaType Integer mediaType) {
      this.mediaType = mediaType;
      return this;
    }

    /** Sets the extras {@link Bundle}. */
    @CanIgnoreReturnValue
    public Builder setExtras(@Nullable Bundle extras) {
      this.extras = extras;
      return this;
    }

    /**
     * Sets all fields supported by the {@link Metadata.Entry entries} within the {@link Metadata}.
     *
     * <p>Fields are only set if the {@link Metadata.Entry} has an implementation for {@link
     * Metadata.Entry#populateMediaMetadata(Builder)}.
     *
     * <p>In the event that multiple {@link Metadata.Entry} objects within the {@link Metadata}
     * relate to the same {@link MediaMetadata} field, then the last one will be used.
     */
    @CanIgnoreReturnValue
    public Builder populateFromMetadata(Metadata metadata) {
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        entry.populateMediaMetadata(this);
      }
      return this;
    }

    /**
     * Sets all fields supported by the {@link Metadata.Entry entries} within the list of {@link
     * Metadata}.
     *
     * <p>Fields are only set if the {@link Metadata.Entry} has an implementation for {@link
     * Metadata.Entry#populateMediaMetadata(Builder)}.
     *
     * <p>In the event that multiple {@link Metadata.Entry} objects within any of the {@link
     * Metadata} relate to the same {@link MediaMetadata} field, then the last one will be used.
     */
    @CanIgnoreReturnValue
    public Builder populateFromMetadata(List<Metadata> metadataList) {
      for (int i = 0; i < metadataList.size(); i++) {
        Metadata metadata = metadataList.get(i);
        for (int j = 0; j < metadata.length(); j++) {
          Metadata.Entry entry = metadata.get(j);
          entry.populateMediaMetadata(this);
        }
      }
      return this;
    }

    /** Populates all the fields from {@code mediaMetadata}, provided they are non-null. */
    @CanIgnoreReturnValue
    public Builder populate(@Nullable MediaMetadata mediaMetadata) {
      if (mediaMetadata == null) {
        return this;
      }
      if (mediaMetadata.title != null) {
        setTitle(mediaMetadata.title);
      }
      if (mediaMetadata.artist != null) {
        setArtist(mediaMetadata.artist);
      }
      if (mediaMetadata.albumTitle != null) {
        setAlbumTitle(mediaMetadata.albumTitle);
      }
      if (mediaMetadata.albumArtist != null) {
        setAlbumArtist(mediaMetadata.albumArtist);
      }
      if (mediaMetadata.displayTitle != null) {
        setDisplayTitle(mediaMetadata.displayTitle);
      }
      if (mediaMetadata.subtitle != null) {
        setSubtitle(mediaMetadata.subtitle);
      }
      if (mediaMetadata.description != null) {
        setDescription(mediaMetadata.description);
      }
      if (mediaMetadata.userRating != null) {
        setUserRating(mediaMetadata.userRating);
      }
      if (mediaMetadata.overallRating != null) {
        setOverallRating(mediaMetadata.overallRating);
      }
      if (mediaMetadata.artworkData != null) {
        setArtworkData(mediaMetadata.artworkData, mediaMetadata.artworkDataType);
      }
      if (mediaMetadata.artworkUri != null) {
        setArtworkUri(mediaMetadata.artworkUri);
      }
      if (mediaMetadata.trackNumber != null) {
        setTrackNumber(mediaMetadata.trackNumber);
      }
      if (mediaMetadata.totalTrackCount != null) {
        setTotalTrackCount(mediaMetadata.totalTrackCount);
      }
      if (mediaMetadata.folderType != null) {
        setFolderType(mediaMetadata.folderType);
      }
      if (mediaMetadata.isBrowsable != null) {
        setIsBrowsable(mediaMetadata.isBrowsable);
      }
      if (mediaMetadata.isPlayable != null) {
        setIsPlayable(mediaMetadata.isPlayable);
      }
      if (mediaMetadata.year != null) {
        setRecordingYear(mediaMetadata.year);
      }
      if (mediaMetadata.recordingYear != null) {
        setRecordingYear(mediaMetadata.recordingYear);
      }
      if (mediaMetadata.recordingMonth != null) {
        setRecordingMonth(mediaMetadata.recordingMonth);
      }
      if (mediaMetadata.recordingDay != null) {
        setRecordingDay(mediaMetadata.recordingDay);
      }
      if (mediaMetadata.releaseYear != null) {
        setReleaseYear(mediaMetadata.releaseYear);
      }
      if (mediaMetadata.releaseMonth != null) {
        setReleaseMonth(mediaMetadata.releaseMonth);
      }
      if (mediaMetadata.releaseDay != null) {
        setReleaseDay(mediaMetadata.releaseDay);
      }
      if (mediaMetadata.writer != null) {
        setWriter(mediaMetadata.writer);
      }
      if (mediaMetadata.composer != null) {
        setComposer(mediaMetadata.composer);
      }
      if (mediaMetadata.conductor != null) {
        setConductor(mediaMetadata.conductor);
      }
      if (mediaMetadata.discNumber != null) {
        setDiscNumber(mediaMetadata.discNumber);
      }
      if (mediaMetadata.totalDiscCount != null) {
        setTotalDiscCount(mediaMetadata.totalDiscCount);
      }
      if (mediaMetadata.genre != null) {
        setGenre(mediaMetadata.genre);
      }
      if (mediaMetadata.compilation != null) {
        setCompilation(mediaMetadata.compilation);
      }
      if (mediaMetadata.station != null) {
        setStation(mediaMetadata.station);
      }
      if (mediaMetadata.mediaType != null) {
        setMediaType(mediaMetadata.mediaType);
      }
      if (mediaMetadata.extras != null) {
        setExtras(mediaMetadata.extras);
      }

      return this;
    }

    /** Returns a new {@link MediaMetadata} instance with the current builder values. */
    public MediaMetadata build() {
      return new MediaMetadata(/* builder= */ this);
    }
  }

  /**
   * The type of content described by the media item.
   *
   * <p>One of {@link #MEDIA_TYPE_MIXED}, {@link #MEDIA_TYPE_MUSIC}, {@link
   * #MEDIA_TYPE_AUDIO_BOOK_CHAPTER}, {@link #MEDIA_TYPE_PODCAST_EPISODE}, {@link
   * #MEDIA_TYPE_RADIO_STATION}, {@link #MEDIA_TYPE_NEWS}, {@link #MEDIA_TYPE_VIDEO}, {@link
   * #MEDIA_TYPE_TRAILER}, {@link #MEDIA_TYPE_MOVIE}, {@link #MEDIA_TYPE_TV_SHOW}, {@link
   * #MEDIA_TYPE_ALBUM}, {@link #MEDIA_TYPE_ARTIST}, {@link #MEDIA_TYPE_GENRE}, {@link
   * #MEDIA_TYPE_PLAYLIST}, {@link #MEDIA_TYPE_YEAR}, {@link #MEDIA_TYPE_AUDIO_BOOK}, {@link
   * #MEDIA_TYPE_PODCAST}, {@link #MEDIA_TYPE_TV_CHANNEL}, {@link #MEDIA_TYPE_TV_SERIES}, {@link
   * #MEDIA_TYPE_TV_SEASON}, {@link #MEDIA_TYPE_FOLDER_MIXED}, {@link #MEDIA_TYPE_FOLDER_ALBUMS},
   * {@link #MEDIA_TYPE_FOLDER_ARTISTS}, {@link #MEDIA_TYPE_FOLDER_GENRES}, {@link
   * #MEDIA_TYPE_FOLDER_PLAYLISTS}, {@link #MEDIA_TYPE_FOLDER_YEARS}, {@link
   * #MEDIA_TYPE_FOLDER_AUDIO_BOOKS}, {@link #MEDIA_TYPE_FOLDER_PODCASTS}, {@link
   * #MEDIA_TYPE_FOLDER_TV_CHANNELS}, {@link #MEDIA_TYPE_FOLDER_TV_SERIES}, {@link
   * #MEDIA_TYPE_FOLDER_TV_SHOWS}, {@link #MEDIA_TYPE_FOLDER_RADIO_STATIONS}, {@link
   * #MEDIA_TYPE_FOLDER_NEWS}, {@link #MEDIA_TYPE_FOLDER_VIDEOS}, {@link
   * #MEDIA_TYPE_FOLDER_TRAILERS} or {@link #MEDIA_TYPE_FOLDER_MOVIES}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    MEDIA_TYPE_MIXED,
    MEDIA_TYPE_MUSIC,
    MEDIA_TYPE_AUDIO_BOOK_CHAPTER,
    MEDIA_TYPE_PODCAST_EPISODE,
    MEDIA_TYPE_RADIO_STATION,
    MEDIA_TYPE_NEWS,
    MEDIA_TYPE_VIDEO,
    MEDIA_TYPE_TRAILER,
    MEDIA_TYPE_MOVIE,
    MEDIA_TYPE_TV_SHOW,
    MEDIA_TYPE_ALBUM,
    MEDIA_TYPE_ARTIST,
    MEDIA_TYPE_GENRE,
    MEDIA_TYPE_PLAYLIST,
    MEDIA_TYPE_YEAR,
    MEDIA_TYPE_AUDIO_BOOK,
    MEDIA_TYPE_PODCAST,
    MEDIA_TYPE_TV_CHANNEL,
    MEDIA_TYPE_TV_SERIES,
    MEDIA_TYPE_TV_SEASON,
    MEDIA_TYPE_FOLDER_MIXED,
    MEDIA_TYPE_FOLDER_ALBUMS,
    MEDIA_TYPE_FOLDER_ARTISTS,
    MEDIA_TYPE_FOLDER_GENRES,
    MEDIA_TYPE_FOLDER_PLAYLISTS,
    MEDIA_TYPE_FOLDER_YEARS,
    MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
    MEDIA_TYPE_FOLDER_PODCASTS,
    MEDIA_TYPE_FOLDER_TV_CHANNELS,
    MEDIA_TYPE_FOLDER_TV_SERIES,
    MEDIA_TYPE_FOLDER_TV_SHOWS,
    MEDIA_TYPE_FOLDER_RADIO_STATIONS,
    MEDIA_TYPE_FOLDER_NEWS,
    MEDIA_TYPE_FOLDER_VIDEOS,
    MEDIA_TYPE_FOLDER_TRAILERS,
    MEDIA_TYPE_FOLDER_MOVIES,
  })
  public @interface MediaType {}

  /** Media of undetermined type or a mix of multiple {@linkplain MediaType media types}. */
  public static final int MEDIA_TYPE_MIXED = 0;
  /** {@link MediaType} for music. */
  public static final int MEDIA_TYPE_MUSIC = 1;
  /** {@link MediaType} for an audio book chapter. */
  public static final int MEDIA_TYPE_AUDIO_BOOK_CHAPTER = 2;
  /** {@link MediaType} for a podcast episode. */
  public static final int MEDIA_TYPE_PODCAST_EPISODE = 3;
  /** {@link MediaType} for a radio station. */
  public static final int MEDIA_TYPE_RADIO_STATION = 4;
  /** {@link MediaType} for news. */
  public static final int MEDIA_TYPE_NEWS = 5;
  /** {@link MediaType} for a video. */
  public static final int MEDIA_TYPE_VIDEO = 6;
  /** {@link MediaType} for a movie trailer. */
  public static final int MEDIA_TYPE_TRAILER = 7;
  /** {@link MediaType} for a movie. */
  public static final int MEDIA_TYPE_MOVIE = 8;
  /** {@link MediaType} for a TV show. */
  public static final int MEDIA_TYPE_TV_SHOW = 9;
  /**
   * {@link MediaType} for a group of items (e.g., {@link #MEDIA_TYPE_MUSIC music}) belonging to an
   * album.
   */
  public static final int MEDIA_TYPE_ALBUM = 10;
  /**
   * {@link MediaType} for a group of items (e.g., {@link #MEDIA_TYPE_MUSIC music}) from the same
   * artist.
   */
  public static final int MEDIA_TYPE_ARTIST = 11;
  /**
   * {@link MediaType} for a group of items (e.g., {@link #MEDIA_TYPE_MUSIC music}) of the same
   * genre.
   */
  public static final int MEDIA_TYPE_GENRE = 12;
  /**
   * {@link MediaType} for a group of items (e.g., {@link #MEDIA_TYPE_MUSIC music}) forming a
   * playlist.
   */
  public static final int MEDIA_TYPE_PLAYLIST = 13;
  /**
   * {@link MediaType} for a group of items (e.g., {@link #MEDIA_TYPE_MUSIC music}) from the same
   * year.
   */
  public static final int MEDIA_TYPE_YEAR = 14;
  /**
   * {@link MediaType} for a group of items forming an audio book. Items in this group are typically
   * of type {@link #MEDIA_TYPE_AUDIO_BOOK_CHAPTER}.
   */
  public static final int MEDIA_TYPE_AUDIO_BOOK = 15;
  /**
   * {@link MediaType} for a group of items belonging to a podcast. Items in this group are
   * typically of type {@link #MEDIA_TYPE_PODCAST_EPISODE}.
   */
  public static final int MEDIA_TYPE_PODCAST = 16;
  /**
   * {@link MediaType} for a group of items that are part of a TV channel. Items in this group are
   * typically of type {@link #MEDIA_TYPE_TV_SHOW}, {@link #MEDIA_TYPE_TV_SERIES} or {@link
   * #MEDIA_TYPE_MOVIE}.
   */
  public static final int MEDIA_TYPE_TV_CHANNEL = 17;
  /**
   * {@link MediaType} for a group of items that are part of a TV series. Items in this group are
   * typically of type {@link #MEDIA_TYPE_TV_SHOW} or {@link #MEDIA_TYPE_TV_SEASON}.
   */
  public static final int MEDIA_TYPE_TV_SERIES = 18;
  /**
   * {@link MediaType} for a group of items that are part of a TV series. Items in this group are
   * typically of type {@link #MEDIA_TYPE_TV_SHOW}.
   */
  public static final int MEDIA_TYPE_TV_SEASON = 19;
  /** {@link MediaType} for a folder with mixed or undetermined content. */
  public static final int MEDIA_TYPE_FOLDER_MIXED = 20;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_ALBUM albums}. */
  public static final int MEDIA_TYPE_FOLDER_ALBUMS = 21;
  /** {@link MediaType} for a folder containing {@linkplain #FIELD_ARTIST artists}. */
  public static final int MEDIA_TYPE_FOLDER_ARTISTS = 22;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_GENRE genres}. */
  public static final int MEDIA_TYPE_FOLDER_GENRES = 23;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_PLAYLIST playlists}. */
  public static final int MEDIA_TYPE_FOLDER_PLAYLISTS = 24;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_YEAR years}. */
  public static final int MEDIA_TYPE_FOLDER_YEARS = 25;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_AUDIO_BOOK audio books}. */
  public static final int MEDIA_TYPE_FOLDER_AUDIO_BOOKS = 26;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_PODCAST podcasts}. */
  public static final int MEDIA_TYPE_FOLDER_PODCASTS = 27;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_TV_CHANNEL TV channels}. */
  public static final int MEDIA_TYPE_FOLDER_TV_CHANNELS = 28;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_TV_SERIES TV series}. */
  public static final int MEDIA_TYPE_FOLDER_TV_SERIES = 29;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_TV_SHOW TV shows}. */
  public static final int MEDIA_TYPE_FOLDER_TV_SHOWS = 30;
  /**
   * {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_RADIO_STATION radio
   * stations}.
   */
  public static final int MEDIA_TYPE_FOLDER_RADIO_STATIONS = 31;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_NEWS news}. */
  public static final int MEDIA_TYPE_FOLDER_NEWS = 32;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_VIDEO videos}. */
  public static final int MEDIA_TYPE_FOLDER_VIDEOS = 33;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_TRAILER movie trailers}. */
  public static final int MEDIA_TYPE_FOLDER_TRAILERS = 34;
  /** {@link MediaType} for a folder containing {@linkplain #MEDIA_TYPE_MOVIE movies}. */
  public static final int MEDIA_TYPE_FOLDER_MOVIES = 35;

  /**
   * The folder type of the media item.
   *
   * <p>This can be used as the type of a browsable bluetooth folder (see section 6.10.2.2 of the <a
   * href="https://www.bluetooth.com/specifications/specs/a-v-remote-control-profile-1-6-2/">Bluetooth
   * AVRCP 1.6.2</a>).
   *
   * <p>One of {@link #FOLDER_TYPE_NONE}, {@link #FOLDER_TYPE_MIXED}, {@link #FOLDER_TYPE_TITLES},
   * {@link #FOLDER_TYPE_ALBUMS}, {@link #FOLDER_TYPE_ARTISTS}, {@link #FOLDER_TYPE_GENRES}, {@link
   * #FOLDER_TYPE_PLAYLISTS} or {@link #FOLDER_TYPE_YEARS}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    FOLDER_TYPE_NONE,
    FOLDER_TYPE_MIXED,
    FOLDER_TYPE_TITLES,
    FOLDER_TYPE_ALBUMS,
    FOLDER_TYPE_ARTISTS,
    FOLDER_TYPE_GENRES,
    FOLDER_TYPE_PLAYLISTS,
    FOLDER_TYPE_YEARS
  })
  public @interface FolderType {}

  /** Type for an item that is not a folder. */
  public static final int FOLDER_TYPE_NONE = -1;
  /** Type for a folder containing media of mixed types. */
  public static final int FOLDER_TYPE_MIXED = 0;
  /** Type for a folder containing only playable media. */
  public static final int FOLDER_TYPE_TITLES = 1;
  /** Type for a folder containing media categorized by album. */
  public static final int FOLDER_TYPE_ALBUMS = 2;
  /** Type for a folder containing media categorized by artist. */
  public static final int FOLDER_TYPE_ARTISTS = 3;
  /** Type for a folder containing media categorized by genre. */
  public static final int FOLDER_TYPE_GENRES = 4;
  /** Type for a folder containing a playlist. */
  public static final int FOLDER_TYPE_PLAYLISTS = 5;
  /** Type for a folder containing media categorized by year. */
  public static final int FOLDER_TYPE_YEARS = 6;

  /**
   * The picture type of the artwork.
   *
   * <p>Values sourced from the ID3 v2.4 specification (See section 4.14 of
   * https://id3.org/id3v2.4.0-frames).
   *
   * <p>One of {@link #PICTURE_TYPE_OTHER}, {@link #PICTURE_TYPE_FILE_ICON}, {@link
   * #PICTURE_TYPE_FILE_ICON_OTHER}, {@link #PICTURE_TYPE_FRONT_COVER}, {@link
   * #PICTURE_TYPE_BACK_COVER}, {@link #PICTURE_TYPE_LEAFLET_PAGE}, {@link #PICTURE_TYPE_MEDIA},
   * {@link #PICTURE_TYPE_LEAD_ARTIST_PERFORMER}, {@link #PICTURE_TYPE_ARTIST_PERFORMER}, {@link
   * #PICTURE_TYPE_CONDUCTOR}, {@link #PICTURE_TYPE_BAND_ORCHESTRA}, {@link #PICTURE_TYPE_COMPOSER},
   * {@link #PICTURE_TYPE_LYRICIST}, {@link #PICTURE_TYPE_RECORDING_LOCATION}, {@link
   * #PICTURE_TYPE_DURING_RECORDING}, {@link #PICTURE_TYPE_DURING_PERFORMANCE}, {@link
   * #PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE}, {@link #PICTURE_TYPE_A_BRIGHT_COLORED_FISH}, {@link
   * #PICTURE_TYPE_ILLUSTRATION}, {@link #PICTURE_TYPE_BAND_ARTIST_LOGO} or {@link
   * #PICTURE_TYPE_PUBLISHER_STUDIO_LOGO}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    PICTURE_TYPE_OTHER,
    PICTURE_TYPE_FILE_ICON,
    PICTURE_TYPE_FILE_ICON_OTHER,
    PICTURE_TYPE_FRONT_COVER,
    PICTURE_TYPE_BACK_COVER,
    PICTURE_TYPE_LEAFLET_PAGE,
    PICTURE_TYPE_MEDIA,
    PICTURE_TYPE_LEAD_ARTIST_PERFORMER,
    PICTURE_TYPE_ARTIST_PERFORMER,
    PICTURE_TYPE_CONDUCTOR,
    PICTURE_TYPE_BAND_ORCHESTRA,
    PICTURE_TYPE_COMPOSER,
    PICTURE_TYPE_LYRICIST,
    PICTURE_TYPE_RECORDING_LOCATION,
    PICTURE_TYPE_DURING_RECORDING,
    PICTURE_TYPE_DURING_PERFORMANCE,
    PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE,
    PICTURE_TYPE_A_BRIGHT_COLORED_FISH,
    PICTURE_TYPE_ILLUSTRATION,
    PICTURE_TYPE_BAND_ARTIST_LOGO,
    PICTURE_TYPE_PUBLISHER_STUDIO_LOGO
  })
  public @interface PictureType {}

  public static final int PICTURE_TYPE_OTHER = 0x00;
  public static final int PICTURE_TYPE_FILE_ICON = 0x01;
  public static final int PICTURE_TYPE_FILE_ICON_OTHER = 0x02;
  public static final int PICTURE_TYPE_FRONT_COVER = 0x03;
  public static final int PICTURE_TYPE_BACK_COVER = 0x04;
  public static final int PICTURE_TYPE_LEAFLET_PAGE = 0x05;
  public static final int PICTURE_TYPE_MEDIA = 0x06;
  public static final int PICTURE_TYPE_LEAD_ARTIST_PERFORMER = 0x07;
  public static final int PICTURE_TYPE_ARTIST_PERFORMER = 0x08;
  public static final int PICTURE_TYPE_CONDUCTOR = 0x09;
  public static final int PICTURE_TYPE_BAND_ORCHESTRA = 0x0A;
  public static final int PICTURE_TYPE_COMPOSER = 0x0B;
  public static final int PICTURE_TYPE_LYRICIST = 0x0C;
  public static final int PICTURE_TYPE_RECORDING_LOCATION = 0x0D;
  public static final int PICTURE_TYPE_DURING_RECORDING = 0x0E;
  public static final int PICTURE_TYPE_DURING_PERFORMANCE = 0x0F;
  public static final int PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE = 0x10;
  public static final int PICTURE_TYPE_A_BRIGHT_COLORED_FISH = 0x11;
  public static final int PICTURE_TYPE_ILLUSTRATION = 0x12;
  public static final int PICTURE_TYPE_BAND_ARTIST_LOGO = 0x13;
  public static final int PICTURE_TYPE_PUBLISHER_STUDIO_LOGO = 0x14;

  /** Empty {@link MediaMetadata}. */
  public static final MediaMetadata EMPTY = new MediaMetadata.Builder().build();

  /** Optional title. */
  @Nullable public final CharSequence title;
  /** Optional artist. */
  @Nullable public final CharSequence artist;
  /** Optional album title. */
  @Nullable public final CharSequence albumTitle;
  /** Optional album artist. */
  @Nullable public final CharSequence albumArtist;
  /** Optional display title. */
  @Nullable public final CharSequence displayTitle;
  /**
   * Optional subtitle.
   *
   * <p>This is the secondary title of the media, unrelated to closed captions.
   */
  @Nullable public final CharSequence subtitle;
  /** Optional description. */
  @Nullable public final CharSequence description;
  /** Optional user {@link Rating}. */
  @Nullable public final Rating userRating;
  /** Optional overall {@link Rating}. */
  @Nullable public final Rating overallRating;
  /** Optional artwork data as a compressed byte array. */
  @Nullable public final byte[] artworkData;
  /** Optional {@link PictureType} of the artwork data. */
  @Nullable public final @PictureType Integer artworkDataType;
  /** Optional artwork {@link Uri}. */
  @Nullable public final Uri artworkUri;
  /** Optional track number. */
  @Nullable public final Integer trackNumber;
  /** Optional total number of tracks. */
  @Nullable public final Integer totalTrackCount;
  /**
   * Optional {@link FolderType}.
   *
   * <p>This field will be deprecated. Use {@link #isBrowsable} to indicate if an item is a
   * browsable folder and use {@link #mediaType} to indicate the type of the folder.
   */
  @Nullable public final @FolderType Integer folderType;
  /** Optional boolean to indicate that the media is a browsable folder. */
  @Nullable public final Boolean isBrowsable;
  /** Optional boolean to indicate that the media is playable. */
  @Nullable public final Boolean isPlayable;
  /**
   * @deprecated Use {@link #recordingYear} instead.
   */
  @Deprecated @Nullable public final Integer year;
  /** Optional year of the recording date. */
  @Nullable public final Integer recordingYear;
  /**
   * Optional month of the recording date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer recordingMonth;
  /**
   * Optional day of the recording date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer recordingDay;

  /** Optional year of the release date. */
  @Nullable public final Integer releaseYear;
  /**
   * Optional month of the release date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer releaseMonth;
  /**
   * Optional day of the release date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer releaseDay;
  /** Optional writer. */
  @Nullable public final CharSequence writer;
  /** Optional composer. */
  @Nullable public final CharSequence composer;
  /** Optional conductor. */
  @Nullable public final CharSequence conductor;
  /** Optional disc number. */
  @Nullable public final Integer discNumber;
  /** Optional total number of discs. */
  @Nullable public final Integer totalDiscCount;
  /** Optional genre. */
  @Nullable public final CharSequence genre;
  /** Optional compilation. */
  @Nullable public final CharSequence compilation;
  /** Optional name of the station streaming the media. */
  @Nullable public final CharSequence station;
  /** Optional {@link MediaType}. */
  @Nullable public final @MediaType Integer mediaType;

  /**
   * Optional extras {@link Bundle}.
   *
   * <p>Given the complexities of checking the equality of two {@link Bundle}s, this is not
   * considered in the {@link #equals(Object)} or {@link #hashCode()}.
   */
  @Nullable public final Bundle extras;

  private MediaMetadata(Builder builder) {
    // Handle compatibility for deprecated fields.
    @Nullable Boolean isBrowsable = builder.isBrowsable;
    @Nullable Integer folderType = builder.folderType;
    @Nullable Integer mediaType = builder.mediaType;
    if (isBrowsable != null) {
      if (!isBrowsable) {
        folderType = FOLDER_TYPE_NONE;
      } else if (folderType == null || folderType == FOLDER_TYPE_NONE) {
        folderType = mediaType != null ? getFolderTypeFromMediaType(mediaType) : FOLDER_TYPE_MIXED;
      }
    } else if (folderType != null) {
      isBrowsable = folderType != FOLDER_TYPE_NONE;
      if (isBrowsable && mediaType == null) {
        mediaType = getMediaTypeFromFolderType(folderType);
      }
    }
    this.title = builder.title;
    this.artist = builder.artist;
    this.albumTitle = builder.albumTitle;
    this.albumArtist = builder.albumArtist;
    this.displayTitle = builder.displayTitle;
    this.subtitle = builder.subtitle;
    this.description = builder.description;
    this.userRating = builder.userRating;
    this.overallRating = builder.overallRating;
    this.artworkData = builder.artworkData;
    this.artworkDataType = builder.artworkDataType;
    this.artworkUri = builder.artworkUri;
    this.trackNumber = builder.trackNumber;
    this.totalTrackCount = builder.totalTrackCount;
    this.folderType = folderType;
    this.isBrowsable = isBrowsable;
    this.isPlayable = builder.isPlayable;
    this.year = builder.recordingYear;
    this.recordingYear = builder.recordingYear;
    this.recordingMonth = builder.recordingMonth;
    this.recordingDay = builder.recordingDay;
    this.releaseYear = builder.releaseYear;
    this.releaseMonth = builder.releaseMonth;
    this.releaseDay = builder.releaseDay;
    this.writer = builder.writer;
    this.composer = builder.composer;
    this.conductor = builder.conductor;
    this.discNumber = builder.discNumber;
    this.totalDiscCount = builder.totalDiscCount;
    this.genre = builder.genre;
    this.compilation = builder.compilation;
    this.station = builder.station;
    this.mediaType = mediaType;
    this.extras = builder.extras;
  }

  /** Returns a new {@link Builder} instance with the current {@link MediaMetadata} fields. */
  public Builder buildUpon() {
    return new Builder(/* mediaMetadata= */ this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MediaMetadata that = (MediaMetadata) obj;
    return Util.areEqual(title, that.title)
        && Util.areEqual(artist, that.artist)
        && Util.areEqual(albumTitle, that.albumTitle)
        && Util.areEqual(albumArtist, that.albumArtist)
        && Util.areEqual(displayTitle, that.displayTitle)
        && Util.areEqual(subtitle, that.subtitle)
        && Util.areEqual(description, that.description)
        && Util.areEqual(userRating, that.userRating)
        && Util.areEqual(overallRating, that.overallRating)
        && Arrays.equals(artworkData, that.artworkData)
        && Util.areEqual(artworkDataType, that.artworkDataType)
        && Util.areEqual(artworkUri, that.artworkUri)
        && Util.areEqual(trackNumber, that.trackNumber)
        && Util.areEqual(totalTrackCount, that.totalTrackCount)
        && Util.areEqual(folderType, that.folderType)
        && Util.areEqual(isBrowsable, that.isBrowsable)
        && Util.areEqual(isPlayable, that.isPlayable)
        && Util.areEqual(recordingYear, that.recordingYear)
        && Util.areEqual(recordingMonth, that.recordingMonth)
        && Util.areEqual(recordingDay, that.recordingDay)
        && Util.areEqual(releaseYear, that.releaseYear)
        && Util.areEqual(releaseMonth, that.releaseMonth)
        && Util.areEqual(releaseDay, that.releaseDay)
        && Util.areEqual(writer, that.writer)
        && Util.areEqual(composer, that.composer)
        && Util.areEqual(conductor, that.conductor)
        && Util.areEqual(discNumber, that.discNumber)
        && Util.areEqual(totalDiscCount, that.totalDiscCount)
        && Util.areEqual(genre, that.genre)
        && Util.areEqual(compilation, that.compilation)
        && Util.areEqual(station, that.station)
        && Util.areEqual(mediaType, that.mediaType);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        title,
        artist,
        albumTitle,
        albumArtist,
        displayTitle,
        subtitle,
        description,
        userRating,
        overallRating,
        Arrays.hashCode(artworkData),
        artworkDataType,
        artworkUri,
        trackNumber,
        totalTrackCount,
        folderType,
        isBrowsable,
        isPlayable,
        recordingYear,
        recordingMonth,
        recordingDay,
        releaseYear,
        releaseMonth,
        releaseDay,
        writer,
        composer,
        conductor,
        discNumber,
        totalDiscCount,
        genre,
        compilation,
        station,
        mediaType);
  }

  // Bundleable implementation.

  private static final String FIELD_TITLE = Util.intToStringMaxRadix(0);
  private static final String FIELD_ARTIST = Util.intToStringMaxRadix(1);
  private static final String FIELD_ALBUM_TITLE = Util.intToStringMaxRadix(2);
  private static final String FIELD_ALBUM_ARTIST = Util.intToStringMaxRadix(3);
  private static final String FIELD_DISPLAY_TITLE = Util.intToStringMaxRadix(4);
  private static final String FIELD_SUBTITLE = Util.intToStringMaxRadix(5);
  private static final String FIELD_DESCRIPTION = Util.intToStringMaxRadix(6);
  // 7 is reserved to maintain backward compatibility for a previously defined field.
  private static final String FIELD_USER_RATING = Util.intToStringMaxRadix(8);
  private static final String FIELD_OVERALL_RATING = Util.intToStringMaxRadix(9);
  private static final String FIELD_ARTWORK_DATA = Util.intToStringMaxRadix(10);
  private static final String FIELD_ARTWORK_URI = Util.intToStringMaxRadix(11);
  private static final String FIELD_TRACK_NUMBER = Util.intToStringMaxRadix(12);
  private static final String FIELD_TOTAL_TRACK_COUNT = Util.intToStringMaxRadix(13);
  private static final String FIELD_FOLDER_TYPE = Util.intToStringMaxRadix(14);
  private static final String FIELD_IS_PLAYABLE = Util.intToStringMaxRadix(15);
  private static final String FIELD_RECORDING_YEAR = Util.intToStringMaxRadix(16);
  private static final String FIELD_RECORDING_MONTH = Util.intToStringMaxRadix(17);
  private static final String FIELD_RECORDING_DAY = Util.intToStringMaxRadix(18);
  private static final String FIELD_RELEASE_YEAR = Util.intToStringMaxRadix(19);
  private static final String FIELD_RELEASE_MONTH = Util.intToStringMaxRadix(20);
  private static final String FIELD_RELEASE_DAY = Util.intToStringMaxRadix(21);
  private static final String FIELD_WRITER = Util.intToStringMaxRadix(22);
  private static final String FIELD_COMPOSER = Util.intToStringMaxRadix(23);
  private static final String FIELD_CONDUCTOR = Util.intToStringMaxRadix(24);
  private static final String FIELD_DISC_NUMBER = Util.intToStringMaxRadix(25);
  private static final String FIELD_TOTAL_DISC_COUNT = Util.intToStringMaxRadix(26);
  private static final String FIELD_GENRE = Util.intToStringMaxRadix(27);
  private static final String FIELD_COMPILATION = Util.intToStringMaxRadix(28);
  private static final String FIELD_ARTWORK_DATA_TYPE = Util.intToStringMaxRadix(29);
  private static final String FIELD_STATION = Util.intToStringMaxRadix(30);
  private static final String FIELD_MEDIA_TYPE = Util.intToStringMaxRadix(31);
  private static final String FIELD_IS_BROWSABLE = Util.intToStringMaxRadix(32);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(1000);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (title != null) {
      bundle.putCharSequence(FIELD_TITLE, title);
    }
    if (artist != null) {
      bundle.putCharSequence(FIELD_ARTIST, artist);
    }
    if (albumTitle != null) {
      bundle.putCharSequence(FIELD_ALBUM_TITLE, albumTitle);
    }
    if (albumArtist != null) {
      bundle.putCharSequence(FIELD_ALBUM_ARTIST, albumArtist);
    }
    if (displayTitle != null) {
      bundle.putCharSequence(FIELD_DISPLAY_TITLE, displayTitle);
    }
    if (subtitle != null) {
      bundle.putCharSequence(FIELD_SUBTITLE, subtitle);
    }
    if (description != null) {
      bundle.putCharSequence(FIELD_DESCRIPTION, description);
    }
    if (artworkData != null) {
      bundle.putByteArray(FIELD_ARTWORK_DATA, artworkData);
    }
    if (artworkUri != null) {
      bundle.putParcelable(FIELD_ARTWORK_URI, artworkUri);
    }
    if (writer != null) {
      bundle.putCharSequence(FIELD_WRITER, writer);
    }
    if (composer != null) {
      bundle.putCharSequence(FIELD_COMPOSER, composer);
    }
    if (conductor != null) {
      bundle.putCharSequence(FIELD_CONDUCTOR, conductor);
    }
    if (genre != null) {
      bundle.putCharSequence(FIELD_GENRE, genre);
    }
    if (compilation != null) {
      bundle.putCharSequence(FIELD_COMPILATION, compilation);
    }
    if (station != null) {
      bundle.putCharSequence(FIELD_STATION, station);
    }
    if (userRating != null) {
      bundle.putBundle(FIELD_USER_RATING, userRating.toBundle());
    }
    if (overallRating != null) {
      bundle.putBundle(FIELD_OVERALL_RATING, overallRating.toBundle());
    }
    if (trackNumber != null) {
      bundle.putInt(FIELD_TRACK_NUMBER, trackNumber);
    }
    if (totalTrackCount != null) {
      bundle.putInt(FIELD_TOTAL_TRACK_COUNT, totalTrackCount);
    }
    if (folderType != null) {
      bundle.putInt(FIELD_FOLDER_TYPE, folderType);
    }
    if (isBrowsable != null) {
      bundle.putBoolean(FIELD_IS_BROWSABLE, isBrowsable);
    }
    if (isPlayable != null) {
      bundle.putBoolean(FIELD_IS_PLAYABLE, isPlayable);
    }
    if (recordingYear != null) {
      bundle.putInt(FIELD_RECORDING_YEAR, recordingYear);
    }
    if (recordingMonth != null) {
      bundle.putInt(FIELD_RECORDING_MONTH, recordingMonth);
    }
    if (recordingDay != null) {
      bundle.putInt(FIELD_RECORDING_DAY, recordingDay);
    }
    if (releaseYear != null) {
      bundle.putInt(FIELD_RELEASE_YEAR, releaseYear);
    }
    if (releaseMonth != null) {
      bundle.putInt(FIELD_RELEASE_MONTH, releaseMonth);
    }
    if (releaseDay != null) {
      bundle.putInt(FIELD_RELEASE_DAY, releaseDay);
    }
    if (discNumber != null) {
      bundle.putInt(FIELD_DISC_NUMBER, discNumber);
    }
    if (totalDiscCount != null) {
      bundle.putInt(FIELD_TOTAL_DISC_COUNT, totalDiscCount);
    }
    if (artworkDataType != null) {
      bundle.putInt(FIELD_ARTWORK_DATA_TYPE, artworkDataType);
    }
    if (mediaType != null) {
      bundle.putInt(FIELD_MEDIA_TYPE, mediaType);
    }
    if (extras != null) {
      bundle.putBundle(FIELD_EXTRAS, extras);
    }
    return bundle;
  }

  /** Object that can restore {@link MediaMetadata} from a {@link Bundle}. */
  public static final Creator<MediaMetadata> CREATOR = MediaMetadata::fromBundle;

  private static MediaMetadata fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    builder
        .setTitle(bundle.getCharSequence(FIELD_TITLE))
        .setArtist(bundle.getCharSequence(FIELD_ARTIST))
        .setAlbumTitle(bundle.getCharSequence(FIELD_ALBUM_TITLE))
        .setAlbumArtist(bundle.getCharSequence(FIELD_ALBUM_ARTIST))
        .setDisplayTitle(bundle.getCharSequence(FIELD_DISPLAY_TITLE))
        .setSubtitle(bundle.getCharSequence(FIELD_SUBTITLE))
        .setDescription(bundle.getCharSequence(FIELD_DESCRIPTION))
        .setArtworkData(
            bundle.getByteArray(FIELD_ARTWORK_DATA),
            bundle.containsKey(FIELD_ARTWORK_DATA_TYPE)
                ? bundle.getInt(FIELD_ARTWORK_DATA_TYPE)
                : null)
        .setArtworkUri(bundle.getParcelable(FIELD_ARTWORK_URI))
        .setWriter(bundle.getCharSequence(FIELD_WRITER))
        .setComposer(bundle.getCharSequence(FIELD_COMPOSER))
        .setConductor(bundle.getCharSequence(FIELD_CONDUCTOR))
        .setGenre(bundle.getCharSequence(FIELD_GENRE))
        .setCompilation(bundle.getCharSequence(FIELD_COMPILATION))
        .setStation(bundle.getCharSequence(FIELD_STATION))
        .setExtras(bundle.getBundle(FIELD_EXTRAS));

    if (bundle.containsKey(FIELD_USER_RATING)) {
      @Nullable Bundle fieldBundle = bundle.getBundle(FIELD_USER_RATING);
      if (fieldBundle != null) {
        builder.setUserRating(Rating.CREATOR.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(FIELD_OVERALL_RATING)) {
      @Nullable Bundle fieldBundle = bundle.getBundle(FIELD_OVERALL_RATING);
      if (fieldBundle != null) {
        builder.setOverallRating(Rating.CREATOR.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(FIELD_TRACK_NUMBER)) {
      builder.setTrackNumber(bundle.getInt(FIELD_TRACK_NUMBER));
    }
    if (bundle.containsKey(FIELD_TOTAL_TRACK_COUNT)) {
      builder.setTotalTrackCount(bundle.getInt(FIELD_TOTAL_TRACK_COUNT));
    }
    if (bundle.containsKey(FIELD_FOLDER_TYPE)) {
      builder.setFolderType(bundle.getInt(FIELD_FOLDER_TYPE));
    }
    if (bundle.containsKey(FIELD_IS_BROWSABLE)) {
      builder.setIsBrowsable(bundle.getBoolean(FIELD_IS_BROWSABLE));
    }
    if (bundle.containsKey(FIELD_IS_PLAYABLE)) {
      builder.setIsPlayable(bundle.getBoolean(FIELD_IS_PLAYABLE));
    }
    if (bundle.containsKey(FIELD_RECORDING_YEAR)) {
      builder.setRecordingYear(bundle.getInt(FIELD_RECORDING_YEAR));
    }
    if (bundle.containsKey(FIELD_RECORDING_MONTH)) {
      builder.setRecordingMonth(bundle.getInt(FIELD_RECORDING_MONTH));
    }
    if (bundle.containsKey(FIELD_RECORDING_DAY)) {
      builder.setRecordingDay(bundle.getInt(FIELD_RECORDING_DAY));
    }
    if (bundle.containsKey(FIELD_RELEASE_YEAR)) {
      builder.setReleaseYear(bundle.getInt(FIELD_RELEASE_YEAR));
    }
    if (bundle.containsKey(FIELD_RELEASE_MONTH)) {
      builder.setReleaseMonth(bundle.getInt(FIELD_RELEASE_MONTH));
    }
    if (bundle.containsKey(FIELD_RELEASE_DAY)) {
      builder.setReleaseDay(bundle.getInt(FIELD_RELEASE_DAY));
    }
    if (bundle.containsKey(FIELD_DISC_NUMBER)) {
      builder.setDiscNumber(bundle.getInt(FIELD_DISC_NUMBER));
    }
    if (bundle.containsKey(FIELD_TOTAL_DISC_COUNT)) {
      builder.setTotalDiscCount(bundle.getInt(FIELD_TOTAL_DISC_COUNT));
    }
    if (bundle.containsKey(FIELD_MEDIA_TYPE)) {
      builder.setMediaType(bundle.getInt(FIELD_MEDIA_TYPE));
    }

    return builder.build();
  }

  private static @FolderType int getFolderTypeFromMediaType(@MediaType int mediaType) {
    switch (mediaType) {
      case MEDIA_TYPE_ALBUM:
      case MEDIA_TYPE_ARTIST:
      case MEDIA_TYPE_AUDIO_BOOK:
      case MEDIA_TYPE_AUDIO_BOOK_CHAPTER:
      case MEDIA_TYPE_FOLDER_MOVIES:
      case MEDIA_TYPE_FOLDER_NEWS:
      case MEDIA_TYPE_FOLDER_RADIO_STATIONS:
      case MEDIA_TYPE_FOLDER_TRAILERS:
      case MEDIA_TYPE_FOLDER_VIDEOS:
      case MEDIA_TYPE_GENRE:
      case MEDIA_TYPE_MOVIE:
      case MEDIA_TYPE_MUSIC:
      case MEDIA_TYPE_NEWS:
      case MEDIA_TYPE_PLAYLIST:
      case MEDIA_TYPE_PODCAST:
      case MEDIA_TYPE_PODCAST_EPISODE:
      case MEDIA_TYPE_RADIO_STATION:
      case MEDIA_TYPE_TRAILER:
      case MEDIA_TYPE_TV_CHANNEL:
      case MEDIA_TYPE_TV_SEASON:
      case MEDIA_TYPE_TV_SERIES:
      case MEDIA_TYPE_TV_SHOW:
      case MEDIA_TYPE_VIDEO:
      case MEDIA_TYPE_YEAR:
        return FOLDER_TYPE_TITLES;
      case MEDIA_TYPE_FOLDER_ALBUMS:
        return FOLDER_TYPE_ALBUMS;
      case MEDIA_TYPE_FOLDER_ARTISTS:
        return FOLDER_TYPE_ARTISTS;
      case MEDIA_TYPE_FOLDER_GENRES:
        return FOLDER_TYPE_GENRES;
      case MEDIA_TYPE_FOLDER_PLAYLISTS:
        return FOLDER_TYPE_PLAYLISTS;
      case MEDIA_TYPE_FOLDER_YEARS:
        return FOLDER_TYPE_YEARS;
      case MEDIA_TYPE_FOLDER_AUDIO_BOOKS:
      case MEDIA_TYPE_FOLDER_MIXED:
      case MEDIA_TYPE_FOLDER_TV_CHANNELS:
      case MEDIA_TYPE_FOLDER_TV_SERIES:
      case MEDIA_TYPE_FOLDER_TV_SHOWS:
      case MEDIA_TYPE_FOLDER_PODCASTS:
      case MEDIA_TYPE_MIXED:
      default:
        return FOLDER_TYPE_MIXED;
    }
  }

  private static @MediaType int getMediaTypeFromFolderType(@FolderType int folderType) {
    switch (folderType) {
      case FOLDER_TYPE_ALBUMS:
        return MEDIA_TYPE_FOLDER_ALBUMS;
      case FOLDER_TYPE_ARTISTS:
        return MEDIA_TYPE_FOLDER_ARTISTS;
      case FOLDER_TYPE_GENRES:
        return MEDIA_TYPE_FOLDER_GENRES;
      case FOLDER_TYPE_PLAYLISTS:
        return MEDIA_TYPE_FOLDER_PLAYLISTS;
      case FOLDER_TYPE_TITLES:
        return MEDIA_TYPE_MIXED;
      case FOLDER_TYPE_YEARS:
        return MEDIA_TYPE_FOLDER_YEARS;
      case FOLDER_TYPE_MIXED:
      case FOLDER_TYPE_NONE:
      default:
        return MEDIA_TYPE_FOLDER_MIXED;
    }
  }
}

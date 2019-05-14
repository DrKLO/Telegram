/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.video;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.coremedia.iso.BoxParser;
import com.coremedia.iso.IsoFile;
import com.coremedia.iso.IsoTypeWriter;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.CompositionTimeToSample;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.DataEntryUrlBox;
import com.coremedia.iso.boxes.DataInformationBox;
import com.coremedia.iso.boxes.DataReferenceBox;
import com.coremedia.iso.boxes.FileTypeBox;
import com.coremedia.iso.boxes.HandlerBox;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.MediaInformationBox;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.coremedia.iso.boxes.SampleSizeBox;
import com.coremedia.iso.boxes.SampleTableBox;
import com.coremedia.iso.boxes.SampleToChunkBox;
import com.coremedia.iso.boxes.StaticChunkOffsetBox;
import com.coremedia.iso.boxes.SyncSampleBox;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.util.Matrix;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class MP4Builder {

    private InterleaveChunkMdat mdat = null;
    private Mp4Movie currentMp4Movie = null;
    private FileOutputStream fos = null;
    private FileChannel fc = null;
    private long dataOffset = 0;
    private long wroteSinceLastMdat = 0;
    private boolean writeNewMdat = true;
    private HashMap<Track, long[]> track2SampleSizes = new HashMap<>();
    private ByteBuffer sizeBuffer = null;
    private boolean splitMdat;
    private boolean wasFirstVideoFrame;

    public MP4Builder createMovie(Mp4Movie mp4Movie, boolean split) throws Exception {
        currentMp4Movie = mp4Movie;

        fos = new FileOutputStream(mp4Movie.getCacheFile());
        fc = fos.getChannel();

        FileTypeBox fileTypeBox = createFileTypeBox();
        fileTypeBox.getBox(fc);
        dataOffset += fileTypeBox.getSize();
        wroteSinceLastMdat += dataOffset;
        splitMdat = split;

        mdat = new InterleaveChunkMdat();

        sizeBuffer = ByteBuffer.allocateDirect(4);

        return this;
    }

    private void flushCurrentMdat() throws Exception {
        long oldPosition = fc.position();
        fc.position(mdat.getOffset());
        mdat.getBox(fc);
        fc.position(oldPosition);
        mdat.setDataOffset(0);
        mdat.setContentSize(0);
        fos.flush();
        fos.getFD().sync();
    }

    public long writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, boolean writeLength) throws Exception {
        if (writeNewMdat) {
            mdat.setContentSize(0);
            mdat.getBox(fc);
            mdat.setDataOffset(dataOffset);
            dataOffset += 16;
            wroteSinceLastMdat += 16;
            writeNewMdat = false;
        }

        /*if (writeLength && !wasFirstVideoFrame) {
            wasFirstVideoFrame = true;
            byte[] buff = new byte[bufferInfo.size];
            byteBuf.position(bufferInfo.offset);
            byteBuf.limit(bufferInfo.offset + bufferInfo.size);
            byteBuf.get(buff);

            ByteBuffer nativeBuffer = ByteBuffer.allocateDirect(bufferInfo.size);
            nativeBuffer.position(4);
            int indexOfNal = -1;
            int totalLen = 0;
            for (int a = 0, N = buff.length - 3; a < N; a++) {
                if (buff[a] == 0 && buff[a + 1] == 0 && buff[a + 2] == 0 && buff[a + 3] == 1 || a == N - 1) {
                    if (indexOfNal != -1) {
                        int len = a - indexOfNal - 4;
                        nativeBuffer.put(buff, indexOfNal, len);
                        totalLen += len;
                    }
                    indexOfNal = a;
                }
            }
            nativeBuffer.position(0);
            nativeBuffer.putInt(totalLen);
            bufferInfo.offset = 0;
            bufferInfo.size = totalLen + 4;
            byteBuf = nativeBuffer;
        }*/

        mdat.setContentSize(mdat.getContentSize() + bufferInfo.size);
        wroteSinceLastMdat += bufferInfo.size;

        boolean flush = false;
        if (wroteSinceLastMdat >= 32 * 1024) {
            if (splitMdat) {
                flushCurrentMdat();
                writeNewMdat = true;
            }
            flush = true;
            wroteSinceLastMdat = 0;
        }

        currentMp4Movie.addSample(trackIndex, dataOffset, bufferInfo);

        if (writeLength) {
            sizeBuffer.position(0);
            sizeBuffer.putInt(bufferInfo.size - 4);
            sizeBuffer.position(0);
            fc.write(sizeBuffer);

            byteBuf.position(bufferInfo.offset + 4);
        } else {
            byteBuf.position(bufferInfo.offset);
        }
        byteBuf.limit(bufferInfo.offset + bufferInfo.size);
        fc.write(byteBuf);

        dataOffset += bufferInfo.size;

        if (flush) {
            fos.flush();
            fos.getFD().sync();
            return fc.position();
        }
        return 0;
    }

    public int addTrack(MediaFormat mediaFormat, boolean isAudio) {
        return currentMp4Movie.addTrack(mediaFormat, isAudio);
    }

    public void finishMovie() throws Exception {
        if (mdat.getContentSize() != 0) {
            flushCurrentMdat();
        }

        for (Track track : currentMp4Movie.getTracks()) {
            List<Sample> samples = track.getSamples();
            long[] sizes = new long[samples.size()];
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = samples.get(i).getSize();
            }
            track2SampleSizes.put(track, sizes);
        }

        Box moov = createMovieBox(currentMp4Movie);
        moov.getBox(fc);
        fos.flush();
        fos.getFD().sync();

        fc.close();
        fos.close();
    }

    protected FileTypeBox createFileTypeBox() {
        LinkedList<String> minorBrands = new LinkedList<>();
        minorBrands.add("isom");
        minorBrands.add("iso2");
        minorBrands.add("avc1");
        minorBrands.add("mp41");
        return new FileTypeBox("isom", 512, minorBrands);
    }

    private class InterleaveChunkMdat implements Box {
        private Container parent;
        private long contentSize = 1024 * 1024 * 1024;
        private long dataOffset = 0;

        public Container getParent() {
            return parent;
        }

        public long getOffset() {
            return dataOffset;
        }

        public void setDataOffset(long offset) {
            dataOffset = offset;
        }

        public void setParent(Container parent) {
            this.parent = parent;
        }

        public void setContentSize(long contentSize) {
            this.contentSize = contentSize;
        }

        public long getContentSize() {
            return contentSize;
        }

        public String getType() {
            return "mdat";
        }

        public long getSize() {
            return 16 + contentSize;
        }

        private boolean isSmallBox(long contentSize) {
            return (contentSize + 8) < 4294967296L;
        }

        @Override
        public void parse(DataSource dataSource, ByteBuffer header, long contentSize, BoxParser boxParser) {

        }

        public void getBox(WritableByteChannel writableByteChannel) throws IOException {
            ByteBuffer bb = ByteBuffer.allocate(16);
            long size = getSize();
            if (isSmallBox(size)) {
                IsoTypeWriter.writeUInt32(bb, size);
            } else {
                IsoTypeWriter.writeUInt32(bb, 1);
            }
            bb.put(IsoFile.fourCCtoBytes("mdat"));
            if (isSmallBox(size)) {
                bb.put(new byte[8]);
            } else {
                IsoTypeWriter.writeUInt64(bb, size);
            }
            bb.rewind();
            writableByteChannel.write(bb);
        }
    }

    public static long gcd(long a, long b) {
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }

    public long getTimescale(Mp4Movie mp4Movie) {
        long timescale = 0;
        if (!mp4Movie.getTracks().isEmpty()) {
            timescale = mp4Movie.getTracks().iterator().next().getTimeScale();
        }
        for (Track track : mp4Movie.getTracks()) {
            timescale = gcd(track.getTimeScale(), timescale);
        }
        return timescale;
    }

    protected MovieBox createMovieBox(Mp4Movie movie) {
        MovieBox movieBox = new MovieBox();
        MovieHeaderBox mvhd = new MovieHeaderBox();

        mvhd.setCreationTime(new Date());
        mvhd.setModificationTime(new Date());
        mvhd.setMatrix(Matrix.ROTATE_0);
        long movieTimeScale = getTimescale(movie);
        long duration = 0;

        for (Track track : movie.getTracks()) {
            track.prepare();
            long tracksDuration = track.getDuration() * movieTimeScale / track.getTimeScale();
            if (tracksDuration > duration) {
                duration = tracksDuration;
            }
        }

        mvhd.setDuration(duration);
        mvhd.setTimescale(movieTimeScale);
        mvhd.setNextTrackId(movie.getTracks().size() + 1);

        movieBox.addBox(mvhd);
        for (Track track : movie.getTracks()) {
            movieBox.addBox(createTrackBox(track, movie));
        }
        return movieBox;
    }

    protected TrackBox createTrackBox(Track track, Mp4Movie movie) {
        TrackBox trackBox = new TrackBox();
        TrackHeaderBox tkhd = new TrackHeaderBox();

        tkhd.setEnabled(true);
        tkhd.setInMovie(true);
        tkhd.setInPreview(true);
        if (track.isAudio()) {
            tkhd.setMatrix(Matrix.ROTATE_0);
        } else {
            tkhd.setMatrix(movie.getMatrix());
        }
        tkhd.setAlternateGroup(0);
        tkhd.setCreationTime(track.getCreationTime());
        tkhd.setDuration(track.getDuration() * getTimescale(movie) / track.getTimeScale());
        tkhd.setHeight(track.getHeight());
        tkhd.setWidth(track.getWidth());
        tkhd.setLayer(0);
        tkhd.setModificationTime(new Date());
        tkhd.setTrackId(track.getTrackId() + 1);
        tkhd.setVolume(track.getVolume());

        trackBox.addBox(tkhd);

        MediaBox mdia = new MediaBox();
        trackBox.addBox(mdia);
        MediaHeaderBox mdhd = new MediaHeaderBox();
        mdhd.setCreationTime(track.getCreationTime());
        mdhd.setDuration(track.getDuration());
        mdhd.setTimescale(track.getTimeScale());
        mdhd.setLanguage("eng");
        mdia.addBox(mdhd);
        HandlerBox hdlr = new HandlerBox();
        hdlr.setName(track.isAudio() ? "SoundHandle" : "VideoHandle");
        hdlr.setHandlerType(track.getHandler());

        mdia.addBox(hdlr);

        MediaInformationBox minf = new MediaInformationBox();
        minf.addBox(track.getMediaHeaderBox());

        DataInformationBox dinf = new DataInformationBox();
        DataReferenceBox dref = new DataReferenceBox();
        dinf.addBox(dref);
        DataEntryUrlBox url = new DataEntryUrlBox();
        url.setFlags(1);
        dref.addBox(url);
        minf.addBox(dinf);

        Box stbl = createStbl(track);
        minf.addBox(stbl);
        mdia.addBox(minf);

        return trackBox;
    }

    protected Box createStbl(Track track) {
        SampleTableBox stbl = new SampleTableBox();

        createStsd(track, stbl);
        createStts(track, stbl);
        createCtts(track, stbl);
        createStss(track, stbl);
        createStsc(track, stbl);
        createStsz(track, stbl);
        createStco(track, stbl);

        return stbl;
    }

    protected void createStsd(Track track, SampleTableBox stbl) {
        stbl.addBox(track.getSampleDescriptionBox());
    }

    protected void createCtts(Track track, SampleTableBox stbl) {
        int[] sampleCompositions = track.getSampleCompositions();
        if (sampleCompositions == null) {
            return;
        }
        CompositionTimeToSample.Entry lastEntry = null;
        List<CompositionTimeToSample.Entry> entries = new ArrayList<>();

        for (int a = 0; a < sampleCompositions.length; a++) {
            int offset = sampleCompositions[a];
            if (lastEntry != null && lastEntry.getOffset() == offset) {
                lastEntry.setCount(lastEntry.getCount() + 1);
            } else {
                lastEntry = new CompositionTimeToSample.Entry(1, offset);
                entries.add(lastEntry);
            }
        }
        CompositionTimeToSample ctts = new CompositionTimeToSample();
        ctts.setEntries(entries);
        stbl.addBox(ctts);
    }

    protected void createStts(Track track, SampleTableBox stbl) {
        TimeToSampleBox.Entry lastEntry = null;
        List<TimeToSampleBox.Entry> entries = new ArrayList<>();
        long[] deltas = track.getSampleDurations();

        for (int a = 0; a < deltas.length; a++) {
            long delta = deltas[a];
            if (lastEntry != null && lastEntry.getDelta() == delta) {
                lastEntry.setCount(lastEntry.getCount() + 1);
            } else {
                lastEntry = new TimeToSampleBox.Entry(1, delta);
                entries.add(lastEntry);
            }
        }
        TimeToSampleBox stts = new TimeToSampleBox();
        stts.setEntries(entries);
        stbl.addBox(stts);
    }

    protected void createStss(Track track, SampleTableBox stbl) {
        long[] syncSamples = track.getSyncSamples();
        if (syncSamples != null && syncSamples.length > 0) {
            SyncSampleBox stss = new SyncSampleBox();
            stss.setSampleNumber(syncSamples);
            stbl.addBox(stss);
        }
    }

    protected void createStsc(Track track, SampleTableBox stbl) {
        SampleToChunkBox stsc = new SampleToChunkBox();
        stsc.setEntries(new LinkedList<>());

        long lastOffset;
        int lastChunkNumber = 1;
        int lastSampleCount = 0;

        int previousWritedChunkCount = -1;

        int samplesCount = track.getSamples().size();
        for (int a = 0; a < samplesCount; a++) {
            Sample sample = track.getSamples().get(a);
            long offset = sample.getOffset();
            long size = sample.getSize();

            lastOffset = offset + size;
            lastSampleCount++;

            boolean write = false;
            if (a != samplesCount - 1) {
                Sample nextSample = track.getSamples().get(a + 1);
                if (lastOffset != nextSample.getOffset()) {
                    write = true;
                }
            } else {
                write = true;
            }
            if (write) {
                if (previousWritedChunkCount != lastSampleCount) {
                    stsc.getEntries().add(new SampleToChunkBox.Entry(lastChunkNumber, lastSampleCount, 1));
                    previousWritedChunkCount = lastSampleCount;
                }
                lastSampleCount = 0;
                lastChunkNumber++;
            }
        }
        stbl.addBox(stsc);
    }

    protected void createStsz(Track track, SampleTableBox stbl) {
        SampleSizeBox stsz = new SampleSizeBox();
        stsz.setSampleSizes(track2SampleSizes.get(track));
        stbl.addBox(stsz);
    }

    protected void createSidx(Track track, SampleTableBox stbl) {
        //SampleSizeBox stsz = new SampleSizeBox();
        //stsz.setSampleSizes(track2SampleSizes.get(track));
        //stbl.addBox(stsz);
    }

    protected void createStco(Track track, SampleTableBox stbl) {
        ArrayList<Long> chunksOffsets = new ArrayList<>();
        long lastOffset = -1;
        for (Sample sample : track.getSamples()) {
            long offset = sample.getOffset();
            if (lastOffset != -1 && lastOffset != offset) {
                lastOffset = -1;
            }
            if (lastOffset == -1) {
                chunksOffsets.add(offset);
            }
            lastOffset = offset + sample.getSize();
        }
        long[] chunkOffsetsLong = new long[chunksOffsets.size()];
        for (int a = 0; a < chunksOffsets.size(); a++) {
            chunkOffsetsLong[a] = chunksOffsets.get(a);
        }

        StaticChunkOffsetBox stco = new StaticChunkOffsetBox();
        stco.setChunkOffsets(chunkOffsetsLong);
        stbl.addBox(stco);
    }
}

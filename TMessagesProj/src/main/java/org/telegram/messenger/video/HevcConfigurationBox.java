package org.telegram.messenger.video;

import com.googlecode.mp4parser.AbstractBox;


import java.nio.ByteBuffer;
import java.util.List;

public class HevcConfigurationBox extends AbstractBox {
    public static final String TYPE = "hvcC";


    private HevcDecoderConfigurationRecord hevcDecoderConfigurationRecord;

    public HevcConfigurationBox() {
        super(TYPE);
        hevcDecoderConfigurationRecord = new HevcDecoderConfigurationRecord();
    }

    @Override
    protected long getContentSize() {
        return hevcDecoderConfigurationRecord.getSize();
    }

    @Override
    protected void getContent(ByteBuffer byteBuffer) {
        hevcDecoderConfigurationRecord.write(byteBuffer);
    }

    @Override
    protected void _parseDetails(ByteBuffer content) {
        hevcDecoderConfigurationRecord.parse(content);
    }

    public HevcDecoderConfigurationRecord getHevcDecoderConfigurationRecord() {
        return hevcDecoderConfigurationRecord;
    }

    public void setHevcDecoderConfigurationRecord(HevcDecoderConfigurationRecord hevcDecoderConfigurationRecord) {
        this.hevcDecoderConfigurationRecord = hevcDecoderConfigurationRecord;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HevcConfigurationBox that = (HevcConfigurationBox) o;

        if (hevcDecoderConfigurationRecord != null ? !hevcDecoderConfigurationRecord.equals(that.hevcDecoderConfigurationRecord) : that.hevcDecoderConfigurationRecord != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return hevcDecoderConfigurationRecord != null ? hevcDecoderConfigurationRecord.hashCode() : 0;
    }


    public int getConfigurationVersion() {
        return hevcDecoderConfigurationRecord.configurationVersion;
    }

    public int getGeneral_profile_space() {
        return hevcDecoderConfigurationRecord.general_profile_space;
    }

    public boolean isGeneral_tier_flag() {
        return hevcDecoderConfigurationRecord.general_tier_flag;
    }


    public int getGeneral_profile_idc() {
        return hevcDecoderConfigurationRecord.general_profile_idc;
    }

    public long getGeneral_profile_compatibility_flags() {
        return hevcDecoderConfigurationRecord.general_profile_compatibility_flags;
    }

    public long getGeneral_constraint_indicator_flags() {
        return hevcDecoderConfigurationRecord.general_constraint_indicator_flags;
    }

    public int getGeneral_level_idc() {
        return hevcDecoderConfigurationRecord.general_level_idc;
    }

    public int getMin_spatial_segmentation_idc() {
        return hevcDecoderConfigurationRecord.min_spatial_segmentation_idc;
    }

    public int getParallelismType() {
        return hevcDecoderConfigurationRecord.parallelismType;
    }

    public int getChromaFormat() {
        return hevcDecoderConfigurationRecord.chromaFormat;
    }

    public int getBitDepthLumaMinus8() {
        return hevcDecoderConfigurationRecord.bitDepthLumaMinus8;
    }

    public int getBitDepthChromaMinus8() {
        return hevcDecoderConfigurationRecord.bitDepthChromaMinus8;
    }

    public int getAvgFrameRate() {
        return hevcDecoderConfigurationRecord.avgFrameRate;
    }

    public int getNumTemporalLayers() {
        return hevcDecoderConfigurationRecord.numTemporalLayers;
    }

    public int getLengthSizeMinusOne() {
        return hevcDecoderConfigurationRecord.lengthSizeMinusOne;
    }

    public boolean isTemporalIdNested() {
        return hevcDecoderConfigurationRecord.temporalIdNested;
    }

    public int getConstantFrameRate() {
        return hevcDecoderConfigurationRecord.constantFrameRate;
    }

    public List<HevcDecoderConfigurationRecord.Array> getArrays() {
        return hevcDecoderConfigurationRecord.arrays;
    }
}
/*
 * 09/26/08     throw exception on subbband alloc error: Christopher G. Jennings (cjennings@acm.org)
 *
 * 11/19/04        1.0 moved to LGPL.
 *
 * 12/12/99        Initial version. Adapted from javalayer.java
 *                and Subband*.java. mdm@techie.com
 *
 * 02/28/99        Initial version : javalayer.java by E.B
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package javazoom.jl.decoder;

/**
 * Implements decoding of MPEG Audio Layer I frames.
 */
class LayerIDecoder implements FrameDecoder {

    protected Bitstream stream;
    protected Header header;
    protected SynthesisFilter filter1, filter2;
    protected Obuffer buffer;
    protected int whichChannels;
    protected int mode;

    protected int num_subbands;
    protected Subband[] subbands;
    protected Crc16 crc; // new Crc16[1] to enable CRC checking.

    public LayerIDecoder() {
        crc = new Crc16();
    }

    public void create(Bitstream stream, Header header,
                       SynthesisFilter filterA, SynthesisFilter filterB,
                       Obuffer buffer, int whichCh) {
        this.stream = stream;
        this.header = header;
        filter1 = filterA;
        filter2 = filterB;
        this.buffer = buffer;
        whichChannels = whichCh;
    }

    @Override
    public void decodeFrame() throws DecoderException {

        num_subbands = header.numberOfSubbands();
        subbands = new Subband[32];
        mode = header.mode();

        createSubbands();

        readAllocation();
        readScaleFactorSelection();

        if ((crc != null) || header.checksumOk()) {
            readScaleFactors();

            readSampleData();
        }
    }

    protected void createSubbands() {
        int i;
        if (mode == Header.SINGLE_CHANNEL)
            for (i = 0; i < num_subbands; ++i)
                subbands[i] = new SubbandLayer1(i);
        else if (mode == Header.JOINT_STEREO) {
            for (i = 0; i < header.intensityStereoBound(); ++i)
                subbands[i] = new SubbandLayer1Stereo(i);
            for (; i < num_subbands; ++i)
                subbands[i] = new SubbandLayer1IntensityStereo(i);
        } else {
            for (i = 0; i < num_subbands; ++i)
                subbands[i] = new SubbandLayer1Stereo(i);
        }
    }

    protected void readAllocation() throws DecoderException {
        // start to read audio data:
        for (int i = 0; i < num_subbands; ++i)
            subbands[i].readAllocation(stream, header, crc);
    }

    protected void readScaleFactorSelection() {
        // scale factor selection not present for layer I.
    }

    protected void readScaleFactors() {
        for (int i = 0; i < num_subbands; ++i)
            subbands[i].readScaleFactor(stream, header);
    }

    protected void readSampleData() {
        boolean readReady = false;
        boolean writeReady = false;
        int mode = header.mode();
        int i;
        do {
            for (i = 0; i < num_subbands; ++i)
                readReady = subbands[i].readSampleData(stream);
            do {
                for (i = 0; i < num_subbands; ++i)
                    writeReady = subbands[i].put_next_sample(whichChannels, filter1, filter2);

                filter1.calculate_pcm_samples(buffer);
                if ((whichChannels == OutputChannels.BOTH_CHANNELS) && (mode != Header.SINGLE_CHANNEL))
                    filter2.calculate_pcm_samples(buffer);
            } while (!writeReady);
        } while (!readReady);

    }

    /**
     * Abstract base class for subband classes of layer I and II
     */
    static abstract class Subband {
        /*
         * ScaleFactors for layer I and II, Annex 3-B.1 in ISO/IEC DIS 11172:
         *
         * Changes from version 1.1 to 1.2:
         *    - array size increased by one, although a scalefactor with index 63
         *      is illegal (to prevent segmentation faults)
         */
        public static final float[] scaleFactors = {
                2.00000000000000f, 1.58740105196820f, 1.25992104989487f, 1.00000000000000f,
                0.79370052598410f, 0.62996052494744f, 0.50000000000000f, 0.39685026299205f,
                0.31498026247372f, 0.25000000000000f, 0.19842513149602f, 0.15749013123686f,
                0.12500000000000f, 0.09921256574801f, 0.07874506561843f, 0.06250000000000f,
                0.04960628287401f, 0.03937253280921f, 0.03125000000000f, 0.02480314143700f,
                0.01968626640461f, 0.01562500000000f, 0.01240157071850f, 0.00984313320230f,
                0.00781250000000f, 0.00620078535925f, 0.00492156660115f, 0.00390625000000f,
                0.00310039267963f, 0.00246078330058f, 0.00195312500000f, 0.00155019633981f,
                0.00123039165029f, 0.00097656250000f, 0.00077509816991f, 0.00061519582514f,
                0.00048828125000f, 0.00038754908495f, 0.00030759791257f, 0.00024414062500f,
                0.00019377454248f, 0.00015379895629f, 0.00012207031250f, 0.00009688727124f,
                0.00007689947814f, 0.00006103515625f, 0.00004844363562f, 0.00003844973907f,
                0.00003051757813f, 0.00002422181781f, 0.00001922486954f, 0.00001525878906f,
                0.00001211090890f, 0.00000961243477f, 0.00000762939453f, 0.00000605545445f,
                0.00000480621738f, 0.00000381469727f, 0.00000302772723f, 0.00000240310869f,
                0.00000190734863f, 0.00000151386361f, 0.00000120155435f, 0.00000000000000f // illegal scaleFactor
        };

        public abstract void readAllocation(Bitstream stream, Header header, Crc16 crc) throws DecoderException;

        public abstract void readScaleFactor(Bitstream stream, Header header);

        public abstract boolean readSampleData(Bitstream stream);

        public abstract boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2);
    }

    /**
     * Class for layer I subbands in single channel mode.
     * Used for single channel mode
     * and in derived class for intensity stereo mode
     */
    static class SubbandLayer1 extends Subband {

        // Factors and offsets for sample requantization
        public static final float[] tableFactor = {
                0.0f, (1.0f / 2.0f) * (4.0f / 3.0f), (1.0f / 4.0f) * (8.0f / 7.0f), (1.0f / 8.0f) * (16.0f / 15.0f),
                (1.0f / 16.0f) * (32.0f / 31.0f), (1.0f / 32.0f) * (64.0f / 63.0f), (1.0f / 64.0f) * (128.0f / 127.0f),
                (1.0f / 128.0f) * (256.0f / 255.0f), (1.0f / 256.0f) * (512.0f / 511.0f),
                (1.0f / 512.0f) * (1024.0f / 1023.0f), (1.0f / 1024.0f) * (2048.0f / 2047.0f),
                (1.0f / 2048.0f) * (4096.0f / 4095.0f), (1.0f / 4096.0f) * (8192.0f / 8191.0f),
                (1.0f / 8192.0f) * (16384.0f / 16383.0f), (1.0f / 16384.0f) * (32768.0f / 32767.0f)
        };

        public static final float[] tableOffset = {
                0.0f, ((1.0f / 2.0f) - 1.0f) * (4.0f / 3.0f), ((1.0f / 4.0f) - 1.0f) * (8.0f / 7.0f), ((1.0f / 8.0f) - 1.0f) * (16.0f / 15.0f),
                ((1.0f / 16.0f) - 1.0f) * (32.0f / 31.0f), ((1.0f / 32.0f) - 1.0f) * (64.0f / 63.0f), ((1.0f / 64.0f) - 1.0f) * (128.0f / 127.0f),
                ((1.0f / 128.0f) - 1.0f) * (256.0f / 255.0f), ((1.0f / 256.0f) - 1.0f) * (512.0f / 511.0f),
                ((1.0f / 512.0f) - 1.0f) * (1024.0f / 1023.0f), ((1.0f / 1024.0f) - 1.0f) * (2048.0f / 2047.0f),
                ((1.0f / 2048.0f) - 1.0f) * (4096.0f / 4095.0f), ((1.0f / 4096.0f) - 1.0f) * (8192.0f / 8191.0f),
                ((1.0f / 8192.0f) - 1.0f) * (16384.0f / 16383.0f), ((1.0f / 16384.0f) - 1.0f) * (32768.0f / 32767.0f)
        };

        protected int subbandNumber;
        protected int sampleNumber;
        protected int allocation;
        protected float scaleFactor;
        protected int sampleLength;
        protected float sample;
        protected float factor, offset;

        /**
         * Constructor.
         */
        public SubbandLayer1(int subbandNumber) {
            this.subbandNumber = subbandNumber;
            sampleNumber = 0;
        }

        /**
         *
         */
        @Override
        public void readAllocation(Bitstream stream, Header header, Crc16 crc) throws DecoderException {
            if ((allocation = stream.getBits(4)) == 15) {
                // CGJ: catch this condition and throw appropriate exception
                throw new DecoderException(DecoderErrors.ILLEGAL_SUBBAND_ALLOCATION, null);
                // MPEG-stream is corrupted!
            }

            if (crc != null) crc.addBits(allocation, 4);
            if (allocation != 0) {
                sampleLength = allocation + 1;
                factor = tableFactor[allocation];
                offset = tableOffset[allocation];
            }
        }

        /**
         *
         */
        @Override
        public void readScaleFactor(Bitstream stream, Header header) {
            if (allocation != 0) scaleFactor = scaleFactors[stream.getBits(6)];
        }

        /**
         *
         */
        @Override
        public boolean readSampleData(Bitstream stream) {
            if (allocation != 0) {
                sample = (stream.getBits(sampleLength));
            }
            if (++sampleNumber == 12) {
                sampleNumber = 0;
                return true;
            }
            return false;
        }

        /**
         *
         */
        @Override
        public boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2) {
            if ((allocation != 0) && (channels != OutputChannels.RIGHT_CHANNEL)) {
                float scaled_sample = (sample * factor + offset) * scaleFactor;
                filter1.inputSample(scaled_sample, subbandNumber);
            }
            return true;
        }
    }

    /**
     * Class for layer I subbands in joint stereo mode.
     */
    static class SubbandLayer1IntensityStereo extends SubbandLayer1 {
        protected float channel2ScaleFactor;

        /**
         * Constructor
         */
        public SubbandLayer1IntensityStereo(int subbandNumber) {
            super(subbandNumber);
        }

        /**
         *
         */
        @Override
        public void readAllocation(Bitstream stream, Header header, Crc16 crc) throws DecoderException {
            super.readAllocation(stream, header, crc);
        }

        /**
         *
         */
        @Override
        public void readScaleFactor(Bitstream stream, Header header) {
            if (allocation != 0) {
                scaleFactor = scaleFactors[stream.getBits(6)];
                channel2ScaleFactor = scaleFactors[stream.getBits(6)];
            }
        }

        /**
         *
         */
        @Override
        public boolean readSampleData(Bitstream stream) {
            return super.readSampleData(stream);
        }

        /**
         *
         */
        @Override
        public boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2) {
            if (allocation != 0) {
                sample = sample * factor + offset; // requantization
                if (channels == OutputChannels.BOTH_CHANNELS) {
                    float sample1 = sample * scaleFactor,
                            sample2 = sample * channel2ScaleFactor;
                    filter1.inputSample(sample1, subbandNumber);
                    filter2.inputSample(sample2, subbandNumber);
                } else if (channels == OutputChannels.LEFT_CHANNEL) {
                    float sample1 = sample * scaleFactor;
                    filter1.inputSample(sample1, subbandNumber);
                } else {
                    float sample2 = sample * channel2ScaleFactor;
                    filter1.inputSample(sample2, subbandNumber);
                }
            }
            return true;
        }
    }

    /**
     * Class for layer I subbands in stereo mode.
     */
    static class SubbandLayer1Stereo extends SubbandLayer1 {

        protected int channel2Allocation;
        protected float channel2ScaleFactor;
        protected int channel2SampleLength;
        protected float channel2Sample;
        protected float channel2Factor, channel2Offset;

        /**
         * Constructor
         */
        public SubbandLayer1Stereo(int subbandNumber) {
            super(subbandNumber);
        }

        /**
         *
         */
        @Override
        public void readAllocation(Bitstream stream, Header header, Crc16 crc) throws DecoderException {
            allocation = stream.getBits(4);
            channel2Allocation = stream.getBits(4);
            if (crc != null) {
                crc.addBits(allocation, 4);
                crc.addBits(channel2Allocation, 4);
            }
            if (allocation != 0) {
                sampleLength = allocation + 1;
                factor = tableFactor[allocation];
                offset = tableOffset[allocation];
            }
            if (channel2Allocation != 0) {
                channel2SampleLength = channel2Allocation + 1;
                channel2Factor = tableFactor[channel2Allocation];
                channel2Offset = tableOffset[channel2Allocation];
            }
        }

        /**
         *
         */
        @Override
        public void readScaleFactor(Bitstream stream, Header header) {
            if (allocation != 0) scaleFactor = scaleFactors[stream.getBits(6)];
            if (channel2Allocation != 0) channel2ScaleFactor = scaleFactors[stream.getBits(6)];
        }

        /**
         *
         */
        @Override
        public boolean readSampleData(Bitstream stream) {
            boolean returnValue = super.readSampleData(stream);
            if (channel2Allocation != 0) {
                channel2Sample = (stream.getBits(channel2SampleLength));
            }
            return (returnValue);
        }

        /**
         *
         */
        @Override
        public boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2) {
            super.put_next_sample(channels, filter1, filter2);
            if ((channel2Allocation != 0) && (channels != OutputChannels.LEFT_CHANNEL)) {
                float sample2 = (channel2Sample * channel2Factor + channel2Offset) *
                        channel2ScaleFactor;
                if (channels == OutputChannels.BOTH_CHANNELS)
                    filter2.inputSample(sample2, subbandNumber);
                else
                    filter1.inputSample(sample2, subbandNumber);
            }
            return true;
        }
    }
}

/*
 * 11/19/04 : 1.0 moved to LGPL.
 *            VBRI header support added, E.B javalayer@javazoom.net
 *
 * 12/04/03 : VBR (XING) header support added, E.B javalayer@javazoom.net
 *
 * 02/13/99 : Java Conversion by JavaZOOM , E.B javalayer@javazoom.net
 *
 * Declarations for MPEG header class
 * A few layer III, MPEG-2 LSF, and seeking modifications made by Jeff Tsay.
 * Last modified : 04/19/97
 *
 *  @(#) header.h 1.7, last edit: 6/15/94 16:55:33
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
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
 * Class for extracting information from an MPEG audio frame header.
 * <p>
 * Optimized version with improved performance and better API design.
 *
 * @author MDM (original)
 * @author E.B (VBR support)
 * @author TamKungZ
 */
public final class Header {

    // Constants

    // MPEG version constants
    public static final int MPEG2_LSF = 0;
    public static final int MPEG1 = 1;
    public static final int MPEG25_LSF = 2; // SZD

    // Channel mode constants
    public static final int STEREO = 0;
    public static final int JOINT_STEREO = 1;
    public static final int DUAL_CHANNEL = 2;
    public static final int SINGLE_CHANNEL = 3;

    // Sample frequency constants
    public static final int FOURTYFOUR_POINT_ONE = 0;
    public static final int FOURTYEIGHT = 1;
    public static final int THIRTYTWO = 2;

    /**
     * Sample frequencies table [version][frequency_index].
     * Made static final for better performance.
     */
    public static final int[][] frequencies = {
            {22050, 24000, 16000, 1},
            {44100, 48000, 32000, 1},
            {11025, 12000, 8000, 1} // SZD: MPEG25
    };

    /**
     * Bitrates table [version][layer][bitrate_index].
     * Made static final for better performance.
     */
    public static final int[][][] bitrates = {{
            // MPEG2 LSF
            {0, 32000, 48000, 56000, 64000, 80000, 96000,
                    112000, 128000, 144000, 160000, 176000, 192000, 224000, 256000, 0},
            {0, 8000, 16000, 24000, 32000, 40000, 48000,
                    56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 0},
            {0, 8000, 16000, 24000, 32000, 40000, 48000,
                    56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 0}
    }, {
            // MPEG1
            {0, 32000, 64000, 96000, 128000, 160000, 192000,
                    224000, 256000, 288000, 320000, 352000, 384000, 416000, 448000, 0},
            {0, 32000, 48000, 56000, 64000, 80000, 96000,
                    112000, 128000, 160000, 192000, 224000, 256000, 320000, 384000, 0},
            {0, 32000, 40000, 48000, 56000, 64000, 80000,
                    96000, 112000, 128000, 160000, 192000, 224000, 256000, 320000, 0}
    }, {
            // MPEG2.5 LSF
            {0, 32000, 48000, 56000, 64000, 80000, 96000,
                    112000, 128000, 144000, 160000, 176000, 192000, 224000, 256000, 0},
            {0, 8000, 16000, 24000, 32000, 40000, 48000,
                    56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 0},
            {0, 8000, 16000, 24000, 32000, 40000, 48000,
                    56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 0}},
    };

    /**
     * VBR time per frame constant [layer].
     */
    private static final double[] h_vbr_time_per_frame = {-1, 384, 1152, 1152};

    /**
     * Milliseconds per frame table [layer][frequency].
     * Pre-computed for performance.
     */
    private static final float[][] msPerFrames = {
            {8.707483f, 8.0f, 12.0f},
            {26.12245f, 24.0f, 36.0f},
            {26.12245f, 24.0f, 36.0f}
    };

    // Instance Fields

    // Header components
    private int hLayer;
    private int hVersion;
    private int hBitrateIndex;
    private int h_protection_bit;
    private int h_sample_frequency;
    private int h_mode;
    private int h_mode_extension;
    private int h_padding_bit;
    private int hNumberOfSubbands;
    private int hIntensityStereoBound;

    // Flags
    private boolean h_copyright;
    private boolean h_original;

    // VBR information
    private boolean h_vbr;
    private int h_vbr_frames;
    private int h_vbr_scale = -1;
    private int h_vbr_bytes;
    private byte[] h_vbr_toc;

    // Frame information
    public short checksum;
    public int framesize;
    public int nSlots;

    // Synchronization
    private byte syncmode = Bitstream.INITIAL_SYNC;
    private Crc16 crc;
    private int _headerstring = -1;

    /**
     * Create a new Header instance.
     */
    Header() {
    }

    /**
     * Read a 32-bit header from the bitstream.
     *
     * @param stream the bitstream to read from
     * @param crcp array to store CRC calculator
     * @throws BitstreamException on read error or invalid header
     */
    void read_header(Bitstream stream, Crc16[] crcp) throws BitstreamException {
        int headerString;
        int channelBitrate;
        boolean sync = false;
        do {
            headerString = stream.syncHeader(syncmode);
            _headerstring = headerString;

            if (syncmode == Bitstream.INITIAL_SYNC) {
                // Parse version
                hVersion = ((headerString >>> 19) & 1);
                if (((headerString >>> 20) & 1) == 0) { // MPEG2.5 detection
                    if (hVersion == MPEG2_LSF) {
                        hVersion = MPEG25_LSF;
                    } else {
                        throw stream.newBitstreamException(Bitstream.UNKNOWN_ERROR);
                    }
                }

                // Parse sample frequency
                h_sample_frequency = ((headerString >>> 10) & 3);
                if (h_sample_frequency == 3) {
                    throw stream.newBitstreamException(Bitstream.UNKNOWN_ERROR);
                }
            }

            // Parse header fields
            hLayer = 4 - (headerString >>> 17) & 3;
            h_protection_bit = (headerString >>> 16) & 1;
            hBitrateIndex = (headerString >>> 12) & 0xF;
            h_padding_bit = (headerString >>> 9) & 1;
            h_mode = (headerString >>> 6) & 3;
            h_mode_extension = (headerString >>> 4) & 3;

            // Calculate intensity stereo bound
            if (h_mode == JOINT_STEREO) {
                hIntensityStereoBound = (h_mode_extension << 2) + 4;
            } else {
                hIntensityStereoBound = 0; // should never be used
            }

            // Parse copyright and original bits
            h_copyright = ((headerString >>> 3) & 1) == 1;
            h_original = ((headerString >>> 2) & 1) == 1;

            // Calculate number of subbands
            calculateNumberOfSubbands(channelBitrate = calculateChannelBitrate());

            if (hIntensityStereoBound > hNumberOfSubbands) {
                hIntensityStereoBound = hNumberOfSubbands;
            }

            // Calculate framesize and read frame data
            calculateFrameSize();
            int framesizeloaded = stream.readFrameData(framesize);

            if ((framesize >= 0) && (framesizeloaded != framesize)) {
                // Data loaded does not match expected framesize (might be ID3v1 TAG)
                throw stream.newBitstreamException(Bitstream.INVALIDFRAME);
            }

            // Check sync at current position
            if (stream.isSyncCurrentPosition(syncmode)) {
                if (syncmode == Bitstream.INITIAL_SYNC) {
                    syncmode = Bitstream.STRICT_SYNC;
                    stream.setSyncWord(headerString & 0xFFF80CC0);
                }
                sync = true;
            } else {
                stream.unreadFrame();
            }
        } while (!sync);

        stream.parseFrame();

        // Handle CRC if present
        if (h_protection_bit == 0) {
            checksum = (short) stream.getBits(16);
            if (crc == null) {
                crc = new Crc16();
            }
            crc.addBits(headerString, 16);
            crcp[0] = crc;
        } else {
            crcp[0] = null;
        }
    }

    /**
     * Calculate channel bitrate based on mode and bitrate index.
     *
     * @return channel bitrate
     */
    private int calculateChannelBitrate() {
        int channelBitrate = hBitrateIndex;

        if (h_mode != SINGLE_CHANNEL) {
            if (channelBitrate == 4) {
                channelBitrate = 1;
            } else {
                channelBitrate -= 4;
            }
        }

        return channelBitrate;
    }

    /**
     * Calculate number of subbands based on layer and channel bitrate.
     *
     * @param channelBitrate the channel bitrate
     */
    private void calculateNumberOfSubbands(int channelBitrate) {
        if (hLayer == 1) {
            hNumberOfSubbands = 32;
        } else {
            if ((channelBitrate == 1) || (channelBitrate == 2)) {
                hNumberOfSubbands = (h_sample_frequency == THIRTYTWO) ? 12 : 8;
            } else if ((h_sample_frequency == FOURTYEIGHT) ||
                    ((channelBitrate >= 3) && (channelBitrate <= 5))) {
                hNumberOfSubbands = 27;
            } else {
                hNumberOfSubbands = 30;
            }
        }
    }

    // VBR Parsing

    /**
     * Parse frame to extract optional VBR header (Xing or VBRI).
     *
     * @param firstFrame the first frame data
     * @throws BitstreamException on parse error
     */
    void parseVBR(byte[] firstFrame) throws BitstreamException {
        // Try Xing header first
        if (parseXingVBR(firstFrame)) {
            return;
        }

        // Try VBRI header
        parseVBRIHeader(firstFrame);
    }

    /**
     * Parse Xing VBR header.
     *
     * @param firstFrame the first frame data
     * @return true if Xing header found
     */
    private boolean parseXingVBR(byte[] firstFrame) {
        byte[] tmp = new byte[4];
        int offset;

        // Compute "Xing" offset depending on MPEG version and channels
        if (hVersion == MPEG1) {
            offset = (h_mode == SINGLE_CHANNEL) ? 17 : 32;
        } else {
            offset = (h_mode == SINGLE_CHANNEL) ? 9 : 17;
        }

        // Check for "Xing" or "Info" markers
        try {
            System.arraycopy(firstFrame, offset, tmp, 0, 4);

            if ("Xing".equals(new String(tmp)) || "Info".equals(new String(tmp))) {
                h_vbr = true;
                h_vbr_frames = -1;
                h_vbr_bytes = -1;
                h_vbr_scale = -1;
                h_vbr_toc = new byte[100];

                int flags = 0;
                for (int i = 0; i < 4; i++) {
                    flags <<= 8;
                    flags += (firstFrame[offset + 4 + i] & 0xFF);
                }

                offset += 8;

                // Parse frames
                if ((flags & 0x0001) != 0) {
                    for (int i = 0; i < 4; i++) {
                        h_vbr_frames <<= 8;
                        h_vbr_frames += (firstFrame[offset + i] & 0xFF);
                    }
                    offset += 4;
                }

                // Parse bytes
                if ((flags & 0x0002) != 0) {
                    for (int i = 0; i < 4; i++) {
                        h_vbr_bytes <<= 8;
                        h_vbr_bytes += (firstFrame[offset + i] & 0xFF);
                    }
                    offset += 4;
                }

                // Parse TOC
                if ((flags & 0x0004) != 0) {
                    System.arraycopy(firstFrame, offset, h_vbr_toc, 0, 100);
                    offset += 100;
                }

                // Parse scale
                if ((flags & 0x0008) != 0) {
                    for (int i = 0; i < 4; i++) {
                        h_vbr_scale <<= 8;
                        h_vbr_scale += (firstFrame[offset + i] & 0xFF);
                    }
                }

                return true;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // No Xing header
        }

        return false;
    }

    /**
     * Parse VBRI VBR header.
     *
     * @param firstFrame the first frame data
     */
    private void parseVBRIHeader(byte[] firstFrame) {
        byte[] tmp = new byte[4];
        int offset = 36;

        try {
            System.arraycopy(firstFrame, offset, tmp, 0, 4);

            if ("VBRI".equals(new String(tmp))) {
                h_vbr = true;
                h_vbr_frames = -1;
                h_vbr_bytes = -1;
                h_vbr_scale = -1;

                // VBRI version ID
                offset += 6;

                // Delay
                offset += 2;

                // Quality indicator
                offset += 2;

                // Parse bytes
                for (int i = 0; i < 4; i++) {
                    h_vbr_bytes <<= 8;
                    h_vbr_bytes += (firstFrame[offset + i] & 0xFF);
                }
                offset += 4;

                // Parse frames
                for (int i = 0; i < 4; i++) {
                    h_vbr_frames <<= 8;
                    h_vbr_frames += (firstFrame[offset + i] & 0xFF);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // No VBRI header
        }
    }

    // Frame Size Calculation

    /**
     * Calculate frame size in bytes excluding header size.
     *
     * @return frame size in bytes
     */
    public int calculateFrameSize() {
        if (hLayer == 1) {
            // Layer I
            framesize = (12 * bitrates[hVersion][0][hBitrateIndex]) / frequencies[hVersion][h_sample_frequency];
            if (h_padding_bit != 0) framesize++;
            framesize <<= 2; // one slot is 4 bytes long
            nSlots = 0;
        } else {
            // Layer II and III
            framesize = (144 * bitrates[hVersion][hLayer - 1][hBitrateIndex]) / frequencies[hVersion][h_sample_frequency];

            if (hVersion == MPEG2_LSF || hVersion == MPEG25_LSF) {
                framesize >>= 1; // Half framesize for MPEG2 LSF
            }

            if (h_padding_bit != 0) framesize++;

            // Layer III slots calculation
            if (hLayer == 3) {
                if (hVersion == MPEG1) {
                    nSlots = framesize -
                            ((h_mode == SINGLE_CHANNEL) ? 17 : 32) - // side info size
                            ((h_protection_bit != 0) ? 0 : 2) -      // CRC size
                            4;                                       // header size
                } else {
                    // MPEG-2 LSF, MPEG-2.5 LSF
                    nSlots = framesize -
                            ((h_mode == SINGLE_CHANNEL) ? 9 : 17) -  // side info size
                            ((h_protection_bit != 0) ? 0 : 2) -      // CRC size
                            4;                                      // header size
                }
            } else {
                nSlots = 0;
            }
        }

        framesize -= 4; // subtract header size
        return framesize;
    }

    // Getter Methods

    /**
     * Returns MPEG version.
     *
     * @return MPEG1, MPEG2_LSF, or MPEG25_LSF
     */
    public int version() {
        return hVersion;
    }

    /**
     * Returns layer (1, 2, or 3).
     *
     * @return layer number
     */
    public int layer() {
        return hLayer;
    }

    /**
     * Returns bitrate index.
     *
     * @return bitrate index
     */
    public int bitrateIndex() {
        return hBitrateIndex;
    }

    /**
     * Returns sample frequency index.
     *
     * @return sample frequency index
     */
    public int sampleFrequency() {
        return h_sample_frequency;
    }

    /**
     * Returns sample frequency in Hz.
     *
     * @return frequency in Hz
     */
    public int frequency() {
        return frequencies[hVersion][h_sample_frequency];
    }

    /**
     * Returns channel mode.
     *
     * @return STEREO, JOINT_STEREO, DUAL_CHANNEL, or SINGLE_CHANNEL
     */
    public int mode() {
        return h_mode;
    }

    /**
     * Returns whether frame has CRC checksum.
     *
     * @return true if CRC present
     */
    public boolean checksums() {
        return h_protection_bit == 0;
    }

    /**
     * Returns copyright flag.
     *
     * @return true if copyrighted
     */
    public boolean copyright() {
        return h_copyright;
    }

    /**
     * Returns original flag.
     *
     * @return true if original
     */
    public boolean original() {
        return h_original;
    }

    /**
     * Returns VBR flag.
     *
     * @return true if VBR header found
     */
    public boolean vbr() {
        return h_vbr;
    }

    /**
     * Returns VBR scale (-1 if not available).
     *
     * @return VBR scale
     */
    public int vbrScale() {
        return h_vbr_scale;
    }

    /**
     * Returns VBR table of contents (null if not available).
     *
     * @return VBR TOC array
     */
    public byte[] vbrToc() {
        return h_vbr_toc;
    }

    /**
     * Check if computed checksum matches stream checksum.
     *
     * @return true if checksums match
     */
    public boolean checksumOk() {
        return (checksum == crc.checksum());
    }

    /**
     * Returns padding flag.
     *
     * @return true if padding bit set
     */
    public boolean padding() {
        return h_padding_bit != 0;
    }

    /**
     * Returns number of slots.
     *
     * @return slot count
     */
    public int slots() {
        return nSlots;
    }

    /**
     * Returns mode extension.
     *
     * @return mode extension value
     */
    public int modeExtension() {
        return h_mode_extension;
    }

    /**
     * Returns number of subbands.
     *
     * @return subband count
     */
    public int numberOfSubbands() {
        return hNumberOfSubbands;
    }

    /**
     * Returns intensity stereo bound.
     *
     * @return intensity stereo bound
     */
    public int intensityStereoBound() {
        return hIntensityStereoBound;
    }

    /**
     * Returns synchronized header string.
     *
     * @return header bits
     */
    public int getSyncHeader() { // E.B
        return _headerstring;
    }

    // Calculated Values

    /**
     * Returns bitrate in bits per second.
     * For VBR, returns average bitrate.
     *
     * @return bitrate in bps
     */
    public int bitrate() {
        if (h_vbr && h_vbr_frames > 0) {
            return ((int) ((h_vbr_bytes * 8) / (msPerFrame() * h_vbr_frames))) * 1000;
        } else {
            return bitrates[hVersion][hLayer - 1][hBitrateIndex];
        }
    }

    /**
     * Returns instant bitrate (actual frame bitrate, not VBR average).
     *
     * @return bitrate in bps
     */
    public int bitrateInstant() {
        return bitrates[hVersion][hLayer - 1][hBitrateIndex];
    }

    /**
     * Returns milliseconds per frame.
     *
     * @return ms per frame
     */
    public float msPerFrame() {
        if (h_vbr) {
            double tpf = h_vbr_time_per_frame[hLayer] / frequency();
            if ((hVersion == MPEG2_LSF) || (hVersion == MPEG25_LSF)) {
                tpf /= 2;
            }
            return (float) (tpf * 1000);
        } else {
            return msPerFrames[hLayer - 1][h_sample_frequency];
        }
    }

    /**
     * Returns maximum number of frames in stream.
     *
     * @param streamSize stream size in bytes
     * @return frame count
     */
    public int maxNumberOfFrames(int streamSize) {
        if (h_vbr) {
            return h_vbr_frames;
        } else {
            int frameSizeWithHeader = framesize + 4 - h_padding_bit;
            return (frameSizeWithHeader == 0) ? 0 : (streamSize / frameSizeWithHeader);
        }
    }

    /**
     * Returns minimum number of frames in stream.
     *
     * @param streamSize stream size in bytes
     * @return frame count
     */
    public int minNumberOfFrames(int streamSize) {
        if (h_vbr) {
            return h_vbr_frames;
        } else {
            int frameSizeWithPadding = framesize + 5 - h_padding_bit;
            return (frameSizeWithPadding == 0) ? 0 : (streamSize / frameSizeWithPadding);
        }
    }

    /**
     * Returns total duration in milliseconds.
     *
     * @param streamSize stream size in bytes
     * @return duration in ms
     */
    public float totalMs(int streamSize) {
        return maxNumberOfFrames(streamSize) * msPerFrame();
    }

    // String Representations

    /**
     * Returns layer as string.
     *
     * @return "I", "II", or "III"
     */
    public String layerString() {
        return switch (hLayer) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "Unknown";
        };
    }

    /**
     * Returns bitrate as formatted string.
     *
     * @return bitrate string (e.g., "128 kb/s")
     */
    public String bitrateString() {
        if (h_vbr) {
            return bitrate() / 1000 + " kbit/s";
        } else {
            // Use lookup table for standard bitrates
            int br = bitrateInstant();
            return (br == 0) ? "free format" : (br / 1000) + " kbit/s";
        }
    }

    /**
     * Returns sample frequency as formatted string.
     *
     * @return frequency string (e.g., "44.1 kHz")
     */
    public String sampleFrequencyString() {
        int freq = frequency();

        if (freq >= 1000) {
            return "%.1f".formatted(freq / 1000f) + " kHz";
        } else if (freq > 0) {
            return freq + " Hz";
        } else {
            return "Unknown";
        }
    }

    /**
     * @return mode string
     */
    public String modeString() {
        return switch (h_mode) {
            case STEREO -> "Stereo";
            case JOINT_STEREO -> "Joint stereo";
            case DUAL_CHANNEL -> "Dual channel";
            case SINGLE_CHANNEL -> "Single channel";
            default -> null;
        };
    }

    /**
     * Returns version as string.
     *
     * @return version string
     */
    public String versionString() {
        return switch (hVersion) {
            case MPEG1 -> "MPEG-1";
            case MPEG2_LSF -> "MPEG-2 LSF";
            case MPEG25_LSF -> "MPEG-2.5 LSF";
            default -> "Unknown";
        };
    }

    /**
     * Returns comprehensive header information as string.
     *
     * @return formatted header info
     */
    @Override
    public String toString() {
        return String.format(
                "Layer %s frame %s %s%s checksums %s, %s",
                layerString(),
                modeString(),
                versionString(),
                checksums() ? "" : " no",
                sampleFrequencyString(),
                bitrateString()
        );
    }
}

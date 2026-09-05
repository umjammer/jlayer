/*
 * 11/19/04  1.0 moved to LGPL.
 *
 * 11/17/04     Uncomplete frames discarded. E.B, javalayer@javazoom.net
 *
 * 12/05/03     ID3v2 tag returned. E.B, javalayer@javazoom.net
 *
 * 12/12/99     Based on Ibitstream. Exceptions thrown on errors,
 *             Temporary removed seek functionality. mdm@techie.com
 *
 * 02/12/99 : Java Conversion by E.B , javalayer@javazoom.net
 *
 * 04/14/97 : Added function prototypes for new syncing and seeking
 * mechanisms. Also made this file portable. Changes made by Jeff Tsay
 *
 *  @(#) ibitstream.h 1.5, last edit: 6/15/94 16:55:34
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Objects;


/**
 * The <code>Bistream</code> class is responsible for parsing
 * an MPEG audio bitstream.
 * <p>
 * Optimized version with additional helper APIs and improved performance.
 *
 * @author MDM (original)
 * @author TamKungZ
 */
public final class Bitstream implements BitstreamErrors, AutoCloseable {

    /**
     * Synchronization control constant for the initial
     * synchronization to the start of a frame.
     */
    static final byte INITIAL_SYNC = 0;

    /**
     * Synchronization control constant for non-initial frame
     * synchronizations.
     */
    static final byte STRICT_SYNC = 1;

    /**
     * Maximum size of the frame buffer.
     * <p>
     * max. 1730 bytes per frame: 144 * 384kbit/s / 32000 Hz + 2 Bytes CRC
     */
    private static final int BUFFER_INT_SIZE = 433;

    /**
     * Pre-computed bitmask for efficient bit extraction.
     * Made static to save memory across instances.
     */
    private static final int[] bitmask = {
            0, // dummy
            0x0000_0001, 0x0000_0003, 0x0000_0007, 0x0000_000f,
            0x0000_001f, 0x0000_003f, 0x0000_007f, 0x0000_00ff,
            0x0000_01ff, 0x0000_03ff, 0x0000_07ff, 0x0000_0fff,
            0x0000_1fff, 0x0000_3fff, 0x0000_7fff, 0x0000_ffff,
            0x0001_ffff
    };

    /**
     * The frame buffer that holds the data for the current frame.
     */
    private final int[] frameBuffer = new int[BUFFER_INT_SIZE];

    /**
     * The bytes read from the stream.
     */
    private final byte[] frameBytes = new byte[BUFFER_INT_SIZE * 4];

    /**
     * Sync buffer for frame synchronization.
     */
    private final byte[] syncBuf = new byte[4];

    /**
     * The input source stream.
     */
    private final PushbackInputStream source;

    /**
     * Reusable header instance to avoid allocations.
     */
    private final Header header = new Header();

    /**
     * CRC calculator array.
     */
    private Crc16[] crc = new Crc16[1];

    /**
     * Raw ID3v2 tag data.
     */
    private byte[] rawId3v2 = null;

    /**
     * Number of valid bytes in the frame buffer.
     */
    private int frameSize;

    /**
     * Index into frameBuffer where the next bits are retrieved.
     */
    private int wordPointer;

    /**
     * Number (0-31, from MSB to LSB) of next bit for get_bits()
     */
    private int bitIndex;

    /**
     * The current specified syncWord
     */
    private int syncWord;

    /**
     * Audio header position in stream.
     */
    private int headerPos = 0;

    /**
     * Single channel mode flag.
     */
    private boolean singleChMode;

    /**
     * First frame flag for VBR parsing.
     */
    private boolean firstFrame = true;

    /**
     * End of stream flag.
     */
    private boolean eof = false;

    /**
     * Closed state flag.
     */
    private boolean closed = false;

    /**
     * Total frames read counter for statistics.
     */
    private long totalFramesRead = 0;

    /**
     * Total bytes read counter for statistics.
     */
    private long totalBytesRead = 0;

    /**
     * Construct a IBitstream that reads data from a given InputStream.
     *
     * @param in The InputStream to read from (must not be null)
     * @throws NullPointerException if in is null
     */
    public Bitstream(InputStream in) {
        Objects.requireNonNull(in, "InputStream cannot be null");

        in = new BufferedInputStream(in);
        loadID3v2(in);
        firstFrame = true;
        source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);

        closeFrame();
    }

    /**
     * Return position of the first audio header.
     *
     * @return size of ID3v2 tag frames
     */
    public int getHeaderPosition() {
        return headerPos;
    }

    /**
     * @deprecated Use {@link #getHeaderPosition()} instead
     */
    @Deprecated
    public int header_pos() {
        return headerPos;
    }

    /**
     * Load ID3v2 frames.
     *
     * @param in MP3 InputStream
     * @author JavaZOOM
     */
    private void loadID3v2(InputStream in) {
        int size = -1;
        try {
            // Read ID3v2 header (10 bytes).
            in.mark(10);
            size = readID3v2Header(in);
            headerPos = size;
        } catch (IOException ignore) {
            // no ID3v2 tag present
        } finally {
            try {
                // Unread ID3v2 header (10 bytes).
                in.reset();
            } catch (IOException ignore) {
            }
        }

        // Load ID3v2 tags.
        if (size > 0) {
            try {
                rawId3v2 = new byte[size];
                in.readNBytes(rawId3v2, 0, rawId3v2.length);
            } catch (IOException e) {
                rawId3v2 = null;
            }
        }
    }

    /**
     * Parse ID3v2 tag header to find out size of ID3v2 frames.
     *
     * @param in MP3 InputStream
     * @return size of ID3v2 frames + header
     * @throws IOException if read fails
     * @author JavaZOOM
     */
    private static int readID3v2Header(InputStream in) throws IOException {
        byte[] id3header = new byte[4];
        int size = -10;

        in.readNBytes(id3header, 0, 3);

        // Look for ID3v2
        if ((id3header[0] == 'I') && (id3header[1] == 'D') && (id3header[2] == '3')) {
            in.readNBytes(id3header, 0, 3);
//            int majorVersion = id3header[0];
//            int revision = id3header[1];
            in.readNBytes(id3header, 0, 4);
            size = (id3header[0] << 21) + (id3header[1] << 14) +
                    (id3header[2] << 7) + (id3header[3]);
        }
        return (size + 10);
    }

    /**
     * Return raw ID3v2 frames + header.
     *
     * @return ID3v2 InputStream or null if ID3v2 frames are not available
     */
    public InputStream getRawID3v2() {
        return rawId3v2 == null ? null : new ByteArrayInputStream(rawId3v2);
    }

    /**
     * Check if ID3v2 tag is present.
     *
     * @return true if ID3v2 tag exists
     * @since 1.0.4
     */
    public boolean hasID3v2Tag() {
        return rawId3v2 != null && rawId3v2.length > 0;
    }

    /**
     * Get the size of the ID3v2 tag in bytes.
     *
     * @return ID3v2 tag size, or 0 if no tag present
     * @since 1.0.4
     */
    public int getID3v2TagSize() {
        return (rawId3v2 == null) ? 0 : rawId3v2.length;
    }

    /**
     * Reads and parses the next frame from the input source.
     *
     * @return the Header describing details of the frame read,
     *         or null if the end of the stream has been reached.
     * @throws BitstreamException if an error occurs
     */
    public Header readFrame() throws BitstreamException {
        Header result = null;
        try {
            result = readNextFrame();

            // Parse VBR on first frame
            if (firstFrame && result != null) {
                result.parseVBR(frameBytes);
                firstFrame = false;
            }
        } catch (BitstreamException ex) {
            if (ex.getErrorCode() == INVALIDFRAME) {
                // Try to skip this frame
//logger.log(Level.TRACE, "INVALIDFRAME");
                try {
                    closeFrame();
                    result = readNextFrame();
                } catch (BitstreamException e) {
                    if (e.getErrorCode() != STREAM_EOF) {
                        throw newBitstreamException(e.getErrorCode(), e);
                    }
                }
            } else if (ex.getErrorCode() != STREAM_EOF) {
                throw ex;
            }
        }

        if (result != null) {
            totalFramesRead++;
        }

        return result;
    }

    /**
     * Read next frame header and data.
     *
     * @return Header or null at EOF
     * @throws BitstreamException  when a stream error occurs
     */
    private Header readNextFrame() throws BitstreamException {
        if (eof) return null;
        if (closed) throw newBitstreamException(STREAM_ERROR, new IllegalStateException("Bitstream is closed"));

        // Frame is read by header class
        if (frameSize == -1) {
            nextFrame();
        }

        return header;
    }

    /**
     * Read next MP3 frame.
     *
     * @throws BitstreamException when a stream error occurs
     */
    private void nextFrame() throws BitstreamException {
        // entire frame is read by the header class
        header.read_header(this, crc);

        if (frameSize > 0) {
            totalBytesRead += frameSize;
        }
    }

    /**
     * Close the current frame.
     */
    public void closeFrame() {
        frameSize = -1;
        wordPointer = -1;
        bitIndex = -1;
    }

    /**
     * Create a new BitstreamException with error code only.
     *
     * @param errorCode the error code
     * @return new BitstreamException
     */
    BitstreamException newBitstreamException(int errorCode) {
        return new BitstreamException(errorCode, null);
    }

    /**
     * Create a new BitstreamException with error code and cause.
     *
     * @param errorCode the error code
     * @param cause the cause
     * @return new BitstreamException
     */
    BitstreamException newBitstreamException(int errorCode, Throwable cause) {
        return new BitstreamException(errorCode, cause);
    }

    /**
     * Read bits - wrapper for Header compatibility.
     *
     * @param n number of bits to read
     * @return bits value
     */
    public int readBits(int n) {
        return getBits(n);
    }

    /**
     * Read checked bits - wrapper for Header compatibility.
     * TODO: implement CRC check
     *
     * @param n number of bits to read
     * @return bits value
     */
    public int readCheckedBits(int n) {
        return getBits(n);
    }

    /**
     * Get next 32 bits from bitstream for header synchronization.
     * They are stored in the headerString.
     * syncMode allows Synchro flag ID.
     *
     * @param syncMode INITIAL_SYNC or STRICT_SYNC
     * @return header bits
     * @throws BitstreamException on error or EOF
     */
    int syncHeader(byte syncMode) throws BitstreamException {
        boolean sync;
        int headerString;

        // read additional 2 bytes
        int bytesRead = readBytes(syncBuf, 0, 3);

        if (bytesRead != 3) {
            throw newBitstreamException(STREAM_EOF, null);
        }

        headerString = ((syncBuf[0] << 16) & 0x00ff_0000) |
                        ((syncBuf[1] << 8) & 0x0000_ff00) |
                        ((syncBuf[2] << 0) & 0x0000_00ff);

        do {
            headerString <<= 8;

            if (readBytes(syncBuf, 3, 1) != 1) {
                throw newBitstreamException(STREAM_EOF, null);
            }

            headerString |= (syncBuf[3] & 0x0000_00ff);
            sync = isSyncMark(headerString, syncMode, syncWord);
        } while (!sync);

        return headerString;
    }

    /**
     * Check if header string is a valid sync mark.
     *
     * @param headerString the header bits
     * @param syncMode INITIAL_SYNC or STRICT_SYNC
     * @param word sync word to match
     * @return true if valid sync mark
     */
    public boolean isSyncMark(int headerString, int syncMode, int word) {
        boolean sync;

        if (syncMode == INITIAL_SYNC) {
            // MPEG 2.5 support
            sync = ((headerString & 0xffe0_0000) == 0xffe0_0000);
        } else {
            sync = ((headerString & 0xfff8_0c00) == word) &&
                    (((headerString & 0x0000_00c0) == 0x0000_00c0) == singleChMode);
        }

        // filter out invalid sample rate
        if (sync) {
            sync = (((headerString >>> 10) & 3) != 3);
        }
        // filter out invalid layer
        if (sync) {
            sync = (((headerString >>> 17) & 3) != 0);
        }
        // filter out invalid version
        if (sync) {
            sync = (((headerString >>> 19) & 3) != 1);
        }

        return sync;
    }

    /**
     * Read frame data from stream.
     */
    int readFrameData(int byteSize) throws BitstreamException {
        int numread = readFully(frameBytes, 0, byteSize);
        frameSize = byteSize;
        wordPointer = -1;
        bitIndex = -1;
        return numread;
    }

    /**
     * Parse frame data into int buffer.
     */
    void parseFrame() {
        int b = 0;
        int byteSize = frameSize;

        // Convert bytes to ints (4 bytes per int)
        for (int k = 0; k < byteSize; k += 4) {
            byte b0 = frameBytes[k];
            byte b1 = (k + 1 < byteSize) ? frameBytes[k + 1] : 0;
            byte b2 = (k + 2 < byteSize) ? frameBytes[k + 2] : 0;
            byte b3 = (k + 3 < byteSize) ? frameBytes[k + 3] : 0;
            frameBuffer[b++] = ((b0 << 24) & 0xff00_0000) |
                    ((b1 << 16) & 0x00ff_0000) |
                    ((b2 << 8) & 0x0000_ff00) |
                    (b3 & 0x0000_00ff);
        }

        wordPointer = 0;
        bitIndex = 0;
    }

    /**
     * Read bits from buffer into the lower bits of an unsigned int.
     * The LSB contains the latest read bit of the stream.
     * <p>
     * Optimized version with better bounds checking.
     *
     * @param numberOfBits number of bits to read (0-32)
     * @return the bits as an unsigned int
     */
    public int getBits(int numberOfBits) {
        if (numberOfBits <= 0) return 0;
        if (numberOfBits > 32) {
            throw new IllegalArgumentException("Cannot read more than 32 bits at once: " + numberOfBits);
        }

        int sum = bitIndex + numberOfBits;

        // Ensure wordPointer is in a sane range
        if (wordPointer < 0) wordPointer = 0;

        int maxWords = (frameSize + 3) / 4;
        if (maxWords <= 0) return 0;

        if (sum <= 32) {
            // All bits in current word
            if (wordPointer >= maxWords) return 0;

            int w = frameBuffer[wordPointer];
            int returnValue = (w >>> (32 - sum)) & bitmask[numberOfBits];

            bitIndex += numberOfBits;
            if (bitIndex == 32) {
                bitIndex = 0;
                wordPointer++;

                // Prevent future out of bounds
                if (wordPointer >= maxWords) {
                    wordPointer = maxWords - 1;
                    bitIndex = 0;
                }
            }

            return returnValue;
        }

        // Need bits from two words
        if (wordPointer >= maxWords) return 0;

        int right = (frameBuffer[wordPointer] & 0x0000_ffff);
        wordPointer++;

        int left = 0;
        if (wordPointer < maxWords) {
            left = (frameBuffer[wordPointer] & 0xffff_0000);
        }

        int returnValue = ((right << 16) & 0xffff_0000) |
                ((left >>> 16) & 0x0000_ffff);

        returnValue >>>= 48 - sum;
        returnValue &= bitmask[numberOfBits];
        bitIndex = sum - 32;

        return returnValue;
    }

    /**
     * Determines if the next 4 bytes of the stream represent a frame header.
     *
     * @param syncmode INITIAL_SYNC or STRICT_SYNC
     * @return true if sync mark found at current position
     * @throws BitstreamException on error
     */
    public boolean isSyncCurrentPosition(int syncmode) throws BitstreamException {
        int read = readBytes(syncBuf, 0, 4);
        int headerString = ((syncBuf[0] << 24) & 0xff00_0000) |
                ((syncBuf[1] << 16) & 0x00ff_0000) |
                ((syncBuf[2] << 8) & 0x0000_ff00) |
                ((syncBuf[3]) & 0x0000_00ff);

        try {
            source.unread(syncBuf, 0, read);
        } catch (IOException ignore) {
        }

        return switch (read) {
            case 0 -> true;
            case 4 -> isSyncMark(headerString, syncmode, syncWord);
            default -> false;
        };
    }

    /**
     * Unreads the bytes read from the frame.
     * Used when a frame needs to be re-read.
     *
     * @throws BitstreamException on error
     */
    public void unreadFrame() throws BitstreamException {
        if (wordPointer == -1 && bitIndex == -1 && (frameSize > 0)) {
            try {
                source.unread(frameBytes, 0, frameSize);
            } catch (IOException ex) {
                throw newBitstreamException(STREAM_ERROR, ex);
            }
        }
    }

    /**
     * Read a single bit.
     *
     * @return 0 or 1
     * @since 1.0.4
     */
    public int getBit() {
        return getBits(1);
    }

    /**
     * Skip bits in the stream.
     *
     * @param numberOfBits number of bits to skip
     * @since 1.0.4
     */
    public void skipBits(int numberOfBits) {
        getBits(numberOfBits);
    }

    /**
     * Get current bit position in the frame.
     *
     * @return bit position
     * @since 1.0.4
     */
    public int getBitPosition() {
        return (wordPointer * 32) + bitIndex;
    }

    /**
     * Set the sync word for header synchronization.
     * In Big-Endian byte order.
     * Set the word we want to sync the header to.
     * In Big-Endian byte order
     */
    void setSyncWord(int syncWord) {
        this.syncWord = syncWord & 0xffff_ff3f;
        singleChMode = ((syncWord & 0x0000_00c0) == 0x0000_00c0);
    }

    /**
     * Read exact number of bytes from stream.
     */
    private int readFully(byte[] b, int offs, int len) throws BitstreamException {
        int nRead = 0;
        try {
            while (len > 0) {
                int bytesRead = source.read(b, offs, len);
                if (bytesRead == -1) {
                    eof = true;
                    // Zero-fill remainder
                    while (len-- > 0) {
                        b[offs++] = 0;
                    }
                    break;
                }
                nRead += bytesRead;
                offs += bytesRead;
                len -= bytesRead;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return nRead;
    }

    /**
     * Read bytes without throwing exception on EOF.
     */
    private int readBytes(byte[] b, int offs, int len) throws BitstreamException {
        int totalBytesRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) {
                    eof = true;
                    break;
                }
                totalBytesRead += bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return totalBytesRead;
    }

    /**
     * Get a copy of the raw frame bytes.
     *
     * @return copy of frame bytes, or null if no frame available
     */
    public byte[] getFrameBytes() {
        if (frameSize <= 0) return null;
        byte[] out = new byte[frameSize];
        System.arraycopy(frameBytes, 0, out, 0, frameSize);
        return out;
    }

    /**
     * Get number of bytes in current frame.
     *
     * @return frame size in bytes
     */
    public int getFrameSize() {
        return frameSize;
    }

    /**
     * Check if end of stream has been reached.
     *
     * @return true if at EOF
     */
    public boolean isEOF() {
        return eof;
    }

    /**
     * Check if bitstream has been closed.
     *
     * @return true if closed
     * @since 1.0.4
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Get total number of frames read.
     *
     * @return frame count
     * @since 1.0.4
     */
    public long getTotalFramesRead() {
        return totalFramesRead;
    }

    /**
     * Get total number of bytes read (excluding ID3v2 tag).
     *
     * @return byte count
     * @since 1.0.4
     */
    public long getTotalBytesRead() {
        return totalBytesRead;
    }

    /**
     * Get stream statistics as a formatted string.
     *
     * @return statistics string
     * @since 1.0.4
     */
    public String getStatistics() {
        return "Bitstream Statistics: frames=%d, bytes=%d, ID3v2=%d bytes".formatted(
                totalFramesRead, totalBytesRead, getID3v2TagSize());
    }

    /**
     * Reset statistics counters.
     *
     * @since 1.0.4
     */
    public void resetStatistics() {
        totalFramesRead = 0;
        totalBytesRead = 0;
    }

    /**
     * Close the bitstream and release resources.
     * <p>
     * This method is idempotent - calling it multiple times has no effect.
     *
     * @throws BitstreamException if an I/O error occurs
     * @since 1.0.4
     */
    @Override
    public void close() throws BitstreamException {
        if (closed) {
            return;
        }
        try {
            source.close();
        } catch (IOException ex) {
            throw new BitstreamException(ex);
        } finally {
            closed = true;
        }
    }

    /**
     * Get a string representation of the bitstream state.
     *
     * @return string representation
     * @since 1.0.4
     */
    @Override
    public String toString() {
        return "Bitstream[closed=%b, eof=%b, frames=%d, position=%d bits]".formatted(
                closed, eof, totalFramesRead, getBitPosition());
    }
}

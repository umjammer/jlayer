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


/**
 * The <code>Bistream</code> class is responsible for parsing
 * an MPEG audio bitstream.
 *
 * <b>REVIEW:</b> much of the parsing currently occurs in the
 * various decoders. This should be moved into this class and associated
 * inner classes.
 */
public final class Bitstream implements BitstreamErrors {

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
     * The frame buffer that holds the data for the current frame.
     */
    private final int[] frameBuffer = new int[BUFFER_INT_SIZE];

    /**
     * Number of valid bytes in the frame buffer.
     */
    private int frameSize;

    /**
     * The bytes read from the stream.
     */
    private final byte[] frameBytes = new byte[BUFFER_INT_SIZE * 4];

    /**
     * Index into <code>frameBuffer</code> where the next bits are
     * retrieved.
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
     *
     */
    private boolean singleChMode;

    private final int[] bitmask = {
            0, // dummy
            0x0000_0001, 0x0000_0003, 0x0000_0007, 0x0000_000f, 0x0000_001f, 0x0000_003f, 0x0000_007f, 0x0000_00ff, 0x0000_01ff, 0x0000_03ff,
            0x0000_07ff, 0x0000_0fff, 0x0000_1fff, 0x0000_3fff, 0x0000_7fff, 0x0000_ffff, 0x0001_ffff
    };

    private final PushbackInputStream source;

    private final Header header = new Header();

    private final byte[] syncBuf = new byte[4];

    private final Crc16[] crc = new Crc16[1];

    private byte[] rawId3v2 = null;

    private boolean firstFrame;

    private boolean eof = false;

    /**
     * Construct a IBitstream that reads data from a
     * given InputStream.
     *
     * @param in The InputStream to read from.
     */
    public Bitstream(InputStream in) {
        if (in == null)
            throw new NullPointerException("in");
        in = new BufferedInputStream(in);
        loadID3v2(in);
        firstFrame = true;
        source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);

        closeFrame();
    }

    /**
     * Return position of the first audio header.
     *
     * @return size of ID3v2 tag frames.
     */
    public int header_pos() {
        return headerPos;
    }

    /**
     * Load ID3v2 frames.
     *
     * @param in MP3 InputStream.
     * @author JavaZOOM
     */
    private void loadID3v2(InputStream in) {
        int size = -1;
        try {
            // Read ID3v2 header (10 bytes).
            in.mark(10);
            size = readID3v2Header(in);
            headerPos = size;
        } catch (IOException e) {
        } finally {
            try {
                // Unread ID3v2 header (10 bytes).
                in.reset();
            } catch (IOException e) {
            }
        }
        // Load ID3v2 tags.
        try {
            if (size > 0) {
                rawId3v2 = new byte[size];
                in.read(rawId3v2, 0, rawId3v2.length);
            }
        } catch (IOException e) {
        }
    }

    /**
     * Parse ID3v2 tag header to find out size of ID3v2 frames.
     *
     * @param in MP3 InputStream
     * @return size of ID3v2 frames + header
     * @throws IOException
     * @author JavaZOOM
     */
    private static int readID3v2Header(InputStream in) throws IOException {
        byte[] id3header = new byte[4];
        int size = -10;
        in.read(id3header, 0, 3);
        // Look for ID3v2
        if ((id3header[0] == 'I') && (id3header[1] == 'D') && (id3header[2] == '3')) {
            in.read(id3header, 0, 3);
            @SuppressWarnings("unused")
            int majorVersion = id3header[0];
            @SuppressWarnings("unused")
            int revision = id3header[1];
            in.read(id3header, 0, 4);
            size = (id3header[0] << 21) + (id3header[1] << 14) + (id3header[2] << 7) + (id3header[3]);
        }
        return (size + 10);
    }

    /**
     * Return raw ID3v2 frames + header.
     *
     * @return ID3v2 InputStream or null if ID3v2 frames are not available.
     */
    public InputStream getRawID3v2() {
        if (rawId3v2 == null)
            return null;
        else {
            ByteArrayInputStream bain = new ByteArrayInputStream(rawId3v2);
            return bain;
        }
    }

    /**
     * Close the Bitstream.
     *
     * @throws BitstreamException when a stream error occurs
     */
    public void close() throws BitstreamException {
        try {
            source.close();
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
    }

    /**
     * Reads and parses the next frame from the input source.
     *
     * @return the Header describing details of the frame read,
     * or null if the end of the stream has been reached.
     */
    public Header readFrame() throws BitstreamException {
        Header result = null;
        try {
            result = readNextFrame();
            // E.B, Parse VBR (if any) first frame.
            if (firstFrame) {
                result.parseVBR(frameBytes);
                firstFrame = false;
            }
        } catch (BitstreamException ex) {
            if ((ex.getErrorCode() == INVALIDFRAME)) {
                // Try to skip this frame.
//logger.log(Level.TRACE, "INVALIDFRAME");
                try {
                    closeFrame();
                    result = readNextFrame();
                } catch (BitstreamException e) {
                    if ((e.getErrorCode() != STREAM_EOF)) {
                        // wrap original exception so stack trace is maintained.
                        throw newBitstreamException(e.getErrorCode(), e);
                    }
                }
            } else if ((ex.getErrorCode() != STREAM_EOF)) {
                // wrap original exception so stack trace is maintained.
                throw newBitstreamException(ex.getErrorCode(), ex);
            }
        }
        return result;
    }

    /**
     * Read next MP3 frame.
     *
     * @return MP3 frame header.
     * @throws BitstreamException when a stream error occurs
     */
    private Header readNextFrame() throws BitstreamException {
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
        // entire frame is read by the header class.
        header.read_header(this, crc);
    }

    /**
     * Unreads the bytes read from the frame.
     * <p>
     * REVIEW: add new error codes for this.
     *
     * @throws BitstreamException when a stream error occurs
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
     * Close MP3 frame.
     */
    public void closeFrame() {
        frameSize = -1;
        wordPointer = -1;
        bitIndex = -1;
    }

    /**
     * Determines if the next 4 bytes of the stream represent a
     * frame header.
     */
    public boolean isSyncCurrentPosition(int syncmode) throws BitstreamException {
        int read = readBytes(syncBuf, 0, 4);
        int headerString = ((syncBuf[0] << 24) & 0xff00_0000) | ((syncBuf[1] << 16) & 0x00ff_0000)
                | ((syncBuf[2] << 8) & 0x0000_ff00) | ((syncBuf[3] << 0) & 0x0000_00ff);

        try {
            source.unread(syncBuf, 0, read);
        } catch (IOException ex) {
        }

        boolean sync = switch (read) {
            case 0 -> true;
            case 4 -> isSyncMark(headerString, syncmode, syncWord);
            default -> false;
        };

        return sync;
    }

    /**
     * REVIEW: this class should provide inner classes to
     * parse the frame contents. Eventually, readBits will
     * be removed.
     */
    public int readBits(int n) {
        return getBits(n);
    }

    public int readCheckedBits(int n) {
        // REVIEW: implement CRC check.
        return getBits(n);
    }

    BitstreamException newBitstreamException(int errorcode) {
        return new BitstreamException(errorcode, null);
    }

    BitstreamException newBitstreamException(int errorcode, Throwable throwable) {
        return new BitstreamException(errorcode, throwable);
    }

    /**
     * Get next 32 bits from bitstream.
     * They are stored in the headerString.
     * syncMode allows Synchro flag ID
     * The returned value is False at the end of stream.
     */
    int syncHeader(byte syncMode) throws BitstreamException {
        boolean sync;
        int headerString;
        // read additional 2 bytes
        int bytesRead = readBytes(syncBuf, 0, 3);

        if (bytesRead != 3)
            throw newBitstreamException(STREAM_EOF, null);

        headerString = ((syncBuf[0] << 16) & 0x00ff_0000) | ((syncBuf[1] << 8) & 0x0000_ff00) | ((syncBuf[2] << 0) & 0x0000_00ff);

        do {
            headerString <<= 8;

            if (readBytes(syncBuf, 3, 1) != 1)
                throw newBitstreamException(STREAM_EOF, null);

            headerString |= (syncBuf[3] & 0x0000_00ff);

            sync = isSyncMark(headerString, syncMode, syncWord);
        } while (!sync);

        return headerString;
    }

    public boolean isSyncMark(int headerString, int syncMode, int word) {
        boolean sync = false;

        if (syncMode == INITIAL_SYNC) {
//            sync =  ((headerString & 0xfff0_0000) == 0xfff0_0000);
            sync = ((headerString & 0xffe0_0000) == 0xffe0_0000); // SZD: MPEG 2.5
        } else {
            sync = ((headerString & 0xfff8_0c00) == word) && (((headerString & 0x0000_00c0) == 0x0000_00c0) == singleChMode);
        }

        // filter out invalid sample rate
        if (sync)
            sync = (((headerString >>> 10) & 3) != 3);
        // filter out invalid layer
        if (sync)
            sync = (((headerString >>> 17) & 3) != 0);
        // filter out invalid version
        if (sync)
            sync = (((headerString >>> 19) & 3) != 1);

        return sync;
    }

    /**
     * Reads the data for the next frame. The frame is not parsed
     * until parse frame is called.
     */
    int readFrameData(int byteSize) throws BitstreamException {
        int numread = 0;
        numread = readFully(frameBytes, 0, byteSize);
        frameSize = byteSize;
        wordPointer = -1;
        bitIndex = -1;
        return numread;
    }

    /**
     * Parses the data previously read with read_frame_data().
     */
    void parseFrame() throws BitstreamException {
        // Convert Bytes read to int
        int b = 0;
        byte[] byteRead = frameBytes;
        int byteSize = frameSize;

        // Check ID3v1 TAG (True only if last frame).

        for (int k = 0; k < byteSize; k += 4) {
            byte b0 = byteRead[k];
            byte b1 = (k + 1 < byteSize) ? byteRead[k + 1] : 0;
            byte b2 = (k + 2 < byteSize) ? byteRead[k + 2] : 0;
            byte b3 = (k + 3 < byteSize) ? byteRead[k + 3] : 0;
            frameBuffer[b++] = ((b0 << 24) & 0xff00_0000) | ((b1 << 16) & 0x00ff_0000) | ((b2 << 8) & 0x0000_ff00)
                    | (b3 & 0x0000_00ff);
        }
        wordPointer = 0;
        bitIndex = 0;
    }

    /**
     * Read bits from buffer into the lower bits of an unsigned int.
     * The LSB contains the latest read bit of the stream.
     * (1 <= numberOfBits <= 16)
     */
    public int getBits(int numberOfBits) {
        int returnValue = 0;
        int sum = bitIndex + numberOfBits;

        if (numberOfBits <= 0)
            return 0;

        // Ensure wordPointer is in a sane range
        if (wordPointer < 0)
            wordPointer = 0;

        // Determine how many words are valid based on frameSize (bytes -> ints)
        int maxWords = (frameSize <= 0) ? 0 : ((frameSize + 3) / 4);

        if (sum <= 32) {
            // all bits contained in *wordPointer
            if (wordPointer >= maxWords)
                return 0; // prevent ArrayIndexOutOfBounds

            int w = frameBuffer[wordPointer];
            returnValue = (w >>> (32 - sum)) & bitmask[numberOfBits];
            if ((bitIndex += numberOfBits) == 32) {
                bitIndex = 0;
                wordPointer++;

                // Safety check after increment to prevent future access violations
                if (wordPointer >= maxWords) {
                    wordPointer = maxWords - 1;
                    bitIndex = 0;
                }
            }
            return returnValue;
        }

        // need bits from two words
        if (wordPointer >= maxWords)
            return 0;

        int right = (frameBuffer[wordPointer] & 0x0000_ffff);
        wordPointer++;

        int left = 0;
        if (wordPointer < maxWords)
            left = (frameBuffer[wordPointer] & 0xffff_0000);

        returnValue = ((right << 16) & 0xffff_0000) | ((left >>> 16) & 0x0000_ffff);

        returnValue >>>= 48 - sum;
        returnValue &= bitmask[numberOfBits];
        bitIndex = sum - 32;
        return returnValue;
    }

    /**
     * Set the word we want to sync the header to.
     * In Big-Endian byte order
     */
    void setSyncWord(int syncWord) {
        this.syncWord = syncWord & 0xffff_ff3f;
        singleChMode = ((syncWord & 0x0000_00c0) == 0x0000_00c0);
    }

    /**
     * Reads the exact number of bytes from the source
     * input stream into a byte array.
     *
     * @param b    The byte array to read the specified number
     *             of bytes into.
     * @param offs The index in the array where the first byte
     *             read should be stored.
     * @param len  the number of bytes to read.
     * @throws BitstreamException is thrown if the specified
     *                            number of bytes could not be read from the stream.
     */
    private int readFully(byte[] b, int offs, int len) throws BitstreamException {
        int nRead = 0;
        try {
            while (len > 0) {
                int bytesRead = source.read(b, offs, len);
                if (bytesRead == -1) {
                    eof = true;
                    while (len-- > 0) {
                        b[offs++] = 0;
                    }
                    break;
//                    throw newBitstreamException(UNEXPECTED_EOF, new EOFException());
                }
                nRead = nRead + bytesRead;
                offs += bytesRead;
                len -= bytesRead;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return nRead;
    }

    /**
     * Similar to readFully, but doesn't throw exception when
     * EOF is reached.
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
     * Convenience: return a copy of the raw frame bytes read by the last readFrameData().
     * Returns null if no frame bytes are available.
     */
    public byte[] getFrameBytes() {
        if (frameSize <= 0) return null;
        byte[] out = new byte[frameSize];
        System.arraycopy(frameBytes, 0, out, 0, frameSize);
        return out;
    }

    /**
     * Convenience: returns number of bytes in the current frame (from last readFrameData()).
     */
    public int getFrameSize() {
        return frameSize;
    }

    /**
     * Convenience: indicates whether end-of-stream has been reached while reading.
     */
    public boolean isEOF() {
        return eof;
    }
}

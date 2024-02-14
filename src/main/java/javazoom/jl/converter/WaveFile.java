/*
 * 11/19/04 1.0 moved to LGPL.
 * 02/23/99 JavaConversion by E.B
 * Don Cross, April 1993.
 * RIFF file format classes.
 * See Chapter 8 of "Multimedia Programmer's Reference" in
 * the Microsoft Windows SDK.
 *
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

package javazoom.jl.converter;


/**
 * Class allowing WaveFormat Access
 */
public class WaveFile extends RiffFile {

    public static final int MAX_WAVE_CHANNELS = 2;

    static class ChunkData {
        /** Format category (PCM=1) */
        public short wFormatTag = 0;
        /** Number of channels (mono=1, stereo=2) */
        public short nChannels = 0;
        /** Sampling rate [Hz] */
        public int nSamplesPerSec = 0;
        public int nAvgBytesPerSec = 0;
        public short nBlockAlign = 0;
        public short nBitsPerSample = 0;

        public ChunkData() {
            wFormatTag = 1;     // PCM
            config(44100, (short) 16, (short) 1);
        }

        public void config(int newSamplingRate, short newBitsPerSample, short newNumChannels) {
            nSamplesPerSec = newSamplingRate;
            nChannels = newNumChannels;
            nBitsPerSample = newBitsPerSample;
            nAvgBytesPerSec = (nChannels * nSamplesPerSec * nBitsPerSample) / 8;
            nBlockAlign = (short) ((nChannels * nBitsPerSample) / 8);
        }
    }

    static class Chunk {
        public RiffChunkHeader header;
        public ChunkData data;

        public Chunk() {
            header = new RiffChunkHeader();
            data = new ChunkData();
            header.ckID = fourCC("fmt ");
            header.ckSize = 16;
        }

        public int verifyValidity() {
            boolean ret = header.ckID == fourCC("fmt ") &&

                    (data.nChannels == 1 || data.nChannels == 2) &&

                    data.nAvgBytesPerSec == (data.nChannels *
                            data.nSamplesPerSec *
                            data.nBitsPerSample) / 8 &&

                    data.nBlockAlign == (data.nChannels *
                            data.nBitsPerSample) / 8;
            if (ret) return 1;
            else return 0;
        }
    }

    public static class WaveFileSample {
        public short[] chan;

        public WaveFileSample() {
            chan = new short[WaveFile.MAX_WAVE_CHANNELS];
        }
    }

    private Chunk waveFormat;
    private RiffChunkHeader pcmData;
    private long pcmDataOffset = 0;  // offset of 'pcmData' in output file
    private int numSamples = 0;

    /**
     * Constructs a new WaveFile instance.
     */
    public WaveFile() {
        pcmData = new RiffChunkHeader();
        waveFormat = new Chunk();
        pcmData.ckID = fourCC("data");
        pcmData.ckSize = 0;
        numSamples = 0;
    }

    /**
     *
     */
    public int openForWrite(String filename, int samplingRate, short bitsPerSample, short numChannels) {
        // Verify parameters...
        if ((filename == null) ||
                (bitsPerSample != 8 && bitsPerSample != 16) ||
                numChannels < 1 || numChannels > 2) {
            return DDC_INVALID_CALL;
        }

        waveFormat.data.config(samplingRate, bitsPerSample, numChannels);

        int retcode = Open(filename, RFM_WRITE);

        if (retcode == DDC_SUCCESS) {
            byte[] theWave = {(byte) 'W', (byte) 'A', (byte) 'V', (byte) 'E'};
            retcode = write(theWave, 4);

            if (retcode == DDC_SUCCESS) {
                // Writing waveFormat
                retcode = write(waveFormat.header, 8);
                retcode = write(waveFormat.data.wFormatTag, 2);
                retcode = write(waveFormat.data.nChannels, 2);
                retcode = write(waveFormat.data.nSamplesPerSec, 4);
                retcode = write(waveFormat.data.nAvgBytesPerSec, 4);
                retcode = write(waveFormat.data.nBlockAlign, 2);
                retcode = write(waveFormat.data.nBitsPerSample, 2);


                if (retcode == DDC_SUCCESS) {
                    pcmDataOffset = currentFilePosition();
                    retcode = write(pcmData, 8);
                }
            }
        }

        return retcode;
    }

    /**
     * Write 16-bit audio
     */
    public int writeData(short[] data, int numData) {
        int extraBytes = numData * 2;
        pcmData.ckSize += extraBytes;
        return super.write(data, extraBytes);
    }

    /**
     *
     */
    @Override
    public int close() {
        int rc = DDC_SUCCESS;

        if (fmode == RFM_WRITE)
            rc = backpatch(pcmDataOffset, pcmData, 8);
        if (rc == DDC_SUCCESS)
            rc = super.close();
        return rc;
    }

    // [Hz]
    public int samplingRate() {
        return waveFormat.data.nSamplesPerSec;
    }

    public short bitsPerSample() {
        return waveFormat.data.nBitsPerSample;
    }

    public short numChannels() {
        return waveFormat.data.nChannels;
    }

    public int numSamples() {
        return numSamples;
    }

    /**
     * Open for write using another wave file's parameters...
     */
    public int openForWrite(String filename, WaveFile otherWave) {
        return openForWrite(filename,
                otherWave.samplingRate(),
                otherWave.bitsPerSample(),
                otherWave.numChannels());
    }

    /**
     *
     */
    @Override
    public long currentFilePosition() {
        return super.currentFilePosition();
    }
}
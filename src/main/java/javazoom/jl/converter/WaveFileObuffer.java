/*
 * 11/19/04  1.0 moved to LGPL.
 *
 * 12/12/99     0.0.7 Renamed class, additional constructor arguments
 *             and larger write buffers. mdm@techie.com.
 *
 * 15/02/99  Java Conversion by E.B ,javalayer@javazoom.net
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

import javazoom.jl.decoder.Obuffer;


/**
 * Implements an {@link Obuffer} by writing the data to
 * a file in RIFF WAVE format.
 *
 * @since 0.0
 */
public class WaveFileObuffer extends Obuffer {

    private final short[] buffer;
    private final short[] bufferP;
    private final int channels;
    private final WaveFile outWave;

    /**
     * Creates a new WareFileObuffer instance.
     *
     * @param number_of_channels The number of channels of audio data
     *                           this buffer will receive.
     * @param freq               The sample frequency of the samples in the buffer.
     * @param fileName           The filename to write the data to.
     */
    public WaveFileObuffer(int number_of_channels, int freq, String fileName) {
        if (fileName == null)
            throw new NullPointerException("fileName");

        buffer = new short[OBUFFERSIZE];
        bufferP = new short[MAXCHANNELS];
        channels = number_of_channels;

        for (int i = 0; i < number_of_channels; ++i)
            bufferP[i] = (short) i;

        outWave = new WaveFile();

        int rc = outWave.openForWrite(fileName, freq, (short) 16, (short) channels);
    }

    /**
     * Takes a 16 Bit PCM sample.
     */
    @Override
    public void append(int channel, short value) {
        buffer[bufferP[channel]] = value;
        bufferP[channel] += (short) channels;
    }

    /**
     * Write the samples to the file (Random Access).
     */
    short[] myBuffer = new short[2];

    @Override
    public void writeBuffer(int val) {

        int rc = outWave.writeData(buffer, bufferP[0]);
        // REVIEW: handle RiffFile errors.
        for (int i = 0; i < channels; ++i) bufferP[i] = (short) i;
    }

    @Override
    public void close() {
        outWave.close();
    }

    /**
     *
     */
    @Override
    public void clearBuffer() {
    }

    /**
     *
     */
    @Override
    public void setStopFlag() {
    }

//    /**
//     * Create STDOUT buffer
//     */
//    public static Obuffer create_stdout_obuffer(MPEG_Args maplay_args) {
//        Obuffer thebuffer = null;
//        int mode = maplay_args.MPEGheader.mode();
//        int whichChannels = maplay_args.whichC;
//        if (mode == Header.single_channel || whichChannels != MPEG_Args.both)
//            thebuffer = new FileObuffer(1, maplay_args.outputFilename);
//        else thebuffer = new FileObuffer(2, maplay_args.outputFilename);
//        return (thebuffer);
//    }
}

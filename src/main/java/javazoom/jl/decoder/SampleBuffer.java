/*
 * 11/19/04     1.0 moved to LGPL.
 *
 * 12/12/99  Initial Version based on FileObuffer.    mdm@techie.com.
 *
 * FileObuffer:
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

package javazoom.jl.decoder;

/**
 * The <code>SampleBuffer</code> class implements an output buffer
 * that provides storage for a fixed size block of samples.
 */
public class SampleBuffer extends Obuffer {

    private final short[] buffer;
    private final int[] bufferP;
    private final int channels;
    private final int frequency;

    /**
     * Constructor
     */
    public SampleBuffer(int sample_frequency, int number_of_channels) {
        buffer = new short[OBUFFERSIZE];
        bufferP = new int[MAXCHANNELS];
        channels = number_of_channels;
        frequency = sample_frequency;

        for (int i = 0; i < number_of_channels; ++i)
            bufferP[i] = (short) i;
    }

    public int getChannelCount() {
        return this.channels;
    }

    public int getSampleFrequency() {
        return this.frequency;
    }

    public short[] getBuffer() {
        return this.buffer;
    }

    public int getBufferLength() {
        return bufferP[0];
    }

    /**
     * Takes a 16 Bit PCM sample.
     */
    @Override
    public void append(int channel, short value) {
        buffer[bufferP[channel]] = value;
        bufferP[channel] += channels;
    }

    @Override
    public void appendSamples(int channel, float[] f) {
        int pos = bufferP[channel];

        short s;
        float fs;
        for (int i = 0; i < 32; ) {
            fs = f[i++];
            fs = (fs > 32767.0f ? 32767.0f
                    : (Math.max(fs, -32767.0f)));

            s = (short) fs;
            buffer[pos] = s;
            pos += channels;
        }

        bufferP[channel] = pos;
    }

    /**
     * Write the samples to the file (Random Acces).
     */
    @Override
    public void writeBuffer(int val) {
    }

    @Override
    public void close() {
    }

    /**
     *
     */
    @Override
    public void clearBuffer() {
        for (int i = 0; i < channels; ++i)
            bufferP[i] = (short) i;
    }

    /**
     *
     */
    @Override
    public void setStopFlag() {
    }
}

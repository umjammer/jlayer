/*
 * 11/19/04        1.0 moved to LGPL.
 * 01/12/99        Initial version.    mdm@techie.com
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

import javazoom.jl.converter.Converter;


/**
 * The <code>Decoder</code> class encapsulates the details of
 * decoding an MPEG audio frame.
 *
 * @author MDM
 * @version 0.0.7 12/12/99
 * @since 0.0.5
 */
public class Decoder implements DecoderErrors {
    static private final Params DEFAULT_PARAMS = new Params();

    /**
     * The OBuffer instance that will receive the decoded
     * PCM samples.
     */
    private OBuffer output;

    /**
     * Synthesis filter for the left channel.
     */
    private SynthesisFilter filter1;

    /**
     * Synthesis filter for the right channel.
     */
    private SynthesisFilter filter2;

    /**
     * The decoder used to decode layer III frames.
     */
    private LayerIIIDecoder l3decoder;
    private LayerIIDecoder l2decoder;
    private LayerIDecoder l1decoder;

    private int outputFrequency;
    private int outputChannels;

    private final Equalizer equalizer = new Equalizer();

    private final Params params;

    private boolean initialized;

    /**
     * Creates a new <code>Decoder</code> instance with default
     * parameters.
     */
    public Decoder() {
        this(null);
    }

    /**
     * Creates a new <code>Decoder</code> instance with default
     * parameters.
     *
     * @param params The <code>Params</code> instance that describes
     *               the customizable aspects of the decoder.
     */
    public Decoder(Params params) {
        if (params == null)
            params = DEFAULT_PARAMS;

        this.params = params;

        Equalizer eq = this.params.getInitialEqualizerSettings();
        if (eq != null) {
            equalizer.setFrom(eq);
        }
    }

    static public Params getDefaultParams() {
        return (Params) DEFAULT_PARAMS.clone();
    }

    public void setEqualizer(Equalizer eq) {
        if (eq == null)
            eq = Equalizer.PASS_THRU_EQ;

        equalizer.setFrom(eq);

        float[] factors = equalizer.getBandFactors();

        if (filter1 != null)
            filter1.setEQ(factors);

        if (filter2 != null)
            filter2.setEQ(factors);
    }

    /**
     * Decodes one frame from an MPEG audio bitstream.
     *
     * <p>A frame that cannot be reconstructed writes nothing, which a {@link SampleBuffer} reports
     * as {@code getBufferLength() == 0}. That happens after a {@link #seekNotify} until the bit
     * reservoir has refilled, and it is the signal a seeking caller waits on rather than guessing a
     * warm-up length.
     *
     * @param header The header describing the frame to decode.
     * @param stream The bit stream that provides the bits for the body of the frame.
     * @return A SampleBuffer containing the decoded samples.
     */
    public OBuffer decodeFrame(Header header, Bitstream stream)
            throws DecoderException {
        if (!initialized) {
            initialize(header);
        }

        int layer = header.layer();

        output.clearBuffer();

        FrameDecoder decoder = retrieveDecoder(header, stream, layer);

        decoder.decodeFrame();

        output.writeBuffer(1);

        return output;
    }

    /**
     * Tells the decoder that the bitstream has jumped, so that none of the last frame is carried
     * into the next one.
     *
     * <p>The counterpart of {@link Bitstream#seek}, and the piece that was missing rather than the
     * arithmetic: {@link LayerIIIDecoder#seek_notify} has been in this library since 1997 and
     * nothing called it.
     *
     * <h2>The warm-up a seek needs, measured</h2>
     *
     * <p>The first frames after a seek cannot be reconstructed and produce no samples: a Layer III
     * frame's {@code main_data_begin} points backwards up to 511 bytes into frames a seek did not
     * read, and {@link LayerIIIDecoder} writes nothing rather than writing rubbish. How many frames
     * that is depends on the bit rate — 511 bytes is under one frame at 320 kbps and about five at
     * 32 kbps — so a caller waits for samples rather than counting frames.
     *
     * <p>But the first frame that <em>does</em> produce samples is still wrong, because its IMDCT
     * overlap came from a frame that was never decoded. Measured on the fixtures here, against a
     * sequential decode of the same frame:
     *
     * <pre>
     *   192 kbps CBR, 44.1 kHz:  lead 0 no samples | lead 1 differs, worst 8146 | lead 2 exact
     *   116 kbps VBR, 48 kHz:    lead 0,1 no samples | lead 2 differs, worst 4495 | lead 3 exact
     * </pre>
     *
     * <p>So the rule, and it holds at both bit rates: <b>decode forward discarding frames until one
     * produces samples, discard that one too, and play from the next.</b> Getting it wrong is not
     * subtle — 8146 out of 32768 is a quarter of full scale, which is a click, not a blemish.
     *
     * <h2>What the synthesis filters contribute: nothing, measured</h2>
     *
     * <p>The filters are reset here and it makes no difference to a single sample — the table above
     * is identical with the two {@code reset()} calls removed. One frame of decoding makes 36 passes
     * through a 16-deep history, so the old position's 512 taps are overwritten by the frame that
     * has to be discarded anyway. The calls stay because "the stream jumped" ought to mean nothing
     * is carried over, rather than nothing that was measured; they are not what prevents the click.
     *
     * @since 1.0.5
     */
    public void seekNotify() {
        if (l3decoder != null) {
            l3decoder.seek_notify();
        }
        // Layer I and II have no bit reservoir and no overlap between frames, so they need nothing
        // beyond the filters below; they are listed here so that a reader does not wonder.
        if (filter1 != null) {
            filter1.reset();
        }
        if (filter2 != null) {
            filter2.reset();
        }
        if (output != null) {
            output.clearBuffer();
        }
    }

    /**
     * Changes the output buffer. This will take effect the next time
     * decodeFrame() is called.
     */
    public void setOutputBuffer(OBuffer out) {
        output = out;
    }

    /**
     * Retrieves the sample frequency of the PCM samples output
     * by this decoder. This typically corresponds to the sample
     * rate encoded in the MPEG audio stream.
     *
     * @return the sample rate (in Hz) of the samples written to the
     * output buffer when decoding.
     */
    public int getOutputFrequency() {
        return outputFrequency;
    }

    /**
     * Retrieves the number of channels of PCM samples output by
     * this decoder. This usually corresponds to the number of
     * channels in the MPEG audio stream, although it may differ.
     *
     * @return The number of output channels in the decoded samples: 1
     * for mono, or 2 for stereo.
     */
    public int getOutputChannels() {
        return outputChannels;
    }

    /**
     * Retrieves the maximum number of samples that will be written to
     * the output buffer when one frame is decoded. This can be used to
     * help calculate the size of other buffers whose size is based upon
     * the number of samples written to the output buffer. NB: this is
     * an upper bound and fewer samples may actually be written, depending
     * upon the sample rate and number of channels.
     *
     * @return The maximum number of samples that are written to the
     * output buffer when decoding a single frame of MPEG audio.
     */
    public int getOutputBlockSize() {
        return OBuffer.O_BUFFER_SIZE;
    }

    /**
     * Convenience: decode the next frame from the provided Bitstream and
     * return a populated SampleBuffer. Returns null if no frame is available.
     * This preserves the old API; use this method for simpler decoding.
     */
    public SampleBuffer decodeNextFrame(Bitstream stream) throws BitstreamException, DecoderException {
        Header h = stream.readFrame();
        if (h == null) return null;
        int channels = (h.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
        SampleBuffer sb = new SampleBuffer(h.frequency(), channels);
        setOutputBuffer(sb);
        decodeFrame(h, stream);
        stream.closeFrame();
        return sb;
    }

    /**
     * Convenience: decode the next frame and return raw PCM 16-bit little-endian bytes.
     * Returns null if no frame is available.
     */
    public byte[] decodeNextFrameToPCM(Bitstream stream) throws BitstreamException, DecoderException {
        SampleBuffer sb = decodeNextFrame(stream);
        if (sb == null) return null;
        short[] buf = sb.getBuffer();
        int len = sb.getBufferLength();
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = buf[i];
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >>> 8) & 0xFF);
        }
        return out;
    }

    /**
     * Convenience static helper that uses the existing Converter API.
     * Keeps backwards compatibility while offering a simpler entry point.
     */
    public static void convertToWav(String sourceName, String destName) throws javazoom.jl.decoder.JavaLayerException {
        new Converter().convert(sourceName, destName);
    }

    protected DecoderException newDecoderException(int errorCode) {
        return new DecoderException(errorCode, null);
    }

    protected DecoderException newDecoderException(int errorCode, Throwable throwable) {
        return new DecoderException(errorCode, throwable);
    }

    protected FrameDecoder retrieveDecoder(Header header, Bitstream stream, int layer) throws DecoderException {
        FrameDecoder decoder = switch (layer) {
            case 3 -> {
                if (l3decoder == null) {
                    l3decoder = new LayerIIIDecoder(stream,
                            header, filter1, filter2,
                            output, OutputChannels.BOTH_CHANNELS);
                }

                yield l3decoder;
            }
            case 2 -> {
                if (l2decoder == null) {
                    l2decoder = new LayerIIDecoder();
                    l2decoder.create(stream,
                            header, filter1, filter2,
                            output, OutputChannels.BOTH_CHANNELS);
                }
                yield l2decoder;
            }
            case 1 -> {
                if (l1decoder == null) {
                    l1decoder = new LayerIDecoder();
                    l1decoder.create(stream,
                            header, filter1, filter2,
                            output, OutputChannels.BOTH_CHANNELS);
                }
                yield l1decoder;
            }
            default -> null;

            // REVIEW: allow channel output selection type
            // (LEFT, RIGHT, BOTH, DOWNMIX)
        };

        if (decoder == null) {
            throw newDecoderException(UNSUPPORTED_LAYER, null);
        }

        return decoder;
    }

    private void initialize(Header header) throws DecoderException {

        // REVIEW: allow customizable scale factor
        float scalefactor = 32700.0f;

        int mode = header.mode();
        @SuppressWarnings("unused")
        int layer = header.layer();
        int channels = mode == Header.SINGLE_CHANNEL ? 1 : 2;


        // set up output buffer if not set up by client.
        if (output == null)
            output = new SampleBuffer(header.frequency(), channels);

        float[] factors = equalizer.getBandFactors();
        filter1 = new SynthesisFilter(0, scalefactor, factors);

        // REVIEW: allow mono output for stereo
        if (channels == 2)
            filter2 = new SynthesisFilter(1, scalefactor, factors);

        outputChannels = channels;
        outputFrequency = header.frequency();

        initialized = true;
    }

    /**
     * The <code>Params</code> class presents the customizable
     * aspects of the decoder.
     * <p>
     * Instances of this class are not thread safe.
     */
    public static class Params implements Cloneable {

        private OutputChannels outputChannels = OutputChannels.BOTH;

        private final Equalizer equalizer = new Equalizer();

        public Params() {
        }

        @Override
        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new InternalError(this + ": " + ex);
            }
        }

        public void setOutputChannels(OutputChannels out) {
            if (out == null)
                throw new NullPointerException("out");

            outputChannels = out;
        }

        public OutputChannels getOutputChannels() {
            return outputChannels;
        }

        /**
         * Retrieves the equalizer settings that the decoder's equalizer
         * will be initialized from.
         * <p>
         * The <code>Equalizer</code> instance returned
         * cannot be changed in real time to affect the
         * decoder output as it is used only to initialize the decoders
         * EQ settings. To affect the decoder's output in realtime,
         * use the Equalizer returned from the getEqualizer() method on
         * the decoder.
         *
         * @return The <code>Equalizer</code> used to initialize the
         * EQ settings of the decoder.
         */
        public Equalizer getInitialEqualizerSettings() {
            return equalizer;
        }
    }
}


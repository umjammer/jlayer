/*
 * JLayer
 *
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Xing/Info header frame and the LAME gapless fields.
 *
 * <p>Both fixtures hold the same 212 frames of a C major scale at 48 kHz stereo, one written by
 * LAME 3.100 and one by a Lavc encoder that imitates its tag, so the same expectations apply to
 * both. The file states its own answer — frame count, encoder delay and padding are all in its
 * header — which is what makes these assertions arithmetic rather than golden values.
 */
class GaplessTest {

    /** Written by LAME 3.100. */
    private static final String LAME = "src/test/resources/c-major-scale_test_audacity.mp3";

    /** Written by Lavc 61.19, whose tag imitates LAME's. */
    private static final String LAVC = "src/test/resources/c-major-scale_test_web-convert_mono.mp3";

    /** Has no Xing header at all, so nothing here should touch it. */
    private static final String NO_TAG = "src/test/resources/test.mp3";

    /** Samples per MPEG frame for Layer III. */
    private static final int SAMPLES_PER_FRAME = 1152;

    /** What one decode produced. */
    private record Decoded(int mpegFrames, long pcmFramesRaw, long pcmFramesTrimmed, Header first) {
    }

    private Decoded decode(String file) throws IOException, BitstreamException, DecoderException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(Path.of(file)))) {
            Bitstream stream = new Bitstream(in);
            Decoder decoder = new Decoder();
            GaplessTrim trim = null;
            Header header;
            Header first = null;
            int mpegFrames = 0;
            long raw = 0;
            long trimmed = 0;
            while ((header = stream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, stream);
                int channels = buffer.getChannelCount();
                if (first == null) {
                    first = header;
                    trim = GaplessTrim.of(header, channels);
                }
                raw += buffer.getBufferLength() / channels;
                trimmed += trim.accept(buffer.getBufferLength()) / channels;
                mpegFrames++;
                stream.closeFrame();
            }
            return new Decoded(mpegFrames, raw, trimmed, first);
        }
    }

    @Test
    @DisplayName("the LAME tag's delay and padding are read")
    void readsTheGaplessFields() throws Exception {
        Decoded decoded = decode(LAME);

        assertEquals(576, decoded.first().encoderDelay(), "encoder delay");
        assertEquals(1122, decoded.first().encoderPadding(), "encoder padding");
        assertEquals(576 + Header.DECODER_DELAY, decoded.first().samplesToSkipAtStart());
        assertEquals(1122 - Header.DECODER_DELAY, decoded.first().samplesToSkipAtEnd());
    }

    @ParameterizedTest
    @ValueSource(strings = {LAME, LAVC})
    @DisplayName("the header frame is not handed to the caller as audio")
    void doesNotDecodeTheHeaderFrame(String file) throws Exception {
        Decoded decoded = decode(file);

        // The header says 212, meaning 212 frames of audio after itself. Before this change the
        // loop saw 213 and the extra one decoded to 1152 samples of silence.
        assertEquals(212, decoded.first().vbrFrames(), "frames the header claims");
        assertEquals(212, decoded.mpegFrames(), "frames the caller was given");
        assertEquals(212L * SAMPLES_PER_FRAME, decoded.pcmFramesRaw(), "PCM frames before trimming");
    }

    @ParameterizedTest
    @ValueSource(strings = {LAME, LAVC})
    @DisplayName("the VBR information survives the frame that carried it")
    void keepsTheVbrInformation(String file) throws Exception {
        // The header frame is consumed inside Bitstream, so what it held is copied onto the first
        // frame the caller does receive - which is where callers read it from anyway.
        Decoded decoded = decode(file);

        assertTrue(decoded.first().vbr(), "vbr");
        assertNotNull(decoded.first().vbrToc(), "toc");
        assertEquals(100, decoded.first().vbrToc().length, "toc length");
    }

    @ParameterizedTest
    @ValueSource(strings = {LAME, LAVC})
    @DisplayName("trimming leaves exactly the samples the recording contains")
    void trimsToTheRecording(String file) throws Exception {
        Decoded decoded = decode(file);

        long expected = 212L * SAMPLES_PER_FRAME - 576 - 1122;
        assertEquals(expected, decoded.pcmFramesTrimmed(), "PCM frames after trimming");
        assertEquals(1698, decoded.pcmFramesRaw() - decoded.pcmFramesTrimmed(),
                "samples of encoder padding removed");
    }

    @Test
    @DisplayName("a file with no Xing header is left exactly as it was")
    void leavesAnUntaggedFileAlone() throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(Path.of(NO_TAG)))) {
            Bitstream stream = new Bitstream(in);
            Decoder decoder = new Decoder();
            Header first = stream.readFrame();
            assertNotNull(first, "a first frame");
            SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(first, stream);

            assertFalse(first.vbr(), "not a VBR file");
            assertEquals(-1, first.encoderDelay(), "no delay to report");
            assertEquals(-1, first.encoderPadding(), "no padding to report");

            // No tag, so nothing is known and nothing is guessed: the trim keeps every sample.
            GaplessTrim trim = GaplessTrim.of(first, buffer.getChannelCount());
            assertFalse(trim.isTrimming(), "nothing to trim");
            assertEquals(buffer.getBufferLength(), trim.accept(buffer.getBufferLength()));
            assertEquals(0, trim.offset());
        }
    }
}

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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeking, which this library has described as "temporarily removed" since 1997.
 *
 * <p>The assertion that matters is the first one: samples decoded after a seek to a frame are the
 * same samples a sequential decode produces at that frame. That is what makes the bit reservoir and
 * the filter state right rather than merely plausible, and no assertion about positions or frame
 * counts can substitute for it — a seek that lands correctly and decodes from cold state gives
 * exactly the right number of samples with a click at the front of them.
 */
class BitstreamSeekTest {

    /** CBR, no Xing header: frame offsets are arithmetic, so a wrong landing is unambiguous. */
    private static final String CBR = "src/test/resources/test.mp3";

    /** VBR with a LAME tag, where frame lengths vary and offsets are not arithmetic. */
    private static final String VBR = "src/test/resources/c-major-scale_test_audacity.mp3";

    /** A frame's worth of samples per channel, Layer III. */
    private static final int SAMPLES_PER_FRAME = 1152;

    /** One decoded frame: where it started in the file, and what came out of it. */
    private record Decoded(long offset, short[] samples) {
    }

    /**
     * Decodes {@code frames} frames from the start, remembering each one's offset and samples.
     */
    private static List<Decoded> sequentially(String file, int frames) throws Exception {
        List<Decoded> decoded = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(Path.of(file)));
                Bitstream stream = new Bitstream(in)) {
            Decoder decoder = new Decoder();
            while (decoded.size() < frames) {
                long before = stream.position();
                Header header = stream.readFrame();
                if (header == null) {
                    break;
                }
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, stream);
                decoded.add(new Decoded(before, copyOf(out)));
                stream.closeFrame();
            }
        }
        return decoded;
    }

    private static short[] copyOf(SampleBuffer out) {
        short[] samples = new short[out.getBufferLength()];
        System.arraycopy(out.getBuffer(), 0, samples, 0, samples.length);
        return samples;
    }

    @Test
    @DisplayName("a file source can seek and a stream cannot, and each says so")
    void whetherItCanSeekAtAll() throws Exception {
        try (FileSource source = new FileSource(Path.of(CBR));
                Bitstream seekable = new Bitstream(source)) {
            assertTrue(seekable.isSeekable(), "a file can be repositioned");
            assertEquals(Files.size(Path.of(CBR)), seekable.length());
        }

        try (InputStream in = Files.newInputStream(Path.of(CBR));
                Bitstream notSeekable = new Bitstream(in)) {
            assertFalse(notSeekable.isSeekable(), "a stream cannot go back");
            assertEquals(Source.LENGTH_UNKNOWN, notSeekable.seek(0),
                    "and it says so rather than pretending to have moved");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {CBR, VBR})
    @DisplayName("samples after a seek are the samples a sequential decode gives at that frame")
    void seekingProducesTheSameAudio(String file) throws Exception {
        List<Decoded> reference = sequentially(file, 40);
        // Frame 30 is far enough in that the bit reservoir is genuinely in use and the IMDCT has
        // real overlap to carry. Frame 2 would pass even with a broken warm-up.
        Decoded target = reference.get(30);

        try (FileSource source = new FileSource(Path.of(file));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            // One frame first, so the decoder is initialised and the strict sync word is established
            // from this file's own version, layer and sample rate — which is what a player does too.
            Header first = stream.readFrame();
            assertNotNull(first);
            decoder.decodeFrame(first, stream);
            stream.closeFrame();

            // Start a few frames early and let the decoder say when it can reconstruct again.
            stream.seek(reference.get(26).offset());
            decoder.seekNotify();

            short[] atTarget = null;
            int starved = 0;
            long offsetOfFirstAudio = -1;
            for (int frames = 0; frames < 10 && atTarget == null; frames++) {
                long before = stream.position();
                Header header = stream.readFrame();
                assertNotNull(header, "ran out of frames while warming up");
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, stream);
                stream.closeFrame();
                if (out.getBufferLength() == 0) {
                    starved++;
                    continue;
                }
                if (offsetOfFirstAudio < 0) {
                    offsetOfFirstAudio = before;
                }
                if (before == target.offset()) {
                    atTarget = copyOf(out);
                }
            }

            assertNotNull(atTarget, "never reached the target frame");
            assertEquals(target.samples().length, atTarget.length);
            assertArrayEquals(target.samples(), atTarget,
                    "a seek must not change the audio: frame at offset " + target.offset()
                            + " decoded differently after seeking than in sequence");
            assertTrue(starved >= 0 && starved < 10, "warm-up should end, not run for ever");
        }
    }

    /**
     * Decodes the frame at reference index {@code target}, having seeked {@code lead} frames before
     * it, and says what came out.
     */
    private static short[] afterSeekingAhead(String file, List<Decoded> reference, int target,
            int lead) throws Exception {
        try (FileSource source = new FileSource(Path.of(file));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            Header first = stream.readFrame();
            assertNotNull(first);
            decoder.decodeFrame(first, stream);
            stream.closeFrame();

            stream.seek(reference.get(target - lead).offset());
            decoder.seekNotify();
            for (int frames = 0; frames <= lead + 2; frames++) {
                long before = stream.position();
                Header header = stream.readFrame();
                if (header == null) {
                    return null;
                }
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, stream);
                stream.closeFrame();
                if (before == reference.get(target).offset()) {
                    return copyOf(out);
                }
            }
            return null;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {CBR, VBR})
    @DisplayName("the frame right after the reservoir refills is still wrong, and the next is exact")
    void theWarmUpASeekNeeds(String file) throws Exception {
        // The measurement the seek contract rests on, pinned. Two independent things are missing
        // after a seek and they clear one after the other: the bit reservoir, until a frame produces
        // any samples at all, and the IMDCT overlap, for one frame beyond that. A caller that stops
        // at the first frame with samples plays a quarter-scale click.
        List<Decoded> reference = sequentially(file, 40);
        short[] wanted = reference.get(30).samples();

        int firstWithSamples = -1;
        for (int lead = 0; lead <= 6 && firstWithSamples < 0; lead++) {
            short[] got = afterSeekingAhead(file, reference, 30, lead);
            if (got != null && got.length > 0) {
                firstWithSamples = lead;
            }
        }
        assertTrue(firstWithSamples > 0,
                "some lead-in produces samples; a bit rate needing more than six frames of "
                        + "reservoir would be a fixture worth knowing about");

        assertFalse(java.util.Arrays.equals(wanted, afterSeekingAhead(file, reference, 30,
                        firstWithSamples)),
                "the first frame that can be reconstructed is still missing its IMDCT overlap, so "
                        + "it must not match — if it does, the overlap is not being reset and "
                        + "seekNotify is not doing its job");

        assertArrayEquals(wanted, afterSeekingAhead(file, reference, 30, firstWithSamples + 1),
                "one frame further back is bit-exact: reservoir refilled and overlap rebuilt");
    }

    /**
     * Seeks to the frame at {@code target} after playing {@code played} frames from the start, and
     * returns the first frame that produces samples.
     */
    private static short[] firstAudioAfterSeeking(String file, List<Decoded> reference, int target,
            int played) throws Exception {
        try (FileSource source = new FileSource(Path.of(file));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            for (int frames = 0; frames < played; frames++) {
                Header header = stream.readFrame();
                assertNotNull(header);
                decoder.decodeFrame(header, stream);
                stream.closeFrame();
            }

            stream.seek(reference.get(target).offset());
            decoder.seekNotify();
            for (int frames = 0; frames < 8; frames++) {
                Header header = stream.readFrame();
                assertNotNull(header);
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, stream);
                stream.closeFrame();
                if (out.getBufferLength() > 0) {
                    return copyOf(out);
                }
            }
            throw new AssertionError("no frame produced samples");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {CBR, VBR})
    @DisplayName("what a seek produces does not depend on where the player was before it")
    void aSeekCarriesNothingOverFromTheOldPosition(String file) throws Exception {
        // This is what seekNotify() is for, and the only way to see it. Its resets — the IMDCT
        // overlap and the synthesis filters — do not change any sample a caller is told to keep,
        // because the warm-up discards the frames they affect. What they do change is whether a seek
        // is deterministic: without them the first frame after a jump carries a fragment of overlap
        // from wherever the decoder happened to be, so the same seek gives different audio depending
        // on how much had been played. A seek bar that behaves differently on the second drag is the
        // bug this prevents.
        List<Decoded> reference = sequentially(file, 40);

        short[] havingPlayedOne = firstAudioAfterSeeking(file, reference, 30, 1);
        short[] havingPlayedTwenty = firstAudioAfterSeeking(file, reference, 30, 20);

        assertArrayEquals(havingPlayedOne, havingPlayedTwenty,
                "the same seek must give the same audio whatever was played first");
    }

    @Test
    @DisplayName("the frames a seek cannot reconstruct come back empty rather than wrong")
    void starvedFramesAreEmpty() throws Exception {
        // The signal a caller waits on. Seeking straight to a frame in the middle and decoding it
        // immediately is exactly the case main_data_begin cannot satisfy, and the library's answer
        // is no samples — which is why a caller can detect it without knowing the bit rate.
        List<Decoded> reference = sequentially(CBR, 20);

        try (FileSource source = new FileSource(Path.of(CBR));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            Header first = stream.readFrame();
            decoder.decodeFrame(first, stream);
            stream.closeFrame();

            stream.seek(reference.get(15).offset());
            decoder.seekNotify();
            Header header = stream.readFrame();
            assertNotNull(header);
            SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, stream);

            assertEquals(0, out.getBufferLength(),
                    "the first frame after a cold seek has no reservoir behind it");
        }
    }

    @Test
    @DisplayName("a seek into the middle of a frame lands on the next whole one")
    void seekingToRubbishResynchronises() throws Exception {
        List<Decoded> reference = sequentially(CBR, 20);
        long middleOfFrameTen = reference.get(10).offset() + 17;

        try (FileSource source = new FileSource(Path.of(CBR));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            Header first = stream.readFrame();
            decoder.decodeFrame(first, stream);
            stream.closeFrame();

            stream.seek(middleOfFrameTen);
            decoder.seekNotify();
            long before = stream.position();
            assertEquals(middleOfFrameTen, before, "the seek went where it was told");

            assertNotNull(stream.readFrame(), "a frame was found past the offset");
            // Frame 10 was landed in the middle of and is therefore skipped; frame 11 is read whole,
            // so the position afterwards is exactly where frame 12 starts. Named precisely rather
            // than as "some boundary": a false sync inside frame 11 would also be "some boundary"
            // by the time the reader had stumbled to the next one.
            assertEquals(reference.get(12).offset(), stream.position(),
                    "read frame 11 whole, having skipped the frame the seek landed inside");
        }
    }

    @Test
    @DisplayName("position is the offset of the next frame, so it can be seeked back to")
    void positionRoundTrips() throws Exception {
        try (FileSource source = new FileSource(Path.of(CBR));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            Header first = stream.readFrame();
            assertNotNull(first);
            decoder.decodeFrame(first, stream);
            stream.closeFrame();

            long afterFirst = stream.position();
            Header second = stream.readFrame();
            assertNotNull(second);
            int frameSize = stream.getFrameSize();
            stream.closeFrame();

            stream.seek(afterFirst);
            decoder.seekNotify();
            Header again = stream.readFrame();

            assertNotNull(again);
            assertEquals(frameSize, stream.getFrameSize(),
                    "seeking to a remembered position returns the same frame");
        }
    }

    @Test
    @DisplayName("a seek past the end finds nothing and says so")
    void seekingPastTheEnd() throws Exception {
        try (FileSource source = new FileSource(Path.of(CBR));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            decoder.decodeFrame(stream.readFrame(), stream);
            stream.closeFrame();

            long size = Files.size(Path.of(CBR));
            assertEquals(size, stream.seek(size + 10_000), "clamped to the end of the file");
            decoder.seekNotify();

            assertNull(stream.readFrame(), "there is no frame after the end");
        }
    }

    @Test
    @DisplayName("the ID3v2 tag is counted, so the first frame's offset is where it really is")
    void positionsAreFileOffsets() throws Exception {
        try (FileSource source = new FileSource(Path.of(VBR));
                Bitstream stream = new Bitstream(source)) {
            // Before any frame is read, the position is past the tag: a position this class reports
            // is comparable with a file offset, which is the whole point of reporting one.
            assertEquals(stream.getHeaderPosition(), stream.position());

            byte[] file = Files.readAllBytes(Path.of(VBR));
            long firstFrame = stream.position();
            assertEquals((byte) 0xFF, file[(int) firstFrame],
                    "a frame starts with a sync byte at the offset this reports");
        }
    }

    @Test
    @DisplayName("a closed bitstream refuses to seek instead of reading a closed file")
    void seekingAfterClose() throws Exception {
        FileSource source = new FileSource(Path.of(CBR));
        Bitstream stream = new Bitstream(source);
        stream.close();

        BitstreamException refused = org.junit.jupiter.api.Assertions.assertThrows(
                BitstreamException.class, () -> stream.seek(0));
        assertEquals(Bitstream.STREAM_ERROR, refused.getErrorCode());
    }

    @Test
    @DisplayName("seeking does not re-read the Xing frame as audio")
    void theVbrHeaderIsNotFoundTwice() throws Exception {
        // The Xing/Info frame is consumed at the head of the file. A seek that let firstFrame stay
        // true would run the VBR parse again on whatever frame it landed on and believe the result.
        //
        // This can be asserted on one Header because Bitstream returns the same instance from every
        // readFrame(), so a re-parse after the seek would overwrite the fields read at the head of
        // the file. bitrate() is what a caller consumes: for a file with a Xing tag it is computed
        // from h_vbr_frames and h_vbr_bytes and does not vary per frame, so it changes if and only
        // if those fields are rewritten.
        try (FileSource source = new FileSource(Path.of(VBR));
                Bitstream stream = new Bitstream(source)) {
            Decoder decoder = new Decoder();
            Header first = stream.readFrame();
            assertNotNull(first);
            assertTrue(first.vbr(), "the fixture has a Xing header");
            decoder.decodeFrame(first, stream);
            stream.closeFrame();
            int bitrateFromTheTag = first.bitrate();

            stream.seek(stream.position());
            decoder.seekNotify();
            Header afterSeek = stream.readFrame();

            assertNotNull(afterSeek);
            assertEquals(bitrateFromTheTag, first.bitrate(),
                    "what the Xing tag said is unchanged by a seek");
            assertTrue(afterSeek.framesize > 0, "and the frame after a seek is an audio frame");
        }
    }
}

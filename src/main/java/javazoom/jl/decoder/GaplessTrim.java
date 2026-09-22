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

/**
 * Drops the samples an MP3 encoder added that are not part of the recording.
 *
 * <p>An MP3 encoder pads: it prepends a delay because its filter bank needs samples before it can
 * emit any, and it appends whatever is needed to fill the last frame. Both amounts are written
 * into the LAME tag, and a decoder that plays them produces audio the recording does not contain —
 * about 26 ms at the front of a typical file and a few more at the end. On a gapless album that is
 * audible at every track boundary.
 *
 * <p>Feed each decoded buffer to {@link #accept} in order and write back the range it returns:
 *
 * <pre>{@code
 * int keep = trim.accept(output.getBufferLength());
 * if (keep > 0) {
 *     device.write(output.getBuffer(), trim.offset(), keep);
 * }
 * }</pre>
 *
 * <p>Stateful and not thread-safe; one instance belongs to one decode of one stream.
 *
 * @since 1.0.5
 */
public final class GaplessTrim {

    /** Samples per MPEG frame for Layer III, which is what carries a LAME tag. */
    private static final int SAMPLES_PER_FRAME = 1152;

    /** First element of the interleaved stream that belongs to the recording. */
    private final long first;

    /** One past the last element that belongs to the recording, or -1 when the end is unknown. */
    private final long last;

    /** Elements handed to {@link #accept} so far. */
    private long position;

    /** Offset into the buffer most recently accepted. */
    private int offset;

    private GaplessTrim(long first, long last) {
        this.first = first;
        this.last = last;
    }

    /**
     * A trim for a stream described by {@code header}, or one that drops nothing.
     *
     * <p>Drops nothing when the file carries no LAME tag, which is the honest answer rather than a
     * guess: without the tag there is no way to know what the encoder added. Drops only from the
     * start when the tag is there but the VBR header omits its frame count, since the end cannot
     * be located without it.
     *
     * @param header the first frame's header, carrying the VBR information
     * @param channels channels in the decoded stream, as the stream interleaves them
     * @return a trim, never null
     */
    public static GaplessTrim of(Header header, int channels) {
        if (header == null || channels < 1) {
            return new GaplessTrim(0, -1);
        }
        long first = (long) header.samplesToSkipAtStart() * channels;
        int frames = header.vbrFrames();
        if (frames <= 0) {
            return new GaplessTrim(first, -1);
        }
        long total = (long) frames * SAMPLES_PER_FRAME * channels;
        long last = total - (long) header.samplesToSkipAtEnd() * channels;
        return new GaplessTrim(first, Math.max(first, last));
    }

    /**
     * Accepts the next decoded buffer and returns how many of its elements to keep.
     *
     * <p>Zero is an ordinary answer and means the whole buffer falls inside the padding — which is
     * the case for the first buffer or two of every file. Keep calling; audio follows.
     *
     * @param bufferLength elements in the buffer just decoded
     * @return elements to write, starting at {@link #offset()}
     */
    public int accept(int bufferLength) {
        long start = Math.max(position, first);
        long end = position + bufferLength;
        if (last >= 0) {
            end = Math.min(end, last);
        }
        position += bufferLength;
        if (end <= start) {
            offset = 0;
            return 0;
        }
        offset = (int) (start - (position - bufferLength));
        return (int) (end - start);
    }

    /**
     * Where the kept range begins in the buffer last passed to {@link #accept}.
     *
     * @return offset in elements
     */
    public int offset() {
        return offset;
    }

    /**
     * Whether this trim will ever drop anything.
     *
     * @return false when the file said nothing about its padding
     */
    public boolean isTrimming() {
        return first > 0 || last >= 0;
    }
}

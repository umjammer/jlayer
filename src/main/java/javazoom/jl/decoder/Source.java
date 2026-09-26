/*
 * 11/19/04        1.0 moved to LGPL.
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

import java.io.IOException;


/**
 * A data source a {@link Bitstream} can read, and sometimes reposition.
 * <p>
 * Declared in 1999 and left unimplemented; {@link Bitstream} now takes one, which is what gives it
 * the seek that {@code Bitstream}'s own header comment has described as "temporarily removed" since
 * 1997. A source that cannot reposition answers {@code false} to {@link #isSeekable} and is read
 * straight through, exactly as an {@link java.io.InputStream} always was.
 *
 * @see InputStreamSource
 * @see FileSource
 */
public interface Source {

    /** {@link #length} and {@link #tell} answer this when the source cannot say. */
    long LENGTH_UNKNOWN = -1;

    /**
     * Reads up to {@code len} bytes, as {@link java.io.InputStream#read(byte[], int, int)} does.
     *
     * @return the number of bytes read, or -1 at the end of the source
     */
    int read(byte[] b, int offs, int len) throws IOException;

    /** Whether a read would block. Advisory: a source that cannot tell says {@code true}. */
    boolean willReadBlock();

    /**
     * Whether {@link #seek} does anything.
     * <p>
     * A file says yes; a socket, a pipe or a decoding stream says no. Asked before a seek is
     * offered to a listener rather than after one is attempted, so that a player can grey out a
     * seek bar instead of discovering the answer under the user's finger.
     */
    boolean isSeekable();

    /** The total length in bytes, or {@link #LENGTH_UNKNOWN}. */
    long length();

    /** The position the next read will start at, or {@link #LENGTH_UNKNOWN}. */
    long tell();

    /**
     * Moves to {@code pos}, counted in bytes from the start of the source.
     *
     * @return the position actually reached, which is {@code pos} for a file, or
     *         {@link #LENGTH_UNKNOWN} for a source that cannot reposition
     */
    long seek(long pos);
}

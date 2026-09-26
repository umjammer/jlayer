/*
 * 11/19/04        1.0 moved to LGPL.
 * 12/12/99        Initial version.    mdm@techie.com
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
import java.io.InputStream;


/**
 * A {@link Source} over an {@link InputStream}, which is what every caller that hands
 * {@link Bitstream} a stream gets.
 * <p>
 * Not seekable, because an {@code InputStream} is not: it cannot go back, and pretending otherwise
 * by buffering the whole thing would turn a five-minute track into forty megabytes of heap. A
 * caller that wants to seek passes a {@link FileSource} instead.
 *
 * @author MDM
 */
public class InputStreamSource implements Source {

    private final InputStream in;

    /** Bytes handed out so far, which is the one position question a forward-only source can answer. */
    private long position;

    public InputStreamSource(InputStream in) {
        if (in == null)
            throw new NullPointerException("in");

        this.in = in;
    }

    @Override
    public int read(byte[] b, int offs, int len) throws IOException {
        int read = in.read(b, offs, len);
        if (read > 0) {
            position += read;
        }
        return read;
    }

    @Override
    public boolean willReadBlock() {
        return true;
    }

    @Override
    public boolean isSeekable() {
        return false;
    }

    /**
     * How far into the stream the next read will start.
     *
     * <p>Countable even here: a stream that cannot go back still knows how far it has come, and a
     * caller that wants to remember a position for a later run can have it.
     */
    @Override
    public long tell() {
        return position;
    }

    @Override
    public long seek(long to) {
        return LENGTH_UNKNOWN;
    }

    @Override
    public long length() {
        return LENGTH_UNKNOWN;
    }
}

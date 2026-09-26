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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * A {@link Source} over a file, which is the one that can seek.
 *
 * <p>Unbuffered on purpose: {@link Bitstream} puts a {@link java.io.BufferedInputStream} in front of
 * whatever it is given, so a second buffer here would be a second copy of every byte and, worse, a
 * second thing to invalidate when the position moves.
 *
 * <p>The caller owns the file and closes it. {@code Bitstream.close()} closes the stream it wrapped,
 * which for a source is this object's {@link #close}.
 */
public class FileSource implements Source, AutoCloseable {

    private final RandomAccessFile file;

    private final long length;

    public FileSource(File file) throws IOException {
        this.file = new RandomAccessFile(file, "r");
        this.length = this.file.length();
    }

    public FileSource(Path path) throws IOException {
        this(path.toFile());
    }

    @Override
    public int read(byte[] b, int offs, int len) throws IOException {
        return file.read(b, offs, len);
    }

    /**
     * Whether a read would block.
     *
     * <p>{@code false}: a local file's read is a page fault at worst. A file on a network share can
     * block for a long time and still answers {@code false}, because there is no way to ask it —
     * which is why this is advisory in {@link Source} rather than a guarantee.
     */
    @Override
    public boolean willReadBlock() {
        return false;
    }

    @Override
    public boolean isSeekable() {
        return true;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public long tell() {
        try {
            return file.getFilePointer();
        } catch (IOException cannotTell) {
            return LENGTH_UNKNOWN;
        }
    }

    /**
     * Moves to {@code pos}.
     *
     * <p>Clamped to the file rather than refused: a seek past the end is a caller asking for the
     * end, and a decoder that lands there reads no frames and stops, which is the same thing as
     * having played to the end.
     */
    @Override
    public long seek(long pos) {
        try {
            long wanted = Math.max(0, Math.min(pos, length));
            file.seek(wanted);
            return wanted;
        } catch (IOException cannotSeek) {
            return LENGTH_UNKNOWN;
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}

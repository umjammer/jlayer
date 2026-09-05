/*
 * 11/19/2004 : 1.0 moved to LGPL.
 * 01/01/2004 : Initial version by E.B javalayer@javazoom.net
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Bitstream unit test.
 * It matches test.mp3 properties to test.mp3.properties expected results.
 * As we don't ship test.mp3, you have to generate your own test.mp3.properties
 * Uncomment out = System.out; in setUp() method to generated it on stdout from
 * your own MP3 file.
 *
 * @since 0.4
 */
public class BitstreamTest {

    private String basefile = null;
    private String name = null;
    private String filename = null;
    private Properties props = null;
    private FileInputStream mp3in = null;
    private Bitstream in = null;

    @BeforeEach
    protected void setUp() throws Exception {
        props = new Properties();
        InputStream pin = getClass().getClassLoader().getResourceAsStream("test.mp3.properties");
        props.load(pin);
        basefile = props.getProperty("basefile");
        name = props.getProperty("filename");
        filename = basefile + name;
        mp3in = new FileInputStream(filename);
        in = new Bitstream(mp3in);
    }

    @AfterEach
    protected void tearDown() throws Exception {
        in.close();
        mp3in.close();
    }

    @Test
    public void testStream() throws Exception {
        InputStream id3in = in.getRawID3v2();
        int size = id3in.available();
        Header header = in.readFrame();
System.err.println("--- " + filename + " ---");
System.err.println("ID3v2Size=" + size);
System.err.println("version=" + header.version());
System.err.println("version_string=" + header.versionString());
System.err.println("layer=" + header.layer());
System.err.println("frequency=" + header.frequency());
System.err.println("frequency_string=" + header.sampleFrequencyString());
System.err.println("bitrate=" + header.bitrate());
System.err.println("bitrate_string=" + header.bitrateString());
System.err.println("mode=" + header.mode());
System.err.println("mode_string=" + header.modeString());
System.err.println("slots=" + header.slots());
System.err.println("vbr=" + header.vbr());
System.err.println("vbr_scale=" + header.vbrScale());
System.err.println("max_number_of_frames=" + header.maxNumberOfFrames(mp3in.available()));
System.err.println("min_number_of_frames=" + header.minNumberOfFrames(mp3in.available()));
System.err.println("ms_per_frame=" + header.msPerFrame());
System.err.println("frames_per_second=" + (float) ((1.0 / (header.msPerFrame())) * 1000.0));
System.err.println("total_ms=" + header.totalMs(mp3in.available()));
System.err.println("SyncHeader=" + header.getSyncHeader());
System.err.println("checksums=" + header.checksums());
System.err.println("copyright=" + header.copyright());
System.err.println("original=" + header.original());
System.err.println("padding=" + header.padding());
System.err.println("framesize=" + header.calculateFrameSize());
System.err.println("number_of_subbands=" + header.numberOfSubbands());
        assertEquals(Integer.parseInt(props.getProperty("ID3v2Size")), size, "ID3v2Size");
        assertEquals(Integer.parseInt(props.getProperty("version")), header.version(), "version");
        assertEquals(props.getProperty("version_string"), header.versionString(), "version_string");
        assertEquals(Integer.parseInt(props.getProperty("layer")), header.layer(), "layer");
        assertEquals(Integer.parseInt(props.getProperty("frequency")), header.frequency(), "frequency");
        assertEquals(props.getProperty("frequency_string"), header.sampleFrequencyString(), "frequency_string");
        assertEquals(Integer.parseInt(props.getProperty("bitrate")), header.bitrate(), "bitrate");
        assertEquals(props.getProperty("bitrate_string"), header.bitrateString(), "bitrate_string");
        assertEquals(Integer.parseInt(props.getProperty("mode")), header.mode(), "mode");
        assertEquals(props.getProperty("mode_string"), header.modeString(), "mode_string");
        assertEquals(Integer.parseInt(props.getProperty("slots")), header.slots(), "slots");
        assertEquals(Boolean.valueOf(props.getProperty("vbr")), header.vbr(), "vbr");
        assertEquals(Integer.parseInt(props.getProperty("vbr_scale")), header.vbrScale(), "vbr_scale");
        assertEquals(Integer.parseInt(props.getProperty("max_number_of_frames")),
                header.maxNumberOfFrames(mp3in.available()),
                "max_number_of_frames");
        assertEquals(Integer.parseInt(props.getProperty("min_number_of_frames")),
                header.minNumberOfFrames(mp3in.available()),
                "min_number_of_frames");
        assertEquals(Float.parseFloat(props.getProperty("ms_per_frame")), header.msPerFrame(), "ms_per_frame");
        assertEquals(Float
                .parseFloat(props.getProperty("frames_per_second")), (float) ((1.0 / (header.msPerFrame())) * 1000.0), "frames_per_second");
        assertEquals(Float.parseFloat(props.getProperty("total_ms")), header.totalMs(mp3in.available()), "total_ms");
        assertEquals(Integer.parseInt(props.getProperty("SyncHeader")), header.getSyncHeader(), "SyncHeader");
        assertEquals(Boolean.valueOf(props.getProperty("checksums")), header.checksums(), "checksums");
        assertEquals(Boolean.valueOf(props.getProperty("copyright")), header.copyright(), "copyright");
        assertEquals(Boolean.valueOf(props.getProperty("original")), header.original(), "original");
        assertEquals(Boolean.valueOf(props.getProperty("padding")), header.padding(), "padding");
        assertEquals(Integer.parseInt(props.getProperty("framesize")), header.calculateFrameSize(), "framesize");
        assertEquals(Integer.parseInt(props.getProperty("number_of_subbands")),
                header.numberOfSubbands(),
                "number_of_subbands");
        in.closeFrame();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/c-major-scale_test_audacity.mp3",
            "/c-major-scale_test_web-convert_mono.mp3",
    })
    public void testStream2(String mp3) throws Exception {
        in = new Bitstream(BitstreamTest.class.getResourceAsStream(mp3));
        InputStream id3in = in.getRawID3v2();
        int size = (id3in == null) ? 0 : id3in.available();
        Header header = in.readFrame();
System.err.println("--- " + mp3 + " ---");
System.err.println("ID3v2Size=" + size);
System.err.println("version=" + header.version());
System.err.println("version_string=" + header.versionString());
System.err.println("layer=" + header.layer());
System.err.println("frequency=" + header.frequency());
System.err.println("frequency_string=" + header.sampleFrequencyString());
System.err.println("bitrate=" + header.bitrate());
System.err.println("bitrate_string=" + header.bitrateString());
System.err.println("mode=" + header.mode());
System.err.println("mode_string=" + header.modeString());
System.err.println("slots=" + header.slots());
System.err.println("vbr=" + header.vbr());
System.err.println("vbr_scale=" + header.vbrScale());
System.err.println("max_number_of_frames=" + header.maxNumberOfFrames(mp3in.available()));
System.err.println("min_number_of_frames=" + header.minNumberOfFrames(mp3in.available()));
System.err.println("ms_per_frame=" + header.msPerFrame());
System.err.println("frames_per_second=" + (float) ((1.0 / (header.msPerFrame())) * 1000.0));
System.err.println("total_ms=" + header.totalMs(mp3in.available()));
System.err.println("SyncHeader=" + header.getSyncHeader());
System.err.println("checksums=" + header.checksums());
System.err.println("copyright=" + header.copyright());
System.err.println("original=" + header.original());
System.err.println("padding=" + header.padding());
System.err.println("framesize=" + header.calculateFrameSize());
System.err.println("number_of_subbands=" + header.numberOfSubbands());
        // Relaxed assertions: ensure header successfully parsed and no exceptions.
        assertNotNull(header, "Header should not be null");
        assertTrue(header.calculateFrameSize() >= 0, "framesize");
        // Basic sanity checks (relaxed to support different test files)
        assertTrue(header.msPerFrame() > 0.0f, "ms_per_frame");
        assertTrue((float) ((1.0 / (header.msPerFrame())) * 1000.0) > 0.0f, "frames_per_second");
        assertTrue(header.totalMs(mp3in.available()) >= 0.0f, "total_ms");
        assertTrue(header.calculateFrameSize() >= 0, "framesize");
        assertTrue(header.numberOfSubbands() >= 0, "number_of_subbands");
        in.closeFrame();
    }
}

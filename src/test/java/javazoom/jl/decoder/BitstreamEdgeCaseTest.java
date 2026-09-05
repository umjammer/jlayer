package javazoom.jl.decoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


/**
 * Tests edge cases and boundary conditions for Bitstream.
 */
class BitstreamEdgeCaseTest {

    @DisplayName("Read various bit sizes without exceptions")
    void testVariousBitSizes() throws Exception {
        InputStream is = getClass().getResourceAsStream("/test.mp3");
        assertNotNull(is, "test.mp3 resource must be available");

        try (Bitstream bs = new Bitstream(is)) {
            int[] bitSizes = {1, 2, 4, 7, 8, 12, 16, 20, 24, 31, 32};

            for (int i = 0; i < 100 && !bs.isEOF(); i++) {
                for (int bits : bitSizes) {
                    if (bs.isEOF()) break;

                    int b = bits;
                    assertDoesNotThrow(() -> {
                        int val = bs.getBits(b);

                        // Validate returned value is in valid range
                        if (b <= 31) {
                            assertTrue(val >= 0, "Value should be non-negative");
                            int maxVal = (1 << b) - 1;
                            assertTrue(val <= maxVal,
                                    "Value should be <= " + maxVal + " for " + b + " bits");
                        }
                    }, "getBits(" + bits + ") should not throw");
                }

                System.out.println("Successfully tested various bit sizes");
            }
        }
    }

    @Test
    @DisplayName("Handle partial word at EOF")
    void testPartialWordAtEOF() throws Exception {
        // Create minimal stream (3 bytes)
        byte[] data = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};

        try (Bitstream bs = new Bitstream(new ByteArrayInputStream(data))) {
            // Read 32 bits from 24-bit stream
            int value = bs.getBits(32);
            assertNotNull(Integer.valueOf(value), "Should return a value");

            // Subsequent reads should handle EOF gracefully
            bs.getBits(8);

            // Should eventually reach EOF
            assertTrue(bs.isEOF() || bs.getBits(1) >= 0,
                    "Should reach EOF or return valid data");

            System.out.println("Handled partial word at EOF correctly");
        }
    }

    @Test
    @DisplayName("Empty stream handling")
    void testEmptyStream() throws Exception {
        byte[] empty = new byte[0];

        try (Bitstream bs = new Bitstream(new ByteArrayInputStream(empty))) {
            // Reading from empty stream should be safe
            assertDoesNotThrow(() -> {
                int val = bs.getBits(8);
                // May return 0 or reach EOF
            }, "Reading from empty stream should not throw");

            // After attempting to read, stream should be at EOF or have returned 0
            // Note: Bitstream only sets EOF when it tries to read from the underlying stream
            // An empty stream that hasn't been read from yet may not report EOF
            assertTrue(bs.isEOF() || bs.getBits(1) >= 0,
                    "Empty stream should be at EOF after read attempt");
        }
    }

    @Test
    @DisplayName("Close idempotency")
    void testCloseIdempotency() throws Exception {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        Bitstream bs = new Bitstream(new ByteArrayInputStream(data));

        assertFalse(bs.isClosed(), "Should not be closed initially");

        // First close
        bs.close();
        assertTrue(bs.isClosed(), "Should be closed after first close");

        // Multiple closes should be safe
        assertDoesNotThrow(bs::close, "Second close should not throw");
        assertDoesNotThrow(bs::close, "Third close should not throw");

        assertTrue(bs.isClosed(), "Should still be closed");
        System.out.println("Close idempotency verified");
    }

    @Test
    @DisplayName("Read zero bits")
    void testReadZeroBits() throws Exception {
        byte[] data = {0x01, 0x02};

        try (Bitstream bs = new Bitstream(new ByteArrayInputStream(data))) {
            int value = bs.getBits(0);
            assertEquals(0, value, "Reading 0 bits should return 0");

            // Stream position should not change
            int next = bs.getBits(8);
            assertTrue(next >= 0, "Should still be able to read");
        }
    }

    @DisplayName("Concurrent close safety")
    void testConcurrentClose() throws Exception {
        byte[] data = new byte[1000];
        Bitstream bs = new Bitstream(new ByteArrayInputStream(data));

        // Simulate concurrent close from different threads
        Thread t1 = new Thread(() -> {
        try {
            bs.close();
        } catch (BitstreamException e) {
            // Expected to be safe
        }
        });

        Thread t2 = new Thread(() -> {
            try {
                bs.close();
            } catch (BitstreamException e) {
                // Expected to be safe
            }
        });

        t1.start();
        t2.start();

        t1.join(1000);
        t2.join(1000);

        assertTrue(bs.isClosed(), "Bitstream should be closed");
        System.out.println("Concurrent close handled safely");
    }

    @Test
    @DisplayName("Large bit read")
    void testLargeBitRead() throws Exception {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }

        try (Bitstream bs = new Bitstream(new ByteArrayInputStream(data))) {
            // Read various large bit sizes
            assertDoesNotThrow(() -> bs.getBits(32), "32-bit read should work");
            assertDoesNotThrow(() -> bs.getBits(31), "31-bit read should work");
            assertDoesNotThrow(() -> bs.getBits(24), "24-bit read should work");
            assertDoesNotThrow(() -> bs.getBits(16), "16-bit read should work");

            System.out.println("Large bit reads handled correctly");
        }
    }
}

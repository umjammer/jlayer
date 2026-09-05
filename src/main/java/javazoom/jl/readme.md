# jlayer — API Notes

This document summarizes the higher-level, convenience API additions and testing guidance added to the codebase.

## Bitstream (javazoom.jl.decoder.Bitstream)
- `int getBits(int n)` — Read `n` bits from the MPEG bitstream. Returns an int containing the unsigned value of the requested bits. Method is now robust to partial-word reads and EOF conditions; callers should check `isEOF()` when necessary.
- `boolean isEOF()` — Returns true when input reached end-of-stream.
- `byte[] getFrameBytes()` — Convenience accessor returning the raw bytes of the frame last parsed (if available).

Usage example:

```
try (Bitstream bs = new Bitstream(inputStream)) {
    while (!bs.isEOF()) {
        int v = bs.getBits(16);
        // use v...
    }
}
```

## Decoder (javazoom.jl.decoder.Decoder)
- `decodeNextFrame(Bitstream)` — Convenience method to decode the next frame from a `Bitstream` and return decoded PCM samples in the project `Obuffer`/`SampleBuffer` mechanism.
- `decodeNextFrameToPCM(Bitstream)` — Convenience shim that returns PCM bytes for the next frame.
- `convertToWav(...)` — Helper convenience that coordinates decoding and writes a WAV file (keeps older APIs untouched).

## Tests
- `src/test/java/javazoom/jl/decoder/BitstreamEdgeCaseTest.java` — New JUnit tests exercising `getBits(...)` with various bit widths and partial-stream EOF conditions.

## Generating Javadoc
Run the standard Maven Javadoc goal to produce API docs:

```bash
mvn javadoc:javadoc
```

The generated site will appear in `target/site/apidocs`.

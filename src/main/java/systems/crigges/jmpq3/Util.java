package systems.crigges.jmpq3;

import systems.crigges.jmpq3.security.MPQHashGenerator;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

public class Util {

    /**
     * Utility method to fill a buffer from the given channel.
     *
     * @param buffer buffer to fill.
     * @param src    channel to fill from.
     * @throws IOException  if an exception occurs when reading.
     * @throws EOFException if EoF is encountered before buffer is full or channel is non
     *                      blocking.
     */
    @Deprecated
    public static void readFully(ByteBuffer buffer, ReadableByteChannel src) throws IOException {
        while (buffer.hasRemaining()) {
            if (src.read(buffer) < 1)
                throw new EOFException("Cannot read enough bytes.");
        }
    }

    /**
     * Utility method to fill a buffer from the given channel.
     *
     * @param buffer buffer to fill.
     * @param src    channel to fill from.
     * @param offset the offset in the file channel
     * @throws IOException  if an exception occurs when reading.
     * @throws EOFException if EoF is encountered before buffer is full or channel is non
     *                      blocking.
     */
    public static void readFully(ByteBuffer buffer, FileChannel src, long offset) throws IOException {
        int read = src.read(buffer, offset);
        if(read != buffer.capacity())
            throw new IllegalStateException("Could not read all bytes");
    }

    public static <T extends Enum<T>> Set<T> toImmutableEnumSet(@Nonnull Collection<T> values) {
        if (values.isEmpty()) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    public static long calculateFileKey(String name) {
        // generate file key
        final MPQHashGenerator key1Gen = MPQHashGenerator.getTableKey1Generator();
        key1Gen.process(name);
        final int key1 = key1Gen.getHash();
        final MPQHashGenerator key2Gen = MPQHashGenerator.getTableKey2Generator();
        key2Gen.process(name);
        final int key2 = key2Gen.getHash();
        return ((long) key2 << 32) | Integer.toUnsignedLong(key1);
    }

    private Util() {
    }
}

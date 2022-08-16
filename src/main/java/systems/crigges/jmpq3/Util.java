package systems.crigges.jmpq3;

import systems.crigges.jmpq3.security.MPQHashGenerator;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Util {

    public static void readFully(ByteBuffer buffer, FileChannel src, long offset) throws IOException {
        while(buffer.hasRemaining()) {
            if(src.read(buffer, offset + buffer.position()) < 1)
                throw new IOException("Could not read all bytes");
        }
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

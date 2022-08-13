package systems.crigges.jmpq3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Hashtable bucket.
 *
 * @param key               64 bit file key.
 * @param locale            File locale in the form of a Windows Language ID.
 * @param blockTableIndex   Block table index for file data. Some negative magic
 *                          numbers are used to represent the bucket state.
 */
public record Bucket(long key, short locale, int blockTableIndex) {

    public static Bucket readFromBuffer(ByteBuffer src) {
        src.order(ByteOrder.LITTLE_ENDIAN);
        long key = src.getLong();
        short locale = src.getShort();
        src.getShort(); // platform not used
        int blockTableIndex = src.getInt();

        return new Bucket(key, locale, blockTableIndex);
    }

    public void writeToBuffer(ByteBuffer dest) {
        dest.order(ByteOrder.LITTLE_ENDIAN);
        dest.putLong(key);
        dest.putShort(locale);
        dest.putShort((short) 0); // platform not used
        dest.putInt(blockTableIndex);
    }
}
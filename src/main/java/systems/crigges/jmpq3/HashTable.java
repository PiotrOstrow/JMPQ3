package systems.crigges.jmpq3;

import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * MPQ hash table. Used to map file paths to block table indices.
 * <p>
 * Supports localised files using Windows Language ID codes. When requesting a
 * localised mapping it will prioritise finding the requested locale, then the
 * default locale and finally the first locale found.
 * <p>
 * File paths are uniquely identified using a combination of a 64 bit key and
 * their bucket position. As such the hash table does not know what file paths
 * it contains. To get around this limitation MPQs often contain a list file
 * which lists all the file paths used by the hash table. The list file can be
 * used to populate a different capacity hash table with the same mappings.
 */
@Immutable
public class HashTable {

    /**
     * Magic block number representing a hash table entry which is not in use.
     */
    private static final int ENTRY_UNUSED = -1;

    /**
     * Magic block number representing a hash table entry which was deleted.
     */
    private static final int ENTRY_DELETED = -2;

    /**
     * The default file locale, US English.
     */
    public static final short DEFAULT_LOCALE = 0;

    private final List<Bucket> buckets;

    private HashTable(List<Bucket> buckets) {
        this.buckets = buckets;
    }

    /**
     * Internal method to get a bucket index for the specified file.
     *
     * @param file file identifier.
     * @return the bucket index used, or -1 if the file has no mapping.
     */
    private int getFileEntryIndex(FileIdentifier file) {
        final int mask = buckets.size() - 1;
        final int start = file.offset() & mask;
        int bestEntryIndex = -1;
        for (int c = 0; c < buckets.size(); c++) {
            final int index = start + c & mask;
            final Bucket entry = buckets.get(index);

            if (entry.blockTableIndex() == ENTRY_UNUSED)
                break;

            if (entry.blockTableIndex() != ENTRY_DELETED && entry.key() == file.key()) {
                if (entry.locale() == file.locale()) {
                    return index;
                } else if (bestEntryIndex == -1 || entry.locale() == DEFAULT_LOCALE) {
                    bestEntryIndex = index;
                }
            }
        }

        return bestEntryIndex;
    }

    /**
     * Internal method to get a bucket for the specified file.
     *
     * @param file file identifier.
     * @return the file bucket, or null if the file has no mapping.
     */
    private Bucket getFileEntry(FileIdentifier file) {
        final int index = getFileEntryIndex(file);
        return index != -1 ? buckets.get(index) : null;
    }

    /**
     * Check if the specified file path has a mapping in this hash table.
     * <p>
     * A file path has a mapping if it has been mapped for at least 1 locale.
     *
     * @param file file path.
     * @return true if the hash table has a mapping for the file, otherwise
     *         false.
     */
    public boolean hasFile(String file) {
        return getFileEntryIndex(new FileIdentifier(file, DEFAULT_LOCALE)) != -1;
    }

    /**
     * Get the block table index for the specified file.
     *
     * @param name file path name.
     * @return block table index.
     * @throws IOException
     *             if the specified file has no mapping.
     */
    public int getBlockIndexOfFile(String name) throws IOException {
        return getFileBlockIndex(name, DEFAULT_LOCALE);
    }

    /**
     * Get the block table index for the specified file.
     * <p>
     * Locale parameter is only a recommendation and the return result might be
     * for a different locale. When multiple locales are available the order of
     * priority for selection is the specified locale followed by the default
     * locale and lastly the first locale found.
     *
     * @param name   file path name.
     * @param locale file locale.
     * @return block table index.
     * @throws IOException
     *             if the specified file has no mapping.
     */
    public int getFileBlockIndex(String name, short locale) throws IOException {
        final FileIdentifier fid = new FileIdentifier(name, locale);
        Bucket entry = getFileEntry(fid);

        if (entry == null)
            throw new JMpqException("File Not Found <" + name + ">.");
        else if (entry.blockTableIndex() < 0)
            throw new JMpqException("File has invalid block table index <" + entry.blockTableIndex() + ">.");

        return entry.blockTableIndex();
    }

    public static HashTable fromBuffer(ByteBuffer src, int hashSize) {
        if (hashSize <= 0 || (hashSize & (hashSize - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be power of 2.");
        }

        List<Bucket> buckets = new ArrayList<>();
        for(int i = 0; i < hashSize; i++) {
            Bucket bucket = Bucket.readFromBuffer(src);
            buckets.add(bucket);
        }

        return new HashTable(buckets);
    }
}
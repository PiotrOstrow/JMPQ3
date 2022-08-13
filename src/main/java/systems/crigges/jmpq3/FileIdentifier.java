package systems.crigges.jmpq3;

import systems.crigges.jmpq3.security.MPQHashGenerator;

import static systems.crigges.jmpq3.Util.calculateFileKey;

/**
 * Plain old data class to internally represent a uniquely identifiable
 * file.
 * <p>
 * Used to cache file name hash results.
 *
 * @param key       64 bit file key.
 * @param offset    Offset into hash table bucket array to start search.
 * @param locale    File locale in the form of a Windows Language ID.
 */
public record FileIdentifier(
    long key, int offset, short locale
) {

    public FileIdentifier(String name, short locale) {
        this(calculateFileKey(name), getOffsetHash(name), locale);
    }

    private static int getOffsetHash(String name) {
        MPQHashGenerator offsetGen = MPQHashGenerator.getTableOffsetGenerator();
        offsetGen.process(name);
        return offsetGen.getHash();
    }
}

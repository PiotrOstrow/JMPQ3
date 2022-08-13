package systems.crigges.jmpq3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.crigges.jmpq3.security.MPQEncryption;
import systems.crigges.jmpq3.security.MPQHashGenerator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static systems.crigges.jmpq3.Block.ENCRYPTED;

/**
 * <p>
 * Provides an interface for reading MPQ archive files. MPQ archive files contain
 * a virtual file system used by some old games to hold data, primarily those
 * from Blizzard Entertainment.
 * <p>
 * This class is effectively immutable, and therefore safe to use concurrently. The suggested approach for
 * extracting multiple files is to limit IO to a single thread (meaning {@link JMpqArchive#getMpqFile(String)}
 * and by extension {@link JMpqArchive#getListFile()}), and utilize multiple threads for decrypting and/or
 * decompressing files using  {@link MpqFile#extractToBytes()} or {@link MpqFile#extractToOutputStream(OutputStream)}.
 * <p>
 * For platform independence the implementation is pure Java.
 */
@ThreadSafe
public class JMpqArchive implements AutoCloseable {

    public static final int ARCHIVE_HEADER_MAGIC = ByteBuffer.wrap(new byte[]{'M', 'P', 'Q', 0x1A}).order(ByteOrder.LITTLE_ENDIAN).getInt();
    public static final int USER_DATA_HEADER_MAGIC = ByteBuffer.wrap(new byte[]{'M', 'P', 'Q', 0x1B}).order(ByteOrder.LITTLE_ENDIAN).getInt();

    private static final String LIST_FILE = "(listfile)";

    /**
     * Encryption key for hash table data.
     */
    private static final int KEY_HASH_TABLE = MPQHashGenerator.generateFileKey("(hash table)");

    private final FileChannel fileChannel;

    private long headerOffset;
    private int headerSize;
    private long archiveSize;
    private int formatVersion;
    private int discBlockSize;
    private long hashTablePosition;
    private long blockTablePosition;
    private int hashSize;
    private int blockSize;

    private HashTable hashTable;
    private BlockTable blockTable;

    private final Set<MPQOpenOption> mpqOpenOptions;

    public JMpqArchive(File mpqArchive, MPQOpenOption... openOptions) throws IOException {
        this(mpqArchive.toPath(), openOptions);
    }

    public JMpqArchive(Path mpqArchive, MPQOpenOption... openOptions) throws IOException {
        this(FileChannel.open(mpqArchive, StandardOpenOption.READ), openOptions);
    }

    private JMpqArchive(FileChannel fileChannel, MPQOpenOption... openOptions) throws IOException {
        this.fileChannel = fileChannel;
        this.mpqOpenOptions = Util.toImmutableEnumSet(List.of(openOptions));

        readMpq();
    }

    private void readMpq() throws IOException {
        headerOffset = searchHeader();

        readHeaderSize();

        readHeader();

        checkLegacyCompat();

        readHashTable();

        readBlockTable();
    }

    /**
     * Searches the file for the MPQ archive header.
     *
     * @return the file position at which the MPQ archive starts.
     * @throws IOException   if an error occurs while searching.
     * @throws JMpqException if file does not contain a MPQ archive.
     */
    private long searchHeader() throws IOException {
        // probe to sample file with
        ByteBuffer probe = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

        final long fileSize = fileChannel.size();
        for (long filePos = 0; filePos + probe.capacity() < fileSize; filePos += 0x200) {
            probe.rewind();
            Util.readFully(probe, fileChannel, filePos);

            final int sample = probe.getInt(0);
            if (sample == ARCHIVE_HEADER_MAGIC) {
                // found archive header
                return filePos;
            } else if (sample == USER_DATA_HEADER_MAGIC && !isLegacyCompatibility()) {
                // MPQ user data header with redirect to MPQ header
                // ignore in legacy compatibility mode

                // TODO process these in some meaningful way

                probe.rewind();
                Util.readFully(probe, fileChannel, filePos + 8);

                // add header offset and align
                filePos += (probe.getInt(0) & 0xFFFFFFFFL);
                filePos &= -0x200;
            }
        }

        throw new JMpqException("No MPQ archive in file.");
    }

    private void readHeaderSize() throws IOException {
        // probe to sample file with
        ByteBuffer probe = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

        // read header size
        Util.readFully(probe, fileChannel, headerOffset + 4);
        headerSize = probe.getInt(0);

        if (isLegacyCompatibility()) {
            // force version 0 header size
            headerSize = 32;
        } else if (headerSize < 32 || 208 < headerSize) {
            // header too small or too big
            throw new JMpqException("Bad header size.");
        }
    }

    private void readHeader() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        Util.readFully(buffer, fileChannel, headerOffset + 8);
        buffer.rewind();

        archiveSize = buffer.getInt() & 0xFFFFFFFFL;
        formatVersion = buffer.getShort();
        if (isLegacyCompatibility()) {
            // force version 0 interpretation
            formatVersion = 0;
        }

        discBlockSize = 512 * (1 << (buffer.getShort() & 0xFFFF));
        hashTablePosition = buffer.getInt() & 0xFFFFFFFFL;
        blockTablePosition = buffer.getInt() & 0xFFFFFFFFL;
        hashSize = buffer.getInt() & 0x0FFFFFFF;
        blockSize = buffer.getInt();

        // version 1 extension
        if (formatVersion >= 1) {
            // TODO add high block table support
            buffer.getLong();

            // high 16 bits of file pos
            hashTablePosition |= (buffer.getShort() & 0xFFFFL) << 32;
            blockTablePosition |= (buffer.getShort() & 0xFFFFL) << 32;
        }

        // version 2 extension
        if (formatVersion >= 2) {
            // 64 bit archive size
            archiveSize = buffer.getLong();

            // TODO add support for BET and HET tables
            buffer.getLong();
            buffer.getLong();
        }
    }

    private void checkLegacyCompat() throws IOException {
        if (isLegacyCompatibility()) {
            // limit end of archive by end of file
            archiveSize = Math.min(archiveSize, fileChannel.size() - headerOffset);

            // limit block table size by end of archive
            blockSize = (int) (Math.min(blockSize, (archiveSize - blockTablePosition) / 16));
        }
    }

    private void readHashTable() throws IOException {
        // read hash table
        ByteBuffer hashBuffer = ByteBuffer.allocate(hashSize * 16);
        Util.readFully(hashBuffer, fileChannel, headerOffset + hashTablePosition);
        hashBuffer.rewind();

        // decrypt hash table
        final MPQEncryption decrypt = new MPQEncryption(KEY_HASH_TABLE, true);
        decrypt.processSingle(hashBuffer);
        hashBuffer.rewind();

        // create hash table
        hashTable = HashTable.readFromBuffer(hashBuffer, hashSize);
    }

    private void readBlockTable() throws IOException {
        ByteBuffer blockBuffer = ByteBuffer.allocate(blockSize * 16).order(ByteOrder.LITTLE_ENDIAN);
        Util.readFully(blockBuffer, fileChannel, headerOffset + blockTablePosition);
        blockBuffer.rewind();
        blockTable = BlockTable.readFrom(blockBuffer);
    }

    public Listfile getListFile() throws IOException{
        MpqFile mpqFile = getMpqFile(LIST_FILE);
        byte[] data = mpqFile.extractToBytes();
        return Listfile.from(data);
    }

    public int getTotalFileCount() {
        return blockTable.getAllVaildBlocks().size();
    }

    public boolean hasFile(String name) {
        try {
            hashTable.getBlockIndexOfFile(name);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Loads an MPQ file into memory and returns a representation of it with the possibility to extract
     * the decrypted and decompressed data. See {@link MpqFile}
     */
    public MpqFile getMpqFile(String name) throws IOException {
        int pos = hashTable.getBlockIndexOfFile(name);
        Block b = blockTable.getBlockAtPos(pos);

        ByteBuffer buffer = ByteBuffer.allocate(b.compressedSize()).order(ByteOrder.LITTLE_ENDIAN);
        Util.readFully(buffer, fileChannel, headerOffset + b.getFilePosUnsigned());

        return new MpqFile(buffer.array(), b, discBlockSize, name);
    }

    public MpqFile getMpqFileByBlock(Block block) throws IOException {
        if (block.hasFlag(ENCRYPTED))
            throw new IOException("cant access this block");

        ByteBuffer buffer = ByteBuffer.allocate(block.compressedSize()).order(ByteOrder.LITTLE_ENDIAN);
        Util.readFully(buffer, fileChannel, headerOffset + block.getFilePosUnsigned());

        return new MpqFile(buffer.array(), block, discBlockSize, "");
    }

    public List<MpqFile> getMpqFilesByBlockTable() {
        List<MpqFile> mpqFiles = new ArrayList<>();
        List<Block> list = blockTable.getAllVaildBlocks();
        for (Block block : list) {
            try {
                MpqFile mpqFile = getMpqFileByBlock(block);
                mpqFiles.add(mpqFile);
            } catch (IOException ignore) {
            }
        }
        return mpqFiles;
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
    }

    public boolean isLegacyCompatibility() {
        return mpqOpenOptions.contains(MPQOpenOption.FORCE_V0);
    }

    public BlockTable getBlockTable() {
        return blockTable;
    }

    @Override
    public String toString() {
        return "JMpqEditor [headerSize=" + headerSize + ", archiveSize=" + archiveSize + ", formatVersion=" + formatVersion + ", discBlockSize=" + discBlockSize
            + ", hashPos=" + hashTablePosition + ", blockPos=" + blockTablePosition + ", hashSize=" + hashSize + ", blockSize=" + blockSize + ", hashMap=" + hashTable + "]";
    }
}


package systems.crigges.jmpq3;

import systems.crigges.jmpq3.compression.CompressionUtil;
import systems.crigges.jmpq3.security.MPQEncryption;
import systems.crigges.jmpq3.security.MPQHashGenerator;

import javax.annotation.concurrent.Immutable;
import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static systems.crigges.jmpq3.Block.*;

/**
 * Represents a file from an MPQ archive that is loaded into memory. The in-memory data may be encrypted
 * and/or compressed. Use {@link MpqFile#extractToBytes()} or {@link MpqFile#extractToOutputStream(OutputStream)} in
 * order to extract the decrypted and/or decompressed data. Since this class is effectively immutable, it is safe
 * to do so in a multithreaded setting.
 */
@Immutable
public class MpqFile {

    private final byte[] buffer;
    private final Block block;
    private final String name;
    private final boolean isEncrypted;
    private final int sectorSize;
    private final int sectorCount;
    private final int baseKey;

    MpqFile(byte[] buffer, Block b, int sectorSize, String name)  {
        this.buffer = buffer;
        this.block = b;
        this.sectorSize = sectorSize;
        this.name = name;
        this.isEncrypted = b.hasFlag(ENCRYPTED);
        this.sectorCount = (int) (Math.ceil(((double) block.normalSize() / (double) sectorSize)) + 1);

        int sepIndex = name.lastIndexOf('\\');
        String pathlessName = name.substring(sepIndex + 1);
        if (isEncrypted) {
            final MPQHashGenerator keyGen = MPQHashGenerator.getFileKeyGenerator();
            keyGen.process(pathlessName);
            if (b.hasFlag(ADJUSTED_ENCRYPTED)) {
                this.baseKey = ((keyGen.getHash() + b.getFilePos()) ^ b.normalSize());
            } else {
                this.baseKey = keyGen.getHash();
            }
        } else {
            this.baseKey = 0;
        }
    }

    public String getName() {
        return name;
    }

    /**
     * Decrypts and decompresses the data in this file and returns a raw byte array.
     */
    public byte[] extractToBytes() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        extractToOutputStream(byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        return bytes;
    }

    /**
     * Decrypts and decompresses the data in this file and writes it to the given output stream
     */
    public void extractToOutputStream(OutputStream outputStream) throws IOException {
        if (sectorCount == 1) {
            outputStream.close();
            return;
        }
        if (extractImplodedBlock(outputStream)) return;
        if (extractSingleUnitBlock(outputStream)) return;
        if (block.hasFlag(COMPRESSED)) {
            extractCompressedBlock(outputStream);
        } else {
            check(outputStream);
        }
    }

    private void extractCompressedBlock(OutputStream outputStream) throws IOException {
        byte[] sot = Arrays.copyOfRange(buffer, 0, sectorCount * 4);

        if (isEncrypted) {
            new MPQEncryption(baseKey - 1, true).processSingle(ByteBuffer.wrap(sot));
        }
        ByteBuffer sotBuffer = ByteBuffer.wrap(sot).order(ByteOrder.LITTLE_ENDIAN);
        int start = sotBuffer.getInt();
        int end = sotBuffer.getInt();
        int finalSize = 0;
        for (int i = 0; i < sectorCount - 1; i++) {
            byte[] arr = Arrays.copyOfRange(buffer, start, end);
            if (isEncrypted) {
                new MPQEncryption(baseKey + i, true).processSingle(ByteBuffer.wrap(arr));
            }
            if (block.normalSize() - finalSize <= sectorSize) {
                arr = decompressSector(arr, end - start, block.normalSize() - finalSize);
            } else {
                arr = decompressSector(arr, end - start, sectorSize);
            }
            outputStream.write(arr);

            finalSize += sectorSize;
            start = end;
            try {
                end = sotBuffer.getInt();
            } catch (BufferUnderflowException e) {
                break;
            }
        }
        outputStream.flush();
        outputStream.close();
    }

    private boolean extractSingleUnitBlock(OutputStream writer) throws IOException {
        if (block.hasFlag(SINGLE_UNIT)) {
            if (block.hasFlag(COMPRESSED)) {
                byte[] arr = Arrays.copyOfRange(buffer, 0, block.compressedSize());
                if (isEncrypted) {
                    new MPQEncryption(baseKey, true).processSingle(ByteBuffer.wrap(arr));
                }
                arr = decompressSingleUnitSector(arr, block.compressedSize(), block.normalSize());
                writer.write(arr);
                writer.flush();
                writer.close();
            } else {
                check(writer);
            }
            return true;
        }
        return false;
    }

    private boolean extractImplodedBlock(OutputStream outputStream) throws IOException {
        if (!block.hasFlag(IMPLODED))
            return false;

        byte[] sot = Arrays.copyOfRange(buffer, 0, sectorCount * 4);
        if (isEncrypted) {
            new MPQEncryption(baseKey - 1, true).processSingle(ByteBuffer.wrap(sot));
        }
        ByteBuffer sotBuffer = ByteBuffer.wrap(sot).order(ByteOrder.LITTLE_ENDIAN);
        int start = sotBuffer.getInt();
        int end = sotBuffer.getInt();
        int finalSize = 0;
        for (int i = 0; i < sectorCount - 1; i++) {
            byte[] arr = Arrays.copyOfRange(buffer, start, end);
            if (isEncrypted) {
                new MPQEncryption(baseKey + i, true).processSingle(ByteBuffer.wrap(arr));
            }
            if (block.normalSize() - finalSize <= sectorSize) {
                arr = decompressImplodedSector(arr, end - start, block.normalSize() - finalSize);
            } else {
                arr = decompressImplodedSector(arr, end - start, sectorSize);
            }
            outputStream.write(arr);

            finalSize += sectorSize;
            start = end;
            try {
                end = sotBuffer.getInt();
            } catch (BufferUnderflowException e) {
                break;
            }
        }

        outputStream.flush();
        outputStream.close();

        return true;
    }

    private void check(OutputStream outputStream) throws IOException {
        byte[] arr = Arrays.copyOfRange(buffer, 0, block.compressedSize());
        if (isEncrypted) {
            new MPQEncryption(baseKey, true).processSingle(ByteBuffer.wrap(arr));
        }
        outputStream.write(arr);
        outputStream.flush();
        outputStream.close();
    }

    private byte[] decompressSector(byte[] sector, int normalSize, int uncompressedSize) throws IOException {
        return CompressionUtil.decompress(sector, normalSize, uncompressedSize);
    }

    private byte[] decompressSingleUnitSector(byte[] sector, int normalSize, int uncompressedSize) throws IOException {
        return CompressionUtil.decompressSingleUnit(sector, normalSize, uncompressedSize);
    }

    private byte[] decompressImplodedSector(byte[] sector, int normalSize, int uncompressedSize) {
        return CompressionUtil.explode(sector, normalSize, uncompressedSize);
    }
}

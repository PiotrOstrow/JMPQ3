package systems.crigges.jmpq3;

import systems.crigges.jmpq3.security.MPQEncryption;

import javax.annotation.concurrent.Immutable;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static systems.crigges.jmpq3.Block.EXISTS;

@Immutable
public class BlockTable {

    private static final int BLOCK_SIZE = 16;

    private final List<Block> blocks;

    private BlockTable(List<Block> blocks) {
        this.blocks = blocks;
    }

    public Block getBlockAtPos(int pos) throws JMpqException {
        if (pos < 0 || pos > blocks.size())
            throw new JMpqException("Invaild block position");

        return blocks.get(pos);
    }

    public List<Block> getAllVaildBlocks() {
        return blocks.stream()
            .filter(e -> e.hasFlag(EXISTS))
            .toList();
    }

    public static BlockTable readFrom(ByteBuffer byteBuffer) {
        ByteBuffer decryptedBuffer = ByteBuffer.allocate(byteBuffer.capacity());
        new MPQEncryption(-326913117, true).processFinal(byteBuffer, decryptedBuffer);
        decryptedBuffer.order(ByteOrder.LITTLE_ENDIAN);
        decryptedBuffer.rewind();

        int numBlocks = byteBuffer.capacity() / BLOCK_SIZE;
        List<Block> blocks = new ArrayList<>(numBlocks);

        for (int i = 0; i < numBlocks; i++)
            blocks.add(Block.readFromBuffer(decryptedBuffer));

        return new BlockTable(blocks);
    }

    public static void writeNewBlocktable(List<Block> blocks, int size, MappedByteBuffer buf) {
        ByteBuffer temp = ByteBuffer.allocate(size * 16);
        temp.order(ByteOrder.LITTLE_ENDIAN);
        for (Block b : blocks) {
            b.writeToBuffer(temp);
        }
        temp.clear();
        if (new MPQEncryption(-326913117, false).processFinal(temp, buf))
            throw new BufferOverflowException();
    }
}
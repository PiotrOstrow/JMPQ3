package systems.crigges.jmpq3;

import java.nio.ByteBuffer;

public record Block(long filePos, int compressedSize, int normalSize, int flags) {

    public static final int COMPRESSED = 0x00000200;
    public static final int ENCRYPTED = 0x00010000;
    public static final int SINGLE_UNIT = 0x01000000;
    public static final int ADJUSTED_ENCRYPTED = 0x00020000;
    public static final int EXISTS = 0x80000000;
    public static final int DELETED = 0x02000000;
    public static final int IMPLODED = 0x00000100;

    public static Block readFromBuffer(ByteBuffer buf) {
        return new Block(buf.getInt() & 0xFFFFFFFFL, buf.getInt(), buf.getInt(), buf.getInt());
    }

    public void writeToBuffer(ByteBuffer bb) {
        bb.putInt((int) this.filePos);
        bb.putInt(this.compressedSize);
        bb.putInt(this.normalSize);
        bb.putInt(this.flags);
    }

    public int getFilePos() {
        return (int) this.filePos;
    }

    public long getFilePosUnsigned() {
        return this.filePos;
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    public String toString() {
        return "Block [filePos=" + this.filePos + ", compressedSize=" + this.compressedSize + ", normalSize=" + this.normalSize + ", flags=" +
            printFlags().trim() + "]";
    }

    public String printFlags() {
        return (hasFlag(EXISTS) ? "EXISTS " : "") + (hasFlag(SINGLE_UNIT) ? "SINGLE_UNIT " : "") + (hasFlag(COMPRESSED) ? "COMPRESSED " : "")
            + (hasFlag(ENCRYPTED) ? "ENCRYPTED " : "") + (hasFlag(ADJUSTED_ENCRYPTED) ? "ADJUSTED " : "") + (hasFlag(DELETED) ? "DELETED " : "");
    }
}
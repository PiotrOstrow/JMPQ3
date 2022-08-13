package systems.crigges.jmpq3.compression;

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.GZIPException;
import com.jcraft.jzlib.Inflater;

public class JzLibHelper {

    public static void inflate(byte[] bytes, int offset, int uncompSize, byte[] output) {
        Inflater inf = new Inflater();
        inf.init();
        inf.setInput(bytes, offset, bytes.length - 1, false);
        inf.setOutput(output);
        while ((inf.total_out < uncompSize) && (inf.total_in < bytes.length)) {
            inf.avail_in = (inf.avail_out = 1);
            int err = inf.inflate(0);
            if (err == 1)
                break;
        }
        inf.end();
    }

    public static byte[] deflate(byte[] bytes, boolean strongDeflate) throws GZIPException {
        Deflater def = new Deflater(strongDeflate ? 9 : 1);

        byte[] comp = new byte[Math.max(1024, bytes.length)];
        def.setInput(bytes);
        def.setOutput(comp);

        while ((def.total_in != bytes.length) && (def.total_out < bytes.length)) {
            def.avail_in = (def.avail_out = 1);
            def.deflate(0);
        }
        int err;
        do {
            def.avail_out = 1;
            err = def.deflate(4);
        } while (err != 1);

        byte[] temp = new byte[(int) def.getTotalOut()];
        System.arraycopy(comp, 0, temp, 0, (int) def.getTotalOut());
        def.end();
        return temp;
    }

    private JzLibHelper() {
    }
}
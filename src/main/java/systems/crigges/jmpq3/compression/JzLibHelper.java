package systems.crigges.jmpq3.compression;

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.GZIPException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class JzLibHelper {

    public static void inflate(byte[] input, byte[] output) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(input, 1, input.length - 1);
        try (InflaterInputStream stream = new InflaterInputStream(in, new Inflater(), input.length - 1)) {
            for(int read = 0, pos = 0; read != -1 && pos < output.length; pos += read) {
                read = stream.read(output, pos, output.length - pos);
            }
        }
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
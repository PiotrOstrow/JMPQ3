package systems.crigges.jmpq3.compression;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import systems.crigges.jmpq3.JMpqException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Created by Frotty on 30.04.2017.
 */
public class CompressionUtil {

    private static final ADPCM ADPCM = new ADPCM(2);;
    private static final Huffman HUFFMAN = new Huffman();
    private static final ZopfliHelper ZOPFLI = new ZopfliHelper();

    /* Masks for Decompression Type 2 */
    private static final byte FLAG_HUFFMAN = 0x01;
    private static final byte FLAG_DEFLATE = 0x02;
    // 0x04 is unknown
    private static final byte FLAG_IMPLODE = 0x08;
    private static final byte FLAG_BZIP2 = 0x10;
    private static final byte FLAG_SPARSE = 0x20;
    private static final byte FLAG_ADPCM1C = 0x40;
    private static final byte FLAG_ADPCM2C = -0x80;
    private static final byte FLAG_LZMA = 0x12;

    public static byte[] compress(byte[] temp, RecompressOptions recompress) {
        return recompress.useZopfli ? ZOPFLI.deflate(temp, recompress.iterations) : JzLibHelper.deflate(temp, recompress.recompress);
    }

    public static byte[] decompress(byte[] sector, int compressedSize, int uncompressedSize) throws JMpqException {
        if (compressedSize == uncompressedSize) {
            return sector;
        }

        byte compressionType = sector[0];
        byte[] outByteArray = new byte[uncompressedSize];
        ByteBuffer out = ByteBuffer.wrap(outByteArray);
        ByteBuffer in = ByteBuffer.wrap(sector, 1, sector.length - 1);

        boolean flip = false;

        if ((compressionType & FLAG_DEFLATE) != 0) {
            JzLibHelper.inflate(sector, 1, uncompressedSize, outByteArray);
            flip = true;
        } else if ((compressionType & FLAG_LZMA) != 0) {
            throw new JMpqException("Unsupported compression LZMA");
        } else if ((compressionType & FLAG_BZIP2) != 0) {
            throw new JMpqException("Unsupported compression Bzip2");
        } else if ((compressionType & FLAG_IMPLODE) != 0) {
            Exploder.pkexplode(sector, outByteArray, 1);
            flip = true;
        }

        if ((compressionType & FLAG_SPARSE) != 0) {
            throw new JMpqException("Unsupported compression sparse");
        }

        if ((compressionType & FLAG_HUFFMAN) != 0) {
            (flip ? in : out).clear();
            HUFFMAN.Decompress(flip ? out : in, flip ? in : out);
            out.limit(out.position());
            in.position(0);
            out.position(0);
            flip = !flip;
        }

        if ((compressionType & FLAG_ADPCM2C) != 0) {
            ByteBuffer newOut = ByteBuffer.wrap(new byte[uncompressedSize]);
            ADPCM.decompress(flip ? out : in, newOut, 2);
            (flip ? out : in).position(0);
            return newOut.array();
        }

        if ((compressionType & FLAG_ADPCM1C) != 0) {
            ByteBuffer newOut = ByteBuffer.wrap(new byte[uncompressedSize]);
            ADPCM.decompress(flip ? out : in, newOut, 1);
            (flip ? out : in).position(0);
            return newOut.array();
        }

        return (flip ? out : in).array();
    }

	public static byte[] decompressSingleUnit(byte[] sector, int compressedSize, int uncompressedSize) throws IOException {
		if (compressedSize == uncompressedSize)
			return sector;

		int compressionType = sector[0];
		byte[] out = new byte[uncompressedSize];

		switch (compressionType) {
			case FLAG_DEFLATE -> JzLibHelper.inflate(sector, 1, uncompressedSize, out);
			case FLAG_IMPLODE -> Exploder.pkexplode(sector, out, 1);
			case FLAG_BZIP2 -> {
				InputStream inputStream = new ByteArrayInputStream(sector, 1, sector.length - 1);
				try (BZip2CompressorInputStream bZip2CompressorInputStream = new BZip2CompressorInputStream(inputStream)) {
					if (bZip2CompressorInputStream.read(out, 0, uncompressedSize) != uncompressedSize)
						throw new IllegalStateException();
				}
			}
			case FLAG_LZMA,
				FLAG_SPARSE,
				FLAG_SPARSE | FLAG_DEFLATE,
				FLAG_SPARSE | FLAG_BZIP2,
				FLAG_ADPCM1C | FLAG_HUFFMAN,
				FLAG_ADPCM2C | FLAG_HUFFMAN ->
				throw new JMpqException("Unsupported compression type/combination: 0x" + Integer.toHexString(compressionType & 0xFF));
			default ->
				throw new JMpqException("Invalid compression type/combination: 0x" + Integer.toHexString(compressionType & 0xFF));
		}

		return out;
	}

    public static byte[] explode(byte[] sector, int compressedSize, int uncompressedSize) {
        if (compressedSize == uncompressedSize) {
            return sector;
        } else {
            byte[] buffer = new byte[uncompressedSize];
            Exploder.pkexplode(sector, buffer, 0);
            return buffer;
        }
    }

    private CompressionUtil() {
    }
}

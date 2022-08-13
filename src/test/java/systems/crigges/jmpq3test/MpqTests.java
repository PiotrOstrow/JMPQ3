package systems.crigges.jmpq3test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import systems.crigges.jmpq3.*;
import systems.crigges.jmpq3.security.MPQEncryption;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static systems.crigges.jmpq3.Block.ENCRYPTED;

/**
 * Created by Frotty on 06.03.2017.
 */
public class MpqTests {
    private static File[] files;
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private static File[] getMpqs() throws IOException {
        File[] files = new File(MpqTests.class.getClassLoader().getResource("./mpqs/").getFile())
            .listFiles((dir, name) -> name.endsWith(".w3x") || name.endsWith("" + ".mpq") || name.endsWith(".scx"));
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                Path target = files[i].toPath().resolveSibling(files[i].getName() + "_copy");
                files[i] = Files.copy(files[i].toPath(), target,
                    StandardCopyOption.REPLACE_EXISTING).toFile();
            }
        }
        MpqTests.files = files;
        return files;
    }

    @AfterMethod
    public static void clearFiles() throws IOException {
        if (files != null) {
            for (File file : files) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }

    private static File getFile(String name) {
        return new File(MpqTests.class.getClassLoader().getResource(name).getFile());
    }

    @Test
    public void cryptoTest() throws IOException {
        byte[] bytes = "Hello World!".getBytes();

        final ByteBuffer workBuffer = ByteBuffer.allocate(bytes.length);
        final MPQEncryption encryptor = new MPQEncryption(-1011927184, false);
        encryptor.processFinal(ByteBuffer.wrap(bytes), workBuffer);
        workBuffer.flip();
        encryptor.changeKey(-1011927184, true);
        encryptor.processSingle(workBuffer);
        workBuffer.flip();

        //Assert.assertTrue(Arrays.equals(new byte[]{-96, -93, 89, -50, 43, -60, 18, -33, -31, -71, -81, 86}, a));
        //Assert.assertTrue(Arrays.equals(new byte[]{2, -106, -97, 38, 5, -82, -88, -91, -6, 63, 114, -31}, b));
        Assert.assertTrue(Arrays.equals(bytes, workBuffer.array()));
    }


    @Test
    public void testException() {
        Assert.expectThrows(JMpqException.class, () -> BlockTable.readFrom(ByteBuffer.wrap(new byte[0])).getBlockAtPos(-1));
    }

    @Test
    public void testExtractScriptFile() throws IOException {
        File[] mpqs = getMpqs();
        for (File mpq : mpqs) {
            log.info("test extract script: " + mpq.getName());
            JMpqArchive mpqEditor = new JMpqArchive(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0);
            if (mpqEditor.hasFile("war3map.j")) {
                byte[] bytes = mpqEditor.getMpqFile("war3map.j").extractToBytes();
                String extractedFile = new String(bytes).replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
                String existingFile = new String(Files.readAllBytes(getFile("war3map.j").toPath())).replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
                Assert.assertEquals(existingFile, extractedFile);
            }
            mpqEditor.close();
        }
    }

    @Test
    public void testExtractScriptFileBA() throws IOException {
        File[] mpqs = getMpqs();
        for (File mpq : mpqs) {
            log.info("test extract script: " + mpq.getName());
            JMpqArchive mpqEditor = new JMpqArchive(mpq.toPath(), MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0);
            if (mpqEditor.hasFile("war3map.j")) {
                byte[] bytes = mpqEditor.getMpqFile("war3map.j").extractToBytes();
                String extractedFile = new String(bytes).replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
                String existingFile = new String(Files.readAllBytes(getFile("war3map.j").toPath())).replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
                Assert.assertEquals(existingFile, extractedFile);
            }
            mpqEditor.close();
        }
    }

    @Test
    public void testForGetMpqFileByBlock() throws IOException {
        File[] mpqs = getMpqs();
        for (File mpq : mpqs) {
            if (mpq.getName().equals("invalidHashSize.scx_copy")) {
                continue;
            }
            try (JMpqArchive mpqEditor = new JMpqArchive(mpq, MPQOpenOption.FORCE_V0)) {

                Assert.assertTrue(mpqEditor.getMpqFilesByBlockTable().size() > 0);
                BlockTable blockTable = mpqEditor.getBlockTable();
                Assert.assertNotNull(blockTable);

                for (Block block : blockTable.getAllVaildBlocks()) {
                    if (block.hasFlag(ENCRYPTED)) {
                        continue;
                    }
                    Assert.assertNotNull(mpqEditor.getMpqFileByBlock(block));
                }
            }
        }
    }
}

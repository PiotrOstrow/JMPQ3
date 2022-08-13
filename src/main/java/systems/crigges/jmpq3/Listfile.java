package systems.crigges.jmpq3;

import javax.annotation.concurrent.Immutable;
import java.io.ByteArrayInputStream;
import java.util.*;

import static systems.crigges.jmpq3.Util.calculateFileKey;

@Immutable
public class Listfile {

    private final Map<Long, String> files;

    private Listfile(Map<Long, String> files) {
        this.files = files;
    }

    public Collection<String> getFiles() {
        return Collections.unmodifiableCollection(files.values());
    }

    public Map<Long, String> getFileMap() {
        return Collections.unmodifiableMap(files);
    }

    public boolean containsFile(String name) {
        long key = calculateFileKey(name);
        return files.containsKey(key);
    }

    public byte[] asByteArray() {
        StringBuilder temp = new StringBuilder();
        for (String entry : files.values()) {
            temp.append(entry);
            temp.append("\r\n");
        }
        return temp.toString().getBytes();
    }

    public static Listfile from(byte[] data) {
        Map<Long, String> result = new HashMap<>();

        Scanner scanner = new Scanner(new ByteArrayInputStream(data));
        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line == null || line.isBlank())
                continue;

            long key = calculateFileKey(line);
            result.putIfAbsent(key, line);
        }

        return new Listfile(result);
    }
}

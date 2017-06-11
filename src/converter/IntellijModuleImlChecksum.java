package converter;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by trangdp on 17/05/2017.
 */
public class IntellijModuleImlChecksum {
    private static IntellijModuleImlChecksum Instance = null;
    private ConcurrentHashMap<String, String> checksumCache = new ConcurrentHashMap<>();

    private IntellijModuleImlChecksum() {
    }

    public static IntellijModuleImlChecksum getInstance() {
        if (Instance == null) {
            Instance = new IntellijModuleImlChecksum();
        }

        return Instance;
    }

    public String getLastCheckSum(String moduleName) {
        return checksumCache.get(moduleName);
    }

    public void updateLastChecksum(String moduleName, String pathToFile) throws IOException {
        String md5 = checksum(pathToFile);
        this.checksumCache.put(moduleName, md5);
    }

    public String checksum(String pathToFile) throws IOException {
        InputStream is = Files.newInputStream(Paths.get(pathToFile));
        String md5 = DigestUtils.md5Hex(is);

        return md5;

    }

}

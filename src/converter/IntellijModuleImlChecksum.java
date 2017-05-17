package converter;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

/**
 * Created by trangdp on 17/05/2017.
 */
public class IntellijModuleImlChecksum {
    private static IntellijModuleImlChecksum Instance = null;
    private String lastCheckSum = "";

    private IntellijModuleImlChecksum() {
    }

    public static IntellijModuleImlChecksum getInstance() {
        if (Instance == null) {
            Instance = new IntellijModuleImlChecksum();
        }

        return Instance;
    }

    public String getLastCheckSum() {
        return lastCheckSum;
    }

    public void updateLastChecksum(String pathToFile) throws IOException {
        this.lastCheckSum = checksum(pathToFile);
    }

    public String checksum(String pathToFile) throws IOException {
        InputStream is = Files.newInputStream(Paths.get(pathToFile));
        String md5 = DigestUtils.md5Hex(is);

        return md5;

    }

}

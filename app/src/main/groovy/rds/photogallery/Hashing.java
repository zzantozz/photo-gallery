package rds.photogallery;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

/**
 * Created by IntelliJ IDEA.
 * User: ryan
 * Date: 12/6/10
 * Time: 11:59 PM
 */
public class Hashing {
    private static final Logger logger = LoggerFactory.getLogger(Hashing.class);

    public static String sha1sum(String photoFile) {
        return sha1sum(App.getInstance().resolvePhotoPath(photoFile));
    }

    public static String sha1sum(File file) {
        logger.debug("Hashing file: " + file);
        long start = System.nanoTime();
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            int length;
            while ((length = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            String hexString = Hex.encodeHexString(digest.digest());
            fis.close();
            if (logger.isTraceEnabled()) {
                String elapsed = new DecimalFormat("#.000").format((System.nanoTime() - start) / 1000000d);
                logger.trace("Hashing took " + elapsed + " ms");
            }
            return hexString;
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.error("Failed to hash file {}", file.getAbsolutePath());
            throw new RuntimeException("Couldn't load file", e);
        }
    }
}

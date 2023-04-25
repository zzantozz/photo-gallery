package rds.photogallery;

import com.google.common.io.Files;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Holds all the data about a photo that's stored in the photo db. It duplicates some stuff in Photo so it can
 * be a standalone container, and the database code doesn't have to mess with anything else.
 * User: ryan
 * Date: Dec 6, 2010
 * Time: 6:57:04 PM
 */
public class PhotoData {

    private static final Logger logger = LoggerFactory.getLogger(PhotoData.class);

    public static final Integer UNRATED = -999;
    private String hash;
    private Integer rating = UNRATED;
    private String path;
    private List<String> userTags = new ArrayList<>();
    private List<String> implicitTags = new ArrayList<>();

    public PhotoData(String path) {
        this.path = path;
        determineImplicitTags();
    }

    public PhotoData(String hash, String path, Integer rating) {
        this.hash = hash;
        // Always store paths in the db with unix separators. Windows should be able to recognize them too.
        this.path = FilenameUtils.separatorsToUnix(path);
        this.rating = rating;
        determineImplicitTags();
    }

    /**
     * Convenience constructor for use with groovy json parsing. I think it ought to work automatically, but
     * something's up. It might be the mismatch between field name 'hash' and getting name 'getPhotoHash'.
     * @param fields
     */
    public PhotoData(Map fields) {
        this.hash = (String) fields.get("hash");
        this.rating = (Integer) fields.getOrDefault("rating", UNRATED);
        this.path = (String) fields.get("path");
        this.userTags = (List) fields.get("tags");
        determineImplicitTags();
    }

    private void determineImplicitTags() {
        String parent = new File(path).getParent();
        if (parent != null) {
            implicitTags.add(parent);
        }
        implicitTags.add(Files.getFileExtension(path).toLowerCase());
    }

    public boolean isDefault() {
        return UNRATED.equals(rating) && userTags.isEmpty();
    }

    /**
     * Sets the hash for this photo. This should normally not be used. Prefer instead to construct
     * a PhotoData with a getter that provides the hash on demand. This method is primarily meant for
     * resolving database audits, where a hash may need to be updated.
     */
    public void setPhotoHash(String photoHash) {
        this.hash = photoHash;
    }

    public String getPhotoHash() {
        if (this.hash == null) {
            if (logger.isTraceEnabled()) {
                try {
                    throw new UnsupportedOperationException("Not an exception - Stacktrace to show who triggered the hash init");
                } catch (UnsupportedOperationException e) {
                    logger.debug("Lazily initting hash for {}", path);
                    logger.trace("", e);
                }
            }
            this.hash = Hashing.sha1sum(path);
        }
        return hash;
    }

    public String getPath() {
        return path;
    }

    public String getRelativePath() {
        return path;
    }

    // another method to allow db auditing
    public void setPath(String path) {
        this.path = path;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Collection<String> getAllTags() {
        return Collections.unmodifiableCollection(new ArrayList<String>(){{
            addAll(userTags);
            addAll(implicitTags);
        }});
    }

    public List<String> getUserTags() {
        return Collections.unmodifiableList(userTags);
    }

    public void setUserTags(List<String> userTags) {
        this.userTags = Collections.unmodifiableList(userTags);
    }

    public List<String> getImplicitTags() {
        return implicitTags;
    }

    public void setImplicitTags(List<String> implicitTags) {
        this.implicitTags = implicitTags;
    }

    @Override
    public String toString() {
        return "PhotoData{" +
                "path='" + path + '\'' +
                ", rating=" + rating +
                ", userTags=" + userTags +
                '}';
    }
}

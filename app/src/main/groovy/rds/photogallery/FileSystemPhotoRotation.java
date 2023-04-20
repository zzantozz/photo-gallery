package rds.photogallery;

/**
 * A dumb photo rotation that just goes over files on the file system. This is just the first implementation of
 * PhotoRotation to keep things working.
 */
public class FileSystemPhotoRotation implements PhotoRotation {

    private final String rootDir;
    private PhotoLister photolister;

    public FileSystemPhotoRotation(String rootDir) {
        this.rootDir = rootDir;
        photolister = new FileSystemPhotoLister(rootDir);
    }

    @Override
    public String next() {
        if (!photolister.hasNext()) {
            photolister = new FileSystemPhotoLister(rootDir);
        }
        return photolister.next();
    }
}

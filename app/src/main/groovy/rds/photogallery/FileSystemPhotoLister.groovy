package rds.photogallery

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FileSystemPhotoLister implements PhotoLister {
    String rootDir
    Iterator<Path> fileIterator
    List<String> photoFileExtensions = ['jpg']

    FileSystemPhotoLister(String rootDir) {
        this.rootDir = rootDir
        this.fileIterator = getIterator()
    }

    @Override
    String next() {
        if (!fileIterator.hasNext()) {
            fileIterator = getIterator()
        }
        fileIterator.next()
    }

    private Iterator<Path> getIterator() {
        this.fileIterator = Metrics.time("getting photo iterator from file system", {
            Files.walk(Paths.get(rootDir))
                    .filter(Files::isRegularFile)
                    .filter((f) -> photoFileExtensions.any {
                        f.fileName.toString().toLowerCase().endsWith(it)
                    })
                    .iterator()
        })
        fileIterator
    }
}

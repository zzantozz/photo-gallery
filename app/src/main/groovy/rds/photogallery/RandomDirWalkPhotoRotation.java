package rds.photogallery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RandomDirWalkPhotoRotation implements PhotoRotation {
    private final Path baseDir;
    private final Random rand = new Random();
    private final Set<Path> blackList = new HashSet<>();

    public RandomDirWalkPhotoRotation(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public String next() {
        Stack<Path> history = new Stack<>();
        try {
            Path selectedPath = baseDir;
            while (Files.isDirectory(selectedPath)) {
                history.push(selectedPath);
                List<Path> entries = Files.list(selectedPath)
                        .filter(p -> Files.isDirectory(p) || isImage(p))
                        .filter(p -> !App.isRewrite(p))
                        .collect(Collectors.toList());
                if (entries.isEmpty()) {
                    blackList.add(selectedPath);
                    history.pop();
                    selectedPath = history.peek();
                } else {
                    do {
                        int selection = rand.nextInt(entries.size());
                        selectedPath = entries.get(selection);
                    } while (blackList.contains(selectedPath));
                }
            }
            return baseDir.relativize(selectedPath).toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk dirs", e);
        }
    }

    /**
     * Tries to determine whether the give path represents a loadable image so that we skip things like text files or
     * other things that show up in the same directory.
     */
    public boolean isImage(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null && contentType.startsWith("image");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to probe file for media type", e);
        }
    }
}

package rds.photogallery;

import com.google.common.collect.Collections2;

import java.util.stream.Stream;

public interface PhotoDataSource {
    PhotoData getPhotoData(String photoPath);

    void changeRating(PhotoData photoData, int newRating);

    Stream<PhotoData> getAllPhotoData();
}

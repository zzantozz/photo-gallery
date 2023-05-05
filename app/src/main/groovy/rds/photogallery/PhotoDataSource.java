package rds.photogallery;

public interface PhotoDataSource {
    PhotoData getPhotoData(String photoPath);

    void changeRating(PhotoData photoData, int newRating);
}

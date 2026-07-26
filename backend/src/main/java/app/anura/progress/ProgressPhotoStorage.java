package app.anura.progress;

public interface ProgressPhotoStorage {
    boolean enabled();
    StoredPhoto validate(String storageUrl, String thumbnailUrl);
    record StoredPhoto(String storageUrl,String thumbnailUrl) {}
}

package app.anura.progress;

import static org.junit.jupiter.api.Assertions.*;
import app.anura.error.ApiException;
import org.junit.jupiter.api.Test;

class ExternalUrlProgressPhotoStorageTest {
 @Test void disabledProviderRejectsStorage(){assertThrows(ApiException.class,()->new ExternalUrlProgressPhotoStorage(false).validate("https://cdn.example/photo.jpg",null));}
 @Test void enabledProviderOnlyAcceptsHttps(){var storage=new ExternalUrlProgressPhotoStorage(true);assertEquals("https://cdn.example/photo.jpg",storage.validate("https://cdn.example/photo.jpg",null).storageUrl());assertThrows(ApiException.class,()->storage.validate("javascript:alert(1)",null));}
}

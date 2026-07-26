package app.anura.progress;

import app.anura.error.ApiException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ExternalUrlProgressPhotoStorage implements ProgressPhotoStorage {
    private final boolean enabled;
    ExternalUrlProgressPhotoStorage(@Value("${app.progress.photo-storage-enabled:false}") boolean enabled){this.enabled=enabled;}
    public boolean enabled(){return enabled;}
    public StoredPhoto validate(String storageUrl,String thumbnailUrl){
        if(!enabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PHOTO_STORAGE_DISABLED","El almacenamiento de fotografías no está configurado");
        return new StoredPhoto(secure(storageUrl,"storageUrl"),thumbnailUrl==null||thumbnailUrl.isBlank()?null:secure(thumbnailUrl,"thumbnailUrl"));
    }
    private String secure(String value,String field){
        try { URI uri=URI.create(value); if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null) throw new IllegalArgumentException(); return uri.toString(); }
        catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHOTO_URL",field+" debe ser una URL HTTPS válida");}
    }
}

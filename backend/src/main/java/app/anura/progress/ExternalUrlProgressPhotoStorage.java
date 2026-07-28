package app.anura.progress;

import app.anura.error.ApiException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ExternalUrlProgressPhotoStorage implements ProgressPhotoStorage {
    private final boolean externalEnabled;
    ExternalUrlProgressPhotoStorage(@Value("${app.progress.photo-storage-enabled:false}") boolean enabled){this.externalEnabled=enabled;}
    public boolean enabled(){return true;}
    public StoredPhoto validate(String storageUrl,String thumbnailUrl){
        return new StoredPhoto(valid(storageUrl,"storageUrl"),thumbnailUrl==null||thumbnailUrl.isBlank()?null:valid(thumbnailUrl,"thumbnailUrl"));
    }
    private String valid(String value,String field){
        if(value!=null&&value.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")&&value.length()<=2_000_000)return value;
        if(externalEnabled)return secure(value,field);
        throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHOTO","La fotografía no tiene un formato válido o supera el tamaño permitido");
    }
    private String secure(String value,String field){
        try { URI uri=URI.create(value); if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null) throw new IllegalArgumentException(); return uri.toString(); }
        catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHOTO_URL",field+" debe ser una URL HTTPS válida");}
    }
}

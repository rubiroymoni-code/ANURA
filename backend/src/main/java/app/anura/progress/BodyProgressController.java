package app.anura.progress;

import app.anura.progress.BodyProgressService.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/body-checkins")
public class BodyProgressController {
 private final BodyProgressService service;BodyProgressController(BodyProgressService service){this.service=service;}
 @GetMapping List<Checkin> list(){return service.list();}
 @GetMapping("/latest") Checkin latest(){return service.latest();}
 @GetMapping("/{id}") Checkin one(@PathVariable UUID id){return service.one(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Checkin create(@RequestBody CheckinRequest request){return service.create(request);}
 @PutMapping("/{id}") Checkin update(@PathVariable UUID id,@RequestBody CheckinRequest request){return service.update(id,request);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id){service.delete(id);}
 @GetMapping("/evolution") Evolution evolution(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){return service.evolution(from,to);}
 @GetMapping("/photo-storage") Map<String,Boolean> storage(){return Map.of("enabled",service.photoStorageEnabled());}
 @PostMapping("/{id}/photos") @ResponseStatus(HttpStatus.CREATED) Photo photo(@PathVariable UUID id,@RequestBody PhotoRequest request){return service.addPhoto(id,request);}
 @DeleteMapping("/{id}/photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePhoto(@PathVariable UUID id,@PathVariable UUID photoId){service.deletePhoto(id,photoId);}
}

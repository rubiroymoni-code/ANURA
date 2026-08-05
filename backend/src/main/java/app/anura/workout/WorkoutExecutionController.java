package app.anura.workout;

import app.anura.workout.WorkoutExecutionService.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkoutExecutionController {
 private final WorkoutExecutionService service; WorkoutExecutionController(WorkoutExecutionService service){this.service=service;}
 @GetMapping("/api/v1/workouts/today") TodayWorkoutStatus today(){return service.todayStatus();}
 @GetMapping("/api/v1/workout-plans/{planId}/adjustments") List<WorkoutDayAdjustment> adjustments(@PathVariable UUID planId){return service.planAdjustments(planId);}
 @PostMapping("/api/v1/workouts/today/reschedule") TodayWorkout rescheduleToday(@RequestBody RescheduleRequest body){return service.rescheduleToday(body.date());}
 @PostMapping("/api/v1/workouts/today/skip") @ResponseStatus(HttpStatus.NO_CONTENT) void skipToday(@RequestBody(required=false) SkipRequest body){service.skipToday(body==null?null:body.reason());}
 @PostMapping("/api/v1/workout-plan-days/{dayId}/reschedule") @ResponseStatus(HttpStatus.NO_CONTENT) void rescheduleDay(@PathVariable UUID dayId,@RequestBody DayAdjustmentRequest body){service.rescheduleDay(dayId,body.originalDate(),body.targetDate());}
 @PostMapping("/api/v1/workout-plan-days/{dayId}/skip") @ResponseStatus(HttpStatus.NO_CONTENT) void skipDay(@PathVariable UUID dayId,@RequestBody DayAdjustmentRequest body){service.skipDay(dayId,body.originalDate(),body.reason());}
 @GetMapping("/api/v1/workout-sessions/active") SessionView active(){return service.active();}
 @PostMapping("/api/v1/workout-sessions") @ResponseStatus(HttpStatus.CREATED) SessionView start(@Valid @RequestBody StartRequest r){return service.start(r);}
 @GetMapping("/api/v1/workout-sessions") List<SessionSummary> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.history(page,size);}
 @GetMapping("/api/v1/workout-sessions/{id}") SessionView one(@PathVariable UUID id){return service.view(id);}
 @DeleteMapping("/api/v1/workout-sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id){service.deleteSession(id);}
 @PostMapping("/api/v1/workout-sessions/{id}/pause") SessionView pause(@PathVariable UUID id){return service.transition(id,"PAUSED","SESSION_PAUSED");}
 @PostMapping("/api/v1/workout-sessions/{id}/resume") SessionView resume(@PathVariable UUID id){return service.transition(id,"IN_PROGRESS","SESSION_RESUMED");}
 @PostMapping("/api/v1/workout-sessions/{id}/abandon") SessionView abandon(@PathVariable UUID id,@RequestBody(required=false) AbandonRequest body){return service.abandon(id,body==null?null:body.reason());}
 @PostMapping("/api/v1/workout-sessions/{id}/complete") SessionView complete(@PathVariable UUID id,@RequestBody CompleteRequest r){return service.complete(id,r);}
 @PatchMapping("/api/v1/workout-sessions/{id}/duration") SessionView duration(@PathVariable UUID id,@RequestBody DurationRequest r){return service.updateDuration(id,r.seconds());}
 @PostMapping("/api/v1/workout-sessions/{id}/exercises") ExerciseView exercise(@PathVariable UUID id,@RequestBody AddExerciseRequest r){return service.addExercise(id,r);}
 @PostMapping("/api/v1/workout-sessions/{id}/exercises/{exerciseId}/substitute") ExerciseView substitute(@PathVariable UUID id,@PathVariable UUID exerciseId,@RequestBody SubstituteRequest r){return service.substitute(id,exerciseId,r);}
 @PatchMapping("/api/v1/workout-sessions/{id}/exercises/{exerciseId}/pain") ExerciseView pain(@PathVariable UUID id,@PathVariable UUID exerciseId,@RequestBody PainRequest r){return service.pain(id,exerciseId,r);}
 @PostMapping("/api/v1/workout-sessions/{id}/exercises/{exerciseId}/complete") ExerciseView completeExercise(@PathVariable UUID id,@PathVariable UUID exerciseId){return service.completeExercise(id,exerciseId);}
 @PostMapping("/api/v1/workout-sessions/{id}/exercises/{exerciseId}/sets") SetView addSet(@PathVariable UUID id,@PathVariable UUID exerciseId,@RequestBody SetRequest r){return service.addSet(id,exerciseId,r);}
 @PatchMapping("/api/v1/workout-sessions/{id}/exercises/{exerciseId}/sets/{setId}") SetView updateSet(@PathVariable UUID id,@PathVariable UUID exerciseId,@PathVariable UUID setId,@RequestBody SetRequest r){return service.updateSet(id,exerciseId,setId,r);}
 @DeleteMapping("/api/v1/workout-sessions/{id}/exercises/{exerciseId}/sets/{setId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteSet(@PathVariable UUID id,@PathVariable UUID exerciseId,@PathVariable UUID setId){service.deleteSet(id,exerciseId,setId);}
 @GetMapping("/api/v1/exercises/{exerciseId}/history") List<ExerciseHistory> exerciseHistory(@PathVariable UUID exerciseId,@RequestParam(defaultValue="40") int limit){return service.exerciseHistory(exerciseId,limit);}
 @GetMapping("/api/v1/exercises") List<ExerciseOption> exercises(){return service.exerciseCatalog();}
 @GetMapping("/api/v1/exercises/{exerciseId}/last-performance") ExerciseHistory last(@PathVariable UUID exerciseId){return service.exerciseHistory(exerciseId,1).stream().findFirst().orElse(null);}
 @GetMapping("/api/v1/workout-sessions/{id}/metrics") Metrics metrics(@PathVariable UUID id){return service.metrics(id);}
 @GetMapping("/api/v1/training/summary") List<SessionSummary> summary(){return service.history(0,20);}
 @PostMapping("/api/v1/workout-sessions/{id}/sync") List<SyncResult> sync(@PathVariable UUID id,@RequestBody List<SyncOperation> operations){return service.sync(id,operations);}
 record RescheduleRequest(java.time.LocalDate date){} record SkipRequest(String reason){} record DayAdjustmentRequest(java.time.LocalDate originalDate,java.time.LocalDate targetDate,String reason){}
 record DurationRequest(Integer seconds){}
}

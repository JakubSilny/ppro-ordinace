package cz.uhk.ordinace.doctor.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.doctor.dto.AbsenceRequest;
import cz.uhk.ordinace.doctor.dto.AbsenceResponse;
import cz.uhk.ordinace.doctor.dto.DoctorRequest;
import cz.uhk.ordinace.doctor.dto.DoctorResponse;
import cz.uhk.ordinace.doctor.dto.OfficeHoursRequest;
import cz.uhk.ordinace.doctor.dto.OfficeHoursResponse;
import cz.uhk.ordinace.doctor.service.AbsenceResult;
import cz.uhk.ordinace.doctor.service.DoctorService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctors;

    public DoctorController(DoctorService doctors) {
        this.doctors = doctors;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DoctorResponse create(@Valid @RequestBody DoctorRequest request) {
        return DoctorResponse.from(doctors.create(request));
    }

    @GetMapping
    List<DoctorResponse> list() {
        return doctors.listActive().stream().map(DoctorResponse::from).toList();
    }

    @GetMapping("/{id}")
    DoctorResponse get(@PathVariable long id) {
        return DoctorResponse.from(doctors.get(id));
    }

    @PutMapping("/{id}")
    DoctorResponse update(@PathVariable long id, @Valid @RequestBody DoctorRequest request) {
        return DoctorResponse.from(doctors.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    DoctorResponse deactivate(@PathVariable long id) {
        return DoctorResponse.from(doctors.deactivate(id));
    }

    @GetMapping("/{id}/office-hours")
    List<OfficeHoursResponse> officeHours(@PathVariable long id) {
        return doctors.getOfficeHours(id).stream().map(OfficeHoursResponse::from).toList();
    }

    @PutMapping("/{id}/office-hours")
    List<OfficeHoursResponse> replaceOfficeHours(@PathVariable long id,
            @Valid @RequestBody List<@Valid OfficeHoursRequest> request) {
        return doctors.replaceOfficeHours(id, request).stream().map(OfficeHoursResponse::from).toList();
    }

    @PostMapping("/{id}/absences")
    @ResponseStatus(HttpStatus.CREATED)
    AbsenceResponse addAbsence(@PathVariable long id, @Valid @RequestBody AbsenceRequest request) {
        AbsenceResult result = doctors.addAbsence(id, request);
        return AbsenceResponse.from(result.absence(), result.flaggedAppointments());
    }

    @GetMapping("/{id}/absences")
    List<AbsenceResponse> absences(@PathVariable long id) {
        return doctors.upcomingAbsences(id).stream().map(a -> AbsenceResponse.from(a, 0)).toList();
    }
}

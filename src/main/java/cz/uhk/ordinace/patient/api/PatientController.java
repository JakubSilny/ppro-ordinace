package cz.uhk.ordinace.patient.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.patient.dto.PatientRequest;
import cz.uhk.ordinace.patient.dto.PatientResponse;
import cz.uhk.ordinace.patient.service.PatientService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PatientResponse create(@Valid @RequestBody PatientRequest request,
            @RequestParam(defaultValue = "false") boolean confirmDuplicate) {
        return PatientResponse.from(patients.create(request, confirmDuplicate));
    }

    @GetMapping("/{id}")
    PatientResponse get(@PathVariable long id) {
        return PatientResponse.from(patients.get(id));
    }

    @GetMapping
    List<PatientResponse> search(@RequestParam String query) {
        return patients.search(query).stream().map(PatientResponse::from).toList();
    }

    @PutMapping("/{id}")
    PatientResponse update(@PathVariable long id, @Valid @RequestBody PatientRequest request) {
        return PatientResponse.from(patients.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    PatientResponse deactivate(@PathVariable long id) {
        return PatientResponse.from(patients.deactivate(id));
    }
}

package cz.uhk.ordinace.visit.api;

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

import cz.uhk.ordinace.visit.dto.AddProcedureRequest;
import cz.uhk.ordinace.visit.dto.ProcedureItemResponse;
import cz.uhk.ordinace.visit.dto.VisitRequest;
import cz.uhk.ordinace.visit.dto.VisitResponse;
import cz.uhk.ordinace.visit.service.VisitService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class VisitController {

    private final VisitService visits;

    public VisitController(VisitService visits) {
        this.visits = visits;
    }

    @GetMapping("/procedures")
    List<ProcedureItemResponse> catalog() {
        return visits.catalog().stream().map(ProcedureItemResponse::from).toList();
    }

    @PostMapping("/appointments/{appointmentId}/visit")
    @ResponseStatus(HttpStatus.CREATED)
    VisitResponse open(@PathVariable long appointmentId, @Valid @RequestBody VisitRequest request) {
        return VisitResponse.from(visits.open(appointmentId, request.note()));
    }

    @GetMapping("/appointments/{appointmentId}/visit")
    VisitResponse byAppointment(@PathVariable long appointmentId) {
        return VisitResponse.from(visits.byAppointment(appointmentId));
    }

    @PutMapping("/visits/{id}/note")
    VisitResponse updateNote(@PathVariable long id, @Valid @RequestBody VisitRequest request) {
        return VisitResponse.from(visits.updateNote(id, request.note()));
    }

    @PostMapping("/visits/{id}/procedures")
    @ResponseStatus(HttpStatus.CREATED)
    VisitResponse addProcedure(@PathVariable long id, @Valid @RequestBody AddProcedureRequest request) {
        int quantity = request.quantity() == null ? 1 : request.quantity();
        return VisitResponse.from(visits.addProcedure(id, request.procedureId(), quantity));
    }

    @PostMapping("/visits/{id}/procedures/{itemId}/void")
    VisitResponse voidProcedure(@PathVariable long id, @PathVariable long itemId) {
        return VisitResponse.from(visits.voidProcedure(id, itemId));
    }
}

package cz.uhk.ordinace.insurer.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.insurer.dto.InsurerResponse;
import cz.uhk.ordinace.insurer.service.InsurerService;

@RestController
@RequestMapping("/api/insurers")
public class InsurerController {

    private final InsurerService insurers;

    public InsurerController(InsurerService insurers) {
        this.insurers = insurers;
    }

    @GetMapping
    List<InsurerResponse> list() {
        return insurers.list().stream().map(InsurerResponse::from).toList();
    }
}

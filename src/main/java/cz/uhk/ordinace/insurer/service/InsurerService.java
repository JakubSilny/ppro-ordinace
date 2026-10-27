package cz.uhk.ordinace.insurer.service;

import java.util.List;

import org.springframework.stereotype.Service;

import cz.uhk.ordinace.common.NotFoundException;
import cz.uhk.ordinace.insurer.domain.Insurer;
import cz.uhk.ordinace.insurer.repository.InsurerRepository;

@Service
public class InsurerService {

    private final InsurerRepository insurers;

    public InsurerService(InsurerRepository insurers) {
        this.insurers = insurers;
    }

    public List<Insurer> list() {
        return insurers.findAllByOrderByCode();
    }

    public Insurer get(long id) {
        return insurers.findById(id).orElseThrow(() -> new NotFoundException("Insurer " + id + " not found"));
    }
}

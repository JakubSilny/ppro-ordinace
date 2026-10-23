package cz.uhk.ordinace.patient.service;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.common.NotFoundException;
import cz.uhk.ordinace.insurer.domain.Insurer;
import cz.uhk.ordinace.insurer.service.InsurerService;
import cz.uhk.ordinace.patient.domain.Patient;
import cz.uhk.ordinace.patient.dto.PatientRequest;
import cz.uhk.ordinace.patient.repository.PatientRepository;

@Service
public class PatientService {

    private final PatientRepository patients;
    private final InsurerService insurers;
    private final Clock clock;

    public PatientService(PatientRepository patients, InsurerService insurers, Clock clock) {
        this.patients = patients;
        this.insurers = insurers;
        this.clock = clock;
    }

    /** Bez čísla pojištěnce se při shodě jména, roku a telefonu vyžaduje {@code confirmDuplicate}. */
    @Transactional
    public Patient create(PatientRequest req, boolean confirmDuplicate) {
        Optional<String> insuranceId = insuranceId(req);
        String phone = validated(req);

        insuranceId.flatMap(patients::findByInsuranceId).ifPresent(this::throwDuplicateInsuranceId);
        if (insuranceId.isEmpty() && !confirmDuplicate) {
            patients.findSameIdentity(req.firstName().trim(), req.lastName().trim(), req.birthYear(), phone)
                    .stream().findFirst().ifPresent(p -> {
                        throw new ConflictException("Patient with the same name, birth year and phone exists; "
                                + "confirm to create anyway", p.getId());
                    });
        }
        return patients.saveAndFlush(new Patient(req.firstName().trim(), req.lastName().trim(), req.birthYear(),
                phone, insuranceId, insurer(req)));
    }

    @Transactional
    public Patient update(long id, PatientRequest req) {
        Patient patient = get(id);
        Optional<String> insuranceId = insuranceId(req);
        String phone = validated(req);

        insuranceId.flatMap(patients::findByInsuranceId)
                .filter(other -> !other.getId().equals(id)).ifPresent(this::throwDuplicateInsuranceId);
        patient.update(req.firstName().trim(), req.lastName().trim(), req.birthYear(), phone, insuranceId,
                insurer(req));
        return patients.saveAndFlush(patient);
    }

    @Transactional(readOnly = true)
    public Patient get(long id) {
        return patients.findById(id).orElseThrow(() -> new NotFoundException("Patient " + id + " not found"));
    }

    /** Čísla a mezery = telefon, jinak začátek příjmení (bez diakritiky a velikosti písmen). */
    @Transactional(readOnly = true)
    public List<Patient> search(String query) {
        String q = query.trim();
        if (q.isEmpty()) {
            throw new BusinessRuleException("Search query must not be blank");
        }
        if (q.matches("[+\\d\\s]+")) {
            return patients.searchByPhone(PhoneNumbers.normalize(q));
        }
        return patients.searchByLastName(q.replaceAll("[%_\\\\]", ""));
    }

    @Transactional
    public Patient deactivate(long id) {
        Patient patient = get(id);
        long upcoming = patients.countUpcomingBookings(id, clock.instant());
        if (upcoming > 0) {
            throw new BusinessRuleException("Patient has " + upcoming + " upcoming appointments");
        }
        patient.deactivate();
        return patients.saveAndFlush(patient);
    }

    private void throwDuplicateInsuranceId(Patient existing) {
        throw new ConflictException("Insurance id is already registered", existing.getId());
    }

    private Optional<String> insuranceId(PatientRequest req) {
        return Optional.ofNullable(req.insuranceId()).map(String::trim).filter(s -> !s.isEmpty());
    }

    private Optional<Insurer> insurer(PatientRequest req) {
        return Optional.ofNullable(req.insurerId()).map(insurers::get);
    }

    /** Vrací normalizovaný telefon; chybný rok narození nebo telefon = 422. */
    private String validated(PatientRequest req) {
        int year = Year.now(clock).getValue();
        if (req.birthYear() < 1900 || req.birthYear() > year) {
            throw new BusinessRuleException("Birth year must be between 1900 and " + year);
        }
        return PhoneNumbers.normalize(req.phone());
    }
}

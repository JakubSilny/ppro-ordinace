package cz.uhk.ordinace.doctor.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.NotFoundException;
import cz.uhk.ordinace.doctor.domain.Doctor;
import cz.uhk.ordinace.doctor.domain.DoctorAbsence;
import cz.uhk.ordinace.doctor.domain.OfficeHours;
import cz.uhk.ordinace.doctor.dto.AbsenceRequest;
import cz.uhk.ordinace.doctor.dto.DoctorRequest;
import cz.uhk.ordinace.doctor.dto.OfficeHoursRequest;
import cz.uhk.ordinace.doctor.repository.DoctorAbsenceRepository;
import cz.uhk.ordinace.doctor.repository.DoctorRepository;
import cz.uhk.ordinace.doctor.repository.OfficeHoursRepository;

@Service
public class DoctorService {

    private final DoctorRepository doctors;
    private final OfficeHoursRepository officeHours;
    private final DoctorAbsenceRepository absences;
    private final Clock clock;

    public DoctorService(DoctorRepository doctors, OfficeHoursRepository officeHours,
            DoctorAbsenceRepository absences, Clock clock) {
        this.doctors = doctors;
        this.officeHours = officeHours;
        this.absences = absences;
        this.clock = clock;
    }

    @Transactional
    public Doctor create(DoctorRequest req) {
        return doctors.save(new Doctor(req.firstName().trim(), req.lastName().trim()));
    }

    @Transactional(readOnly = true)
    public Doctor get(long id) {
        return doctors.findById(id).orElseThrow(() -> new NotFoundException("Doctor " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Doctor> listActive() {
        return doctors.findByActiveTrueOrderByLastNameAscFirstNameAsc();
    }

    @Transactional
    public Doctor update(long id, DoctorRequest req) {
        Doctor doctor = get(id);
        doctor.update(req.firstName().trim(), req.lastName().trim());
        return doctors.save(doctor);
    }

    @Transactional
    public Doctor deactivate(long id) {
        Doctor doctor = get(id);
        long upcoming = doctors.countUpcomingBookings(id, clock.instant());
        if (upcoming > 0) {
            throw new BusinessRuleException("Doctor has " + upcoming + " upcoming appointments");
        }
        doctor.deactivate();
        return doctors.save(doctor);
    }

    @Transactional(readOnly = true)
    public List<OfficeHours> getOfficeHours(long doctorId) {
        get(doctorId);
        return officeHours.findByDoctorIdOrderByWeekdayAscFromTimeAsc(doctorId);
    }

    /** Nahradí celou týdenní šablonu; intervaly téhož dne se nesmí překrývat. */
    @Transactional
    public List<OfficeHours> replaceOfficeHours(long doctorId, List<OfficeHoursRequest> requested) {
        get(doctorId);
        for (OfficeHoursRequest r : requested) {
            if (!r.to().isAfter(r.from())) {
                throw new BusinessRuleException("Office hours must end after they start");
            }
        }
        List<OfficeHoursRequest> sorted = new ArrayList<>(requested);
        sorted.sort(Comparator.comparing(OfficeHoursRequest::weekday).thenComparing(OfficeHoursRequest::from));
        for (int i = 1; i < sorted.size(); i++) {
            OfficeHoursRequest prev = sorted.get(i - 1);
            OfficeHoursRequest cur = sorted.get(i);
            if (prev.weekday().equals(cur.weekday()) && cur.from().isBefore(prev.to())) {
                throw new BusinessRuleException("Office hours overlap on weekday " + cur.weekday());
            }
        }
        officeHours.deleteByDoctorId(doctorId);
        officeHours.flush();
        return officeHours.saveAll(sorted.stream().map(r -> new OfficeHours(doctorId, r.weekday(), r.from(),
                r.to(), r.location().trim(), r.bookable())).toList());
    }

    /** Zapíše absenci a označí dotčené rezervace ve stavu BOOKED k přeobjednání. */
    @Transactional
    public AbsenceResult addAbsence(long doctorId, AbsenceRequest req) {
        get(doctorId);
        if (!req.endAt().isAfter(req.startAt())) {
            throw new BusinessRuleException("Absence must end after it starts");
        }
        Optional<String> reason = Optional.ofNullable(req.reason()).map(String::trim).filter(s -> !s.isEmpty());
        DoctorAbsence saved = absences.save(new DoctorAbsence(doctorId, req.startAt(), req.endAt(), reason));
        int flagged = absences.flagForRebooking(doctorId, req.startAt(), req.endAt());
        return new AbsenceResult(saved, flagged);
    }

    @Transactional(readOnly = true)
    public List<DoctorAbsence> upcomingAbsences(long doctorId) {
        get(doctorId);
        return absences.findByDoctorIdAndEndAtAfterOrderByStartAt(doctorId, Instant.now(clock));
    }
}

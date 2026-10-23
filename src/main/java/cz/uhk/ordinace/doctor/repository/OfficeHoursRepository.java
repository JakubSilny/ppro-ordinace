package cz.uhk.ordinace.doctor.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cz.uhk.ordinace.doctor.domain.OfficeHours;

public interface OfficeHoursRepository extends JpaRepository<OfficeHours, Long> {

    List<OfficeHours> findByDoctorIdOrderByWeekdayAscFromTimeAsc(long doctorId);

    void deleteByDoctorId(long doctorId);
}

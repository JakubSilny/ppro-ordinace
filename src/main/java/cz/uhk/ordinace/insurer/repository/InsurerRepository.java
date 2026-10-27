package cz.uhk.ordinace.insurer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cz.uhk.ordinace.insurer.domain.Insurer;

public interface InsurerRepository extends JpaRepository<Insurer, Long> {

    List<Insurer> findAllByOrderByCode();
}

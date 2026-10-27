package cz.uhk.ordinace.visit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cz.uhk.ordinace.visit.domain.ProcedureItem;

public interface ProcedureItemRepository extends JpaRepository<ProcedureItem, Long> {

    List<ProcedureItem> findAllByOrderByCode();
}

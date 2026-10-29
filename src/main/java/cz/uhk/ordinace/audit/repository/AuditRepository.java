package cz.uhk.ordinace.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.audit.domain.AuditEntry;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    // ponytail: regulární výraz projde celou tabulku; při statisících záznamů ukládat k přístupu i id pacienta
    @Query(value = "SELECT * FROM audit_log WHERE path ~ :regex ORDER BY at DESC LIMIT 500", nativeQuery = true)
    List<AuditEntry> findByPathMatching(@Param("regex") String regex);
}

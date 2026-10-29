package cz.uhk.ordinace.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.audit.domain.AuditEntry;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    // ponytail: regulární výraz projde celou tabulku; při statisících záznamů ukládat k přístupu i id pacienta
    @Query(value = "SELECT * FROM audit_log WHERE path ~ :regex ORDER BY at DESC LIMIT 500", nativeQuery = true)
    List<AuditEntry> findByPathMatching(@Param("regex") String regex);

    /** Prázdné {@code username} = všichni uživatelé. */
    @Query("""
            select e from AuditEntry e
            where e.at >= :from and e.at < :to and (:username = '' or e.username = :username)
            order by e.at desc
            """)
    List<AuditEntry> search(@Param("from") Instant from, @Param("to") Instant to, @Param("username") String username,
            Limit limit);
}

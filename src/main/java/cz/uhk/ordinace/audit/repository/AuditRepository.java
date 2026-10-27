package cz.uhk.ordinace.audit.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.audit.domain.AuditEntry;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            select e from AuditEntry e
            where e.path = :path or e.path like concat(:path, '/%') or e.path = :historyPath
            order by e.at desc
            """)
    List<AuditEntry> findForPaths(@Param("path") String path, @Param("historyPath") String historyPath, Limit limit);
}

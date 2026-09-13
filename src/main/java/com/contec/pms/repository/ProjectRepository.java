package com.contec.pms.repository;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // userId null means "no membership filter", which is what administrators get
    @Query("""
            select p from Project p
            where (:status is null or p.status = :status)
              and (:userId is null
                   or exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId))
            """)
    Page<Project> findVisible(@Param("userId") Long userId,
                              @Param("status") ProjectStatus status,
                              Pageable pageable);
}

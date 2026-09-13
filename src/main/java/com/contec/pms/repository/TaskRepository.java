package com.contec.pms.repository;

import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
    Optional<Task> findWithDetailsById(Long id);

    @Query("""
            select t from Task t
            where t.project.id = :projectId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assignee.id = :assigneeId)
            """)
    Page<Task> findProjectTasks(@Param("projectId") Long projectId,
                                @Param("status") TaskStatus status,
                                @Param("priority") TaskPriority priority,
                                @Param("assigneeId") Long assigneeId,
                                Pageable pageable);
}

package com.contec.pms.repository;

import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.enums.TaskStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
    Optional<Task> findWithDetailsById(Long id);

    /** Used to stop a member being removed while they still owe work on the project. */
    long countByProjectIdAndAssigneeIdAndStatusIn(Long projectId, Long assigneeId,
                                                  Collection<TaskStatus> statuses);
}

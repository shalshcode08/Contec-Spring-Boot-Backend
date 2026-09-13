package com.contec.pms.repository;

import com.contec.pms.domain.entity.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    @EntityGraph(attributePaths = "actor")
    Page<TaskActivity> findByTaskId(Long taskId, Pageable pageable);

    List<TaskActivity> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    long countByTaskId(Long taskId);
}

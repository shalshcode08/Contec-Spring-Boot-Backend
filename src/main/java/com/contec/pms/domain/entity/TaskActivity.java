package com.contec.pms.domain.entity;

import com.contec.pms.domain.enums.ActivityType;
import com.contec.pms.domain.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "task_activities")
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private TaskStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 30)
    private TaskStatus newStatus;

    @Column(name = "old_progress")
    private Integer oldProgress;

    @Column(name = "new_progress")
    private Integer newProgress;

    @Column(name = "detail", length = 1000)
    private String detail;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskActivity() {
    }

    public TaskActivity(Task task, User actor, ActivityType activityType, String detail) {
        this(task, actor, activityType, null, null, null, null, detail);
    }

    public TaskActivity(Task task, User actor, ActivityType activityType,
                        TaskStatus oldStatus, TaskStatus newStatus,
                        Integer oldProgress, Integer newProgress, String detail) {
        this.task = task;
        this.actor = actor;
        this.activityType = activityType;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.oldProgress = oldProgress;
        this.newProgress = newProgress;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public User getActor() {
        return actor;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public TaskStatus getOldStatus() {
        return oldStatus;
    }

    public TaskStatus getNewStatus() {
        return newStatus;
    }

    public Integer getOldProgress() {
        return oldProgress;
    }

    public Integer getNewProgress() {
        return newProgress;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

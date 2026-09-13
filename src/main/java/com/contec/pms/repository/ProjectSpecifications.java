package com.contec.pms.repository;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.enums.ProjectStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public final class ProjectSpecifications {

    private ProjectSpecifications() {
    }

    public static Specification<Project> hasStatus(ProjectStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    // restricts results to projects the user belongs to
    public static Specification<Project> memberOf(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> membership = query.subquery(Long.class);
            Root<ProjectMember> member = membership.from(ProjectMember.class);
            membership.select(member.get("project").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return root.get("id").in(membership);
        };
    }

    public static Specification<Project> nameContains(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }
}

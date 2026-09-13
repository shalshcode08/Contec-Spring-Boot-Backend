package com.contec.pms.domain.enums;

/**
 * A user's role <em>within a single project</em>. A MANAGER may run the project
 * and approve work; an ENGINEER may only work on tasks assigned to them.
 */
public enum ProjectMemberRole {
    MANAGER,
    ENGINEER
}

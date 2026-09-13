package com.contec.pms.domain.enums;

/**
 * Global role of a user. Project-level authority additionally requires a
 * {@link ProjectMemberRole} on the project in question.
 */
public enum RoleName {
    ADMIN,
    PROJECT_MANAGER,
    SITE_ENGINEER;

    /** Spring Security expects authorities to be prefixed with {@code ROLE_}. */
    public String asAuthority() {
        return "ROLE_" + name();
    }
}

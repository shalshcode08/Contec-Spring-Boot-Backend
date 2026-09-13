package com.contec.pms.domain.enums;

public enum Role {
    ADMIN,
    PROJECT_MANAGER,
    SITE_ENGINEER;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}

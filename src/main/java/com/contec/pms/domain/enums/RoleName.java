package com.contec.pms.domain.enums;

public enum RoleName {
    ADMIN,
    PROJECT_MANAGER,
    SITE_ENGINEER;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}

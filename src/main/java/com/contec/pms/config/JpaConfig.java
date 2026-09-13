package com.contec.pms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables the @CreatedDate / @LastModifiedDate support used by the entities. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

package com.htac_backend.modules.administration.config;

import com.htac_backend.modules.administration.service.BootstrapAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class BootstrapAdminConfig {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    @Bean
    @ConditionalOnProperty(name = "his.bootstrap-admin.enabled", havingValue = "true")
    ApplicationRunner bootstrapAdmin(BootstrapAdminService bootstrap,
                                     @Value("${HIS_BOOTSTRAP_ADMIN_USERNAME:}") String username,
                                     @Value("${HIS_BOOTSTRAP_ADMIN_PASSWORD:}") String password,
                                     @Value("${HIS_BOOTSTRAP_ADMIN_FULL_NAME:}") String fullName,
                                     @Value("${HIS_BOOTSTRAP_ADMIN_PHONE:}") String phone) {
        return args -> {
            if (bootstrap.createIfMissing(username, password, fullName, phone)) {
                log.info("Đã khởi tạo tài khoản ADMIN đầu tiên");
            } else {
                log.info("Đã có tài khoản ADMIN; bỏ qua khởi tạo");
            }
        };
    }
}

package in.zeta.zea_2026_b02_piyushm_microloan.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application used as the context configuration for
 * {@code @WebMvcTest} controller slice tests.
 *
 * Using the real application class would pull in the Zeta SandboxAuthorizedSync
 * AOP interceptor and other heavy infrastructure beans that are not needed in
 * controller unit tests. This class keeps the slice lean.
 */
@SpringBootApplication(scanBasePackages = "in.zeta.zea_2026_b02_piyushm_microloan.controller")
public class WebMvcSliceTestApplication {
}

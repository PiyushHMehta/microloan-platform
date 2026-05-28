package in.zeta.zea_2026_b02_piyushm_microloan_notif;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    FlywayAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "in.zeta.springframework.boot.commons",
    "in.zeta.zea_2026_b02_piyushm_microloan_notif"
})
public class Zea2026B02PiyushmMicroloanNotifApplication {

	public static void main(String[] args) {
		SpringApplication.run(Zea2026B02PiyushmMicroloanNotifApplication.class, args);
	}

}

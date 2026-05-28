package in.zeta.zea_2026_b02_piyushm_microloan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
    "in.zeta.springframework.boot.commons",
    "in.zeta.zea_2026_b02_piyushm_microloan",
    "in.zeta.oms.atropos.client",
	"tech.zeta.academy.olympus.cipher"
})
public class Zea2026B02PiyushmMicroloanApplication {

	public static void main(String[] args) {
		SpringApplication.run(Zea2026B02PiyushmMicroloanApplication.class, args);
	}

}

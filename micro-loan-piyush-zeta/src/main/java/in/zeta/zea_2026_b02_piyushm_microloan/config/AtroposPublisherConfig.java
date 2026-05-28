package in.zeta.zea_2026_b02_piyushm_microloan.config;

import in.zeta.oms.atropos.client.AtroposPublisherClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AtroposPublisherConfig {

    @Bean
    public AtroposPublisherClient atroposPublisherClient() {
        return new AtroposPublisherClient();
    }
}

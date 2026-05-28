package in.zeta.zea_2026_b02_piyushm_microloan.config;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAccessControlProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.BorrowerProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.InternalAdminProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.KycProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.LoanApplicationProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.LoanProductProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.LoanProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.RepaymentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SandboxConfig {

    private final ApplicationContext applicationContext;

    @Autowired
    public SandboxConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    @Primary
    public SandboxAccessControlProvider getSandboxAccessControlProvider(
            BorrowerProvider borrowerProvider,
            KycProvider kycProvider,
            LoanProductProvider loanProductProvider,
            LoanApplicationProvider loanApplicationProvider,
            LoanProvider loanProvider,
            RepaymentProvider repaymentProvider,
            InternalAdminProvider internalAdminProvider
    ) {
        SandboxAccessControlProvider sacp = applicationContext.getBean("sandboxAccessControlProvider", SandboxAccessControlProvider.class);
        sacp.registerObjectProvider(BorrowerProvider.OBJECT_TYPE, borrowerProvider);
        sacp.registerObjectProvider(KycProvider.OBJECT_TYPE, kycProvider);
        sacp.registerObjectProvider(LoanProductProvider.OBJECT_TYPE, loanProductProvider);
        sacp.registerObjectProvider(LoanApplicationProvider.OBJECT_TYPE, loanApplicationProvider);
        sacp.registerObjectProvider(LoanProvider.OBJECT_TYPE, loanProvider);
        sacp.registerObjectProvider(RepaymentProvider.OBJECT_TYPE, repaymentProvider);
        sacp.registerObjectProvider(InternalAdminProvider.OBJECT_TYPE, internalAdminProvider);
        return sacp;
    }
}

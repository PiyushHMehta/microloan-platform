package in.zeta.zea_2026_b02_piyushm_microloan.provider;

import in.zeta.oms.sandbox.model.object.ObjectProvider;
import in.zeta.oms.sandbox.model.realm.Realm;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.CipherPayload;
import lombok.RequiredArgsConstructor;
import olympus.common.JID;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@RequiredArgsConstructor
public class InternalAdminProvider implements ObjectProvider<CipherPayload> {

    public static final String OBJECT_TYPE = "internalAdmin";

    @Override
    public CompletionStage<Optional<CipherPayload>> getObject(JID jid, Realm realm, Long tenantID) {
        CipherPayload payload = new CipherPayload(OBJECT_TYPE);
        return CompletableFuture.completedFuture(Optional.of(payload));
    }
}

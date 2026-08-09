package io.github.somaruntime.examples.realtimedispatch.application;

import io.github.somaruntime.examples.realtimedispatch.domain.DispatchPayload;

public interface DispatchGateway {
    void dispatch(DispatchDecision decision, DispatchPayload payload);
}

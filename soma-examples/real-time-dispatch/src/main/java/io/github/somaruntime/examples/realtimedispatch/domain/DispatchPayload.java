package io.github.somaruntime.examples.realtimedispatch.domain;

/** Application-owned ordinary Java referent; SOMA stores only its reference. */
public final class DispatchPayload {
    private final String requestId;

    public DispatchPayload(String requestId) {
        this.requestId = requestId;
    }

    public String requestId() {
        return requestId;
    }
}

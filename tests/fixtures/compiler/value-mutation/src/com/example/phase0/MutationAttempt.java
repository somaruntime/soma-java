package com.example.phase0;

public final class MutationAttempt {
    private MutationAttempt() {
    }

    public static void mutate(MachineId value) {
        value.value = 9L;
    }
}

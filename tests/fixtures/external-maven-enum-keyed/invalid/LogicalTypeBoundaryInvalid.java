package com.example.soma.enumkeyed;

import com.example.soma.enumkeyed.generated.DateKeyedDayDataFlow;
import com.example.soma.enumkeyed.generated.DateTimeKeyedMomentDataFlow;
import com.example.soma.enumkeyed.generated.EnumKeyedJobDataFlow;
import com.example.soma.enumkeyed.generated.TimeKeyedTickDataFlow;

/** Must not compile: logical facades do not expose raw carrier operations. */
public final class LogicalTypeBoundaryInvalid {
    private LogicalTypeBoundaryInvalid() {
    }

    static void invalidEnumArithmetic() {
        EnumKeyedJobDataFlow.source("enum")
                .columns().state().plus(1L);
    }

    static void invalidDateCarrierComparison() {
        DateKeyedDayDataFlow.source("date")
                .columns().epochDay().equalTo(20_000L);
    }

    static void invalidTimeNumericOrdering() {
        TimeKeyedTickDataFlow.source("time")
                .columns().nanosOfDay().greaterThan(12L);
    }

    static void invalidInstantCarrierComparison() {
        DateTimeKeyedMomentDataFlow.source("instant")
                .columns().epochMillis().equalTo(1_700_000_000_000L);
    }
}

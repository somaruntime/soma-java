package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.examples.simulation.generated.FlowCoefficientBatch;
import com.hgtech.soma.examples.simulation.generated.FlowCoefficientTable;
import com.hgtech.soma.examples.simulation.generated.PendingEventRowBatch;
import com.hgtech.soma.examples.simulation.generated.PendingEventRowTable;
import com.hgtech.soma.examples.simulation.generated.StateVectorRowBatch;
import com.hgtech.soma.examples.simulation.generated.StateVectorRowTable;
import com.hgtech.soma.examples.simulation.generated.TankBatch;
import com.hgtech.soma.examples.simulation.generated.TankTable;
import com.hgtech.soma.examples.simulation.generated.TraceSampleRowBatch;
import com.hgtech.soma.examples.simulation.generated.TraceSampleRowTable;
import com.hgtech.soma.examples.simulation.generated.ValveBatch;
import com.hgtech.soma.examples.simulation.generated.ValveTable;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.List;

/** Dense state vector 为事实源、event/trace 为独立 workspace 的正式仿真场景。 */
public final class SimulationScenario {
    private SimulationScenario() { }

    public static ScenarioResult run() {
        ValveMaterialKey coefficientKey = new ValveMaterialKey(
                new ValveId(1L), new MaterialId(9L));
        TankId sourceTank = new TankId(10L);
        TankId targetTank = new TankId(20L);
        TankTable tanks = TankTable.create();
        ValveTable valves = ValveTable.create();
        FlowCoefficientTable coefficients = FlowCoefficientTable.create();
        StateVectorRowTable state = StateVectorRowTable.create();
        PendingEventRowTable events = PendingEventRowTable.create();
        TraceSampleRowTable trace = TraceSampleRowTable.create();
        try {
            tanks.addBatch(new TankBatch(2)
                    .addValues(sourceTank, new MaterialId(9L), 100.0d,
                            200.0d, 20.0d, 0L)
                    .addValues(targetTank, new MaterialId(9L), 20.0d,
                            200.0d, 20.0d, 0L));
            valves.addBatch(new ValveBatch(1).addValues(new ValveId(1L),
                    sourceTank, targetTank, 0.25d, 8.0d, true));
            coefficients.addBatch(new FlowCoefficientBatch(1)
                    .addValues(coefficientKey, 0.5d));
            require(tanks.byTankId().firstOrThrow().tankId.equals(sourceTank)
                            && valves.findByFromTank(sourceTank).count() == 1L
                            && valves.findByToTank(targetTank).count() == 1L,
                    "tank order and valve topology indexes are live");
            state.reserve(3);
            state.addBatch(new StateVectorRowBatch(3)
                    .addValues(0, SimEntityKind.TANK, 10L,
                            SimVariableKind.LEVEL_LITERS, 100.0d, -2.0d, 1.0d)
                    .addValues(1, SimEntityKind.TANK, 10L,
                            SimVariableKind.TEMPERATURE_CELSIUS, 20.0d, 0.1d, 1.0d)
                    .addValues(2, SimEntityKind.VALVE, 1L,
                            SimVariableKind.VALVE_OPENING_RATIO, 0.25d, 0.0d, 1.0d));
            events.addBatch(new PendingEventRowBatch(2)
                    .addValues(2000L, 2L, SimEventKind.SENSOR_SAMPLE,
                            SimEntityKind.TANK, 10L, false, 0.0d)
                    .addValues(1000L, 1L, SimEventKind.VALVE_SETPOINT,
                            SimEntityKind.VALVE, 1L, true, 0.75d));

            PendingEventRow due = events.byEventTime().firstOrThrow();
            require(due.numericPayload != null && due.numericPayload == 0.75d,
                    "ordered event source preserves optional payload presence");

            DoubleColumnView values = state.valueColumn();
            try {
                require(values.getDouble(0) == 100.0d && values.getDouble(2) == 0.25d,
                        "typed ColumnView reads the authoritative numeric vector");
            } finally {
                values.close();
            }

            double coefficient = coefficients.fetch(coefficientKey).coefficient;
            UpdateResult stepped = state.update(row -> {
                if (row.variableKind() == SimVariableKind.LEVEL_LITERS) {
                    row.setDerivative(-4.0d * coefficient);
                    row.setValue(row.value() + row.derivative());
                } else if (row.variableKind() == SimVariableKind.VALVE_OPENING_RATIO) {
                    row.setValue(due.numericPayload.doubleValue());
                }
            });
            require(stepped.changed() == 2L && state.fetchAt(0).value == 98.0d,
                    "state-vector update publishes numerical facts without DTO live storage");
            // entity表只在step/export边界同步cache，StateVector仍是数值事实源。
            tanks.mutate(sourceTank).setLevelLiters(state.fetchAt(0).value)
                    .setLastUpdateMillis(1000L).commit();
            valves.mutate(new ValveId(1L)).setOpeningRatio(state.fetchAt(2).value).commit();
            require(tanks.fetch(sourceTank).levelLiters == 98.0d
                            && valves.fetch(new ValveId(1L)).openingRatio == 0.75d,
                    "entity boundary caches are synchronized from StateVector");
            events.filter(row -> row.eventTimeMillis() <= 1000L).remove();
            require(events.size() == 1, "due event is separately compacted after application");

            trace.addBatch(new TraceSampleRowBatch(2)
                    .addValues(1000L, SimEntityKind.TANK, 10L,
                            SimVariableKind.LEVEL_LITERS, state.fetchAt(0).value)
                    .addValues(1000L, SimEntityKind.VALVE, 1L,
                            SimVariableKind.VALVE_OPENING_RATIO, state.fetchAt(2).value));
            List<TraceSampleRow> exported = trace.byTimeEntity().fetchAll();
            require(exported.size() == 2 && exported.get(0).value == 98.0d,
                    "trace is a detached export buffer, not the state fact source");
            expectCode("missing_key", () -> coefficients.fetch(new ValveMaterialKey(
                    new ValveId(2L), new MaterialId(9L))));

            return new ScenarioResult(exported.size(), state.runtimePlan().schemaHash(),
                    state.statsSnapshot().lastChanged());
        } finally {
            trace.release();
            events.release();
            state.release();
            coefficients.release();
            valves.release();
            tanks.release();
        }
    }

    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaRuntimeException failure) {
            require(code.equals(failure.code()), "unexpected runtime code " + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }

    public static final class ScenarioResult {
        public final int traceSamples;
        public final String schemaHash;
        public final long changedRows;
        ScenarioResult(int traceSamples, String schemaHash, long changedRows) {
            this.traceSamples = traceSamples;
            this.schemaHash = schemaHash;
            this.changedRows = changedRows;
        }
    }
}

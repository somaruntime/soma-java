package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.examples.simulation.generated.FlowCoefficientBatch;
import com.hgtech.soma.examples.simulation.generated.FlowCoefficientTable;
import com.hgtech.soma.examples.simulation.generated.PendingEventRowBatch;
import com.hgtech.soma.examples.simulation.generated.PendingEventRowTable;
import com.hgtech.soma.examples.simulation.generated.StateVectorRowBatch;
import com.hgtech.soma.examples.simulation.generated.StateVectorRowTable;
import com.hgtech.soma.examples.simulation.generated.TankDefinitionBatch;
import com.hgtech.soma.examples.simulation.generated.TankDefinitionTable;
import com.hgtech.soma.examples.simulation.generated.TraceSampleRowBatch;
import com.hgtech.soma.examples.simulation.generated.TraceSampleRowTable;
import com.hgtech.soma.examples.simulation.generated.ValveDefinitionBatch;
import com.hgtech.soma.examples.simulation.generated.ValveDefinitionTable;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Authoritative dense vector、application event heap 与 trace boundary 场景。 */
public final class SimulationScenario {
  private static final long ONE_SECOND_NANOS = 1_000_000_000L;

  private SimulationScenario() {
  }

  public static ScenarioResult run() {
    validateInput();
    verifyNumericsFailureAtomicity();

    TankId sourceTank = new TankId(10L);
    TankId targetTank = new TankId(20L);
    ValveId valveId = new ValveId(1L);
    MaterialId materialId = new MaterialId(9L);
    ValveMaterialKey coefficientKey =
      new ValveMaterialKey(valveId, materialId);
    TankDefinitionTable tanks = TankDefinitionTable.create();
    ValveDefinitionTable valves = ValveDefinitionTable.create();
    FlowCoefficientTable coefficients = FlowCoefficientTable.create();
    StateVectorRowTable state = StateVectorRowTable.create();
    PendingEventRowTable eventProjection = PendingEventRowTable.create();
    TraceSampleRowTable trace = TraceSampleRowTable.create();
    try {
      tanks.addBatch(new TankDefinitionBatch(2)
        .addValues(sourceTank, materialId, 200.0d)
        .addValues(targetTank, materialId, 200.0d));
      valves.addBatch(new ValveDefinitionBatch(1)
        .addValues(valveId, sourceTank, targetTank, 8.0d, true));
      coefficients.addBatch(new FlowCoefficientBatch(1)
        .addValues(coefficientKey, 0.5d));
      state.addBatch(new StateVectorRowBatch(2)
        .addValues(0, SimEntityKind.TANK, sourceTank.value,
          SimVariableKind.LEVEL_LITERS, 100.0d, 0.0d, 1.0d)
        .addValues(1, SimEntityKind.VALVE, valveId.value,
          SimVariableKind.VALVE_OPENING_RATIO, 0.25d, 0.0d, 1.0d));
      validateDenseVectorLayout(state);
      require(valves.findByFromTank(sourceTank).count() == 1L
          && valves.findByToTank(targetTank).count() == 1L,
        "definition topology indexes are live without numeric shadows");

      double coefficient = readCoefficient(coefficients, coefficientKey);
      Simulator simulator = new Simulator(state, eventProjection,
        trace, coefficient, 8.0d);
      simulator.schedule(ONE_SECOND_NANOS,
        SimEventKind.VALVE_SETPOINT, SimEntityKind.VALVE,
        valveId.value, true, 0.75d);
      simulator.schedule(ONE_SECOND_NANOS,
        SimEventKind.SENSOR_SAMPLE, SimEntityKind.TANK,
        sourceTank.value, false, 0.0d);
      simulator.schedule(Math.multiplyExact(2L, ONE_SECOND_NANOS),
        SimEventKind.SENSOR_SAMPLE, SimEntityKind.TANK,
        sourceTank.value, false, 0.0d);
      simulator.rebuildEventProjection();
      require(eventProjection.size() == 3,
        "optional event projection mirrors a heap boundary only");

      simulator.advanceTo(ONE_SECOND_NANOS);
      require(simulator.appliedCount == 2
          && simulator.appliedSequences[0] == 0L
          && simulator.appliedSequences[1] == 1L
          && eventProjection.size() == 3,
        "same-time heap order is total and projection is not consumed");
      simulator.rebuildEventProjection();
      require(eventProjection.size() == 1,
        "projection rebuild follows authoritative heap at an explicit boundary");

      long finalTime = Math.multiplyExact(2L, ONE_SECOND_NANOS);
      simulator.advanceTo(finalTime);
      simulator.rebuildEventProjection();
      require(eventProjection.size() == 0 && simulator.appliedCount == 3,
        "heap is the only pending-event state");
      expectIllegalArgument(() -> simulator.schedule(
        ONE_SECOND_NANOS, SimEventKind.SENSOR_SAMPLE,
        SimEntityKind.TANK, sourceTank.value, false, 0.0d));

      simulator.sampleTrace(finalTime);
      List<TraceSampleRow> exported = trace.rows().sorted((left, right) -> {
        int compared = Long.compare(
          left.sampleTimeNanos(), right.sampleTimeNanos());
        if (compared != 0) return compared;
        compared = left.entityKind().compareTo(right.entityKind());
        if (compared != 0) return compared;
        compared = Long.compare(left.entityId(), right.entityId());
        return compared != 0 ? compared
          : left.variableKind().compareTo(right.variableKind());
      }).fetchAll();
      List<StateVectorRow> finalState = state.fetchAll();
      require(exported.size() == 2 && finalState.size() == 2,
        "trace and final-state projections remain distinct boundaries");
      require(readStateValue(state, SimEntityKind.TANK, sourceTank.value,
          SimVariableKind.LEVEL_LITERS) == 96.0d
          && readStateValue(state, SimEntityKind.VALVE, valveId.value,
          SimVariableKind.VALVE_OPENING_RATIO) == 0.75d,
        "event-boundary integration updates only authoritative vector state");
      expectCode("missing_key", () -> coefficients.fetch(
        new ValveMaterialKey(new ValveId(2L), materialId)));

      long reads = Math.addExact(finalState.size(), exported.size());
      return new ScenarioResult(exported.size(),
        state.runtimePlan().schemaHash(),
        simulator.stateMutationCount, state.size(), 44,
        (long) state.capacity() * 44L, reads,
        simulator.stateMutationCount, exported.size());
    } finally {
      trace.release();
      eventProjection.release();
      state.release();
      coefficients.release();
      valves.release();
      tanks.release();
    }
  }

  private static double readCoefficient(
      FlowCoefficientTable coefficients, ValveMaterialKey key) {
    int row = coefficients.rowIndexOf(
      key.valveId.value, key.materialId.value);
    DoubleColumnView values = coefficients.coefficientColumn();
    try {
      return values.getDouble(row);
    } finally {
      values.close();
    }
  }

  private static double readStateValue(
      StateVectorRowTable state, SimEntityKind entityKind,
      long entityId, SimVariableKind variableKind) {
    IndexSnapshot rows = state.filter(row ->
      row.entityKind() == entityKind && row.entityId() == entityId
        && row.variableKind() == variableKind).rowIndexes();
    require(rows.size() == 1, "state mapping must identify one vector slot");
    DoubleColumnView values = state.valueColumn();
    try {
      return values.getDouble(rows.indexAt(0));
    } finally {
      values.close();
    }
  }

  private static void validateInput() {
    requireInput(Double.isFinite(200.0d) && 200.0d > 0.0d,
      "tank capacity must be finite and positive");
    requireInput(Double.isFinite(8.0d) && 8.0d >= 0.0d,
      "valve flow must be finite and non-negative");
    requireInput(Double.isFinite(0.5d),
      "coefficient must be finite");
    Math.multiplyExact(2L, ONE_SECOND_NANOS);
  }

  private static void validateDenseVectorLayout(StateVectorRowTable state) {
    int size = state.size();
    boolean[] slots = new boolean[size];
    IntColumnView indexes = state.vectorIndexColumn();
    EnumColumnView<SimEntityKind> entityKinds = state.entityKindColumn();
    LongColumnView entityIds = state.entityIdColumn();
    EnumColumnView<SimVariableKind> variableKinds = state.variableKindColumn();
    DoubleColumnView values = state.valueColumn();
    DoubleColumnView derivatives = state.derivativeColumn();
    DoubleColumnView scales = state.scaleColumn();
    try {
      for (int row = 0; row < size; row++) {
        int slot = indexes.getInt(row);
        require(slot >= 0 && slot < size && !slots[slot],
          "vectorIndex must uniquely cover [0,size)");
        slots[slot] = true;
        requireFinite(values.getDouble(row), derivatives.getDouble(row),
          scales.getDouble(row));
        for (int previous = 0; previous < row; previous++) {
          require(entityKinds.get(previous) != entityKinds.get(row)
              || entityIds.getLong(previous) != entityIds.getLong(row)
              || variableKinds.get(previous) != variableKinds.get(row),
            "state entity mapping must be unique");
        }
      }
    } finally {
      scales.close();
      derivatives.close();
      values.close();
      variableKinds.close();
      entityIds.close();
      entityKinds.close();
      indexes.close();
    }
  }

  private static void verifyNumericsFailureAtomicity() {
    StateVectorRowTable invalid = StateVectorRowTable.create();
    try {
      invalid.addBatch(new StateVectorRowBatch(2)
        .addValues(0, SimEntityKind.TANK, 1L,
          SimVariableKind.LEVEL_LITERS, 1.0d, 1.0d, 1.0d)
        .addValues(1, SimEntityKind.TANK, 2L,
          SimVariableKind.LEVEL_LITERS, 2.0d, 1.0d, 0.0d));
      IntegrationWorkspace integration = new IntegrationWorkspace(2);
      try {
        integration.integrate(invalid, 1.0d);
        throw new AssertionError("expected numeric invariant failure");
      } catch (SimulationNumericsException expected) {
        DoubleColumnView values = invalid.valueColumn();
        try {
          require(values.getDouble(0) == 1.0d
              && values.getDouble(1) == 2.0d,
            "numeric preflight failure publishes no partial vector update");
        } finally {
          values.close();
        }
      }
    } finally {
      invalid.release();
    }
  }

  private static void requireFinite(
      double value, double derivative, double scale) {
    if (!Double.isFinite(value) || !Double.isFinite(derivative)
        || !Double.isFinite(scale) || scale == 0.0d) {
      throw new SimulationNumericsException("invalid state vector row");
    }
  }

  private static final class Simulator {
    private static final Comparator<SimEvent> EVENT_ORDER =
      new Comparator<SimEvent>() {
        @Override
        public int compare(SimEvent left, SimEvent right) {
          int compared = Long.compare(
            left.simulationTimeNanos, right.simulationTimeNanos);
          return compared != 0 ? compared
            : Long.compare(left.sequenceNo, right.sequenceNo);
        }
      };

    private final StateVectorRowTable state;
    private final PendingEventRowTable projection;
    private final TraceSampleRowTable trace;
    private final PriorityQueue<SimEvent> eventHeap =
      new PriorityQueue<SimEvent>(8, EVENT_ORDER);
    private final PendingEventRowBatch projectionBatch =
      new PendingEventRowBatch(8);
    private final TraceSampleRowBatch traceBatch;
    private final IntegrationWorkspace integration;
    private final double[] derivativeScratch;
    private final double coefficient;
    private final double maximumFlow;
    private final long[] appliedSequences = new long[8];
    private long nextSequenceNo;
    private long currentTimeNanos;
    private long lastSampleTimeNanos = -1L;
    private int appliedCount;
    private long stateMutationCount;
    private boolean failed;

    Simulator(StateVectorRowTable state, PendingEventRowTable projection,
              TraceSampleRowTable trace, double coefficient,
              double maximumFlow) {
      this.state = state;
      this.projection = projection;
      this.trace = trace;
      this.coefficient = coefficient;
      this.maximumFlow = maximumFlow;
      traceBatch = new TraceSampleRowBatch(state.size());
      integration = new IntegrationWorkspace(state.size());
      derivativeScratch = new double[state.size()];
    }

    void schedule(long simulationTimeNanos, SimEventKind eventKind,
                  SimEntityKind targetKind, long targetId,
                  boolean payloadPresent, double payload) {
      requireUsable();
      if (simulationTimeNanos < currentTimeNanos || sequenceExhausted()) {
        throw new IllegalArgumentException(
          "event time/sequence is outside the current session");
      }
      if (payloadPresent && !Double.isFinite(payload)) {
        throw new IllegalArgumentException("event payload must be finite");
      }
      long sequence = nextSequenceNo;
      nextSequenceNo = Math.addExact(nextSequenceNo, 1L);
      eventHeap.add(new SimEvent(simulationTimeNanos, sequence,
        eventKind, targetKind, targetId, payloadPresent, payload));
    }

    void rebuildEventProjection() {
      requireUsable();
      projectionBatch.clear();
      // PriorityQueue iterator is deliberately treated as unordered.
      for (SimEvent event : eventHeap) {
        projectionBatch.addValues(event.simulationTimeNanos,
          event.sequenceNo, event.eventKind, event.targetKind,
          event.targetId, event.payloadPresent, event.payload);
      }
      projection.replaceAll(projectionBatch);
    }

    void advanceTo(long targetTimeNanos) {
      requireUsable();
      if (targetTimeNanos < currentTimeNanos) {
        throw new IllegalArgumentException("simulation time cannot move backwards");
      }
      try {
        consumeDueEventsAt(currentTimeNanos);
        while (currentTimeNanos < targetTimeNanos) {
          long segmentEnd = eventHeap.isEmpty()
            ? targetTimeNanos
            : Math.min(targetTimeNanos,
            eventHeap.peek().simulationTimeNanos);
          if (segmentEnd < currentTimeNanos) {
            throw new IllegalStateException("late event in simulation heap");
          }
          long elapsed = Math.subtractExact(segmentEnd, currentTimeNanos);
          if (elapsed != 0L) {
            computeDerivatives();
            stateMutationCount = Math.addExact(stateMutationCount,
              integration.integrate(state, elapsed * 1.0e-9d).changed());
          }
          currentTimeNanos = segmentEnd;
          consumeDueEventsAt(currentTimeNanos);
        }
      } catch (RuntimeException failure) {
        failed = true;
        throw failure;
      } catch (Error failure) {
        failed = true;
        throw failure;
      }
    }

    void sampleTrace(long sampleTimeNanos) {
      requireUsable();
      if (sampleTimeNanos < 0L
          || sampleTimeNanos <= lastSampleTimeNanos) {
        throw new IllegalArgumentException("trace time must increase");
      }
      traceBatch.clear();
      state.forEach(row -> traceBatch.addValues(sampleTimeNanos,
        row.entityKind(), row.entityId(), row.variableKind(), row.value()));
      trace.addBatch(traceBatch);
      lastSampleTimeNanos = sampleTimeNanos;
    }

    private void computeDerivatives() {
      DoubleColumnView values = state.valueColumn();
      IntColumnView indexes = state.vectorIndexColumn();
      EnumColumnView<SimVariableKind> kinds = state.variableKindColumn();
      double opening = 0.0d;
      try {
        for (int row = 0; row < state.size(); row++) {
          double value = values.getDouble(row);
          if (!Double.isFinite(value)) {
            throw new SimulationNumericsException("non-finite state value");
          }
          if (kinds.get(row) == SimVariableKind.VALVE_OPENING_RATIO) {
            opening = value;
          }
        }
        if (opening < 0.0d || opening > 1.0d) {
          throw new SimulationNumericsException("valve opening out of range");
        }
        for (int row = 0; row < state.size(); row++) {
          int slot = indexes.getInt(row);
          derivativeScratch[slot] = kinds.get(row)
            == SimVariableKind.LEVEL_LITERS
            ? -opening * maximumFlow * coefficient : 0.0d;
          if (!Double.isFinite(derivativeScratch[slot])) {
            throw new SimulationNumericsException("non-finite derivative");
          }
        }
      } finally {
        kinds.close();
        indexes.close();
        values.close();
      }
      UpdateResult published = state.update(row ->
        row.setDerivative(derivativeScratch[row.vectorIndex()]));
      stateMutationCount = Math.addExact(
        stateMutationCount, published.changed());
    }

    private void consumeDueEventsAt(long timeNanos) {
      while (!eventHeap.isEmpty()
          && eventHeap.peek().simulationTimeNanos == timeNanos) {
        SimEvent event = eventHeap.peek();
        applyEvent(event);
        if (eventHeap.poll() != event) {
          throw new IllegalStateException("event heap head changed during apply");
        }
        if (appliedCount == appliedSequences.length) {
          throw new IllegalStateException("applied-event evidence exhausted");
        }
        appliedSequences[appliedCount++] = event.sequenceNo;
      }
    }

    private void applyEvent(SimEvent event) {
      if (event.eventKind == SimEventKind.SENSOR_SAMPLE) return;
      if (event.eventKind != SimEventKind.VALVE_SETPOINT
          || event.targetKind != SimEntityKind.VALVE
          || !event.payloadPresent || event.payload < 0.0d
          || event.payload > 1.0d) {
        throw new IllegalArgumentException("unsupported or invalid event");
      }
      UpdateResult applied = state.filter(row ->
        row.entityKind() == event.targetKind
          && row.entityId() == event.targetId
          && row.variableKind() == SimVariableKind.VALVE_OPENING_RATIO)
        .update(row -> row.setValue(event.payload));
      if (applied.matched() != 1L) {
        throw new IllegalStateException("event target must map to one state slot");
      }
      stateMutationCount = Math.addExact(
        stateMutationCount, applied.changed());
    }

    private boolean sequenceExhausted() {
      return nextSequenceNo == Long.MAX_VALUE;
    }

    private void requireUsable() {
      if (failed) {
        throw new IllegalStateException("simulation session is fail-stop");
      }
    }
  }

  private static final class IntegrationWorkspace {
    private final double[] nextValues;

    IntegrationWorkspace(int vectorSize) {
      nextValues = new double[vectorSize];
    }

    UpdateResult integrate(StateVectorRowTable state, double dtSeconds) {
      if (!Double.isFinite(dtSeconds) || dtSeconds < 0.0d) {
        throw new IllegalArgumentException("invalid integration interval");
      }
      if (state.size() != nextValues.length) {
        throw new IllegalStateException("state layout changed during session");
      }
      IntColumnView indexes = state.vectorIndexColumn();
      DoubleColumnView values = state.valueColumn();
      DoubleColumnView derivatives = state.derivativeColumn();
      DoubleColumnView scales = state.scaleColumn();
      try {
        for (int row = 0; row < state.size(); row++) {
          int slot = indexes.getInt(row);
          double value = values.getDouble(row);
          double derivative = derivatives.getDouble(row);
          double scale = scales.getDouble(row);
          requireFinite(value, derivative, scale);
          double next = value + derivative * dtSeconds / scale;
          if (!Double.isFinite(next)) {
            throw new SimulationNumericsException(
              "non-finite integration result");
          }
          nextValues[slot] = next;
        }
      } finally {
        scales.close();
        derivatives.close();
        values.close();
        indexes.close();
      }
      return state.update(row ->
        row.setValue(nextValues[row.vectorIndex()]));
    }
  }

  private static final class SimEvent {
    final long simulationTimeNanos;
    final long sequenceNo;
    final SimEventKind eventKind;
    final SimEntityKind targetKind;
    final long targetId;
    final boolean payloadPresent;
    final double payload;

    SimEvent(long simulationTimeNanos, long sequenceNo,
             SimEventKind eventKind, SimEntityKind targetKind,
             long targetId, boolean payloadPresent, double payload) {
      this.simulationTimeNanos = simulationTimeNanos;
      this.sequenceNo = sequenceNo;
      this.eventKind = eventKind;
      this.targetKind = targetKind;
      this.targetId = targetId;
      this.payloadPresent = payloadPresent;
      this.payload = payload;
    }
  }

  private static void expectIllegalArgument(Action action) {
    try {
      action.run();
      throw new AssertionError("expected application preflight failure");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  private static void expectCode(String code, Action action) {
    try {
      action.run();
      throw new AssertionError("expected " + code);
    } catch (SomaRuntimeException failure) {
      require(code.equals(failure.code()),
        "unexpected runtime code " + failure.code());
    }
  }

  private static void requireInput(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private interface Action {
    void run();
  }

  public static final class ScenarioResult {
    public final int traceSamples;
    public final String schemaHash;
    public final long changedRows;
    public final int apcRows;
    public final int aggregateHotLeafWidths;
    public final long hotLeafWorkingSetBytes;
    public final long reads;
    public final long mutations;
    public final int exports;

    ScenarioResult(int traceSamples, String schemaHash, long changedRows,
                   int apcRows, int aggregateHotLeafWidths,
                   long hotLeafWorkingSetBytes, long reads, long mutations,
                   int exports) {
      this.traceSamples = traceSamples;
      this.schemaHash = schemaHash;
      this.changedRows = changedRows;
      this.apcRows = apcRows;
      this.aggregateHotLeafWidths = aggregateHotLeafWidths;
      this.hotLeafWorkingSetBytes = hotLeafWorkingSetBytes;
      this.reads = reads;
      this.mutations = mutations;
      this.exports = exports;
    }
  }
}

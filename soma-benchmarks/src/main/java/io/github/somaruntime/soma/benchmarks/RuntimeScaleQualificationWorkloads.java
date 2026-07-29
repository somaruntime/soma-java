package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.CategoryId;
import io.github.somaruntime.soma.benchmarks.schema.GroupId;
import io.github.somaruntime.soma.benchmarks.schema.ItemId;
import io.github.somaruntime.soma.benchmarks.schema.NamespaceId;
import io.github.somaruntime.soma.benchmarks.schema.ScaleNumericFact;
import io.github.somaruntime.soma.benchmarks.schema.WorkKey;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnedOptionBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnedOptionDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleNumericFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleNumericFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleNumericFactDelta;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleNumericFactScan;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleNumericFactTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringAccessFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringAccessFactTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringFactCursor;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringFactScan;
import io.github.somaruntime.soma.benchmarks.schema.generated.ScaleStringFactTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.SchemaMetadata;
import io.github.somaruntime.soma.dataflow.CallbackDeliveryDefinition;
import io.github.somaruntime.soma.dataflow.CallbackDeliveryInvocation;
import io.github.somaruntime.soma.dataflow.CancellationToken;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowDefinition;
import io.github.somaruntime.soma.dataflow.DataFlowInvocation;
import io.github.somaruntime.soma.dataflow.DataFlowStats;
import io.github.somaruntime.soma.dataflow.DeliveryResult;
import io.github.somaruntime.soma.dataflow.ExecutionBudget;
import io.github.somaruntime.soma.dataflow.ExecutionPolicy;
import io.github.somaruntime.soma.dataflow.ExpandedFlow;
import io.github.somaruntime.soma.dataflow.GroupCursor;
import io.github.somaruntime.soma.dataflow.GroupVisitor;
import io.github.somaruntime.soma.dataflow.GroupedLongResult;
import io.github.somaruntime.soma.dataflow.JoinedFlow;
import io.github.somaruntime.soma.dataflow.JoinedIndexVisitor;
import io.github.somaruntime.soma.dataflow.KeyExpression;
import io.github.somaruntime.soma.dataflow.LongColumnResult;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.dataflow.LongValueVisitor;
import io.github.somaruntime.soma.dataflow.PartialWindowPolicy;
import io.github.somaruntime.soma.dataflow.ResultDeliveryMode;
import io.github.somaruntime.soma.dataflow.SourceSlot;
import io.github.somaruntime.soma.dataflow.StatsMode;
import io.github.somaruntime.soma.dataflow.WindowCursor;
import io.github.somaruntime.soma.dataflow.WindowVisitor;
import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.dataflow.generated.OwnedChildAccess;
import io.github.somaruntime.soma.runtime.IndexSnapshot;
import io.github.somaruntime.soma.runtime.RuntimePlan;
import io.github.somaruntime.soma.runtime.SomaGroup;
import io.github.somaruntime.soma.runtime.SomaGroupPlan;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;
import io.github.somaruntime.soma.runtime.StringResourceProfile;
import io.github.somaruntime.soma.runtime.StringResourceRole;
import io.github.somaruntime.soma.runtime.TableStats;
import io.github.somaruntime.soma.runtime.generated.RuntimeCompatibility;
import io.github.somaruntime.soma.runtime.metadata.SomaGroupMetadata;
import io.github.somaruntime.soma.runtime.metadata.SomaTableRuntimeMetadata;
import io.github.somaruntime.soma.runtime.metadata.SomaWorkloadProfile;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Production-shape workloads behind the strict qualification runner. */
final class RuntimeScaleQualificationWorkloads {
    private static final int LOAD_CHUNK = 65_536;
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final String LOCAL_LIMITATION =
            "single-machine local qualification; claimAllowed=false; "
                    + "no public latency SLA or support-matrix claim";

    private RuntimeScaleQualificationWorkloads() {
    }

    static QualificationObservation run(
            String lane, QualificationConfig config) {
        if ("small-fast".equals(lane)) return smallFast(config);
        if ("medium".equals(lane)) return medium(config);
        if ("1m-single".equals(lane)) return millionSingle(config);
        if ("1m-double".equals(lane)) return millionDouble(config);
        if ("string".equals(lane)) return stringQualification(config);
        if ("10m-research".equals(lane)) return tenMillion(config);
        if ("100m-single-stress".equals(lane)) {
            return hundredMillionSingle(config);
        }
        if ("100m-double-stress".equals(lane)) {
            return hundredMillionDouble(config);
        }
        if ("100m-string-stress".equals(lane)) {
            return hundredMillionString(config);
        }
        if ("expansion".equals(lane)) return expansion(config);
        if ("delivery".equals(lane)) return delivery(config);
        if ("soak".equals(lane)) return soak(config);
        throw new IllegalArgumentException("unknown lane: " + lane);
    }

    private static QualificationObservation smallFast(
            QualificationConfig config) {
        final int[] points = {0, 1, 16, 256, 1_024, 4_096};
        QualificationObservation result =
                begin("small-fast", "production-exact-v1", 4_096, 4_096);
        result.structuralBytesPerRow = 73;
        result.stringProfile =
                "length=8..24;cardinality=max(rows,1);sharing=mixed;"
                        + "roles=payload,key,unique,index,group,join";
        result.timeoutSeconds = 300L;
        result.retainedReachableStringBytesModel = checkedAdd(
                payloadProfile(4_096, 1, false)
                        .estimatedReachableBytes(),
                accessProfile(4_096).estimatedReachableBytes());
        Probe probe = new Probe();
        long checksum = 0L;
        int callbackInvocations = 0;
        int exactProbes = 0;
        int groupChecks = 0;
        int joinChecks = 0;
        for (int rows : points) {
            RuntimePlan plan = plan(
                    Math.max(1, rows),
                    Math.max(1, rows),
                    Math.max(1, rows),
                    512L * MIB,
                    1L * GIB,
                    payloadProfile(Math.max(1, rows), 1, false),
                    accessProfile(Math.max(1, rows)));
            ScaleSet set = ScaleSet.create(
                    "small-" + rows, plan, true, true, true);
            try {
                set.numeric.reserve(rows);
                set.strings.reserve(rows);
                set.access.reserve(rows);
                loadNumeric(set.numeric, rows, 0L);
                String[] pool = stringPool(Math.max(1, Math.min(rows, 64)),
                        "small");
                loadSharedStrings(set.strings, rows, pool);
                loadStringAccess(set.access, rows, "small", 16);
                require(set.numeric.size() == rows
                                && set.strings.size() == rows
                                && set.access.size() == rows,
                        "small row count " + rows);
                if (rows != 0) {
                    int middle = rows / 2;
                    require(set.numeric.containsKey(middle),
                            "small numeric point");
                    require(set.access.containsKey("small-id-" + middle)
                                    && set.access.containsByUniqueAlias(
                                    "small-alias-" + middle)
                                    && set.access.scanByBucket(
                                    "small-bucket-" + (middle & 15))
                                    .count() > 0L,
                            "small String exact access");
                    exactProbes += 3;
                    long beforeEpoch = set.strings.structuralEpoch();
                    String equalCopy = new String(
                            set.strings.fetchAt(middle).label);
                    set.strings.mutateAt(middle)
                            .setLabel(equalCopy).commit();
                    require(set.strings.structuralEpoch() == beforeEpoch,
                            "equal-value String no-op");
                    set.numeric.mutate(0L)
                            .setMetric(7L).commit();
                    require(set.numeric.fetch(0L).metric == 7L,
                            "small mutation");
                }
                callbackInvocations += verifyCandidateDelivery(
                        set.numeric, Math.min(rows, 16));
                groupChecks += verifyNumericGroup(
                        set.numeric, Math.min(rows, 256));
                joinChecks += verifyNumericSelfJoin(
                        set.numeric, Math.min(rows, 64));
                checksum = mix(checksum, set.numeric.count());
                checksum = mix(checksum, set.strings.count());
                checksum = mix(checksum, set.access.count());
                observeGroup(result, set.group.metadata());
            } finally {
                releaseAndObserve(result, set.group);
            }
        }
        result.workload = BenchmarkModel.object(
                "identity", "small-fast-matrix-v1",
                "rowPoints", integers(points),
                "operations", Arrays.asList(
                        "create", "point", "exact", "scan", "column",
                        "batch", "mutate", "group", "join", "callback"),
                "primitiveAndString", Boolean.TRUE);
        result.observation = BenchmarkModel.object(
                "callbackInvocations", Integer.valueOf(callbackInvocations),
                "exactProbes", Integer.valueOf(exactProbes),
                "groupChecks", Integer.valueOf(groupChecks),
                "joinChecks", Integer.valueOf(joinChecks),
                "fixedTaxMatrixComplete", Boolean.TRUE);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "timing is diagnostic fixed-tax evidence, not a latency guarantee");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation medium(
            QualificationConfig config) {
        final int[] points = {32_768, 65_536, 262_144};
        QualificationObservation result =
                begin("medium", "production-exact-v1", 262_144, 262_144);
        result.structuralBytesPerRow = 41;
        result.stringProfile =
                "length=10..26;cardinality=1024;sharing=high;"
                        + "roles=payload,group,join";
        result.timeoutSeconds = 600L;
        result.retainedReachableStringBytesModel =
                payloadProfile(1_024, 1, false)
                        .estimatedReachableBytes();
        Probe probe = new Probe();
        long checksum = 0L;
        int sequentialDecisions = 0;
        int parallelDecisions = 0;
        ArrayList<Object> layouts = new ArrayList<Object>();
        for (int rows : points) {
            RuntimePlan plan = plan(
                    rows, rows, 1,
                    1L * GIB, 2L * GIB,
                    payloadProfile(1_024, 1, false),
                    accessProfile(1));
            ScaleSet set = ScaleSet.create(
                    "medium-" + rows, plan, true, true, false);
            DataFlowContext context = DataFlowContext.managedParallel(
                    4,
                    ExecutionPolicy.adaptiveParallel()
                            .withMinimumParallelCardinality(65_536)
                            .withStatsMode(StatsMode.DETAILED),
                    ExecutionBudget.defaults());
            try {
                set.numeric.reserve(rows);
                set.strings.reserve(rows);
                loadNumeric(set.numeric, rows, 0L);
                loadSharedStrings(
                        set.strings, rows, stringPool(1_024, "medium"));
                NumericRun sum = numericSum(set.numeric, context);
                require(sum.value == expectedMetricSum(rows),
                        "medium numeric sum " + rows);
                if (sum.stats.tasks() > 1) parallelDecisions++;
                else sequentialDecisions++;
                checksum = mix(checksum, sum.value);
                checksum = mix(
                        checksum,
                        stringCount(set.strings));
                checksum = mix(
                        checksum,
                        verifyNumericSelfJoin(set.numeric, 4_096));
                checksum = mix(
                        checksum,
                        verifyNumericGroup(set.numeric, 4_096));
                SomaTableRuntimeMetadata metadata =
                        set.numeric.runtimeMetadata();
                layouts.add(BenchmarkModel.object(
                        "rows", Integer.valueOf(rows),
                        "layout", metadata.storageLayout().name(),
                        "segments", Integer.valueOf(
                                metadata.segments().size()),
                        "tasks", Integer.valueOf(sum.stats.tasks()),
                        "workers", Integer.valueOf(sum.stats.workers())));
                observeGroup(result, set.group.metadata());
            } finally {
                context.close();
                releaseAndObserve(result, set.group);
            }
        }
        require(sequentialDecisions >= 1 && parallelDecisions >= 1,
                "medium parallel crossover");
        result.workload = BenchmarkModel.object(
                "identity", "medium-crossover-matrix-v1",
                "rowPoints", integers(points),
                "operations", Arrays.asList(
                        "layout", "candidate", "relation",
                        "sequential-parallel-crossover"),
                "primitiveAndString", Boolean.TRUE);
        result.observation = BenchmarkModel.object(
                "layouts", layouts,
                "sequentialDecisions",
                Integer.valueOf(sequentialDecisions),
                "parallelDecisions",
                Integer.valueOf(parallelDecisions),
                "candidateFormula",
                io.github.somaruntime.soma.dataflow.CandidatePhysicalFormula.IDENTITY,
                "relationFormula",
                io.github.somaruntime.soma.dataflow.RelationStrategyFormula.IDENTITY);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "crossover is formula evidence for this exact JVM/profile");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation millionSingle(
            QualificationConfig config) {
        final int rows = 1_000_000;
        QualificationObservation result =
                begin("1m-single", "production-exact-v1", rows, 0);
        result.structuralBytesPerRow = 20;
        result.stringProfile = "not-applicable";
        result.timeoutSeconds = 1_200L;
        Probe probe = new Probe();
        RuntimePlan plan = plan(
                rows, 1, 1,
                2L * GIB, 4L * GIB,
                payloadProfile(1, 1, false),
                accessProfile(1));
        ScaleSet set = ScaleSet.create(
                "million-single", plan, true, false, false);
        long checksum = 0L;
        int windowCount;
        try {
            set.numeric.reserve(rows);
            loadNumeric(set.numeric, rows, 0L);

            require(set.numeric.containsKey(999_999L),
                    "single 1m point");

            DataFlowContext context = DataFlowContext.managedParallel(4);
            try {
                NumericRun sum = numericSum(set.numeric, context);
                require(sum.value == expectedMetricSum(rows),
                        "single 1m closed numeric sum");
                require(sum.stats.resources().outputCurrentBytes() == 0L
                                && sum.stats.resources()
                                .sharedScratchCurrentBytes() == 0L
                                && sum.stats.resources()
                                .workerScratchCurrentBytes() == 0L,
                        "single 1m invocation ledger drained");
                checksum = mix(checksum, sum.value);
            } finally {
                context.close();
            }
            checksum = mix(
                    checksum,
                    verifyNumericSelfJoin(set.numeric, 65_536));
            checksum = mix(
                    checksum,
                    verifyNumericGroup(set.numeric, 65_536));
            windowCount = verifyNumericWindow(set.numeric, 65_536);
            checksum = mix(checksum, windowCount);

            ScaleNumericFact changed = new ScaleNumericFact();
            changed.id = 999_999L;
            changed.groupId = 17;
            changed.metric = 9_999L;
            ScaleNumericFactDelta delta =
                    new ScaleNumericFactDelta(1).update(changed);
            long epoch = set.numeric.structuralEpoch();
            set.numeric.applyDelta(delta);
            require(set.numeric.fetch(999_999L).metric == 9_999L
                            && set.numeric.structuralEpoch() == epoch + 1L,
                    "single 1m changed-row Delta");
            checksum = mix(checksum, set.numeric.fetch(999_999L).metric);
            observeGroup(result, set.group.metadata());
        } finally {
            releaseAndObserve(result, set.group);
        }
        result.workload = BenchmarkModel.object(
                "identity", "one-million-single-numeric-root-v1",
                "operations", Arrays.asList(
                        "point", "exact", "scan", "column", "batch",
                        "delta", "join", "group", "window",
                        "closed-numeric-kernel", "release"),
                "rows", Integer.valueOf(rows));
        result.observation = BenchmarkModel.object(
                "windowCount", Integer.valueOf(windowCount),
                "deltaFormula",
                io.github.somaruntime.soma.runtime.DeltaStagingFormula.IDENTITY,
                "singleActualResidentRoot", Boolean.TRUE);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "1M guarantee is bounded to this narrow numeric-Key "
                        + "production shape");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation millionDouble(
            QualificationConfig config) {
        final int rows = 1_000_000;
        QualificationObservation result =
                begin("1m-double", "production-exact-v1", rows, rows);
        result.structuralBytesPerRow = 20;
        result.stringProfile = "not-applicable";
        result.timeoutSeconds = 1_800L;
        result.resourceBudget =
                "Xmx>=4GiB;two simultaneous 1M numeric roots;"
                        + "bounded relation <=65536 candidates";
        Probe probe = new Probe();
        RuntimePlan plan = plan(
                rows, 1, 1,
                2L * GIB, 4L * GIB,
                payloadProfile(1, 1, false),
                accessProfile(1));
        NumericPair pair = NumericPair.create(
                "million-double", plan);
        long checksum = 0L;
        long leftSum;
        long rightSum;
        long minMaxJoin;
        long bloomJoin;
        long fallbackJoin;
        long crossGroupJoin;
        try {
            pair.left.reserve(rows);
            pair.right.reserve(rows);
            loadNumeric(pair.left, rows, 0L);
            loadNumeric(pair.right, rows, 0L);
            require(pair.left.size() == rows
                            && pair.right.size() == rows,
                    "two Tables are actually 1M");
            DataFlowContext context =
                    DataFlowContext.managedParallel(4);
            try {
                leftSum = numericSum(pair.left, context).value;
                rightSum = numericSum(pair.right, context).value;
            } finally {
                context.close();
            }
            require(leftSum == expectedMetricSum(rows)
                            && rightSum == expectedMetricSum(rows),
                    "double 1m aggregate oracle");
            minMaxJoin = numericMinMaxJoin(
                    pair.left, pair.right, 8_192L);
            require(minMaxJoin == 8_192L,
                    "double 1m min/max-filtered relation");
            bloomJoin = numericBloomJoin(
                    pair.left, pair.right, 127L);
            require(bloomJoin == (rows + 127L) / 128L,
                    "double 1m Bloom-filtered relation");
            fallbackJoin = numericJoin(
                    pair.left, pair.right, 65_536);
            require(fallbackJoin == 65_536L,
                    "double 1m dense fallback relation");
            RuntimePlan smallPlan = plan(
                    4_096, 1, 1,
                    512L * MIB, 1L * GIB,
                    payloadProfile(1, 1, false),
                    accessProfile(1));
            ScaleSet external = ScaleSet.create(
                    "million-double-cross-group",
                    smallPlan, true, false, false);
            try {
                loadNumeric(external.numeric, 4_096, 0L);
                crossGroupJoin = numericJoin(
                        pair.left, external.numeric, 4_096);
                require(crossGroupJoin == 4_096L,
                        "double 1m cross-Group bounded relation");
            } finally {
                external.group.release();
            }
            checksum = mix(checksum, leftSum);
            checksum = mix(checksum, rightSum);
            checksum = mix(checksum, minMaxJoin);
            checksum = mix(checksum, bloomJoin);
            checksum = mix(checksum, fallbackJoin);
            checksum = mix(checksum, crossGroupJoin);
            observeGroup(result, pair.group.metadata());
        } finally {
            releaseAndObserve(result, pair.group);
        }
        result.workload = BenchmarkModel.object(
                "identity", "one-million-double-numeric-root-v1",
                "leftRows", Integer.valueOf(rows),
                "rightRows", Integer.valueOf(rows),
                "operations", Arrays.asList(
                        "reserve-both", "load-both",
                        "closed-numeric-sum-both",
                        "minmax-filtered-join",
                        "bloom-filtered-join",
                        "dense-fallback-join",
                        "cross-group-bounded-join", "release"));
        result.observation = BenchmarkModel.object(
                "bothActualResident", Boolean.TRUE,
                "leftSum", Long.valueOf(leftSum),
                "rightSum", Long.valueOf(rightSum),
                "minMaxJoinRows", Long.valueOf(minMaxJoin),
                "bloomJoinRows", Long.valueOf(bloomJoin),
                "fallbackJoinRows", Long.valueOf(fallbackJoin),
                "crossGroupJoinRows", Long.valueOf(crossGroupJoin));
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "double-1M guarantee is bounded to two simultaneous "
                        + "narrow numeric roots");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation stringQualification(
            QualificationConfig config) {
        final int rows = 1_000_000;
        final int cardinality = 4_096;
        QualificationObservation result =
                begin("string", "production-exact-v1", rows, rows);
        result.structuralBytesPerRow = 73;
        result.stringProfile =
                "payload:length=12..48,cardinality=4096,sharing=high;"
                        + "access:length=10..32,cardinality=1000000,"
                        + "sharing=key/unique-low,index-high;"
                        + "roles=payload,key,unique,index,group,join;"
                        + "simultaneouslyLiveTables=2";
        result.timeoutSeconds = 1_800L;
        result.retainedReachableStringBytesModel = checkedAdd(
                payloadProfile(cardinality, 1, false)
                        .estimatedReachableBytes(),
                accessProfile(rows).estimatedReachableBytes());
        Probe probe = new Probe();
        RuntimePlan plan = plan(
                1, rows, rows,
                3L * GIB, 6L * GIB,
                payloadProfile(cardinality, 1, false),
                accessProfile(rows));
        ScaleSet set = ScaleSet.create(
                "string-qualification", plan, false, true, true);
        long checksum = 0L;
        boolean weakReferenceCleared;
        WeakReference<String> retainedString;
        long relationRows;
        int groups;
        try {
            set.strings.reserve(rows);
            set.access.reserve(rows);
            String[] pool = stringPool(
                    cardinality, "string-qualification");
            loadSharedStrings(set.strings, rows, pool);
            loadStringAccess(
                    set.access, rows, "string-qualification", cardinality);
            require(set.access.containsKey(
                                    "string-qualification-id-999999")
                            && set.access.containsByUniqueAlias(
                                    "string-qualification-alias-999999")
                            && set.access.scanByBucket(
                                    "string-qualification-bucket-4095")
                            .count() > 0L,
                    "String Key/Unique/Index");
            require(stringCount(set.strings) == rows
                            && stringEqualsCount(
                            set.strings, pool[0]) > 0L,
                    "String payload scan/equality");
            relationRows =
                    verifyStringRelation(set.strings, cardinality);
            groups = verifyStringGroup(
                    set.strings, cardinality);
            require(relationRows > 0L
                            && groups == cardinality,
                    "String Group/Join");

            retainedString = mutateTrackedString(
                    set.strings, rows / 2);
            require(set.strings.fetchAt(1).note != null
                            && set.strings.fetchAt(0).note == null,
                    "String presence");
            checksum = mix(checksum, relationRows);
            checksum = mix(checksum, groups);
            checksum = mix(checksum, set.access.size());
            set.strings.clear();
            set.access.clear();
            require(set.strings.size() == 0
                            && set.access.size() == 0,
                    "String clear");
            observeGroup(result, set.group.metadata());
        } finally {
            releaseAndObserve(result, set.group);
        }
        weakReferenceCleared = awaitCollected(retainedString, 16);
        require(weakReferenceCleared,
                "actual 1M String Table release JVM GC observation");
        result.workload = BenchmarkModel.object(
                "identity", "one-million-reference-string-v1",
                "payloadRows", Integer.valueOf(rows),
                "accessRows", Integer.valueOf(rows),
                "operations", Arrays.asList(
                        "payload", "key", "unique", "index",
                        "group", "join", "arbitrary-length-mutation",
                        "equal-value-noop", "presence",
                        "clear", "release", "gc"));
        result.observation = BenchmarkModel.object(
                "stringBackend", "reference-backed-v1",
                "relationRows", Long.valueOf(relationRows),
                "groups", Integer.valueOf(groups),
                "weakReferenceCleared",
                Boolean.valueOf(weakReferenceCleared),
                "profileEstimator",
                StringResourceProfile.ESTIMATOR_IDENTITY);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "String object bytes remain JVM-owned and are modelled "
                        + "separately from SOMA structural bytes");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation tenMillion(
            QualificationConfig config) {
        final int rows = 10_000_000;
        QualificationObservation result =
                begin("10m-research", "research-stress-v1", rows, 65_536);
        result.structuralBytesPerRow = 41;
        result.stringProfile =
                "low:length=12..28,cardinality=4096,sharing=high;"
                        + "high:length=16..32,cardinality=10000000,"
                        + "sharing=none;roles=payload,group,join";
        result.timeoutSeconds = 2_400L;
        Probe probe = new Probe();
        long checksum = 0L;
        long lowCardinalityHighWater;
        long highCardinalityHighWater;

        RuntimePlan lowPlan = plan(
                rows, rows, 1,
                6L * GIB, 10L * GIB,
                payloadProfile(4_096, 1, false),
                accessProfile(1));
        ScaleSet low = ScaleSet.create(
                "ten-million-low", lowPlan, true, true, false);
        try {
            // Deliberately do not reserve the numeric Table: this is the
            // segmented growth qualification path.
            loadNumeric(low.numeric, rows, 0L);
            low.strings.reserve(rows);
            loadSharedStrings(
                    low.strings, rows, stringPool(4_096, "ten-million"));
            DataFlowContext sumContext = DataFlowContext.sequential();
            NumericRun sum;
            try {
                sum = numericSum(low.numeric, sumContext);
            } finally {
                sumContext.close();
            }
            require(sum.value == expectedMetricSum(rows),
                    "10m full fused sum");
            checksum = mix(checksum, sum.value);
            checksum = mix(
                    checksum,
                    verifyNumericSelfJoin(low.numeric, 65_536));
            checksum = mix(
                    checksum,
                    verifyStringRelation(low.strings, 4_096));
            require(low.numeric.runtimeMetadata().segments().size() > 1,
                    "10m segmented growth topology");
            observeGroup(result, low.group.metadata());
            lowCardinalityHighWater =
                    low.group.metadata().structuralHighWaterBytes();
        } finally {
            releaseAndObserve(result, low.group);
        }

        RuntimePlan highPlan = plan(
                1, rows, 1,
                6L * GIB, 10L * GIB,
                highCardinalityProfile(rows),
                accessProfile(1));
        ScaleSet high = ScaleSet.create(
                "ten-million-high", highPlan, false, true, false);
        try {
            high.strings.reserve(rows);
            loadDistinctStrings(high.strings, rows, "ten-million-high");
            require(high.strings.count() == rows
                            && high.strings.fetchAt(rows - 1).label.equals(
                            "ten-million-high-label-" + (rows - 1)),
                    "10m high-cardinality String");
            checksum = mix(checksum, high.strings.count());
            checksum = mix(
                    checksum,
                    stringCount(high.strings));
            observeGroup(result, high.group.metadata());
            highCardinalityHighWater =
                    high.group.metadata().structuralHighWaterBytes();
        } finally {
            releaseAndObserve(result, high.group);
        }
        result.retainedReachableStringBytesModel =
                highCardinalityProfile(rows).estimatedReachableBytes();
        result.workload = BenchmarkModel.object(
                "identity", "ten-million-production-shape-v1",
                "operations", Arrays.asList(
                        "large-segmented-growth",
                        "full-fused-aggregate",
                        "bounded-relation",
                        "low-cardinality-string",
                        "high-cardinality-string"),
                "rows", Integer.valueOf(rows),
                "boundedRelationRows", Integer.valueOf(65_536));
        result.observation = BenchmarkModel.object(
                "lowCardinalityStructuralHighWaterBytes",
                Long.valueOf(lowCardinalityHighWater),
                "highCardinalityStructuralHighWaterBytes",
                Long.valueOf(highCardinalityHighWater),
                "highCardinalityDistinctObjects",
                Integer.valueOf(rows),
                "boundedOutput", Boolean.TRUE);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "10M String high-cardinality profile is one required "
                        + "payload field with optional note absent");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation hundredMillionSingle(
            QualificationConfig config) {
        final int rows = 100_000_000;
        QualificationObservation result =
                begin("100m-single-stress", "research-stress-v1", rows, 0);
        result.structuralBytesPerRow = 20;
        result.stringProfile = "not-applicable";
        result.timeoutSeconds = 5_400L;
        result.resourceBudget =
                "Xmx>=24GiB;tableStructural<=8GiB;"
                        + "aggregateStructural<=12GiB;"
                        + "bounded-scalar-output";
        Probe probe = new Probe();
        RuntimePlan plan = plan(
                rows, 1, 1,
                8L * GIB, 12L * GIB,
                payloadProfile(1, 1, false),
                accessProfile(1));
        ScaleSet set = ScaleSet.create(
                "hundred-million-single", plan, true, false, false);
        long checksum = 0L;
        int tasks;
        int workers;
        long reserveNanos;
        try {
            long reserveStart = System.nanoTime();
            set.numeric.reserve(rows);
            reserveNanos = System.nanoTime() - reserveStart;
            loadNumeric(set.numeric, rows, 0L);
            require(set.numeric.size() == rows
                            && set.numeric.containsKey(0L)
                            && set.numeric.containsKey(rows - 1L)
                            && !set.numeric.containsKey(rows),
                    "100M single point/size");
            DataFlowContext context = DataFlowContext.managedParallel(
                    Math.min(8, Runtime.getRuntime().availableProcessors()),
                    ExecutionPolicy.adaptiveParallel()
                            .withMinimumParallelCardinality(65_536)
                            .withStatsMode(StatsMode.DETAILED),
                    ExecutionBudget.defaults());
            NumericRun sum;
            try {
                sum = numericSum(set.numeric, context);
            } finally {
                context.close();
            }
            require(sum.value == expectedMetricSum(rows),
                    "100M single fused aggregate oracle");
            require(sum.stats.resources().outputCurrentBytes() == 0L
                            && sum.stats.resources()
                            .sharedScratchCurrentBytes() == 0L
                            && sum.stats.resources()
                            .workerScratchCurrentBytes() == 0L,
                    "100M single invocation ledger drained");
            tasks = sum.stats.tasks();
            workers = sum.stats.workers();
            checksum = mix(checksum, sum.value);
            checksum = mix(checksum, set.numeric.findIndex(rows - 1L));
            observeGroup(result, set.group.metadata());
        } finally {
            releaseAndObserve(result, set.group);
        }
        result.workload = BenchmarkModel.object(
                "identity", "hundred-million-single-numeric-key-v1",
                "schema", "long Key + int groupId + long metric",
                "rows", Integer.valueOf(rows),
                "operations", Arrays.asList(
                        "exact-reserve", "chunked-load", "point-first-last",
                        "full-fused-bounded-sum", "release"));
        result.observation = BenchmarkModel.object(
                "actualResidentRows", Integer.valueOf(rows),
                "reserveNanos", Long.valueOf(reserveNanos),
                "aggregateTasks", Integer.valueOf(tasks),
                "aggregateWorkers", Integer.valueOf(workers),
                "expectedMetricSum",
                Long.valueOf(expectedMetricSum(rows)),
                "boundedOutputElements", Integer.valueOf(1));
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "100M applies only to the declared narrow numeric-Key "
                        + "schema; it is not a generic wide-schema claim");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation hundredMillionDouble(
            QualificationConfig config) {
        final int rows = 100_000_000;
        QualificationObservation result =
                begin("100m-double-stress", "research-stress-v1", rows, rows);
        result.structuralBytesPerRow = 20;
        result.stringProfile = "not-applicable";
        result.timeoutSeconds = 7_200L;
        result.resourceBudget =
                "Xmx>=28GiB;two simultaneous 100M roots;"
                        + "per-root aggregateStructural<=12GiB;"
                        + "bounded relation <=65536 candidates";
        Probe probe = new Probe();
        RuntimePlan plan = plan(
                rows, 1, 1,
                8L * GIB, 12L * GIB,
                payloadProfile(1, 1, false),
                accessProfile(1));
        NumericPair pair = NumericPair.create(
                "hundred-million-double", plan);
        long checksum = 0L;
        long sameGroupJoin;
        long crossGroupJoin;
        long leftSum;
        long rightSum;
        try {
            pair.left.reserve(rows);
            pair.right.reserve(rows);
            loadNumeric(pair.left, rows, 0L);
            loadNumeric(pair.right, rows, 0L);
            require(pair.left.size() == rows
                            && pair.right.size() == rows,
                    "both Tables are actually 100M");
            DataFlowContext context = DataFlowContext.managedParallel(
                    Math.min(8, Runtime.getRuntime().availableProcessors()));
            try {
                leftSum = numericSum(pair.left, context).value;
                rightSum = numericSum(pair.right, context).value;
            } finally {
                context.close();
            }
            require(leftSum == expectedMetricSum(rows)
                            && rightSum == expectedMetricSum(rows),
                    "100M double aggregate oracle");
            sameGroupJoin = numericJoin(
                    pair.left, pair.right, 65_536);
            require(sameGroupJoin == 65_536L,
                    "100M same-Group bounded relation");

            RuntimePlan smallPlan = plan(
                    4_096, 1, 1,
                    512L * MIB, 1L * GIB,
                    payloadProfile(1, 1, false),
                    accessProfile(1));
            ScaleSet external = ScaleSet.create(
                    "hundred-million-cross-group",
                    smallPlan, true, false, false);
            try {
                loadNumeric(external.numeric, 4_096, 0L);
                crossGroupJoin = numericJoin(
                        pair.left, external.numeric, 4_096);
                require(crossGroupJoin == 4_096L,
                        "100M cross-Group bounded relation");
            } finally {
                external.group.release();
                require(external.group.metadata()
                                .currentStructuralBytes() == 0L,
                        "cross-Group auxiliary ledger drained");
            }
            checksum = mix(checksum, leftSum);
            checksum = mix(checksum, rightSum);
            checksum = mix(checksum, sameGroupJoin);
            checksum = mix(checksum, crossGroupJoin);
            observeGroup(result, pair.group.metadata());
        } finally {
            releaseAndObserve(result, pair.group);
        }
        result.workload = BenchmarkModel.object(
                "identity", "hundred-million-double-numeric-key-v1",
                "schema", "two simultaneous long-Key narrow roots",
                "leftRows", Integer.valueOf(rows),
                "rightRows", Integer.valueOf(rows),
                "operations", Arrays.asList(
                        "exact-reserve-both", "load-both",
                        "full-fused-sum-both",
                        "same-group-bounded-join",
                        "cross-group-bounded-join", "group-release"));
        result.observation = BenchmarkModel.object(
                "bothActualResident", Boolean.TRUE,
                "leftSum", Long.valueOf(leftSum),
                "rightSum", Long.valueOf(rightSum),
                "sameGroupJoinRows", Long.valueOf(sameGroupJoin),
                "crossGroupJoinRows", Long.valueOf(crossGroupJoin),
                "relationBound", Integer.valueOf(65_536));
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "double-100M evidence is two simultaneous narrow numeric "
                        + "roots; relation enumeration remains explicitly bounded");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation hundredMillionString(
            QualificationConfig config) {
        final int rows = 100_000_000;
        final int cardinality = 1_024;
        QualificationObservation result =
                begin("100m-string-stress", "research-stress-v1", rows, rows);
        result.structuralBytesPerRow = 21;
        result.stringProfile =
                "length=18..34;cardinality=1024;"
                        + "intraTableSharing=high;"
                        + "interTableObjectSharing=100%;"
                        + "optionalPresence=50%;"
                        + "roles=payload,group,join";
        result.timeoutSeconds = 7_200L;
        result.resourceBudget =
                "Xmx>=12GiB;two simultaneous 100M String payload roots;"
                        + "reachable String estimate declared separately;"
                        + "relation <=4096 candidates";
        Probe probe = new Probe();
        StringResourceProfile profile =
                payloadProfile(cardinality, 2, true);
        RuntimePlan plan = plan(
                1, rows, 1,
                5L * GIB, 7L * GIB,
                profile,
                accessProfile(1));
        StringPair pair = StringPair.create(
                "hundred-million-string", plan);
        String[] sharedPool =
                stringPool(cardinality, "hundred-million-string");
        long checksum = 0L;
        long leftMatches;
        long rightMatches;
        long boundedRelation;
        boolean impossibleRejected;
        try {
            pair.left.reserve(rows);
            pair.right.reserve(rows);
            loadSharedStrings(pair.left, rows, sharedPool);
            loadSharedStrings(pair.right, rows, sharedPool);
            require(pair.left.size() == rows
                            && pair.right.size() == rows
                            && pair.left.fetchAt(rows - 1).label
                            == pair.right.fetchAt(rows - 1).label,
                    "100M String simultaneous roots and inter-table sharing");
            leftMatches = stringEqualsCount(
                    pair.left, sharedPool[0]);
            rightMatches = stringEqualsCount(
                    pair.right, sharedPool[0]);
            long expectedMatches =
                    rows / cardinality
                            + (rows % cardinality == 0 ? 0L : 1L);
            require(leftMatches == expectedMatches
                            && rightMatches == expectedMatches,
                    "100M String full payload equality scan");
            boundedRelation = stringJoin(
                    pair.left, pair.right, 4_096);
            require(boundedRelation > 0L
                            && verifyStringGroup(pair.left, 4_096)
                            == cardinality,
                    "100M String bounded Group/Join");
            checksum = mix(checksum, leftMatches);
            checksum = mix(checksum, rightMatches);
            checksum = mix(checksum, boundedRelation);
            observeGroup(result, pair.group.metadata());
        } finally {
            releaseAndObserve(result, pair.group);
            Arrays.fill(sharedPool, null);
            sharedPool = null;
        }

        StringResourceProfile impossible = StringResourceProfile.builder()
                .averageUtf16CodeUnits(4_096)
                .maximumUtf16CodeUnits(8_192)
                .valueCardinality(200_000_000L)
                .distinctObjectIdentityEstimate(200_000_000L)
                .intraTableSharingBasisPoints(0)
                .interTableSharingBasisPoints(0)
                .presenceBasisPoints(10_000)
                .simultaneouslyLiveTableCount(2)
                .role(StringResourceRole.PAYLOAD)
                .build();
        long heap = Runtime.getRuntime().maxMemory();
        impossibleRejected =
                impossible.estimatedReachableBytes() > heap
                        || impossible.estimatedReachableBytes()
                        > Long.MAX_VALUE - 2L * rows * 21L
                        || impossible.estimatedReachableBytes()
                        + 2L * rows * 21L > heap;
        require(impossibleRejected,
                "impossible String profile qualification preflight");
        result.retainedReachableStringBytesModel =
                profile.estimatedReachableBytes();
        result.workload = BenchmarkModel.object(
                "identity", "hundred-million-string-shared-reference-v1",
                "leftRows", Integer.valueOf(rows),
                "rightRows", Integer.valueOf(rows),
                "lengthRange", "18..34 UTF-16 code units",
                "cardinality", Integer.valueOf(cardinality),
                "interTableSharedObjects", Boolean.TRUE,
                "operations", Arrays.asList(
                        "single-then-double-residency",
                        "full-payload-equality-scan",
                        "bounded-string-group",
                        "bounded-string-join",
                        "clear-release-gc-boundary",
                        "impossible-profile-preflight"));
        result.observation = BenchmarkModel.object(
                "leftMatches", Long.valueOf(leftMatches),
                "rightMatches", Long.valueOf(rightMatches),
                "boundedRelationRows", Long.valueOf(boundedRelation),
                "impossibleProfileEstimatedReachableBytes",
                Long.valueOf(impossible.estimatedReachableBytes()),
                "impossibleProfileRejectedBeforeAllocation",
                Boolean.valueOf(impossibleRejected),
                "profileEstimator",
                StringResourceProfile.ESTIMATOR_IDENTITY);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "String evidence is only this declared shared-reference "
                        + "payload profile; SOMA does not own String object bytes",
                "impossible-profile rejection is qualification-owned because "
                        + "the V1 profile is explicitly declared-unverified");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation expansion(
            QualificationConfig config) {
        QualificationObservation result =
                begin("expansion", "production-exact-v1", 8, 8);
        result.structuralBytesPerRow = 16;
        result.stringProfile = "not-applicable";
        result.timeoutSeconds = 300L;
        Probe probe = new Probe();
        ExpansionProbe overBudget = runSyntheticExpansion(
                8, 8, 2L, false);
        ExpansionProbe overflow = runSyntheticExpansion(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Long.MAX_VALUE, false);
        ExpansionProbe unknown = runSyntheticExpansion(
                8, -1, Long.MAX_VALUE, false);
        require("dataflow_output_budget_exceeded".equals(
                                overBudget.code)
                        && "dataflow_cardinality_overflow".equals(
                                overflow.code)
                        && "dataflow_unprovable_cardinality".equals(
                                unknown.code),
                "expansion typed rejection codes");
        require(overBudget.childBindingCalls == 0
                        && overflow.childBindingCalls == 0
                        && unknown.childBindingCalls == 0,
                "expansion rejection before enumeration");
        require(overBudget.releaseCalls == 1
                        && overflow.releaseCalls == 1
                        && unknown.releaseCalls == 1,
                "expansion preflight cleanup");

        OwnerFactBatch batch = new OwnerFactBatch(1);
        batch.addValues(
                new WorkKey(new NamespaceId(1L), new ItemId(1L)),
                1,
                1L,
                new CategoryId(1L),
                new OwnedOptionBatch(1)
                        .addValues(new GroupId(1L), 7L));
        RuntimePlan.Builder builder = SchemaMetadata.newPlan();
        builder.table("owner_facts")
                .initialCapacity(8)
                .planningRows(8)
                .maximumRows(8)
                .maximumTableStorageBytes(64L * MIB);
        builder.table("owned_options")
                .initialCapacity(8)
                .planningRows(8)
                .maximumRows(8)
                .maximumTableStorageBytes(64L * MIB);
        RuntimePlan boundedPlan = builder
                .maximumAggregateStorageBytes(256L * MIB)
                .build();
        OwnerFactTable table = OwnerFactTable.create(boundedPlan);
        long boundedCount;
        try {
            table.addBatch(batch);
            OwnerFactDataFlow.Source owners =
                    OwnerFactDataFlow.source("owners");
            OwnedOptionDataFlow.Source options =
                    OwnedOptionDataFlow.source("options");
            boundedCount = execute(
                    owners.expandOptions(options).count(),
                    owners,
                    OwnerFactDataFlow.bind(table)).value();
            require(boundedCount == 1L,
                    "bounded scalar expansion remains legal");
        } finally {
            table.release();
        }
        result.workload = BenchmarkModel.object(
                "identity", "expansion-admission-negative-matrix-v1",
                "cases", Arrays.asList(
                        "known-over-budget",
                        "checked-byte-overflow",
                        "unknown-unprovable-bound",
                        "bounded-scalar-exception"),
                "enumerationSentinel", "childBindingCalls");
        result.observation = BenchmarkModel.object(
                "overBudgetCode", overBudget.code,
                "overflowCode", overflow.code,
                "unknownCode", unknown.code,
                "childBindingCallsBeforeReject", Integer.valueOf(
                        overBudget.childBindingCalls
                                + overflow.childBindingCalls
                                + unknown.childBindingCalls),
                "boundedScalarCount", Long.valueOf(boundedCount),
                "allLedgersDrained", Boolean.TRUE);
        result.checksum = hex(mix(
                mix(overBudget.code.hashCode(), overflow.code.hashCode()),
                unknown.code.hashCode()));
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "synthetic binding exists only in the benchmark artifact "
                        + "to exercise the generated-protocol fail-closed branch");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation delivery(
            QualificationConfig config) {
        final int rows = 4_096;
        QualificationObservation result =
                begin("delivery", "production-exact-v1", rows, rows);
        result.structuralBytesPerRow = 41;
        result.stringProfile =
                "length=12..28;cardinality=256;sharing=high;"
                        + "roles=payload,group,join";
        result.timeoutSeconds = 600L;
        result.retainedReachableStringBytesModel =
                payloadProfile(256, 1, false)
                        .estimatedReachableBytes();
        Probe probe = new Probe();
        RuntimePlan plan = plan(
                rows, rows, 1,
                512L * MIB, 1L * GIB,
                payloadProfile(256, 1, false),
                accessProfile(1));
        ScaleSet set = ScaleSet.create(
                "delivery", plan, true, true, false);
        long checksum = 0L;
        int callbackModes = 0;
        int earlyStops = 0;
        int typedFailures = 0;
        boolean escapedRejected;
        WeakReference<String> deliveryString;
        try {
            loadNumeric(set.numeric, rows, 0L);
            loadSharedStrings(
                    set.strings, rows, stringPool(256, "delivery"));
            ScaleNumericFactDataFlow.Source source =
                    ScaleNumericFactDataFlow.source("facts");

            DataFlowContext eagerContext = DataFlowContext.sequential();
            LongColumnResult eager;
            try {
                eager = execute(
                        source.candidates()
                                .limit(128L)
                                .project(source.columns().metric())
                                .toColumn(),
                        source,
                        ScaleNumericFactDataFlow.bind(set.numeric),
                        eagerContext);
            } finally {
                eagerContext.close();
            }
            require(eager.size() == 128
                            && eager.valueAt(127) == metric(127),
                    "Eager Detached default");
            callbackModes++;

            final long[] candidateChecksum = new long[1];
            CallbackDeliveryDefinition<ScaleNumericFactScan.Visitor>
                    candidateDelivery =
                    source.deliver(source.candidates().limit(128L));
            CallbackDeliveryInvocation<ScaleNumericFactScan.Visitor>
                    fullInvocation;
            DataFlowContext candidateContext =
                    DataFlowContext.sequential();
            try {
                fullInvocation = candidateDelivery.compile()
                        .newInvocation(candidateContext)
                        .bind(
                                source,
                                ScaleNumericFactDataFlow.bind(set.numeric))
                        .visitor(new ScaleNumericFactScan.Visitor() {
                            @Override
                            public boolean visit(
                                    io.github.somaruntime.soma.benchmarks.schema.generated
                                            .ScaleNumericFactCursor row) {
                                candidateChecksum[0] += row.metric();
                                return true;
                            }
                        });
                DeliveryResult delivered = fullInvocation.execute();
                require(delivered.completed()
                                && delivered.deliveredElements() == 128L
                                && ledgerDrained(fullInvocation.stats())
                                && fullInvocation.stats().delivery().mode()
                                == ResultDeliveryMode.CALLBACK_SCOPED,
                        "Candidate callback full delivery and ledger");
            } finally {
                candidateContext.close();
            }
            callbackModes++;

            DeliveryResult early = numericCandidateDelivery(
                    set.numeric, 128, 7);
            require(!early.completed()
                            && early.deliveredElements() == 7L,
                    "Candidate callback early stop");
            earlyStops++;

            final long[] valueChecksum = new long[1];
            DataFlowContext valueContext = DataFlowContext.sequential();
            try {
                CallbackDeliveryInvocation<LongValueVisitor> invocation =
                        source.candidates()
                                .limit(64L)
                                .project(source.columns().metric())
                                .deliver()
                                .compile()
                                .newInvocation(valueContext)
                                .bind(
                                        source,
                                        ScaleNumericFactDataFlow.bind(
                                                set.numeric))
                                .visitor(new LongValueVisitor() {
                                    @Override
                                    public boolean visit(long value) {
                                        valueChecksum[0] += value;
                                        return true;
                                    }
                                });
                require(invocation.execute().deliveredElements() == 64L
                                && ledgerDrained(invocation.stats()),
                        "Value callback full delivery");
            } finally {
                valueContext.close();
            }
            callbackModes++;

            callbackModes += verifyGroupDelivery(set.numeric);
            callbackModes += verifyJoinDelivery(set.numeric);
            callbackModes += verifyWindowDelivery(set.numeric);
            callbackModes += verifyStringDelivery(set.strings);

            final int[] failedCallbacks = new int[1];
            DataFlowContext failureContext = DataFlowContext.sequential();
            try {
                CallbackDeliveryInvocation<ScaleNumericFactScan.Visitor>
                        failure = candidateDelivery.compile()
                        .newInvocation(failureContext)
                        .bind(
                                source,
                                ScaleNumericFactDataFlow.bind(set.numeric))
                        .visitor(new ScaleNumericFactScan.Visitor() {
                            @Override
                            public boolean visit(
                                    io.github.somaruntime.soma.benchmarks.schema.generated
                                            .ScaleNumericFactCursor row) {
                                failedCallbacks[0]++;
                                throw new IllegalStateException(
                                        "qualification-callback-failure");
                            }
                        });
                try {
                    failure.execute();
                    throw new AssertionError(
                            "callback failure must propagate");
                } catch (SomaRuntimeException expected) {
                    require("callback_failed".equals(expected.code())
                                    && ledgerDrained(failure.stats()),
                            "callback failure cleanup");
                    typedFailures++;
                }
            } finally {
                failureContext.close();
            }
            require(set.numeric.size() == rows,
                    "callback failure keeps Table usable");

            final int[] cancelledCallbacks = new int[1];
            DataFlowContext cancelledContext =
                    DataFlowContext.sequential();
            try {
                CallbackDeliveryInvocation<ScaleNumericFactScan.Visitor>
                        cancelled = candidateDelivery.compile()
                        .newInvocation(cancelledContext)
                        .bind(
                                source,
                                ScaleNumericFactDataFlow.bind(set.numeric))
                        .visitor(new ScaleNumericFactScan.Visitor() {
                            @Override
                            public boolean visit(
                                    io.github.somaruntime.soma.benchmarks.schema.generated
                                            .ScaleNumericFactCursor row) {
                                cancelledCallbacks[0]++;
                                return true;
                            }
                        })
                        .cancellationToken(new CancellationToken() {
                            @Override
                            public boolean isCancellationRequested() {
                                return true;
                            }
                        });
                expectCode(cancelled, "dataflow_cancelled");
                require(cancelledCallbacks[0] == 0
                                && ledgerDrained(cancelled.stats()),
                        "cancel before callback");
                typedFailures++;
            } finally {
                cancelledContext.close();
            }

            final int[] deadlineCallbacks = new int[1];
            DataFlowContext deadlineContext =
                    DataFlowContext.sequential();
            try {
                CallbackDeliveryInvocation<ScaleNumericFactScan.Visitor>
                        deadline = candidateDelivery.compile()
                        .newInvocation(deadlineContext)
                        .bind(
                                source,
                                ScaleNumericFactDataFlow.bind(set.numeric))
                        .visitor(new ScaleNumericFactScan.Visitor() {
                            @Override
                            public boolean visit(
                                    io.github.somaruntime.soma.benchmarks.schema.generated
                                            .ScaleNumericFactCursor row) {
                                deadlineCallbacks[0]++;
                                return true;
                            }
                        })
                        .budget(ExecutionBudget.defaults().toBuilder()
                                .deadlineNanos(
                                        Math.max(1L,
                                                System.nanoTime() - 1L))
                                .build());
                expectCode(deadline, "dataflow_deadline_exceeded");
                require(deadlineCallbacks[0] == 0
                                && ledgerDrained(deadline.stats()),
                        "deadline before callback");
                typedFailures++;
            } finally {
                deadlineContext.close();
            }

            escapedRejected = verifyCursorNonEscape(plan);
            require(escapedRejected, "callback cursor non-escape fence");

            checksum = mix(checksum, eager.valueAt(127));
            checksum = mix(checksum, candidateChecksum[0]);
            checksum = mix(checksum, valueChecksum[0]);
            checksum = mix(checksum, callbackModes);
            checksum = mix(checksum, typedFailures);
            deliveryString = mutateTrackedString(
                    set.strings, rows / 2);
            observeGroup(result, set.group.metadata());
        } finally {
            releaseAndObserve(result, set.group);
        }
        boolean stringGc = awaitCollected(deliveryString, 16);
        require(stringGc, "delivery String release/GC");
        result.workload = BenchmarkModel.object(
                "identity", "result-delivery-capability-matrix-v1",
                "modes", Arrays.asList(
                        "eager-detached",
                        "candidate-callback",
                        "value-callback",
                        "group-callback",
                        "join-callback",
                        "window-callback",
                        "string-callback"),
                "failurePaths", Arrays.asList(
                        "early-stop", "visitor-failure",
                        "cancel", "deadline", "cursor-non-escape",
                        "string-release-gc"));
        result.observation = BenchmarkModel.object(
                "deliveryModesVerified", Integer.valueOf(callbackModes),
                "earlyStops", Integer.valueOf(earlyStops),
                "typedFailures", Integer.valueOf(typedFailures),
                "cursorEscapeRejected",
                Boolean.valueOf(escapedRejected),
                "stringWeakReferenceCleared",
                Boolean.valueOf(stringGc),
                "allInvocationLedgersDrained", Boolean.TRUE);
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "callback-scoped streaming is synchronous/read-only; "
                        + "Iterator, cursor and Publisher are out of scope");
        probe.finish(result);
        return result;
    }

    private static QualificationObservation soak(
            QualificationConfig config) {
        final int iterations = 100;
        final int rows = 4_096;
        QualificationObservation result =
                begin("soak", "production-exact-v1", rows, rows);
        result.structuralBytesPerRow = 41;
        result.stringProfile =
                "length=10..26;cardinality=128;sharing=high;"
                        + "roles=payload;repeated-lifecycle";
        result.timeoutSeconds = 1_800L;
        result.retainedReachableStringBytesModel =
                payloadProfile(128, 1, false)
                        .estimatedReachableBytes();
        result.measurementIterations = iterations;
        Probe probe = new Probe();
        long checksum = 0L;
        long maximumStructuralHighWater = 0L;
        int expectedFailures = 0;
        int clearedWeakReferences = 0;
        ArrayList<WeakReference<String>> weakReferences =
                new ArrayList<WeakReference<String>>();
        DataFlowContext parallel = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1_024)
                        .withStatsMode(StatsMode.DETAILED),
                ExecutionBudget.defaults());
        try {
            for (int iteration = 0;
                 iteration < iterations;
                 iteration++) {
                RuntimePlan plan = plan(
                        rows, rows, 1,
                        512L * MIB, 1L * GIB,
                        payloadProfile(128, 1, false),
                        accessProfile(1));
                ScaleSet set = ScaleSet.create(
                        "soak-" + iteration,
                        plan, true, true, false);
                try {
                    loadNumeric(set.numeric, rows, iteration * 10_000L);
                    String[] pool = stringPool(128, "soak-" + iteration);
                    loadSharedStrings(set.strings, rows, pool);
                    ScaleNumericFact changed = new ScaleNumericFact();
                    changed.id = iteration * 10_000L + rows - 1L;
                    changed.groupId = iteration;
                    changed.metric = 77L;
                    set.numeric.applyDelta(
                            new ScaleNumericFactDelta(1)
                                    .update(changed));
                    NumericRun sum = numericSum(set.numeric, parallel);
                    require(sum.value
                                    == expectedMetricSum(rows)
                                    - metric(rows - 1)
                                    + 77L,
                            "soak Delta/sum " + iteration);
                    require(ledgerDrained(sum.stats),
                            "soak parallel ledger " + iteration);
                    checksum = mix(checksum, sum.value);

                    if ((iteration % 10) == 0) {
                        ScaleNumericFactDataFlow.Source source =
                                ScaleNumericFactDataFlow.source("fault");
                        DataFlowContext failureContext =
                                DataFlowContext.sequential();
                        try {
                            CallbackDeliveryInvocation<
                                    ScaleNumericFactScan.Visitor> failed =
                                    source.deliver(
                                            source.candidates().limit(1L))
                                            .compile()
                                            .newInvocation(failureContext)
                                            .bind(
                                                    source,
                                                    ScaleNumericFactDataFlow
                                                            .bind(set.numeric))
                                            .visitor(
                                                    new ScaleNumericFactScan
                                                            .Visitor() {
                                                        @Override
                                                        public boolean visit(
                                                                io.github.somaruntime.soma
                                                                        .benchmarks
                                                                        .schema
                                                                        .generated
                                                                        .ScaleNumericFactCursor
                                                                        row) {
                                                            throw new
                                                                    IllegalStateException(
                                                                    "soak");
                                                        }
                                                    });
                            expectCode(failed, "callback_failed");
                            require(ledgerDrained(failed.stats()),
                                    "soak callback fault cleanup");
                            expectedFailures++;
                        } finally {
                            failureContext.close();
                        }
                    }

                    String dead = new String(
                            "soak-dead-" + iteration);
                    ScaleStringFactBatch one =
                            new ScaleStringFactBatch(1)
                                    .addValues(7, dead, false, null);
                    set.strings.replaceAll(one);
                    WeakReference<String> reference =
                            new WeakReference<String>(dead);
                    dead = null;
                    set.strings.clear();
                    weakReferences.add(reference);
                    observeGroup(result, set.group.metadata());
                    maximumStructuralHighWater = Math.max(
                            maximumStructuralHighWater,
                            set.group.metadata()
                                    .structuralHighWaterBytes());
                } finally {
                    releaseAndObserve(result, set.group);
                    require(set.group.metadata()
                                    .currentStructuralBytes() == 0L
                                    && set.group.metadata()
                                    .currentTableInstances() == 0L,
                            "soak Group ledger zero " + iteration);
                }
            }
        } finally {
            parallel.close();
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            forceGc();
            clearedWeakReferences = 0;
            for (WeakReference<String> reference : weakReferences) {
                if (reference.get() == null) clearedWeakReferences++;
            }
            if (clearedWeakReferences == weakReferences.size()) break;
        }
        require(clearedWeakReferences == weakReferences.size(),
                "soak String references collect after release");
        result.workload = BenchmarkModel.object(
                "identity", "runtime-lifecycle-soak-v1",
                "iterations", Integer.valueOf(iterations),
                "rowsPerIteration", Integer.valueOf(rows),
                "operations", Arrays.asList(
                        "create", "load", "mutate-delta",
                        "parallel-dataflow", "callback-fault",
                        "clear", "release", "executor-close",
                        "string-gc"));
        result.observation = BenchmarkModel.object(
                "completedIterations", Integer.valueOf(iterations),
                "expectedCallbackFailures",
                Integer.valueOf(expectedFailures),
                "clearedWeakReferences",
                Integer.valueOf(clearedWeakReferences),
                "maximumStructuralHighWaterBytes",
                Long.valueOf(maximumStructuralHighWater),
                "allGroupLedgersZero", Boolean.TRUE,
                "allInvocationLedgersZero", Boolean.TRUE,
                "managedExecutorClosed", Boolean.valueOf(parallel.isClosed()));
        result.checksum = hex(checksum);
        result.limitations = BenchmarkModel.limitations(
                LOCAL_LIMITATION,
                "soak is bounded to 100 deterministic iterations; "
                        + "it is not an indefinite endurance claim");
        probe.finish(result);
        return result;
    }

    private static RuntimePlan plan(
            int numericRows,
            int stringRows,
            int accessRows,
            long maximumTableBytes,
            long maximumAggregateBytes,
            StringResourceProfile payloadProfile,
            StringResourceProfile accessProfile) {
        RuntimePlan.Builder builder = SchemaMetadata.newPlan()
                .maximumAggregateStorageBytes(maximumAggregateBytes);
        editTable(
                builder,
                ScaleNumericFactTable.metadata().logicalName(),
                numericRows,
                maximumTableBytes,
                null);
        editTable(
                builder,
                ScaleStringFactTable.metadata().logicalName(),
                stringRows,
                maximumTableBytes,
                payloadProfile);
        editTable(
                builder,
                ScaleStringAccessFactTable.metadata().logicalName(),
                accessRows,
                maximumTableBytes,
                accessProfile);
        return builder.build();
    }

    private static void editTable(
            RuntimePlan.Builder builder,
            String table,
            int rows,
            long maximumTableBytes,
            StringResourceProfile profile) {
        int maximumRows = Math.max(1, rows);
        RuntimePlan.TableEditor editor = builder.table(table)
                .initialCapacity(Math.min(4_096, maximumRows))
                .planningRows(maximumRows)
                .maximumRows(maximumRows)
                .maximumTableStorageBytes(maximumTableBytes)
                .maximumUpdateScratchBytes(maximumTableBytes)
                .maximumOperationScratchBytes(maximumTableBytes)
                .maximumBulkScratchBytes(maximumTableBytes)
                .workloadProfile(SomaWorkloadProfile.SCAN_GROWTH);
        if (profile != null) {
            editor.stringResourceProfile(profile);
        }
    }

    private static StringResourceProfile payloadProfile(
            long cardinality,
            int simultaneousTables,
            boolean interTableShared) {
        long values = Math.max(1L, cardinality);
        long identities = interTableShared
                ? values : checkedMultiply(values, simultaneousTables);
        return StringResourceProfile.builder()
                .averageUtf16CodeUnits(24)
                .maximumUtf16CodeUnits(64)
                .valueCardinality(values)
                .distinctObjectIdentityEstimate(identities)
                .intraTableSharingBasisPoints(9_999)
                .interTableSharingBasisPoints(
                        interTableShared ? 10_000 : 0)
                .presenceBasisPoints(7_500)
                .simultaneouslyLiveTableCount(simultaneousTables)
                .role(StringResourceRole.PAYLOAD)
                .role(StringResourceRole.GROUP)
                .role(StringResourceRole.JOIN)
                .build();
    }

    private static StringResourceProfile highCardinalityProfile(
            long rows) {
        return StringResourceProfile.builder()
                .averageUtf16CodeUnits(28)
                .maximumUtf16CodeUnits(64)
                .valueCardinality(rows)
                .distinctObjectIdentityEstimate(rows)
                .intraTableSharingBasisPoints(0)
                .interTableSharingBasisPoints(0)
                .presenceBasisPoints(0)
                .simultaneouslyLiveTableCount(1)
                .role(StringResourceRole.PAYLOAD)
                .build();
    }

    private static StringResourceProfile accessProfile(long rows) {
        long bucketCardinality = Math.min(Math.max(1L, rows), 4_096L);
        long values = checkedAdd(
                checkedMultiply(Math.max(1L, rows), 2L),
                bucketCardinality);
        return StringResourceProfile.builder()
                .averageUtf16CodeUnits(28)
                .maximumUtf16CodeUnits(64)
                .valueCardinality(values)
                .distinctObjectIdentityEstimate(values)
                .intraTableSharingBasisPoints(3_333)
                .interTableSharingBasisPoints(0)
                .presenceBasisPoints(10_000)
                .simultaneouslyLiveTableCount(1)
                .role(StringResourceRole.KEY)
                .role(StringResourceRole.UNIQUE)
                .role(StringResourceRole.INDEX)
                .build();
    }

    private static void loadNumeric(
            ScaleNumericFactTable table,
            int rows,
            long keyOffset) {
        ScaleNumericFactBatch batch =
                new ScaleNumericFactBatch(
                        Math.min(LOAD_CHUNK, Math.max(1, rows)));
        for (int start = 0; start < rows; start += LOAD_CHUNK) {
            batch.clear();
            int end = Math.min(rows, start + LOAD_CHUNK);
            for (int index = start; index < end; index++) {
                batch.addValues(
                        keyOffset + index,
                        index & 1_023,
                        metric(index));
            }
            table.addBatch(batch);
        }
        batch.clear();
    }

    private static void loadSharedStrings(
            ScaleStringFactTable table,
            int rows,
            String[] pool) {
        ScaleStringFactBatch batch =
                new ScaleStringFactBatch(
                        Math.min(LOAD_CHUNK, Math.max(1, rows)));
        for (int start = 0; start < rows; start += LOAD_CHUNK) {
            batch.clear();
            int end = Math.min(rows, start + LOAD_CHUNK);
            for (int index = start; index < end; index++) {
                String label = pool[index % pool.length];
                boolean notePresent = (index & 1) != 0;
                String note = notePresent
                        ? pool[(index + 1) % pool.length] : null;
                batch.addValues(
                        index & 1_023,
                        label,
                        notePresent,
                        note);
            }
            table.addBatch(batch);
        }
        batch.clear();
    }

    private static void loadDistinctStrings(
            ScaleStringFactTable table,
            int rows,
            String prefix) {
        ScaleStringFactBatch batch =
                new ScaleStringFactBatch(
                        Math.min(LOAD_CHUNK, Math.max(1, rows)));
        for (int start = 0; start < rows; start += LOAD_CHUNK) {
            batch.clear();
            int end = Math.min(rows, start + LOAD_CHUNK);
            for (int index = start; index < end; index++) {
                batch.addValues(
                        index & 1_023,
                        prefix + "-label-" + index,
                        false,
                        null);
            }
            table.addBatch(batch);
        }
        batch.clear();
    }

    private static void loadStringAccess(
            ScaleStringAccessFactTable table,
            int rows,
            String prefix,
            int bucketCardinality) {
        int buckets = Math.max(1, bucketCardinality);
        String[] bucketPool = new String[buckets];
        for (int index = 0; index < buckets; index++) {
            bucketPool[index] = prefix + "-bucket-" + index;
        }
        ScaleStringAccessFactBatch batch =
                new ScaleStringAccessFactBatch(
                        Math.min(LOAD_CHUNK, Math.max(1, rows)));
        for (int start = 0; start < rows; start += LOAD_CHUNK) {
            batch.clear();
            int end = Math.min(rows, start + LOAD_CHUNK);
            for (int index = start; index < end; index++) {
                batch.addValues(
                        prefix + "-id-" + index,
                        prefix + "-alias-" + index,
                        bucketPool[index % buckets],
                        metric(index));
            }
            table.addBatch(batch);
        }
        batch.clear();
        Arrays.fill(bucketPool, null);
    }

    private static String[] stringPool(int cardinality, String prefix) {
        String[] result = new String[Math.max(1, cardinality)];
        for (int index = 0; index < result.length; index++) {
            result[index] = new String(
                    prefix + "-value-" + index);
        }
        return result;
    }

    private static long metric(int index) {
        return index % 1_000L;
    }

    private static long expectedMetricSum(int rows) {
        long cycles = rows / 1_000L;
        long remainder = rows % 1_000L;
        return cycles * 499_500L
                + remainder * (remainder - 1L) / 2L;
    }

    private static NumericRun numericSum(
            ScaleNumericFactTable table,
            DataFlowContext context) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("numeric");
        DataFlowInvocation<LongScalarResult> invocation =
                source.candidates()
                        .project(source.columns().metric())
                        .sum()
                        .compile()
                        .newInvocation(context)
                        .bind(
                                source,
                                ScaleNumericFactDataFlow.bind(table));
        long value = invocation.execute().value();
        return new NumericRun(value, invocation.stats());
    }

    private static long stringCount(ScaleStringFactTable table) {
        ScaleStringFactDataFlow.Source source =
                ScaleStringFactDataFlow.source("strings");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return execute(
                    source.candidates().count(),
                    source,
                    ScaleStringFactDataFlow.bind(table),
                    context).value();
        } finally {
            context.close();
        }
    }

    private static long stringEqualsCount(
            ScaleStringFactTable table, String value) {
        ScaleStringFactDataFlow.Source source =
                ScaleStringFactDataFlow.source("strings");
        DataFlowContext context = DataFlowContext.managedParallel(
                Math.min(8, Runtime.getRuntime().availableProcessors()));
        try {
            return execute(
                    source.candidates()
                            .filter(source.columns().label().equalTo(value))
                            .count(),
                    source,
                    ScaleStringFactDataFlow.bind(table),
                    context).value();
        } finally {
            context.close();
        }
    }

    private static int verifyCandidateDelivery(
            ScaleNumericFactTable table, int limit) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("candidate");
        final int[] calls = new int[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<ScaleNumericFactScan.Visitor>
                    invocation = source.deliver(
                            source.candidates().limit(limit))
                            .compile()
                            .newInvocation(context)
                            .bind(
                                    source,
                                    ScaleNumericFactDataFlow.bind(table))
                            .visitor(new ScaleNumericFactScan.Visitor() {
                                @Override
                                public boolean visit(
                                        io.github.somaruntime.soma.benchmarks.schema
                                                .generated
                                                .ScaleNumericFactCursor row) {
                                    calls[0]++;
                                    return true;
                                }
                            });
            DeliveryResult delivered = invocation.execute();
            require(delivered.deliveredElements() == limit
                            && delivered.completed()
                            && ledgerDrained(invocation.stats()),
                    "candidate delivery count");
            return 1;
        } finally {
            context.close();
        }
    }

    private static DeliveryResult numericCandidateDelivery(
            ScaleNumericFactTable table,
            int limit,
            final int stopAfter) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("candidate");
        final int[] calls = new int[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return source.deliver(source.candidates().limit(limit))
                    .compile()
                    .newInvocation(context)
                    .bind(
                            source,
                            ScaleNumericFactDataFlow.bind(table))
                    .visitor(new ScaleNumericFactScan.Visitor() {
                        @Override
                        public boolean visit(
                                io.github.somaruntime.soma.benchmarks.schema.generated
                                        .ScaleNumericFactCursor row) {
                            calls[0]++;
                            return calls[0] < stopAfter;
                        }
                    })
                    .execute();
        } finally {
            context.close();
        }
    }

    private static int verifyNumericGroup(
            ScaleNumericFactTable table, int limit) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("group");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            GroupedLongResult groups = execute(
                    source.candidates()
                            .limit(limit)
                            .groupBy(KeyExpression.of(
                                    source.columns().groupId()))
                            .counts(),
                    source,
                    ScaleNumericFactDataFlow.bind(table),
                    context);
            int expected = Math.min(limit, 1_024);
            require(groups.size() == expected,
                    "numeric Group cardinality");
            return groups.size();
        } finally {
            context.close();
        }
    }

    private static long verifyNumericSelfJoin(
            ScaleNumericFactTable table, int limit) {
        return numericJoin(table, table, limit);
    }

    private static long numericJoin(
            ScaleNumericFactTable leftTable,
            ScaleNumericFactTable rightTable,
            int limit) {
        ScaleNumericFactDataFlow.Source left =
                ScaleNumericFactDataFlow.source(0, "left");
        ScaleNumericFactDataFlow.Source right =
                ScaleNumericFactDataFlow.source(1, "right");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return execute(
                    left.candidates().limit(limit)
                            .innerJoin(
                                    right.candidates().limit(limit))
                            .on(
                                    left.columns().id(),
                                    right.columns().id())
                            .count(),
                    left,
                    ScaleNumericFactDataFlow.bind(leftTable),
                    right,
                    ScaleNumericFactDataFlow.bind(rightTable),
                    context).value();
        } finally {
            context.close();
        }
    }

    private static long numericMinMaxJoin(
            ScaleNumericFactTable leftTable,
            ScaleNumericFactTable rightTable,
            long buildExclusive) {
        ScaleNumericFactDataFlow.Source left =
                ScaleNumericFactDataFlow.source(0, "minMaxLeft");
        ScaleNumericFactDataFlow.Source right =
                ScaleNumericFactDataFlow.source(1, "minMaxRight");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return execute(
                    left.candidates()
                            .innerJoin(
                                    right.candidates().filter(
                                            right.columns().id()
                                                    .lessThan(buildExclusive)))
                            .on(
                                    left.columns().id(),
                                    right.columns().id())
                            .count(),
                    left,
                    ScaleNumericFactDataFlow.bind(leftTable),
                    right,
                    ScaleNumericFactDataFlow.bind(rightTable),
                    context).value();
        } finally {
            context.close();
        }
    }

    private static long numericBloomJoin(
            ScaleNumericFactTable leftTable,
            ScaleNumericFactTable rightTable,
            long mask) {
        ScaleNumericFactDataFlow.Source left =
                ScaleNumericFactDataFlow.source(0, "bloomLeft");
        ScaleNumericFactDataFlow.Source right =
                ScaleNumericFactDataFlow.source(1, "bloomRight");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return execute(
                    left.candidates()
                            .innerJoin(
                                    right.candidates().filter(
                                            right.columns().id()
                                                    .bitwiseAnd(mask)
                                                    .equalTo(0L)))
                            .on(
                                    left.columns().id(),
                                    right.columns().id())
                            .count(),
                    left,
                    ScaleNumericFactDataFlow.bind(leftTable),
                    right,
                    ScaleNumericFactDataFlow.bind(rightTable),
                    context).value();
        } finally {
            context.close();
        }
    }

    private static int verifyNumericWindow(
            ScaleNumericFactTable table, int limit) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("window");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            LongColumnResult windows = execute(
                    source.candidates().limit(limit)
                            .windowByCount(
                                    1_024,
                                    1_024,
                                    PartialWindowPolicy.INCLUDE_PARTIAL)
                            .sum(source.columns().metric()),
                    source,
                    ScaleNumericFactDataFlow.bind(table),
                    context);
            int expected = (limit + 1_023) / 1_024;
            require(windows.size() == expected,
                    "numeric Window count");
            return windows.size();
        } finally {
            context.close();
        }
    }

    private static long verifyStringRelation(
            ScaleStringFactTable table, int limit) {
        return stringJoin(table, table, limit)
                + verifyStringGroup(table, limit);
    }

    private static long stringJoin(
            ScaleStringFactTable leftTable,
            ScaleStringFactTable rightTable,
            int limit) {
        ScaleStringFactDataFlow.Source left =
                ScaleStringFactDataFlow.source(0, "leftStrings");
        ScaleStringFactDataFlow.Source right =
                ScaleStringFactDataFlow.source(1, "rightStrings");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return execute(
                    left.candidates().limit(limit)
                            .innerJoin(
                                    right.candidates().limit(limit))
                            .on(
                                    left.columns().label(),
                                    right.columns().label())
                            .count(),
                    left,
                    ScaleStringFactDataFlow.bind(leftTable),
                    right,
                    ScaleStringFactDataFlow.bind(rightTable),
                    context).value();
        } finally {
            context.close();
        }
    }

    private static int verifyStringGroup(
            ScaleStringFactTable table, int limit) {
        ScaleStringFactDataFlow.Source source =
                ScaleStringFactDataFlow.source("stringGroup");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            GroupedLongResult groups = execute(
                    source.candidates().limit(limit)
                            .groupBy(KeyExpression.of(
                                    source.columns().label()))
                            .counts(),
                    source,
                    ScaleStringFactDataFlow.bind(table),
                    context);
            return groups.size();
        } finally {
            context.close();
        }
    }

    private static int verifyGroupDelivery(
            ScaleNumericFactTable table) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("groupDelivery");
        final int[] groups = new int[1];
        final long[] members = new long[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<GroupVisitor> invocation =
                    source.candidates().limit(256L)
                            .groupBy(KeyExpression.of(
                                    source.columns().groupId()))
                            .deliver()
                            .compile()
                            .newInvocation(context)
                            .bind(
                                    source,
                                    ScaleNumericFactDataFlow.bind(table))
                            .visitor(new GroupVisitor() {
                                @Override
                                public boolean visit(GroupCursor group) {
                                    groups[0]++;
                                    members[0] += group.size();
                                    return true;
                                }
                            });
            DeliveryResult delivered = invocation.execute();
            require(delivered.completed()
                            && delivered.deliveredElements() == groups[0]
                            && members[0] == 256L
                            && ledgerDrained(invocation.stats()),
                    "Group callback");
            return 1;
        } finally {
            context.close();
        }
    }

    private static int verifyJoinDelivery(
            ScaleNumericFactTable table) {
        ScaleNumericFactDataFlow.Source left =
                ScaleNumericFactDataFlow.source(0, "joinLeft");
        ScaleNumericFactDataFlow.Source right =
                ScaleNumericFactDataFlow.source(1, "joinRight");
        JoinedFlow<
                ScaleNumericFactDataFlow.Binding,
                ScaleNumericFactDataFlow.Binding> joined =
                left.candidates().limit(128L)
                        .innerJoin(right.candidates().limit(128L))
                        .on(
                                left.columns().id(),
                                right.columns().id());
        final int[] pairs = new int[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<JoinedIndexVisitor> invocation =
                    joined.deliver()
                            .compile()
                            .newInvocation(context)
                            .bind(
                                    left,
                                    ScaleNumericFactDataFlow.bind(table))
                            .bind(
                                    right,
                                    ScaleNumericFactDataFlow.bind(table))
                            .visitor(new JoinedIndexVisitor() {
                                @Override
                                public boolean visit(
                                        int leftIndex,
                                        boolean rightPresent,
                                        int rightIndex) {
                                    require(rightPresent
                                                    && leftIndex
                                                    == rightIndex,
                                            "Join callback indexes");
                                    pairs[0]++;
                                    return true;
                                }
                            });
            DeliveryResult delivered = invocation.execute();
            require(delivered.completed()
                            && pairs[0] == 128
                            && delivered.deliveredElements() == 128L
                            && ledgerDrained(invocation.stats()),
                    "Join callback");
            return 1;
        } finally {
            context.close();
        }
    }

    private static int verifyWindowDelivery(
            ScaleNumericFactTable table) {
        ScaleNumericFactDataFlow.Source source =
                ScaleNumericFactDataFlow.source("windowDelivery");
        final int[] windows = new int[1];
        final long[] members = new long[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<WindowVisitor> invocation =
                    source.candidates().limit(128L)
                            .windowByCount(
                                    16,
                                    16,
                                    PartialWindowPolicy.INCLUDE_PARTIAL)
                            .deliver()
                            .compile()
                            .newInvocation(context)
                            .bind(
                                    source,
                                    ScaleNumericFactDataFlow.bind(table))
                            .visitor(new WindowVisitor() {
                                @Override
                                public boolean visit(WindowCursor window) {
                                    windows[0]++;
                                    members[0] += window.size();
                                    return true;
                                }
                            });
            DeliveryResult delivered = invocation.execute();
            require(delivered.completed()
                            && windows[0] == 8
                            && members[0] == 128L
                            && ledgerDrained(invocation.stats()),
                    "Window callback");
            return 1;
        } finally {
            context.close();
        }
    }

    private static int verifyStringDelivery(
            ScaleStringFactTable table) {
        ScaleStringFactDataFlow.Source source =
                ScaleStringFactDataFlow.source("stringDelivery");
        final int[] calls = new int[1];
        final long[] length = new long[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<ScaleStringFactScan.Visitor>
                    invocation = source.deliver(
                            source.candidates().limit(128L))
                            .compile()
                            .newInvocation(context)
                            .bind(
                                    source,
                                    ScaleStringFactDataFlow.bind(table))
                            .visitor(new ScaleStringFactScan.Visitor() {
                                @Override
                                public boolean visit(
                                        ScaleStringFactCursor row) {
                                    calls[0]++;
                                    length[0] += row.label().length();
                                    if (row.notePresent()) {
                                        length[0] += row.note().length();
                                    }
                                    return true;
                                }
                            });
            DeliveryResult delivered = invocation.execute();
            require(delivered.completed()
                            && calls[0] == 128
                            && length[0] > 0L
                            && ledgerDrained(invocation.stats()),
                    "String callback");
            return 1;
        } finally {
            context.close();
        }
    }

    private static boolean verifyCursorNonEscape(RuntimePlan plan) {
        ScaleSet set = ScaleSet.create(
                "delivery-non-escape", plan, false, true, false);
        final ScaleStringFactCursor[] escaped =
                new ScaleStringFactCursor[1];
        try {
            loadSharedStrings(
                    set.strings, 1, stringPool(1, "escape"));
            ScaleStringFactDataFlow.Source source =
                    ScaleStringFactDataFlow.source("escape");
            DataFlowContext context = DataFlowContext.sequential();
            try {
                source.deliver(source.candidates().limit(1L))
                        .compile()
                        .newInvocation(context)
                        .bind(
                                source,
                                ScaleStringFactDataFlow.bind(set.strings))
                        .visitor(new ScaleStringFactScan.Visitor() {
                            @Override
                            public boolean visit(
                                    ScaleStringFactCursor row) {
                                escaped[0] = row;
                                return true;
                            }
                        })
                        .execute();
            } finally {
                context.close();
            }
            try {
                escaped[0].label();
                return false;
            } catch (SomaRuntimeException expected) {
                return "internal_invariant_violation".equals(
                        expected.code());
            }
        } finally {
            set.group.release();
        }
    }

    private static void expectCode(
            CallbackDeliveryInvocation<?> invocation,
            String code) {
        try {
            invocation.execute();
            throw new AssertionError(code + " must fail");
        } catch (SomaRuntimeException expected) {
            require(code.equals(expected.code()),
                    "expected failure code " + code
                            + " but got " + expected.code());
        }
    }

    private static boolean ledgerDrained(DataFlowStats stats) {
        return stats.resources().sharedScratchCurrentBytes() == 0L
                && stats.resources().workerScratchCurrentBytes() == 0L
                && stats.resources().outputCurrentBytes() == 0L
                && stats.resources().outputCurrentElements() == 0L
                && stats.parallel().taskCurrent() == 0
                && stats.parallel().workerCurrent() == 0;
    }

    private static <R, B extends DataFlowBinding> R execute(
            DataFlowDefinition<R> definition,
            SourceSlot<B> source,
            B binding) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return execute(
                    definition, source, binding, context);
        } finally {
            context.close();
        }
    }

    private static <R, B extends DataFlowBinding> R execute(
            DataFlowDefinition<R> definition,
            SourceSlot<B> source,
            B binding,
            DataFlowContext context) {
        return definition.compile()
                .newInvocation(context)
                .bind(source, binding)
                .execute();
    }

    private static <
            R,
            L extends DataFlowBinding,
            T extends DataFlowBinding> R execute(
            DataFlowDefinition<R> definition,
            SourceSlot<L> left,
            L leftBinding,
            SourceSlot<T> right,
            T rightBinding,
            DataFlowContext context) {
        return definition.compile()
                .newInvocation(context)
                .bind(left, leftBinding)
                .bind(right, rightBinding)
                .execute();
    }

    private static ExpansionProbe runSyntheticExpansion(
            int parentRows,
            int maximumChildRows,
            long maximumOutputElements,
            boolean childPresent) {
        ProbeSource parent = new ProbeSource(
                0, "syntheticParent", "parent");
        ProbeSource child = new ProbeSource(
                1, "syntheticChild", "child");
        ProbeBinding parentBinding =
                new ProbeBinding(parentRows);
        final int[] childBindingCalls = new int[1];
        OwnedChildAccess<ProbeBinding, ProbeBinding> access =
                new OwnedChildAccess<ProbeBinding, ProbeBinding>() {
                    @Override
                    public ProbeBinding childBinding(
                            ProbeBinding binding, int parentIndex) {
                        childBindingCalls[0]++;
                        return childPresent
                                ? new ProbeBinding(1) : null;
                    }

                    @Override
                    public int maximumChildRows(
                            ProbeBinding binding) {
                        return maximumChildRows;
                    }

                    @Override
                    public String identity() {
                        return "qualification.synthetic-owned-child";
                    }
                };
        ExpandedFlow<ProbeBinding, ProbeBinding> expanded =
                io.github.somaruntime.soma.dataflow.GeneratedDataFlow.ownedChildren(
                        parent,
                        io.github.somaruntime.soma.dataflow.GeneratedDataFlow
                                .candidates(parent),
                        child,
                        access);
        DataFlowContext context = DataFlowContext.sequential();
        DataFlowInvocation<
                io.github.somaruntime.soma.dataflow.ExpandedIndexResult>
                invocation = expanded.indexes()
                .compile()
                .newInvocation(context)
                .bind(parent, parentBinding)
                .budget(ExecutionBudget.defaults().toBuilder()
                        .maximumOutputElements(Math.min(
                                Integer.MAX_VALUE,
                                Math.max(1L, maximumOutputElements)))
                        .build());
        String code;
        try {
            invocation.execute();
            throw new AssertionError(
                    "synthetic expansion must fail");
        } catch (SomaRuntimeException expected) {
            code = expected.code();
            require(ledgerDrained(invocation.stats()),
                    "synthetic expansion ledger cleanup");
        } finally {
            context.close();
        }
        return new ExpansionProbe(
                code,
                childBindingCalls[0],
                parentBinding.releaseCalls);
    }

    private static QualificationObservation begin(
            String lane,
            String profile,
            long leftRows,
            long rightRows) {
        QualificationObservation result =
                new QualificationObservation();
        result.lane = lane;
        result.profile = profile;
        result.leftRows = leftRows;
        result.rightRows = rightRows;
        return result;
    }

    private static void observeGroup(
            QualificationObservation observation,
            SomaGroupMetadata metadata) {
        observation.structuralCurrentBytes =
                metadata.currentStructuralBytes();
        observation.structuralHighWaterBytes = Math.max(
                observation.structuralHighWaterBytes,
                metadata.structuralHighWaterBytes());
        observation.heapUsedPeakBytes = Math.max(
                observation.heapUsedPeakBytes, heapUsed());
    }

    private static void releaseAndObserve(
            QualificationObservation observation,
            SomaGroup group) {
        group.release();
        SomaGroupMetadata released = group.metadata();
        require(released.currentStructuralBytes() == 0L
                        && released.currentTableInstances() == 0L,
                "released Group ledger must drain");
        observation.structuralCurrentBytes = 0L;
        observation.structuralHighWaterBytes = Math.max(
                observation.structuralHighWaterBytes,
                released.structuralHighWaterBytes());
        observation.heapUsedPeakBytes = Math.max(
                observation.heapUsedPeakBytes, heapUsed());
    }

    private static WeakReference<String> mutateTrackedString(
            ScaleStringFactTable table, int index) {
        String value = new String(
                "qualification-retained-string-with-different-length");
        long epoch = table.structuralEpoch();
        table.mutateAt(index).setLabel(value).commit();
        require(table.structuralEpoch() == epoch
                        && table.fetchAt(index).label == value,
                "String arbitrary-length mutation");
        long changedEpoch = table.structuralEpoch();
        table.mutateAt(index)
                .setLabel(new String(value)).commit();
        require(table.structuralEpoch() == changedEpoch
                        && table.fetchAt(index).label == value,
                "String equal-value no-op retains original reference");
        WeakReference<String> reference =
                new WeakReference<String>(value);
        value = null;
        return reference;
    }

    private static boolean awaitCollected(
            WeakReference<?> reference, int attempts) {
        for (int attempt = 0;
             attempt < attempts && reference.get() != null;
             attempt++) {
            forceGc();
        }
        return reference.get() == null;
    }

    private static void forceGc() {
        System.gc();
        System.runFinalization();
        byte[] pressure = new byte[1024 * 1024];
        pressure[0] = 1;
        if (pressure[0] != 1) {
            throw new AssertionError("unreachable");
        }
        Thread.yield();
    }

    private static long heapUsed() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static List<Integer> integers(int[] values) {
        ArrayList<Integer> result =
                new ArrayList<Integer>(values.length);
        for (int value : values) {
            result.add(Integer.valueOf(value));
        }
        return Collections.unmodifiableList(result);
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }

    private static String hex(long value) {
        return Long.toHexString(value);
    }

    private static long checkedMultiply(long left, long right) {
        if (left < 0L || right < 0L
                || left != 0L && right > Long.MAX_VALUE / left) {
            throw new IllegalArgumentException(
                    "qualification arithmetic overflow");
        }
        return left * right;
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0L || right < 0L
                || Long.MAX_VALUE - left < right) {
            throw new IllegalArgumentException(
                    "qualification arithmetic overflow");
        }
        return left + right;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(
                    "runtime-scale qualification failed: " + message);
        }
    }

    private static final class NumericRun {
        final long value;
        final DataFlowStats stats;

        NumericRun(long value, DataFlowStats stats) {
            this.value = value;
            this.stats = stats;
        }
    }

    private static final class ExpansionProbe {
        final String code;
        final int childBindingCalls;
        final int releaseCalls;

        ExpansionProbe(
                String code,
                int childBindingCalls,
                int releaseCalls) {
            this.code = code;
            this.childBindingCalls = childBindingCalls;
            this.releaseCalls = releaseCalls;
        }
    }

    private static final class ProbeSource
            extends SourceSlot<ProbeBinding> {
        ProbeSource(
                int ordinal, String alias, String table) {
            super(
                    ordinal,
                    alias,
                    "qualification-synthetic-schema",
                    table);
        }
    }

    private static final class ProbeBinding
            implements DataFlowBinding {
        private final int rows;
        private final Object physicalIdentity = new Object();
        int releaseCalls;

        ProbeBinding(int rows) {
            this.rows = rows;
        }

        @Override
        public long aggregateInstanceId() {
            return 1L;
        }

        @Override
        public Object physicalIdentity() {
            return physicalIdentity;
        }

        @Override
        public String schemaIdentity() {
            return "qualification-synthetic-schema";
        }

        @Override
        public String tableIdentity() {
            return "parent";
        }

        @Override
        public String generatedProtocol() {
            return RuntimeCompatibility.GENERATED_PROTOCOL;
        }

        @Override
        public String transformationProtocol() {
            return io.github.somaruntime.soma.dataflow.GeneratedDataFlow
                    .TRANSFORMATION_PROTOCOL;
        }

        @Override
        public String kernelProtocol() {
            return io.github.somaruntime.soma.dataflow.GeneratedDataFlow
                    .KERNEL_PROTOCOL;
        }

        @Override
        public long structuralEpoch() {
            return 0L;
        }

        @Override
        public int packedSize() {
            return rows;
        }

        @Override
        public boolean isPresent(int columnOrdinal, int index) {
            throw new AssertionError("source enumerated");
        }

        @Override
        public boolean booleanValue(int columnOrdinal, int index) {
            throw new AssertionError("source enumerated");
        }

        @Override
        public long longValue(int columnOrdinal, int index) {
            throw new AssertionError("source enumerated");
        }

        @Override
        public double doubleValue(int columnOrdinal, int index) {
            throw new AssertionError("source enumerated");
        }

        @Override
        public String stringValue(int columnOrdinal, int index) {
            throw new AssertionError("source enumerated");
        }

        @Override
        public IndexSnapshot indexSnapshot(
                int[] indexes, int length) {
            throw new AssertionError("source enumerated");
        }

        @Override
        public void acquire(String operation) {
            // Acquisition and validated cardinality observation are legal
            // before relation enumeration.
        }

        @Override
        public void release(
                String operation,
                boolean success,
                long scanned,
                long matched,
                String failureCode) {
            releaseCalls++;
        }
    }

    private static final class ScaleSet {
        final SomaGroup group;
        final ScaleNumericFactTable numeric;
        final ScaleStringFactTable strings;
        final ScaleStringAccessFactTable access;

        private ScaleSet(
                SomaGroup group,
                ScaleNumericFactTable numeric,
                ScaleStringFactTable strings,
                ScaleStringAccessFactTable access) {
            this.group = group;
            this.numeric = numeric;
            this.strings = strings;
            this.access = access;
        }

        static ScaleSet create(
                String identity,
                RuntimePlan plan,
                boolean numeric,
                boolean strings,
                boolean access) {
            SomaGroupPlan.Builder builder =
                    SomaGroupPlan.builder(identity);
            if (numeric) {
                builder.member(
                        "numeric",
                        SchemaMetadata.metadata(),
                        ScaleNumericFactTable.metadata(),
                        plan);
            }
            if (strings) {
                builder.member(
                        "strings",
                        SchemaMetadata.metadata(),
                        ScaleStringFactTable.metadata(),
                        plan);
            }
            if (access) {
                builder.member(
                        "access",
                        SchemaMetadata.metadata(),
                        ScaleStringAccessFactTable.metadata(),
                        plan);
            }
            SomaGroup group = SomaGroup.create(builder.build());
            ScaleNumericFactTable numericTable = numeric
                    ? ScaleNumericFactTable.attach(group, "numeric")
                    : null;
            ScaleStringFactTable stringTable = strings
                    ? ScaleStringFactTable.attach(group, "strings")
                    : null;
            ScaleStringAccessFactTable accessTable = access
                    ? ScaleStringAccessFactTable.attach(group, "access")
                    : null;
            return new ScaleSet(
                    group, numericTable, stringTable, accessTable);
        }
    }

    private static final class NumericPair {
        final SomaGroup group;
        final ScaleNumericFactTable left;
        final ScaleNumericFactTable right;

        private NumericPair(
                SomaGroup group,
                ScaleNumericFactTable left,
                ScaleNumericFactTable right) {
            this.group = group;
            this.left = left;
            this.right = right;
        }

        static NumericPair create(
                String identity, RuntimePlan plan) {
            SomaGroupPlan groupPlan =
                    SomaGroupPlan.builder(identity)
                            .member(
                                    "left",
                                    SchemaMetadata.metadata(),
                                    ScaleNumericFactTable.metadata(),
                                    plan)
                            .member(
                                    "right",
                                    SchemaMetadata.metadata(),
                                    ScaleNumericFactTable.metadata(),
                                    plan)
                            .build();
            SomaGroup group = SomaGroup.create(groupPlan);
            return new NumericPair(
                    group,
                    ScaleNumericFactTable.attach(group, "left"),
                    ScaleNumericFactTable.attach(group, "right"));
        }
    }

    private static final class StringPair {
        final SomaGroup group;
        final ScaleStringFactTable left;
        final ScaleStringFactTable right;

        private StringPair(
                SomaGroup group,
                ScaleStringFactTable left,
                ScaleStringFactTable right) {
            this.group = group;
            this.left = left;
            this.right = right;
        }

        static StringPair create(
                String identity, RuntimePlan plan) {
            SomaGroupPlan groupPlan =
                    SomaGroupPlan.builder(identity)
                            .member(
                                    "left",
                                    SchemaMetadata.metadata(),
                                    ScaleStringFactTable.metadata(),
                                    plan)
                            .member(
                                    "right",
                                    SchemaMetadata.metadata(),
                                    ScaleStringFactTable.metadata(),
                                    plan)
                            .build();
            SomaGroup group = SomaGroup.create(groupPlan);
            return new StringPair(
                    group,
                    ScaleStringFactTable.attach(group, "left"),
                    ScaleStringFactTable.attach(group, "right"));
        }
    }

    private static final class Probe {
        private final long startedNanos = System.nanoTime();
        private final long heapBefore = heapUsed();
        private final long allocatedBefore =
                JvmRuntimeMetrics.allLiveThreadAllocatedBytes();
        private final JvmRuntimeMetrics.Snapshot jvmBefore =
                JvmRuntimeMetrics.snapshot();

        void finish(QualificationObservation observation) {
            long completedNanos = System.nanoTime();
            long heapAfter = heapUsed();
            long allocatedAfter =
                    JvmRuntimeMetrics.allLiveThreadAllocatedBytes();
            JvmRuntimeMetrics.Delta delta =
                    JvmRuntimeMetrics.snapshot().since(jvmBefore);
            observation.workloadNanos =
                    completedNanos - startedNanos;
            observation.totalNanos =
                    completedNanos - startedNanos;
            observation.heapUsedBeforeBytes = heapBefore;
            observation.heapUsedPeakBytes = Math.max(
                    observation.heapUsedPeakBytes,
                    Math.max(heapBefore, heapAfter));
            observation.heapUsedAfterReleaseBytes = heapAfter;
            observation.allocatedBytes =
                    allocatedBefore < 0L || allocatedAfter < 0L
                            ? -1L
                            : Math.max(
                                    0L,
                                    allocatedAfter - allocatedBefore);
            observation.youngGcCount = delta.youngGcCount;
            observation.youngGcTimeMillis =
                    delta.youngGcTimeMillis;
            observation.fullGcCount = delta.fullGcCount;
            observation.fullGcTimeMillis =
                    delta.fullGcTimeMillis;
            observation.unknownGcCount = delta.unknownGcCount;
            observation.unknownGcTimeMillis =
                    delta.unknownGcTimeMillis;
        }
    }
}

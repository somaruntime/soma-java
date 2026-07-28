package com.example.soma.breadth;

import com.example.soma.breadth.generated.FullRowBatch;
import com.example.soma.breadth.generated.FullRowMutator;
import com.example.soma.breadth.generated.FullRowTable;
import com.example.soma.breadth.generated.SchemaMetadata;
import com.example.soma.breadth.generated.StringKeyRowBatch;
import com.example.soma.breadth.generated.StringKeyRowTable;
import com.example.soma.breadth.generated.StringParentBatch;
import com.example.soma.breadth.generated.StringParentTable;
import com.example.soma.breadth.generated.StringSelectorRowBatch;
import com.example.soma.breadth.generated.StringSelectorRowTable;
import com.example.soma.breadth.generated.StringSelectorRowDataFlow;
import com.example.soma.groupother.generated.OtherRowBatch;
import com.example.soma.groupother.generated.OtherRowDataFlow;
import com.example.soma.groupother.generated.OtherRowTable;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.GroupedLongResult;
import io.github.somaruntime.soma.dataflow.KeyExpression;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.dataflow.ParameterSlot;
import io.github.somaruntime.soma.dataflow.StringColumnResult;
import io.github.somaruntime.soma.runtime.EnumColumnView;
import io.github.somaruntime.soma.runtime.IntColumnView;
import io.github.somaruntime.soma.runtime.MaterializationBudget;
import io.github.somaruntime.soma.runtime.RuntimePlan;
import io.github.somaruntime.soma.runtime.SomaGroup;
import io.github.somaruntime.soma.runtime.SomaGroupMemberState;
import io.github.somaruntime.soma.runtime.SomaGroupPlan;
import io.github.somaruntime.soma.runtime.SomaGroupState;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;
import io.github.somaruntime.soma.runtime.StringResourceProfile;
import io.github.somaruntime.soma.runtime.StringResourceProfileStatus;
import io.github.somaruntime.soma.runtime.StringResourceRole;
import io.github.somaruntime.soma.runtime.TableStats;
import io.github.somaruntime.soma.runtime.UpdateResult;
import io.github.somaruntime.soma.runtime.metadata.SomaColumnMetadata;
import io.github.somaruntime.soma.runtime.metadata.SomaGroupMetadata;
import io.github.somaruntime.soma.runtime.metadata.SomaPrimaryLocatorLayout;
import io.github.somaruntime.soma.runtime.metadata.SomaTableMetadata;
import io.github.somaruntime.soma.runtime.metadata.SomaTypeKind;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BreadthConsumer {
    private BreadthConsumer() { }

    public static void main(String[] args) {
        verifyDefaultsAndReferenceFields();
        verifyFailedUpdateDoesNotRetainReferences();
        verifyPresenceWordBoundaries();
        verifyEffectivePlanAndStringProfile();
        verifyStringKeyAndBudgets();
        verifyStringSelectorsAndMetadata();
        verifyStringReferenceLifecycle();
        verifyStringKeyedChild();
        verifySomaGroupCompositionAndLifecycle();
        System.out.println("breadth-consumer: ok");
    }

    private static void verifySomaGroupCompositionAndLifecycle() {
        RuntimePlan breadthPlan = SchemaMetadata.defaultRuntimePlan();
        RuntimePlan otherPlan =
                com.example.soma.groupother.generated.SchemaMetadata
                        .defaultRuntimePlan();
        SomaGroupPlan.Builder groupBuilder =
                SomaGroupPlan.builder("breadth-explicit-group")
                        .member("left",
                                SchemaMetadata.metadata(),
                                StringSelectorRowTable.metadata(),
                                breadthPlan)
                        .member("leftReplica",
                                SchemaMetadata.metadata(),
                                StringSelectorRowTable.metadata(),
                                breadthPlan)
                        .member("other",
                                com.example.soma.groupother.generated
                                        .SchemaMetadata.metadata(),
                                OtherRowTable.metadata(),
                                otherPlan);
        SomaGroupPlan groupPlan = groupBuilder.build();
        expectIllegalState(
                () -> groupBuilder.maximumStructuralBytes(1L),
                "SomaGroupPlan builder closes after build");
        SomaGroup group = SomaGroup.create(groupPlan);
        SomaGroupMetadata planned = group.metadata();
        check(planned.state() == SomaGroupState.ACTIVE
                        && planned.membershipEpoch() == 0L
                        && planned.requireMember("left").state()
                        == SomaGroupMemberState.PLANNED
                        && planned.requireMember("left")
                                .rootTableRuntimeMetadata() == null,
                "explicit Group starts with frozen unallocated slots");

        StringSelectorRowTable left =
                StringSelectorRowTable.attach(group, "left");
        StringSelectorRowTable leftReplica =
                StringSelectorRowTable.attach(group, "leftReplica");
        OtherRowTable other = OtherRowTable.attach(group, "other");
        left.addBatch(new StringSelectorRowBatch().addValues(1, "shared"));
        leftReplica.addBatch(
                new StringSelectorRowBatch().addValues(2, "shared"));
        other.addBatch(new OtherRowBatch().addValues(1L, 7));
        left.setDataVersion("left-v1");
        group.setDataVersion("group-v1");

        SomaGroupMetadata attached = group.metadata();
        long leftAggregate =
                attached.requireMember("left").aggregateInstanceId();
        long replicaAggregate =
                attached.requireMember("leftReplica").aggregateInstanceId();
        long otherAggregate =
                attached.requireMember("other").aggregateInstanceId();
        check(attached.membershipEpoch() == 3L
                        && leftAggregate > 0L
                        && replicaAggregate > 0L
                        && otherAggregate > 0L
                        && leftAggregate != replicaAggregate
                        && leftAggregate != otherAggregate
                        && replicaAggregate != otherAggregate,
                "same-schema instances and cross-schema root keep independent aggregates");
        check(attached.requireMember("left").attachmentOrdinal() == 1L
                        && attached.requireMember("leftReplica")
                        .attachmentOrdinal() == 2L
                        && attached.requireMember("other")
                        .attachmentOrdinal() == 3L
                        && "left-v1".equals(left.dataVersion())
                        && "group-v1".equals(group.dataVersion()),
                "attachment order and independent dataVersion markers");
        check(attached.requireMember("left")
                        .rootTableRuntimeMetadata() != null
                        && attached.requireMember("left")
                        .rootTableRuntimeMetadata().rows() == 1
                        && attached.requireMember("left")
                        .rootTableRuntimeMetadata().indexes().size() == 1
                        && attached.requireMember("left")
                        .rootTableRuntimeMetadata().uniques().size() == 1,
                "Group member projects detached Table/access Runtime Metadata");
        expectCode("group_member_already_attached",
                () -> StringSelectorRowTable.attach(group, "left"),
                "stable member slot publishes once");
        expectCode("group_release_required", left::release,
                "explicit member cannot release independently");

        StringSelectorRowTable external =
                StringSelectorRowTable.create();
        external.addBatch(
                new StringSelectorRowBatch().addValues(3, "shared"));
        DataFlowContext context = DataFlowContext.sequential();
        try {
            StringSelectorRowDataFlow.Source leftSource =
                    StringSelectorRowDataFlow.source(0, "groupLeft");
            StringSelectorRowDataFlow.Source replicaSource =
                    StringSelectorRowDataFlow.source(1, "groupReplica");
            LongScalarResult sameGroup = leftSource.candidates()
                    .innerJoin(replicaSource.candidates())
                    .on(leftSource.columns().label(),
                            replicaSource.columns().label())
                    .count()
                    .compile()
                    .newInvocation(context)
                    .bind(leftSource, StringSelectorRowDataFlow.bind(left))
                    .bind(replicaSource,
                            StringSelectorRowDataFlow.bind(leftReplica))
                    .execute();
            check(sameGroup.value() == 1L,
                    "DataFlow acquires distinct aggregates in one explicit Group");

            StringSelectorRowDataFlow.Source externalSource =
                    StringSelectorRowDataFlow.source(1, "implicitExternal");
            LongScalarResult crossGroup = leftSource.candidates()
                    .innerJoin(externalSource.candidates())
                    .on(leftSource.columns().label(),
                            externalSource.columns().label())
                    .count()
                    .compile()
                    .newInvocation(context)
                    .bind(leftSource, StringSelectorRowDataFlow.bind(left))
                    .bind(externalSource,
                            StringSelectorRowDataFlow.bind(external))
                    .execute();
            check(crossGroup.value() == 1L,
                    "DataFlow guard is independent of explicit/implicit Group");

            OtherRowDataFlow.Source otherSource =
                    OtherRowDataFlow.source(1, "otherSchema");
            LongScalarResult crossSchema = leftSource.candidates()
                    .innerJoin(otherSource.candidates())
                    .on(leftSource.columns().id(),
                            otherSource.columns().id())
                    .count()
                    .compile()
                    .newInvocation(context)
                    .bind(leftSource, StringSelectorRowDataFlow.bind(left))
                    .bind(otherSource, OtherRowDataFlow.bind(other))
                    .execute();
            check(crossSchema.value() == 1L,
                    "DataFlow guard admits cross-schema aggregates");
        } finally {
            context.close();
            external.release();
        }

        IntColumnView pinned = leftReplica.idColumn();
        expectCode("view_pinned", group::release,
                "Group release completes all-member preflight first");
        check(!left.isReleased() && !leftReplica.isReleased()
                        && !other.isReleased(),
                "failed Group release leaves every root live");
        pinned.close();
        group.release();
        group.release();
        check(group.state() == SomaGroupState.RELEASED
                        && left.isReleased()
                        && leftReplica.isReleased()
                        && other.isReleased()
                        && group.membershipEpoch() == 4L,
                "Group release is terminal and idempotent");
        check(attached.state() == SomaGroupState.ACTIVE
                        && attached.requireMember("left").state()
                        == SomaGroupMemberState.ATTACHED
                        && group.metadata().requireMember("left").state()
                        == SomaGroupMemberState.RELEASED
                        && !attached.requireMember("left")
                                .rootTableRuntimeMetadata().released()
                        && group.metadata().requireMember("left")
                                .rootTableRuntimeMetadata().released(),
                "runtime Metadata snapshots are detached historical values");
        expectCode("group_released",
                () -> StringSelectorRowTable.attach(group, "left"),
                "released Group rejects attach");

        RuntimePlan tiny = SchemaMetadata.newPlan()
                .maximumAggregateStorageBytes(1L)
                .maximumOwnershipTableInstances(1L)
                .build();
        SomaGroup rollbackGroup = SomaGroup.create(
                SomaGroupPlan.builder("attach-rollback")
                        .member("tiny",
                                SchemaMetadata.metadata(),
                                StringSelectorRowTable.metadata(),
                                tiny)
                        .build());
        expectCode("memory_limit_exceeded",
                () -> StringSelectorRowTable.attach(
                        rollbackGroup, "tiny"),
                "attach resource failure");
        check(rollbackGroup.state() == SomaGroupState.ACTIVE
                        && rollbackGroup.membershipEpoch() == 0L
                        && rollbackGroup.metadata().requireMember("tiny")
                        .state() == SomaGroupMemberState.PLANNED,
                "failed attach rolls back membership, ledger and epoch");
        rollbackGroup.release();
        check(rollbackGroup.state() == SomaGroupState.RELEASED,
                "failed attach leaves a releasable empty Group");
    }

    private static void verifyEffectivePlanAndStringProfile() {
        StringResourceProfile profile = StringResourceProfile.builder()
                .averageUtf16CodeUnits(2)
                .maximumUtf16CodeUnits(8)
                .valueCardinality(2L)
                .distinctObjectIdentityEstimate(4L)
                .intraTableSharingBasisPoints(0)
                .interTableSharingBasisPoints(0)
                .presenceBasisPoints(10000)
                .role(StringResourceRole.INDEX)
                .role(StringResourceRole.UNIQUE)
                .role(StringResourceRole.GROUP)
                .role(StringResourceRole.JOIN)
                .simultaneouslyLiveTableCount(2)
                .build();
        RuntimePlan.Builder builder = SchemaMetadata.newPlan();
        RuntimePlan.TableEditor editor =
                builder.table(StringSelectorRowTable.metadata())
                        .initialCapacity(2)
                        .planningRows(1)
                        .maximumRows(2)
                        .stringResourceProfile(profile);
        RuntimePlan plan = builder.build();
        check(plan.effectiveMetadata()
                        .requireTable("StringSelectorRow")
                        .stringResourceProfile().status()
                        == StringResourceProfileStatus.PROFILED_UNVERIFIED
                        && plan.effectiveMetadata()
                        .requireTable("StringSelectorRow")
                        .stringResourceProfile().estimatedReachableBytes()
                        == 192L,
                "String profile is an immutable unverified estimate");
        check(plan.effectiveMetadata()
                        .requireTable("StringSelectorRow").planningRows() == 1
                        && plan.effectiveMetadata()
                        .requireTable("StringSelectorRow").maximumRows() == 2,
                "effective Plan exposes hint and hard row limit");
        check(plan.effectiveMetadata()
                        .requireTable("string_key_rows")
                        .primaryLocatorLayout()
                        == SomaPrimaryLocatorLayout.FLAT_COMPACT
                        && "soma-primary-locator-layout-v1".equals(
                        plan.effectiveMetadata()
                                .requireTable("string_key_rows")
                                .primaryLocatorLayoutFormulaIdentity())
                        && plan.effectiveMetadata()
                                .requireTable("StringSelectorRow")
                                .primaryLocatorLayout()
                                == SomaPrimaryLocatorLayout.NONE,
                "effective Plan exposes the validated flat locator formula");
        expectIllegalState(
                () -> editor.maximumRows(3),
                "child Plan editor closes with parent");

        StringSelectorRowTable bounded =
                StringSelectorRowTable.create(plan);
        bounded.addBatch(new StringSelectorRowBatch()
                .addValues(1, "Aa")
                .addValues(2, "BB"));
        long epoch = bounded.structuralEpoch();
        expectCode("row_limit_exceeded",
                () -> bounded.addBatch(
                        new StringSelectorRowBatch().addValues(3, "Cc")),
                "maximumRows preflight");
        check(bounded.size() == 2 && bounded.structuralEpoch() == epoch,
                "maximumRows rejection preserves rows and epoch");
        bounded.release();
    }

    private static void verifyDefaultsAndReferenceFields() {
        FullRowBatch batch = new FullRowBatch().addValues(row -> {
            row.setName("alpha");
            row.setPoint(new Point(1, 2));
            row.setScalar(new ScalarValue(10));
        });
        FullRowTable table = FullRowTable.create();
        table.reserve(96);
        check(table.size() == 0 && table.capacity() >= 96, "reserve");
        table.addBatch(batch);
        FullRow value = table.fetchAt(0);
        check(value.active && value.small == -7 && value.medium == 9
                        && value.priority == 7 && value.total == 1234567890123L
                        && Float.isNaN(value.payload)
                        && value.ratio == Double.POSITIVE_INFINITY
                        && Float.floatToRawIntBits(value.score)
                                == Float.floatToRawIntBits(-0.0f)
                        && "default".equals(value.label)
                        && value.state == State.READY,
                "writer scalar/String/enum defaults");
        check(value.day == (int) LocalDate.parse("2026-01-02").toEpochDay(),
                "semantic date default");
        check(value.time == LocalTime.parse("12:34:56.123456789").toNanoOfDay()
                        && value.instant == Instant.parse("2026-01-02T03:04:05Z")
                        .toEpochMilli(),
                "semantic time defaults");
        check("alpha".equals(value.name) && value.point.equals(new Point(1, 2))
                        && value.leafDefaults.equals(new LeafDefaults(3, "leaf"))
                        && value.scalar.equals(new ScalarValue(10)),
                "required String/value fields and recursive value defaults");
        check(value.note == null && value.optionalState == null
                        && value.optionalPoint == null && value.optionalScalar == null,
                "optional reference absent");

        FullRowMutator mutation = table.mutateAt(0).setNote("note")
                .setOptionalState(State.RUNNING).setOptionalPoint(new Point(8, 9))
                .setScalar(new ScalarValue(11))
                .setOptionalScalar(new ScalarValue(12));
        mutation.commit();
        value = table.fetchAt(0);
        check("note".equals(value.note) && value.optionalState == State.RUNNING
                        && value.optionalPoint.equals(new Point(8, 9))
                        && value.scalar.equals(new ScalarValue(11))
                        && value.optionalScalar.equals(new ScalarValue(12)),
                "optional String/enum/value and scalar-value mutation");
        UpdateResult updated = table.update(row -> {
            row.setScalar(new ScalarValue(20));
            row.setOptionalScalar(new ScalarValue(21));
        });
        check(updated.changed() == 1 && table.fetchAt(0).scalar.equals(new ScalarValue(20))
                        && table.fetchAt(0).optionalScalar.equals(new ScalarValue(21)),
                "scalar-value pipeline update");
        UpdateResult cleared = table.update(row -> row.clearOptionalScalar());
        check(cleared.changed() == 1 && table.fetchAt(0).optionalScalar == null,
                "optional scalar-value pipeline clear");
        TableStats operationStats = table.statsSnapshot();
        check(operationStats.operationScratchCurrentBytes() > 0L
                        && operationStats.operationScratchHighWaterBytes()
                        >= operationStats.operationScratchCurrentBytes(),
                "row-operation scratch stats");
        table.mutateAt(0).clearNote().clearOptionalState().clearOptionalPoint()
                .setScalar(new ScalarValue(22)).clearOptionalScalar().commit();
        value = table.fetchAt(0);
        check(value.note == null && value.optionalState == null
                        && value.optionalPoint == null && value.optionalScalar == null
                        && value.scalar.equals(new ScalarValue(22)),
                "optional String/enum/value clear and scalar-value materialization");
        expectNullPointer(() -> table.fetchAt(0, (MaterializationBudget) null),
                "null materialization budget");
        table.release();
        expectCode("table_released", table::size, "released table access");
    }

    private static void verifyPresenceWordBoundaries() {
        FullRowBatch batch = new FullRowBatch(65);
        for (int index = 0; index < 65; index++) {
            final int row = index;
            batch.addValues(writer -> {
                writer.setName("row-" + row);
                writer.setPoint(new Point(row, -row));
                writer.setLeafDefaults(new LeafDefaults(row, "leaf-" + row));
                writer.setScalar(new ScalarValue(row));
                if (row == 0) writer.setOptionalState(State.READY);
                if (row == 63) writer.setOptionalState(State.RUNNING);
                if (row == 64) writer.setOptionalState(State.DONE);
            });
        }
        FullRowTable table = FullRowTable.create();
        table.addBatch(batch);
        final State[] visited = new State[3];
        final int[] count = {0};
        table.optionalStateValues().forEach(value -> visited[count[0]++] = value);
        check(count[0] == 3 && visited[0] == State.READY
                        && visited[1] == State.RUNNING && visited[2] == State.DONE,
                "optional enum word traversal at rows 0/63/64");

        EnumColumnView<State> view = table.optionalStateColumn();
        try {
            check(view.isPresent(0) && !view.isPresent(1)
                            && view.isPresent(63) && view.isPresent(64),
                    "optional enum presence at 0/64/65 boundary");
            check(view.get(0) == State.READY && view.get(63) == State.RUNNING
                            && view.get(64) == State.DONE,
                    "optional enum view values");
            expectCode("optional_absent", () -> view.get(1),
                    "optional enum absent get");
        } finally {
            view.close();
        }
        table.release();
    }

    private static void verifyStringKeyAndBudgets() {
        StringKeyRowTable keyed = StringKeyRowTable.create();
        keyed.reserve(64);
        keyed.addBatch(new StringKeyRowBatch()
                .addValues("Aa", 1, false, null)
                .addValues("BB", 2, true, "present"));
        check(keyed.fetch("Aa").value == 1 && keyed.fetch("BB").value == 2,
                "String hash collision full equality");
        check(keyed.fetch("Aa").note == null
                        && "present".equals(keyed.fetch("BB").note),
                "String optional payload");
        check(!keyed.find("missing").isPresent(), "String missing find");
        expectCode("missing_key", () -> keyed.fetch("missing"),
                "String missing fetch");
        expectCode("invalid_null_value", () -> keyed.containsKey(null),
                "null String key");

        MaterializationBudget normal = MaterializationBudget.defaults();
        check(keyed.find("Aa", normal).get().value == 1,
                "explicit row materialization budget");
        List<String> keys = keyed.keys().fetchAll(normal);
        check(keys.size() == 2 && "Aa".equals(keys.get(0)) && "BB".equals(keys.get(1)),
                "explicit key fetchAll budget");
        check("Aa".equals(keyed.keys().findFirst(normal).get())
                        && "Aa".equals(keyed.keys().firstOrThrow(normal)),
                "explicit key first budget overloads");
        MaterializationBudget oneRow = MaterializationBudget.builder()
                .maximumRows(1).build();
        expectCode("materialization_budget_exceeded",
                () -> keyed.keys().fetchAll(oneRow), "key budget enforcement");

        TableStats keyStats = keyed.statsSnapshot();
        check("hash-composite-v2".equals(keyStats.keySpaceImplementation())
                        && keyStats.keySpaceCapacity() > 0
                        && keyStats.keySpaceUsed() >= keyed.size()
                        && keyStats.keySpaceProbeCount() > 0L
                        && keyStats.keySpaceCollisionCount() > 0L
                        && keyStats.keySpaceStorageCurrentBytes() > 0L
                        && keyStats.keySpaceStorageHighWaterBytes()
                        >= keyStats.keySpaceStorageCurrentBytes(),
                "String KeySpace capacity/probe/collision stats");
        int keyCapacity = keyStats.keySpaceCapacity();
        int keyUsed = keyStats.keySpaceUsed();
        keyed.resetStats();
        TableStats resetKeyStats = keyed.statsSnapshot();
        check(resetKeyStats.keySpaceCapacity() == keyCapacity
                        && resetKeyStats.keySpaceUsed() == keyUsed
                        && resetKeyStats.keySpaceProbeCount() == 0L
                        && resetKeyStats.keySpaceCollisionCount() == 0L
                        && resetKeyStats.keySpaceRehashCount() == 0L,
                "KeySpace metric reset preserves locator state");
        check(keyed.containsKey("BB"), "KeySpace probe before staged append");
        long probesBeforeAppend = keyed.statsSnapshot().keySpaceProbeCount();
        keyed.addBatch(new StringKeyRowBatch()
                .addValues("Cc", 3, false, null));
        check(keyed.statsSnapshot().keySpaceProbeCount() > probesBeforeAppend,
                "singleton validation preserves since-reset KeySpace metrics");

        int beforeDuplicate = keyed.size();
        expectCode("duplicate_key",
                () -> keyed.addBatch(new StringKeyRowBatch()
                        .addValues("Aa", 99, false, null)),
                "duplicate String key");
        check(keyed.size() == beforeDuplicate && keyed.fetch("Aa").value == 1,
                "duplicate key atomicity");
        expectCode("duplicate_key",
                () -> keyed.addBatch(new StringKeyRowBatch()
                        .addValues("within-batch", 10, false, null)
                        .addValues("within-batch", 11, false, null)),
                "batch-internal duplicate String key");
        check(keyed.size() == beforeDuplicate,
                "batch-internal duplicate String append is atomic");
        keyed.delete("Aa");
        check(!keyed.containsKey("Aa") && keyed.containsKey("BB")
                        && keyed.containsKey("Cc") && keyed.fetch("BB").value == 2,
                "String key compaction");
        keyed.release();
    }

    private static void verifyStringKeyedChild() {
        StringParent parent = new StringParent();
        parent.id = 1;
        parent.children = new HashMap<String, StringKeyRow>();
        StringKeyRow child = new StringKeyRow();
        child.id = "child";
        child.value = 11;
        child.note = null;
        parent.children.put("child", child);

        StringParentTable parents = StringParentTable.create();
        parents.addBatch(new StringParentBatch().add(parent));
        check(parents.children(0).fetch("child").value == 11,
                "String keyed child live access");
        Map<String, StringKeyRow> materialized = parents
                .materialize(MaterializationBudget.defaults()).get(0).children;
        check(materialized.size() == 1 && materialized.get("child").value == 11,
                "String keyed child materialization");
        parents.release();
    }

    private static void verifyStringSelectorsAndMetadata() {
        StringSelectorRowTable table = StringSelectorRowTable.create();
        table.addBatch(new StringSelectorRowBatch()
                .addValues(1, "Aa")
                .addValues(2, "BB"));
        check(table.scanByLabel("Aa").count() == 1L
                        && table.scanByLabel("BB").count() == 1L,
                "String index hash collision full equality");
        check(table.fetchByUniqueLabel("Aa").id == 1
                        && table.fetchByUniqueLabel("BB").id == 2
                        && table.containsByUniqueLabel("Aa"),
                "String unique point access");
        expectCode("unique_constraint_violation",
                () -> table.addBatch(
                        new StringSelectorRowBatch().addValues(3, "Aa")),
                "String unique duplicate");
        check(table.size() == 2
                        && table.fetchByUniqueLabel("Aa").id == 1,
                "String unique failure atomicity");
        check(table.statsSnapshot().exactIndexCount() == 2
                        && table.statsSnapshot().exactIndexCollisionCount() > 0L,
                "String index/unique collision accounting");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            StringSelectorRowDataFlow.Source source =
                    StringSelectorRowDataFlow.source("strings");
            ParameterSlot<String> selected =
                    ParameterSlot.of(0, "selectedLabel", String.class);
            StringColumnResult projected = source.candidates()
                    .filter(source.columns().label()
                            .equalTo(source.stringParameter(selected)))
                    .project(source.columns().label())
                    .toColumn()
                    .compile()
                    .newInvocation(context)
                    .bind(source, StringSelectorRowDataFlow.bind(table))
                    .parameter(selected, "BB")
                    .execute();
            check(projected.size() == 1
                            && "BB".equals(projected.valueAt(0)),
                    "String expression/parameter/projection");
            GroupedLongResult counts = source.candidates()
                    .groupBy(KeyExpression.of(source.columns().label()))
                    .counts()
                    .compile()
                    .newInvocation(context)
                    .bind(source, StringSelectorRowDataFlow.bind(table))
                    .execute();
            check(counts.size() == 2
                            && counts.valueAt(0) == 1L
                            && counts.valueAt(1) == 1L,
                    "String Group key");
            StringSelectorRowDataFlow.Source left =
                    StringSelectorRowDataFlow.source(0, "leftStrings");
            StringSelectorRowDataFlow.Source right =
                    StringSelectorRowDataFlow.source(1, "rightStrings");
            LongScalarResult joined = left.candidates()
                    .innerJoin(right.candidates())
                    .on(left.columns().label(), right.columns().label())
                    .count()
                    .compile()
                    .newInvocation(context)
                    .bind(left, StringSelectorRowDataFlow.bind(table))
                    .bind(right, StringSelectorRowDataFlow.bind(table))
                    .execute();
            check(joined.value() == 2L, "String Join key");
        } finally {
            context.close();
        }

        SomaTableMetadata descriptor =
                SchemaMetadata.schema().requireTable("StringSelectorRow");
        SomaColumnMetadata label = descriptor.requireColumn("label");
        check(StringSelectorRowTable.metadata() == descriptor
                        && label.type().kind()
                        == SomaTypeKind.REFERENCE_BACKED_IMMUTABLE_SCALAR
                        && "java.lang.String".equals(label.type().storageType())
                        && descriptor.requireIndex("by_label")
                        .leafPaths().get(0).equals("label")
                        && descriptor.requireUnique("unique_label")
                        .leafPaths().get(0).equals("label"),
                "generated String descriptor projection");
        SomaColumnMetadata valueLeaf = SchemaMetadata.schema()
                .requireTable("full_rows")
                .requireColumn("leafDefaults.count");
        check(valueLeaf.type().kind()
                        == SomaTypeKind.COMPILER_FLATTENED_VALUE
                        && valueLeaf.hasDefault()
                        && "3".equals(valueLeaf.normalizedDefault()),
                "flattened value descriptor/default projection");
        check(SchemaMetadata.schema().requireTable("string_parents")
                        .requireOwnership("children").type().kind()
                        == SomaTypeKind.OWNED_STRUCTURED_STATE,
                "owned structured-state descriptor projection");
        boolean immutable = false;
        try {
            SchemaMetadata.schema().tables().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "descriptor collections are immutable");
        table.release();
    }

    private static void verifyStringReferenceLifecycle() {
        String originalLabel = new String("lifecycle-label");
        String equalLabel = new String("lifecycle-label");
        StringSelectorRowTable selectors = StringSelectorRowTable.create();
        selectors.addBatch(new StringSelectorRowBatch()
                .addValues(1, originalLabel));
        check(selectors.fetchAt(0).label == originalLabel,
                "String append preserves the caller reference");
        assertGeneratedTableRetains(selectors, originalLabel, true);

        long beforeNoOpEpoch = selectors.structuralEpoch();
        selectors.mutateAt(0).setLabel(equalLabel).commit();
        check(selectors.fetchAt(0).label == originalLabel
                        && selectors.structuralEpoch() == beforeNoOpEpoch
                        && selectors.statsSnapshot().lastChanged() == 0L,
                "equal-value different-object String mutation is a no-op");
        assertGeneratedTableRetains(selectors, equalLabel, false);

        String replacementLabel = new String("replacement-label");
        selectors.replaceAll(new StringSelectorRowBatch()
                .addValues(2, replacementLabel));
        check(selectors.fetchByUniqueLabel("replacement-label").label
                        == replacementLabel,
                "String replacement publishes the caller reference");
        assertGeneratedTableRetains(selectors, originalLabel, false);
        assertGeneratedTableRetains(selectors, replacementLabel, true);

        String failedDuplicate = new String("replacement-label");
        expectCode("unique_constraint_violation",
                () -> selectors.addBatch(new StringSelectorRowBatch()
                        .addValues(3, failedDuplicate)),
                "failed String append does not publish a reference");
        assertGeneratedTableRetains(selectors, failedDuplicate, false);

        selectors.deleteByUniqueLabel(new String("replacement-label"));
        assertGeneratedTableRetains(selectors, replacementLabel, false);

        String clearedLabel = new String("clear-label");
        selectors.addBatch(new StringSelectorRowBatch()
                .addValues(4, clearedLabel));
        selectors.clear();
        assertGeneratedTableRetains(selectors, clearedLabel, false);

        String releasedLabel = new String("release-label");
        selectors.addBatch(new StringSelectorRowBatch()
                .addValues(5, releasedLabel));
        selectors.release();
        assertGeneratedTableRetains(selectors, releasedLabel, false);

        String keyReference = new String("key-reference");
        String noteReference = new String("note-reference");
        StringKeyRowTable keyed = StringKeyRowTable.create();
        keyed.addBatch(new StringKeyRowBatch()
                .addValues(keyReference, 1, true, noteReference));
        StringKeyRow materialized = keyed.fetch(keyReference);
        check(materialized.id == keyReference
                        && materialized.note == noteReference,
                "String Key/payload columns preserve caller references");
        assertGeneratedTableRetains(keyed, keyReference, true);
        assertGeneratedTableRetains(keyed, noteReference, true);
        keyed.delete(new String("key-reference"));
        assertGeneratedTableRetains(keyed, keyReference, false);
        assertGeneratedTableRetains(keyed, noteReference, false);

        String replacedKey = new String("replaced-key");
        String replacedNote = new String("replaced-note");
        keyed.addBatch(new StringKeyRowBatch()
                .addValues(replacedKey, 2, true, replacedNote));
        String nextKey = new String("next-key");
        keyed.replaceAll(new StringKeyRowBatch()
                .addValues(nextKey, 3, false, null));
        assertGeneratedTableRetains(keyed, replacedKey, false);
        assertGeneratedTableRetains(keyed, replacedNote, false);
        assertGeneratedTableRetains(keyed, nextKey, true);
        keyed.release();
        assertGeneratedTableRetains(keyed, nextKey, false);
    }

    private static void verifyFailedUpdateDoesNotRetainReferences() {
        FullRowBatch batch = new FullRowBatch();
        for (int index = 0; index < 2; index++) {
            final int value = index;
            batch.addValues(row -> {
                row.setName("original-" + value);
                row.setPoint(new Point(value, value + 1));
                row.setScalar(new ScalarValue(value));
            });
        }
        FullRowTable table = FullRowTable.create();
        table.addBatch(batch);
        final String sentinel = new String("failed-update-retained-reference-sentinel");
        expectCode("callback_failed", () -> table.update(row -> {
            row.setName(sentinel);
            throw new IllegalStateException("intentional update failure");
        }), "failed update callback");
        check("original-0".equals(table.fetchAt(0).name)
                        && "original-1".equals(table.fetchAt(1).name),
                "failed update must not publish detached references");
        assertGeneratedTableRetains(table, sentinel, false);
        table.release();
    }

    private static void assertGeneratedTableRetains(
            Object table, Object sentinel, boolean expected) {
        try {
            boolean retained = false;
            for (Field field : table.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(table);
                if (containsIdentity(value, sentinel)) {
                    retained = true;
                    break;
                }
                if (value != null
                        && value.getClass().getName().equals(
                        "io.github.somaruntime.soma.runtime.generated.StringColumn")) {
                    for (Field storage : value.getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(storage.getModifiers())) continue;
                        storage.setAccessible(true);
                        if (containsIdentity(storage.get(value), sentinel)) {
                            retained = true;
                            break;
                        }
                    }
                }
                if (retained) {
                    break;
                }
            }
            check(retained == expected,
                    expected
                            ? "expected generated String storage to retain sentinel"
                            : "generated String storage retained dead sentinel");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("retained-reference oracle could not inspect storage", failure);
        }
    }

    private static boolean containsIdentity(Object value, Object sentinel) {
        if (value == sentinel) return true;
        if (!(value instanceof Object[])) return false;
        for (Object element : (Object[]) value) {
            if (containsIdentity(element, sentinel)) return true;
        }
        return false;
    }

    private static void expectCode(String code, Action action, String message) {
        try {
            action.run();
            throw new AssertionError(message + ": expected " + code);
        } catch (SomaRuntimeException expected) {
            check(code.equals(expected.code()), message + ": actual=" + expected.code());
        }
    }

    private static void expectNullPointer(Action action, String message) {
        try {
            action.run();
            throw new AssertionError(message + ": expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected public null-contract failure.
        }
    }

    private static void expectIllegalState(Action action, String message) {
        try {
            action.run();
            throw new AssertionError(
                    message + ": expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected one-shot builder/editor contract.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action {
        void run();
    }
}

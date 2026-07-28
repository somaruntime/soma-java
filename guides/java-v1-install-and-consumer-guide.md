# SOMA Java V1 安装与 Maven consumer 指南

类型：Report / 用户指南

状态：当前

Owner：SOMA Java 用户输出

受众：准备在 Java 8 Maven 项目中试用 SOMA 的开发者

事实范围：当前本地安装、consumer build、建模提示、升级边界和已验证能力限制

非事实范围：重新定义 public/schema/runtime Design 或声明 public release readiness

适用版本：当前仓库 `0.2.0-SNAPSHOT`，最后 implementation-affecting baseline `2aa8c15`

输入事实源：[正式文档入口](../docs/README.md)、当前 `pom.xml`、external Maven fixtures 与 Gate reports

最后审查日期：2026-07-27

本指南说明 Java 8 Maven consumer 如何使用 SOMA annotations、compiler
transformer、annotation processor、runtime-core 和 typed DataFlow。当前项目
只以 Azul Zulu JDK 8 作为 compiler/runtime 验真与目标支持 distribution；
Corretto 和其他 JDK distribution 均为 untested/unsupported。精确 Zulu
version/build、OS 与 architecture 边界仍只能引用 G6 compatibility matrix。

当前代码已通过 G0–G5，但 G6 仍 blocked，artifact 也仍是本地 snapshot。个人项目试用应先在本仓库执行 `./mvnw -B -ntp install`，不得把它描述为 public RC、production-ready 或已发布 artifact。

## 1. 前置条件

- Azul Zulu 完整 JDK 8，必须同时包含 `java`、`javac` 和 JDK compiler APIs；
- Maven 3.8.6–3.x；
- UTF-8 source encoding；
- SOMA 四个同版本 artifact：`soma-annotations`、`soma-processor`、
  `soma-runtime-core`、`soma-dataflow`。

不能用新 JDK 的 `--release 8` 代替 Zulu full JDK 8 compiler。Corretto、其他 JDK distribution、ECJ、JDK 9+ javac 或未进入正式矩阵的 IDE incremental compiler 不能被视为支持环境。

## 2. Maven 配置

当前本地试用使用 `0.2.0-SNAPSHOT`；未来正式发布后再把 `${soma.version}` 替换为对应 release version。`soma-processor` 只属于 build path，不应进入 application runtime graph。

```xml
<properties>
  <maven.compiler.source>1.8</maven.compiler.source>
  <maven.compiler.target>1.8</maven.compiler.target>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <soma.version>0.2.0-SNAPSHOT</soma.version>
</properties>

<dependencies>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-annotations</artifactId>
    <version>${soma.version}</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-runtime-core</artifactId>
    <version>${soma.version}</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-dataflow</artifactId>
    <version>${soma.version}</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-processor</artifactId>
    <version>${soma.version}</version>
    <scope>provided</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.11.0</version>
      <configuration>
        <source>1.8</source>
        <target>1.8</target>
        <encoding>UTF-8</encoding>
        <compilerArgs>
          <arg>-Xplugin:SomaValue</arg>
        </compilerArgs>
        <annotationProcessorPaths>
          <path>
            <groupId>com.hgtech.soma</groupId>
            <artifactId>soma-processor</artifactId>
            <version>${soma.version}</version>
          </path>
          <path>
            <groupId>com.hgtech.soma</groupId>
            <artifactId>soma-annotations</artifactId>
            <version>${soma.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## 3. Schema 与 generated API

1. 在 `package-info.java` 声明 `@SomaSchema`；
2. 使用 `@SomaTable`、`@SomaValue`、`@SomaField`、`@SomaKey`、`@SomaIndex`、`@SomaUnique`、`@SomaChild` 等定义 logical schema；`@SomaIndex` 提供 exact-group `scanByX`，`@SomaUnique` 优先提供 0..1 point family，业务顺序在 Candidate Scan 上显式调用 `sorted(totalComparator)`；
3. Maven compile 同时激活 `SomaValue` javac plugin 与 annotation processor；
4. application 依赖 generated typed API、`soma-runtime-core` 和
   `soma-dataflow`，不直接访问 generated-sources directory、logical IR 或
   runtime internal type；
5. 所有同一应用中的 generated source、runtime-core、runtime plan 和 schema hash必须通过初始化兼容性检查。

三个正式参考应用位于 `soma-examples` 的独立 child projects；独立于 reactor
parent 的完整 core consumer fixture 位于
`tests/fixtures/external-maven-breadth`。

## 4. 建模顺序与关键语义

1. 先区分 input facts、working state 和 result facts；
2. 有稳定业务 identity 的 row 使用 `@SomaKey` keyed table；只依赖 packed traversal/current Index 的 row 使用 dense table；
3. 只为稳定 exact access 声明 `@SomaIndex`/`@SomaUnique`；
4. parent 独占且同生命周期的数据使用 `@SomaChild`；
5. 可变业务顺序在调用处显式 `sorted(totalComparator)`，跨轮次队列使用 application-owned heap；
6. hot loop 按 Point/Candidate/Column/Key/Bulk/Ownership 选择自然路径；
   Candidate Scan、ColumnTraversal 或 ColumnView 负责局部访问；
7. 需要 Selection、Projection、Aggregation、Partition、Join、GroupBy、
   Window、detached Result 或 safe-point Effect 时，使用生成的
   `<Table>DataFlow` 定义一次或可复用的 typed transformation，不把 generic
   object graph、Java Stream 或 I/O 放进执行热路径。

Keyed identity 稳定，但 current Index 不稳定。Keyed/dense 删除都使用
swap-remove，未排序的 first/limit/indexSnapshot/fetchAll 只基于当时 source
sequence。`findIndex/requireIndex` 不物化 carrier；`IndexSnapshot` 显式复制并
只在紧接着的同步只读批次消费；跨 operation 保存引用必须使用 `@SomaKey`。
SOMA 不提供跨 table transaction，业务提交与恢复由 application 负责。
DataFlow 的 Definition/Template 不持有 live Table；每次 Invocation one-shot，
并行执行只在 Invocation 独占的只读/受控 Effect 边界内发生。外部状态同步先形成
detached Batch/Delta，再在 application safe point 提交。

## 5. Metadata、RuntimePlan 与 SomaGroup

SOMA 把 Metadata control plane 与 live payload 分开。Generated
`SchemaMetadata` 是 schema 入口：

- `metadata()` 返回完整 `SomaMetadata`；
- `schema()` 返回 immutable Descriptor Metadata；
- `newPlan()` 返回 create 前可修改、one-shot 的 RuntimePlan Builder；
- `build()` 形成 immutable Effective Metadata，Table 创建后不再解释 Builder。

```java
RuntimePlan.Builder builder = SchemaMetadata.newPlan();
builder.maximumAggregateStorageBytes(2L * 1024L * 1024L * 1024L);
builder.table(NumericFactTable.metadata())
        .initialCapacity(4096)
        .planningRows(1_000_000)
        .maximumRows(10_000_000)
        .maximumTableStorageBytes(1024L * 1024L * 1024L);

RuntimePlan plan = builder.build();
NumericFactTable table = NumericFactTable.create(plan);
```

`planningRows` 是物理选择 hint，超过它不失败；`maximumRows` 是 hard limit，
跨越前以 structured resource failure 拒绝。Builder 或 child editor 在
`build()` 成功或失败后都关闭，不能复用。

互相关联、需要共同资源和 release owner 的多个 root 使用显式 `SomaGroup`。
Group member slot 是 plan-time composition，不是动态 registry：

```java
RuntimePlan plan = SchemaMetadata.newPlan().build();
SomaGroupPlan groupPlan = SomaGroupPlan.builder("calculation-42")
        .member("left", SchemaMetadata.metadata(),
                NumericFactTable.metadata(), plan)
        .member("right", SchemaMetadata.metadata(),
                NumericFactTable.metadata(), plan)
        .build();
SomaGroup group = SomaGroup.create(groupPlan);
NumericFactTable left = NumericFactTable.attach(group, "left");
NumericFactTable right = NumericFactTable.attach(group, "right");
try {
    // load and compute
} finally {
    group.release();
}
```

同一个 Table descriptor 可以出现在多个 member slot；每个 attached root 仍有独立
aggregate identity。Group 统一 composition、resource ledger、metadata snapshot
和 reverse-order release，但不提供跨 Table transaction 或 snapshot isolation。
简单 `Table.create(plan)` 等价于只有一个 root 的 implicit Group。

## 6. V1 类型与 String 资源边界

V1 schema field 只接受四类语义：

1. primitive-backed scalar：primitive、enum、date/time 和显式 semantic scalar；
2. reference-backed immutable scalar：V1 白名单仅 `String`；
3. compiler-flattened value：`@SomaValue` 在编译期展开为 leaf columns；
4. owned structured state：`@SomaChild` 的 parent-owned child Table。

普通 Java object、`List`、`Map` 或任意 object graph 不能成为 live schema
field。需要关联 application object 时，在 SOMA 保存稳定 ID，在 application
sidecar/registry 保存对象。

String column 保存 caller 提供的 reference，不复制、不 intern、不 normalize。
Key/Unique/Index/Group/Join 使用完整 value equality/hash/order；required String
拒绝 `null`，optional absence 与 empty String 不同。Equal-value、
different-object mutation 是 no-op。`remove`、`clear`、`replace`、rollback 和
`release` 必须清除 dead reference。

进入 String scale qualification 前，应在 plan 中声明 profile：

```java
StringResourceProfile strings = StringResourceProfile.builder()
        .averageUtf16CodeUnits(16)
        .maximumUtf16CodeUnits(32)
        .valueCardinality(1024)
        .distinctObjectIdentityEstimate(1024)
        .intraTableSharingBasisPoints(9900)
        .interTableSharingBasisPoints(0)
        .presenceBasisPoints(10000)
        .role(StringResourceRole.PAYLOAD)
        .role(StringResourceRole.GROUP)
        .simultaneouslyLiveTableCount(1)
        .build();

RuntimePlan.Builder builder = SchemaMetadata.newPlan();
builder.table(ScaleStringFactTable.metadata())
        .stringResourceProfile(strings);
RuntimePlan plan = builder.build();
```

Profile 是 caller-declared、`PROFILED_UNVERIFIED` 的可达内存估算，不是 runtime
读取 String internals 得到的 hard cap。任何 String 规模结论都必须同时声明
UTF-16 长度、value cardinality、distinct object identity、共享率、presence、
字段角色和同时存活 Table 数，并分别报告：

- SOMA-owned structural bytes；
- SOMA-retained reachable String model bytes；
- JVM observed heap/GC。

## 7. DataFlow、Result Delivery 与并行

先创建 typed Source 和可复用 Template，再为每次执行创建 one-shot Invocation：

```java
NumericFactDataFlow.Source facts =
        NumericFactDataFlow.source("facts");
DataFlowTemplate<LongScalarResult> total =
        facts.candidates()
                .filter(facts.columns().factIndex().lessThan(1000L))
                .project(facts.columns().entityId())
                .sum()
                .compile();

DataFlowContext context = DataFlowContext.sequential();
try {
    DataFlowInvocation<LongScalarResult> invocation =
            total.newInvocation(context)
                    .bind(facts, NumericFactDataFlow.bind(table));
    long value = invocation.execute().value();
    DataFlowExplain explain = total.explain();
    DataFlowStats stats = invocation.stats();
} finally {
    context.close();
}
```

Eager Detached 是默认 delivery：结果完整计算、通过预算并在 source
guard/scratch 清理后一次发布。高展开结果必须给出可证明上界和
`ExecutionBudget`；无法证明或超过预算时，会在 relation enumeration、output
allocation 或 callback 前拒绝。

callback-scoped streaming 是唯一惰性试点，适合 bounded visit 或 early stop：

```java
DataFlowContext deliveryContext = DataFlowContext.sequential();
try {
    CallbackDeliveryTemplate<NumericFactScan.Visitor> delivery =
            facts.deliver(facts.candidates().limit(1000L)).compile();
    CallbackDeliveryInvocation<NumericFactScan.Visitor> invocation =
            delivery.newInvocation(deliveryContext)
                    .bind(facts, NumericFactDataFlow.bind(table))
                    .visitor(new NumericFactScan.Visitor() {
                        @Override
                        public boolean visit(NumericFactCursor row) {
                            consume(row.entityId());
                            return true; // false: consume current row, then stop
                        }
                    });
    DeliveryResult result = invocation.execute();
    DataFlowStats stats = invocation.stats();
} finally {
    deliveryContext.close();
}
```

Cursor 只在 callback 内有效，不得保存或传给其他线程；String getter 返回不可变
value，可以由 application 保留。Visitor exception、cancel、deadline、source
conflict 或 cleanup failure 不返回 partial `DeliveryResult`。普通
`Iterator<T>`、closeable pull cursor、Generator、Publisher 和 async push
不属于 V1。

Adaptive Parallel 仍使用同一 logical semantics：

```java
ExecutionPolicy policy = ExecutionPolicy.adaptiveParallel()
        .withMinimumParallelCardinality(32_768)
        .withStatsMode(com.hgtech.soma.dataflow.StatsMode.DETAILED);
DataFlowContext context = DataFlowContext.managedParallel(
        8, policy, ExecutionBudget.defaults());
```

Storage Segment、Parallel Morsel 和 Execution Vector 是三个不同粒度。一个
Segment 也可以拆成多个 morsel；scheduler 根据 rows、operator、touched width、
scratch 和 worker budget 决定 sequential/parallel，不要求 application 选择第二套
并行 API。Opaque callback 始终 sequential。

`DataFlowExplain` 公开 logical/physical shape、formula identity、parallel decision
和 fallback reason；`DataFlowStats` 分为 work、parallel、resources、delivery
四个组件。Stats/Explain 是 detached diagnostics，不是业务事实。

## 8. Mutation、失败与生命周期

- Batch/Delta 是 detached staging；commit 前完成 target、duplicate、capacity、
  Unique/Index/child 和 resource preflight；
- pure small Delta 使用 changed-row specialization，较大或不适用形状走明确
  fallback；失败不发布部分 mutation；
- callback 中禁止嵌套访问同一 aggregate、结构修改或让 Cursor 逃逸；
- Table/Group release 是 terminal；`clear()` 保留 plan 和已准入 capacity，
  `release()` 后普通访问失败；
- `SomaRuntimeException` 的 category、code、operation、path 和 context 是稳定诊断
  envelope；不要解析 message 文本；
- 原始 JVM fatal allocation failure 不能当作可恢复业务失败。调用者应先设置
  RuntimePlan/ExecutionBudget 并保留 JVM/GC headroom。

建议应用处理流程是：

```text
schema + Metadata
  -> freeze RuntimePlan / SomaGroupPlan
  -> create + bounded load
  -> define/compile reusable DataFlow
  -> one-shot Invocation
  -> eager detached or callback-scoped delivery
  -> inspect stats / apply application commit
  -> clear or release at explicit lifecycle boundary
```

## 9. 规模使用边界

SOMA 的规模能力按 profile 判断，不按 row count 单独判断。Small/Medium 要优先避免
固定调用、对象和 plan tax；Large/100M 要优先控制 retained bytes、完整数据遍数、
scratch/output peak、memory bandwidth 和 GC。

“100M 支持”只可用于通过 production qualification 的窄 schema/workload：

- 单表 100M 与两个同时驻留 root 各 100M 必须分别验证；
- 声明 schema bytes/row、locator/exact access、touched columns、selectivity、
  skew、multiplicity、output bound、heap/GC、workers 和 timeout；
- String 另带完整 profile 和三层 memory accounting；
- arbitrary wide schema、high-cardinality String key、无界 N:M expansion、
  global materialization/sort/window 都不能从窄 profile 外推；
- 不可行 profile 应被 resource admission 确定性拒绝，而不是尝试到 OOM。

Application 仍负责 working-set projection、数据分片/驱逐、双缓存、外部一致性和
整个 JVM 的总预算；SOMA 不是数据库、持久化层或分布式执行系统。

## 10. 验证安装

Repository contributor 使用：

```text
./mvnw -B -ntp verify
./scripts/check.sh
```

External consumer 至少确认：

- generated source实际产生并编译；
- application classfile major version为52；
- `soma-processor`不在runtime dependency tree；
- schema hash、runtime plan identity与runtime stats可读取；
- duplicate/missing/stale/released/view-pinned/materialization-budget错误可观察。

## 11. Upgrade、rollback 与 withdrawal

- annotation、processor、runtime-core 和 dataflow 必须使用同一 artifact family
  version；
- schema、generated/runtime protocol或runtime plan identity不匹配时必须fail closed；
- released artifact immutable，不覆盖同version binary；
- 回退时同时回退四个 artifact 并重新生成 consumer generated source，不能把
  旧 generated class 与新 runtime/dataflow 混用；
- 如果某version被标记withdrawn，停止新部署并迁移到公告指定的修复版本；
- SOMA V1不提供持久化schema migration，detached object/wire/database迁移由application adapter拥有。

## 12. Known limitations

- Java-only；不提供Python、C ABI、native runtime或跨语言FFI；
- 只支持正式G6 matrix列出的Azul Zulu full JDK 8 javac/runtime组合；
- runtime 不是跨 table transaction、ORM、ECS、SQL/query engine 或
  persistence layer；
- DataFlow 只处理有限、typed、heap-resident 的 transformation；不提供无限流、
  自动增量视图、full/cross/theta join、隐式 common pool 或 blocking I/O；
- callback 的业务副作用和外部一致性由 application 拥有；SOMA 只保证同步
  invocation lifecycle、取消/期限检查与资源清理；
- benchmark smoke只证明路径和结构化证据可运行，不代表性能优势；
- public publishing endpoint、SCM/contact或签名机制未进入正式G6 report前，不得把本地artifact当作公开release。

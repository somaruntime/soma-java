![SOMA Java](assets/soma-banner.png)

# SOMA Java

**SOMA（Scheme-Oriented Memory Architecture）**

> **Schema-Defined, High-Performance Runtime-State Computing for Java.**

SOMA 是面向 Java 8 的进程内编译式 Table 引擎。它把稳定的运行时状态 Scheme 编译为类型安全的
generated API、紧凑的列式存储布局和专用执行计划，让 application 使用自然 Java object 管理
大规模、频繁变化的计算状态。

SOMA 适用于 schema-defined、状态变化频繁、需要高性能计算，并且对延迟和内存敏感的运行时计算场景，
例如调度与组合优化、仿真和实时派工。

```text
Schema declaration
    -> generated Soma / Group / Table / Field / Index API
        -> Canonical Logical IR and optimizer
            -> specialized execution
                -> chunked columnar runtime state
```

## 为什么是 Scheme-Oriented

在 SOMA 中，Scheme 是 application 运行时状态的稳定组织方式：

```text
Runtime-state Scheme
    = Table structure
    + Field and Value shape
    + Key and Index access paths
    + legal computation relationships
```

Application 声明 Scheme，annotation processor 在编译期获得完整结构信息，并生成与之对应的 Java
类型和能力。Runtime 因而不需要把所有操作退化成反射、通用 `Object` 容器或统一 boxed executor；
它可以在保持 Java API 类型安全的同时，使用面向数据的存储和专用执行路径。

## 产品定位

SOMA 不试图替代 Java Stream、数据库或 ECS。它专注的是同一 JVM 中、schema 已知且频繁变化的
authoritative runtime state。

| 体系 | 主要职责 | 执行与状态边界 | 类型与结构 | 典型计算方式 |
|---|---|---|---|---|
| Java Collection / Stream | 对 application collection 进行通用计算 | 同进程；状态由 application object/collection 拥有 | generic type；不规定物理布局 | iterator、pipeline、collector |
| In-memory Database | 内存中的查询、事务或 Key/Value 服务 | embedded 或独立服务；数据库拥有数据 | runtime schema、SQL 或 Key/Value model | query engine、transaction、network/client API |
| ECS | 组织 Entity 与 Component，并驱动 System | 通常同进程；application/game runtime 拥有生命周期 | component-oriented layout | component scan 与 system update |
| ClickHouse 等列式分析数据库 | 大规模持久化分析与聚合 | 通常独立数据库进程；数据库拥有持久化数据 | database schema 与 SQL | 批量写入、列式 scan、distributed analytics |
| **SOMA** | 管理算法运行时的可变 Table 状态并执行关系计算 | 同 JVM、无 IPC；`SomaGroup` 拥有状态 | compiler-known Scheme 与 generated exact API | Key/Index、typed pipeline、GroupBy、Join、controlled mutation |

SOMA 借鉴 Java Stream 的 source/intermediate/terminal、lazy pipeline、familiar naming 和显式并行，
但它的 source 是 SOMA 管理的 Table、Field、IndexSelection 和 Relation。Table 还拥有 Key、Index、
point mutation、Selection mutation、GroupBy、Join、资源准入和 structured failure。

## 核心产品模型

普通 application 只需要理解以下层级：

```text
Soma
    -> SomaGroup
        -> Table
            -> Field
                -> nested Field
```

- `Soma`：一个 compiled composition 的配置和默认入口；
- `SomaGroup`：一组相互隔离、可以参与关系计算的 Table 状态；
- `Table`：authoritative mutable state，以及 Key、Index 和 Table-local mutation 的 Owner；
- `Field`：类型安全的 logical value source，不向用户暴露 physical Column；
- generated pipeline：从 reusable source 形成的 lazy、finite、one-shot computation；
- terminal：返回 detached result，或者原子发布一次 Table-local state change。

Table、Field 和 IndexSelection 本身就是 source，不需要先调用 `stream()`。Sequential 是默认模式；
只有显式调用 `parallel()` 才进入 SOMA 的受控并行路径。

## 10-Minute Quick Start

当前要求 Java 8 JDK 和 Maven 3.9.x。仓库尚未发布到 Maven Central，因此先从源码安装两项
production artifact：

```sh
git clone https://github.com/somaruntime/soma-java.git
cd soma-java
mvn clean install -Dmaven.install.skip=false -DskipTests
```

### 1. 配置 consumer project

在 consumer `pom.xml` 中加入 runtime 和 annotation processor：

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <soma.version>1.0.0-SNAPSHOT</soma.version>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.somaruntime.soma</groupId>
        <artifactId>soma-runtime</artifactId>
        <version>${soma.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <release>8</release>
                <source>8</source>
                <target>8</target>
                <encoding>${project.build.sourceEncoding}</encoding>
                <compilerArgs>
                    <arg>-Asoma.fullSourceSet=true</arg>
                </compilerArgs>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.somaruntime.soma</groupId>
                        <artifactId>soma-processor</artifactId>
                        <version>${soma.version}</version>
                    </path>
                    <path>
                        <groupId>io.github.somaruntime.soma</groupId>
                        <artifactId>soma-runtime</artifactId>
                        <version>${soma.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 2. 声明一个 Scheme

一个 package-level `@SomaSchema` 定义一个 composition。创建
`src/main/java/example/jobs/schema/package-info.java`：

```java
@io.github.somaruntime.soma.SomaSchema
package example.jobs.schema;
```

在同一个 package 中创建两张 Table。`Job.java`：

```java
package example.jobs.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4096)
final class Job {
    @SomaKey long jobId;
    @SomaIndex long machineId;
    @SomaField int status;
    @SomaField long durationMinutes;
}
```

`Machine.java`：

```java
package example.jobs.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 256)
final class Machine {
    @SomaKey long machineId;
    @SomaIndex String zone;
    @SomaField long availableMinute;
    @SomaField long workloadMinutes;
    @SomaField boolean enabled;
}
```

Processor 只收集该 schema package 直接包含的 Table，并在父 package `example.jobs` 中生成：

```text
Soma / SomaGroup
Job / JobTable
Machine / MachineTable
JobTable.View / JobTable.Editor
MachineTable.View / MachineTable.Editor
typed Field, Index, GroupBy and Join endpoints
```

Schema declaration type 不进入 application API。

### 3. 使用 generated API

创建 `src/main/java/example/jobs/JobsMain.java`：

```java
package example.jobs;

import io.github.somaruntime.soma.LongGroupedLongResult;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.UpdateResult;
import java.util.List;

public final class JobsMain {
    private static final int PENDING = 0;
    private static final int SCHEDULED = 1;

    private JobsMain() {}

    public static void main(String[] args) {
        SomaGroup group = Soma.createGroup();
        JobTable jobs = group.jobTable();
        MachineTable machines = group.machineTable();

        jobs.add(new Job(101L, 7L, PENDING, 30L));
        jobs.add(new Job(102L, 7L, PENDING, 20L));
        jobs.add(new Job(103L, 9L, SCHEDULED, 45L));
        jobs.add(new Job(104L, 11L, PENDING, 15L));

        machines.add(new Machine(7L, "NORTH", 10L, 0L, true));
        machines.add(new Machine(9L, "NORTH", 5L, 45L, true));
        machines.add(new Machine(11L, "SOUTH", 0L, 0L, false));

        // Key、missing 与 secondary Index
        Job first = jobs.get(101L);
        boolean missing = !jobs.find(999L).isPresent();
        List<Job> machine7Jobs = jobs.byMachineId(7L).toList();
        long northMachines = machines.byZone("NORTH").count();

        // Field source、typed filter、projection 与 aggregate
        long allMinutes = jobs.durationMinutes.sum();
        long pendingMinutes = jobs
                .filter(jobs.status.eq(PENDING))
                .mapToLong(jobs.durationMinutes)
                .sum();

        // typed GroupBy
        LongGroupedLongResult jobsPerMachine = jobs
                .groupBy(jobs.machineId)
                .count();

        // 两张 Table 的 Equality Join；typed predicate 可以下推到左右输入
        long runnableJobs = jobs.join(machines)
                .on(jobs.machineId, machines.machineId)
                .inner()
                .filter(jobs.status.eq(PENDING))
                .filter(machines.enabled.eq(true))
                .count();

        // Join Pair projection：同时读取左右两侧 generated View
        long projectedCompletion = jobs.join(machines)
                .on(jobs.machineId, machines.machineId)
                .inner()
                .filter(jobs.status.eq(PENDING))
                .filter(machines.enabled.eq(true))
                .mapToLong(pair -> pair.right().availableMinute()
                        + pair.left().durationMinutes())
                .sum();

        // sequential 是默认模式；parallel 必须显式请求
        long parallelPending = jobs.parallel()
                .filter(jobs.status.eq(PENDING))
                .count();

        // Advanced diagnostic，不作为业务逻辑输入
        String plan = jobs.join(machines)
                .on(jobs.machineId, machines.machineId)
                .inner()
                .filter(machines.enabled.eq(true))
                ._explain();

        // point update、Selection update 与 point remove
        UpdateResult machineUpdated = machines.update(7L, editor -> {
            editor.availableMinute(40L);
            editor.workloadMinutes(editor.workloadMinutes() + 30L);
        });
        UpdateResult jobsUpdated = jobs
                .filter(jobs.status.eq(PENDING))
                .update(editor -> editor.status(SCHEDULED));
        RemoveResult removed = jobs.remove(102L);

        if (first.durationMinutes() != 30L
                || !missing
                || machine7Jobs.size() != 2
                || northMachines != 2L
                || allMinutes != 110L
                || pendingMinutes != 65L
                || jobsPerMachine.size() != 3L
                || runnableJobs != 2L
                || projectedCompletion != 70L
                || parallelPending != 3L
                || plan.length() == 0
                || machineUpdated.changed() != 1
                || jobsUpdated.changed() != 3
                || removed.removed() != 1) {
            throw new AssertionError("unexpected SOMA result");
        }
        System.out.println("SOMA two-table quick start: PASS");
    }
}
```

这个程序在一条连续路径中使用了 SOMA 的主要 generated API：

| API family | 示例中的作用 |
|---|---|
| `add/get/find` | 写入 detached object，并通过 Key 访问 0..1 条记录 |
| `byMachineId/byZone` | 使用 generated secondary Index selection |
| Field source与typed filter | 直接聚合 Field，并形成 optimizer-visible predicate |
| `groupBy` | 按 typed Field 生成结构化分组结果 |
| `join(...).on(...)` | 在同一 Group 的两张 Table 之间执行 Equality Join |
| Join Pair mapper | 同时读取 `pair.left()` 和 `pair.right()` |
| `parallel()` | 显式进入受控并行；默认仍为 sequential |
| point/Selection mutation | 更新一条 Key record，或原子更新一次 Table-local selection |
| `_explain()` | 观察 logical/physical decision，不参与业务判断 |

上例使用显式 Group，使 `JobTable` 与 `MachineTable` 可以进行关系计算。普通单状态场景也可以使用
JVM-global default Group：

```java
JobTable defaultJobs = Soma.jobTable();

SomaGroup active = Soma.createGroup();
SomaGroup backup = Soma.createGroup();

JobTable activeJobs = active.jobTable();
JobTable backupJobs = backup.jobTable();
```

同一 Group 的同一种 generated Table 只有一个 instance；不同 Group 的状态相互隔离。

### 4. 编译并运行

```sh
mvn clean compile

java -cp "target/classes:$HOME/.m2/repository/io/github/somaruntime/soma/\
soma-runtime/1.0.0-SNAPSHOT/soma-runtime-1.0.0-SNAPSHOT.jar" \
  example.jobs.JobsMain
```

预期输出：

```text
SOMA two-table quick start: PASS
```

## 继续使用 generated API

### Key、Index 与 Field source

上面的完整程序已经使用了这些能力。下面保留几个可以直接迁移到实际项目的组合写法：

```java
Job exact = jobs.get(101L);
Optional<Job> optional = jobs.find(101L);

List<Job> machineJobs = jobs.byMachineId(7L)
        .filter(jobs.status.eq(0))
        .toList();

long totalMinutes = jobs.durationMinutes.sum();
long[] pendingDurations = jobs
        .filter(jobs.status.eq(0))
        .mapToLong(jobs.durationMinutes)
        .toArray();
```

### Selection mutation

只有保留单 Table row lineage 的 `Selection` 才生成 mutation terminal：

```java
UpdateResult updated = jobs
        .byMachineId(7L)
        .filter(jobs.status.eq(PENDING))
        .update(editor -> editor.status(SCHEDULED));

RemoveResult removed = jobs
        .byMachineId(7L)
        .remove();
```

Field、mapped result、Group 和 Join 不负责删除 Record；Record membership 始终由 Table 管理。

### GroupBy、Join 与 parallel

Join 可以继续执行 projection、aggregate 和 relation filter，而不是只能返回匹配数量：

```java
LongGroupedLongResult minutesPerMachine = jobs
        .groupBy(jobs.machineId)
        .sum(jobs.durationMinutes);

long enabledMinutes = jobs
        .join(machines)
        .on(jobs.machineId, machines.machineId)
        .inner()
        .filter(machines.enabled.eq(true))
        .mapToLong(pair -> pair.left().durationMinutes())
        .sum();

long parallelPending = jobs
        .parallel()
        .filter(jobs.status.eq(0))
        .count();
```

GroupBy 和 Join 的完整可运行代码见
[`real-time-dispatch`](soma-examples/real-time-dispatch/README.md) 与
[`group-relation` consumer](tests/group-relation/src/main/java/example/i5/I5ConsumerMain.java)。

### Advanced diagnostics

```java
String plan = jobs
        .filter(jobs.machineId.eq(7L))
        ._explain();

TableMetadata metadata = jobs._metadata();
```

`_metadata()` 和 `_explain()` 面向高级用户、诊断与 profile，不应成为 application 业务逻辑的
输入合同。

## 核心能力

### Compile

- package-level `@SomaSchema` 与 aggregating Java 8 annotation processor；
- generated `Soma`、`SomaGroup`、Table、Field、Index、View 和 Editor API；
- compile-time type、capability、owner、name collision 和完整 source-set validation；
- deterministic full regeneration，不依赖 reflection 或 application-written planner SPI。

### State

- primitive/reference chunked column storage 与 logical Value flattening；
- checked 32-bit Table-local structural domain、checked 64-bit cumulative domain 和
  schema-defined application numeric types；
- Key、secondary Index、point mutation 与 Selection atomic mutation；
- AUTO/OFF compression、retained/temporary managed-memory accounting。

### Compute

- Table、Field、IndexSelection 与 Relation direct source；
- typed filter、map、aggregate、sort、top、slice 与 materialization；
- typed GroupBy、binary Equality Join 和 bounded Cross Join；
- Canonical Logical IR、semantics-preserving optimizer、specialized sequential execution；
- application-owned/common `ForkJoinPool` 和显式 `parallel()`。

### Safety and observability

- operation lifecycle、resource preflight、callback scope 和 deterministic order；
- stable structured failure 与 failed mutation zero publication；
- typed `_metadata()` 与 safe `_explain()`。

## 产品边界

SOMA 是同 JVM、单进程、同步的 runtime-state computation library，不是 persistence database、ORM
或分布式计算服务。Application 继续拥有跨 Table 编排、external I/O、side effect、业务补偿、普通
Java object 的线程安全，以及跨系统 transaction。SOMA V1 不提供 cross-Table transaction、动态
runtime schema、ChildTable、任意多路/non-equality Join 或 public physical Column API。

## Reference Applications

这里的 example 不是片段式 demo，而是三个只使用 SOMA public/generated API、按真实 application
责任组织的 reference project：

| Project | 展示内容 |
|---|---|
| [`scheduling`](soma-examples/scheduling/README.md) | 标准 100K-operation FJSP、四张 runtime Table、FCFS + SPT 与自然 waiting `add/remove` |
| [`simulation`](soma-examples/simulation/README.md) | Grassing空间个体仿真、两张runtime Table、五阶段tick、UI/headless与确定性验证 |
| [`real-time-dispatch`](soma-examples/real-time-dispatch/README.md) | Index 缩窄、typed Join、显式并行与 external dispatch boundary |

从仓库根目录构建并运行全部 reference application：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/pom.xml clean package
```

更多说明见 [SOMA reference applications](soma-examples/README.md)。

## 构建、验证与发布状态

```sh
./scripts/check.sh          # 日常完整正确性与本地交付检查
./scripts/qualify.sh        # 完整 non-publishing qualification
./scripts/benchmark.sh      # 独立性能与场景证据
./scripts/package-local.sh  # 本地 artifact、SBOM、checksum 与 source bundle
```

当前 V1 implementation、Canonical IR/Execution replacement 与 G1–G10 local/non-publishing
qualification 已完成。仓库尚未发布 GitHub Release、GitHub Package 或 Maven Central artifact，
因此当前外部使用方式仍是 source build。该状态不构成正式 release、跨硬件性能 SLA 或一亿行性能承诺。

## 仓库导航

| Path | 主要读者 | 内容 |
|---|---|---|
| [`soma-runtime/`](soma-runtime/README.md) | Library user | annotations、shared/generated API linkage 与 runtime |
| [`soma-processor/`](soma-processor/README.md) | Library user | schema compiler 与 generated source processor |
| [`soma-examples/`](soma-examples/README.md) | Library user | 三个完整 reference application |
| `docs/` | Library user | 用户文档入口；当前仅占位，等待独立专题 |
| [`benchmarks/`](benchmarks/README.md) | Maintainer | 长期 non-production performance/correctness evidence |
| [`scripts/`](scripts/README.md) | Maintainer / CI | 四个稳定检查、资格、benchmark 与本地打包入口 |

项目蓝图、正式 Design、Engineering 与 Conformance 集中在 [`project/`](project/README.md)，服务
Product Owner、maintainer 和 Codex/Agent。Codex/Agent 应从 [`AGENTS.md`](AGENTS.md) 开始导航；
普通 Library user 不需要阅读完整内部治理过程。

## 支持与许可

- 使用问题与支持边界：[SUPPORT.md](SUPPORT.md)
- 安全问题：[SECURITY.md](SECURITY.md)
- 行为准则：[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- 许可证：[Apache License 2.0](LICENSE)
- 品牌与第三方声明：[NOTICE](NOTICE)、[assets/README.md](assets/README.md)
